package com.example.kanjireader

// UnifiedDictionaryEntry.kt
data class UnifiedDictionaryEntry(
    val primaryForm: String,
    val primaryReading: String?,
    val primaryTags: List<String>,
    val variants: List<VariantInfo>,
    val meanings: List<String>,
    val isCommon: Boolean = false,
    val originalSearchTerm: String? = null,
    val conjugationInfo: String? = null,
    val verbType: String? = null,
    val frequency: Int? = null,
    val sourceType: String? = null,  // For parallel search labeling
    val isJMNEDictEntry: Boolean = false,
    val isDeinflectedResult: Boolean = false,
    val pitchAccents: List<PitchAccent>? = null  // Pitch accent information
)

data class VariantInfo(
    val text: String,
    val allTags: List<String>,  // Changed from uniqueTags to allTags
    val isCommon: Boolean = false
)