package com.example.kanjireader

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.DataType
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class KanjiPrediction(
    val kanji: String,
    val confidence: Float
)

class DaKanjiRecognitionEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "DaKanjiRecognitionEngine"
        private const val MODEL_PATH = "v1.2/tflite/model.tflite"
        private const val LABELS_PATH = "v1.2/labels_python_list.txt"
        private const val INPUT_SIZE = 64 // DaKanji typically uses 64x64 input
        private const val TOP_K = 20
    }
    
    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var isInitialized = false
    
    init {
        initializeModel()
    }
    
    private fun initializeModel() {
        try {
            Log.d(TAG, "Initializing DaKanji recognition model...")
            
            // Load TensorFlow Lite model
            val modelBuffer = loadModelFile()
            interpreter = Interpreter(modelBuffer)
            
            // Load labels
            labels = loadLabels()
            
            isInitialized = true
            Log.d(TAG, "Model initialized successfully with ${labels.size} labels")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize model", e)
            isInitialized = false
        }
    }
    
    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(MODEL_PATH)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    
    private fun loadLabels(): List<String> {
        return try {
            context.assets.open(LABELS_PATH).bufferedReader().use { reader ->
                val text = reader.readText()
                Log.d(TAG, "Loaded labels file, length: ${text.length}")
                
                // Check if this is a Python list format (e.g., ['一', '二', '三'])
                if (text.trim().startsWith("[") && text.trim().endsWith("]")) {
                    // Parse as Python list
                    val listContent = text.trim().removePrefix("[").removeSuffix("]")
                    val labels = listContent.split(",").map { item ->
                        item.trim().removePrefix("'").removeSuffix("'")
                            .removePrefix("\"").removeSuffix("\"")
                    }
                    Log.d(TAG, "Parsed ${labels.size} labels from Python list format")
                    Log.d(TAG, "First 10 labels: ${labels.take(10).joinToString(", ") { "'$it'" }}")
                    labels
                } else {
                    // Parse as one character per line or all characters in one line
                    val labels = text.toCharArray().map { it.toString() }
                    Log.d(TAG, "Parsed ${labels.size} labels as character array")
                    Log.d(TAG, "First 10 labels: ${labels.take(10).joinToString(", ") { "'$it'" }}")
                    labels
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load labels", e)
            emptyList()
        }
    }
    
    fun recognize(bitmap: Bitmap): List<KanjiPrediction> {
        if (!isInitialized || interpreter == null) {
            Log.w(TAG, "Model not initialized")
            return emptyList()
        }
        
        return try {
            // Debug original model tensor shapes
            val inputTensor = interpreter!!.getInputTensor(0)
            val outputTensor = interpreter!!.getOutputTensor(0)
            Log.d(TAG, "Original input tensor shape: ${inputTensor.shape().contentToString()}, type: ${inputTensor.dataType()}")
            Log.d(TAG, "Output tensor shape: ${outputTensor.shape().contentToString()}, type: ${outputTensor.dataType()}")
            
            // Scale bitmap to 64x64 (DaKanji standard)
            val size = 64
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
            
            // Resize input tensor to correct dimensions (like HandWritingService)
            val inputShape = intArrayOf(1, size, size, 1)
            interpreter!!.resizeInput(0, inputShape)
            
            // Create input buffer with correct shape
            val inputBuffer = TensorBuffer.createFixedSize(inputShape, DataType.FLOAT32)
            
            // Extract pixels and convert to float array (similar to HandWritingService)
            val intArray = IntArray(size * size)
            scaledBitmap.getPixels(intArray, 0, size, 0, 0, size, size)
            
            val floatArray = FloatArray(size * size)
            for (i in intArray.indices) {
                // Extract alpha channel for handwriting (like HandWritingService approach)
                val alpha = (intArray[i] shr 24) and 0xFF
                if (alpha == 0) {
                    // Transparent pixels = white background
                    floatArray[i] = 1.0f
                } else {
                    // For kanji, we want black strokes on white background
                    // Convert to grayscale and normalize
                    val pixel = intArray[i]
                    val red = (pixel shr 16) and 0xFF
                    val green = (pixel shr 8) and 0xFF
                    val blue = pixel and 0xFF
                    val gray = (red * 0.299f + green * 0.587f + blue * 0.114f) / 255.0f
                    // Invert for black strokes on white background
                    floatArray[i] = 1.0f - gray
                }
            }
            
            inputBuffer.loadArray(floatArray)
            Log.d(TAG, "Input prepared: ${floatArray.size} pixels, shape: ${inputShape.contentToString()}")
            
            // Get number of outputs dynamically (like HandWritingService)
            val numOutputs = interpreter!!.getOutputTensor(0).numElements()
            val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, numOutputs), DataType.FLOAT32)
            
            // Run inference
            interpreter!!.run(inputBuffer.buffer, outputBuffer.buffer)
            
            // Process results
            processResults(outputBuffer.floatArray)
            
        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed", e)
            emptyList()
        }
    }
    
    
    
    private fun processResults(output: FloatArray): List<KanjiPrediction> {
        // Create prediction list with confidence scores
        val predictions = mutableListOf<KanjiPrediction>()
        
        for (i in output.indices) {
            if (i < labels.size) {
                predictions.add(KanjiPrediction(labels[i], output[i]))
            }
        }
        
        // Sort by confidence descending and take top K
        val topPredictions = predictions
            .sortedByDescending { it.confidence }
            .take(TOP_K)
            .filter { it.confidence > 0.01f } // Filter out very low confidence predictions
        
        // Log the top predictions for debugging
        Log.d(TAG, "Top predictions:")
        topPredictions.take(5).forEachIndexed { index, prediction ->
            Log.d(TAG, "  ${index + 1}. Kanji: '${prediction.kanji}' (Unicode: U+${prediction.kanji.firstOrNull()?.code?.toString(16)?.uppercase() ?: "????"}), Confidence: ${prediction.confidence}")
        }
        
        return topPredictions
    }
    
    fun isReady(): Boolean = isInitialized
    
    fun release() {
        try {
            interpreter?.close()
            interpreter = null
            isInitialized = false
            Log.d(TAG, "Model resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release model resources", e)
        }
    }
}