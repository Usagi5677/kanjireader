package com.example.kanjireader.database

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Data class for querying a word list with all its words.
 */
data class WordListWithWords(
    @Embedded val wordList: WordListEntity,
    @Relation(
        parentColumn = "listId",
        entityColumn = "wordId",
        associateBy = Junction(
            value = WordListCrossRef::class,
            parentColumn = "listId",
            entityColumn = "wordId"
        )
    )
    val words: List<SavedWordEntity>
)

/**
 * Data class for querying a saved word with all its lists.
 */
data class SavedWordWithLists(
    @Embedded val word: SavedWordEntity,
    @Relation(
        parentColumn = "wordId",
        entityColumn = "listId",
        associateBy = Junction(
            value = WordListCrossRef::class,
            parentColumn = "wordId",
            entityColumn = "listId"
        )
    )
    val lists: List<WordListEntity>
)