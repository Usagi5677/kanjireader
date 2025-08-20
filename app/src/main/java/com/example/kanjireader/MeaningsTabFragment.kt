package com.example.kanjireader

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import android.widget.TextView
import android.widget.ScrollView

/**
 * Fragment displaying meanings for a word in the middle section
 */
class MeaningsTabFragment : Fragment() {
    
    interface OnMeaningsCountListener {
        fun onMeaningsCountUpdated(count: Int)
    }
    
    companion object {
        private const val ARG_MEANINGS = "meanings"
        private const val TAG = "MeaningsTabFragment"
        
        fun newInstance(meanings: ArrayList<String>): MeaningsTabFragment {
            return MeaningsTabFragment().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_MEANINGS, meanings)
                }
            }
        }
    }
    
    // UI Components
    private lateinit var meaningsScrollView: ScrollView
    private lateinit var meaningsText: TextView
    private lateinit var emptyStateLayout: View
    
    // Data
    private var meanings: List<String> = emptyList()
    
    // Listener for count updates
    private var countListener: OnMeaningsCountListener? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            meanings = it.getStringArrayList(ARG_MEANINGS) ?: emptyList()
        }
        
        // Set up listener
        if (activity is OnMeaningsCountListener) {
            countListener = activity as OnMeaningsCountListener
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_meanings_tab, container, false)
        
        // Initialize views
        meaningsScrollView = view.findViewById(R.id.meaningsScrollView)
        meaningsText = view.findViewById(R.id.meaningsText)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        
        // Display meanings
        displayMeanings()
        
        return view
    }
    
    
    private fun displayMeanings() {
        if (meanings.isEmpty()) {
            Log.d(TAG, "No meanings to display")
            showEmptyState()
            countListener?.onMeaningsCountUpdated(0)
        } else {
            Log.d(TAG, "Displaying ${meanings.size} meanings")
            showMeanings()
            countListener?.onMeaningsCountUpdated(meanings.size)
        }
    }
    
    private fun showEmptyState() {
        meaningsScrollView.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
    }
    
    private fun showMeanings() {
        // Format meanings the same way as before
        val meaningsFormatted = meanings.mapIndexed { index, meaning ->
            "${index + 1}. $meaning"
        }.joinToString("\n")
        
        meaningsText.text = meaningsFormatted
        meaningsScrollView.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
    }
}