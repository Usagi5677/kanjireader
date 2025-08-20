package com.example.kanjireader

import android.content.Context
import android.graphics.*
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.min

/**
 * Custom view for displaying Japanese pitch accent as a line graph
 * 
 * Shows pitch accent patterns as a line drawn over hiragana text:
 * - Heiban (0): Low-High, stays high
 * - Atama (1): High-Low drop after first mora
 * - Nakadaka (2+): Low-High-Low, drops after specified mora
 */
class PitchAccentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    companion object {
        private const val TEXT_SIZE = 16f
        private const val VERTICAL_PADDING_TOP = 1f // Minimal top padding to reduce gap with title
        private const val VERTICAL_PADDING_BOTTOM = 8f // Increased bottom padding for full bottom border visibility
        private const val HORIZONTAL_PADDING = 12f
        private const val LINE_THICKNESS = 2f
        private const val PITCH_LINE_HEIGHT = 2f // Height difference between high and low pitch
        private const val BORDER_SPACING = 2f // Space between borders and text
    }
    
    private var pitchAccents: List<PitchAccent>? = null
    private var reading: String = ""
    
    // Paint objects
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF757575") // Grey color similar to subtitle/secondary text
        textSize = TEXT_SIZE * resources.displayMetrics.scaledDensity
        typeface = Typeface.DEFAULT
    }
    
    private val pitchLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3") // Blue color
        strokeWidth = LINE_THICKNESS * resources.displayMetrics.density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    
    private var searchQuery: String? = null
    
    /**
     * Set the pitch accent data to display
     */
    fun setPitchAccents(accents: List<PitchAccent>?, readingText: String, query: String? = null) {
        pitchAccents = accents
        reading = readingText
        searchQuery = query
        
        // Request layout and redraw
        requestLayout()
        invalidate()
    }
    
    /**
     * Get mora list from Japanese text
     * Simplified mora counting - treats each hiragana character as one mora
     */
    private fun getMoraList(text: String): List<String> {
        val morae = mutableListOf<String>()
        var i = 0
        
        while (i < text.length) {
            val char = text[i]
            
            // Check for small characters that combine with the previous mora
            if (i + 1 < text.length) {
                val nextChar = text[i + 1]
                if (nextChar in "ゃゅょ") {
                    // Combine current character with small ya/yu/yo
                    morae.add("$char$nextChar")
                    i += 2
                    continue
                }
            }
            
            // Regular character
            morae.add(char.toString())
            i++
        }
        
        return morae
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (pitchAccents.isNullOrEmpty() || reading.isEmpty()) {
            // No pitch accent data - use minimal size
            val minWidth = (HORIZONTAL_PADDING * 2 * resources.displayMetrics.density).toInt()
            val minHeight = (TEXT_SIZE * resources.displayMetrics.scaledDensity + (VERTICAL_PADDING_TOP + VERTICAL_PADDING_BOTTOM) * resources.displayMetrics.density).toInt()
            setMeasuredDimension(minWidth, minHeight)
            return
        }
        
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        
        // Calculate required width based on reading text
        val readingWidth = textPaint.measureText(reading)
        val paddingWidth = HORIZONTAL_PADDING * 2 * resources.displayMetrics.density
        val requiredWidth = (readingWidth + paddingWidth).toInt()
        
        // Calculate required height (text + pitch line space + border spacing + descent)
        val textHeight = textPaint.textSize
        val textDescent = textPaint.descent()
        val pitchLineSpace = PITCH_LINE_HEIGHT * resources.displayMetrics.density
        val borderSpacing = BORDER_SPACING * 2 * resources.displayMetrics.density // Top and bottom spacing
        val paddingHeight = (VERTICAL_PADDING_TOP + VERTICAL_PADDING_BOTTOM) * resources.displayMetrics.density
        val requiredHeight = (textHeight + textDescent + pitchLineSpace + borderSpacing + paddingHeight).toInt()
        
        // Determine final dimensions
        val finalWidth = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> min(requiredWidth, widthSize)
            else -> requiredWidth
        }
        
        val finalHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(requiredHeight, heightSize)
            else -> requiredHeight
        }
        
        setMeasuredDimension(finalWidth, finalHeight)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val accents = pitchAccents
        if (accents.isNullOrEmpty() || reading.isEmpty()) {
            return
        }
        
        val primaryAccent = accents.first()
        // Handle comma-separated accent numbers - prefer the most common pattern (first one)
        val accentNumbers = primaryAccent.accentPattern.split(",").mapNotNull { it.trim().toIntOrNull() }
        val accentNumber = accentNumbers.firstOrNull() ?: 0
        
        drawPitchAccent(canvas, reading, accentNumber)
    }
    
    private fun drawPitchAccent(canvas: Canvas, reading: String, accentNumber: Int) {
        val morae = getMoraList(reading)
        if (morae.isEmpty()) return
        
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val density = resources.displayMetrics.density
        
        // Calculate spacing and positions
        val verticalPaddingBottom = VERTICAL_PADDING_BOTTOM * density
        
        // Position for text (bottom of view with bottom padding)
        val textY = viewHeight - verticalPaddingBottom
        
        // Calculate character positions - align first character to left edge (no padding)
        val startX = 0f
        
        // Draw the full reading text with highlighting first
        drawTextWithHighlighting(canvas, reading, startX, textY, searchQuery)
        
        // Then draw pitch accent borders over each mora
        var currentX = startX
        for (i in morae.indices) {
            val mora = morae[i]
            val moraWidth = textPaint.measureText(mora)
            
            // Draw pitch accent borders for this mora - FIXED LOGIC
            drawPitchAccentBorders(canvas, accentNumber, reading, i, currentX, moraWidth, textY, density)
            
            currentX += moraWidth
        }
    }
    
    private fun drawPitchAccentBorders(canvas: Canvas, pitchAccent: Int, reading: String, at: Int, x: Float, width: Float, textY: Float, density: Float) {
        val textHeight = textPaint.textSize
        val spacing = BORDER_SPACING * density
        
        // Calculate border positions with equal spacing around the character
        val left = x
        val right = x + width
        val top = textY - textHeight - spacing
        // Calculate bottom based on text descent to ensure equal spacing
        val textBottom = textY + textPaint.descent()
        val bottom = textBottom + spacing
        
        // Add extra spacing for vertical lines between characters
        val verticalLineSpacing = 2f * density
        val verticalLineX = right + verticalLineSpacing
        
        // Determine which borders to draw based on CORRECTED logic
        val borders = getPitchAccentBorders(pitchAccent, reading.length, at)
        
        // Draw borders
        if (borders.top) {
            canvas.drawLine(left, top, right, top, pitchLinePaint)
        }
        if (borders.bottom) {
            canvas.drawLine(left, bottom, right, bottom, pitchLinePaint)
        }
        if (borders.right) {
            // Connect vertical line properly to the horizontal lines
            canvas.drawLine(right, top, right, bottom, pitchLinePaint)
        }
    }
    
    private data class BorderConfig(
        val top: Boolean = false,
        val bottom: Boolean = false,
        val right: Boolean = false
    )
    
    private fun getPitchAccentBorders(pitchAccent: Int, readingLength: Int, at: Int): BorderConfig {
        // CORRECTED logic - flipped Heiban pattern
        
        // 平板 (Heiban) - CORRECTED: first mora HIGH, rest LOW
        if (pitchAccent == 0) {
            return if (at == 0) {
                BorderConfig(top = true, right = true) // falling (high to low)
            } else {
                BorderConfig(bottom = true) // low
            }
        }
        
        // 頭高 (Atama) - CORRECTED: first mora LOW, rest HIGH
        else if (pitchAccent == 1 && readingLength > 1) {
            return if (at == 0) {
                BorderConfig(bottom = true, right = true) // rising (low to high)
            } else {
                BorderConfig(top = true) // high
            }
        }
        
        // 中高 (Nakadaka) - drops after the specified mora 
        else if (pitchAccent > 1 && pitchAccent < readingLength) {
            return when {
                at == 0 -> BorderConfig(bottom = true, right = true) // rising from low to high
                at > 0 && at < pitchAccent - 1 -> BorderConfig(top = true) // high before drop
                at == pitchAccent - 1 -> BorderConfig(top = true, right = true) // falling at drop position
                else -> BorderConfig(bottom = true) // low after drop
            }
        }
        
        // 尾高 (Odaka) - same logic as before
        else if (pitchAccent == readingLength) {
            return when {
                at == 0 -> {
                    if (readingLength == 1) {
                        BorderConfig(top = true, right = true) // falling
                    } else {
                        BorderConfig(bottom = true, right = true) // rising
                    }
                }
                at > 0 && at < readingLength - 1 -> BorderConfig(top = true) // high
                at == readingLength - 1 -> BorderConfig(top = true, right = true) // falling
                else -> BorderConfig(bottom = true) // low
            }
        }
        
        // Default case
        return BorderConfig()
    }
    
    
    /**
     * Get the accent type description for accessibility
     */
    fun getAccentDescription(): String? {
        val accents = pitchAccents
        if (accents.isNullOrEmpty()) return null
        
        val primaryAccent = accents.first()
        val accentNumbers = primaryAccent.accentPattern.split(",").mapNotNull { it.trim().toIntOrNull() }
        val firstAccent = accentNumbers.firstOrNull() ?: 0
        
        val typeDescription = when (firstAccent) {
            0 -> "平板型 (Heiban)"
            1 -> "頭高型 (Atama)"
            else -> "中高型 (Nakadaka) $firstAccent"
        }
        
        return if (accentNumbers.size > 1) {
            "$typeDescription (Multiple: ${primaryAccent.accentPattern})"
        } else {
            typeDescription
        }
    }
    
    /**
     * Check if this view has pitch accent data to display
     */
    fun hasPitchAccent(): Boolean {
        return !pitchAccents.isNullOrEmpty()
    }
    
    /**
     * Draw text with search highlighting support
     */
    private fun drawTextWithHighlighting(canvas: Canvas, text: String, x: Float, y: Float, query: String?) {
        if (query.isNullOrBlank()) {
            // No query - draw normal grey text
            canvas.drawText(text, x, y, textPaint)
            return
        }
        
        val lowerText = text.lowercase()
        val lowerQuery = query.trim().lowercase()
        val highlightIndex = lowerText.indexOf(lowerQuery)
        
        if (highlightIndex == -1) {
            // No match - draw normal grey text
            canvas.drawText(text, x, y, textPaint)
            return
        }
        
        // Create highlighted paint
        val highlightPaint = Paint(textPaint).apply {
            color = ContextCompat.getColor(context, R.color.search_highlight_text)
            typeface = Typeface.DEFAULT_BOLD
        }
        
        // Draw text with highlighting
        var currentX = x
        
        // Before highlight
        if (highlightIndex > 0) {
            val beforeText = text.substring(0, highlightIndex)
            canvas.drawText(beforeText, currentX, y, textPaint)
            currentX += textPaint.measureText(beforeText)
        }
        
        // Highlighted portion
        val highlightText = text.substring(highlightIndex, highlightIndex + lowerQuery.length)
        canvas.drawText(highlightText, currentX, y, highlightPaint)
        currentX += highlightPaint.measureText(highlightText)
        
        // After highlight
        if (highlightIndex + lowerQuery.length < text.length) {
            val afterText = text.substring(highlightIndex + lowerQuery.length)
            canvas.drawText(afterText, currentX, y, textPaint)
        }
    }
}