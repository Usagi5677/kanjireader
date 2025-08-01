package com.example.kanjireader

import android.util.Log
import com.atilika.kuromoji.ipadic.Tokenizer
import com.atilika.kuromoji.ipadic.Token

class KuromojiMorphologicalAnalyzer {
    
    companion object {
        private const val TAG = "KuromojiMorphology"
    }
    
    private val tokenizer = Tokenizer()
    
    fun analyzeWord(word: String): MorphologyResult? {
        Log.d(TAG, "Analyzing word: '$word'")
        
        if (word.length <= 1) {
            Log.d(TAG, "Word '$word' too short for morphological analysis")
            return null
        }
        
        // Check if the word contains invalid characters
        if (word.contains('ｒ') || word.matches(Regex(".*[a-zA-Z].*"))) {
            Log.d(TAG, "Word '$word' contains invalid characters, skipping analysis")
            return null
        }
        
        try {
            val tokens = tokenizer.tokenize(word)
            if (tokens.isEmpty()) {
                Log.d(TAG, "No tokens found for word: '$word'")
                return null
            }
            
            Log.d(TAG, "Kuromoji tokenized '$word' into ${tokens.size} tokens")
            
            // For single word analysis, we typically want the first token
            val token = tokens[0]
            
            // Extract base form - use dictionary form if available, otherwise surface form
            val baseForm = token.baseForm ?: token.surface
            
            // Extract part of speech
            val partOfSpeech = token.partOfSpeechLevel1 ?: "unknown"
            
            // Map to VerbType if it's a verb
            val verbType = mapToVerbType(token)
            
            // Create conjugation type description
            val conjugationType = buildConjugationType(token)
            
            Log.d(TAG, "Analysis result: '$word' -> base: '$baseForm', pos: '$partOfSpeech', type: $verbType")
            Log.d(TAG, "Token details: surface='${token.surface}', baseForm='${token.baseForm}', conjType='${token.conjugationType}', conjForm='${token.conjugationForm}'")
            
            return MorphologyResult(
                originalForm = word,
                baseForm = baseForm,
                conjugationType = conjugationType,
                partOfSpeech = partOfSpeech,
                verbType = verbType
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing word '$word'", e)
            return null
        }
    }
    
    fun deinflect(word: String): List<DeinflectionResult> {
        Log.d(TAG, "Deinflecting word: '$word'")
        
        if (word.length <= 1) {
            Log.d(TAG, "Word '$word' too short for deinflection")
            return emptyList()
        }
        
        // Check if the word contains invalid characters
        if (word.contains('ｒ') || word.matches(Regex(".*[a-zA-Z].*"))) {
            Log.d(TAG, "Word '$word' contains invalid characters, skipping deinflection")
            return emptyList()
        }
        
        try {
            val tokens = tokenizer.tokenize(word)
            val results = mutableListOf<DeinflectionResult>()
            
            Log.d(TAG, "Kuromoji tokenized '$word' into ${tokens.size} tokens")
            
            for ((index, token) in tokens.withIndex()) {
                val surface = token.surface
                val baseForm = token.baseForm ?: surface
                
                Log.d(TAG, "Token $index: surface='$surface', baseForm='$baseForm', pos1='${token.partOfSpeechLevel1}', pos2='${token.partOfSpeechLevel2}'")
                
                // Only add if it's actually different from the original and makes sense
                if (baseForm != word && baseForm != surface && baseForm.length > 1) {
                    val verbType = mapToVerbType(token)
                    val transformations = buildTransformations(token)
                    
                    val deinflectionResult = DeinflectionResult(
                        originalForm = word,
                        baseForm = baseForm,
                        reasonChain = listOf(buildConjugationType(token)),
                        verbType = verbType,
                        transformations = transformations
                    )
                    
                    results.add(deinflectionResult)
                    Log.d(TAG, "Added deinflection result: '$word' -> '$baseForm' (${verbType})")
                }
            }
            
            Log.d(TAG, "Deinflection found ${results.size} results for '$word'")
            return results
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deinflecting word '$word'", e)
            return emptyList()
        }
    }
    
    fun tokenize(text: String): List<Token> {
        return try {
            tokenizer.tokenize(text)
        } catch (e: Exception) {
            Log.e(TAG, "Error tokenizing text: '$text'", e)
            emptyList()
        }
    }
    
    private fun mapToVerbType(token: Token): VerbType? {
        val pos1 = token.partOfSpeechLevel1 ?: ""
        val pos2 = token.partOfSpeechLevel2 ?: ""
        val pos3 = token.partOfSpeechLevel3 ?: ""
        
        return when {
            pos1 == "動詞" -> {
                when {
                    pos2 == "自立" && pos3 == "一段" -> VerbType.ICHIDAN
                    pos2 == "自立" && pos3 == "五段・カ行イ音便" -> VerbType.GODAN_K
                    pos2 == "自立" && pos3 == "五段・サ行" -> VerbType.GODAN_S
                    pos2 == "自立" && pos3 == "五段・タ行" -> VerbType.GODAN_T
                    pos2 == "自立" && pos3 == "五段・ナ行" -> VerbType.GODAN_N
                    pos2 == "自立" && pos3 == "五段・バ行" -> VerbType.GODAN_B
                    pos2 == "自立" && pos3 == "五段・マ行" -> VerbType.GODAN_M
                    pos2 == "自立" && pos3 == "五段・ラ行" -> VerbType.GODAN_R
                    pos2 == "自立" && pos3 == "五段・ガ行" -> VerbType.GODAN_G
                    pos2 == "自立" && pos3 == "五段・ワ行促音便" -> VerbType.GODAN_U
                    pos2 == "自立" && pos3 == "サ変・スル" -> VerbType.SURU_IRREGULAR
                    pos2 == "自立" && pos3 == "カ変・クル" -> VerbType.KURU_IRREGULAR
                    pos2 == "自立" && pos3 == "五段・カ行促音便" && token.surface == "行く" -> VerbType.IKU_IRREGULAR
                    else -> VerbType.GODAN_U // Default to godan
                }
            }
            pos1 == "形容詞" -> {
                when (pos2) {
                    "自立" -> VerbType.ADJECTIVE_I
                    "形容動詞語幹" -> VerbType.ADJECTIVE_NA
                    else -> VerbType.ADJECTIVE_I
                }
            }
            else -> null
        }
    }
    
    private fun buildConjugationType(token: Token): String {
        val conjugationType = token.conjugationType ?: ""
        val conjugationForm = token.conjugationForm ?: ""
        
        val parts = mutableListOf<String>()
        
        if (conjugationType.isNotEmpty() && conjugationType != "*") {
            parts.add(conjugationType)
        }
        if (conjugationForm.isNotEmpty() && conjugationForm != "*") {
            parts.add(conjugationForm)
        }
        
        return if (parts.isNotEmpty()) {
            parts.joinToString(" ")
        } else {
            "base form"
        }
    }
    
    private fun buildTransformations(token: Token): List<DeinflectionStep> {
        val transformations = mutableListOf<DeinflectionStep>()
        
        val baseForm = token.baseForm ?: token.surface
        if (baseForm != token.surface) {
            transformations.add(
                DeinflectionStep(
                    from = token.surface,
                    to = baseForm,
                    reason = buildConjugationType(token),
                    ruleId = "kuromoji"
                )
            )
        }
        
        return transformations
    }
}