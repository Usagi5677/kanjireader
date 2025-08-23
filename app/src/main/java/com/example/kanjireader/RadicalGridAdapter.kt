package com.example.kanjireader

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class RadicalSection(
    val strokeCount: Int,
    val radicals: List<RadicalItem>
)

data class RadicalItem(
    val radical: String,
    val isSelected: Boolean = false,
    val isEnabled: Boolean = true
)

class RadicalGridAdapter(
    private val onRadicalClick: (String) -> Unit
) : RecyclerView.Adapter<RadicalGridAdapter.SectionViewHolder>() {

    private var sections: List<RadicalSection> = emptyList()

    fun updateData(radicalsByStroke: Map<Int, List<String>>, selectedRadicals: Set<String>, enabledRadicals: Set<String>) {
        sections = radicalsByStroke.keys.sorted().map { strokeCount ->
            val radicals = radicalsByStroke[strokeCount]?.map { radical ->
                RadicalItem(
                    radical = radical,
                    isSelected = selectedRadicals.contains(radical),
                    isEnabled = enabledRadicals.contains(radical)
                )
            } ?: emptyList()
            
            RadicalSection(strokeCount, radicals)
        }
        
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_radical_header, parent, false)
        return SectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        holder.bind(sections[position])
    }

    override fun getItemCount() = sections.size

    inner class SectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val headerText: TextView = itemView.findViewById(R.id.headerText)
        private val radicalsGrid: RecyclerView = itemView.findViewById(R.id.radicalsGrid)
        
        fun bind(section: RadicalSection) {
            headerText.text = "${section.strokeCount} stroke${if (section.strokeCount != 1) "s" else ""}"
            
            // Setup grid for this section's radicals
            val adapter = RadicalItemAdapter(section.radicals, onRadicalClick)
            radicalsGrid.layoutManager = GridLayoutManager(itemView.context, 8) // 8 columns
            radicalsGrid.adapter = adapter
        }
    }
}

class RadicalItemAdapter(
    private val radicals: List<RadicalItem>,
    private val onRadicalClick: (String) -> Unit
) : RecyclerView.Adapter<RadicalItemAdapter.RadicalViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RadicalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_radical_button, parent, false)
        return RadicalViewHolder(view)
    }

    override fun onBindViewHolder(holder: RadicalViewHolder, position: Int) {
        holder.bind(radicals[position])
    }

    override fun getItemCount() = radicals.size

    inner class RadicalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val radicalButton: TextView = itemView.findViewById(R.id.radicalButton)

        fun bind(item: RadicalItem) {
            radicalButton.text = item.radical
            
            // Set appearance based on state
            when {
                item.isSelected -> {
                    radicalButton.setBackgroundResource(R.drawable.radical_button_selected)
                    radicalButton.setTextColor(Color.WHITE)
                    radicalButton.alpha = 1.0f
                }
                item.isEnabled -> {
                    radicalButton.setBackgroundResource(R.drawable.radical_button_background)
                    radicalButton.setTextColor(ContextCompat.getColor(itemView.context, R.color.teal_700))
                    radicalButton.alpha = 1.0f
                }
                else -> {
                    radicalButton.setBackgroundResource(R.drawable.radical_button_background)
                    radicalButton.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.darker_gray))
                    radicalButton.alpha = 0.4f
                }
            }
            
            // Set click listener
            radicalButton.setOnClickListener {
                if (item.isEnabled) {
                    onRadicalClick(item.radical)
                }
            }
            
            // Set long press listener to copy radical to clipboard
            radicalButton.setOnLongClickListener {
                val clipboardManager = itemView.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clipData = android.content.ClipData.newPlainText("Radical", item.radical)
                clipboardManager.setPrimaryClip(clipData)
                
                // Show toast confirmation
                android.widget.Toast.makeText(itemView.context, "Copied: ${item.radical}", android.widget.Toast.LENGTH_SHORT).show()
                true // Consume the long press event
            }
        }
    }
}