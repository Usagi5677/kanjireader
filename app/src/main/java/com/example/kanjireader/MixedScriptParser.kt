package com.example.kanjireader

import android.util.Log

/**
 * Enhanced parser for mixed script sentences containing Japanese and romaji
 * Uses script transition detection combined with Kuromoji morphological analysis
 * 
 * Features:
 * - Script transition detection for natural word boundaries
 * - Compound word analysis (e.g., kanji+hiragana patterns)
 * - Kuromoji-based morphological validation and deinflection
 * - Support for romaji input with automatic conversion
 * 
 * Examples:
 * - "国語woべんきょうshiteimasu" -> ["国語", "を", "勉強", "している"]
 * - "watashiha日本語woべんきょうsuru" -> ["私", "は", "日本語", "を", "勉強", "する"]
 */
data class ParsedToken(
    val original: String,           // Original text segment
    val converted: String,          // Converted to Japanese (if applicable)
    val tokenType: TokenType,       // Type of token
    val deinflectedBase: String? = null,  // Base form if deinflected
    val scriptTransition: ScriptTransition? = null  // Script transition info
)

enum class ScriptType {
    KANJI,
    HIRAGANA,
    KATAKANA,
    ROMAJI,
    PUNCTUATION,
    SPACE,
    OTHER
}

enum class TokenizationStrategy {
    SCRIPT_ONLY,        // Pure script-based segmentation
    KUROMOJI_ONLY,      // Pure Kuromoji-based tokenization
    HYBRID              // Script transitions validated by Kuromoji (default)
}

data class MixedScriptParserConfig(
    val strategy: TokenizationStrategy = TokenizationStrategy.HYBRID,
    val enableCompoundWordDetection: Boolean = true,
    val enableOkuriganaDetection: Boolean = true,
    val enableKuromojiValidation: Boolean = true,
    val maxOkuriganaLength: Int = 3,
    val enableDebugLogging: Boolean = false
)

data class ScriptTransition(
    val fromScript: ScriptType,
    val toScript: ScriptType,
    val position: Int,
    val isWordBoundary: Boolean = true
)

enum class TokenType {
    KANJI,           // Pure kanji: 国語
    HIRAGANA,        // Pure hiragana: べんきょう 
    KATAKANA,        // Pure katakana: コンピューター
    ROMAJI_WORD,     // Romaji word: shiteimasu
    ROMAJI_PARTICLE, // Romaji particle: wo, ga, ni
    MIXED,           // Mixed Japanese: 国語勉強
    UNKNOWN          // Unrecognized
}

