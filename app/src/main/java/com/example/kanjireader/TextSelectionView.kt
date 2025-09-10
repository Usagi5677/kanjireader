package com.example.kanjireader

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spannable
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
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
    private val furiganaPaint: TextPaint,
    private val greyTextPaint: TextPaint? = null,
    private val startPosition: Int = 0,    // Start position in the full text
    private val wordPositions: List<Pair<Int, Int>> = emptyList()
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
        // Determine which paint to use based on word positions
        val paintToUse = if (greyTextPaint != null && !isPositionCoveredByWordCard(startPosition)) {
            greyTextPaint
        } else {
            textPaint
        }
        
        // Draw the full text with appropriate paint
        canvas.drawText(fullText, x, y.toFloat(), paintToUse)
        
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
    
    private fun isPositionCoveredByWordCard(position: Int): Boolean {
        return wordPositions.any { (start, end) -> 
            position >= start && position < end
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
    private var lastLayoutWidth: Int = -1
    private var lastLayoutHash: Int = -1
    private var cachedSpannableForHighlight: SpannableStringBuilder? = null
    private var lastHighlightRange: Pair<Int, Int>? = null

    // Furigana properties
    private var showFurigana = false
    private var furiganaText: FuriganaText? = null
    private var furiganaProcessor: FuriganaProcessor? = null
    private var furiganaJob: Job? = null
    
    // Word position tracking for grey styling
    private var wordPositions: List<Pair<Int, Int>> = emptyList() // List of (startPos, endPos)

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
        color = ContextCompat.getColor(context, R.color.blue_600) // Same as forms tab
        isAntiAlias = true
        isFakeBoldText = true
    }
    
    // Paint for non-word card text (grey in light mode, black in dark mode)
    private val greyTextPaint = TextPaint().apply {
        textSize = 64f // Same as main text size
        color = ContextCompat.getColor(context, R.color.subtitle_text_color)
        isAntiAlias = true
    }
    

    // Debounce mechanism to prevent duplicate callbacks
    private var lastCallbackText = ""
    private var lastCallbackTime = 0L
    private var callbackInProgress = false

    init {
        textPaint = TextPaint().apply {
            textSize = 64f // Increased from 48f to 64f
            color = ContextCompat.getColor(context, R.color.text_primary_color)
            isAntiAlias = true
        }

        furiganaPaint = TextPaint().apply {
            textSize = textPaint.textSize * FURIGANA_SIZE_RATIO
            color = ContextCompat.getColor(context, R.color.text_primary_color)
            isAntiAlias = true
        }
        
        // Update highlighted text paint to match
        highlightedTextPaint.textSize = textPaint.textSize
        
        // Update grey text paint to match
        greyTextPaint.textSize = textPaint.textSize
    }

    fun setFuriganaProcessor(processor: FuriganaProcessor) {
        this.furiganaProcessor = processor
    }
    
    /**
     * Set word positions to apply grey styling to non-word card text
     */
    fun setWordPositions(positions: List<Pair<Int, Int>>) {
        this.wordPositions = positions
        // Invalidate layout cache since styling changed
        lastLayoutHash = -1
        cachedSpannableForHighlight = null
        lastHighlightRange = null
        // Rebuild layout to apply styling
        rebuildLayoutWithStyling()
        invalidate()
    }
    
    /**
     * Rebuild the layout with current styling (grey for non-word card text)
     */
    private fun rebuildLayoutWithStyling() {
        if (displayText.isEmpty()) return
        
        // Invalidate layout cache since styling is changing
        lastLayoutHash = -1
        cachedSpannableForHighlight = null
        lastHighlightRange = null
        
        val textWidth = (width - (VIEW_PADDING * 2).toInt()).takeIf { it > 0 } ?: 500
        
        // Build spannable text with styling (including highlighting)
        val spannable = buildSpannableText()
        spannableText = spannable

        // Create StaticLayout
        staticLayout = StaticLayout.Builder
            .obtain(spannable, 0, spannable.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f)
            .setIncludePad(true)
            .build()
    }

    fun setShowFurigana(show: Boolean) {
        if (showFurigana != show) {
            showFurigana = show

            clearSelection()

            // Force complete layout recreation when furigana mode changes
            staticLayout = null
            characterBounds.clear()
            lastLayoutWidth = -1
            lastLayoutHash = -1
            cachedSpannableForHighlight = null
            lastHighlightRange = null

            if (show) {
                processFurigana()
            } else {
                // Clear furigana and rebuild layout with grey styling
                furiganaText = null
                rebuildLayoutWithStyling()
                invalidate()
            }

            // Force remeasure and relayout
            requestLayout()
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
        
        // Invalidate layout cache
        lastLayoutWidth = -1
        lastLayoutHash = -1
        cachedSpannableForHighlight = null
        lastHighlightRange = null

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
        // Validate positions before setting highlight
        if (startPosition < 0 || endPosition < 0 || startPosition >= endPosition) {
            Log.w("TextSelectionView", "Invalid highlight range: $startPosition-$endPosition, clearing highlight")
            clearWordHighlight()
            return
        }
        
        // Validate positions are within display text bounds
        if (startPosition >= displayText.length || endPosition > displayText.length) {
            Log.w("TextSelectionView", "Highlight range $startPosition-$endPosition exceeds text length ${displayText.length}, clearing highlight")
            clearWordHighlight()
            return
        }
        
        highlightedWordRange = Pair(startPosition, endPosition)
        
        // If furigana processing is in progress, wait for it to complete before highlighting
        if (showFurigana && furiganaText == null) {
            Log.d(TAG, "Waiting for furigana processing to complete before highlighting")
            // Schedule highlighting to retry after a short delay
            postDelayed({
                highlightWordInternal(startPosition, endPosition)
            }, 100)
            return
        }
        
        // Highlight immediately without debouncing
        highlightWordInternal(startPosition, endPosition)
    }
    
    private fun highlightWordInternal(startPosition: Int, endPosition: Int) {
        // Always recreate layout when highlighting a word (especially important in furigana mode)
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
        // Clear word positions to prevent stale position references
        wordPositions = emptyList()
        // Recreate layout without highlighting
        val width = width
        if (width > 0) {
            createTextLayout(width)
        }
        invalidate()
    }
    
    /**
     * Get the length of the display text for debugging
     */
    fun getDisplayTextLength(): Int {
        return displayText.length
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

                    // Rebuild layout with grey styling preserved
                    rebuildLayoutWithStyling()
                    // Request layout since height may have changed with furigana
                    requestLayout()
                    invalidate()
                    
                    // If there's a pending highlight, apply it now that furigana is ready
                    highlightedWordRange?.let { range ->
                        Log.d(TAG, "Applying delayed highlight after furigana processing: ${range.first}-${range.second}")
                        highlightWordInternal(range.first, range.second)
                    }

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
            var totalHeight = layout.height + (VIEW_PADDING * 2).toInt()
            
            // Add extra height for furigana if enabled
            if (showFurigana && furiganaText != null) {
                // More generous height calculation for furigana
                // Each line needs space for furigana above it
                val lineCount = layout.lineCount
                val furiganaHeight = furiganaPaint.textSize + 8f // Furigana size + more spacing
                // Use 75% of lines having furigana for better coverage
                val extraHeight = (furiganaHeight * lineCount * 0.75f).toInt()
                totalHeight += extraHeight
            }
            
            totalHeight
        } else {
            200 // Minimum height
        }

        setMeasuredDimension(width, height)
    }

    private fun createTextLayout(width: Int) {
        if (displayText.isEmpty() || width <= (VIEW_PADDING * 2).toInt()) {
            staticLayout = null
            characterBounds.clear()
            lastLayoutWidth = -1
            lastLayoutHash = -1
            cachedSpannableForHighlight = null
            lastHighlightRange = null
            return
        }

        val textWidth = width - (VIEW_PADDING * 2).toInt()

        // Create a hash of the current state to detect if we need to recreate the layout
        // Exclude highlightedWordRange from cache hash - we'll handle highlighting separately
        val currentHash = (displayText + showFurigana.toString() + 
                          (furiganaText?.segments?.size ?: 0).toString() + 
                          (wordPositions?.size ?: 0).toString()).hashCode()

        // Check if we can reuse the existing layout (allow small width differences)
        val widthDiff = kotlin.math.abs(lastLayoutWidth - textWidth)
        val highlightChanged = lastHighlightRange != highlightedWordRange
        
        if (staticLayout != null && widthDiff <= 2 && lastLayoutHash == currentHash) {
            // Layout structure is valid, check if only highlighting changed
            if (highlightChanged && cachedSpannableForHighlight != null) {
                // Fast path: only update highlighting without full layout recreation
                val updatedSpannable = applyHighlightingToSpannable(cachedSpannableForHighlight!!)
                spannableText = updatedSpannable
                
                // Recreate layout with updated highlighting
                staticLayout = StaticLayout.Builder
                    .obtain(updatedSpannable, 0, updatedSpannable.length, textPaint, textWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.0f)
                    .setIncludePad(true)
                    .build()
                
                lastHighlightRange = highlightedWordRange
                return
            } else if (!highlightChanged) {
                // Complete cache hit - no changes needed
                return
            }
        }

        // Build spannable text with furigana spans (exclude highlighting for caching)
        val baseSpannable = buildSpannableTextForLayout()
        
        // Cache the base spannable for highlighting
        cachedSpannableForHighlight = SpannableStringBuilder(baseSpannable)
        
        // Apply current highlighting if needed
        val finalSpannable = applyHighlightingToSpannable(baseSpannable)
        spannableText = finalSpannable

        // Create StaticLayout with the spannable text
        staticLayout = StaticLayout.Builder
            .obtain(finalSpannable, 0, finalSpannable.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f) // Let spans handle spacing
            .setIncludePad(true)
            .build()

        // Cache the current state
        lastLayoutWidth = textWidth
        lastLayoutHash = currentHash
        lastHighlightRange = highlightedWordRange

        // Build character bounds for selection (only if needed for text selection)
        val shouldBuildBounds = characterBounds.isEmpty() || isDragging
        if (shouldBuildBounds) {
            buildCharacterBounds(width)
        }
    }

    /**
     * Build a SpannableStringBuilder for layout creation (excludes highlighting for better caching)
     */
    private fun buildSpannableTextForLayout(): SpannableStringBuilder {
        val spannable = SpannableStringBuilder()
        
        // Grey text paint for non-word card text (grey in light mode, black in dark mode)
        val greyTextPaint = TextPaint().apply {
            textSize = textPaint.textSize
            color = ContextCompat.getColor(context, R.color.subtitle_text_color)
        }
        
        if (!showFurigana || furiganaText == null) {
            // No furigana - build plain text with grey styling and highlighting
            spannable.append(displayText)
            applyGreyStylingToPlainText(spannable)
            
            // Apply highlighting if needed
            val highlightRange = highlightedWordRange
            if (highlightRange != null && 
                highlightRange.first >= 0 && highlightRange.second <= spannable.length &&
                highlightRange.first < highlightRange.second) {
                    
                // Use custom span to match the bold styling of furigana mode
                val highlightSpan = object : ReplacementSpan() {
                    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
                        return highlightedTextPaint.measureText(text, start, end).toInt()
                    }
                    
                    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
                        if (text != null) {
                            canvas.drawText(text, start, end, x, y.toFloat(), highlightedTextPaint)
                        }
                    }
                }
                
                spannable.setSpan(
                    highlightSpan,
                    highlightRange.first,
                    highlightRange.second,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            return spannable
        }

        // Build text with furigana spans
        val segments = furiganaText?.segments ?: listOf(
            FuriganaSegment(displayText, null, false, 0, displayText.length)
        )

        var currentPos = 0
        
        for (segment in segments) {
            val segmentStart = spannable.length
            spannable.append(segment.text)
            val segmentEnd = spannable.length

            if (segment.furigana != null && segment.isKanji) {
                // Determine if this segment should be grey (not in word positions)
                val isInWordCard = wordPositions?.any { (start, end) ->
                    val segmentActualStart = currentPos
                    val segmentActualEnd = currentPos + segment.text.length
                    segmentActualStart < end && segmentActualEnd > start
                } ?: true

                // Check if this segment should be highlighted
                val segmentHighlightRange = highlightedWordRange
                val segmentTextStart = currentPos
                val segmentTextEnd = currentPos + segment.text.length
                val isHighlighted = segmentHighlightRange != null && 
                                    segmentTextStart < segmentHighlightRange.second && 
                                    segmentTextEnd > segmentHighlightRange.first
                
                val furiganaSpan = FuriganaReplacementSpan(
                    segment.text,
                    segment.furigana,
                    if (isHighlighted) highlightedTextPaint else textPaint,
                    furiganaPaint,
                    if (!isInWordCard && !isHighlighted) greyTextPaint else null,
                    currentPos,
                    wordPositions ?: emptyList()
                )

                spannable.setSpan(
                    furiganaSpan,
                    segmentStart,
                    segmentEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                // Apply styling for non-kanji segments (highlighting takes priority over grey)
                val isInWordCard = wordPositions?.any { (start, end) ->
                    val segmentActualStart = currentPos
                    val segmentActualEnd = currentPos + segment.text.length
                    segmentActualStart < end && segmentActualEnd > start
                } ?: true

                // Check if this segment should be highlighted
                val segmentHighlightRange = highlightedWordRange
                val segmentTextStart = currentPos
                val segmentTextEnd = currentPos + segment.text.length
                val isHighlighted = segmentHighlightRange != null && 
                                    segmentTextStart < segmentHighlightRange.second && 
                                    segmentTextEnd > segmentHighlightRange.first

                if (isHighlighted) {
                    // Apply highlighting with bold styling to match furigana spans
                    val highlightSpan = object : ReplacementSpan() {
                        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
                            return highlightedTextPaint.measureText(text, start, end).toInt()
                        }
                        
                        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
                            if (text != null) {
                                canvas.drawText(text, start, end, x, y.toFloat(), highlightedTextPaint)
                            }
                        }
                    }
                    spannable.setSpan(highlightSpan, segmentStart, segmentEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else if (!isInWordCard && wordPositions?.isNotEmpty() == true) {
                    // Apply grey styling for non-word card text (grey in light mode, black in dark mode)
                    val greySpan = ForegroundColorSpan(ContextCompat.getColor(context, R.color.subtitle_text_color))
                    spannable.setSpan(greySpan, segmentStart, segmentEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            
            currentPos += segment.text.length
        }
        
        return spannable
    }

    /**
     * Build a SpannableStringBuilder with FuriganaReplacementSpans for kanji segments
     */
    private fun buildSpannableText(): SpannableStringBuilder {
        val spannable = SpannableStringBuilder()
        
        if (!showFurigana || furiganaText == null) {
            // No furigana - build plain text with grey styling and highlighting
            spannable.append(displayText)
            applyGreyStylingToPlainText(spannable)
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
                    textPaint = getTextPaintForSegment(currentPos, segment.text.length),
                    furiganaPaint = furiganaPaint,
                    greyTextPaint = greyTextPaint,
                    startPosition = currentPos,
                    wordPositions = wordPositions
                )
                
                spannable.setSpan(
                    span,
                    segmentStart,
                    spannable.length,
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                // Add plain text without furigana, but check if it needs highlighting or grey styling
                val segmentStart = spannable.length
                spannable.append(segment.text)
                
                // Determine which paint to use
                val paintToUse = getTextPaintForSegment(currentPos, segment.text.length)
                
                // Apply appropriate styling based on paint type
                if (paintToUse == highlightedTextPaint || paintToUse == greyTextPaint) {
                    val styledSpan = object : ReplacementSpan() {
                        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
                            return paintToUse.measureText(text, start, end).toInt()
                        }
                        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
                            if (text != null) {
                                canvas.drawText(text, start, end, x, y.toFloat(), paintToUse)
                            }
                        }
                    }
                    spannable.setSpan(styledSpan, segmentStart, spannable.length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
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
        if (highlightRange.first >= highlightRange.second) return // Invalid range: start >= end
        
        // In furigana mode, highlighting is handled by the furigana spans themselves
        // using getTextPaintForPosition() - no additional span needed
        if (showFurigana && furiganaText != null) {
            return
        }
        
        // For non-furigana mode, create a custom highlighting span
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
        
        spannable.setSpan(
            highlightSpan,
            highlightRange.first,
            highlightRange.second,
            SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    /**
     * Get the appropriate text paint for a character position (for highlighting and grey styling)
     */
    private fun getTextPaintForPosition(position: Int): TextPaint {
        val highlightRange = highlightedWordRange
        
        // Check if position is highlighted (takes priority)
        if (highlightRange != null && position >= highlightRange.first && position < highlightRange.second) {
            return highlightedTextPaint
        }
        
        // Check if position is covered by word cards
        if (isPositionCoveredByWordCard(position)) {
            return textPaint // Normal black text
        } else {
            return greyTextPaint // Grey text for non-word card content
        }
    }
    
    /**
     * Check if a text position is covered by any word card
     */
    private fun isPositionCoveredByWordCard(position: Int): Boolean {
        return wordPositions.any { (start, end) -> 
            position >= start && position < end
        }
    }
    
    /**
     * Apply grey styling to plain text (non-furigana mode)
     */
    private fun applyGreyStylingToPlainText(spannable: SpannableStringBuilder) {
        if (wordPositions.isEmpty()) return
        
        val textLength = spannable.length
        
        // Filter out positions that are out of bounds and sort them
        val validPositions = wordPositions
            .filter { (start, end) -> start >= 0 && end <= textLength && start < end }
            .sortedBy { it.first }
        
        if (validPositions.isEmpty()) return
        
        // Create spans for each character range
        var currentPos = 0
        
        for ((start, end) in validPositions) {
            // Ensure positions are within bounds
            val clampedStart = start.coerceAtLeast(0).coerceAtMost(textLength)
            val clampedEnd = end.coerceAtLeast(0).coerceAtMost(textLength)
            
            if (clampedStart >= clampedEnd) continue
            
            // Add grey span for text before this word card (if any)
            if (currentPos < clampedStart) {
                val spanStart = currentPos.coerceAtLeast(0).coerceAtMost(textLength)
                val spanEnd = clampedStart.coerceAtLeast(spanStart).coerceAtMost(textLength)
                
                if (spanStart < spanEnd) {
                    val greySpan = object : ReplacementSpan() {
                        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
                            return greyTextPaint.measureText(text, start, end).toInt()
                        }
                        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
                            if (text != null) {
                                canvas.drawText(text, start, end, x, y.toFloat(), greyTextPaint)
                            }
                        }
                    }
                    spannable.setSpan(greySpan, spanStart, spanEnd, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            currentPos = maxOf(currentPos, clampedEnd)
        }
        
        // Add grey span for remaining text after last word card (if any)
        if (currentPos < textLength) {
            val spanStart = currentPos.coerceAtLeast(0).coerceAtMost(textLength)
            val spanEnd = textLength
            
            if (spanStart < spanEnd) {
                val greySpan = object : ReplacementSpan() {
                    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
                        return greyTextPaint.measureText(text, start, end).toInt()
                    }
                    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
                        if (text != null) {
                            canvas.drawText(text, start, end, x, y.toFloat(), greyTextPaint)
                        }
                    }
                }
                spannable.setSpan(greySpan, spanStart, spanEnd, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
    
    /**
     * Get the appropriate text paint for a segment (checks for highlighting and grey styling)
     */
    private fun getTextPaintForSegment(startPos: Int, length: Int): TextPaint {
        val highlightRange = highlightedWordRange
        val segmentEnd = startPos + length
        
        // Check if segment overlaps with highlight range (takes priority)
        if (highlightRange != null) {
            val overlaps = startPos < highlightRange.second && segmentEnd > highlightRange.first
            if (overlaps) {
                return highlightedTextPaint
            }
        }
        
        // Check if segment is covered by word cards
        val isCovered = wordPositions.any { (start, end) ->
            // Check if segment overlaps with any word position
            start < segmentEnd && end > startPos
        }
        
        return if (isCovered) {
            textPaint // Normal black text
        } else {
            greyTextPaint // Grey text for non-word card content
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
        
        // Word highlighting is now applied through spans when needed
        
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

    /**
     * Apply word highlighting to a spannable text using blue text color
     * Note: buildSpannableTextForLayout now handles highlighting internally,
     * so this method just rebuilds with highlighting included
     */
    private fun applyHighlightingToSpannable(baseSpannable: SpannableStringBuilder): SpannableStringBuilder {
        // Since buildSpannableTextForLayout now includes highlighting,
        // we need to rebuild from scratch with the current highlight range
        return buildSpannableTextForLayout()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Cancel any ongoing processing
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