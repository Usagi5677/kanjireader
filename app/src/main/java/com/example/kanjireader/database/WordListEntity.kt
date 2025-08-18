package com.example.kanjireader.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a word list in the database.
 * Users can create multiple lists to organize their saved words.
 */
@Entity(tableName = "word_lists")
data class WordListEntity(
    @PrimaryKey(autoGenerate = true)
    val listId: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val wordCount: Int = 0 // Cached count for performance
)