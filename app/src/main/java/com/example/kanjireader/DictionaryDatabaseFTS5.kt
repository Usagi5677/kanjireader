package com.example.kanjireader

import android.content.Context
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * SQLite database helper with FTS5 support using requery/sqlite-android
 * Uses pre-built database directly from assets (no copying needed)
 */
class DictionaryDatabaseFTS5 private constructor(context: Context) {
    
    private val dbPath = "databases/jmdict_fts5.db"
    private var database: SQLiteDatabase? = null
    private val context = context

    companion object {
        private const val TAG = "DictionaryDatabaseFTS5"

        @Volatile
        private var INSTANCE: DictionaryDatabaseFTS5? = null

        fun getInstance(context: Context): DictionaryDatabaseFTS5 {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DictionaryDatabaseFTS5(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        // Table names
        const val TABLE_ENTRIES = "dictionary_entries"
        const val TABLE_FTS5 = "entries_fts5"

        // Column names for main table
        const val COL_ID = "id"
        const val COL_KANJI = "kanji"
        const val COL_READING = "reading"
        const val COL_MEANINGS = "meanings"  // JSON array
        const val COL_PARTS_OF_SPEECH = "parts_of_speech"  // JSON array
        const val COL_FREQUENCY = "frequency"  // For ranking results
        const val COL_IS_COMMON = "is_common"  // Essential for good ranking
        const val COL_TOKENIZED_KANJI = "tokenized_kanji"  // MeCab tokenized kanji
        const val COL_TOKENIZED_READING = "tokenized_reading"  // MeCab tokenized reading

        // Schema for main entries table
        private const val CREATE_ENTRIES_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_ENTRIES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_KANJI TEXT,
                $COL_READING TEXT NOT NULL,
                $COL_MEANINGS TEXT NOT NULL,
                $COL_PARTS_OF_SPEECH TEXT,
                $COL_FREQUENCY INTEGER DEFAULT 0,
                $COL_IS_COMMON INTEGER DEFAULT 0,
                UNIQUE($COL_KANJI, $COL_READING)
            )
        """

        // Essential indexes for fast search
        private const val CREATE_KANJI_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_kanji ON $TABLE_ENTRIES($COL_KANJI)"
        private const val CREATE_READING_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_reading ON $TABLE_ENTRIES($COL_READING)"
            
        // Japanese-optimized indexes for instant prefix search
        private const val CREATE_READING_PREFIX_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_reading_prefix ON $TABLE_ENTRIES(substr($COL_READING, 1, 1), $COL_READING)"
        private const val CREATE_KANJI_PREFIX_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_kanji_prefix ON $TABLE_ENTRIES(substr($COL_KANJI, 1, 1), $COL_KANJI)"
        
        // Ranking optimization
        private const val CREATE_FREQUENCY_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_frequency_ranking ON $TABLE_ENTRIES($COL_IS_COMMON DESC, $COL_FREQUENCY DESC)"

        // FTS5 table with advanced configuration
        private const val CREATE_FTS5_TABLE = """
            CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_FTS5 USING fts5(
                kanji,
                reading,
                meanings,
                parts_of_speech,
                tokenize='unicode61 remove_diacritics 2',
                prefix='1 2 3'
            )
        """

        // Trigger to keep FTS5 in sync
        private const val CREATE_FTS5_INSERT_TRIGGER = """
            CREATE TRIGGER IF NOT EXISTS entries_ai AFTER INSERT ON $TABLE_ENTRIES
            BEGIN
                INSERT INTO $TABLE_FTS5(rowid, kanji, reading, meanings, parts_of_speech)
                VALUES (new.$COL_ID, 
                       COALESCE(new.$COL_KANJI, ''), 
                       new.$COL_READING, 
                       new.$COL_MEANINGS,
                       COALESCE(new.$COL_PARTS_OF_SPEECH, ''));
            END
        """

        private const val CREATE_FTS5_UPDATE_TRIGGER = """
            CREATE TRIGGER IF NOT EXISTS entries_au AFTER UPDATE ON $TABLE_ENTRIES
            BEGIN
                UPDATE $TABLE_FTS5 SET 
                    kanji = COALESCE(new.$COL_KANJI, ''),
                    reading = new.$COL_READING,
                    meanings = new.$COL_MEANINGS,
                    parts_of_speech = COALESCE(new.$COL_PARTS_OF_SPEECH, '')
                WHERE rowid = new.$COL_ID;
            END
        """

        private const val CREATE_FTS5_DELETE_TRIGGER = """
            CREATE TRIGGER IF NOT EXISTS entries_ad AFTER DELETE ON $TABLE_ENTRIES
            BEGIN
                DELETE FROM $TABLE_FTS5 WHERE rowid = old.$COL_ID;
            END
        """

        // Tags tables
        const val TABLE_TAG_DEFINITIONS = "tag_definitions"
        const val TABLE_WORD_TAGS = "word_tags"

        // Schema for tag definitions
        private const val CREATE_TAG_DEFINITIONS_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_TAG_DEFINITIONS (
                tag_code TEXT PRIMARY KEY,
                tag_name TEXT NOT NULL,
                tag_category TEXT
            )
        """

        // Schema for word-tag mappings
        private const val CREATE_WORD_TAGS_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_WORD_TAGS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word TEXT NOT NULL,
                tag_data TEXT NOT NULL,
                UNIQUE(word)
            )
        """

        // Indexes for word tags
        private const val CREATE_WORD_TAGS_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_word_tags_word ON $TABLE_WORD_TAGS(word)"
    }

    /**
     * Get database instance (opens from assets directly)
     */
    private fun getDatabase(): SQLiteDatabase {
        if (database?.isOpen != true) {
            try {
                Log.d(TAG, "Opening FTS5 database from assets: $dbPath")
                
                // Open database directly from assets using requery
                val inputStream = context.assets.open(dbPath)
                val tempFile = File.createTempFile("jmdict_fts5", ".db", context.cacheDir)
                
                // Copy to temp file (required for SQLite to work)
                tempFile.outputStream().use { output ->
                    inputStream.use { input ->
                        input.copyTo(output)
                    }
                }
                
                database = io.requery.android.database.sqlite.SQLiteDatabase.openDatabase(
                    tempFile.absolutePath,
                    null,
                    io.requery.android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
                
                Log.d(TAG, "FTS5 database opened successfully")
                verifyDatabase()
                
            } catch (e: java.io.FileNotFoundException) {
                Log.e(TAG, "FTS5 database file not found in assets: $dbPath")
                Log.e(TAG, "Please run 'python3 build_fts5_database.py' to create jmdict_fts5.db in assets/databases/")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open FTS5 database from assets: $dbPath", e)
                Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Make sure jmdict_fts5.db exists and is valid in assets/databases/")
                throw e
            }
        }
        return database!!
    }
    
    /**
     * Verify the copied FTS5 database has the expected structure and data
     */
    private fun verifyDatabase() {
        try {
            Log.d(TAG, "Verifying FTS5 database structure and data...")
            val db = database ?: return  // Don't verify if database is null
            
            // Check main table
            val entriesCursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_ENTRIES", null)
            entriesCursor.use {
                if (it.moveToFirst()) {
                    val count = it.getInt(0)
                    Log.d(TAG, "Dictionary entries: $count")
                    if (count == 0) {
                        Log.w(TAG, "Warning: Dictionary entries table is empty!")
                    }
                }
            }
            
            // Check FTS5 table
            val fts5Cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_FTS5", null)
            fts5Cursor.use {
                if (it.moveToFirst()) {
                    val count = it.getInt(0)
                    Log.d(TAG, "FTS5 entries: $count")
                    if (count == 0) {
                        Log.w(TAG, "Warning: FTS5 table is empty!")
                    }
                }
            }
            
            // Test a simple FTS5 query
            val testCursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_FTS5 WHERE $TABLE_FTS5 MATCH 'cat'", null)
            testCursor.use {
                if (it.moveToFirst()) {
                    val count = it.getInt(0)
                    Log.d(TAG, "FTS5 test query (cat): $count results")
                }
            }
            
            Log.d(TAG, "FTS5 database verification completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify FTS5 database", e)
        }
    }

    /**
     * Close database connection
     */
    fun close() {
        database?.close()
        database = null
    }

    /**
     * Search using FTS5 with advanced features
     */
    fun searchFTS5(query: String, searchType: SearchType = SearchType.ALL, limit: Int = 100, offset: Int = 0): List<FTS5SearchResult> {
        val results = mutableListOf<FTS5SearchResult>()
        if (query.isBlank()) return results

        val db = getDatabase()
        
        // For Japanese search, use direct LIKE search (more reliable for Japanese)
        if (searchType == SearchType.JAPANESE) {
            return searchJapaneseSubstring(query, limit, offset)
        }
        
        // Build the FTS5 query for non-Japanese searches
        val fts5Query = when (searchType) {
            SearchType.ENGLISH -> buildEnglishFTS5Query(query)
            SearchType.ALL -> buildUnifiedFTS5Query(query)
            else -> buildJapaneseFTS5Query(query) // fallback
        }

        // Use FTS5 rank function for better ordering
        val sql = """
            SELECT 
                e.$COL_ID,
                e.$COL_KANJI,
                e.$COL_READING,
                e.$COL_MEANINGS,
                e.$COL_PARTS_OF_SPEECH,
                e.$COL_IS_COMMON,
                e.$COL_FREQUENCY,
                rank
            FROM $TABLE_FTS5 f
            JOIN $TABLE_ENTRIES e ON f.rowid = e.$COL_ID
            WHERE $TABLE_FTS5 MATCH ?
            ORDER BY 
                e.$COL_IS_COMMON DESC,
                rank,
                e.$COL_FREQUENCY DESC
            LIMIT ? OFFSET ?
        """

        Log.d(TAG, "FTS5 query: '$fts5Query' for original: '$query' (limit=$limit, offset=$offset)")

        db.rawQuery(sql, arrayOf(fts5Query, limit.toString(), offset.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(FTS5SearchResult(
                    id = cursor.getLong(0),
                    kanji = cursor.getString(1),
                    reading = cursor.getString(2),
                    meanings = cursor.getString(3),
                    partsOfSpeech = cursor.getString(4),
                    isCommon = cursor.getInt(5) == 1,
                    frequency = cursor.getInt(6),
                    rank = cursor.getDouble(7)
                ))
            }
        }
        
        Log.d(TAG, "FTS5 search returned ${results.size} results")
        return results
    }
    
    /**
     * Direct SQL LIKE search for Japanese substring matching
     */
    private fun searchJapaneseSubstring(query: String, limit: Int, offset: Int): List<FTS5SearchResult> {
        val results = mutableListOf<FTS5SearchResult>()
        val db = getDatabase()
        
        // Optimized query for prefix searches (most common case)
        val sql = if (query.length <= 2) {
            // For short queries, use prefix index
            """
            SELECT 
                $COL_ID,
                $COL_KANJI,
                $COL_READING,
                $COL_MEANINGS,
                $COL_PARTS_OF_SPEECH,
                $COL_IS_COMMON,
                $COL_FREQUENCY
            FROM $TABLE_ENTRIES
            WHERE $COL_READING LIKE ? OR $COL_KANJI LIKE ?
            ORDER BY 
                $COL_IS_COMMON DESC,
                $COL_FREQUENCY DESC,
                LENGTH($COL_READING) ASC
            LIMIT ? OFFSET ?
            """
        } else {
            // For longer queries, use standard LIKE
            """
            SELECT 
                $COL_ID,
                $COL_KANJI,
                $COL_READING,
                $COL_MEANINGS,
                $COL_PARTS_OF_SPEECH,
                $COL_IS_COMMON,
                $COL_FREQUENCY
            FROM $TABLE_ENTRIES
            WHERE ($COL_KANJI LIKE ? OR $COL_READING LIKE ?)
            ORDER BY 
                $COL_IS_COMMON DESC,
                $COL_FREQUENCY DESC,
                LENGTH($COL_READING) ASC
            LIMIT ? OFFSET ?
            """
        }
        
        val likeQuery = "%$query%"
        Log.d(TAG, "Japanese substring search: '$likeQuery' (limit=$limit, offset=$offset)")
        val startTime = System.currentTimeMillis()
        
        try {
            // Direct search without debugging overhead
            db.rawQuery(sql, arrayOf(likeQuery, likeQuery, limit.toString(), offset.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(FTS5SearchResult(
                        id = cursor.getLong(0),
                        kanji = cursor.getString(1),
                        reading = cursor.getString(2),
                        meanings = cursor.getString(3),
                        partsOfSpeech = cursor.getString(4),
                        isCommon = cursor.getInt(5) == 1,
                        frequency = cursor.getInt(6),
                        rank = 0.0 // No FTS5 rank for LIKE queries
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in Japanese substring search", e)
        }
        
        val queryTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "⏱️ SQL query took ${queryTime}ms, returned ${results.size} results")
        return results
    }
    
    /**
     * Hybrid Japanese search: Use optimized LIKE search for Japanese
     * FTS5 isn't ideal for Japanese without proper tokenization
     */
    private fun searchJapaneseHybrid(query: String, limit: Int, offset: Int): List<FTS5SearchResult> {
        val db = getDatabase()
        val startTime = System.currentTimeMillis()
        
        // First attempt: FTS5 search with tokenized columns
        Log.d(TAG, "Attempting FTS5 search with tokenized columns for: '$query'")
        val queryBuildStart = System.currentTimeMillis()
        val fts5Query = buildJapaneseFTS5Query(query)
        val queryBuildTime = System.currentTimeMillis() - queryBuildStart
        Log.d(TAG, "FTS5 query built in ${queryBuildTime}ms: $fts5Query")
        
        val fts5Results = mutableListOf<FTS5SearchResult>()
        
        try {
            // Optimized query: Get rowids from FTS5 first, then fetch details
            val fts5Sql = """
                SELECT rowid, rank
                FROM $TABLE_FTS5
                WHERE $TABLE_FTS5 MATCH ?
                ORDER BY rank
                LIMIT ? OFFSET ?
            """
            
            // Step 1: Get matching rowids from FTS5 (fast)
            val queryStart = System.currentTimeMillis()
            val rowIds = mutableListOf<Pair<Long, Double>>()
            
            db.rawQuery(fts5Sql, arrayOf(fts5Query, limit.toString(), offset.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    rowIds.add(cursor.getLong(0) to cursor.getDouble(1))
                }
            }
            
            val fts5QueryTime = System.currentTimeMillis() - queryStart
            Log.d(TAG, "FTS5 rowid query took ${fts5QueryTime}ms, found ${rowIds.size} matches")
            
            // Step 2: Batch fetch full records (if we have matches)
            if (rowIds.isNotEmpty()) {
                val fetchStart = System.currentTimeMillis()
                val idList = rowIds.map { it.first }.joinToString(",")
                val detailSql = """
                    SELECT $COL_ID, $COL_KANJI, $COL_READING, $COL_MEANINGS, 
                           $COL_PARTS_OF_SPEECH, $COL_IS_COMMON, $COL_FREQUENCY
                    FROM $TABLE_ENTRIES
                    WHERE $COL_ID IN ($idList)
                """
                
                val resultMap = mutableMapOf<Long, FTS5SearchResult>()
                db.rawQuery(detailSql, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        resultMap[id] = FTS5SearchResult(
                            id = id,
                            kanji = cursor.getString(1),
                            reading = cursor.getString(2),
                            meanings = cursor.getString(3),
                            partsOfSpeech = cursor.getString(4),
                            isCommon = cursor.getInt(5) == 1,
                            frequency = cursor.getInt(6),
                            rank = rowIds.find { it.first == id }?.second ?: 0.0
                        )
                    }
                }
                
                // Maintain FTS5 ranking order
                rowIds.forEach { (id, _) ->
                    resultMap[id]?.let { fts5Results.add(it) }
                }
                
                val fetchTime = System.currentTimeMillis() - fetchStart
                Log.d(TAG, "Detail fetch took ${fetchTime}ms for ${fts5Results.size} results")
            }
            
            val fts5Time = System.currentTimeMillis() - startTime
            Log.d(TAG, "⚡ FTS5 tokenized search took ${fts5Time}ms, found ${fts5Results.size} results")
            
            // If FTS5 found good results, return them
            if (fts5Results.isNotEmpty()) {
                Log.d(TAG, "✅ FTS5 tokenized search successful, returning ${fts5Results.size} results")
                return fts5Results
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "FTS5 tokenized search failed: ${e.message}")
        }
        
        // Fallback: Use LIKE search for comprehensive substring matching
        Log.d(TAG, "⚠️ FTS5 found no results, falling back to LIKE search")
        val likeResults = searchJapaneseSubstring(query, limit, offset)
        
        val totalTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "🔄 Hybrid search completed in ${totalTime}ms: FTS5 (${fts5Results.size}) + LIKE (${likeResults.size}) = ${likeResults.size} total")
        
        return likeResults
    }

    /**
     * Build FTS5 query for Japanese text using tokenized columns
     */
    private fun buildJapaneseFTS5Query(query: String): String {
        // Optimized FTS5 query for Japanese:
        // For short queries (1-2 chars), focus on exact and prefix matches
        // For longer queries, also search tokenized columns
        return when (query.length) {
            1, 2 -> {
                // Short queries: exact and prefix matches only (faster)
                """(reading:"$query" OR reading:"$query"* OR kanji:"$query" OR kanji:"$query"*)"""
            }
            else -> {
                // Longer queries: include tokenized columns for better morphological matching
                """(reading:"$query" OR reading:"$query"* OR tokenized_reading:"$query" OR tokenized_reading:"$query"* OR kanji:"$query" OR kanji:"$query"*)"""
            }
        }
    }

    /**
     * Build FTS5 query for English text
     */
    private fun buildEnglishFTS5Query(query: String): String {
        val words = query.split(" ").filter { it.isNotBlank() }
        
        return when {
            words.size == 1 -> {
                // Single word - search in meanings with prefix
                """meanings:"${words[0]}"*"""
            }
            else -> {
                // Multiple words - try phrase first, then individual words
                val phrase = """meanings:"$query""""
                val individual = words.joinToString(" OR ") { """meanings:"$it"*""" }
                """($phrase OR ($individual))"""
            }
        }
    }

    /**
     * Build unified FTS5 query for any text
     */
    private fun buildUnifiedFTS5Query(query: String): String {
        // Search across all indexed columns
        return """"$query"*"""
    }

    /**
     * Get entry by ID
     */
    fun getEntry(id: Long): FTS5SearchResult? {
        val db = getDatabase()

        val cursor = db.query(
            TABLE_ENTRIES,
            null,
            "$COL_ID = ?",
            arrayOf(id.toString()),
            null, null, null
        )

        return cursor.use {
            if (it.moveToFirst()) {
                FTS5SearchResult(
                    id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                    kanji = it.getString(it.getColumnIndexOrThrow(COL_KANJI)),
                    reading = it.getString(it.getColumnIndexOrThrow(COL_READING)),
                    meanings = it.getString(it.getColumnIndexOrThrow(COL_MEANINGS)),
                    partsOfSpeech = it.getString(it.getColumnIndexOrThrow(COL_PARTS_OF_SPEECH)),
                    frequency = it.getInt(it.getColumnIndexOrThrow(COL_FREQUENCY)),
                    isCommon = it.getInt(it.getColumnIndexOrThrow(COL_IS_COMMON)) == 1,
                    rank = 0.0
                )
            } else null
        }
    }

    /**
     * Check if database is properly initialized
     */
    fun isDatabaseReady(): Boolean {
        return try {
            val db = getDatabase()  // This will initialize the database if needed
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_ENTRIES LIMIT 1", null)
            cursor.use {
                it.moveToFirst()
                val count = it.getInt(0)
                Log.d(TAG, "Database check: found $count entries")
                count > 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Database not ready", e)
            false
        }
    }

    /**
     * Optimize FTS5 index
     */
    fun optimizeFTS5() {
        try {
            // Skip optimization - database is read-only
            // getDatabase().execSQL("INSERT INTO $TABLE_FTS5($TABLE_FTS5) VALUES('optimize')")
            Log.d(TAG, "FTS5 index optimization skipped (read-only database)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to optimize FTS5 index", e)
        }
    }

    /**
     * Get FTS5 statistics
     */
    fun getFTS5Stats(): String {
        return try {
            val db = database!!
            val stats = StringBuilder()
            
            // Get table size
            db.rawQuery("SELECT COUNT(*) FROM $TABLE_ENTRIES", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    stats.append("Total entries: ${cursor.getInt(0)}\n")
                }
            }
            
            // Try to get FTS5 size, but handle potential issues
            try {
                db.rawQuery("SELECT COUNT(*) FROM $TABLE_FTS5", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        stats.append("FTS5 entries: ${cursor.getInt(0)}\n")
                    }
                }
            } catch (e: Exception) {
                stats.append("FTS5 entries: Error - ${e.message}\n")
            }
            
            stats.toString()
        } catch (e: Exception) {
            "Failed to get stats: ${e.message}"
        }
    }

    enum class SearchType {
        JAPANESE,
        ENGLISH,
        ALL
    }
}

// Data class for FTS5 search results
data class FTS5SearchResult(
    val id: Long,
    val kanji: String?,
    val reading: String,
    val meanings: String,  // JSON string
    val partsOfSpeech: String?,  // JSON string
    val frequency: Int,
    val isCommon: Boolean,
    val rank: Double
)