package com.example.kanjireader

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class KanjiTabFragment : Fragment() {

    companion object {
        private const val ARG_WORD = "word"

        fun newInstance(word: String): KanjiTabFragment {
            return KanjiTabFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WORD, word)
                }
            }
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var kanjiAdapter: KanjiDetailAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_kanji_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.kanjiRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        setupRecyclerView()

        // Get the word from arguments
        val word = arguments?.getString(ARG_WORD) ?: ""
        if (word.isEmpty()) {
            Log.w("KanjiTabFragment", "No word provided to KanjiTabFragment!")
            showEmptyState()
        } else {
            loadKanjiData(word)
        }
    }

    private fun setupRecyclerView() {
        kanjiAdapter = KanjiDetailAdapter(requireContext())
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = kanjiAdapter
        }
    }

    private fun loadKanjiData(wordText: String) {
        Log.d("KanjiTabFragment", "Loading kanji data for word: '$wordText'")

        // Extract individual kanji from the word
        val kanjiList = extractKanji(wordText)
        Log.d("KanjiTabFragment", "Extracted kanji: $kanjiList")

        if (kanjiList.isEmpty()) {
            Log.w("KanjiTabFragment", "No kanji characters found in word: '$wordText'")
            showEmptyState()
            return
        }

        // Get DictionaryRepository
        val repository = DictionaryRepository.getInstance(requireContext())

        // Use coroutine to load kanji data from database
        lifecycleScope.launch {
            try {
                Log.d("KanjiTabFragment", "Kanji list in order: ${kanjiList.joinToString(", ")}")
                Log.d("KanjiTabFragment", "Querying database for kanji: $kanjiList")
                val kanjiDetails = repository.getKanjiInfo(kanjiList)
                Log.d("KanjiTabFragment", "Found ${kanjiDetails.size} kanji details")
                
                // Log details for debugging
                kanjiDetails.forEach { kanji ->
                    Log.d("KanjiTabFragment", "Kanji '${kanji.kanji}': meanings=${kanji.meanings}, radicals=${kanji.radicalNames}")
                }
                
                if (kanjiDetails.isEmpty()) {
                    showEmptyState()
                } else {
                    showKanjiData(kanjiDetails)
                }
            } catch (e: Exception) {
                Log.e("KanjiTabFragment", "Error loading kanji data", e)
                showEmptyState()
            }
        }
    }

    private fun extractKanji(text: String): List<String> {
        val seenKanji = mutableSetOf<String>()
        val kanjiInOrder = mutableListOf<String>()
        
        for (char in text) {
            val charString = char.toString()
            if (isKanji(charString) && !seenKanji.contains(charString)) {
                seenKanji.add(charString)
                kanjiInOrder.add(charString)
            }
        }
        
        return kanjiInOrder
    }

    private fun isKanji(char: String): Boolean {
        if (char.isEmpty()) return false
        val codePoint = char.codePointAt(0)
        return (codePoint in 0x4E00..0x9FAF) || (codePoint in 0x3400..0x4DBF)
    }

    private fun showEmptyState() {
        recyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
    }

    private fun showKanjiData(kanjiDetails: List<KanjiResult>) {
        recyclerView.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
        kanjiAdapter.updateData(kanjiDetails)
    }
}