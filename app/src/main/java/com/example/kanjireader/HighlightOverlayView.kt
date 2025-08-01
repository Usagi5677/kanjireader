package com.example.kanjireader

import android.graphics.*

// Extension function for easier RoundRect drawing
private fun Canvas.drawRoundRect(
    left: Float, top: Float, right: Float, bottom: Float,
    rx: Float, ry: Float, paint: Paint
) {
    val rectF = RectF(left, top, right, bottom)
    drawRoundRect(rectF, rx, ry, paint)
}