package com.example.kanjireader

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
// IMPORTANT: Ensure these imports are from io.requery.android.database.sqlite
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteOpenHelper

/**
 * Complete SQLite database helper with Japanese FTS5 support
 * This manages the pre-built database that ships with the APK.
 *
 * This version retains SQLiteOpenHelper inheritance but ensures io.requery's
 * SQLiteDatabase and its FTS5 capabilities are consistently used.
 */
class DictionaryDatabase private constructor(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null, // Factory can be null; using default which should be io.requery's
    DATABASE_VERSION
) {

    companion object {
        private const val TAG = "DictionaryDatabaseFTS" // More descriptive tag
        private const val DATABASE_NAME = "jmdict_fts5.db"
        private const val DATABASE_VERSION = 15 // IMPORTANT: Increment to trigger upgrade!

        @Volatile
        private var INSTANCE: DictionaryDatabase? = null

        @Volatile
        private var needsRecreation = false

        fun getInstance(context: Context): DictionaryDatabase {
            return INSTANCE ?: synchronized(this) {
                // Check if we need to recreate database
                if (needsRecreation) {
                    Log.d(TAG, "Database recreation needed - forcing recreation from assets")
                    val dbFile = context.getDatabasePath(DATABASE_NAME)
                    if (dbFile.exists()) {
                        dbFile.delete()
                        Log.d(TAG, "Deleted existing database for recreation")
                    }
                    needsRecreation = false
                }

                INSTANCE ?: DictionaryDatabase(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        // Force delete database for testing purposes
        fun forceDeleteDatabase(context: Context) {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                if (dbFile.exists()) {
                    val deleted = dbFile.delete()
                    Log.d(TAG, "Force delete database: $deleted")
                }
            }
        }

        // Table names
        const val TABLE_ENTRIES = "dictionary_entries"
        const val TABLE_ENGLISH_FTS = "english_fts"
        const val TABLE_KANJI_RADICAL_MAPPING = "kanji_radical_mapping"
        const val TABLE_RADICAL_KANJI_MAPPING = "radical_kanji_mapping"
        const val TABLE_RADICAL_DECOMPOSITION_MAPPING = "radical_decomposition_mapping"
        const val TABLE_PITCH_ACCENTS = "pitch_accents"
        const val COL_ID = "id"
        const val COL_KANJI = "kanji"
        const val COL_READING = "reading"
        const val COL_MEANINGS = "meanings"
        const val COL_PARTS_OF_SPEECH = "parts_of_speech"
        const val COL_FREQUENCY = "frequency"
        const val COL_IS_COMMON = "is_common"
        const val COL_IS_JMNEDICT_ENTRY = "is_jmnedict_entry"
        const val COL_TOKENIZED_KANJI = "tokenized_kanji"
        const val COL_TOKENIZED_READING = "tokenized_reading"
        const val COL_FORM_IS_COMMON = "form_is_common"

        // Kanji radical mapping table columns
        const val COL_KRM_KANJI = "kanji"
        const val COL_KRM_COMPONENTS = "components"
        
        // Radical kanji mapping table columns  
        const val COL_RKM_RADICAL = "radical"
        const val COL_RKM_STROKE_COUNT = "stroke_count"
        const val COL_RKM_KANJI_LIST = "kanji_list"
        
        // Radical decomposition mapping table columns
        const val COL_RDM_RADICAL = "radical"
        const val COL_RDM_COMPONENTS = "components"
        const val COL_RDM_COMPONENT_COUNT = "component_count"
        
        // Pitch accent table columns
        const val COL_PA_KANJI_FORM = "kanji_form"
        const val COL_PA_READING = "reading"
        const val COL_PA_ACCENT_PATTERN = "accent_pattern"

        // FTS Table names and columns
        // Note: FTS columns are implicitly part of the FTS table definition,
        // no need to define them as separate COL_ constants if only used within FTS SQL.


        // Main entries table schema (ensure tokenized columns are here)
        private const val CREATE_ENTRIES_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_ENTRIES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_KANJI TEXT,
                $COL_READING TEXT NOT NULL,
                $COL_MEANINGS TEXT NOT NULL,
                $COL_PARTS_OF_SPEECH TEXT,
                $COL_FREQUENCY INTEGER DEFAULT 0,
                $COL_IS_COMMON INTEGER DEFAULT 0,
                $COL_TOKENIZED_KANJI TEXT,
                $COL_TOKENIZED_READING TEXT,
                form_is_common INTEGER DEFAULT 0,
                UNIQUE($COL_KANJI, $COL_READING)
            )
        """

        // Essential indexes for fast search (these are for the main table)
        private const val CREATE_KANJI_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_kanji ON $TABLE_ENTRIES($COL_KANJI)"
        private const val CREATE_READING_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_reading ON $TABLE_ENTRIES($COL_READING)"
        private const val CREATE_READING_PREFIX_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_reading_prefix ON $TABLE_ENTRIES(substr($COL_READING, 1, 1), $COL_READING)"
        private const val CREATE_KANJI_PREFIX_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_kanji_prefix ON $TABLE_ENTRIES(substr($COL_KANJI, 1, 1), $COL_KANJI)"
        private const val CREATE_FREQUENCY_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_frequency_ranking ON $TABLE_ENTRIES($COL_IS_COMMON DESC, $COL_FREQUENCY DESC)"

        // --- FTS Table Definitions ---

        // English FTS5 table (updated to use unicode61 tokenizer)
        private const val CREATE_ENGLISH_FTS5_TABLE = """
            CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_ENGLISH_FTS USING fts5(
                entry_id UNINDEXED,
                meanings,
                parts_of_speech,
                tokenize='unicode61',
                prefix='1 2 3'
            )
        """

        // English FTS4 fallback (updated to use unicode61 tokenizer)
        private const val CREATE_ENGLISH_FTS4_TABLE = """
            CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_ENGLISH_FTS USING fts4(
                entry_id,
                meanings,
                parts_of_speech,
                tokenize=unicode61,
                prefix='1,2,3'
            )
        """

        // Japanese FTS5 table - REMOVED: No longer needed, using entries_fts5 instead
        /*
        private const val CREATE_JAPANESE_FTS5_TABLE = """
            CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_JAPANESE_FTS USING fts5(
                entry_id UNINDEXED,
                kanji_tokens,
                reading_tokens,
                original_kanji UNINDEXED,
                original_reading UNINDEXED,
                tokenize='unicode61',
                prefix='1 2 3'
            )
        """

        // Japanese FTS4 fallback
        private const val CREATE_JAPANESE_FTS4_TABLE = """
            CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_JAPANESE_FTS USING fts4(
                entry_id,
                kanji_tokens,
                reading_tokens,
                original_kanji,
                original_reading,
                tokenize=unicode61,
                prefix='1,2,3'
            )
        """
        */

        // --- FTS Trigger Definitions ---

        // English FTS triggers (adjusted columns to match CREATE_ENGLISH_FTS5_TABLE)
        private const val CREATE_ENGLISH_FTS_INSERT_TRIGGER = """
            CREATE TRIGGER IF NOT EXISTS english_fts_ai AFTER INSERT ON $TABLE_ENTRIES
            BEGIN
                INSERT INTO $TABLE_ENGLISH_FTS(rowid, entry_id, meanings, parts_of_speech)
                VALUES (new.$COL_ID, new.$COL_ID, new.$COL_MEANINGS, 
                       COALESCE(new.$COL_PARTS_OF_SPEECH, ''));
            END
        """
        private const val CREATE_ENGLISH_FTS_UPDATE_TRIGGER = """
            CREATE TRIGGER IF NOT EXISTS english_fts_au AFTER UPDATE ON $TABLE_ENTRIES
            BEGIN
                UPDATE $TABLE_ENGLISH_FTS SET
                    meanings = new.$COL_MEANINGS,
                    parts_of_speech = COALESCE(new.$COL_PARTS_OF_SPEECH, '')
                WHERE entry_id = old.$COL_ID;
            END
        """
        private const val CREATE_ENGLISH_FTS_DELETE_TRIGGER = """
            CREATE TRIGGER IF NOT EXISTS english_fts_ad AFTER DELETE ON $TABLE_ENTRIES
            BEGIN
                DELETE FROM $TABLE_ENGLISH_FTS WHERE entry_id = old.$COL_ID;
            END
        """


        // Tags tables
        const val TABLE_TAG_DEFINITIONS = "tag_definitions"
        const val TABLE_WORD_TAGS = "word_tags"

        // Kanji table
        const val TABLE_KANJI_ENTRIES = "kanji_entries"

        // Schema for word-tag mappings (Normalized to use entry_id)
        private const val CREATE_TAG_DEFINITIONS_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_TAG_DEFINITIONS (
                tag_code TEXT PRIMARY KEY,
                tag_name TEXT NOT NULL,
                tag_category TEXT
            )
        """
        private const val CREATE_WORD_TAGS_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_WORD_TAGS (
                entry_id INTEGER NOT NULL,
                tag_code TEXT NOT NULL,
                PRIMARY KEY (entry_id, tag_code),
                FOREIGN KEY (entry_id) REFERENCES $TABLE_ENTRIES($COL_ID) ON DELETE CASCADE,
                FOREIGN KEY (tag_code) REFERENCES $TABLE_TAG_DEFINITIONS(tag_code) ON DELETE CASCADE
            )
        """

        // Kanji entries table schema
        private const val CREATE_KANJI_ENTRIES_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_KANJI_ENTRIES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                kanji TEXT NOT NULL UNIQUE,
                jlpt_level INTEGER,
                grade INTEGER,
                stroke_count INTEGER,
                frequency INTEGER,
                meanings TEXT,
                kun_readings TEXT,
                on_readings TEXT,
                nanori_readings TEXT,
                radical_names TEXT,
                heisig_number INTEGER,
                heisig_keyword TEXT,
                components TEXT,
                radical TEXT,
                radical_number INTEGER
            )
        """

        // Kanji radical mapping tables schema
        private const val CREATE_KANJI_RADICAL_MAPPING_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_KANJI_RADICAL_MAPPING (
                $COL_KRM_KANJI TEXT PRIMARY KEY,
                $COL_KRM_COMPONENTS TEXT NOT NULL
            )
        """
        
        private const val CREATE_RADICAL_KANJI_MAPPING_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_RADICAL_KANJI_MAPPING (
                $COL_RKM_RADICAL TEXT PRIMARY KEY,
                $COL_RKM_STROKE_COUNT INTEGER,
                $COL_RKM_KANJI_LIST TEXT NOT NULL
            )
        """
        
        private const val CREATE_RADICAL_DECOMPOSITION_MAPPING_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_RADICAL_DECOMPOSITION_MAPPING (
                $COL_RDM_RADICAL TEXT PRIMARY KEY,
                $COL_RDM_COMPONENTS TEXT NOT NULL,
                $COL_RDM_COMPONENT_COUNT INTEGER NOT NULL
            )
        """
        
        private const val CREATE_PITCH_ACCENTS_TABLE = """
            CREATE TABLE IF NOT EXISTS $TABLE_PITCH_ACCENTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PA_KANJI_FORM TEXT NOT NULL,
                $COL_PA_READING TEXT NOT NULL,
                $COL_PA_ACCENT_PATTERN TEXT NOT NULL,
                UNIQUE($COL_PA_KANJI_FORM, $COL_PA_READING)
            )
        """
        
    }

    // Context is now a member variable
    private val context = context

    // Cache the database path
    private val dbPath = context.getDatabasePath(DATABASE_NAME).absolutePath

    init {
        // Note: io.requery sqlite-android initialization is handled in KanjiReaderApplication
        // This ensures FTS5 support is available before any SQLiteOpenHelper instances are created

        // Copy pre-built database from assets if needed
        copyDatabaseFromAssets()

        // CRITICAL: Explicitly trigger onCreate/onUpgrade after copying
        // Getting a writableDatabase will force SQLiteOpenHelper to call onCreate or onUpgrade
        // based on the version of the *copied* database file.
        // This will now use the io.requery SQLiteDatabase with FTS5 support.
        Log.d(TAG, "Triggering database initialization after asset copy (via getWritableDatabase())...")
        try {
            writableDatabase // This call is crucial to trigger onCreate/onUpgrade if needed.
            Log.d(TAG, "✅ Database initialization triggered successfully via writableDatabase.")
            Log.d(TAG, "📝 NOTE: FTS tables are created, but population will occur via ensureFTSDataPopulated() in Application class.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to trigger database initialization (may indicate critical DB setup issue)", e)
            throw RuntimeException("Failed to initialize database (onCreate/onUpgrade did not complete)", e)
        }
    }



    /**
     * Centralized FTS table creation with fallback (called by onCreate/onUpgrade)
     */
    private fun createFTSTableWithFallback(
        db: SQLiteDatabase,
        tableName: String,
        createFts5Sql: String,
        createFts4Sql: String
    ) {
        Log.d(TAG, "=== CREATE FTS TABLE START: $tableName ===")

        try {
            Log.d(TAG, "FTS STEP 1: Attempting to create FTS5 table: $tableName...")
            Log.d(TAG, "FTS5 SQL: $createFts5Sql")
            db.execSQL(createFts5Sql)
            Log.d(TAG, "✅ FTS STEP 1 COMPLETE: FTS5 table $tableName created successfully")

            // Verify table creation
            Log.d(TAG, "FTS STEP 2: Verifying FTS5 table creation...")
            val cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName))
            val exists = cursor.use { it.count > 0 }
            if (exists) {
                Log.d(TAG, "✅ FTS STEP 2 COMPLETE: FTS5 table $tableName verified")
            } else {
                Log.e(TAG, "❌ FTS STEP 2 FAILED: FTS5 table $tableName not found after creation")
                throw DatabaseCreationException("FTS5 table $tableName not found after creation")
            }

        } catch (e: Exception) {
            // Catch error, log, and attempt FTS4 fallback
            Log.w(TAG, "⚠️ FTS5 creation failed for $tableName (${e.message}), falling back to FTS4", e)
            try {
                // Ensure table is dropped before trying FTS4, in case of partial creation
                db.execSQL("DROP TABLE IF EXISTS $tableName")
                Log.d(TAG, "FTS FALLBACK STEP 1: Dropped potential partial FTS5 table.")

                Log.d(TAG, "FTS FALLBACK STEP 2: Creating FTS4 table: $tableName...")
                Log.d(TAG, "FTS4 SQL: $createFts4Sql")
                db.execSQL(createFts4Sql)
                Log.d(TAG, "✅ FTS FALLBACK STEP 2 COMPLETE: FTS4 table $tableName created")

                // Verify fallback table creation
                Log.d(TAG, "FTS FALLBACK STEP 3: Verifying FTS4 table creation...")
                val fallbackCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName))
                val fallbackExists = fallbackCursor.use { it.count > 0 }
                if (fallbackExists) {
                    Log.d(TAG, "✅ FTS FALLBACK STEP 3 COMPLETE: FTS4 table $tableName verified")
                } else {
                    Log.e(TAG, "❌ FTS FALLBACK STEP 3 FAILED: FTS4 table $tableName not found after creation")
                    throw DatabaseCreationException("FTS4 table $tableName not found after creation", e)
                }

            } catch (e2: Exception) {
                Log.e(TAG, "❌ Failed to create FTS4 fallback table $tableName: ${e2.message}", e2)
                throw DatabaseCreationException("Failed to create FTS table $tableName (both FTS5 and FTS4 failed)", e2)
            }
        }

        Log.d(TAG, "=== CREATE FTS TABLE COMPLETE: $tableName ===")
    }


    /**
     * Force recreate database from assets (deletes existing and copies fresh)
     */
    fun forceRecreateDatabaseFromAssets() {
        val dbFile = context.getDatabasePath(DATABASE_NAME)

        // Close any open connections first
        try {
            close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing database: ${e.message}")
        }

        // Delete existing database
        if (dbFile.exists()) {
            Log.d(TAG, "Deleting existing database at: ${dbFile.absolutePath}")
            dbFile.delete()

            // Also delete journal and wal files if they exist
            File("${dbFile.absolutePath}-journal").delete()
            File("${dbFile.absolutePath}-wal").delete()
            File("${dbFile.absolutePath}-shm").delete()
        }

        // Copy fresh from assets
        copyDatabaseFromAssets()

        // Re-initialize
        writableDatabase
    }

    /**
     * Copy pre-built database from assets to app storage
     * This only happens once on first launch or when upgrading to a new asset version
     */
    fun copyDatabaseFromAssets() { // Made public so it can be called explicitly if needed
        val dbFile = context.getDatabasePath(DATABASE_NAME)

        // Only copy if the database file does NOT exist
        // This is the common strategy for pre-built databases.
        // If it exists, onCreate/onUpgrade will handle schema changes.
        if (!dbFile.exists()) {
            dbFile.parentFile?.mkdirs() // Ensure parent directories exist

            try {
                Log.d(TAG, "Copying pre-built database from assets/databases/$DATABASE_NAME...")
                context.assets.open("databases/$DATABASE_NAME").use { input -> // Consistently uses DATABASE_NAME
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "✅ Database copied successfully to: ${dbFile.absolutePath}")

                // Set file permissions after copying
                dbFile.setReadable(true, false)
                dbFile.setWritable(true, false)
                Log.d(TAG, "✅ Database copied and permissions updated.")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to copy database from assets: ${e.message}", e)
                throw e // Re-throw to indicate critical failure
            }
        } else {
            Log.d(TAG, "Database file already exists at ${dbFile.absolutePath}. Skipping asset copy.")
        }
    }


    override fun onCreate(db: SQLiteDatabase) {
        Log.d(TAG, "=== DATABASE ONCREATE START ===")
        Log.d(TAG, "onCreate called - creating schema only (no data population)")

        db.beginTransaction()
        try {
            // Step 1: Create main table
            Log.d(TAG, "STEP 1: Creating main entries table...")
            db.execSQL(CREATE_ENTRIES_TABLE)
            Log.d(TAG, "✅ STEP 1 COMPLETE: Main entries table created")

            // Step 2: Create indexes
            Log.d(TAG, "STEP 2: Creating indexes...")
            db.execSQL(CREATE_KANJI_INDEX)
            db.execSQL(CREATE_READING_INDEX)
            db.execSQL(CREATE_READING_PREFIX_INDEX)
            db.execSQL(CREATE_KANJI_PREFIX_INDEX)
            db.execSQL(CREATE_FREQUENCY_INDEX)
            Log.d(TAG, "✅ STEP 2 COMPLETE: All indexes created")

            // Step 3: Create FTS tables (EMPTY - no population in onCreate)
            Log.d(TAG, "STEP 3: Creating empty FTS tables...")
            createFTSTableWithFallback(db, TABLE_ENGLISH_FTS, CREATE_ENGLISH_FTS5_TABLE, CREATE_ENGLISH_FTS4_TABLE)
            // createFTSTableWithFallback(db, TABLE_JAPANESE_FTS, CREATE_JAPANESE_FTS5_TABLE, CREATE_JAPANESE_FTS4_TABLE) // REMOVED: Using entries_fts5 instead
            Log.d(TAG, "✅ STEP 3 COMPLETE: Empty FTS tables created")

            // Step 4: Create triggers
            Log.d(TAG, "STEP 4: Creating triggers...")
            createTriggers(db)
            Log.d(TAG, "✅ STEP 4 COMPLETE: All triggers created")

            // Step 5: Create tag tables
            Log.d(TAG, "STEP 5: Creating tag tables...")
            db.execSQL(CREATE_TAG_DEFINITIONS_TABLE)
            db.execSQL(CREATE_WORD_TAGS_TABLE)
            Log.d(TAG, "✅ STEP 5 COMPLETE: Tag tables created")

            // Step 6: Create kanji table
            Log.d(TAG, "STEP 6: Creating kanji table...")
            db.execSQL(CREATE_KANJI_ENTRIES_TABLE)
            Log.d(TAG, "✅ STEP 6 COMPLETE: Kanji table created")
            
            // Step 7: Create kanji radical mapping tables
            Log.d(TAG, "STEP 7: Creating kanji radical mapping tables...")
            db.execSQL(CREATE_KANJI_RADICAL_MAPPING_TABLE)
            db.execSQL(CREATE_RADICAL_KANJI_MAPPING_TABLE)
            db.execSQL(CREATE_RADICAL_DECOMPOSITION_MAPPING_TABLE)
            Log.d(TAG, "✅ STEP 7 COMPLETE: Kanji radical mapping tables created")

            // Step 8: Create pitch accent table
            Log.d(TAG, "STEP 8: Creating pitch accent table...")
            db.execSQL(CREATE_PITCH_ACCENTS_TABLE)
            Log.d(TAG, "✅ STEP 8 COMPLETE: Pitch accent table created")

            // Step 9: Verify table creation (empty tables expected)
            Log.d(TAG, "STEP 9: Verifying table creation...")
            verifyTableCreation(db)
            Log.d(TAG, "✅ STEP 9 COMPLETE: Table verification done")

            Log.d(TAG, "📝 NOTE: FTS tables are empty - call ensureFTSDataPopulated() after asset copy")

            db.setTransactionSuccessful()
            Log.d(TAG, "✅ DATABASE ONCREATE TRANSACTION COMMITTED")
        } catch (e: Exception) {
            Log.e(TAG, "❌ DATABASE ONCREATE FAILED", e)
            throw DatabaseCreationException("Failed to create database in onCreate", e)
        } finally {
            db.endTransaction()
        }

        Log.d(TAG, "=== DATABASE ONCREATE COMPLETE ===")
    }

    private fun markDatabaseForRecreation() {
        Companion.needsRecreation = true
        // Clear instance so it gets recreated
        Companion.INSTANCE = null
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d(TAG, "=== DATABASE ONUPGRADE START ===")
        Log.d(TAG, "Upgrading database from version $oldVersion to $newVersion")

        // For version 13+, we need to recreate from assets but can't close db during upgrade
        if (oldVersion < 13) {
            Log.d(TAG, "Major version change to 13 - forcing immediate recreation")
            // Force immediate recreation by clearing the instance and deleting the database
            INSTANCE = null

            // Delete the database file so it gets recreated from assets
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (dbFile.exists()) {
                try {
                    dbFile.delete()
                    Log.d(TAG, "✅ Database file deleted for recreation")
                } catch (e: Exception) {
                    Log.w(TAG, "Warning: Could not delete database file: ${e.message}")
                }
            }

            // End the current transaction cleanly
            db.setTransactionSuccessful()
            return
        }

        // For version 10, the database has unified structure with JMdict+JMnedict+KanjiDic
        // We just need to ensure all tables are properly created and populated
        if (oldVersion < 10) {
            Log.d(TAG, "Major version change to 10 - database has unified structure")
            // Don't close db here - let the normal upgrade flow handle FTS table creation

            db.beginTransaction()
            try {
                // Drop old FTS tables if they exist
                db.execSQL("DROP TABLE IF EXISTS english_fts")
                Log.d(TAG, "Dropped old FTS tables")

                // Create new FTS tables
                // createFTSTableWithFallback(db, TABLE_JAPANESE_FTS, CREATE_JAPANESE_FTS5_TABLE, CREATE_JAPANESE_FTS4_TABLE) // REMOVED: Using entries_fts5 instead
                createFTSTableWithFallback(db, TABLE_ENGLISH_FTS, CREATE_ENGLISH_FTS5_TABLE, CREATE_ENGLISH_FTS4_TABLE)
                Log.d(TAG, "Created new FTS tables")

                // Create triggers
                createTriggers(db)
                Log.d(TAG, "Created triggers")

                // Populate FTS tables with the fixed tokenization
                Log.d(TAG, "Populating FTS tables...")
                populateFTSTablesFromEntries(db)
                Log.d(TAG, "FTS tables populated")

                db.setTransactionSuccessful()
                Log.d(TAG, "✅ Version 8 upgrade completed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Version 8 upgrade failed", e)
                throw e
            } finally {
                db.endTransaction()
            }
            return
        }

        db.beginTransaction()
        try {
            // Handle different upgrade paths
            when {
                oldVersion < 5 -> {
                    Log.d(TAG, "UPGRADE STEP 1: Performing major upgrade from version $oldVersion to 5+ with FTS and tokenized columns.")
                    performMajorUpgrade(db)
                }
                oldVersion < 7 -> { // This path is specifically for populating FTS tables for v6 -> v7
                    Log.d(TAG, "UPGRADE STEP 1: Performing FTS population upgrade from version $oldVersion to 7.")
                    performFTSPopulationUpgrade(db)
                }
                else -> {
                    Log.d(TAG, "UPGRADE: No specific upgrade path needed for version $oldVersion to $newVersion")
                }
            }

            db.setTransactionSuccessful()
            Log.d(TAG, "✅ DATABASE ONUPGRADE TRANSACTION COMMITTED")
        } catch (e: Exception) {
            Log.e(TAG, "❌ DATABASE ONUPGRADE FAILED", e)
            throw DatabaseUpgradeException("Failed to upgrade database from $oldVersion to $newVersion", e)
        } finally {
            db.endTransaction()
        }

        Log.d(TAG, "=== DATABASE ONUPGRADE COMPLETE ===")
    }

    /**
     * Perform major upgrade for versions < 5 (creating schema and FTS tables)
     */
    private fun performMajorUpgrade(db: SQLiteDatabase) {
        Log.d(TAG, "=== PERFORMING MAJOR UPGRADE ===")

        // Add tokenized columns if they don't exist
        Log.d(TAG, "UPGRADE STEP 2: Adding tokenized columns if needed...")
        addColumnIfNotExists(db, TABLE_ENTRIES, COL_TOKENIZED_KANJI, "TEXT")
        addColumnIfNotExists(db, TABLE_ENTRIES, COL_TOKENIZED_READING, "TEXT NOT NULL DEFAULT ''")
        
        // Add new per-form columns for form-specific common flag
        Log.d(TAG, "UPGRADE STEP 2b: Adding per-form common flag column if needed...")
        addColumnIfNotExists(db, TABLE_ENTRIES, COL_FORM_IS_COMMON, "INTEGER DEFAULT 0")
        Log.d(TAG, "✅ UPGRADE STEP 2 COMPLETE: Tokenized and per-form columns checked/added")

        // Drop existing FTS tables and triggers to ensure clean recreation
        Log.d(TAG, "UPGRADE STEP 3: Dropping old FTS tables and triggers...")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ENGLISH_FTS")
        db.execSQL("DROP TRIGGER IF EXISTS english_fts_ai")
        db.execSQL("DROP TRIGGER IF EXISTS english_fts_au")
        db.execSQL("DROP TRIGGER IF EXISTS english_fts_ad")
        Log.d(TAG, "✅ Old English FTS components dropped")

        // japanese_fts table has been removed - no longer needed
        Log.d(TAG, "✅ UPGRADE STEP 3 COMPLETE: All old FTS components dropped")

        // Recreate all FTS tables with latest schema
        Log.d(TAG, "UPGRADE STEP 4: Creating new FTS tables...")
        createFTSTableWithFallback(db, TABLE_ENGLISH_FTS, CREATE_ENGLISH_FTS5_TABLE, CREATE_ENGLISH_FTS4_TABLE)
        // createFTSTableWithFallback(db, TABLE_JAPANESE_FTS, CREATE_JAPANESE_FTS5_TABLE, CREATE_JAPANESE_FTS4_TABLE) // REMOVED: Using entries_fts5 instead
        Log.d(TAG, "✅ UPGRADE STEP 4 COMPLETE: New FTS tables created")

        // Recreate all triggers
        Log.d(TAG, "UPGRADE STEP 5: Creating triggers...")
        createTriggers(db)
        Log.d(TAG, "✅ UPGRADE STEP 5 COMPLETE: All triggers recreated")

        // No population here -- it's handled by performFTSPopulationUpgrade after onCreate/onUpgrade

        // Handle word_tags table upgrade/normalization
        Log.d(TAG, "UPGRADE STEP 7: Handling word_tags table...")
        if (!checkTableExists(db, TABLE_WORD_TAGS)) {
            Log.d(TAG, "Creating $TABLE_WORD_TAGS table...")
            db.execSQL(CREATE_WORD_TAGS_TABLE)
            Log.d(TAG, "✅ $TABLE_WORD_TAGS table created")
        } else {
            Log.d(TAG, "✅ $TABLE_WORD_TAGS table already exists")
        }
        Log.d(TAG, "✅ UPGRADE STEP 7 COMPLETE: Word tags table handled")

        // Handle kanji_entries table upgrade
        Log.d(TAG, "UPGRADE STEP 8: Handling kanji_entries table...")
        if (!checkTableExists(db, TABLE_KANJI_ENTRIES)) {
            Log.d(TAG, "Creating $TABLE_KANJI_ENTRIES table...")
            db.execSQL(CREATE_KANJI_ENTRIES_TABLE)
            Log.d(TAG, "✅ $TABLE_KANJI_ENTRIES table created")
        } else {
            Log.d(TAG, "✅ $TABLE_KANJI_ENTRIES table already exists")
        }
        Log.d(TAG, "✅ UPGRADE STEP 8 COMPLETE: Kanji entries table handled")
        
        // Handle kanji radical mapping tables upgrade
        Log.d(TAG, "UPGRADE STEP 9: Handling kanji radical mapping tables...")
        if (!checkTableExists(db, TABLE_KANJI_RADICAL_MAPPING)) {
            Log.d(TAG, "Creating $TABLE_KANJI_RADICAL_MAPPING table...")
            db.execSQL(CREATE_KANJI_RADICAL_MAPPING_TABLE)
            Log.d(TAG, "✅ $TABLE_KANJI_RADICAL_MAPPING table created")
        } else {
            Log.d(TAG, "✅ $TABLE_KANJI_RADICAL_MAPPING table already exists")
        }
        
        if (!checkTableExists(db, TABLE_RADICAL_KANJI_MAPPING)) {
            Log.d(TAG, "Creating $TABLE_RADICAL_KANJI_MAPPING table...")
            db.execSQL(CREATE_RADICAL_KANJI_MAPPING_TABLE)
            Log.d(TAG, "✅ $TABLE_RADICAL_KANJI_MAPPING table created")
        } else {
            Log.d(TAG, "✅ $TABLE_RADICAL_KANJI_MAPPING table already exists")
        }
        
        if (!checkTableExists(db, TABLE_RADICAL_DECOMPOSITION_MAPPING)) {
            Log.d(TAG, "Creating $TABLE_RADICAL_DECOMPOSITION_MAPPING table...")
            db.execSQL(CREATE_RADICAL_DECOMPOSITION_MAPPING_TABLE)
            Log.d(TAG, "✅ $TABLE_RADICAL_DECOMPOSITION_MAPPING table created")
        } else {
            Log.d(TAG, "✅ $TABLE_RADICAL_DECOMPOSITION_MAPPING table already exists")
        }
        Log.d(TAG, "✅ UPGRADE STEP 9 COMPLETE: Kanji radical mapping tables handled")

        // Final verification
        Log.d(TAG, "UPGRADE STEP 10: Final verification (schema only)...")
        verifyTableCreation(db) // Verify tables exist, but might be empty
        Log.d(TAG, "✅ UPGRADE STEP 10 COMPLETE: Final verification done")
    }

    /**
     * Perform FTS population upgrade (specifically for populating FTS tables)
     */
    private fun performFTSPopulationUpgrade(db: SQLiteDatabase) {
        Log.d(TAG, "=== PERFORMING FTS POPULATION UPGRADE ===")

        // This is specifically for databases that have the schema but empty FTS tables
        populateFTSTablesFromEntries(db) // This function will handle actual population

        // Final verification (ensure tables are now populated)
        Log.d(TAG, "FTS POPULATION UPGRADE: Final verification (data count)...")
        verifyTableCreation(db) // This also checks counts and throws if 0 entries
        Log.d(TAG, "✅ FTS POPULATION UPGRADE COMPLETE: Final verification done")
    }

    /**
     * Populate FTS tables from dictionary_entries (used by ensureFTSDataPopulated and upgrades)
     */
    private fun populateFTSTablesFromEntries(db: SQLiteDatabase) {
        Log.d(TAG, "=== POPULATING FTS TABLES FROM ENTRIES ===")

        // Check if we have data to populate
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_ENTRIES", null)
        val entryCount = cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        Log.d(TAG, "Found $entryCount entries in main table for FTS population")

        if (entryCount == 0) {
            Log.w(TAG, "⚠️ No entries found in main table - skipping FTS population")
            return
        }

        // English FTS repopulation
        Log.d(TAG, "FTS POPULATE STEP 1: Populating English FTS...")
        val englishStartTime = System.currentTimeMillis()
        populateEnglishFTSInternal(db) // Call the dedicated function
        val englishElapsed = System.currentTimeMillis() - englishStartTime
        Log.d(TAG, "✅ English FTS repopulated in ${englishElapsed}ms")

        // Japanese FTS repopulation - REMOVED: Using entries_fts5 instead
        Log.d(TAG, "FTS POPULATE STEP 2: Skipping Japanese FTS (using entries_fts5)")
        val japaneseStartTime = System.currentTimeMillis()
        // populateJapaneseFTSInternal(db) // REMOVED: japanese_fts table no longer exists
        val japaneseElapsed = System.currentTimeMillis() - japaneseStartTime
        Log.d(TAG, "✅ Japanese FTS skipped (using entries_fts5) in ${japaneseElapsed}ms")

        Log.d(TAG, "✅ FTS TABLES POPULATION COMPLETE")
    }

    /**
     * PUBLIC: Ensure FTS tables are populated with data from dictionary_entries
     * This method is safe to call multiple times - it will only populate if tables are empty
     */
    fun ensureFTSDataPopulated() {
        Log.d(TAG, "=== ENSURE FTS DATA POPULATED START ===")

        // CRITICAL: Open database directly with io.requery to ensure FTS5 support
        val db = SQLiteDatabase.openDatabase(dbPath, null,
            SQLiteDatabase.OPEN_READWRITE)

        try {
            // Check if we have source data in the main table
            val entryCount = db.rawQuery("SELECT COUNT(*) FROM $TABLE_ENTRIES", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            if (entryCount == 0) {
                Log.w(TAG, "⚠️ No entries found in $TABLE_ENTRIES - cannot populate FTS tables")
                db.close()
                return
            }
            Log.d(TAG, "Source data available: $entryCount entries in $TABLE_ENTRIES")

            // Check current FTS table status
            // val japaneseCount = db.rawQuery("SELECT COUNT(*) FROM $TABLE_JAPANESE_FTS", null).use { cursor ->
            //     if (cursor.moveToFirst()) cursor.getInt(0) else 0
            // } // REMOVED: japanese_fts table no longer exists
            val englishCount = db.rawQuery("SELECT COUNT(*) FROM $TABLE_ENGLISH_FTS", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }

        Log.d(TAG, "FTS table status: Japanese=N/A (using entries_fts5), English=$englishCount")

            // Populate if English FTS table is empty (Japanese FTS removed)
            if (englishCount == 0) {
                Log.d(TAG, "English FTS table is empty - initiating population.")
                db.beginTransaction() // Start transaction for population
                try {
                    // Japanese FTS population removed - using entries_fts5 instead
                    Log.d(TAG, "POPULATE: Skipping Japanese FTS (using entries_fts5)")
                    if (englishCount == 0) {
                        Log.d(TAG, "POPULATE: Starting English FTS population...")
                        populateEnglishFTSInternal(db) // Use internal populate function
                        Log.d(TAG, "✅ English FTS population complete")
                    }
                    db.setTransactionSuccessful()
                    Log.d(TAG, "✅ FTS POPULATION TRANSACTION COMMITTED")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ ensureFTSDataPopulated population failed", e)
                    throw DatabasePopulationException("Failed to populate FTS tables via ensureFTSDataPopulated", e)
                } finally {
                    db.endTransaction()
                }
            } else {
                Log.d(TAG, "✅ FTS tables already populated - no action needed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ ensureFTSDataPopulated failed: ${e.message}", e)
            db.close()
            throw e
        } finally {
            db.close()
        }

        Log.d(TAG, "=== ENSURE FTS DATA POPULATED COMPLETE ===")
    }

    /**
     * Populate English FTS table from main dictionary_entries table (internal)
     */
    private fun populateEnglishFTSInternal(db: SQLiteDatabase) {
        Log.d(TAG, "=== POPULATE ENGLISH FTS START (Internal) ===")
        val populateStartTime = System.currentTimeMillis()

        try {
            db.execSQL("DELETE FROM $TABLE_ENGLISH_FTS") // Clear existing data first
            Log.d(TAG, "✅ ENGLISH POPULATE STEP 1 COMPLETE: Existing data cleared")

            val populateSql = """
                INSERT INTO $TABLE_ENGLISH_FTS (
                    entry_id, meanings, parts_of_speech
                )
                SELECT 
                    $COL_ID,
                    $COL_MEANINGS,
                    COALESCE($COL_PARTS_OF_SPEECH, '')
                FROM $TABLE_ENTRIES
            """
            Log.d(TAG, "ENGLISH POPULATE STEP 2: Executing population SQL...")
            db.execSQL(populateSql)
            Log.d(TAG, "✅ ENGLISH POPULATE STEP 2 COMPLETE: Population SQL executed")

            val populatedCount = db.rawQuery("SELECT COUNT(*) FROM $TABLE_ENGLISH_FTS", null).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
            val populateElapsed = System.currentTimeMillis() - populateStartTime
            Log.d(TAG, "✅ English FTS table populated: $populatedCount entries in ${populateElapsed}ms")

        } catch (e: Exception) {
            Log.e(TAG, "❌ English FTS population failed (internal)", e)
            throw DatabasePopulationException("Failed to populate English FTS table", e)
        }
        Log.d(TAG, "=== POPULATE ENGLISH FTS COMPLETE (Internal) ===")
    }

    /**
     * Populate Japanese FTS table from main dictionary_entries table (internal)
     */
    private fun populateJapaneseFTSInternal(db: SQLiteDatabase) {
        Log.d(TAG, "=== POPULATE JAPANESE FTS START (Internal) ===")
        val populateStartTime = System.currentTimeMillis()

        // REMOVED: japanese_fts table no longer exists, using entries_fts5 instead
        try {
            // Japanese FTS population removed - using entries_fts5 instead
            Log.d(TAG, "✅ POPULATE STEP 2 COMPLETE: Japanese FTS skipped (using entries_fts5)")

            val populatedCount = 0 // Placeholder since japanese_fts no longer exists
            val populateElapsed = System.currentTimeMillis() - populateStartTime
            Log.d(TAG, "✅ Japanese FTS table skipped: using entries_fts5 instead (${populateElapsed}ms)")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Japanese FTS population failed (internal)", e)
            throw DatabasePopulationException("Failed to populate Japanese FTS table", e)
        }
        Log.d(TAG, "=== POPULATE JAPANESE FTS COMPLETE (Internal) ===")
    }

    /**
     * Helper to create all FTS triggers.
     * Moved from companion object to instance method.
     */
    private fun createTriggers(db: SQLiteDatabase) {
        Log.d(TAG, "Creating English FTS triggers...")
        db.execSQL(CREATE_ENGLISH_FTS_INSERT_TRIGGER)
        db.execSQL(CREATE_ENGLISH_FTS_UPDATE_TRIGGER)
        db.execSQL(CREATE_ENGLISH_FTS_DELETE_TRIGGER)
        Log.d(TAG, "✅ English FTS triggers created.")

        Log.d(TAG, "Creating Japanese FTS triggers...")
        // db.execSQL(CREATE_JAPANESE_FTS_INSERT_TRIGGER) // REMOVED: japanese_fts table no longer exists
        // db.execSQL(CREATE_JAPANESE_FTS_UPDATE_TRIGGER) // REMOVED: japanese_fts table no longer exists
        // db.execSQL(CREATE_JAPANESE_FTS_DELETE_TRIGGER) // REMOVED: japanese_fts table no longer exists
        Log.d(TAG, "✅ Japanese FTS triggers created.")
    }

    /**
     * Helper to verify that tables are created.
     * Moved from companion object to instance method.
     */
    private fun verifyTableCreation(db: SQLiteDatabase) {
        val tablesToVerify = listOf(
            TABLE_ENTRIES to CREATE_ENTRIES_TABLE,
            TABLE_ENGLISH_FTS to CREATE_ENGLISH_FTS5_TABLE, // or FTS4, depends on fallback
            // TABLE_JAPANESE_FTS to CREATE_JAPANESE_FTS5_TABLE, // REMOVED: Using entries_fts5 instead
            TABLE_TAG_DEFINITIONS to CREATE_TAG_DEFINITIONS_TABLE,
            TABLE_WORD_TAGS to CREATE_WORD_TAGS_TABLE,
            TABLE_KANJI_ENTRIES to CREATE_KANJI_ENTRIES_TABLE,
            TABLE_KANJI_RADICAL_MAPPING to CREATE_KANJI_RADICAL_MAPPING_TABLE,
            TABLE_RADICAL_KANJI_MAPPING to CREATE_RADICAL_KANJI_MAPPING_TABLE,
            TABLE_RADICAL_DECOMPOSITION_MAPPING to CREATE_RADICAL_DECOMPOSITION_MAPPING_TABLE
        )

        for ((tableName, createSql) in tablesToVerify) {
            if (!checkTableExists(db, tableName)) {
                Log.e(TAG, "❌ Table $tableName does NOT exist after creation attempt!")
                // Optionally re-attempt creation or throw
                try {
                    db.execSQL(createSql)
                    if (!checkTableExists(db, tableName)) {
                        throw DatabaseCreationException("Table $tableName still missing after re-attempted creation.")
                    }
                    Log.w(TAG, "⚠️ Table $tableName was missing, but successfully created on re-attempt.")
                } catch (e: Exception) {
                    throw DatabaseCreationException("Failed to create critical table: $tableName", e)
                }
            } else {
                Log.d(TAG, "✅ Table $tableName exists.")
                // For FTS tables, check if they are populated (after population steps)
                if (tableName == TABLE_ENGLISH_FTS) { // Removed TABLE_JAPANESE_FTS check
                    val countCursor = db.rawQuery("SELECT COUNT(*) FROM $tableName", null)
                    val rowCount = countCursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
                    Log.d(TAG, "    $tableName has $rowCount entries.")
                    // Do not enforce non-zero count here in verifyTableCreation
                    // as it's also called during onCreate where tables are initially empty.
                }
            }
        }
    }

    /**
     * Helper to add a column if it doesn't already exist.
     * Moved from companion object to instance method.
     */
    private fun addColumnIfNotExists(db: SQLiteDatabase, tableName: String, columnName: String, columnDefinition: String) {
        val cursor = db.rawQuery(
            "PRAGMA table_info($tableName)",
            null
        )
        cursor.use {
            val columnIndex = it.getColumnIndex("name")
            var columnExists = false
            while (it.moveToNext()) {
                if (it.getString(columnIndex) == columnName) {
                    columnExists = true
                    break
                }
            }
            if (!columnExists) {
                Log.d(TAG, "Adding column '$columnName' to '$tableName'...")
                db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $columnDefinition")
                Log.d(TAG, "✅ Column '$columnName' added to '$tableName'.")
            } else {
                Log.d(TAG, "Column '$columnName' already exists in '$tableName'.")
            }
        }
    }

    /**
     * Helper to check if a table exists.
     * Moved from companion object to instance method.
     */
    private fun checkTableExists(db: SQLiteDatabase, tableName: String): Boolean {
        val cursor = db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        )
        return cursor.use { it.count > 0 }
    }


    // ==================================================================
    // SEARCH FUNCTIONS (UNCHANGED IN THIS ITERATION, BUT CHECKED)
    // ==================================================================

    /**
     * Fast Japanese search using FTS5 with pre-tokenized data
     * This is the main function you'll use for Japanese queries
     */
    fun searchJapaneseFTS(query: String, limit: Int = 100, exactMatch: Boolean = false, isDeinflectedQuery: Boolean = false, baseForm: String? = null): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        if (query.isBlank()) return results

        // Use existing connection from SQLiteOpenHelper for better performance
        val db = readableDatabase
        val normalizedQuery = query.trim()
        val startTime = System.currentTimeMillis()

        // Build FTS5 query for optimal search
        val ftsQuery = buildJapaneseFtsQuery(normalizedQuery)
        Log.d(TAG, "🔍 Japanese FTS search: '$query' → FTS query: '$ftsQuery' (limit=$limit)")

        val sql = """
            SELECT
                T1.$COL_ID,
                T1.$COL_KANJI,
                T1.$COL_READING,
                T1.$COL_MEANINGS AS meanings,
                T1.$COL_PARTS_OF_SPEECH AS parts_of_speech,
                T1.$COL_IS_COMMON,
                T1.$COL_FREQUENCY,
                T1.$COL_IS_JMNEDICT_ENTRY,
                1.0 AS fts_relevance_rank,
                T1.$COL_FORM_IS_COMMON
            FROM $TABLE_ENTRIES AS T1
            WHERE T1.$COL_ID IN (
                SELECT rowid FROM entries_fts5
                WHERE (kanji MATCH ?) OR (reading MATCH ?) OR (tokenized_kanji MATCH ?) OR (tokenized_reading MATCH ?)
            )
            ORDER BY
                T1.$COL_FORM_IS_COMMON DESC,
                T1.$COL_IS_COMMON DESC,
                T1.$COL_FREQUENCY DESC,
                LENGTH(T1.$COL_READING) ASC,
                T1.$COL_KANJI ASC
            LIMIT ?
        """

        // Execute the search
        try {
            db.rawQuery(sql, arrayOf(
                ftsQuery,                            // P1: FTS kanji MATCH
                ftsQuery,                            // P2: FTS reading MATCH
                ftsQuery,                            // P3: FTS tokenized_kanji MATCH
                ftsQuery,                            // P4: FTS tokenized_reading MATCH
                limit.toString()                     // P5: LIMIT
            )).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(SearchResult(
                        id = cursor.getLong(0),
                        kanji = cursor.getString(1),
                        reading = cursor.getString(2),
                        meanings = cursor.getString(3),
                        partsOfSpeech = cursor.getString(4),
                        isCommon = cursor.getInt(5) == 1,
                        frequency = cursor.getInt(6),
                        isJMNEDictEntry = cursor.getInt(7) == 1,
                        rank = cursor.getDouble(8), // fts_relevance_rank from SQL
                        formIsCommon = cursor.getInt(9) == 1
                        // Note: highlightedKanji and highlightedReading are not selected in this test
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ DIAGNOSTIC ERROR: Query failed", e)
            Log.e(TAG, "❌ Error message: ${e.message}")
            Log.e(TAG, "❌ Stack trace:", e)
            // Re-throw to allow ViewModel to handle it
            throw e
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "✨ Japanese FTS search completed in ${elapsed}ms: ${results.size} results")

        return results
    }

    /**
     * Helper function to build optimal FTS5 query for Japanese search
     */
    private fun buildJapaneseFtsQuery(query: String): String {
        val words = query.split(" ").filter { it.isNotBlank() }

        return when {
            words.size == 1 -> {
                val word = words[0]
                // Include both exact matches and prefix matches for comprehensive results
                "\"$word\" OR $word*"
            }
            else -> {
                words.joinToString(" AND ") { word -> "\"$word\"*" }
            }
        }
    }


    /**
     * English FTS search (now explicitly uses English-only FTS table, and only indexed columns)
     */
    fun searchEnglishFTS(query: String, limit: Int = 100): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        if (query.isBlank()) return results

        // CRITICAL: Open database directly with io.requery to ensure FTS5 support
        val db = SQLiteDatabase.openDatabase(dbPath, null,
            SQLiteDatabase.OPEN_READWRITE)
        val startTime = System.currentTimeMillis()

        // Build English FTS query (Claude's previous logic with AND for multiple words with prefix)
        val ftsQuery = query.split(" ").filter { it.isNotBlank() }
            .joinToString(" AND ") { "$it*" } // Matches any word in query as prefix

        Log.d(TAG, "🔍 English FTS search: '$query' → FTS query: '$ftsQuery' (limit=$limit)")

        val sql = """
            SELECT
                $COL_ID,
                $COL_KANJI,
                $COL_READING,
                $COL_MEANINGS,
                $COL_PARTS_OF_SPEECH,
                $COL_IS_COMMON,
                $COL_FREQUENCY,
                1.0 AS relevance_score,
                '' AS highlighted_meanings,
                '' AS highlighted_parts_of_speech,
                $COL_FORM_IS_COMMON
            FROM $TABLE_ENTRIES
            WHERE $COL_ID IN (
                SELECT entry_id FROM $TABLE_ENGLISH_FTS 
                WHERE meanings MATCH ? OR parts_of_speech MATCH ?
            )
            ORDER BY
                $COL_FORM_IS_COMMON DESC,
                $COL_IS_COMMON DESC,
                $COL_FREQUENCY DESC,
                LENGTH($COL_MEANINGS) ASC
            LIMIT ?
        """
        // Pass the FTS query string to both meanings and parts_of_speech MATCH clauses
        try {
            db.rawQuery(sql, arrayOf(ftsQuery, ftsQuery, limit.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(SearchResult(
                        id = cursor.getLong(0),
                        kanji = cursor.getString(1),
                        reading = cursor.getString(2),
                        meanings = cursor.getString(3),
                        partsOfSpeech = cursor.getString(4),
                        isCommon = cursor.getInt(5) == 1,
                        frequency = cursor.getInt(6),
                        rank = cursor.getDouble(7),
                        highlightedKanji = null, // English search, kanji won't be highlighted here
                        highlightedReading = null, // English search, reading won't be highlighted here
                        formIsCommon = cursor.getInt(10) == 1
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ DIAGNOSTIC ERROR: English Query failed", e)
            Log.e(TAG, "❌ Error message: ${e.message}")
            Log.e(TAG, "❌ Stack trace:", e)
            db.close() // Close database on error
            throw e
        } finally {
            db.close() // Always close the database connection
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "✨ English FTS search completed in ${elapsed}ms: ${results.size} results")

        return results
    }

    /**
     * Legacy LIKE search for Japanese (can be kept as fallback or removed)
     */
    fun searchJapaneseLegacy(query: String, limit: Int = 100): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        if (query.isBlank()) return results

        val db = readableDatabase
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "⚠️ Using legacy LIKE search for Japanese (fallback)")

        // Your existing LIKE-based search logic here
        val sql = """
            SELECT
                $COL_ID, $COL_KANJI, $COL_READING, $COL_MEANINGS, $COL_PARTS_OF_SPEECH,
                $COL_IS_COMMON, $COL_FREQUENCY
            FROM $TABLE_ENTRIES
            WHERE $COL_READING LIKE ? OR $COL_KANJI LIKE ?
            ORDER BY 
                $COL_IS_COMMON DESC,
                $COL_FREQUENCY DESC,
                LENGTH($COL_READING) ASC
            LIMIT ?
        """

        db.rawQuery(sql, arrayOf("%$query%", "%$query%", limit.toString())).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(SearchResult(
                    id = cursor.getLong(0),
                    kanji = cursor.getString(1),
                    reading = cursor.getString(2),
                    meanings = cursor.getString(3),
                    partsOfSpeech = cursor.getString(4),
                    isCommon = cursor.getInt(5) == 1,
                    frequency = cursor.getInt(6)
                ))
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Legacy Japanese search completed in ${elapsed}ms: ${results.size} results")

        return results
    }

    /**
     * Wildcard search using LIKE patterns
     * Converts ? to _ for SQL LIKE syntax
     */
    fun searchWildcard(pattern: String, limit: Int = 100): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        if (pattern.isBlank()) return results

        // CRITICAL: Open database directly with io.requery to ensure FTS5 support
        val db = SQLiteDatabase.openDatabase(dbPath, null,
            SQLiteDatabase.OPEN_READONLY)

        val startTime = System.currentTimeMillis()
        Log.d(TAG, "🔍 Wildcard search: '$pattern' → SQL LIKE pattern")

        try {
            // Search in both kanji and reading columns using LIKE
            val sql = """
                SELECT
                    $COL_ID, $COL_KANJI, $COL_READING, $COL_MEANINGS, $COL_PARTS_OF_SPEECH,
                    $COL_IS_COMMON, $COL_FREQUENCY
                FROM $TABLE_ENTRIES
                WHERE $COL_READING LIKE ? OR $COL_KANJI LIKE ?
                ORDER BY 
                    $COL_IS_COMMON DESC,
                    $COL_FREQUENCY DESC,
                    LENGTH(COALESCE($COL_READING, '')) ASC
                LIMIT ?
            """

            db.rawQuery(sql, arrayOf(pattern, pattern, limit.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    results.add(SearchResult(
                        id = cursor.getLong(0),
                        kanji = cursor.getString(1),
                        reading = cursor.getString(2),
                        meanings = cursor.getString(3),
                        partsOfSpeech = cursor.getString(4),
                        isCommon = cursor.getInt(5) == 1,
                        frequency = cursor.getInt(6)
                    ))
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "✨ Wildcard search completed in ${elapsed}ms: ${results.size} results")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Wildcard search failed for pattern '$pattern'", e)
        } finally {
            db.close()
        }

        return results
    }

    // ==================================================================
    // UTILITY / GETTER FUNCTIONS
    // ==================================================================

    /**
     * Get entry by ID (for when you need full details) - Unchanged, still fast
     */
    fun getEntry(id: Long): DatabaseDictionaryEntry? {
        val db = readableDatabase

        val cursor = db.query(
            TABLE_ENTRIES,
            null,
            "$COL_ID = ?",
            arrayOf(id.toString()),
            null, null, null
        )

        return cursor.use {
            if (it.moveToFirst()) {
                DatabaseDictionaryEntry(
                    id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                    kanji = it.getString(it.getColumnIndexOrThrow(COL_KANJI)),
                    reading = it.getString(it.getColumnIndexOrThrow(COL_READING)),
                    meanings = it.getString(it.getColumnIndexOrThrow(COL_MEANINGS)),
                    partsOfSpeech = it.getString(it.getColumnIndexOrThrow(COL_PARTS_OF_SPEECH)),
                    frequency = it.getInt(it.getColumnIndexOrThrow(COL_FREQUENCY)),
                    tokenizedKanji = it.getString(it.getColumnIndexOrThrow(COL_TOKENIZED_KANJI)),
                    tokenizedReading = it.getString(it.getColumnIndexOrThrow(COL_TOKENIZED_READING)),
                    formIsCommon = it.getInt(it.getColumnIndexOrThrow(COL_FORM_IS_COMMON)) == 1
                )
            } else null
        }
    }

    /**
     * Get kanji information by character
     */
    fun getKanjiByCharacter(kanji: String): KanjiDatabaseEntry? {
        val db = readableDatabase
        
        val cursor = db.query(
            TABLE_KANJI_ENTRIES,
            null,
            "kanji = ?",
            arrayOf(kanji),
            null, null, null
        )
        
        return cursor.use {
            if (it.moveToFirst()) {
                KanjiDatabaseEntry(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    kanji = it.getString(it.getColumnIndexOrThrow("kanji")),
                    jlptLevel = it.getIntOrNull(it.getColumnIndexOrThrow("jlpt_level")),
                    grade = it.getIntOrNull(it.getColumnIndexOrThrow("grade")),
                    strokeCount = it.getIntOrNull(it.getColumnIndexOrThrow("stroke_count")),
                    frequency = it.getIntOrNull(it.getColumnIndexOrThrow("frequency")),
                    meanings = it.getString(it.getColumnIndexOrThrow("meanings")),
                    kunReadings = it.getString(it.getColumnIndexOrThrow("kun_readings")),
                    onReadings = it.getString(it.getColumnIndexOrThrow("on_readings")),
                    nanoriReadings = it.getString(it.getColumnIndexOrThrow("nanori_readings")),
                    radicalNames = it.getString(it.getColumnIndexOrThrow("radical_names")),
                    radical = it.getString(it.getColumnIndexOrThrow("radical")),
                    radicalNumber = it.getIntOrNull(it.getColumnIndexOrThrow("radical_number"))
                )
            } else null
        }
    }

    /**
     * Get kanji information for multiple characters
     */
    fun getKanjiByCharacters(kanjiList: List<String>): List<KanjiDatabaseEntry> {
        if (kanjiList.isEmpty()) return emptyList()
        
        val results = mutableListOf<KanjiDatabaseEntry>()
        val db = readableDatabase
        
        val placeholders = kanjiList.joinToString(",") { "?" }
        val cursor = db.query(
            TABLE_KANJI_ENTRIES,
            null,
            "kanji IN ($placeholders)",
            kanjiList.toTypedArray(),
            null, null, null
        )
        
        cursor.use {
            while (it.moveToNext()) {
                results.add(KanjiDatabaseEntry(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    kanji = it.getString(it.getColumnIndexOrThrow("kanji")),
                    jlptLevel = it.getIntOrNull(it.getColumnIndexOrThrow("jlpt_level")),
                    grade = it.getIntOrNull(it.getColumnIndexOrThrow("grade")),
                    strokeCount = it.getIntOrNull(it.getColumnIndexOrThrow("stroke_count")),
                    frequency = it.getIntOrNull(it.getColumnIndexOrThrow("frequency")),
                    meanings = it.getString(it.getColumnIndexOrThrow("meanings")),
                    kunReadings = it.getString(it.getColumnIndexOrThrow("kun_readings")),
                    onReadings = it.getString(it.getColumnIndexOrThrow("on_readings")),
                    nanoriReadings = it.getString(it.getColumnIndexOrThrow("nanori_readings")),
                    radicalNames = it.getString(it.getColumnIndexOrThrow("radical_names")),
                    radical = it.getString(it.getColumnIndexOrThrow("radical")),
                    radicalNumber = it.getIntOrNull(it.getColumnIndexOrThrow("radical_number"))
                ))
            }
        }
        
        return results
    }


    /**
     * Helper extension function to get nullable int from cursor
     */
    private fun android.database.Cursor.getIntOrNull(columnIndex: Int): Int? {
        return if (isNull(columnIndex)) null else getInt(columnIndex)
    }

    /**
     * Check if database is properly initialized
     */
    fun isDatabaseReady(): Boolean {
        return try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_ENTRIES LIMIT 1", null)
            cursor.use {
                it.moveToFirst()
                Log.d(TAG, "Database check: found entries")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Database not ready: ${e.message}", e)
            false
        }
    }

    /**
     * Get tags for a word (UPDATED: Use entry_id for normalized table)
     */
    fun getWordTags(entryId: Long): String? { // Changed 'word' to 'entryId'
        val db = readableDatabase
        val cursor = db.query(
            TABLE_WORD_TAGS,
            arrayOf("tag_code"), // Querying individual tag_codes now
            "entry_id = ?",
            arrayOf(entryId.toString()),
            null, null, null, null
        )

        val tags = mutableListOf<String>()
        cursor.use {
            while (it.moveToNext()) {
                tags.add(it.getString(0))
            }
        }
        return if (tags.isNotEmpty()) tags.joinToString(",") else null // Return comma-separated tags
    }
    
    /**
     * Get tags for a word by its kanji or reading
     */
    fun getTagsForWord(word: String): List<String> {
        val db = readableDatabase
        
        // First, get the entry ID for this word
        val entryQuery = """
            SELECT id FROM $TABLE_ENTRIES 
            WHERE kanji = ? OR reading = ?
            LIMIT 1
        """.trimIndent()
        
        val entryCursor = db.rawQuery(entryQuery, arrayOf(word, word))
        var entryId: Long? = null
        
        entryCursor.use {
            if (it.moveToFirst()) {
                entryId = it.getLong(0)
            }
        }
        
        if (entryId == null) {
            Log.d(TAG, "getTagsForWord: No entry found for word '$word'")
            return emptyList()
        }
        
        Log.d(TAG, "getTagsForWord: Found entry ID $entryId for word '$word'")
        
        // Now get the tags for this entry
        val tagsCursor = db.query(
            TABLE_WORD_TAGS,
            arrayOf("tag_code"),
            "entry_id = ?",
            arrayOf(entryId.toString()),
            null, null, null, null
        )

        val tags = mutableListOf<String>()
        tagsCursor.use {
            while (it.moveToNext()) {
                tags.add(it.getString(0))
            }
        }
        
        Log.d(TAG, "getTagsForWord: Found ${tags.size} tags for word '$word': $tags")
        return tags
    }
    
    /**
     * Get components (parts) for a kanji character from kradfile data
     */
    fun getKanjiComponents(kanji: String): List<String> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_KANJI_RADICAL_MAPPING,
            arrayOf(COL_KRM_COMPONENTS),
            "$COL_KRM_KANJI = ?",
            arrayOf(kanji),
            null, null, null
        )
        
        return cursor.use {
            if (it.moveToFirst()) {
                val componentsString = it.getString(0)
                if (!componentsString.isNullOrBlank()) {
                    componentsString.split(",").map { component -> component.trim() }
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
    }
    
    /**
     * Get kanji characters that contain a specific radical
     */
    fun getKanjiByRadical(radical: String): List<String> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_RADICAL_KANJI_MAPPING,
            arrayOf(COL_RKM_KANJI_LIST),
            "$COL_RKM_RADICAL = ?",
            arrayOf(radical),
            null, null, null
        )
        
        return cursor.use {
            if (it.moveToFirst()) {
                val kanjiString = it.getString(0)
                if (!kanjiString.isNullOrBlank()) {
                    kanjiString.split(",").map { kanji -> kanji.trim() }
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
    }

    /**
     * Get all radicals grouped by stroke count for radical search
     */
    fun getAllRadicalsByStrokeCount(): Map<Int, List<String>> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_RADICAL_KANJI_MAPPING,
            arrayOf(COL_RKM_RADICAL, COL_RKM_STROKE_COUNT),
            null, null, null, null,
            "$COL_RKM_STROKE_COUNT ASC"
        )
        
        val radicalsByStroke = mutableMapOf<Int, MutableList<String>>()
        
        cursor.use {
            while (it.moveToNext()) {
                val radical = it.getString(0)
                val strokeCount = it.getInt(1)
                
                radicalsByStroke.getOrPut(strokeCount) { mutableListOf() }.add(radical)
            }
        }
        
        return radicalsByStroke
    }


    /**
     * Get kanji that contain ALL of the specified radicals
     */
    fun getKanjiForMultipleRadicals(radicals: List<String>): List<String> {
        if (radicals.isEmpty()) return emptyList()
        
        val db = readableDatabase
        
        // Expand the radical selection to include composite radicals
        val expandedRadicals = expandRadicalSelection(radicals)
        
        // Build query to find kanji that appear in all radical lists
        val placeholders = expandedRadicals.joinToString(",") { "?" }
        val sql = """
            SELECT $COL_RKM_RADICAL, $COL_RKM_KANJI_LIST
            FROM $TABLE_RADICAL_KANJI_MAPPING 
            WHERE $COL_RKM_RADICAL IN ($placeholders)
        """
        
        val cursor = db.rawQuery(sql, expandedRadicals.toTypedArray())
        
        return cursor.use {
            // Map each radical to its kanji set
            val radicalToKanjiSets = mutableMapOf<String, Set<String>>()
            
            while (it.moveToNext()) {
                val radical = it.getString(0)
                val kanjiString = it.getString(1)
                
                if (!radical.isNullOrBlank() && !kanjiString.isNullOrBlank()) {
                    val kanjiSet = kanjiString.split(",").map { kanji -> kanji.trim() }.toSet()
                    radicalToKanjiSets[radical] = kanjiSet
                }
            }
            
            // For each original radical, find all kanji that satisfy it (directly or through composites)
            val originalRadicalSatisfiedKanji = mutableListOf<Set<String>>()
            
            for (originalRadical in radicals) {
                // Get expansion for this specific radical
                val expansionForRadical = expandRadicalSelection(listOf(originalRadical))
                
                // Union all kanji sets for radicals that satisfy this original radical
                var satisfiedKanji = emptySet<String>()
                for (expandedRadical in expansionForRadical) {
                    radicalToKanjiSets[expandedRadical]?.let { kanjiSet ->
                        satisfiedKanji = satisfiedKanji.union(kanjiSet)
                    }
                }
                
                if (satisfiedKanji.isNotEmpty()) {
                    originalRadicalSatisfiedKanji.add(satisfiedKanji)
                }
            }
            
            // Find intersection of all satisfied kanji sets (kanji that satisfy ALL original radicals)
            if (originalRadicalSatisfiedKanji.size != radicals.size) {
                // Some original radicals had no results
                emptyList()
            } else {
                var result = originalRadicalSatisfiedKanji.first()
                for (kanjiSet in originalRadicalSatisfiedKanji.drop(1)) {
                    result = result.intersect(kanjiSet)
                }
                result.sorted() // Return sorted list
            }
        }
    }

    /**
     * Get radicals that are present in at least one kanji from the given set
     */
    fun getValidRadicalsForKanjiSet(kanjiList: List<String>): Set<String> {
        if (kanjiList.isEmpty()) return emptySet()
        
        val db = readableDatabase
        val placeholders = kanjiList.joinToString(",") { "?" }
        val sql = """
            SELECT $COL_KRM_COMPONENTS
            FROM $TABLE_KANJI_RADICAL_MAPPING 
            WHERE $COL_KRM_KANJI IN ($placeholders)
        """
        
        val cursor = db.rawQuery(sql, kanjiList.toTypedArray())
        val allRadicals = mutableSetOf<String>()
        
        cursor.use {
            while (it.moveToNext()) {
                val components = it.getString(0)
                if (!components.isNullOrBlank()) {
                    val radicals = components.split(",").map { radical -> radical.trim() }
                    allRadicals.addAll(radicals)
                }
            }
        }
        
        return allRadicals
    }

    /**
     * Get composite radicals that contain any of the given component radicals
     * Used for hierarchical radical search expansion
     */
    fun getCompositeRadicalsForComponents(components: List<String>): Set<String> {
        if (components.isEmpty()) return emptySet()
        
        val db = readableDatabase
        val compositeRadicals = mutableSetOf<String>()
        
        // For each component, find composite radicals that contain it
        for (component in components) {
            val sql = """
                SELECT $COL_RDM_RADICAL
                FROM $TABLE_RADICAL_DECOMPOSITION_MAPPING 
                WHERE $COL_RDM_COMPONENTS LIKE ?
            """
            
            val cursor = db.rawQuery(sql, arrayOf("%$component%"))
            
            cursor.use {
                while (it.moveToNext()) {
                    val radical = it.getString(0)
                    if (!radical.isNullOrBlank()) {
                        // Verify this component is actually in the decomposition
                        val componentsList = getRadicalComponents(radical)
                        if (componentsList.contains(component)) {
                            compositeRadicals.add(radical)
                        }
                    }
                }
            }
        }
        
        return compositeRadicals
    }

    /**
     * Get the component radicals for a given composite radical
     */
    fun getRadicalComponents(radical: String): List<String> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_RADICAL_DECOMPOSITION_MAPPING,
            arrayOf(COL_RDM_COMPONENTS),
            "$COL_RDM_RADICAL = ?",
            arrayOf(radical),
            null, null, null
        )
        
        return cursor.use {
            if (it.moveToFirst()) {
                val componentsString = it.getString(0)
                if (!componentsString.isNullOrBlank()) {
                    componentsString.split(",").map { component -> component.trim() }
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
    }

    /**
     * Expand a set of selected radicals to include composite radicals
     * that can be formed from the selected components
     */
    fun expandRadicalSelection(selectedRadicals: List<String>): Set<String> {
        val expandedSet = selectedRadicals.toMutableSet()
        
        // Add composite radicals that contain any of the selected radicals as components
        val compositeRadicals = getCompositeRadicalsForComponents(selectedRadicals)
        expandedSet.addAll(compositeRadicals)
        
        return expandedSet
    }

    /**
     * Get all tag definitions (Unchanged)
     */
    fun getTagDefinitions(): Map<String, String> {
        val tags = mutableMapOf<String, String>()
        val db = readableDatabase

        val cursor = db.query(
            TABLE_TAG_DEFINITIONS,
            arrayOf("tag_code", "tag_name"),
            null, null, null, null, null
        )

        cursor.use {
            while (it.moveToNext()) {
                tags[it.getString(0)] = it.getString(1)
            }
        }

        return tags
    }

    /**
     * Get frequency for a specific word (Consider if this is still needed, or if FTS results are sufficient)
     */
    fun getWordFrequency(word: String): Int? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_ENTRIES,
            arrayOf("frequency"),
            "kanji = ? OR reading = ?",
            arrayOf(word, word),
            null, null, null
        )

        cursor.use {
            if (it.moveToFirst()) {
                val freq = it.getInt(0)
                return if (freq > 0) freq else null
            }
        }
        return null
    }

    /**
     * Search individual kanji characters in the kanji_entries table
     * This enables searching for kanji like 瓴 that exist in Kanjidic but not in common word entries
     */
    fun searchKanjiCharacters(kanjiCharacter: String, limit: Int = 10): List<KanjiDatabaseEntry> {
        if (kanjiCharacter.length != 1) {
            return emptyList() // Only search single characters
        }
        
        val db = readableDatabase
        val results = mutableListOf<KanjiDatabaseEntry>()
        
        try {
            // Search for exact kanji character match
            val cursor = db.query(
                TABLE_KANJI_ENTRIES,
                arrayOf(
                    "id", "kanji", "jlpt_level", "grade", "stroke_count", 
                    "frequency", "meanings", "kun_readings", "on_readings", 
                    "nanori_readings", "radical_names", "radical", "radical_number"
                ),
                "kanji = ?",
                arrayOf(kanjiCharacter),
                null, null,
                "frequency DESC, jlpt_level ASC", // Order by frequency (if available) and JLPT level
                limit.toString()
            )
            
            cursor.use {
                while (it.moveToNext()) {
                    val entry = KanjiDatabaseEntry(
                        id = it.getLong(0),
                        kanji = it.getString(1),
                        jlptLevel = if (it.isNull(2)) null else it.getInt(2),
                        grade = if (it.isNull(3)) null else it.getInt(3),
                        strokeCount = if (it.isNull(4)) null else it.getInt(4),
                        frequency = if (it.isNull(5)) null else it.getInt(5),
                        meanings = it.getString(6),
                        kunReadings = it.getString(7),
                        onReadings = it.getString(8),
                        nanoriReadings = it.getString(9),
                        radicalNames = it.getString(10),
                        radical = it.getString(11),
                        radicalNumber = if (it.isNull(12)) null else it.getInt(12)
                    )
                    results.add(entry)
                }
            }
            
            Log.d(TAG, "Found ${results.size} kanji entries for character '$kanjiCharacter'")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching kanji characters for '$kanjiCharacter'", e)
        }
        
        return results
    }

    /**
     * Convert KanjiDatabaseEntry to KanjiCardInfo for display in card UI
     */
    fun convertToKanjiCardInfo(kanjiList: List<String>): List<KanjiCardInfo> {
        if (kanjiList.isEmpty()) return emptyList()
        
        val kanjiEntries = getKanjiByCharacters(kanjiList)
        val kanjiMap = kanjiEntries.associateBy { it.kanji }
        
        // PERFORMANCE: Simple conversion without expensive commonality scoring
        return kanjiList.mapNotNull { kanji ->
            kanjiMap[kanji]?.let { entry ->
                // Clean up readings - remove brackets and quotes
                val cleanOnReadings = entry.onReadings
                    ?.removeSurrounding("[", "]")
                    ?.replace("\"", "")
                    ?.replace(", ", "、")
                    ?: ""
                
                val cleanKunReadings = entry.kunReadings
                    ?.removeSurrounding("[", "]")
                    ?.replace("\"", "")
                    ?.replace(", ", "、")
                    ?: ""
                
                // Clean up meanings - remove brackets and quotes
                val cleanMeanings = entry.meanings
                    ?.removeSurrounding("[", "]")
                    ?.replace("\"", "")
                    ?.split(", ")
                    ?.take(3)
                    ?.joinToString(", ")
                    ?: ""
                
                // Check if readings are available
                val hasReadings = cleanOnReadings.isNotBlank() || cleanKunReadings.isNotBlank()
                
                // Basic score from JLPT/grade only (no expensive database queries)
                val basicScore = calculateBasicCommonalityScore(entry)
                
                KanjiCardInfo(
                    kanji = entry.kanji,
                    onReadings = cleanOnReadings,
                    kunReadings = cleanKunReadings,
                    primaryMeaning = cleanMeanings,
                    jlptLevel = entry.jlptLevel,
                    grade = entry.grade,
                    commonalityScore = basicScore,
                    hasReadings = hasReadings
                )
            }
        }.sortedWith(compareByDescending<KanjiCardInfo> { it.hasReadings }
            .thenByDescending { it.commonalityScore })
    }
    
    /**
     * PERFORMANCE: Calculate commonality scores for multiple kanji using fast FTS5 queries
     * This replaces individual queries with optimized FTS5 batch queries
     */
    private fun calculateKanjiCommonalityScoresBatch(kanjiList: List<String>, kanjiMap: Map<String, KanjiDatabaseEntry>): Map<String, Int> {
        if (kanjiList.isEmpty()) return emptyMap()
        
        val db = readableDatabase
        val scores = mutableMapOf<String, Int>()
        
        try {
            // Initialize scores for all kanji
            kanjiList.forEach { scores[it] = 0 }
            
            // Use FTS5 for fast kanji searches - much faster than LIKE queries
            val kanjiStats = mutableMapOf<String, MutableList<Pair<Boolean, Int>>>()
            
            // Process each kanji with FTS5 search using entries_fts5 table
            kanjiList.forEach { targetKanji ->
                val cursor = db.rawQuery("""
                    SELECT e.${COL_IS_COMMON}, e.${COL_FREQUENCY}
                    FROM entries_fts5 f
                    JOIN ${TABLE_ENTRIES} e ON f.rowid = e.id
                    WHERE entries_fts5 MATCH ?
                    ORDER BY e.${COL_FREQUENCY} DESC
                    LIMIT 10
                """, arrayOf(targetKanji))
                
                cursor.use {
                    val stats = mutableListOf<Pair<Boolean, Int>>()
                    while (it.moveToNext()) {
                        val isCommon = it.getInt(0) == 1
                        val frequency = it.getInt(1)
                        stats.add(Pair(isCommon, frequency))
                    }
                    if (stats.isNotEmpty()) {
                        kanjiStats[targetKanji] = stats
                    }
                }
            }
            
            // Calculate scores from collected stats
            kanjiStats.forEach { (kanji, stats) ->
                var score = 0
                var totalFrequency = 0
                var commonEntries = 0
                
                stats.forEach { (isCommon, frequency) ->
                    if (isCommon) commonEntries++
                    totalFrequency += frequency
                }
                
                // Base score from frequency and common entries
                score += (totalFrequency / 1000).coerceAtMost(1000)
                score += commonEntries * 100
                score += stats.size * 10
                
                // Add JLPT and grade bonuses from kanji data
                kanjiMap[kanji]?.let { entry ->
                    when (entry.jlptLevel) {
                        5 -> score += 500 // N5 most common
                        4 -> score += 400 // N4
                        3 -> score += 300 // N3
                        2 -> score += 200 // N2
                        1 -> score += 100 // N1
                    }
                    
                    when (entry.grade) {
                        1 -> score += 600 // 1st grade most common
                        2 -> score += 500 // 2nd grade
                        3 -> score += 400 // 3rd grade
                        4 -> score += 300 // 4th grade
                        5 -> score += 200 // 5th grade
                        6 -> score += 100 // 6th grade
                        // Grades 7-12 get no bonus
                    }
                }
                
                scores[kanji] = score
            }
            
            // Add JLPT/grade scores for kanji that didn't match any FTS5 entries
            kanjiList.forEach { kanji ->
                if (!scores.containsKey(kanji)) {
                    scores[kanji] = kanjiMap[kanji]?.let { calculateBasicCommonalityScore(it) } ?: 0
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating FTS5 batch commonality scores", e)
        }
        
        return scores
    }
    
    /**
     * Calculate basic commonality score from kanji entry data only (JLPT + grade)
     * Used as fallback when batch scoring fails
     */
    private fun calculateBasicCommonalityScore(entry: KanjiDatabaseEntry): Int {
        var score = 0
        
        // Bonus for JLPT level (lower level = higher score)
        when (entry.jlptLevel) {
            5 -> score += 500 // N5 most common
            4 -> score += 400 // N4
            3 -> score += 300 // N3
            2 -> score += 200 // N2
            1 -> score += 100 // N1
        }
        
        // Bonus for school grade (lower grade = higher score)
        when (entry.grade) {
            1 -> score += 600 // 1st grade most common
            2 -> score += 500 // 2nd grade
            3 -> score += 400 // 3rd grade
            4 -> score += 300 // 4th grade
            5 -> score += 200 // 5th grade
            6 -> score += 100 // 6th grade
            // Grades 7-12 get no bonus
        }
        
        return score
    }
    
    /**
     * Calculate commonality score for a kanji based on:
     * - Dictionary entries containing this kanji (frequency and common flags)
     * - JLPT level (lower level = more common)
     * - School grade (lower grade = more common)
     * 
     * NOTE: This method is kept for compatibility but should use batch version when possible
     */
    private fun calculateKanjiCommonalityScore(kanji: String): Int {
        // Use batch method for single kanji (requires kanji entry for JLPT/grade bonuses)
        val kanjiEntry = getKanjiByCharacters(listOf(kanji)).firstOrNull()
        val kanjiMap = if (kanjiEntry != null) mapOf(kanji to kanjiEntry) else emptyMap()
        return calculateKanjiCommonalityScoresBatch(listOf(kanji), kanjiMap)[kanji] ?: 0
    }

    /**
     * Get pitch accent data for a specific word and reading
     */
    fun getPitchAccents(kanjiForm: String, reading: String): List<PitchAccent> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PITCH_ACCENTS,
            null,
            "$COL_PA_KANJI_FORM = ? AND $COL_PA_READING = ?",
            arrayOf(kanjiForm, reading),
            null, null, null
        )
        
        val results = mutableListOf<PitchAccent>()
        cursor.use {
            while (it.moveToNext()) {
                val accentPattern = it.getString(it.getColumnIndexOrThrow(COL_PA_ACCENT_PATTERN))
                
                // Parse accent numbers from comma-separated pattern string
                val accentNumbers = try {
                    accentPattern.split(",").mapNotNull { it.trim().toIntOrNull() }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing accent pattern: $accentPattern", e)
                    emptyList<Int>()
                }
                
                results.add(
                    PitchAccent(
                        kanjiForm = kanjiForm,
                        reading = reading,
                        accentNumbers = accentNumbers,
                        accentPattern = accentPattern
                    )
                )
            }
        }
        return results
    }
    
    /**
     * Get all pitch accent variations for a word (regardless of specific reading)
     */
    fun getAllPitchAccentsForWord(kanjiForm: String): List<PitchAccent> {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PITCH_ACCENTS,
            null,
            "$COL_PA_KANJI_FORM = ?",
            arrayOf(kanjiForm),
            null, null, null
        )
        
        val results = mutableListOf<PitchAccent>()
        cursor.use {
            while (it.moveToNext()) {
                val reading = it.getString(it.getColumnIndexOrThrow(COL_PA_READING))
                val accentPattern = it.getString(it.getColumnIndexOrThrow(COL_PA_ACCENT_PATTERN))
                
                // Parse accent numbers from comma-separated pattern string
                val accentNumbers = try {
                    accentPattern.split(",").mapNotNull { it.trim().toIntOrNull() }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing accent pattern: $accentPattern", e)
                    emptyList<Int>()
                }
                
                results.add(
                    PitchAccent(
                        kanjiForm = kanjiForm,
                        reading = reading,
                        accentNumbers = accentNumbers,
                        accentPattern = accentPattern
                    )
                )
            }
        }
        return results
    }
}

// ==================================================================
// DATA CLASSES
// ==================================================================

data class SearchResult(
    val id: Long,
    val kanji: String?,
    val reading: String,
    val meanings: String,
    val partsOfSpeech: String?,
    val isCommon: Boolean = false,
    val frequency: Int = 0,
    val rank: Double = 0.0,
    val isJMNEDictEntry: Boolean = false,
    val highlightedKanji: String? = null,
    val highlightedReading: String? = null,
    val formIsCommon: Boolean = false
)

data class DatabaseDictionaryEntry(
    val id: Long,
    val kanji: String?,
    val reading: String,
    val meanings: String,
    val partsOfSpeech: String?,
    val frequency: Int,
    val tokenizedKanji: String?,
    val tokenizedReading: String,
    val formIsCommon: Boolean
)

data class KanjiDatabaseEntry(
    val id: Long,
    val kanji: String,
    val jlptLevel: Int?,
    val grade: Int?,
    val strokeCount: Int?,
    val frequency: Int?,
    val meanings: String?,
    val kunReadings: String?,
    val onReadings: String?,
    val nanoriReadings: String?,
    val radicalNames: String?,
    val radical: String?,
    val radicalNumber: Int?
)

// ==================================================================
// CUSTOM EXCEPTIONS
// ==================================================================

/**
 * Exception thrown when database creation fails
 */
class DatabaseCreationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when database upgrade fails
 */
class DatabaseUpgradeException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Exception thrown when database population fails
 */
class DatabasePopulationException(message: String, cause: Throwable? = null) : Exception(message, cause)