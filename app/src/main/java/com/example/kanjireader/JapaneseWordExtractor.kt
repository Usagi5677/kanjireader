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

    /**
     * Extract words from text using Kuromoji morphological analyzer
     * This provides proper Japanese word segmentation
     * 
     * @param text The text to extract words from
     * @param repository The dictionary repository (optional, for filtering)
     * @return List of word positions from Kuromoji tokenization
     */
    suspend fun extractWordsWithKuromoji(
        text: String,
        repository: DictionaryRepository? = null
    ): List<WordPosition> {
        val results = mutableListOf<WordPosition>()
        val particles = setOf("を", "は", "が", "に", "で", "と", "も", "の", "へ", "から", "まで", "より", "や", "か", "ね", "よ", "わ", "さ", "ぞ", "な", "だ", "です", "ます")
        
        // First, handle middle dot separators in compound words
        val textWithSplitCompounds = splitCompoundWords(text)
        
        // Preprocess text to remove spaces within Japanese words
        val processedText = reconnectJapaneseWords(textWithSplitCompounds)
        
        // Track word occurrences to find correct instance
        val wordOccurrenceCount = mutableMapOf<String, Int>()
        
        Log.d(TAG, "Original text: '$text'")
        Log.d(TAG, "Split compounds: '$textWithSplitCompounds'")
        Log.d(TAG, "Processed text: '$processedText'")
        
        try {
            // Use Kuromoji tokenizer to segment the processed text
            val tokenizer = com.atilika.kuromoji.ipadic.Tokenizer()
            val tokens = tokenizer.tokenize(processedText)
            
            var currentPosition = 0
            
            for (token in tokens) {
                val surface = token.surface
                val pos1 = token.partOfSpeechLevel1
                val pos2 = token.partOfSpeechLevel2
                val baseForm = token.baseForm ?: surface
                
                // Find the actual position of this token in the ORIGINAL text
                // We need to map from processed text position to original text position
                val tokenStartInProcessed = processedText.indexOf(surface, currentPosition)
                if (tokenStartInProcessed == -1) {
                    Log.w(TAG, "Could not find token '$surface' in processed text starting from position $currentPosition")
                    continue
                }
                
                // Track occurrence count for this word to find the correct instance
                val currentOccurrence = wordOccurrenceCount.getOrDefault(surface, 0)
                wordOccurrenceCount[surface] = currentOccurrence + 1
                
                // Find the correct occurrence of this word in original text
                val tokenStart = findNthOccurrence(text, surface, currentOccurrence)
                if (tokenStart == -1) {
                    Log.w(TAG, "Could not find occurrence #$currentOccurrence of '$surface' in original text")
                    currentPosition = tokenStartInProcessed + surface.length
                    continue
                }
                val tokenEnd = tokenStart + surface.length
                
                // Update current position for next search (in processed text)
                currentPosition = tokenStartInProcessed + surface.length
                
                // Skip punctuation and symbols
                if (pos1 == "記号" || surface.all { it.isWhitespace() }) {
                    Log.d(TAG, "Skipping punctuation/symbol: '$surface'")
                    continue
                }
                
                // Skip numbers and numeric expressions
                if (surface.all { it.isDigit() || it == '.' || it == ',' } || pos1 == "名詞" && pos2 == "数") {
                    Log.d(TAG, "Skipping number: '$surface'")
                    continue
                }
                
                // Skip single hyphens, dashes, and other connecting symbols
                if (surface in setOf("-", "－", "—", "–", "_", "/", "\\", "|")) {
                    Log.d(TAG, "Skipping connector symbol: '$surface'")
                    continue
                }
                
                // Skip particles unless they're part of compound words
                if (pos1 == "助詞" && surface in particles) {
                    Log.d(TAG, "Skipping particle: '$surface'")
                    continue
                }
                
                // Skip auxiliary verbs that are just conjugation helpers
                if (pos1 == "助動詞" && surface in setOf("だ", "です", "ます", "た", "ない")) {
                    Log.d(TAG, "Skipping auxiliary: '$surface'")
                    continue
                }
                
                // Skip non-Japanese text (like English letters or symbols)
                if (!surface.any { JAPANESE_CHAR.matches(it.toString()) }) {
                    Log.d(TAG, "Skipping non-Japanese text: '$surface'")
                    continue
                }
                
                // For verbs and adjectives, try to use the base form if available
                val wordToAdd = when {
                    pos1 == "動詞" && baseForm != surface -> {
                        // For verbs, use base form but keep surface position
                        Log.d(TAG, "Verb: '$surface' -> base form: '$baseForm'")
                        baseForm
                    }
                    pos1 == "形容詞" && baseForm != surface -> {
                        // For adjectives, use base form but keep surface position
                        Log.d(TAG, "Adjective: '$surface' -> base form: '$baseForm'")
                        baseForm
                    }
                    else -> surface
                }
                
                // Add the word with its position
                results.add(WordPosition(wordToAdd, tokenStart, tokenEnd))
                Log.d(TAG, "Added word: '$wordToAdd' at position $tokenStart-$tokenEnd (POS: $pos1/$pos2)")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Kuromoji tokenization failed: ${e.message}", e)
            // Fallback to simple extraction if Kuromoji fails
            return extractJapaneseWordsWithPositions(text)
        }
        
        Log.d(TAG, "Extracted ${results.size} words using Kuromoji")
        return results
    }
    
    /**
     * Split compound words separated by middle dot (・) into individual words
     * Example: "エレクトロニック・テキスト・センター" -> "エレクトロニック|テキスト|センター"
     * Using | as a temporary marker that won't be removed by reconnectJapaneseWords
     */
    private fun splitCompoundWords(text: String): String {
        // Replace middle dot with pipe character as temporary marker
        return text.replace("・", "|")
    }
    
    /**
     * Remove spaces within Japanese words that were incorrectly split by OCR
     * Example: "輝か しい" -> "輝かしい"
     */
    private fun reconnectJapaneseWords(text: String): String {
        val result = StringBuilder()
        var i = 0
        
        while (i < text.length) {
            val char = text[i]
            
            // If current char is a pipe marker (word boundary), keep as-is
            if (char == '|') {
                result.append(char)
                i++
            }
            // If current char is Japanese
            else if (JAPANESE_CHAR.matches(char.toString())) {
                result.append(char)
                i++
                
                // Look ahead and skip any spaces between Japanese characters
                while (i < text.length && text[i].isWhitespace()) {
                    val nextNonSpace = findNextNonSpace(text, i)
                    if (nextNonSpace != -1 && JAPANESE_CHAR.matches(text[nextNonSpace].toString())) {
                        // Skip the space(s) - they're within a Japanese word
                        i = nextNonSpace
                    } else {
                        // Keep the space - it's between Japanese and non-Japanese
                        result.append(text[i])
                        i++
                        break
                    }
                }
            } else {
                // Non-Japanese character, keep as-is
                result.append(char)
                i++
            }
        }
        
        // Replace pipe markers with spaces for word separation
        return result.toString().replace("|", " ")
    }
    
    /**
     * Find the next non-whitespace character position
     */
    private fun findNextNonSpace(text: String, startPos: Int): Int {
        for (i in startPos until text.length) {
            if (!text[i].isWhitespace()) {
                return i
            }
        }
        return -1
    }
    
    /**
     * Find the Nth occurrence of a word in text (0-based indexing)
     * Returns -1 if the occurrence doesn't exist
     */
    private fun findNthOccurrence(text: String, word: String, occurrence: Int): Int {
        var currentOccurrence = 0
        var searchStart = 0
        
        while (searchStart < text.length) {
            val foundPos = text.indexOf(word, searchStart)
            if (foundPos == -1) {
                // No more occurrences found
                return -1
            }
            
            if (currentOccurrence == occurrence) {
                // Found the desired occurrence
                return foundPos
            }
            
            // Continue searching after this occurrence
            currentOccurrence++
            searchStart = foundPos + 1
        }
        
        return -1
    }

    /**
     * Map a position in the processed text back to the original text
     * This accounts for compound word splitting and spaces that were removed during processing
     */
    private fun findOriginalPosition(
        originalText: String, 
        splitCompoundsText: String,
        processedText: String, 
        processedPos: Int,
        surface: String
    ): Int {
        // Estimate the position in original text based on processed text position
        val searchWindow = 50 // characters
        val estimatedPos = (processedPos.toFloat() / processedText.length * originalText.length).toInt()
        val searchStart = maxOf(0, estimatedPos - searchWindow)
        val searchEnd = minOf(originalText.length, estimatedPos + searchWindow)
        
        // Search for the surface text within the estimated window (not from beginning)
        var foundPos = originalText.indexOf(surface, searchStart)
        if (foundPos != -1 && foundPos < searchEnd) {
            return foundPos
        }
        
        // If still not found, try searching for the word spanning a middle dot
        // For compound word parts, they might appear after removing the middle dot
        val surfaceNoSpaces = surface.replace(" ", "")
        for (i in searchStart until searchEnd - surfaceNoSpaces.length) {
            if (matchesIgnoringMiddleDot(originalText, i, surfaceNoSpaces)) {
                return i
            }
        }
        
        return -1
    }
    
    /**
     * Check if text at position matches the target string, ignoring middle dots
     */
    private fun matchesIgnoringMiddleDot(text: String, startPos: Int, target: String): Boolean {
        var textPos = startPos
        var targetPos = 0
        
        while (targetPos < target.length && textPos < text.length) {
            // Skip middle dots in original text
            if (text[textPos] == '・') {
                textPos++
                continue
            }
            
            // Skip spaces in original text
            while (textPos < text.length && text[textPos].isWhitespace()) {
                textPos++
            }
            
            if (textPos >= text.length) return false
            
            if (text[textPos] != target[targetPos]) {
                return false
            }
            
            textPos++
            targetPos++
        }
        
        return targetPos == target.length
    }
    
    /**
     * Check if text at position matches the target string, ignoring spaces
     */
    private fun matchesWithSpaces(text: String, startPos: Int, target: String): Boolean {
        var textPos = startPos
        var targetPos = 0
        
        while (targetPos < target.length && textPos < text.length) {
            // Skip spaces in original text
            while (textPos < text.length && text[textPos].isWhitespace()) {
                textPos++
            }
            
            if (textPos >= text.length) return false
            
            if (text[textPos] != target[targetPos]) {
                return false
            }
            
            textPos++
            targetPos++
        }
        
        return targetPos == target.length
    }

}
