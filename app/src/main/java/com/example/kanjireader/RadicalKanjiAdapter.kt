package com.example.kanjireader

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RadicalKanjiAdapter(
    private val onKanjiClick: (String) -> Unit
) : RecyclerView.Adapter<RadicalKanjiAdapter.KanjiViewHolder>() {

    private var kanjiList: List<String> = emptyList()

    fun updateData(newKanjiList: List<String>) {
        kanjiList = newKanjiList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): KanjiViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_kanji_grid, parent, false)
        return KanjiViewHolder(view)
    }

    override fun onBindViewHolder(holder: KanjiViewHolder, position: Int) {
        holder.bind(kanjiList[position])
    }

    override fun getItemCount() = kanjiList.size

    inner class KanjiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val kanjiText: TextView = itemView.findViewById(R.id.kanjiText)

        fun bind(kanji: String) {
            kanjiText.text = kanji
            
            itemView.setOnClickListener {
                onKanjiClick(kanji)
            }
        }
    }
}