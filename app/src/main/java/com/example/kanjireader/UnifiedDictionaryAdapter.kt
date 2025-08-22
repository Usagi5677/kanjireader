package com.example.kanjireader

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

enum class ChipType {
    VERB, ADJECTIVE, NOUN, PARTICLE, ADVERB, COMMON, FREQUENCY, CONJUGATED, OTHER
}

class UnifiedDictionaryAdapter(
    private val onItemClick: (UnifiedDictionaryEntry) -> Unit
) : RecyclerView.Adapter<UnifiedDictionaryAdapter.UnifiedViewHolder>() {

    private var entries: List<UnifiedDictionaryEntry> = emptyList()
    private var searchQuery: String? = null

    fun updateEntries(newEntries: List<UnifiedDictionaryEntry>, query: String? = null) {
        entries = newEntries
        searchQuery = query
        notifyDataSetChanged() // Re-enabled - not the cause
    }
    
    fun appendEntries(moreEntries: List<UnifiedDictionaryEntry>) {
        val oldSize = entries.size
        entries = entries + moreEntries
        notifyItemRangeInserted(oldSize, moreEntries.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UnifiedViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_unified_dictionary_result, parent, false)
        return UnifiedViewHolder(view)
    }

    override fun onBindViewHolder(holder: UnifiedViewHolder, position: Int) {
        holder.bind(entries[position])
    }

    override fun getItemCount() = entries.size

    inner class UnifiedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val primaryKanjiText: TextView = itemView.findViewById(R.id.primaryKanjiText)
        private val primaryReadingText: TextView = itemView.findViewById(R.id.primaryReadingText)
        private val pitchAccentView: PitchAccentView = itemView.findViewById(R.id.pitchAccentView)
        private val primaryChipGroup: ChipGroup = itemView.findViewById(R.id.primaryChipGroup)
        private val variantsContainer: LinearLayout = itemView.findViewById(R.id.variantsContainer)
        private val meaningsText: TextView = itemView.findViewById(R.id.meaningsText)
        private val frequencyCounter: TextView = itemView.findViewById(R.id.frequencyCounter)

        fun bind(entry: UnifiedDictionaryEntry) {
            // Clear any previous dynamic views
            variantsContainer.removeAllViews()

            // TEMPORARILY DISABLED: Pitch accent display for performance testing
            // Show pitch accent if available, otherwise show regular reading
            /*
            if (!entry.pitchAccents.isNullOrEmpty() && entry.primaryReading != null) {
                // Set primary form with highlighting
                primaryKanjiText.text = highlightSearchText(entry.primaryForm, searchQuery)
                // Pass search query to pitch accent view for hiragana highlighting
                pitchAccentView.setPitchAccents(entry.pitchAccents, entry.primaryReading, searchQuery)
                pitchAccentView.visibility = View.VISIBLE
                primaryReadingText.visibility = View.GONE // Hide regular reading since pitch accent view shows it
            } else {
            */
                // Set primary form with highlighting when no pitch accent
                primaryKanjiText.text = highlightSearchText(entry.primaryForm, searchQuery)
                pitchAccentView.visibility = View.GONE
                // Show reading if different from primary form
                if (entry.primaryReading != null && entry.primaryReading != entry.primaryForm) {
                    primaryReadingText.text = highlightSearchText(entry.primaryReading, searchQuery)
                    primaryReadingText.visibility = View.VISIBLE
                } else {
                    primaryReadingText.visibility = View.GONE
                }
            /*
            }
            */

            // Add primary tags
            primaryChipGroup.removeAllViews()
            entry.primaryTags.forEach { tag ->
                val chipType = getChipTypeForTag(tag)
                val chip = createStyledChip(getSimplifiedTag(tag), chipType)
                primaryChipGroup.addView(chip)
            }

            // Add common chip if applicable
            if (entry.isCommon) {
                val commonChip = createStyledChip("common", ChipType.COMMON)
                primaryChipGroup.addView(commonChip)
            }

            // Show frequency counter if available
            if (entry.frequency != null && entry.frequency > 0) {
                frequencyCounter.text = formatFrequency(entry.frequency)
                applyFrequencyColors(frequencyCounter, entry.frequency)
                frequencyCounter.visibility = View.VISIBLE
            } else {
                frequencyCounter.visibility = View.GONE
            }

            // Show conjugation info if present
            if (entry.conjugationInfo != null) {
                val conjugatedChip = createStyledChip("conjugated", ChipType.CONJUGATED)
                // Set proper layout parameters to wrap content instead of filling width
                conjugatedChip.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 4.dpToPx()
                }
                variantsContainer.addView(conjugatedChip)
                variantsContainer.visibility = View.VISIBLE
            }
            

            // Show source type if present (for parallel search)
            if (entry.sourceType != null) {
                val sourceTypeText = TextView(itemView.context).apply {
                    text = "Source: ${entry.sourceType}"
                    textSize = 13f
                    setTextColor(ContextCompat.getColor(context, R.color.blue_600))
                    setPadding(0, 4, 0, 8)
                    setTypeface(null, android.graphics.Typeface.ITALIC)
                }
                variantsContainer.addView(sourceTypeText)
                variantsContainer.visibility = View.VISIBLE
            }

            // Handle variants
            if (entry.variants.isNotEmpty()) {
                // Add a small spacing if we already added conjugation/verb type info
                if (entry.conjugationInfo != null || entry.verbType != null) {
                    val spacer = View(itemView.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            8
                        )
                    }
                    variantsContainer.addView(spacer)
                }

                entry.variants.forEach { variant ->
                    val variantView = createVariantView(variant, entry.primaryTags)
                    variantsContainer.addView(variantView)
                }
                variantsContainer.visibility = View.VISIBLE
            } else if (entry.conjugationInfo == null && entry.verbType == null) {
                // Hide variants container if no content
                variantsContainer.visibility = View.GONE
            }

            // Set meanings with highlighting
            val meaningsWithNumbers = entry.meanings.mapIndexed { index, meaning ->
                "${index + 1}. $meaning"
            }.joinToString("\n")
            meaningsText.text = highlightSearchText(meaningsWithNumbers, searchQuery)

            // Click listener
            itemView.setOnClickListener {
                onItemClick(entry)
            }
        }

        private fun createVariantView(variant: VariantInfo, primaryTags: List<String>): View {
            val inflater = LayoutInflater.from(itemView.context)
            val view = inflater.inflate(R.layout.item_variant_entry, variantsContainer, false)

            val variantText: TextView = view.findViewById(R.id.variantText)
            val variantChipGroup: ChipGroup = view.findViewById(R.id.variantChipGroup)
            val variantInheritText: TextView = view.findViewById(R.id.variantInheritText)

            variantText.text = highlightSearchText(variant.text, searchQuery)

            // Always hide the inherit text
            variantInheritText.visibility = View.GONE

            // Show ALL tags for this variant (not just unique ones)
            if (variant.allTags.isNotEmpty()) {
                variantChipGroup.visibility = View.VISIBLE

                variant.allTags.forEach { tag ->
                    val chip = createChip(tag)
                    variantChipGroup.addView(chip)
                }

                // Add common chip if applicable
                if (variant.isCommon) {
                    val commonChip = createChip("common", R.color.green_100)
                    variantChipGroup.addView(commonChip)
                }
            } else {
                variantChipGroup.visibility = View.GONE
            }

            return view
        }

        private fun createChip(text: String, colorResId: Int? = null): Chip {
            return Chip(itemView.context).apply {
                this.text = getSimplifiedTag(text)
                textSize = 12f

                // Set both min and max height to force uniformity
                minimumHeight = 32.dpToPx()
                maxHeight = 32.dpToPx()

                chipCornerRadius = 12f
                chipBackgroundColor = if (colorResId != null) {
                    ContextCompat.getColorStateList(context, colorResId)
                } else {
                    ContextCompat.getColorStateList(context, R.color.teal_100)
                }
                isClickable = false
                isFocusable = false
                chipStrokeWidth = 0f
            }
        }

        // Extension function to convert dp to pixels
        private fun Int.dpToPx(): Int {
            return (this * itemView.context.resources.displayMetrics.density).toInt()
        }

        private fun getSimplifiedTag(tag: String): String {
            return when (tag) {
                // Regular dictionary tags
                "v1" -> "ichidan"
                "v5k", "v5s", "v5t", "v5n", "v5b", "v5m", "v5r", "v5g", "v5u" -> "godan"
                "vt" -> "transitive"
                "vi" -> "intransitive"
                "aux-v" -> "auxiliary"
                "adj-i" -> "い-adj"
                "adj-na" -> "な-adj"
                "n" -> "noun"
                "adv" -> "adverb"
                "prt" -> "particle"
                else -> tag
            }
        }

        private fun formatFrequency(frequency: Int): String {
            return when {
                frequency >= 1000000 -> {
                    val millions = frequency / 1000000.0
                    "%.2f".format(millions).removeSuffix("0").removeSuffix(".") + "M"
                }
                frequency >= 1000 -> {
                    val thousands = frequency / 1000.0
                    "%.1f".format(thousands).removeSuffix("0").removeSuffix(".") + "k"
                }
                else -> frequency.toString()
            }
        }

        private fun applyFrequencyColors(textView: TextView, frequency: Int) {
            val (bgColorRes, textColorRes) = when {
                frequency >= 10000000 -> Pair(R.color.freq_very_high_bg, R.color.freq_very_high_text) // 10M+ (very common)
                frequency >= 1000000 -> Pair(R.color.freq_high_bg, R.color.freq_high_text)          // 1M+ (common)
                frequency >= 100000 -> Pair(R.color.freq_medium_bg, R.color.freq_medium_text)       // 100k+ (medium)
                frequency >= 10000 -> Pair(R.color.freq_low_bg, R.color.freq_low_text)              // 10k+ (uncommon)
                else -> Pair(R.color.freq_very_low_bg, R.color.freq_very_low_text)                  // <10k (rare)
            }
            
            textView.backgroundTintList = ContextCompat.getColorStateList(textView.context, bgColorRes)
            textView.setTextColor(ContextCompat.getColor(textView.context, textColorRes))
        }

        private fun getChipTypeForTag(tag: String): ChipType {
            return when {
                tag.startsWith("v") || tag == "aux-v" -> ChipType.VERB
                tag.startsWith("adj-") -> ChipType.ADJECTIVE
                tag == "n" -> ChipType.NOUN
                tag == "prt" -> ChipType.PARTICLE
                tag == "adv" -> ChipType.ADVERB
                else -> ChipType.OTHER
            }
        }

        private fun createStyledChip(text: String, chipType: ChipType): Chip {
            val (bgColorRes, textColorRes) = when (chipType) {
                ChipType.VERB -> Pair(R.color.tag_verb_bg, R.color.tag_verb_text)
                ChipType.ADJECTIVE -> Pair(R.color.tag_adjective_bg, R.color.tag_adjective_text)
                ChipType.NOUN -> Pair(R.color.tag_noun_bg, R.color.tag_noun_text)
                ChipType.PARTICLE -> Pair(R.color.tag_particle_bg, R.color.tag_particle_text)
                ChipType.ADVERB -> Pair(R.color.tag_adverb_bg, R.color.tag_adverb_text)
                ChipType.COMMON -> Pair(R.color.tag_common_bg, R.color.tag_common_text)
                ChipType.FREQUENCY -> Pair(R.color.tag_frequency_bg, R.color.tag_frequency_text)
                ChipType.CONJUGATED -> Pair(R.color.orange_100, R.color.orange_700)
                ChipType.OTHER -> Pair(R.color.tag_other_bg, R.color.tag_other_text)
            }

            return Chip(itemView.context).apply {
                this.text = text
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)

                // Set both min and max height to force uniformity
                minimumHeight = 32.dpToPx()
                maxHeight = 32.dpToPx()

                chipCornerRadius = 12f
                chipBackgroundColor = ContextCompat.getColorStateList(context, bgColorRes)
                setTextColor(ContextCompat.getColor(context, textColorRes))
                isClickable = false
                isFocusable = false
                chipStrokeWidth = 0f
            }
        }
        
        /**
         * Highlight search query text in the given text string
         */
        private fun highlightSearchText(text: String, query: String?): CharSequence {
            if (query.isNullOrBlank() || text.isEmpty()) {
                return text
            }
            
            // Create a list of all possible query variations to match
            val queriesToMatch = mutableSetOf<String>()
            val cleanQuery = query.trim().lowercase()
            
            // Add original query
            queriesToMatch.add(cleanQuery)
            
            // If query is romaji, try to convert to hiragana and add that too
            if (isRomajiText(cleanQuery)) {
                try {
                    val romajiConverter = RomajiConverter()
                    val hiraganaQuery = romajiConverter.toHiragana(cleanQuery)
                    if (hiraganaQuery != cleanQuery) {
                        queriesToMatch.add(hiraganaQuery)
                    }
                } catch (e: Exception) {
                    // Ignore conversion errors
                }
            }
            
            val spannableString = SpannableString(text)
            val lowerText = text.lowercase()
            
            // Find and highlight all matching query variations
            for (queryToMatch in queriesToMatch) {
                var startIndex = 0
                while (startIndex < lowerText.length) {
                    val index = lowerText.indexOf(queryToMatch, startIndex)
                    if (index == -1) break
                    
                    val endIndex = index + queryToMatch.length
                    
                    // Apply blue text highlighting (no background)
                    spannableString.setSpan(
                        ForegroundColorSpan(ContextCompat.getColor(itemView.context, R.color.search_highlight_text)),
                        index, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannableString.setSpan(
                        StyleSpan(Typeface.BOLD),
                        index, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    
                    startIndex = endIndex
                }
            }
            
            return spannableString
        }
        
        /**
         * Check if text looks like romaji
         */
        private fun isRomajiText(text: String): Boolean {
            return text.all { char ->
                char in 'a'..'z' || char in 'A'..'Z'
            }
        }
    }
}