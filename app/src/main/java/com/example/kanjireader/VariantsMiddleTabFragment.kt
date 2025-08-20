package com.example.kanjireader

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

/**
 * Fragment displaying variants for a word in the middle section
 */
class VariantsMiddleTabFragment : Fragment() {
    
    interface OnVariantsCountListener {
        fun onVariantsCountUpdated(count: Int)
    }
    
    companion object {
        private const val ARG_WORD = "word"
        private const val TAG = "VariantsMiddleTabFragment"
        
        fun newInstance(word: String): VariantsMiddleTabFragment {
            return VariantsMiddleTabFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WORD, word)
                }
            }
        }
    }
    
    // UI Components
    private lateinit var variantsRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: View
    private lateinit var loadingStateLayout: View
    
    // Data
    private var word: String = ""
    private lateinit var variantAdapter: VariantAdapter
    
    // Repository
    private lateinit var repository: DictionaryRepository
    
    // Listener for count updates
    private var countListener: OnVariantsCountListener? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            word = it.getString(ARG_WORD, "")
        }
        
        // Initialize repository
        repository = DictionaryRepository.getInstance(requireContext())
        
        // Set up listener
        if (activity is OnVariantsCountListener) {
            countListener = activity as OnVariantsCountListener
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_variants_middle_tab, container, false)
        
        // Initialize views
        variantsRecyclerView = view.findViewById(R.id.variantsRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        loadingStateLayout = view.findViewById(R.id.loadingStateLayout)
        
        // Setup RecyclerView
        setupRecyclerView()
        
        // Load variants
        loadVariants()
        
        return view
    }
    
    private fun setupRecyclerView() {
        variantAdapter = VariantAdapter { variant ->
            // Handle variant click - open word detail for the variant
            openWordDetail(variant.variantKanji)
        }
        
        variantsRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 2) // 2 columns
            adapter = variantAdapter
        }
    }
    
    private fun loadVariants() {
        if (word.isEmpty()) {
            showEmptyState()
            return
        }
        
        showLoadingState()
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading variants for: $word")
                val variants = repository.getVariants(word)
                
                if (variants.isEmpty()) {
                    Log.d(TAG, "No variants found for: $word")
                    showEmptyState()
                    countListener?.onVariantsCountUpdated(0)
                } else {
                    Log.d(TAG, "Found ${variants.size} variants for: $word")
                    showVariants(variants)
                    countListener?.onVariantsCountUpdated(variants.size)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading variants for $word", e)
                showEmptyState()
                countListener?.onVariantsCountUpdated(0)
            }
        }
    }
    
    private fun showLoadingState() {
        variantsRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE
        loadingStateLayout.visibility = View.VISIBLE
    }
    
    private fun showEmptyState() {
        variantsRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
        loadingStateLayout.visibility = View.GONE
    }
    
    private fun showVariants(variants: List<Variant>) {
        variantAdapter.updateVariants(variants)
        variantsRecyclerView.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
        loadingStateLayout.visibility = View.GONE
    }
    
    private fun openWordDetail(variantKanji: String) {
        try {
            val intent = Intent(context, WordDetailActivity::class.java).apply {
                putExtra("word", variantKanji)
                putExtra("reading", "")  // Will be filled by the detail activity
                putExtra("meanings", ArrayList(listOf("Alternative form")))
                putExtra("frequency", 0)
                putExtra("isJMNEDict", false)
            }
            
            startActivity(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error opening word detail for variant: $variantKanji", e)
        }
    }
}