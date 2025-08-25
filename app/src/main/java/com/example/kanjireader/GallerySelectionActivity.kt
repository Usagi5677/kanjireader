package com.example.kanjireader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.gson.Gson
import android.graphics.Rect
import java.io.File
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.delay
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import android.content.Intent
import java.io.FileOutputStream
import androidx.appcompat.app.AlertDialog

class GallerySelectionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GallerySelection"
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
    private lateinit var selectPhotoButton: MaterialButton

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
    private var tagDictLoader: TagDictSQLiteLoader? = null
    private var morphologyAnalyzer: MorphologicalAnalyzer? = null
    private var isJapaneseOnlyMode = true
    private var originalOcrText = ""

    private var deinflectionEngine: TenTenStyleDeinflectionEngine? = null

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    // Gallery selection launcher
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { selectedUri ->
            processSelectedImage(selectedUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery_selection)

        Log.d(TAG, "Starting GallerySelectionActivity...")

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

            // SQLite database is initialized automatically
            Log.d(TAG, "Dictionary database ready for use")
            if (tagDictLoader?.isTagDatabaseReady() != true) {
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

            // Initialize furigana processor with Kuromoji support
            furiganaProcessor = FuriganaProcessor(
                dictionaryRepository = DictionaryRepository.getInstance(this),
                deinflectionEngine = deinflectionEngine,
                tagDictLoader = tagDictLoader
            )
            
            // Set processor for text selection view
            textSelectionView.setFuriganaProcessor(furiganaProcessor)

            // Initialize furigana toggle appearance
            updateFuriganaToggleAppearance()

            // Show initial empty state
            showSelectPhotoPrompt()

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing views: ${e.message}")
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
        selectPhotoButton = findViewById(R.id.selectPhotoButton)

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
        selectPhotoButton.setOnClickListener {
            selectPhotoFromGallery()
        }

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
    }

    private fun selectPhotoFromGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun processSelectedImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Processing selected image: $uri")

                // Processing image (no overlay needed with SQLite FTS5)

                // Convert URI to Bitmap
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap == null) {
                    Log.e(TAG, "Failed to decode image from URI")
                    Toast.makeText(this@GallerySelectionActivity, "Failed to load image", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Display the image
                imageView.setImageBitmap(bitmap)

                // Create InputImage for ML Kit
                val inputImage = InputImage.fromBitmap(bitmap, 0)

                // Process with OCR
                processImageWithOCR(inputImage, bitmap)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing selected image", e)
                Toast.makeText(this@GallerySelectionActivity, "Error processing image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processImageWithOCR(image: InputImage, bitmap: Bitmap) {
        val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        Log.d("OCR", "Image dimensions sent to OCR: ${image.width}x${image.height}")
        Log.d("OCR", "Bitmap dimensions: ${bitmap.width}x${bitmap.height}")
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text
                Log.d(TAG, "Extracted text: '$text'")
                
                // Processing complete
                
                if (text.isBlank()) {
                    Toast.makeText(this, "No text found in image", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Process the OCR result
                handleOcrSuccess(bitmap, visionText)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Text recognition failed", e)
                Toast.makeText(this, "Text recognition failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleOcrSuccess(bitmap: Bitmap, visionText: Text) {
        this.visionText = visionText
        originalOcrText = visionText.text

        // Set title
        ocrTextTitle.text = "Extracted Text (${visionText.textBlocks.size} blocks)"

        // Process Japanese words for auto-saving
        val extractedWords: Set<String> = wordExtractor?.extractJapaneseWords(visionText.text) ?: emptySet()
        Log.d(TAG, "Extracted ${extractedWords.size} Japanese words for auto-saving")

        if (extractedWords.isNotEmpty()) {
            // Auto-save words to readings list (simplified for SQLite mode)
            // TODO: Implement SQLite-based auto-save if needed
            val wordsToSave: List<WordResult> = emptyList()
            
            if (wordsToSave.isNotEmpty()) {
                ReadingsListManager.addWords(wordsToSave)
                Log.d(TAG, "Auto-saved ${wordsToSave.size} words to readings list")
            }
        }

        // Update text selection view
        updateTextDisplay()

        // Show bottom sheet
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        
        // Hide select photo button and show controls
        selectPhotoButton.visibility = View.GONE
    }

    // Copy the remaining methods from ImageAnnotationActivity...
    private fun updateTextDisplay() {
        val displayText = if (isJapaneseOnlyMode) {
            getFilteredJapaneseText()
        } else {
            originalOcrText.replace(Regex("\\s+"), " ").trim()
        }

        textSelectionView.setText(displayText, visionText)
        textSelectionView.setShowFurigana(isFuriganaMode)
    }

    private fun getFilteredJapaneseText(): String {
        return originalOcrText.filter { char ->
            val unicodeBlock = Character.UnicodeBlock.of(char)
            unicodeBlock == Character.UnicodeBlock.HIRAGANA ||
                    unicodeBlock == Character.UnicodeBlock.KATAKANA ||
                    unicodeBlock == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                    unicodeBlock == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                    char == '。' || char == '、' || char == '？' || char == '！' ||
                    char == ' ' || char == '\n'
        }.replace(Regex("\\s+"), " ").trim()
    }

    private fun showSelectPhotoPrompt() {
        selectPhotoButton.visibility = View.VISIBLE
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        ocrTextTitle.text = getString(R.string.select_photo_from_gallery)
    }


    // Include the other necessary methods from ImageAnnotationActivity
    // (toggleJapaneseOnlyMode, toggleFuriganaMode, copyOcrTextToClipboard, etc.)
    
    private fun toggleJapaneseOnlyMode() {
        isJapaneseOnlyMode = !isJapaneseOnlyMode
        updateJapaneseToggleAppearance()
        updateTextDisplay()
        Log.d(TAG, "Japanese-only mode: $isJapaneseOnlyMode")
    }

    private fun updateJapaneseToggleAppearance() {
        if (isJapaneseOnlyMode) {
            japaneseOnlyToggle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.teal_700)
            )
            japaneseOnlyToggle.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            japaneseOnlyToggle.strokeWidth = 0
        } else {
            japaneseOnlyToggle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.transparent)
            )
            japaneseOnlyToggle.setTextColor(ContextCompat.getColor(this, R.color.teal_700))
            japaneseOnlyToggle.strokeWidth = 2
        }
    }

    private fun toggleFuriganaMode() {
        isFuriganaMode = !isFuriganaMode
        updateFuriganaToggleAppearance()
        textSelectionView.setShowFurigana(isFuriganaMode)
        Log.d(TAG, "Furigana mode: $isFuriganaMode")
    }

    private fun updateFuriganaToggleAppearance() {
        if (isFuriganaMode) {
            furiganaToggle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.teal_700)
            )
            furiganaToggle.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            furiganaToggle.strokeWidth = 0
        } else {
            furiganaToggle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.transparent)
            )
            furiganaToggle.setTextColor(ContextCompat.getColor(this, R.color.teal_700))
            furiganaToggle.strokeWidth = 2
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
                        Toast.makeText(this@GallerySelectionActivity, "Translation failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    translateOcrTextButton.isEnabled = true
                    Toast.makeText(this@GallerySelectionActivity, "Translation error: ${e.message}", Toast.LENGTH_SHORT).show()
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
            setTextColor(ContextCompat.getColor(this@GallerySelectionActivity, R.color.teal_700))
        }
        
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(ContextCompat.getColor(this@GallerySelectionActivity, android.R.color.darker_gray))
        }
    }

    // Add the missing methods for navigation, bottom sheet, etc.
    private fun setupBottomSheetBehavior() {
        bottomSheetBehavior.apply {
            isDraggable = false
            state = BottomSheetBehavior.STATE_HIDDEN
            skipCollapsed = true
            peekHeight = 0
            isHideable = true
            halfExpandedRatio = 0.001f

            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            btnToggleWordList.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                            isBottomSheetVisible = true
                        }
                        BottomSheetBehavior.STATE_HIDDEN -> {
                            btnToggleWordList.setImageResource(android.R.drawable.ic_menu_agenda)
                            isBottomSheetVisible = false
                        }
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            })
        }
    }

    private fun toggleBottomSheet() {
        if (isBottomSheetVisible) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            Log.d(TAG, "Bottom sheet hidden")
        } else {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            Log.d(TAG, "Bottom sheet expanded")
        }
    }

    private fun setupNavigationDrawer() {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_camera -> {
                    finish()
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_gallery -> {
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
        navigationView.menu.findItem(R.id.nav_gallery)?.isChecked = true
    }

    // Add the missing text selection and word lookup methods
    private fun onTextSelected(selectedText: String) {
        Log.d(TAG, "Text selected: '$selectedText'")
        lookupWordAndLaunchDetail(selectedText)
    }

    private fun lookupWordAndLaunchDetail(selectedText: String) {
        Log.d(TAG, "Text selected: '$selectedText'")

        // Check if it's just punctuation - skip for now
        if (isPunctuation(selectedText)) {
            Log.d(TAG, "Skipping punctuation: $selectedText")
            return
        }

        // Use SQLite lookup with async processing
        lifecycleScope.launch {
            try {
                val repository = DictionaryRepository.getInstance(this@GallerySelectionActivity)
                val searchResults = repository.search(selectedText, limit = 1)
                
                if (searchResults.isNotEmpty()) {
                    val result = searchResults.first()
                    
                    // Get Kuromoji reading for the actual result word, not the selected text
                    val wordToAnalyze = result.kanji ?: result.reading
                    val kuromojiReading = furiganaProcessor.getWordReading(wordToAnalyze)
                    val finalReading = if (kuromojiReading != null && kuromojiReading != result.reading) {
                        Log.d(TAG, "Using Kuromoji reading '$kuromojiReading' instead of dictionary reading '${result.reading}' for result word '$wordToAnalyze'")
                        kuromojiReading
                    } else {
                        Log.d(TAG, "Using dictionary reading '${result.reading}' for result word '$wordToAnalyze'")
                        result.reading
                    }
                    
                    // Convert to EnhancedWordResult format
                    val enhancedResult = EnhancedWordResult(
                        kanji = result.kanji,
                        reading = finalReading,
                        meanings = result.meanings,
                        isCommon = result.isCommon,
                        numericFrequency = result.frequency
                    )
                    launchWordDetailForResults(selectedText, enhancedResult)
                    return@launch
                }
                
                Log.d(TAG, "No results found for '$selectedText'")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error looking up word: $selectedText", e)
            }
        }

        Log.d(TAG, "No results found for '$selectedText'")
    }

    private fun launchWordDetailForResults(selectedText: String, result: EnhancedWordResult) {
        // Clear persistent selection to prevent duplicate popups
        textSelectionView.clearPersistentSelection()
        
        val intent = Intent(this, WordDetailActivity::class.java)
        intent.putExtra("word", result.kanji ?: result.reading)
        intent.putExtra("reading", result.reading)
        intent.putStringArrayListExtra("meanings", ArrayList(result.meanings))
        intent.putExtra("selectedText", selectedText)
        startActivity(intent)
    }

    private fun checkIfWordExists(testWord: String): Boolean {
        // Use SQLite lookup
        return try {
            val repository = DictionaryRepository.getInstance(this)
            runBlocking {
                val results = repository.search(testWord, limit = 1)
                results.isNotEmpty()
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun hideTopPopup() {
        topPopup.visibility = View.GONE
    }

    private fun isPunctuation(text: String): Boolean {
        return text.matches(Regex("[\\p{Punct}\\s]+"))
    }

    private fun enhanceWithTagDict(wordResults: List<WordResult>): List<EnhancedWordResult> {
        val tagDictLoader = this.tagDictLoader

        return if (tagDictLoader?.isTagDatabaseReady() == true) {
            // TagDict is available - enhance all results
            Log.d(TAG, "Enhancing ${wordResults.size} results with TagDict")
            wordResults.map { wordResult ->
                tagDictLoader.enhanceWordResult(wordResult)
            }
        } else {
            // TagDict not available - convert to basic enhanced results
            Log.d(TAG, "TagDict not available, using basic enhancement for ${wordResults.size} results")
            wordResults.map { wordResult ->
                EnhancedWordResult(
                    kanji = wordResult.kanji,
                    reading = wordResult.reading,
                    meanings = wordResult.meanings,
                    isCommon = wordResult.isCommon,
                    numericFrequency = wordResult.frequency
                )
            }
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}