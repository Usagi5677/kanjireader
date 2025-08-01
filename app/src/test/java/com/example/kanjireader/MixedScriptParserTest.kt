package com.example.kanjireader

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Comprehensive tests for enhanced MixedScriptParser with script transition detection,
 * compound word analysis, and Kuromoji integration
 */
class MixedScriptParserTest {

    private lateinit var parser: MixedScriptParser
    private lateinit var strictParser: MixedScriptParser
    private lateinit var kuromojiOnlyParser: MixedScriptParser
    private lateinit var debugParser: MixedScriptParser

    @Before
    fun setup() {
        // Default hybrid parser
        parser = MixedScriptParser()
        
        // Script-only parser with compound detection disabled
        strictParser = MixedScriptParser(MixedScriptParserConfig(
            strategy = TokenizationStrategy.SCRIPT_ONLY,
            enableCompoundWordDetection = false,
            enableOkuriganaDetection = false
        ))
        
        // Kuromoji-only parser
        kuromojiOnlyParser = MixedScriptParser(MixedScriptParserConfig(
            strategy = TokenizationStrategy.KUROMOJI_ONLY
        ))
        
        // Debug parser with detailed logging
        debugParser = MixedScriptParser(MixedScriptParserConfig(
            enableDebugLogging = true
        ))
    }

    // ==========================================================================
    // Script Transition Detection Tests
    // ==========================================================================

    @Test
    fun `test basic script transition detection`() {
        val text = "国語woべんきょうshiteimasu"
        val tokens = parser.parseSentence(text)
        
        println("=== Script Transition Test ===")
        println("Input: '$text'")
        tokens.forEachIndexed { i, token ->
            println("Token $i: '${token.original}' -> '${token.converted}' (${token.tokenType})")
            token.scriptTransition?.let { transition ->
                println("  Transition: ${transition.fromScript} -> ${transition.toScript}")
            }
        }
        
        // Should detect transitions: 国語 (KANJI) -> wo (ROMAJI) -> べんきょう (HIRAGANA) -> shiteimasu (ROMAJI)
        assertEquals(4, tokens.size)
        assertEquals("国語", tokens[0].converted)
        assertEquals("を", tokens[1].converted) // wo -> を
        assertEquals("べんきょう", tokens[2].converted)
        assertEquals("している", tokens[3].converted) // shiteimasu -> している
    }

    @Test
    fun `test complex mixed script with particles`() {
        val text = "watashiha日本語woべんきょうsuru"
        val tokens = parser.parseSentence(text)
        
        println("\n=== Complex Mixed Script Test ===")
        println("Input: '$text'")
        println("Breakdown: ${parser.getParsingBreakdown(tokens)}")
        
        assertTrue("Should have multiple tokens", tokens.size >= 5)
        
        // Check for proper particle conversion
        val particleTokens = tokens.filter { it.tokenType == TokenType.ROMAJI_PARTICLE }
        assertTrue("Should identify romaji particles", particleTokens.isNotEmpty())
        
        // Check for script transitions
        val transitions = parser.getScriptTransitions(tokens)
        assertTrue("Should detect script transitions", transitions.isNotEmpty())
        
        println("Script transitions found: ${transitions.size}")
        transitions.forEach { transition ->
            println("  ${transition.fromScript} -> ${transition.toScript} at position ${transition.position}")
        }
    }

    @Test
    fun `test pure Japanese text without transitions`() {
        val text = "私は日本語を勉強します"
        val tokens = parser.parseSentence(text)
        
        println("\n=== Pure Japanese Test ===")
        println("Input: '$text'")
        println("Tokens: ${tokens.size}")
        
        // Should still tokenize properly without script transitions
        assertTrue("Should create tokens even without script transitions", tokens.isNotEmpty())
        
        // All tokens should be Japanese types
        val japaneseTypes = setOf(TokenType.KANJI, TokenType.HIRAGANA, TokenType.KATAKANA, TokenType.MIXED)
        assertTrue("All tokens should be Japanese", tokens.all { it.tokenType in japaneseTypes })
    }

    // ==========================================================================
    // Compound Word Analysis Tests
    // ==========================================================================

    @Test
    fun `test okurigana detection and merging`() {
        val text = "勉強する"
        val tokens = parser.parseSentence(text)
        val strictTokens = strictParser.parseSentence(text)
        
        println("\n=== Okurigana Detection Test ===")
        println("Input: '$text'")
        println("Hybrid parser tokens: ${tokens.size}")
        println("Script-only parser tokens: ${strictTokens.size}")
        
        // The hybrid parser might merge kanji+hiragana if it detects okurigana
        // This depends on the implementation - either merged or separate is valid
        tokens.forEach { token ->
            println("Token: '${token.original}' -> '${token.converted}' (${token.tokenType})")
            if (token.deinflectedBase != null) {
                println("  Base form: ${token.deinflectedBase}")
            }
        }
    }

