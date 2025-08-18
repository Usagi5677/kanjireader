package com.example.kanjireader

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ReplacementSpan
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.*

/**
 * Custom ReplacementSpan for rendering furigana (reading hints) above kanji characters.
 * This span handles both measurement and drawing of text segments with furigana positioned only over kanji.
 */
class FuriganaReplacementSpan(
    private val fullText: String,          // Full segment text (e.g., "出くわし")
    private val furiganaText: String,      // Furigana for kanji only (e.g., "で")
    private val textPaint: TextPaint,
    private val furiganaPaint: TextPaint
) : ReplacementSpan() {
    
    companion object {
        private const val FURIGANA_SPACING = 4f // Space between furigana and main text
    }
    
    // Find the kanji portion within the text
    private fun findKanjiPortion(): Pair<Int, String>? {
        var kanjiStart = -1
        var kanjiEnd = -1
        
        for (i in fullText.indices) {
            if (isKanji(fullText[i])) {
                if (kanjiStart == -1) kanjiStart = i
                kanjiEnd = i + 1
            } else if (kanjiStart != -1) {
                // Stop at first non-kanji after finding kanji
                break
            }
        }
        
        return if (kanjiStart != -1) {
            Pair(kanjiStart, fullText.substring(kanjiStart, kanjiEnd))
        } else {
            null
        }
    }
    
    private fun isKanji(char: Char): Boolean {
        val codePoint = char.code
        return (codePoint in 0x4E00..0x9FAF) || (codePoint in 0x3400..0x4DBF)
    }
    
    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        // Measure the full text width
        val fullWidth = textPaint.measureText(fullText)
        
        // Check if we need extra width for furigana
        val kanjiInfo = findKanjiPortion()
        if (kanjiInfo != null) {
            val (kanjiStart, kanjiText) = kanjiInfo
            val preKanjiWidth = if (kanjiStart > 0) {
                textPaint.measureText(fullText.substring(0, kanjiStart))
            } else 0f
            
            val kanjiWidth = textPaint.measureText(kanjiText)
            val furiganaWidth = furiganaPaint.measureText(furiganaText)
            
            // If furigana is wider than kanji, we need extra space
            if (furiganaWidth > kanjiWidth) {
                val extraWidth = (furiganaWidth - kanjiWidth) / 2
                val totalWidth = fullWidth + extraWidth
                
                // Update font metrics to account for furigana height
                if (fm != null) {
                    val kanjiMetrics = textPaint.fontMetricsInt
                    val furiganaMetrics = furiganaPaint.fontMetricsInt
                    
                    val furiganaHeight = furiganaMetrics.bottom - furiganaMetrics.top
                    val spacing = FURIGANA_SPACING.toInt()
                    
                    fm.top = kanjiMetrics.top - furiganaHeight - spacing
                    fm.ascent = kanjiMetrics.ascent - furiganaHeight - spacing
                    fm.descent = kanjiMetrics.descent
                    fm.bottom = kanjiMetrics.bottom
                    fm.leading = kanjiMetrics.leading
                }
                
                return totalWidth.toInt()
            }
        }
        
        // Update font metrics even if no extra width needed
        if (fm != null) {
            val kanjiMetrics = textPaint.fontMetricsInt
            val furiganaMetrics = furiganaPaint.fontMetricsInt
            
            val furiganaHeight = furiganaMetrics.bottom - furiganaMetrics.top
            val spacing = FURIGANA_SPACING.toInt()
            
            fm.top = kanjiMetrics.top - furiganaHeight - spacing
            fm.ascent = kanjiMetrics.ascent - furiganaHeight - spacing
            fm.descent = kanjiMetrics.descent
            fm.bottom = kanjiMetrics.bottom
            fm.leading = kanjiMetrics.leading
        }
        
        return fullWidth.toInt()
    }
    
    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        // Draw the full text
        canvas.drawText(fullText, x, y.toFloat(), textPaint)
        
        // Find kanji portion and draw furigana over it
        val kanjiInfo = findKanjiPortion()
        if (kanjiInfo != null) {
            val (kanjiStart, kanjiText) = kanjiInfo
            
            // Calculate position for furigana
            val preKanjiWidth = if (kanjiStart > 0) {
                textPaint.measureText(fullText.substring(0, kanjiStart))
            } else 0f
            
            val kanjiWidth = textPaint.measureText(kanjiText)
            val furiganaWidth = furiganaPaint.measureText(furiganaText)
            
            // Center furigana over kanji portion only
            val kanjiX = x + preKanjiWidth
            val furiganaX = kanjiX + (kanjiWidth - furiganaWidth) / 2
            val furiganaY = y - textPaint.textSize - FURIGANA_SPACING
            
            canvas.drawText(furiganaText, furiganaX, furiganaY, furiganaPaint)
        }
    }
}

