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
    private var realWordCount: Int = 0  // Track number of real words vs fake ones
    private var highlightedPosition: Int = -1

    fun updateData(newWordCards: List<WordCardInfo>) {
        realWordCount = newWordCards.size
        
        // Add fake cards if we have 3 or fewer real cards to ensure scrollability
        val minCardsForScroll = 5
        wordCards = if (newWordCards.size <= 3) {
            val fakeCards = mutableListOf<WordCardInfo>()
            
            // Add the real cards first
            fakeCards.addAll(newWordCards)
            
            // Add fake/empty cards to ensure scrollability
            repeat(minCardsForScroll - newWordCards.size) { index ->
                fakeCards.add(WordCardInfo(
                    word = "", // Empty word
                    reading = "",
                    meanings = "",
                    startPosition = -1, // Invalid position to identify fake cards
                    endPosition = -1,
                    baseForm = null // No base form for fake cards
                ))
            }
            fakeCards
        } else {
            newWordCards
        }
        
        // Automatically highlight first card if there are any real cards
        highlightedPosition = if (realWordCount > 0) 0 else -1
        notifyDataSetChanged()
        
        // Notify about the first card being highlighted
        if (realWordCount > 0) {
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
            val isFakeCard = wordCard.startPosition == -1 && wordCard.word.isEmpty()
            
            if (isFakeCard) {
                // Show fake cards as empty but with normal height to enable scrolling
                itemView.visibility = View.VISIBLE
                itemView.layoutParams = itemView.layoutParams?.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
                
                // Make content empty/transparent
                wordText.text = ""
                readingText.text = ""
                meaningsText.text = ""
                highlightIndicator.visibility = View.GONE
                itemView.alpha = 0.0f // Make completely transparent
                itemView.setOnClickListener(null)
            } else {
                // Show real cards normally
                itemView.visibility = View.VISIBLE
                itemView.layoutParams = itemView.layoutParams?.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
                itemView.alpha = 1.0f // Make fully visible
                
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
}