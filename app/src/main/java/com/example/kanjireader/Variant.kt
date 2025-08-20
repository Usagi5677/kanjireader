package com.example.kanjireader

/**
 * Data class representing a kanji variant of a word
 * 
 * Examples:
 * - 見る has variants: 観る, 視る, 覧る
 * - 聞く has variant: 聴く
 * - 言う has variants: 云う, 謂う
 */
data class Variant(
    val jmdictId: String,         // JMdict entry ID
    val primaryKanji: String,     // The original kanji form we're looking variants for
    val variantKanji: String,     // The variant kanji form
    val reading: String,          // Shared reading (hiragana)
    val meaning: String?          // Brief meaning of the word
) {
    
    /**
     * Check if this variant represents a different kanji form
     */
    fun isDifferentForm(): Boolean {
        return primaryKanji != variantKanji
    }
    
    /**
     * Get a display label for this variant
     */
    fun getDisplayLabel(): String {
        return if (meaning != null) {
            "$variantKanji ($meaning)"
        } else {
            variantKanji
        }
    }
}