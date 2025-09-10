package com.example.kanjireader

import android.app.Application
import android.util.Log
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteOpenHelper

/**
 * Application class to initialize io.requery sqlite-android library
 * This ensures FTS5 support is available throughout the app
 */
class KanjiReaderApplication : Application() {
    
    companion object {
        private const val TAG = "KanjiReaderApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Apply saved theme before anything else
        ThemeManager.initTheme(this)
        
        Log.d(TAG, "=== INITIALIZING IO.REQUERY SQLITE-ANDROID LIBRARY ===")
        
        // CRITICAL: Initialize io.requery sqlite-android library FIRST
        // This must happen before any SQLiteOpenHelper instances are created
        try {
            Log.d(TAG, "STEP 1: Setting SQLite library configuration...")
            
            // Force io.requery to use its own native library
            System.setProperty("sqlite.lib.path", applicationInfo.nativeLibraryDir)
            System.setProperty("sqlite.lib.name", "sqlite3x")
            
            Log.d(TAG, "STEP 2: Loading native sqlite3x library for FTS5 support...")
            System.loadLibrary("sqlite3x")
            Log.d(TAG, "✅ STEP 2 COMPLETE: Native sqlite3x library loaded successfully")
            
            // Additional verification - try to get version immediately after loading
            Log.d(TAG, "STEP 3: Testing immediate SQLite version after library load...")
            val testDb = SQLiteDatabase.create(null)
            val version = testDb.rawQuery("SELECT sqlite_version()", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else "unknown"
            }
            val options = testDb.rawQuery("PRAGMA compile_options", null).use { cursor ->
                buildString {
                    while (cursor.moveToNext()) {
                        append(cursor.getString(0)).append(" ")
                    }
                }
            }
            testDb.close()
            
            Log.d(TAG, "IMMEDIATE TEST: SQLite version = $version")
            Log.d(TAG, "IMMEDIATE TEST: Compile options = $options")
            
            if (options.contains("ENABLE_FTS5")) {
                Log.d(TAG, "✅ STEP 3 SUCCESS: FTS5 detected immediately after library load")
            } else {
                Log.e(TAG, "❌ STEP 3 FAILED: FTS5 not detected immediately after library load")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ SQLITE LIBRARY SETUP FAILED", e)
            throw RuntimeException("Failed to initialize sqlite library", e)
        }
        
        // Verify FTS5 support is available with io.requery
        initIoRequery()
        
        Log.d(TAG, "=== IO.REQUERY SQLITE-ANDROID LIBRARY INITIALIZATION COMPLETE ===")
        
        // Initialize database with proper FTS population
        initializeDictionaryDatabase()
    }
    
    /**
     * Initialize dictionary database with proper FTS population
     * This follows the robust pattern for asset database initialization
     */
    private fun initializeDictionaryDatabase() {
        Log.d(TAG, "=== INITIALIZING DICTIONARY DATABASE ===")
        
        try {
            // Step 1: Get DictionaryDatabase instance
            // This will automatically:
            // - Copy database from assets if needed
            // - Trigger onCreate/onUpgrade to create schema
            Log.d(TAG, "DICT INIT STEP 1: Getting DictionaryDatabase instance...")
            val dictionaryDb = DictionaryDatabase.getInstance(this)
            Log.d(TAG, "✅ DICT INIT STEP 1 COMPLETE: DictionaryDatabase instance obtained")
            
            // Step 2: Ensure FTS tables are populated
            // This method is safe to call multiple times
            Log.d(TAG, "DICT INIT STEP 2: Ensuring FTS tables are populated...")
            dictionaryDb.ensureFTSDataPopulated()
            Log.d(TAG, "✅ DICT INIT STEP 2 COMPLETE: FTS tables populated")
            
            // Step 3: Verify final state
            Log.d(TAG, "DICT INIT STEP 3: Verifying final database state...")
            if (dictionaryDb.isDatabaseReady()) {
                Log.d(TAG, "✅ DICT INIT STEP 3 COMPLETE: Database is ready for use")
            } else {
                Log.e(TAG, "❌ DICT INIT STEP 3 FAILED: Database is not ready")
                throw RuntimeException("Dictionary database initialization failed")
            }
            
            Log.d(TAG, "✅ DICTIONARY DATABASE INITIALIZATION COMPLETE")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ CRITICAL: Dictionary database initialization failed", e)
            
            // This is a critical failure - the app cannot function without the dictionary
            throw RuntimeException("Failed to initialize dictionary database", e)
        }
        
        Log.d(TAG, "=== DICTIONARY DATABASE READY ===")
    }
    
    /**
     * Initialize and verify io.requery sqlite-android with FTS5 support
     * This must be called after SQLiteDatabase.loadLibs()
     */
    private fun initIoRequery() {
        try {
            Log.d(TAG, "IO.REQUERY STEP 1: Testing FTS5 module availability after loadLibs()...")
            
            // Create a temporary in-memory database to test FTS5
            val testDb = SQLiteDatabase.create(null)
            
            // Log compile options to verify FTS5 is available
            Log.d(TAG, "IO.REQUERY STEP 2: Checking compile options...")
            val cursor = testDb.rawQuery("PRAGMA compile_options", null)
            var fts5Found = false
            cursor.use {
                val options = buildString {
                    while (it.moveToNext()) {
                        val option = it.getString(0)
                        append(option).append(" ")
                        if (option.contains("FTS5")) {
                            fts5Found = true
                        }
                    }
                }
                Log.d(TAG, "IO.REQUERY STEP 2: Compile options: $options")
                if (fts5Found) {
                    Log.d(TAG, "✅ IO.REQUERY STEP 2 PASSED: FTS5 found in compile options")
                } else {
                    Log.w(TAG, "⚠️ IO.REQUERY STEP 2 WARNING: FTS5 not found in compile options")
                }
            }
            
            // Try to create an FTS5 table
            Log.d(TAG, "IO.REQUERY STEP 3: Creating FTS5 test table...")
            testDb.execSQL("CREATE VIRTUAL TABLE test_fts USING fts5(content)")
            Log.d(TAG, "✅ IO.REQUERY STEP 3 PASSED: FTS5 table created successfully")
            
            // Try to insert and query
            Log.d(TAG, "IO.REQUERY STEP 4: Testing FTS5 MATCH functionality...")
            testDb.execSQL("INSERT INTO test_fts(content) VALUES ('test content')")
            val searchCursor = testDb.rawQuery("SELECT * FROM test_fts WHERE test_fts MATCH 'test'", null)
            
            val hasResults = searchCursor.use { it.count > 0 }
            if (hasResults) {
                Log.d(TAG, "✅ IO.REQUERY STEP 4 PASSED: FTS5 MATCH works correctly")
            } else {
                Log.w(TAG, "⚠️ IO.REQUERY STEP 4 WARNING: FTS5 MATCH returned no results")
            }
            
            // Clean up
            testDb.execSQL("DROP TABLE test_fts")
            testDb.close()
            
            Log.d(TAG, "✅ IO.REQUERY INITIALIZATION COMPLETE: All FTS5 tests passed")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ IO.REQUERY INITIALIZATION FAILED: FTS5 not available", e)
            
            // Log additional debugging info
            try {
                val testDb = SQLiteDatabase.create(null)
                val cursor = testDb.rawQuery("PRAGMA compile_options", null)
                Log.d(TAG, "SQLite compile options after loadLibs() failure:")
                cursor.use {
                    while (it.moveToNext()) {
                        val option = it.getString(0)
                        Log.d(TAG, "  - $option")
                        if (option.contains("FTS5")) {
                            Log.d(TAG, "    ✅ FTS5 found in compile options")
                        }
                    }
                }
                testDb.close()
            } catch (e2: Exception) {
                Log.e(TAG, "❌ Failed to check compile options after loadLibs() failure", e2)
            }
            
            throw RuntimeException("io.requery sqlite-android FTS5 initialization failed", e)
        }
    }
}