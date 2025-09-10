package com.example.kanjireader

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.launch

class PhrasesTabFragment : Fragment() {

    companion object {
        private const val ARG_WORD = "word"
        private const val ARG_INITIAL_WORD = "initial_word"
        private const val ARG_LAZY_LOAD = "lazy_load"

        fun newInstance(word: String, lazyLoad: Boolean = false): PhrasesTabFragment {
            return PhrasesTabFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WORD, word)
                    putString(ARG_INITIAL_WORD, word)
                    putBoolean(ARG_LAZY_LOAD, lazyLoad)
                }
            }
        }
    }

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var phrasesRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: View
    private lateinit var errorStateLayout: View
    private lateinit var progressBar: ProgressBar
    private lateinit var errorMessageText: TextView
    
    private lateinit var phrasesAdapter: PhrasesAdapter
    private lateinit var skeletonAdapter: SkeletonPhrasesAdapter
    private val tatoebaService by lazy { TatoebaApiService(requireContext()) }
    
    // Keep track of current sentences and word for refresh
    private var currentSentences: List<ExampleSentence> = emptyList()
    private var currentWord: String = ""
    var hasLoadedData = false
        private set
    
    // Track if we're in the middle of a user-initiated search from Forms tab
    private var isUserInitiatedSearch = false
    
    // Track the last requested search to handle delayed searches properly
    private var lastRequestedSearch: String? = null
    private var lastRequestedSearchTime: Long = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_phrases_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        setupRecyclerView()
        
        val word = arguments?.getString(ARG_WORD) ?: ""
        val lazyLoad = arguments?.getBoolean(ARG_LAZY_LOAD, false) ?: false
        
        
        if (!lazyLoad && word.isNotEmpty()) {
            searchSentences(word)
        } else {
            // For lazy load, show empty state until explicitly searched
            showEmptyState()
        }
    }
    
    private fun initializeViews(view: View) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        phrasesRecyclerView = view.findViewById(R.id.phrasesRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        errorStateLayout = view.findViewById(R.id.errorStateLayout)
        progressBar = view.findViewById(R.id.progressBar)
        errorMessageText = view.findViewById(R.id.errorMessageText)
        
        // Skeleton will be implemented differently
        
        // Setup pull-to-refresh
        setupSwipeRefresh()
    }
    
    private fun setupRecyclerView() {
        phrasesAdapter = PhrasesAdapter { sentence ->
            onPhraseCardClicked(sentence)
        }
        skeletonAdapter = SkeletonPhrasesAdapter(5)
        phrasesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = phrasesAdapter
        }
    }
    
    /**
     * Public method to search for sentences with a specific word
     * This can be called from other fragments/activities
     */
    fun searchSentences(word: String, force: Boolean = false, fromFormsTab: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        
        // If user initiated search from Forms tab, mark it and set high priority
        if (fromFormsTab) {
            isUserInitiatedSearch = true
            lastRequestedSearch = word
            lastRequestedSearchTime = currentTime
        } else {
            // For automatic searches, check if there's a recent user-initiated search
            if (isUserInitiatedSearch && (currentTime - lastRequestedSearchTime) < 1000) {
                return
            }
            
            // If there's a pending user search that's different from this automatic search, ignore this
            if (lastRequestedSearch != null && lastRequestedSearch != word && (currentTime - lastRequestedSearchTime) < 2000) {
                return
            }
        }
        
        // If we've already loaded this word and not forcing, skip
        if (currentWord == word && hasLoadedData && !force) {
            return
        }
        
        currentWord = word
        
        // Update the argument so getCurrentWord() returns the correct value
        arguments?.putString(ARG_WORD, word)
        
        if (!::phrasesAdapter.isInitialized) {
            // Store the word and search when view is ready
            return
        }
        
        // Clear current results and show loading immediately to prevent showing old data
        currentSentences = emptyList()
        showLoading()
        hasLoadedData = true
        
        // Perform search without calling showLoading again since we already did it
        performSearchInternal(word, false)
    }
    
    /**
     * Perform the actual search with option to append results
     */
    private fun performSearch(word: String, isRefresh: Boolean = false) {
        if (!isRefresh) {
            showLoading()
        }
        performSearchInternal(word, isRefresh)
    }
    
    /**
     * Internal search method without loading state management
     */
    private fun performSearchInternal(word: String, isRefresh: Boolean = false) {
        Log.d("PhrasesTabFragment", "performSearchInternal called with word: $word, isRefresh: $isRefresh")
        lifecycleScope.launch {
            try {
                // Try both search methods to get the best results
                var sentences = tatoebaService.searchSentencesByWord(word, 10)
                Log.d("PhrasesTabFragment", "searchSentencesByWord returned ${sentences.size} results")
                
                // If we don't get enough results, try the regular search
                if (sentences.size < 5) {
                    val moreSentences = tatoebaService.searchSentences(word, 10 - sentences.size)
                    Log.d("PhrasesTabFragment", "searchSentences returned ${moreSentences.size} additional results")
                    sentences = (sentences + moreSentences).distinctBy { it.japanese }
                }
                
                if (isRefresh) {
                    // For refresh, get new sentences and combine with existing ones
                    val newSentences = sentences.filter { newSentence ->
                        currentSentences.none { existing -> existing.japanese == newSentence.japanese }
                    }
                    
                    if (newSentences.isNotEmpty()) {
                        currentSentences = newSentences + currentSentences
                        showSentences(currentSentences)
                    } else {
                        // No new sentences found, just update UI
                        swipeRefreshLayout.isRefreshing = false
                    }
                } else {
                    // Initial search
                    currentSentences = sentences
                    Log.d("PhrasesTabFragment", "Total sentences found: ${sentences.size}")
                    if (sentences.isNotEmpty()) {
                        Log.d("PhrasesTabFragment", "About to call showSentences with ${sentences.size} sentences")
                        showSentences(sentences)
                        Log.d("PhrasesTabFragment", "showSentences call completed")
                    } else {
                        Log.d("PhrasesTabFragment", "No sentences found, showing empty state")
                        showEmptyState()
                    }
                }
                
                // Clear the user-initiated flag and search tracking after successful search
                isUserInitiatedSearch = false
                lastRequestedSearch = null
                
            } catch (e: Exception) {
                Log.e("PhrasesTabFragment", "Error searching sentences", e)
                // Clear the flag and search tracking on error too
                isUserInitiatedSearch = false
                lastRequestedSearch = null
                
                if (isRefresh) {
                    swipeRefreshLayout.isRefreshing = false
                    // Show a brief error message for refresh failure
                } else {
                    showError(e.message ?: "Unknown error occurred")
                }
            }
        }
    }
    
    private fun showLoading() {
        Log.d("PhrasesTabFragment", "showLoading called")
        progressBar.visibility = View.GONE
        phrasesRecyclerView.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
        errorStateLayout.visibility = View.GONE
        
        // Show skeleton adapter while loading
        phrasesRecyclerView.adapter = skeletonAdapter
    }
    
    private fun showSentences(sentences: List<ExampleSentence>) {
        Log.d("PhrasesTabFragment", "showSentences called with ${sentences.size} sentences")
        progressBar.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false
        emptyStateLayout.visibility = View.GONE
        errorStateLayout.visibility = View.GONE
        
        // Stop skeleton animations and switch to real adapter
        skeletonAdapter.stopAllAnimations()
        phrasesRecyclerView.adapter = phrasesAdapter
        phrasesAdapter.updateSentences(sentences)
        
        // Ensure RecyclerView is visible
        phrasesRecyclerView.visibility = View.VISIBLE
        
        // Force layout update
        phrasesRecyclerView.requestLayout()
        
        Log.d("PhrasesTabFragment", "RecyclerView visibility: ${phrasesRecyclerView.visibility}, item count: ${phrasesAdapter.itemCount}")
        Log.d("PhrasesTabFragment", "RecyclerView height: ${phrasesRecyclerView.height}, width: ${phrasesRecyclerView.width}")
        Log.d("PhrasesTabFragment", "SwipeRefreshLayout visibility: ${swipeRefreshLayout.visibility}")
    }
    
    private fun showEmptyState() {
        Log.d("PhrasesTabFragment", "showEmptyState called")
        progressBar.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false
        phrasesRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
        errorStateLayout.visibility = View.GONE
        
        // Stop skeleton animations if showing empty state
        skeletonAdapter.stopAllAnimations()
        phrasesRecyclerView.adapter = phrasesAdapter
    }
    
    private fun showError(message: String) {
        Log.e("PhrasesTabFragment", "showError called with message: $message")
        progressBar.visibility = View.GONE
        swipeRefreshLayout.isRefreshing = false
        phrasesRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE
        errorStateLayout.visibility = View.VISIBLE
        
        // Stop skeleton animations if showing error
        skeletonAdapter.stopAllAnimations()
        phrasesRecyclerView.adapter = phrasesAdapter
        
        // Show user-friendly error message instead of technical details
        errorMessageText.text = "Unable to load example sentences. Please try again later."
    }
    
    /**
     * Get the current word being displayed
     */
    fun getCurrentWord(): String {
        return arguments?.getString(ARG_WORD) ?: ""
    }
    
    /**
     * Check if this fragment is showing results for the initial word
     * (vs. a conjugated form clicked from the Forms tab)
     */
    fun isShowingInitialWord(): Boolean {
        val currentWord = arguments?.getString(ARG_WORD) ?: ""
        val initialWord = arguments?.getString(ARG_INITIAL_WORD) ?: ""
        return currentWord == initialWord
    }
    
    /**
     * Handle phrase card clicks - placeholder for future functionality
     */
    private fun onPhraseCardClicked(sentence: ExampleSentence) {
        Log.d("PhrasesTabFragment", "Phrase card clicked: ${sentence.japanese}")
        
        // TODO: Add your card click functionality here
        // Examples of what you could do:
        // 1. Launch a detail activity
        // 2. Show a bottom sheet with more information  
        // 3. Perform sentence analysis
        // 4. Navigate to a different screen
        // 5. Copy text to clipboard
        // 6. Add to favorites
        // etc.
        
        // For now, just log the click - no skeleton loading needed
    }

    /**
     * Setup pull-to-refresh functionality
     */
    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(
            R.color.teal_500,
            R.color.teal_600,
            R.color.teal_700
        )
        
        swipeRefreshLayout.setOnRefreshListener {
            if (currentWord.isNotEmpty()) {
                performSearch(currentWord, isRefresh = true)
            } else {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }
}