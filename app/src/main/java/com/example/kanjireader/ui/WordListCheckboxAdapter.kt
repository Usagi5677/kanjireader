package com.example.kanjireader.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kanjireader.R
import com.example.kanjireader.viewmodel.WordListViewModel

/**
 * Adapter for displaying word lists with checkboxes in the bottom sheet.
 */
class WordListCheckboxAdapter(
    private val onListToggled: (Long, Boolean) -> Unit,
    private val onListLongPressed: (WordListViewModel.SelectableWordList) -> Unit
) : ListAdapter<WordListViewModel.SelectableWordList, WordListCheckboxAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_word_list_checkbox, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        private val tvListName: TextView = itemView.findViewById(R.id.tvListName)
        private val tvWordCount: TextView = itemView.findViewById(R.id.tvWordCount)

        fun bind(selectableList: WordListViewModel.SelectableWordList) {
            val wordList = selectableList.wordList
            
            tvListName.text = wordList.name
            tvWordCount.text = "${wordList.wordCount} words"
            
            // Set checkbox state without triggering listener
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = selectableList.isSelected
            
            // Set click listeners - only use checkbox clicks to avoid conflicts
            itemView.setOnClickListener {
                // Toggle checkbox when item is clicked
                checkbox.isChecked = !checkbox.isChecked
            }
            
            itemView.setOnLongClickListener {
                onListLongPressed(selectableList)
                true
            }
            
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                onListToggled(wordList.listId, isChecked)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<WordListViewModel.SelectableWordList>() {
        override fun areItemsTheSame(
            oldItem: WordListViewModel.SelectableWordList,
            newItem: WordListViewModel.SelectableWordList
        ): Boolean {
            return oldItem.wordList.listId == newItem.wordList.listId
        }

        override fun areContentsTheSame(
            oldItem: WordListViewModel.SelectableWordList,
            newItem: WordListViewModel.SelectableWordList
        ): Boolean {
            return oldItem == newItem
        }
    }
}