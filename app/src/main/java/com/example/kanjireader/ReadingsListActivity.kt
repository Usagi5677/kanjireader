package com.example.kanjireader

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView

class ReadingsListActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var kanjiRecyclerView: RecyclerView
    private lateinit var noResultsLayout: LinearLayout
    private lateinit var kanjiOnlyToggle: MaterialButton
    private lateinit var searchView: SearchView

    private lateinit var resultsAdapter: KanjiResultsAdapter
    private var isKanjiOnlyMode = false
    private var currentSearchQuery = ""

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    private val updateListener = { updateWordList() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_readings_list)

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        setupRecyclerView()
        setupKanjiToggle()
        setupSearchView()
        updateWordList()

        // Listen for changes in the readings list
        ReadingsListManager.addListener(updateListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        ReadingsListManager.removeListener(updateListener)
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        kanjiRecyclerView = findViewById(R.id.kanjiRecyclerView)
        noResultsLayout = findViewById(R.id.noResultsLayout)
        kanjiOnlyToggle = findViewById(R.id.kanjiOnlyToggle)
        searchView = findViewById(R.id.searchView)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        // Change to hamburger menu
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)
        supportActionBar?.title = "Saved Words"

        // Update to open drawer instead of going back
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupNavigationDrawer() {
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_camera -> {
                    // Clear back to camera (MainActivity)
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_gallery -> {
                    // Navigate to MainActivity for gallery selection
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    intent.putExtra("action", "open_gallery")
                    startActivity(intent)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_saved_words -> {
                    // Already in saved words, just close drawer
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.nav_dictionary -> {
                    val intent = Intent(this, DictionaryActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(intent)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
            }
            true
        }
        navigationView.menu.findItem(R.id.nav_saved_words)?.isChecked = true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        resultsAdapter = KanjiResultsAdapter { wordResult: WordResult ->
            // This callback is still required by the constructor but not used
            // since we handle clicks directly in the adapter
        }

        kanjiRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ReadingsListActivity)
            adapter = resultsAdapter
        }
    }

    private fun setupKanjiToggle() {
        kanjiOnlyToggle.setOnClickListener {
            isKanjiOnlyMode = !isKanjiOnlyMode
            updateToggleAppearance()
            updateWordList()
        }
        updateToggleAppearance()
    }

    private fun updateToggleAppearance() {
        if (isKanjiOnlyMode) {
            // Enabled state - filled button
            kanjiOnlyToggle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.teal_700)
            )
            kanjiOnlyToggle.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            kanjiOnlyToggle.strokeWidth = 0
        } else {
            // Disabled state - outlined button
            kanjiOnlyToggle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.transparent)
            )
            kanjiOnlyToggle.setTextColor(ContextCompat.getColor(this, R.color.teal_700))
            kanjiOnlyToggle.strokeWidth = 2
        }
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText ?: ""
                updateWordList()
                return true
            }
        })
    }

    private fun updateWordList() {
        val words = if (currentSearchQuery.isNotBlank()) {
            ReadingsListManager.searchWords(currentSearchQuery)
        } else if (isKanjiOnlyMode) {
            ReadingsListManager.getKanjiOnlyWords()
        } else {
            ReadingsListManager.getAllWords()
        }

        if (words.isNotEmpty()) {
            showResults(words)
            updateTitle(words.size)
        } else {
            showNoResults()
            updateTitle(0)
        }
    }

    private fun showResults(results: List<WordResult>) {
        kanjiRecyclerView.visibility = View.VISIBLE
        noResultsLayout.visibility = View.GONE

        resultsAdapter.updateResults(results)
    }

    private fun showNoResults() {
        kanjiRecyclerView.visibility = View.GONE
        noResultsLayout.visibility = View.VISIBLE
    }

    private fun updateTitle(count: Int) {
        val totalWords = ReadingsListManager.getWordCount()
        val kanjiWords = ReadingsListManager.getKanjiWordCount()

        supportActionBar?.title = when {
            currentSearchQuery.isNotBlank() -> "Search: $count results"
            isKanjiOnlyMode -> "Kanji Words ($kanjiWords)"
            else -> "Saved Words ($totalWords)"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.readings_list_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_all -> {
                showClearAllDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear All Words")
            .setMessage("Are you sure you want to delete all saved words? This cannot be undone.")
            .setPositiveButton("Clear All") { _, _ ->
                ReadingsListManager.clearAll()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}