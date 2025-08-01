package com.example.kanjireader

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
)

enum class VerbType {
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
    val isJMNEDictEntry: Boolean,
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