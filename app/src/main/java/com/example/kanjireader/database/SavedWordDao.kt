package com.example.kanjireader.database

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for saved word operations.
 */
@Dao
interface SavedWordDao {
    
    // ===== Word Operations =====
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: SavedWordEntity): Long
    
    @Update
    suspend fun updateWord(word: SavedWordEntity)
    
    @Delete
    suspend fun deleteWord(word: SavedWordEntity)
    
    @Query("SELECT * FROM saved_words ORDER BY addedAt DESC")
    fun getAllSavedWords(): Flow<List<SavedWordEntity>>
    
    @Query("SELECT * FROM saved_words WHERE wordId = :wordId")
    suspend fun getWordById(wordId: Long): SavedWordEntity?
    
    @Query("SELECT * FROM saved_words WHERE (kanji = :kanji OR (kanji IS NULL AND :kanji IS NULL)) AND reading = :reading LIMIT 1")
    suspend fun findWordByKanjiAndReading(kanji: String?, reading: String): SavedWordEntity?
    
    @Transaction
    @Query("SELECT * FROM saved_words WHERE wordId = :wordId")
    fun getWordWithLists(wordId: Long): Flow<SavedWordWithLists?>
    
    // ===== Search Operations =====
    
    @Query("""
        SELECT * FROM saved_words 
        WHERE kanji LIKE '%' || :query || '%' 
        OR reading LIKE '%' || :query || '%'
        OR meanings LIKE '%' || :query || '%'
        ORDER BY addedAt DESC
    """)
    fun searchWords(query: String): Flow<List<SavedWordEntity>>
    
    // ===== Words in List Operations =====
    
    @Query("""
        SELECT sw.* FROM saved_words sw
        INNER JOIN word_list_cross_ref cr ON sw.wordId = cr.wordId
        WHERE cr.listId = :listId
        ORDER BY cr.addedAt DESC
    """)
    fun getWordsInList(listId: Long): Flow<List<SavedWordEntity>>
    
    @Query("""
        SELECT sw.* FROM saved_words sw
        INNER JOIN word_list_cross_ref cr ON sw.wordId = cr.wordId
        WHERE cr.listId = :listId
        ORDER BY cr.addedAt DESC
    """)
    suspend fun getWordsInListSync(listId: Long): List<SavedWordEntity>
    
    // ===== List Membership Operations =====
    
    @Query("""
        SELECT wl.* FROM word_lists wl
        INNER JOIN word_list_cross_ref cr ON wl.listId = cr.listId
        WHERE cr.wordId = :wordId
        ORDER BY wl.name ASC
    """)
    suspend fun getListsForWord(wordId: Long): List<WordListEntity>
    
    @Query("""
        SELECT cr.listId FROM word_list_cross_ref cr
        WHERE cr.wordId = :wordId
    """)
    suspend fun getListIdsForWord(wordId: Long): List<Long>
    
    // ===== Cleanup Operations =====
    
    @Query("""
        DELETE FROM saved_words 
        WHERE wordId NOT IN (SELECT DISTINCT wordId FROM word_list_cross_ref)
    """)
    suspend fun deleteOrphanedWords(): Int
    
    // ===== Statistics =====
    
    @Query("SELECT COUNT(*) FROM saved_words")
    suspend fun getTotalWordCount(): Int
    
    @Query("SELECT COUNT(DISTINCT kanji) FROM saved_words WHERE kanji IS NOT NULL")
    suspend fun getUniqueKanjiCount(): Int
}