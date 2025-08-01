package com.example.kanjireader

import android.util.Log
import com.atilika.kuromoji.ipadic.Tokenizer
import com.atilika.kuromoji.ipadic.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SentenceAnalysisEngine(
    private val romajiConverter: RomajiConverter,
    private val dictionaryDatabase: DictionaryDatabase
) {
    
    companion object {
        private const val TAG = "SentenceAnalysis"
        
        // POS tags we want to include in sentence analysis
        private val MEANINGFUL_POS = setOf(
            "名詞",     // Noun
            "動詞",     // Verb  
            "形容詞",   // I-adjective
            "形容動詞", // Na-adjective
            "副詞",     // Adverb
            "連体詞",   // Adnominal
            "感動詞"    // Interjection
        )
        
        // Particles we want to show (grammatically important)
        private val IMPORTANT_PARTICLES = setOf(
            "は", "が", "を", "に", "で", "と", "の", "から", "まで", "より"
        )
    }
    
    private val tokenizer = Tokenizer()
    
    /**
     * Analyzes a sentence and extracts meaningful words for dictionary lookup
     */
    suspend fun analyzeSentence(input: String): SentenceAnalysis = withContext(Dispatchers.IO) {
        Log.d(TAG, "Analyzing sentence: '$input'")
        
        // First convert any romaji to hiragana
        val normalizedSentence = convertMixedToJapanese(input)
        Log.d(TAG, "Normalized sentence: '$normalizedSentence'")
        
        try {
            // Tokenize the full sentence with Kuromoji
            val tokens = tokenizer.tokenize(normalizedSentence)
            Log.d(TAG, "Kuromoji found ${tokens.size} tokens")
            
            // Extract meaningful words and look them up in dictionary
            val sentenceWords = mutableListOf<SentenceWord>()
            var hasVerb = false
            var tense: String? = null
            
            for (token in tokens) {
                Log.d(TAG, "Processing token: ${token.surface} (${token.partOfSpeechLevel1})")
                
                // Check if this is a meaningful word we should include
                if (shouldIncludeToken(token)) {
                    val sentenceWord = createSentenceWord(token)
                    if (sentenceWord != null) {
                        sentenceWords.add(sentenceWord)
                        
                        // Track sentence properties
                        if (token.partOfSpeechLevel1 == "動詞") {
                            hasVerb = true
                            if (tense == null) {
                                tense = detectTense(token)
                            }
                        }
                    }
                }
            }
            
            Log.d(TAG, "Extracted ${sentenceWords.size} meaningful words from sentence")
            
            return@withContext SentenceAnalysis(
                originalSentence = input,
                normalizedSentence = normalizedSentence,
                words = sentenceWords,
                hasVerb = hasVerb,
                tense = tense
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing sentence: '$input'", e)
            return@withContext SentenceAnalysis(
                originalSentence = input,
                normalizedSentence = normalizedSentence,
                words = emptyList()
            )
        }
    }
    
    /**
     * Converts mixed script input to full Japanese
     */
    private fun convertMixedToJapanese(input: String): String {
        val result = StringBuilder()
        var i = 0
        
        while (i < input.length) {
            val char = input[i]
            
            when {
                // Japanese characters - keep as is
                char.isJapanese() -> {
                    result.append(char)
                    i++
                }
                
                // Roman characters - try to convert romaji segments
                char.isLetter() -> {
                    val romajiSegment = extractRomajiSegment(input, i)
                    val converted = romajiConverter.toHiragana(romajiSegment.text)
                    result.append(converted)
                    i = romajiSegment.endIndex
                }
                
                // Other characters - keep as is
                else -> {
                    result.append(char)
                    i++
                }
            }
        }
        
        return result.toString()
    }
    
    /**
     * Extracts a romaji segment starting at the given index
     */
    private fun extractRomajiSegment(input: String, startIndex: Int): RomajiSegment {
        var endIndex = startIndex
        while (endIndex < input.length && input[endIndex].isLetter()) {
            endIndex++
        }
        return RomajiSegment(
            text = input.substring(startIndex, endIndex),
            endIndex = endIndex
        )
    }
    
    /**
     * Determines if a token should be included in sentence analysis
     */
    private fun shouldIncludeToken(token: Token): Boolean {
        val pos = token.partOfSpeechLevel1 ?: return false
        val surface = token.surface
        
        return when {
            // Include meaningful POS categories
            MEANINGFUL_POS.contains(pos) -> true
            
            // Include important particles
            pos == "助詞" && IMPORTANT_PARTICLES.contains(surface) -> true
            
            // Exclude other particles, symbols, etc.
            else -> false
        }
    }
    
    /**
     * Creates a SentenceWord from a Kuromoji token with dictionary lookup
     */
    private suspend fun createSentenceWord(token: Token): SentenceWord? {
        val baseForm = token.baseForm ?: token.surface
        val surface = token.surface
        val reading = token.reading
        val pos = token.partOfSpeechLevel1 ?: "unknown"
        
        // Look up the base form in the dictionary
        val dictionaryResults = dictionaryDatabase.searchJapaneseFTS(baseForm, 5)
        
        val meanings = if (dictionaryResults.isNotEmpty()) {
            // Extract meanings from the best match
            val bestMatch = dictionaryResults.first()
            try {
                val meaningsJson = bestMatch.meanings
                // Parse JSON array of meanings
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                gson.fromJson<List<String>>(meaningsJson, type) ?: emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing meanings for '$baseForm'", e)
                listOf(bestMatch.meanings) // Fallback to raw string
            }
        } else {
            // No dictionary entry found
            emptyList()
        }
        
        // Get frequency and common status from best match
        val frequency = dictionaryResults.firstOrNull()?.frequency ?: 0
        val isCommon = dictionaryResults.firstOrNull()?.isCommon ?: false
        
        // Create conjugation info if the surface form differs from base form
        val conjugationInfo = if (surface != baseForm && pos == "動詞") {
            "$surface → $baseForm"
        } else null
        
        return SentenceWord(
            surface = surface,
            baseForm = baseForm,
            reading = reading,
            partOfSpeech = pos,
            meanings = meanings,
            frequency = frequency,
            isCommon = isCommon,
            conjugationInfo = conjugationInfo
        )
    }
    
    /**
     * Detects tense from a verb token
     */
    private fun detectTense(token: Token): String? {
        val conjugationForm = token.conjugationForm
        return when {
            conjugationForm?.contains("過去") == true -> "past"
            conjugationForm?.contains("現在") == true -> "present" 
            token.surface.endsWith("た") || token.surface.endsWith("だ") -> "past"
            token.surface.endsWith("ます") -> "polite present"
            token.surface.endsWith("ました") -> "polite past"
            else -> null
        }
    }
    
    /**
     * Helper function to check if a character is Japanese
     */
    private fun Char.isJapanese(): Boolean {
        return this in '\u3040'..'\u309F' ||  // Hiragana
               this in '\u30A0'..'\u30FF' ||  // Katakana
               this in '\u4E00'..'\u9FAF'     // Kanji
    }
    
    /**
     * Data class for romaji segment extraction
     */
    private data class RomajiSegment(
        val text: String,
        val endIndex: Int
    )
}