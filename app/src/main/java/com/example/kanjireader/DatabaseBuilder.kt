package com.example.kanjireader

import android.content.ContentValues
import android.content.Context
import io.requery.android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

// Database Builder specific data classes (to avoid conflicts with other files)
private data class DatabaseCleanedEntry(
    val kana: List<DatabaseKanaEntry>,
    val kanji: List<DatabaseKanjiEntry>? = null,
    val meanings: List<String>
)

private data class DatabaseKanaEntry(
    val text: String,
    val common: Boolean = false
)

data class DatabaseKanjiEntry(
    val text: String,
    val common: Boolean = false
)

// Type aliases for compatibility with the rest of the code
private typealias CleanedEntry = DatabaseCleanedEntry
private typealias KanaEntry = DatabaseKanaEntry
private typealias KanjiEntry = DatabaseKanjiEntry

// TagEntry is imported from TagDictLoader.kt (don't redefine here)

/**
 * Tool to build the SQLite database from JSON files
 * This should be run once during development to create the pre-built database
 *
 * Usage:
 * 1. Run this on a device/emulator during development
 * 2. Pull the created database file from the device
 * 3. Place it in app/src/main/assets/databases/jmdict.db
 */
class DatabaseBuilder(private val context: Context) {

    companion object {
        private const val TAG = "DatabaseBuilder"
        private const val BATCH_SIZE = 1000

        // The 4 JSON files
        private val JSON_FILES = arrayOf(
            "jmdict_part1.json",
            "jmdict_part2.json",
            "jmdict_part3.json",
            "jmdict_part4.json"
        )

        // The 4 tag files
        private val TAG_FILES = arrayOf(
            "tags_part1.json",
            "tags_part2.json",
            "tags_part3.json",
            "tags_part4.json"
        )
    }

    private val gson = Gson()

