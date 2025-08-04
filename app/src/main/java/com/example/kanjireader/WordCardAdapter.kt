package com.example.kanjireader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WordCardAdapter(
    private val onWordCardClick: (WordCardInfo, Int) -> Unit,
    private val onWordCardScroll: (Int) -> Unit
) : RecyclerView.Adapter<WordCardAdapter.WordCardViewHolder>() {

    private var wordCards: List<WordCardInfo> = emptyList()
    private var highlightedPosition: Int = -1

    fun updateData(newWordCards: List<WordCardInfo>) {
        wordCards = newWordCards
        // Automatically highlight first card if there are any cards
        highlightedPosition = if (newWordCards.isNotEmpty()) 0 else -1
        notifyDataSetChanged()
        
        // Notify about the first card being highlighted
        if (newWordCards.isNotEmpty()) {
            onWordCardScroll(0)
        }
    }

    fun highlightCard(position: Int) {
        val oldPosition = highlightedPosition
        highlightedPosition = position
        
        // Update the old highlighted card
        if (oldPosition != -1) {
            notifyItemChanged(oldPosition)
        }
        
        // Update the new highlighted card
        if (position != -1 && position < wordCards.size) {
            notifyItemChanged(position)
        }
    }

    fun clearHighlight() {
        val oldPosition = highlightedPosition
        highlightedPosition = -1
        if (oldPosition != -1) {
            notifyItemChanged(oldPosition)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordCardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_word_card, parent, false)
        return WordCardViewHolder(view)
    }

    override fun onBindViewHolder(holder: WordCardViewHolder, position: Int) {
        holder.bind(wordCards[position], position == highlightedPosition)
    }

    override fun getItemCount() = wordCards.size

    inner class WordCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val wordText: TextView = itemView.findViewById(R.id.wordText)
        private val readingText: TextView = itemView.findViewById(R.id.readingText)
        private val meaningsText: TextView = itemView.findViewById(R.id.meaningsText)
        private val highlightIndicator: View = itemView.findViewById(R.id.highlightIndicator)

        fun bind(wordCard: WordCardInfo, isHighlighted: Boolean) {
            wordText.text = wordCard.word
            readingText.text = wordCard.reading
            meaningsText.text = wordCard.meanings
            
            // Simple show/hide for left-side highlight - no animations
            highlightIndicator.visibility = if (isHighlighted) View.VISIBLE else View.GONE
            
            // Set click listener - removed highlighting, only triggers callback
            itemView.setOnClickListener {
                onWordCardClick(wordCard, adapterPosition)
                // Removed: onWordCardScroll(adapterPosition) - no more click highlighting
            }
        }
    }
}