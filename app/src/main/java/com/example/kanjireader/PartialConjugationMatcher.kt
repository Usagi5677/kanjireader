package com.example.kanjireader

import android.util.Log

/**
 * Recognizes partial conjugation typing patterns for Japanese verbs
 * Handles cases like: たべｍ → たべます, かｋ → かきます, etc.
 */
class PartialConjugationMatcher {
    
    companion object {
        private const val TAG = "PartialConjugationMatcher"
    }
    
    /**
     * Analyze a query to detect partial conjugation patterns
     * @param query The input query (e.g., "たべｍ", "たべま", "たべませんで")
     * @return List of likely completions with confidence scores
     */
    fun analyzePartialConjugation(query: String): List<ConjugationCompletion> {
        if (query.length < 2) return emptyList()
        
        val completions = mutableListOf<ConjugationCompletion>()
        
        // COMPREHENSIVE PATTERN MATCHING
        // Check for progressively longer patterns first (more specific)
        
        // Long patterns (highest priority)
        when {
            // Romaji partial suffixes for deshita (check these first)
            query.endsWith("ませんdesh") || query.endsWith("ませんでsh") -> {
                // たべませんdesh → たべませんでした
                completions.addAll(generateRomajiNegativePastCompletions(query, "でした"))
            }
            query.endsWith("ませんです") -> {
                // たべませんです → たべませんでした (des was converted to です)
                completions.addAll(generateRomajiNegativePastCompletions(query, "でした"))
            }
            query.endsWith("ませんでし") -> {
                // たべませんでし → たべませんでした (desh was converted to でし)
                completions.addAll(generateRomajiNegativePastCompletions(query, "でした"))
            }
            query.endsWith("ませんdes") || query.endsWith("ませんでs") -> {
                // たべませんdes → たべませんでした
                completions.addAll(generateRomajiNegativePastCompletions(query, "でした"))
            }
            query.endsWith("ませんde") -> {
                // たべませんde → たべませんでした
                completions.addAll(generateRomajiNegativePastCompletions(query, "でした"))
            }
            query.endsWith("ませんで") -> {
                // たべませんで → たべませんでした (already has で)
                completions.addAll(generateLongNegativeCompletions(query))
            }
            query.endsWith("ませんd") || query.endsWith("ませんｄ") -> {
                // たべませんd → たべませんでした
                completions.addAll(generateRomajiNegativePastCompletions(query, "でした"))
            }
            query.endsWith("ました") -> {
                // Already complete past polite form
                completions.add(ConjugationCompletion(query, query, ConjugationType.PAST_POLITE_FORM, 1.0f))
            }
            query.endsWith("ませ") -> {
                // たべませ → たべません, たべませんでした
                completions.addAll(generateMediumNegativeCompletions(query))
            }
            query.endsWith("まし") -> {
                // たべまし → たべました, たべましょう
                completions.addAll(generateMediumPastCompletions(query))
            }
            query.endsWith("ます") -> {
                // Already complete masu form
                completions.add(ConjugationCompletion(query, query, ConjugationType.MASU_FORM, 1.0f))
            }
            query.endsWith("ま") -> {
                // たべま → たべます, たべません, たべませんでした, たべました, たべましょう
                completions.addAll(generateMasuGroupCompletions(query))
            }
            
            // Single character patterns (existing logic)
            query.endsWith("ｍ") || query.endsWith("m") -> {
                val normalizedQuery = if (query.endsWith("m")) query.dropLast(1) + "ｍ" else query
                completions.addAll(generateMasuCompletions(normalizedQuery))
            }
            query.endsWith("ｔ") || query.endsWith("t") -> {
                val normalizedQuery = if (query.endsWith("t")) query.dropLast(1) + "ｔ" else query
                completions.addAll(generateTeFormCompletions(normalizedQuery))
            }
            query.endsWith("ｎ") || query.endsWith("n") -> {
                val normalizedQuery = if (query.endsWith("n")) query.dropLast(1) + "ｎ" else query
                completions.addAll(generateNegativeCompletions(normalizedQuery))
            }
            query.endsWith("ｋ") || query.endsWith("k") -> {
                val normalizedQuery = if (query.endsWith("k")) query.dropLast(1) + "ｋ" else query
                completions.addAll(generateKVerbCompletions(normalizedQuery))
            }
            query.endsWith("ｓ") || query.endsWith("s") -> {
                val normalizedQuery = if (query.endsWith("s")) query.dropLast(1) + "ｓ" else query
                completions.addAll(generateSVerbCompletions(normalizedQuery))
            }
            query.endsWith("ｇ") || query.endsWith("g") -> {
                val normalizedQuery = if (query.endsWith("g")) query.dropLast(1) + "ｇ" else query
                completions.addAll(generateGVerbCompletions(normalizedQuery))
            }
        }
        
        // Sort by confidence (highest first)
        return completions.sortedByDescending { it.confidence }
    }
    
