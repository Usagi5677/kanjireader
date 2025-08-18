package com.example.kanjireader

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kanjireader.viewmodel.WordListViewModel
import com.example.kanjireader.database.SavedWordEntity

class WordListDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LIST_ID = "list_id"
        const val EXTRA_LIST_NAME = "list_name"
    }

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var noResultsLayout: LinearLayout
    private lateinit var searchView: SearchView

    private lateinit var wordsAdapter: UnifiedDictionaryAdapter
    private val wordListViewModel: WordListViewModel by viewModels()

    private var listId: Long = 0L
    private var listName: String = ""
    private var currentSearchQuery = ""
    private var allWords: List<SavedWordEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_list_detail)

        // Get list info from intent
        listId = intent.getLongExtra(EXTRA_LIST_ID, 0L)
        listName = intent.getStringExtra(EXTRA_LIST_NAME) ?: "Word List"

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupSearchView()
        setupObservers()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerViewWords)
        noResultsLayout = findViewById(R.id.noResultsLayout)
        searchView = findViewById(R.id.searchView)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = listName
        
        // Ensure the navigation icon is white
        toolbar.navigationIcon?.setTint(getColor(android.R.color.white))
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        wordsAdapter = UnifiedDictionaryAdapter { entry ->
            // Navigate to word detail when clicked
            val intent = Intent(this, WordDetailActivity::class.java).apply {
                putExtra("word", entry.primaryForm)
                putExtra("reading", entry.primaryReading ?: entry.primaryForm)
                putExtra("meanings", ArrayList(entry.meanings))
                putExtra("frequency", entry.frequency ?: 0)
                putExtra("isJMNEDict", entry.isJMNEDictEntry)
            }
            startActivity(intent)
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@WordListDetailActivity)
            adapter = wordsAdapter
        }
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText?.trim() ?: ""
                filterWords(currentSearchQuery)
                return true
            }
        })
    }

    private fun setupObservers() {
        wordListViewModel.getWordsInList(listId).observe(this) { words ->
            allWords = words
            filterWords(currentSearchQuery)
        }
    }
    
    private fun filterWords(query: String) {
        val filteredWords = if (query.isEmpty()) {
            allWords
        } else {
            allWords.filter { word ->
                word.kanji?.contains(query, ignoreCase = true) == true ||
                word.reading.contains(query, ignoreCase = true) ||
                word.meanings.any { it.contains(query, ignoreCase = true) }
            }
        }
        
        if (filteredWords.isNotEmpty()) {
            showResults(filteredWords)
        } else {
            showNoResults()
        }
    }

    private fun showResults(savedWords: List<SavedWordEntity>) {
        recyclerView.visibility = View.VISIBLE
        noResultsLayout.visibility = View.GONE

        // Convert SavedWordEntity to UnifiedDictionaryEntry
        val entries = savedWords.map { savedWord ->
            UnifiedDictionaryEntry(
                primaryForm = savedWord.kanji ?: savedWord.reading,
                primaryReading = if (savedWord.kanji != null) savedWord.reading else null,
                primaryTags = emptyList(), // Don't show part of speech tags to match dictionary view
                variants = emptyList(),
                meanings = savedWord.meanings,
                frequency = savedWord.frequency,
                isCommon = savedWord.isCommon,
                isJMNEDictEntry = false // SavedWords are not JMNEDict entries
            )
        }
        
        // Only pass search query if actively searching
        val queryToHighlight = if (currentSearchQuery.isNotEmpty()) currentSearchQuery else null
        wordsAdapter.updateEntries(entries, queryToHighlight)
        updateTitle(savedWords.size)
    }

    private fun showNoResults() {
        recyclerView.visibility = View.GONE
        noResultsLayout.visibility = View.VISIBLE
        updateTitle(0)
    }

    private fun updateTitle(count: Int) {
        supportActionBar?.title = "$listName ($count words)"
    }
}