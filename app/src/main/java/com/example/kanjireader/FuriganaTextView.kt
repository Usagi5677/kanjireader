package com.example.kanjireader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat


class FuriganaTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val FURIGANA_SIZE_RATIO = 0.5f // Furigana is 50% of main text size
        private const val FURIGANA_SPACING = 4f // Space between furigana and main text
        private const val TOP_PADDING = 8f // Padding at top for furigana
        private const val SIDE_PADDING = 16f
    }

    private var textPaint: TextPaint
    private var furiganaPaint: TextPaint
    private var staticLayout: StaticLayout? = null
    
    private var displayText = ""
    private var furiganaSegments: List<FuriganaSegment> = emptyList()
    private var searchWord: String? = null
    private var highlightColor: Int = 0
    private var textSize = 16f
    
    init {
        textPaint = TextPaint().apply {
            textSize = this@FuriganaTextView.textSize
            color = ContextCompat.getColor(context, android.R.color.black)
            isAntiAlias = true
        }

        furiganaPaint = TextPaint().apply {
            textSize = this@FuriganaTextView.textSize * FURIGANA_SIZE_RATIO
            color = ContextCompat.getColor(context, android.R.color.black)
            isAntiAlias = true
        }
    }
    
    fun setText(text: String, segments: List<FuriganaSegment>? = null, textSizeSp: Float = 16f, searchWord: String? = null) {
        displayText = text
        this.searchWord = searchWord
        furiganaSegments = segments ?: emptyList()
        
        // Log segment information for debugging alignment
        Log.d("FuriganaTextView", "setText: text='${text.take(30)}${if (text.length > 30) "..." else ""}', segments=${segments?.size ?: 0}")
        segments?.forEachIndexed { index, segment ->
            Log.d("FuriganaTextView", "Segment $index: '${segment.text}' [${segment.startIndex}-${segment.endIndex}] furigana='${segment.furigana}' isKanji=${segment.isKanji}")
        }
        
        // Log only if there's a search word to help debug highlighting issues
        if (searchWord != null && searchWord.isNotEmpty()) {
            Log.d("FuriganaTextView", "setText with searchWord: '$searchWord' in text: '${text.take(50)}${if (text.length > 50) "..." else ""}'")
        }
        
        // Set highlight color - using blue for production
        highlightColor = ContextCompat.getColor(context, R.color.blue_600)
        
        // Update text size
        textSize = textSizeSp * resources.displayMetrics.scaledDensity
        textPaint.textSize = textSize
        furiganaPaint.textSize = textSize * FURIGANA_SIZE_RATIO
        
        staticLayout = null
        requestLayout()
    }
    
    fun setTextColor(color: Int) {
        textPaint.color = color
        furiganaPaint.color = color
        invalidate()
    }
    
    fun setTextStyle(style: Int) {
        textPaint.typeface = Typeface.defaultFromStyle(style)
        furiganaPaint.typeface = Typeface.defaultFromStyle(style)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val availableWidth = width - (SIDE_PADDING * 2).toInt()
        
        if (displayText.isNotEmpty() && availableWidth > 0) {
            // Calculate line spacing based on whether we have furigana
            val lineSpacingExtra = if (furiganaSegments.any { it.furigana != null && it.isKanji }) {
                furiganaPaint.textSize + FURIGANA_SPACING * 2 // Extra space for furigana
            } else {
                4f // Normal line spacing
            }
            
            staticLayout = StaticLayout.Builder
                .obtain(displayText, 0, displayText.length, textPaint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(lineSpacingExtra, 1.0f)
                .setIncludePad(false)
                .build()
        }
        
        val layout = staticLayout
        val baseHeight = if (layout != null) {
            layout.height + (SIDE_PADDING * 2).toInt()
        } else {
            (textSize + SIDE_PADDING * 2).toInt()
        }
        
        // Add extra top padding for first line furigana if we have furigana
        val extraTopHeight = if (furiganaSegments.any { it.furigana != null && it.isKanji }) {
            (furiganaPaint.textSize + FURIGANA_SPACING + TOP_PADDING).toInt()
        } else {
            0
        }
        
        setMeasuredDimension(width, baseHeight + extraTopHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val layout = staticLayout ?: return
        
        canvas.save()
        
        val topOffset = if (furiganaSegments.any { it.furigana != null && it.isKanji }) {
            TOP_PADDING + furiganaPaint.textSize + FURIGANA_SPACING
        } else {
            SIDE_PADDING
        }
        
        canvas.translate(SIDE_PADDING, topOffset)
        
        if (furiganaSegments.isNotEmpty()) {
            drawTextWithFurigana(canvas, layout)
        } else {
            drawTextWithHighlighting(canvas, layout)
        }
        
        canvas.restore()
    }
    
    private fun drawTextWithFurigana(canvas: Canvas, layout: StaticLayout) {
        // Group segments by their position to handle overlapping text properly
        val textDrawn = BooleanArray(displayText.length) { false }
        
        // First pass: draw all text segments
        for (segment in furiganaSegments) {
            val startIndex = segment.startIndex.coerceIn(0, displayText.length)
            val endIndex = segment.endIndex.coerceIn(startIndex, displayText.length)
            
            if (startIndex >= endIndex) continue
            
            val line = layout.getLineForOffset(startIndex)
            val x = layout.getPrimaryHorizontal(startIndex)
            val baselineY = layout.getLineBaseline(line).toFloat()
            
            // Draw the text segment with highlighting if it matches search word
            val segmentText = displayText.substring(startIndex, endIndex)
            val paintToUse = if (shouldHighlightSegment(startIndex, endIndex)) {
                getHighlightPaint()
            } else {
                textPaint
            }
            canvas.drawText(segmentText, x, baselineY, paintToUse)
            
            // Mark this text as drawn
            for (i in startIndex until endIndex) {
                if (i < textDrawn.size) textDrawn[i] = true
            }
        }
        
        // Draw any remaining text that wasn't covered by segments
        for (i in displayText.indices) {
            if (!textDrawn[i]) {
                val line = layout.getLineForOffset(i)
                val x = layout.getPrimaryHorizontal(i)
                val baselineY = layout.getLineBaseline(line).toFloat()
                val charText = displayText[i].toString()
                val paintToUse = if (shouldHighlightSegment(i, i + 1)) {
                    getHighlightPaint()
                } else {
                    textPaint
                }
                canvas.drawText(charText, x, baselineY, paintToUse)
            }
        }
        
        // Second pass: draw furigana
        for (segment in furiganaSegments) {
            if (segment.furigana != null && segment.isKanji) {
                val startIndex = segment.startIndex.coerceIn(0, displayText.length)
                val endIndex = segment.endIndex.coerceIn(startIndex, displayText.length)
                
                if (startIndex >= endIndex) continue
                
                val line = layout.getLineForOffset(startIndex)
                val x = layout.getPrimaryHorizontal(startIndex)
                val baselineY = layout.getLineBaseline(line).toFloat()
                
                // Calculate segment width and furigana width
                val segmentText = displayText.substring(startIndex, endIndex)
                val segmentWidth = textPaint.measureText(segmentText)
                val furiganaWidth = furiganaPaint.measureText(segment.furigana)
                
                // For segments with okurigana (like 見る with furigana み), 
                // center furigana over just the kanji part, not the entire segment
                val furiganaX = if (segmentText.length > 1 && containsKanji(segmentText)) {
                    // Find kanji part of the segment
                    val kanjiEndIndex = segmentText.indexOfLast { containsKanji(it.toString()) }
                    if (kanjiEndIndex >= 0) {
                        val kanjiPart = segmentText.substring(0, kanjiEndIndex + 1)
                        val kanjiWidth = textPaint.measureText(kanjiPart)
                        // Center furigana over just the kanji part
                        x + (kanjiWidth - furiganaWidth) / 2
                    } else {
                        // Fallback to centering over entire segment
                        x + (segmentWidth - furiganaWidth) / 2
                    }
                } else {
                    // Center furigana over the entire segment (normal case)
                    x + (segmentWidth - furiganaWidth) / 2
                }
                // ascent() is negative, so we add it to go up from baseline, then subtract spacing
                val furiganaY = baselineY + textPaint.ascent() - FURIGANA_SPACING
                
                canvas.drawText(segment.furigana, furiganaX, furiganaY, furiganaPaint)
            }
        }
    }
    
    private fun drawTextWithHighlighting(canvas: Canvas, layout: StaticLayout) {
        val word = searchWord
        if (word == null || word.isEmpty()) {
            // No search word, draw normally
            layout.draw(canvas)
            return
        }
        
        // Draw text character by character with highlighting
        for (i in displayText.indices) {
            val line = layout.getLineForOffset(i)
            val x = layout.getPrimaryHorizontal(i)
            val baselineY = layout.getLineBaseline(line).toFloat()
            val charText = displayText[i].toString()
            
            // Check if this character should be highlighted
            val paintToUse = if (shouldHighlightSegment(i, i + 1)) {
                getHighlightPaint()
            } else {
                textPaint
            }
            
            canvas.drawText(charText, x, baselineY, paintToUse)
        }
    }
    
    
    private fun shouldHighlightSegment(startIndex: Int, endIndex: Int): Boolean {
        val word = searchWord
        if (word == null || word.isEmpty()) return false
        
        val segmentText = displayText.substring(startIndex, endIndex)
        
        // Check if this segment contains the entire search word
        if (segmentText.contains(word)) {
            Log.d("FuriganaTextView", "✓ Highlighting segment '$segmentText' (contains entire word '$word')")
            return true
        }
        
        // Check if this segment is part of the search word at the correct position
        // Find all occurrences of the search word in the full text
        var searchIndex = 0
        while (searchIndex < displayText.length) {
            val foundIndex = displayText.indexOf(word, searchIndex)
            if (foundIndex == -1) break
            
            val wordEndIndex = foundIndex + word.length
            
            // Check if this segment overlaps with the found word
            if (startIndex < wordEndIndex && endIndex > foundIndex) {
                // The segment overlaps with an actual occurrence of the search word
                Log.d("FuriganaTextView", "✓ Highlighting segment '$segmentText' (part of word '$word' at position $foundIndex)")
                return true
            }
            
            searchIndex = foundIndex + 1
        }
        
        return false
    }
    
    
    private fun getHighlightPaint(): TextPaint {
        val highlightPaint = TextPaint(textPaint)
        highlightPaint.color = highlightColor
        return highlightPaint
    }
    
    /**
     * Check if text contains kanji characters
     */
    private fun containsKanji(text: String): Boolean {
        return text.any { char ->
            val codePoint = char.code
            (codePoint in 0x4E00..0x9FAF) || (codePoint in 0x3400..0x4DBF)
        }
    }
    
}