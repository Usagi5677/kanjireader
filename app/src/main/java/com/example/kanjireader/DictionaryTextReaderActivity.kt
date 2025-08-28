package com.example.kanjireader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kanjireader.databinding.ActivityDictionaryTextReaderBinding
import kotlinx.coroutines.launch

class DictionaryTextReaderActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DictionaryTextReaderActivity"
        const val EXTRA_PARAGRAPH_TEXT = "paragraph_text"
        const val EXTRA_WORD_CARDS = "word_cards"
    }

    private lateinit var binding: ActivityDictionaryTextReaderBinding
    private lateinit var wordCardAdapter: WordCardAdapter
    private var paragraphText: String = ""
    private var wordCards: List<WordCardInfo> = emptyList()
    private val wordExtractor = JapaneseWordExtractor()
    private var isFuriganaMode = false
    private var furiganaProcessor: FuriganaProcessor? = null
    
    // Top popup components
    private lateinit var topPopup: LinearLayout
    private lateinit var wordNotFoundLayout: LinearLayout
    private lateinit var notFoundWordText: TextView
    private lateinit var closePopupButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDictionaryTextReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get paragraph text from intent
        paragraphText = intent.getStringExtra(EXTRA_PARAGRAPH_TEXT) ?: ""
        
        if (paragraphText.isEmpty()) {
            Log.e(TAG, "No paragraph text provided")
            Toast.makeText(this, "No text to display", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupViews()
        setupWordCards()
        initializeFurigana()
        loadTextAndWords()
    }

    private fun setupViews() {
        // Setup navigation drawer
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_camera -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_dictionary -> {
                    val intent = Intent(this, DictionaryActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_saved_words -> {
                    val intent = Intent(this, ReadingsListActivity::class.java)
                    startActivity(intent)
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
            true
        }

        // Setup buttons
        binding.furiganaToggle.setOnClickListener {
            toggleFuriganaMode()
        }
        
        binding.copyTextButton.setOnClickListener {
            copyTextToClipboard()
        }
        
        binding.translateTextButton.setOnClickListener {
            translateText()
        }
        
        // Initialize popup components
        topPopup = binding.topPopup
        wordNotFoundLayout = binding.wordNotFoundLayout
        notFoundWordText = binding.notFoundWordText
        closePopupButton = binding.closePopupButton
        
        closePopupButton.setOnClickListener {
            hideTopPopup()
        }

    }

    private fun setupWordCards() {
        wordCardAdapter = WordCardAdapter(
            onWordCardClick = { wordCard, position ->
                // Word card clicked - highlight corresponding word in text and open detail
                highlightWordInText(wordCard)
                openWordDetailActivity(wordCard)
                Log.d(TAG, "Word card clicked: ${wordCard.word} at position ${wordCard.startPosition}-${wordCard.endPosition}")
            },
            onWordCardScroll = { position ->
                // Initial highlighting when data is loaded
                highlightWordInTextByPosition(position)
            }
        )

        binding.wordCardsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DictionaryTextReaderActivity)
            adapter = wordCardAdapter
            
            // Disable animations to prevent "popping" effect during fast scrolling
            itemAnimator = null
            
            // Add scroll listener for automatic highlighting using scroll-based positioning
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val totalItems = wordCards.size
                    if (totalItems <= 0) return
                    
                    // Calculate scroll-based highlight position using mathematical mapping
                    val scrollY = recyclerView.computeVerticalScrollOffset()
                    val scrollRange = recyclerView.computeVerticalScrollRange() - recyclerView.height
                    
                    val highlightPosition = if (scrollRange > 0) {
                        // Map scroll position to item index
                        val scrollProgress = scrollY.toFloat() / scrollRange.toFloat()
                        val calculatedPosition = (scrollProgress * (totalItems - 1)).toInt()
                        
                        // Clamp to valid range
                        maxOf(0, minOf(calculatedPosition, totalItems - 1))
                    } else {
                        // No scroll possible, highlight first item
                        0
                    }
                    
                    // Update highlighting and word card selection
                    recyclerView.post {
                        wordCardAdapter.highlightCard(highlightPosition)
                        highlightWordInTextByPosition(highlightPosition)
                    }
                }
            })
        }
    }

    private fun loadTextAndWords() {
        // Stitch Japanese words that are broken across lines and set up text selection view
        val stitchedText = stitchBrokenJapaneseWords(paragraphText)
        binding.textSelectionView.setText(stitchedText, null)
        
        // Set up text selection callback to highlight corresponding word card and open detail
        binding.textSelectionView.onTextSelected = { selectedText ->
            highlightCorrespondingWordCard(selectedText)
            openWordDetailForSelectedText(selectedText)
        }

        // Extract words and create word cards
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Extracting words from paragraph...")
                
                val repository = DictionaryRepository.getInstance(this@DictionaryTextReaderActivity)
                val wordPositions = wordExtractor.extractWordsWithKuromoji(stitchedText, repository)
                
                Log.d(TAG, "Extracted ${wordPositions.size} words")
                
                val extractedWordCards = mutableListOf<WordCardInfo>()
                
                // Create word cards from extracted words
                for (wordPos in wordPositions) {
                    try {
                        Log.d(TAG, "Looking up word details: '${wordPos.word}' at position ${wordPos.startPosition}-${wordPos.endPosition}")
                        
                        // Special handling for common する conjugations
                        // Also check the explicit baseForm field for artificially split tokens like したら
                        val isConjugatedSuru = (wordPos.kuromojiToken?.baseForm == "する" || wordPos.baseForm == "する") && 
                                              wordPos.word in listOf("した", "しない", "します", "しました", "して", "したり", "したら", "させる", "される", "しよう")
                        
                        val baseFormForSearch = wordPos.getBaseFormForLookup()
                        val isKiWoTsukeyouCompound = baseFormForSearch == "気をつける" || wordPos.word == "気をつけよう"
                        val isAriConjugation = baseFormForSearch == "ある" && wordPos.word == "あり"
                        
                        val searchResults = if (isConjugatedSuru) {
                            // For する conjugations, search for the base form to get correct meanings
                            Log.d(TAG, "Detected する conjugation '${wordPos.word}', searching for base form 'する'")
                            repository?.search("する", limit = 10) ?: emptyList()
                        } else if (isKiWoTsukeyouCompound) {
                            // For 気をつけよう, search using base form to get correct meanings
                            Log.d(TAG, "Detected 気をつけよう compound '${wordPos.word}', searching for base form '$baseFormForSearch'")
                            repository?.search(baseFormForSearch, limit = 10) ?: emptyList()
                        } else if (isAriConjugation) {
                            // For あり, search using base form ある to get correct existence meanings instead of ant meanings
                            Log.d(TAG, "Detected あり conjugation '${wordPos.word}', searching for base form '$baseFormForSearch'")
                            repository?.search(baseFormForSearch, limit = 10) ?: emptyList()
                        } else {
                            repository?.search(wordPos.word, limit = 10) ?: emptyList()
                        }
                        
                        if (searchResults.isNotEmpty()) {
                            // Debug logging for 気をつけよう
                            if (wordPos.word == "気をつけよう") {
                                Log.d(TAG, "🔍 Debug 気をつけよう search results (${searchResults.size} total):")
                                searchResults.forEachIndexed { index, result ->
                                    Log.d(TAG, "  [$index] kanji='${result.kanji}', reading='${result.reading}', meanings='${result.meanings.take(2).joinToString(", ")}'")
                                }
                            }
                            // Find the best matching result - prioritize exact matches (same logic as OCR view)
                            val bestResult = if (isConjugatedSuru || isKiWoTsukeyouCompound || isAriConjugation) {
                                // For する conjugations, 気をつけよう compound, and あり conjugation, use the first result (which should be the base form)
                                searchResults.firstOrNull()
                            } else {
                                // Check for special hiragana preference cases first
                                val baseFormForSearch = wordPos.getBaseFormForLookup()
                                val isHouRelated = baseFormForSearch == "ほう" || wordPos.word == "ほう"
                                val isYaruRelated = baseFormForSearch == "やる" || wordPos.word == "やる"
                                
                                if (isHouRelated) {
                                    // Find 方 with "direction, way, method" meaning instead of 法 with "law, act"
                                    searchResults.find { result ->
                                        result.reading == "ほう" && result.kanji == "方"
                                    } ?: searchResults.find { result ->
                                        result.reading == "ほう" && result.meanings.any { meaning ->
                                            meaning.contains("direction", ignoreCase = true) ||
                                            meaning.contains("way", ignoreCase = true) ||
                                            meaning.contains("method", ignoreCase = true) ||
                                            meaning.contains("side", ignoreCase = true) ||
                                            meaning.contains("person", ignoreCase = true)
                                        }
                                    } ?: searchResults.first()
                                } else if (isYaruRelated) {
                                    // Find 行る with "to do, to undertake" meaning
                                    searchResults.find { it.reading == "やる" && it.kanji == "行る" }
                                        ?: searchResults.find { result ->
                                            result.reading == "やる" && result.meanings.any { meaning ->
                                                (meaning.startsWith("to do", ignoreCase = true) && 
                                                 !meaning.contains("someone", ignoreCase = true)) ||
                                                meaning.contains("to undertake", ignoreCase = true) ||
                                                meaning.contains("to perform", ignoreCase = true) ||
                                                meaning.contains("to play", ignoreCase = true)
                                            }
                                        } ?: searchResults.first()
                                } else {
                                    // Standard exact match logic
                                    searchResults.find { result ->
                                        // Exact match on kanji or reading
                                        result.kanji == wordPos.word || result.reading == wordPos.word
                                    } ?: searchResults.find { result ->
                                        // For katakana words, also check if reading matches without conversion
                                        isAllKatakana(wordPos.word) && result.reading == wordPos.word
                                    }
                                }
                            } ?: run {
                                // More conservative fallback - only use first result if it's actually reasonable
                                // Special handling for やる - prefer hiragana-only forms over kanji forms
                                val baseFormForSearch = wordPos.getBaseFormForLookup()
                                val isYaruRelated = baseFormForSearch == "やる" || wordPos.word == "やる"
                                val isHouRelated = baseFormForSearch == "ほう" || wordPos.word == "ほう"
                                
                                val firstResult = if (isYaruRelated) {
                                    // Find hiragana-only やる if it exists in results
                                    val hiraganaYaru = searchResults.find { it.reading == "やる" && it.kanji == null }
                                    
                                    // If no hiragana-only やる, try to find one with meanings containing "to do" instead of inappropriate content
                                    if (hiraganaYaru != null) {
                                        hiraganaYaru
                                    } else {
                                        // Look for やる with appropriate meanings - prefer 行る with "to do, to undertake"
                                        searchResults.find { result ->
                                            result.reading == "やる" && result.kanji == "行る"
                                        } ?: searchResults.find { result ->
                                            result.reading == "やる" && result.meanings.any { meaning ->
                                                (meaning.startsWith("to do", ignoreCase = true) && 
                                                 !meaning.contains("someone", ignoreCase = true)) ||
                                                meaning.contains("to undertake", ignoreCase = true) ||
                                                meaning.contains("to perform", ignoreCase = true) ||
                                                meaning.contains("to play", ignoreCase = true)
                                            }
                                        } ?: searchResults.first()
                                    }
                                } else if (isHouRelated) {
                                    // Find 方 with "direction, way, method" meaning instead of 法 with "law, act"
                                    searchResults.find { result ->
                                        result.reading == "ほう" && result.kanji == "方"
                                    } ?: searchResults.find { result ->
                                        result.reading == "ほう" && result.meanings.any { meaning ->
                                            meaning.contains("direction", ignoreCase = true) ||
                                            meaning.contains("way", ignoreCase = true) ||
                                            meaning.contains("method", ignoreCase = true) ||
                                            meaning.contains("side", ignoreCase = true) ||
                                            meaning.contains("person", ignoreCase = true)
                                        }
                                    } ?: searchResults.first()
                                } else {
                                    searchResults.first()
                                }
                                Log.d(TAG, "No exact match for '${wordPos.word}', considering first result: kanji='${firstResult.kanji}', reading='${firstResult.reading}'")
                                
                                // For conjugated verbs, accept any valid deinflection result
                                val isConjugatedVerb = wordPos.word.length > 1 && 
                                                       firstResult.isDeinflectedValidConjugation
                                
                                when {
                                    // Exact match on kanji field
                                    firstResult.kanji == wordPos.word -> {
                                        Log.d(TAG, "Using first result as kanji exactly matches our word")
                                        firstResult
                                    }
                                    // Accept conjugated verb forms of common verbs
                                    isConjugatedVerb -> {
                                        Log.d(TAG, "Using first result as valid conjugated verb: '${wordPos.word}' → '${firstResult.reading}'")
                                        firstResult
                                    }
                                    else -> {
                                        Log.d(TAG, "First result kanji '${firstResult.kanji}' doesn't exactly match '${wordPos.word}', skipping fallback")
                                        null
                                    }
                                }
                            }
                            
                            if (bestResult != null) {
                                // Debug logging for 気をつけよう result selection
                                if (wordPos.word == "気をつけよう") {
                                    Log.d(TAG, "🔍 Debug 気をつけよう: Selected result - kanji='${bestResult.kanji}', reading='${bestResult.reading}', meanings='${bestResult.meanings.take(3).joinToString(", ")}'")
                                }
                                val meanings = bestResult.meanings.take(3).joinToString(", ")
                                
                                // For katakana words, use the word itself as reading to preserve original form
                                val reading = if (isAllKatakana(wordPos.word)) {
                                    wordPos.word // Keep original katakana (e.g., プロ stays プロ, not ぷろ)
                                } else {
                                    bestResult.reading // Use dictionary reading for other words
                                }
                                
                                val baseFormForCard = wordPos.getBaseFormForLookup()
                                Log.d(TAG, "📋 Creating WordCard: '${wordPos.word}' with baseForm: '$baseFormForCard' (kuromojiToken.baseForm: '${wordPos.kuromojiToken?.baseForm}', explicit.baseForm: '${wordPos.baseForm}')")
                                
                                val wordCard = WordCardInfo(
                                    word = wordPos.word,
                                    reading = reading,
                                    meanings = meanings,
                                    startPosition = wordPos.startPosition,
                                    endPosition = wordPos.endPosition,
                                    baseForm = baseFormForCard
                                )
                                extractedWordCards.add(wordCard)
                                Log.d(TAG, "Added word card: ${wordCard.word} - ${wordCard.reading}")
                            } else {
                                // No good match found, try splitting into individual characters
                                Log.d(TAG, "No exact match found for '${wordPos.word}', splitting into individual characters")
                                splitIntoIndividualCharacters(wordPos, extractedWordCards, repository)
                            }
                        } else {
                            // Even if not in dictionary (like single kanji), still add it (same as OCR view)
                            val wordCard = WordCardInfo(
                                word = wordPos.word,
                                reading = wordPos.word, // Use the word itself as reading for katakana
                                meanings = "", // No meanings available
                                startPosition = wordPos.startPosition,
                                endPosition = wordPos.endPosition,
                                baseForm = wordPos.getBaseFormForLookup()
                            )
                            extractedWordCards.add(wordCard)
                            Log.d(TAG, "Added word card without dictionary entry: ${wordCard.word}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error creating word card for '${wordPos.word}': ${e.message}")
                    }
                }
                
                // Update UI on main thread
                runOnUiThread {
                    wordCards = extractedWordCards
                    wordCardAdapter.updateData(wordCards)
                    
                    // Initialize highlighting for first word card
                    if (wordCards.isNotEmpty()) {
                        highlightWordInTextByPosition(0)
                    }
                    
                    // Update word count badge
                    binding.wordCountBadge.text = wordCards.size.toString()
                    binding.wordCountBadge.visibility = if (wordCards.isNotEmpty()) View.VISIBLE else View.GONE
                    
                    Log.d(TAG, "Loaded ${wordCards.size} word cards")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading words", e)
                runOnUiThread {
                    Toast.makeText(this@DictionaryTextReaderActivity, "Error loading words", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun highlightWordInText(wordCard: WordCardInfo) {
        Log.d(TAG, "Highlighting word in text: '${wordCard.word}' at ${wordCard.startPosition}-${wordCard.endPosition}")
        binding.textSelectionView.highlightWord(wordCard.startPosition, wordCard.endPosition)
    }
    
    private fun highlightWordInTextByPosition(position: Int) {
        if (position >= 0 && position < wordCards.size) {
            val wordCard = wordCards[position]
            Log.d(TAG, "Highlighting word: '${wordCard.word}' at position ${wordCard.startPosition}-${wordCard.endPosition}")
            
            // Special debug logging for 名付けられた
            if (wordCard.word.contains("名付け")) {
                Log.d(TAG, "DEBUG 名付け: word='${wordCard.word}', start=${wordCard.startPosition}, end=${wordCard.endPosition}")
                val textLength = binding.textSelectionView.getDisplayTextLength()
                Log.d(TAG, "DEBUG 名付け: display text length=$textLength")
                if (wordCard.startPosition >= 0 && wordCard.endPosition <= textLength && wordCard.startPosition < wordCard.endPosition) {
                    Log.d(TAG, "DEBUG 名付け: Position range is valid, attempting highlight")
                } else {
                    Log.d(TAG, "DEBUG 名付け: Position range is INVALID")
                }
            }
            
            binding.textSelectionView.highlightWord(wordCard.startPosition, wordCard.endPosition)
        } else {
            // Clear highlighting if position is invalid
            binding.textSelectionView.clearWordHighlight()
        }
    }

    private fun highlightCorrespondingWordCard(selectedText: String) {
        // Find the word card that corresponds to the selected text
        val matchingCardIndex = wordCards.indexOfFirst { wordCard ->
            // Check if the selected text matches the word or overlaps with its position
            wordCard.word == selectedText
        }
        
        if (matchingCardIndex != -1) {
            Log.d(TAG, "Highlighting word card at position $matchingCardIndex for text '$selectedText'")
            wordCardAdapter.highlightCard(matchingCardIndex)
            
            // Scroll to the matching word card
            binding.wordCardsRecyclerView.smoothScrollToPosition(matchingCardIndex)
        }
    }

    private fun copyTextToClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Dictionary Text", paragraphText)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy text to clipboard", e)
            Toast.makeText(this, "Failed to copy text", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openWordDetailActivity(wordCard: WordCardInfo) {
        Log.d(TAG, "Opening word detail for: '${wordCard.word}'")
        
        // Do a fresh dictionary lookup to get complete word information
        lifecycleScope.launch {
            try {
                val repository = DictionaryRepository.getInstance(this@DictionaryTextReaderActivity)
                // Use base form for search, fall back to surface form if no base form available
                val searchTerm = wordCard.baseForm ?: wordCard.word
                Log.d(TAG, "🔍 Searching with base form: '$searchTerm' (original: '${wordCard.word}', baseForm: '${wordCard.baseForm}')")
                val searchResults = repository.search(searchTerm, limit = 10)
                
                if (searchResults.isNotEmpty()) {
                    // Find the best matching result
                    val bestResult = searchResults.find { result ->
                        result.kanji == wordCard.word || result.reading == wordCard.word
                    } ?: searchResults.find { result ->
                        isAllKatakana(wordCard.word) && result.reading == wordCard.word
                    } ?: run {
                        // Apply same preference logic for word detail activity
                        val baseFormForSearch = wordCard.baseForm ?: wordCard.word
                        if (baseFormForSearch == "やる" || wordCard.word == "やる") {
                            // Find 行る with "to do, to undertake" meaning
                            searchResults.find { it.reading == "やる" && it.kanji == "行る" }
                                ?: searchResults.find { result ->
                                    result.reading == "やる" && result.meanings.any { meaning ->
                                        (meaning.startsWith("to do", ignoreCase = true) && 
                                         !meaning.contains("someone", ignoreCase = true)) ||
                                        meaning.contains("to undertake", ignoreCase = true) ||
                                        meaning.contains("to perform", ignoreCase = true) ||
                                        meaning.contains("to play", ignoreCase = true)
                                    }
                                } ?: searchResults.first()
                        } else if (baseFormForSearch == "ほう" || wordCard.word == "ほう") {
                            // Find 方 with "direction, way, method" meaning instead of 法 with "law, act"
                            searchResults.find { it.reading == "ほう" && it.kanji == "方" }
                                ?: searchResults.find { result ->
                                    result.reading == "ほう" && result.meanings.any { meaning ->
                                        meaning.contains("direction", ignoreCase = true) ||
                                        meaning.contains("way", ignoreCase = true) ||
                                        meaning.contains("method", ignoreCase = true) ||
                                        meaning.contains("side", ignoreCase = true) ||
                                        meaning.contains("person", ignoreCase = true)
                                    }
                                } ?: searchResults.first()
                        } else {
                            searchResults.first()
                        }
                    }
                    
                    // Launch WordDetailActivity with complete word information
                    // Use the base form (kanji or reading) from the dictionary result for the title
                    val displayWord = bestResult.kanji ?: bestResult.reading
                    val intent = Intent(this@DictionaryTextReaderActivity, WordDetailActivity::class.java).apply {
                        putExtra("word", displayWord)  // Use base form from dictionary
                        putExtra("reading", bestResult.reading)
                        putExtra("meanings", ArrayList(bestResult.meanings))
                        putExtra("frequency", bestResult.frequency)
                        putExtra("selectedText", wordCard.word)  // Keep original form for context
                    }
                    
                    startActivity(intent)
                } else {
                    // Fallback to basic information if no dictionary entry found
                    // Use base form if available, otherwise use the original word
                    val displayWord = wordCard.baseForm ?: wordCard.word
                    val intent = Intent(this@DictionaryTextReaderActivity, WordDetailActivity::class.java).apply {
                        putExtra("word", displayWord)  // Use base form for title
                        putExtra("reading", wordCard.reading)
                        putExtra("meanings", ArrayList(wordCard.meanings.split(", ").filter { it.isNotBlank() }))
                        putExtra("frequency", 0)
                        putExtra("isJMNEDict", false)
                        putExtra("selectedText", wordCard.word)  // Keep original form for context
                    }
                    
                    startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error looking up word details for: ${wordCard.word}", e)
                // Fallback to basic information on error
                // Use base form if available, otherwise use the original word
                val displayWord = wordCard.baseForm ?: wordCard.word
                val intent = Intent(this@DictionaryTextReaderActivity, WordDetailActivity::class.java).apply {
                    putExtra("word", displayWord)  // Use base form for title
                    putExtra("reading", wordCard.reading)
                    putExtra("meanings", ArrayList(wordCard.meanings.split(", ").filter { it.isNotBlank() }))
                    putExtra("frequency", 0)
                    putExtra("isJMNEDict", false)
                    putExtra("selectedText", wordCard.word)  // Keep original form for context
                }
                
                startActivity(intent)
            }
        }
    }
    
    private fun openWordDetailForSelectedText(selectedText: String) {
        Log.d(TAG, "Opening word detail for selected text: '$selectedText'")
        
        // Check if it's a single kanji character - use kanji lookup
        if (selectedText.length == 1 && isKanji(selectedText)) {
            lifecycleScope.launch {
                try {
                    val repository = DictionaryRepository.getInstance(this@DictionaryTextReaderActivity)
                    val kanjiResults = repository.getKanjiInfo(listOf(selectedText))
                    if (kanjiResults.isNotEmpty()) {
                        val kanjiResult = kanjiResults.first()
                        
                        val intent = Intent(this@DictionaryTextReaderActivity, WordDetailActivity::class.java).apply {
                            putExtra("word", kanjiResult.kanji)
                            putExtra("reading", kanjiResult.onReadings.joinToString(", "))
                            putExtra("meanings", ArrayList(kanjiResult.meanings))
                            putExtra("selectedText", selectedText)
                        }
                        
                        startActivity(intent)
                        return@launch
                    } else {
                        // No kanji info found - try regular word lookup
                        performWordLookup(selectedText)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error looking up kanji info for: $selectedText", e)
                    performWordLookup(selectedText)
                }
            }
        } else {
            // Regular word lookup for non-single-kanji selections
            performWordLookup(selectedText)
        }
    }
    
    private fun performWordLookup(selectedText: String) {
        lifecycleScope.launch {
            try {
                val repository = DictionaryRepository.getInstance(this@DictionaryTextReaderActivity)
                val searchResults = repository.search(selectedText, limit = 10)
                
                if (searchResults.isNotEmpty()) {
                    Log.d(TAG, "Found ${searchResults.size} search results for '$selectedText'")
                    
                    // Prioritize exact matches for both katakana and hiragana (same logic as OCR view)
                    val isKatakanaSelection = selectedText.all { it.toInt() in 0x30A0..0x30FF }
                    val sortedResults = if (isKatakanaSelection) {
                        // For katakana, prioritize entries where reading field matches the selected text exactly
                        // Most katakana words have null kanji and the word is in the reading field
                        searchResults.sortedBy { result ->
                            when {
                                // For katakana entries, the word is usually in the reading field
                                result.reading == selectedText -> 0  // Exact match in reading field
                                result.kanji == selectedText -> 1  // Exact match in kanji field (rare for katakana)
                                result.reading?.startsWith(selectedText) == true -> 2  // Starts with selected text
                                result.kanji == null && result.reading != null -> 3  // Other katakana words
                                else -> 4  // Kanji entries with the reading (like 帆)
                            }
                        }
                    } else {
                        // For hiragana and kanji searches, also prioritize exact matches
                        searchResults.sortedBy { result ->
                            when {
                                result.reading == selectedText -> 0  // Exact match in reading (e.g., ため)
                                result.kanji == selectedText -> 1  // Exact match in kanji
                                result.reading?.startsWith(selectedText) == true -> 2  // Reading starts with text
                                result.kanji?.startsWith(selectedText) == true -> 3  // Kanji starts with text
                                else -> 4  // Other entries
                            }
                        }
                    }
                    
                    val bestResult = sortedResults.first()
                    Log.d(TAG, "Best result: kanji='${bestResult.kanji}', reading='${bestResult.reading}'")
                    
                    val intent = Intent(this@DictionaryTextReaderActivity, WordDetailActivity::class.java).apply {
                        putExtra("word", bestResult.kanji ?: bestResult.reading)
                        putExtra("reading", bestResult.reading)
                        putExtra("meanings", ArrayList(bestResult.meanings))
                        putExtra("frequency", bestResult.frequency)
                        putExtra("selectedText", selectedText)
                    }
                    
                    startActivity(intent)
                } else {
                    Log.d(TAG, "No results found for: $selectedText")
                    runOnUiThread {
                        showWordNotFound(selectedText)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing word lookup for: $selectedText", e)
                Toast.makeText(this@DictionaryTextReaderActivity, "Error looking up word", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun isKanji(text: String): Boolean {
        return text.all { char ->
            char in '\u4E00'..'\u9FAF' // CJK Unified Ideographs range
        }
    }
    
    private fun isAllKatakana(text: String): Boolean {
        return text.all { char ->
            char in '\u30A0'..'\u30FF' // Katakana range
        }
    }
    
    private fun initializeFurigana() {
        try {
            // Initialize furigana processor (similar to OCR view)
            val repository = DictionaryRepository.getInstance(this)
            val deinflectionEngine = TenTenStyleDeinflectionEngine()
            val tagDictLoader = TagDictSQLiteLoader(this)
            
            furiganaProcessor = FuriganaProcessor(
                dictionaryRepository = repository,
                deinflectionEngine = deinflectionEngine,
                tagDictLoader = tagDictLoader
            )
            
            // Set furigana processor for text selection view
            furiganaProcessor?.let { processor ->
                binding.textSelectionView.setFuriganaProcessor(processor)
            }
            
            // Initialize furigana toggle appearance
            updateFuriganaToggleAppearance()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing furigana: ${e.message}", e)
        }
    }
    
    private fun toggleFuriganaMode() {
        isFuriganaMode = !isFuriganaMode
        updateFuriganaToggleAppearance()
        binding.textSelectionView.setShowFurigana(isFuriganaMode)
        Log.d(TAG, "Furigana mode: $isFuriganaMode")
    }
    
    private fun updateFuriganaToggleAppearance() {
        if (isFuriganaMode) {
            // Enabled state - filled button
            binding.furiganaToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.teal_700)
            )
            binding.furiganaToggle.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.white))
            binding.furiganaToggle.strokeWidth = 0
        } else {
            // Disabled state - outlined button
            binding.furiganaToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, android.R.color.transparent)
            )
            binding.furiganaToggle.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.teal_700))
            binding.furiganaToggle.strokeWidth = 2 // 1dp stroke width
        }
    }
    
    private fun translateText() {
        val textToTranslate = paragraphText.trim()
        
        if (textToTranslate.isBlank()) {
            Toast.makeText(this, "No text to translate", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show loading state
        binding.translateTextButton.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val googleTranslateService = GoogleTranslateService()
                val translatedText = googleTranslateService.translateText(textToTranslate)
                
                runOnUiThread {
                    // Re-enable button
                    binding.translateTextButton.isEnabled = true
                    
                    // Show translated text in a dialog or toast
                    translatedText?.let { translation ->
                        showTranslationDialog(textToTranslate, translation)
                    } ?: run {
                        Toast.makeText(this@DictionaryTextReaderActivity, "Translation returned empty result", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Translation failed", e)
                runOnUiThread {
                    binding.translateTextButton.isEnabled = true
                    Toast.makeText(this@DictionaryTextReaderActivity, "Translation failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showTranslationDialog(originalText: String, translatedText: String) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
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
        
        // Set rounded corners background (same as OCR view)
        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_rounded_background)
        
        dialog.show()
    }
    
    private fun copyToClipboard(label: String, text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy to clipboard", e)
            Toast.makeText(this, "Failed to copy", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showWordNotFound(selectedText: String) {
        Log.d(TAG, "Showing word not found for: $selectedText")
        
        notFoundWordText.text = selectedText
        wordNotFoundLayout.visibility = View.VISIBLE
        showTopPopup()
    }
    
    private fun showTopPopup() {
        topPopup.visibility = View.VISIBLE
        topPopup.animate()
            .translationY(0f)
            .setDuration(300)
            .start()
    }
    
    private fun hideTopPopup() {
        topPopup.animate()
            .translationY(-400f)
            .setDuration(300)
            .withEndAction {
                topPopup.visibility = View.GONE
                wordNotFoundLayout.visibility = View.GONE
            }
            .start()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            // First check if drawer is open
            binding.drawerLayout.isDrawerOpen(GravityCompat.START) -> {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            // Then check if the top popup is visible
            topPopup.visibility == View.VISIBLE -> {
                hideTopPopup()
            }
            // No drawer or popup visible, go back normally
            else -> {
                @Suppress("DEPRECATION")
                super.onBackPressed()
            }
        }
    }
    
    /**
     * Stitch Japanese words that are broken across lines due to OCR line breaks
     * Example: "輝か しい" -> "輝かしい"
     */
    private fun stitchBrokenJapaneseWords(text: String): String {
        if (text.isBlank()) return text
        
        val result = StringBuilder()
        var i = 0
        
        while (i < text.length) {
            val char = text[i]
            
            // Add current character
            result.append(char)
            
            // If current char is Japanese, check if we should remove following whitespace
            if (isJapaneseChar(char.toString())) {
                i++
                
                // Skip any whitespace/line breaks if the next non-whitespace char is also Japanese
                while (i < text.length && isWhitespaceOrLineBreak(text[i])) {
                    val nextNonWhitespace = findNextNonWhitespace(text, i)
                    if (nextNonWhitespace != -1 && isJapaneseChar(text[nextNonWhitespace].toString())) {
                        // Skip whitespace - it's between Japanese characters
                        i = nextNonWhitespace
                        break
                    } else {
                        // Keep whitespace - it's between Japanese and non-Japanese
                        result.append(' ') // Normalize to single space
                        i++
                        break
                    }
                }
            } else {
                i++
            }
        }
        
        return result.toString()
    }
    
    /**
     * Check if character is Japanese (kanji, hiragana, or katakana)
     */
    private fun isJapaneseChar(char: String): Boolean {
        if (char.isEmpty()) return false
        val codePoint = char.codePointAt(0)
        return (codePoint in 0x3040..0x309F) || // Hiragana
                (codePoint in 0x30A0..0x30FF) || // Katakana
                (codePoint in 0x4E00..0x9FAF)    // Kanji
    }
    
    /**
     * Check if character is whitespace or line break
     */
    private fun isWhitespaceOrLineBreak(char: Char): Boolean {
        return char.isWhitespace() || char == '\n' || char == '\r' || char == '\u3000'
    }
    
    /**
     * Find next non-whitespace character position
     */
    private fun findNextNonWhitespace(text: String, startPos: Int): Int {
        for (i in startPos until text.length) {
            if (!isWhitespaceOrLineBreak(text[i])) {
                return i
            }
        }
        return -1
    }
    
    /**
     * Split a word into individual characters and look each one up in the dictionary
     */
    private suspend fun splitIntoIndividualCharacters(wordPos: JapaneseWordExtractor.WordPosition, extractedWordCards: MutableList<WordCardInfo>, repository: DictionaryRepository?) {
        // Only split kanji words, not hiragana/katakana
        if (!wordPos.word.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS }) {
            Log.d(TAG, "Skipping character splitting for '${wordPos.word}' - contains no kanji")
            return
        }
        
        Log.d(TAG, "Splitting kanji word '${wordPos.word}' into individual characters")
        
        var currentPos = wordPos.startPosition
        for (char in wordPos.word) {
            val charString = char.toString()
            
            // Try to look up each character in the dictionary
            try {
                val charResults = repository?.search(charString, limit = 5) ?: emptyList()
                val exactMatch = charResults.find { it.kanji == charString || it.reading == charString }
                
                val wordCard = if (exactMatch != null) {
                    // Found dictionary entry for this character
                    val meanings = exactMatch.meanings.take(2).joinToString(", ") // Fewer meanings for individual chars
                    WordCardInfo(
                        word = charString,
                        reading = exactMatch.reading,
                        meanings = meanings,
                        startPosition = currentPos,
                        endPosition = currentPos + 1,
                        baseForm = charString // For individual characters, word and base form are the same
                    )
                } else {
                    // No dictionary entry, use character as-is
                    WordCardInfo(
                        word = charString,
                        reading = charString,
                        meanings = "",
                        startPosition = currentPos,
                        endPosition = currentPos + 1,
                        baseForm = charString // For individual characters, word and base form are the same
                    )
                }
                
                extractedWordCards.add(wordCard)
                Log.d(TAG, "Added individual character: $charString → ${wordCard.reading}")
                
            } catch (e: Exception) {
                Log.w(TAG, "Error looking up character '$charString': ${e.message}")
                // Fallback: add character as-is
                val wordCard = WordCardInfo(
                    word = charString,
                    reading = charString,
                    meanings = "",
                    startPosition = currentPos,
                    endPosition = currentPos + 1,
                    baseForm = charString // For individual characters, word and base form are the same
                )
                extractedWordCards.add(wordCard)
            }
            
            currentPos++
        }
    }
    
}