package com.example.kanjireader

/**
 * Represents a meaningful word extracted from a sentence with its dictionary information
 */
data class SentenceWord(
    val surface: String,           // Original form as it appears in sentence (e.g., "しています")
    val baseForm: String,          // Dictionary form (e.g., "する")
    val reading: String?,          // Reading in katakana
    val partOfSpeech: String,      // Part of speech (e.g., "動詞", "名詞")
    val meanings: List<String>,    // Dictionary meanings
    val frequency: Int = 0,        // Word frequency
    val isCommon: Boolean = false, // Whether it's a common word
    val conjugationInfo: String? = null // Conjugation description if applicable
)

/**
 * Result of analyzing a sentence for its component words
 */
data class SentenceAnalysis(
    val originalSentence: String,
    val normalizedSentence: String,  // After romaji conversion
    val words: List<SentenceWord>,
    val hasVerb: Boolean = false,
    val tense: String? = null
)

/**
 * Roles that words can play in a sentence
 */
enum class WordRole {
    SUBJECT,
    OBJECT, 
    VERB,
    ADJECTIVE,
    NOUN,
    PARTICLE,
    AUXILIARY,
    OTHER
}