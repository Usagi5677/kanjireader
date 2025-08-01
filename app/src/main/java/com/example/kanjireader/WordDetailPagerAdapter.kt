package com.example.kanjireader

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class WordDetailPagerAdapter(
    private val activity: FragmentActivity,
    private val word: String,
    private val wordForPhrases: String = word
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> KanjiTabFragment.newInstance(word)
            1 -> FormsTabFragment.newInstance(word)
            2 -> PhrasesTabFragment.newInstance(wordForPhrases, lazyLoad = true) // Use lazy loading to prevent auto-initialization
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}