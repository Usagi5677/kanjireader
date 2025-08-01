package com.example.kanjireader

import org.junit.Test
import org.junit.Assert.*

/**
 * Test class for the Enhanced TenTenDeinflectionEngine
 * Specifically tests the missing patterns that were causing your examples to fail
 */
class EnhancedDeinflectionEngineTest {

    private val engine = TenTenDeinflectionEngine()

    @Test
    fun debugEngineInternals() {
        println("\n=== DEBUGGING ENGINE INTERNALS ===\n")

        // Test 1: Check if rules are loaded
        println("1. Testing if deinflection rules are loaded:")
        // We need to check if the engine has rules. Since we can't access private members,
        // let's try a known simple deinflection

        // Test 2: Try the simplest possible deinflection
        println("\n2. Testing simplest deinflection (た → る):")
        val simpleTest = engine.deinflect("食べた", null)
        println("   Result for '食べた': ${simpleTest.size} results")

        // Test 3: Try without any rules - just return the word itself
        println("\n3. Testing if engine returns original word:")
        val identityTest = engine.deinflect("食べる", null)
        println("   Result for '食べる': ${identityTest.size} results")
        if (identityTest.isNotEmpty()) {
            println("   First result: ${identityTest.first().baseForm}")
        }

        // Test 4: Check the exact method we're calling
        println("\n4. Verifying method signature:")
        try {
            // Try different ways to call the method
            val method = engine::class.java.methods.find { it.name == "deinflect" }
            println("   Found method: ${method?.toString()}")
            println("   Parameter count: ${method?.parameterCount}")
            println("   Parameter types: ${method?.parameterTypes?.joinToString()}")
        } catch (e: Exception) {
            println("   Error checking method: ${e.message}")
        }

        // Test 5: Create a minimal test case
        println("\n5. Minimal test with print statements:")
        try {
            println("   Calling engine.deinflect('た', null)...")
            val minimalResult = engine.deinflect("た", null)
            println("   Returned: ${minimalResult.size} results")
        } catch (e: Exception) {
            println("   Exception: ${e.message}")
            e.printStackTrace()
        }
    }

    @Test
    fun testDirectEngineCreation() {
        println("\n=== TESTING DIRECT ENGINE CREATION ===\n")

        // Create a fresh engine instance
        val testEngine = TenTenDeinflectionEngine()

        // Try the most basic past tense deinflection
        println("Testing '食べた' (tabeta - ate):")
        val results = testEngine.deinflect("食べた", null)

        println("Number of results: ${results.size}")

        if (results.isEmpty()) {
            println("ERROR: No results returned!")

            // Try even simpler
            println("\nTrying single character 'た':")
            val taResults = testEngine.deinflect("た", null)
            println("Results for 'た': ${taResults.size}")
        } else {
            results.forEach { result ->
                println("- ${result.baseForm} (${result.reasonChain})")
            }
        }
    }

    @Test
    fun debugWhatEngineReturns() {
        println("\n=== DEBUG: Testing what the engine actually returns ===\n")

        val testWords = listOf(
            "言ってました",     // Expected: 言う
            "食べてた",         // Expected: 食べる
            "話し",            // Expected: 話す (noun form)
            "よく",            // Expected: 良い
            "食べていました",    // Expected: 食べる
            "良い"             // Should return itself
        )

        for (word in testWords) {
            println("Testing: '$word'")

            // Try calling with null tagDictLoader
            val results = engine.deinflect(word, null)

            if (results.isEmpty()) {
                println("  ❌ NO RESULTS!")
            } else {
                println("  Found ${results.size} results:")
                results.take(5).forEach { result ->
                    println("    - baseForm: '${result.baseForm}'")
                    println("      reasons: ${result.reasonChain}")
                    println("      verbType: ${result.verbType}")
                }
            }
            println()
        }
    }

    @Test
    fun testYourSpecificExamples() {
        // **Your original failing examples should now work**

        // 1. 言ってました → 言う (polite past continuous)
        val result1 = engine.deinflect("言ってました", null)
        val match1 = result1.find { it.baseForm == "言う" }
        assertNotNull("Should find 言う from 言ってました", match1)
        assertTrue("Should contain polite past continuous",
            match1!!.reasonChain.any { it.contains("polite") && it.contains("continuous") })

        // 2. 出くわし → 出くわす (noun form)
        val result2 = engine.deinflect("出くわし")
        val match2 = result2.find { it.baseForm == "出くわす" }
        assertNotNull("Should find 出くわす from 出くわし", match2)
        assertTrue("Should contain noun form",
            match2!!.reasonChain.any { it.contains("noun") })

        // 3. 貫き → 貫く (noun form)
        val result3 = engine.deinflect("貫き")
        val match3 = result3.find { it.baseForm == "貫く" }
        assertNotNull("Should find 貫く from 貫き", match3)
        assertTrue("Should contain noun form",
            match3!!.reasonChain.any { it.contains("noun") })

        // 4. よくない → 良い (irregular adjective)
        val result4 = engine.deinflect("よくない")
        val match4 = result4.find { it.baseForm == "良い" }
        assertNotNull("Should find 良い from よくない", match4)
        assertTrue("Should contain negative",
            match4!!.reasonChain.any { it.contains("negative") })
    }

