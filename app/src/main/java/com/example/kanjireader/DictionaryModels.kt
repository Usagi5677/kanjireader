package com.example.kanjireader

import java.io.Serializable

/**
 * Data models for dictionary operations and search results
 */

data class EnhancedWordResult(
    val kanji: String?,
    val reading: String,
    val meanings: List<String>,
    val partOfSpeech: List<String> = emptyList(),
    val verbType: VerbType? = null,
    val isCommon: Boolean = false,
    val numericFrequency: Int = 0,
    val frequencyTags: List<String> = emptyList(),
    val fields: List<String> = emptyList(),
    val styles: List<String> = emptyList()
) : Serializable

enum class VerbType : Serializable {
    ICHIDAN, 
    GODAN_K, GODAN_S, GODAN_T, GODAN_N, GODAN_B, 
    GODAN_M, GODAN_R, GODAN_G, GODAN_U, 
    SURU_IRREGULAR, KURU_IRREGULAR, IKU_IRREGULAR, 
    ADJECTIVE_I, ADJECTIVE_NA,
    UNKNOWN
}

data class TagEntry(
    val senses: List<Sense>?
)

data class Sense(
    val pos: List<String>?
)

data class WordResult(
    val kanji: String?,
    val reading: String,
    val meanings: List<String>,
    val isCommon: Boolean,
    val frequency: Int,
    val wordOrder: Int,
    val tags: List<String>,
    val partsOfSpeech: List<String>,
    val isDeinflectedValidConjugation: Boolean
)

data class KanjiResult(
    val kanji: String,
    val onReadings: List<String>,
    val kunReadings: List<String>,
    val meanings: List<String>,
    val strokeCount: Int?,
    val jlptLevel: Int?,
    val frequency: Int?,
    val grade: Int?,
    val nanori: List<String>,
    val radicalNames: List<String>,
    val classicalRadical: Int?,
    val radicalNumber: Int?,
    val components: List<String> = emptyList()
)

data class KanjiCardInfo(
    val kanji: String,
    val onReadings: String,  // Comma-separated for display
    val kunReadings: String, // Comma-separated for display
    val primaryMeaning: String, // First 2-3 meanings for card display
    val jlptLevel: Int? = null,
    val grade: Int? = null,
    val commonalityScore: Int = 0,  // Higher score = more common kanji
    val hasReadings: Boolean = true,  // False for "No readings available" cases
    val confidence: Int? = null  // Recognition confidence percentage (0-100) for drawing results
)

data class WordCardInfo(
    val word: String,
    val reading: String,
    val meanings: String, // First 3 meanings, comma-separated
    val startPosition: Int, // Start index in the original text
    val endPosition: Int,   // End index in the original text
    val isHighlighted: Boolean = false
)