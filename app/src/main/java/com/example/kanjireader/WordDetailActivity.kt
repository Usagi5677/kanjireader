package com.example.kanjireader

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.appcompat.widget.Toolbar
import android.widget.TextView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.chip.Chip
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.kanjireader.ui.AddToListBottomSheet
import androidx.activity.viewModels
import com.example.kanjireader.viewmodel.WordListViewModel
import com.example.kanjireader.database.WordListEntity

enum class DetailChipType {
    VERB_GODAN, VERB_ICHIDAN, VERB_TRANSITIVE, VERB_INTRANSITIVE, VERB_AUXILIARY,
    ADJECTIVE, NOUN, PARTICLE, ADVERB, COMMON, FREQUENCY, OTHER,
    // JMNEDict types
    JMNE_PERSON, JMNE_PLACE, JMNE_COMPANY, JMNE_ORGANIZATION, JMNE_GIVEN, JMNE_SURNAME, JMNE_STATION, JMNE_OTHER,
    // Form-level tags (kanji/kana usage)
    FORM_RARELY_USED_KANJI, FORM_IRREGULAR_KANJI, FORM_OUTDATED_KANJI, FORM_SEARCH_ONLY_KANJI,
    FORM_RARELY_USED_KANA, FORM_IRREGULAR_KANA, FORM_OUTDATED_KANA, FORM_SEARCH_ONLY_KANA,
    FORM_ATEJI, FORM_GIKUN, FORM_IRREGULAR_OKURIGANA, FORM_KANA_ONLY
}

class WordDetailActivity : AppCompatActivity(), KanjiTabFragment.OnKanjiCountListener, MeaningsTabFragment.OnMeaningsCountListener, VariantsMiddleTabFragment.OnVariantsCountListener {

    companion object {
        const val EXTRA_WORD_RESULT = "word_result"
        private const val TAG = "WordDetailActivity"
    }

    // UI Components
    private lateinit var toolbar: Toolbar
    private lateinit var wordText: TextView
    private lateinit var readingText: TextView
    private lateinit var pitchAccentScrollView: HorizontalScrollView
    private lateinit var pitchAccentContainer: LinearLayout
    private lateinit var tagChipGroup: ChipGroup
    private lateinit var middleTabLayout: TabLayout
    private lateinit var middleViewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private var kanjiBadge: TextView? = null
    private var kanjiTabTitle: TextView? = null
    private var meaningsBadge: TextView? = null
    private var meaningsTabTitle: TextView? = null
    private var variantsBadge: TextView? = null
    private var variantsTabTitle: TextView? = null

    private lateinit var grammarChipGroup: ChipGroup

    private var currentWord: String = ""
    
    // Store reference to page change callback to disable automatic search
    private var pageChangeCallback: ViewPagerCallback? = null

    // Data
    private var wordResult: EnhancedWordResult? = null
    
    // ViewModel for word list operations
    private val wordListViewModel: WordListViewModel by viewModels()
    
