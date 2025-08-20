package com.example.kanjireader

/**
 * Data class representing pitch accent information for a Japanese word
 * 
 * The accent numbers correspond to where the pitch drops in the word:
 * - 0 (Heiban): Low-High pattern with no drop
 * - 1 (Atama): High-Low pattern (drops after first mora)  
 * - 2+ (Nakadaka): Low-High-Low pattern (drops after specified mora)
 */
data class PitchAccent(
    val kanjiForm: String,          // The word form (kanji or kana)
    val reading: String,            // Hiragana reading
    val accentNumbers: List<Int>,   // List of accent pattern numbers
    val accentPattern: String       // Comma-separated accent numbers as string
) {
    
    /**
     * Get the primary accent pattern (first one if multiple exist)
     */
    val primaryAccent: Int
        get() = accentNumbers.firstOrNull() ?: 0
    
    /**
     * Check if this word has multiple accent patterns
     */
    val hasMultiplePatterns: Boolean
        get() = accentNumbers.size > 1
    
    /**
     * Get accent type description
     */
    val accentType: AccentType
        get() = when (primaryAccent) {
            0 -> AccentType.HEIBAN
            1 -> AccentType.ATAMA
            else -> AccentType.NAKADAKA
        }
    
    /**
     * Generate visual representation of the pitch pattern
     * Uses underscores for low pitch and overlines for high pitch
     */
    fun generatePitchPattern(moraCount: Int): String {
        if (moraCount <= 0) return ""
        
        return when (primaryAccent) {
            0 -> { // Heiban: Low-High with no drop
                if (moraCount == 1) "￣" 
                else "_" + "￣".repeat(moraCount - 1)
            }
            1 -> { // Atama: High-Low 
                if (moraCount == 1) "￣"
                else "￣" + "_".repeat(moraCount - 1)
            }
            else -> { // Nakadaka: Low-High-Low with drop after accent position
                if (moraCount == 1) "￣"
                else if (primaryAccent >= moraCount) {
                    // Drop is at or after the end, treat as Heiban
                    "_" + "￣".repeat(moraCount - 1)
                } else {
                    val beforeDrop = if (primaryAccent > 1) "_" + "￣".repeat(primaryAccent - 1) else ""
                    val afterDrop = "_".repeat(moraCount - primaryAccent)
                    beforeDrop + afterDrop
                }
            }
        }
    }
}

/**
 * Enum representing the three main types of Japanese pitch accent
 */
enum class AccentType(val description: String) {
    HEIBAN("平板型"),      // Flat type - no pitch drop
    ATAMA("頭高型"),       // Head-high type - drops after first mora  
    NAKADAKA("中高型")     // Mid-high type - drops at specified position
}