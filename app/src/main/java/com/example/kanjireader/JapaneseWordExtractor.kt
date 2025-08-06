package com.example.kanjireader

import android.util.Log

class JapaneseWordExtractor {

    companion object {
        private const val TAG = "JapaneseWordExtractor"

        // Japanese character ranges
        private val HIRAGANA_RANGE = Regex("[\u3040-\u309F]")
        private val KATAKANA_RANGE = Regex("[\u30A0-\u30FF]")
        private val KANJI_RANGE = Regex("[\u4E00-\u9FAF]")
        private val JAPANESE_CHAR = Regex("[\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FAF]")

        // Pattern to match Japanese words (sequences of Japanese characters)
        private val JAPANESE_WORD_PATTERN = Regex("[\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FAF]+")
        
        // Pattern to match romaji words (sequences of Latin letters)
        private val ROMAJI_WORD_PATTERN = Regex("[a-zA-Z]+")
        
        // Pattern to match mixed script sequences (Japanese + romaji together)
        private val MIXED_SCRIPT_PATTERN = Regex("[\u3040-\u309F\u30A0-\u30FF\u4E00-\u9FAFa-zA-Z]+")
    }

    private val romajiConverter = RomajiConverter()

    /**
     * Extract Japanese words from OCR text instead of individual kanji
     */
    // Update extractJapaneseWords method in JapaneseWordExtractor.kt
    // Update extractJapaneseWords method in JapaneseWordExtractor.kt
    // Update extractJapaneseWords method in JapaneseWordExtractor.kt
    fun extractJapaneseWords(text: String): Set<String> {
        val words = mutableSetOf<String>()

        // Find all Japanese word sequences
        val matches = JAPANESE_WORD_PATTERN.findAll(text)

        for (match in matches) {
            val word = match.value

            // Add the full word if it's reasonable length
            if (word.length >= 1) {
                words.add(word)

                // Add individual kanji characters for single-character lookups
                for (char in word) {
                    if (isKanji(char.toString())) {
                        words.add(char.toString())
                    }
                }
            }
        }

        Log.d(TAG, "Extracted ${words.size} word candidates")
        return words
    }


    // Add helper function to check if a character is kanji
    private fun isKanji(char: String): Boolean {
        if (char.isEmpty()) return false
        val codePoint = char.codePointAt(0)
        return (codePoint in 0x4E00..0x9FAF) || (codePoint in 0x3400..0x4DBF)
    }

    /**
     * Extract only kanji-containing words (useful for dictionary lookup)
     */
    fun extractKanjiWords(text: String): Set<String> {
        val allWords = extractJapaneseWords(text)
        val kanjiWords = allWords.filter { word ->
            word.any { char -> KANJI_RANGE.matches(char.toString()) }
        }.toSet()

        Log.d(TAG, "Filtered to ${kanjiWords.size} kanji-containing words")
        return kanjiWords
    }

    /**
     * Extract all Japanese words including hiragana/katakana only words
     */
    fun extractAllJapaneseWords(text: String): Set<String> {
        return extractJapaneseWords(text)
    }

    /**
     * Extract Japanese words with their positions in the text
     * Returns a list of WordPosition objects containing word and position info
     */
    fun extractJapaneseWordsWithPositions(text: String): List<WordPosition> {
        val words = mutableListOf<WordPosition>()

        // Find all Japanese word sequences with their positions
        val matches = JAPANESE_WORD_PATTERN.findAll(text)

        for (match in matches) {
            val word = match.value
            val startPos = match.range.first
            val endPos = match.range.last + 1

            // Add the full word if it's reasonable length
            if (word.length >= 1) {
                words.add(WordPosition(word, startPos, endPos))

                // For multi-character words, also add individual kanji
                if (word.length > 1) {
                    var currentPos = startPos
                    for (char in word) {
                        if (isKanji(char.toString())) {
                            words.add(WordPosition(char.toString(), currentPos, currentPos + 1))
                        }
                        currentPos++
                    }
                }
            }
        }

        Log.d(TAG, "Extracted ${words.size} word positions")
        return words
    }

    /**
     * Data class to hold word and its position information
     */
    data class WordPosition(
        val word: String,
        val startPosition: Int,
        val endPosition: Int
    )

