package com.example.kanjireader

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.text.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    // LiveData for UI state
    private val _loadingState = MutableLiveData<LoadingState>()
    val loadingState: LiveData<LoadingState> = _loadingState

    private val _cameraState = MutableLiveData<CameraState>()
    val cameraState: LiveData<CameraState> = _cameraState

    private val _errorState = MutableLiveData<ErrorState?>()
    val errorState: LiveData<ErrorState?> = _errorState

    private val _ocrResult = MutableLiveData<OcrResult?>()
    val ocrResult: LiveData<OcrResult?> = _ocrResult

    private val _readingsButtonEnabled = MutableLiveData<Boolean>()
    val readingsButtonEnabled: LiveData<Boolean> = _readingsButtonEnabled

    // Dictionary loading state
    private val _isDictionaryReady = MutableLiveData<Boolean>()
    val isDictionaryReady: LiveData<Boolean> = _isDictionaryReady

    // Job management
    private var dictionaryLoadingJob: Job? = null
    private val dictionaryScope = SupervisorJob()

    // Core components for SQLite FTS5 mode
    private var tagDictLoader: TagDictSQLiteLoader? = null
    private var morphologyAnalyzer: MorphologicalAnalyzer? = null

    init {
        _loadingState.value = LoadingState.Loading("", "")
        _cameraState.value = CameraState.Initializing
        _isDictionaryReady.value = false
        _readingsButtonEnabled.value = false
    }

    fun initializeDictionaries() {
        if (dictionaryLoadingJob?.isActive == true) {
            Log.d(TAG, "Dictionary loading already in progress")
            return
        }

        // Using SQLite FTS5 for all searches
        Log.d(TAG, "🚀 SQLite FTS5 MODE: Using database for all searches")

        // Initialize core components for SQLite mode
        tagDictLoader = TagDictSQLiteLoader(getApplication())
        morphologyAnalyzer = MorphologicalAnalyzer()
        
        // Apply tag loader to morphology analyzer
        tagDictLoader?.let { loader ->
            morphologyAnalyzer?.setTagDictLoader(loader)
        }
        
        // SQLite database is always ready - no loading needed
        Log.d(TAG, "SQLite database ready immediately")
        _isDictionaryReady.value = true
        _readingsButtonEnabled.value = true
        _cameraState.value = CameraState.Ready
    }

    fun processImageCapture() {
        _cameraState.value = CameraState.Processing
    }

    fun handleOcrSuccess(bitmap: Bitmap, visionText: Text) {
        viewModelScope.launch {
            try {
                // Convert to our serializable format for intent passing
                val ocrData = SerializableOcrData(
                    text = visionText.text,
                    textBlocks = visionText.textBlocks.map { block ->
                        SerializableTextBlock(
                            text = block.text,
                            boundingBox = block.boundingBox?.let { rect ->
                                SerializableRect(rect.left, rect.top, rect.right, rect.bottom)
                            },
                            lines = block.lines.map { line ->
                                SerializableTextLine(
                                    text = line.text,
                                    boundingBox = line.boundingBox?.let { rect ->
                                        SerializableRect(rect.left, rect.top, rect.right, rect.bottom)
                                    }
                                )
                            }
                        )
                    }
                )

                _ocrResult.value = OcrResult(bitmap, ocrData)
                _cameraState.value = CameraState.Ready

            } catch (e: Exception) {
                Log.e(TAG, "Error processing OCR result", e)
                _errorState.value = ErrorState.ImageProcessingFailed("Failed to process OCR result")
                _cameraState.value = CameraState.Ready
            }
        }
    }

    fun handleOcrError(error: Throwable) {
        Log.e(TAG, "OCR processing failed", error)
        _errorState.value = ErrorState.OcrFailed("Text recognition failed")
        _cameraState.value = CameraState.Ready
    }

    fun handleImageProcessingError(error: Throwable) {
        Log.e(TAG, "Image processing failed", error)
        val errorState = when (error) {
            is OutOfMemoryError -> ErrorState.OutOfMemory("Out of memory")
            is SecurityException -> ErrorState.CameraPermissionDenied("Camera access denied")
            else -> ErrorState.ImageProcessingFailed("Image processing failed")
        }
        _errorState.value = errorState
        _cameraState.value = CameraState.Ready
    }

    fun clearError() {
        _errorState.value = null
    }

    fun clearOcrResult() {
        _ocrResult.value = null
    }

    fun resetCameraState() {
        _cameraState.value = CameraState.Ready
    }

    override fun onCleared() {
        super.onCleared()
        dictionaryLoadingJob?.cancel()
        Log.d(TAG, "MainViewModel cleared")
    }

    // Data classes for state management
    sealed class LoadingState {
        data class Loading(val title: String, val subtitle: String) : LoadingState()
        object Complete : LoadingState()
        data class Error(val message: String) : LoadingState()
    }

    sealed class CameraState {
        object Initializing : CameraState()
        object Ready : CameraState()
        object Processing : CameraState()
    }

    sealed class ErrorState {
        data class DictionaryLoadFailed(val message: String) : ErrorState()
        data class ImageProcessingFailed(val message: String) : ErrorState()
        data class OcrFailed(val message: String) : ErrorState()
        data class OutOfMemory(val message: String) : ErrorState()
        data class CameraPermissionDenied(val message: String) : ErrorState()
        data class NetworkError(val message: String) : ErrorState()
    }

    data class OcrResult(
        val bitmap: Bitmap,
        val ocrData: SerializableOcrData
    )
}