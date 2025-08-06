package com.example.kanjireader

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "DrawingView"
        private const val STROKE_WIDTH = 12f
        private const val EXPORT_SIZE = 224 // Size for model input
    }

    // Drawing state
    private var drawPath = Path()
    private var drawPaint = Paint()
    private var canvasPaint = Paint(Paint.DITHER_FLAG)
    private var drawCanvas: Canvas? = null
    private var canvasBitmap: Bitmap? = null
    
    // Track all drawn paths for clearing
    private val drawnPaths = mutableListOf<Path>()
    private val pathPaints = mutableListOf<Paint>()
    
    // Background color
    private val backgroundColor = Color.WHITE
    
    // Guide lines
    private var guidePaint = Paint()
    private var showGuideLines = true
    
    // Callbacks for drawing events
    var onDrawingChanged: (() -> Unit)? = null
    var onStrokeCompleted: (() -> Unit)? = null

    init {
        setupDrawing()
    }

    private fun setupDrawing() {
        drawPaint.apply {
            color = Color.BLACK
            isAntiAlias = true
            strokeWidth = STROKE_WIDTH
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        
        // Setup guide lines paint
        guidePaint.apply {
            color = Color.GRAY
            strokeWidth = 2f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f) // More visible dotted line
            isAntiAlias = true
            alpha = 128 // Semi-transparent
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawCanvas = Canvas(canvasBitmap!!)
        drawCanvas!!.drawColor(backgroundColor)
        
        // Draw initial guide lines to the bitmap canvas
        drawGuideLinesToBitmap()
        
        Log.d(TAG, "Canvas size changed to: ${w}x${h}")
    }
    
    private fun drawGuideLinesToBitmap() {
        if (drawCanvas != null) {
            val centerX = width / 2f
            val centerY = height / 2f
            
            drawCanvas!!.drawLine(centerX, 0f, centerX, height.toFloat(), guidePaint)
            drawCanvas!!.drawLine(0f, centerY, width.toFloat(), centerY, guidePaint)
            
            Log.d(TAG, "Guide lines drawn to bitmap canvas at center: $centerX, $centerY")
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        Log.d(TAG, "onDraw called - drawPath empty: ${drawPath.isEmpty}")
        
        // Draw the bitmap containing all previous strokes
        canvasBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, 0f, 0f, canvasPaint)
        }
        
        // Draw guide lines BEFORE the current path so they appear underneath user drawing
        val centerX = width / 2f
        val centerY = height / 2f
        
        // Draw guide lines to view canvas
        canvas.drawLine(centerX, 0f, centerX, height.toFloat(), guidePaint)
        canvas.drawLine(0f, centerY, width.toFloat(), centerY, guidePaint)
        
        Log.d(TAG, "Guide lines drawn to view canvas at center: $centerX, $centerY")
        
        // Draw the current path being drawn (on top of guide lines)
        canvas.drawPath(drawPath, drawPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val touchX = event.x
        val touchY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "ACTION_DOWN")
                drawPath.moveTo(touchX, touchY)
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                drawPath.lineTo(touchX, touchY)
            }
            
            MotionEvent.ACTION_UP -> {
                Log.d(TAG, "ACTION_UP - completing stroke")
                
                // Draw the completed path to the canvas bitmap
                drawCanvas?.drawPath(drawPath, drawPaint)
                
                // Store the path for potential clearing/undo
                drawnPaths.add(Path(drawPath))
                pathPaints.add(Paint(drawPaint))
                
                // Reset current path
                drawPath.reset()
                
                // Notify that drawing has changed
                onDrawingChanged?.invoke()
                
                // Trigger auto-recognition after stroke completion
                onStrokeCompleted?.invoke()
                
                // Trigger redraw
                invalidate()
            }
            
            else -> return false
        }

        // Trigger a redraw
        invalidate()
        return true
    }

    /**
     * Undo the last stroke
     */
    fun undoLastStroke() {
        if (drawnPaths.isEmpty()) {
            Log.d(TAG, "Nothing to undo")
            return
        }
        
        // Remove the last path and paint
        drawnPaths.removeLastOrNull()
        pathPaints.removeLastOrNull()
        
        // Redraw all remaining paths
        redrawCanvas()
        
        Log.d(TAG, "Undid last stroke. Remaining paths: ${drawnPaths.size}")
    }
    
    /**
     * Check if undo is available
     */
    fun canUndo(): Boolean {
        return drawnPaths.isNotEmpty()
    }
    
    /**
     * Clear all drawing
     */
    fun clearDrawing() {
        drawnPaths.clear()
        pathPaints.clear()
        drawPath.reset()
        
        // Clear the canvas
        canvasBitmap?.let { bitmap ->
            drawCanvas?.drawColor(backgroundColor, PorterDuff.Mode.CLEAR)
            drawCanvas?.drawColor(backgroundColor)
        }
        
        // Redraw guide lines to bitmap
        drawGuideLinesToBitmap()
        
        invalidate()
        onDrawingChanged?.invoke()
        
        Log.d(TAG, "Drawing cleared")
    }
    
    /**
     * Redraw the entire canvas with current paths
     */
    private fun redrawCanvas() {
        canvasBitmap?.let { bitmap ->
            // Clear the canvas
            drawCanvas?.drawColor(backgroundColor, PorterDuff.Mode.CLEAR)
            drawCanvas?.drawColor(backgroundColor)
            
            // Always draw guide lines to bitmap
            drawGuideLinesToBitmap()
            Log.d(TAG, "redrawCanvas: Guide lines always shown")
            
            // Redraw all stored paths
            for (i in drawnPaths.indices) {
                drawCanvas?.drawPath(drawnPaths[i], pathPaints[i])
            }
            
            invalidate()
            onDrawingChanged?.invoke()
        }
    }

    /**
     * Check if there's any drawing on the canvas
     */
    fun hasDrawing(): Boolean {
        return drawnPaths.isNotEmpty()
    }

    /**
     * Get the drawing as a bitmap suitable for model input
     */
    fun getDrawingBitmap(): Bitmap? {
        if (!hasDrawing()) {
            Log.w(TAG, "No drawing to export")
            return null
        }

        return try {
            val originalBitmap = canvasBitmap ?: return null
            
            // Create a square bitmap centered on the drawing
            val size = minOf(originalBitmap.width, originalBitmap.height)
            val x = (originalBitmap.width - size) / 2
            val y = (originalBitmap.height - size) / 2
            
            // Extract square region
            val squareBitmap = Bitmap.createBitmap(originalBitmap, x, y, size, size)
            
            // Scale to model input size
            val scaledBitmap = Bitmap.createScaledBitmap(squareBitmap, EXPORT_SIZE, EXPORT_SIZE, true)
            
            // Convert to grayscale if needed (model expects grayscale)
            val grayscaleBitmap = convertToGrayscale(scaledBitmap)
            
            // Clean up intermediate bitmaps
            if (scaledBitmap != grayscaleBitmap) {
                scaledBitmap.recycle()
            }
            if (squareBitmap != originalBitmap) {
                squareBitmap.recycle()
            }
            
            Log.d(TAG, "Drawing bitmap exported as ${EXPORT_SIZE}x${EXPORT_SIZE}")
            grayscaleBitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export drawing bitmap", e)
            null
        }
    }

    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        val grayscaleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscaleBitmap)
        
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f) // Convert to grayscale
        }
        
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayscaleBitmap
    }

    /**
     * Get drawing bounds for centering
     */
    private fun getDrawingBounds(): RectF {
        val bounds = RectF()
        if (drawnPaths.isNotEmpty()) {
            drawnPaths.forEach { path ->
                val pathBounds = RectF()
                path.computeBounds(pathBounds, true)
                bounds.union(pathBounds)
            }
        }
        return bounds
    }
}