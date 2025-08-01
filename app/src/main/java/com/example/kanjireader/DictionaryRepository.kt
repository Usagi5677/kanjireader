package com.example.kanjireader

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Repository that uses FTS5 for all search operations
 * This integrates with your existing code seamlessly
 */
class DictionaryRepository(private val context: Context) {

    companion object {
        private const val TAG = "DictionaryRepository"
        
        // Using SQLite FTS5 exclusively for all searches

        @Volatile
        private var INSTANCE: DictionaryRepository? = null

        fun getInstance(context: Context): DictionaryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DictionaryRepository(context.applicationContext).also { repo ->
                    INSTANCE = repo
                    // FTS5 database is automatically initialized via DictionaryDatabase
                    Log.d(TAG, "Repository created in FTS5 mode")
                }
            }
        }
    }

    private val database = DictionaryDatabase.getInstance(context)
    // Note: FTS5 functionality is now integrated into DictionaryDatabase class
    private val gson = Gson()
    private val typeToken = object : TypeToken<List<String>>() {}

    // Using SQLite FTS5 exclusively - no HashMap references needed

    private var deinflectionEngine: TenTenStyleDeinflectionEngine? = null
    private var tagDictLoader: TagDictSQLiteLoader? = null
    
    // Kuromoji morphological analyzer (works alongside TenTen deinflection)
    private val kuromojiAnalyzer = KuromojiMorphologicalAnalyzer()
    
    // Cache variables for performance optimization
    private val deinflectionCache = mutableMapOf<String, DeinflectionResult?>()
    private val directMatchQueries = mutableSetOf<String>()
    private val progressiveMatchQueries = mutableSetOf<String>()
    private val noDeinfectionQueries = mutableSetOf<String>()
    
    // Enhanced deinflection with suffix tree support (temporarily disabled)
    // private val enhancedDeinflectionEngine = EnhancedDeinflectionEngine()
    
    // Mixed script support
    private val romajiConverter = RomajiConverter()
    private val japaneseWordExtractor = JapaneseWordExtractor()
    
    // Sentence analysis for mixed script sentences
    private val sentenceAnalysisEngine by lazy { SentenceAnalysisEngine(romajiConverter, database) }
    
    // Now using FTS5 exclusively

    /**
     * Initialize repository with SQLite FTS5 database components
     */
    fun initialize(
        deinflectionEngine: TenTenStyleDeinflectionEngine? = null,
        tagDictLoader: TagDictSQLiteLoader? = null
    ) {
        // Initialize TenTen engine and tag loader
        this.deinflectionEngine = deinflectionEngine ?: TenTenStyleDeinflectionEngine()
        this.tagDictLoader = tagDictLoader
        
        // Kuromoji analyzer is ready to use immediately (no initialization needed)
        Log.d(TAG, "Kuromoji morphological analyzer ready for SQLite FTS5 mode")
        
        Log.d(TAG, "Repository initialized for SQLite FTS5 mode")
    }

    /**
     * Initialize only deinflection engine for lightweight mode
     */
    fun initializeForDeinflectionOnly(
        deinflectionEngine: TenTenStyleDeinflectionEngine,
        tagDictLoader: TagDictSQLiteLoader
    ) {
        // Initialize with SQLite components only
        this.deinflectionEngine = deinflectionEngine
        this.tagDictLoader = tagDictLoader
        
        // Kuromoji analyzer is ready to use immediately (no initialization needed)
        Log.d(TAG, "Kuromoji morphological analyzer ready for SQLite FTS5 mode")
        
        Log.d(TAG, "Repository initialized for deinflection-only SQLite FTS5 mode")
    }


    /**
     * Initialize only FTS5 database for immediate search capability
     */
    private fun initializeFTS5Only() {
        Log.i(TAG, "🔄 FTS5 AUTO-INIT: Starting FTS5 database initialization...")
        
        // Kuromoji analyzer is ready to use (no initialization needed for FTS5 mode)
        Log.d(TAG, "Kuromoji analyzer ready for FTS5 mode")
        
        // Check if FTS5 database is ready
        try {
            GlobalScope.launch(Dispatchers.IO) {
                if (database.isDatabaseReady()) {
                    Log.i(TAG, "✅ FTS5 AUTO-INIT: FTS5 database is ready!")
                    Log.i(TAG, "📊 FTS5 STATS: Database ready with integrated FTS5 tables")
                    
                    // Database is ready for FTS5 searches
                    Log.i(TAG, "✅ FTS5 ready for searches")
                } else {
                    Log.e(TAG, "❌ FTS5 AUTO-INIT: FTS5 database not ready")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 FTS5 AUTO-INIT: Failed to initialize FTS5 database", e)
        }
    }

    /**
     * Check if SQLite FTS5 database is ready
     */
    fun isReady(): Boolean {
        // SQLite FTS5 mode only - check database readiness
        val fts5Ready = database.isDatabaseReady()
        Log.d(TAG, "Repository ready status - SQLite FTS5: $fts5Ready")
        return fts5Ready
    }

    
    
    
    
    /**
     * Search by Japanese text (kanji/kana) - DEPRECATED
     * Uses existing HashMap implementation (legacy) - REMOVED
     * Use search() method instead which uses SQLite FTS5
     */
    /*
    suspend fun searchJapanese(query: String): List<WordResult> = withContext(Dispatchers.IO) {
        val extractor = jmdictExtractor
        if (extractor?.isDictionaryLoaded() != true) {
            Log.w(TAG, "JMdict not loaded for Japanese search")
            return@withContext emptyList()
        }

        Log.d(TAG, "searchJapanese: Starting for '$query'")

        // Handle particles specially - only return exact matches
        if (isParticle(query)) {
            Log.d(TAG, "searchJapanese: Detected particle '$query', filtering to exact matches only")
            val allResults = extractor.lookupWordsWithPrefix(query, 100)
            val exactMatches = allResults.filter { result ->
                result.kanji == query || result.reading == query
            }
            Log.d(TAG, "searchJapanese: Particle filtered from ${allResults.size} to ${exactMatches.size} results")
            return@withContext exactMatches
        }

        val results = mutableListOf<WordResult>()
        
        // Check if query looks like a conjugated form that should be deinflected
        val isPotentiallyConjugated = query.endsWith("ています") || query.endsWith("ている") || 
                                      query.endsWith("てる") || query.endsWith("でる") ||
                                      query.endsWith("ました") || query.endsWith("ます") ||
                                      query.endsWith("でした") || query.endsWith("です") ||
                                      query.endsWith("んだ") || query.endsWith("った") ||
                                      query.endsWith("いた") || query.endsWith("えた") ||
                                      query.endsWith("した") || query.endsWith("きた") ||
                                      query.endsWith("ない") || query.endsWith("なく") ||
                                      // Only treat "て"/"で" as conjugated if it's more than 1 character and looks like a verb stem
                                      (query.length > 2 && (query.endsWith("て") || query.endsWith("で"))) ||
                                      // Specifically catch common te-forms like みて, きて, して, いて, etc.
                                      (query.length >= 2 && (query.matches(Regex(".*[いきしちにひみりぎじびぴ][て]")) ||
                                       query.matches(Regex(".*[いきしちにひみりぎじびぴ][で]"))))

        // If it's potentially conjugated, try deinflection first
        // Use Kuromoji for better performance and coverage
        if (isPotentiallyConjugated && query.length > 1) {
            Log.d(TAG, "searchJapanese: Query appears conjugated, trying Kuromoji deinflection first")
            val deinflectionResults = kuromojiAnalyzer.deinflect(query)
            Log.d(TAG, "searchJapanese: Found ${deinflectionResults.size} Kuromoji deinflection results")

            for (deinflection in deinflectionResults) {
                // Skip if it's the same as the query
                if (deinflection.baseForm == query) continue

                // Store deinflection info for conjugation display
                storeDeinflectionInfo(query, deinflection)

                // Try exact lookup first
                val exactResults = extractor.lookupExactWords(listOf(deinflection.baseForm))
                Log.d(TAG, "searchJapanese: Base form '${deinflection.baseForm}' exact lookup has ${exactResults.size} results")

                for (baseResult in exactResults) {
                    // Avoid duplicates
                    if (results.none { it.reading == baseResult.reading && it.kanji == baseResult.kanji }) {
                        results.add(baseResult)
                    }
                }
                
                // Also try prefix search for the base form to catch variations
                val prefixResults = extractor.lookupWordsWithPrefix(deinflection.baseForm, 50)
                Log.d(TAG, "searchJapanese: Base form '${deinflection.baseForm}' prefix lookup has ${prefixResults.size} results")

                for (baseResult in prefixResults) {
                    // Avoid duplicates
                    if (results.none { it.reading == baseResult.reading && it.kanji == baseResult.kanji }) {
                        results.add(baseResult)
                    }
                }
            }
        }
        
        // Always do prefix search for the original query to catch related entries
        // This ensures we find entries like 見る when searching みて
        val prefixResults = extractor.lookupWordsWithPrefix(query, 100)
        Log.d(TAG, "searchJapanese: Prefix lookup for original query found ${prefixResults.size} results")
        
        for (prefixResult in prefixResults) {
            // Avoid duplicates
            if (results.none { it.reading == prefixResult.reading && it.kanji == prefixResult.kanji }) {
                results.add(prefixResult)
            }
        }

        // If we still don't have many results and haven't tried deinflection yet, try it now
        if (results.size < 20 && query.length > 1 && !isPotentiallyConjugated) {
            Log.d(TAG, "searchJapanese: Trying Kuromoji deinflection for more results")
            val deinflectionResults = kuromojiAnalyzer.deinflect(query)
            Log.d(TAG, "searchJapanese: Found ${deinflectionResults.size} Kuromoji deinflection results")

            for (deinflection in deinflectionResults) {
                // Skip if it's the same as the query
                if (deinflection.baseForm == query) continue

                // Store deinflection info for conjugation display
                storeDeinflectionInfo(query, deinflection)

                val baseResults = extractor.lookupExactWords(listOf(deinflection.baseForm))
                Log.d(TAG, "searchJapanese: Base form '${deinflection.baseForm}' has ${baseResults.size} results")

                for (baseResult in baseResults) {
                    // Avoid duplicates
                    if (results.none { it.reading == baseResult.reading && it.kanji == baseResult.kanji }) {
                        results.add(baseResult)
                    }
                }

                // Limit results to avoid too many
                if (results.size >= 100) break
            }
        }

        Log.d(TAG, "searchJapanese: Final result count: ${results.size}")
        
        // Sort results before returning - prioritize by frequency, but deprioritize proper nouns
        val sortedResults = results.sortedWith(
            compareBy<WordResult> { isProperNoun(it) }  // Proper nouns go to bottom (false = 0, true = 1)
                .thenByDescending { it.frequency ?: 0 }
                .thenByDescending { it.isCommon }
                .thenBy { it.reading.length }
        )
        
        // Debug logging for sorting
        if (query.contains("みた") || query.contains("みる")) {
            Log.d(TAG, "Japanese search results ordering for '$query':")
            sortedResults.take(10).forEachIndexed { index, result ->
                val isProper = isProperNoun(result)
                Log.d(TAG, "  $index: '${result.kanji}' (${result.reading}) isProper=$isProper tags=${result.tags}")
            }
        }
        
        // Supplement with legacy SQLite search for additional coverage
        try {
            val sqliteResults = database.searchJapaneseLegacy(query, 50)
            val supplementalResults: List<WordResult> = sqliteResults.map { result ->
                WordResult(
                    kanji = result.kanji,
                    reading = result.reading,
                    meanings = parseMeaningsJson(result.meanings),
                    isCommon = result.isCommon,
                    frequency = result.frequency,
                    wordOrder = 999,
                    tags = emptyList(), // Tags will be loaded by TagDictSQLiteLoader for both JMdict and JMNEDict entries
                    partsOfSpeech = emptyList()
                )
            }
            
            // Add supplemental results that aren't already in the main results
            for (supplemental in supplementalResults) {
                if (results.none { it.reading == supplemental.reading && it.kanji == supplemental.kanji }) {
                    results.add(supplemental)
                }
            }
            
            Log.d(TAG, "searchJapanese: Added ${supplementalResults.size} supplemental SQLite results")
        } catch (e: Exception) {
            Log.w(TAG, "Supplemental SQLite search failed", e)
        }
        
        // Sort final results - deprioritize proper nouns
        val finalSortedResults = results.sortedWith(
            compareBy<WordResult> { isProperNoun(it) }  // Proper nouns go to bottom
                .thenByDescending { it.frequency ?: 0 }
                .thenByDescending { it.isCommon }
                .thenBy { it.reading.length }
        )
        
        // Debug final sorting
        if (query.contains("みた") || query.contains("みる")) {
            Log.d(TAG, "FINAL Japanese search results for '$query':")
            finalSortedResults.take(10).forEachIndexed { index, result ->
                val isProper = isProperNoun(result)
                Log.d(TAG, "  FINAL $index: '${result.kanji}' (${result.reading}) isProper=$isProper tags=${result.tags}")
            }
        }
        
        return@withContext finalSortedResults.take(150)
    }

    private val deinflectionCache = mutableMapOf<String, DeinflectionResult>()
    private val progressiveMatchQueries = mutableSetOf<String>() // Track queries that used progressive matching
    private val directMatchQueries = mutableSetOf<String>() // Track queries that had direct database matches
    private val noDeinfectionQueries = mutableSetOf<String>() // Track queries that shouldn't show deinflection info

    private fun storeDeinflectionInfo(originalQuery: String, deinflection: DeinflectionResult) {
        // Validate with Kuromoji to ensure it's actually a conjugation
        val kuromojiResult = kuromojiAnalyzer.analyzeWord(originalQuery)
        
        Log.d(TAG, "Validating deinflection for '$originalQuery': kuromoji=${kuromojiResult != null}, " +
                "baseFormDiffers=${kuromojiResult?.baseForm != originalQuery}")
        
        if (kuromojiResult != null && 
            kuromojiResult.baseForm != originalQuery && 
            isValidConjugationByKuromoji(originalQuery, kuromojiResult)) {
            // Kuromoji confirms this is a conjugation
            deinflectionCache[originalQuery] = deinflection
            Log.d(TAG, "✅ Stored deinflection (Kuromoji validated): '$originalQuery' -> '${deinflection.baseForm}' (${deinflection.verbType})")
        } else {
            // Kuromoji doesn't recognize this as a conjugation - don't store
            Log.d(TAG, "❌ Kuromoji validation failed for '$originalQuery' - not storing as conjugation")
            Log.d(TAG, "   Reason: kuromoji=${kuromojiResult != null}, baseForm='${kuromojiResult?.baseForm}', " +
                    "partOfSpeech='${kuromojiResult?.partOfSpeech}'")
        }
    }
    
    private fun isValidConjugationByKuromoji(originalQuery: String, kuromojiResult: MorphologyResult): Boolean {
        // Check if Kuromoji can tokenize the word as a single meaningful token
        val tokens = kuromojiAnalyzer.tokenize(originalQuery)
        
        // Check if any token contains non-Japanese characters or is marked as unknown
        var hasUnknownTokens = false
        var hasAlphabetTokens = false
        var hasValidConjugatablePOS = false
        
        for (token in tokens) {
            val surface = token.surface
            val pos1 = token.partOfSpeechLevel1 ?: ""
            val pos2 = token.partOfSpeechLevel2 ?: ""
            val baseForm = token.baseForm ?: surface
            
            // If any token is unknown, reject
            if (pos1 == "その他" || pos1 == "未知語") {
                Log.d(TAG, "Token '$surface' has pos '$pos1' - rejecting as invalid conjugation")
                hasUnknownTokens = true
            }
            
            // If any token is marked as alphabet symbol, reject
            if (pos1 == "記号" && pos2 == "アルファベット") {
                Log.d(TAG, "Token '$surface' is alphabet symbol - rejecting as invalid conjugation")
                hasAlphabetTokens = true
            }
            
            // Only allow conjugation for verbs and adjectives, and only if base form differs from surface
            if (pos1 == "動詞" || pos1 == "形容詞") {
                if (baseForm != surface) {
                    hasValidConjugatablePOS = true
                }
            } else if (pos1 == "名詞") {
                // Nouns should never be considered conjugated - reject immediately
                Log.d(TAG, "Token '$surface' is a noun (pos='$pos1') - rejecting as invalid conjugation")
                return false
            }
            
            Log.d(TAG, "Token analysis: surface='$surface', baseForm='$baseForm', pos1='$pos1', pos2='$pos2'")
        }
        
        // Reject if we found unknown tokens or alphabet symbols
        if (hasUnknownTokens || hasAlphabetTokens) {
            return false
        }
        
        // Only accept if we found at least one token that can actually be conjugated
        if (!hasValidConjugatablePOS) {
            Log.d(TAG, "No conjugatable POS found for '$originalQuery' - rejecting as invalid conjugation")
            return false
        }
        
        // Additional check: reject if the word ends with a particle
        // (e.g., "みまで" should not be treated as a conjugation of "みる")
        val particles = setOf("を", "は", "が", "の", "に", "で", "と", "から", "まで", "より", "へ", "や", "も", "か", "ね", "よ", "ぞ", "ぜ")
        if (particles.any { originalQuery.endsWith(it) }) {
            Log.d(TAG, "Query '$originalQuery' ends with particle - rejecting as invalid conjugation")
            return false
        }
        
        // Also check if the last token is a particle
        val lastToken = tokens.lastOrNull()
        if (lastToken?.partOfSpeechLevel1 == "助詞") {
            Log.d(TAG, "Query '$originalQuery' ends with particle token '${lastToken.surface}' - rejecting as invalid conjugation")
            return false
        }
        
        Log.d(TAG, "Kuromoji validation passed for '$originalQuery' - valid conjugation (${tokens.size} tokens)")
        return true
    }

    private fun clearDeinflectionInfo(query: String) {
        deinflectionCache.remove(query)
    }

    /**
     * Select the best deinflection result from multiple candidates
     */
    private fun selectBestDeinflection(deinflections: List<DeinflectionResult>, originalQuery: String): DeinflectionResult {
        Log.d(TAG, "Selecting best from ${deinflections.size} deinflection candidates:")
        deinflections.forEachIndexed { index, result ->
            Log.d(TAG, "  $index: ${result.baseForm} (${result.verbType})")
        }
        
        // Priority ranking:
        // 1. Prefer results that end with る (ichidan verbs) or う (godan verbs) 
        // 2. Prefer shorter base forms (more likely to be dictionary forms)
        // 3. Prefer results with proper verb types
        // 4. Avoid obvious intermediate forms
        
        val scored = deinflections.map { result ->
            var score = 0
            
            // Dictionary form endings get highest priority
            when {
                result.baseForm.endsWith("る") -> score += 100
                result.baseForm.endsWith("う") || result.baseForm.endsWith("く") || 
                result.baseForm.endsWith("ぐ") || result.baseForm.endsWith("す") || 
                result.baseForm.endsWith("つ") || result.baseForm.endsWith("ぬ") || 
                result.baseForm.endsWith("ぶ") || result.baseForm.endsWith("む") -> score += 90
                result.baseForm.endsWith("い") -> score += 80 // Adjectives
            }
            
            // Proper verb types get bonus
            if (result.verbType != null) {
                score += 50
            }
            
            // Shorter forms are generally better (dictionary forms)
            score += (10 - result.baseForm.length).coerceAtLeast(0)
            
            // Avoid obvious junk forms
            when {
                result.baseForm.contains("まする") -> score -= 200 // "見まする" is nonsense
                result.baseForm.contains("ましる") -> score -= 200 // "見ましる" is nonsense  
                result.baseForm.endsWith("ます") -> score -= 100  // Still conjugated
                result.baseForm == originalQuery -> score -= 50   // No transformation
            }
            
            result to score
        }.sortedByDescending { it.second }
        
        val best = scored.first().first
        Log.d(TAG, "Selected best deinflection: ${best.baseForm} (score: ${scored.first().second})")
        
        return best
    }
    */

    fun getDeinflectionInfo(query: String): DeinflectionResult? {
        // Don't provide conjugation info for space-separated queries
        if (query.contains(Regex("\\s+"))) {
            Log.d(TAG, "Query '$query' contains spaces - not providing conjugation info")
            return null
        }
        
        // If this query had direct database matches, don't provide deinflection info
        // BUT allow deinflection for potential conjugated forms
        if (directMatchQueries.contains(query) && !isPotentialConjugatedForm(query)) {
            Log.d(TAG, "Query '$query' had direct database matches - not providing deinflection info")
            return null
        }
        
        // If this query is marked as having too many direct results, don't compute deinflection
        // UNLESS it's a potential conjugated form that we should still try to deinflect
        if (noDeinfectionQueries.contains(query) && !isPotentialConjugatedForm(query)) {
            Log.d(TAG, "Query '$query' marked as no deinflection due to too many direct results")
            return null
        }
        
        // First check cache, but validate it for direct matches
        val cached = deinflectionCache[query]
        if (cached != null) {
            // Double-check if this is a direct database match before returning cached result
            try {
                val directCheck = runBlocking { database.searchJapaneseFTS(query, 5) }
                if (directCheck.any { it.kanji == query || it.reading == query }) {
                    Log.d(TAG, "Found '$query' is actually a direct database match - clearing bad cache")
                    deinflectionCache.remove(query)
                    directMatchQueries.add(query)
                    return null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to validate cached deinflection for '$query'", e)
            }
            
            Log.d(TAG, "Retrieved validated cached deinflection for '$query': ${cached.baseForm} (${cached.verbType})")
            return cached
        }
        
        // If this query used progressive matching (found a dictionary word), don't compute deinflection
        if (progressiveMatchQueries.contains(query)) {
            Log.d(TAG, "Query '$query' used progressive matching - not computing deinflection")
            return null
        }
        
        // If this query had direct database matches, don't compute deinflection
        if (directMatchQueries.contains(query)) {
            Log.d(TAG, "Query '$query' had direct database matches - not computing deinflection")
            return null
        }
        
        
        // Debug logging for specific problematic queries
        if (query == "みた") {
            Log.d(TAG, "DEBUG: Processing deinflection for '$query'")
        }
        
        // If not in cache, try to compute deinflection now using Kuromoji
        Log.d(TAG, "Computing deinflection for '$query' using Kuromoji...")
        
        try {
            val deinflections = kuromojiAnalyzer.deinflect(query)
            if (deinflections.isNotEmpty()) {
                // Pick the best result instead of just the first
                val result = selectBestDeinflection(deinflections, query)
                storeDeinflectionInfo(query, result)
                Log.d(TAG, "Computed deinflection for '$query': ${result.baseForm} (${result.verbType})")
                
                // Only return the result if it was successfully stored (passed validation)
                return deinflectionCache[query]
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute deinflection for '$query'", e)
        }
        
        Log.d(TAG, "No deinflection found for '$query'")
        return null
    }
    

    /**
     * Search by English text
     * Uses new SQLite FTS implementation
     */

    // In DictionaryRepository.kt
    suspend fun searchEnglish(query: String, limit: Int = 50): List<WordResult> = withContext(Dispatchers.IO) {
        if (query.length < 2) {
            return@withContext emptyList()
        }

        try {
            // Use English FTS search
            val searchResults = database.searchEnglishFTS(query, limit)

            // Simply convert SQLite results to WordResult format
            return@withContext searchResults.map { result ->
                WordResult(
                    kanji = result.kanji,
                    reading = result.reading,
                    meanings = parseMeaningsJson(result.meanings),
                    isCommon = result.isCommon,
                    frequency = result.frequency,
                    wordOrder = 999,
                    tags = emptyList(),
                    partsOfSpeech = emptyList(),
                    isJMNEDictEntry = false,
                    isDeinflectedValidConjugation = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "English search failed", e)
            return@withContext emptyList()
        }
    }


    /**
     * Main search method using FTS5 exclusively
     * This is what DictionaryActivity should use
     */
    suspend fun search(query: String, limit: Int = 500, offset: Int = 0): List<WordResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== FTS5 SEARCH START ===")
        Log.d(TAG, "Query: '$query'")
        Log.d(TAG, "Search mode: FTS5")

        // Use FTS5 for all searches with pagination
        val results = searchFTS5(query, limit, offset)

        Log.d(TAG, "Total results: ${results.size}")
        Log.d(TAG, "=== FTS5 SEARCH END ===")

        return@withContext results.drop(offset).take(limit)
    }
    
    /**
     * Search using FTS5 implementation
     */
    private suspend fun searchFTS5(query: String, limit: Int = 500, offset: Int = 0): List<WordResult> = withContext(Dispatchers.IO) {
        if (!database.isDatabaseReady()) {
            Log.e(TAG, "FTS5 database not ready")
            return@withContext emptyList()
        }
        
        Log.d(TAG, "=== FTS5 SEARCH START ===")
        Log.d(TAG, "Query: '$query'")
        
        val results = mutableListOf<WordResult>()
        
        // Check if query contains spaces - if so, treat as separate word search
        if (query.contains(Regex("\\s+"))) {
            Log.d(TAG, "FTS5: Space-separated query detected: '$query'")
            results.addAll(searchFTS5SpaceSeparated(query, limit, offset))
        } else {
            // Single word/phrase search
            when {
                // Check for wildcard pattern first - only for Japanese text
                query.contains("?") && isWildcardPattern(query) && isJapaneseText(query) -> {
                    Log.d(TAG, "FTS5: Wildcard search detected for Japanese pattern: '$query'")
                    results.addAll(searchFTS5Wildcard(query, limit, offset))
                }
                // Handle wildcard on non-Japanese text gracefully
                query.contains("?") && !isJapaneseText(query) -> {
                    Log.d(TAG, "FTS5: Wildcard detected on non-Japanese text '$query' - wildcards only supported for Japanese")
                    // Return empty results gracefully instead of crashing
                    results.clear()
                }
                // Mixed script (Japanese + romaji)
                isMixedScript(query) -> {
                    Log.d(TAG, "FTS5: Using mixed script search")
                    results.addAll(searchFTS5MixedScript(query, limit, offset))
                }
                // Pure romaji input - use parallel search (both romaji->Japanese and English)
                containsRomaji(query) && !isJapaneseText(query) && isLikelyJapaneseRomaji(query) -> {
                    Log.d(TAG, "FTS5: Using parallel romaji+English search")
                    results.addAll(searchFTS5Parallel(query, limit, offset))
                }
                // Pure Japanese text
                isJapaneseText(query) -> {
                    Log.d(TAG, "FTS5: Using Japanese search")
                    results.addAll(searchFTS5Japanese(query, limit, offset))
                }
                // English text
                isEnglishText(query) -> {
                    Log.d(TAG, "FTS5: Using English search")
                    results.addAll(searchFTS5English(query, limit, offset))
                }
                else -> {
                    Log.d(TAG, "FTS5: Unclear query - using unified search")
                    results.addAll(searchFTS5Unified(query, limit, offset))
                }
            }
        }
        
        Log.d(TAG, "FTS5 search returned ${results.size} results")
        Log.d(TAG, "=== FTS5 SEARCH END ===")
        
        return@withContext results.distinctBy { "${it.kanji ?: ""}|${it.reading}" }
    }
    
    /**
     * FTS5 Japanese search - Updated to use integrated DictionaryDatabase
     */
    private suspend fun searchFTS5Japanese(query: String, limit: Int, offset: Int = 0): List<WordResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 searchFTS5Japanese: Starting search for '$query'")
            val startTime = System.currentTimeMillis()
            
            // Try both kana variants if input is hiragana/katakana
            val kanaVariants = romajiConverter.getBothKanaVariants(query)
            val searchQuery = kanaVariants[0] // Always use primary variant for main search
            val alternateVariant = if (kanaVariants.size > 1) kanaVariants[1] else null
            
            Log.d(TAG, "🔍 Kana variants for '$query': primary='$searchQuery', alternate='$alternateVariant'")
            
            // Single database call for direct search
            val fts5Results = database.searchJapaneseFTS(searchQuery, limit).toMutableList()
            
            // If we have an alternate variant and didn't find enough results, try it too
            if (alternateVariant != null && alternateVariant != searchQuery && fts5Results.size < limit) {
                val remainingLimit = limit - fts5Results.size
                Log.d(TAG, "🔍 Trying alternate kana variant '$alternateVariant' (remaining limit: $remainingLimit)")
                val alternateResults = database.searchJapaneseFTS(alternateVariant, remainingLimit)
                // Add unique results from alternate search
                for (altResult in alternateResults) {
                    if (!fts5Results.any { it.kanji == altResult.kanji && it.reading == altResult.reading }) {
                        fts5Results.add(altResult)
                    }
                }
            }
            
            val directSearchTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "⏱️ Direct search took ${directSearchTime}ms, found ${fts5Results.size} results")
            
            // Always attempt deinflection for Japanese queries to find base forms
            val deinflectedResults = mutableListOf<SearchResult>()
            
            // Check for particle suffixes before attempting deinflection
            val particles = setOf("を", "は", "が", "の", "に", "で", "と", "から", "まで", "より", "へ", "や", "も", "か", "ね", "よ", "ぞ", "ぜ")
            val endsWithParticle = particles.any { searchQuery.endsWith(it) }
            
            // Clear any incorrect cache entries for words ending with particles
            if (endsWithParticle && deinflectionCache.containsKey(searchQuery)) {
                Log.d(TAG, "🔧 Clearing incorrect cache entry for particle word: '$searchQuery'")
                deinflectionCache.remove(searchQuery)
            }
            
            // Use Kuromoji for accurate deinflection, but skip if ends with particle
            if (!endsWithParticle) {
                Log.d(TAG, "🔧 Attempting deinflection for '$searchQuery' using Kuromoji")
                
                // First, check for irregular verb patterns
                val irregularDeinflection = checkIrregularVerbs(searchQuery)
                if (irregularDeinflection != null) {
                    Log.d(TAG, "🔧 Irregular verb deinflection: '$searchQuery' -> '${irregularDeinflection.baseForm}'")
                    
                    val irregularResults = database.searchJapaneseFTS(
                        irregularDeinflection.baseForm,
                        30,
                        exactMatch = false,
                        isDeinflectedQuery = true,
                        baseForm = irregularDeinflection.baseForm
                    )
                    
                    if (irregularResults.isNotEmpty()) {
                        Log.d(TAG, "✅ Found ${irregularResults.size} results for irregular verb '${irregularDeinflection.baseForm}'")
                        deinflectedResults.addAll(irregularResults)
                        
                        // Store the irregular deinflection result
                        storeDeinflectionInfo(query, irregularDeinflection)
                    }
                }
                
                // If no irregular match found, try Kuromoji
                if (deinflectedResults.isEmpty()) {
                    val kuromojiDeinflection = getDeinflectionInfo(searchQuery)
                    if (kuromojiDeinflection != null && kuromojiDeinflection.baseForm != searchQuery) {
                        Log.d(TAG, "🔧 Kuromoji deinflection: '$searchQuery' -> '${kuromojiDeinflection.baseForm}'")
                        
                        val kuromojiResults = database.searchJapaneseFTS(
                            kuromojiDeinflection.baseForm,
                            30,
                            exactMatch = false,
                            isDeinflectedQuery = true,
                            baseForm = kuromojiDeinflection.baseForm
                        )
                        
                        if (kuromojiResults.isNotEmpty()) {
                            Log.d(TAG, "✅ Found ${kuromojiResults.size} results for Kuromoji base form '${kuromojiDeinflection.baseForm}'")
                            deinflectedResults.addAll(kuromojiResults)
                        } else {
                            Log.d(TAG, "❌ No results found for base form '${kuromojiDeinflection.baseForm}'")
                        }
                    } else {
                        Log.d(TAG, "🔧 No deinflection found for '$query'")
                    }
                }
            } else {
                Log.d(TAG, "🔧 Skipping deinflection for '$query' - ends with particle")
            }
            
            // If no results found, try progressive shortening to find the longest valid conjugation
            if (fts5Results.isEmpty() && deinflectedResults.isEmpty() && query.length > 3) {
                Log.d(TAG, "🔧 No results found, trying progressive shortening for '$query'")
                var shortenedQuery = query
                
                // Try removing characters from the end until we find a valid conjugation
                for (length in query.length - 1 downTo 3) {
                    shortenedQuery = query.substring(0, length)
                    Log.d(TAG, "🔧 Trying shortened query: '$shortenedQuery'")
                    
                    val shortenedDeinflection = getDeinflectionInfo(shortenedQuery)
                    if (shortenedDeinflection != null && shortenedDeinflection.baseForm != shortenedQuery) {
                        // Additional validation: check if this is actually a valid conjugation
                        // by analyzing with Kuromoji and ensuring it's not just a cached bad result
                        val morphologyResult = MorphologyResult(
                            originalForm = shortenedQuery,
                            baseForm = shortenedDeinflection.baseForm,
                            conjugationType = "POLITE_NEGATIVE_PAST", // Generic conjugation type
                            partOfSpeech = "動詞", // Assume verb for deinflected forms
                            verbType = shortenedDeinflection.verbType
                        )
                        val isActuallyValid = isValidConjugationByKuromoji(shortenedQuery, shortenedDeinflection.baseForm)
                        
                        if (isActuallyValid) {
                            Log.d(TAG, "🔧 Found valid conjugation: '$shortenedQuery' -> '${shortenedDeinflection.baseForm}'")
                            
                            val shortenedResults = database.searchJapaneseFTS(
                                shortenedDeinflection.baseForm,
                                30,
                                exactMatch = false,
                                isDeinflectedQuery = true,
                                baseForm = shortenedDeinflection.baseForm
                            )
                            
                            if (shortenedResults.isNotEmpty()) {
                                Log.d(TAG, "✅ Found ${shortenedResults.size} results for shortened conjugation")
                                deinflectedResults.addAll(shortenedResults)
                                break // Stop at first valid conjugation found
                            }
                        } else {
                            Log.d(TAG, "🔧 Rejected '$shortenedQuery' - cached result but failed Kuromoji validation")
                            // Clear the bad cache entry
                            deinflectionCache.remove(shortenedQuery)
                        }
                    }
                }
            }
            
            // Combine direct and deinflected results
            if (deinflectedResults.isNotEmpty()) {
                fts5Results.addAll(deinflectedResults)
                Log.d(TAG, "✅ Added ${deinflectedResults.size} deinflected results")
            }
            
            // Get deinflection info to mark valid conjugations
            val deinflectionResult = getDeinflectionInfo(query)
            val deinflectedBaseForm = deinflectionResult?.baseForm
            
            // Convert results and mark valid deinflections
            val convertedResults = fts5Results.map { searchResult ->
                val wordResult = convertFTS5ToWordResult(searchResult)
                
                // Check if this result is a valid deinflected form
                val isDeinflectedValid = if (deinflectedBaseForm != null && deinflectedBaseForm != query && !searchResult.isJMNEDictEntry) {
                    // This result is a valid deinflection if:
                    // 1. It exactly matches the base form (reading or kanji)
                    // 2. AND it's actually conjugatable (verb/adjective)
                    val matchesBaseForm = searchResult.kanji == deinflectedBaseForm || searchResult.reading == deinflectedBaseForm
                    
                    if (matchesBaseForm) {
                        // Check if it's actually conjugatable by examining parts of speech
                        val partsOfSpeech = try {
                            searchResult.partsOfSpeech?.let {
                                gson.fromJson(it, typeToken.type) as? List<String>
                            } ?: emptyList()
                        } catch (e: Exception) {
                            emptyList<String>()
                        }
                        
                        val isConjugatable = partsOfSpeech.any { pos ->
                            pos.contains("verb", ignoreCase = true) ||
                            pos.contains("adjective", ignoreCase = true) ||
                            pos.startsWith("v", ignoreCase = true) ||      // v1, v5k, vs, etc.
                            pos.startsWith("adj-", ignoreCase = true) ||   // adj-i, adj-na, etc.
                            pos == "aux-v"                                 // auxiliary verbs
                        }
                        
                        isConjugatable
                    } else {
                        false
                    }
                } else {
                    false
                }
                
                // Return updated WordResult with correct deinflection flag
                wordResult.copy(isDeinflectedValidConjugation = isDeinflectedValid)
            }
            
            // Apply improved sorting with special priority for deinflected results
            
            // Separate results by type for better control
            val validDeinflectedResults = convertedResults.filter { it.isDeinflectedValidConjugation && !it.isJMNEDictEntry }
            val exactMatches = convertedResults.filter { (it.kanji == query || it.reading == query) && !it.isDeinflectedValidConjugation }
            val otherMatches = convertedResults.filter { 
                !(it.kanji == query || it.reading == query) && !it.isDeinflectedValidConjugation 
            }
            
            // Sort deinflected results: highest frequency and common words first (these should be #1 priority)
            val sortedDeinflectedResults = validDeinflectedResults.sortedWith(
                compareByDescending<WordResult> { it.isCommon }     // Common words first
                    .thenByDescending { it.frequency ?: 0 }         // High frequency first
                    .thenBy { it.reading.length }                   // Shorter readings for tie-breaking
            )
            
            // Sort exact matches: non-proper nouns first, then by common status and frequency
            val exactNonProperNouns = exactMatches.filter { !it.isJMNEDictEntry }
            val exactProperNouns = exactMatches.filter { it.isJMNEDictEntry }
            
            val sortedExactNonProperNouns = exactNonProperNouns.sortedWith(
                compareByDescending<WordResult> { it.isCommon }     // Common words first
                    .thenByDescending { it.frequency ?: 0 }         // High frequency first
                    .thenBy { it.reading.length }                   // Shorter readings for tie-breaking
            )
            
            val sortedExactProperNouns = exactProperNouns.sortedWith(
                compareByDescending<WordResult> { it.isCommon }     // Common words first
                    .thenByDescending { it.frequency ?: 0 }         // High frequency first
                    .thenBy { it.reading.length }                   // Shorter readings for tie-breaking
            )
            
            // Sort other matches: non-proper nouns first, then proper nouns
            val otherNonProperNouns = otherMatches.filter { !it.isJMNEDictEntry }
            val otherProperNouns = otherMatches.filter { it.isJMNEDictEntry }
            
            val sortedOtherNonProperNouns = otherNonProperNouns.sortedWith(
                compareByDescending<WordResult> { it.isCommon }     // Common words first
                    .thenByDescending { it.frequency ?: 0 }         // High frequency first
                    .thenBy { it.reading.length }                   // Shorter readings for tie-breaking
            )
            
            val sortedOtherProperNouns = otherProperNouns.sortedWith(
                compareByDescending<WordResult> { it.isCommon }     // Common words first
                    .thenByDescending { it.frequency ?: 0 }         // High frequency first
                    .thenBy { it.reading.length }                   // Shorter readings for tie-breaking
            )
            
            // Combine all non-proper noun matches and sort by relevance
            // Priority: common + high frequency words should come before low-frequency exact matches
            val allNonProperNouns = (sortedExactNonProperNouns + sortedOtherNonProperNouns).sortedWith(
                // Remove exact match priority - let frequency and common status decide
                compareByDescending<WordResult> { if (it.isCommon) 1000000 + (it.frequency ?: 0) else (it.frequency ?: 0) }  // Boost common words significantly
                    .thenBy { !(it.kanji == query || it.reading == query) }  // Exact matches as tie-breaker only
                    .thenBy { it.reading.length }  // Shorter readings for final tie-breaking
            )
            
            val allProperNouns = (sortedExactProperNouns + sortedOtherProperNouns).sortedWith(
                compareByDescending<WordResult> { it.frequency ?: 0 }  // High frequency proper nouns first
                    .thenBy { it.reading.length }
            )
            
            // Final priority order:
            // 1. Deinflected results (like みる from みた) - HIGHEST PRIORITY  
            // 2. All non-proper nouns (exact matches + others, but common high-frequency prioritized)
            // 3. All proper noun matches - LOWEST PRIORITY
            val finalResults = sortedDeinflectedResults + allNonProperNouns + allProperNouns
            
            // Debug: Let's see what the sorting is actually doing for first few results
            if (query == "みた") {
                Log.d(TAG, "🔍 NEW DEBUG: Advanced sorting results")
                Log.d(TAG, "🔍 NEW DEBUG: Total results: ${convertedResults.size}")
                Log.d(TAG, "🔍 NEW DEBUG: Deinflected: ${validDeinflectedResults.size}")
                Log.d(TAG, "🔍 NEW DEBUG: Exact matches: ${exactMatches.size}")
                Log.d(TAG, "🔍 NEW DEBUG: Other matches: ${otherMatches.size}")
                Log.d(TAG, "🔍 NEW DEBUG: Exact proper nouns: ${exactProperNouns.size}")
                Log.d(TAG, "🔍 NEW DEBUG: Exact non-proper nouns: ${exactNonProperNouns.size}")
                
                Log.d(TAG, "🔍 NEW DEBUG: Top 3 deinflected results (PRIORITY #1):")
                sortedDeinflectedResults.take(3).forEachIndexed { i, result ->
                    Log.d(TAG, "🔍 NEW DEBUG: #${i+1}: ${result.kanji ?: result.reading} (common=${result.isCommon}, freq=${result.frequency}, deinflected=${result.isDeinflectedValidConjugation})")
                }
                
                Log.d(TAG, "🔍 NEW DEBUG: Top 3 exact non-proper results (PRIORITY #2):")
                sortedExactNonProperNouns.take(3).forEachIndexed { i, result ->
                    Log.d(TAG, "🔍 NEW DEBUG: #${i+1}: ${result.kanji ?: result.reading} (common=${result.isCommon}, freq=${result.frequency})")
                }
                
                Log.d(TAG, "🔍 NEW DEBUG: Top 10 other non-proper results (where みたい should be):")
                sortedOtherNonProperNouns.take(10).forEachIndexed { i, result ->
                    val displayForm = result.kanji ?: result.reading
                    Log.d(TAG, "🔍 NEW DEBUG: #${i+1}: $displayForm (common=${result.isCommon}, freq=${result.frequency})")
                }
                
                // Check if みたい is anywhere in the results
                val mitaiResult = convertedResults.find { (it.kanji == "みたい" || it.reading == "みたい") }
                if (mitaiResult != null) {
                    Log.d(TAG, "🔍 NEW DEBUG: Found みたい: kanji=${mitaiResult.kanji}, reading=${mitaiResult.reading}, common=${mitaiResult.isCommon}, freq=${mitaiResult.frequency}")
                } else {
                    Log.d(TAG, "🔍 NEW DEBUG: みたい NOT found in search results!")
                }
                
                val mitasuResult = convertedResults.find { (it.kanji == "満たす" || it.reading == "みたす") }
                if (mitasuResult != null) {
                    Log.d(TAG, "🔍 NEW DEBUG: Found 満たす: kanji=${mitasuResult.kanji}, reading=${mitasuResult.reading}, common=${mitasuResult.isCommon}, freq=${mitasuResult.frequency}")
                } else {
                    Log.d(TAG, "🔍 NEW DEBUG: 満たす NOT found in search results!")
                }
            }
            
            val totalTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "🏁 searchFTS5Japanese completed in ${totalTime}ms with ${finalResults.size} total results")
            Log.d(TAG, "🔍 Sorting: Exact matches: ${finalResults.count { it.kanji == query || it.reading == query }}")
            Log.d(TAG, "🔍 Sorting: Valid deinflections: ${finalResults.count { it.isDeinflectedValidConjugation }}")
            Log.d(TAG, "🔍 Sorting: Proper nouns: ${finalResults.count { it.isJMNEDictEntry }}")
            
            // Log first 10 results for debugging
            if (query == "みた") {
                finalResults.take(10).forEachIndexed { index, result ->
                    val displayForm = result.kanji ?: result.reading
                    val kanjiInfo = result.kanji?.let { "kanji='$it'" } ?: "no kanji"
                    Log.d(TAG, "🔍 Result #${index + 1}: $displayForm ($kanjiInfo, reading='${result.reading}', " +
                        "exact=${result.reading == query || result.kanji == query}, " +
                        "deinflected=${result.isDeinflectedValidConjugation}, " +
                        "common=${result.isCommon}, freq=${result.frequency}, " +
                        "proper=${result.isJMNEDictEntry}, meanings=${result.meanings.take(2)})")
                }
            }
            
            return@withContext finalResults
        } catch (e: Exception) {
            Log.e(TAG, "❌ REPOSITORY ERROR: searchFTS5Japanese failed", e)
            Log.e(TAG, "❌ REPOSITORY: Error message: ${e.message}")
            throw e
        }
    }
    
    /**
     * Result of progressive matching
     */
    private data class ProgressiveMatchResult(
        val matchedSubstring: String,
        val deinflectionResult: DeinflectionResult?
    )

    /**
     * Check if a substring looks like a valid conjugation pattern
     * Delegates to the centralized validation in TenTenStyleDeinflectionEngine
     */
    private fun looksLikeValidConjugation(text: String): Boolean {
        // Use safe call and default to false if engine is not initialized
        return deinflectionEngine?.looksLikeValidConjugation(text) ?: false
    }

    /**
     * Try progressive matching by attempting shorter substrings of the query
     */
    private suspend fun tryProgressiveMatching(query: String, fts5Results: MutableList<SearchResult>): ProgressiveMatchResult? {
        // Try progressively shorter substrings (minimum 2 characters)
        for (length in query.length - 1 downTo 2) {
            val substring = query.substring(0, length)
            Log.d(TAG, "Trying progressive match: '$substring' (from '$query')")
            
            // Try direct search first, but only if the substring looks like a valid conjugation
            if (looksLikeValidConjugation(substring)) {
                val directResults = database.searchJapaneseFTS(substring, 50)
                if (directResults.isNotEmpty()) {
                    Log.d(TAG, "Found progressive match (direct): '$substring'")
                    fts5Results.addAll(directResults)
                    // Mark this query as using progressive matching (not conjugation)
                    progressiveMatchQueries.add(query)
                    return ProgressiveMatchResult(substring, null)
                }
            }
            
            // Try deinflection on the substring
            deinflectionEngine?.let { engine ->
                val tenTenResults = engine.deinflect(substring, tagDictLoader)
                for (deinflection in tenTenResults) {
                    if (deinflection.baseForm != substring) {
                        // Validate with Kuromoji first to get the correct base form
                        val kuromojiResult = kuromojiAnalyzer.analyzeWord(substring)
                        val actualBaseForm = if (kuromojiResult != null && 
                                               kuromojiResult.baseForm != substring && 
                                               isValidConjugationByKuromoji(substring, kuromojiResult.baseForm)) {
                            // Use Kuromoji's base form for search
                            Log.d(TAG, "Progressive: Using Kuromoji correction: '${deinflection.baseForm}' -> '${kuromojiResult.baseForm}'")
                            kuromojiResult.baseForm
                        } else {
                            // Fall back to TenTen's result if Kuromoji doesn't validate
                            Log.d(TAG, "Progressive: Using TenTen result: '${deinflection.baseForm}'")
                            deinflection.baseForm
                        }
                        
                        val baseResults = database.searchJapaneseFTS(actualBaseForm, 50, exactMatch = false, isDeinflectedQuery = true, baseForm = actualBaseForm)
                        if (baseResults.isNotEmpty()) {
                            Log.d(TAG, "Found progressive match (deinflection): '$substring' -> '$actualBaseForm'")
                            // Create the deinflection result
                            val deinflectionResult = DeinflectionResult(
                                originalForm = substring, // Use the matched substring, not the full query
                                baseForm = actualBaseForm,
                                reasonChain = deinflection.reasonChain,
                                verbType = deinflection.verbType,
                                transformations = deinflection.transformations.map { legacyStep ->
                                    DeinflectionStep(
                                        from = legacyStep.from,
                                        to = legacyStep.to,
                                        reason = legacyStep.reason,
                                        ruleId = legacyStep.ruleId
                                    )
                                }
                            )
                            fts5Results.addAll(baseResults)
                            return ProgressiveMatchResult(substring, deinflectionResult)
                        }
                    }
                }
            }
        }
        
        // No progressive match found
        return null
    }
    
    /**
     * FTS5 English search - Updated to use integrated DictionaryDatabase with meaning prioritization
     */
    private suspend fun searchFTS5English(query: String, limit: Int, offset: Int = 0): List<WordResult> = withContext(Dispatchers.IO) {
        try {
            // Check for wildcard characters that aren't supported in English FTS5
            if (query.contains("?")) {
                Log.d(TAG, "FTS5 English: Wildcard detected in '$query' - wildcards not supported for English search")
                return@withContext emptyList()
            }
            
            val fts5Results = database.searchEnglishFTS(query, limit)
            val wordResults = fts5Results.map { convertFTS5ToWordResult(it) }
            
            // Apply meaning-based prioritization
            val prioritizedResults = prioritizeByMeaningPosition(wordResults, query)
            
            Log.d(TAG, "FTS5 English: Applied meaning prioritization for '$query' - ${prioritizedResults.size} results")
            
            return@withContext prioritizedResults
        } catch (e: Exception) {
            Log.e(TAG, "FTS5 English search failed for '$query': ${e.message}")
            return@withContext emptyList()
        }
    }
    
    /**
     * Prioritize search results based on meaning position
     * Results where the search term appears in meaning #1 get highest priority
     * Also deprioritizes proper nouns
     */
    private fun prioritizeByMeaningPosition(results: List<WordResult>, query: String): List<WordResult> {
        val queryLower = query.lowercase()
        
        return results.sortedWith(
            compareBy<WordResult> { isProperNoun(it) }  // Proper nouns go to bottom first
                .thenBy { result ->
                    // Find the earliest meaning position where the query appears
                    val meaningPosition = result.meanings.indexOfFirst { meaning ->
                        meaning.lowercase().contains(queryLower)
                    }
                    
                    when {
                        meaningPosition == 0 -> 0  // First meaning (highest priority)
                        meaningPosition > 0 -> 1  // Later meanings (lower priority)
                        else -> 2  // Query not found in meanings (lowest priority)
                    }
                }
                .thenByDescending { it.isCommon }  // Common words first within same meaning position
                .thenByDescending { it.frequency }  // Higher frequency first
                .thenBy { it.meanings.size }  // Fewer meanings first (more specific)
        )
    }
    
    /**
     * FTS5 Unified search (searches both Japanese and English)
     */
    private suspend fun searchFTS5Unified(query: String, limit: Int, offset: Int = 0): List<WordResult> = withContext(Dispatchers.IO) {
        val japaneseResults = database.searchJapaneseFTS(query, limit / 2)
        val englishResults = database.searchEnglishFTS(query, limit / 2)
        
        val allResults = (japaneseResults + englishResults).map { convertFTS5ToWordResult(it) }
        return@withContext allResults.take(limit)
    }
    
    /**
     * FTS5 Romaji search
     */
    private suspend fun searchFTS5Romaji(query: String, limit: Int, offset: Int = 0): List<WordResult> = withContext(Dispatchers.IO) {
        // Convert romaji to both hiragana and katakana
        val kanaVariants = romajiConverter.toBothKanaVariants(query)
        val hiraganaQuery = kanaVariants[0]
        val katakanaQuery = kanaVariants[1]
        Log.d(TAG, "FTS5 Romaji converted: '$query' -> hiragana: '$hiraganaQuery', katakana: '$katakanaQuery'")
        
        val results = mutableListOf<WordResult>()
        
        // Search hiragana first (for native Japanese words)
        results.addAll(searchFTS5Japanese(hiraganaQuery, limit, offset))
        
        // Search katakana if different from hiragana (for foreign loanwords)
        if (katakanaQuery != hiraganaQuery && results.size < limit) {
            val remainingLimit = limit - results.size
            val katakanaResults = searchFTS5Japanese(katakanaQuery, remainingLimit, offset)
            // Add katakana results that aren't already in hiragana results
            for (katakanaResult in katakanaResults) {
                if (!results.any { it.kanji == katakanaResult.kanji && it.reading == katakanaResult.reading }) {
                    results.add(katakanaResult)
                }
            }
        }
        
        return@withContext results
    }
    
    /**
     * FTS5 Wildcard search
     */
    private suspend fun searchFTS5Wildcard(query: String, limit: Int, offset: Int = 0): List<WordResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "FTS5 Wildcard search for pattern: '$query'")
        
        // Convert wildcard pattern to SQL LIKE pattern
        val likePattern = query.replace("?", "_")
        Log.d(TAG, "Converted wildcard pattern: '$query' -> '$likePattern'")
        
        // Search using LIKE pattern in the database
        val fts5Results = database.searchWildcard(likePattern, limit)
        Log.d(TAG, "Wildcard search returned ${fts5Results.size} results")
        
        return@withContext fts5Results.map { convertFTS5ToWordResult(it) }
    }
    
    /**
     * FTS5 Parallel search (both romaji->Japanese and English)
     */
    private suspend fun searchFTS5Parallel(query: String, limit: Int, offset: Int = 0): List<WordResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "FTS5 Parallel search for: '$query'")
        
        val results = mutableListOf<WordResult>()
        
        // Search 1: Romaji to Japanese conversion WITH deinflection (both hiragana and katakana)
        val kanaVariants = romajiConverter.toBothKanaVariants(query)
        val hiraganaQuery = kanaVariants[0]
        val katakanaQuery = kanaVariants[1]
        Log.d(TAG, "FTS5 Parallel: Romaji '$query' -> Hiragana '$hiraganaQuery', Katakana '$katakanaQuery'")
        
        // Use the full Japanese search which includes deinflection for both variants
        val japaneseResults = mutableListOf<WordResult>()
        japaneseResults.addAll(searchFTS5Japanese(hiraganaQuery, limit / 4, offset))
        
        // Search katakana if different and we have room for more results
        if (katakanaQuery != hiraganaQuery && japaneseResults.size < limit / 2) {
            val remainingLimit = (limit / 2) - japaneseResults.size
            val katakanaResults = searchFTS5Japanese(katakanaQuery, remainingLimit, offset)
            // Add unique katakana results
            for (katakanaResult in katakanaResults) {
                if (!japaneseResults.any { it.kanji == katakanaResult.kanji && it.reading == katakanaResult.reading }) {
                    japaneseResults.add(katakanaResult)
                }
            }
        }
        
        Log.d(TAG, "FTS5 Parallel: Japanese search (with deinflection) returned ${japaneseResults.size} results")
        
        // Add Japanese results
        results.addAll(japaneseResults)
        
        // Search 2: English dictionary search
        val englishResults = searchFTS5English(query, limit / 2, offset)
        Log.d(TAG, "FTS5 Parallel: English search returned ${englishResults.size} results")
        
        // Add English results
        results.addAll(englishResults)
        
        Log.d(TAG, "FTS5 Parallel: Total results ${results.size} (Japanese: ${japaneseResults.size}, English: ${englishResults.size})")
        
        return@withContext results
    }
    
    /**
     * FTS5 Space-separated search - Search each word individually
     */
    private suspend fun searchFTS5SpaceSeparated(query: String, limit: Int, offset: Int = 0): List<WordResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "FTS5 Space-separated search for: '$query'")
        
        val results = mutableListOf<WordResult>()
        
        // Split query by spaces and search each word individually
        val words = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        Log.d(TAG, "FTS5 Space-separated: Split into ${words.size} words: ${words.joinToString(", ")}")
        
        for ((index, word) in words.withIndex()) {
            Log.d(TAG, "FTS5 Space-separated: Searching word ${index + 1}/${words.size}: '$word'")
            
            val wordResults = when {
                // Check for wildcard pattern - only for Japanese text
                word.contains("?") && isWildcardPattern(word) && isJapaneseText(word) -> {
                    Log.d(TAG, "FTS5 Space-separated: Wildcard search for Japanese: '$word'")
                    searchFTS5Wildcard(word, limit / words.size.coerceAtLeast(1))
                }
                // Handle wildcard on non-Japanese text gracefully
                word.contains("?") && !isJapaneseText(word) -> {
                    Log.d(TAG, "FTS5 Space-separated: Wildcard detected on non-Japanese word '$word' - skipping")
                    emptyList()
                }
                // Mixed script (Japanese + romaji)
                isMixedScript(word) -> {
                    Log.d(TAG, "FTS5 Space-separated: Mixed script search for: '$word'")
                    searchFTS5MixedScript(word, limit / words.size.coerceAtLeast(1))
                }
                // Pure romaji input
                containsRomaji(word) && !isJapaneseText(word) && isLikelyJapaneseRomaji(word) -> {
                    Log.d(TAG, "FTS5 Space-separated: Parallel romaji+English search for: '$word'")
                    searchFTS5Parallel(word, limit / words.size.coerceAtLeast(1))
                }
                // Pure Japanese text
                isJapaneseText(word) -> {
                    Log.d(TAG, "FTS5 Space-separated: Japanese search for: '$word'")
                    searchFTS5Japanese(word, limit / words.size.coerceAtLeast(1))
                }
                // English text
                isEnglishText(word) -> {
                    Log.d(TAG, "FTS5 Space-separated: English search for: '$word'")
                    searchFTS5English(word, limit / words.size.coerceAtLeast(1))
                }
                else -> {
                    Log.d(TAG, "FTS5 Space-separated: Unified search for: '$word'")
                    searchFTS5Unified(word, limit / words.size.coerceAtLeast(1))
                }
            }
            
            // Add results without source type labels for clean UI
            results.addAll(wordResults)
            Log.d(TAG, "FTS5 Space-separated: Word '$word' returned ${wordResults.size} results")
        }
        
        Log.d(TAG, "FTS5 Space-separated: Total results ${results.size} from ${words.size} words")
        
        return@withContext results
    }
    
    /**
     * FTS5 Mixed script search
     */
    private suspend fun searchFTS5MixedScript(query: String, limit: Int, offset: Int = 0): List<WordResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "FTS5 Mixed script search for: '$query'")
        
        // Check if this looks like a sentence and analyze it if so
        if (isSentenceInput(query)) {
            Log.d(TAG, "Detected sentence input, analyzing with Kuromoji")
            return@withContext searchWithSentenceAnalysis(query, limit, offset)
        }
        
        // Fall back to token-based parsing for non-sentence inputs
        val parser = MixedScriptParser()
        val tokens = parser.parseSentence(query)
        
        val results = mutableListOf<WordResult>()
        
        for (token in tokens) {
            when (token.tokenType) {
                TokenType.KANJI, TokenType.HIRAGANA, TokenType.KATAKANA, TokenType.MIXED -> {
                    results.addAll(searchFTS5Japanese(token.converted, 50))
                }
                TokenType.ROMAJI_WORD -> {
                    val hiragana = romajiConverter.toHiragana(token.original)
                    results.addAll(searchFTS5Japanese(hiragana, 50))
                }
                TokenType.ROMAJI_PARTICLE -> {
                    // Handle particles specially
                    val particleResults = searchFTS5Japanese(token.converted, 10)
                    results.addAll(particleResults.filter { it.reading == token.converted || it.kanji == token.converted })
                }
                else -> {
                    // Skip unknown tokens
                }
            }
            
            if (results.size >= limit) break
        }
        
        return@withContext results.take(limit)
    }
    
    /**
     * Check if input looks like a sentence (has multiple meaningful words/particles)
     */
    private fun isSentenceInput(input: String): Boolean {
        // Convert to Japanese first to normalize the analysis
        val normalized = japaneseWordExtractor.convertMixedScriptToJapanese(input)
        
        // Simple heuristics to detect sentences:
        // 1. Contains particles
        val commonParticles = listOf("は", "が", "を", "に", "で", "と", "の", "から", "まで", "より", "wo", "ga", "ni", "de", "to", "no", "kara", "made", "yori")
        val hasParticles = commonParticles.any { particle -> 
            input.contains(particle, ignoreCase = true) || normalized.contains(particle)
        }
        
        // 2. Has multiple words (length > 4 and contains multiple components)
        val hasMultipleWords = normalized.length > 4 && (
            normalized.contains(Regex("[\\u3040-\\u309F]+[\\u4E00-\\u9FAF]+")) || // hiragana + kanji
            normalized.contains(Regex("[\\u4E00-\\u9FAF]+[\\u3040-\\u309F]+")) || // kanji + hiragana  
            input.split(Regex("[a-zA-Z]+")).size > 2 // multiple romaji words
        )
        
        // 3. Contains verb endings that suggest conjugation
        val verbEndings = listOf("ます", "した", "して", "ている", "shite", "teiru", "masu", "shita")
        val hasVerbEndings = verbEndings.any { ending ->
            input.contains(ending, ignoreCase = true) || normalized.contains(ending)
        }
        
        val isSentence = hasParticles || (hasMultipleWords && hasVerbEndings)
        Log.d(TAG, "Sentence detection for '$input': particles=$hasParticles, multipleWords=$hasMultipleWords, verbEndings=$hasVerbEndings -> isSentence=$isSentence")
        
        return isSentence
    }
    
    /**
     * Search using simplified sentence analysis - use Kuromoji directly for better tokenization  
     */
    private suspend fun searchWithSentenceAnalysis(query: String, limit: Int, offset: Int): List<WordResult> = withContext(Dispatchers.IO) {
        try {
            // Convert mixed script to Japanese first
            val japaneseQuery = japaneseWordExtractor.convertMixedScriptToJapanese(query)
            Log.d(TAG, "Converted mixed script: '$query' -> '$japaneseQuery'")
            
            // Use Kuromoji to tokenize the full sentence properly
            val tokens = kuromojiAnalyzer.tokenize(japaneseQuery)
            Log.d(TAG, "Kuromoji tokenized sentence into ${tokens.size} tokens")
            
            val results = mutableListOf<WordResult>()
            val sentenceTokens = mutableSetOf<String>() // Track tokens we found from the sentence
            var tokenOrder = 0 // Track the order of tokens in the sentence
            
            // Process each meaningful token from the sentence
            for (token in tokens) {
                val surface = token.surface
                val baseForm = token.baseForm ?: surface
                val pos = token.partOfSpeechLevel1 ?: ""
                val reading = token.reading
                
                Log.d(TAG, "Processing token: surface='$surface', base='$baseForm', pos='$pos', reading='$reading'")
                
                // Only process meaningful words (nouns, verbs, adjectives)
                if (pos in setOf("名詞", "動詞", "形容詞", "副詞", "連体詞")) {
                    // Try multiple search strategies for better matching
                    val searches = mutableListOf<String>()
                    
                    // 1. Base form (dictionary form)
                    if (baseForm.isNotEmpty() && baseForm != "*") {
                        searches.add(baseForm)
                    }
                    
                    // 2. Surface form (as it appears in text)
                    if (surface != baseForm && surface.isNotEmpty()) {
                        searches.add(surface)
                    }
                    
                    // 3. Reading (if available and different from surface/base)
                    if (!reading.isNullOrEmpty() && reading != "*" && reading != surface && reading != baseForm) {
                        searches.add(reading)
                    }
                    
                    // Search for each form and take the best results
                    var bestResults: List<WordResult>? = null
                    var bestSearchTerm = ""
                    
                    for (searchTerm in searches) {
                        // For sentence analysis, search directly without deinflection
                        // Kuromoji already gave us the base form
                        // Use exactMatch=true to avoid wildcard matches like 国語*
                        val tokenResults = database.searchJapaneseFTS(searchTerm, 5, exactMatch = true)
                            .map { convertFTS5ToWordResult(it) }
                        
                        if (tokenResults.isNotEmpty()) {
                            // Prefer results with higher frequency/common status
                            val sortedResults = tokenResults.sortedWith(
                                compareByDescending<WordResult> { it.isCommon }
                                    .thenByDescending { it.frequency }
                            )
                            
                            if (bestResults == null || sortedResults.first().frequency > (bestResults.firstOrNull()?.frequency ?: 0)) {
                                bestResults = sortedResults
                                bestSearchTerm = searchTerm
                            }
                        }
                    }
                    
                    bestResults?.let { tokenResults ->
                        // Filter results to only include words that contain the search term
                        val filteredResults = tokenResults.filter { result ->
                            val containsInKanji = result.kanji?.contains(bestSearchTerm, ignoreCase = true) ?: false
                            val containsInReading = result.reading.contains(bestSearchTerm, ignoreCase = true)
                            containsInKanji || containsInReading
                        }
                        
                        if (filteredResults.isNotEmpty()) {
                            // Mark these results as coming from sentence analysis
                            // Use tokenOrder to maintain the order of appearance in the sentence
                            val sentenceResults = filteredResults.map { result ->
                                result.copy(
                                    wordOrder = tokenOrder // Use token order instead of fixed priority
                                )
                            }
                            results.addAll(sentenceResults)
                            sentenceTokens.add(bestSearchTerm)
                            sentenceTokens.add(surface)
                            sentenceTokens.add(baseForm)
                            tokenOrder++ // Increment for next token
                            
                            Log.d(TAG, "Added ${sentenceResults.size} filtered results for token '$bestSearchTerm' (from surface='$surface', base='$baseForm')")
                        } else {
                            Log.d(TAG, "No results containing '$bestSearchTerm' found from ${tokenResults.size} candidates")
                        }
                    }
                }
            }
            
            // Sort results by wordOrder to maintain sentence order
            val sortedResults = results.sortedBy { it.wordOrder }
            results.clear()
            results.addAll(sortedResults)
            
            Log.d(TAG, "Found ${results.size} results from sentence tokens (sorted by sentence order)")
            
            // If we have room for more results, add regular mixed script search
            val remaining = limit - results.size
            if (remaining > 0) {
                val parser = MixedScriptParser()
                val parserTokens = parser.parseSentence(query)
                
                for (token in parserTokens) {
                    when (token.tokenType) {
                        TokenType.KANJI, TokenType.HIRAGANA, TokenType.KATAKANA, TokenType.MIXED -> {
                            // Skip if we already processed this token via sentence analysis
                            if (!sentenceTokens.contains(token.converted)) {
                                // Direct database search without TenTen deinflection
                                val additionalResults = database.searchJapaneseFTS(token.converted, remaining / parserTokens.size.coerceAtLeast(1))
                                    .map { convertFTS5ToWordResult(it) }
                                // Filter to only include words containing the search term
                                val filteredAdditional = additionalResults.filter { result ->
                                    val containsInKanji = result.kanji?.contains(token.converted, ignoreCase = true) ?: false
                                    val containsInReading = result.reading.contains(token.converted, ignoreCase = true)
                                    containsInKanji || containsInReading
                                }
                                results.addAll(filteredAdditional)
                            }
                        }
                        TokenType.ROMAJI_WORD -> {
                            val hiragana = romajiConverter.toHiragana(token.original)
                            if (!sentenceTokens.contains(hiragana)) {
                                // Direct database search without TenTen deinflection
                                val additionalResults = database.searchJapaneseFTS(hiragana, remaining / parserTokens.size.coerceAtLeast(1))
                                    .map { convertFTS5ToWordResult(it) }
                                // Filter to only include words containing the search term
                                val filteredAdditional = additionalResults.filter { result ->
                                    val containsInKanji = result.kanji?.contains(hiragana, ignoreCase = true) ?: false
                                    val containsInReading = result.reading.contains(hiragana, ignoreCase = true)
                                    containsInKanji || containsInReading
                                }
                                results.addAll(filteredAdditional)
                            }
                        }
                        else -> { /* Skip other token types */ }
                    }
                    
                    if (results.size >= limit) break
                }
            }
            
            return@withContext results.take(limit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in sentence analysis, falling back to regular search", e)
            // Fall back to regular mixed script search
            val parser = MixedScriptParser()
            val tokens = parser.parseSentence(query)
            
            val results = mutableListOf<WordResult>()
            
            for (token in tokens) {
                when (token.tokenType) {
                    TokenType.KANJI, TokenType.HIRAGANA, TokenType.KATAKANA, TokenType.MIXED -> {
                        // Direct database search without TenTen deinflection
                        val directResults = database.searchJapaneseFTS(token.converted, 50)
                            .map { convertFTS5ToWordResult(it) }
                        results.addAll(directResults)
                    }
                    TokenType.ROMAJI_WORD -> {
                        val hiragana = romajiConverter.toHiragana(token.original)
                        // Direct database search without TenTen deinflection
                        val directResults = database.searchJapaneseFTS(hiragana, 50)
                            .map { convertFTS5ToWordResult(it) }
                        results.addAll(directResults)
                    }
                    else -> { /* Skip other token types */ }
                }
                
                if (results.size >= limit) break
            }
            
            return@withContext results.take(limit)
        }
    }
    
    /**
     * Convert SearchResult to WordResult
     */
    private fun convertFTS5ToWordResult(searchResult: SearchResult): WordResult {
        return WordResult(
            kanji = searchResult.kanji,
            reading = searchResult.reading,
            meanings = parseMeaningsJson(searchResult.meanings),
            isCommon = searchResult.isCommon,
            frequency = searchResult.frequency,
            wordOrder = 999,
            tags = emptyList(), // Tags will be loaded by TagDictSQLiteLoader for both JMdict and JMNEDict entries
            partsOfSpeech = try {
                searchResult.partsOfSpeech?.let {
                    gson.fromJson(it, typeToken.type)
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            },
            isJMNEDictEntry = searchResult.isJMNEDictEntry, // Now properly retrieved from database
            isDeinflectedValidConjugation = false // Not computed in simplified SQL anymore
        )
    }
    
    /**
     * Legacy combined search - tries both Japanese and English using HashMap
     * Kept for fallback purposes when FTS5 is not available
     */
    suspend fun searchLegacy(query: String): List<WordResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== LEGACY SEARCH START ===")
        Log.d(TAG, "Query: '$query'")

        val results = mutableListOf<WordResult>()

        when {
            // Check for wildcard pattern first
            query.contains("?") && isJapaneseText(query.replace("?", "")) -> {
                Log.d(TAG, "Using SQLite FTS5 search (wildcard patterns not supported)")
                results.addAll(searchFTS5(query.replace("?", "")))
            }
            // Mixed script (Japanese + romaji)
            isMixedScript(query) -> {
                Log.d(TAG, "Using mixed script search")
                results.addAll(searchMixedScript(query))
            }
            // Pure romaji input - but check if it's likely Japanese romaji
            containsRomaji(query) && !isJapaneseText(query) && isLikelyJapaneseRomaji(query) -> {
                Log.d(TAG, "Using romaji search")
                results.addAll(searchRomaji(query))
            }
            // Pure Japanese text
            isJapaneseText(query) -> {
                Log.d(TAG, "Using SQLite FTS5 search (detected Japanese text)")
                results.addAll(searchFTS5(query))
            }
            // English text
            isEnglishText(query) -> {
                Log.d(TAG, "Using English search")
                results.addAll(searchEnglish(query))
            }
            else -> {
                Log.d(TAG, "Unclear query - trying multiple approaches")
                // Try as romaji first (for cases like "ni")
                if (query.all { it.code in 0x0041..0x007A }) {
                    results.addAll(searchRomaji(query))
                }
                // If no results, try SQLite FTS5
                if (results.isEmpty()) {
                    results.addAll(searchFTS5(query))
                }
                // If still no results, try English
                if (results.isEmpty()) {
                    results.addAll(searchEnglish(query))
                }
            }
        }

        Log.d(TAG, "Total results: ${results.size}")
        Log.d(TAG, "=== LEGACY SEARCH END ===")

        results.distinctBy { "${it.kanji ?: ""}|${it.reading}" }
    }
    
    /**
     * Search for romaji input - converts to hiragana and searches
     * Example: "shiteimasu" -> convert to "しています" -> search
     * Also supports prefix matching for incomplete romaji like "nihong" -> "日本語"
     */
    suspend fun searchRomaji(query: String): List<WordResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Searching romaji: '$query'")
        
        // Convert romaji to hiragana
        val hiraganaQuery = romajiConverter.toHiragana(query)
        Log.d(TAG, "Converted to hiragana: '$hiraganaQuery'")
        
        // Search using the converted hiragana with SQLite FTS5
        var results = searchFTS5(hiraganaQuery, 100, 0)
        
        // If no results found and the conversion seems incomplete (contains latin letters),
        // try prefix search for partial romaji input - but only if it looks like Japanese romaji
        if (results.isEmpty() && hiraganaQuery.any { it.code in 0x0041..0x007A }) {
            // Only do prefix search if the query looks like incomplete Japanese romaji
            // (has substantial hiragana AND it's not a pure English word)
            val hiraganaCount = hiraganaQuery.count { it.code in 0x3040..0x309F }
            val latinCount = hiraganaQuery.count { it.code in 0x0041..0x007A }
            val isLikelyJapaneseRomaji = hiraganaCount >= 3 && hiraganaCount > latinCount
            
            if (isLikelyJapaneseRomaji) {
                Log.d(TAG, "No exact results found and conversion incomplete, trying prefix search")
                
                // Remove any remaining latin letters and try SQLite FTS5 search
                val cleanHiragana = hiraganaQuery.filter { it.code !in 0x0041..0x007A }
                if (cleanHiragana.length >= 2) { // At least 2 hiragana characters
                    Log.d(TAG, "Trying SQLite FTS5 search with: '$cleanHiragana'")
                    results = searchFTS5(cleanHiragana, 20, 0)
                }
            } else {
                Log.d(TAG, "Appears to be English word, skipping prefix search")
            }
        }
        
        // IMPORTANT: Copy deinflection info from hiragana key to romaji key
        // so the UI can find it using the original romaji query
        // But store the hiragana form for display purposes
        val hiraganaInfo = deinflectionCache[hiraganaQuery]
        if (hiraganaInfo != null) {
            // Create a new deinflection result that shows hiragana in the display
            val displayInfo = DeinflectionResult(
                originalForm = hiraganaQuery,  // Use hiragana form for display
                baseForm = hiraganaInfo.baseForm,
                reasonChain = hiraganaInfo.reasonChain,
                verbType = hiraganaInfo.verbType,
                transformations = hiraganaInfo.transformations
            )
            deinflectionCache[query] = displayInfo
            Log.d(TAG, "Copied deinflection info from '$hiraganaQuery' to '$query' with hiragana display")
        }
        
        return@withContext results
    }
    
    /**
     * Search for Japanese words that start with the given prefix - DEPRECATED
     * Used for autocomplete-style searching - REMOVED
     * Use search() method instead which uses SQLite FTS5
     */
    /*
    private suspend fun searchJapanesePrefix(prefix: String): List<WordResult> = withContext(Dispatchers.IO) {
        val extractor = jmdictExtractor
        if (extractor?.isDictionaryLoaded() != true) {
            return@withContext emptyList()
        }
        
        Log.d(TAG, "Prefix search for: '$prefix'")
        
        // Use existing prefix search but with higher limit for autocomplete
        val prefixResults = extractor.lookupWordsWithPrefix(prefix, 50)
        Log.d(TAG, "Prefix search found ${prefixResults.size} results")
        
        // Sort by relevance: exact prefix match in reading > kanji > longer words last
        // Also deprioritize proper nouns
        return@withContext prefixResults.sortedWith(
            compareBy<WordResult> { isProperNoun(it) }  // Proper nouns go to bottom
                .thenBy { result ->
                    when {
                        result.reading.startsWith(prefix) -> 0  // Reading starts with prefix
                        result.kanji?.startsWith(prefix) == true -> 1  // Kanji starts with prefix  
                        else -> 2  // Other matches
                    }
                }
                .thenBy { it.reading.length }  // Shorter words first
                .thenByDescending { it.frequency ?: 0 }  // Higher frequency first
        ).take(20)  // Limit results for autocomplete
    }
    */
    
    /**
     * Search mixed script text with smart prioritization
     * Example: "国語woべんきょうshiteimasu" -> extract and search individual words
     */
    suspend fun searchMixedScript(query: String): List<WordResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Searching mixed script: '$query'")
        
        // Use a list of lists to maintain word order
        val resultsByWord = mutableListOf<List<WordResult>>()
        
        // Extract all words from mixed script text
        val extractedWords = japaneseWordExtractor.extractMixedScriptWords(query)
        Log.d(TAG, "Extracted words: $extractedWords")
        
        // Keep words in original order based on their appearance in the query
        // Create a map of word to its position for reliable ordering
        val wordPositions = mutableMapOf<String, Int>()
        
        for (word in extractedWords) {
            val position = when {
                // Direct match in query
                query.contains(word) -> query.indexOf(word)
                // Romaji word (like "wo" or "shiteimasu")
                containsRomaji(word) -> query.indexOf(word)
                // Hiragana converted from romaji
                !containsRomaji(word) -> {
                    // Check if this hiragana word came from romaji in the query
                    // Find romaji words in the query and see if any convert to this hiragana
                    val romajiMatches = """[a-zA-Z]+""".toRegex().findAll(query)
                    var romajiPosition = 999
                    for (match in romajiMatches) {
                        if (romajiConverter.toHiragana(match.value) == word) {
                            romajiPosition = match.range.first
                            break
                        }
                    }
                    romajiPosition
                }
                else -> 999
            }
            wordPositions[word] = position
        }
        
        Log.d(TAG, "Word positions: $wordPositions")
        
        val sortedWords = extractedWords.sortedWith(
            compareBy<String> { wordPositions[it] ?: 999 }
                .thenByDescending { it.length } // Within same position, prefer longer matches
        )
        
        // Keep track of which exact word matches we've found
        val exactMatches = mutableSetOf<String>()
        
        // Check if we have any meaningful words (2+ characters)
        val hasMeaningfulWords = sortedWords.any { it.length >= 2 }
        
        // Keep track of any conjugated words for main query deinflection info
        var mainQueryDeinflectionInfo: DeinflectionResult? = null
        
        // Search for each extracted word, maintaining order
        for ((wordIndex, word) in sortedWords.withIndex()) {
            val wordResults = if (containsRomaji(word)) {
                val results = searchRomaji(word)
                // Check if this romaji word has deinflection info we should use for the main query
                // Only use deinflection info from significant verbs, not short words or particles
                val wordDeinflectionInfo = deinflectionCache[word]
                if (wordDeinflectionInfo != null && mainQueryDeinflectionInfo == null && 
                    word.length >= 3 && !isParticle(romajiConverter.toHiragana(word))) {
                    mainQueryDeinflectionInfo = wordDeinflectionInfo
                }
                results
            } else {
                // Skip single kanji if we have meaningful longer words
                if (word.length == 1 && isKanji(word) && hasMeaningfulWords) {
                    emptyList() // Skip single kanji when we have better options
                } else if (word.length == 1 && isKanji(word)) {
                    searchFTS5(word, 10, 0) // Reduced limit for single kanji
                } else if (isParticle(word)) {
                    // For particles, only get exact matches
                    searchFTS5(word, 100, 0).filter { result ->
                        result.kanji == word || result.reading == word
                    }
                } else {
                    val results = searchFTS5(word, 100, 0)
                    // Check if this Japanese word has deinflection info we should use for the main query
                    // Only use deinflection info from significant verbs, not short words or particles
                    val wordDeinflectionInfo = deinflectionCache[word]
                    if (wordDeinflectionInfo != null && mainQueryDeinflectionInfo == null && 
                        word.length >= 3 && !isParticle(word)) {
                        mainQueryDeinflectionInfo = wordDeinflectionInfo
                    }
                    results
                }
            }
            
            // Process results for this word and add to ordered collection
            val currentWordResults = mutableListOf<WordResult>()
            
            // Prioritize exact matches for longer words
            if (word.length >= 2) {
                val exactWordMatches = wordResults.filter { result ->
                    result.kanji == word || result.reading == word
                }
                
                if (exactWordMatches.isNotEmpty()) {
                    exactMatches.addAll(exactWordMatches.map { "${it.kanji ?: ""}|${it.reading}" })
                    currentWordResults.addAll(exactWordMatches)
                } else {
                    // For deinflection results, add all results since they come from conjugated forms
                    val isConjugatedForm = word.endsWith("ています") || word.endsWith("ている") || 
                                          word.endsWith("てる") || word.endsWith("でる") ||
                                          word.endsWith("ました") || word.endsWith("ます") ||
                                          word.endsWith("でした") || word.endsWith("です") ||
                                          word.endsWith("んだ") || word.endsWith("った") ||
                                          word.endsWith("いた") || word.endsWith("えた") ||
                                          word.endsWith("した") || word.endsWith("きた") ||
                                          word.endsWith("ない") || word.endsWith("なく")
                    
                    if (containsRomaji(word) || isConjugatedForm) {
                        // These are likely deinflection results, keep them all
                        currentWordResults.addAll(wordResults)
                    } else {
                        // For other non-exact matches, only add if they're relevant
                        val relevantResults = wordResults.filter { result ->
                            val kanji = result.kanji ?: ""
                            val reading = result.reading
                            // Only keep if it starts with or equals our search word
                            kanji == word || reading == word || 
                            kanji.startsWith(word) || reading.startsWith(word)
                        }
                        currentWordResults.addAll(relevantResults)
                    }
                }
            } else {
                currentWordResults.addAll(wordResults)
            }
            
            // Assign word order and sort this word's results by frequency and common status
            if (currentWordResults.isNotEmpty()) {
                // Assign word order to all results from this word
                val resultsWithOrder = currentWordResults.map { result ->
                    result.copy(wordOrder = wordIndex)
                }
                
                val sortedWordResults = resultsWithOrder.sortedWith(
                    compareBy<WordResult> { isProperNoun(it) }  // Proper nouns go to bottom
                        .thenByDescending { it.isCommon }
                        .thenByDescending { it.frequency ?: 0 }
                )
                resultsByWord.add(sortedWordResults)
            }
        }
        
        // Store deinflection info under main query if we found any conjugated words
        if (mainQueryDeinflectionInfo != null) {
            deinflectionCache[query] = mainQueryDeinflectionInfo
            Log.d(TAG, "Stored deinflection info for main query '$query': ${mainQueryDeinflectionInfo.baseForm}")
        }
        
        // Flatten results while maintaining word order: word1 results, then word2 results, etc.
        val allResults = resultsByWord.flatten()
        
        // Remove duplicates while preserving order (first occurrence wins)
        val seenResults = mutableSetOf<String>()
        val filteredResults = allResults.filter { result ->
            val key = "${result.kanji ?: ""}|${result.reading}"
            if (key in seenResults) {
                false
            } else {
                seenResults.add(key)
                true
            }
        }
        
        Log.d(TAG, "Processed ${allResults.size} results into ${filteredResults.size} unique results in word order")
        return@withContext filteredResults
    }

    /**
     * Parse meanings JSON string
     */
    private fun parseMeaningsJson(meaningsJson: String?): List<String> {
        if (meaningsJson.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(meaningsJson, type)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse meanings JSON", e)
            emptyList()
        }
    }

    /**
     * Get the database instance
     */
    fun getDatabase(): DictionaryDatabase = database

    /**
     * Check if text is Japanese
     */
    private fun isJapaneseText(text: String): Boolean {
        return text.any { char ->
            val code = char.code
            // Hiragana, Katakana, or Kanji
            (code in 0x3040..0x309F) ||
                    (code in 0x30A0..0x30FF) ||
                    (code in 0x4E00..0x9FAF)
        }
    }
    
    /**
     * Check if text contains romaji characters
     */
    private fun containsRomaji(text: String): Boolean {
        return romajiConverter.containsRomaji(text)
    }
    
    /**
     * Check if text is a valid wildcard pattern
     */
    private fun isWildcardPattern(text: String): Boolean {
        if (!text.contains("?")) return false
        
        // Check if the non-wildcard characters are Japanese
        val nonWildcardText = text.replace("?", "")
        if (nonWildcardText.isEmpty()) return false
        
        return isJapaneseText(nonWildcardText)
    }
    
    /**
     * Check if a string contains only kanji characters
     */
    private fun isKanji(text: String): Boolean {
        if (text.isEmpty()) return false
        return text.all { char ->
            val codePoint = char.code
            (codePoint in 0x4E00..0x9FAF) || (codePoint in 0x3400..0x4DBF)
        }
    }
    
    /**
     * Check if a word is a common Japanese particle
     */
    private fun isParticle(word: String): Boolean {
        return word in setOf("を", "は", "が", "の", "に", "で", "と", "から", "まで", "より", "へ", "や", "も", "か", "ね", "よ", "ぞ", "ぜ")
    }
    
    /**
     * Check if a query might be a conjugated form that should be deinflected
     * even if there are direct database matches
     */
    private fun isPotentialConjugatedForm(query: String): Boolean {
        // Common conjugated endings that should be deinflected
        val conjugatedEndings = setOf(
            "た", "だ",           // past tense
            "ます", "ました",      // polite forms
            "ません", "ませんでした", // negative polite
            "ない", "なかった",    // negative forms
            "ている", "ていた",    // continuous forms
            "れる", "られる",      // passive/potential
            "せる", "させる"       // causative
        )
        
        // Check if query ends with any conjugated ending
        return conjugatedEndings.any { query.endsWith(it) }
    }
    
    /**
     * Check if a word result is a proper noun (person/place name)
     * Proper nouns should be deprioritized in search results
     */
    private fun isProperNoun(result: WordResult): Boolean {
        // Use database flag first (most reliable)
        if (result.isJMNEDictEntry) {
            return true
        }
        
        // Fallback to tag-based check for entries without database flag
        val properNounTags = setOf(
            "n-pr", "person", "place", "surname", "given", "masc", "fem", "unclass", 
            "company", "organization", "station", "group", "char", "fict", "legend", 
            "myth", "relig", "dei", "ship", "product", "work", "ev", "obj", "creat", 
            "serv", "doc", "oth"
        )
        
        // Check parts of speech first (fastest)
        val hasProperNounTag = result.partsOfSpeech.any { pos ->
            properNounTags.any { tag -> pos.lowercase().contains(tag) }
        }
        
        if (hasProperNounTag) return true
        
        // Check tags
        val hasProperNounInTags = result.tags.any { tag ->
            properNounTags.any { properTag -> 
                tag.lowercase().contains(properTag) || tag.lowercase() == properTag
            }
        }
        
        return hasProperNounInTags
    }
    
    /**
     * Check if a SearchResult is a proper noun (person/place name)
     * Helper function for sorting SearchResult objects
     */
    private fun isProperNounSearchResult(result: SearchResult): Boolean {
        // Convert SearchResult to WordResult temporarily to use existing logic
        val wordResult = convertFTS5ToWordResult(result)
        return isProperNoun(wordResult)
    }
    
    /**
     * Check if text is mixed script (Japanese + romaji)
     */
    private fun isMixedScript(text: String): Boolean {
        return isJapaneseText(text) && containsRomaji(text)
    }

    /**
     * Check if text is primarily English
     */
    private fun isEnglishText(text: String): Boolean {
        return text.any { it.code in 0x0041..0x007A } &&  // Has English letters
                !isJapaneseText(text)  // But no Japanese
    }
    
    /**
     * Check if text is likely Japanese romaji (not just English with some romaji letters)
     */
    private fun isLikelyJapaneseRomaji(text: String): Boolean {
        // First check common English words to exclude them early
        val commonEnglishWords = setOf(
            "search", "the", "and", "for", "are", "with", "his", "they", "have", "this",
            "will", "you", "that", "but", "not", "from", "she", "been", "more", "were",
            "eat", "go", "get", "see", "come", "take", "know", "good", "first", "last",
            "long", "great", "little", "own", "other", "old", "right", "big", "high", "small"
        )
        if (text.lowercase() in commonEnglishWords) {
            return false
        }
        
        // Common Japanese romaji patterns
        val japaneseRomajiPatterns = listOf(
            ".*[aiueo].*[aiueo].*", // Multiple vowels (common in Japanese)
            ".*sh.*", ".*ch.*", ".*ts.*", ".*ky.*", ".*gy.*", ".*ny.*", // Japanese sounds
            ".*ou.*", ".*ei.*", ".*ai.*", // Long vowel patterns  
            ".*masu.*", ".*desu.*", ".*shimasu.*", // Common endings
            ".*[aiueo]{3,}.*", // 3+ consecutive vowels (more specifically Japanese)
        )
        
        // Check if it matches Japanese patterns
        val hasJapanesePattern = japaneseRomajiPatterns.any { text.matches(it.toRegex()) }
        
        // For short words, be more lenient if they don't look obviously English
        if (text.length <= 3) {
            // Short words with consonant clusters are likely English (like "str", "thr")
            val hasConsonantCluster = text.lowercase().matches(Regex(".*[bcdfghjklmnpqrstvwxz]{2,}.*"))
            return !hasConsonantCluster && hasJapanesePattern
        }
        
        return hasJapanesePattern
    }

    /**
     * Get full entry details by ID
     * Useful for showing detailed information
     */
    suspend fun getEntryDetails(id: Long): WordResult? = withContext(Dispatchers.IO) {
        val entry = database.getEntry(id) ?: return@withContext null

        WordResult(
            kanji = entry.kanji,
            reading = entry.reading,
            meanings = parseMeaningsJson(entry.meanings),
            isCommon = false,  // Would need to be retrieved from database
            frequency = 0,     // Would need to be retrieved from database
            wordOrder = 999,
            tags = emptyList(),
            partsOfSpeech = emptyList(),
            isJMNEDictEntry = false,
            isDeinflectedValidConjugation = false
        )
    }

    /**
     * Warm up the SQLite cache
     * Call this in background after app starts
     */
    suspend fun warmUpCache() = withContext(Dispatchers.IO) {
        try {
            // Do a simple query to load SQLite metadata
            database.searchEnglishFTS("test", 1)
            Log.d(TAG, "SQLite cache warmed up")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to warm up cache", e)
        }
    }
    
    /**
     * Clear all cached deinflection information - useful for debugging
     */
    fun clearDeinflectionCache() {
        deinflectionCache.clear()
        directMatchQueries.clear()
        progressiveMatchQueries.clear()
        noDeinfectionQueries.clear()
        Log.d(TAG, "Cleared all deinflection caches")
    }

    /**
     * Search by Japanese text with wildcard support - DEPRECATED
     * ? = any single character
     * Uses SQL LIKE with _ for single character wildcard - REMOVED
     * Use search() method instead which uses SQLite FTS5
     */
    /*
    suspend fun searchJapaneseWithWildcard(query: String): List<WordResult> = withContext(Dispatchers.IO) {
        if (!query.contains("?")) {
            // No wildcards, use regular search
            return@withContext searchJapanese(query)
        }

        // Convert ? to SQL LIKE wildcard _
        val sqlPattern = query.replace("?", "_")

        val db = database.readableDatabase
        val results = mutableListOf<WordResult>()

        // Search in both kanji and reading columns
        // Remove the length restriction as it's causing issues
        val sql = """
        SELECT DISTINCT kanji, reading, meanings, frequency
        FROM ${DictionaryDatabase.TABLE_ENTRIES}
        WHERE kanji LIKE ? OR reading LIKE ?
        ORDER BY frequency DESC
        LIMIT 100
    """

        db.rawQuery(sql, arrayOf(sqlPattern, sqlPattern)).use { cursor ->
            while (cursor.moveToNext()) {
                val kanji = cursor.getString(0)
                val reading = cursor.getString(1)
                val meaningsJson = cursor.getString(2)
                val frequency = cursor.getInt(3)

                // Only include results that match the pattern length
                if ((kanji != null && kanji.length == query.length) ||
                    (reading.length == query.length)) {
                    results.add(WordResult(
                        kanji = kanji,
                        reading = reading,
                        meanings = parseMeaningsJson(meaningsJson),
                        frequency = frequency,
                        wordOrder = 999,
                        tags = emptyList(),
                        partsOfSpeech = emptyList()
                    ))
                }
            }
        }

        Log.d(TAG, "Wildcard search for '$query' (pattern: '$sqlPattern') returned ${results.size} results")
        return@withContext results
    }
    */
    
    /**
     * Get kanji information from database
     */
    suspend fun getKanjiInfo(kanjiList: List<String>): List<KanjiResult> = withContext(Dispatchers.IO) {
        val databaseEntries = database.getKanjiByCharacters(kanjiList)
        
        // Create a map for quick lookup
        val entriesMap = databaseEntries.associateBy { it.kanji }
        
        // Return results in the same order as the input list
        val orderedResults = kanjiList.mapNotNull { kanji ->
            entriesMap[kanji]?.let { entry ->
                // Debug logging for radical data
                Log.d(TAG, "Kanji '${entry.kanji}' database entry:")
                Log.d(TAG, "  radicalNames: '${entry.radicalNames}'")
                Log.d(TAG, "  radical: '${entry.radical}'")
                Log.d(TAG, "  radicalNumber: ${entry.radicalNumber}")
                
                val parsedRadicalNames = parseReadingsFromDatabase(entry.radicalNames)
                Log.d(TAG, "  parsed radicalNames: $parsedRadicalNames")
                
                val finalRadicalNames = parsedRadicalNames
                
                // Get kanji components from kradfile data
                val components = database.getKanjiComponents(entry.kanji)
                Log.d(TAG, "  components: $components")
                
                KanjiResult(
                    kanji = entry.kanji,
                    onReadings = parseReadingsFromDatabase(entry.onReadings),
                    kunReadings = parseReadingsFromDatabase(entry.kunReadings),
                    meanings = parseMeaningsFromDatabase(entry.meanings),
                    strokeCount = entry.strokeCount,
                    jlptLevel = entry.jlptLevel,
                    frequency = entry.frequency,
                    grade = entry.grade,
                    nanori = parseReadingsFromDatabase(entry.nanoriReadings),
                    radicalNames = finalRadicalNames,
                    classicalRadical = entry.radicalNumber,
                    radicalNumber = entry.radicalNumber,
                    components = components
                )
            }
        }
        
        return@withContext orderedResults
    }

    /**
     * Check for irregular verb conjugation patterns that Kuromoji might misparse
     * Handles special cases for 来る (kuru) and 行く (iku) irregular verbs
     */
    private fun checkIrregularVerbs(query: String): DeinflectionResult? {
        Log.d(TAG, "Checking irregular verb patterns for: '$query'")
        
        // 来る (kuru) irregular patterns
        val kuruPatterns = mapOf(
            // Negative patterns
            "こない" to "来る",        // konai -> kuru
            "こなかった" to "来る",    // konakatta -> kuru  
            "きません" to "来る",      // kimasen -> kuru
            "きませんでした" to "来る", // kimasendeshita -> kuru
            
            // Potential/passive patterns  
            "こられる" to "来る",      // korareru -> kuru
            "こられない" to "来る",    // korarenai -> kuru
            "こられません" to "来る",  // koráremasen -> kuru
            "こられた" to "来る",      // korareta -> kuru
            
            // Te-form and related
            "きて" to "来る",         // kite -> kuru
            "きている" to "来る",     // kiteiru -> kuru
            "きた" to "来る",         // kita -> kuru
            
            // Conditional
            "くれば" to "来る",       // kureba -> kuru
            "きたら" to "来る",       // kitara -> kuru
            
            // Polite forms
            "きます" to "来る",       // kimasu -> kuru
            "きました" to "来る",     // kimashita -> kuru
            
            // Imperative
            "こい" to "来る"          // koi -> kuru
        )
        
        // 行く (iku) irregular patterns
        val ikuPatterns = mapOf(
            // Past tense (irregular)
            "いった" to "行く",       // itta -> iku (irregular past)
            "いかなかった" to "行く", // ikanakatta -> iku
            
            // Te-form (irregular)
            "いって" to "行く",       // itte -> iku (irregular te-form)
            "いっている" to "行く",   // itteiru -> iku
            
            // Negative
            "いかない" to "行く",     // ikanai -> iku
            "いきません" to "行く",   // ikimasen -> iku
            "いきませんでした" to "行く", // ikimasendeshita -> iku
            
            // Polite forms
            "いきます" to "行く",     // ikimasu -> iku
            "いきました" to "行く",   // ikimashita -> iku
            
            // Conditional
            "いけば" to "行く",       // ikeba -> iku
            "いったら" to "行く"      // ittara -> iku
        )
        
        // Check 来る patterns first
        kuruPatterns[query]?.let { baseForm ->
            Log.d(TAG, "Found 来る irregular pattern: '$query' -> '$baseForm'")
            return DeinflectionResult(
                originalForm = query,
                baseForm = baseForm,
                reasonChain = listOf("irregular verb (来る)"),
                verbType = VerbType.KURU_IRREGULAR,
                transformations = listOf(
                    DeinflectionStep(
                        from = query,
                        to = baseForm,
                        reason = "irregular verb (来る)",
                        ruleId = "kuru_irregular"
                    )
                )
            )
        }
        
        // Check 行く patterns
        ikuPatterns[query]?.let { baseForm ->
            Log.d(TAG, "Found 行く irregular pattern: '$query' -> '$baseForm'")
            return DeinflectionResult(
                originalForm = query,
                baseForm = baseForm,
                reasonChain = listOf("irregular verb (行く)"),
                verbType = VerbType.IKU_IRREGULAR,
                transformations = listOf(
                    DeinflectionStep(
                        from = query,
                        to = baseForm,
                        reason = "irregular verb (行く)",
                        ruleId = "iku_irregular"
                    )
                )
            )
        }
        
        // No irregular patterns found
        Log.d(TAG, "No irregular verb patterns found for: '$query'")
        return null
    }
    
    /**
     * Parse and clean meanings from database field
     * Handles JSON arrays, quoted strings, and comma-separated values
     */
    private fun parseMeaningsFromDatabase(meaningsString: String?): List<String> {
        if (meaningsString.isNullOrBlank()) {
            return emptyList()
        }
        
        // Remove surrounding brackets if present (JSON array format)
        val cleaned = meaningsString.trim()
            .removePrefix("[")
            .removeSuffix("]")
        
        // Split by comma and clean each meaning
        return cleaned.split(",")
            .map { meaning ->
                meaning.trim()
                    .removePrefix("\"")  // Remove leading quotes
                    .removeSuffix("\"")   // Remove trailing quotes
                    .removePrefix("'")    // Remove leading single quotes
                    .removeSuffix("'")    // Remove trailing single quotes
                    .trim()
            }
            .filter { it.isNotBlank() }
    }

    /**
     * Parse and clean readings/radical names from database field
     * Same logic as meanings but reusable for readings and radical names
     */
    private fun parseReadingsFromDatabase(readingsString: String?): List<String> {
        if (readingsString.isNullOrBlank()) {
            return emptyList()
        }
        
        // Remove surrounding brackets if present (JSON array format)
        val cleaned = readingsString.trim()
            .removePrefix("[")
            .removeSuffix("]")
        
        // Split by comma and clean each reading
        return cleaned.split(",")
            .map { reading ->
                reading.trim()
                    .removePrefix("\"")  // Remove leading quotes
                    .removeSuffix("\"")   // Remove trailing quotes
                    .removePrefix("'")    // Remove leading single quotes
                    .removeSuffix("'")    // Remove trailing single quotes
                    .trim()
            }
            .filter { it.isNotBlank() }
    }
    
    /**
     * Helper methods for deinflection and cache management
     */
    private fun selectBestDeinflection(deinflections: List<DeinflectionResult>, query: String): DeinflectionResult {
        // Return the first (best) deinflection result
        return deinflections.first()
    }
    
    private fun storeDeinflectionInfo(query: String, result: DeinflectionResult) {
        // Store the deinflection result in cache
        deinflectionCache[query] = result
    }
    
    private fun isValidConjugationByKuromoji(query: String, baseForm: String): Boolean {
        // Simple validation - could be enhanced with more sophisticated checks
        return query != baseForm && baseForm.isNotEmpty()
    }
}