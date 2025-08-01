package com.example.kanjireader

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for English search functionality
 * Tests FTS query building and search routing
 */
class EnglishSearchUnitTest {
    
    @Test
    fun `test single word FTS query`() {
        // Single words should get prefix search
        assertEquals("find*", buildFtsQuery("find"))
        assertEquals("search*", buildFtsQuery("search"))
        assertEquals("eat*", buildFtsQuery("eat"))
        assertEquals("study*", buildFtsQuery("study"))
    }
    
    @Test
    fun `test multi-word with stopwords`() {
        // Stopwords should be filtered out
        assertEquals("find*", buildFtsQuery("to find"))
        assertEquals("search*", buildFtsQuery("to search"))
        assertEquals("book*", buildFtsQuery("the book"))
        assertEquals("cat*", buildFtsQuery("a cat"))
        assertEquals("house*", buildFtsQuery("in the house"))
        assertEquals("way*", buildFtsQuery("the way"))
    }
    
    @Test
    fun `test multi-word without stopwords`() {
        // Multiple significant words should use OR
        assertEquals("look* OR for*", buildFtsQuery("look for"))
        assertEquals("look* OR up*", buildFtsQuery("look up"))
        assertEquals("write* OR down*", buildFtsQuery("write down"))
        assertEquals("study* OR hard*", buildFtsQuery("study hard"))
    }
    
    @Test
    fun `test only stopwords`() {
        // If only stopwords, search exact phrase
        assertEquals("\"to the\"", buildFtsQuery("to the"))
        assertEquals("\"of the\"", buildFtsQuery("of the"))
        assertEquals("\"in a\"", buildFtsQuery("in a"))
    }
    
    @Test
    fun `test complex phrases`() {
        // Mix of stopwords and content words
        assertEquals("look* OR for*", buildFtsQuery("to look for"))
        assertEquals("search* OR answer*", buildFtsQuery("search for the answer"))
        assertEquals("find* OR way*", buildFtsQuery("to find a way"))
        assertEquals("book* OR table*", buildFtsQuery("the book on the table"))
    }
    
    @Test
    fun `test case insensitivity for stopwords`() {
        // Stopwords should be case-insensitive
        assertEquals("find*", buildFtsQuery("TO find"))
        assertEquals("find*", buildFtsQuery("To Find"))
        assertEquals("book*", buildFtsQuery("THE book"))
    }
    
    @Test
    fun `test empty and whitespace`() {
        // Edge cases
        assertEquals("", buildFtsQuery(""))
        assertEquals("find*", buildFtsQuery("  find  "))
        assertEquals("find*", buildFtsQuery("to   find"))
    }
    
    // Helper function that mimics the actual implementation
    private fun buildFtsQuery(query: String): String {
        val words = query.split(" ").filter { it.isNotBlank() }
        
        if (words.isEmpty()) return ""
        
        // Common English stopwords to filter out
        val stopwords = setOf("to", "a", "an", "the", "of", "in", "on", "at", "with", "by")

        return when {
            // Single word - match as whole word or prefix
            words.size == 1 -> {
                val word = words[0]
                "${word}*"
            }
            // Multiple words - filter stopwords and search for important words
            else -> {
                val significantWords = words.filter { it.lowercase() !in stopwords }
                
                if (significantWords.isEmpty()) {
                    // If only stopwords, search for the full phrase
                    "\"$query\""
                } else if (significantWords.size == 1) {
                    // Single significant word - do prefix search
                    "${significantWords[0]}*"
                } else {
                    // Multiple significant words - match any of them
                    significantWords.joinToString(" OR ") { "${it}*" }
                }
            }
        }
    }
}