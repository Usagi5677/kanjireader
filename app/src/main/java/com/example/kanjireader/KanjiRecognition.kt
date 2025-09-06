package com.example.kanjireader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.DataType
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class KanjiPrediction(
    val kanji: String,
    val confidence: Float
)

class KanjiRecognition(private val context: Context) {
    
    companion object {
        private const val TAG = "KanjiRecognition"
        private const val MODEL_PATH = "model_float16.tflite"  // Updated to new model
        private const val LABELS_PATH = "labels_2_python_list.txt"  // New labels matching model_float16.tflite
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
            Log.d(TAG, "Initializing Kanji recognition model...")
            
            // Load TensorFlow Lite model
            val modelBuffer = loadModelFile()
            interpreter = Interpreter(modelBuffer)
            
            // Load labels
            labels = loadLabels()
            
            // Validate label count matches model output
            val outputTensor = interpreter!!.getOutputTensor(0)
            val modelOutputSize = outputTensor.shape()[1]
            
            if (labels.size != modelOutputSize) {
                Log.e(TAG, "CRITICAL: Label count mismatch! Model outputs $modelOutputSize classes but labels.txt has ${labels.size} entries")
                Log.e(TAG, "This will cause incorrect character mapping. Truncating labels to match model.")
                
                // Truncate labels to match model size
                if (labels.size > modelOutputSize) {
                    val originalSize = labels.size
                    labels = labels.take(modelOutputSize)
                    Log.w(TAG, "Truncated labels from $originalSize to $modelOutputSize to match model")
                } else {
                    Log.e(TAG, "Labels file has fewer entries than model outputs! This will crash.")
                    isInitialized = false
                    return
                }
            }
            
            isInitialized = true
            Log.d(TAG, "Model initialized successfully with ${labels.size} labels (model outputs: $modelOutputSize classes)")
            
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
                    Log.d(TAG, "Raw text length: ${text.length}")
                    Log.d(TAG, "Text contains newlines: ${text.contains('\n')}")
                    Log.d(TAG, "Text contains carriage returns: ${text.contains('\r')}")
                    Log.d(TAG, "First 50 chars: '${text.take(50)}'")
                    Log.d(TAG, "Last 50 chars: '${text.takeLast(50)}'")
                    
                    val labels = text.toCharArray().map { it.toString() }
                    Log.d(TAG, "Parsed ${labels.size} labels as character array")
                    Log.d(TAG, "First 10 labels: ${labels.take(10).joinToString(", ") { "'$it'" }}")
                    Log.d(TAG, "Last 10 labels: ${labels.takeLast(10).joinToString(", ") { "'$it'" }}")
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
            
            // Resize input tensor to correct dimensions
            val inputShape = intArrayOf(1, size, size, 1)
            interpreter!!.resizeInput(0, inputShape)
            interpreter!!.allocateTensors()  // Allocate tensors after resizing
            
            // Create input buffer with correct shape
            val inputBuffer = TensorBuffer.createFixedSize(inputShape, DataType.FLOAT32)
            
            // Extract pixels and convert to float array (similar to HandWritingService)
            val intArray = IntArray(size * size)
            scaledBitmap.getPixels(intArray, 0, size, 0, 0, size, size)
            
            // First pass: compute mean to determine if inversion is needed
            var sum = 0f
            val grayValues = FloatArray(size * size)
            for (i in intArray.indices) {
                val alpha = (intArray[i] shr 24) and 0xFF
                if (alpha == 0) {
                    grayValues[i] = 0.0f  // Transparent = black
                } else {
                    val pixel = intArray[i]
                    val red = (pixel shr 16) and 0xFF
                    val green = (pixel shr 8) and 0xFF
                    val blue = pixel and 0xFF
                    val gray = (red * 0.299f + green * 0.587f + blue * 0.114f) / 255.0f
                    grayValues[i] = gray
                    sum += gray
                }
            }
            
            val mean = sum / grayValues.size
            val shouldInvert = mean > 0.5f  // White background -> need to invert

            
            Log.d(TAG, "Image mean: $mean, will invert: $shouldInvert")
            
            // Second pass: apply conditional inversion
            val floatArray = FloatArray(size * size)
            for (i in grayValues.indices) {
                floatArray[i] = if (shouldInvert) 1.0f - grayValues[i] else grayValues[i]
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
    
    /**
     * Debug function to test model with an image from assets
     */
    fun testWithAssetImage(imageName: String = "2.jpg") {
        Log.d("KANJI_DEBUG", "=== STARTING DEBUG TEST WITH $imageName ===")
        
        if (!isInitialized || interpreter == null) {
            Log.e("KANJI_DEBUG", "Model not initialized!")
            return
        }
        
        try {
            // Load image from assets
            val inputStream = context.assets.open(imageName)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            Log.d("KANJI_DEBUG", "Loaded image: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")
            
            // Scale to 64x64
            val size = 64
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
            Log.d("KANJI_DEBUG", "Scaled to: ${scaledBitmap.width}x${scaledBitmap.height}")
            
            // Prepare input tensor
            val inputShape = intArrayOf(1, size, size, 1)
            interpreter!!.resizeInput(0, inputShape)
            interpreter!!.allocateTensors()  // Allocate tensors after resizing
            
            val inputBuffer = TensorBuffer.createFixedSize(inputShape, DataType.FLOAT32)
            
            // Extract pixels and convert to float array
            val intArray = IntArray(size * size)
            scaledBitmap.getPixels(intArray, 0, size, 0, 0, size, size)
            
            // First pass: compute mean to determine if inversion is needed
            var sum = 0f
            val grayValues = FloatArray(size * size)
            for (i in intArray.indices) {
                val pixel = intArray[i]
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                val gray = (red * 0.299f + green * 0.587f + blue * 0.114f) / 255.0f
                grayValues[i] = gray
                sum += gray
            }
            
            val mean = sum / grayValues.size
            val shouldInvert = mean > 0.5f  // White background -> need to invert
            
            Log.d("KANJI_DEBUG", "Test image mean: $mean, will invert: $shouldInvert")
            
            // Second pass: apply conditional inversion
            val floatArray = FloatArray(size * size)
            for (i in grayValues.indices) {
                floatArray[i] = if (shouldInvert) 1.0f - grayValues[i] else grayValues[i]
            }
            
            // Calculate and log tensor statistics
            val n = floatArray.size
            var mn = Float.POSITIVE_INFINITY
            var mx = Float.NEGATIVE_INFINITY
            var tensorSum = 0f
            for (v in floatArray) { 
                mn = minOf(mn, v)
                mx = maxOf(mx, v)
                tensorSum += v 
            }
            val tensorMean = tensorSum / n
            Log.d("KANJI_DEBUG", "Input tensor stats -> min=$mn max=$mx mean=$tensorMean size=$n")
            
            // Optional: dump the tensor as PNG for verification
            dumpTensorAsPng(floatArray, size, context.getExternalFilesDir(null)?.absolutePath + "/kanji_debug_input.png")
            
            inputBuffer.loadArray(floatArray)
            
            // Get output tensor info
            val outputTensor = interpreter!!.getOutputTensor(0)
            Log.d("KANJI_DEBUG", "Output tensor shape: ${outputTensor.shape().contentToString()}, type: ${outputTensor.dataType()}")
            
            val numOutputs = outputTensor.numElements()
            Log.d("KANJI_DEBUG", "Number of output classes: $numOutputs, Labels loaded: ${labels.size}")
            
            val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, numOutputs), DataType.FLOAT32)
            
            // Run inference
            Log.d("KANJI_DEBUG", "Running inference...")
            interpreter!!.run(inputBuffer.buffer, outputBuffer.buffer)
            
            // Check output statistics
            val outputArray = outputBuffer.floatArray
            var outMin = Float.POSITIVE_INFINITY
            var outMax = Float.NEGATIVE_INFINITY
            var outSum = 0f
            for (v in outputArray) {
                outMin = minOf(outMin, v)
                outMax = maxOf(outMax, v)
                outSum += v
            }
            val outMean = outSum / outputArray.size
            Log.d("KANJI_DEBUG", "Output tensor stats -> min=$outMin max=$outMax mean=$outMean")
            
            // Find top 10 predictions
            val predictions = mutableListOf<Pair<Int, Float>>()
            for (i in outputArray.indices) {
                predictions.add(Pair(i, outputArray[i]))
            }
            
            val top10 = predictions.sortedByDescending { it.second }.take(10)
            Log.d("KANJI_DEBUG", "=== TOP 10 RAW PREDICTIONS ===")
            for ((idx, pred) in top10.withIndex()) {
                val classIdx = pred.first
                val confidence = pred.second
                val label = if (classIdx < labels.size) labels[classIdx] else "???"
                Log.d("KANJI_DEBUG", "${idx + 1}. Class $classIdx: '$label' = $confidence")
            }
            
            // Process with normal method for comparison
            val normalResults = processResults(outputArray)
            Log.d("KANJI_DEBUG", "Normal processResults returned ${normalResults.size} predictions")
            
            Log.d("KANJI_DEBUG", "=== DEBUG TEST COMPLETE ===")
            
        } catch (e: Exception) {
            Log.e("KANJI_DEBUG", "Test failed with exception", e)
        }
    }
    
    private fun dumpTensorAsPng(floatArray: FloatArray, size: Int = 64, path: String?) {
        if (path == null) {
            Log.w("KANJI_DEBUG", "Cannot dump tensor - no path available")
            return
        }
        
        try {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            var i = 0
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val g = (floatArray[i++].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
                    val argb = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
                    bmp.setPixel(x, y, argb)
                }
            }
            val file = File(path)
            FileOutputStream(file).use { out -> 
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out) 
            }
            Log.d("KANJI_DEBUG", "Wrote debug image to: $path")
        } catch (e: Exception) {
            Log.e("KANJI_DEBUG", "Failed to dump tensor as PNG", e)
        }
    }
}