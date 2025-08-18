package com.example.kanjireader.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.kanjireader.VerbType

/**
 * Entity representing a saved word in the database.
 * Stores all relevant information from EnhancedWordResult.
 */
@Entity(tableName = "saved_words")
@TypeConverters(Converters::class)
data class SavedWordEntity(
    @PrimaryKey(autoGenerate = true)
    val wordId: Long = 0,
    val kanji: String?,
    val reading: String,
    val meanings: List<String>,
    val partOfSpeech: List<String>,
    val verbType: VerbType?,
    val isCommon: Boolean,
    val frequency: Int,
    val frequencyTags: List<String>,
    val fields: List<String>,
    val styles: List<String>,
    val addedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Create a SavedWordEntity from an EnhancedWordResult
         */
        fun fromEnhancedWordResult(result: com.example.kanjireader.EnhancedWordResult): SavedWordEntity {
            return SavedWordEntity(
                kanji = result.kanji,
                reading = result.reading,
                meanings = result.meanings,
                partOfSpeech = result.partOfSpeech,
                verbType = result.verbType,
                isCommon = result.isCommon,
                frequency = result.numericFrequency,
                frequencyTags = result.frequencyTags,
                fields = result.fields,
                styles = result.styles
            )
        }
    }
    
    /**
     * Convert back to EnhancedWordResult for use in the app
     */
    fun toEnhancedWordResult(): com.example.kanjireader.EnhancedWordResult {
        return com.example.kanjireader.EnhancedWordResult(
            kanji = kanji,
            reading = reading,
            meanings = meanings,
            partOfSpeech = partOfSpeech,
            verbType = verbType,
            isCommon = isCommon,
            numericFrequency = frequency,
            frequencyTags = frequencyTags,
            fields = fields,
            styles = styles
        )
    }
}