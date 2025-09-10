package com.example.kanjireader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import java.nio.ByteBuffer
import kotlinx.coroutines.launch
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.graphics.Rect
import java.io.ByteArrayOutputStream
import java.io.File
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import android.view.MenuItem
import com.example.kanjireader.databinding.ActivityMainBinding
import java.io.FileOutputStream
import java.io.IOException
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import androidx.activity.OnBackPressedCallback

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // UI Components are now accessed through binding
    // No need for individual view variables

    // Core components - now managed by ViewModel
    private lateinit var wordExtractor: JapaneseWordExtractor

    // Camera
    private var imageCapture: ImageCapture? = null
    private var flashEnabled = false

    // Camera components
    private var camera: Camera? = null
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    companion object {
        private const val TAG = "KanjiReader"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private var isCapturing = false
    private var isGalleryProcessing = false  // Track if we're processing from gallery

    // Gallery selection launcher
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            Log.d(TAG, "[GALLERY] Gallery image selected - processing: $uri")
            processSelectedImage(uri)
        } else {
            Log.d(TAG, "[GALLERY] Gallery cancelled")
            // User cancelled gallery picker - no overlay to hide since we don't show it early
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "MainActivity onCreate() called")
        
        // Clear FTS fix flag (performance optimization)
        val prefs = getSharedPreferences("dictionary_prefs", MODE_PRIVATE)
        prefs.edit().remove("fts_fixed_v2").apply()
        
        // Initialize views
        initializeViews()
        setupNavigationDrawer()
        
        // Set up ViewModel observers 
        setupViewModelObservers()
        
        // Initialize ViewModel (this will handle dictionary loading)
        viewModel.initializeDictionaries()
        
        // Initialize other components
        wordExtractor = JapaneseWordExtractor()
        
        // Handle intent actions
        when (intent.getStringExtra("action")) {
            "open_gallery" -> {
                Log.d(TAG, "Intent open_gallery")
                // Reset any processing state first
                isCapturing = false
                resetUIState()
                
                galleryLauncher.launch("image/*")
                
                // Clear the action so it doesn't interfere with normal camera usage
                intent.removeExtra("action")
            }
            "reset_camera" -> {
                // Reset any lingering state and ensure camera is ready
                isCapturing = false
                resetUIState()
                viewModel.resetCameraState()
                viewModel.clearError()
                
                // Force hide processing overlay
                binding.processingOverlay.visibility = View.GONE
                
                // Ensure camera preview is visible
                binding.previewView.visibility = View.VISIBLE
                
                // Clear the action
                intent.removeExtra("action")
            }
        }
        
        // Setup back button handling
        setupBackButtonHandling()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Handle intent actions when activity is reused
        when (intent?.getStringExtra("action")) {
            "open_gallery" -> {
                Log.d(TAG, "Intent open_gallery")
                // Reset any processing state first
                isCapturing = false
                resetUIState()
                
                galleryLauncher.launch("image/*")
                
                // Clear the action so it doesn't interfere with normal camera usage
                intent.removeExtra("action")
            }
            "reset_camera" -> {
                // Reset any lingering state and ensure camera is ready
                isCapturing = false
                resetUIState()
                viewModel.resetCameraState()
                viewModel.clearError()
                
                // Force hide processing overlay
                binding.processingOverlay.visibility = View.GONE
                
                // Ensure camera preview is visible
                binding.previewView.visibility = View.VISIBLE
                
                // Clear the action
                intent.removeExtra("action")
            }
        }
    }

    private fun setupViewModelObservers() {
        // Observe camera state
        viewModel.cameraState.observe(this) { cameraState ->
            when (cameraState) {
                is MainViewModel.CameraState.Initializing -> {
                    // Camera initializing - just wait
                }
                is MainViewModel.CameraState.Ready -> {
                    setupCameraUI()
                }
                is MainViewModel.CameraState.Processing -> {
                    binding.processingOverlay.visibility = View.VISIBLE
                    binding.btnCapture.isEnabled = false
                }
            }
        }

        // Observe dictionary ready state
        viewModel.isDictionaryReady.observe(this) { isReady ->
            if (isReady) {
                initializeSQLiteSearch()
            }
        }

        // Observe readings button state
        viewModel.readingsButtonEnabled.observe(this) { enabled ->
            updateReadingsButtonState(enabled)
        }

        // Observe error state
        viewModel.errorState.observe(this) { errorState ->
            errorState?.let { error ->
                when (error) {
                    is MainViewModel.ErrorState.DictionaryLoadFailed -> {
                        showUserFriendlyError(getString(R.string.error_dictionary_load_failed), error.message)
                    }
                    is MainViewModel.ErrorState.ImageProcessingFailed -> {
                        showUserFriendlyError(getString(R.string.error_image_processing_failed), getString(R.string.error_please_try_again))
                    }
                    is MainViewModel.ErrorState.OcrFailed -> {
                        showUserFriendlyError(getString(R.string.error_text_recognition_failed), getString(R.string.error_no_japanese_text_detected))
                    }
                    is MainViewModel.ErrorState.OutOfMemory -> {
                        showUserFriendlyError(getString(R.string.error_out_of_memory), getString(R.string.error_close_other_apps))
                    }
                    is MainViewModel.ErrorState.NetworkError -> {
                        showUserFriendlyError(getString(R.string.error_network), getString(R.string.error_check_internet_connection))
                    }
                }
                viewModel.clearError()
            }
        }

        // Observe OCR result
        viewModel.ocrResult.observe(this) { ocrResult ->
            ocrResult?.let { result ->
                launchImageAnnotationActivityFromViewModel(result.bitmap, result.ocrData)
                viewModel.clearOcrResult()
            }
        }
    }

    private fun setupNavigationDrawer() {
        // Drawer is unlocked from the start since SQLite is ready immediately
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)

        // Set navigation view colors programmatically
        val navColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(
                ContextCompat.getColor(this, R.color.teal_900), // selected
                ContextCompat.getColor(this, R.color.text_primary_color) // unselected - uses theme-aware color
            )
        )
        binding.navigationView.itemTextColor = navColors
        binding.navigationView.itemIconTintList = navColors
        
        // Set navigation item listener
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_camera -> {
                    // Reset any lingering state and ensure camera is ready
                    isCapturing = false
                    resetUIState()
                    viewModel.resetCameraState()
                    viewModel.clearError()
                    
                    // Force hide processing overlay
                    binding.processingOverlay.visibility = View.GONE
                    
                    // Check if camera permission is granted
                    if (allPermissionsGranted()) {
                        // Ensure camera is started and preview is visible
                        binding.previewView.visibility = View.VISIBLE
                        startCamera()
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    } else {
                        // Request camera permission
                        requestPermissions()
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    }
                }
                R.id.nav_gallery -> {
                    Log.d(TAG, "Sidebar gallery clicked")
                    galleryLauncher.launch("image/*")
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_saved_words -> {
                    val intent = Intent(this, ReadingsListActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_dictionary -> {
                    val intent = Intent(this, DictionaryActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_settings -> {
                    try {
                        Log.d(TAG, "Settings menu item clicked")
                        val intent = Intent(this, SettingsActivity::class.java)
                        startActivity(intent)
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                        Log.d(TAG, "Settings activity started successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open Settings activity", e)
                    }
                }
            }
            true
        }
        binding.navigationView.menu.findItem(R.id.nav_camera)?.isChecked = true
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel ONLY the activity-scoped jobs, NOT the dictionary loading
        // The dictionaryScope will continue running
        Log.d(TAG, "MainActivity onDestroy() - keeping dictionary loading job alive")
    }

    private fun initializeViews() {
        // Views are now accessed through binding
        // Setup click listeners (but they'll be disabled until dictionaries load)
        setupClickListeners()
        
        // Apply light mode green color to ProgressBars in both modes
        val progressBarColor = ColorStateList.valueOf(android.graphics.Color.parseColor("#00695C"))
        
        // Processing overlay ProgressBar - dark green in both modes
        binding.processingProgress.indeterminateTintList = progressBarColor
        
        // Loading overlay ProgressBar - dark green in both modes
        binding.loadingProgress.indeterminateTintList = progressBarColor
    }

    private fun showLoadingScreen() {
        // Show loading overlay
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.loadingProgress.visibility = View.VISIBLE

        // Hide ALL camera UI
        binding.previewView.visibility = View.GONE
        binding.btnCapture.visibility = View.GONE
        binding.btnReadingsList.visibility = View.GONE
        binding.btnFlash.visibility = View.GONE
        binding.btnMenu.visibility = View.GONE
        binding.processingOverlay.visibility = View.GONE

        Log.d(TAG, "Loading screen displayed - camera UI hidden")
    }

    // Dictionary loading is now handled in ViewModel

    // Setup modern back button handling
    private fun setupBackButtonHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
            }
        })
    }




    private fun updateReadingsButtonState(enabled: Boolean) {
        if (enabled) {
            // Dictionaries ready - enable button with full features
            binding.btnReadingsList.isEnabled = true && !isCapturing
            binding.btnReadingsList.alpha = 1.0f
            binding.btnReadingsList.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.teal_500)
            )
        } else {
            // Dictionaries not ready - disable button
            binding.btnReadingsList.isEnabled = false
            binding.btnReadingsList.alpha = 1.0f
            binding.btnReadingsList.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.teal_500)
            )
        }
    }

    private fun setupCameraUI() {
        Log.d(TAG, "Setting up camera UI...")

        // Show camera UI
        binding.previewView.visibility = View.VISIBLE
        binding.btnCapture.visibility = View.VISIBLE
        binding.btnFlash.visibility = View.VISIBLE
        binding.btnMenu.visibility = View.VISIBLE

        // Drawer is already unlocked from the start

        // Show readings button but keep it disabled initially
        binding.btnReadingsList.visibility = View.VISIBLE
        // Button state will be updated by ViewModel observer

        // Request camera permission and start camera
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        Log.d(TAG, "Camera UI setup complete")
    }

    private fun updateLoadingText(mainText: String, subText: String = "") {
        runOnUiThread {
            binding.loadingText.text = mainText
            binding.loadingSubtext.text = subText
            Log.d(TAG, "$mainText $subText")
        }
    }

    private fun showLoadingError(error: String) {
        runOnUiThread {
            binding.loadingProgress.visibility = View.GONE
            binding.loadingText.text = "Error"
            binding.loadingSubtext.text = error

            // Error is shown in the loading screen - no need for toast
        }
    }

    private fun showUserFriendlyError(title: String, message: String) {
        runOnUiThread {
            // Error handling now done through ViewModel observers - no toast needed
            
            // Log for debugging
            Log.w(TAG, "User error: $title - $message")
        }
    }

    private fun handleNetworkError(operation: String) {
        showUserFriendlyError(getString(R.string.error_network), getString(R.string.error_check_internet_connection))
        Log.w(TAG, "Network error during: $operation")
    }

    private fun launchImageAnnotationActivityFromViewModel(bitmap: Bitmap, ocrData: SerializableOcrData) {
        try {
            // Save bitmap to temporary file
            val tempFile = File(cacheDir, "temp_capture.jpg")
            val outputStream = FileOutputStream(tempFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.close()

            // Save OCR data to JSON file
            val jsonFile = File(cacheDir, "temp_ocr_data.json")
            val gson = Gson()
            val jsonString = gson.toJson(ocrData)
            jsonFile.writeText(jsonString)

            // Launch ImageAnnotationActivity with file paths
            val intent = Intent(this, ImageAnnotationActivity::class.java)
            intent.putExtra("bitmap_path", tempFile.absolutePath)
            intent.putExtra("ocr_data_path", jsonFile.absolutePath)
            startActivity(intent)
            
            // Don't reset capture state here - let it be handled by the capture completion/error callbacks

        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ImageAnnotationActivity", e)
            showUserFriendlyError(getString(R.string.error_image_processing_failed), getString(R.string.error_please_try_again))
        }
    }

    private fun initializeSQLiteSearch() {
        // Initialize repository with SQLite FTS5 only
        val repository = DictionaryRepository.getInstance(this)
        
        // Initialize with required components
        val deinflectionEngine = TenTenStyleDeinflectionEngine()
        val tagDictLoader = TagDictSQLiteLoader(this)
        
        repository.initialize(deinflectionEngine, tagDictLoader)
        
        Log.d(TAG, "SQLite FTS5 repository initialized")
    }

    private fun setupClickListeners() {
        binding.btnCapture.setOnClickListener {
            captureImage()
        }

        binding.btnFlash.setOnClickListener {
            toggleFlash()
        }

        binding.btnReadingsList.setOnClickListener {
            // Button is already enabled/disabled by ViewModel, so just launch
            val intent = Intent(this, ReadingsListActivity::class.java)
            startActivity(intent)
        }

        // Use binding to access btnMenu
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun toggleFlash() {
        flashEnabled = !flashEnabled

        // Update button icon
        val flashIcon = if (flashEnabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        binding.btnFlash.setImageResource(flashIcon)

        // Update camera flash mode
        imageCapture?.flashMode = if (flashEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }

        val message = if (flashEnabled) getString(R.string.flash_on) else getString(R.string.flash_off)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

                setupZoomControls()

                // Test focus indicator
                binding.previewView.post {
                    testFocusIndicator()
                }

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    // Enhanced zoom functionality with limits and smoothing
    private fun setupZoomControls() {
        var isActuallyScaling = false

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                Log.d(TAG, "Scale gesture BEGIN")
                isActuallyScaling = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val camera = this@MainActivity.camera ?: return false
                val cameraInfo = camera.cameraInfo

                val currentZoomRatio = cameraInfo.zoomState.value?.zoomRatio ?: 1f
                val minZoomRatio = cameraInfo.zoomState.value?.minZoomRatio ?: 1f
                val maxZoomRatio = cameraInfo.zoomState.value?.maxZoomRatio ?: 10f

                val scaleFactor = detector.scaleFactor
                val newZoomRatio = (currentZoomRatio * scaleFactor).coerceIn(minZoomRatio, maxZoomRatio)

                camera.cameraControl.setZoomRatio(newZoomRatio)
                updateZoomIndicator(newZoomRatio)

                Log.d(TAG, "Zooming: $newZoomRatio")
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                Log.d(TAG, "Scale gesture END")
                isActuallyScaling = false
            }
        })

        // Handle both zoom and tap-to-focus
        binding.previewView.setOnTouchListener { _, event ->
            Log.d(TAG, "Touch event: action=${event.action}, x=${event.x}, y=${event.y}, pointers=${event.pointerCount}")

            // Always let the scale gesture detector process the event
            scaleGestureDetector.onTouchEvent(event)

            Log.d(TAG, "Is actually scaling: $isActuallyScaling")

            // Handle tap-to-focus only if we're NOT actually scaling AND it's a single tap up
            if (!isActuallyScaling && event.action == MotionEvent.ACTION_UP && event.pointerCount == 1) {
                Log.d(TAG, "Single tap detected - calling handleTapToFocus")
                handleTapToFocus(event.x, event.y)
            }

            // Reset scaling flag on ACTION_UP to be safe
            if (event.action == MotionEvent.ACTION_UP && event.pointerCount <= 1) {
                isActuallyScaling = false
            }

            // Return true to consume the event
            return@setOnTouchListener true
        }

        Log.d(TAG, "Zoom controls and tap-to-focus setup complete")
    }

    // Add this missing function
    private fun updateZoomIndicator(zoomRatio: Float) {
        runOnUiThread {
            binding.zoomIndicator.text = String.format("%.1fx", zoomRatio)

            // Show the indicator when zooming
            binding.zoomIndicator.visibility = View.VISIBLE

            // Hide it after a delay
            binding.zoomIndicator.removeCallbacks(hideZoomIndicatorRunnable)
            binding.zoomIndicator.postDelayed(hideZoomIndicatorRunnable, 2000) // Hide after 2 seconds
        }
    }

    // Runnable to hide the zoom indicator
    private val hideZoomIndicatorRunnable = Runnable {
        binding.zoomIndicator.visibility = View.GONE
    }

    // Add this test method to MainActivity.kt for debugging
    private fun testFocusIndicator() {
        Log.d(TAG, "Testing focus indicator...")

        Log.d(TAG, "Focus indicator is initialized")

        // Test by showing focus indicator in center of screen
        val centerX = binding.previewView.width / 2f
        val centerY = binding.previewView.height / 2f

        Log.d(TAG, "Showing test focus indicator at center: ($centerX, $centerY)")
        binding.focusIndicator.showFocusIndicator(centerX, centerY)
    }

    // Add method to reset zoom (optional)
    private fun resetZoom() {
        camera?.cameraControl?.setZoomRatio(1f)
    }

    // Update the tap-to-focus implementation
    private fun handleTapToFocus(x: Float, y: Float) {
        Log.d(TAG, "handleTapToFocus called with x=$x, y=$y")

        val camera = this.camera ?: run {
            Log.e(TAG, "Camera is null - cannot focus")
            return
        }

        // Focus indicator is available through binding

        Log.d(TAG, "Focus indicator found, showing at ($x, $y)")

        // Show focus indicator FIRST (for immediate feedback)
        binding.focusIndicator.showFocusIndicator(x, y)

        // Then start camera focus
        try {
            val factory = binding.previewView.meteringPointFactory
            val point = factory.createPoint(x, y)

            val action = FocusMeteringAction.Builder(point)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()

            val focusResult = camera.cameraControl.startFocusAndMetering(action)

            focusResult.addListener({
                try {
                    val result = focusResult.get()
                    if (result.isFocusSuccessful) {
                        Log.d(TAG, "Focus successful at (${x.toInt()}, ${y.toInt()})")
                    } else {
                        Log.d(TAG, "Focus failed at (${x.toInt()}, ${y.toInt()})")
                    }
                } catch (e: Exception) {
                    // Don't log as error for cancelled operations - this is normal
                    if (e.message?.contains("Cancelled") == true) {
                        Log.d(TAG, "Focus operation cancelled (normal behavior)")
                    } else {
                        Log.e(TAG, "Focus error", e)
                    }
                }
            }, ContextCompat.getMainExecutor(this))

            Log.d(TAG, "Focus action started successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting focus", e)
        }
    }

    // UPDATED: Add capture state protection
    private fun captureImage() {
        // Check capture state flag
        if (isCapturing) {
            Log.d(TAG, "Already capturing, ignoring click")
            return
        }

        val imageCapture = imageCapture ?: return

        // Set capturing flag and show processing UI immediately
        isCapturing = true
        
        // Show processing overlay
        binding.processingOverlay.visibility = View.VISIBLE
        
        // Disable button to prevent multiple clicks
        binding.btnCapture.isEnabled = false
        binding.btnCapture.alpha = 0.5f  // Visual feedback

        // Start the capture process immediately
        performImageCapture()
    }

    // Extract the actual capture logic to a separate method
    private fun performImageCapture() {
        val imageCapture = imageCapture ?: run {
            // Reset state on error
            isCapturing = false
            resetUIState()
            return
        }

        // Save image to temporary file first
        val photoFile = File.createTempFile("temp_photo", ".jpg", cacheDir)
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputFileOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Process with quality enhancements
                    processImageWithEnhancements(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)

                    // Reset state on capture error
                    isCapturing = false
                    resetUIState()

                    Toast.makeText(this@MainActivity, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun processSelectedImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Processing selected image: $uri")
                
                // Set processing state
                isCapturing = true
                isGalleryProcessing = true  // Flag this as gallery processing
                
                // Show processing overlay
                Log.d(TAG, "[GALLERY] Setting processing overlay to VISIBLE in processSelectedImage")
                binding.processingOverlay.visibility = View.VISIBLE
                Log.d(TAG, "[GALLERY] Processing overlay visibility is now: ${binding.processingOverlay.visibility}")
                
                // Disable UI during processing
                binding.btnCapture.isEnabled = false
                binding.btnCapture.alpha = 0.5f

                // Convert URI to Bitmap
                val inputStream = contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap == null) {
                    Log.e(TAG, "Failed to decode image from URI")
                    binding.processingOverlay.visibility = View.GONE
                    isCapturing = false
                    isGalleryProcessing = false
                    resetUIState()
                    showUserFriendlyError("Failed to load image", "Please try selecting a different image")
                    return@launch
                }

                // Apply image enhancements for better OCR
                val enhancedBitmap = enhanceImageForOCR(originalBitmap)

                // Create InputImage for ML Kit
                val inputImage = InputImage.fromBitmap(enhancedBitmap, 0)

                // Process with OCR (reuse existing method)
                Log.d(TAG, "[GALLERY] Starting OCR processing")
                processImageWithOCR(inputImage, enhancedBitmap, null)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing selected image", e)
                binding.processingOverlay.visibility = View.GONE
                isCapturing = false
                isGalleryProcessing = false
                resetUIState()
                showUserFriendlyError("Error processing image", "Please try selecting a different image")
            }
        }
    }

    // Add image enhancement before OCR
    private fun processImageWithEnhancements(photoFile: File) {
        try {
            // Load and potentially enhance the image
            val originalBitmap = BitmapFactory.decodeFile(photoFile.absolutePath)

            if (originalBitmap != null) {
                // Apply image enhancements for better OCR
                val enhancedBitmap = enhanceImageForOCR(originalBitmap)

                // Create InputImage from enhanced bitmap
                val image = InputImage.fromBitmap(enhancedBitmap, 0)

                processImageWithOCR(image, enhancedBitmap, photoFile)
            } else {
                // Reset state on processing error
                isCapturing = false
                resetUIState()
                showUserFriendlyError(getString(R.string.error_image_process_failed), getString(R.string.error_try_another_photo))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image processing failed", e)

            // Reset state on processing error
            isCapturing = false
            resetUIState()
            
            when (e) {
                is OutOfMemoryError -> showUserFriendlyError(getString(R.string.error_out_of_memory), getString(R.string.error_close_other_apps))
                is SecurityException -> showUserFriendlyError("Security error", "Please try again")
                else -> showUserFriendlyError(getString(R.string.error_image_processing_failed), getString(R.string.error_please_try_again))
            }
        } finally {
            photoFile.delete()
        }
    }

    // Image enhancement function
    private fun enhanceImageForOCR(originalBitmap: Bitmap): Bitmap {
        // Create a mutable copy
        val enhancedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

        // Apply sharpening filter for better text clarity
        val canvas = Canvas(enhancedBitmap)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                // Increase contrast slightly
                set(floatArrayOf(
                    1.2f, 0f, 0f, 0f, -10f,  // Red
                    0f, 1.2f, 0f, 0f, -10f,  // Green
                    0f, 0f, 1.2f, 0f, -10f,  // Blue
                    0f, 0f, 0f, 1f, 0f       // Alpha
                ))
            })
        }

        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)

        Log.d(TAG, "Applied image enhancements for OCR")
        return enhancedBitmap
    }

    // Update processImageWithOCR to wait for dictionaries seamlessly
    private fun processImageWithOCR(image: InputImage, bitmap: Bitmap, photoFile: File?) {
        val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        Log.d("OCR", "Image dimensions sent to OCR: ${image.width}x${image.height}")
        Log.d("OCR", "Bitmap dimensions: ${bitmap.width}x${bitmap.height}")
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text
                Log.d(TAG, "[OCR] Extracted text: '$text'")
                Log.d(TAG, "[OCR] Processing overlay visibility before ViewModel call: ${binding.processingOverlay.visibility}")

                // Pass the OCR result to ViewModel for processing
                viewModel.handleOcrSuccess(bitmap, visionText)
                
                Log.d(TAG, "[OCR] Called viewModel.handleOcrSuccess - overlay should stay visible")
                Log.d(TAG, "[OCR] Processing overlay visibility after ViewModel call: ${binding.processingOverlay.visibility}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Text recognition failed", e)

                // Reset state on OCR error
                isCapturing = false
                resetUIState()
                showUserFriendlyError(getString(R.string.error_text_recognition_failed), getString(R.string.error_no_japanese_text_detected))
            }
    }


    // Reset capture state when activity resumes (safety measure)
    override fun onResume() {
        super.onResume()

        Log.d(TAG, "[RESUME] onResume called - isCapturing=$isCapturing, isGalleryProcessing=$isGalleryProcessing, overlay visibility=${binding.processingOverlay.visibility}")
        
        // Only apply delay for gallery processing, not camera captures
        if (isCapturing || binding.processingOverlay.visibility == View.VISIBLE) {
            if (isGalleryProcessing) {
                // Gallery processing needs delay to show overlay properly
                Log.d(TAG, "[RESUME] Delaying state reset for gallery processing")
                binding.root.postDelayed({
                    Log.d(TAG, "[RESUME] Delayed reset - resetting gallery processing state")
                    isCapturing = false
                    isGalleryProcessing = false
                    resetUIState()
                }, 1000) // 1 second delay for gallery
            } else {
                // Camera capture - reset immediately
                Log.d(TAG, "[RESUME] Immediate reset for camera capture")
                isCapturing = false
                isGalleryProcessing = false
                resetUIState()
            }
        }

        // Dictionary status is now managed by ViewModel
        Log.d(TAG, "onResume - dictionary status managed by ViewModel")
        // Button state is now updated automatically via ViewModel observers

        // Check and update dictionary state manager
        DictionaryStateManager.checkAndUpdateDictionaryState()
    }

    private fun launchImageAnnotationActivity(bitmap: Bitmap, visionText: Text) {
        try {
            // Save bitmap to temporary file
            val bitmapFile = File(cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
            val fos = FileOutputStream(bitmapFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            fos.close()

            // Convert Text object to serializable data
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

            // Launch activity with file path and OCR data
            val intent = Intent(this, ImageAnnotationActivity::class.java)
            intent.putExtra("bitmap_file_path", bitmapFile.absolutePath)
            intent.putExtra("ocr_data_json", Gson().toJson(ocrData))
            startActivity(intent)

            // Reset capture flag after activity launch
            isCapturing = false

            Log.d(TAG, "ImageAnnotationActivity launched with file data, capture flag reset")

        } catch (e: IOException) {
            Log.e(TAG, "Failed to save bitmap to file", e)
            Toast.makeText(this, getString(R.string.error_failed_to_process_image), Toast.LENGTH_SHORT).show()
            isCapturing = false
        }
    }

    private fun resetUIState() {
        // Hide any overlays and restore button states
        binding.processingOverlay.visibility = View.GONE
        binding.btnCapture.isEnabled = true
        binding.btnCapture.alpha = 1.0f  // Restore full opacity
        binding.btnFlash.isEnabled = true
        binding.btnReadingsList.isEnabled = true
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private val activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            startCamera()
        } else {
            // Camera permission denied - user can still use gallery or request again later
            Log.d(TAG, "Camera permission denied")
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}
