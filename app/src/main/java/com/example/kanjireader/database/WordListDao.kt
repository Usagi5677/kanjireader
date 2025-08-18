package com.example.kanjireader.database

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for word list operations.
 */
@Dao
interface WordListDao {
    
    // ===== Word List Operations =====
    
    @Insert
    suspend fun insertWordList(wordList: WordListEntity): Long
    
    @Update
    suspend fun updateWordList(wordList: WordListEntity)
    
    @Delete
    suspend fun deleteWordList(wordList: WordListEntity)
    
    @Query("SELECT * FROM word_lists ORDER BY name ASC")
    fun getAllWordLists(): Flow<List<WordListEntity>>
    
    @Query("SELECT * FROM word_lists ORDER BY name DESC")
    fun getAllWordListsSortedByNameDesc(): Flow<List<WordListEntity>>
    
    @Query("SELECT * FROM word_lists ORDER BY createdAt DESC")
    fun getAllWordListsSortedByNewest(): Flow<List<WordListEntity>>
    
    @Query("SELECT * FROM word_lists ORDER BY createdAt ASC")
    fun getAllWordListsSortedByOldest(): Flow<List<WordListEntity>>
    
    @Query("SELECT * FROM word_lists ORDER BY wordCount DESC")
    fun getAllWordListsSortedByMostWords(): Flow<List<WordListEntity>>
    
    @Query("SELECT * FROM word_lists ORDER BY wordCount ASC")
    fun getAllWordListsSortedByFewestWords(): Flow<List<WordListEntity>>
    
    @Query("SELECT * FROM word_lists ORDER BY name ASC")
    suspend fun getAllWordListsSync(): List<WordListEntity>
    
    @Query("SELECT * FROM word_lists WHERE listId = :listId")
    suspend fun getWordListById(listId: Long): WordListEntity?
    
    @Query("SELECT EXISTS(SELECT 1 FROM word_lists WHERE LOWER(name) = LOWER(:name))")
    suspend fun checkListNameExists(name: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM word_lists WHERE LOWER(name) = LOWER(:name) AND listId != :excludeListId)")
    suspend fun checkListNameExistsExcluding(name: String, excludeListId: Long): Boolean
    
    @Transaction
    @Query("SELECT * FROM word_lists WHERE listId = :listId")
    fun getWordListWithWords(listId: Long): Flow<WordListWithWords?>
    
    @Transaction
    @Query("SELECT * FROM word_lists ORDER BY name ASC")
    fun getAllWordListsWithWords(): Flow<List<WordListWithWords>>
    
    // ===== Cross Reference Operations =====
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWordListCrossRef(crossRef: WordListCrossRef)
    
    @Delete
    suspend fun deleteWordListCrossRef(crossRef: WordListCrossRef)
    
    @Query("DELETE FROM word_list_cross_ref WHERE listId = :listId AND wordId = :wordId")
    suspend fun removeWordFromList(listId: Long, wordId: Long)
    
    @Query("SELECT EXISTS(SELECT 1 FROM word_list_cross_ref WHERE listId = :listId AND wordId = :wordId)")
    suspend fun isWordInList(listId: Long, wordId: Long): Boolean
    
    // ===== Word Count Operations =====
    
    @Query("UPDATE word_lists SET wordCount = (SELECT COUNT(*) FROM word_list_cross_ref WHERE listId = :listId) WHERE listId = :listId")
    suspend fun updateWordCount(listId: Long)
    
    @Query("SELECT COUNT(*) FROM word_list_cross_ref WHERE listId = :listId")
    suspend fun getWordCountForList(listId: Long): Int
    
    // ===== Bulk Operations =====
    
    @Transaction
    suspend fun addWordToLists(wordId: Long, listIds: List<Long>) {
        listIds.forEach { listId ->
            insertWordListCrossRef(WordListCrossRef(listId, wordId))
            updateWordCount(listId)
        }
    }
    
    @Transaction
    suspend fun removeWordFromLists(wordId: Long, listIds: List<Long>) {
        listIds.forEach { listId ->
            removeWordFromList(listId, wordId)
            updateWordCount(listId)
        }
    }
}