    /**
     * Generate masu-form completions (highest priority)
     */
    private fun generateMasuCompletions(query: String): List<ConjugationCompletion> {
        val stem = query.dropLast(1) // Remove the "ｍ"
        val completions = mutableListOf<ConjugationCompletion>()
        
        // Primary: masu-form
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "ます",
            conjugationType = ConjugationType.MASU_FORM,
            confidence = 0.9f
        ))
        
        // Secondary: dictionary form (for ichidan verbs)
        if (stem.isNotEmpty()) {
            completions.add(ConjugationCompletion(
                original = query,
                completion = stem + "る",
                conjugationType = ConjugationType.DICTIONARY_FORM,
                confidence = 0.7f
            ))
        }
        
        return completions
    }
    
    /**
     * Generate te-form completions
     */
    private fun generateTeFormCompletions(query: String): List<ConjugationCompletion> {
        val stem = query.dropLast(1) // Remove the "ｔ"
        val completions = mutableListOf<ConjugationCompletion>()
        
        // Te-form
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "て",
            conjugationType = ConjugationType.TE_FORM,
            confidence = 0.8f
        ))
        
        // Past form
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "た",
            conjugationType = ConjugationType.PAST_FORM,
            confidence = 0.7f
        ))
        
        return completions
    }
    
    /**
     * Generate negative completions
     */
    private fun generateNegativeCompletions(query: String): List<ConjugationCompletion> {
        val stem = query.dropLast(1) // Remove the "ｎ"
        val completions = mutableListOf<ConjugationCompletion>()
        
        // Negative form
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "ない",
            conjugationType = ConjugationType.NEGATIVE_FORM,
            confidence = 0.8f
        ))
        
        // Past negative (んだ pattern for some verbs)
        if (stem.length > 1) {
            completions.add(ConjugationCompletion(
                original = query,
                completion = stem + "んだ",
                conjugationType = ConjugationType.PAST_FORM,
                confidence = 0.6f
            ))
        }
        
        return completions
    }
    
    /**
     * Generate k-verb completions (書く, 歩く, etc.)
     */
    private fun generateKVerbCompletions(query: String): List<ConjugationCompletion> {
        val stem = query.dropLast(1) // Remove the "ｋ"
        val completions = mutableListOf<ConjugationCompletion>()
        
        // Masu-form: かｋ → かきます
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "きます",
            conjugationType = ConjugationType.MASU_FORM,
            confidence = 0.9f
        ))
        
        // Dictionary form: かｋ → かく
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "く",
            conjugationType = ConjugationType.DICTIONARY_FORM,
            confidence = 0.8f
        ))
        
        // Te-form: かｋ → かいて
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "いて",
            conjugationType = ConjugationType.TE_FORM,
            confidence = 0.7f
        ))
        
        return completions
    }
    
    /**
     * Generate s-verb completions (話す, 出す, etc.)
     */
    private fun generateSVerbCompletions(query: String): List<ConjugationCompletion> {
        val stem = query.dropLast(1) // Remove the "ｓ"
        val completions = mutableListOf<ConjugationCompletion>()
        
        // Masu-form: はなｓ → はなします
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "します",
            conjugationType = ConjugationType.MASU_FORM,
            confidence = 0.9f
        ))
        
        // Dictionary form: はなｓ → はなす
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "す",
            conjugationType = ConjugationType.DICTIONARY_FORM,
            confidence = 0.8f
        ))
        
        // Te-form: はなｓ → はなして
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "して",
            conjugationType = ConjugationType.TE_FORM,
            confidence = 0.7f
        ))
        
        return completions
    }
    
    /**
     * Generate g-verb completions (泳ぐ, 脱ぐ, etc.)
     */
    private fun generateGVerbCompletions(query: String): List<ConjugationCompletion> {
        val stem = query.dropLast(1) // Remove the "ｇ"
        val completions = mutableListOf<ConjugationCompletion>()
        
        // Masu-form: およｇ → およぎます
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "ぎます",
            conjugationType = ConjugationType.MASU_FORM,
            confidence = 0.9f
        ))
        
        // Dictionary form: およｇ → およぐ
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "ぐ",
            conjugationType = ConjugationType.DICTIONARY_FORM,
            confidence = 0.8f
        ))
        
        // Te-form: およｇ → およいで
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "いで",
            conjugationType = ConjugationType.TE_FORM,
            confidence = 0.7f
        ))
        
        return completions
    }
    
    /**
     * Check if a query looks like a partial conjugation
     */
    fun isPartialConjugation(query: String): Boolean {
        if (query.length < 2) return false
        
        // Check for romaji partial patterns first (including progressive deshita typing)
        val romajiPatterns = listOf(
            "ませんdesh", "ませんでsh", "ませんでし", "ませんです", "ませんdes", "ませんでs", 
            "ませんde", "ませんd", "ませんｄ"
        )
        if (romajiPatterns.any { query.endsWith(it) }) return true
        
        // Check for long patterns first
        val longPatterns = listOf("ませんで", "ました", "ませ", "まし", "ます", "ま")
        if (longPatterns.any { query.endsWith(it) }) return true
        
        // Check for single character patterns
        val lastChar = query.last()
        return lastChar in listOf('ｍ', 'm', 'ｔ', 't', 'ｎ', 'n', 'ｋ', 'k', 'ｓ', 's', 'ｇ', 'g')
    }
    
    /**
     * Generate completions for romaji negative past patterns (ませんd → ませんでした)
     */
    private fun generateRomajiNegativePastCompletions(query: String, targetSuffix: String = "でした"): List<ConjugationCompletion> {
        // Find the base by removing the partial romaji suffix
        val base = when {
            query.endsWith("ませんdesh") -> query.dropLast(4) // Remove "desh"
            query.endsWith("ませんでsh") -> query.dropLast(2) // Remove "sh"
            query.endsWith("ませんでし") -> query.dropLast(1) // Remove "し", keep "ませんで"
            query.endsWith("ませんです") -> query.dropLast(2) // Remove "です", keep "ません"
            query.endsWith("ませんdes") -> query.dropLast(3) // Remove "des"
            query.endsWith("ませんでs") -> query.dropLast(1) // Remove "s"
            query.endsWith("ませんde") -> query.dropLast(2) // Remove "de"
            query.endsWith("ませんで") -> query // Already has で
            query.endsWith("ませんd") -> query.dropLast(1) // Remove "d"
            query.endsWith("ませんｄ") -> query.dropLast(1) // Remove "ｄ"
            else -> query
        }
        
        val completions = mutableListOf<ConjugationCompletion>()
        
        // Past negative polite
        completions.add(ConjugationCompletion(
            original = query,
            completion = base + targetSuffix,
            conjugationType = ConjugationType.PAST_NEGATIVE_POLITE_FORM,
            confidence = 0.95f
        ))
        
        return completions
    }
    
    /**
     * Generate completions for long negative patterns (ませんで → ませんでした)
     */
    private fun generateLongNegativeCompletions(query: String): List<ConjugationCompletion> {
        val stem = query.dropLast(4) // Remove "ませんで"
        val completions = mutableListOf<ConjugationCompletion>()
        
        // Past negative polite
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "ませんでした",
            conjugationType = ConjugationType.PAST_NEGATIVE_POLITE_FORM,
            confidence = 0.95f
        ))
        
        return completions
    }
    
    /**
     * Generate completions for medium negative patterns (ませ → ません, ませんでした)
     */
    private fun generateMediumNegativeCompletions(query: String): List<ConjugationCompletion> {
        val stem = query.dropLast(2) // Remove "ませ"
        val completions = mutableListOf<ConjugationCompletion>()
        
        // Negative polite
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "ません",
            conjugationType = ConjugationType.NEGATIVE_POLITE_FORM,
            confidence = 0.9f
        ))
        
        // Past negative polite
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "ませんでした",
            conjugationType = ConjugationType.PAST_NEGATIVE_POLITE_FORM,
            confidence = 0.85f
        ))
        
        return completions
    }
    
    /**
     * Generate completions for medium past patterns (まし → ました, ましょう)
     */
    private fun generateMediumPastCompletions(query: String): List<ConjugationCompletion> {
        val stem = query.dropLast(2) // Remove "まし"
        val completions = mutableListOf<ConjugationCompletion>()
        
        // Past polite
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "ました",
            conjugationType = ConjugationType.PAST_POLITE_FORM,
            confidence = 0.9f
        ))
        
        // Volitional polite
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "ましょう",
            conjugationType = ConjugationType.VOLITIONAL_POLITE_FORM,
            confidence = 0.8f
        ))
        
        return completions
    }
    
    /**
     * Generate completions for masu group patterns (ま → ます, ません, ませんでした, ました, ましょう)
     */
    private fun generateMasuGroupCompletions(query: String): List<ConjugationCompletion> {
        val stem = query.dropLast(1) // Remove "ま"
        val completions = mutableListOf<ConjugationCompletion>()
        
        // Primary: masu-form
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "ます",
            conjugationType = ConjugationType.MASU_FORM,
            confidence = 0.95f
        ))
        
        // Negative polite
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "ません",
            conjugationType = ConjugationType.NEGATIVE_POLITE_FORM,
            confidence = 0.8f
        ))
        
        // Past polite
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "ました",
            conjugationType = ConjugationType.PAST_POLITE_FORM,
            confidence = 0.7f
        ))
        
        // Past negative polite
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "ませんでした",
            conjugationType = ConjugationType.PAST_NEGATIVE_POLITE_FORM,
            confidence = 0.6f
        ))
        
        // Volitional polite
        completions.add(ConjugationCompletion(
            original = query,
            completion = stem + "ましょう",
            conjugationType = ConjugationType.VOLITIONAL_POLITE_FORM,
            confidence = 0.5f
        ))
        
        return completions
    }
}

/**
 * Represents a potential conjugation completion
 */
data class ConjugationCompletion(
    val original: String,           // Original query (e.g., "たべｍ")
    val completion: String,         // Completed form (e.g., "たべます")
    val conjugationType: ConjugationType,
    val confidence: Float           // Confidence score (0.0 - 1.0)
)

/**
 * Types of conjugations
 */
enum class ConjugationType {
    DICTIONARY_FORM,              // 辞書形 (たべる)
    MASU_FORM,                   // ます形 (たべます)
    TE_FORM,                     // て形 (たべて)
    PAST_FORM,                   // 過去形 (たべた)
    NEGATIVE_FORM,               // 否定形 (たべない)
    PAST_POLITE_FORM,            // 過去丁寧形 (たべました)
    NEGATIVE_POLITE_FORM,        // 否定丁寧形 (たべません)
    PAST_NEGATIVE_POLITE_FORM,   // 過去否定丁寧形 (たべませんでした)
    VOLITIONAL_POLITE_FORM       // 意志丁寧形 (たべましょう)
}