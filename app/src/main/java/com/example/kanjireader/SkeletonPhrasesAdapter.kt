package com.example.kanjireader

import android.animation.ValueAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.RecyclerView

class SkeletonPhrasesAdapter(private val itemCount: Int = 5) : RecyclerView.Adapter<SkeletonPhrasesAdapter.SkeletonViewHolder>() {
    
    private val animators = mutableListOf<ValueAnimator>()
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkeletonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.skeleton_phrase_item, parent, false)
        return SkeletonViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: SkeletonViewHolder, position: Int) {
        holder.startShimmerAnimation()
    }
    
    override fun getItemCount(): Int = itemCount
    
    override fun onViewRecycled(holder: SkeletonViewHolder) {
        super.onViewRecycled(holder)
        holder.stopShimmerAnimation()
    }
    
    fun stopAllAnimations() {
        animators.forEach { it.cancel() }
        animators.clear()
    }
    
    inner class SkeletonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val shimmerViews = listOf(
            itemView.findViewById<View>(R.id.skeletonJapaneseText),
            itemView.findViewById<View>(R.id.skeletonJapaneseTextShort),
            itemView.findViewById<View>(R.id.skeletonEnglishText),
            itemView.findViewById<View>(R.id.skeletonEnglishTextShort)
        )
        
        private var animator: ValueAnimator? = null
        
        fun startShimmerAnimation() {
            animator = ValueAnimator.ofFloat(0.3f, 1.0f, 0.3f).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    val alpha = animation.animatedValue as Float
                    shimmerViews.forEach { view ->
                        view?.alpha = alpha
                    }
                }
                start()
            }
            animators.add(animator!!)
        }
        
        fun stopShimmerAnimation() {
            animator?.cancel()
            animator = null
        }
    }
}