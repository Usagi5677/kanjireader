// Update KanjiDetailAdapter.kt
package com.example.kanjireader

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KanjiDetailAdapter(
    private val context: Context
) : RecyclerView.Adapter<KanjiDetailAdapter.KanjiViewHolder>() {

    private var kanjiList: List<KanjiResult> = emptyList()

    fun updateData(newList: List<KanjiResult>) {
        kanjiList = newList
        notifyDataSetChanged()
    }

    data class StrokeData(
        val strokeNumber: Int,
        val path: String,
        val viewBox: String
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KanjiViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_kanji_detail, parent, false)
        return KanjiViewHolder(view)
    }

    override fun onBindViewHolder(holder: KanjiViewHolder, position: Int) {
        holder.bind(kanjiList[position])
    }

    override fun getItemCount() = kanjiList.size

    inner class KanjiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val kanjiText: TextView = itemView.findViewById(R.id.kanjiCharacter)
        private val meaningsText: TextView = itemView.findViewById(R.id.kanjiMeanings)
        private val onReadingText: TextView = itemView.findViewById(R.id.onReading)
        private val kunReadingText: TextView = itemView.findViewById(R.id.kunReading)
        private val radicalText: TextView = itemView.findViewById(R.id.radical)
        private val partsText: ClickablePartsTextView = itemView.findViewById(R.id.parts)
        private val strokeCountText: TextView = itemView.findViewById(R.id.strokeCount)
        private val jlptText: TextView = itemView.findViewById(R.id.jlptLevel)
        private val strokeOrderSection: LinearLayout = itemView.findViewById(R.id.strokeOrderSection)
        private val strokeProgressionView: StrokeProgressionView = itemView.findViewById(R.id.strokeProgressionView)

        fun bind(kanji: KanjiResult) {
            // Keep the kanji text visible
            kanjiText.text = kanji.kanji
            kanjiText.visibility = View.VISIBLE
            
            // Make kanji text clickable
            kanjiText.setOnClickListener {
                openKanjiDetail(kanji.kanji)
            }

            // Meanings
            meaningsText.text = kanji.meanings.joinToString(", ")

            // Readings
            onReadingText.text = "On: ${kanji.onReadings.joinToString(", ")}"
            kunReadingText.text = "Kun: ${kanji.kunReadings.joinToString(", ")}"
            
            // Radical information
            val radicalInfo = buildString {
                append("Radical: ")
                if (kanji.radicalNames.isNotEmpty()) {
                    append(kanji.radicalNames.first())
                } else {
                    append("Unknown")
                }
                kanji.classicalRadical?.let { 
                    append(" ($it)")
                } ?: kanji.radicalNumber?.let {
                    append(" ($it)")
                }
            }
            radicalText.text = radicalInfo
            
            // Parts information with click handling
            partsText.setParts(kanji.components)
            partsText.setOnPartClickListener(object : ClickablePartsTextView.OnPartClickListener {
                override fun onPartClicked(part: String) {
                    handlePartClick(part)
                }
            })

            // Additional info
            strokeCountText.text = "Strokes: ${kanji.strokeCount ?: "?"}"
            jlptText.text = "JLPT: N${kanji.jlptLevel ?: "?"}"

            // Load stroke order progression
            loadStrokeOrder(kanji.kanji)
            
            // Make stroke progression view clickable
            strokeProgressionView.setOnClickListener {
                openKanjiDetail(kanji.kanji)
            }
        }

        private fun loadStrokeOrder(kanjiChar: String) {

            CoroutineScope(Dispatchers.Main).launch {
                val strokes = withContext(Dispatchers.IO) {
                    val svgData = StrokeOrderCache.getSvgData(itemView.context, kanjiChar)
                    svgData?.let {
                        val parsedStrokes = KanjiVGParser.parseStrokes(it)
                        parsedStrokes
                    } ?: emptyList()
                }

                if (strokes.isNotEmpty()) {
                    strokeOrderSection.visibility = View.VISIBLE
                    strokeProgressionView.visibility = View.VISIBLE  // Make sure this is visible
                    strokeProgressionView.setStrokeData(strokes, kanjiChar)
                } else {
                    strokeOrderSection.visibility = View.GONE
                }
            }
        }

        private fun openKanjiDetail(kanjiChar: String) {
            val intent = Intent(context, WordDetailActivity::class.java).apply {
                putExtra("word", kanjiChar)
                putExtra("is_single_kanji", true)
            }
            context.startActivity(intent)
        }

        private fun handlePartClick(part: String) {
            if (part.length == 1) {
                // Handle special radical-to-kanji mappings
                val kanjiVersion = getKanjiVersionOfRadical(part[0])
                if (kanjiVersion != null) {
                    openKanjiDetail(kanjiVersion.toString())
                    return
                }
                
                // Check if the part is a single kanji character
                if (isKanji(part[0])) {
                    openKanjiDetail(part)
                }
            }
        }
        
        private fun getKanjiVersionOfRadical(char: Char): Char? {
            return when (char) {
                '⺣' -> '灬'  // Radical version -> Kanji version (fire radical)
                // Add more radical-to-kanji mappings here if needed
                else -> null
            }
        }

        private fun isKanji(char: Char): Boolean {
            val codePoint = char.code
            return (codePoint in 0x4E00..0x9FAF) || (codePoint in 0x3400..0x4DBF)
        }
    }
}