    // Track current heart icon state
    private var addToListMenuItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_detail)

        // Get the word from intent FIRST
        currentWord = intent.getStringExtra("word") ?: ""

        initializeViews()
        setupToolbar()
        loadWordData()
        setupTabs()
        setupMiddleTabs()
        setupObservers()
    }
    

    private fun initializeViews() {
        toolbar = findViewById<Toolbar>(R.id.toolbar)
        wordText = findViewById<TextView>(R.id.wordText)
        readingText = findViewById<TextView>(R.id.readingText)
        pitchAccentScrollView = findViewById<HorizontalScrollView>(R.id.pitchAccentScrollView)
        pitchAccentContainer = findViewById<LinearLayout>(R.id.pitchAccentContainer)
        tagChipGroup = findViewById<ChipGroup>(R.id.tagChipGroup)
        grammarChipGroup = findViewById<ChipGroup>(R.id.grammarChipGroup)
        middleTabLayout = findViewById<TabLayout>(R.id.middleTabLayout)
        middleViewPager = findViewById<ViewPager2>(R.id.middleViewPager)
        tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        viewPager = findViewById<ViewPager2>(R.id.viewPager)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detail"  // Changed from "Word Detail" to "Detail"
        
        // Set the navigation icon color to green for better visibility
        val navigationIcon = toolbar.navigationIcon
        navigationIcon?.setTint(ContextCompat.getColor(this, R.color.green_700))
        
        toolbar.setNavigationOnClickListener { finish() }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_word_detail, menu)
        addToListMenuItem = menu?.findItem(R.id.action_add_to_list)
        updateHeartIcon()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_to_list -> {
                handleHeartClick()
                true
            }
            R.id.action_manage_lists -> {
                showAddToListBottomSheet()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAddToListBottomSheet() {
        val currentWordResult = wordResult
        if (currentWordResult == null) {
            Log.w(TAG, "No word result available for adding to list")
            return
        }

        val bottomSheet = AddToListBottomSheet.newInstance(currentWordResult)
        bottomSheet.onWordListChanged = { updateHeartIcon() }
        bottomSheet.show(supportFragmentManager, "AddToListBottomSheet")
    }
    
    private fun handleHeartClick() {
        val currentWordResult = wordResult
        if (currentWordResult == null) {
            Log.w(TAG, "No word result available for adding to list")
            return
        }
        
        lifecycleScope.launch {
            try {
                val listIds = wordListViewModel.getListIdsForWord(currentWordResult)
                
                if (listIds.isEmpty()) {
                    // Word not in any list - add to first list automatically
                    val allLists = wordListViewModel.getAllWordListsSync()
                    if (allLists.isNotEmpty()) {
                        val firstList = allLists.first()
                        wordListViewModel.addWordToSingleList(currentWordResult, firstList.listId)
                    } else {
                        // No lists exist - show bottom sheet to create one
                        showAddToListBottomSheet()
                    }
                } else {
                    // Word is in lists - remove from first list only
                    val allLists = wordListViewModel.getAllWordListsSync()
                    if (allLists.isNotEmpty()) {
                        val firstList = allLists.first()
                        if (firstList.listId in listIds) {
                            wordListViewModel.removeWordFromSingleList(firstList.listId, currentWordResult)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling heart click", e)
            }
        }
    }
    
    private fun updateHeartIcon() {
        val result = wordResult ?: return
        
        lifecycleScope.launch {
            try {
                // Small delay to ensure database operation completes
                kotlinx.coroutines.delay(100)
                
                val listIds = wordListViewModel.getListIdsForWord(result)
                val isInAnyList = listIds.isNotEmpty()
                
                // Update icon on main thread
                runOnUiThread {
                    addToListMenuItem?.icon = ContextCompat.getDrawable(
                        this@WordDetailActivity,
                        if (isInAnyList) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                    )
                }
                
                Log.d(TAG, "Updated heart icon: isInAnyList=$isInAnyList, listIds=$listIds")
            } catch (e: Exception) {
                Log.e(TAG, "Error checking word list status", e)
            }
        }
    }

    private fun loadWordData() {
        // Clear any existing data first to prevent flashing old content
        wordText.text = ""
        readingText.text = ""
        tagChipGroup.removeAllViews()
        grammarChipGroup.removeAllViews()
        pitchAccentContainer.removeAllViews()
        pitchAccentScrollView.visibility = View.GONE
        
        // Get data from intent
        val word = intent.getStringExtra("word") ?: ""
        val reading = intent.getStringExtra("reading") ?: ""
        val meanings = intent.getStringArrayListExtra("meanings") ?: arrayListOf()
        val frequency = intent.getIntExtra("frequency", 0)
        val isJMNEDict = intent.getBooleanExtra("isJMNEDict", false)
        
        // Debug logging for JMnedict entries
        if (word == "上") {
            Log.d("WordDetailActivity", "🔍 Intent data: word='$word', reading='$reading', isJMNEDict=$isJMNEDict")
        }

        // Immediately display basic data to prevent blank screen
        displayBasicWordData(word, reading, meanings)

        // Try to get more detailed information from the dictionary
        val repository = DictionaryRepository.getInstance(this)
        val tagDictLoader = TagDictSQLiteLoader(this)

        lifecycleScope.launch {
            // Search for the word to get full details using the new FTS5 search
            // For JMNEDict entries, we need to search more broadly to include proper nouns
            val searchResults = if (isJMNEDict) {
                repository.search(word, limit = 500) // Get many more results to find JMNEDict entries which are deprioritized
            } else {
                repository.search(word, limit = 100) // Increased from 20 to catch more variants
            }

            // Debug logging for JMnedict entries
            if (word == "上") {
                Log.d("WordDetailActivity", "🔍 Search found ${searchResults.size} results")
                val jmnedictResults = searchResults.filter { it.isJMNEDictEntry }
                Log.d("WordDetailActivity", "🔍 JMnedict results: ${jmnedictResults.size}")
                jmnedictResults.take(5).forEach {
                    Log.d("WordDetailActivity", "🔍   ${it.kanji}/${it.reading} isJMNEDict=${it.isJMNEDictEntry}")
                }
            }

            // Find the exact match - respect whether user clicked kanji or hiragana entry
            val exactMatch = searchResults.firstOrNull {
                if (isKanjiString(word)) {
                    // User clicked kanji entry - match kanji exactly with reading
                    // For JMNEDict entries, also check if it's actually a JMNEDict entry
                    it.kanji == word && it.reading == reading && 
                    (!isJMNEDict || it.isJMNEDictEntry == isJMNEDict)
                } else {
                    // User clicked hiragana entry - match reading exactly and ensure no kanji
                    it.reading == word && it.kanji.isNullOrEmpty()
                }
            } ?: searchResults.firstOrNull {
                // Fallback: broader match but still respect entry type and JMNEDict status
                if (isKanjiString(word)) {
                    it.kanji == word && (!isJMNEDict || it.isJMNEDictEntry == isJMNEDict)
                } else {
                    it.reading == word
                }
            }
            
            // Debug logging for JMnedict entries
            if (word == "上") {
                Log.d("WordDetailActivity", "🔍 Exact match found: ${exactMatch?.kanji}/${exactMatch?.reading} isJMNEDict=${exactMatch?.isJMNEDictEntry}")
            }
            

            if (exactMatch != null && tagDictLoader != null) {
                // Enhance with tag data
                val enhancedResult = tagDictLoader.enhanceWordResult(exactMatch)
                
                // Use frequency from Intent if available, otherwise use enhanced result's frequency
                val finalFrequency = if (frequency > 0) frequency else enhancedResult.numericFrequency
                val updatedEnhanced = enhancedResult.copy(numericFrequency = finalFrequency)

                runOnUiThread {
                    // Update with enhanced data
                    wordResult = updatedEnhanced
                    // Pass the original search result for JMNEDict tag handling
                    displayEnhancedWordData(updatedEnhanced, exactMatch)
                    // Fetch and display pitch accent data
                    loadPitchAccentData(updatedEnhanced.kanji ?: updatedEnhanced.reading, updatedEnhanced.reading)
                    updateHeartIcon()
                }
            } else {
                // Use basic data from intent
                wordResult = EnhancedWordResult(
                    kanji = if (word != reading) word else null,
                    reading = reading,
                    meanings = meanings,
                    partOfSpeech = emptyList(),
                    isCommon = false,
                    numericFrequency = frequency
                )

                displayBasicWordData(word, reading, meanings)
                // Also try to load pitch accent for basic data
                loadPitchAccentData(word, reading)
                updateHeartIcon()
            }
        }
    }
    
    private fun setupObservers() {
        // Observe word list changes to update heart icon immediately
        wordListViewModel.operationSuccess.observe(this) { message ->
            if (message != null) {
                updateHeartIcon()
            }
        }
        
        // Also observe when lists change to update icon
        wordListViewModel.allWordLists.observe(this) {
            updateHeartIcon()
        }
    }

    private fun displayEnhancedWordData(enhanced: EnhancedWordResult, originalResult: WordResult? = null) {
        // Display the main word
        wordText.text = enhanced.kanji ?: enhanced.reading
        readingText.text = if (enhanced.kanji != null) enhanced.reading else ""
        readingText.visibility = if (enhanced.kanji != null) View.VISIBLE else View.GONE

        // Add JLPT tags
        addJlptTags(enhanced.kanji ?: enhanced.reading)

        // Add grammar tags (Common, Part of Speech, etc.)
        addGrammarTags(enhanced, originalResult)

        // Setup middle tabs with meanings data
        setupMiddleTabsWithData(enhanced.meanings)
    }

    private fun displayBasicWordData(word: String, reading: String, meanings: List<String>) {
        wordText.text = word
        readingText.text = reading
        readingText.visibility = if (word != reading) View.VISIBLE else View.GONE

        // Add JLPT tags
        addJlptTags(word)

        // Setup middle tabs with meanings data
        setupMiddleTabsWithData(meanings)
    }

    private fun addJlptTags(word: String) {
        tagChipGroup.removeAllViews()

        // Get JLPT level for each kanji using SQLite database
        val repository = DictionaryRepository.getInstance(this)
        val kanjiChars = word.filter { isKanji(it.toString()) }

        lifecycleScope.launch {
            val kanjiList = kanjiChars.map { it.toString() }
            val kanjiDetails = repository.getKanjiInfo(kanjiList)
            
            runOnUiThread {
                // Use database results (includes fallback for empty database)
                for (kanjiDetail in kanjiDetails) {
                    // Show all kanji, even if they don't have JLPT levels
                    addKanjiJlptChip(kanjiDetail.kanji, kanjiDetail.jlptLevel)
                }
            }
        }
    }

    private fun addGrammarTags(enhanced: EnhancedWordResult, originalResult: WordResult? = null) {
        grammarChipGroup.removeAllViews()

        // Check if this is a JMNEDict entry
        val isJMNEEntry = originalResult?.isJMNEDictEntry == true
        
        // Get all tags for this word to separate form-level tags from grammatical tags
        val allTags = getAllTagsForWord(enhanced, originalResult)
        val (formTags, grammarTags) = separateFormAndGrammarTags(allTags)
        
        // Add frequency chip to grammar section first
        if (enhanced.numericFrequency > 0) {
            val freqChip = createFrequencyChip(formatFrequency(enhanced.numericFrequency), enhanced.numericFrequency)
            grammarChipGroup.addView(freqChip)
        }
        
        // Add common tag to grammar section second
        if (enhanced.isCommon) {
            val commonChip = createStyledChip("common", DetailChipType.COMMON)
            grammarChipGroup.addView(commonChip)
        }
        
        // Add grammar tags to the grammar section (proper ordering: frequency, common, grammar tags, then form tags, then other tags)
        grammarTags.forEach { pos ->
            // Auto-detect if this is a JMNEDict tag regardless of entry type
            val chipType = if (isJMNEDictTag(pos)) {
                getJMNEChipTypeForTag(pos)
            } else {
                getChipTypeForTag(pos)
            }
            val chip = createStyledChip(getSimplifiedPos(pos), chipType)
            grammarChipGroup.addView(chip)
        }

        // Add form-level tags to the grammar section (after grammar tags)
        formTags.forEach { tag ->
            val chipType = getChipTypeForTag(tag)
            val displayText = getFormTagDisplayText(tag)
            val chip = createStyledChip(displayText, chipType)
            grammarChipGroup.addView(chip)
        }

        // Add other tags if available (not for JMNEDict entries)
        if (!isJMNEEntry) {
            enhanced.frequencyTags.forEach { freq ->
                val chip = createStyledChip(freq, DetailChipType.OTHER)
                grammarChipGroup.addView(chip)
            }

            // Add field tags (medicine, computing, etc.)
            enhanced.fields.forEach { field ->
                val chip = createStyledChip(field, DetailChipType.OTHER)
                grammarChipGroup.addView(chip)
            }

            // Add style tags (formal, colloquial, etc.)
            enhanced.styles.take(3).forEach { style ->
                val chip = createStyledChip(style, DetailChipType.OTHER)
                grammarChipGroup.addView(chip)
            }
        }
    }

    /**
     * Get all tags for a word from the database
     */
    private fun getAllTagsForWord(enhanced: EnhancedWordResult, originalResult: WordResult?): List<String> {
        return try {
            val tagLoader = TagDictSQLiteLoader(this)
            
            // Use the new method that gets ALL tags without filtering
            val allTags = tagLoader.getAllTagsForKanjiReadingWithJMnedict(
                enhanced.kanji, 
                enhanced.reading, 
                originalResult?.isJMNEDictEntry == true
            )
            
            if (allTags.isNotEmpty()) {
                allTags
            } else {
                // Fallback to current data if no tags found
                enhanced.partOfSpeech
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get all tags for word: ${e.message}")
            enhanced.partOfSpeech // Fallback to current data
        }
    }

    /**
     * Separate form-level tags from grammatical tags
     */
    private fun separateFormAndGrammarTags(allTags: List<String>): Pair<List<String>, List<String>> {
        val formTags = mutableListOf<String>()
        val grammarTags = mutableListOf<String>()
        
        // Form-level and display tag patterns (tags to show in top section)
        val displayInTopSectionTags = setOf(
            // Form-level tags (kanji/kana usage)
            "rK", "iK", "oK", "sK",        // Kanji usage tags
            "rk", "ik", "ok", "sk",        // Kana usage tags  
            "ateji", "gikun", "io", "uk",   // Special usage tags
            // JMNEDict entity types (should be displayed prominently)
            "person", "place", "company", "organization", "given", "fem", "masc",
            "surname", "station", "group", "char", "fict", "work", "ev", "obj",
            "product", "serv", "relig", "dei", "ship", "leg", "myth", "creat",
            "oth", "unclass", "doc"
        )
        
        // Grammar tag patterns (part-of-speech tags)
        val grammarTagPrefixes = setOf("v", "adj", "n", "adv", "prt", "aux", "int", "exp", "pref", "suf")
        
        allTags.forEach { tag ->
            when {
                displayInTopSectionTags.contains(tag) -> formTags.add(tag)
                grammarTagPrefixes.any { tag.startsWith(it) } -> grammarTags.add(tag)
                else -> grammarTags.add(tag) // Default to grammar section
            }
        }
        
        return Pair(formTags, grammarTags)
    }


    /**
     * Get display text for form-level tags
     */
    private fun getFormTagDisplayText(tag: String): String {
        return when (tag) {
            // Form-level tags (kanji/kana usage)
            "rK" -> "rarely used kanji"
            "iK" -> "irregular kanji"
            "oK" -> "outdated kanji"
            "sK" -> "search-only kanji"
            "rk" -> "rarely used kana"
            "ik" -> "irregular kana"
            "ok" -> "outdated kana"
            "sk" -> "search-only kana"
            "ateji" -> "ateji"
            "gikun" -> "gikun"
            "io" -> "irregular okurigana"
            "uk" -> "kana only"
            // JMNEDict entity types
            "person" -> "person"
            "place" -> "place"
            "company" -> "company"
            "organization" -> "organization" 
            "given" -> "given name"
            "fem" -> "female given name"
            "masc" -> "male given name"
            "surname" -> "surname"
            "station" -> "station"
            "group" -> "group"
            "char" -> "character"
            "fict" -> "fiction"
            "work" -> "work"
            "ev" -> "event"
            "obj" -> "object"
            "product" -> "product"
            "serv" -> "service"
            "relig" -> "religion"
            "dei" -> "deity"
            "ship" -> "ship"
            "leg" -> "legend"
            "myth" -> "mythology"
            "creat" -> "creature"
            "oth" -> "other"
            "unclass" -> "unclassified"
            "doc" -> "document"
            else -> tag
        }
    }

    private fun addGrammarChip(text: String, colorResId: Int) {
        val chip = Chip(this).apply {
            this.text = text
            chipBackgroundColor = ContextCompat.getColorStateList(context, colorResId)
            isClickable = false
            chipStrokeWidth = 0f
            textSize = 12f
            chipMinHeight = 28f.dpToPx()
        }
        grammarChipGroup.addView(chip)
    }

    private fun addKanjiJlptChip(kanji: String, jlptLevel: Int?) {
        val inflater = LayoutInflater.from(this)
        val tagView = inflater.inflate(R.layout.kanji_jlpt_tag, tagChipGroup, false)

        val kanjiText = tagView.findViewById<TextView>(R.id.kanjiText)
        val jlptChip = tagView.findViewById<Chip>(R.id.jlptChip)

        kanjiText.text = kanji
        
        if (jlptLevel != null) {
            jlptChip.text = "JLPT N$jlptLevel"
            
            // Set the chip background color based on JLPT level
            val chipColorRes = when (jlptLevel) {
                5 -> R.color.jlpt_n5_light  // Light green
                4 -> R.color.jlpt_n4_light  // Lighter green
                3 -> R.color.jlpt_n3_light  // Light amber
                2 -> R.color.jlpt_n2_light  // Light orange
                1 -> R.color.jlpt_n1_light  // Light red
                else -> R.color.blue_100
            }
            jlptChip.chipBackgroundColor = ContextCompat.getColorStateList(this, chipColorRes)
        } else {
            // No JLPT level - show "N?" in light grey
            jlptChip.text = "JLPT N?"
            jlptChip.chipBackgroundColor = ContextCompat.getColorStateList(this, R.color.tag_light_grey)
        }

        // Set text color to match the darker version or light black for unknown
        val textColor = when (jlptLevel) {
            5 -> R.color.jlpt_n5
            4 -> R.color.jlpt_n4
            3 -> R.color.jlpt_n3
            2 -> R.color.jlpt_n2
            1 -> R.color.jlpt_n1
            null -> R.color.text_light_black  // Light black for unknown JLPT
            else -> R.color.blue_100
        }
        jlptChip.setTextColor(ContextCompat.getColor(this, textColor))

        // Add minimal margin between items
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(2.dpToPx(), 2.dpToPx(), 2.dpToPx(), 2.dpToPx())
        }
        tagView.layoutParams = layoutParams

        tagChipGroup.addView(tagView)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun isKanji(char: String): Boolean {
        if (char.isEmpty()) return false
        val codePoint = char.codePointAt(0)
        return (codePoint in 0x4E00..0x9FAF) || (codePoint in 0x3400..0x4DBF)
    }

    private fun addChip(text: String, colorResId: Int) {
        val chip = Chip(this).apply {
            this.text = text
            chipBackgroundColor = ContextCompat.getColorStateList(context, colorResId)
            isClickable = false
            chipStrokeWidth = 0f
            textSize = 12f
            chipMinHeight = 32f.dpToPx()
        }
        tagChipGroup.addView(chip)
    }

    // Add this extension function
    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }

    private fun isKanjiString(text: String): Boolean {
        return text.any { char ->
            val codePoint = char.code
            (codePoint in 0x4E00..0x9FAF) || (codePoint in 0x3400..0x4DBF)
        }
    }

    private fun setupTabs() {
        // Double-check currentWord is not empty
        if (currentWord.isEmpty()) {
            currentWord = intent.getStringExtra("word") ?: ""
        }

        // For phrase searching, use the original selected text if available
        val selectedText = intent.getStringExtra("selectedText")
        val wordForPhrases = selectedText ?: currentWord
        

        // Pass the word to the adapter (use currentWord for kanji/forms, selectedText for phrases)
        val adapter = WordDetailPagerAdapter(this, currentWord, wordForPhrases)
        viewPager.adapter = adapter
        
        // Keep all fragments in memory to prevent recreation
        viewPager.offscreenPageLimit = 2

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> {
                    // Create custom view for Kanji tab with badge
                    val customView = LayoutInflater.from(this).inflate(R.layout.custom_tab_with_badge, null)
                    val titleView = customView.findViewById<TextView>(R.id.tabTitle)
                    titleView.text = "Kanji"
                    kanjiBadge = customView.findViewById(R.id.tabBadge)
                    kanjiTabTitle = titleView
                    tab.customView = customView
                }
                1 -> tab.text = "Forms"
                2 -> tab.text = "Phrases"
            }
        }.attach()
        
        // Add tab selection listener to handle custom tab styling
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    // Kanji tab selected - use the same color as other tabs
                    kanjiTabTitle?.setTextColor(ContextCompat.getColor(this@WordDetailActivity, R.color.teal_500))
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    // Kanji tab unselected - make it grey
                    kanjiTabTitle?.setTextColor(ContextCompat.getColor(this@WordDetailActivity, android.R.color.darker_gray))
                }
            }
            
            override fun onTabReselected(tab: TabLayout.Tab?) {
                // Do nothing
            }
        })
        
        // Set initial color for Kanji tab (selected by default)
        kanjiTabTitle?.setTextColor(ContextCompat.getColor(this, R.color.teal_500))
        
        // Add page change listener to trigger initial search when phrases tab is first accessed
        pageChangeCallback = ViewPagerCallback(wordForPhrases)
        viewPager.registerOnPageChangeCallback(pageChangeCallback!!)
    }


    override fun onKanjiCountUpdated(count: Int) {
        // Update the badge in the Kanji tab
        kanjiBadge?.apply {
            if (count > 0) {
                text = count.toString()
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
    }

    override fun onMeaningsCountUpdated(count: Int) {
        // Update the badge in the Meanings tab
        meaningsBadge?.apply {
            if (count > 0) {
                text = count.toString()
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
    }

    override fun onVariantsCountUpdated(count: Int) {
        // Update the badge in the Variants tab
        variantsBadge?.apply {
            if (count > 0) {
                text = count.toString()
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }
    }
    
    /**
     * Preload variant count and update badge immediately without waiting for tab selection
     */
    private fun preloadVariantCount(word: String) {
        if (word.isEmpty()) {
            onVariantsCountUpdated(0)
            return
        }
        
        lifecycleScope.launch {
            try {
                val repository = DictionaryRepository.getInstance(this@WordDetailActivity)
                val variants = repository.getVariants(word)
                
                runOnUiThread {
                    onVariantsCountUpdated(variants.size)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error preloading variant count for $word", e)
                onVariantsCountUpdated(0)
            }
        }
    }

    private fun setupMiddleTabs() {
        // Setup will be done when data is available
        // This method can be called initially but setupMiddleTabsWithData will actually populate it
    }

    private fun setupMiddleTabsWithData(meanings: List<String>) {
        // Create adapter with current word and meanings
        val middleAdapter = MidSectionPagerAdapter(this, currentWord, ArrayList(meanings))
        middleViewPager.adapter = middleAdapter

        TabLayoutMediator(middleTabLayout, middleViewPager) { tab, position ->
            when (position) {
                0 -> {
                    // Create custom view for Meanings tab with badge
                    val customView = LayoutInflater.from(this).inflate(R.layout.custom_middle_tab_with_badge, null)
                    val titleView = customView.findViewById<TextView>(R.id.tabTitle)
                    titleView.text = "Meanings"
                    meaningsBadge = customView.findViewById(R.id.tabBadge)
                    meaningsTabTitle = titleView
                    tab.customView = customView
                }
                1 -> {
                    // Create custom view for Variants tab with badge
                    val customView = LayoutInflater.from(this).inflate(R.layout.custom_middle_tab_with_badge, null)
                    val titleView = customView.findViewById<TextView>(R.id.tabTitle)
                    titleView.text = "Variants"
                    variantsBadge = customView.findViewById(R.id.tabBadge)
                    variantsTabTitle = titleView
                    tab.customView = customView
                }
            }
        }.attach()
        
        // Preload variant count to show badge immediately
        preloadVariantCount(currentWord)

        // Add middle tab selection listener to handle custom tab styling
        middleTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        // Meanings tab selected - make it green
                        meaningsTabTitle?.setTextColor(ContextCompat.getColor(this@WordDetailActivity, R.color.teal_500))
                    }
                    1 -> {
                        // Variants tab selected - make it green
                        variantsTabTitle?.setTextColor(ContextCompat.getColor(this@WordDetailActivity, R.color.teal_500))
                    }
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        // Meanings tab unselected - make it grey
                        meaningsTabTitle?.setTextColor(ContextCompat.getColor(this@WordDetailActivity, android.R.color.darker_gray))
                    }
                    1 -> {
                        // Variants tab unselected - make it grey
                        variantsTabTitle?.setTextColor(ContextCompat.getColor(this@WordDetailActivity, android.R.color.darker_gray))
                    }
                }
            }
            
            override fun onTabReselected(tab: TabLayout.Tab?) {
                // Do nothing
            }
        })
        
        // Set initial colors for middle tabs (meanings selected by default)
        meaningsTabTitle?.setTextColor(ContextCompat.getColor(this, R.color.teal_500))
        variantsTabTitle?.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
    }


    private fun getSimplifiedPos(pos: String): String {
        return when (pos) {
            // Regular dictionary tags
            "v1" -> "ichidan"
            "v5k", "v5s", "v5t", "v5n", "v5b", "v5m", "v5r", "v5g", "v5u" -> "godan"
            "vt" -> "transitive"
            "vi" -> "intransitive"
            "aux-v" -> "auxiliary"
            "adj-i" -> "い-adj"
            "adj-na" -> "な-adj"
            "n" -> "noun"
            "adv" -> "adverb"
            "prt" -> "particle"
            // JMNEDict tags - keep human-readable names
            "person" -> "person"
            "place" -> "place"
            "company" -> "company"
            "organization" -> "org"
            "given" -> "given name"
            "fem" -> "female name"
            "masc" -> "male name"
            "surname" -> "surname"
            "station" -> "station"
            "group" -> "group"
            "char" -> "character"
            "fict" -> "fiction"
            "work" -> "work"
            "ev" -> "event"
            "obj" -> "object"
            "product" -> "product"
            "serv" -> "service"
            "relig" -> "religion"
            "dei" -> "deity"
            "ship" -> "ship"
            "leg" -> "legend"
            "myth" -> "myth"
            "creat" -> "creature"
            "oth" -> "other"
            "unclass" -> "unclassified"
            "doc" -> "document"
            else -> pos
        }
    }

    private fun getChipTypeForTag(tag: String): DetailChipType {
        return when (tag) {
            // Specific verb types
            "v1" -> DetailChipType.VERB_ICHIDAN
            "v5k", "v5s", "v5t", "v5n", "v5b", "v5m", "v5r", "v5g", "v5u" -> DetailChipType.VERB_GODAN
            "vt" -> DetailChipType.VERB_TRANSITIVE
            "vi" -> DetailChipType.VERB_INTRANSITIVE
            "aux-v" -> DetailChipType.VERB_AUXILIARY
            // Other categories
            "adj-i", "adj-na" -> DetailChipType.ADJECTIVE
            "n" -> DetailChipType.NOUN
            "prt" -> DetailChipType.PARTICLE
            "adv" -> DetailChipType.ADVERB
            // Form-level tags (kanji/kana usage)
            "rK" -> DetailChipType.FORM_RARELY_USED_KANJI
            "iK" -> DetailChipType.FORM_IRREGULAR_KANJI
            "oK" -> DetailChipType.FORM_OUTDATED_KANJI
            "sK" -> DetailChipType.FORM_SEARCH_ONLY_KANJI
            "rk" -> DetailChipType.FORM_RARELY_USED_KANA
            "ik" -> DetailChipType.FORM_IRREGULAR_KANA
            "ok" -> DetailChipType.FORM_OUTDATED_KANA
            "sk" -> DetailChipType.FORM_SEARCH_ONLY_KANA
            "ateji" -> DetailChipType.FORM_ATEJI
            "gikun" -> DetailChipType.FORM_GIKUN
            "io" -> DetailChipType.FORM_IRREGULAR_OKURIGANA
            "uk" -> DetailChipType.FORM_KANA_ONLY
            else -> DetailChipType.OTHER
        }
    }
    
    /**
     * Check if a tag is a JMNEDict tag (proper noun type)
     */
    private fun isJMNEDictTag(tag: String): Boolean {
        return when (tag) {
            "person", "place", "company", "organization", "given", "fem", "masc", 
            "surname", "station", "group", "char", "fict", "work", "ev", "obj", 
            "product", "serv", "relig", "dei", "ship", "leg", "myth", "creat", 
            "oth", "unclass", "doc" -> true
            else -> false
        }
    }
    
    private fun getJMNEChipTypeForTag(tag: String): DetailChipType {
        return when (tag) {
            "person" -> DetailChipType.JMNE_PERSON
            "place" -> DetailChipType.JMNE_PLACE
            "company" -> DetailChipType.JMNE_COMPANY
            "organization" -> DetailChipType.JMNE_ORGANIZATION
            "given", "fem", "masc" -> DetailChipType.JMNE_GIVEN
            "surname" -> DetailChipType.JMNE_SURNAME
            "station" -> DetailChipType.JMNE_STATION
            "group", "char", "fict", "work", "ev", "obj", "product", "serv", 
            "relig", "dei", "ship", "leg", "myth", "creat", "oth", "unclass", "doc" -> DetailChipType.JMNE_OTHER
            else -> DetailChipType.JMNE_OTHER
        }
    }

    private fun createStyledChip(text: String, chipType: DetailChipType): Chip {
        val (bgColorRes, textColorRes) = when (chipType) {
            // Specific verb types with distinct colors
            DetailChipType.VERB_GODAN -> Pair(R.color.tag_verb_godan_bg, R.color.tag_verb_godan_text)
            DetailChipType.VERB_ICHIDAN -> Pair(R.color.tag_verb_ichidan_bg, R.color.tag_verb_ichidan_text)
            DetailChipType.VERB_TRANSITIVE -> Pair(R.color.tag_verb_transitive_bg, R.color.tag_verb_transitive_text)
            DetailChipType.VERB_INTRANSITIVE -> Pair(R.color.tag_verb_intransitive_bg, R.color.tag_verb_intransitive_text)
            DetailChipType.VERB_AUXILIARY -> Pair(R.color.tag_verb_auxiliary_bg, R.color.tag_verb_auxiliary_text)
            // Other part of speech
            DetailChipType.ADJECTIVE -> Pair(R.color.tag_adjective_bg, R.color.tag_adjective_text)
            DetailChipType.NOUN -> Pair(R.color.tag_noun_bg, R.color.tag_noun_text)
            DetailChipType.PARTICLE -> Pair(R.color.tag_particle_bg, R.color.tag_particle_text)
            DetailChipType.ADVERB -> Pair(R.color.tag_adverb_bg, R.color.tag_adverb_text)
            // Special tags
            DetailChipType.COMMON -> Pair(R.color.tag_common_bg, R.color.tag_common_text)
            DetailChipType.FREQUENCY -> Pair(R.color.tag_frequency_bg, R.color.tag_frequency_text)
            DetailChipType.OTHER -> Pair(R.color.tag_other_bg, R.color.tag_other_text)
            // JMNEDict types
            DetailChipType.JMNE_PERSON -> Pair(R.color.jmne_person_bg, R.color.jmne_person_text)
            DetailChipType.JMNE_PLACE -> Pair(R.color.jmne_place_bg, R.color.jmne_place_text)
            DetailChipType.JMNE_COMPANY -> Pair(R.color.jmne_company_bg, R.color.jmne_company_text)
            DetailChipType.JMNE_ORGANIZATION -> Pair(R.color.jmne_organization_bg, R.color.jmne_organization_text)
            DetailChipType.JMNE_GIVEN -> Pair(R.color.jmne_given_bg, R.color.jmne_given_text)
            DetailChipType.JMNE_SURNAME -> Pair(R.color.jmne_surname_bg, R.color.jmne_surname_text)
            DetailChipType.JMNE_STATION -> Pair(R.color.jmne_station_bg, R.color.jmne_station_text)
            DetailChipType.JMNE_OTHER -> Pair(R.color.jmne_other_bg, R.color.jmne_other_text)
            // Form-level tags (using existing colors)
            DetailChipType.FORM_RARELY_USED_KANJI -> Pair(R.color.purple_100, R.color.purple_700)
            DetailChipType.FORM_IRREGULAR_KANJI -> Pair(R.color.orange_100, R.color.orange_700) 
            DetailChipType.FORM_OUTDATED_KANJI -> Pair(R.color.tag_adverb_bg, R.color.tag_adverb_text) // Brown-ish
            DetailChipType.FORM_SEARCH_ONLY_KANJI -> Pair(R.color.tag_other_bg, R.color.tag_other_text) // Grey
            DetailChipType.FORM_RARELY_USED_KANA -> Pair(R.color.jmne_station_bg, R.color.jmne_station_text) // Purple-blue
            DetailChipType.FORM_IRREGULAR_KANA -> Pair(R.color.pink_100, R.color.jmne_given_text) // Pink
            DetailChipType.FORM_OUTDATED_KANA -> Pair(R.color.tag_verb_intransitive_bg, R.color.tag_verb_intransitive_text) // Orange
            DetailChipType.FORM_SEARCH_ONLY_KANA -> Pair(R.color.teal_100, R.color.teal_700) // Blue-grey-ish
            DetailChipType.FORM_ATEJI -> Pair(R.color.green_100, R.color.green_700) // Light green
            DetailChipType.FORM_GIKUN -> Pair(R.color.tag_verb_transitive_bg, R.color.tag_verb_transitive_text) // Amber
            DetailChipType.FORM_IRREGULAR_OKURIGANA -> Pair(R.color.tag_common_bg, R.color.tag_common_text) // Cyan
            DetailChipType.FORM_KANA_ONLY -> Pair(R.color.blue_100, R.color.blue_700) // Light blue
        }

        return Chip(this).apply {
            this.text = text
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            chipCornerRadius = 12f
            chipBackgroundColor = ContextCompat.getColorStateList(context, bgColorRes)
            setTextColor(ContextCompat.getColor(context, textColorRes))
            isClickable = false
            isFocusable = false
            chipStrokeWidth = 0f
            chipMinHeight = 32f.dpToPx()
        }
    }

    private fun formatFrequency(frequency: Int): String {
        return when {
            frequency >= 1000000 -> {
                val millions = frequency / 1000000.0
                "%.2f".format(millions).removeSuffix("0").removeSuffix(".") + "M"
            }
            frequency >= 1000 -> {
                val thousands = frequency / 1000.0
                "%.1f".format(thousands).removeSuffix("0").removeSuffix(".") + "k"
            }
            else -> frequency.toString()
        }
    }

    private fun createFrequencyChip(text: String, frequency: Int): Chip {
        val (bgColorRes, textColorRes) = when {
            frequency >= 10000000 -> Pair(R.color.freq_very_high_bg, R.color.freq_very_high_text) // 10M+ (very common)
            frequency >= 1000000 -> Pair(R.color.freq_high_bg, R.color.freq_high_text)          // 1M+ (common)
            frequency >= 100000 -> Pair(R.color.freq_medium_bg, R.color.freq_medium_text)       // 100k+ (medium)
            frequency >= 10000 -> Pair(R.color.freq_low_bg, R.color.freq_low_text)              // 10k+ (uncommon)
            else -> Pair(R.color.freq_very_low_bg, R.color.freq_very_low_text)                  // <10k (rare)
        }

        return Chip(this).apply {
            this.text = text
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            chipCornerRadius = 12f
            chipBackgroundColor = ContextCompat.getColorStateList(context, bgColorRes)
            setTextColor(ContextCompat.getColor(context, textColorRes))
            isClickable = false
            isFocusable = false
            chipStrokeWidth = 0f
            chipMinHeight = 32f.dpToPx()
        }
    }
    
    /**
     * Switch to the phrases tab and search for a specific word
     * This is called when users click on conjugations in the Forms tab
     */
    fun switchToPhrasesTab(word: String) {
        // Disable automatic search from ViewPager since user is taking control
        pageChangeCallback?.disableAutomaticSearch()
        
        // Force search even if already on phrases tab
        val currentItem = viewPager.currentItem
        
        if (currentItem == 2) {
            // Already on phrases tab - search immediately
            val fragment = supportFragmentManager.findFragmentByTag("f2") as? PhrasesTabFragment
            fragment?.searchSentences(word, force = true, fromFormsTab = true)
        } else {
            // Switch to phrases tab and then search
            viewPager.currentItem = 2
            
            // Wait for the fragment to be properly attached, then trigger search
            viewPager.post {
                val fragment = supportFragmentManager.findFragmentByTag("f2") as? PhrasesTabFragment
                fragment?.searchSentences(word, force = true, fromFormsTab = true)
            }
        }
    }
    
    /**
     * Load and display pitch accent data for a word
     */
    private fun loadPitchAccentData(kanjiForm: String, reading: String) {
        val repository = DictionaryRepository.getInstance(this)
        
        lifecycleScope.launch {
            try {
                // Get pitch accents from database
                val pitchAccents: List<PitchAccent> = repository.getPitchAccents(kanjiForm, reading)
                
                runOnUiThread {
                    displayPitchAccentData(pitchAccents, reading)
                }
                
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load pitch accent data for $kanjiForm/$reading: ${e.message}")
            }
        }
    }
    
    /**
     * Display pitch accent patterns in the UI
     */
    private fun displayPitchAccentData(pitchAccents: List<PitchAccent>, reading: String) {
        pitchAccentContainer.removeAllViews()
        
        if (pitchAccents.isEmpty()) {
            pitchAccentScrollView.visibility = View.GONE
            return
        }
        
        pitchAccentScrollView.visibility = View.VISIBLE
        val searchQuery = intent.getStringExtra("word") // Use search context for highlighting
        
        // If there are multiple patterns, show each pattern separately
        val primaryAccent = pitchAccents.first()
        val accentNumbers = primaryAccent.accentPattern.split(",").mapNotNull { it.trim().toIntOrNull() }
        
        accentNumbers.forEachIndexed { index, accentNumber ->
            val patternView = createPitchAccentPatternView(reading, accentNumber, searchQuery)
            
            // Add margin between patterns
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (index > 0) {
                    setMargins(8.dpToPx(), 0, 0, 0)
                }
            }
            patternView.layoutParams = layoutParams
            
            pitchAccentContainer.addView(patternView)
        }
    }
    
    /**
     * Create a view for a single pitch accent pattern
     */
    private fun createPitchAccentPatternView(reading: String, accentNumber: Int, searchQuery: String?): View {
        val inflater = LayoutInflater.from(this)
        val patternView = inflater.inflate(R.layout.item_pitch_accent_pattern, pitchAccentContainer, false)
        
        val pitchAccentView = patternView.findViewById<PitchAccentView>(R.id.pitchAccentView)
        val patternTypeText = patternView.findViewById<TextView>(R.id.patternTypeText)
        
        // Create single accent for this pattern
        val singleAccent = PitchAccent(
            kanjiForm = reading,
            reading = reading, 
            accentNumbers = listOf(accentNumber),
            accentPattern = accentNumber.toString()
        )
        
        // Set pitch accent with search highlighting
        pitchAccentView.setPitchAccents(listOf(singleAccent), reading, searchQuery)
        
        // Hide pattern labels - the visual pitch accent is clear enough
        patternTypeText.visibility = View.GONE
        
        return patternView
    }

    /**
     * Custom ViewPager callback that can be controlled to disable automatic search
     */
    inner class ViewPagerCallback(private val wordForPhrases: String) : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
        private var hasPhrasesTabBeenInitialized = false
        private var userHasInteractedWithFormsTab = false
        
        override fun onPageSelected(position: Int) {
            if (position == 2) { // Phrases tab
                // Only trigger search with the original word on first access
                // Don't override if user clicked a conjugated form from Forms tab
                if (!hasPhrasesTabBeenInitialized && !userHasInteractedWithFormsTab) {
                    hasPhrasesTabBeenInitialized = true
                    viewPager.post {
                        val fragment = supportFragmentManager.findFragmentByTag("f2") as? PhrasesTabFragment
                        // Only search if the fragment hasn't loaded data yet
                        if (fragment != null && !fragment.hasLoadedData) {
                            fragment.searchSentences(wordForPhrases)
                        }
                    }
                }
            }
        }
        
        // Method to disable automatic search when user interacts with Forms tab
        fun disableAutomaticSearch() {
            userHasInteractedWithFormsTab = true
        }
    }
}