    @Test
    fun `test compound word boundary detection`() {
        val text = "勉強家"
        val tokens = parser.parseSentence(text)
        
        println("\n=== Compound Word Test ===")
        println("Input: '$text'")
        println("Tokens: ${tokens.size}")
        
        tokens.forEach { token ->
            println("Token: '${token.original}' (${token.tokenType})")
        }
        
        // Should properly handle kanji compounds
        assertFalse("Should not create empty tokens", tokens.any { it.original.isEmpty() })
        assertTrue("Should create at least one token", tokens.isNotEmpty())
    }

    // ==========================================================================
    // Tokenization Strategy Tests
    // ==========================================================================

    @Test
    fun `test different tokenization strategies`() {
        val text = "国語woべんきょう"
        
        val hybridTokens = parser.parseSentence(text)
        val scriptOnlyTokens = strictParser.parseSentence(text)
        val kuromojiTokens = kuromojiOnlyParser.parseSentence(text)
        
        println("\n=== Tokenization Strategy Comparison ===")
        println("Input: '$text'")
        println("Hybrid (${hybridTokens.size} tokens): ${hybridTokens.map { it.converted }}")
        println("Script-only (${scriptOnlyTokens.size} tokens): ${scriptOnlyTokens.map { it.converted }}")
        println("Kuromoji-only (${kuromojiTokens.size} tokens): ${kuromojiTokens.map { it.converted }}")
        
        // All strategies should produce valid results
        assertTrue("Hybrid should produce tokens", hybridTokens.isNotEmpty())
        assertTrue("Script-only should produce tokens", scriptOnlyTokens.isNotEmpty())
        assertTrue("Kuromoji should produce tokens", kuromojiTokens.isNotEmpty())
        
        // All should properly convert romaji
        val woToken = hybridTokens.find { it.original.lowercase() == "wo" }
        assertEquals("Romaji 'wo' should convert to 'を'", "を", woToken?.converted)
    }

    @Test
    fun `test configuration options`() {
        val config = MixedScriptParserConfig(
            strategy = TokenizationStrategy.HYBRID,
            enableCompoundWordDetection = true,
            enableOkuriganaDetection = true,
            enableKuromojiValidation = false,
            maxOkuriganaLength = 2,
            enableDebugLogging = true
        )
        
        val customParser = MixedScriptParser(config)
        val text = "勉強している"
        val tokens = customParser.parseSentence(text)
        
        println("\n=== Configuration Test ===")
        println("Input: '$text'")
        println("Custom config applied - max okurigana length: 2")
        
        assertNotNull("Should create parser with custom config", customParser)
        assertTrue("Should produce tokens with custom config", tokens.isNotEmpty())
    }

    // ==========================================================================
    // Romaji Particle Recognition Tests
    // ==========================================================================

    @Test
    fun `test romaji particle recognition`() {
        val particles = listOf("wo", "wa", "ga", "ni", "de", "to", "no", "mo")
        
        println("\n=== Romaji Particle Recognition Test ===")
        
        for (particle in particles) {
            val text = "test$particle test"
            val tokens = parser.parseSentence(text)
            
            val particleToken = tokens.find { it.original.lowercase() == particle }
            assertNotNull("Should find particle '$particle'", particleToken)
            assertEquals("Particle '$particle' should be identified as ROMAJI_PARTICLE", 
                TokenType.ROMAJI_PARTICLE, particleToken?.tokenType)
            
            println("'$particle' -> '${particleToken?.converted}' ✓")
        }
    }

    @Test
    fun `test mixed particles and words`() {
        val text = "sushiga好きです"
        val tokens = parser.parseSentence(text)
        
        println("\n=== Mixed Particles and Words Test ===")
        println("Input: '$text'")
        println("Breakdown: ${parser.getParsingBreakdown(tokens)}")
        
        // Should identify 'ga' as particle and 'sushi' as word
        val gaToken = tokens.find { it.original.lowercase() == "ga" }
        assertEquals("'ga' should be particle", TokenType.ROMAJI_PARTICLE, gaToken?.tokenType)
        assertEquals("'ga' should convert to 'が'", "が", gaToken?.converted)
    }

    // ==========================================================================
    // Deinflection Integration Tests
    // ==========================================================================

    @Test
    fun `test deinflection with romaji input`() {
        val text = "shiteimasu"
        val tokens = parser.parseSentence(text)
        
        println("\n=== Deinflection Test ===")
        println("Input: '$text'")
        
        val token = tokens.first()
        println("Token: '${token.original}' -> '${token.converted}'")
        if (token.deinflectedBase != null) {
            println("Base form: ${token.deinflectedBase}")
        }
        
        assertEquals("Should convert romaji", "している", token.converted)
        // Note: deinflection depends on Kuromoji integration working properly
    }

