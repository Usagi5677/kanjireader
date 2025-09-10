package com.example.kanjireader

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch

class RadicalSearchActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "RadicalSearchActivity"
    }

    // UI Components
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var clearButton: Button
    private lateinit var drawButton: Button
    private lateinit var kanjiResultsRecyclerView: RecyclerView
    private lateinit var radicalGridRecyclerView: RecyclerView
    private lateinit var resultsCounter: TextView

    // Adapters
    private lateinit var radicalGridAdapter: RadicalGridAdapter
    private lateinit var kanjiCardAdapter: KanjiCardAdapter

    // Data
    private val selectedRadicals = mutableSetOf<String>()
    private var allRadicalsByStroke = mapOf<Int, List<String>>()
    private var currentKanjiResults = listOf<String>()
    private var enabledRadicals = setOf<String>()

    // Database
    private lateinit var database: DictionaryDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_radical_search)

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        setupRecyclerViews()
        loadInitialData()
    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        toolbar = findViewById(R.id.toolbar)
        clearButton = findViewById(R.id.clearButton)
        drawButton = findViewById(R.id.drawButton)
        kanjiResultsRecyclerView = findViewById(R.id.kanjiResultsRecyclerView)
        radicalGridRecyclerView = findViewById(R.id.radicalGridRecyclerView)
        resultsCounter = findViewById(R.id.resultsCounter)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)
        supportActionBar?.title = "Kanji search"

        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        clearButton.setOnClickListener {
            clearSelection()
        }

        drawButton.setOnClickListener {
            Log.d(TAG, "Draw button clicked - launching KanjiDrawingActivity")
            val intent = Intent(this, KanjiDrawingActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupNavigationDrawer() {
        // Set navigation view colors programmatically
        val navColors = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked), // selected
                intArrayOf() // unselected
            ),
            intArrayOf(
                ContextCompat.getColor(this, R.color.teal_900), // selected
                ContextCompat.getColor(this, R.color.text_primary_color) // unselected
            )
        )
        navigationView.itemTextColor = navColors
        navigationView.itemIconTintList = navColors
        
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_camera -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_gallery -> {
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    intent.putExtra("action", "open_gallery")
                    startActivity(intent)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_saved_words -> {
                    val intent = Intent(this, ReadingsListActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_dictionary -> {
                    val intent = Intent(this, DictionaryActivity::class.java)
                    startActivity(intent)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_settings -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
            true
        }
    }
    
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun setupRecyclerViews() {
        // Setup kanji results with card layout (vertical scrolling)
        kanjiCardAdapter = KanjiCardAdapter { kanji ->
            onKanjiClicked(kanji)
        }
        kanjiResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@RadicalSearchActivity)
            adapter = kanjiCardAdapter
        }

        // Setup radical selection grid (vertical scrolling)
        radicalGridAdapter = RadicalGridAdapter { radical ->
            onRadicalClicked(radical)
        }
        radicalGridRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@RadicalSearchActivity)
            adapter = radicalGridAdapter
        }
    }

    private fun loadInitialData() {
        database = DictionaryDatabase.getInstance(this)
        
        lifecycleScope.launch {
            try {
                // Load all radicals grouped by stroke count
                allRadicalsByStroke = database.getAllRadicalsByStrokeCount()
                Log.d(TAG, "Loaded ${allRadicalsByStroke.size} stroke count groups")
                
                
                // Initially all radicals are enabled
                enabledRadicals = allRadicalsByStroke.values.flatten().toSet()
                
                // Update radical grid with initial data
                updateRadicalGrid()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading initial data", e)
            }
        }
    }

    private fun onRadicalClicked(radical: String) {
        Log.d(TAG, "Radical clicked: $radical")
        
        if (radical == "⻂") {
            Log.d(TAG, "⻂ radical clicked successfully!")
        }
        
        if (selectedRadicals.contains(radical)) {
            // Deselect radical
            selectedRadicals.remove(radical)
            Log.d(TAG, "Deselected radical: $radical")
        } else {
            // Select radical
            selectedRadicals.add(radical)
            Log.d(TAG, "Selected radical: $radical")
        }
        
        updateSearchResults()
    }

    private fun onKanjiClicked(kanji: String) {
        Log.d(TAG, "Kanji clicked: $kanji - returning to dictionary search")
        
        // Return to DictionaryActivity and append kanji to search
        val intent = Intent(this, DictionaryActivity::class.java).apply {
            putExtra("append_to_search", kanji)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun updateSearchResults() {
        lifecycleScope.launch {
            try {
                if (selectedRadicals.isEmpty()) {
                    // No radicals selected - clear results and enable all radicals
                    currentKanjiResults = emptyList()
                    enabledRadicals = allRadicalsByStroke.values.flatten().toSet()
                } else {
                    // Find kanji that contain ALL selected radicals
                    currentKanjiResults = database.getKanjiForMultipleRadicals(selectedRadicals.toList())
                    Log.d(TAG, "Found ${currentKanjiResults.size} kanji for radicals: $selectedRadicals")
                    
                    // Find radicals that would produce results when combined with selected ones
                    // This returns all radicals that appear in kanji containing ALL selected radicals
                    enabledRadicals = database.getValidRadicalsForCombination(selectedRadicals)
                    Log.d(TAG, "Enabled ${enabledRadicals.size} radicals that would produce results")
                    
                }
                
                // Update both grids
                updateKanjiGrid()
                updateRadicalGrid()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error updating search results", e)
            }
        }
    }

    private fun updateKanjiGrid() {
        lifecycleScope.launch {
            val kanjiCards = database.convertToKanjiCardInfo(currentKanjiResults)
            kanjiCardAdapter.updateData(kanjiCards)
            
            // Auto-scroll to top when results change (especially after radical selection)
            if (kanjiCards.isNotEmpty()) {
                kanjiResultsRecyclerView.scrollToPosition(0)
            }
            
            // Update the results counter - show only if there are results
            if (kanjiCards.isNotEmpty()) {
                resultsCounter.visibility = android.view.View.VISIBLE
                resultsCounter.text = kanjiCards.size.toString()
            } else {
                resultsCounter.visibility = android.view.View.GONE
            }
            
            Log.d(TAG, "Updated kanji grid with ${kanjiCards.size} results (fast sorting)")
        }
    }

    private fun updateRadicalGrid() {
        radicalGridAdapter.updateData(
            radicalsByStroke = allRadicalsByStroke,
            selectedRadicals = selectedRadicals,
            enabledRadicals = enabledRadicals
        )
        Log.d(TAG, "Updated radical grid with ${selectedRadicals.size} selected, ${enabledRadicals.size} enabled")
    }

    private fun clearSelection() {
        Log.d(TAG, "Clearing all selections")
        selectedRadicals.clear()
        updateSearchResults()
    }
}