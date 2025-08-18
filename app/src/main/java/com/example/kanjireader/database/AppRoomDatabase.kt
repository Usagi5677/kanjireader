package com.example.kanjireader.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Room database for the app's word list feature.
 * This database is separate from the main dictionary database.
 */
@Database(
    entities = [
        WordListEntity::class,
        SavedWordEntity::class,
        WordListCrossRef::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppRoomDatabase : RoomDatabase() {
    
    abstract fun wordListDao(): WordListDao
    abstract fun savedWordDao(): SavedWordDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppRoomDatabase? = null
        
        fun getDatabase(context: Context): AppRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppRoomDatabase::class.java,
                    "app_database"
                )
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Database callback to create default lists on first creation
         */
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDatabase(database.wordListDao())
                    }
                }
            }
            
            suspend fun populateDatabase(wordListDao: WordListDao) {
                // Create default lists
                wordListDao.insertWordList(
                    WordListEntity(name = "Favorites")
                )
                wordListDao.insertWordList(
                    WordListEntity(name = "Study List")
                )
                wordListDao.insertWordList(
                    WordListEntity(name = "Recently Learned")
                )
            }
        }
    }
}