    /**
     * Clean OCR text and normalize common OCR errors
     */
    fun cleanOCRText(text: String): String {
        val cleaned = text
            // Remove non-Japanese characters except common punctuation
            .replace(Regex("[^\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FAF\\s\\n.,!?()\\-:：※。、0-9]"), "")
            // Normalize common OCR mistakes
            .replace("０", "0")
            .replace("１", "1")
            .replace("２", "2")
            .replace("３", "3")
            .replace("４", "4")
            .replace("５", "5")
            .replace("６", "6")
            .replace("７", "7")
            .replace("８", "8")
            .replace("９", "9")
            // Common OCR character mistakes (based on your log)
            .replace("鹿", "能") // OCR often mistakes 能 for 鹿
            .replace("熊", "能") // Another common mistake
            .replace("険", "験") // 険 -> 験
            .replace("除", "業") // 除 -> 業
            .replace("較", "設") // 較 -> 設
            // Normalize whitespace
            .replace(Regex("\\s+"), " ")
            .trim()

        Log.d(TAG, "Cleaned OCR text: '$cleaned'")
        return cleaned
    }

    /**
     * Check if a string contains Japanese characters
     */
    fun containsJapanese(text: String): Boolean {
        return JAPANESE_CHAR.containsMatchIn(text)
    }

    /**
     * Check if a string contains kanji
     */
    fun containsKanji(text: String): Boolean {
        return text.any { char -> KANJI_RANGE.matches(char.toString()) }
    }
    
    /**
     * Extract words from mixed script text (Japanese + romaji)
     * Example: "国語woべんきょうshiteimasu" -> ["国語", "wo", "べんきょう", "shiteimasu"]
     */
    fun extractMixedScriptWords(text: String): Set<String> {
        val words = mutableSetOf<String>()
        
        Log.d(TAG, "Extracting mixed script words from: '$text'")
        
        // First, extract pure Japanese words
        val japaneseMatches = JAPANESE_WORD_PATTERN.findAll(text)
        for (match in japaneseMatches) {
            val word = match.value
            words.add(word)
            
            // Only add individual kanji characters for dictionary lookup
            for (char in word) {
                if (isKanji(char.toString())) {
                    words.add(char.toString())
                }
            }
        }
        
        // Extract romaji words and convert them to hiragana
        val romajiMatches = ROMAJI_WORD_PATTERN.findAll(text)
        for (match in romajiMatches) {
            val romajiWord = match.value
            
            // Add original romaji word
            words.add(romajiWord)
            
            // Convert to hiragana and add
            val hiraganaWord = romajiConverter.toHiraganaWithParticles(romajiWord)
            if (hiraganaWord != romajiWord) {
                words.add(hiraganaWord)
                Log.d(TAG, "Converted romaji: '$romajiWord' -> '$hiraganaWord'")
            }
        }
        
        Log.d(TAG, "Extracted ${words.size} mixed script words")
        return words
    }
    
    /**
     * Convert mixed script text to pure Japanese
     * Example: "国語woべんきょうshiteimasu" -> "国語をべんきょうしています"
     */
    fun convertMixedScriptToJapanese(text: String): String {
        return romajiConverter.convertMixedScript(text)
    }
    
    /**
     * Extract and convert romaji words to their hiragana equivalents
     * Useful for dictionary lookup
     */
    fun extractAndConvertRomajiWords(text: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        
        val romajiMatches = ROMAJI_WORD_PATTERN.findAll(text)
        for (match in romajiMatches) {
            val romajiWord = match.value
            val hiraganaWord = romajiConverter.toHiraganaWithParticles(romajiWord)
            
            if (hiraganaWord != romajiWord) {
                results.add(Pair(romajiWord, hiraganaWord))
            }
        }
        
        return results
    }
    
    /**
     * Check if text contains romaji characters
     */
    fun containsRomaji(text: String): Boolean {
        return romajiConverter.containsRomaji(text)
    }
    
    /**
     * Extract words from text that may contain both Japanese and romaji
     */
    fun extractAllWords(text: String): Set<String> {
        return if (containsRomaji(text)) {
            extractMixedScriptWords(text)
        } else {
            extractJapaneseWords(text)
        }
    }

}
