package com.example.kanjireader

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any

/**
 * Test cases for TenTenDeinflectionEngine
 * Tests common Japanese conjugations to ensure deinflection works correctly
 */
class TenTenDeinflectionEngineTest {

    private lateinit var deinflectionEngine: TenTenDeinflectionEngine
    
    @Mock
    private lateinit var mockTagDictLoader: TagDictSQLiteLoader

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        deinflectionEngine = TenTenDeinflectionEngine()
        
        // Mock tag loader to return basic verb types
        whenever(mockTagDictLoader.isTagDatabaseReady()).thenReturn(true)
        whenever(mockTagDictLoader.getVerbType(any())).thenReturn(VerbType.ICHIDAN) // Default to ichidan
    }

    /**
     * Test Case 1: Basic Ichidan verb conjugations
     * These should work if the deinflection engine is functioning
     */
    @Test
    fun testIchidanVerbConjugations() {
        println("=== Testing Ichidan Verb Conjugations ===")
        
        // Test たべる (ichidan verb) conjugations
        testDeinflection("たべた", "たべる", "past tense")  // 食べた → 食べる
        testDeinflection("たべて", "たべる", "te-form")    // 食べて → 食べる
        testDeinflection("たべない", "たべる", "negative")  // 食べない → 食べる
        testDeinflection("たべられる", "たべる", "potential") // 食べられる → 食べる
        
        // Test みる (ichidan verb) conjugations  
        testDeinflection("みた", "みる", "past tense")    // 見た → 見る
        testDeinflection("みて", "みる", "te-form")      // 見て → 見る
        testDeinflection("みない", "みる", "negative")    // 見ない → 見る
        testDeinflection("みられる", "みる", "potential")  // 見られる → 見る
    }

    /**
     * Test Case 2: Basic Godan verb conjugations
     */
    @Test
    fun testGodanVerbConjugations() {
        println("=== Testing Godan Verb Conjugations ===")
        
        // Mock godan verb types
        whenever(mockTagDictLoader.getVerbType("いく")).thenReturn(VerbType.GODAN_KU)
        whenever(mockTagDictLoader.getVerbType("よむ")).thenReturn(VerbType.GODAN_MU)
        
        // Test いく (godan ku verb) conjugations
        testDeinflection("いった", "いく", "past tense")   // 行った → 行く
        testDeinflection("いって", "いく", "te-form")     // 行って → 行く
        testDeinflection("いかない", "いく", "negative")   // 行かない → 行く
        
        // Test よむ (godan mu verb) conjugations
        testDeinflection("よんだ", "よむ", "past tense")   // 読んだ → 読む
        testDeinflection("よんで", "よむ", "te-form")     // 読んで → 読む
        testDeinflection("よまない", "よむ", "negative")   // 読まない → 読む
    }

    /**
     * Test Case 3: Irregular verbs (most important)
     */
    @Test
    fun testIrregularVerbConjugations() {
        println("=== Testing Irregular Verb Conjugations ===")
        
        // Mock irregular verb types
        whenever(mockTagDictLoader.getVerbType("する")).thenReturn(VerbType.IRREGULAR_SURU)
        whenever(mockTagDictLoader.getVerbType("くる")).thenReturn(VerbType.IRREGULAR_KURU)
        
        // Test する (irregular) conjugations
        testDeinflection("した", "する", "past tense")     // した → する
        testDeinflection("して", "する", "te-form")       // して → する
        testDeinflection("している", "する", "progressive") // している → する
        testDeinflection("しない", "する", "negative")     // しない → する
        
        // Test くる (irregular) conjugations
        testDeinflection("きた", "くる", "past tense")     // 来た → 来る
        testDeinflection("きて", "くる", "te-form")       // 来て → 来る
        testDeinflection("こない", "くる", "negative")     // 来ない → 来る
    }

    /**
     * Test Case 4: Progressive forms (している pattern)
     */
    @Test
    fun testProgressiveForms() {
        println("=== Testing Progressive Forms ===")
        
        testDeinflection("たべている", "たべる", "progressive") // 食べている → 食べる
        testDeinflection("みている", "みる", "progressive")   // 見ている → 見る
        testDeinflection("している", "する", "progressive")   // している → する
        testDeinflection("いっている", "いく", "progressive") // 行っている → 行く
    }

    /**
     * Test Case 5: Polite forms
     */
    @Test
    fun testPoliteForms() {
        println("=== Testing Polite Forms ===")
        
        testDeinflection("たべます", "たべる", "polite")      // 食べます → 食べる
        testDeinflection("みます", "みる", "polite")        // 見ます → 見る
        testDeinflection("します", "する", "polite")        // します → する
        testDeinflection("いきます", "いく", "polite")       // 行きます → 行く
    }

    /**
     * Test Case 6: Words that should NOT be deinflected
     */
    @Test
    fun testNonConjugatedWords() {
        println("=== Testing Non-Conjugated Words ===")
        
        // These should return empty results (not be deinflected)
        testNoDeinflection("ねこ", "noun - should not deinflect")        // 猫
        testNoDeinflection("おおきい", "i-adjective - base form")         // 大きい
        testNoDeinflection("きれい", "na-adjective - should not deinflect") // きれい
        testNoDeinflection("たべもの", "compound noun")                   // 食べ物
    }

    /**
     * Helper method to test deinflection
     */
    private fun testDeinflection(conjugated: String, expectedBase: String, description: String) {
        println("Testing: $conjugated → $expectedBase ($description)")
        
        val results = deinflectionEngine.deinflect(conjugated, mockTagDictLoader)
        
        if (results.isEmpty()) {
            println("  ❌ FAIL: No deinflection found for '$conjugated'")
            fail("Expected deinflection of '$conjugated' to '$expectedBase' but got no results")
        } else {
            val bestResult = results.first()
            println("  Result: ${bestResult.originalForm} → ${bestResult.baseForm} (${bestResult.verbType})")
            
            if (bestResult.baseForm == expectedBase) {
                println("  ✅ PASS")
            } else {
                println("  ❌ FAIL: Expected '$expectedBase' but got '${bestResult.baseForm}'")
                fail("Expected deinflection of '$conjugated' to '$expectedBase' but got '${bestResult.baseForm}'")
            }
        }
        println()
    }

    /**
     * Helper method to test that words should NOT be deinflected
     */
    private fun testNoDeinflection(word: String, description: String) {
        println("Testing no deinflection: $word ($description)")
        
        val results = deinflectionEngine.deinflect(word, mockTagDictLoader)
        
        if (results.isEmpty()) {
            println("  ✅ PASS: Correctly identified as non-conjugated")
        } else {
            val result = results.first()
            println("  ❌ FAIL: Incorrectly deinflected to '${result.baseForm}'")
            fail("Word '$word' should not be deinflected but got result: ${result.baseForm}")
        }
        println()
    }

    /**
     * Test Case 7: Engine initialization
     */
    @Test
    fun testEngineInitialization() {
        println("=== Testing Engine Initialization ===")
        
        // Test that engine initializes without errors
        try {
            val engine = TenTenDeinflectionEngine()
            println("✅ Engine created successfully")
            
            // Test that it can handle basic calls
            val results = engine.deinflect("test", mockTagDictLoader)
            println("✅ Engine accepts deinflection calls")
            
        } catch (e: Exception) {
            println("❌ Engine initialization failed: ${e.message}")
            fail("Engine should initialize without errors")
        }
    }

    /**
     * Manual test runner - prints results to console
     */
    @Test
    fun runAllManualTests() {
        println("\n" + "=".repeat(50))
        println("MANUAL DEINFLECTION ENGINE TEST RESULTS")
        println("=".repeat(50))
        
        try {
            testEngineInitialization()
            testIchidanVerbConjugations()
            testIrregularVerbConjugations()
            testProgressiveForms()
            testPoliteForms()
            testNonConjugatedWords()
            
            println("=".repeat(50))
            println("✅ ALL TESTS COMPLETED")
            println("Check output above for specific pass/fail results")
            println("=".repeat(50))
            
        } catch (e: Exception) {
            println("❌ TEST SUITE FAILED: ${e.message}")
            e.printStackTrace()
        }
    }
}

/**
 * Usage Instructions:
 * 
 * 1. Run this test: ./gradlew test --tests="TenTenDeinflectionEngineTest"
 * 2. Check console output for detailed results
 * 3. Look for ✅ PASS and ❌ FAIL markers
 * 
 * Expected Results:
 * - If deinflection engine is working: Most basic tests should pass
 * - If engine has issues: Will show exactly which conjugations fail
 * - This will help isolate whether the issue is in the engine or integration
 */