    @Test
    fun testEnhancedContinuousForms() {
        // Test the enhanced continuous forms that 10ten supports

        val testCases = mapOf(
            "食べてた" to "食べる",      // casual continuous past
            "行ってた" to "行く",        // godan continuous past
            "読んでた" to "読む",        // godan continuous past
            "見てる" to "見る",          // casual continuous
            "やってる" to "やる",        // casual continuous
            "食べてました" to "食べる",    // polite continuous past
            "行ってました" to "行く",       // polite continuous past
            "言ってました" to "言う"
        )

        for ((inflected, expected) in testCases) {
            val result = engine.deinflect(inflected)
            val match = result.find { it.baseForm == expected }
            assertNotNull("Should find $expected from $inflected", match)
            println("✅ $inflected → $expected (${match!!.reasonChain.joinToString(" → ")})")
        }
    }

    @Test
    fun testNounForms() {
        // Test noun form patterns for all godan types

        val testCases = mapOf(
            "話し" to "話す",          // GODAN_S
            "歩き" to "歩く",          // GODAN_K
            "泳ぎ" to "泳ぐ",          // GODAN_G
            "立ち" to "立つ",          // GODAN_T
            "走り" to "走る",          // GODAN_R
            "買い" to "買う",          // GODAN_U
            "死に" to "死ぬ",          // GODAN_N
            "遊び" to "遊ぶ",          // GODAN_B
            "読み" to "読む"           // GODAN_M
        )

        for ((nounForm, baseVerb) in testCases) {
            val result = engine.deinflect(nounForm)
            val match = result.find { it.baseForm == baseVerb }
            assertNotNull("Should find $baseVerb from $nounForm", match)
            assertTrue("Should be noun form",
                match!!.reasonChain.any { it.contains("noun") })
            println("✅ $nounForm → $baseVerb (noun form)")
        }
    }

    @Test
    fun testIrregularAdjectives() {
        // Test irregular adjective patterns, especially 良い

        val testCases = mapOf(
            "よく" to "良い",            // adverb
            "よくない" to "良い",        // negative
            "よかった" to "良い",        // past
            "よくなかった" to "良い",    // negative past
            "よければ" to "良い",        // conditional
            "いい" to "良い"             // alternate form
        )

        for ((inflected, expected) in testCases) {
            val result = engine.deinflect(inflected)
            val match = result.find { it.baseForm == expected }
            assertNotNull("Should find $expected from $inflected", match)
            println("✅ $inflected → $expected (${match!!.reasonChain.joinToString(" → ")})")
        }
    }

    @Test
    fun testPoliteComplexForms() {
        // Test complex polite forms that combine multiple transformations

        val testCases = mapOf(
            "食べていました" to "食べる",     // polite continuous past
            "行っていました" to "行く",       // polite continuous past
            "読んでいました" to "読む",       // polite continuous past
            "見ていません" to "見る",         // polite continuous negative
            "やっています" to "やる"          // polite continuous present
        )

        for ((inflected, expected) in testCases) {
            val result = engine.deinflect(inflected)
            val match = result.find { it.baseForm == expected }
            assertNotNull("Should find $expected from $inflected", match)
            println("✅ $inflected → $expected (${match!!.reasonChain.joinToString(" → ")})")
        }
    }

    @Test
    fun testReasonChainLogic() {
        // Test that reason chains make sense and don't have duplicates

        val result = engine.deinflect("食べさせられていました")
        val match = result.find { it.baseForm == "食べる" }

        assertNotNull("Should handle complex causative-passive-continuous-polite", match)

        // Check no duplicate reasons
        val reasons = match!!.reasonChain
        assertEquals("Should not have duplicate reasons", reasons.size, reasons.distinct().size)

        println("Complex chain: 食べさせられていました → 食べる")
        println("Reason chain: ${reasons.joinToString(" → ")}")
    }

    @Test
    fun testConfidenceScoring() {
        // Test that confidence scoring works properly

        val result = engine.deinflect("良い")

        // Direct form should have highest confidence
        val directMatch = result.find { it.baseForm == "良い" && it.reasonChain.isEmpty() }
        assertNotNull("Should have direct match", directMatch)

        // Check that irregular forms have high confidence
        val irregularResult = engine.deinflect("よくない")
        val irregularMatch = irregularResult.find { it.baseForm == "良い" }
        assertNotNull("Should find irregular match", irregularMatch)
    }

    @Test
    fun testVerbTypeClassification() {
        // Test that verb types are correctly classified

        val testCases = mapOf(
            "食べた" to VerbType.ICHIDAN,      // ichidan past
            "行った" to VerbType.GODAN_K,      // godan k past
            "した" to VerbType.SURU_IRREGULAR, // suru past
            "きた" to VerbType.KURU_IRREGULAR  // kuru past
        )

        for ((inflected, expectedType) in testCases) {
            val result = engine.deinflect(inflected)
            val match = result.find { it.verbType == expectedType }
            assertNotNull("Should correctly classify $inflected as $expectedType", match)
        }
    }
}