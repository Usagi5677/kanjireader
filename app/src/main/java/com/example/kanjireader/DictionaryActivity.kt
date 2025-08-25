package com.example.kanjireader

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
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
            val currentQuery = binding.searchEditText.text.toString()
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
                    showNoResults(binding.searchEditText.text.toString())
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
                
                Log.d(TAG, "Dictionary ready! FTS5 search system active")
                
                // FTS5 is now always active
                if (false) {
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
        binding.searchEditText.apply {
            hint = "Search..."
            requestFocus()
            
            // Block Enter key completely - prevent new lines
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN) {
                    // Consume the Enter key event completely
                    true
                } else {
                    false
                }
            }
            
            // Also handle IME action
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT) {
                    // Hide keyboard on search action
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(windowToken, 0)
                    true
                } else {
                    false
                }
            }

            addTextChangedListener(object : TextWatcher {
                private var isProcessingNewline = false
                private var wasNewlineOnly = false
                
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Skip if we're already processing a newline
                    if (isProcessingNewline) return
                    
                    val text = s?.toString() ?: ""
                    
                    // Remove any newline characters that somehow got through
                    if (text.contains("\n")) {
                        isProcessingNewline = true
                        val cleanedText = if (text.trim().isEmpty()) {
                            wasNewlineOnly = true // Mark that this was just Enter key
                            "" // If only newlines, clear everything
                        } else {
                            wasNewlineOnly = false
                            text.replace("\n", " ") // Replace newlines with spaces for paragraphs
                        }
                        setText(cleanedText)
                        setSelection(cleanedText.length)
                        // Update clear button visibility after cleaning
                        binding.clearButton.visibility = if (cleanedText.isNotEmpty()) View.VISIBLE else View.GONE
                        isProcessingNewline = false
                        // Don't return - let the height adjustment happen in afterTextChanged
                    } else {
                        wasNewlineOnly = false
                        // Update clear button visibility immediately based on actual text content
                        binding.clearButton.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                    // Skip if we're processing a newline replacement
                    if (isProcessingNewline) return
                    
                    // Skip search if this was just an Enter key press on empty text
                    if (wasNewlineOnly) {
                        wasNewlineOnly = false
                        return
                    }
                    
                    val newText = s?.toString() ?: ""
                    
                    Log.d(TAG, "=== TEXT CHANGE === '$newText'")

                    // --- REMOVED: Manual delays and filtering logic from Activity
                    // This logic is redundant and can cause ANRs.
                    // The ViewModel's channel/debounce handles this correctly.
                    // searchJob?.cancel()
                    // searchJob = lifecycleScope.launch { delay(300) ... }
                    // ---
                    
                    // Force layout to calculate line count for long text
                    if (newText.length > 20) {
                        binding.searchEditText.requestLayout()
                    }
                    
                    // Check line count after layout update and adjust height
                    binding.searchEditText.post {
                        val lineCount = binding.searchEditText.lineCount
                        val density = resources.displayMetrics.density
                        
                        // Calculate new height based on line count
                        val newHeight = when {
                            lineCount <= 1 -> (48 * density).toInt()
                            lineCount == 2 -> (72 * density).toInt()
                            lineCount == 3 -> (96 * density).toInt()
                            else -> (120 * density).toInt() // Max 4 lines
                        }
                        
                        // Update CardView height
                        val layoutParams = binding.searchCardView.layoutParams
                        if (layoutParams.height != newHeight) {
                            layoutParams.height = newHeight
                            binding.searchCardView.layoutParams = layoutParams
                            binding.searchCardView.requestLayout() // Force layout update
                        }
                        
                        // Show word navigation button for wrapped text or long text
                        binding.wordNavigationButton.visibility = 
                            if (lineCount > 1 || newText.length > 30) View.VISIBLE else View.GONE
                    }

                    // This is the core logic: Always send the raw query to the ViewModel.
                    // The ViewModel is responsible for deciding when to perform the search.
                    if (newText.isEmpty()) {
                        Log.d(TAG, "Empty query, clearing search")
                        viewModel.clearSearch()
                        binding.searchHelpText.visibility = View.VISIBLE
                    } else {
                        binding.searchHelpText.visibility = View.GONE
                        viewModel.search(newText)
                    }
                }
            })
        }
        
        // Setup clear button
        binding.clearButton.setOnClickListener {
            binding.searchEditText.text?.clear()
            binding.searchEditText.requestFocus()
        }
        
        // Setup word navigation button
        binding.wordNavigationButton.setOnClickListener {
            openWordNavigationView()
        }
    }

    private fun setupRadicalSearchButton() {
        binding.radicalSearchButton.setOnClickListener {
            val intent = Intent(this, RadicalSearchActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun openWordNavigationView() {
        val currentQuery = binding.searchEditText.text.toString()
        
        if (currentQuery.trim().isEmpty()) {
            Toast.makeText(this, "Enter text first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check if this is long text (wrapped lines or length > 30)
        val lineCount = binding.searchEditText.lineCount
        val isLongText = lineCount > 1 || currentQuery.length > 30
        
        if (!isLongText) {
            Toast.makeText(this, "Word navigation is for longer texts", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Launch the dictionary text reader activity
        val intent = Intent(this, DictionaryTextReaderActivity::class.java)
        intent.putExtra(DictionaryTextReaderActivity.EXTRA_PARAGRAPH_TEXT, currentQuery)
        startActivity(intent)
    }

    private fun handleAppendToSearchIntent() {
        val appendText = intent.getStringExtra("append_to_search")
        if (!appendText.isNullOrEmpty()) {
            // Get current search text
            val currentQuery = binding.searchEditText.text.toString()
            
            // Append the new text
            val newQuery = currentQuery + appendText
            
            // Set the new query and trigger search
            binding.searchEditText.setText(newQuery)
            
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
}