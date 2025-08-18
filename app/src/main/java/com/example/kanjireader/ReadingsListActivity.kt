package com.example.kanjireader

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ImageButton
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.example.kanjireader.viewmodel.WordListViewModel
import com.example.kanjireader.viewmodel.WordListSortOrder
import com.example.kanjireader.ui.CreateListDialog
import com.example.kanjireader.ui.WordListManagementAdapter
import com.example.kanjireader.ui.RenameListDialog
import androidx.appcompat.app.AlertDialog
import android.text.SpannableString
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.ContextCompat

class ReadingsListActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var noResultsLayout: LinearLayout
    private lateinit var createListButton: ImageButton
    private lateinit var searchView: SearchView
    private lateinit var sortMenuButton: ImageButton

    private lateinit var listAdapter: WordListManagementAdapter
    private val wordListViewModel: WordListViewModel by viewModels()

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    
    private var currentSearchQuery = ""
    private var allWordLists: List<com.example.kanjireader.database.WordListEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_readings_list)

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        setupRecyclerView()
        setupCreateButton()
        setupSearchView()
        setupSortMenu()
        setupObservers()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.kanjiRecyclerView) // Reusing the same ID for now
        noResultsLayout = findViewById(R.id.noResultsLayout)
        createListButton = findViewById(R.id.btnCreateList)
        searchView = findViewById(R.id.searchView)
        sortMenuButton = findViewById(R.id.btnSortMenu)
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        // Change to hamburger menu
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)
        supportActionBar?.title = "Manage Lists"

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
                    // Already in manage lists, just close drawer
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
        listAdapter = WordListManagementAdapter(
            onListClick = { wordList ->
                val intent = Intent(this, WordListDetailActivity::class.java).apply {
                    putExtra(WordListDetailActivity.EXTRA_LIST_ID, wordList.listId)
                    putExtra(WordListDetailActivity.EXTRA_LIST_NAME, wordList.name)
                }
                startActivity(intent)
            },
            onListLongPress = { wordList ->
                showDeleteListDialog(wordList)
            },
            onEditClick = { wordList ->
                showRenameListDialog(wordList)
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ReadingsListActivity)
            adapter = listAdapter
        }
    }

    private fun setupCreateButton() {
        createListButton.setOnClickListener {
            showCreateListDialog()
        }
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText?.trim() ?: ""
                filterLists(currentSearchQuery)
                return true
            }
        })
    }

    private fun setupSortMenu() {
        sortMenuButton.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.menu_sort_lists, popup.menu)
            
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.sort_name_asc -> {
                        wordListViewModel.setSortOrder(WordListSortOrder.NAME_ASC)
                        true
                    }
                    R.id.sort_name_desc -> {
                        wordListViewModel.setSortOrder(WordListSortOrder.NAME_DESC)
                        true
                    }
                    R.id.sort_newest_first -> {
                        wordListViewModel.setSortOrder(WordListSortOrder.NEWEST_FIRST)
                        true
                    }
                    R.id.sort_oldest_first -> {
                        wordListViewModel.setSortOrder(WordListSortOrder.OLDEST_FIRST)
                        true
                    }
                    R.id.sort_most_words -> {
                        wordListViewModel.setSortOrder(WordListSortOrder.MOST_WORDS)
                        true
                    }
                    R.id.sort_fewest_words -> {
                        wordListViewModel.setSortOrder(WordListSortOrder.FEWEST_WORDS)
                        true
                    }
                    else -> false
                }
            }
            
            popup.show()
        }
    }

    private fun setupObservers() {
        wordListViewModel.wordLists.observe(this) { wordLists ->
            allWordLists = wordLists
            filterLists(currentSearchQuery)
        }
        
        wordListViewModel.operationError.observe(this) { errorMessage ->
            errorMessage?.let {
                android.widget.Toast.makeText(this, it, android.widget.Toast.LENGTH_LONG).show()
                wordListViewModel.clearMessages()
            }
        }
        
        wordListViewModel.operationSuccess.observe(this) { successMessage ->
            successMessage?.let {
                android.widget.Toast.makeText(this, it, android.widget.Toast.LENGTH_SHORT).show()
                wordListViewModel.clearMessages()
            }
        }
    }
    
    private fun filterLists(query: String) {
        val filteredLists = if (query.isEmpty()) {
            allWordLists
        } else {
            allWordLists.filter { wordList ->
                wordList.name.contains(query, ignoreCase = true)
            }
        }
        
        if (filteredLists.isNotEmpty()) {
            showResults(filteredLists, query)
        } else {
            showNoResults()
        }
    }

    private fun showCreateListDialog() {
        val createListDialog = CreateListDialog(this) { listName ->
            wordListViewModel.createWordList(listName)
        }
        createListDialog.show()
    }

    private fun showRenameListDialog(wordList: com.example.kanjireader.database.WordListEntity) {
        val renameDialog = RenameListDialog(this, wordList.name) { newName ->
            wordListViewModel.renameWordList(wordList.listId, newName)
        }
        renameDialog.show()
    }

    private fun showResults(wordLists: List<com.example.kanjireader.database.WordListEntity>, searchQuery: String? = null) {
        recyclerView.visibility = View.VISIBLE
        noResultsLayout.visibility = View.GONE

        // Use provided search query or current search query
        val queryToHighlight = if (!searchQuery.isNullOrEmpty()) searchQuery else null
        listAdapter.updateWithSearch(wordLists, queryToHighlight)
        
        // Scroll to top after the list has been updated - use post to ensure layout is complete
        recyclerView.post {
            (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
        }
        updateTitle(wordLists.size)
    }

    private fun showNoResults() {
        recyclerView.visibility = View.GONE
        noResultsLayout.visibility = View.VISIBLE
        updateTitle(0)
    }

    private fun updateTitle(count: Int) {
        supportActionBar?.title = "Manage Lists ($count)"
    }

    private fun showDeleteListDialog(wordList: com.example.kanjireader.database.WordListEntity) {
        val message = "Are you sure you want to delete the list '${wordList.name}'?\n\nThis will remove ${wordList.wordCount} words from this list."
        val spannableMessage = SpannableString(message)
        
        // Make the list name bold (just the name, not the quotes)
        val nameStart = message.indexOf(wordList.name)
        val nameEnd = nameStart + wordList.name.length
        if (nameStart >= 0) {
            spannableMessage.setSpan(StyleSpan(Typeface.BOLD), nameStart, nameEnd, 0)
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Delete List")
            .setMessage(spannableMessage)
            .setPositiveButton("Delete") { _, _ ->
                wordListViewModel.deleteWordList(wordList)
            }
            .setNegativeButton("Cancel", null)
            .create()
            
        dialog.show()
        
        // Make delete button red
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(this, android.R.color.holo_red_dark)
        )
    }
}