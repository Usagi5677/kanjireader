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

        return words
    }

    /**
     * Data class to hold word and its position information
     */
    data class WordPosition(
        val word: String,
        val startPosition: Int,
        val endPosition: Int,
        val sequenceId: Int = 0,  // Unique identifier for this word instance
        val kuromojiToken: com.atilika.kuromoji.ipadic.Token? = null  // Store original Kuromoji token
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
            }
        }
        
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
        
        
        
        // Declare tokens outside try block so it's available for grouping
        val tokens: List<com.atilika.kuromoji.ipadic.Token>
        
        try {
            // Use Kuromoji tokenizer to segment the processed text
            val tokenizer = com.atilika.kuromoji.ipadic.Tokenizer()
            tokens = tokenizer.tokenize(processedText)
            
            var currentPosition = 0
            var sequenceId = 0  // Unique identifier for each word
            
            for (token in tokens) {
                val surface = token.surface
                val pos1 = token.partOfSpeechLevel1
                val pos2 = token.partOfSpeechLevel2
                val baseForm = token.baseForm ?: surface
                
                // Find position in processed text
                val tokenStartInProcessed = processedText.indexOf(surface, currentPosition)
                if (tokenStartInProcessed == -1) {
                    continue
                }
                
                // Map the processed text position back to original text position
                val tokenStart = mapProcessedPositionToOriginal(text, processedText, tokenStartInProcessed, surface)
                if (tokenStart == -1) {
                    currentPosition = tokenStartInProcessed + surface.length
                    continue
                }
                val tokenEnd = tokenStart + surface.length
                
                // Update current position for next search (in processed text)
                currentPosition = tokenStartInProcessed + surface.length
                
                // Skip punctuation and symbols
                if (pos1 == "記号" || surface.all { it.isWhitespace() }) {
                    continue
                }
                
                // Skip numbers and numeric expressions
                if (surface.all { it.isDigit() || it == '.' || it == ',' } || pos1 == "名詞" && pos2 == "数") {
                    continue
                }
                
                // Skip single hyphens, dashes, and other connecting symbols
                if (surface in setOf("-", "－", "—", "–", "_", "/", "\\", "|")) {
                    continue
                }
                
                // Skip particles unless they're part of compound words
                if (pos1 == "助詞" && surface in particles) {
                    continue
                }
                
                // Skip auxiliary verbs that are just conjugation helpers (but keep た for past tense grouping)
                if (pos1 == "助動詞" && surface in setOf("だ", "です", "ます", "ない")) {
                    continue
                }
                
                // Skip non-Japanese text (like English letters or symbols)
                if (!surface.any { JAPANESE_CHAR.matches(it.toString()) }) {
                    continue
                }
                
                // Keep surface form for grouping, don't convert to base form yet
                val wordToAdd = surface
                
                // Add the word with its position, unique sequence ID, and original Kuromoji token
                results.add(WordPosition(wordToAdd, tokenStart, tokenEnd, sequenceId, token))
                sequenceId++
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Kuromoji tokenization failed: ${e.message}", e)
            // Fallback to simple extraction if Kuromoji fails
            return extractJapaneseWordsWithPositions(text)
        }
        
        // Group related tokens that form logical units (like verb conjugations)
        val groupedResults = groupRelatedTokens(results, processedText)
        
        return groupedResults
    }
    
    /**
     * Group adjacent tokens that form logical units (like verb conjugations)
     * This prevents fragmenting words like "吊っている" into separate highlight regions
     */
    private fun groupRelatedTokens(tokens: List<WordPosition>, text: String): List<WordPosition> {
        if (tokens.isEmpty()) return tokens
        
        // Debug: Check if we have the problematic tokens
        val hasProblematicTokens = tokens.any { it.word == "考え" || it.word == "思い" }
        if (hasProblematicTokens) {
            Log.d("TokenGroup", "Found tokens to debug: ${tokens.map { "${it.word}(${it.startPosition}-${it.endPosition})" }}")
        }
        
        // Token grouping process
        
        val grouped = mutableListOf<WordPosition>()
        var i = 0
        
        while (i < tokens.size) {
            val currentToken = tokens[i]
            val currentKuromojiToken = currentToken.kuromojiToken
            if (currentKuromojiToken == null) {
                grouped.add(currentToken)
                i++
                continue
            }
            
            val groupCandidates = mutableListOf(Pair(currentToken, currentKuromojiToken))
            var j = i + 1
            
            // Look ahead for tokens that should be grouped with current token
            while (j < tokens.size) {
                val nextToken = tokens[j]
                val nextKuromojiToken = nextToken.kuromojiToken ?: break
                
                // Check if tokens are adjacent
                val isAdjacent = nextToken.startPosition <= groupCandidates.last().first.endPosition + 2
                
                if (!isAdjacent) break
                
                // Check if current group + next token should be grouped
                val shouldGroup = shouldGroupTokens(
                    groupCandidates.last().second, 
                    nextKuromojiToken,
                    groupCandidates.last().first,
                    nextToken
                )
                
                if (shouldGroup) {
                    groupCandidates.add(Pair(nextToken, nextKuromojiToken))
                    j++
                } else {
                    break
                }
            }
            
            // Handle special three-token pattern: verb タ接続 + て + auxiliary (like 吊っ-て-いる)
            if (groupCandidates.size == 2 && j < tokens.size) {
                val thirdToken = tokens[j]
                val thirdKuromojiToken = thirdToken.kuromojiToken
                
                // Check if we have: verb タ接続 + て + auxiliary verb
                if (groupCandidates.size == 2 && thirdKuromojiToken != null &&
                    groupCandidates[0].second.partOfSpeechLevel1 == "動詞" &&
                    groupCandidates[0].second.conjugationForm == "連用タ接続" &&
                    groupCandidates[1].first.word == "て" &&
                    thirdToken.startPosition <= groupCandidates[1].first.endPosition + 2 &&
                    thirdKuromojiToken.partOfSpeechLevel1 == "動詞" &&
                    thirdKuromojiToken.partOfSpeechLevel2 == "非自立") {
                    
                    groupCandidates.add(Pair(thirdToken, thirdKuromojiToken))
                    j++
                    Log.d(TAG, "Added third token for verb+て+auxiliary pattern: ${thirdToken.word}")
                }
            }
            
            // Create grouped token
            if (groupCandidates.size > 1) {
                val firstToken = groupCandidates.first().first
                val lastToken = groupCandidates.last().first
                val groupedWord = groupCandidates.joinToString("") { it.first.word }
                
                // Group the tokens into a single word
                
                // Debug logging for merged tokens containing our problematic cases
                if (groupCandidates.any { it.first.word == "考え" || it.first.word == "思い" }) {
                    val tokenWords = groupCandidates.map { it.first.word }.joinToString("+")
                    Log.d("TokenGroup", "Created merged token: $tokenWords → $groupedWord")
                }
                
                grouped.add(WordPosition(
                    word = groupedWord,
                    startPosition = firstToken.startPosition,
                    endPosition = lastToken.endPosition,
                    sequenceId = firstToken.sequenceId, // Use first token's sequence ID
                    kuromojiToken = null // Grouped tokens don't have a single kuromoji token
                ))
                
                i = j // Skip all grouped tokens
            } else {
                // Single token, add as-is
                // Debug for ungrouped problematic tokens
                if (currentToken.word == "考え" || currentToken.word == "思い") {
                    Log.d("TokenGroup", "Token ${currentToken.word} was NOT grouped")
                }
                grouped.add(currentToken)
                i++
            }
        }
        
        return grouped
    }
    
    /**
     * Determine if two adjacent tokens should be grouped together using Kuromoji POS data
     */
    private fun shouldGroupTokens(
        token1: com.atilika.kuromoji.ipadic.Token,
        token2: com.atilika.kuromoji.ipadic.Token,
        wordPos1: WordPosition,
        wordPos2: WordPosition
    ): Boolean {
        val pos1_1 = token1.partOfSpeechLevel1
        val pos1_2 = token1.partOfSpeechLevel2
        val pos1_3 = token1.partOfSpeechLevel3
        val pos1_6 = token1.conjugationForm // Conjugation form (like 連用タ接続)
        
        val pos2_1 = token2.partOfSpeechLevel1
        val pos2_2 = token2.partOfSpeechLevel2
        val pos2_3 = token2.partOfSpeechLevel3
        
        // Debug logging for specific cases
        if (wordPos1.word == "考え" || wordPos1.word == "思い") {
            Log.d("TokenGroup", "Checking grouping: ${wordPos1.word}(${pos1_1},${pos1_2}) + ${wordPos2.word}(${pos2_1},${pos2_2})")
        }
        
        // Pattern 1: Verb with タ接続 + て + auxiliary verb (like 吊っ-て-いる)
        if (pos1_1 == "動詞" && pos1_6 == "連用タ接続" && 
            wordPos2.word == "て" && pos2_1 == "助詞") {
            return true
        }
        
        // Pattern 2: て + auxiliary verb いる/ある/いく etc. (like て-いる)
        if (wordPos1.word == "て" && pos1_1 == "助詞" &&
            pos2_1 == "動詞" && pos2_2 == "非自立") {
            return true
        }
        
        // Pattern 2.5: て + ます polite form (like て-ます/まし/ました)
        if (wordPos1.word == "て" && pos1_1 == "助詞" &&
            pos2_1 == "助動詞" && (wordPos2.word.startsWith("ま") || wordPos2.word == "です")) {
            return true
        }
        
        // Pattern 3: Verb stem + でしょう/ます/だ/た etc. (polite forms)
        if (pos1_1 == "動詞" && (wordPos2.word in setOf("ます", "だ", "です", "でしょう", "た"))) {
            if (wordPos1.word == "考え" || wordPos1.word == "思い") {
                Log.d("TokenGroup", "Pattern 3 matched: ${wordPos1.word} + ${wordPos2.word}")
            }
            return true
        }
        
        // Pattern 3.5: ます stem + past tense (like まし-た for ました)
        if (pos1_1 == "助動詞" && wordPos1.word.startsWith("ま") && 
            wordPos2.word == "た") {
            return true
        }
        
        // Pattern 4: い-adjectives + ない/くない etc.
        if (pos1_1 == "形容詞" && pos1_2 == "自立" && 
            (wordPos2.word.endsWith("ない") || wordPos2.word.endsWith("なかっ"))) {
            return true
        }
        
        // Debug logging for specific cases when no pattern matches
        if (wordPos1.word == "考え" || wordPos1.word == "思い") {
            Log.d("TokenGroup", "No pattern matched for: ${wordPos1.word} + ${wordPos2.word}")
        }
        
        return false
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
            
            // If current char is a pipe marker (word boundary), convert to space
            if (char == '|') {
                result.append(' ')
                i++
            }
            // If current char is Japanese
            else if (JAPANESE_CHAR.matches(char.toString())) {
                result.append(char)
                i++
                
                // Look ahead and skip any whitespace (including line breaks) between Japanese characters
                while (i < text.length && isWhitespaceOrLineBreak(text[i])) {
                    val nextNonSpace = findNextNonSpace(text, i)
                    if (nextNonSpace != -1 && JAPANESE_CHAR.matches(text[nextNonSpace].toString())) {
                        // Skip the whitespace/line break - they're within a Japanese word
                        i = nextNonSpace
                    } else {
                        // Keep the space - it's between Japanese and non-Japanese
                        result.append(' ') // Normalize to regular space
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
        
        return result.toString()
    }
    
    /**
     * Check if character is whitespace or line break (including full-width spaces)
     */
    private fun isWhitespaceOrLineBreak(char: Char): Boolean {
        return char.isWhitespace() || char == '\u3000' || char == '\n' || char == '\r'
    }
    
    /**
     * Find the next non-whitespace character position (including full-width spaces and line breaks)
     */
    private fun findNextNonSpace(text: String, startPos: Int): Int {
        for (i in startPos until text.length) {
            if (!isWhitespaceOrLineBreak(text[i])) {
                return i
            }
        }
        return -1
    }
    
    /**
     * Map a position from processed text back to original text
     * Build a character-by-character mapping to ensure accuracy
     */
    private fun mapProcessedPositionToOriginal(originalText: String, processedText: String, processedPos: Int, surface: String): Int {
        // If texts are identical, positions map directly
        if (originalText == processedText) {
            return processedPos
        }
        
        // Build character mapping between processed and original text
        val mapping = buildPositionMapping(originalText, processedText)
        
        // Map the processed position to original position
        val originalPos = if (processedPos < mapping.size) {
            mapping[processedPos]
        } else {
            -1
        }
        
        // Verify the mapping is correct by checking if surface text matches
        if (originalPos >= 0 && originalPos + surface.length <= originalText.length) {
            val originalSurface = originalText.substring(originalPos, originalPos + surface.length)
            if (originalSurface == surface) {
                return originalPos
            }
        }
        
        // If mapping failed, fall back to search
        return originalText.indexOf(surface)
    }
    
    /**
     * Build a mapping array from processed text positions to original text positions
     */
    private fun buildPositionMapping(originalText: String, processedText: String): IntArray {
        val mapping = IntArray(processedText.length) { -1 }
        var originalPos = 0
        var processedPos = 0
        
        while (originalPos < originalText.length && processedPos < processedText.length) {
            val originalChar = originalText[originalPos]
            val processedChar = processedText[processedPos]
            
            if (originalChar == processedChar) {
                // Characters match - direct mapping
                mapping[processedPos] = originalPos
                originalPos++
                processedPos++
            } else if (isWhitespaceOrLineBreak(originalChar)) {
                // Original has whitespace/line break that was removed in processed text
                originalPos++
            } else if (processedChar.isWhitespace()) {
                // Processed has whitespace that wasn't in original (shouldn't happen with our logic)
                processedPos++
            } else {
                // Characters don't match - try to align
                mapping[processedPos] = originalPos
                originalPos++
                processedPos++
            }
        }
        
        return mapping
    }
}