class MixedScriptParser(
    private val config: MixedScriptParserConfig = MixedScriptParserConfig()
) {
    
    private data class ScriptSegment(
        val text: String,
        val script: ScriptType,
        val startPos: Int
    )
    
    companion object {
        private const val TAG = "MixedScriptParser"
        
        // Common romaji particles and their Japanese equivalents
        private val ROMAJI_PARTICLES = mapOf(
            "wo" to "を",
            "wa" to "は",
            "ga" to "が", 
            "ni" to "に",
            "de" to "で",
            "to" to "と",
            "ya" to "や",
            "ka" to "か",
            "no" to "の",
            "mo" to "も",
            "yo" to "よ",
            "ne" to "ね",
            "na" to "な",
            "ze" to "ぜ"
        )
        
        /**
         * Create a parser with custom configuration
         */
        fun create(configure: MixedScriptParserConfig.() -> MixedScriptParserConfig): MixedScriptParser {
            val config = MixedScriptParserConfig().configure()
            return MixedScriptParser(config)
        }
    }
    
    private val romajiConverter = RomajiConverter()
    private val morphologicalAnalyzer = KuromojiMorphologicalAnalyzer()
    // Temporarily disabled - using Kuromoji for deinflection instead
    // private val deinflectionEngine = TenTenStyleDeinflectionEngine()
    
    /**
     * Parse a mixed script sentence into individual tokens using script transitions
     */
    fun parseSentence(text: String): List<ParsedToken> {
        if (text.isEmpty()) return emptyList()
        
        if (config.enableDebugLogging) {
            Log.d(TAG, "Parsing sentence: '$text' with strategy: ${config.strategy}")
        }
        
        // Choose tokenization strategy
        val tokens = when (config.strategy) {
            TokenizationStrategy.SCRIPT_ONLY -> {
                val segments = if (config.enableCompoundWordDetection) {
                    analyzeCompoundWords(segmentByScriptTransitions(text))
                } else {
                    segmentByScriptTransitions(text)
                }
                segments.map { extractTokenFromSegment(it, text) }
            }
            TokenizationStrategy.KUROMOJI_ONLY -> {
                parseWithKuromojiOnly(text)
            }
            TokenizationStrategy.HYBRID -> {
                val segments = segmentByScriptTransitionsEnhanced(text)
                segments.map { extractTokenFromSegment(it, text) }
            }
        }
        
        if (config.enableDebugLogging) {
            Log.d(TAG, "Parsed into ${tokens.size} tokens using ${config.strategy}")
        }
        return tokens
    }
    
    /**
     * Segment text by script transitions for better word boundary detection
     */
    private fun segmentByScriptTransitions(text: String): List<ScriptSegment> {
        if (text.isEmpty()) return emptyList()
        
        val segments = mutableListOf<ScriptSegment>()
        var currentStart = 0
        var currentScript = getScriptType(text[0])
        
        for (i in 1 until text.length) {
            val charScript = getScriptType(text[i])
            
            // Check if we have a meaningful script transition
            if (charScript != currentScript && isSignificantTransition(currentScript, charScript)) {
                // Create segment from current start to current position
                val segment = ScriptSegment(
                    text = text.substring(currentStart, i),
                    script = currentScript,
                    startPos = currentStart
                )
                segments.add(segment)
                
                // Start new segment
                currentStart = i
                currentScript = charScript
            }
        }
        
        // Add final segment
        if (currentStart < text.length) {
            val segment = ScriptSegment(
                text = text.substring(currentStart),
                script = currentScript,
                startPos = currentStart
            )
            segments.add(segment)
        }
        
        return segments
    }
    
    /**
     * Determine the script type of a character
     */
    private fun getScriptType(char: Char): ScriptType {
        return when {
            isKanji(char) -> ScriptType.KANJI
            isHiragana(char) -> ScriptType.HIRAGANA
            isKatakana(char) -> ScriptType.KATAKANA
            char.isLetter() -> ScriptType.ROMAJI
            char.isWhitespace() -> ScriptType.SPACE
            char in ".,!?;:()[]{}" -> ScriptType.PUNCTUATION
            else -> ScriptType.OTHER
        }
    }
    
    /**
     * Check if a script transition is significant for word boundaries
     */
    private fun isSignificantTransition(from: ScriptType, to: ScriptType): Boolean {
        // Spaces and punctuation always create boundaries
        if (from == ScriptType.SPACE || to == ScriptType.SPACE ||
            from == ScriptType.PUNCTUATION || to == ScriptType.PUNCTUATION) {
            return true
        }
        
        // Romaji transitions are always significant
        if (from == ScriptType.ROMAJI || to == ScriptType.ROMAJI) {
            return true
        }
        
        // Within Japanese scripts, some transitions may not be word boundaries
        // For example: kanji + hiragana might be okurigana (勉強する)
        // But for now, treat all as significant for better separation
        return from != to
    }
    
    /**
     * Extract a token from a script segment with transition information
     */
    private fun extractTokenFromSegment(segment: ScriptSegment, fullText: String): ParsedToken {
        val transition = if (segment.startPos > 0) {
            val prevScript = getScriptType(fullText[segment.startPos - 1])
            ScriptTransition(
                fromScript = prevScript,
                toScript = segment.script,
                position = segment.startPos
            )
        } else null
        
        return when (segment.script) {
            ScriptType.KANJI, ScriptType.HIRAGANA, ScriptType.KATAKANA -> {
                createJapaneseToken(segment.text, transition)
            }
            ScriptType.ROMAJI -> {
                createRomajiToken(segment.text, transition)
            }
            else -> {
                ParsedToken(
                    original = segment.text,
                    converted = segment.text,
                    tokenType = TokenType.UNKNOWN,
                    scriptTransition = transition
                )
            }
        }
    }
    
    /**
     * Create a token from a Japanese text segment
     */
    private fun createJapaneseToken(segment: String, transition: ScriptTransition? = null): ParsedToken {
        val tokenType = when {
            segment.all { isKanji(it) } -> TokenType.KANJI
            segment.all { isHiragana(it) } -> TokenType.HIRAGANA
            segment.all { isKatakana(it) } -> TokenType.KATAKANA
            else -> TokenType.MIXED
        }
        
        // Try deinflection to find base form using Kuromoji
        val baseForm: String? = try {
            val morphologyResult = morphologicalAnalyzer.analyzeWord(segment)
            morphologyResult?.baseForm?.takeIf { it != segment }
        } catch (e: Exception) {
            // Fallback to no base form if analysis fails
            null
        }
        
        return ParsedToken(
            original = segment,
            converted = segment,
            tokenType = tokenType,
            deinflectedBase = baseForm?.takeIf { it != segment },
            scriptTransition = transition
        )
    }
    
    /**
     * Create a token from a romaji text segment
     */
    private fun createRomajiToken(segment: String, transition: ScriptTransition? = null): ParsedToken {
        val lowercaseSegment = segment.lowercase()
        
        // Check if it's a known particle
        ROMAJI_PARTICLES[lowercaseSegment]?.let { particle ->
            return ParsedToken(
                original = segment,
                converted = particle,
                tokenType = TokenType.ROMAJI_PARTICLE,
                scriptTransition = transition
            )
        }
        
        // Convert to hiragana
        val hiragana = romajiConverter.toHiragana(segment)
        
        // Try deinflection on the hiragana form using Kuromoji
        val baseForm: String? = try {
            val deinflectionResults = morphologicalAnalyzer.deinflect(hiragana)
            deinflectionResults.firstOrNull()?.baseForm?.takeIf { it != hiragana }
        } catch (e: Exception) {
            // Fallback to no base form if analysis fails
            null
        }
        
        return ParsedToken(
            original = segment,
            converted = hiragana,
            tokenType = TokenType.ROMAJI_WORD,
            deinflectedBase = baseForm?.takeIf { it != hiragana },
            scriptTransition = transition
        )
    }
    
    /**
     * Get dictionary-ready words from parsed tokens
     * Returns both the converted forms and base forms for dictionary lookup
     */
    fun getDictionaryWords(tokens: List<ParsedToken>): Set<String> {
        val words = mutableSetOf<String>()
        
        for (token in tokens) {
            // Add the converted form
            if (token.converted.isNotEmpty() && token.tokenType != TokenType.UNKNOWN) {
                words.add(token.converted)
            }
            
            // Add the base form if different
            token.deinflectedBase?.let { base ->
                words.add(base)
            }
        }
        
        return words
    }
    
    /**
     * Convert parsed tokens back to pure Japanese text
     */
    fun toJapaneseText(tokens: List<ParsedToken>): String {
        return tokens.joinToString("") { token ->
            when (token.tokenType) {
                TokenType.ROMAJI_PARTICLE, TokenType.ROMAJI_WORD -> token.converted
                else -> token.original
            }
        }
    }
    
    /**
     * Get a human-readable breakdown of the parsing with transition info
     */
    fun getParsingBreakdown(tokens: List<ParsedToken>): String {
        return tokens.joinToString(" + ") { token ->
            val baseInfo = when {
                token.deinflectedBase != null -> "${token.converted} (${token.deinflectedBase})"
                token.converted != token.original -> "${token.original} (${token.converted})"
                else -> token.original
            }
            
            // Add transition info if available
            val transitionInfo = token.scriptTransition?.let { transition ->
                " [${transition.fromScript}→${transition.toScript}]"
            } ?: ""
            
            baseInfo + transitionInfo
        }
    }
    
    /**
     * Get script transition summary for debugging
     */
    fun getScriptTransitions(tokens: List<ParsedToken>): List<ScriptTransition> {
        return tokens.mapNotNull { it.scriptTransition }
    }
    
    /**
     * Analyze text to show script composition
     */
    fun analyzeScriptComposition(text: String): Map<ScriptType, Int> {
        val composition = mutableMapOf<ScriptType, Int>()
        
        for (char in text) {
            val script = getScriptType(char)
            composition[script] = composition.getOrDefault(script, 0) + 1
        }
        
        return composition
    }
    
    // Character type checking helper methods
    private fun isKanji(char: Char): Boolean {
        val code = char.code
        return (code in 0x4E00..0x9FAF) || (code in 0x3400..0x4DBF)
    }
    
    private fun isHiragana(char: Char): Boolean {
        return char.code in 0x3040..0x309F
    }
    
    private fun isKatakana(char: Char): Boolean {
        return char.code in 0x30A0..0x30FF
    }
    
    /**
     * Analyze multiple segments to detect compound words and improve boundaries
     */
    private fun analyzeCompoundWords(segments: List<ScriptSegment>): List<ScriptSegment> {
        if (segments.size <= 1) return segments
        
        val analyzedSegments = mutableListOf<ScriptSegment>()
        var i = 0
        
        while (i < segments.size) {
            val current = segments[i]
            
            // Look ahead for potential compound patterns
            if (i < segments.size - 1) {
                val next = segments[i + 1]
                
                // Check for kanji + hiragana pattern (potential okurigana)
                if (shouldMergeSegments(current, next)) {
                    val merged = mergeSegments(current, next)
                    analyzedSegments.add(merged)
                    i += 2 // Skip the next segment as it's been merged
                    continue
                }
            }
            
            // No merging, add current segment
            analyzedSegments.add(current)
            i++
        }
        
        return analyzedSegments
    }
    
    /**
     * Determine if two adjacent segments should be merged into one word
     */
    private fun shouldMergeSegments(current: ScriptSegment, next: ScriptSegment): Boolean {
        return when {
            // Merge kanji + hiragana for potential okurigana
            current.script == ScriptType.KANJI && next.script == ScriptType.HIRAGANA && 
            next.text.length <= 3 -> {
                // Short hiragana after kanji might be okurigana
                isLikelyOkurigana(next.text)
            }
            // Don't merge other patterns for now to maintain clarity
            else -> false
        }
    }
    
    /**
     * Merge two script segments into one
     */
    private fun mergeSegments(first: ScriptSegment, second: ScriptSegment): ScriptSegment {
        return ScriptSegment(
            text = first.text + second.text,
            script = ScriptType.KANJI, // Treat merged as mixed/kanji-based
            startPos = first.startPos
        )
    }
    
    /**
     * Enhanced segmentation with compound word analysis
     */
    private fun segmentByScriptTransitionsEnhanced(text: String): List<ScriptSegment> {
        val basicSegments = segmentByScriptTransitions(text)
        val compoundAnalyzed = analyzeCompoundWords(basicSegments)
        return validateWithKuromoji(compoundAnalyzed, text)
    }
    
    /**
     * Validate script-based segmentation using Kuromoji morphological analysis
     */
    private fun validateWithKuromoji(segments: List<ScriptSegment>, originalText: String): List<ScriptSegment> {
        // For validation, convert romaji segments to hiragana first
        val japaneseText = buildJapaneseTextFromSegments(segments)
        
        try {
            // Get Kuromoji's tokenization
            val kuromojiTokens = morphologicalAnalyzer.tokenize(japaneseText)
            
            if (kuromojiTokens.isEmpty()) {
                Log.d(TAG, "Kuromoji validation: no tokens found, using script-based segmentation")
                return segments
            }
            
            Log.d(TAG, "Kuromoji validation: found ${kuromojiTokens.size} tokens for '$japaneseText'")
            
            // For now, return script-based segmentation but log comparison
            // In future iterations, could merge the approaches more deeply
            kuromojiTokens.forEachIndexed { index, token ->
                Log.d(TAG, "Kuromoji token $index: '${token.surface}' -> '${token.baseForm ?: token.surface}'")
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Kuromoji validation failed: ${e.message}")
        }
        
        return segments
    }
    
    /**
     * Build Japanese text from segments for Kuromoji analysis
     */
    private fun buildJapaneseTextFromSegments(segments: List<ScriptSegment>): String {
        return segments.joinToString("") { segment ->
            when (segment.script) {
                ScriptType.ROMAJI -> {
                    // Check if it's a particle
                    val lowercase = segment.text.lowercase()
                    ROMAJI_PARTICLES[lowercase] ?: romajiConverter.toHiragana(segment.text)
                }
                else -> segment.text
            }
        }
    }
    
    /**
     * Check if hiragana text is likely to be okurigana
     */
    private fun isLikelyOkurigana(hiragana: String): Boolean {
        // Common okurigana patterns
        val commonOkurigana = setOf(
            "する", "した", "して", "しい", "しく", "しく",
            "く", "い", "う", "る", "た", "て", "ない", "ます",
            "だ", "である", "な", "に", "の", "が", "を"
        )
        
        // Short hiragana strings are more likely to be okurigana
        if (hiragana.length <= 2) {
            return true
        }
        
        // Check against common patterns
        return commonOkurigana.contains(hiragana) || hiragana.length <= 3
    }
    
    /**
     * Get detailed morphological analysis for all tokens
     */
    fun getMorphologicalAnalysis(tokens: List<ParsedToken>): List<Pair<ParsedToken, MorphologyResult?>> {
        return tokens.map { token ->
            val analysis = when (token.tokenType) {
                TokenType.KANJI, TokenType.HIRAGANA, TokenType.KATAKANA, TokenType.MIXED -> {
                    try {
                        morphologicalAnalyzer.analyzeWord(token.converted)
                    } catch (e: Exception) {
                        null
                    }
                }
                TokenType.ROMAJI_WORD -> {
                    try {
                        morphologicalAnalyzer.analyzeWord(token.converted)
                    } catch (e: Exception) {
                        null
                    }
                }
                else -> null
            }
            token to analysis
        }
    }
    
    /**
     * Validate token boundaries using morphological analysis
     */
    fun validateTokenBoundaries(tokens: List<ParsedToken>): List<ParsedToken> {
        // For now, return original tokens but add morphological validation
        // In future versions, could split/merge tokens based on Kuromoji analysis
        val validated = tokens.map { token ->
            if (token.deinflectedBase == null && 
                (token.tokenType == TokenType.HIRAGANA || token.tokenType == TokenType.MIXED)) {
                
                // Try to get base form if we don't have one
                val baseForm = try {
                    morphologicalAnalyzer.analyzeWord(token.converted)?.baseForm?.takeIf { it != token.converted }
                } catch (e: Exception) {
                    null
                }
                
                if (baseForm != null) {
                    token.copy(deinflectedBase = baseForm)
                } else {
                    token
                }
            } else {
                token
            }
        }
        
        return validated
    }
    
    /**
     * Parse text using only Kuromoji morphological analysis
     */
    private fun parseWithKuromojiOnly(text: String): List<ParsedToken> {
        try {
            // Convert any romaji to hiragana first
            val processedText = convertRomajiInText(text)
            val kuromojiTokens = morphologicalAnalyzer.tokenize(processedText)
            
            return kuromojiTokens.map { token ->
                val tokenType = determineTokenTypeFromKuromoji(token.surface)
                val baseForm = token.baseForm?.takeIf { it != token.surface }
                
                ParsedToken(
                    original = token.surface,
                    converted = token.surface,
                    tokenType = tokenType,
                    deinflectedBase = baseForm
                )
            }
        } catch (e: Exception) {
            if (config.enableDebugLogging) {
                Log.w(TAG, "Kuromoji-only parsing failed: ${e.message}")
            }
            // Fallback to simple script-based parsing
            return segmentByScriptTransitions(text).map { extractTokenFromSegment(it, text) }
        }
    }
    
    /**
     * Convert romaji particles and words in text to hiragana for Kuromoji
     */
    private fun convertRomajiInText(text: String): String {
        val segments = segmentByScriptTransitions(text)
        return segments.joinToString("") { segment ->
            when (segment.script) {
                ScriptType.ROMAJI -> {
                    val lowercase = segment.text.lowercase()
                    ROMAJI_PARTICLES[lowercase] ?: romajiConverter.toHiragana(segment.text)
                }
                else -> segment.text
            }
        }
    }
    
    /**
     * Determine token type from Kuromoji token surface
     */
    private fun determineTokenTypeFromKuromoji(surface: String): TokenType {
        return when {
            surface.all { isKanji(it) } -> TokenType.KANJI
            surface.all { isHiragana(it) } -> TokenType.HIRAGANA
            surface.all { isKatakana(it) } -> TokenType.KATAKANA
            surface.any { isKanji(it) || isHiragana(it) || isKatakana(it) } -> TokenType.MIXED
            else -> TokenType.UNKNOWN
        }
    }
    
}