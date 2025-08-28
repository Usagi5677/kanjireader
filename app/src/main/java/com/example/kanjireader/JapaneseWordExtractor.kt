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
        val kuromojiToken: com.atilika.kuromoji.ipadic.Token? = null,  // Store original Kuromoji token
        val baseForm: String? = null  // Base form for dictionary lookup
    ) {
        // Get the base form from either the explicit field or the Kuromoji token
        fun getBaseFormForLookup(): String {
            return baseForm ?: kuromojiToken?.baseForm ?: word
        }
    }

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
        // Note: Removed は, も, で from this list as they need special handling
        val particles = setOf("を", "が", "に", "と", "の", "へ", "や", "か", "ね", "よ", "わ", "ぞ", "な", "だ", "です", "この", "その", "あの", "どの")
        // Removed "ます" from particles so it can be grouped with verbs
        
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
            
            // Debug: Check if we have problematic words in the text to debug tokenization
            if (processedText.contains("考え") || processedText.contains("名付け")) {
                Log.d("KuromojiDebug", "Processed text: '$processedText'")
                Log.d("KuromojiDebug", "Raw tokens from Kuromoji (${tokens.size} tokens):")
                tokens.forEachIndexed { index, token ->
                    Log.d("KuromojiDebug", "Token $index: '${token.surface}' (${token.partOfSpeechLevel1},${token.partOfSpeechLevel2}) baseForm='${token.baseForm}' conjugation='${token.conjugationForm}'")
                }
            }
            
            var currentPosition = 0
            var sequenceId = 0  // Unique identifier for each word
            val skippedTokenIndices = mutableSetOf<Int>()  // Track tokens to skip
            
            for ((tokenIndex, token) in tokens.withIndex()) {
                val surface = token.surface
                val pos1 = token.partOfSpeechLevel1
                val pos2 = token.partOfSpeechLevel2
                val baseForm = token.baseForm ?: surface
                
                // Debug specific tokens
                if (surface == "ます" || surface == "考え" || surface == "この" || surface == "で" ||
                    surface == "良" || surface == "さ" || surface == "そう" || surface == "誰" ||
                    surface == "も" || surface == "は" || surface == "ら" || surface == "でも" || surface == "読め" ||
                    surface == "書け" || surface == "ば" || surface == "気" || surface == "付け" || 
                    surface == "よかっ" || surface == "た") {
                    Log.d("TokenFilter", "Processing token: '$surface' (${pos1},${pos2}) - particles check: ${particles.contains(surface)}")
                }
                
                // Find position in processed text, looking for exact match at expected position
                val tokenStartInProcessed = findTokenPosition(processedText, surface, currentPosition)
                
                // Debug で tokens and preceding context
                if (surface == "で") {
                    val prevText = if (tokenStartInProcessed > 0) processedText.substring(Math.max(0, tokenStartInProcessed - 5), tokenStartInProcessed) else ""
                    val nextText = if (tokenStartInProcessed >= 0 && tokenStartInProcessed + surface.length < processedText.length) 
                        processedText.substring(tokenStartInProcessed + surface.length, Math.min(processedText.length, tokenStartInProcessed + surface.length + 5)) 
                        else ""
                    Log.d("TokenPosition", "で token at position $tokenStartInProcessed: [...$prevText] で [$nextText...], POS: $pos1,$pos2")
                    if (prevText.contains("読め")) {
                        Log.d("TokenPosition", "で token follows 読め - index $tokenIndex, next token: ${if (tokenIndex + 1 < tokens.size) tokens[tokenIndex + 1].surface + " (${tokens[tokenIndex + 1].partOfSpeechLevel1})" else "N/A"}")
                    }
                }
                
                // Debug でも tokens
                if (surface == "でも") {
                    val prevText = if (tokenStartInProcessed > 0) processedText.substring(Math.max(0, tokenStartInProcessed - 5), tokenStartInProcessed) else ""
                    val nextText = if (tokenStartInProcessed >= 0 && tokenStartInProcessed + surface.length < processedText.length) 
                        processedText.substring(tokenStartInProcessed + surface.length, Math.min(processedText.length, tokenStartInProcessed + surface.length + 5)) 
                        else ""
                    Log.d("TokenPosition", "でも token at position $tokenStartInProcessed: [...$prevText] でも [$nextText...], POS: $pos1,$pos2")
                }
                
                // Debug 読め tokens
                if (surface == "読め") {
                    val prevText = if (tokenStartInProcessed > 0) processedText.substring(Math.max(0, tokenStartInProcessed - 5), tokenStartInProcessed) else ""
                    val nextText = if (tokenStartInProcessed >= 0 && tokenStartInProcessed + surface.length < processedText.length) 
                        processedText.substring(tokenStartInProcessed + surface.length, Math.min(processedText.length, tokenStartInProcessed + surface.length + 5)) 
                        else ""
                    Log.d("TokenPosition", "読め token at position $tokenStartInProcessed: [...$prevText] 読め [$nextText...], POS: $pos1,$pos2")
                    Log.d("TokenPosition", "読め token index $tokenIndex, next token: ${if (tokenIndex + 1 < tokens.size) tokens[tokenIndex + 1].surface + " (${tokens[tokenIndex + 1].partOfSpeechLevel1})" else "N/A"}")
                }
                
                if (tokenStartInProcessed == -1) {
                    // Debug logging for position mapping issues
                    if (surface.contains("名付け") || surface == "た") {
                        Log.w("PositionMap", "Failed to find '$surface' in processed text after position $currentPosition")
                    }
                    continue
                }
                
                // Map the processed text position back to original text position
                val tokenStart = mapProcessedPositionToOriginal(text, processedText, tokenStartInProcessed, surface)
                if (tokenStart == -1) {
                    currentPosition = tokenStartInProcessed + surface.length
                    continue
                }
                val tokenEnd = tokenStart + surface.length
                
                // Debug logging for problematic tokens
                if (surface.contains("名付け") || (surface == "た" && processedText.contains("名付け"))) {
                    Log.d("PositionMap", "Token '$surface' mapped: processed=$tokenStartInProcessed, original=$tokenStart-$tokenEnd")
                }
                
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
                
                // Skip な (adjectival particle), but keep it if it follows よう to form ような
                if (surface == "な") {
                    // Check if the previous token was よう to form ような
                    val shouldKeepForYouna = results.isNotEmpty() && 
                                           results.last().word == "よう" &&
                                           results.last().endPosition == tokenStart
                    if (!shouldKeepForYouna) {
                        continue
                    }
                }
                
                // Skip particles unless they're part of compound words
                // Special handling for particles that are often part of compounds
                val shouldSkipParticle = if (pos1 == "助詞") {
                    when (surface) {
                        "ら", "は", "も", "で" -> {
                            // These particles need special handling - check if part of compound
                            val isPartOfCompound = checkIfPartOfCompound(surface, tokens, tokenIndex, pos1)
                            // Debug logging for ら particle specifically
                            if (surface == "ら") {
                                Log.d("ParticleFilter", "Particle ら: isPartOfCompound=$isPartOfCompound, willSkip=${!isPartOfCompound}")
                                if (tokenIndex > 0) {
                                    val prevToken = tokens[tokenIndex - 1]
                                    Log.d("ParticleFilter", "  Previous token: '${prevToken.surface}' baseForm='${prevToken.baseForm}'")
                                }
                            }
                            !isPartOfCompound
                        }
                        "さ" -> {
                            // Keep さ for adjective patterns like 良さそう
                            false
                        }
                        in particles -> {
                            // Check if other particles are part of a compound
                            !checkIfPartOfCompound(surface, tokens, tokenIndex, pos1)
                        }
                        else -> {
                            // Keep unknown particles by default
                            false
                        }
                    }
                } else {
                    false
                }
                
                if (shouldSkipParticle) {
                    continue
                }
                
                // Skip し and だし when followed by comma (grammar connectors)
                if ((surface == "し" && ((pos1 == "動詞" && token.baseForm == "する") || (pos1 == "助詞" && pos2 == "接続助詞"))) ||
                    (surface == "だし" && pos1 == "助動詞")) {
                    // Check if next token is a comma or punctuation
                    val nextTokenIndex = tokenIndex + 1
                    if (nextTokenIndex < tokens.size) {
                        val nextToken = tokens[nextTokenIndex]
                        if (nextToken.surface == "," || nextToken.surface == "、" ||
                            nextToken.partOfSpeechLevel1 == "記号" && 
                            (nextToken.surface == "," || nextToken.surface == "、")) {
                            Log.d("ParticleFilter", "Skipping $surface followed by comma (grammar connector): pos=$pos1,$pos2")
                            continue
                        }
                    }
                }
                
                // Skip でも when it's tokenized as a single particle (助詞,副助詞)
                // BUT keep it if preceded by certain words like 誰, 私, etc. that form meaningful compounds
                if (surface == "でも" && pos1 == "助詞" && pos2 == "副助詞") {
                    // Check if preceded by a word that should group with でも
                    val shouldKeepForGrouping = tokenIndex > 0 && tokens[tokenIndex - 1].surface in setOf("誰", "私", "彼", "彼女", "あなた")
                    if (!shouldKeepForGrouping) {
                        Log.d("ParticleFilter", "Skipping でも particle (助詞,副助詞) to prevent incorrect highlighting")
                        continue
                    } else {
                        Log.d("ParticleFilter", "Keeping でも particle for grouping with ${tokens[tokenIndex - 1].surface}")
                    }
                }
                
                // Skip demonstrative determiners (この, その, あの, どの)
                // Skip あの as it should be treated like a particle
                if (pos1 == "連体詞" && surface == "あの") {
                    continue
                }
                
                // Skip auxiliary verbs that are just conjugation helpers (but keep た for past tense grouping)
                // Don't skip で/だ/です if they're part of compound expressions
                val isAuxiliaryPartOfCompound = checkIfAuxiliaryIsPartOfCompound(surface, tokens, tokenIndex, pos1)
                if (pos1 == "助動詞" && surface in setOf("だ", "です") && !isAuxiliaryPartOfCompound) {
                    continue
                }
                // Special handling for auxiliary で - keep it for compounds but skip で+も patterns
                if (pos1 == "助動詞" && surface == "で") {
                    // Check if followed by も - if so, skip this で to avoid creating "読めで" 
                    // The でも should be handled as a single token or the grouping will handle it
                    if (tokenIndex + 1 < tokens.size && tokens[tokenIndex + 1].surface == "も") {
                        Log.d("ParticleFilter", "Skipping auxiliary で followed by も to prevent incorrect word boundaries")
                        // Mark the next token (も) to be skipped as well
                        skippedTokenIndices.add(tokenIndex + 1)
                        continue
                    }
                    
                    if (!isAuxiliaryPartOfCompound) {
                        // Still check if it should be kept for other patterns
                        val shouldKeepDe = tokenIndex + 1 < tokens.size && 
                                          (tokens[tokenIndex + 1].surface == "は" || 
                                           tokens[tokenIndex + 1].surface == "ない")
                        if (!shouldKeepDe) {
                            continue
                        }
                    }
                }
                
                // Skip tokens that were marked to be skipped by previous logic
                if (skippedTokenIndices.contains(tokenIndex)) {
                    Log.d("ParticleFilter", "Skipping token $surface (marked by previous filtering logic)")
                    continue
                }
                
                // Skip non-Japanese text (like English letters or symbols)
                if (!surface.any { JAPANESE_CHAR.matches(it.toString()) }) {
                    continue
                }
                
                // Don't skip tokens that are part of compound expressions
                // Keep さ (suffix) for adjective patterns like 良さそう
                if (surface == "さ" && pos1 == "接尾") {
                    // Keep this for adjective patterns
                }
                // Keep そう for seeming/appearance patterns
                else if (surface == "そう" && (pos1 == "名詞" || pos1 == "形容詞")) {
                    // Keep this for そう patterns
                }
                
                // Keep surface form for grouping, don't convert to base form yet
                val wordToAdd = surface
                
                // Debug logging for specific problematic words  
                if (surface in setOf("し", "積み", "ました", "まし", "た", "見", "られ", "ませ", "ん", "んで", "たく", "ない", "なかっ", "したら", "ら")) {
                    Log.d("KuromojiDebug", "Processing token: '$surface' pos='$pos1,$pos2' baseForm='$baseForm'")
                }
                
                // Special handling for したら conditional - split it into した + ら tokens
                // Kuromoji sometimes tags したら as 接続詞 (conjunction) instead of 動詞 (verb)
                if (surface == "したら" && (pos1 == "動詞" || pos1 == "接続詞")) {
                    Log.d("KuromojiDebug", "Splitting したら into した + ら tokens")
                    
                    // Create WordPosition for した with original token for base form info
                    results.add(WordPosition("した", tokenStart, tokenStart + 2, sequenceId, token, "する"))
                    sequenceId++
                    
                    // Create WordPosition for ら (no base form needed for particles)
                    results.add(WordPosition("ら", tokenStart + 2, tokenEnd, sequenceId, null, null))
                    sequenceId++
                } else {
                    // Add the word with its position, unique sequence ID, and original Kuromoji token
                    results.add(WordPosition(wordToAdd, tokenStart, tokenEnd, sequenceId, token))
                    sequenceId++
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Kuromoji tokenization failed: ${e.message}", e)
            // Fallback to simple extraction if Kuromoji fails
            return extractJapaneseWordsWithPositions(text)
        }
        
        // Use the simplified Kuromoji-only approach (most reliable)
        val finalResults = groupConsecutiveVerbTokens(results, processedText)
        
        Log.d("KuromojiGrouping", "Kuromoji-only grouping: ${results.size} tokens → ${finalResults.size} tokens")
        
        return finalResults
    }
    
    /**
     * Group adjacent tokens that form logical units (like verb conjugations)
     * This prevents fragmenting words like "吊っている" into separate highlight regions
     */
    private fun groupRelatedTokens(tokens: List<WordPosition>, text: String): List<WordPosition> {
        if (tokens.isEmpty()) return tokens
        
        // Debug: Check if we have the problematic tokens
        val hasProblematicTokens = tokens.any { it.word == "考え" || it.word == "思い" || it.word.contains("名付け") }
        if (hasProblematicTokens) {
            Log.d("TokenGroup", "Found tokens to debug: ${tokens.map { "${it.word}(${it.startPosition}-${it.endPosition})" }}")
            
            // Check what comes after 考え in the text
            val kangaeToken = tokens.find { it.word == "考え" }
            if (kangaeToken != null) {
                val endPos = kangaeToken.endPosition
                val nextChars = if (endPos + 5 <= text.length) text.substring(endPos, endPos + 5) else text.substring(endPos)
                Log.d("TokenGroup", "Text after 考え (pos ${endPos}): '$nextChars'")
            }
            
            // Debug 名付け tokens specifically
            val nazukeTokens = tokens.filter { it.word.contains("名付け") }
            nazukeTokens.forEach { token ->
                Log.d("TokenGroup", "DEBUG 名付け token: '${token.word}' at ${token.startPosition}-${token.endPosition}")
            }
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
                val shouldGroup = shouldGroupTokensNew(
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
                // Pattern B: verb 連用形 + て + い (continuous form like 考え-て-い)  
                else if (groupCandidates.size == 2 && thirdKuromojiToken != null &&
                    groupCandidates[0].second.partOfSpeechLevel1 == "動詞" &&
                    groupCandidates[0].second.partOfSpeechLevel2 == "自立" &&
                    groupCandidates[0].second.conjugationForm == "連用形" &&
                    groupCandidates[1].first.word == "て" &&
                    thirdToken.word == "い" &&
                    thirdToken.startPosition <= groupCandidates[1].first.endPosition + 2 &&
                    thirdKuromojiToken.partOfSpeechLevel1 == "動詞" &&
                    thirdKuromojiToken.partOfSpeechLevel2 == "非自立") {
                    
                    Log.d("TokenGroup", "Three-token pattern B: ${groupCandidates[0].first.word}+${groupCandidates[1].first.word}+${thirdToken.word} (連用形+て+い)")
                    groupCandidates.add(Pair(thirdToken, thirdKuromojiToken))
                    j++
                }
                // Pattern C: verb 連用形 + まし + た (polite past form like 積み-まし-た)
                else if (groupCandidates.size == 2 && thirdKuromojiToken != null &&
                    groupCandidates[0].second.partOfSpeechLevel1 == "動詞" &&
                    groupCandidates[0].second.partOfSpeechLevel2 == "自立" &&
                    groupCandidates[0].second.conjugationForm == "連用形" &&
                    groupCandidates[1].first.word == "まし" &&
                    groupCandidates[1].second.partOfSpeechLevel1 == "助動詞" &&
                    thirdToken.word == "た" &&
                    thirdToken.startPosition <= groupCandidates[1].first.endPosition + 2 &&
                    thirdKuromojiToken.partOfSpeechLevel1 == "助動詞") {
                    
                    Log.d("TokenGroup", "Three-token pattern C: ${groupCandidates[0].first.word}+${groupCandidates[1].first.word}+${thirdToken.word} (連用形+まし+た)")
                    groupCandidates.add(Pair(thirdToken, thirdKuromojiToken))
                    j++
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
                    kuromojiToken = firstToken.kuromojiToken // Preserve main verb's token for further grouping
                ))
                
                i = j // Skip all grouped tokens
            } else {
                // Single token, add as-is
                // Debug for ungrouped problematic tokens
                if (currentToken.word == "考え" || currentToken.word == "思い" || currentToken.word == "考えてい") {
                    Log.d("TokenGroup", "Token ${currentToken.word} was NOT grouped")
                }
                grouped.add(currentToken)
                i++
            }
        }
        
        return grouped
    }
    
    /**
     * SIMPLIFIED APPROACH: Group consecutive verb + auxiliary tokens based on Kuromoji's base forms
     * Much simpler than rule-based approach - just stitch verb + following auxiliaries together
     */
    private fun groupConsecutiveVerbTokens(tokens: List<WordPosition>, text: String): List<WordPosition> {
        if (tokens.isEmpty()) return tokens
        
        val grouped = mutableListOf<WordPosition>()
        var i = 0
        
        while (i < tokens.size) {
            val currentToken = tokens[i]
            val currentKuromojiToken = currentToken.kuromojiToken
            
            // Debug ALL で tokens
            if (currentToken.word == "で") {
                val nextWord = if (i + 1 < tokens.size) tokens[i + 1].word else "END"
                val nextNextWord = if (i + 2 < tokens.size) tokens[i + 2].word else "END"
                Log.d("SimplifiedGrouping", "🔍 Processing で token at position $i: で → $nextWord → $nextNextWord (positions: ${currentToken.startPosition}-${currentToken.endPosition})")
            }
            
            if (currentKuromojiToken == null) {
                grouped.add(currentToken)
                i++
                continue
            }
            
            // Check if this is a main verb (動詞/自立) or any verb that could start a sequence
            val pos1_1 = currentKuromojiToken.partOfSpeechLevel1
            val pos1_2 = currentKuromojiToken.partOfSpeechLevel2
            
            // Check if this is a demonstrative pronoun that should be grouped with 時 (except あの)
            // その時, この時, どの時 are dictionary compounds, but あの時 should stay separate
            val isDemonstrativeWithJi = (pos1_1 == "連体詞" && currentToken.word in setOf("その", "この", "どの")) &&
                                        i + 1 < tokens.size &&
                                        tokens[i + 1].word == "時" &&
                                        tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "名詞" &&
                                        tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is だろ or ましょ that should be grouped with following う 
            val isVolitionalPart = ((currentToken.word == "だろ" && pos1_1 == "助動詞") ||
                                   (currentToken.word == "ましょ" && pos1_1 == "助動詞")) &&
                                  i + 1 < tokens.size &&
                                  tokens[i + 1].word == "う" &&
                                  tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助動詞" &&
                                  tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is conditional form (verb + ば)
            val isConditional = pos1_1 == "動詞" && pos1_2 == "自立" &&
                               i + 1 < tokens.size &&
                               tokens[i + 1].word == "ば" &&
                               tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助詞" &&
                               tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is past tense i-adjective (stem + た)
            val isAdjectivePast = pos1_1 == "形容詞" && pos1_2 == "自立" &&
                                 currentToken.word.endsWith("かっ") &&
                                 i + 1 < tokens.size &&
                                 tokens[i + 1].word == "た" &&
                                 tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助動詞" &&
                                 tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is 気をつけ compound expression (気 + を + つけ)
            val isKiWoTsuke = currentToken.word == "気" && pos1_1 == "名詞" &&
                             i + 2 < tokens.size &&
                             tokens[i + 1].word == "を" &&
                             tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助詞" &&
                             tokens[i + 2].word == "つけ" &&
                             tokens[i + 2].kuromojiToken?.partOfSpeechLevel1 == "動詞" &&
                             tokens[i + 1].startPosition <= currentToken.endPosition + 2 &&
                             tokens[i + 2].startPosition <= tokens[i + 1].endPosition + 2
            
            // Check if this is 朝早く compound expression (朝 + 早く)
            val isAsaHayaku = currentToken.word == "朝" && pos1_1 == "名詞" &&
                             i + 1 < tokens.size &&
                             tokens[i + 1].word == "早く" &&
                             (tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "副詞" || 
                              tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "形容詞") &&
                             tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            
            // Check if this is うまくいきそう compound expression (うまく + いき + そう)
            val isUmakuIkiSou = currentToken.word == "うまく" && (pos1_1 == "副詞" || pos1_1 == "形容詞") &&
                               i + 2 < tokens.size &&
                               tokens[i + 1].word == "いき" &&
                               tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "動詞" &&
                               tokens[i + 2].word == "そう" &&
                               (tokens[i + 2].kuromojiToken?.partOfSpeechLevel1 == "名詞" || tokens[i + 2].kuromojiToken?.partOfSpeechLevel1 == "助動詞") &&
                               tokens[i + 1].startPosition <= currentToken.endPosition + 2 &&
                               tokens[i + 2].startPosition <= tokens[i + 1].endPosition + 2
            
            // Debug logging for うまくいきそう detection
            if (currentToken.word == "うまく") {
                Log.d("SimplifiedGrouping", "Found うまく token at position $i: pos='$pos1_1', hasNext2=${i + 2 < tokens.size}")
                if (i + 1 < tokens.size && i + 2 < tokens.size) {
                    val nextToken1 = tokens[i + 1]
                    val nextToken2 = tokens[i + 2]
                    Log.d("SimplifiedGrouping", "  Next token1: '${nextToken1.word}' pos='${nextToken1.kuromojiToken?.partOfSpeechLevel1}'")
                    Log.d("SimplifiedGrouping", "  Next token2: '${nextToken2.word}' pos='${nextToken2.kuromojiToken?.partOfSpeechLevel1}'")
                    Log.d("SimplifiedGrouping", "  Expected: いき (動詞) + そう (名詞|助動詞)")
                    Log.d("SimplifiedGrouping", "  Actual: ${nextToken1.word} (${nextToken1.kuromojiToken?.partOfSpeechLevel1}) + ${nextToken2.word} (${nextToken2.kuromojiToken?.partOfSpeechLevel1})")
                    Log.d("SimplifiedGrouping", "  Position checks: ${nextToken1.startPosition} <= ${currentToken.endPosition + 2}, ${nextToken2.startPosition} <= ${nextToken1.endPosition + 2}")
                    Log.d("SimplifiedGrouping", "  isUmakuIkiSou check result: $isUmakuIkiSou")
                }
            }
            
            // Check if this is 気をつけよう compound expression (気 + つけよ + う)
            val isKiWoTsukeyou = currentToken.word == "気" && pos1_1 == "名詞" &&
                                i + 2 < tokens.size &&
                                tokens[i + 1].word == "つけよ" &&
                                tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "動詞" &&
                                tokens[i + 2].word == "う" &&
                                tokens[i + 2].kuromojiToken?.partOfSpeechLevel1 == "助動詞" &&
                                tokens[i + 1].startPosition <= currentToken.endPosition + 2 &&
                                tokens[i + 2].startPosition <= tokens[i + 1].endPosition + 2
            
            // Debug logging for 気をつけよう detection
            if (currentToken.word == "気") {
                Log.d("SimplifiedGrouping", "Found 気 token at position $i: pos='$pos1_1', hasNext2=${i + 2 < tokens.size}")
                // Show available tokens regardless of count
                Log.d("SimplifiedGrouping", "  Available tokens after 気:")
                for (j in 1..2) {
                    if (i + j < tokens.size) {
                        val nextToken = tokens[i + j]
                        Log.d("SimplifiedGrouping", "    Token $j: '${nextToken.word}' pos='${nextToken.kuromojiToken?.partOfSpeechLevel1}'")
                    } else {
                        Log.d("SimplifiedGrouping", "    Token $j: [NO TOKEN - end of list]")
                    }
                }
                if (i + 2 < tokens.size) {
                    val nextToken1 = tokens[i + 1]
                    val nextToken2 = tokens[i + 2] 
                    Log.d("SimplifiedGrouping", "  Expected: つけよ (動詞) + う (助動詞)")
                    Log.d("SimplifiedGrouping", "  Actual: ${nextToken1.word} (${nextToken1.kuromojiToken?.partOfSpeechLevel1}) + ${nextToken2.word} (${nextToken2.kuromojiToken?.partOfSpeechLevel1})")
                    Log.d("SimplifiedGrouping", "  Position checks: ${nextToken1.startPosition} <= ${currentToken.endPosition + 2}, ${nextToken2.startPosition} <= ${nextToken1.endPosition + 2}")
                    Log.d("SimplifiedGrouping", "  isKiWoTsukeyou check result: $isKiWoTsukeyou")
                }
            }
            
            // Check if this is an adjective that should be grouped with following て to form て-form
            val isAdjectiveWithTe = (pos1_1 == "形容詞" && pos1_2 == "自立") &&
                                   i + 1 < tokens.size &&
                                   tokens[i + 1].word == "て" &&
                                   tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助詞" &&
                                   tokens[i + 1].kuromojiToken?.partOfSpeechLevel2 == "接続助詞" &&
                                   tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Allow main verbs OR verbs with する base form (covers conjugated する forms like さ)
            val isVerbStart = (pos1_1 == "動詞" && pos1_2 == "自立") || 
                             (pos1_1 == "動詞" && currentKuromojiToken.baseForm == "する")
            
            // Check if this is a noun that should be grouped with 者 (person/agent suffix)
            val isNounForAgentSuffix = (pos1_1 == "名詞" && (pos1_2 == "一般" || pos1_2 == "サ変接続")) && 
                                      i + 1 < tokens.size && 
                                      tokens[i + 1].word == "者" &&
                                      tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is a noun that should be grouped with noun suffix (like 光, 性, 化, etc.)
            // But exclude date components (月+日) which should stay separate  
            val isNounForSuffix = (pos1_1 == "名詞" && (pos1_2 == "一般" || pos1_2 == "サ変接続" || pos1_2 == "副詞可能")) && 
                                  i + 1 < tokens.size && 
                                  tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "名詞" &&
                                  tokens[i + 1].kuromojiToken?.partOfSpeechLevel2 == "接尾" &&
                                  tokens[i + 1].startPosition <= currentToken.endPosition + 2 &&
                                  // Don't group 月+日 (month+day) as they're separate date components
                                  !(currentToken.word == "月" && tokens[i + 1].word == "日")
            
            // Check if this is a prefix that should be grouped with following noun
            val isPrefixForNoun = (pos1_1 == "接頭詞" && pos1_2 == "名詞接続") &&
                                  i + 1 < tokens.size &&
                                  tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "名詞" &&
                                  tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is よう that should be grouped with following な to form ような
            val isYouNa = (currentToken.word == "よう" && pos1_1 == "名詞") &&
                          i + 1 < tokens.size &&
                          tokens[i + 1].word == "な" &&
                          tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is よう that should be grouped with following に to form ように
            val isYouNi = (currentToken.word == "よう" && pos1_1 == "名詞") &&
                          i + 1 < tokens.size &&
                          tokens[i + 1].word == "に" &&
                          tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助詞" &&
                          tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is と that should be grouped with following いう to form という
            val isToIu = (currentToken.word == "と" && pos1_1 == "助詞") &&
                         i + 1 < tokens.size &&
                         tokens[i + 1].word == "いう" &&
                         tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "動詞" &&
                         tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is で that should be grouped with は+ない to form ではない
            // Note: で can be either auxiliary verb or particle
            val isDeWaNai = currentToken.word == "で" &&
                            i + 2 < tokens.size &&
                            tokens[i + 1].word == "は" &&
                            tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助詞" &&
                            tokens[i + 2].word == "ない" &&
                            (tokens[i + 2].kuromojiToken?.partOfSpeechLevel1 == "助動詞" || tokens[i + 2].kuromojiToken?.partOfSpeechLevel1 == "形容詞") &&
                            tokens[i + 1].startPosition <= currentToken.endPosition + 2 &&
                            tokens[i + 2].startPosition <= tokens[i + 1].endPosition + 2
            
            // Check if this is で that should be grouped with も to form でも
            // Note: で can be either auxiliary verb or particle
            val isDeMo = currentToken.word == "で" &&
                         i + 1 < tokens.size &&
                         tokens[i + 1].word == "も" &&
                         tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助詞" &&
                         tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is で that should be grouped with も+ない to form でもない
            // Note: で can be either auxiliary verb or particle  
            val isDeMoNai = currentToken.word == "で" &&
                            i + 2 < tokens.size &&
                            tokens[i + 1].word == "も" &&
                            tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助詞" &&
                            tokens[i + 2].word == "ない" &&
                            (tokens[i + 2].kuromojiToken?.partOfSpeechLevel1 == "助動詞" || tokens[i + 2].kuromojiToken?.partOfSpeechLevel1 == "形容詞") &&
                            tokens[i + 1].startPosition <= currentToken.endPosition + 2 &&
                            tokens[i + 2].startPosition <= tokens[i + 1].endPosition + 2
            
            if (currentToken.word == "で" && i + 1 < tokens.size && tokens[i + 1].word == "も") {
                Log.d("SimplifiedGrouping", "Found で + も pattern: isDeMo=$isDeMo, isDeMoNai=$isDeMoNai, nextToken=${tokens[i+1].word}, nextPOS=${tokens[i + 1].kuromojiToken?.partOfSpeechLevel1}")
            }
            
            if (currentToken.word == "で" && i + 2 < tokens.size) {
                Log.d("SimplifiedGrouping", "Checking で patterns: next=${tokens[i+1].word}, next2=${if (i+2 < tokens.size) tokens[i+2].word else "N/A"}")
            }
            
            // Check if this is a verb that should be grouped with たり/だり
            // したり pattern: し + たり
            val isShitari = (currentToken.word == "し" && pos1_1 == "動詞" && currentKuromojiToken.baseForm == "する") &&
                           i + 1 < tokens.size &&
                           tokens[i + 1].word == "たり" &&
                           tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助詞" &&
                           tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // General verb + たり/だり pattern
            val isTariDariPattern = (pos1_1 == "動詞" && pos1_2 == "自立" && !isShitari) &&
                                   i + 1 < tokens.size &&
                                   (tokens[i + 1].word == "たり" || tokens[i + 1].word == "だり") &&
                                   tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助詞" &&
                                   tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is あれ that should be grouped with following ば to form あれば (conditional)
            val isAreba = (currentToken.word == "あれ" && pos1_1 == "動詞") &&
                          i + 1 < tokens.size &&
                          tokens[i + 1].word == "ば" &&
                          tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助詞" &&
                          tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is an adjective stem + そう pattern (like 良さ + そう → 良さそう)
            // Also check for general noun stems that form そう expressions
            // Note: 良さ may be parsed as 良 + さ + そう, so also check for さ + そう
            val isAdjectiveWithSou = ((pos1_1 == "名詞" && pos1_2 == "形容動詞語幹") ||
                                     (pos1_1 == "名詞" && pos1_2 == "一般" && currentToken.word.endsWith("さ")) ||
                                     (currentToken.word == "さ" && pos1_1 == "接尾")) &&
                                    i + 1 < tokens.size &&
                                    tokens[i + 1].word == "そう" &&
                                    (tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "名詞" || 
                                     tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "形容詞") &&
                                    tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this needs to be a three-token pattern: 良 + さ + そう → 良さそう
            // Check for 良さそう pattern - need to handle POS tags correctly
            val isThreePartAdjective = (pos1_1 == "形容詞" && currentToken.word == "良") &&
                                      i + 2 < tokens.size &&
                                      tokens[i + 1].word == "さ" &&
                                      (tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "名詞" && 
                                       tokens[i + 1].kuromojiToken?.partOfSpeechLevel2 == "接尾") &&
                                      tokens[i + 2].word == "そう" &&
                                      (tokens[i + 2].kuromojiToken?.partOfSpeechLevel1 == "名詞" && 
                                       tokens[i + 2].kuromojiToken?.partOfSpeechLevel2 == "接尾") &&
                                      tokens[i + 1].startPosition <= currentToken.endPosition + 2 &&
                                      tokens[i + 2].startPosition <= tokens[i + 1].endPosition + 2
            
            if (currentToken.word == "良" && i + 2 < tokens.size) {
                Log.d("SimplifiedGrouping", "Checking 良さそう: next=${tokens[i+1].word}(${tokens[i+1].kuromojiToken?.partOfSpeechLevel1},${tokens[i+1].kuromojiToken?.partOfSpeechLevel2}), next2=${tokens[i+2].word}(${tokens[i+2].kuromojiToken?.partOfSpeechLevel1},${tokens[i+2].kuromojiToken?.partOfSpeechLevel2})")
            }
            
            // Check if this is したら conditional pattern (した + ら)
            // Note: Kuromoji often parses this as した + ら rather than し + たら
            // Special case: when we artificially split したら, the tokens may have different POS tags
            val isShitara = (currentToken.word == "した" && (pos1_1 == "動詞" || pos1_1 == "接続詞")) &&
                           i + 1 < tokens.size &&
                           tokens[i + 1].word == "ら" &&
                           (tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助詞" || tokens[i + 1].kuromojiToken == null) &&
                           tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Debug logging for したら pattern
            if (currentToken.word == "した" && i + 1 < tokens.size) {
                val nextToken = tokens[i + 1]
                Log.d("SimplifiedGrouping", "🔍 Checking したら: した + ${nextToken.word}(${nextToken.kuromojiToken?.partOfSpeechLevel1}) = $isShitara")
                Log.d("SimplifiedGrouping", "  Position check: current=${currentToken.endPosition}, next=${nextToken.startPosition}, diff=${nextToken.startPosition - currentToken.endPosition}")
                Log.d("SimplifiedGrouping", "  Detailed check: pos1='${pos1_1}', nextWord='${nextToken.word}', nextPOS='${nextToken.kuromojiToken?.partOfSpeechLevel1}'")
            }
            
            // Additional debug for any ら token
            if (currentToken.word == "ら") {
                Log.d("SimplifiedGrouping", "Found ら token at position ${currentToken.startPosition}-${currentToken.endPosition}")
                if (i > 0) {
                    val prevToken = tokens[i - 1] 
                    Log.d("SimplifiedGrouping", "  Previous token: '${prevToken.word}' pos=${prevToken.startPosition}-${prevToken.endPosition}")
                }
            }
            
            // Check if this is 誰でも pattern (誰 + で + も)
            // Note: で might not be recognized as 助詞, check for both で and でも pattern
            val isDaredemo = (currentToken.word == "誰" && pos1_1 == "名詞") &&
                            ((i + 2 < tokens.size &&
                              tokens[i + 1].word == "で" &&
                              tokens[i + 2].word == "も" &&
                              tokens[i + 1].startPosition <= currentToken.endPosition + 2 &&
                              tokens[i + 2].startPosition <= tokens[i + 1].endPosition + 2) ||
                             (i + 1 < tokens.size &&
                              tokens[i + 1].word == "でも" &&
                              tokens[i + 1].startPosition <= currentToken.endPosition + 2))
            
            if (currentToken.word == "誰" && i + 1 < tokens.size) {
                Log.d("SimplifiedGrouping", "Checking 誰でも: next=${tokens[i+1].word}, has2=${i+2 < tokens.size}, next2=${if (i+2 < tokens.size) tokens[i+2].word else "N/A"}")
            }
            
            // Check if this is とはいえ pattern (と + は + いえ/言え)
            val isToWaIe = (currentToken.word == "と" && pos1_1 == "助詞") &&
                          i + 2 < tokens.size &&
                          tokens[i + 1].word == "は" &&
                          tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助詞" &&
                          (tokens[i + 2].word == "言え" || tokens[i + 2].word == "いえ") &&
                          tokens[i + 2].kuromojiToken?.partOfSpeechLevel1 == "動詞" &&
                          tokens[i + 1].startPosition <= currentToken.endPosition + 2 &&
                          tokens[i + 2].startPosition <= tokens[i + 1].endPosition + 2
            
            // Check if this is 今すぐ pattern (今 + すぐ)
            val isImaSugu = (currentToken.word == "今" && (pos1_1 == "名詞" || pos1_1 == "副詞")) &&
                           i + 1 < tokens.size &&
                           tokens[i + 1].word == "すぐ" &&
                           (tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "副詞" || 
                            tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "名詞") &&
                           tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is しよう volitional pattern (しよ + う)
            val isShiyou = (currentToken.word == "しよ" && pos1_1 == "動詞" && currentKuromojiToken.baseForm == "する") &&
                          i + 1 < tokens.size &&
                          tokens[i + 1].word == "う" &&
                          tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助動詞" &&
                          tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is a general volitional そう pattern (verb stem + そう)
            // Like 直そう (なおそ + う)
            val isVolitionalSou = (pos1_1 == "動詞" && currentToken.word.endsWith("そ")) &&
                                 i + 1 < tokens.size &&
                                 tokens[i + 1].word == "う" &&
                                 tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助動詞" &&
                                 tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is a volitional ろう pattern (verb stem + ろう)
            // Like 走ろう (走ろ + う)
            val isVolitionalRou = (pos1_1 == "動詞" && currentToken.word.endsWith("ろ")) &&
                                 i + 1 < tokens.size &&
                                 tokens[i + 1].word == "う" &&
                                 tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "助動詞" &&
                                 tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is a noun that should be grouped with following なく (adverbial form of ない)
            // Forms adverbial expressions like 関係なく (regardless), 問題なく (without problem)
            val isNounNaku = (pos1_1 == "名詞" && (pos1_2 == "一般" || pos1_2 == "サ変接続" || pos1_2 == "副詞可能")) &&
                             i + 1 < tokens.size &&
                             tokens[i + 1].word == "なく" &&
                             tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "形容詞" &&
                             tokens[i + 1].startPosition <= currentToken.endPosition + 2
            
            // Check if this is the start of a compound noun sequence (consecutive nouns)
            val isCompoundNounStart = pos1_1 == "名詞" && 
                                     i + 1 < tokens.size &&
                                     tokens[i + 1].kuromojiToken?.partOfSpeechLevel1 == "名詞" &&
                                     tokens[i + 1].startPosition <= currentToken.endPosition + 2 &&
                                     // Only form compounds for certain patterns (avoid over-grouping)
                                     shouldFormCompoundNoun(currentToken, tokens, i)
            
            if (isShitara) {
                // Found した + ら - group them together to form したら conditional
                val shitaToken = currentToken
                val raToken = tokens[i + 1]
                val combinedWord = shitaToken.word + raToken.word
                
                Log.d("SimplifiedGrouping", "✅ Successfully grouping したら: ${shitaToken.word} + ${raToken.word} → $combinedWord")
                
                // For artificially split したら tokens, we need to ensure the base form is "する"
                val baseFormForLookup = if (shitaToken.baseForm == "する") "する" else currentKuromojiToken?.baseForm
                Log.d("SimplifiedGrouping", "  BaseForm for lookup: $baseFormForLookup (from shitaToken.baseForm=${shitaToken.baseForm}, currentToken.baseForm=${currentKuromojiToken?.baseForm})")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = shitaToken.startPosition,
                    endPosition = raToken.endPosition,
                    sequenceId = shitaToken.sequenceId,
                    kuromojiToken = currentKuromojiToken, // Keep verb's token info
                    baseForm = baseFormForLookup // Ensure base form is "する" for dictionary lookup
                ))
                
                // Skip both tokens
                i += 2
            } else if (isDaredemo) {
                // Found 誰 + で + も or 誰 + でも - group them together to form 誰でも
                val dareToken = currentToken
                
                // Check if it's already grouped as でも or separate で + も
                if (i + 1 < tokens.size && tokens[i + 1].word == "でも") {
                    // Already grouped as でも
                    val demoToken = tokens[i + 1]
                    val combinedWord = dareToken.word + demoToken.word
                    
                    Log.d("SimplifiedGrouping", "Grouping 誰でも (pre-grouped): ${dareToken.word} + ${demoToken.word} → $combinedWord")
                    
                    grouped.add(WordPosition(
                        word = combinedWord,
                        startPosition = dareToken.startPosition,
                        endPosition = demoToken.endPosition,
                        sequenceId = dareToken.sequenceId,
                        kuromojiToken = currentKuromojiToken // Keep 誰's token info
                    ))
                    
                    // Skip both tokens
                    i += 2
                } else if (i + 2 < tokens.size && tokens[i + 1].word == "で" && tokens[i + 2].word == "も") {
                    // Separate で + も
                    val deToken = tokens[i + 1]
                    val moToken = tokens[i + 2]
                    val combinedWord = dareToken.word + deToken.word + moToken.word
                    
                    Log.d("SimplifiedGrouping", "Grouping 誰でも (three parts): ${dareToken.word} + ${deToken.word} + ${moToken.word} → $combinedWord")
                    
                    grouped.add(WordPosition(
                        word = combinedWord,
                        startPosition = dareToken.startPosition,
                        endPosition = moToken.endPosition,
                        sequenceId = dareToken.sequenceId,
                        kuromojiToken = currentKuromojiToken // Keep 誰's token info
                    ))
                    
                    // Skip all three tokens
                    i += 3
                } else {
                    // Shouldn't happen, but add the token as-is
                    grouped.add(currentToken)
                    i++
                }
            } else if (isToWaIe) {
                // Found と + は + いえ/言え - group them together to form とはいえ
                val toToken = currentToken
                val waToken = tokens[i + 1]
                val ieToken = tokens[i + 2]
                val combinedWord = toToken.word + waToken.word + ieToken.word
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = toToken.startPosition,
                    endPosition = ieToken.endPosition,
                    sequenceId = toToken.sequenceId,
                    kuromojiToken = ieToken.kuromojiToken // Keep verb's token info as primary
                ))
                
                // Skip all three tokens
                i += 3
            } else if (isImaSugu) {
                // Found 今 + すぐ - group them together to form 今すぐ
                val imaToken = currentToken
                val suguToken = tokens[i + 1]
                val combinedWord = imaToken.word + suguToken.word
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = imaToken.startPosition,
                    endPosition = suguToken.endPosition,
                    sequenceId = imaToken.sequenceId,
                    kuromojiToken = imaToken.kuromojiToken // Keep 今's token info
                ))
                
                // Skip both tokens
                i += 2
            } else if (isShiyou) {
                // Found しよ + う - group them together to form しよう volitional
                val shiyoToken = currentToken
                val uToken = tokens[i + 1]
                val combinedWord = shiyoToken.word + uToken.word
                
                Log.d("SimplifiedGrouping", "Grouping しよう volitional: ${shiyoToken.word} + ${uToken.word} → $combinedWord")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = shiyoToken.startPosition,
                    endPosition = uToken.endPosition,
                    sequenceId = shiyoToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep main verb's token info
                ))
                
                // Skip both tokens
                i += 2
            } else if (isVolitionalSou) {
                // Found verb stem ending in そ + う - group them for volitional form (like 直そう)
                val verbToken = currentToken
                val uToken = tokens[i + 1]
                val combinedWord = verbToken.word + uToken.word
                
                Log.d("SimplifiedGrouping", "Grouping volitional そう: ${verbToken.word} + ${uToken.word} → $combinedWord")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = verbToken.startPosition,
                    endPosition = uToken.endPosition,
                    sequenceId = verbToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep verb's token info
                ))
                
                // Skip both tokens
                i += 2
            } else if (isVolitionalRou) {
                // Found verb stem ending in ろ + う - group them for volitional form (like 走ろう)
                val verbToken = currentToken
                val uToken = tokens[i + 1]
                val combinedWord = verbToken.word + uToken.word
                
                Log.d("SimplifiedGrouping", "Grouping volitional ろう: ${verbToken.word} + ${uToken.word} → $combinedWord")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = verbToken.startPosition,
                    endPosition = uToken.endPosition,
                    sequenceId = verbToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep verb's token info
                ))
                
                // Skip both tokens
                i += 2
            } else if (isYouNi) {
                // Found よう + に - group them together to form ように
                val youToken = currentToken
                val niToken = tokens[i + 1]
                val combinedWord = youToken.word + niToken.word
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = youToken.startPosition,
                    endPosition = niToken.endPosition,
                    sequenceId = youToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep よう's token info
                ))
                
                // Skip both tokens
                i += 2
            } else if (isToIu) {
                // Found と + いう - group them together to form という
                val toToken = currentToken
                val iuToken = tokens[i + 1]
                val combinedWord = toToken.word + iuToken.word
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = toToken.startPosition,
                    endPosition = iuToken.endPosition,
                    sequenceId = toToken.sequenceId,
                    kuromojiToken = iuToken.kuromojiToken // Keep いう's token info as it's the main word
                ))
                
                // Skip both tokens
                i += 2
            } else if (isDeWaNai) {
                // Found で + は + ない - group them together to form ではない
                val deToken = currentToken
                val waToken = tokens[i + 1]
                val naiToken = tokens[i + 2]
                val combinedWord = deToken.word + waToken.word + naiToken.word
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = deToken.startPosition,
                    endPosition = naiToken.endPosition,
                    sequenceId = deToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep で's token info
                ))
                
                // Skip all three tokens
                i += 3
            } else if (isDeMoNai) {
                // Found で + も + ない - group them together to form でもない
                val deToken = currentToken
                val moToken = tokens[i + 1]
                val naiToken = tokens[i + 2]
                val combinedWord = deToken.word + moToken.word + naiToken.word
                
                Log.d("SimplifiedGrouping", "🔥 EXECUTING Grouping でもない at position $i: ${deToken.word} + ${moToken.word} + ${naiToken.word} → $combinedWord")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = deToken.startPosition,
                    endPosition = naiToken.endPosition,
                    sequenceId = deToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep で's token info
                ))
                
                // Skip all three tokens
                i += 3
            } else if (isDeMo) {
                // Found で + も - group them together to form でも
                val deToken = currentToken
                val moToken = tokens[i + 1]
                val combinedWord = deToken.word + moToken.word
                
                Log.d("SimplifiedGrouping", "🔥 EXECUTING Grouping でも at position $i: ${deToken.word} + ${moToken.word} → $combinedWord")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = deToken.startPosition,
                    endPosition = moToken.endPosition,
                    sequenceId = deToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep で's token info
                ))
                
                // Skip both tokens
                i += 2
            } else if (isShitari) {
                // Found し + たり - group them together to form したり
                val shiToken = currentToken
                val tariToken = tokens[i + 1]
                val combinedWord = shiToken.word + tariToken.word
                
                Log.d("SimplifiedGrouping", "Grouping したり: ${shiToken.word} + ${tariToken.word} → $combinedWord")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = shiToken.startPosition,
                    endPosition = tariToken.endPosition,
                    sequenceId = shiToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep verb's token info
                ))
                
                // Skip both tokens
                i += 2
            } else if (isTariDariPattern) {
                // Found verb + たり/だり - group them together for listing pattern
                val verbToken = currentToken
                val tariToken = tokens[i + 1]
                val combinedWord = verbToken.word + tariToken.word
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = verbToken.startPosition,
                    endPosition = tariToken.endPosition,
                    sequenceId = verbToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep verb's token info
                ))
                
                // Skip both tokens
                i += 2
            } else if (isAreba) {
                // Found あれ + ば - group them together to form あれば (conditional)
                val areToken = currentToken
                val baToken = tokens[i + 1]
                val combinedWord = areToken.word + baToken.word
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = areToken.startPosition,
                    endPosition = baToken.endPosition,
                    sequenceId = areToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep あれ's token info
                ))
                
                // Skip both tokens
                i += 2
            } else if (isThreePartAdjective) {
                // Found 良 + さ + そう - group all three together to form 良さそう
                val adjToken = currentToken
                val saToken = tokens[i + 1]
                val souToken = tokens[i + 2]
                val combinedWord = adjToken.word + saToken.word + souToken.word
                
                Log.d("SimplifiedGrouping", "Grouping three-part adjective: ${adjToken.word} + ${saToken.word} + ${souToken.word} → $combinedWord")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = adjToken.startPosition,
                    endPosition = souToken.endPosition,
                    sequenceId = adjToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep adjective's token info
                ))
                
                // Skip all three tokens
                i += 3
            } else if (isAdjectiveWithSou) {
                // Found adjective stem + そう - group them together (like 良さそう)
                val adjectiveToken = currentToken
                val souToken = tokens[i + 1]
                val combinedWord = adjectiveToken.word + souToken.word
                
                Log.d("SimplifiedGrouping", "Grouping adjective + そう: ${adjectiveToken.word} + ${souToken.word} → $combinedWord")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = adjectiveToken.startPosition,
                    endPosition = souToken.endPosition,
                    sequenceId = adjectiveToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep adjective's token info
                ))
                
                // Skip both tokens
                i += 2
            } else if (isDemonstrativeWithJi) {
                // Found demonstrative pronoun + 時 - group them together
                val demonstrativeToken = currentToken
                val jiToken = tokens[i + 1]
                val combinedWord = demonstrativeToken.word + jiToken.word
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = demonstrativeToken.startPosition,
                    endPosition = jiToken.endPosition,
                    sequenceId = demonstrativeToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep demonstrative's token info
                ))
                
                // Skip both tokens
                i += 2
            } else if (isVolitionalPart) {
                // Found だろ/ましょ + う - group them together to form だろう/ましょう
                val volitionalToken = currentToken
                val uToken = tokens[i + 1] 
                val combinedWord = volitionalToken.word + uToken.word
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = volitionalToken.startPosition,
                    endPosition = uToken.endPosition,
                    sequenceId = volitionalToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep volitional token's info
                ))
                
                // Skip both tokens
                i += 2
            } else if (isConditional) {
                // Found verb + ば - group them together to form conditional
                val verbToken = currentToken
                val baToken = tokens[i + 1] 
                val combinedWord = verbToken.word + baToken.word
                
                // Get base form for dictionary lookup
                val baseFormForLookup = currentKuromojiToken.baseForm ?: verbToken.word
                
                Log.d("SimplifiedGrouping", "Grouping conditional: ${verbToken.word} + ${baToken.word} → $combinedWord (baseForm: $baseFormForLookup)")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = verbToken.startPosition,
                    endPosition = baToken.endPosition,
                    sequenceId = verbToken.sequenceId,
                    kuromojiToken = currentKuromojiToken, // Keep verb token's info
                    baseForm = baseFormForLookup // Set base form for dictionary lookup
                ))
                
                // Skip both tokens
                i += 2
            } else if (isAdjectivePast) {
                // Found adjective stem + た - group them together to form past tense
                val adjToken = currentToken
                val taToken = tokens[i + 1] 
                val combinedWord = adjToken.word + taToken.word
                
                // For adjectives like よかっ + た → よい/いい base form
                val baseFormForLookup = when {
                    currentKuromojiToken.baseForm != null -> currentKuromojiToken.baseForm
                    adjToken.word == "よかっ" -> "よい"
                    adjToken.word.endsWith("かっ") -> adjToken.word.dropLast(2) + "い" // General pattern: かっ → い
                    else -> adjToken.word
                }
                
                Log.d("SimplifiedGrouping", "Grouping adjective past: ${adjToken.word} + ${taToken.word} → $combinedWord (baseForm: $baseFormForLookup)")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = adjToken.startPosition,
                    endPosition = taToken.endPosition,
                    sequenceId = adjToken.sequenceId,
                    kuromojiToken = currentKuromojiToken, // Keep adjective token's info
                    baseForm = baseFormForLookup // Set base form for dictionary lookup
                ))
                
                // Skip both tokens
                i += 2
            } else if (isKiWoTsuke) {
                // Found 気 + を + つけ - group them together to form compound expression
                val kiToken = currentToken
                val woToken = tokens[i + 1] 
                val tsukeToken = tokens[i + 2]
                val combinedWord = kiToken.word + woToken.word + tsukeToken.word
                
                // Base form should be 気をつける for dictionary lookup
                val baseFormForLookup = "気をつける"
                
                Log.d("SimplifiedGrouping", "Grouping compound: ${kiToken.word} + ${woToken.word} + ${tsukeToken.word} → $combinedWord (baseForm: $baseFormForLookup)")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = kiToken.startPosition,
                    endPosition = tsukeToken.endPosition,
                    sequenceId = kiToken.sequenceId,
                    kuromojiToken = currentKuromojiToken, // Keep first token's info
                    baseForm = baseFormForLookup // Set base form for dictionary lookup
                ))
                
                // Skip all three tokens
                i += 3
            } else if (isAsaHayaku) {
                // Found 朝 + 早く - group them together to form compound time expression
                val asaToken = currentToken
                val hayakuToken = tokens[i + 1] 
                val combinedWord = asaToken.word + hayakuToken.word
                
                // Base form should be 朝早く for dictionary lookup (it's an adverb compound)
                val baseFormForLookup = "朝早く"
                
                Log.d("SimplifiedGrouping", "Grouping time compound: ${asaToken.word} + ${hayakuToken.word} → $combinedWord (baseForm: $baseFormForLookup)")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = asaToken.startPosition,
                    endPosition = hayakuToken.endPosition,
                    sequenceId = asaToken.sequenceId,
                    kuromojiToken = currentKuromojiToken, // Keep first token's info
                    baseForm = baseFormForLookup // Set base form for dictionary lookup
                ))
                
                // Skip both tokens
                i += 2
            } else if (isUmakuIkiSou) {
                // Found うまく + いき + そう - group them together to form compound expression
                val umakuToken = currentToken
                val ikiToken = tokens[i + 1] 
                val souToken = tokens[i + 2]
                val combinedWord = umakuToken.word + ikiToken.word + souToken.word
                
                // Base form should be うまくいく for dictionary lookup
                val baseFormForLookup = "うまくいく"
                
                Log.d("SimplifiedGrouping", "Grouping compound: ${umakuToken.word} + ${ikiToken.word} + ${souToken.word} → $combinedWord (baseForm: $baseFormForLookup)")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = umakuToken.startPosition,
                    endPosition = souToken.endPosition,
                    sequenceId = umakuToken.sequenceId,
                    kuromojiToken = currentKuromojiToken, // Keep first token's info
                    baseForm = baseFormForLookup // Set base form for dictionary lookup
                ))
                
                // Skip all three tokens
                i += 3
            } else if (isKiWoTsukeyou) {
                // Found 気 + つけよ + う - group them together to form compound expression
                val kiToken = currentToken
                val tsukeyoToken = tokens[i + 1] 
                val uToken = tokens[i + 2]
                val combinedWord = kiToken.word + "を" + tsukeyoToken.word + uToken.word
                
                // Base form should be 気をつける for dictionary lookup
                val baseFormForLookup = "気をつける"
                
                Log.d("SimplifiedGrouping", "Grouping compound: ${kiToken.word} + を + ${tsukeyoToken.word} + ${uToken.word} → $combinedWord (baseForm: $baseFormForLookup)")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = kiToken.startPosition,
                    endPosition = uToken.endPosition,
                    sequenceId = kiToken.sequenceId,
                    kuromojiToken = currentKuromojiToken, // Keep first token's info
                    baseForm = baseFormForLookup // Set base form for dictionary lookup
                ))
                
                // Skip all three tokens
                i += 3
            } else if (isAdjectiveWithTe) {
                // Found adjective + て - group them together (e.g., 美しくて)
                val adjectiveToken = currentToken
                val teToken = tokens[i + 1]
                val combinedWord = adjectiveToken.word + teToken.word
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = adjectiveToken.startPosition,
                    endPosition = teToken.endPosition,
                    sequenceId = adjectiveToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep adjective's token info
                ))
                
                // Skip both tokens
                i += 2
            } else if (isVerbStart) {
                // Found a verb that can start a group - start building verb group
                val verbGroup = mutableListOf<Pair<WordPosition, com.atilika.kuromoji.ipadic.Token>>()
                
                // Debug logging for verb starts, especially する forms
                if (currentKuromojiToken.baseForm == "する" || currentToken.word.contains("さ")) {
                    Log.d("SimplifiedGrouping", "Starting verb group with: ${currentToken.word} (${pos1_1},${pos1_2}) baseForm=${currentKuromojiToken.baseForm}")
                }
                
                verbGroup.add(Pair(currentToken, currentKuromojiToken))
                var j = i + 1
                
                // Check if this verb should be grouped with a following volitional form (like ましょう)
                var foundVolitional = false
                if (j < tokens.size) {
                    val nextToken = tokens[j]
                    val nextKuromojiToken = nextToken.kuromojiToken
                    if (nextKuromojiToken != null) {
                        val nextPos1 = nextKuromojiToken.partOfSpeechLevel1
                        // Check if next token is "ましょ" that would be followed by "う"
                        if (nextToken.word == "ましょ" && nextPos1 == "助動詞" && 
                            j + 1 < tokens.size && 
                            tokens[j + 1].word == "う" && 
                            tokens[j + 1].kuromojiToken?.partOfSpeechLevel1 == "助動詞" &&
                            nextToken.startPosition <= currentToken.endPosition + 2) {
                            
                            // Include both "ましょ" and "う" in the verb group
                            verbGroup.add(Pair(nextToken, nextKuromojiToken))
                            verbGroup.add(Pair(tokens[j + 1], tokens[j + 1].kuromojiToken!!))
                            j += 2
                            foundVolitional = true
                            Log.d("SimplifiedGrouping", "Added volitional form to verb: ${currentToken.word} + ${nextToken.word} + ${tokens[j-1].word}")
                        }
                    }
                }
                
                // Look for consecutive auxiliary tokens (only if we didn't find a volitional form)
                if (!foundVolitional) {
                    while (j < tokens.size) {
                        val nextToken = tokens[j]
                        val nextKuromojiToken = nextToken.kuromojiToken ?: break
                        
                        // Check if tokens are adjacent
                        val isAdjacent = nextToken.startPosition <= verbGroup.last().first.endPosition + 2
                        if (!isAdjacent) break
                        
                        // Check if it's an auxiliary or verb suffix
                        if (isAuxiliaryOrVerbSuffix(nextKuromojiToken)) {
                            verbGroup.add(Pair(nextToken, nextKuromojiToken))
                            j++
                        } else {
                            break
                        }
                    }
                }
                
                // Create grouped token if we have more than just the main verb
                if (verbGroup.size > 1) {
                    val firstToken = verbGroup.first().first
                    val lastToken = verbGroup.last().first
                    val surfaceForm = verbGroup.joinToString("") { it.first.word }
                    
                    val baseForm = currentKuromojiToken.baseForm ?: currentToken.word
                    
                    // Debug logging for simplified grouping
                    if (surfaceForm.contains("見られません") || surfaceForm.contains("考えています") || 
                        surfaceForm.contains("発表され") || surfaceForm.contains("された") || 
                        surfaceForm.contains("さ") || baseForm == "する" || surfaceForm.contains("ましょう")) {
                        Log.d("SimplifiedGrouping", "Grouped: $surfaceForm (base: $baseForm) from ${verbGroup.size} tokens")
                        verbGroup.forEach { (token, kuromojiToken) ->
                            Log.d("SimplifiedGrouping", "  - ${token.word} (${kuromojiToken.partOfSpeechLevel1},${kuromojiToken.partOfSpeechLevel2}) base=${kuromojiToken.baseForm}")
                        }
                    }
                    
                    grouped.add(WordPosition(
                        word = surfaceForm,
                        startPosition = firstToken.startPosition,
                        endPosition = lastToken.endPosition,
                        sequenceId = firstToken.sequenceId,
                        kuromojiToken = currentKuromojiToken, // Keep main verb's token info
                        baseForm = baseForm // Store the base form for proper dictionary lookup
                    ))
                } else {
                    // Single verb token, add as-is
                    grouped.add(currentToken)
                }
                
                // Skip all grouped tokens, accounting for compound verb start position
                i = j
            } else if (isNounForAgentSuffix) {
                // Found a noun followed by 者 - group them together
                val nounToken = currentToken
                val agentToken = tokens[i + 1]
                val combinedWord = nounToken.word + agentToken.word
                
                Log.d("SimplifiedGrouping", "Grouping noun + 者: ${nounToken.word} + ${agentToken.word} → $combinedWord")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = nounToken.startPosition,
                    endPosition = agentToken.endPosition,
                    sequenceId = nounToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep noun's token info
                ))
                
                // Skip both the noun and 者 tokens
                i += 2
            } else if (isNounForSuffix) {
                // Found a noun followed by noun suffix - group them together
                val nounToken = currentToken
                val suffixToken = tokens[i + 1]
                val combinedWord = nounToken.word + suffixToken.word
                
                Log.d("SimplifiedGrouping", "Grouping noun + suffix: ${nounToken.word} + ${suffixToken.word} → $combinedWord")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = nounToken.startPosition,
                    endPosition = suffixToken.endPosition,
                    sequenceId = nounToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep main noun's token info
                ))
                
                // Skip both the noun and suffix tokens
                i += 2
            } else if (isPrefixForNoun) {
                // Found a prefix followed by noun - group them together
                val prefixToken = currentToken
                val nounToken = tokens[i + 1]
                val combinedWord = prefixToken.word + nounToken.word
                
                Log.d("SimplifiedGrouping", "Grouping prefix + noun: ${prefixToken.word} + ${nounToken.word} → $combinedWord")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = prefixToken.startPosition,
                    endPosition = nounToken.endPosition,
                    sequenceId = prefixToken.sequenceId,
                    kuromojiToken = nounToken.kuromojiToken // Keep noun's token info as primary
                ))
                
                // Skip both the prefix and noun tokens
                i += 2
            } else if (isYouNa) {
                // Found よう followed by な - group them together to form ような
                val youToken = currentToken
                val naToken = tokens[i + 1]
                val combinedWord = youToken.word + naToken.word
                
                Log.d("SimplifiedGrouping", "Grouping よう + な: ${youToken.word} + ${naToken.word} → $combinedWord")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = youToken.startPosition,
                    endPosition = naToken.endPosition,
                    sequenceId = youToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep よう's token info
                ))
                
                // Skip both the よう and な tokens
                i += 2
            } else if (isNounNaku) {
                // Found noun followed by なく - group them together and convert to dictionary form (ない)
                val nounToken = currentToken
                val nakuToken = tokens[i + 1]
                val combinedWord = nounToken.word + "ない" // Use ない (dictionary form) instead of なく
                
                Log.d("SimplifiedGrouping", "Grouping noun + なく: ${nounToken.word} + ${nakuToken.word} → $combinedWord")
                
                grouped.add(WordPosition(
                    word = combinedWord,
                    startPosition = nounToken.startPosition,
                    endPosition = nakuToken.endPosition,
                    sequenceId = nounToken.sequenceId,
                    kuromojiToken = currentKuromojiToken // Keep noun's token info
                ))
                
                // Skip both the noun and なく tokens
                i += 2
            } else if (isCompoundNounStart) {
                // Found start of compound noun sequence - group consecutive technical nouns
                val compoundTokens = mutableListOf<Pair<WordPosition, com.atilika.kuromoji.ipadic.Token>>()
                compoundTokens.add(Pair(currentToken, currentKuromojiToken))
                
                var j = i + 1
                // Collect all consecutive technical nouns
                while (j < tokens.size) {
                    val nextToken = tokens[j]
                    val nextKuromojiToken = nextToken.kuromojiToken
                    
                    if (nextKuromojiToken != null &&
                        nextKuromojiToken.partOfSpeechLevel1 == "名詞" &&
                        nextToken.startPosition <= compoundTokens.last().first.endPosition + 2 &&
                        shouldFormCompoundNoun(compoundTokens.last().first, tokens, j - 1)) {
                        
                        compoundTokens.add(Pair(nextToken, nextKuromojiToken))
                        j++
                    } else {
                        break
                    }
                }
                
                // Create compound if we have multiple tokens
                if (compoundTokens.size > 1) {
                    val firstToken = compoundTokens.first().first
                    val lastToken = compoundTokens.last().first
                    val compoundWord = compoundTokens.joinToString("") { it.first.word }
                    
                    Log.d("SimplifiedGrouping", "Grouping compound noun: ${compoundTokens.map { it.first.word }.joinToString("+")} → $compoundWord")
                    
                    grouped.add(WordPosition(
                        word = compoundWord,
                        startPosition = firstToken.startPosition,
                        endPosition = lastToken.endPosition,
                        sequenceId = firstToken.sequenceId,
                        kuromojiToken = currentKuromojiToken // Keep first noun's token info
                    ))
                    
                    i = j // Skip all compound tokens
                } else {
                    // Single noun, add as-is
                    grouped.add(currentToken)
                    i++
                }
            } else {
                // Not a main verb, noun+者, noun+suffix, prefix+noun, noun+なく, compound noun, or よう+な, add as single token
                if (currentToken.word == "で") {
                    val nextWord = if (i + 1 < tokens.size) tokens[i + 1].word else "END"
                    Log.d("SimplifiedGrouping", "⚠️ で token at position $i falling through to single token processing: で → $nextWord")
                }
                grouped.add(currentToken)
                i++
            }
        }
        
        return grouped
    }
    
    /**
     * Check if consecutive nouns should form a compound noun
     * This is conservative to avoid over-grouping common words
     */
    private fun shouldFormCompoundNoun(currentToken: WordPosition, tokens: List<WordPosition>, currentIndex: Int): Boolean {
        val currentWord = currentToken.word
        val nextWord = if (currentIndex + 1 < tokens.size) tokens[currentIndex + 1].word else ""
        
        // Specific compound patterns that should be grouped
        val validCompounds = setOf(
            "太陽" to "電池",    // solar battery
            "電池" to "パネル",  // battery panel  
            "太陽電池" to "パネル", // solar battery panel (if already formed)
            "スケール" to "アップ",  // scale up
            "可能" to "性"       // possibility
        )
        
        // Check if this specific pair should form a compound
        return validCompounds.contains(currentWord to nextWord)
    }

    /**
     * Check if a token is an auxiliary verb or verb suffix that should be grouped with a main verb
     */
    private fun isAuxiliaryOrVerbSuffix(token: com.atilika.kuromoji.ipadic.Token): Boolean {
        val pos1 = token.partOfSpeechLevel1
        val pos2 = token.partOfSpeechLevel2
        val surface = token.surface
        val baseForm = token.baseForm
        
        // Special handling for volitional forms - these components will be grouped by special logic above
        // So they should not be treated as auxiliary verbs for general grouping
        if ((surface == "だろ" && baseForm == "だ") || 
            (surface == "ましょ" && baseForm == "ます") ||
            (surface == "う" && baseForm == "う")) {
            return false // Don't group these as general auxiliaries - they have special handling
        }
        
        // Certain 非自立 verbs should be treated as independent verbs, not auxiliaries
        val independentNonJirituVerbs = setOf("続ける", "始める", "終える", "出す", "込む", "上がる", "下がる")
        if (pos1 == "動詞" && pos2 == "非自立" && independentNonJirituVerbs.contains(baseForm)) {
            return false // Treat these as independent verbs
        }
        
        return pos1 == "助動詞" || // Auxiliary verbs: ます, ない, た, etc.
               (pos1 == "動詞" && pos2 == "接尾") || // Verb suffixes: られ, せ, etc.
               (pos1 == "動詞" && pos2 == "非自立") || // Non-independent verbs: いる in ている
               (pos1 == "助詞" && surface == "て") // Te particle (for continuous forms)
    }

    
    // ===== NEW RULE-BASED SYSTEM =====
    
    /**
     * NEW RULE-BASED SYSTEM: Determine if two adjacent tokens should be grouped together
     * Rules are ordered by specificity (most specific first) to prevent conflicts
     */
    private fun shouldGroupTokensNew(
        token1: com.atilika.kuromoji.ipadic.Token,
        token2: com.atilika.kuromoji.ipadic.Token,
        wordPos1: WordPosition,
        wordPos2: WordPosition
    ): Boolean {
        // Apply rules in priority order (most specific to most general)
        return shouldGroupByTeForm(token1, token2, wordPos1, wordPos2) ||
               shouldGroupVerbWithSuffix(token1, token2, wordPos1, wordPos2) ||
               shouldGroupAdjectiveConjugation(token1, token2, wordPos1, wordPos2) ||
               shouldGroupVerbWithAuxiliary(token1, token2, wordPos1, wordPos2) ||
               shouldGroupAuxiliaryChain(token1, token2, wordPos1, wordPos2)
    }
    
    /**
     * RULE 1: Te-form Grouping (Highest Priority)
     * Handles: verb + て, て + auxiliary patterns
     */
    private fun shouldGroupByTeForm(
        token1: com.atilika.kuromoji.ipadic.Token,
        token2: com.atilika.kuromoji.ipadic.Token,
        wordPos1: WordPosition,
        wordPos2: WordPosition
    ): Boolean {
        val pos1_1 = token1.partOfSpeechLevel1
        val pos1_2 = token1.partOfSpeechLevel2 
        val pos1_6 = token1.conjugationForm
        val pos2_1 = token2.partOfSpeechLevel1
        val pos2_2 = token2.partOfSpeechLevel2
        
        // Rule 1a: Verb (連用形 or 連用タ接続) + て particle
        if (pos1_1 == "動詞" && pos1_2 == "自立" && 
            (pos1_6 == "連用形" || pos1_6 == "連用タ接続") &&
            wordPos2.word == "て" && pos2_1 == "助詞") {
            return true
        }
        
        // Rule 1b: て particle + auxiliary verb (いる/ある/いく etc.)
        if (wordPos1.word == "て" && pos1_1 == "助詞" &&
            pos2_1 == "動詞" && pos2_2 == "非自立") {
            return true
        }
        
        // Rule 1c: て particle + い (continuous form fragment)
        if (wordPos1.word == "て" && pos1_1 == "助詞" &&
            wordPos2.word == "い" && pos2_1 == "動詞") {
            return true
        }
        
        // Rule 1d: て + polite auxiliary (ます/です forms)
        if (wordPos1.word == "て" && pos1_1 == "助詞" &&
            pos2_1 == "助動詞" && isPoliteAuxiliary(wordPos2.word)) {
            return true
        }
        
        return false
    }
    
    /**
     * RULE 2: Verb + Verb Suffix (High Priority)
     * Handles: passive/potential られ, causative させ patterns
     */
    private fun shouldGroupVerbWithSuffix(
        token1: com.atilika.kuromoji.ipadic.Token,
        token2: com.atilika.kuromoji.ipadic.Token,
        wordPos1: WordPosition,
        wordPos2: WordPosition
    ): Boolean {
        val pos1_1 = token1.partOfSpeechLevel1
        val pos1_2 = token1.partOfSpeechLevel2
        val pos1_6 = token1.conjugationForm
        val pos2_1 = token2.partOfSpeechLevel1
        val pos2_2 = token2.partOfSpeechLevel2
        
        // Rule 2a: Verb 未然形 + られ (passive/potential)
        if (pos1_1 == "動詞" && pos1_2 == "自立" && pos1_6 == "未然形" &&
            pos2_1 == "動詞" && pos2_2 == "接尾" && wordPos2.word == "られ") {
            return true
        }
        
        // Rule 2b: Verb suffix + auxiliary (られ + ませ etc.)
        if (pos1_1 == "動詞" && pos1_2 == "接尾" &&
            pos2_1 == "助動詞" && isCompatibleWithVerbSuffix(wordPos1.word, wordPos2.word)) {
            return true
        }
        
        return false
    }
    
    /**
     * RULE 3: Adjective Conjugations (Medium Priority) - NEW!
     * Handles: い-adjectives + ない, adjective stems + auxiliary
     */
    private fun shouldGroupAdjectiveConjugation(
        token1: com.atilika.kuromoji.ipadic.Token,
        token2: com.atilika.kuromoji.ipadic.Token,
        wordPos1: WordPosition,
        wordPos2: WordPosition
    ): Boolean {
        val pos1_1 = token1.partOfSpeechLevel1
        val pos1_2 = token1.partOfSpeechLevel2
        val pos2_1 = token2.partOfSpeechLevel1
        val pos2_2 = token2.partOfSpeechLevel2
        
        // Rule 3a: い-adjective + ない (negative forms)
        if (pos1_1 == "形容詞" && pos1_2 == "自立" &&
            ((pos2_1 == "助動詞" && wordPos2.word == "ない") ||
             (pos2_1 == "形容詞" && pos2_2 == "自立" && wordPos2.word == "ない"))) {
            return true
        }
        
        // Rule 3b: Adjective stem + auxiliary/verb (大きく + なる etc.)
        if (pos1_1 == "形容詞" && pos1_2 == "自立" && 
            (pos2_1 == "助動詞" || pos2_1 == "動詞") &&
            isCompatibleWithAdjective(wordPos1.word, wordPos2.word)) {
            return true
        }
        
        return false
    }
    
    /**
     * RULE 4: Verb Stem + Compatible Auxiliary (Medium-Low Priority)
     * Handles: basic polite forms, tense markers
     */
    private fun shouldGroupVerbWithAuxiliary(
        token1: com.atilika.kuromoji.ipadic.Token,
        token2: com.atilika.kuromoji.ipadic.Token,
        wordPos1: WordPosition,
        wordPos2: WordPosition
    ): Boolean {
        val pos1_1 = token1.partOfSpeechLevel1
        val pos1_2 = token1.partOfSpeechLevel2
        val pos1_6 = token1.conjugationForm
        val pos2_1 = token2.partOfSpeechLevel1
        
        // Rule 4a: Main verb stem + compatible auxiliary based on conjugation form
        if (pos1_1 == "動詞" && pos1_2 == "自立" && pos2_1 == "助動詞" &&
            isVerbAuxiliaryCompatible(pos1_6, wordPos2.word)) {
            return true
        }
        
        // Rule 4b: Auxiliary verb + compatible auxiliary (like い + ます)
        if (pos1_1 == "動詞" && pos1_2 == "非自立" && pos2_1 == "助動詞" &&
            isAuxiliaryVerbCompatible(wordPos1.word, wordPos2.word)) {
            return true
        }
        
        return false
    }
    
    /**
     * RULE 5: Chained Auxiliaries (Lowest Priority)
     * Handles: auxiliary + auxiliary patterns
     */
    private fun shouldGroupAuxiliaryChain(
        token1: com.atilika.kuromoji.ipadic.Token,
        token2: com.atilika.kuromoji.ipadic.Token,
        wordPos1: WordPosition,
        wordPos2: WordPosition
    ): Boolean {
        val pos1_1 = token1.partOfSpeechLevel1
        val pos2_1 = token2.partOfSpeechLevel1
        
        // Rule 5a: Auxiliary + auxiliary chains
        if (pos1_1 == "助動詞" && pos2_1 == "助動詞" &&
            isAuxiliaryChainValid(wordPos1.word, wordPos2.word)) {
            return true
        }
        
        return false
    }
    
    // ===== COMPATIBILITY VALIDATION FUNCTIONS =====
    
    private fun isPoliteAuxiliary(word: String): Boolean {
        return word.startsWith("ま") || word == "です"
    }
    
    private fun isCompatibleWithVerbSuffix(suffix: String, auxiliary: String): Boolean {
        return when (suffix) {
            "られ" -> auxiliary in setOf("ます", "ませ", "た", "ない")
            else -> false
        }
    }
    
    private fun isCompatibleWithAdjective(adjective: String, auxiliary: String): Boolean {
        return when {
            adjective.endsWith("く") -> auxiliary in setOf("ない", "なる", "する")
            else -> false
        }
    }
    
    private fun isVerbAuxiliaryCompatible(conjugationForm: String?, auxiliary: String): Boolean {
        return when (conjugationForm) {
            "連用形" -> auxiliary in setOf("ます", "まし", "た", "て", "ませ", "たい", "たく")
            "未然形" -> auxiliary in setOf("ない", "なかっ", "れる", "られる", "せる", "させる")
            "基本形" -> auxiliary in setOf("だ", "です", "でしょう")
            else -> auxiliary in setOf("ます", "だ", "です", "た") // Default safe auxiliaries
        }
    }
    
    private fun isAuxiliaryChainValid(aux1: String, aux2: String): Boolean {
        return when (aux1) {
            "まし" -> aux2 == "た"
            "ませ" -> aux2 in setOf("ん", "んで")
            "なかっ" -> aux2 == "た"
            "たく" -> aux2 == "ない"
            else -> false
        }
    }
    
    private fun isAuxiliaryVerbCompatible(auxVerb: String, auxiliary: String): Boolean {
        return when (auxVerb) {
            "い" -> auxiliary in setOf("ます", "まし", "ませ", "た", "だ", "です") // continuous form auxiliary
            "ある" -> auxiliary in setOf("ます", "まし", "ませ", "だ", "です") // existence auxiliary
            "いる" -> auxiliary in setOf("ます", "まし", "ませ", "だ", "です") // continuous existence
            else -> false
        }
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
     * Find the next occurrence of a token in text, with better position tracking
     * This addresses issues with multiple identical tokens getting wrong positions
     */
    private fun findTokenPosition(text: String, surface: String, startPosition: Int): Int {
        var searchPos = startPosition
        while (searchPos < text.length) {
            val foundPos = text.indexOf(surface, searchPos)
            if (foundPos == -1) return -1
            
            // Check if this occurrence makes sense contextually
            // For now, just return the first occurrence after startPosition
            if (foundPos >= startPosition) {
                return foundPos
            }
            searchPos = foundPos + 1
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
        
        // For complex cases, use the simple approach if processed and original are similar
        // This fixes the issue where character mapping gets confused with multiple identical tokens
        if (processedPos >= 0 && processedPos + surface.length <= processedText.length) {
            val processedSurface = processedText.substring(processedPos, processedPos + surface.length)
            if (processedSurface == surface && processedPos + surface.length <= originalText.length) {
                val originalSurface = originalText.substring(processedPos, processedPos + surface.length)
                if (originalSurface == surface) {
                    // Debug logging for position mapping fixes
                    if (surface.contains("名付け") || (surface == "た" && processedText.contains("名付け"))) {
                        Log.d("PositionMap", "Direct position mapping for '$surface': $processedPos -> $processedPos")
                    }
                    return processedPos
                }
            }
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
                // Debug logging for complex mapping
                if (surface.contains("名付け") || (surface == "た" && processedText.contains("名付け"))) {
                    Log.d("PositionMap", "Complex position mapping for '$surface': $processedPos -> $originalPos")
                }
                return originalPos
            }
        }
        
        // If mapping failed, fall back to search but log the issue
        if (surface.contains("名付け") || (surface == "た" && processedText.contains("名付け"))) {
            Log.w("PositionMap", "Mapping failed for '$surface' at processed pos $processedPos, falling back to search")
        }
        return originalText.indexOf(surface)
    }
    
    /**
     * Check if an auxiliary verb is part of a compound expression
     */
    private fun checkIfAuxiliaryIsPartOfCompound(
        surface: String, 
        tokens: List<com.atilika.kuromoji.ipadic.Token>, 
        currentIndex: Int, 
        pos1: String
    ): Boolean {
        when (surface) {
            "で" -> {
                // Keep で only if followed by は+ない (for ではない) or も (for でも)
                if (currentIndex + 1 < tokens.size) {
                    val nextToken = tokens[currentIndex + 1]
                    if (nextToken.surface == "も") {
                        return true // Keep for でも
                    }
                    // Keep で only if followed by は AND then ない (for ではない)
                    if (nextToken.surface == "は" && currentIndex + 2 < tokens.size) {
                        val thirdToken = tokens[currentIndex + 2]
                        if (thirdToken.surface == "ない") {
                            return true // Keep for ではない
                        }
                    }
                    // Don't keep で for standalone では
                }
            }
        }
        return false
    }
    
    /**
     * Check if a particle is part of a compound expression that should be grouped
     */
    private fun checkIfPartOfCompound(
        surface: String, 
        tokens: List<com.atilika.kuromoji.ipadic.Token>, 
        currentIndex: Int, 
        pos1: String
    ): Boolean {
        // Check for particles that are part of compound expressions
        when (surface) {
            "に" -> {
                // Keep に if preceded by よう to form ように
                if (currentIndex > 0) {
                    val prevToken = tokens[currentIndex - 1]
                    if (prevToken.surface == "よう" && prevToken.partOfSpeechLevel1 == "名詞") {
                        return true
                    }
                }
            }
            "で" -> {
                // Keep で only if followed by は+ない (for ではない) or も (for でも)
                if (currentIndex + 1 < tokens.size) {
                    val nextToken = tokens[currentIndex + 1]
                    if (nextToken.surface == "も") {
                        return true // Keep for でも
                    }
                    // Keep で only if followed by は AND then ない (for ではない)
                    if (nextToken.surface == "は" && currentIndex + 2 < tokens.size) {
                        val thirdToken = tokens[currentIndex + 2]
                        if (thirdToken.surface == "ない") {
                            return true // Keep for ではない
                        }
                    }
                    // Don't keep で for standalone では
                }
                // Keep で if preceded by 誰 and followed by も to form 誰でも
                if (currentIndex > 0 && currentIndex + 1 < tokens.size) {
                    val prevToken = tokens[currentIndex - 1]
                    val nextToken = tokens[currentIndex + 1]
                    if (prevToken.surface == "誰" && prevToken.partOfSpeechLevel1 == "名詞" &&
                        nextToken.surface == "も") {
                        return true
                    }
                }
            }
            "は" -> {
                // Keep は only if it's part of specific patterns
                if (currentIndex > 0) {
                    val prevToken = tokens[currentIndex - 1]
                    
                    // Keep は if preceded by で AND followed by ない to form ではない
                    if (prevToken.surface == "で" && currentIndex + 1 < tokens.size) {
                        val nextToken = tokens[currentIndex + 1]
                        if (nextToken.surface == "ない") {
                            return true // Keep for ではない pattern
                        }
                        // Don't keep は for standalone では (will be filtered out)
                        return false
                    }
                    
                    // Keep は if preceded by と to form とは (for とはいえ pattern)
                    if (prevToken.surface == "と" && prevToken.partOfSpeechLevel1 == "助詞") {
                        return true
                    }
                }
            }
            "も" -> {
                // Keep も if preceded by で to form でも (check both auxiliary and particle で)
                if (currentIndex > 0) {
                    val prevToken = tokens[currentIndex - 1]
                    if (prevToken.surface == "で") {
                        return true // Always keep も after で to form でも
                    }
                    // Also keep も if it's part of 誰でも pattern
                    if (currentIndex > 1 && 
                        prevToken.surface == "で" && 
                        tokens[currentIndex - 2].surface == "誰") {
                        return true
                    }
                }
            }
            "と" -> {
                // Keep と if followed by いう, or は+いえ (for とはいえ pattern)
                if (currentIndex + 1 < tokens.size) {
                    val nextToken = tokens[currentIndex + 1]
                    if (nextToken.surface == "いう" && nextToken.partOfSpeechLevel1 == "動詞") {
                        return true
                    }
                    // Check for とはいえ pattern (と+は+いえ)
                    if (nextToken.surface == "は" && currentIndex + 2 < tokens.size) {
                        val thirdToken = tokens[currentIndex + 2]
                        if ((thirdToken.surface == "言え" || thirdToken.surface == "いえ") && 
                            thirdToken.partOfSpeechLevel1 == "動詞") {
                            return true
                        }
                    }
                }
            }
            "ら" -> {
                // Keep ら if preceded by した to form したら conditional
                if (currentIndex > 0) {
                    val prevToken = tokens[currentIndex - 1]
                    if (prevToken.surface == "した" || 
                        (prevToken.surface == "し" && prevToken.baseForm == "する")) {
                        return true
                    }
                }
            }
        }
        return false
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