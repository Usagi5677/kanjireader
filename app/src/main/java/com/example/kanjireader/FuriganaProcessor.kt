package com.example.kanjireader

import android.util.Log
import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer

// Data class representing a segment of text with optional furigana
data class FuriganaSegment(
    val text: String,           // The actual text (kanji, hiragana, etc.)
    val furigana: String?,      // Reading in hiragana (null if not needed)
    val isKanji: Boolean,       // Whether this segment contains kanji
    val startIndex: Int,        // Start position in original text
    val endIndex: Int           // End position in original text
)

// Data class for processed text with furigana information
data class FuriganaText(
    val segments: List<FuriganaSegment>,
    val originalText: String
)

class FuriganaProcessor(
    private val dictionaryRepository: DictionaryRepository?,
    private val deinflectionEngine: TenTenStyleDeinflectionEngine?,
    private val tagDictLoader: TagDictSQLiteLoader?
) {
    
    // Kuromoji tokenizer for morphological analysis
    private val kuromojiTokenizer = Tokenizer()
    
    companion object {
        private const val TAG = "FuriganaProcessor"
    }

    /**
     * Process text and generate furigana segments using Kuromoji exclusively
     */
    suspend fun processText(text: String): FuriganaText {
        Log.d(TAG, "Processing text with Kuromoji-based furigana: '$text'")
        
        val segments = mutableListOf<FuriganaSegment>()
        
        try {
            val tokens = kuromojiTokenizer.tokenize(text)
            Log.d(TAG, "Kuromoji tokens: ${tokens.map { it.surface }}")
            
            for (token in tokens) {
                val surface = token.surface
                val reading = extractKuromojiReading(token)
                
                Log.d(TAG, "Token: surface='$surface', reading='$reading', pos='${token.partOfSpeechLevel1}'")
                
                // Create segment with furigana only if it contains kanji and has a valid reading
                val segment = if (containsKanji(surface) && reading != null && reading != surface) {
                    FuriganaSegment(
                        text = surface,
                        furigana = reading,
                        isKanji = true,
                        startIndex = 0, // Will be fixed in post-processing
                        endIndex = surface.length
                    )
                } else {
                    FuriganaSegment(
                        text = surface,
                        furigana = null,
                        isKanji = containsKanji(surface),
                        startIndex = 0, // Will be fixed in post-processing
                        endIndex = surface.length
                    )
                }
                
                segments.add(segment)
            }
            
            // Fix segment indices to match actual character positions
            val fixedSegments = fixSegmentIndices(segments, text)
            segments.clear()
            segments.addAll(fixedSegments)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing text with Kuromoji", e)
            // Fallback: single segment without furigana
            segments.add(FuriganaSegment(
                text = text,
                furigana = null,
                isKanji = containsKanji(text),
                startIndex = 0,
                endIndex = text.length
            ))
        }
        
        return FuriganaText(segments, text)
    }
    
    /**
     * Get the pronunciation reading for a specific word using Kuromoji
     * This is used when clicking on cards to get consistent pronunciation
     */
    fun getWordReading(word: String): String? {
        Log.d(TAG, "Getting reading for word: '$word'")
        
        try {
            val tokens = kuromojiTokenizer.tokenize(word)
            if (tokens.isNotEmpty()) {
                val token = tokens[0] // Use first token for the word
                val reading = extractKuromojiReadingForCard(token)
                
                Log.d(TAG, "Word '$word' reading: '$reading'")
                return reading
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting reading for word '$word'", e)
        }
        
        return null
    }

    /**
     * Extract reading from Kuromoji token for card display (no okurigana trimming)
     */
    private fun extractKuromojiReadingForCard(token: Token): String? {
        return when {
            !token.reading.isNullOrEmpty() && token.reading != "*" -> {
                val katakanaReading = token.reading
                val hiraganaReading = convertKatakanaToHiragana(katakanaReading)
                Log.d(TAG, "Card reading for '${token.surface}': '$katakanaReading' -> '$hiraganaReading'")
                hiraganaReading
            }
            else -> {
                Log.d(TAG, "No reading found for '${token.surface}'")
                null
            }
        }
    }

    /**
     * Extract reading from Kuromoji token and convert to hiragana with okurigana trimming
     */
    private fun extractKuromojiReading(token: Token): String? {
        return when {
            !token.reading.isNullOrEmpty() && token.reading != "*" -> {
                val katakanaReading = token.reading
                val hiraganaReading = convertKatakanaToHiragana(katakanaReading)
                val trimmedReading = trimOkurigana(token.surface, hiraganaReading)
                Log.d(TAG, "Reading for '${token.surface}': '$katakanaReading' -> '$hiraganaReading' -> '$trimmedReading'")
                trimmedReading
            }
            else -> {
                Log.d(TAG, "No reading found for '${token.surface}'")
                null
            }
        }
    }
    
    /**
     * Convert katakana to hiragana for furigana display
     */
    private fun convertKatakanaToHiragana(katakana: String): String {
        val result = StringBuilder()
        
        for (char in katakana) {
            val converted = when (char.code) {
                in 0x30A1..0x30F6 -> {
                    // Convert katakana to hiragana by subtracting 0x60
                    (char.code - 0x60).toChar()
                }
                else -> char // Keep other characters as-is
            }
            result.append(converted)
        }
        
        return result.toString()
    }
    
    /**
     * Trim okurigana from reading for conjugated words
     * For example: 届けた (surface) with とどけた (reading) -> とど
     * For words like 見る (surface) with みる (reading) -> み
     */
    private fun trimOkurigana(surface: String, reading: String): String {
        if (surface.isEmpty() || reading.isEmpty()) {
            return reading
        }
        
        // Count kanji characters in the surface form
        val kanjiCount = surface.count { containsKanji(it.toString()) }
        
        // For single kanji words with okurigana, show only the kanji reading
        if (kanjiCount == 1) {
            // Find the kanji position
            val kanjiIndex = surface.indexOfFirst { containsKanji(it.toString()) }
            
            if (kanjiIndex >= 0 && kanjiIndex + 1 < surface.length) {
                // Get the hiragana part after the kanji (okurigana)
                val okurigana = surface.substring(kanjiIndex + 1)
                
                // Only trim if the reading clearly ends with the okurigana
                if (reading.endsWith(okurigana) && reading.length > okurigana.length) {
                    val trimmedReading = reading.substring(0, reading.length - okurigana.length)
                    Log.d(TAG, "Trimmed okurigana '$okurigana' from reading '$reading' -> '$trimmedReading'")
                    return trimmedReading
                }
            }
        }
        
        // For complex words or ambiguous cases, return the full reading
        Log.d(TAG, "Keeping full reading for surface='$surface', reading='$reading'")
        return reading
    }
    
    /**
     * Fix segment indices to match actual character positions in the original text
     * This solves the alignment issue by ensuring segments map correctly to text positions
     */
    private fun fixSegmentIndices(segments: List<FuriganaSegment>, originalText: String): List<FuriganaSegment> {
        val fixedSegments = mutableListOf<FuriganaSegment>()
        var currentIndex = 0
        
        for (segment in segments) {
            // Find the actual position of this segment text in the original text
            val actualIndex = originalText.indexOf(segment.text, currentIndex)
            
            if (actualIndex >= 0) {
                // Create new segment with correct indices
                val updatedSegment = segment.copy(
                    startIndex = actualIndex,
                    endIndex = actualIndex + segment.text.length
                )
                
                fixedSegments.add(updatedSegment)
                currentIndex = actualIndex + segment.text.length
                Log.d(TAG, "Fixed segment '${segment.text}' position: ${updatedSegment.startIndex}-${updatedSegment.endIndex}")
            } else {
                Log.w(TAG, "Could not find segment '${segment.text}' in original text at position $currentIndex")
                // Keep original segment as fallback
                fixedSegments.add(segment)
            }
        }
        
        return fixedSegments
    }

    /**
     * Check if text contains kanji characters
     */
    private fun containsKanji(text: String): Boolean {
        return text.any { char ->
            val codePoint = char.code
            (codePoint in 0x4E00..0x9FAF) || (codePoint in 0x3400..0x4DBF)
        }
    }
}