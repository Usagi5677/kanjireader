// Create a new file: FocusIndicatorView.kt
package com.example.kanjireader

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class FocusIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var focusX = 0f
    private var focusY = 0f
    private var isVisible = false

    private val focusPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f // Made thicker for better visibility
        color = 0xFFFF8C00.toInt() // Yellow-Orange color (Dark Orange)
        isAntiAlias = true
    }

    private val cornerLength = 30f
    private val squareSize = 120f

    fun showFocusIndicator(x: Float, y: Float) {
        android.util.Log.d("FocusIndicator", "showFocusIndicator called with x=$x, y=$y")

        focusX = x
        focusY = y
        isVisible = true

        // Make sure view is visible immediately
        visibility = View.VISIBLE
        alpha = 1f

        // Set pivot point to the focus location for centered scaling
        pivotX = x
        pivotY = y

        // Start small and expand
        scaleX = 0.5f
        scaleY = 0.5f

        android.util.Log.d("FocusIndicator", "View visibility set to VISIBLE, calling invalidate()")

        // Force immediate redraw
        invalidate()

        // Start expand animation
        animate()
            .scaleX(1.2f)  // Expand slightly beyond normal size
            .scaleY(1.2f)
            .setDuration(200)
            .withEndAction {
                // Contract back to normal size
                animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .withEndAction {
                        // Auto-hide after 1.5 seconds
                        postDelayed({
                            hideFocusIndicator()
                        }, 1500)
                    }
                    .start()
            }
            .start()
    }

    fun hideFocusIndicator() {
        android.util.Log.d("FocusIndicator", "hideFocusIndicator called")

        animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction {
                isVisible = false
                visibility = View.GONE
            }
            .start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        android.util.Log.d("FocusIndicator", "onDraw called - isVisible=$isVisible, focusX=$focusX, focusY=$focusY")

        if (!isVisible) return

        val halfSize = squareSize / 2f
        val left = focusX - halfSize
        val top = focusY - halfSize
        val right = focusX + halfSize
        val bottom = focusY + halfSize

        android.util.Log.d("FocusIndicator", "Drawing focus square at left=$left, top=$top, right=$right, bottom=$bottom")

        // Draw corner brackets
        // Top-left corner
        canvas.drawLine(left, top, left + cornerLength, top, focusPaint)
        canvas.drawLine(left, top, left, top + cornerLength, focusPaint)

        // Top-right corner
        canvas.drawLine(right - cornerLength, top, right, top, focusPaint)
        canvas.drawLine(right, top, right, top + cornerLength, focusPaint)

        // Bottom-left corner
        canvas.drawLine(left, bottom - cornerLength, left, bottom, focusPaint)
        canvas.drawLine(left, bottom, left + cornerLength, bottom, focusPaint)

        // Bottom-right corner
        canvas.drawLine(right - cornerLength, bottom, right, bottom, focusPaint)
        canvas.drawLine(right, bottom - cornerLength, right, bottom, focusPaint)

        // Center dot
        canvas.drawCircle(focusX, focusY, 4f, focusPaint)

        android.util.Log.d("FocusIndicator", "Focus square drawn successfully")
    }
}