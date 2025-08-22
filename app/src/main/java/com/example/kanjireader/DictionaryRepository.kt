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

    fun getDeinflectionInfo(query: String): DeinflectionResult? {
        // Don't provide conjugation info for space-separated queries
        if (query.contains(Regex("\\s+"))) {
            return null
        }

        // If this query had direct database matches, don't provide deinflection info
        // BUT allow deinflection for potential conjugated forms
        if (directMatchQueries.contains(query)) {
            return null
        }

        // If this query is marked as having too many direct results, don't compute deinflection
        // UNLESS it's a potential conjugated form that we should still try to deinflect
        if (noDeinfectionQueries.contains(query)) {
            return null
        }

        // First check cache, but validate it for direct matches
        val cached = deinflectionCache[query]
        if (cached != null) {
            // No need to perform another database search here.
            // The cache should be trusted.
            return cached
        }

        // If this query used progressive matching (found a dictionary word), don't compute deinflection
        if (progressiveMatchQueries.contains(query)) {
            return null
        }

        // If this query had direct database matches, don't compute deinflection
        // UNLESS it's a potential conjugated form that should be deinflected
        if (directMatchQueries.contains(query)) {
            return null
        }

        // If not in cache, try to compute deinflection now using Kuromoji
        try {
            val deinflections = kuromojiAnalyzer.deinflect(query)
            if (deinflections.isNotEmpty()) {
                val result = selectBestDeinflection(deinflections, query)
                storeDeinflectionInfo(query, result)
                // Only return the result if it was successfully stored (passed validation)
                return deinflectionCache[query]
            }
        } catch (e: Exception) {
            // Handle exceptions
        }

        return null
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
            val searchQuery = kanaVariants[0]
            val alternateVariant = if (kanaVariants.size > 1) kanaVariants[1] else null

            Log.d(TAG, "🔍 Kana variants for '$query': primary='$searchQuery', alternate='$alternateVariant'")

            // Fetch all potential results from the database
            val allSearchResults = mutableSetOf<SearchResult>()

            // 1. Direct search on the query itself (and its kana variant)
            allSearchResults.addAll(database.searchJapaneseFTS(searchQuery, limit))
            if (alternateVariant != null && alternateVariant != searchQuery) {
                allSearchResults.addAll(database.searchJapaneseFTS(alternateVariant, limit))
            }

            // 2. Deinflection search for base forms
            val particles = setOf("を", "は", "が", "の", "に", "で", "と", "から", "まで", "より", "へ", "や", "も", "か", "ね", "よ", "ぞ", "ぜ")
            val endsWithParticle = particles.any { searchQuery.endsWith(it) }

            if (!endsWithParticle) {
                val deinflection = getDeinflectionInfo(searchQuery)
                if (deinflection != null && deinflection.baseForm != searchQuery) {
                    allSearchResults.addAll(database.searchJapaneseFTS(deinflection.baseForm, limit))
                }
            }

            val directSearchTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "⏱️ Combined search took ${directSearchTime}ms, found ${allSearchResults.size} unique results")

            // Convert all unique SearchResults to WordResult objects
            val convertedResults = allSearchResults.map { searchResult ->
                val wordResult = convertFTS5ToWordResult(searchResult)

                // Mark the result if it's a valid deinflected conjugation
                val deinflectionResult = getDeinflectionInfo(query)
                val isDeinflected = deinflectionResult != null &&
                        (wordResult.kanji == deinflectionResult.baseForm || wordResult.reading == deinflectionResult.baseForm) &&
                        !isProperNoun(wordResult)

                wordResult.copy(isDeinflectedValidConjugation = isDeinflected)
            }

            // Apply a single, clear sort order
            val finalSortedResults = convertedResults.sortedWith(
                compareBy<WordResult> { !it.isDeinflectedValidConjugation }  // Priority 1: Deinflected forms first
                    .thenBy { !(it.kanji == query || it.reading == query) }   // Priority 2: Exact matches next
                    .thenByDescending { it.isCommon }                     // Priority 3: Common words next
                    .thenByDescending { it.frequency ?: 0 }               // Priority 4: Higher frequency
                    .thenBy { it.reading.length }                         // Priority 5: Shorter readings
            )

            // Add single kanji character search for single kanji queries
            val finalResultsWithKanji = if (query.length == 1 && isKanji(query)) {
                if (finalSortedResults.none { it.kanji == query }) {
                    val kanjiResults = database.searchKanjiCharacters(query, 5).map { convertKanjiEntryToWordResult(it, query) }
                    kanjiResults + finalSortedResults
                } else {
                    finalSortedResults
                }
            } else {
                finalSortedResults
            }

            val filteredResults = filterKatakanaDuplicatesSimple(finalResultsWithKanji).distinctBy { "${it.kanji}|${it.reading}" }.take(limit)

            val totalTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "🏁 Search completed in ${totalTime}ms with ${filteredResults.size} total results")

            return@withContext filteredResults
        } catch (e: Exception) {
            Log.e(TAG, "❌ REPOSITORY ERROR: searchFTS5Japanese failed", e)
            return@withContext emptyList()
        }
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
     * Prioritize search results based on meaning position and match quality
     * Enhanced with phrase-aware scoring and stop words filtering
     */
    private fun prioritizeByMeaningPosition(results: List<WordResult>, query: String): List<WordResult> {
        val queryLower = query.lowercase().trim()
        val isPhrase = queryLower.contains(" ")

        val sorted = results.sortedWith(
            compareBy<WordResult> { isProperNoun(it) }
                .thenBy { result ->
                    calculateMeaningScore(result, queryLower, isPhrase)
                }
                .thenByDescending { it.isCommon }
                .thenByDescending { it.frequency ?: 0 }
                .thenBy { it.reading.length }
                .thenBy { it.meanings.size }
        )

        //logPrioritizationDebug(sorted, query, limit = 10)

        return sorted
    }

    /**
     * Calculate meaning score with phrase-aware logic and meaning position penalty
     */
    private fun calculateMeaningScore(result: WordResult, queryLower: String, isPhrase: Boolean): Int {
        var minScore = 500 // Start with a very high score

        if (result.reading.lowercase().startsWith(queryLower) || (result.kanji?.lowercase()?.startsWith(queryLower) == true)) {
            minScore = 0
        }

        result.meanings.forEachIndexed { index, meaning ->
            val meaningLower = meaning.lowercase()
            var currentBaseScore = 5

            if (isPhrase) {
                when {
                    meaningLower.contains(queryLower) -> currentBaseScore = 1
                    isCompoundWordMatch(meaningLower, queryLower) -> currentBaseScore = 2
                    containsMostPhraseWords(meaningLower, queryLower) -> currentBaseScore = 3
                    containsSomePhraseWords(meaningLower, queryLower) -> currentBaseScore = 4
                }
            } else {
                when {
                    meaningLower == queryLower -> currentBaseScore = 1
                    meaningLower.startsWith("$queryLower ") -> currentBaseScore = 2
                    meaningLower.contains(" $queryLower") ||
                            meaningLower.contains("($queryLower)") -> currentBaseScore = 2
                    meaningLower.contains(queryLower) -> currentBaseScore = 3
                }
            }

            val meaningPositionPenalty = index * 5
            val finalScore = currentBaseScore + meaningPositionPenalty

            if (finalScore < minScore) {
                minScore = finalScore
            }
        }

        return minScore
    }

    /**
     * Helper function to log the top N results after prioritization.
     */
    private fun logPrioritizationDebug(sortedResults: List<WordResult>, query: String, limit: Int = 50) {
        Log.d(TAG, "=== PRIORITIZATION DEBUG for '$query' ===")
        Log.d(TAG, "Total results: ${sortedResults.size}")
        Log.d(TAG, "First ${limit} results after prioritization:")

        sortedResults.take(limit).forEachIndexed { index, result ->
            val displayName = result.kanji ?: result.reading
            val meaningScore = calculateMeaningScore(result, query.lowercase().trim(), query.contains(" "))
            val primaryMeaning = result.meanings.firstOrNull()?.lowercase()?.trim() ?: ""

            Log.d(TAG, "  #${index + 1}: '$displayName' (score=$meaningScore, common=${result.isCommon}, freq=${result.frequency})")
            Log.d(TAG, "      Primary meaning: '$primaryMeaning'")
        }
    }

    /**
     * Check if meaning represents a compound word that matches the phrase concept
     */
    private fun isCompoundWordMatch(meaning: String, phrase: String): Boolean {
        // Examples: "mother and child" should match "parent and child", "family", etc.
        val meaningWords = meaning.split(" ", ",", ";").map { it.trim() }
        val phraseWords = phrase.split(" ")

        // If meaning is a single compound concept that relates to multiple phrase words
        return meaningWords.size <= 2 && phraseWords.size >= 2 &&
               phraseWords.any { word -> meaningWords.any { it.contains(word) || word.contains(it) } }
    }

    /**
     * Check if meaning contains most words from the phrase
     */
    private fun containsMostPhraseWords(meaning: String, phrase: String): Boolean {
        val phraseWords = phrase.split(" ")
        val matchedWords = phraseWords.count { meaning.contains(it) }
        return matchedWords >= (phraseWords.size * 0.7).toInt() // At least 70% of phrase words
    }

    /**
     * Check if meaning contains some words from the phrase
     */
    private fun containsSomePhraseWords(meaning: String, phrase: String): Boolean {
        val phraseWords = phrase.split(" ")
        val matchedWords = phraseWords.count { meaning.contains(it) }
        return matchedWords >= 1 && matchedWords < (phraseWords.size * 0.7).toInt()
    }

    /**
     * FTS5 Unified search (searches both Japanese and English)
     */
    private suspend fun searchFTS5Unified(query: String, limit: Int, offset: Int = 0): List<WordResult> = withContext(Dispatchers.IO) {
        val japaneseResults = database.searchJapaneseFTS(query, limit / 2)
        val englishResults = database.searchEnglishFTS(query, limit / 2)

        val allResults = (japaneseResults + englishResults).map { convertFTS5ToWordResult(it) }

        // Apply meaning-based prioritization for better English match ranking
        val prioritizedResults = prioritizeByMeaningPosition(allResults, query)

        return@withContext prioritizedResults.take(limit)
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
     * FTS5 Space-separated search - First try as complete phrase, then individual words
     */
    private suspend fun searchFTS5SpaceSeparated(query: String, limit: Int, offset: Int = 0): List<WordResult> = withContext(Dispatchers.IO) {
        Log.d(TAG, "FTS5 Space-separated search for: '$query'")

        val results = mutableListOf<WordResult>()

        // FIRST: Try searching for the complete phrase in English meanings
        if (isEnglishText(query)) {
            Log.d(TAG, "FTS5 Space-separated: First trying complete phrase search for: '$query'")
            val phraseResults = searchFTS5English(query, limit, offset)

            if (phraseResults.isNotEmpty()) {
                Log.d(TAG, "FTS5 Space-separated: Complete phrase search returned ${phraseResults.size} results. Prioritizing these.")
                // Add phrase results directly - no wordOrder modification needed
                results.addAll(phraseResults)
            } else {
                Log.d(TAG, "FTS5 Space-separated: Phrase search returned no results, falling back to individual words.")
            }

            // ONLY search for individual words if the phrase search found nothing
            if (results.isEmpty()) {
                // Split query by spaces and search each word individually
                val words = query.split(Regex("\\s+")).filter { it.isNotBlank() }
                Log.d(TAG, "FTS5 Space-separated: Searching ${words.size} individual words: ${words.joinToString(", ")}")

                // Calculate remaining limit
                val remainingLimit = limit - results.size
                if (remainingLimit > 0) {
                    for ((index, word) in words.withIndex()) {
                        // Skip if we already have enough results
                        if (results.size >= limit) break

                        Log.d(TAG, "FTS5 Space-separated: Searching word ${index + 1}/${words.size}: '$word'")

                        val wordLimit = remainingLimit / words.size.coerceAtLeast(1)
                        val wordResults = when {
                            // The rest of your existing logic for searching individual words...
                            // (no changes needed here)
                            word.contains("?") && isWildcardPattern(word) && isJapaneseText(word) -> {
                                searchFTS5Wildcard(word, wordLimit)
                            }
                            word.contains("?") && !isJapaneseText(word) -> {
                                emptyList()
                            }
                            isMixedScript(word) -> {
                                searchFTS5MixedScript(word, wordLimit)
                            }
                            containsRomaji(word) && !isJapaneseText(word) && isLikelyJapaneseRomaji(word) -> {
                                searchFTS5Parallel(word, wordLimit)
                            }
                            isJapaneseText(word) -> {
                                searchFTS5Japanese(word, wordLimit)
                            }
                            isEnglishText(word) -> {
                                searchFTS5English(word, wordLimit)
                            }
                            else -> {
                                searchFTS5Unified(word, wordLimit)
                            }
                        }

                        // Filter out duplicates before adding
                        for (newResult in wordResults) {
                            val isDuplicate = results.any { existing ->
                                existing.kanji == newResult.kanji && existing.reading == newResult.reading
                            }
                            if (!isDuplicate && results.size < limit) {
                                results.add(newResult)
                            }
                        }
                        Log.d(TAG, "FTS5 Space-separated: Word '$word' returned ${wordResults.size} results")
                    }
                }
            }
        } else {
            // Handle non-English space-separated queries (your existing logic)
            // This part remains unchanged
            val words = query.split(Regex("\\s+")).filter { it.isNotBlank() }
            for ((index, word) in words.withIndex()) {
                if (results.size >= limit) break
                val wordResults = when {
                    isJapaneseText(word) -> searchFTS5Japanese(word, limit)
                    else -> searchFTS5Unified(word, limit)
                }
                results.addAll(wordResults)
            }
        }

        Log.d(TAG, "FTS5 Space-separated: Total results ${results.size}")

        // Final prioritization happens here on the (now smaller) result set
        val prioritizedResults = prioritizeByMeaningPosition(results, query)

        return@withContext prioritizedResults
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
    private fun convertFTS5ToWordResult(searchResult: SearchResult, wordOrder: Int = 999): WordResult {
        return WordResult(
            kanji = searchResult.kanji,
            reading = searchResult.reading,
            meanings = parseMeaningsJson(searchResult.meanings),
            isCommon = searchResult.isCommon, // Use form-specific common flag
            frequency = searchResult.frequency,
            wordOrder = wordOrder,
            tags = emptyList(), // Tags will be loaded by TagDictSQLiteLoader for both JMdict and JMNEDict entries
            partsOfSpeech = try {
                searchResult.partsOfSpeech?.let {
                    gson.fromJson(it, typeToken.type)
                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            },
            isDeinflectedValidConjugation = false // Not computed in simplified SQL anymore
        )
    }

    /**
     * Get pitch accent data for a word
     */
    suspend fun getPitchAccents(kanjiForm: String, reading: String): List<PitchAccent> = withContext(Dispatchers.IO) {
        return@withContext database.getPitchAccents(kanjiForm, reading)
    }

    /**
     * Get all variants for a given kanji form
     */
    suspend fun getVariants(kanjiForm: String): List<Variant> = withContext(Dispatchers.IO) {
        return@withContext database.getWordVariants(kanjiForm)
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
     * Check if a string contains any katakana
     */
    private fun containsKatakana(text: String): Boolean {
        if (text.isEmpty()) return false
        return text.any { char ->
            val codePoint = char.code
            codePoint in 0x30A0..0x30FF
        }
    }

    /**
     * Convert all katakana characters in text to hiragana
     * Handles mixed scripts like アガる → あがる
     */
    private fun convertKatakanaToHiragana(text: String): String {
        return text.map { char ->
            val codePoint = char.code
            when {
                codePoint in 0x30A1..0x30F6 -> (codePoint - 0x60).toChar()
                codePoint == 0x30FC -> 'ー' // Keep long vowel mark as is
                else -> char
            }
        }.joinToString("")
    }

    /**
     * Simple filter to remove katakana duplicates based on kanji+reading combination
     * This works on raw results before any grouping
     */
    private fun filterKatakanaDuplicatesSimple(results: List<WordResult>): List<WordResult> {
        // Create a map to track hiragana versions
        val hiraganaVersions = mutableMapOf<String, WordResult>()
        val toFilter = mutableListOf<WordResult>()

        // First pass: collect all hiragana readings
        results.forEach { result ->
            val key = result.kanji ?: ""
            if (!containsKatakana(result.reading)) {
                // Pure hiragana reading
                hiraganaVersions["$key|${result.reading}"] = result
            }
            toFilter.add(result)
        }

        // Second pass: filter out katakana versions if hiragana exists
        val filtered = toFilter.filter { result ->
            if (containsKatakana(result.reading)) {
                val key = result.kanji ?: ""
                val hiraganaReading = convertKatakanaToHiragana(result.reading)
                val hiraganaKey = "$key|$hiraganaReading"

                if (hiraganaVersions.containsKey(hiraganaKey)) {
                    Log.d(TAG, "Filtering out katakana duplicate: ${result.kanji}/${result.reading} → $hiraganaReading")
                    false // Filter out this katakana version
                } else {
                    true // Keep it - no hiragana version exists
                }
            } else {
                true // Keep all hiragana entries
            }
        }

        Log.d(TAG, "Filtered ${results.size} results to ${filtered.size} (removed ${results.size - filtered.size} katakana duplicates)")
        return filtered
    }

    /**
     * Check if a word result is a proper noun (person/place name)
     * Proper nouns should be deprioritized in search results
     */
    private fun isProperNoun(result: WordResult): Boolean {

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
     * Convert KanjiDatabaseEntry to WordResult for integration with search results
     */
    private fun convertKanjiEntryToWordResult(kanjiEntry: KanjiDatabaseEntry, query: String): WordResult {
        // Parse meanings from the database format
        val meanings = parseMeaningsFromDatabase(kanjiEntry.meanings)

        // Parse readings from the database format
        val kunReadings = parseReadingsFromDatabase(kanjiEntry.kunReadings)
        val onReadings = parseReadingsFromDatabase(kanjiEntry.onReadings)

        // Create a reading string - prioritize kun readings, then on readings
        val primaryReading = kunReadings.firstOrNull() ?: onReadings.firstOrNull() ?: kanjiEntry.kanji

        return WordResult(
            kanji = kanjiEntry.kanji,
            reading = primaryReading,
            meanings = meanings,
            isCommon = false, // Individual kanji aren't marked as "common" in the same way as words
            frequency = kanjiEntry.frequency ?: 0, // Handle nullable frequency
            wordOrder = 1, // High priority for exact kanji match
            tags = emptyList(),
            partsOfSpeech = listOf("kanji"), // Mark as kanji type
            isDeinflectedValidConjugation = false
        )
    }

    /**
     * Get kanji information from database
     */
    suspend fun getKanjiInfo(kanjiList: List<String>): List<KanjiResult> = withContext(Dispatchers.IO) {
        val databaseEntries = database.getKanjiByCharacters(kanjiList)

        // Create a map for quick lookup
        val entriesMap: Map<String, KanjiDatabaseEntry> = databaseEntries.associateBy { entry -> entry.kanji }

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
}