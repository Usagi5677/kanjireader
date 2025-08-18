package com.example.kanjireader.ui

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kanjireader.R
import com.example.kanjireader.database.WordListEntity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying word lists in the management interface.
 */
class WordListManagementAdapter(
    private val onListClick: (WordListEntity) -> Unit,
    private val onListLongPress: (WordListEntity) -> Unit,
    private val onEditClick: (WordListEntity) -> Unit
) : ListAdapter<WordListEntity, WordListManagementAdapter.ViewHolder>(DiffCallback()) {

    private var searchQuery: String? = null

    fun updateWithSearch(lists: List<WordListEntity>, query: String? = null) {
        searchQuery = query
        submitList(null) // Force refresh
        submitList(lists)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_word_list_management, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvListName: TextView = itemView.findViewById(R.id.tvListName)
        private val tvWordCount: TextView = itemView.findViewById(R.id.tvWordCount)
        private val tvCreatedDate: TextView = itemView.findViewById(R.id.tvCreatedDate)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)

        fun bind(wordList: WordListEntity) {
            // Apply highlighting if search query exists
            tvListName.text = if (!searchQuery.isNullOrEmpty()) {
                highlightText(wordList.name, searchQuery)
            } else {
                wordList.name
            }
            
            tvWordCount.text = "${wordList.wordCount} words"
            
            // Format creation date
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            tvCreatedDate.text = "Created ${dateFormat.format(Date(wordList.createdAt))}"
            
            itemView.setOnClickListener { onListClick(wordList) }
            itemView.setOnLongClickListener {
                onListLongPress(wordList)
                true
            }
            btnEdit.setOnClickListener { 
                onEditClick(wordList)
            }
        }
        
        private fun highlightText(text: String, query: String?): CharSequence {
            if (query.isNullOrEmpty()) return text
            
            val spannableString = SpannableString(text)
            val lowerText = text.lowercase()
            val lowerQuery = query.lowercase()
            
            var startIndex = 0
            while (startIndex < lowerText.length) {
                val index = lowerText.indexOf(lowerQuery, startIndex)
                if (index == -1) break
                
                val endIndex = index + lowerQuery.length
                
                // Apply blue text highlighting with bold
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
            
            return spannableString
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<WordListEntity>() {
        override fun areItemsTheSame(oldItem: WordListEntity, newItem: WordListEntity): Boolean {
            return oldItem.listId == newItem.listId
        }

        override fun areContentsTheSame(oldItem: WordListEntity, newItem: WordListEntity): Boolean {
            return oldItem == newItem
        }
    }
}