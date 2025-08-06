package com.example.kanjireader

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kanjireader.DictionaryRepository
import android.content.Intent
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.kanjireader.databinding.ActivityDictionaryBinding
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat

class DictionaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDictionaryBinding
    private val viewModel: DictionaryViewModel by viewModels()

    companion object {
        private const val TAG = "DictionaryActivity"
    }

    // UI Components
    private lateinit var unifiedAdapter: UnifiedDictionaryAdapter
    private var searchJob: Job? = null
    private var isLoadingMore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDictionaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        setupRecyclerView()
        setupSearchView()
        setupRadicalSearchButton()

        // Initialize ViewModel first
        viewModel.initializeRepository()
        
        // Then setup observers
        setupViewModelObservers()

        // Show initial empty state
        showEmptyState()
        
        // Handle append to search intent
        handleAppendToSearchIntent()

    }

    private fun setupViewModelObservers() {
        // Observe search results
        viewModel.searchResults.observe(this) { results ->
            // Update the adapter when results change, pass current search query for highlighting
            val currentQuery = binding.searchView.query.toString()
            unifiedAdapter.updateEntries(results, currentQuery)
            
            // Scroll to top when new search results arrive (not during loading more)
            if (viewModel.uiState.value !is DictionaryViewModel.UiState.LoadingMore && results.isNotEmpty()) {
                binding.resultsRecyclerView.scrollToPosition(0)
            }
        }

        // Observe UI state
        viewModel.uiState.observe(this) { uiState ->
            when (uiState) {
                is DictionaryViewModel.UiState.Empty -> showEmptyState()
                is DictionaryViewModel.UiState.Loading -> showSearching()
                is DictionaryViewModel.UiState.LoadingMore -> {
                    // Keep current results visible but show loading indicator at bottom
                    val results = viewModel.searchResults.value ?: emptyList()
                    showResultsWithLoadingMore(results)
                }
                is DictionaryViewModel.UiState.Results -> {
                    val results = viewModel.searchResults.value ?: emptyList()
                    showResults(results, uiState.showingCount, uiState.totalCount)
                }
                is DictionaryViewModel.UiState.NoResults -> {
                    // We need the current query to show "no results for X"
                    showNoResults(binding.searchView.query.toString())
                }
                else -> {
                    // Handle any unexpected states gracefully
                    showEmptyState()
                }
            }
        }

        // Observe dictionary ready state
        viewModel.dictionaryReady.observe(this) { isReady ->
            if (isReady) {
                // Update search prompt if it was showing loading
                if (binding.searchPromptText.text.contains("loading")) {
                    binding.searchPromptText.text = getString(R.string.search_hint)
                }
                
                // Show DAT status for debugging
                val datStatus = viewModel.getDATSystemStatus()
                Log.d(TAG, "Dictionary ready! DAT Status: $datStatus")
                
                // Optional: Show toast if DAT is active
                if (datStatus.contains("DAT")) {
                    Toast.makeText(this, "Ultra-fast DAT search active!", Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.searchPromptText.text = getString(R.string.dictionary_loading_please_wait)
            }
        }

        // Error state observer removed - technical errors are handled silently
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
    }



    private fun initializeViews() {
        // Views are now accessed through binding
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)
        supportActionBar?.title = getString(R.string.dictionary_title)

        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupNavigationDrawer() {
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_camera -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_gallery -> {
                    // Navigate to MainActivity for gallery selection
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    intent.putExtra("action", "open_gallery")
                    startActivity(intent)
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_saved_words -> {
                    val intent = Intent(this, ReadingsListActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_dictionary -> {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
            true
        }
        binding.navigationView.menu.findItem(R.id.nav_dictionary)?.isChecked = true
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        // Update the adapter initialization to handle clicks
        unifiedAdapter = UnifiedDictionaryAdapter { unifiedEntry ->
            // Launch WordDetailActivity directly with UnifiedDictionaryEntry data
            val intent = Intent(this, WordDetailActivity::class.java)
            // For now, we'll pass basic data through intent extras
            // Later you might want to use a database ID or singleton pattern
            intent.putExtra("word", unifiedEntry.primaryForm)
            intent.putExtra("reading", unifiedEntry.primaryReading ?: unifiedEntry.primaryForm)
            intent.putExtra("meanings", ArrayList(unifiedEntry.meanings))
            intent.putExtra("frequency", unifiedEntry.frequency ?: 0)
            intent.putExtra("isJMNEDict", unifiedEntry.isJMNEDictEntry)
            startActivity(intent)
        }

        binding.resultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DictionaryActivity)
            adapter = unifiedAdapter
            
            // Add scroll listener for infinite scrolling
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                    
                    // Check if we need to load more items
                    val shouldLoadMore = !isLoadingMore && viewModel.hasMoreResults() && 
                        (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5 &&
                        firstVisibleItemPosition >= 0
                        
                    Log.d(TAG, "Scroll check: visible=$visibleItemCount, total=$totalItemCount, firstPos=$firstVisibleItemPosition, hasMore=${viewModel.hasMoreResults()}, shouldLoad=$shouldLoadMore")
                    
                    if (shouldLoadMore) {
                        // Load more when user is 5 items from the bottom
                        Log.d(TAG, "Loading more results...")
                        isLoadingMore = true
                        // Post to next frame to avoid modifying during scroll
                        recyclerView.post {
                            viewModel.loadMoreResults()
                            isLoadingMore = false
                        }
                    }
                }
            })
        }
    }


    private fun setupSearchView() {
        binding.searchView.apply {
            queryHint = getString(R.string.search_hint)
            isIconified = false
            requestFocus()

            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchJob?.cancel()
                    binding.searchHelpText.visibility = View.GONE
                    viewModel.search(query ?: "")
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    Log.d(TAG, "=== QUERY CHANGE === '$newText'")
                    searchJob?.cancel()
                    
                    if (newText.isNullOrEmpty()) {
                        Log.d(TAG, "Empty query, clearing search")
                        viewModel.clearSearch()
                        binding.searchHelpText.visibility = View.VISIBLE
                    } else {
                        binding.searchHelpText.visibility = View.GONE
                        
                        // Add minimal debouncing to avoid searching for every IME intermediate state
                        searchJob = lifecycleScope.launch {
                            delay(30) // Reduced delay - just enough to skip IME intermediates
                            
                            // Skip if it looks like an IME intermediate state or is too short
                            if (!isIMEIntermediateState(newText) && !isSuspiciousQuery(newText)) {
                                Log.d(TAG, "=== TRIGGERING SEARCH === '$newText' (not IME intermediate)")
                                viewModel.search(newText)
                            } else {
                                Log.d(TAG, "Skipping suspicious query: '$newText' (IME: ${isIMEIntermediateState(newText)}, suspicious: ${isSuspiciousQuery(newText)})")
                            }
                        }
                    }
                    return true
                }
            })
        }
    }

    private fun setupRadicalSearchButton() {
        binding.radicalSearchButton.setOnClickListener {
            val intent = Intent(this, RadicalSearchActivity::class.java)
            startActivity(intent)
        }
    }

    private fun handleAppendToSearchIntent() {
        val appendText = intent.getStringExtra("append_to_search")
        if (!appendText.isNullOrEmpty()) {
            // Get current search text
            val currentQuery = binding.searchView.query.toString()
            
            // Append the new text
            val newQuery = currentQuery + appendText
            
            // Set the new query and trigger search
            binding.searchView.setQuery(newQuery, true)
            
            // Clear the intent extra to prevent repeated appending
            intent.removeExtra("append_to_search")
        }
    }

    private fun showSearching() {
        binding.emptyStateLayout.visibility = View.GONE
        binding.noResultsLayout.visibility = View.GONE
        binding.resultsRecyclerView.visibility = View.GONE

        supportActionBar?.subtitle = getString(R.string.searching)
    }

    private fun showResults(unifiedEntries: List<UnifiedDictionaryEntry>, showingCount: Int = 0, totalCount: Int = 0) {
        binding.emptyStateLayout.visibility = View.GONE
        binding.noResultsLayout.visibility = View.GONE
        binding.resultsRecyclerView.visibility = View.VISIBLE

        // Show count in subtitle with pagination info like Takoboto
        val subtitle = if (totalCount > showingCount) {
            android.text.SpannableString("Showing $showingCount of $totalCount entries")
        } else {
            android.text.SpannableString("$totalCount entries")
        }
        
        // Make numbers bold
        val boldSpan = android.text.style.StyleSpan(android.graphics.Typeface.BOLD)
        if (totalCount > showingCount) {
            subtitle.setSpan(boldSpan, 8, 8 + showingCount.toString().length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val ofIndex = subtitle.indexOf("of ") + 3
            subtitle.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), ofIndex, ofIndex + totalCount.toString().length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            subtitle.setSpan(boldSpan, 0, totalCount.toString().length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        supportActionBar?.subtitle = subtitle
    }
    
    private fun showResultsWithLoadingMore(unifiedEntries: List<UnifiedDictionaryEntry>) {
        binding.emptyStateLayout.visibility = View.GONE
        binding.noResultsLayout.visibility = View.GONE
        binding.resultsRecyclerView.visibility = View.VISIBLE

        // Show loading indicator in subtitle
        supportActionBar?.subtitle = "Loading more results..."
    }

    private fun showNoResults(query: String) {
        binding.emptyStateLayout.visibility = View.GONE
        binding.resultsRecyclerView.visibility = View.GONE
        binding.noResultsLayout.visibility = View.VISIBLE

        // Limit the query length in the no results message
        val displayQuery = if (query.length > 30) {
            "No results found"
        } else {
            getString(R.string.no_results_for, query)
        }
        binding.noResultsText.text = displayQuery
        supportActionBar?.subtitle = null
    }

    private fun showEmptyState() {
        binding.resultsRecyclerView.visibility = View.GONE
        binding.noResultsLayout.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.searchHelpText.visibility = View.VISIBLE
        supportActionBar?.subtitle = null
    }

    private fun showSearchError(title: String, message: String) {
        // Show error in place of search results
        binding.resultsRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.noResultsLayout.visibility = View.VISIBLE
        
        binding.noResultsText.text = title
        supportActionBar?.subtitle = message
        
        // Error is now shown in the UI - no need for toast
        
        Log.w(TAG, "Search error: $title - $message")
    }
    
    /**
     * Check if the text looks like an IME intermediate state
     * These are typically single full-width Latin characters like 'ｍ' 
     */
    private fun isIMEIntermediateState(text: String): Boolean {
        if (text.length != 1) return false
        
        val char = text[0]
        // Check for full-width Latin characters (commonly used by IMEs)
        return char in '\uFF01'..'\uFF5E'
    }
    
    /**
     * Check if a query looks suspicious and should be filtered out
     * This includes very short queries that might be IME artifacts
     */
    private fun isSuspiciousQuery(text: String): Boolean {
        if (text.length > 2) return false // Allow 3+ character queries
        
        // Allow single Japanese characters
        if (text.length == 1) {
            val char = text[0]
            val isJapanese = char in '\u3040'..'\u309F' || // Hiragana
                           char in '\u30A0'..'\u30FF' || // Katakana  
                           char in '\u4E00'..'\u9FAF'    // Kanji
            if (isJapanese) return false
        }
        
        // Block single Latin characters (likely IME artifacts)
        if (text.length == 1 && text[0] in 'a'..'z') {
            Log.d(TAG, "Blocking single Latin character: '$text'")
            return true
        }
        
        return false
    }
}