/**
 * A custom view for displaying Japanese text with selectable characters and optional furigana.
 * This refactored version uses SpannableStringBuilder with ReplacementSpans for robust layout.
 */
class TextSelectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "TextSelectionView"
        private const val EXTRA_TOUCH_PADDING = 8f
        private const val FURIGANA_SIZE_RATIO = 0.4f // Furigana is 40% of main text size
        private const val VIEW_PADDING = 16f
    }

    // Text and layout properties
    private var displayText = ""
    private var visionText: Text? = null
    private var textPaint: TextPaint
    private var furiganaPaint: TextPaint
    private var staticLayout: StaticLayout? = null
    private var spannableText: SpannableStringBuilder? = null

    // Furigana properties
    private var showFurigana = false
    private var furiganaText: FuriganaText? = null
    private var furiganaProcessor: FuriganaProcessor? = null
    private var furiganaJob: Job? = null

    // Selection properties
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragCurrentX = 0f
    private var dragCurrentY = 0f

    // Keep selection visible after drag ends
    private var lastSelectedChars: List<CharacterBound> = emptyList()
    private var lastSelectedText = ""

    // Visual elements
    private val dotPaint = Paint().apply {
        color = 0xFF2196F3.toInt()
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val selectionPaint = Paint().apply {
        color = 0x332196F3
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val boundingBoxPaint = Paint().apply {
        color = 0xFF2196F3.toInt()
        strokeWidth = 4f
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    // Character mapping for selection
    private val characterBounds = mutableListOf<CharacterBound>()

    // Callbacks
    var onTextSelected: ((String) -> Unit)? = null
    var onWordLookup: ((String) -> Boolean)? = null

    // Word highlighting
    private var highlightedWordRange: Pair<Int, Int>? = null
    private val highlightedTextPaint = TextPaint().apply {
        textSize = 48f
        color = ContextCompat.getColor(context, R.color.blue_700)
        isAntiAlias = true
        isFakeBoldText = true
    }

    // Debounce mechanism to prevent duplicate callbacks
    private var lastCallbackText = ""
    private var lastCallbackTime = 0L
    private var callbackInProgress = false

    init {
        textPaint = TextPaint().apply {
            textSize = 48f
            color = ContextCompat.getColor(context, android.R.color.black)
            isAntiAlias = true
        }

        furiganaPaint = TextPaint().apply {
            textSize = textPaint.textSize * FURIGANA_SIZE_RATIO
            color = ContextCompat.getColor(context, android.R.color.black)
            isAntiAlias = true
        }
        
        // Update highlighted text paint to match
        highlightedTextPaint.textSize = textPaint.textSize
    }

    fun setFuriganaProcessor(processor: FuriganaProcessor) {
        this.furiganaProcessor = processor
    }

    fun setShowFurigana(show: Boolean) {
        if (showFurigana != show) {
            showFurigana = show

            clearSelection()

            // Force complete layout recreation when furigana mode changes
            staticLayout = null
            characterBounds.clear()

            if (show) {
                processFurigana()
            } else {
                // Clear furigana and recreate layout
                requestLayout()
                invalidate()
            }

            // Force parent to remeasure and relayout
            parent?.let { parent ->
                if (parent is View) {
                    parent.requestLayout()
                }
            }
        }
    }

    fun setText(text: String, visionText: Text?) {
        this.displayText = text
        this.visionText = visionText
        this.furiganaText = null

        Log.d(TAG, "setText called with text length: ${text.length}")

        // Clear previous selection
        clearSelection()
        clearWordHighlight()

        // Process furigana if enabled
        if (showFurigana) {
            processFurigana()
        } else {
            // Request layout to trigger measurement
            requestLayout()
        }
    }

    /**
     * Highlight a word at the specified position range
     */
    fun highlightWord(startPosition: Int, endPosition: Int) {
        highlightedWordRange = Pair(startPosition, endPosition)
        // Recreate layout with highlighting
        val width = width
        if (width > 0) {
            createTextLayout(width)
        }
        invalidate()

        // Auto-scroll to show the highlighted word
        scrollToHighlightedWord(startPosition, endPosition)
    }

    /**
     * Clear word highlighting
     */
    fun clearWordHighlight() {
        highlightedWordRange = null
        // Recreate layout without highlighting
        val width = width
        if (width > 0) {
            createTextLayout(width)
        }
        invalidate()
    }

    private fun processFurigana() {
        if (displayText.isNotEmpty()) {
            // Cancel previous furigana job
            furiganaJob?.cancel()

            // Start new async furigana processing
            furiganaJob = CoroutineScope(Dispatchers.Main).launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        val processor = furiganaProcessor
                        if (processor != null) {
                            Log.d(TAG, "Using Kuromoji-based furigana processing")
                            processor.processText(displayText)
                        } else {
                            Log.w(TAG, "No furigana processor available")
                            return@withContext FuriganaText(emptyList(), displayText)
                        }
                    }

                    // Update UI on main thread
                    furiganaText = result
                    Log.d(TAG, "Processed furigana with ${result.segments.size} segments")

                    // Trigger redraw
                    requestLayout()
                    invalidate()

                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Log.e(TAG, "Error processing furigana", e)
                    }
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)

        // Create text layout with the measured width
        createTextLayout(width)

        // Calculate height based on text layout
        val layout = staticLayout
        val height = if (layout != null) {
            layout.height + (VIEW_PADDING * 2).toInt()
        } else {
            200 // Minimum height
        }

        setMeasuredDimension(width, height)
    }

    private fun createTextLayout(width: Int) {
        if (displayText.isEmpty() || width <= (VIEW_PADDING * 2).toInt()) {
            staticLayout = null
            characterBounds.clear()
            return
        }

        val textWidth = width - (VIEW_PADDING * 2).toInt()

        // Build spannable text with furigana spans
        val spannable = buildSpannableText()
        spannableText = spannable

        // Create StaticLayout with the spannable text
        staticLayout = StaticLayout.Builder
            .obtain(spannable, 0, spannable.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f) // Let spans handle spacing
            .setIncludePad(true)
            .build()

        Log.d(TAG, "Created text layout with width: $textWidth, lines: ${staticLayout?.lineCount}")

        // Build character bounds for selection
        buildCharacterBounds(width)
    }

    /**
     * Build a SpannableStringBuilder with FuriganaReplacementSpans for kanji segments
     */
    private fun buildSpannableText(): SpannableStringBuilder {
        val spannable = SpannableStringBuilder()
        
        if (!showFurigana || furiganaText == null) {
            // No furigana - build plain text with optional highlighting
            spannable.append(displayText)
            applyWordHighlighting(spannable)
            return spannable
        }

        // Build text with furigana spans
        val segments = furiganaText?.segments ?: listOf(
            FuriganaSegment(displayText, null, false, 0, displayText.length)
        )

        var currentPos = 0
        
        for (segment in segments) {
            val segmentStart = spannable.length
            
            if (segment.furigana != null && segment.isKanji) {
                // Add text with furigana span
                spannable.append(segment.text)
                
                val span = FuriganaReplacementSpan(
                    fullText = segment.text,
                    furiganaText = segment.furigana,
                    textPaint = getTextPaintForPosition(currentPos),
                    furiganaPaint = furiganaPaint
                )
                
                spannable.setSpan(
                    span,
                    segmentStart,
                    spannable.length,
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                // Add plain text without furigana
                spannable.append(segment.text)
            }
            
            currentPos += segment.text.length
        }

        applyWordHighlighting(spannable)
        return spannable
    }

    /**
     * Apply word highlighting to the spannable text
     */
    private fun applyWordHighlighting(spannable: SpannableStringBuilder) {
        val highlightRange = highlightedWordRange ?: return
        if (highlightRange.first < 0 || highlightRange.second > spannable.length) return
        
        // Create a custom span for highlighting
        val highlightSpan = object : ReplacementSpan() {
            override fun getSize(
                paint: Paint,
                text: CharSequence?,
                start: Int,
                end: Int,
                fm: Paint.FontMetricsInt?
            ): Int {
                return highlightedTextPaint.measureText(text, start, end).toInt()
            }
            
            override fun draw(
                canvas: Canvas,
                text: CharSequence?,
                start: Int,
                end: Int,
                x: Float,
                top: Int,
                y: Int,
                bottom: Int,
                paint: Paint
            ) {
                if (text != null) {
                    canvas.drawText(text, start, end, x, y.toFloat(), highlightedTextPaint)
                }
            }
        }
        
        // Apply highlighting span only if it doesn't conflict with furigana spans
        val existingSpans = spannable.getSpans(
            highlightRange.first, 
            highlightRange.second, 
            ReplacementSpan::class.java
        )
        
        if (existingSpans.isEmpty()) {
            spannable.setSpan(
                highlightSpan,
                highlightRange.first,
                highlightRange.second,
                SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    /**
     * Get the appropriate text paint for a character position (for highlighting)
     */
    private fun getTextPaintForPosition(position: Int): TextPaint {
        val highlightRange = highlightedWordRange
        return if (highlightRange != null && position >= highlightRange.first && position < highlightRange.second) {
            highlightedTextPaint
        } else {
            textPaint
        }
    }

    private fun buildCharacterBounds(viewWidth: Int) {
        characterBounds.clear()
        val layout = staticLayout ?: return
        val text = spannableText ?: SpannableStringBuilder(displayText)

        for (line in 0 until layout.lineCount) {
            val lineStart = layout.getLineStart(line)
            val lineEnd = layout.getLineEnd(line)

            val lineTop = layout.getLineTop(line).toFloat()
            val lineBottom = layout.getLineBottom(line).toFloat()

            for (charIndex in lineStart until lineEnd) {
                if (charIndex < text.length) {
                    val char = text[charIndex]
                    if (char.isWhitespace()) continue

                    val charLeft = layout.getPrimaryHorizontal(charIndex) + VIEW_PADDING

                    val charRight = if (charIndex + 1 < text.length && charIndex + 1 <= lineEnd) {
                        layout.getPrimaryHorizontal(charIndex + 1) + VIEW_PADDING
                    } else {
                        val measuredWidth = textPaint.measureText(char.toString())
                        charLeft + measuredWidth
                    }

                    val visualBounds = RectF(
                        charLeft, 
                        lineTop + VIEW_PADDING, 
                        charRight, 
                        lineBottom + VIEW_PADDING
                    )
                    
                    val touchBounds = RectF(
                        charLeft - EXTRA_TOUCH_PADDING,
                        lineTop + VIEW_PADDING - EXTRA_TOUCH_PADDING,
                        charRight + EXTRA_TOUCH_PADDING,
                        lineBottom + VIEW_PADDING + EXTRA_TOUCH_PADDING
                    )

                    characterBounds.add(
                        CharacterBound(
                            char = char,
                            charIndex = charIndex,
                            lineNumber = line,
                            bounds = touchBounds,
                            visualBounds = visualBounds
                        )
                    )
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val layout = staticLayout ?: return

        // Draw the text layout with all spans
        canvas.save()
        canvas.translate(VIEW_PADDING, VIEW_PADDING)
        layout.draw(canvas)
        canvas.restore()

        // Draw selection highlight (both during drag and after)
        if (isDragging) {
            drawCurrentSelection(canvas)
        } else if (lastSelectedChars.isNotEmpty()) {
            drawPersistentSelection(canvas)
        }

        // Draw dot only if dragging
        if (isDragging) {
            canvas.drawCircle(dragCurrentX, dragCurrentY, 15f, dotPaint)
        }
    }

    private fun drawCurrentSelection(canvas: Canvas) {
        val selectedChars = getSelectedCharacters()
        if (selectedChars.isEmpty()) return

        for (charBound in selectedChars) {
            canvas.drawRect(charBound.visualBounds, selectionPaint)
        }

        drawMultiLineBoundingBox(canvas, selectedChars)
    }

    private fun drawPersistentSelection(canvas: Canvas) {
        for (charBound in lastSelectedChars) {
            canvas.drawRect(charBound.visualBounds, selectionPaint)
        }

        drawMultiLineBoundingBox(canvas, lastSelectedChars)
    }

    private fun drawMultiLineBoundingBox(canvas: Canvas, selectedChars: List<CharacterBound>) {
        val lineGroups = selectedChars.groupBy { it.lineNumber }

        for ((lineNumber, charsInLine) in lineGroups) {
            if (charsInLine.isEmpty()) continue

            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = Float.MIN_VALUE
            var bottom = Float.MIN_VALUE

            for (charBound in charsInLine) {
                val bounds = charBound.visualBounds
                left = minOf(left, bounds.left)
                top = minOf(top, bounds.top)
                right = maxOf(right, bounds.right)
                bottom = maxOf(bottom, bounds.bottom)
            }

            val lineBounds = RectF(left - 6f, top - 6f, right + 6f, bottom + 6f)
            canvas.drawRoundRect(lineBounds, 12f, 12f, boundingBoxPaint)
        }
    }

    private fun getSelectedCharacters(): List<CharacterBound> {
        if (!isDragging) return emptyList()

        val selectionRect = RectF(
            minOf(dragStartX, dragCurrentX),
            minOf(dragStartY, dragCurrentY),
            maxOf(dragStartX, dragCurrentX),
            maxOf(dragStartY, dragCurrentY)
        )

        val selectedChars = characterBounds.filter { charBound ->
            RectF.intersects(selectionRect, charBound.bounds)
        }

        return selectedChars
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastSelectedChars = emptyList()
                lastSelectedText = ""
                callbackInProgress = false

                dragStartX = event.x
                dragStartY = event.y
                dragCurrentX = event.x
                dragCurrentY = event.y
                isDragging = true

                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    dragCurrentX = event.x
                    dragCurrentY = event.y
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                Log.d(TAG, "ACTION_UP: isDragging=$isDragging")
                if (isDragging) {
                    val selectedChars = getSelectedCharacters()

                    if (selectedChars.isNotEmpty()) {
                        val selectedText = selectedChars
                            .sortedBy { it.charIndex }
                            .map { it.char }
                            .joinToString("")

                        lastSelectedChars = selectedChars
                        lastSelectedText = selectedText

                        copyToClipboard(selectedText)

                        // Prevent duplicate callbacks with simple flag + time-based debounce
                        val currentTime = System.currentTimeMillis()
                        val timeDiff = currentTime - lastCallbackTime
                        Log.d(TAG, "Debounce check: selectedText='$selectedText', lastCallbackText='$lastCallbackText', timeDiff=${timeDiff}ms, callbackInProgress=$callbackInProgress")

                        if (!callbackInProgress && (selectedText != lastCallbackText || timeDiff > 500)) {
                            Log.d(TAG, "Allowing callback for '$selectedText'")
                            callbackInProgress = true
                            lastCallbackText = selectedText
                            lastCallbackTime = currentTime
                            onTextSelected?.invoke(selectedText)
                        } else {
                            Log.d(TAG, "Ignoring duplicate callback for '$selectedText' (inProgress=$callbackInProgress, timeDiff=${timeDiff}ms)")
                        }

                        // Clear persistent selection immediately after callback to prevent duplicates
                        lastSelectedChars = emptyList()
                        lastSelectedText = ""
                    }

                    isDragging = false
                    invalidate()
                }
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun clearSelection() {
        isDragging = false
        lastSelectedChars = emptyList()
        lastSelectedText = ""
        callbackInProgress = false
        invalidate()
    }

    fun clearPersistentSelection() {
        lastSelectedChars = emptyList()
        lastSelectedText = ""
        Log.d(TAG, "Cleared persistent selection")
        invalidate()
    }

    // Direct control of callback state from ImageAnnotationActivity
    fun setCallbackActive(active: Boolean) {
        callbackInProgress = active
        Log.d(TAG, "Set callbackInProgress = $active")
    }

    private fun copyToClipboard(text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Selected Text", text)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy to clipboard: ${e.message}")
        }
    }

    /**
     * Auto-scroll to show the highlighted word in the parent ScrollView
     */
    private fun scrollToHighlightedWord(startPosition: Int, endPosition: Int) {
        // Find the parent ScrollView
        val scrollView = findParentScrollView() ?: return

        // Get the layout to calculate line positions
        val layout = staticLayout ?: return

        if (startPosition < 0 || startPosition >= displayText.length) return

        // Find which line contains the highlighted word
        var lineIndex = -1
        for (i in 0 until layout.lineCount) {
            val lineStart = layout.getLineStart(i)
            val lineEnd = layout.getLineEnd(i)
            if (startPosition >= lineStart && startPosition < lineEnd) {
                lineIndex = i
                break
            }
        }

        if (lineIndex == -1) return

        // Calculate the Y position of the highlighted line
        val lineTop = layout.getLineTop(lineIndex)
        val lineBottom = layout.getLineBottom(lineIndex)

        val wordTop = (lineTop + VIEW_PADDING).toInt()
        val wordBottom = (lineBottom + VIEW_PADDING).toInt()

        // Get ScrollView dimensions
        val scrollViewHeight = scrollView.height
        val currentScrollY = scrollView.scrollY

        // Calculate if the word is visible
        val wordVisibleTop = wordTop - currentScrollY
        val wordVisibleBottom = wordBottom - currentScrollY

        // Scroll if the word is not fully visible
        val targetScrollY = when {
            wordVisibleTop < 0 -> {
                // Word is above visible area - scroll up to show it at top
                wordTop - 50 // Add some padding
            }
            wordVisibleBottom > scrollViewHeight -> {
                // Word is below visible area - scroll down to show it
                wordBottom - scrollViewHeight + 100 // Add some padding
            }
            else -> {
                // Word is already visible, no need to scroll
                return
            }
        }

        // Smooth scroll to the target position
        scrollView.smoothScrollTo(0, maxOf(0, targetScrollY))
    }

    /**
     * Find the parent ScrollView of this view
     */
    private fun findParentScrollView(): ScrollView? {
        var parent = this.parent
        while (parent != null) {
            if (parent is ScrollView) {
                return parent
            }
            parent = parent.parent
        }
        return null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Cancel any ongoing furigana processing
        furiganaJob?.cancel()
    }

    data class CharacterBound(
        val char: Char,
        val charIndex: Int,
        val lineNumber: Int,
        val bounds: RectF,
        val visualBounds: RectF
    )
}