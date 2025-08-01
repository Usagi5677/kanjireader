package com.example.kanjireader

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ConjugationGroup(
    val groupName: String,
    val conjugations: List<ConjugationItem>,
    var isExpanded: Boolean = true
)

data class ConjugationItem(
    val formName: String,
    val conjugation: String,
    val reading: String?,
    val meaning: String
)

class ConjugationAdapter(
    private val onConjugationClick: ((String) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var groups: List<ConjugationGroup> = emptyList()

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    fun updateData(newGroups: List<ConjugationGroup>) {
        groups = newGroups
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        var currentPos = 0
        for (group in groups) {
            if (position == currentPos) return TYPE_HEADER
            currentPos++
            if (group.isExpanded) {
                if (position < currentPos + group.conjugations.size) return TYPE_ITEM
                currentPos += group.conjugations.size
            }
        }
        return TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_conjugation_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_conjugation, parent, false)
                ConjugationViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItemAtPosition(position)
        when (holder) {
            is HeaderViewHolder -> holder.bind(item as ConjugationGroup) { group ->
                toggleGroup(group)
            }
            is ConjugationViewHolder -> holder.bind(item as ConjugationItem, onConjugationClick)
        }
    }

    private fun toggleGroup(group: ConjugationGroup) {
        val groupIndex = groups.indexOf(group)
        if (groupIndex == -1) return
        
        val wasExpanded = group.isExpanded
        group.isExpanded = !group.isExpanded
        
        // Calculate the position of the header
        var headerPosition = 0
        for (i in 0 until groupIndex) {
            headerPosition += 1 + (if (groups[i].isExpanded) groups[i].conjugations.size else 0)
        }
        
        if (wasExpanded) {
            // Collapsing - remove items
            notifyItemRangeRemoved(headerPosition + 1, group.conjugations.size)
        } else {
            // Expanding - add items
            notifyItemRangeInserted(headerPosition + 1, group.conjugations.size)
        }
    }

    override fun getItemCount(): Int {
        return groups.sumOf { 1 + (if (it.isExpanded) it.conjugations.size else 0) }
    }

    private fun getItemAtPosition(position: Int): Any {
        var currentPos = 0
        for (group in groups) {
            if (position == currentPos) return group
            currentPos++
            if (group.isExpanded) {
                val indexInGroup = position - currentPos
                if (indexInGroup < group.conjugations.size) {
                    return group.conjugations[indexInGroup]
                }
                currentPos += group.conjugations.size
            }
        }
        throw IndexOutOfBoundsException()
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val headerText: TextView = itemView.findViewById(R.id.headerText)
        private val arrowIcon: ImageView = itemView.findViewById(R.id.arrowIcon)

        fun bind(group: ConjugationGroup, onToggle: (ConjugationGroup) -> Unit) {
            headerText.text = group.groupName
            
            // Set arrow rotation based on expanded state (without animation during bind)
            val targetRotation = if (group.isExpanded) 270f else 90f
            arrowIcon.rotation = targetRotation
            
            // Set click listener
            itemView.setOnClickListener {
                // Animate arrow rotation first
                val newRotation = if (group.isExpanded) 90f else 270f
                val animator = ObjectAnimator.ofFloat(
                    arrowIcon, 
                    "rotation", 
                    arrowIcon.rotation, 
                    newRotation
                )
                animator.duration = 200
                animator.start()
                
                // Then toggle the group
                onToggle(group)
            }
        }
    }

    class ConjugationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val formNameText: TextView = itemView.findViewById(R.id.formNameText)
        private val conjugationText: TextView = itemView.findViewById(R.id.conjugationText)
        private val readingText: TextView = itemView.findViewById(R.id.readingText)
        private val meaningText: TextView = itemView.findViewById(R.id.meaningText)

        fun bind(item: ConjugationItem, onConjugationClick: ((String) -> Unit)? = null) {
            formNameText.text = item.formName
            conjugationText.text = item.conjugation

            // Only show reading if it's different from the conjugation
            if (item.reading != null && item.reading != item.conjugation) {
                readingText.text = item.reading
                readingText.visibility = View.VISIBLE
            } else {
                readingText.visibility = View.GONE
            }

            meaningText.text = item.meaning
            
            // Add click listeners for conjugation and reading text
            val clickListener = View.OnClickListener {
                // Use conjugation (kanji form) if available, otherwise use reading
                // This prioritizes kanji forms for better phrase search results
                val wordToSearch = item.conjugation
                onConjugationClick?.invoke(wordToSearch)
            }
            
            conjugationText.setOnClickListener(clickListener)
            if (item.reading != null && item.reading != item.conjugation) {
                readingText.setOnClickListener(clickListener)
            }
        }
    }
}