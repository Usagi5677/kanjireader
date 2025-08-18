package com.example.kanjireader

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DictionaryViewModel(application: Application) : AndroidViewModel(application), DictionaryStateObserver {

    companion object {
        private const val TAG = "DictionaryViewModel"
    }

    // LiveData for UI state
    private val _searchResults = MutableLiveData<List<UnifiedDictionaryEntry>>()
    val searchResults: LiveData<List<UnifiedDictionaryEntry>> = _searchResults
    
    // Pagination support
    private var allSearchResults: MutableList<UnifiedDictionaryEntry> = mutableListOf()
    private var currentDisplayedCount = 0
    private val PAGE_SIZE = 20
    private var hasMoreInDatabase = true
    private var currentSearchOffset = 0

    private val _uiState = MutableLiveData<UiState>()
    val uiState: LiveData<UiState> = _uiState

    private val _errorState = MutableLiveData<ErrorState?>()
    val errorState: LiveData<ErrorState?> = _errorState

    private val _dictionaryReady = MutableLiveData<Boolean>()
    val dictionaryReady: LiveData<Boolean> = _dictionaryReady

    // Repository and components
    private lateinit var repository: DictionaryRepository
    private var tagDictLoader: TagDictSQLiteLoader? = null
    private val wordExtractor = JapaneseWordExtractor()
    // private lateinit var entryGrouper: DictionaryEntryGrouper  // No longer needed with Kuromoji
    
    // Enhanced DAT manager
    // DATManager removed - using FTS5 exclusively

    // Search state
    private var searchJob: Job? = null
    private var currentQuery: String = ""

    init {
        Log.d(TAG, "DictionaryViewModel initialized")
        _uiState.value = UiState.Empty
        _dictionaryReady.value = false
        
        // Register for dictionary state changes
        DictionaryStateManager.addObserver(this)
        
        // Check current dictionary state
        _dictionaryReady.value = DictionaryStateManager.isDictionaryReady()
    }

    fun initializeRepository() {
        try {
            // Get repository instance
            repository = DictionaryRepository.getInstance(getApplication())

            // Entry grouper no longer needed - Kuromoji handles morphological analysis
            
            // Setup with deinflection if dictionaries are ready
            val dictReady = DictionaryStateManager.isDictionaryReady()
            
            Log.d(TAG, "Initialization check: Dict ready=$dictReady")
            
            if (dictReady) {
                Log.d(TAG, "Proceeding with repository setup...")
                setupRepositoryWithDeinflection()
            } else {
                Log.w(TAG, "Dictionary repository not fully ready yet")
            }

            // Warm up SQLite cache in background (disabled for DAT-only mode)
            // viewModelScope.launch {
            //     repository.warmUpCache()
            //     Log.d(TAG, "SQLite cache warmed up")
            // }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize repository", e)
            // Don't show technical errors to users - handle silently
        }
    }

    private fun setupRepositoryWithDeinflection() {
        Log.d(TAG, "Setting up repository with deinflection engine")

        try {
            // Check if repository is initialized first
            if (!::repository.isInitialized) {
                Log.w(TAG, "Repository not initialized yet, skipping deinflection setup")
                return
            }

            // Get or create deinflection engine
            val deinflectionEngine = TenTenStyleDeinflectionEngine()

            // Get tag loader
            val tagLoader = TagDictSQLiteLoader(getApplication())
            if (tagLoader.isTagDatabaseReady()) {
                Log.d(TAG, "Tags available in SQLite")
                tagDictLoader = tagLoader
            }

            // SQLite FTS5 system is ready for search
            Log.d(TAG, "SQLite FTS5 system initialized - ready for search!")
            
            // Initialize repository with SQLite components only
            repository.initialize(deinflectionEngine, tagLoader)
            Log.d(TAG, "Repository initialized with SQLite FTS5 and deinflection engine")
            
            _dictionaryReady.value = true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup repository with deinflection", e)
            // Don't show technical errors to users - handle silently
        }
    }

    fun search(query: String) {
        val trimmedQuery = query.trim()
        Log.d(TAG, "=== SEARCH REQUEST === Query: '$query' -> '$trimmedQuery' (caller: ${Thread.currentThread().stackTrace[3]})")
        
        if (trimmedQuery.isEmpty()) {
            Log.d(TAG, "Empty query, clearing search")
            _uiState.value = UiState.Empty
            _searchResults.value = emptyList()
            currentQuery = ""
            return
        }

        // Cancel previous search
        val previousJob = searchJob
        searchJob?.cancel()
        if (previousJob != null) {
            Log.d(TAG, "Cancelled previous search job for query: '$currentQuery'")
        }

        // Reset pagination state for new query
        if (currentQuery != trimmedQuery) {
            allSearchResults.clear()
            currentDisplayedCount = 0
            hasMoreInDatabase = true
            currentSearchOffset = 0
        }
        
        currentQuery = trimmedQuery
        _uiState.value = UiState.Loading

        searchJob = viewModelScope.launch {
            try {
                Log.d(TAG, "=== SEARCH START === Searching for: '$currentQuery' (thread: ${Thread.currentThread().name})")

                // FTS5 provides fast search - minimal debounce needed
                delay(50) // Light debounce for FTS5 searches

                // Check if repository is initialized
                if (!::repository.isInitialized) {
                    Log.w(TAG, "Repository not initialized yet")
                    _uiState.value = UiState.NoResults
                    return@launch
                }

                // Detect if this is a paragraph that needs Kuromoji processing
                val isParagraph = currentQuery.lines().size > 1 || currentQuery.length > 30
                Log.d(TAG, "Query analysis: length=${currentQuery.length}, lines=${currentQuery.lines().size}, isParagraph=$isParagraph")
                
                val startTime = System.currentTimeMillis()
                val searchResults: List<WordResult> = if (isParagraph) {
                    // Use Kuromoji to extract individual words from paragraph
                    Log.d(TAG, "Processing paragraph with Kuromoji word extraction")
                    searchParagraphWithKuromoji(currentQuery)
                } else {
                    // Regular single word/phrase search
                    Log.d(TAG, "Regular FTS5 search for single word/phrase")
                    repository.search(currentQuery)
                }
                Log.d(TAG, "Search for '$currentQuery' returned ${searchResults.size} results (isParagraph: $isParagraph)")
                val searchTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "⏱️ Search query took ${searchTime}ms")

                // Get deinflection info if available
                val deinflectionInfo = (repository as DictionaryRepository).getDeinflectionInfo(currentQuery)

                // Log for debugging
                Log.d(TAG, "Search '$currentQuery' returned ${searchResults.size} results")
                if (deinflectionInfo != null) {
                    Log.d(TAG, "Deinflection: ${currentQuery} -> ${deinflectionInfo.baseForm}")
                }

                // Convert WordResult to UnifiedDictionaryEntry directly (bypassing grouper)
                val unsortedResults: List<UnifiedDictionaryEntry> = searchResults.map { wordResult ->
                    // Use the database flag to determine if we should show deinflection info
                    val shouldShowDeinflection = wordResult.isDeinflectedValidConjugation
                    
                    UnifiedDictionaryEntry(
                        primaryForm = wordResult.kanji ?: wordResult.reading,
                        primaryReading = wordResult.reading,
                        meanings = wordResult.meanings,
                        primaryTags = emptyList(), // WordResult doesn't have partOfSpeech
                        variants = emptyList(),
                        isCommon = wordResult.isCommon,
                        verbType = if (shouldShowDeinflection) deinflectionInfo?.verbType?.toString() else null,
                        conjugationInfo = if (shouldShowDeinflection) deinflectionInfo?.originalForm else null,
                        frequency = if (wordResult.frequency > 0) wordResult.frequency else null,
                        isJMNEDictEntry = wordResult.isJMNEDictEntry,
                        isDeinflectedResult = wordResult.isDeinflectedValidConjugation
                    )
                }
                
                // Sort results: regular dictionary entries first, JMNEDict entries (proper nouns) last
                val groupedResults = unsortedResults.sortedBy { entry ->
                    if (entry.isJMNEDictEntry) 1 else 0 // JMNEDict entries get priority 1 (last)
                }
                
                val regularCount = groupedResults.count { !it.isJMNEDictEntry }
                val jmneCount = groupedResults.count { it.isJMNEDictEntry }
                Log.d(TAG, "Final results sorted: ${groupedResults.size} total (${regularCount} regular, ${jmneCount} proper nouns at bottom)")

                // Store all results (no pagination)
                allSearchResults = groupedResults.toMutableList()
                currentDisplayedCount = groupedResults.size
                hasMoreInDatabase = false // No pagination needed
                currentSearchOffset = 0
                
                Log.d(TAG, "Search complete: ${allSearchResults.size} total results")
                
                // Display all results
                _searchResults.value = groupedResults
                _uiState.value = if (groupedResults.isEmpty()) {
                    UiState.NoResults
                } else {
                    UiState.Results(currentDisplayedCount, currentDisplayedCount)
                }
                
                // Log paragraph processing summary
                if (isParagraph) {
                    Log.d(TAG, "PARAGRAPH SEARCH SUMMARY: Input length=${currentQuery.length}, Words extracted via Kuromoji, Total results=${groupedResults.size}")
                }
                
                Log.d(TAG, "=== SEARCH COMPLETE === Query: '$currentQuery', Results: ${groupedResults.size}, State: ${_uiState.value}")

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "Search cancelled for query '$currentQuery' - not changing UI state")
                    // Don't change UI state when search is cancelled - keep previous results
                } else {
                    Log.e(TAG, "Search failed for query '$currentQuery'", e)
                    // Show no results instead of technical error
                    _uiState.value = UiState.NoResults
                }
            }
        }
    }

    /**
     * Process paragraph text using Kuromoji to extract individual words and search for each
     */
    private suspend fun searchParagraphWithKuromoji(paragraph: String): List<WordResult> {
        Log.d(TAG, "=== KUROMOJI PARAGRAPH PROCESSING START ===")
        Log.d(TAG, "Input paragraph: '${paragraph.take(100)}${if (paragraph.length > 100) "..." else ""}'")
        
        val allResults = mutableListOf<WordResult>()
        
        try {
            // Extract words using Kuromoji
            val wordPositions = wordExtractor.extractWordsWithKuromoji(paragraph, repository)
            Log.d(TAG, "Kuromoji extracted ${wordPositions.size} words")
            
            // Track unique words to avoid duplicates
            val processedWords = mutableSetOf<String>()
            
            // Create set of all words actually found in the paragraph for filtering
            val actualWordsInText = wordPositions.map { it.word }.toSet()
            Log.d(TAG, "Actual words extracted from paragraph: ${actualWordsInText.joinToString(", ")}")
            
            for (wordPos in wordPositions) {
                val word = wordPos.word
                
                // Skip if we've already processed this word
                if (processedWords.contains(word)) {
                    Log.d(TAG, "Skipping duplicate word: '$word'")
                    continue
                }
                processedWords.add(word)
                
                try {
                    Log.d(TAG, "Searching for extracted word: '$word' (${wordPos.startPosition}-${wordPos.endPosition})")
                    
                    // Search dictionary for this individual word
                    val wordResults = repository.search(word, limit = 10) // Get more results for filtering
                    
                    if (wordResults.isNotEmpty()) {
                        Log.d(TAG, "Found ${wordResults.size} dictionary results for word '$word'")
                        
                        // Filter results to only include words that actually appear in the paragraph
                        val filteredResults = wordResults.filter { result ->
                            val resultWord = result.kanji ?: result.reading
                            val isInText = actualWordsInText.contains(resultWord)
                            
                            if (!isInText) {
                                Log.d(TAG, "Filtering out '$resultWord' - not found in paragraph text")
                            }
                            
                            isInText
                        }
                        
                        Log.d(TAG, "After filtering: ${filteredResults.size} results for word '$word'")
                        allResults.addAll(filteredResults)
                    } else {
                        Log.d(TAG, "No dictionary results for word '$word'")
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Error searching for word '$word': ${e.message}")
                }
            }
            
            Log.d(TAG, "=== KUROMOJI PARAGRAPH PROCESSING COMPLETE ===")
            Log.d(TAG, "Total unique words processed: ${processedWords.size}")
            Log.d(TAG, "Total dictionary results found: ${allResults.size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in Kuromoji paragraph processing", e)
        }
        
        return allResults
    }

    fun clearSearch() {
        searchJob?.cancel()
        currentQuery = ""
        _searchResults.value = emptyList()
        allSearchResults = mutableListOf()
        currentDisplayedCount = 0
        _uiState.value = UiState.Empty
    }
    
    fun loadMoreResults() {
        // Check if we have more results in our current cache
        if (currentDisplayedCount < allSearchResults.size) {
            // Show more from existing results
            val nextBatch = allSearchResults.take(currentDisplayedCount + PAGE_SIZE)
            currentDisplayedCount = nextBatch.size
            _searchResults.value = nextBatch
            
            // Update to results state with counts
            _uiState.value = UiState.Results(currentDisplayedCount, allSearchResults.size)
            
            Log.d(TAG, "Loaded more cached results: showing $currentDisplayedCount of ${allSearchResults.size}")
            return
        }
        
        // If we've shown all cached results but there might be more in database
        if (!hasMoreInDatabase) {
            Log.d(TAG, "No more results available in database")
            return
        }
        
        // Fetch more results from database
        fetchMoreFromDatabase()
    }
    
    fun hasMoreResults(): Boolean {
        return false // No pagination - show all results at once
    }
    
    private fun fetchMoreFromDatabase() {
        if (currentQuery.isEmpty()) return
        
        // Don't start new search if one is already running
        if (searchJob?.isActive == true) {
            Log.d(TAG, "Search already in progress, skipping")
            return
        }
        
        Log.d(TAG, "Fetching more results from database for query: '$currentQuery'")
        
        // Set loading state
        _uiState.value = UiState.LoadingMore
        
        searchJob = viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    // Use current offset for next batch (already set correctly)
                    Log.d(TAG, "Loading more results with offset $currentSearchOffset")
                    
                    // Fetch more results with offset
                    val moreResults: List<WordResult> = try {
                        Log.d(TAG, "Using FTS5 search for more results with offset $currentSearchOffset")
                        val results = repository.search(currentQuery, PAGE_SIZE, currentSearchOffset)
                        Log.d(TAG, "FTS5 search returned ${results.size} results")
                        if (results.isNotEmpty()) {
                            Log.d(TAG, "First result: ${results[0].kanji ?: results[0].reading}")
                        }
                        results
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception in search block: ${e.javaClass.simpleName}: ${e.message}")
                        e.printStackTrace()
                        Log.d(TAG, "DAT search failed for more results, using standard repository: ${e.message}")
                        val results = repository.search(currentQuery, PAGE_SIZE, currentSearchOffset)
                        Log.d(TAG, "Fallback repository search returned ${results.size} results")
                        results
                    }
                    
                    Log.d(TAG, "Final moreResults size: ${moreResults.size} for offset $currentSearchOffset")
                
                if (moreResults.isNotEmpty()) {
                    // Convert more results directly (no grouper)
                    val groupedMoreResults: List<UnifiedDictionaryEntry> = moreResults.map { wordResult ->
                        UnifiedDictionaryEntry(
                            primaryForm = wordResult.kanji ?: wordResult.reading,
                            primaryReading = wordResult.reading,
                            meanings = wordResult.meanings,
                            primaryTags = emptyList(),
                            variants = emptyList(),
                            isCommon = wordResult.isCommon,
                            verbType = null, // Could get from cache if needed
                            conjugationInfo = null,
                            frequency = if (wordResult.frequency > 0) wordResult.frequency else null
                        )
                    }
                    
                    // Add to existing results (avoiding duplicates)
                    val newEntries = groupedMoreResults.filter { newEntry ->
                        !allSearchResults.any { existing -> existing.primaryForm == newEntry.primaryForm }
                    }
                    allSearchResults.addAll(newEntries)
                    
                    // Update offset for next potential fetch
                    currentSearchOffset += PAGE_SIZE
                    
                    // Check if there are potentially more results
                    hasMoreInDatabase = moreResults.size >= PAGE_SIZE
                    
                    // Update displayed results
                    val nextBatch = allSearchResults.take(currentDisplayedCount + PAGE_SIZE)
                    currentDisplayedCount = nextBatch.size
                    _searchResults.value = nextBatch
                    
                    // Update UI state
                    _uiState.value = UiState.Results(currentDisplayedCount, allSearchResults.size)
                    
                    Log.d(TAG, "Added ${newEntries.size} new results, now showing $currentDisplayedCount of ${allSearchResults.size}, hasMore=$hasMoreInDatabase")
                } else {
                    // No more results available
                    hasMoreInDatabase = false
                    _uiState.value = UiState.Results(currentDisplayedCount, allSearchResults.size)
                    Log.d(TAG, "No more results available, total: ${allSearchResults.size}")
                }
                
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "LoadMore cancelled - keeping current UI state")
                    // Don't change UI state when cancelled
                } else {
                    Log.e(TAG, "Failed to fetch more results", e)
                    _uiState.value = UiState.Results(currentDisplayedCount, allSearchResults.size)
                }
            }
            }
        }
    }
    
    fun getAllResultsCount(): Int {
        return allSearchResults.size
    }

    fun clearError() {
        _errorState.value = null
    }

    // Implementation of DictionaryStateObserver
    override fun onDictionaryStateChanged(isReady: Boolean) {
        Log.d(TAG, "Dictionary state changed: ready=$isReady")
        _dictionaryReady.value = isReady

        if (isReady) {
            setupRepositoryWithDeinflection()
        }
    }

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
        DictionaryStateManager.removeObserver(this)
        Log.d(TAG, "DictionaryViewModel cleared")
    }
    
    /**
     * Get DAT system status for debugging
     */
    fun getDATSystemStatus(): String {
        return "FTS5 | FTS5 search system active"
    }

    // UI States
    sealed class UiState {
        object Empty : UiState()
        object Loading : UiState()
        data class Results(val showingCount: Int = 0, val totalCount: Int = 0) : UiState()
        object LoadingMore : UiState()
        object NoResults : UiState()
        object Error : UiState()
    }
    
    // Removed isWordResultProperNoun function - now using database flags

    // Error States
    sealed class ErrorState {
        data class RepositoryInitFailed(val message: String) : ErrorState()
        data class RepositoryNotInitialized(val message: String) : ErrorState()
        data class SearchFailed(val message: String) : ErrorState()
        data class DictionaryNotReady(val message: String) : ErrorState()
    }
}