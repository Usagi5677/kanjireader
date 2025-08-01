package com.example.kanjireader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class PhrasesAdapter(private val onItemClick: ((ExampleSentence) -> Unit)? = null) : RecyclerView.Adapter<PhrasesAdapter.PhraseViewHolder>() {
    
    private var sentences: List<ExampleSentence> = emptyList()
    
    fun updateSentences(newSentences: List<ExampleSentence>) {
        sentences = newSentences
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhraseViewHolder {
        Log.d("PhrasesAdapter", "onCreateViewHolder called")
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_phrase, parent, false)
        return PhraseViewHolder(view, onItemClick)
    }
    
    override fun onBindViewHolder(holder: PhraseViewHolder, position: Int) {
        Log.d("PhrasesAdapter", "onBindViewHolder called for position $position")
        holder.bind(sentences[position])
    }
    
    override fun getItemCount(): Int = sentences.size
    
    class PhraseViewHolder(itemView: View, private val onItemClick: ((ExampleSentence) -> Unit)?) : RecyclerView.ViewHolder(itemView) {
        private val japaneseText: FuriganaTextView = itemView.findViewById(R.id.japaneseText)
        private val englishText: TextView = itemView.findViewById(R.id.englishText)
        
        fun bind(sentence: ExampleSentence) {
            // Set click listener
            itemView.setOnClickListener {
                onItemClick?.invoke(sentence)
            }
            
            // Set long click listener to copy Japanese text
            itemView.setOnLongClickListener {
                copyJapaneseText(sentence.japanese)
                true
            }
            
            // Log only if there's a search word to debug highlighting
            if (!sentence.searchWord.isNullOrEmpty()) {
                Log.d("PhrasesAdapter", "Binding sentence with searchWord: '${sentence.searchWord}' in '${sentence.japanese.take(30)}...'")
            }
            
            // Set furigana text using our FuriganaProcessor segments
            japaneseText.setText(
                sentence.japanese, 
                sentence.furiganaSegments, 
                16f, 
                sentence.searchWord
            )
            
            if (sentence.english.isNullOrEmpty()) {
                englishText.visibility = View.GONE
            } else {
                englishText.text = sentence.english
                englishText.visibility = View.VISIBLE
            }
        }
        
        private fun copyJapaneseText(text: String) {
            val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Japanese Text", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(itemView.context, "Copied!", Toast.LENGTH_SHORT).show()
        }
    }
}