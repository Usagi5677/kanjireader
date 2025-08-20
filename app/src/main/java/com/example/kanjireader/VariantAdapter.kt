package com.example.kanjireader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for displaying word variants
 */
class VariantAdapter(
    private val onVariantClick: (Variant) -> Unit
) : RecyclerView.Adapter<VariantAdapter.VariantViewHolder>() {
    
    private val variants = mutableListOf<Variant>()
    
    fun updateVariants(newVariants: List<Variant>) {
        variants.clear()
        variants.addAll(newVariants)
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VariantViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_variant_grid, parent, false)
        return VariantViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: VariantViewHolder, position: Int) {
        val variant = variants[position]
        holder.bind(variant, onVariantClick)
    }
    
    override fun getItemCount(): Int = variants.size
    
    class VariantViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val variantKanjiText: TextView = itemView.findViewById(R.id.variantKanjiText)
        private val variantReadingText: TextView = itemView.findViewById(R.id.variantReadingText)
        
        fun bind(variant: Variant, onVariantClick: (Variant) -> Unit) {
            variantKanjiText.text = variant.variantKanji
            variantReadingText.text = variant.reading
            
            // Set click listener
            itemView.setOnClickListener {
                onVariantClick(variant)
            }
        }
    }
}