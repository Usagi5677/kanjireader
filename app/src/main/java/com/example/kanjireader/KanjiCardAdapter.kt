package com.example.kanjireader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class KanjiCardAdapter(
    private val onKanjiClick: (String) -> Unit
) : RecyclerView.Adapter<KanjiCardAdapter.KanjiCardViewHolder>() {

    private var kanjiCards: List<KanjiCardInfo> = emptyList()

    fun updateData(newKanjiCards: List<KanjiCardInfo>) {
        kanjiCards = newKanjiCards
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KanjiCardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_kanji_card, parent, false)
        return KanjiCardViewHolder(view)
    }

    override fun onBindViewHolder(holder: KanjiCardViewHolder, position: Int) {
        holder.bind(kanjiCards[position])
    }

    override fun getItemCount() = kanjiCards.size

    inner class KanjiCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val kanjiCharacter: TextView = itemView.findViewById(R.id.kanjiCharacter)
        private val readings: TextView = itemView.findViewById(R.id.readings)
        private val meaning: TextView = itemView.findViewById(R.id.meaning)

        fun bind(kanjiCard: KanjiCardInfo) {
            kanjiCharacter.text = kanjiCard.kanji
            
            // Combine readings with semicolon separator
            val readingsText = buildString {
                if (kanjiCard.onReadings.isNotBlank()) {
                    append(kanjiCard.onReadings)
                }
                if (kanjiCard.onReadings.isNotBlank() && kanjiCard.kunReadings.isNotBlank()) {
                    append("; ")
                }
                if (kanjiCard.kunReadings.isNotBlank()) {
                    append(kanjiCard.kunReadings)
                }
            }
            
            readings.text = readingsText.ifBlank { "No readings available" }
            
            // Set meaning
            meaning.text = kanjiCard.primaryMeaning.ifBlank { "No meaning available" }
            
            // Set click listener
            itemView.setOnClickListener {
                onKanjiClick(kanjiCard.kanji)
            }
        }
    }
}