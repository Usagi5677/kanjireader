package com.example.kanjireader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class FormsTabFragment : Fragment() {

    companion object {
        private const val ARG_WORD = "word"

        fun newInstance(word: String): FormsTabFragment {
            return FormsTabFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WORD, word)
                }
            }
        }
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var conjugationAdapter: ConjugationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_forms_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.conjugationRecyclerView)
        emptyStateText = view.findViewById(R.id.emptyStateText)

        setupRecyclerView()

        val word = arguments?.getString(ARG_WORD) ?: ""
        if (word.isNotEmpty()) {
            loadConjugations(word)
        }
    }

    private fun setupRecyclerView() {
        conjugationAdapter = ConjugationAdapter { conjugatedWord ->
            // When a conjugation is clicked, switch to phrases tab and search for that word
            switchToPhrasesTabWithWord(conjugatedWord)
        }
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = conjugationAdapter
        }
    }
    
    private fun switchToPhrasesTabWithWord(word: String) {
        // Get the parent WordDetailActivity and switch to phrases tab
        val activity = activity as? WordDetailActivity
        activity?.switchToPhrasesTab(word)
    }

    private fun loadConjugations(word: String) {
        lifecycleScope.launch {
            // Get word info from dictionary
            val repository = DictionaryRepository.getInstance(requireContext())
            val tagLoader = TagDictSQLiteLoader(requireContext())

            // Look up the word using FTS5 search
            val searchResults = repository.search(word)
            if (searchResults.isEmpty()) {
                showNotConjugatable()
                return@launch
            }

            val wordResult = searchResults.first()
            val tagEntry = tagLoader.lookupTags(word)

            // Get part of speech tags
            val posTags = mutableListOf<String>()
            tagEntry?.senses?.forEach { sense ->
                sense.pos?.let { posTags.addAll(it) }
            }

            // Debug: Let's see what tags 満足 has
            android.util.Log.d("FormsTab", "Word: $word, POS tags: $posTags")

            // Determine word type and generate conjugations
            // For words that can be multiple types (like 満足), show all applicable forms
            val conjugationGroups = mutableListOf<ConjugationGroup>()
            
            // Check for I-adjective forms
            if (isIAdjective(posTags)) {
                android.util.Log.d("FormsTab", "Detected as I-adjective")
                conjugationGroups.addAll(generateIAdjectiveConjugations(word, wordResult))
            }
            
            // Check for Na-adjective forms
            if (isNaAdjective(posTags)) {
                android.util.Log.d("FormsTab", "Detected as Na-adjective")
                conjugationGroups.addAll(generateNaAdjectiveConjugations(word, wordResult))
            }
            
            // Check for noun forms
            if (isNoun(posTags)) {
                android.util.Log.d("FormsTab", "Detected as noun")
                conjugationGroups.addAll(generateNounConjugations(word, wordResult))
            }
            
            // Check for verb forms (especially suru verbs)
            if (isVerb(posTags)) {
                android.util.Log.d("FormsTab", "Detected as verb, isSuru: ${isSuruVerb(posTags)}")
                val verbType = getVerbType(posTags)
                // For suru verbs, check if we need to add する
                val actualVerb = if (isSuruVerb(posTags) && !word.endsWith("する")) {
                    "${word}する"
                } else {
                    word
                }
                android.util.Log.d("FormsTab", "Verb type: $verbType, actual verb: $actualVerb")
                
                // Check if we also have na-adjective forms to avoid redundant forms
                val hasNaAdjectiveForms = isNaAdjective(posTags)
                conjugationGroups.addAll(generateVerbConjugations(actualVerb, verbType, wordResult, hasNaAdjectiveForms))
            }
            
            if (conjugationGroups.isNotEmpty()) {
                showConjugations(conjugationGroups)
            } else {
                showNotConjugatable()
            }
        }
    }

    private fun isVerb(tags: List<String>): Boolean {
        // Detect verbs including suru verbs (even when they also have noun tags)
        val verbTags = setOf("v1", "v5k", "v5s", "v5t", "v5n", "v5b", "v5m", "v5r", "v5g", "v5u", "vs-i", "vs", "vs-s", "vk", "v5k-s", "vt", "vi", "aux-v")
        return tags.any { it in verbTags }
    }

    private fun isIAdjective(tags: List<String>): Boolean {
        return tags.contains("adj-i")
    }

    private fun isNaAdjective(tags: List<String>): Boolean {
        return tags.contains("adj-na")
    }

    private fun isNoun(tags: List<String>): Boolean {
        // Check for noun tags and suru-noun tags
        return tags.any { 
            it == "n" || 
            it.startsWith("n-") || 
            it == "vs-s" ||  // suru noun (can be used as noun or with suru)
            (it == "vs" && tags.contains("n"))  // vs tag with noun tag means it's primarily a noun
        }
    }

    private fun isSuruVerb(tags: List<String>): Boolean {
        return tags.contains("vs") || tags.contains("vs-i") || tags.contains("vs-s")
    }

    private fun getVerbType(tags: List<String>): VerbType {
        return when {
            tags.contains("v1") -> VerbType.ICHIDAN
            tags.contains("v5k") -> VerbType.GODAN_K
            tags.contains("v5s") -> VerbType.GODAN_S
            tags.contains("v5t") -> VerbType.GODAN_T
            tags.contains("v5n") -> VerbType.GODAN_N
            tags.contains("v5b") -> VerbType.GODAN_B
            tags.contains("v5m") -> VerbType.GODAN_M
            tags.contains("v5r") -> VerbType.GODAN_R
            tags.contains("v5g") -> VerbType.GODAN_G
            tags.contains("v5u") -> VerbType.GODAN_U
            tags.contains("vs-i") -> VerbType.SURU_IRREGULAR
            tags.contains("vs") -> VerbType.SURU_IRREGULAR
            tags.contains("vk") -> VerbType.KURU_IRREGULAR
            tags.contains("v5k-s") -> VerbType.IKU_IRREGULAR
            else -> VerbType.UNKNOWN
        }
    }

    private fun showConjugations(conjugations: List<ConjugationGroup>) {
        recyclerView.visibility = View.VISIBLE
        emptyStateText.visibility = View.GONE
        conjugationAdapter.updateData(conjugations)
    }

    private fun showNotConjugatable() {
        recyclerView.visibility = View.GONE
        emptyStateText.visibility = View.VISIBLE
        emptyStateText.text = "This word is not conjugatable"
    }

    private fun generateVerbConjugations(
        word: String,
        verbType: VerbType,
        wordResult: WordResult,
        hasNaAdjectiveForms: Boolean = false
    ): List<ConjugationGroup> {
        android.util.Log.d("FormsTab", "generateVerbConjugations called with word: $word, verbType: $verbType")
        val engine = TenTenStyleDeinflectionEngine()
        val generator = ConjugationGenerator(engine)

        // For suru verbs, extend the reading if needed
        val baseReading = if (word.endsWith("する") && !wordResult.reading.endsWith("する")) {
            "${wordResult.reading}する"
        } else {
            wordResult.reading
        }
        android.util.Log.d("FormsTab", "Base reading: $baseReading")

        val conjugations = generator.generateConjugations(
            word = word,
            verbType = verbType,
            baseReading = baseReading,
            meaning = wordResult.meanings.firstOrNull() ?: "do"
        )
        
        // If this word also has na-adjective forms, modify suru verb forms to be more natural
        val filteredConjugations = if (hasNaAdjectiveForms && verbType == VerbType.SURU_IRREGULAR) {
            val baseWord = word.removeSuffix("する") // Get base word (e.g., 満足)
            val baseWordReading = baseReading.removeSuffix("する")
            
            conjugations.map { group ->
                when (group.groupName) {
                    "Special Forms" -> {
                        // Remove "Seems like" forms since na-adjective has better ones
                        val filteredForms = group.conjugations.filterNot { item ->
                            item.formName.contains("Seems like", true)
                        }
                        group.copy(conjugations = filteredForms)
                    }
                    "Hearsay & Presumptive Forms" -> {
                        // Modify hearsay forms to use base word instead of suru verb
                        val modifiedForms = group.conjugations.map { item ->
                            when {
                                item.formName.contains("Hearsay") -> {
                                    val newConjugation = item.conjugation.replace("${baseWord}する", baseWord)
                                    val newReading = item.reading?.replace("${baseWordReading}する", baseWordReading)
                                    item.copy(
                                        conjugation = newConjugation,
                                        reading = if (newReading != newConjugation) newReading else null
                                    )
                                }
                                else -> item
                            }
                        }
                        group.copy(conjugations = modifiedForms)
                    }
                    else -> group
                }
            }.filter { it.conjugations.isNotEmpty() }
        } else {
            conjugations
        }
        
        android.util.Log.d("FormsTab", "Generated ${filteredConjugations.size} conjugation groups (filtered: $hasNaAdjectiveForms)")
        return filteredConjugations
    }

    private fun generateIAdjectiveConjugations(word: String, wordResult: WordResult): List<ConjugationGroup> {
        val groups = mutableListOf<ConjugationGroup>()
        val stem = word.dropLast(1) // Remove い
        val baseReading = wordResult.reading
        val stemReading = if (baseReading != word) baseReading.dropLast(1) else ""
        val meaning = wordResult.meanings.firstOrNull() ?: "..."

        val forms = listOf(
            ConjugationItem("Dictionary", word, if (word != baseReading) baseReading else null, "is $meaning"),
            ConjugationItem("Past", "${stem}かった", if (hasKanji(stem)) "${stemReading}かった" else null, "was $meaning"),
            ConjugationItem("Negative", "${stem}くない", if (hasKanji(stem)) "${stemReading}くない" else null, "is not $meaning"),
            ConjugationItem("Past Negative", "${stem}くなかった", if (hasKanji(stem)) "${stemReading}くなかった" else null, "was not $meaning"),
            ConjugationItem("Te-form", "${stem}くて", if (hasKanji(stem)) "${stemReading}くて" else null, "$meaning and..."),
            ConjugationItem("Adverb", "${stem}く", if (hasKanji(stem)) "${stemReading}く" else null, "in a $meaning way"),
            ConjugationItem("Conditional", "${stem}ければ", if (hasKanji(stem)) "${stemReading}ければ" else null, "if $meaning"),
            ConjugationItem("Too much", "${stem}すぎる", if (hasKanji(stem)) "${stemReading}すぎる" else null, "too $meaning"),
            ConjugationItem("Seems like", "${stem}そう", if (hasKanji(stem)) "${stemReading}そう" else null, "seems $meaning")
        )

        groups.add(ConjugationGroup("I-Adjective Forms", forms))
        return groups
    }

    private fun generateNaAdjectiveConjugations(word: String, wordResult: WordResult): List<ConjugationGroup> {
        val groups = mutableListOf<ConjugationGroup>()
        val baseReading = wordResult.reading
        val meaning = wordResult.meanings.firstOrNull() ?: "..."

        val forms = listOf(
            ConjugationItem("Dictionary", word, if (word != baseReading) baseReading else null, formatAdjectiveMeaning(meaning, "dictionary")),
            ConjugationItem("Attributive", "${word}な", if (hasKanji(word)) "${baseReading}な" else null, formatAdjectiveMeaning(meaning, "attributive")),
            ConjugationItem("With copula", "${word}だ", if (hasKanji(word)) "${baseReading}だ" else null, formatAdjectiveMeaning(meaning, "copula")),
            ConjugationItem("Past", "${word}だった", if (hasKanji(word)) "${baseReading}だった" else null, formatAdjectiveMeaning(meaning, "past")),
            ConjugationItem("Negative", "${word}じゃない", if (hasKanji(word)) "${baseReading}じゃない" else null, formatAdjectiveMeaning(meaning, "negative")),
            ConjugationItem("Past Negative", "${word}じゃなかった", if (hasKanji(word)) "${baseReading}じゃなかった" else null, formatAdjectiveMeaning(meaning, "past negative")),
            ConjugationItem("Te-form", "${word}で", if (hasKanji(word)) "${baseReading}で" else null, formatAdjectiveMeaning(meaning, "te-form")),
            ConjugationItem("Adverbial", "${word}に", if (hasKanji(word)) "${baseReading}に" else null, formatAdjectiveMeaning(meaning, "adverbial")),
            ConjugationItem("Conditional", "${word}なら", if (hasKanji(word)) "${baseReading}なら" else null, formatAdjectiveMeaning(meaning, "conditional")),
            ConjugationItem("Too much", "${word}すぎる", if (hasKanji(word)) "${baseReading}すぎる" else null, formatAdjectiveMeaning(meaning, "too much")),
            ConjugationItem("Seems like", "${word}そう", if (hasKanji(word)) "${baseReading}そう" else null, formatAdjectiveMeaning(meaning, "seems"))
        )

        groups.add(ConjugationGroup("Na-Adjective Forms", forms))
        return groups
    }

    private fun generateNounConjugations(word: String, wordResult: WordResult): List<ConjugationGroup> {
        val groups = mutableListOf<ConjugationGroup>()
        val baseReading = wordResult.reading
        val meaning = wordResult.meanings.firstOrNull() ?: "..."

        val forms = listOf(
            ConjugationItem("Dictionary", word, if (word != baseReading) baseReading else null, formatNounMeaning(meaning, "dictionary")),
            ConjugationItem("With copula", "${word}だ", if (hasKanji(word)) "${baseReading}だ" else null, formatNounMeaning(meaning, "copula")),
            ConjugationItem("Past", "${word}だった", if (hasKanji(word)) "${baseReading}だった" else null, formatNounMeaning(meaning, "past")),
            ConjugationItem("Negative", "${word}じゃない", if (hasKanji(word)) "${baseReading}じゃない" else null, formatNounMeaning(meaning, "negative")),
            ConjugationItem("Past Negative", "${word}じゃなかった", if (hasKanji(word)) "${baseReading}じゃなかった" else null, formatNounMeaning(meaning, "past negative")),
            ConjugationItem("With で", "${word}で", if (hasKanji(word)) "${baseReading}で" else null, formatNounMeaning(meaning, "de")),
            ConjugationItem("With に", "${word}に", if (hasKanji(word)) "${baseReading}に" else null, formatNounMeaning(meaning, "ni")),
            ConjugationItem("With の", "${word}の", if (hasKanji(word)) "${baseReading}の" else null, formatNounMeaning(meaning, "no")),
            ConjugationItem("With を", "${word}を", if (hasKanji(word)) "${baseReading}を" else null, formatNounMeaning(meaning, "wo")),
            ConjugationItem("With は", "${word}は", if (hasKanji(word)) "${baseReading}は" else null, formatNounMeaning(meaning, "wa")),
            ConjugationItem("With が", "${word}が", if (hasKanji(word)) "${baseReading}が" else null, formatNounMeaning(meaning, "ga"))
        )

        groups.add(ConjugationGroup("Noun Forms", forms))
        return groups
    }

    private fun formatAdjectiveMeaning(meaning: String, conjugationType: String): String {
        val cleanMeaning = meaning.toLowerCase()
        
        return when (conjugationType.lowercase()) {
            "dictionary" -> "when something is $cleanMeaning"
            "attributive" -> "when describing something as $cleanMeaning"
            "copula" -> "when stating something is $cleanMeaning"
            "past" -> "when something was $cleanMeaning"
            "negative" -> "when something is not $cleanMeaning"
            "past negative" -> "when something was not $cleanMeaning"
            "te-form" -> "when something is $cleanMeaning and..."
            "adverbial" -> "when doing something in a $cleanMeaning manner"
            "conditional" -> "when something would be $cleanMeaning"
            "too much" -> "when something is too $cleanMeaning"
            "seems" -> "when something seems $cleanMeaning"
            else -> "when something is $cleanMeaning"
        }
    }

    private fun formatNounMeaning(meaning: String, conjugationType: String): String {
        val cleanMeaning = meaning.toLowerCase()
        
        return when (conjugationType.lowercase()) {
            "dictionary" -> "when referring to $cleanMeaning"
            "copula" -> "when stating something is $cleanMeaning"
            "past" -> "when something was $cleanMeaning"
            "negative" -> "when something is not $cleanMeaning"
            "past negative" -> "when something was not $cleanMeaning"
            "de" -> "when using $cleanMeaning as a means or location"
            "ni" -> "when indicating direction or purpose toward $cleanMeaning"
            "no" -> "when indicating possession or relationship with $cleanMeaning"
            "wo" -> "when $cleanMeaning is the direct object of an action"
            "wa" -> "when $cleanMeaning is the topic of discussion"
            "ga" -> "when $cleanMeaning is the subject performing an action"
            else -> "when referring to $cleanMeaning"
        }
    }

    private fun hasKanji(text: String): Boolean {
        return text.any { char ->
            val code = char.code
            (code in 0x4E00..0x9FAF) || (code in 0x3400..0x4DBF)
        }
    }
}