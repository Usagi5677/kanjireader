package com.example.kanjireader

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.*

class TextSelectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "TextSelectionView"
        private const val EXTRA_TOUCH_PADDING = 8f
        private const val FURIGANA_SIZE_RATIO = 0.4f // Furigana is 40% of main text size
        private const val FURIGANA_SPACING = 8f // Space between furigana and main text
        private const val TOP_MARGIN_FOR_FURIGANA = 24f // Extra top margin when furigana is shown
    }

    // Text and layout properties
    private var displayText = ""
    private var visionText: Text? = null
    private var textPaint: TextPaint
    private var furiganaPaint: TextPaint
    private var staticLayout: StaticLayout? = null

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
    }

    fun setFuriganaProcessor(processor: FuriganaProcessor) {
        this.furiganaProcessor = processor
    }
    

    fun setShowFurigana(show: Boolean) {
        if (showFurigana != show) {
            showFurigana = show

            clearSelection()

            if (show) {
                processFurigana()
            }
            requestLayout()
            invalidate()
        }
    }

    fun setText(text: String, visionText: Text?) {
        this.displayText = text
        this.visionText = visionText
        this.furiganaText = null

        Log.d(TAG, "setText called with text length: ${text.length}")

        // Clear previous selection
        clearSelection()

        // Process furigana if enabled
        if (showFurigana) {
            processFurigana()
        }

        // Request layout to trigger measurement
        requestLayout()
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
                    invalidate()
                    requestLayout()
                    
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
        val baseHeight = if (layout != null) {
            layout.height + 32 // Base padding
        } else {
            200 // Minimum height
        }

        // Add extra height for furigana if enabled
        val extraHeight = if (showFurigana) {
            calculateFuriganaExtraHeight()
        } else {
            0
        }

        setMeasuredDimension(width, baseHeight + extraHeight)
    }

    private fun calculateFuriganaExtraHeight(): Int {
        val furiganaLineHeight = furiganaPaint.textSize + FURIGANA_SPACING
        val layout = staticLayout ?: return 0

        // Add top margin for first line furigana
        val topMargin = TOP_MARGIN_FOR_FURIGANA

        // Add furigana space for each line
        val furiganaSpace = layout.lineCount * furiganaLineHeight

        return (topMargin + furiganaSpace).toInt()
    }

    private fun createTextLayout(width: Int) {
        if (displayText.isEmpty() || width <= 32) {
            staticLayout = null
            characterBounds.clear()
            return
        }

        val textWidth = width - 32 // 16dp padding each side

        // Use original text without modification
        staticLayout = StaticLayout.Builder
            .obtain(displayText, 0, displayText.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(12f, 1.3f)
            .setIncludePad(false)
            .build()

        Log.d(TAG, "Created text layout with width: $textWidth, lines: ${staticLayout?.lineCount}")

        // Build character bounds for selection
        buildCharacterBounds(width)
    }

    private fun buildCharacterBounds(viewWidth: Int) {
        characterBounds.clear()
        val layout = staticLayout ?: return

        val furiganaOffset = if (showFurigana) {
            furiganaPaint.textSize + FURIGANA_SPACING
        } else {
            0f
        }

        val topOffset = if (showFurigana) TOP_MARGIN_FOR_FURIGANA else 16f

        for (line in 0 until layout.lineCount) {
            val lineStart = layout.getLineStart(line)
            val lineEnd = layout.getLineEnd(line)

            // Calculate line offset considering furigana
            val lineOffset = topOffset + (line * furiganaOffset)

            val lineTop = layout.getLineTop(line).toFloat() + lineOffset
            val lineBottom = layout.getLineBottom(line).toFloat() + lineOffset

            for (charIndex in lineStart until lineEnd) {
                if (charIndex < displayText.length) {
                    val char = displayText[charIndex]
                    if (char.isWhitespace()) continue

                    val charLeft = layout.getPrimaryHorizontal(charIndex) + 16f

                    val charRight = if (charIndex + 1 < displayText.length && charIndex + 1 < lineEnd) {
                        layout.getPrimaryHorizontal(charIndex + 1) + 16f
                    } else {
                        val measuredWidth = textPaint.measureText(char.toString())
                        val calculatedRight = charLeft + measuredWidth

                        if (charIndex == lineEnd - 1) {
                            val maxRight = viewWidth - 16f
                            maxOf(calculatedRight, minOf(maxRight, charLeft + measuredWidth * 1.5f))
                        } else {
                            calculatedRight
                        }
                    }

                    val visualBounds = RectF(charLeft, lineTop, charRight, lineBottom)
                    val touchBounds = RectF(
                        charLeft - EXTRA_TOUCH_PADDING,
                        lineTop - EXTRA_TOUCH_PADDING,
                        charRight + EXTRA_TOUCH_PADDING,
                        lineBottom + EXTRA_TOUCH_PADDING
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

        canvas.save()
        canvas.translate(16f, 16f)

        if (showFurigana && furiganaText != null) {
            drawTextWithFurigana(canvas, layout)
        } else {
            // Normal drawing without furigana
            canvas.translate(0f, if (showFurigana) TOP_MARGIN_FOR_FURIGANA else 0f)
            layout.draw(canvas)
        }

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

    private fun findKanjiPortion(text: String): KanjiInfo? {
        if (text.isEmpty()) return null
        
        // Find the first kanji character
        var kanjiStart = -1
        for (i in text.indices) {
            if (isKanji(text[i])) {
                kanjiStart = i
                break
            }
        }
        
        if (kanjiStart == -1) {
            // No kanji found
            return null
        }
        
        // Find the end of the continuous kanji sequence
        var kanjiEnd = kanjiStart
        for (i in kanjiStart until text.length) {
            if (isKanji(text[i])) {
                kanjiEnd = i + 1
            } else {
                break
            }
        }
        
        val kanjiText = text.substring(kanjiStart, kanjiEnd)
        Log.d(TAG, "Found kanji portion in '$text': startIndex=$kanjiStart, kanjiText='$kanjiText'")
        
        return KanjiInfo(
            startIndex = kanjiStart,
            kanjiText = kanjiText
        )
    }
    
    private fun isKanji(char: Char): Boolean {
        val codePoint = char.code
        return (codePoint in 0x4E00..0x9FAF) || (codePoint in 0x3400..0x4DBF)
    }

    private fun drawTextWithFurigana(canvas: Canvas, layout: StaticLayout) {
        val segments = furiganaText?.segments ?: return

        // Calculate the furigana height
        val furiganaHeight = furiganaPaint.textSize + FURIGANA_SPACING

        // Draw segments by reconstructing positions sequentially
        var currentPos = 0
        
        for (segment in segments) {
            if (currentPos >= displayText.length) break
            
            // Find the actual position of this segment in the current text
            val segmentStart = displayText.indexOf(segment.text, currentPos)
            if (segmentStart == -1) {
                // Skip if segment not found
                continue
            }
            
            val line = layout.getLineForOffset(segmentStart)
            
            // Calculate positions using actual segment position
            val x = layout.getPrimaryHorizontal(segmentStart)
            val lineOffset = TOP_MARGIN_FOR_FURIGANA + (line * furiganaHeight)
            val baselineY = layout.getLineBaseline(line).toFloat() + lineOffset

            // Draw furigana if present
            if (segment.furigana != null && segment.isKanji) {
                // Find kanji portion within the segment
                val kanjiInfo = findKanjiPortion(segment.text)
                if (kanjiInfo != null) {
                    // Calculate position for just the kanji portion
                    val preKanjiWidth = textPaint.measureText(segment.text.substring(0, kanjiInfo.startIndex))
                    val kanjiWidth = textPaint.measureText(kanjiInfo.kanjiText)
                    val furiganaWidth = furiganaPaint.measureText(segment.furigana)

                    // Position furigana over just the kanji part
                    val kanjiX = x + preKanjiWidth
                    val furiganaX = kanjiX + (kanjiWidth - furiganaWidth) / 2
                    val furiganaY = baselineY - textPaint.textSize - FURIGANA_SPACING

                    canvas.drawText(segment.furigana, furiganaX, furiganaY, furiganaPaint)
                } else {
                    // Fallback: center over entire segment if no kanji found
                    val segmentWidth = textPaint.measureText(segment.text)
                    val furiganaWidth = furiganaPaint.measureText(segment.furigana)
                    val furiganaX = x + (segmentWidth - furiganaWidth) / 2
                    val furiganaY = baselineY - textPaint.textSize - FURIGANA_SPACING

                    canvas.drawText(segment.furigana, furiganaX, furiganaY, furiganaPaint)
                }
            }

            // Draw main text
            canvas.drawText(segment.text, x, baselineY, textPaint)
            
            // Move to next position
            currentPos = segmentStart + segment.text.length
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

            // Adjust top if furigana is shown to not cover it
            if (showFurigana) {
                top -= (furiganaPaint.textSize + FURIGANA_SPACING)
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

    private fun checkWordBoundaryExtension(selectedChars: List<CharacterBound>): List<CharacterBound> {
        if (selectedChars.isEmpty()) return selectedChars

        // First check if the current selection is already a valid word
        val currentText = selectedChars
            .sortedBy { it.charIndex }
            .map { it.char }
            .joinToString("")
        
        // If current selection is a valid word, don't extend it
        if (onWordLookup?.invoke(currentText) == true) {
            Log.d(TAG, "Current selection '$currentText' is already a valid word - not extending")
            return selectedChars
        }

        val sortedChars = selectedChars.sortedBy { it.charIndex }
        val lastChar = sortedChars.last()
        val lastCharLine = lastChar.lineNumber

        val isLastOnLine = characterBounds
            .filter { it.lineNumber == lastCharLine }
            .none { it.charIndex > lastChar.charIndex }

        if (!isLastOnLine) {
            return selectedChars
        }

        if (!isWordCharacter(lastChar.char)) {
            return selectedChars
        }

        return tryExtendToNextLine(selectedChars)
    }

    private fun tryExtendToNextLine(selectedChars: List<CharacterBound>): List<CharacterBound> {
        val sortedChars = selectedChars.sortedBy { it.charIndex }
        val lastChar = sortedChars.last()
        val nextLineNumber = lastChar.lineNumber + 1

        val nextLineChars = characterBounds
            .filter { it.lineNumber == nextLineNumber }
            .sortedBy { it.charIndex }

        if (nextLineChars.isEmpty()) {
            return selectedChars
        }

        val extendedChars = mutableListOf<CharacterBound>()
        extendedChars.addAll(sortedChars)
        
        // Keep track of the longest valid word found
        var longestValidChars: List<CharacterBound> = selectedChars
        var longestValidText = ""

        for (nextChar in nextLineChars) {
            if (isPunctuationOrSpace(nextChar.char)) {
                break
            }

            extendedChars.add(nextChar)

            val testText = extendedChars
                .sortedBy { it.charIndex }
                .map { it.char }
                .joinToString("")

            // Check if this combination is a valid word
            if (onWordLookup?.invoke(testText) == true) {
                // Found a valid word - but keep testing for longer ones
                longestValidChars = extendedChars.toList()
                longestValidText = testText
                Log.d(TAG, "Found valid word: $testText (length: ${testText.length})")
            }
        }
        
        // Return the longest valid word found, or original selection if none
        if (longestValidText.isNotEmpty()) {
            Log.d(TAG, "Selecting longest word: $longestValidText")
            return longestValidChars
        }

        return selectedChars
    }

    private fun isWordCharacter(char: Char): Boolean {
        val codePoint = char.code
        return (codePoint in 0x3040..0x309F) ||
                (codePoint in 0x30A0..0x30FF) ||
                (codePoint in 0x4E00..0x9FAF) ||
                (codePoint in 0x3400..0x4DBF)
    }

    private fun isPunctuationOrSpace(char: Char): Boolean {
        return char.isWhitespace() || char in "。、！？「」『』（）［］｛｝〈〉《》・.,!?()[]{}\"'"
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastSelectedChars = emptyList()
                lastSelectedText = ""
                callbackInProgress = false  // Reset on new selection attempt

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
                    var selectedChars = getSelectedCharacters()

                    // Temporarily disable word boundary extension to fix duplicate lookup issue
                    // if (selectedChars.isNotEmpty() && onWordLookup != null) {
                    //     selectedChars = checkWordBoundaryExtension(selectedChars)
                    // }

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
                            // Note: callbackInProgress will be reset by clearPersistentSelection()
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
        callbackInProgress = false  // Reset the callback flag
        invalidate()
    }
    
    fun clearPersistentSelection() {
        lastSelectedChars = emptyList()
        lastSelectedText = ""
        // Note: callbackInProgress is now managed by setCallbackActive()
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

    data class CharacterBound(
        val char: Char,
        val charIndex: Int,
        val lineNumber: Int,
        val bounds: RectF,
        val visualBounds: RectF
    )

    data class KanjiInfo(
        val startIndex: Int,
        val kanjiText: String
    )

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Cancel any ongoing furigana processing
        furiganaJob?.cancel()
    }
}