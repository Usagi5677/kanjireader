package com.example.kanjireader

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MidSectionPagerAdapter(
    private val activity: FragmentActivity,
    private val word: String,
    private val meanings: ArrayList<String>
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> MeaningsTabFragment.newInstance(meanings)
            1 -> VariantsMiddleTabFragment.newInstance(word)
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}