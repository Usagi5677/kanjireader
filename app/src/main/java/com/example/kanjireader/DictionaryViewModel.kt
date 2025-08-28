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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
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

    private val _dictionaryReady = MutableLiveData<Boolean>()
    val dictionaryReady: LiveData<Boolean> = _dictionaryReady

    // Repository and components
    private lateinit var repository: DictionaryRepository
    private var tagDictLoader: TagDictSQLiteLoader? = null
    private val wordExtractor = JapaneseWordExtractor()

    // Search state
    private var searchJob: Job? = null
    private var currentQuery: String = ""

    // StateFlow for search queries - eliminates race conditions
    private val _searchQuery = MutableStateFlow("")

    init {
        Log.d(TAG, "DictionaryViewModel initialized")
        _uiState.value = UiState.Empty
        _dictionaryReady.value = false

        // Register for dictionary state changes
        DictionaryStateManager.addObserver(this)

        // Check current dictionary state
        _dictionaryReady.value = DictionaryStateManager.isDictionaryReady()

        // Launch a coroutine that listens to the search StateFlow with proper threading
        // Using flowOn(Dispatchers.IO) ensures debouncing happens on IO thread pool
        viewModelScope.launch {
            _searchQuery
                .debounce(150) // Increased from 50ms to prevent issues during rapid typing
                .flowOn(Dispatchers.IO) // Process flow operations on IO dispatcher
                .collect { query ->
                    performSearch(query)
                }
        }
    }

    fun initializeRepository() {
        try {
            repository = DictionaryRepository.getInstance(getApplication())
            val dictReady = DictionaryStateManager.isDictionaryReady()
            Log.d(TAG, "Initialization check: Dict ready=$dictReady")

            if (dictReady) {
                Log.d(TAG, "Proceeding with repository setup...")
                setupRepositoryWithDeinflection()
            } else {
                Log.w(TAG, "Dictionary repository not fully ready yet")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize repository", e)
        }
    }

    private fun setupRepositoryWithDeinflection() {
        Log.d(TAG, "Setting up repository with deinflection engine")
        try {
            if (!::repository.isInitialized) {
                Log.w(TAG, "Repository not initialized yet, skipping deinflection setup")
                return
            }

            val deinflectionEngine = TenTenStyleDeinflectionEngine()
            val tagLoader = TagDictSQLiteLoader(getApplication())
            if (tagLoader.isTagDatabaseReady()) {
                Log.d(TAG, "Tags available in SQLite")
                tagDictLoader = tagLoader
            }

            Log.d(TAG, "SQLite FTS5 system initialized - ready for search!")
            repository.initialize(deinflectionEngine, tagLoader)
            Log.d(TAG, "Repository initialized with SQLite FTS5 and deinflection engine")
            _dictionaryReady.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup repository with deinflection", e)
        }
    }

    fun search(query: String) {
        val trimmedQuery = query.trim()
        Log.d(TAG, "=== SEARCH REQUEST === Query: '$query' -> '$trimmedQuery' (caller: ${Thread.currentThread().stackTrace[3]})")

        if (trimmedQuery.isEmpty()) {
            Log.d(TAG, "Empty query, clearing search")
            clearSearch()
            return
        }

        // Update StateFlow value - this will trigger debounced search automatically
        _searchQuery.value = trimmedQuery
    }

    private suspend fun performSearch(query: String) {
        val trimmedQuery = query.trim()
        
        // Exit early if query is empty - prevents stale searches from showing results
        if (trimmedQuery.isEmpty()) {
            Log.d(TAG, "Skipping empty query from channel")
            return
        }

        // Cancel any previous search job to prevent outdated results.
        // This must be done at the start of the new search process.
        searchJob?.cancel()

        // Create a new search job for this specific query.
        searchJob = viewModelScope.launch {
            try {
                // First, perform all UI updates and state management on the main thread.
                withContext(Dispatchers.Main) {
                    // Reset pagination state if the query has changed.
                    if (currentQuery != trimmedQuery) {
                        allSearchResults.clear()
                        currentDisplayedCount = 0
                        hasMoreInDatabase = true
                        currentSearchOffset = 0
                    }
                    currentQuery = trimmedQuery
                    _uiState.value = UiState.Loading // Update UI to show loading state.
                }

                // Next, perform all the heavy, non-UI-related work on a background thread.
                // This prevents the main thread from being blocked.
                val searchResults: List<WordResult> = withContext(Dispatchers.IO) {
                    // Use the actual query being searched, not the stored currentQuery
                    val searchQuery = trimmedQuery
                    val isParagraph = searchQuery.lines().size > 1 || searchQuery.length > 30
                    Log.d(TAG, "Query analysis: length=${searchQuery.length}, lines=${searchQuery.lines().size}, isParagraph=$isParagraph")

                    val startTime = System.currentTimeMillis()
                    val results = if (isParagraph) {
                        Log.d(TAG, "Processing paragraph with Kuromoji word extraction")
                        searchParagraphWithKuromoji(searchQuery)
                    } else {
                        Log.d(TAG, "Regular FTS5 search for single word/phrase")
                        repository.search(searchQuery, limit = 500)
                    }
                    val searchTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "⏱️ Search query took ${searchTime}ms")
                    results
                }

                // Process the raw search results into the final data structure.
                val unsortedResults = withContext(Dispatchers.IO) { // Changed to IO since getDeinflectionInfo does heavy work
                    val queryForDeinflection = if ((repository as DictionaryRepository).isRomajiQuery(trimmedQuery)) {
                        repository.convertRomajiToHiragana(trimmedQuery)
                    } else {
                        trimmedQuery
                    }
                    val deinflectionInfo = repository.getDeinflectionInfo(queryForDeinflection)

                    searchResults.map { wordResult ->
                        val shouldShowDeinflection = wordResult.isDeinflectedValidConjugation
                        val pitchAccents: List<PitchAccent> = emptyList()

                        UnifiedDictionaryEntry(
                            primaryForm = wordResult.kanji ?: wordResult.reading,
                            primaryReading = wordResult.reading,
                            meanings = wordResult.meanings,
                            primaryTags = emptyList(),
                            variants = emptyList(),
                            isCommon = wordResult.isCommon,
                            verbType = if (shouldShowDeinflection) deinflectionInfo?.verbType?.toString() else null,
                            conjugationInfo = if (shouldShowDeinflection) deinflectionInfo?.originalForm else null,
                            frequency = if (wordResult.frequency > 0) wordResult.frequency else null,
                            isDeinflectedResult = wordResult.isDeinflectedValidConjugation,
                            pitchAccents = pitchAccents.takeIf { it.isNotEmpty() }
                        )
                    }
                }

                // Finally, update LiveData on the main thread after all data is ready.
                withContext(Dispatchers.Main) {
                    allSearchResults = unsortedResults.toMutableList()
                    currentDisplayedCount = allSearchResults.size
                    hasMoreInDatabase = false
                    currentSearchOffset = 0

                    _searchResults.value = allSearchResults
                    _uiState.value = if (allSearchResults.isEmpty()) {
                        UiState.NoResults
                    } else {
                        UiState.Results(currentDisplayedCount, allSearchResults.size)
                    }

                    Log.d(TAG, "Final results sorted: ${allSearchResults.size} total")
                    Log.d(TAG, "Search complete: ${allSearchResults.size} total results")
                }
            } catch (e: Exception) {
                // All exception handling that updates the UI must also be on the main thread.
                withContext(Dispatchers.Main) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        Log.d(TAG, "Search cancelled for query '$currentQuery' - not changing UI state")
                    } else {
                        Log.e(TAG, "Search failed for query '$currentQuery'", e)
                        _uiState.value = UiState.NoResults
                    }
                }
            }
        }
    }

    private suspend fun searchParagraphWithKuromoji(paragraph: String): List<WordResult> {
        Log.d(TAG, "=== KUROMOJI PARAGRAPH PROCESSING START ===")
        Log.d(TAG, "Input paragraph: '${paragraph.take(100)}${if (paragraph.length > 100) "..." else ""}'")
        val allResults = mutableListOf<WordResult>()
        try {
            val wordPositions = wordExtractor.extractWordsWithKuromoji(paragraph, repository)
            Log.d(TAG, "Kuromoji extracted ${wordPositions.size} words")
            val processedWords = mutableSetOf<String>()
            val actualWordsInText = wordPositions.map { it.word }.toSet()
            Log.d(TAG, "Actual words extracted from paragraph: ${actualWordsInText.joinToString(", ")}")
            for (wordPos in wordPositions) {
                val word = wordPos.word
                if (processedWords.contains(word)) {
                    Log.d(TAG, "Skipping duplicate word: '$word'")
                    continue
                }
                processedWords.add(word)
                try {
                    Log.d(TAG, "Searching for extracted word: '$word' (${wordPos.startPosition}-${wordPos.endPosition})")
                    val wordResults = repository.search(word, limit = 10)
                    if (wordResults.isNotEmpty()) {
                        Log.d(TAG, "Found ${wordResults.size} dictionary results for word '$word'")
                        val filteredResults = wordResults.filter { result ->
                            val resultWord = result.kanji ?: result.reading
                            val isInText = actualWordsInText.contains(resultWord)
                            if (!isInText) {
                                Log.d(TAG, "Filtering out '$resultWord' (kanji='${result.kanji}', reading='${result.reading}') - not found in paragraph text")
                                Log.d(TAG, "actualWordsInText contains: ${actualWordsInText.joinToString(", ")}")
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
        _searchQuery.value = "" // Immediately set StateFlow to empty to prevent ghost results
        _searchResults.value = emptyList()
        allSearchResults = mutableListOf()
        currentDisplayedCount = 0
        _uiState.value = UiState.Empty
    }

    fun loadMoreResults() {
        if (currentDisplayedCount < allSearchResults.size) {
            val nextBatch = allSearchResults.take(currentDisplayedCount + PAGE_SIZE)
            currentDisplayedCount = nextBatch.size
            _searchResults.value = nextBatch
            _uiState.value = UiState.Results(currentDisplayedCount, allSearchResults.size)
            Log.d(TAG, "Loaded more cached results: showing $currentDisplayedCount of ${allSearchResults.size}")
            return
        }
        if (!hasMoreInDatabase) {
            Log.d(TAG, "No more results available in database")
            return
        }
        fetchMoreFromDatabase()
    }

    fun hasMoreResults(): Boolean {
        return false // No pagination - show all results at once
    }

    private fun fetchMoreFromDatabase() {
        if (currentQuery.isEmpty()) return
        if (searchJob?.isActive == true) {
            Log.d(TAG, "Search already in progress, skipping")
            return
        }
        _uiState.value = UiState.LoadingMore
        searchJob = viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    val moreResults: List<WordResult> = try {
                        val results = repository.search(currentQuery, PAGE_SIZE, currentSearchOffset)
                        results
                    } catch (e: Exception) {
                        val results = repository.search(currentQuery, PAGE_SIZE, currentSearchOffset)
                        results
                    }
                    if (moreResults.isNotEmpty()) {
                        val groupedMoreResults: List<UnifiedDictionaryEntry> = moreResults.map { wordResult ->
                            val pitchAccents: List<PitchAccent> = emptyList()
                            UnifiedDictionaryEntry(
                                primaryForm = wordResult.kanji ?: wordResult.reading,
                                primaryReading = wordResult.reading,
                                meanings = wordResult.meanings,
                                primaryTags = emptyList<String>(),
                                variants = emptyList<VariantInfo>(),
                                isCommon = wordResult.isCommon,
                                verbType = null,
                                conjugationInfo = null,
                                frequency = if (wordResult.frequency > 0) wordResult.frequency else null,
                                isDeinflectedResult = wordResult.isDeinflectedValidConjugation,
                                pitchAccents = pitchAccents.takeIf { it.isNotEmpty() }
                            )
                        }
                        val newEntries = groupedMoreResults.filter { newEntry ->
                            !allSearchResults.any { existing -> existing.primaryForm == newEntry.primaryForm }
                        }
                        allSearchResults.addAll(newEntries)
                        currentSearchOffset += PAGE_SIZE
                        hasMoreInDatabase = moreResults.size >= PAGE_SIZE
                        val nextBatch = allSearchResults.take(currentDisplayedCount + PAGE_SIZE)
                        currentDisplayedCount = nextBatch.size
                        _searchResults.value = nextBatch
                        _uiState.value = UiState.Results(currentDisplayedCount, allSearchResults.size)
                    } else {
                        hasMoreInDatabase = false
                        _uiState.value = UiState.Results(currentDisplayedCount, allSearchResults.size)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                    } else {
                        _uiState.value = UiState.Results(currentDisplayedCount, allSearchResults.size)
                    }
                }
            }
        }
    }

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
}