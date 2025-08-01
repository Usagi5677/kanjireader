package com.example.kanjireader

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.text.Text
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.content.Intent
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.gson.Gson
import android.graphics.BitmapFactory
import android.graphics.Rect
import java.io.File
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.delay
import androidx.appcompat.app.AlertDialog


class ImageAnnotationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ImageAnnotation"
    }

    // Basic UI components
    private lateinit var imageView: ImageView
    private lateinit var bottomSheetView: View
    private lateinit var ocrTextTitle: TextView
    private lateinit var textSelectionView: TextSelectionView
    private lateinit var copyOcrTextButton: ImageButton
    private lateinit var translateOcrTextButton: ImageButton
    private lateinit var japaneseOnlyToggle: MaterialButton
    private lateinit var btnToggleWordList: FloatingActionButton
    private lateinit var btnSelectFromGallery: FloatingActionButton

    // Top popup components
    private lateinit var topPopup: LinearLayout
    private lateinit var selectedWordTitle: TextView
    private lateinit var closePopupButton: ImageButton
    private lateinit var wordFoundLayout: LinearLayout
    private lateinit var wordNotFoundLayout: LinearLayout
    private lateinit var kanjiText: TextView
    private lateinit var readingText: TextView
    private lateinit var meaningsText: TextView
    private lateinit var notFoundWordText: TextView

    private lateinit var furiganaToggle: MaterialButton
    private lateinit var furiganaProcessor: FuriganaProcessor

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private var isBottomSheetVisible = true

    private var isFuriganaMode = false

    // Data and state
    private var visionText: Text? = null
    private var wordExtractor: JapaneseWordExtractor? = null
    // Removed HashMap extractors - using SQLite only
    private var tagDictLoader: TagDictSQLiteLoader? = null  // Changed from TagDictLoader
    private var morphologyAnalyzer: MorphologicalAnalyzer? = null
    private var isJapaneseOnlyMode = true
    private var originalOcrText = ""

    private var deinflectionEngine: TenTenStyleDeinflectionEngine? = null

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_annotation)

        Log.d(TAG, "Starting to find views...")

        try {
            // Find all views
            initializeViews()

            setupNavigationDrawer()

            // Setup click listeners
            setupClickListeners()

            // Setup initial state
            wordExtractor = JapaneseWordExtractor()

            // Initialize SQLite components
            tagDictLoader = TagDictSQLiteLoader(this)
            morphologyAnalyzer = MorphologicalAnalyzer()

            // Initialize deinflection engine
            deinflectionEngine = TenTenStyleDeinflectionEngine()

            // Initialize morphology analyzer with tagDictLoader
            morphologyAnalyzer?.let { analyzer ->
                tagDictLoader?.let { loader ->
                    analyzer.setTagDictLoader(loader)
                }
            }

            // SQLite database is always ready
            Log.d(TAG, "SQLite database components initialized")
            if (tagDictLoader?.isTagDatabaseReady() != true) {  // Changed method name
                Log.w(TAG, "TagDict not loaded yet - this should not happen if MainActivity loaded properly")
            }

            if (morphologyAnalyzer == null) {
                Log.w(TAG, "Enhanced morphology analyzer not available")
            }

            updateJapaneseToggleAppearance()

            // Setup text selection callback
            textSelectionView.onTextSelected = { selectedText ->
                onTextSelected(selectedText)
            }

            textSelectionView.onWordLookup = { testWord ->
                checkIfWordExists(testWord)
            }

            // Load and display image
            loadAndDisplayImage()

            // Initialize traditional furigana processor (keep for compatibility)
            furiganaProcessor = FuriganaProcessor(
                dictionaryRepository = DictionaryRepository.getInstance(this),
                deinflectionEngine = deinflectionEngine,
                tagDictLoader = tagDictLoader
            )
            
            // Set furigana processor for text selection view
            textSelectionView.setFuriganaProcessor(furiganaProcessor)

            // Initialize furigana toggle appearance
            updateFuriganaToggleAppearance()

        } catch (e: Exception) {
            Log.e(TAG, "Error finding views: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun initializeViews() {
        // Find all views
        imageView = findViewById(R.id.imageView)
        bottomSheetView = findViewById(R.id.bottomSheet)
        ocrTextTitle = findViewById(R.id.ocrTextTitle)
        textSelectionView = findViewById(R.id.textSelectionView)
        copyOcrTextButton = findViewById(R.id.copyOcrTextButton)
        translateOcrTextButton = findViewById(R.id.translateOcrTextButton)
        japaneseOnlyToggle = findViewById(R.id.japaneseOnlyToggle)
        btnToggleWordList = findViewById(R.id.btnToggleWordList)
        btnSelectFromGallery = findViewById(R.id.btnSelectFromGallery)

        // Top popup components
        topPopup = findViewById<LinearLayout>(R.id.topPopup)
        selectedWordTitle = topPopup.findViewById<TextView>(R.id.selectedWordTitle)
        closePopupButton = topPopup.findViewById<ImageButton>(R.id.closePopupButton)
        wordFoundLayout = topPopup.findViewById<LinearLayout>(R.id.wordFoundLayout)
        wordNotFoundLayout = topPopup.findViewById<LinearLayout>(R.id.wordNotFoundLayout)
        kanjiText = topPopup.findViewById<TextView>(R.id.kanjiText)
        readingText = topPopup.findViewById<TextView>(R.id.readingText)
        meaningsText = topPopup.findViewById<TextView>(R.id.meaningsText)
        notFoundWordText = topPopup.findViewById<TextView>(R.id.notFoundWordText)

        furiganaToggle = findViewById(R.id.furiganaToggle)

        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetView as LinearLayout)
        setupBottomSheetBehavior()

        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
    }

    private fun setupClickListeners() {

        copyOcrTextButton.setOnClickListener {
            copyOcrTextToClipboard()
        }

        translateOcrTextButton.setOnClickListener {
            translateOcrText()
        }

        japaneseOnlyToggle.setOnClickListener {
            toggleJapaneseOnlyMode()
        }

        closePopupButton.setOnClickListener {
            hideTopPopup()
        }
        furiganaToggle.setOnClickListener {
            toggleFuriganaMode()
        }

        btnToggleWordList.setOnClickListener {
            toggleBottomSheet()
        }

        btnSelectFromGallery.setOnClickListener {
            launchGallerySelection()
        }
    }

    private fun launchGallerySelection() {
        // Navigate to MainActivity for gallery selection
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        intent.putExtra("action", "open_gallery")
        startActivity(intent)
    }

    // Add these new methods:
    private fun toggleFuriganaMode() {
        isFuriganaMode = !isFuriganaMode
        updateFuriganaToggleAppearance()
        textSelectionView.setShowFurigana(isFuriganaMode)
        Log.d(TAG, "Furigana mode: $isFuriganaMode")
    }

    // Add new method to toggle bottom sheet:
    private fun toggleBottomSheet() {
        if (isBottomSheetVisible) {
            // Hide completely
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            Log.d(TAG, "Bottom sheet hidden")
        } else {
            // Show expanded
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            Log.d(TAG, "Bottom sheet expanded")
        }
    }

    private fun setupNavigationDrawer() {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_camera -> {
                    // Navigate to MainActivity and reset camera state
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    intent.putExtra("action", "reset_camera")
                    startActivity(intent)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_gallery -> {
                    // Navigate to MainActivity for gallery selection
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    intent.putExtra("action", "open_gallery")
                    startActivity(intent)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_saved_words -> {
                    val intent = Intent(this, ReadingsListActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_dictionary -> {
                    val intent = Intent(this, DictionaryActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
            true
        }
        navigationView.menu.findItem(R.id.nav_camera)?.isChecked = false
        navigationView.menu.findItem(R.id.nav_saved_words)?.isChecked = false
    }

    // Optional: Add state change listener to update button icon
    private fun setupBottomSheetBehavior() {
        bottomSheetBehavior.apply {
            // Disable dragging
            isDraggable = false

            // Set states
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true // This prevents the collapsed state

            // Set peek height to 0 to completely hide when "collapsed"
            peekHeight = 0

            // Disable half-expanded state
            isHideable = true
            halfExpandedRatio = 0.001f // Effectively disables half-expanded

            // Add state change callback
            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            // Update button icon to collapse icon
                            btnToggleWordList.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                            isBottomSheetVisible = true
                        }
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            // Update button icon to expand icon
                            btnToggleWordList.setImageResource(android.R.drawable.ic_menu_agenda)
                            isBottomSheetVisible = false
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    // Not needed since dragging is disabled
                }
            })
        }
    }

    private fun updateFuriganaToggleAppearance() {
        if (isFuriganaMode) {
            // Enabled state - filled button
            furiganaToggle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.teal_700)
            )
            furiganaToggle.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            furiganaToggle.strokeWidth = 0
        } else {
            // Disabled state - outlined button
            furiganaToggle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.transparent)
            )
            furiganaToggle.setTextColor(ContextCompat.getColor(this, R.color.teal_700))
            furiganaToggle.strokeWidth = 1
        }
    }



    // ADD this new method to ImageAnnotationActivity class:
    private fun checkIfWordExists(word: String): Boolean {
        Log.d(TAG, "Checking if word exists: '$word'")

        val repository = DictionaryRepository.getInstance(this)
        if (repository == null) {
            return false
        }

        // Use coroutine to perform async search
        lifecycleScope.launch {
            try {
                val searchResults = repository.search(word, limit = 1)
                // This method just needs to return boolean for UI feedback
                // The actual word lookup will be handled by performWordLookupAndLaunch
            } catch (e: Exception) {
                Log.e(TAG, "Error checking word existence", e)
            }
        }

        // For immediate UI feedback, return true if it looks like Japanese text
        return word.any { char ->
            val codePoint = char.code
            (codePoint in 0x4E00..0x9FAF) || // Kanji
            (codePoint in 0x3040..0x309F) || // Hiragana  
            (codePoint in 0x30A0..0x30FF)    // Katakana
        }
    }

    private fun loadAndDisplayImage() {
        // Get data from Intent - MainActivity sends file paths
        val bitmapFilePath = intent.getStringExtra("bitmap_path")
        val ocrDataFilePath = intent.getStringExtra("ocr_data_path")

        // Load bitmap from file
        bitmapFilePath?.let { filePath ->
            try {
                val bitmap = BitmapFactory.decodeFile(filePath)
                bitmap?.let {
                    imageView.setImageBitmap(it)
                    Log.d(TAG, "Bitmap loaded and displayed successfully")
                    // Clean up temporary file
                    File(filePath).delete()
                } ?: run {
                    Log.e(TAG, "Failed to decode bitmap from file")
                    showImageError(getString(R.string.error_failed_to_load_image), getString(R.string.error_try_capturing_again))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bitmap from file", e)
                showImageError(getString(R.string.error_failed_to_load_image), getString(R.string.error_try_capturing_again))
            }
        }

        // Load OCR data from JSON file
        ocrDataFilePath?.let { filePath ->
            try {
                val jsonString = File(filePath).readText()
                val ocrData = Gson().fromJson(jsonString, SerializableOcrData::class.java)
                
                // Store the serializable data and work with it directly
                this.visionText = null // We'll work with serializable data instead
                originalOcrText = ocrData.text
                updateOcrTextDisplay()
                
                Log.d(TAG, "OCR data loaded successfully: ${ocrData.text.take(100)}...")
                
                // Clean up temporary file
                File(filePath).delete()

                // Auto-save extracted words after OCR text is displayed
                lifecycleScope.launch {
                    saveExtractedWords()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load OCR data from file", e)
                showImageError(getString(R.string.error_failed_to_process_text_data), getString(R.string.error_image_no_readable_text))
            }
        }
    }

    private fun updateOcrTextDisplay() {
        val textToShow = if (isJapaneseOnlyMode) {
            getFilteredJapaneseText()
        } else {
            originalOcrText.replace(Regex("\\s+"), " ").trim()
        }

        if (textToShow.isBlank()) {
            textSelectionView.setText("No text was detected in this image.", null)
        } else {
            textSelectionView.setText(textToShow, visionText)
        }

        // Reapply furigana if enabled
        if (isFuriganaMode) {
            textSelectionView.setShowFurigana(true)
        }

        // Ensure bottom sheet is visible after OCR
        if (!isBottomSheetVisible) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            isBottomSheetVisible = true
        }

        Log.d(TAG, "Displayed text (Japanese only: $isJapaneseOnlyMode, Furigana: $isFuriganaMode)")
    }

    private fun preparePopupForDisplay() {
        // Hide both layouts initially
        wordFoundLayout.visibility = View.GONE
        wordNotFoundLayout.visibility = View.GONE

        // Clear all dynamic content from wordFoundLayout
        val viewsToKeep = listOf(kanjiText, readingText, meaningsText).mapNotNull { view ->
            (view.parent as? ViewGroup)?.let { parent ->
                parent.indexOfChild(view)
            }
        }.maxOrNull() ?: 3

        // Remove all views after the legacy views
        while (wordFoundLayout.childCount > viewsToKeep + 1) {
            wordFoundLayout.removeViewAt(wordFoundLayout.childCount - 1)
        }

        // Reset all legacy views
        kanjiText.visibility = View.GONE
        kanjiText.text = ""
        readingText.visibility = View.GONE
        readingText.text = ""
        meaningsText.visibility = View.GONE
        meaningsText.text = ""
    }

    private fun getFilteredJapaneseText(): String {
        val wordExtractor = this.wordExtractor ?: return originalOcrText
        return wordExtractor.cleanOCRText(originalOcrText)
    }

    private fun toggleJapaneseOnlyMode() {
        isJapaneseOnlyMode = !isJapaneseOnlyMode
        updateJapaneseToggleAppearance()
        updateOcrTextDisplay()
        Log.d(TAG, "Japanese-only mode: $isJapaneseOnlyMode")
    }

    private fun updateJapaneseToggleAppearance() {
        if (isJapaneseOnlyMode) {
            // Enabled state - filled button
            japaneseOnlyToggle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.teal_700)
            )
            japaneseOnlyToggle.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            japaneseOnlyToggle.strokeWidth = 0
        } else {
            // Disabled state - outlined button
            japaneseOnlyToggle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.transparent)
            )
            japaneseOnlyToggle.setTextColor(ContextCompat.getColor(this, R.color.teal_700))
            japaneseOnlyToggle.strokeWidth = 1
        }
    }

    private fun copyOcrTextToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val textToCopy = if (isJapaneseOnlyMode) {
            getFilteredJapaneseText()
        } else {
            originalOcrText.replace(Regex("\\s+"), " ").trim()
        }

        if (textToCopy.isNotBlank()) {
            val clip = ClipData.newPlainText("OCR Text", textToCopy)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No text to copy", Toast.LENGTH_SHORT).show()
        }
    }

    private fun translateOcrText() {
        val textToTranslate = if (isJapaneseOnlyMode) {
            getFilteredJapaneseText()
        } else {
            originalOcrText.replace(Regex("\\s+"), " ").trim()
        }

        if (textToTranslate.isBlank()) {
            Toast.makeText(this, "No text to translate", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading state
        translateOcrTextButton.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val googleTranslateService = GoogleTranslateService()
                val translatedText = googleTranslateService.translateText(textToTranslate)
                
                runOnUiThread {
                    translateOcrTextButton.isEnabled = true
                    
                    if (translatedText != null) {
                        showTranslationDialog(textToTranslate, translatedText)
                    } else {
                        Toast.makeText(this@ImageAnnotationActivity, "Translation failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    translateOcrTextButton.isEnabled = true
                    Toast.makeText(this@ImageAnnotationActivity, "Translation error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showTranslationDialog(originalText: String, translatedText: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Translation")
            .setMessage(translatedText)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Translation", translatedText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Translation copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .create()
        
        // Set rounded corners background
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_rounded_background)
        
        dialog.show()
        
        // Style the buttons after showing
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(ContextCompat.getColor(this@ImageAnnotationActivity, R.color.teal_700))
        }
        
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(ContextCompat.getColor(this@ImageAnnotationActivity, android.R.color.darker_gray))
        }
    }

    // Aggressive duplicate prevention
    private var lastLookupText = ""
    private var lastLookupTime = 0L
    private var lookupInProgress = false
    private var activityLaunchInProgress = false
    
    private fun onTextSelected(selectedText: String) {
        val stackTrace = Thread.currentThread().stackTrace
        val caller = if (stackTrace.size > 3) stackTrace[3].toString() else "unknown"
        Log.d(TAG, "Text selected: '$selectedText' (called from: $caller)")
        
        // Aggressive duplicate prevention - block ANY concurrent lookups
        if (lookupInProgress) {
            Log.d(TAG, "Ignoring text selection '$selectedText' - lookup already in progress")
            return
        }
        
        // Prevent duplicate lookups within 300ms
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastLookupTime
        Log.d(TAG, "Activity debounce check: selectedText='$selectedText', lastLookupText='$lastLookupText', timeDiff=${timeDiff}ms")
        
        if (selectedText == lastLookupText && timeDiff < 300) {
            Log.d(TAG, "Ignoring duplicate text selection for '$selectedText' within 300ms (timeDiff=${timeDiff}ms)")
            return
        }
        
        Log.d(TAG, "Proceeding with lookup for '$selectedText'")
        lookupInProgress = true
        // Set TextSelectionView callback flag immediately
        textSelectionView.setCallbackActive(true)
        lastLookupText = selectedText
        lastLookupTime = currentTime
        lookupWordAndLaunchDetail(selectedText)
    }

    override fun onResume() {
        super.onResume()  
        // Reset flags when activity resumes to prevent stuck states
        resetAllFlags("activity resume")
    }
    
    private fun resetAllFlags(reason: String) {
        lookupInProgress = false
        activityLaunchInProgress = false
        textSelectionView.setCallbackActive(false)
        Log.d(TAG, "Reset all flags - reason: $reason")
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            // First check if drawer is open
            drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                drawerLayout.closeDrawer(GravityCompat.START)
                Log.d(TAG, "Back button pressed - closing navigation drawer")
            }
            // Then check if the top popup is visible
            topPopup.visibility == View.VISIBLE -> {
                hideTopPopup()
                Log.d(TAG, "Back button pressed - hiding top popup")
            }
            // No drawer or popup visible, go back to camera
            else -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
                Log.d(TAG, "Back button pressed - returning to camera")
            }
        }
    }

    private fun lookupWordAndLaunchDetail(selectedText: String) {
        Log.d(TAG, "Text selected: '$selectedText'")

        // Check if it's just punctuation - skip for now
        if (isPunctuation(selectedText)) {
            Log.d(TAG, "Skipping punctuation: $selectedText")
            resetAllFlags("punctuation skipped")
            return
        }

        // Check if it's a single kanji character - use SQLite lookup
        if (selectedText.length == 1 && isKanji(selectedText)) {
            lifecycleScope.launch {
                try {
                    val repository = DictionaryRepository.getInstance(this@ImageAnnotationActivity)
                    val kanjiResults = repository.getKanjiInfo(listOf(selectedText))
                    if (kanjiResults.isNotEmpty()) {
                        val kanjiResult = kanjiResults.first()
                        // kanjiResult is already a KanjiResult from getKanjiInfo()
                        val legacyResult = kanjiResult
                        launchWordDetailForKanji(selectedText, legacyResult)
                        return@launch
                    } else {
                        // No kanji info found - try regular word lookup
                        Log.d(TAG, "No kanji info found for '$selectedText', trying regular lookup")
                        performWordLookupAndLaunch(selectedText)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error looking up kanji info", e)
                    runOnUiThread {
                        resetAllFlags("kanji lookup error")
                    }
                    return@launch
                }
            }
        } else {
            // Regular word lookup for non-single-kanji selections
            performWordLookupAndLaunch(selectedText)
        }
    }

    // Show kanji result
    private fun showKanjiResult(selectedText: String, kanjiResult: KanjiResult) {
        Log.d(TAG, "Showing kanji result for: $selectedText")

        preparePopupForDisplay()

        // Use legacy views for kanji display
        kanjiText.text = kanjiResult.kanji
        kanjiText.visibility = View.VISIBLE
        kanjiText.textSize = 48f
        kanjiText.gravity = android.view.Gravity.CENTER
        kanjiText.setPadding(0, 16, 0, 16)

        // Readings
        val readingsFormatted = buildString {
            if (kanjiResult.onReadings.isNotEmpty()) {
                append("On: ${kanjiResult.onReadings.joinToString(", ")}")
            }
            if (kanjiResult.kunReadings.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("Kun: ${kanjiResult.kunReadings.joinToString(", ")}")
            }
        }

        readingText.text = readingsFormatted
        readingText.visibility = View.VISIBLE
        readingText.textSize = 16f
        readingText.gravity = android.view.Gravity.START
        readingText.setPadding(0, 0, 0, 16)

        // Add meanings label
        addMeaningsLabel()

        // Meanings and info
        val meaningsWithInfo = buildString {
            kanjiResult.meanings.forEachIndexed { index, meaning ->
                append("${index + 1}. $meaning\n")
            }

            append("\n")
            if (kanjiResult.strokeCount != null) append("Strokes: ${kanjiResult.strokeCount}\n")
            if (kanjiResult.jlptLevel != null) append("JLPT: N${kanjiResult.jlptLevel}\n")
            if (kanjiResult.frequency != null) append("Frequency: #${kanjiResult.frequency}\n")
            if (kanjiResult.grade != null) append("Grade: ${kanjiResult.grade}\n")
            if (kanjiResult.nanori.isNotEmpty()) append("Name readings: ${kanjiResult.nanori.joinToString(", ")}")
        }

        meaningsText.text = meaningsWithInfo.trim()
        meaningsText.visibility = View.VISIBLE
        meaningsText.textSize = 16f
        meaningsText.setPadding(8, 0, 0, 16)

        wordFoundLayout.visibility = View.VISIBLE
        wordNotFoundLayout.visibility = View.GONE
        showTopPopup()
    }

    private fun addMeaningsLabel() {
        val label = TextView(this).apply {
            text = "Meanings:"
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@ImageAnnotationActivity, android.R.color.black))
            setPadding(0, 0, 0, 8)
        }
        wordFoundLayout.addView(label)
    }

    private fun addSpacing(heightDp: Int) {
        val space = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (heightDp * resources.displayMetrics.density).toInt()
            )
        }
        wordFoundLayout.addView(space)
    }

    private fun inflateDeinflectionInfo(deinflectionInfo: DeinflectionResult): View {
        val view = layoutInflater.inflate(R.layout.item_deinflection_info, null)

        val detailsText = view.findViewById<TextView>(R.id.deinflectionDetails)
        detailsText.text = buildString {
            append("Original: ${deinflectionInfo.originalForm}\n")
            append("Dictionary form: ${deinflectionInfo.baseForm}\n")
            if (deinflectionInfo.reasonChain.isNotEmpty()) {
                append("Conjugation: ${deinflectionInfo.reasonChain.joinToString(" → ")}\n")
            }
            if (deinflectionInfo.verbType != null) {
                append("Type: ${getVerbTypeDisplay(deinflectionInfo.verbType)}")
            }
        }

        return view
    }

    private fun inflateWordEntry(result: EnhancedWordResult, index: Int): View {
        val view = layoutInflater.inflate(R.layout.item_word_entry, null)

        val entryNumber = view.findViewById<TextView>(R.id.entryNumber)
        val entryTitle = view.findViewById<TextView>(R.id.entryTitle)
        val entryMeanings = view.findViewById<TextView>(R.id.entryMeanings)
        val entryEnhancedInfo = view.findViewById<TextView>(R.id.entryEnhancedInfo)

        entryNumber.text = "$index."

        entryTitle.text = if (result.kanji != null) {
            "${result.kanji} (${result.reading})"
        } else {
            result.reading
        }

        entryMeanings.text = result.meanings.joinToString("; ")

        // Enhanced info
        if (result.partOfSpeech.isNotEmpty() || result.verbType != null) {
            entryEnhancedInfo.visibility = View.VISIBLE
            entryEnhancedInfo.text = buildString {
                if (result.verbType != null) {
                    append("Type: ${getVerbTypeDisplay(result.verbType)}")
                    if (result.partOfSpeech.isNotEmpty()) append("\n")
                }
                if (result.partOfSpeech.isNotEmpty()) {
                    append("Grammar: ${result.partOfSpeech.joinToString(", ")}")
                }
            }
        }

        return view
    }

    private fun performWordLookupAndLaunch(selectedText: String) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "=== Starting word lookup for: '$selectedText' ===")

        val repository = DictionaryRepository.getInstance(this)
        if (repository == null) {
            Log.e(TAG, "Dictionary repository not available")
            Toast.makeText(this, "Dictionary not loaded", Toast.LENGTH_SHORT).show()
            resetAllFlags("repository not available")
            return
        }

        lifecycleScope.launch {
            try {
                // Step 1: Try FTS5 search
                Log.d(TAG, "Step 1: Trying FTS5 search for '$selectedText'")
                val lookupStartTime = System.currentTimeMillis()
                val searchResults = repository.search(selectedText, limit = 10)
                val lookupTime = System.currentTimeMillis() - lookupStartTime
                Log.d(TAG, "FTS5 search took ${lookupTime}ms")
                
                if (searchResults.isNotEmpty()) {
                    Log.d(TAG, "Found ${searchResults.size} search results for '$selectedText'")
                    // Log first few results to debug "迫" vs "迫る" issue
                    searchResults.take(3).forEachIndexed { index, result ->
                        Log.d(TAG, "Result[$index]: kanji='${result.kanji}', reading='${result.reading}'")
                    }
                    
                    val enhanceStartTime = System.currentTimeMillis()
                    // Convert WordResult to EnhancedWordResult and enhance with tags
                    val enhancedResults = searchResults.map { wordResult ->
                        var enhanced = tagDictLoader?.enhanceWordResult(wordResult) ?: EnhancedWordResult(
                            kanji = wordResult.kanji,
                            reading = wordResult.reading,
                            meanings = wordResult.meanings,
                            partOfSpeech = emptyList(),
                            isCommon = wordResult.isCommon,
                            numericFrequency = wordResult.frequency
                        )
                        
                        // Use Kuromoji reading if available for more accurate pronunciation
                        val wordToAnalyze = wordResult.kanji ?: wordResult.reading
                        val kuromojiReading = furiganaProcessor.getWordReading(wordToAnalyze)
                        if (kuromojiReading != null && kuromojiReading != enhanced.reading) {
                            Log.d(TAG, "Using Kuromoji reading '$kuromojiReading' instead of dictionary reading '${enhanced.reading}' for result word '$wordToAnalyze'")
                            enhanced = enhanced.copy(reading = kuromojiReading)
                        } else {
                            Log.d(TAG, "Using dictionary reading '${enhanced.reading}' for result word '$wordToAnalyze'")
                        }
                        
                        enhanced
                    }
                    val enhanceTime = System.currentTimeMillis() - enhanceStartTime
                    Log.d(TAG, "Enhancement took ${enhanceTime}ms")
                    
                    runOnUiThread {
                        val firstResult = enhancedResults.first()
                        Log.d(TAG, "Launching single result: kanji='${firstResult.kanji}', reading='${firstResult.reading}'")
                        launchWordDetailForResults(selectedText, firstResult)
                    }
                    val totalTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Total lookup time: ${totalTime}ms")
                    return@launch
                }

                // Step 2: Nothing found - RESET FLAGS
                Log.d(TAG, "No results found for '$selectedText'")
                runOnUiThread {
                    showWordNotFound(selectedText)
                    // Reset flags immediately when no results found
                    resetAllFlags("no results found")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during word lookup", e)
                runOnUiThread {
                    Toast.makeText(this@ImageAnnotationActivity, "Error looking up word", Toast.LENGTH_SHORT).show()
                    // Reset flags immediately on error
                    resetAllFlags("lookup error")
                }
            }
        }
    }

    private fun launchWordDetailForResults(selectedText: String, result: EnhancedWordResult) {
        // Prevent duplicate activity launches
        if (activityLaunchInProgress) {
            Log.d(TAG, "Ignoring duplicate activity launch request for '$selectedText'")
            return
        }
        
        activityLaunchInProgress = true
        val launchStartTime = System.currentTimeMillis()
        
        val intent = Intent(this, WordDetailActivity::class.java)
        intent.putExtra("word", result.kanji ?: result.reading)
        intent.putExtra("reading", result.reading)
        intent.putStringArrayListExtra("meanings", ArrayList(result.meanings))
        // Pass the original selected text for phrase searching
        intent.putExtra("selectedText", selectedText)
        Log.d(TAG, "Launching WordDetailActivity with word='${result.kanji ?: result.reading}' and selectedText='$selectedText'")
        startActivity(intent)
        val launchTime = System.currentTimeMillis() - launchStartTime
        Log.d(TAG, "startActivity() took ${launchTime}ms")
        
        // Delay flag reset to prevent race condition during activity transition
        lifecycleScope.launch {
            delay(1000) // Wait 1 second for activity to fully initialize
            resetAllFlags("delayed reset after successful launch")
        }
    }

    private fun launchWordDetailForKanji(selectedText: String, kanjiResult: KanjiResult) {
        // Prevent duplicate activity launches
        if (activityLaunchInProgress) {
            Log.d(TAG, "Ignoring duplicate kanji activity launch request for '$selectedText'")
            return
        }
        
        activityLaunchInProgress = true
        
        val intent = Intent(this, WordDetailActivity::class.java)
        intent.putExtra("word", kanjiResult.kanji)
        intent.putExtra("reading", kanjiResult.onReadings.joinToString(", "))
        intent.putStringArrayListExtra("meanings", ArrayList(kanjiResult.meanings))
        // Pass the original selected text for phrase searching  
        intent.putExtra("selectedText", selectedText)
        Log.d(TAG, "Launching WordDetailActivity with kanji='${kanjiResult.kanji}' and selectedText='$selectedText'")
        startActivity(intent)
        
        // Delay flag reset to prevent race condition during activity transition
        lifecycleScope.launch {
            delay(1000) // Wait 1 second for activity to fully initialize
            resetAllFlags("delayed reset after successful kanji launch")
        }
    }

    private fun isHiragana(char: Char): Boolean {
        val codePoint = char.code
        return codePoint in 0x3040..0x309F
    }

    private fun isKatakana(char: Char): Boolean {
        val codePoint = char.code
        return codePoint in 0x30A0..0x30FF
    }

    private fun isLikelyConjugatedEnding(char: Char): Boolean {
        // Common conjugated endings that might be worth looking up
        return char in "うくぐすつぬるたちにらむけげせてねれめでべぺ"
    }



    // New method for single word result using XML layout
    private fun showSingleWordResultNew(result: EnhancedWordResult, showDictionaryEntryHeader: Boolean = false) {
        val view = layoutInflater.inflate(R.layout.item_single_word_result, null)

        // Use the correct IDs from the XML
        val dictHeader = view.findViewById<TextView>(R.id.dictionaryEntryHeader)
        val kanjiDisplay = view.findViewById<TextView>(R.id.kanjiDisplay)
        val readingDisplay = view.findViewById<TextView>(R.id.readingDisplay)
        val meaningsContent = view.findViewById<TextView>(R.id.meaningsContent)

        if (showDictionaryEntryHeader) {
            dictHeader.visibility = View.VISIBLE
        }

        if (result.kanji != null) {
            kanjiDisplay.text = result.kanji
            kanjiDisplay.visibility = View.VISIBLE
        }

        readingDisplay.text = result.reading

        // Format meanings
        val meaningsFormatted = buildString {
            result.meanings.forEachIndexed { index, meaning ->
                append("${index + 1}. $meaning")
                if (index < result.meanings.size - 1) append("\n")
            }

            // Add enhanced info if available
            if (result.verbType != null || result.partOfSpeech.isNotEmpty()) {
                append("\n\n")
                if (result.verbType != null) {
                    append("Type: ${getVerbTypeDisplay(result.verbType)}\n")
                }
                if (result.partOfSpeech.isNotEmpty()) {
                    append("Grammar: ${result.partOfSpeech.joinToString(", ")}")
                }
            }
        }

        meaningsContent.text = meaningsFormatted

        wordFoundLayout.addView(view)
    }

    // Helper method to display verb type nicely
    private fun getVerbTypeDisplay(verbType: VerbType): String {
        return when (verbType) {
            VerbType.ICHIDAN -> "Ichidan verb (一段動詞)"
            VerbType.GODAN_K -> "Godan verb -ku (五段動詞-く)"
            VerbType.GODAN_S -> "Godan verb -su (五段動詞-す)"
            VerbType.GODAN_T -> "Godan verb -tsu (五段動詞-つ)"
            VerbType.GODAN_N -> "Godan verb -nu (五段動詞-ぬ)"
            VerbType.GODAN_B -> "Godan verb -bu (五段動詞-ぶ)"
            VerbType.GODAN_M -> "Godan verb -mu (五段動詞-む)"
            VerbType.GODAN_R -> "Godan verb -ru (五段動詞-る)"
            VerbType.GODAN_G -> "Godan verb -gu (五段動詞-ぐ)"
            VerbType.GODAN_U -> "Godan verb -u (五段動詞-う)"
            VerbType.SURU_IRREGULAR -> "Suru verb (する動詞)"
            VerbType.KURU_IRREGULAR -> "Kuru verb (来る)"
            VerbType.IKU_IRREGULAR -> "Iku verb (行く)"
            VerbType.ADJECTIVE_I -> "I-adjective (い形容詞)"
            VerbType.ADJECTIVE_NA -> "Na-adjective (な形容詞)"
            else -> "Unknown type"
        }
    }

    // Enhance WordResults with TagDict data
    // In ImageAnnotationActivity.kt, update the enhanceWithTagDict method:

    private fun enhanceWithTagDict(wordResults: List<WordResult>): List<EnhancedWordResult> {
        val tagDictLoader = this.tagDictLoader

        return if (tagDictLoader?.isTagDatabaseReady() == true) {  // Changed method
            // TagDict is available - enhance all results
            Log.d(TAG, "Enhancing ${wordResults.size} results with TagDict")
            wordResults.map { wordResult ->
                tagDictLoader.enhanceWordResult(wordResult)  // This method should exist in SQLite version
            }
        } else {
            // TagDict not available - convert to basic enhanced results
            Log.d(TAG, "TagDict not available, using basic enhancement")
            wordResults.map { wordResult ->
                EnhancedWordResult(
                    kanji = wordResult.kanji,
                    reading = wordResult.reading,
                    meanings = wordResult.meanings,
                    partOfSpeech = emptyList(),
                    fields = emptyList(),
                    styles = emptyList(),
                    frequencyTags = emptyList(),  // Changed from 'frequency' to 'frequencyTags'
                    numericFrequency = 0  // Added this line
                )
            }
        }
    }

    // Add divider between results
    private fun addDivider(parent: LinearLayout) {
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            setBackgroundColor(ContextCompat.getColor(this@ImageAnnotationActivity, android.R.color.darker_gray))
            alpha = 0.3f
        }
        parent.addView(divider)
    }

    private fun isPunctuation(text: String): Boolean {
        val punctuationChars = setOf('。', '、', '！', '？', '：', '；', '「', '」', '『', '』',
            '（', '）', '［', '］', '｛', '｝', '〈', '〉', '《', '》',
            '.', ',', '!', '?', ':', ';', '(', ')', '[', ']', '{', '}')
        return text.length == 1 && text[0] in punctuationChars
    }

    private fun showPunctuationInfo(punctuation: String) {
        val punctuationName = when (punctuation) {
            "。" -> "Japanese period (maru)"
            "、" -> "Japanese comma (ten)"
            "！" -> "Japanese exclamation mark"
            "？" -> "Japanese question mark"
            "「" -> "Opening quotation mark"
            "」" -> "Closing quotation mark"
            "『" -> "Opening double quotation mark"
            "』" -> "Closing double quotation mark"
            "（" -> "Opening parenthesis"
            "）" -> "Closing parenthesis"
            else -> "Punctuation mark"
        }

        val punctuationResult = WordResult(
            kanji = null,
            reading = punctuation,
            meanings = listOf(punctuationName),
            isCommon = false,
            frequency = 0,
            wordOrder = 999,
            tags = emptyList(),
            partsOfSpeech = emptyList(),
            isJMNEDictEntry = false,
            isDeinflectedValidConjugation = false
        )

        showWordFound(punctuation, punctuationResult)
    }

    private fun isKanji(char: String): Boolean {
        if (char.isEmpty()) return false
        val codePoint = char.codePointAt(0)
        // Only CJK Unified Ideographs ranges - NOT hiragana/katakana
        return (codePoint in 0x4E00..0x9FAF) ||  // CJK Unified Ideographs
                (codePoint in 0x3400..0x4DBF) ||  // CJK Extension A
                (codePoint in 0x20000..0x2A6DF) || // CJK Extension B
                (codePoint in 0x2A700..0x2B73F) || // CJK Extension C
                (codePoint in 0x2B740..0x2B81F) || // CJK Extension D
                (codePoint in 0x2B820..0x2CEAF)    // CJK Extension E
    }

    private fun isHiragana(char: String): Boolean {
        if (char.isEmpty()) return false
        val codePoint = char.codePointAt(0)
        return codePoint in 0x3040..0x309F // Hiragana range
    }

    private fun isKatakana(char: String): Boolean {
        if (char.isEmpty()) return false
        val codePoint = char.codePointAt(0)
        return codePoint in 0x30A0..0x30FF // Katakana range
    }

    private fun showWordFound(selectedText: String, wordResult: WordResult) {
        Log.d(TAG, "Showing word found for: $selectedText")

        preparePopupForDisplay()

        if (wordResult.kanji != null) {
            kanjiText.text = wordResult.kanji
            kanjiText.visibility = View.VISIBLE
        } else {
            kanjiText.visibility = View.GONE
        }

        readingText.text = wordResult.reading
        readingText.visibility = View.VISIBLE

        val meaningsFormatted = wordResult.meanings.mapIndexed { index, meaning ->
            "${index + 1}. $meaning"
        }.joinToString("\n")
        meaningsText.text = meaningsFormatted
        meaningsText.visibility = View.VISIBLE

        wordFoundLayout.visibility = View.VISIBLE
        wordNotFoundLayout.visibility = View.GONE

        showTopPopup()
    }

    private fun showWordNotFound(selectedText: String) {
        Log.d(TAG, "Showing word not found for: $selectedText")

        preparePopupForDisplay()

        notFoundWordText.text = selectedText

        wordFoundLayout.visibility = View.GONE
        wordNotFoundLayout.visibility = View.VISIBLE

        showTopPopup()
    }

    private fun showTopPopup() {
        topPopup.visibility = View.VISIBLE

        topPopup.animate()
            .translationY(0f)
            .setDuration(300)
            .start()

        Log.d(TAG, "Top popup shown")
    }

    private fun hideTopPopup() {
        topPopup.animate()
            .translationY(-400f)
            .setDuration(300)
            .withEndAction {
                topPopup.visibility = View.GONE
            }
            .start()

        Log.d(TAG, "Top popup hidden")
    }

    private suspend fun saveExtractedWords() {
        Log.d(TAG, "Starting to save extracted words...")

        val wordsToSave = mutableListOf<WordResult>()
        val savedKeys = mutableSetOf<String>() // Track what we've already saved

        // Get the text to process
        val textToProcess = if (isJapaneseOnlyMode) {
            getFilteredJapaneseText()
        } else {
            originalOcrText.replace(Regex("\\s+"), " ").trim()
        }

        // Remove all whitespace between Japanese characters
        val cleanedText = removeWhitespaceBetweenJapanese(textToProcess)

        if (cleanedText.isBlank()) {
            Log.d(TAG, "No text to process for saving")
            return
        }

        val repository = DictionaryRepository.getInstance(this)
        if (repository == null) {
            Log.w(TAG, "Dictionary repository not available, cannot save words")
            return
        }

        // Process text character by character
        var currentPos = 0

        while (currentPos < cleanedText.length) {
            // Skip non-Japanese characters
            if (!isJapaneseChar(cleanedText[currentPos].toString())) {
                currentPos++
                continue
            }

            // Find the longest word starting at this position
            val result = findLongestValidWord(cleanedText, currentPos)

            if (result != null) {
                // Create a unique key to avoid duplicates
                val key = "${result.wordResult.kanji ?: ""}|${result.wordResult.reading}"

                if (key !in savedKeys) {
                    wordsToSave.add(result.wordResult)
                    savedKeys.add(key)
                }

                currentPos += result.length
            } else {
                currentPos++
            }
        }

        // Save all found words WITHOUT showing toast
        if (wordsToSave.isNotEmpty()) {
            ReadingsListManager.addWords(wordsToSave)
        }
    }

    // Add this helper method
    private fun removeWhitespaceBetweenJapanese(text: String): String {
        val result = StringBuilder()
        var i = 0

        while (i < text.length) {
            val char = text[i]

            // Always add non-whitespace characters
            if (!char.isWhitespace()) {
                result.append(char)
            } else {
                // For whitespace, only add if it's NOT between two Japanese characters
                val prevIsJapanese = i > 0 && isJapaneseChar(text[i - 1].toString())
                val nextIsJapanese = i < text.length - 1 && isJapaneseChar(text[i + 1].toString())

                if (!prevIsJapanese || !nextIsJapanese) {
                    result.append(char)
                }
                // Skip whitespace between Japanese characters
            }

            i++
        }

        return result.toString()
    }

    private suspend fun findLongestValidWord(text: String, startPos: Int): AutoSaveWordResult? {
        // Auto-save functionality simplified for SQLite mode
        // For now, disable complex auto-save word detection to focus on main functionality
        // TODO: Implement SQLite-based auto-save word detection if needed
        return null
    }

    // Also update showUnifiedWordDisplay to remove logging:
    private fun showUnifiedWordDisplay(
        originalText: String,
        results: List<EnhancedWordResult>,
        deinflectionInfo: DeinflectionResult? = null
    ) {
        // Remove this log line:
        // Log.d(TAG, "Showing unified display for: $originalText")

        preparePopupForDisplay()

        // Add deinflection info if present
        if (deinflectionInfo != null && deinflectionInfo.reasonChain.isNotEmpty()) {
            val deinflectionView = inflateDeinflectionInfo(deinflectionInfo)
            wordFoundLayout.addView(deinflectionView)
        }

        // Handle display based on number of results
        when {
            results.isEmpty() -> {
                showWordNotFound(originalText)
                return
            }
            results.size == 1 -> {
                // For single result, we can still use the existing views or inflate new one
                showSingleWordResultNew(results.first(), showDictionaryEntryHeader = deinflectionInfo != null)
            }
            else -> {
                // Multiple results
                val headerView = layoutInflater.inflate(R.layout.layout_multiple_definitions_header, null)
                wordFoundLayout.addView(headerView)

                results.take(5).forEachIndexed { index, result ->
                    val entryView = inflateWordEntry(result, index + 1)
                    wordFoundLayout.addView(entryView)

                    if (index < results.size - 1 && index < 4) {
                        addDivider(wordFoundLayout)
                    }
                }
            }
        }

        wordFoundLayout.visibility = View.VISIBLE
        wordNotFoundLayout.visibility = View.GONE
        showTopPopup()
    }


    private fun isJapaneseChar(char: String): Boolean {
        return isKanji(char) || isHiragana(char) || isKatakana(char)
    }
    // Find the longest VALID word (strict matching)


    private fun containsKanji(text: String): Boolean {
        return text.any { isKanji(it.toString()) }
    }

    private fun findExactMatchInContext(candidate: String, fullText: String, startPos: Int): WordResult? {
        val repository = DictionaryRepository.getInstance(this) ?: return null
        
        // Use coroutine for database search but return synchronously for auto-save
        // This is a simplified version for auto-save - the main lookup uses full async search
        return try {
            // For auto-save, we'll do a basic check and let the main search handle complex cases
            if (candidate.length >= 2 && containsKanji(candidate)) {
                // Create a basic WordResult for common patterns
                // The actual detailed lookup will happen when user clicks on the word
                WordResult(
                    kanji = if (containsKanji(candidate)) candidate else null,
                    reading = candidate,
                    meanings = listOf("Word from OCR text"),
                    isCommon = false,
                    frequency = 0,
                    wordOrder = 999,
                    tags = emptyList(),
                    partsOfSpeech = emptyList(),
                    isJMNEDictEntry = false,
                    isDeinflectedValidConjugation = false
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in findExactMatchInContext", e)
            null
        }
    }

    // Check if the full word exists in the text at this position
    private fun checkTextHasFullWord(fullText: String, startPos: Int, candidate: String, dictionaryWord: String): Boolean {
        val expectedLength = dictionaryWord.length
        val endPos = startPos + expectedLength

        if (endPos > fullText.length) return false

        val actualWord = fullText.substring(startPos, endPos)
        return actualWord == dictionaryWord
    }


    // Find exact match with simplified validation for auto-save
    private fun findExactMatch(candidate: String): WordResult? {
        // For auto-save, simplified approach - main lookup will handle details
        return if (candidate.length >= 2 && containsKanji(candidate)) {
            WordResult(
                kanji = candidate,
                reading = candidate,
                meanings = listOf("Word from OCR auto-save"),
                isCommon = false,
                frequency = 0,
                wordOrder = 999,
                tags = emptyList(),
                partsOfSpeech = emptyList(),
                isJMNEDictEntry = false,
                isDeinflectedValidConjugation = false
            )
        } else {
            null
        }
    }

    // Check if a partial match is valid (e.g., "食べ" matching "食べる")
    private fun isValidPartialMatch(candidate: String, dictionaryWord: String): Boolean {
        // The candidate should be the kanji part of the dictionary word
        // After the candidate, there should only be hiragana (okurigana)
        if (!dictionaryWord.startsWith(candidate)) return false

        val remainder = dictionaryWord.substring(candidate.length)
        return remainder.all { isHiragana(it.toString()) }
    }

    // Check if we already found an exact match for this candidate
    private fun hasFoundExactMatch(bestResult: AutoSaveWordResult?, candidate: String): Boolean {
        if (bestResult == null) return false
        val word = bestResult.wordResult
        return word.kanji == candidate || (word.kanji == null && word.reading == candidate)
    }

    // Check if a word is reasonable to save (not a weird partial match)
    private fun isReasonableWord(word: WordResult): Boolean {
        // Skip very short readings that are likely wrong matches
        if (word.reading.length == 1 && !isCommonSingleCharWord(word.reading)) {
            return false
        }

        // Skip if the meaning seems unrelated to typical text
        val unlikelyMeanings = listOf(
            "skipjack tuna", "bull", "ox", "thicket", "bush",
            "77th birthday", "Germanic", "the frog in the well"
        )

        return !word.meanings.any { meaning ->
            unlikelyMeanings.any { unlikely -> meaning.contains(unlikely, ignoreCase = true) }
        }
    }



    // Check if a single character word is common
    private fun isCommonSingleCharWord(word: String): Boolean {
        val commonSingleWords = setOf(
            "を", "が", "は", "に", "の", "で", "と", "も", "や", "か",
        )
        return word in commonSingleWords
    }

    // Try deinflection (simplified for auto-save)
    private fun tryDeinflectionForAutoSave(word: String): WordResult? {
        // For auto-save, we'll do basic pattern matching
        // The actual deinflection will happen during the main lookup when user clicks
        return if (word.length > 2 && word.any { isKanji(it.toString()) }) {
            WordResult(
                kanji = word,
                reading = word,
                meanings = listOf("Potential conjugated word from OCR"),
                isCommon = false,
                frequency = 0,
                wordOrder = 999,
                tags = emptyList(),
                partsOfSpeech = emptyList(),
                isJMNEDictEntry = false,
                isDeinflectedValidConjugation = false
            )
        } else {
            null
        }
    }

    // Helper data class
    private data class AutoSaveWordResult(
        val wordResult: WordResult,
        val length: Int
    )

    // Find word boundary (keep existing implementation)
    private fun findWordBoundaryForAutoSave(text: String, startPos: Int): Int {
        var length = 0
        var pos = startPos

        while (pos < text.length) {
            val char = text[pos]

            if (isPunctuationOrSpace(char)) break
            if (pos > startPos && isScriptChange(text[pos - 1], char)) break

            length++
            pos++
        }

        return minOf(length, 10)
    }
    private fun isPunctuationOrSpace(char: Char): Boolean {
        return char.isWhitespace() || char in "。、！？「」『』（）［］｛｝〈〉《》・.,!?()[]{}\"'"
    }

    private fun isScriptChange(char1: Char, char2: Char): Boolean {
        val type1 = getCharType(char1)
        val type2 = getCharType(char2)

        // Allow kanji→hiragana (for okurigana)
        if (type1 == CharType.KANJI && type2 == CharType.HIRAGANA) return false

        // Otherwise different types mean script change
        return type1 != type2
    }
    private fun getCharType(char: Char): CharType {
        return when {
            isKanji(char.toString()) -> CharType.KANJI
            isHiragana(char.toString()) -> CharType.HIRAGANA
            isKatakana(char.toString()) -> CharType.KATAKANA
            else -> CharType.OTHER
        }
    }
    private enum class CharType {
        KANJI, HIRAGANA, KATAKANA, OTHER
    }


    private fun showImageError(title: String, message: String) {
        runOnUiThread {
            // Log error for debugging - no toast to avoid UI lag
            Log.w(TAG, "Image error: $title - $message")
        }
    }
}