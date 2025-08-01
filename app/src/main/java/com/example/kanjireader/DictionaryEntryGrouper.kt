package com.example.kanjireader

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


class DictionaryEntryGrouper(
    private val tagDictLoader: TagDictSQLiteLoader?,
    private val database: DictionaryDatabase?
) {
    companion object {
        private const val TAG = "DictionaryGrouper"
    }

    private val gson = Gson()
    
    // Cache for romaji conversions to improve performance
    private val romajiConversionCache = mutableMapOf<String, String>()

    /**
     * Group search results by their dictionary entry (variants belong together)
     */
    fun groupSearchResults(
        searchResults: List<WordResult>,
        originalQuery: String? = null,
        deinflectionInfo: DeinflectionResult? = null
    ): List<UnifiedDictionaryEntry> {
        Log.d(TAG, "Grouping ${searchResults.size} results for query: $originalQuery")

        // Filter results to only include words that actually relate to the query
        val filteredResults = if (originalQuery != null) {
            filterRelevantResults(searchResults, originalQuery, deinflectionInfo)
        } else {
            searchResults
        }
        
        Log.d(TAG, "Filtered to ${filteredResults.size} relevant results (from ${searchResults.size})")

        // Map to store entry ID -> list of results that belong together
        val entryGroups = mutableMapOf<String, MutableList<WordResult>>()

        // Group by shared meanings (simple heuristic for now)
        filteredResults.forEach { result ->
            val key = findGroupKey(result, filteredResults)
            entryGroups.getOrPut(key) { mutableListOf() }.add(result)
        }

        // Convert groups to unified entries with conjugation info, preserving word order
        val unifiedEntries = entryGroups.values.map { group ->
            val entry = createUnifiedEntry(group, originalQuery, deinflectionInfo)
            // Add word order info to the entry for sorting
            val minWordOrder = group.minOfOrNull { it.wordOrder } ?: 999
            entry to minWordOrder
        }
        
        // Sort by query relevance first, then word order, then frequency
        return unifiedEntries.sortedWith(
            compareBy<Pair<UnifiedDictionaryEntry, Int>> { 
                // First priority: exact query matches (0 = highest priority)
                if (originalQuery != null && isExactMatch(it.first, originalQuery)) 0 else 1
            }
            .thenBy { it.second }  // word order (for mixed script)
            .thenBy { 
                // Then by query relevance score (0 = most relevant)
                if (originalQuery != null) calculateRelevanceScore(it.first, originalQuery) else 999
            }
            .thenByDescending { it.first.frequency ?: 0 }
            .thenByDescending { it.first.isCommon }
            .thenBy { it.first.primaryForm.length }
        ).map { it.first }  // Extract just the entries
    }

    /**
     * Find which group this result belongs to
     * This is a simplified approach - in reality, you might want to use entry IDs
     */
    private fun findGroupKey(result: WordResult, allResults: List<WordResult>): String {
        // For comprehensive searches (like "miru"), show individual entries separately
        // Only group when we have very specific matches or conjugated forms
        
        val word = result.kanji ?: result.reading
        
        // Strategy: Use the word itself as the key for basic searches to avoid over-grouping
        // This ensures 見る, 診る, 看る show as separate entries rather than being grouped
        return word + "|" + result.meanings.take(1).joinToString("|")
    }

    /**
     * Create a unified entry from a group of related results
     */
    private fun createUnifiedEntry(
        group: List<WordResult>,
        originalQuery: String? = null,
        deinflectionInfo: DeinflectionResult? = null
    ): UnifiedDictionaryEntry {
        // Sort to put most common/primary form first
        val sortedGroup = group.sortedWith(
            compareByDescending<WordResult> { it.isCommon }
                .thenBy { result ->
                    // Prefer entries with more general meanings
                    when (result.kanji ?: result.reading) {
                        "見る" -> 0  // Primary form
                        "聞く" -> 0  // Primary form
                        else -> 1
                    }
                }
        )

        val primaryResult = sortedGroup.first()
        val primaryWord = primaryResult.kanji ?: primaryResult.reading

        // Get tags from partsOfSpeech field (available for both JMdict and JMNEDict entries)
        val primaryTags = primaryResult.partsOfSpeech

        // Process variants
        val variants = sortedGroup.drop(1).map { variantResult ->
            val variantWord = variantResult.kanji ?: variantResult.reading

            VariantInfo(
                text = variantWord,
                allTags = variantResult.partsOfSpeech, // Use partsOfSpeech for variants too
                isCommon = variantResult.isCommon
            )
        }

        // Add pure kana reading as a variant if needed
        if (primaryResult.kanji != null && !variants.any { it.text == primaryResult.reading }) {
            val kanaVariant = VariantInfo(
                text = primaryResult.reading,
                allTags = emptyList(),
                isCommon = false
            )
        }

        // Check if the word is conjugatable (STRICT CHECK - only actual verbs/adjectives)
        // Use parts of speech from WordResult instead of tags since getAllTagsForWord returns empty
        val isConjugatable = primaryResult.partsOfSpeech.any { pos ->
            pos.contains("verb", ignoreCase = true) ||
            pos.contains("adjective", ignoreCase = true) ||
            pos.startsWith("v", ignoreCase = true) ||      // v1, v5k, vs, etc.
            pos.startsWith("adj-", ignoreCase = true) ||   // adj-i, adj-na, etc.
            pos == "aux-v"                                 // auxiliary verbs
        }


        // FIXED: Only show conjugation info if:
        // 1. We have deinflection info
        // 2. The original query is different from base form
        // 3. The word is actually conjugatable (verb/adjective)
        // 4. This specific word group matches the base form from deinflection
        Log.d(TAG, "Conjugation check for '$originalQuery': deinflectionInfo=${deinflectionInfo != null}, baseForm=${deinflectionInfo?.baseForm}, isConjugatable=$isConjugatable, primaryWord='$primaryWord', partsOfSpeech=${primaryResult.partsOfSpeech}, isJMNE=${primaryResult.isJMNEDictEntry}")
        
        val conjugationInfo = if (deinflectionInfo != null &&
            originalQuery != null &&
            deinflectionInfo.baseForm != originalQuery &&
            isConjugatable &&
            !primaryResult.isJMNEDictEntry && // Don't show conjugation info for proper nouns
            (primaryWord == deinflectionInfo.baseForm || 
             primaryResult.reading == deinflectionInfo.baseForm ||
             // Also check if any result in this group matches the base form
             sortedGroup.any { it.kanji == deinflectionInfo.baseForm || it.reading == deinflectionInfo.baseForm })) {
            Log.d(TAG, "Setting conjugation info for '$originalQuery' (matches base form '${deinflectionInfo.baseForm}')")
            // Use the originalForm from deinflection info if available (shows hiragana)
            // Otherwise fall back to originalQuery
            deinflectionInfo.originalForm ?: originalQuery
        } else {
            Log.d(TAG, "No conjugation info for '$originalQuery': deinflectionInfo=${deinflectionInfo != null}, baseFormDifferent=${deinflectionInfo?.baseForm != originalQuery}, isConjugatable=$isConjugatable, matchesBaseForm=${deinflectionInfo?.baseForm != null && (primaryWord == deinflectionInfo.baseForm || primaryResult.reading == deinflectionInfo.baseForm)}")
            null
        }

        // Get verb type from tags (only for conjugatable words)
        val verbType = if (isConjugatable) {
            when {
                primaryTags.contains("v1") -> "Ichidan verb"
                primaryTags.contains("v5k") -> "Godan verb -ku"
                primaryTags.contains("v5s") -> "Godan verb -su"
                primaryTags.contains("v5t") -> "Godan verb -tsu"
                primaryTags.contains("v5n") -> "Godan verb -nu"
                primaryTags.contains("v5b") -> "Godan verb -bu"
                primaryTags.contains("v5m") -> "Godan verb -mu"
                primaryTags.contains("v5r") -> "Godan verb -ru"
                primaryTags.contains("v5g") -> "Godan verb -gu"
                primaryTags.contains("v5u") -> "Godan verb -u"
                primaryTags.contains("vk") -> "Kuru verb - special class"
                primaryTags.contains("vs") -> "Suru verb"
                primaryTags.contains("vs-i") -> "Suru verb - irregular"
                primaryTags.contains("v5k-s") -> "Godan verb - Iku/Yuku special class"
                primaryTags.contains("adj-i") -> "I-adjective"
                primaryTags.contains("adj-na") -> "Na-adjective"
                else -> null
            }
        } else {
            // For non-conjugatable words, show their type
            when {
                primaryTags.contains("n") -> "Noun"
                primaryTags.contains("adv") -> "Adverb"
                primaryTags.contains("prt") -> "Particle"
                primaryTags.contains("pref") -> "Prefix"
                primaryTags.contains("suf") -> "Suffix"
                primaryTags.contains("exp") -> "Expression"
                primaryTags.contains("int") -> "Interjection"
                else -> null
            }
        }

        // Use frequency from search results (FTS5 has accurate frequency data)
        val frequency = if (primaryResult.frequency > 0) primaryResult.frequency else null

        return UnifiedDictionaryEntry(
            primaryForm = primaryWord,
            primaryReading = if (primaryResult.kanji != null) primaryResult.reading else null,
            primaryTags = primaryTags, // Use partsOfSpeech for both JMdict and JMNEDict entries
            variants = if (primaryResult.kanji != null && !variants.any { it.text == primaryResult.reading }) {
                variants + VariantInfo(
                    text = primaryResult.reading,
                    allTags = emptyList(),
                    isCommon = false
                )
            } else {
                variants
            },
            meanings = primaryResult.meanings,
            isCommon = primaryResult.isCommon,
            originalSearchTerm = originalQuery,
            conjugationInfo = conjugationInfo,
            verbType = verbType,
            frequency = frequency,
            isJMNEDictEntry = primaryResult.isJMNEDictEntry
        )
    }

    /**
     * Get all grammatical tags for a word
     * Optimized: Skip tag lookup for search cards, only get basic info
     */
    private fun getAllTagsForWord(word: String): List<String> {
        // For search cards, we don't need detailed tags
        // The isCommon flag already handles common status
        // Full tags will be loaded in detail view
        return emptyList()
    }

    /**
     * Extract frequency ranking from word's priority data
     * Simplified for SQLite FTS5 mode - frequency is now handled by the database
     */
    private fun extractFrequencyRanking(word: String): Int? {
        // In SQLite FTS5 mode, frequency data is already included in search results
        // This function is kept for compatibility but returns null to use database frequency
        return null
    }

    /**
     * Find tags that are unique to the variant
     */
    private fun findUniqueTags(variantTags: List<String>, primaryTags: List<String>): List<String> {
        // Special case: if variant has different meanings (like 診る), show key differentiator
        val uniqueTags = variantTags.filter { it !in primaryTags }.toMutableList()

        // Add semantic hints for known special variants
        // This would ideally come from the sense data
        return uniqueTags
    }

    /**
     * Find pure kana reading in the group
     */
    private fun findKanaOnlyVariant(group: List<WordResult>, primaryResult: WordResult): String? {
        // If primary has kanji, its reading is the kana variant
        if (primaryResult.kanji != null) {
            return primaryResult.reading
        }

        // Otherwise look for a kana-only entry in the group
        return group.firstOrNull { it.kanji == null }?.reading
    }

    /**
     * Enhanced grouping using JMdict structure (for better accuracy)
     * DISABLED: This function was designed for the old HashMap-based system
     * and tries to access private types from DatabaseBuilder.kt
     */
    /*
    fun groupByJMdictStructure(
        searchResults: List<WordResult>,
        jmdictEntries: List<CleanedEntry>
    ): List<UnifiedDictionaryEntry> {
        // This function is commented out as it was designed for the old HashMap system
        // and references private types that are not accessible from this file
        return emptyList()
    }
    */
    
    /**
     * Filter results to only include words that actually relate to the query
     */
    private fun filterRelevantResults(
        results: List<WordResult>, 
        query: String, 
        deinflectionInfo: DeinflectionResult?
    ): List<WordResult> {
        Log.d(TAG, "Filtering results for query: '$query'")
        
        // Convert romaji query to hiragana for comparison (with caching)
        val hiraganaQuery = if (isRomajiText(query)) {
            romajiConversionCache.getOrPut(query) {
                val romajiConverter = RomajiConverter()
                romajiConverter.toHiragana(query)
            }
        } else {
            query
        }
        
        Log.d(TAG, "Query '$query' converted to hiragana: '$hiraganaQuery'")
        
        return results.filter { result ->
            val word = result.kanji ?: result.reading
            val reading = result.reading
            
            // Allow result if:
            // 1. Word/reading contains the original query
            val containsOriginalQuery = word.contains(query) || reading.contains(query)
            
            // 1b. For English searches, check if meanings contain the query
            val containsInMeanings = if (isRomajiText(query)) {
                result.meanings.any { meaning -> 
                    meaning.lowercase().contains(query.lowercase())
                }
            } else false
            
            // 2. Word/reading contains the hiragana converted query (for romaji input)
            val containsHiraganaQuery = if (hiraganaQuery != query) {
                word.contains(hiraganaQuery) || reading.contains(hiraganaQuery)
            } else false
            
            // 3. Query (original or hiragana) contains part of the word (for partial typing)
            val queryContainsWord = query.contains(word) || query.contains(reading) ||
                                   hiraganaQuery.contains(word) || hiraganaQuery.contains(reading)
            
            // 4. It matches the deinflection base form (for conjugated searches)
            val matchesDeinflection = deinflectionInfo != null && 
                (word == deinflectionInfo.baseForm || reading == deinflectionInfo.baseForm)
            
            // 5. For very short queries (1-2 chars), be more lenient with prefix matching
            val isShortQueryPrefix = query.length <= 2 && (word.startsWith(query) || reading.startsWith(query) ||
                                    word.startsWith(hiraganaQuery) || reading.startsWith(hiraganaQuery))
            
            // 6. Exact matches (most important)
            val isExactMatch = word == query || reading == query || 
                              word == hiraganaQuery || reading == hiraganaQuery
            
            val isRelevant = isExactMatch || containsOriginalQuery || containsInMeanings || containsHiraganaQuery || 
                           queryContainsWord || matchesDeinflection || isShortQueryPrefix
            
            if (!isRelevant) {
                Log.d(TAG, "Filtering out irrelevant result: '$word' / '$reading' for query '$query' (hiragana: '$hiraganaQuery')")
            }
            
            isRelevant
        }
    }
    
    /**
     * Check if text is romaji (contains Latin characters)
     */
    private fun isRomajiText(text: String): Boolean {
        return text.any { it in 'a'..'z' || it in 'A'..'Z' }
    }
    
    /**
     * Check if entry is an exact match for the query
     */
    private fun isExactMatch(entry: UnifiedDictionaryEntry, query: String): Boolean {
        return entry.primaryForm == query || 
               entry.primaryReading == query ||
               entry.variants.any { it.text == query }
    }
    
    /**
     * Calculate relevance score (lower = more relevant)
     */
    private fun calculateRelevanceScore(entry: UnifiedDictionaryEntry, query: String): Int {
        val primaryForm = entry.primaryForm
        val primaryReading = entry.primaryReading
        
        return when {
            // Exact matches get highest priority (already handled above)
            primaryForm == query || primaryReading == query -> 0
            
            // Starts with query
            primaryForm.startsWith(query) || primaryReading?.startsWith(query) == true -> 1
            
            // Contains query
            primaryForm.contains(query) || primaryReading?.contains(query) == true -> 2
            
            // Query starts with this word (for partial typing)
            query.startsWith(primaryForm) || (primaryReading != null && query.startsWith(primaryReading)) -> 3
            
            // Variants match
            entry.variants.any { it.text == query || it.text.startsWith(query) || it.text.contains(query) } -> 4
            
            // Default (least relevant)
            else -> 5
        }
    }
}