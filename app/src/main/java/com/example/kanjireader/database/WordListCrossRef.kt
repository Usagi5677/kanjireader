package com.example.kanjireader.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Cross-reference entity for many-to-many relationship between word lists and saved words.
 * A word can belong to multiple lists, and a list can contain multiple words.
 */
@Entity(
    tableName = "word_list_cross_ref",
    primaryKeys = ["listId", "wordId"],
    foreignKeys = [
        ForeignKey(
            entity = WordListEntity::class,
            parentColumns = ["listId"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SavedWordEntity::class,
            parentColumns = ["wordId"],
            childColumns = ["wordId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["listId"]),
        Index(value = ["wordId"])
    ]
)
data class WordListCrossRef(
    val listId: Long,
    val wordId: Long,
    val addedAt: Long = System.currentTimeMillis()
)