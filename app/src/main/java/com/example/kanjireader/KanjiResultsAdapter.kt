package com.example.kanjireader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class KanjiResultsAdapter(
    private val onItemClick: (WordResult) -> Unit
) : RecyclerView.Adapter<KanjiResultsAdapter.WordResultViewHolder>() {

    private var results: List<WordResult> = emptyList()

    fun updateResults(newResults: List<WordResult>) {
        results = newResults
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_word_result, parent, false)
        return WordResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: WordResultViewHolder, position: Int) {
        holder.bind(results[position])
    }

    override fun getItemCount(): Int = results.size

    inner class WordResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val kanjiText: TextView = itemView.findViewById(R.id.kanjiText)
        private val readingText: TextView = itemView.findViewById(R.id.readingText)
        private val meaningText: TextView = itemView.findViewById(R.id.meaningText)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

        fun bind(wordResult: WordResult) {
            // Set kanji text
            if (wordResult.kanji != null) {
                kanjiText.text = wordResult.kanji
                kanjiText.visibility = View.VISIBLE
            } else {
                kanjiText.visibility = View.GONE
            }

            // Set reading
            readingText.text = wordResult.reading

            // Set ALL meanings (no truncation)
            val allMeanings = wordResult.meanings.joinToString(", ")
            meaningText.text = allMeanings

            // Delete button click listener
            deleteButton.setOnClickListener {
                // Remove word from readings list
                ReadingsListManager.removeWord(wordResult)
                Log.d("KanjiResultsAdapter", "Deleted word: ${wordResult.kanji ?: wordResult.reading}")
            }

            // Card click listener - open WordDetailActivity
            itemView.setOnClickListener {
                openWordDetail(wordResult)
            }

            // Long press listener for clipboard copy
            itemView.setOnLongClickListener {
                copyToClipboard(wordResult)
                true
            }
        }

        private fun copyToClipboard(wordResult: WordResult) {
            val context = itemView.context
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            // Prefer kanji if available, otherwise use reading
            val textToCopy = wordResult.kanji ?: wordResult.reading

            val clip = ClipData.newPlainText("Japanese Word", textToCopy)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(context, "Copied: $textToCopy", Toast.LENGTH_SHORT).show()
            Log.d("KanjiResultsAdapter", "Copied to clipboard: $textToCopy")

            // Visual feedback animation
            animateClipboardCopy()
        }

        private fun openWordDetail(wordResult: WordResult) {
            val context = itemView.context
            val intent = Intent(context, WordDetailActivity::class.java)
            
            intent.putExtra("word", wordResult.kanji ?: wordResult.reading)
            intent.putExtra("reading", wordResult.reading)
            intent.putStringArrayListExtra("meanings", ArrayList(wordResult.meanings))
            
            context.startActivity(intent)
            Log.d("KanjiResultsAdapter", "Opening detail for: ${wordResult.kanji ?: wordResult.reading}")
        }

        private fun animateClipboardCopy() {
            // Flash animation for clipboard copy
            itemView.animate()
                .alpha(0.7f)
                .setDuration(150)
                .withEndAction {
                    itemView.animate()
                        .alpha(1f)
                        .setDuration(150)
                        .start()
                }
                .start()
        }
    }
}