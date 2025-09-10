package com.example.kanjireader

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch

class KanjiDrawingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "KanjiDrawingActivity"
    }

    // UI Components
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var clearButton: Button
    private lateinit var undoButton: Button
    private lateinit var drawingView: DrawingView
    private lateinit var kanjiResultsRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: View
    private lateinit var resultsCounter: TextView
    private lateinit var statusText: TextView

    // Components
    private lateinit var kanjiCardAdapter: KanjiCardAdapter
    private var recognitionEngine: KanjiRecognition? = null

    // Data
    private var currentPredictions = listOf<KanjiPrediction>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kanji_drawing)

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        setupRecyclerView()
        setupDrawingView()
        initializeRecognitionEngine()
        
        // Show empty state initially
        showEmptyResults()
    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        toolbar = findViewById(R.id.toolbar)
        clearButton = findViewById(R.id.clearButton)
        undoButton = findViewById(R.id.undoButton)
        drawingView = findViewById(R.id.drawingView)
        kanjiResultsRecyclerView = findViewById(R.id.kanjiResultsRecyclerView)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        resultsCounter = findViewById(R.id.resultsCounter)
        statusText = findViewById(R.id.statusText)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)
        supportActionBar?.title = "Draw Kanji"

        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        clearButton.setOnClickListener {
            clearDrawingAndResults()
        }
        
        undoButton.setOnClickListener {
            undoLastStroke()
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

    private fun setupRecyclerView() {
        kanjiCardAdapter = KanjiCardAdapter { kanji ->
            onKanjiSelected(kanji)
        }
        
        kanjiResultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@KanjiDrawingActivity)
            adapter = kanjiCardAdapter
        }
    }

    private fun setupDrawingView() {
        drawingView.onDrawingChanged = {
            updateUIForDrawingState()
        }
        
        // Auto-recognize when user finishes a stroke
        drawingView.onStrokeCompleted = {
            if (recognitionEngine?.isReady() == true) {
                performRecognition()
            }
        }
    }

    private fun initializeRecognitionEngine() {
        lifecycleScope.launch {
            try {
                recognitionEngine = KanjiRecognition(this@KanjiDrawingActivity)
                
                if (recognitionEngine?.isReady() == true) {
                    Log.d(TAG, "Recognition engine initialized successfully")
                } else {
                    Log.e(TAG, "Failed to initialize recognition engine")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing recognition engine", e)
            }
        }
    }

    private fun updateUIForDrawingState() {
        val hasDrawing = drawingView.hasDrawing()
        val canUndo = drawingView.canUndo()
        
        // Update undo button state
        undoButton.isEnabled = canUndo
        
        if (!hasDrawing) {
            clearResults()
        }
    }

    private fun performRecognition() {
        val engine = recognitionEngine
        if (engine == null || !engine.isReady()) {
            Log.w(TAG, "Recognition engine not ready")
            return
        }

        val bitmap = drawingView.getDrawingBitmap()
        if (bitmap == null) {
            Log.w(TAG, "No drawing to recognize")
            return
        }

        lifecycleScope.launch {
            try {
                // Perform recognition
                val predictions = engine.recognize(bitmap)
                currentPredictions = predictions
                
                Log.d(TAG, "Recognition complete: ${predictions.size} predictions")
                
                if (predictions.isEmpty()) {
                    showEmptyResults()
                } else {
                    showRecognitionResults(predictions)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Recognition failed", e)
                showEmptyResults()
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun showRecognitionResults(predictions: List<KanjiPrediction>) {
        // Convert predictions to kanji cards
        lifecycleScope.launch {
            val repository = DictionaryRepository.getInstance(this@KanjiDrawingActivity)
            val kanjiCards = mutableListOf<KanjiCardInfo>()
            
            for (prediction in predictions) {
                try {
                    // Try to get kanji info from repository
                    val kanjiInfo = repository.getKanjiInfo(listOf(prediction.kanji))
                    
                    if (kanjiInfo.isNotEmpty()) {
                        val info: KanjiResult = kanjiInfo[0]
                        kanjiCards.add(
                            KanjiCardInfo(
                                kanji = prediction.kanji,
                                onReadings = info.onReadings.take(3).joinToString(", "),
                                kunReadings = info.kunReadings.take(3).joinToString(", "),
                                primaryMeaning = info.meanings.take(3).joinToString(", "),
                                jlptLevel = info.jlptLevel,
                                grade = info.grade,
                                commonalityScore = info.frequency ?: 0,
                                hasReadings = info.onReadings.isNotEmpty() || info.kunReadings.isNotEmpty(),
                                confidence = (prediction.confidence * 100).toInt() // Add confidence as percentage
                            )
                        )
                    } else {
                        // Create basic card info without database data
                        kanjiCards.add(
                            KanjiCardInfo(
                                kanji = prediction.kanji,
                                onReadings = "",
                                kunReadings = "",
                                primaryMeaning = "Unknown kanji",
                                jlptLevel = null,
                                grade = null,
                                commonalityScore = 0,
                                hasReadings = false,
                                confidence = (prediction.confidence * 100).toInt()
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get info for kanji: ${prediction.kanji}", e)
                }
            }

            // Update UI
            kanjiCardAdapter.updateData(kanjiCards)
            showResults(kanjiCards.size)
        }
    }

    private fun showResults(count: Int) {
        emptyStateLayout.visibility = View.GONE
        kanjiResultsRecyclerView.visibility = View.VISIBLE
        
        resultsCounter.visibility = View.VISIBLE
        resultsCounter.text = count.toString()
        
        // Auto-scroll to top
        if (count > 0) {
            kanjiResultsRecyclerView.scrollToPosition(0)
        }
        
        Log.d(TAG, "Showing $count recognition results")
    }

    private fun showEmptyResults() {
        kanjiResultsRecyclerView.visibility = View.GONE
        resultsCounter.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
        kanjiCardAdapter.updateData(emptyList())
    }

    private fun clearResults() {
        currentPredictions = emptyList()
        showEmptyResults()
    }

    private fun undoLastStroke() {
        drawingView.undoLastStroke()
        Log.d(TAG, "Undid last stroke")
        
        // Re-run recognition if there's still drawing
        if (drawingView.hasDrawing() && recognitionEngine?.isReady() == true) {
            performRecognition()
        }
    }
    
    private fun clearDrawingAndResults() {
        drawingView.clearDrawing()
        clearResults()
        Log.d(TAG, "Drawing and results cleared")
    }

    private fun onKanjiSelected(kanji: String) {
        Log.d(TAG, "Kanji selected: $kanji - returning to dictionary search")
        
        // Return to DictionaryActivity and append kanji to search
        val intent = Intent(this, DictionaryActivity::class.java).apply {
            putExtra("append_to_search", kanji)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        recognitionEngine?.release()
    }
}