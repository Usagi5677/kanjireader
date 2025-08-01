package com.example.kanjireader

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.PictureDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.caverock.androidsvg.SVG
import com.example.kanjireader.KanjiDetailAdapter.StrokeData
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.JustifyContent

class StrokeProgressionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FlexboxLayout(context, attrs, defStyleAttr) {

    init {
        flexWrap = FlexWrap.WRAP
        justifyContent = JustifyContent.FLEX_START
        setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
    }

    fun setStrokeData(strokes: List<StrokeData>, kanjiChar: String) {
        removeAllViews()

        // Create progressive stroke diagrams
        for (i in strokes.indices) {
            val strokeView = createStrokeView(strokes, i + 1, kanjiChar)
            addView(strokeView)
        }
    }

    private fun createStrokeView(allStrokes: List<StrokeData>, upToStroke: Int, kanjiChar: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
            }

            // Stroke number label
            val label = TextView(context).apply {
                text = upToStroke.toString()
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                setPadding(0, 0, 0, 2.dpToPx())
                gravity = Gravity.CENTER
            }
            addView(label)

            // SVG image showing strokes up to this point
            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(72.dpToPx(), 72.dpToPx())
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
            }

            // Create SVG with progressive strokes
            val svgString = createProgressiveSVG(allStrokes, upToStroke)
            try {
                val svg = SVG.getFromString(svgString)
                svg.documentWidth = 72f
                svg.documentHeight = 72f
                val picture = svg.renderToPicture()
                val drawable = PictureDrawable(picture)
                imageView.setImageDrawable(drawable)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            addView(imageView)
        }
    }

    private fun createProgressiveSVG(allStrokes: List<StrokeData>, upToStroke: Int): String {
        val viewBox = allStrokes.firstOrNull()?.viewBox ?: "0 0 109 109"

        // All strokes up to current in black
        val strokePaths = allStrokes.take(upToStroke).joinToString("\n") { stroke ->
            """<path d="${stroke.path}" fill="none" stroke="black" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>"""
        }

        // Optionally highlight just the newest stroke in red
        val currentStrokePath = if (upToStroke > 0 && upToStroke <= allStrokes.size) {
            val currentStroke = allStrokes[upToStroke - 1]
            """<path d="${currentStroke.path}" fill="none" stroke="#FF0000" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" opacity="0.6"/>"""
        } else ""

        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="$viewBox">
                <g>
                    $strokePaths
                    $currentStrokePath
                </g>
            </svg>
        """.trimIndent()
    }

    private fun Int.dpToPx(): Int = (this * context.resources.displayMetrics.density).toInt()
}