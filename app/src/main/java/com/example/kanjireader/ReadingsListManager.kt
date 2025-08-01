// Create a new file: ReadingsListManager.kt
package com.example.kanjireader

import android.util.Log

object ReadingsListManager {
    private const val TAG = "ReadingsListManager"

    private val savedWords = mutableSetOf<WordResult>()
    private var listeners = mutableListOf<() -> Unit>()

    fun addWords(words: List<WordResult>) {
        val initialSize = savedWords.size
        savedWords.addAll(words)
        val finalSize = savedWords.size
        val addedCount = finalSize - initialSize

        if (addedCount > 0) {
            Log.d(TAG, "Added $addedCount new words (total: $finalSize)")
            notifyListeners()
        }
    }

    fun removeWord(word: WordResult) {
        if (savedWords.remove(word)) {
            Log.d(TAG, "Removed word: ${word.kanji ?: word.reading}")
            notifyListeners()
        }
    }

    fun getAllWords(): List<WordResult> {
        return savedWords.toList().sortedBy { it.kanji ?: it.reading }
    }

    fun getKanjiOnlyWords(): List<WordResult> {
        return savedWords.filter { it.kanji != null }.sortedBy { it.kanji }
    }

    fun searchWords(query: String): List<WordResult> {
        if (query.isBlank()) return getAllWords()

        return savedWords.filter { word ->
            val kanji = word.kanji ?: ""
            val reading = word.reading
            val meanings = word.meanings.joinToString(" ")

            kanji.contains(query, ignoreCase = true) ||
                    reading.contains(query, ignoreCase = true) ||
                    meanings.contains(query, ignoreCase = true)
        }.sortedBy { it.kanji ?: it.reading }
    }

    fun getWordCount(): Int = savedWords.size

    fun getKanjiWordCount(): Int = savedWords.count { it.kanji != null }

    fun clearAll() {
        savedWords.clear()
        Log.d(TAG, "Cleared all saved words")
        notifyListeners()
    }

    // Listener system for UI updates
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it.invoke() }
    }
}