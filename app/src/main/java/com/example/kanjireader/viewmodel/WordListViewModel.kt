package com.example.kanjireader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.switchMap
import com.example.kanjireader.EnhancedWordResult
import com.example.kanjireader.database.*
import com.example.kanjireader.repository.WordListRepository
import kotlinx.coroutines.launch

enum class WordListSortOrder {
    NAME_ASC,
    NAME_DESC,
    NEWEST_FIRST,
    OLDEST_FIRST,
    MOST_WORDS,
    FEWEST_WORDS
}

/**
 * ViewModel for managing word lists and saved words.
 */
class WordListViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppRoomDatabase.getDatabase(application)
    private val repository = WordListRepository(
        database.wordListDao(),
        database.savedWordDao()
    )
    
    // Sort order state
    private val _sortOrder = MutableLiveData(WordListSortOrder.NAME_ASC)
    val sortOrder: LiveData<WordListSortOrder> = _sortOrder
    
    // LiveData for word lists with sorting
    val wordLists: LiveData<List<WordListEntity>> = _sortOrder.switchMap { order ->
        when (order) {
            WordListSortOrder.NAME_ASC -> repository.getAllWordLists()
            WordListSortOrder.NAME_DESC -> database.wordListDao().getAllWordListsSortedByNameDesc()
            WordListSortOrder.NEWEST_FIRST -> database.wordListDao().getAllWordListsSortedByNewest()
            WordListSortOrder.OLDEST_FIRST -> database.wordListDao().getAllWordListsSortedByOldest()
            WordListSortOrder.MOST_WORDS -> database.wordListDao().getAllWordListsSortedByMostWords()
            WordListSortOrder.FEWEST_WORDS -> database.wordListDao().getAllWordListsSortedByFewestWords()
        }.asLiveData()
    }
    
    // Keep the old property for backward compatibility
    val allWordLists: LiveData<List<WordListEntity>> = wordLists
    
    // LiveData for UI state
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _operationSuccess = MutableLiveData<String?>()
    val operationSuccess: LiveData<String?> = _operationSuccess
    
    private val _operationError = MutableLiveData<String?>()
    val operationError: LiveData<String?> = _operationError
    
    // Current word being added to lists
    private var currentWord: EnhancedWordResult? = null
    
    // Track ongoing operations to prevent race conditions
    private val ongoingOperations = mutableSetOf<String>()
    
    // Selected lists for the current word
    private val _selectedListIds = MutableLiveData<Set<Long>>(emptySet())
    val selectedListIds: LiveData<Set<Long>> = _selectedListIds
    
    // ===== Word List Operations =====
    
    fun createWordList(name: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val listId = repository.createWordList(name)
                _operationSuccess.value = "List '$name' created"
                
                // Add to selected lists AND save word if creating during word addition
                currentWord?.let { word ->
                    _selectedListIds.value = _selectedListIds.value?.plus(listId)
                    // Actually add the word to the new list
                    addWordToSingleList(word, listId)
                }
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Failed to create list"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun deleteWordList(wordList: WordListEntity) {
        viewModelScope.launch {
            try {
                repository.deleteWordList(wordList)
                _operationSuccess.value = "List '${wordList.name}' deleted"
            } catch (e: Exception) {
                _operationError.value = "Failed to delete list: ${e.message}"
            }
        }
    }
    
    fun renameWordList(listId: Long, newName: String) {
        viewModelScope.launch {
            try {
                repository.renameWordList(listId, newName)
                _operationSuccess.value = "List renamed to '$newName'"
            } catch (e: Exception) {
                _operationError.value = e.message ?: "Failed to rename list"
            }
        }
    }
    
    fun setSortOrder(order: WordListSortOrder) {
        _sortOrder.value = order
    }
    
    // ===== Word Operations =====
    
    fun setCurrentWord(word: EnhancedWordResult) {
        currentWord = word
        loadSelectedListsForWord(word)
    }
    
    private fun loadSelectedListsForWord(word: EnhancedWordResult) {
        viewModelScope.launch {
            try {
                val listIds = repository.getListIdsForWord(word)
                _selectedListIds.value = listIds.toSet()
            } catch (e: Exception) {
                _selectedListIds.value = emptySet()
            }
        }
    }
    
    fun toggleListSelection(listId: Long) {
        val current = _selectedListIds.value ?: emptySet()
        _selectedListIds.value = if (listId in current) {
            current - listId
        } else {
            current + listId
        }
    }
    
    fun saveWordToSelectedLists() {
        val word = currentWord ?: return
        val listIds = _selectedListIds.value?.toList() ?: return
        
        if (listIds.isEmpty()) {
            _operationError.value = "Please select at least one list"
            return
        }
        
        viewModelScope.launch {
            try {
                _isLoading.value = true
                repository.addWordToLists(word, listIds)
                
                val wordDisplay = word.kanji ?: word.reading
                _operationSuccess.value = "'$wordDisplay' added to ${listIds.size} list(s)"
                
                // Reset state
                currentWord = null
                _selectedListIds.value = emptySet()
            } catch (e: Exception) {
                _operationError.value = "Failed to save word: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun addWordToSingleList(word: EnhancedWordResult, listId: Long) {
        val operationKey = "${word.kanji ?: word.reading}-${listId}-add"
        
        // Prevent duplicate operations
        if (operationKey in ongoingOperations) {
            return
        }
        
        ongoingOperations.add(operationKey)
        
        viewModelScope.launch {
            try {
                repository.addWordToLists(word, listOf(listId))
                val wordDisplay = word.kanji ?: word.reading
                _operationSuccess.value = "'$wordDisplay' added to list"
            } catch (e: Exception) {
                _operationError.value = "Failed to add word: ${e.message}"
            } finally {
                ongoingOperations.remove(operationKey)
            }
        }
    }
    
    fun removeWordFromSingleList(listId: Long, word: EnhancedWordResult) {
        val operationKey = "${word.kanji ?: word.reading}-${listId}-remove"
        
        // Prevent duplicate operations
        if (operationKey in ongoingOperations) {
            return
        }
        
        ongoingOperations.add(operationKey)
        
        viewModelScope.launch {
            try {
                // Find the word ID first
                val wordId = repository.saveWord(word) // This will return existing ID
                repository.removeWordFromList(listId, wordId)
                val wordDisplay = word.kanji ?: word.reading
                _operationSuccess.value = "'$wordDisplay' removed from list"
            } catch (e: Exception) {
                _operationError.value = "Failed to remove word: ${e.message}"
            } finally {
                ongoingOperations.remove(operationKey)
            }
        }
    }
    
    fun removeWordFromList(listId: Long, wordId: Long) {
        viewModelScope.launch {
            try {
                repository.removeWordFromList(listId, wordId)
                _operationSuccess.value = "Word removed from list"
            } catch (e: Exception) {
                _operationError.value = "Failed to remove word: ${e.message}"
            }
        }
    }
    
    suspend fun getListIdsForWord(word: EnhancedWordResult): List<Long> {
        return repository.getListIdsForWord(word)
    }
    
    suspend fun getAllWordListsSync(): List<WordListEntity> {
        return repository.getAllWordListsSync()
    }
    
    // ===== Utility Operations =====
    
    fun clearMessages() {
        _operationSuccess.value = null
        _operationError.value = null
    }
    
    fun getWordsInList(listId: Long): LiveData<List<SavedWordEntity>> {
        return repository.getWordsInList(listId).asLiveData()
    }
    
    // ===== Data class for list with selection state =====
    
    data class SelectableWordList(
        val wordList: WordListEntity,
        val isSelected: Boolean
    )
    
    fun getSelectableWordLists(): LiveData<List<SelectableWordList>> {
        return MutableLiveData<List<SelectableWordList>>().apply {
            allWordLists.observeForever { lists ->
                val selectedIds = _selectedListIds.value ?: emptySet()
                value = lists.map { list ->
                    SelectableWordList(
                        wordList = list,
                        isSelected = list.listId in selectedIds
                    )
                }
            }
        }
    }
}