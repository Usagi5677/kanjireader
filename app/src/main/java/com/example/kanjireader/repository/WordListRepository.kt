package com.example.kanjireader.repository

import com.example.kanjireader.EnhancedWordResult
import com.example.kanjireader.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository for managing word lists and saved words.
 * Provides a clean API to the rest of the app.
 */
class WordListRepository(
    private val wordListDao: WordListDao,
    private val savedWordDao: SavedWordDao
) {
    
    // ===== Word List Operations =====
    
    fun getAllWordLists(): Flow<List<WordListEntity>> = wordListDao.getAllWordLists()
    
    suspend fun getAllWordListsSync(): List<WordListEntity> = withContext(Dispatchers.IO) {
        wordListDao.getAllWordListsSync()
    }
    
    fun getAllWordListsWithWords(): Flow<List<WordListWithWords>> = 
        wordListDao.getAllWordListsWithWords()
    
    suspend fun createWordList(name: String): Long = withContext(Dispatchers.IO) {
        // Check for duplicate names
        if (wordListDao.checkListNameExists(name)) {
            throw IllegalArgumentException("A list with this name already exists")
        }
        wordListDao.insertWordList(WordListEntity(name = name))
    }
    
    suspend fun deleteWordList(wordList: WordListEntity) = withContext(Dispatchers.IO) {
        wordListDao.deleteWordList(wordList)
    }
    
    suspend fun renameWordList(listId: Long, newName: String) = withContext(Dispatchers.IO) {
        // Check for duplicate names (excluding current list)
        if (wordListDao.checkListNameExistsExcluding(newName, listId)) {
            throw IllegalArgumentException("A list with this name already exists")
        }
        
        val list = wordListDao.getWordListById(listId)
        list?.let {
            wordListDao.updateWordList(
                it.copy(
                    name = newName,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
    
    // ===== Saved Word Operations =====
    
    fun getAllSavedWords(): Flow<List<SavedWordEntity>> = savedWordDao.getAllSavedWords()
    
    fun searchWords(query: String): Flow<List<SavedWordEntity>> = savedWordDao.searchWords(query)
    
    suspend fun saveWord(enhancedWordResult: EnhancedWordResult): Long = withContext(Dispatchers.IO) {
        // Check if word already exists
        val existingWord = savedWordDao.findWordByKanjiAndReading(
            enhancedWordResult.kanji,
            enhancedWordResult.reading
        )
        
        if (existingWord != null) {
            existingWord.wordId
        } else {
            savedWordDao.insertWord(SavedWordEntity.fromEnhancedWordResult(enhancedWordResult))
        }
    }
    
    suspend fun deleteWord(word: SavedWordEntity) = withContext(Dispatchers.IO) {
        savedWordDao.deleteWord(word)
    }
    
    // ===== Word-List Association Operations =====
    
    suspend fun addWordToLists(
        enhancedWordResult: EnhancedWordResult,
        listIds: List<Long>
    ) = withContext(Dispatchers.IO) {
        // First save the word (or get existing)
        val wordId = saveWord(enhancedWordResult)
        
        // Then add to selected lists
        wordListDao.addWordToLists(wordId, listIds)
    }
    
    suspend fun removeWordFromList(listId: Long, wordId: Long) = withContext(Dispatchers.IO) {
        wordListDao.removeWordFromList(listId, wordId)
        wordListDao.updateWordCount(listId)
        
        // Clean up orphaned words
        savedWordDao.deleteOrphanedWords()
    }
    
    fun getWordsInList(listId: Long): Flow<List<SavedWordEntity>> = 
        savedWordDao.getWordsInList(listId)
    
    suspend fun getListsForWord(wordId: Long): List<WordListEntity> = withContext(Dispatchers.IO) {
        savedWordDao.getListsForWord(wordId)
    }
    
    suspend fun getListIdsForWord(enhancedWordResult: EnhancedWordResult): List<Long> = 
        withContext(Dispatchers.IO) {
            val existingWord = savedWordDao.findWordByKanjiAndReading(
                enhancedWordResult.kanji,
                enhancedWordResult.reading
            )
            
            existingWord?.let {
                savedWordDao.getListIdsForWord(it.wordId)
            } ?: emptyList()
        }
    
    suspend fun isWordInList(listId: Long, wordId: Long): Boolean = withContext(Dispatchers.IO) {
        wordListDao.isWordInList(listId, wordId)
    }
    
    // ===== Statistics =====
    
    suspend fun getTotalWordCount(): Int = withContext(Dispatchers.IO) {
        savedWordDao.getTotalWordCount()
    }
    
    suspend fun getWordCountForList(listId: Long): Int = withContext(Dispatchers.IO) {
        wordListDao.getWordCountForList(listId)
    }
}