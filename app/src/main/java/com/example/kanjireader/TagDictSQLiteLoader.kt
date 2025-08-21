package com.example.kanjireader

import android.content.Context
import android.util.Log

/**
 * SQLite-based TagDict loader that populates the database instead of HashMap
 */
class TagDictSQLiteLoader(private val context: Context) {
    companion object {
        private const val TAG = "TagDictSQLiteLoader"
    }

    private val database = DictionaryDatabase.getInstance(context)



    fun enhanceWordResults(wordResults: List<WordResult>): List<EnhancedWordResult> {
        return wordResults.map { enhanceWordResult(it) }
    }


    /**
     * Runtime lookup - get tags from SQLite
     */
    fun lookupTags(word: String): TagEntry? {
        return try {
            // Find ALL entry IDs for this word (handles both JMdict and JMNEDict entries)
            val entryIds = findAllEntryIdsForWord(word)
            if (entryIds.isNotEmpty()) {
                // Get all tags from all matching entries
                val allTags = mutableSetOf<String>()
                for (entryId in entryIds) {
                    val tags = getTagsForEntry(entryId)
                    allTags.addAll(tags)
                }
                
                if (allTags.isNotEmpty()) {
                    // Convert tags list to TagEntry format
                    createTagEntryFromTags(allTags.toList())
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lookup tags for: $word", e)
            null
        }
    }
    
    /**
     * Lookup tags for specific kanji+reading combination (more precise)
     */
    fun lookupTagsForKanjiReading(kanji: String?, reading: String): TagEntry? {
        return try {
            // Find entry IDs for exact kanji+reading match
            val entryIds = findEntryIdsForKanjiReading(kanji, reading)
            if (entryIds.isNotEmpty()) {
                // Get all tags from matching entries
                val allTags = mutableSetOf<String>()
                for (entryId in entryIds) {
                    val tags = getTagsForEntry(entryId)
                    allTags.addAll(tags)
                }
                
                if (allTags.isNotEmpty()) {
                    // Convert tags list to TagEntry format
                    createTagEntryFromTags(allTags.toList())
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lookup tags for kanji='$kanji', reading='$reading'", e)
            null
        }
    }
    
    /**
     * Lookup tags for specific kanji+reading combination with JMnedict flag (most precise)
     */
    private fun lookupTagsForKanjiReadingInternal(kanji: String?, reading: String): TagEntry? {
        return try {
            // Find entry IDs for exact kanji+reading+JMnedict match
            val entryIds = findEntryIdsForKanjiReading(kanji, reading)
            if (entryIds.isNotEmpty()) {
                // Get all tags from matching entries
                val allTags = mutableSetOf<String>()
                for (entryId in entryIds) {
                    val tags = getTagsForEntry(entryId)
                    allTags.addAll(tags)
                }
                
                if (allTags.isNotEmpty()) {
                    // Convert tags list to TagEntry format
                    createTagEntryFromTags(allTags.toList())
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lookup tags for kanji='$kanji', reading='$reading'", e)
            null
        }
    }
    
    /**
     * Get ALL tags (including form-level tags like rK, iK) for kanji+reading combination without filtering
     */
    fun getAllTagsForKanjiReading(kanji: String?, reading: String): List<String> {
        return try {
            // Find entry IDs for exact kanji+reading+JMnedict match
            val entryIds = findEntryIdsForKanjiReading(kanji, reading)
            if (entryIds.isNotEmpty()) {
                // Get all tags from matching entries
                val allTags = mutableSetOf<String>()
                for (entryId in entryIds) {
                    // Get tags from word_tags table
                    val tags = getTagsForEntry(entryId)
                    allTags.addAll(tags)
                    
                    // Also get tags from parts_of_speech column in dictionary_entries
                    val posTagsFromEntry = getPartsOfSpeechFromEntry(entryId)
                    allTags.addAll(posTagsFromEntry)
                }
                allTags.toList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get all tags for kanji='$kanji', reading='$reading'", e)
            emptyList()
        }
    }
    
    /**
     * Get parts of speech tags from the dictionary_entries table
     */
    private fun getPartsOfSpeechFromEntry(entryId: Long): List<String> {
        return try {
            val db = database.readableDatabase
            val cursor = db.query(
                DictionaryDatabase.TABLE_ENTRIES,
                arrayOf(DictionaryDatabase.COL_PARTS_OF_SPEECH),
                "${DictionaryDatabase.COL_ID} = ?",
                arrayOf(entryId.toString()),
                null, null, null
            )
            
            val tags = mutableListOf<String>()
            cursor.use {
                if (it.moveToFirst()) {
                    val partsOfSpeechJson = it.getString(0)
                    if (!partsOfSpeechJson.isNullOrEmpty() && partsOfSpeechJson != "null") {
                        try {
                            // Parse JSON array of parts of speech
                            val jsonArray = org.json.JSONArray(partsOfSpeechJson)
                            for (i in 0 until jsonArray.length()) {
                                tags.add(jsonArray.getString(i))
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse parts_of_speech JSON for entry $entryId: $partsOfSpeechJson", e)
                        }
                    }
                }
            }
            tags
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get parts of speech for entry ID: $entryId", e)
            emptyList()
        }
    }
    
    /**
     * Get all tags for a specific entry ID
     */
    private fun getTagsForEntry(entryId: Long): List<String> {
        return try {
            val db = database.readableDatabase
            val cursor = db.query(
                "word_tags",
                arrayOf("tag"),
                "entry_id = ?",
                arrayOf(entryId.toString()),
                null, null, null
            )
            
            val tags = mutableListOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    tags.add(it.getString(0))
                }
            }
            if (tags.isNotEmpty()) {
                Log.d(TAG, "Found ${tags.size} tags for entry ID $entryId: $tags")
            }
            tags
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get tags for entry ID: $entryId", e)
            emptyList()
        }
    }
    
    /**
     * Create a TagEntry from a list of tag strings
     */
    private fun createTagEntryFromTags(tags: List<String>): TagEntry {
        // Group tags by sense (include both JMdict and JMNEDict tags)
        val jmdictTags = listOf("v", "adj", "n", "adv", "prt", "aux", "int", "exp", "pref", "suf")
        val jmnedictTags = listOf("person", "place", "company", "organization", "given", "fem", "masc", 
                                 "surname", "station", "group", "char", "fict", "work", "ev", "obj", 
                                 "product", "serv", "relig", "dei", "ship", "leg", "myth", "creat", 
                                 "oth", "unclass", "doc")
        
        var filteredTags = tags.filter { tag ->
            jmdictTags.any { tag.startsWith(it) } || jmnedictTags.contains(tag)
        }
        
        
        val sense = Sense(
            pos = filteredTags
        )

        return TagEntry(
            senses = listOf(sense)
        )
    }
    
    /**
     * Find entry ID for a word (reading or kanji)
     */
    private fun findAllEntryIdsForWord(word: String): List<Long> {
        return try {
            val db = database.readableDatabase
            val cursor = db.query(
                DictionaryDatabase.TABLE_ENTRIES,
                arrayOf(DictionaryDatabase.COL_ID),
                "${DictionaryDatabase.COL_READING} = ? OR ${DictionaryDatabase.COL_KANJI} = ?",
                arrayOf(word, word),
                null, null, null
            )
            
            val entryIds = mutableListOf<Long>()
            cursor.use {
                while (it.moveToNext()) {
                    entryIds.add(it.getLong(0))
                }
            }
            entryIds
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find entry IDs for word: $word", e)
            emptyList()
        }
    }
    
    /**
     * Find entry IDs for exact kanji+reading combination
     */
    private fun findEntryIdsForKanjiReading(kanji: String?, reading: String): List<Long> {
        return try {
            val db = database.readableDatabase
            val cursor = if (kanji != null) {
                // Look for exact kanji+reading match
                db.query(
                    DictionaryDatabase.TABLE_ENTRIES,
                    arrayOf(DictionaryDatabase.COL_ID),
                    "${DictionaryDatabase.COL_KANJI} = ? AND ${DictionaryDatabase.COL_READING} = ?",
                    arrayOf(kanji, reading),
                    null, null, null
                )
            } else {
                // No kanji, just look for reading
                db.query(
                    DictionaryDatabase.TABLE_ENTRIES,
                    arrayOf(DictionaryDatabase.COL_ID),
                    "${DictionaryDatabase.COL_READING} = ?",
                    arrayOf(reading),
                    null, null, null
                )
            }
            
            val entryIds = mutableListOf<Long>()
            cursor.use {
                while (it.moveToNext()) {
                    entryIds.add(it.getLong(0))
                }
            }
            entryIds
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find entry IDs for kanji='$kanji', reading='$reading'", e)
            emptyList()
        }
    }
    

    /**
     * Check if tags are loaded in database
     */
    fun isTagDatabaseReady(): Boolean {
        return try {
            val db = database.readableDatabase
            val cursor = db.rawQuery("SELECT COUNT(*) FROM word_tags LIMIT 1", null)
            cursor.use {
                it.moveToFirst()
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tag database not ready", e)
            false
        }
    }

    /**
     * Get verb type for a word (for conjugation)
     */
    fun getVerbType(word: String): VerbType? {
        val tagEntry = lookupTags(word) ?: return null
        val partOfSpeech = extractPartOfSpeech(tagEntry)
        return classifyVerbType(partOfSpeech)
    }

    /**
     * Enhance word results with tag data
     */
    fun enhanceWordResult(wordResult: WordResult): EnhancedWordResult {
        // Use precise kanji+reading lookup to get correct tags
        var tagEntry = lookupTagsForKanjiReading(wordResult.kanji, wordResult.reading)

        // If not found with precise lookup, fall back to less precise methods
        if (tagEntry == null) {
            tagEntry = lookupTagsForKanjiReading(wordResult.kanji, wordResult.reading)
        }
        
        // If still not found, fall back to even less precise methods
        if (tagEntry == null) {
            val primaryKey = wordResult.kanji ?: wordResult.reading
            tagEntry = lookupTags(primaryKey)
        }
        

        // If still not found, try common patterns (e.g., 観る → 見る)

        return if (tagEntry != null) {
            val partOfSpeech = extractPartOfSpeech(tagEntry)
            val verbType = classifyVerbType(partOfSpeech)
            

            EnhancedWordResult(
                kanji = wordResult.kanji,
                reading = wordResult.reading,
                meanings = wordResult.meanings,
                partOfSpeech = partOfSpeech,
                verbType = verbType,
                isCommon = wordResult.isCommon,
                numericFrequency = wordResult.frequency
            )
        } else {
            
            EnhancedWordResult(
                kanji = wordResult.kanji,
                reading = wordResult.reading,
                meanings = wordResult.meanings,
                isCommon = wordResult.isCommon,
                numericFrequency = wordResult.frequency
            )
        }
    }




    // Keep the same helper methods from original TagDictLoader
    private fun extractPartOfSpeech(tagEntry: TagEntry): List<String> {
        val pos = mutableListOf<String>()
        tagEntry.senses?.forEach { sense ->
            sense.pos?.let { pos.addAll(it) }
        }
        return pos.distinct()
    }

    private fun classifyVerbType(partOfSpeech: List<String>): VerbType? {
        for (pos in partOfSpeech) {
            when (pos) {
                "v1" -> return VerbType.ICHIDAN
                "v5k" -> return VerbType.GODAN_K
                "v5s" -> return VerbType.GODAN_S
                "v5t" -> return VerbType.GODAN_T
                "v5n" -> return VerbType.GODAN_N
                "v5b" -> return VerbType.GODAN_B
                "v5m" -> return VerbType.GODAN_M
                "v5r" -> return VerbType.GODAN_R
                "v5g" -> return VerbType.GODAN_G
                "v5u" -> return VerbType.GODAN_U
                "vs-i" -> return VerbType.SURU_IRREGULAR
                "vk" -> return VerbType.KURU_IRREGULAR
                "v5k-s" -> return VerbType.IKU_IRREGULAR
                "adj-i" -> return VerbType.ADJECTIVE_I
                "adj-na" -> return VerbType.ADJECTIVE_NA
            }
        }
        return null
    }
}