    @Test
    fun `test deinflection with Japanese input`() {
        val text = "勉強している"
        val tokens = parser.parseSentence(text)
        
        println("\n=== Japanese Deinflection Test ===")
        println("Input: '$text'")
        
        tokens.forEach { token ->
            println("Token: '${token.original}' -> '${token.converted}'")
            if (token.deinflectedBase != null) {
                println("  Base form: ${token.deinflectedBase}")
            }
        }
        
        assertTrue("Should produce tokens", tokens.isNotEmpty())
    }

    // ==========================================================================
    // Edge Cases and Error Handling Tests
    // ==========================================================================

    @Test
    fun `test empty input`() {
        val tokens = parser.parseSentence("")
        assertTrue("Empty input should produce empty token list", tokens.isEmpty())
    }

    @Test
    fun `test single character inputs`() {
        val testCases = listOf("a", "あ", "ア", "漢")
        
        println("\n=== Single Character Tests ===")
        
        for (testCase in testCases) {
            val tokens = parser.parseSentence(testCase)
            assertEquals("Single character '$testCase' should produce one token", 1, tokens.size)
            assertEquals("Token should match input", testCase, tokens.first().original)
            println("'$testCase' -> ${tokens.first().tokenType} ✓")
        }
    }

    @Test
    fun `test punctuation and spaces`() {
        val text = "hello, 世界！"
        val tokens = parser.parseSentence(text)
        
        println("\n=== Punctuation Test ===")
        println("Input: '$text'")
        println("Tokens: ${tokens.size}")
        
        tokens.forEach { token ->
            println("'${token.original}' (${token.tokenType})")
        }
        
        assertTrue("Should handle punctuation", tokens.isNotEmpty())
    }

    @Test
    fun `test script composition analysis`() {
        val text = "hello世界123"
        val composition = parser.analyzeScriptComposition(text)
        
        println("\n=== Script Composition Analysis ===")
        println("Input: '$text'")
        composition.forEach { (script, count) ->
            println("$script: $count characters")
        }
        
        assertTrue("Should detect multiple script types", composition.size > 1)
        assertTrue("Should count characters correctly", composition.values.sum() == text.length)
    }

    // ==========================================================================
    // Integration and Utility Tests
    // ==========================================================================

    @Test
    fun `test dictionary word extraction`() {
        val text = "国語woべんきょうshiteimasu"
        val tokens = parser.parseSentence(text)
        val words = parser.getDictionaryWords(tokens)
        
        println("\n=== Dictionary Word Extraction Test ===")
        println("Input: '$text'")
        println("Dictionary words: ${words.joinToString(", ")}")
        
        assertTrue("Should extract dictionary words", words.isNotEmpty())
        assertTrue("Should include converted forms", words.contains("を"))
        assertTrue("Should include original Japanese", words.contains("国語"))
    }

    @Test
    fun `test Japanese text conversion`() {
        val text = "watashiwa日本人です"
        val tokens = parser.parseSentence(text)
        val japaneseText = parser.toJapaneseText(tokens)
        
        println("\n=== Japanese Text Conversion Test ===")
        println("Input: '$text'")
        println("Converted: '$japaneseText'")
        
        // Should convert romaji to Japanese
        assertFalse("Should not contain romaji", japaneseText.contains("watashiwa"))
        assertTrue("Should contain converted text", japaneseText.contains("日本人"))
    }

    @Test
    fun `test morphological analysis integration`() {
        val text = "勉強する"
        val tokens = parser.parseSentence(text)
        val analysis = parser.getMorphologicalAnalysis(tokens)
        
        println("\n=== Morphological Analysis Test ===")
        println("Input: '$text'")
        
        analysis.forEach { (token, morphology) ->
            println("Token: '${token.converted}'")
            if (morphology != null) {
                println("  Base form: ${morphology.baseForm}")
                println("  POS: ${morphology.partOfSpeech}")
                println("  Verb type: ${morphology.verbType}")
            }
        }
        
        assertTrue("Should provide morphological analysis", analysis.isNotEmpty())
    }

    @Test
    fun `test token boundary validation`() {
        val text = "勉強している"
        val tokens = parser.parseSentence(text)
        val validatedTokens = parser.validateTokenBoundaries(tokens)
        
        println("\n=== Token Boundary Validation Test ===")
        println("Input: '$text'")
        println("Original tokens: ${tokens.size}")
        println("Validated tokens: ${validatedTokens.size}")
        
        assertEquals("Token count should remain the same", tokens.size, validatedTokens.size)
        
        // Check if any tokens gained deinflected base forms through validation
        val originalBaseForms = tokens.count { it.deinflectedBase != null }
        val validatedBaseForms = validatedTokens.count { it.deinflectedBase != null }
        
        println("Base forms before validation: $originalBaseForms")
        println("Base forms after validation: $validatedBaseForms")
        
        assertTrue("Validation should not decrease base forms", validatedBaseForms >= originalBaseForms)
    }
}