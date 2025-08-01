package com.example.kanjireader

import org.junit.Test
import org.junit.Assert.*

/**
 * Simple test to verify basic dictionary functionality
 * This test verifies core search routing logic without complex mocking
 */
class SimpleDictionaryTest {

    @Test
    fun `test search routing logic`() {
        // Test the basic search routing logic that we implemented
        
        // Test 1: Japanese text detection
        assertTrue("Should detect Japanese text", isJapaneseText("みる"))
        assertTrue("Should detect Japanese text", isJapaneseText("日本語"))
        assertFalse("Should not detect English as Japanese", isJapaneseText("search"))
        
        // Test 2: Mixed script detection  
        assertTrue("Should detect mixed script", isMixedScript("国語woべんきょう"))
        assertFalse("Should not detect pure Japanese as mixed", isMixedScript("国語を勉強"))
        assertFalse("Should not detect pure English as mixed", isMixedScript("search"))
        
        // Test 3: English text detection
        assertTrue("Should detect English text", isEnglishText("search"))
        assertTrue("Should detect English text", isEnglishText("hello world"))
        assertFalse("Should not detect Japanese as English", isEnglishText("みる"))
        
        // Test 4: Romaji detection
        assertTrue("Should detect romaji", containsRomaji("nihongo"))
        assertTrue("Should detect romaji", containsRomaji("shiteimasu"))
        assertFalse("Should not detect pure hiragana as romaji", containsRomaji("にほんご"))
        
        // Test 5: Particle detection
        assertTrue("Should detect particle", isParticle("を"))
        assertTrue("Should detect particle", isParticle("は"))
        assertFalse("Should not detect regular word as particle", isParticle("日本語"))
    }
    
    @Test
    fun `test conjugation pattern detection`() {
        // Test the conjugation detection patterns we implemented
        
        assertTrue("Should detect ている form", isPotentiallyConjugated("みている"))
        assertTrue("Should detect てる form", isPotentiallyConjugated("みてる"))
        assertTrue("Should detect て form", isPotentiallyConjugated("みて"))
        assertTrue("Should detect ます form", isPotentiallyConjugated("みます"))
        assertTrue("Should detect ました form", isPotentiallyConjugated("みました"))
        
        assertFalse("Should not detect base form as conjugated", isPotentiallyConjugated("見る"))
        assertFalse("Should not detect short words as conjugated", isPotentiallyConjugated("て"))
    }
    
    @Test
    fun `test likely Japanese romaji detection`() {
        // Test the romaji vs English detection logic
        
        assertTrue("Should detect Japanese romaji", isLikelyJapaneseRomaji("nihongo"))
        assertTrue("Should detect Japanese romaji", isLikelyJapaneseRomaji("shiteimasu"))
        assertTrue("Should detect Japanese romaji", isLikelyJapaneseRomaji("benkyou"))
        
        assertFalse("Should not detect common English as romaji", isLikelyJapaneseRomaji("search"))
        assertFalse("Should not detect common English as romaji", isLikelyJapaneseRomaji("hello"))
        assertFalse("Should not detect common English as romaji", isLikelyJapaneseRomaji("world"))
    }
    
    // Helper functions that mirror the logic in DictionaryRepository
    private fun isJapaneseText(text: String): Boolean {
        return text.any { char ->
            val code = char.code
            // Hiragana, Katakana, or Kanji
            (code in 0x3040..0x309F) ||
                    (code in 0x30A0..0x30FF) ||
                    (code in 0x4E00..0x9FAF)
        }
    }
    
    private fun containsRomaji(text: String): Boolean {
        return text.any { it.code in 0x0041..0x007A }  // Has latin letters
    }
    
    private fun isMixedScript(text: String): Boolean {
        return isJapaneseText(text) && containsRomaji(text)
    }
    
    private fun isEnglishText(text: String): Boolean {
        return text.any { it.code in 0x0041..0x007A } &&  // Has English letters
                !isJapaneseText(text)  // But no Japanese
    }
    
    private fun isParticle(word: String): Boolean {
        return word in setOf("を", "は", "が", "の", "に", "で", "と", "から", "まで", "より", "へ", "や", "も", "か", "ね", "よ", "ぞ", "ぜ")
    }
    
    private fun isPotentiallyConjugated(query: String): Boolean {
        return query.endsWith("ています") || query.endsWith("ている") || 
               query.endsWith("てる") || query.endsWith("でる") ||
               query.endsWith("ました") || query.endsWith("ます") ||
               query.endsWith("でした") || query.endsWith("です") ||
               query.endsWith("んだ") || query.endsWith("った") ||
               query.endsWith("いた") || query.endsWith("えた") ||
               query.endsWith("した") || query.endsWith("きた") ||
               query.endsWith("ない") || query.endsWith("なく") ||
               // Only treat "て"/"で" as conjugated if it's more than 1 character and looks like a verb stem
               (query.length > 2 && (query.endsWith("て") || query.endsWith("で"))) ||
               // Specifically catch common te-forms like みて, きて, して, いて, etc.
               (query.length >= 2 && (query.matches(Regex(".*[いきしちにひみりぎじびぴ][て]")) ||
                query.matches(Regex(".*[いきしちにひみりぎじびぴ][で]"))))
    }
    
    private fun isLikelyJapaneseRomaji(text: String): Boolean {
        // Common Japanese romaji patterns
        val japaneseRomajiPatterns = listOf(
            ".*[aiueo].*[aiueo].*", // Multiple vowels (common in Japanese)
            ".*sh.*", ".*ch.*", ".*ts.*", ".*ky.*", ".*gy.*", ".*ny.*", // Japanese sounds
            ".*ou.*", ".*ei.*", ".*ai.*", // Long vowel patterns
            ".*masu.*", ".*desu.*", ".*shimasu.*", // Common endings
        )
        
        // Check if it matches Japanese patterns AND doesn't look like common English words
        val hasJapanesePattern = japaneseRomajiPatterns.any { text.matches(it.toRegex()) }
        val commonEnglishWords = setOf(
            "search", "the", "and", "for", "are", "with", "his", "they", "have", "this",
            "will", "you", "that", "but", "not", "from", "she", "been", "more", "were"
        )
        val isCommonEnglish = text.lowercase() in commonEnglishWords
        
        return hasJapanesePattern && !isCommonEnglish
    }
}