    /**
     * Build the database from JSON files
     * This is a one-time operation during development
     */
    suspend fun buildDatabase(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting database build from JSON files...")

        // Load frequency data first
        frequencyMap = loadFrequencyData()

        try {
            // Delete existing database to start fresh
            val dbPath = context.getDatabasePath("jmdict.db")
            if (dbPath.exists()) {
                dbPath.delete()
                Log.d(TAG, "Deleted existing database")
            }

            // Create new database
            val dbHelper = DictionaryDatabaseHelper(context)
            val db = dbHelper.writableDatabase

            // Begin transaction for speed
            db.beginTransaction()

            try {
                var totalEntries = 0
                var entryId = 1L

                // Process each JSON file
                for ((index, filename) in JSON_FILES.withIndex()) {
                    Log.d(TAG, "Processing $filename (${index + 1}/${JSON_FILES.size})...")

                    val entries = loadJsonFile(filename)
                    if (entries.isEmpty()) {
                        Log.e(TAG, "Failed to load $filename")
                        continue
                    }

                    // Insert entries in batches
                    entries.chunked(BATCH_SIZE).forEach { batch ->
                        insertBatch(db, batch, entryId)
                        entryId += batch.size
                        totalEntries += batch.size

                        if (totalEntries % 10000 == 0) {
                            Log.d(TAG, "Processed $totalEntries entries...")
                        }
                    }
                }

                // Optimize FTS index
                Log.d(TAG, "Optimizing FTS index...")
                db.execSQL("INSERT INTO english_fts(english_fts) VALUES('optimize')")

                // Create tags tables
                Log.d(TAG, "Creating tags tables...")
                createTagsTables(db)

                // Populate tag definitions
                Log.d(TAG, "Populating tag definitions...")
                populateTagDefinitions(db)

                // Load tags data
                Log.d(TAG, "Loading tags data...")
                loadTagsData(db)

                // Analyze tables for better query planning
                db.execSQL("ANALYZE")

                db.setTransactionSuccessful()
                Log.d(TAG, "Database build completed! Total entries: $totalEntries")

                // Log database size
                val sizeInMB = dbPath.length() / (1024.0 * 1024.0)
                Log.d(TAG, "Database size: %.2f MB".format(sizeInMB))

                return@withContext true

            } finally {
                db.endTransaction()
                db.close()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to build database", e)
            return@withContext false
        }
    }

    /**
     * Load entries from a single JSON file
     */
    private fun loadJsonFile(filename: String): List<CleanedEntry> {
        return try {
            context.assets.open(filename).use { inputStream ->
                InputStreamReader(inputStream, "UTF-8").use { reader ->
                    val listType = object : TypeToken<List<CleanedEntry>>() {}.type
                    gson.fromJson(reader, listType)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $filename", e)
            emptyList()
        }
    }

    /**
     * Insert a batch of entries into the database
     */
    // In DatabaseBuilder.kt, update the insertBatch method to handle both formats:

    // In DatabaseBuilder.kt, update the insertBatch method:

    private fun insertBatch(db: SQLiteDatabase, entries: List<CleanedEntry>, startId: Long) {
        var currentId = startId

        for (entry in entries) {
            if (entry.meanings.isEmpty()) continue

            // Extract all readings from this entry
            val kanaReadings = entry.kana.map { it.text }.filter { it.isNotEmpty() }

            if (kanaReadings.isEmpty()) continue

            // Create entries for all kanji/reading combinations
            if (entry.kanji != null && entry.kanji.isNotEmpty()) {
                // Has kanji - create entry for each kanji/reading combination
                for (kanjiEntry in entry.kanji) {
                    for (reading in kanaReadings) {
                        val kanjiText = kanjiEntry.text
                        val isCommonForThisEntry = kanjiEntry.common

                        try {
                            val values = ContentValues().apply {
                                put("id", currentId)
                                put("kanji", kanjiText)
                                put("reading", reading)
                                put("meanings", gson.toJson(entry.meanings))
                                put("is_common", if (isCommonForThisEntry) 1 else 0)
                                putNull("parts_of_speech")
                                put("frequency", calculateFrequency(entry))
                            }

                            // Use insertWithOnConflict with REPLACE strategy
                            val rowId = db.insertWithOnConflict(
                                "dictionary_entries",
                                null,
                                values,
                                SQLiteDatabase.CONFLICT_REPLACE
                            )

                            if (rowId == -1L) {
                                Log.w(TAG, "Failed to insert entry: $kanjiText/$reading")
                            }

                        } catch (e: Exception) {
                            Log.e(TAG, "Error inserting entry: $kanjiText/$reading", e)
                        }

                        currentId++
                    }
                }
            } else {
                // Kana-only entries - create entry for each reading
                val isKanaCommon = entry.kana.any { it.common }

                for (reading in kanaReadings) {
                    try {
                        val values = ContentValues().apply {
                            put("id", currentId)
                            putNull("kanji")  // No kanji for kana-only entries
                            put("reading", reading)
                            put("meanings", gson.toJson(entry.meanings))
                            put("is_common", if (isKanaCommon) 1 else 0)
                            putNull("parts_of_speech")
                            put("frequency", calculateFrequency(entry))
                        }

                        // Use insertWithOnConflict with REPLACE strategy
                        val rowId = db.insertWithOnConflict(
                            "dictionary_entries",
                            null,
                            values,
                            SQLiteDatabase.CONFLICT_REPLACE
                        )

                        if (rowId == -1L) {
                            Log.w(TAG, "Failed to insert kana-only entry: $reading")
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Error inserting kana-only entry: $reading", e)
                    }

                    currentId++
                }
            }
        }
    }

    /**
     * Create tags tables
     */
    private fun createTagsTables(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS tag_definitions (
                tag_code TEXT PRIMARY KEY,
                tag_name TEXT NOT NULL,
                tag_category TEXT
            )
        """)

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS word_tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word TEXT NOT NULL,
                tag_data TEXT NOT NULL,
                UNIQUE(word)
            )
        """)

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_word_tags_word ON word_tags(word)")
    }

    /**
     * Populate tag definitions
     */
    private fun populateTagDefinitions(db: SQLiteDatabase) {
        val tagDefinitions = mapOf(
            // Verb types
            "v1" to Pair("Ichidan verb", "verb"),
            "v5k" to Pair("Godan verb with 'ku' ending", "verb"),
            "v5g" to Pair("Godan verb with 'gu' ending", "verb"),
            "v5s" to Pair("Godan verb with 'su' ending", "verb"),
            "v5t" to Pair("Godan verb with 'tsu' ending", "verb"),
            "v5n" to Pair("Godan verb with 'nu' ending", "verb"),
            "v5b" to Pair("Godan verb with 'bu' ending", "verb"),
            "v5m" to Pair("Godan verb with 'mu' ending", "verb"),
            "v5r" to Pair("Godan verb with 'ru' ending", "verb"),
            "v5u" to Pair("Godan verb with 'u' ending", "verb"),
            "vk" to Pair("Kuru verb - special class", "verb"),
            "vs" to Pair("noun or participle which takes the aux. verb suru", "verb"),
            "vs-i" to Pair("suru verb - included", "verb"),
            "v5k-s" to Pair("Godan verb - Iku/Yuku special class", "verb"),

            // Adjectives
            "adj-i" to Pair("adjective (keiyoushi)", "adjective"),
            "adj-na" to Pair("adjectival nouns or quasi-adjectives (keiyodoshi)", "adjective"),
            "adj-no" to Pair("nouns which may take the genitive case particle 'no'", "adjective"),
            "adj-pn" to Pair("pre-noun adjectival (rentaishi)", "adjective"),
            "adj-t" to Pair("'taru' adjective", "adjective"),
            "adj-f" to Pair("noun or verb acting prenominally", "adjective"),

            // Nouns
            "n" to Pair("noun (common) (futsuumeishi)", "noun"),
            "n-suf" to Pair("noun, used as a suffix", "noun"),
            "n-pref" to Pair("noun, used as a prefix", "noun"),
            "n-t" to Pair("noun (temporal) (jisoumeishi)", "noun"),
            "n-adv" to Pair("adverbial noun (fukushitekimeishi)", "noun"),
            "n-pr" to Pair("proper noun", "noun"),

            // Other parts of speech
            "exp" to Pair("expressions (phrases, clauses, etc.)", "phrase"),
            "int" to Pair("interjection (kandoushi)", "interjection"),
            "adv" to Pair("adverb (fukushi)", "adverb"),
            "adv-to" to Pair("adverb taking the 'to' particle", "adverb"),
            "prt" to Pair("particle", "particle"),
            "conj" to Pair("conjunction", "conjunction"),
            "pref" to Pair("prefix", "prefix"),
            "suf" to Pair("suffix", "suffix"),

            // Style markers
            "arch" to Pair("archaic", "style"),
            "col" to Pair("colloquial", "style"),
            "form" to Pair("formal or literary term", "style"),
            "hon" to Pair("honorific or respectful (sonkeigo) language", "style"),
            "hum" to Pair("humble (kenjougo) language", "style"),
            "pol" to Pair("polite (teineigo) language", "style"),
            "sl" to Pair("slang", "style"),
            "vulg" to Pair("vulgar expression or word", "style"),
            "sens" to Pair("sensitive", "style"),
            "derog" to Pair("derogatory", "style"),
            "obs" to Pair("obsolete term", "style"),
            "dated" to Pair("dated term", "style"),

            // Dialects
            "ksb" to Pair("Kansai-ben", "dialect"),
            "ktb" to Pair("Kantou-ben", "dialect"),
            "kyb" to Pair("Kyoto-ben", "dialect"),
            "osb" to Pair("Osaka-ben", "dialect"),
            "tsb" to Pair("Tosa-ben", "dialect"),
            "thb" to Pair("Touhoku-ben", "dialect"),
            "tsug" to Pair("Tsugaru-ben", "dialect"),
            "kyu" to Pair("Kyuushuu-ben", "dialect"),
            "rkb" to Pair("Ryuukyuu-ben", "dialect"),

            // Fields
            "med" to Pair("medicine", "field"),
            "comp" to Pair("computing", "field"),
            "math" to Pair("mathematics", "field"),
            "physics" to Pair("physics", "field"),
            "chem" to Pair("chemistry", "field"),
            "biol" to Pair("biology", "field"),
            "geol" to Pair("geology", "field"),
            "ling" to Pair("linguistics", "field"),
            "mil" to Pair("military", "field"),
            "law" to Pair("law", "field"),
            "econ" to Pair("economics", "field"),
            "bus" to Pair("business", "field"),
            "finc" to Pair("finance", "field"),
            "sports" to Pair("sports", "field"),
            "baseb" to Pair("baseball", "field"),
            "golf" to Pair("golf", "field"),
            "sumo" to Pair("sumo", "field"),
            "MA" to Pair("martial arts", "field"),
            "food" to Pair("food, cooking", "field"),

            // Usage notes
            "uk" to Pair("word usually written using kana alone", "usage"),
            "abbr" to Pair("abbreviation", "usage"),
            "on-mim" to Pair("onomatopoeic or mimetic word", "usage"),
            "chn" to Pair("children's language", "usage"),
            "fem" to Pair("female term or language", "usage"),
            "male" to Pair("male term or language", "usage"),
            "fam" to Pair("familiar language", "usage"),
            "rare" to Pair("rare term", "usage")
        )

        val stmt = db.compileStatement(
            "INSERT OR IGNORE INTO tag_definitions (tag_code, tag_name, tag_category) VALUES (?, ?, ?)"
        )

        tagDefinitions.forEach { (code, pair) ->
            stmt.bindString(1, code)
            stmt.bindString(2, pair.first)
            stmt.bindString(3, pair.second)
            stmt.executeInsert()
        }

        stmt.close()
    }

    /**
     * Load tags data from JSON files
     */
    private fun loadTagsData(db: SQLiteDatabase) {
        // First, build a mapping of primary form -> all variants from JMdict
        val variantMap = buildVariantMapping()

        var totalTags = 0

        for ((index, filename) in TAG_FILES.withIndex()) {
            Log.d(TAG, "Processing $filename (${index + 1}/${TAG_FILES.size})...")

            try {
                context.assets.open(filename).use { inputStream ->
                    InputStreamReader(inputStream, "UTF-8").use { reader ->
                        val mapType = object : TypeToken<Map<String, TagEntry>>() {}.type
                        val entries: Map<String, TagEntry> = gson.fromJson(reader, mapType)

                        entries.entries.chunked(BATCH_SIZE).forEach { batch ->
                            insertTagBatch(db, batch)

                            // Use variant mapping to insert tags for all variants
                            batch.forEach { (mainForm, tagEntry) ->
                                val variants = variantMap[mainForm] ?: emptyList()
                                variants.forEach { variant ->
                                    if (variant != mainForm) {
                                        insertSingleTag(db, variant, tagEntry)
                                    }
                                }
                            }

                            totalTags += batch.size
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $filename", e)
            }
        }

        Log.d(TAG, "Loaded $totalTags tag entries")
    }

    private fun buildVariantMapping(): Map<String, List<String>> {
        val variantMap = mutableMapOf<String, List<String>>()

        // Read JMdict files to build variant relationships
        for (filename in JSON_FILES) {
            try {
                context.assets.open(filename).use { inputStream ->
                    InputStreamReader(inputStream, "UTF-8").use { reader ->
                        val entries: List<CleanedEntry> = gson.fromJson(reader,
                            object : TypeToken<List<CleanedEntry>>() {}.type)

                        entries.forEach { entry ->
                            // Get all kanji forms from this entry
                            val kanjiTexts = entry.kanji?.map { it.text } ?: emptyList()

                            // Each kanji form maps to all other forms in the same entry
                            kanjiTexts.forEach { kanji ->
                                variantMap[kanji] = kanjiTexts
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build variant map from $filename", e)
            }
        }

        Log.d(TAG, "Built variant map with ${variantMap.size} entries")
        return variantMap
    }

    // Add this helper method
    private fun insertSingleTag(db: SQLiteDatabase, word: String, tagEntry: TagEntry) {
        val stmt = db.compileStatement(
            "INSERT OR REPLACE INTO word_tags (word, tag_data) VALUES (?, ?)"
        )

        try {
            stmt.bindString(1, word)
            stmt.bindString(2, gson.toJson(tagEntry))
            stmt.executeInsert()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to insert tag for variant: $word", e)
        } finally {
            stmt.close()
        }
    }

    /**
     * Insert a batch of tag entries
     */
    private fun insertTagBatch(db: SQLiteDatabase, entries: List<Map.Entry<String, TagEntry>>) {
        val stmt = db.compileStatement(
            "INSERT OR REPLACE INTO word_tags (word, tag_data) VALUES (?, ?)"
        )

        for ((word, tagEntry) in entries) {
            try {
                stmt.bindString(1, word)
                stmt.bindString(2, gson.toJson(tagEntry))
                stmt.executeInsert()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to insert tags for word: $word", e)
            }
        }

        stmt.close()
    }

    private var frequencyMap: Map<String, Int> = emptyMap()

    /**
     * Load frequency data from CSV file
     */
    private suspend fun loadFrequencyData(): Map<String, Int> = withContext(Dispatchers.IO) {
        val frequencyMap = mutableMapOf<String, Int>()
        
        try {
            val inputStream = context.assets.open("frequency_data.csv")
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            
            reader.useLines { lines ->
                lines.drop(1).forEach { line -> // Skip header
                    // Since frequency numbers contain commas, prioritize tab separation
                    val parts = if (line.contains("\t")) {
                        line.split("\t")
                    } else {
                        // If no tabs, try to be smart about comma separation
                        val commaparts = line.split(",")
                        if (commaparts.size > 4) {
                            // Rejoin the last parts that are likely part of the frequency number
                            val word = commaparts[0]
                            val pos = commaparts[1] 
                            val reading = commaparts[2]
                            val frequency = commaparts.drop(3).joinToString("")
                            listOf(word, pos, reading, frequency)
                        } else {
                            commaparts
                        }
                    }
                    
                    if (parts.size >= 4) {
                        val word = parts[0].trim().replace("\"", "")
                        val frequencyStr = parts[3].trim().replace("\"", "").replace(",", "").trim()
                        val frequency = frequencyStr.toIntOrNull()
                        
                        if (frequency != null && word.isNotEmpty() && !word.contains("#")) {
                            // Only keep the highest frequency for each word
                            val existingFreq = frequencyMap[word]
                            if (existingFreq == null || frequency > existingFreq) {
                                frequencyMap[word] = frequency
                                if (word == "する") {
                                    Log.d(TAG, "Updated frequency for する: $frequency (from $frequencyStr)")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Silently fail and use default values
        }
        
        frequencyMap
    }

    /**
     * Calculate frequency score from loaded data
     */
    private fun calculateFrequency(entry: CleanedEntry): Int {
        // Check kanji forms first
        entry.kanji?.forEach { kanjiEntry ->
            frequencyMap[kanjiEntry.text]?.let { freq ->
                if (kanjiEntry.text == "為る") {
                    Log.d(TAG, "Found frequency for 為る: $freq")
                }
                return freq
            }
        }
        
        // Check kana forms
        entry.kana.forEach { kanaEntry ->
            frequencyMap[kanaEntry.text]?.let { freq ->
                if (kanaEntry.text == "する") {
                    Log.d(TAG, "Found frequency for する: $freq")
                }
                return freq
            }
        }
        
        // Log if する or 為る has no frequency found
        val hasする = entry.kana.any { it.text == "する" }
        val has為る = entry.kanji?.any { it.text == "為る" } == true
        if (hasする || has為る) {
            Log.d(TAG, "No frequency found for entry - kana: ${entry.kana.map { it.text }}, kanji: ${entry.kanji?.map { it.text }}")
        }
        
        // Default fallback
        return 0
    }

    /**
     * Copy the built database from internal storage to a location
     * where it can be pulled via adb
     */
    fun exportDatabase(): Boolean {
        try {
            val internalDb = context.getDatabasePath("jmdict.db")
            val externalDb = File(context.getExternalFilesDir(null), "jmdict.db")

            if (!internalDb.exists()) {
                Log.e(TAG, "Database doesn't exist yet")
                return false
            }

            internalDb.copyTo(externalDb, overwrite = true)
            Log.d(TAG, "Database exported to: ${externalDb.absolutePath}")
            Log.d(TAG, "Pull with: adb pull ${externalDb.absolutePath}")

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export database", e)
            return false
        }
    }
}

/**
 * Simplified database helper for building
 * (Reuses schema from DictionaryDatabase)
 */
private class DictionaryDatabaseHelper(context: Context) :
    io.requery.android.database.sqlite.SQLiteOpenHelper(context, "jmdict.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        // Main entries table
        db.execSQL("""
            CREATE TABLE dictionary_entries (
                id INTEGER PRIMARY KEY,
                kanji TEXT,
                reading TEXT NOT NULL,
                meanings TEXT NOT NULL,
                parts_of_speech TEXT,
                frequency INTEGER DEFAULT 0,
                is_common INTEGER DEFAULT 0,
                UNIQUE(kanji, reading)
            )
        """)

        // Indexes
        db.execSQL("CREATE INDEX idx_kanji ON dictionary_entries(kanji)")
        db.execSQL("CREATE INDEX idx_reading ON dictionary_entries(reading)")
        db.execSQL("CREATE INDEX idx_frequency ON dictionary_entries(frequency)")
        db.execSQL("CREATE INDEX idx_common ON dictionary_entries(is_common)")

        // Pitch accent table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS pitch_accents (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kanji_form TEXT NOT NULL,
                reading TEXT NOT NULL,
                accent_pattern TEXT NOT NULL,
                accent_numbers TEXT NOT NULL,
                UNIQUE(kanji_form, reading)
            )
        """)

        // Indexes for pitch accent table
        db.execSQL("CREATE INDEX idx_pitch_kanji_form ON pitch_accents(kanji_form)")
        db.execSQL("CREATE INDEX idx_pitch_reading ON pitch_accents(reading)")

        // FTS4 table (more compatible with older Android versions)
        db.execSQL("""
            CREATE VIRTUAL TABLE english_fts USING fts4(
                entry_id,
                meanings
            )
        """)

        // Trigger to keep FTS in sync
        db.execSQL("""
            CREATE TRIGGER entries_ai AFTER INSERT ON dictionary_entries
            BEGIN
                INSERT INTO english_fts(rowid, entry_id, meanings)
                VALUES (new.id, new.id, new.meanings);
            END
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Not needed for building
    }
}