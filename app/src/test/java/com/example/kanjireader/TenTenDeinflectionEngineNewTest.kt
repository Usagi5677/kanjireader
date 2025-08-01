package com.example.kanjireader

import org.junit.Test
import org.junit.Assert.*

/**
 * Test for the new TenTenDeinflectionEngine
 */
class TenTenDeinflectionEngineNewTest {

    private val engine = TenTenDeinflectionEngine()

    @Test
    fun testBasicConjugations() {
        // Test basic past tense
        val results1 = engine.deinflect("食べた")
        assertEquals("Should find 食べる", "食べる", results1.firstOrNull()?.baseForm)
        
        // Test polite form
        val results2 = engine.deinflect("食べます")
        assertEquals("Should find 食べる", "食べる", results2.firstOrNull()?.baseForm)
        
        // Test negative
        val results3 = engine.deinflect("食べない")
        assertEquals("Should find 食べる", "食べる", results3.firstOrNull()?.baseForm)
    }

    @Test
    fun testContinuousForms() {
        // Test continuous form
        val results1 = engine.deinflect("食べている")
        assertEquals("Should find 食べる", "食べる", results1.firstOrNull()?.baseForm)
        
        // Test polite continuous
        val results2 = engine.deinflect("食べています")
        assertEquals("Should find 食べる", "食べる", results2.firstOrNull()?.baseForm)
        
        // Test past continuous
        val results3 = engine.deinflect("食べていた")
        assertEquals("Should find 食べる", "食べる", results3.firstOrNull()?.baseForm)
    }

    @Test
    fun testGodanVerbs() {
        // Test godan past tense
        val results1 = engine.deinflect("行った")
        assertEquals("Should find 行く", "行く", results1.firstOrNull()?.baseForm)
        
        // Test godan negative
        val results2 = engine.deinflect("行かない")
        assertEquals("Should find 行く", "行く", results2.firstOrNull()?.baseForm)
        
        // Test godan polite
        val results3 = engine.deinflect("行きます")
        assertEquals("Should find 行く", "行く", results3.firstOrNull()?.baseForm)
    }

    @Test
    fun testPassiveForms() {
        // Test ichidan passive
        val results1 = engine.deinflect("食べられる")
        assertEquals("Should find 食べる", "食べる", results1.firstOrNull()?.baseForm)
        
        // Test godan passive
        val results2 = engine.deinflect("読まれる")
        assertEquals("Should find 読む", "読む", results2.firstOrNull()?.baseForm)
    }

    @Test
    fun testCausativeForms() {
        // Test ichidan causative
        val results1 = engine.deinflect("食べさせる")
        assertEquals("Should find 食べる", "食べる", results1.firstOrNull()?.baseForm)
        
        // Test godan causative
        val results2 = engine.deinflect("読ませる")
        assertEquals("Should find 読む", "読む", results2.firstOrNull()?.baseForm)
    }

    @Test
    fun testSuruVerbs() {
        // Test suru past
        val results1 = engine.deinflect("した")
        assertEquals("Should find する", "する", results1.firstOrNull()?.baseForm)
        
        // Test suru polite
        val results2 = engine.deinflect("します")
        assertEquals("Should find する", "する", results2.firstOrNull()?.baseForm)
        
        // Test suru continuous
        val results3 = engine.deinflect("している")
        assertEquals("Should find する", "する", results3.firstOrNull()?.baseForm)
    }

    @Test
    fun testKuruVerbs() {
        // Test kuru past
        val results1 = engine.deinflect("きた")
        assertEquals("Should find くる", "くる", results1.firstOrNull()?.baseForm)
        
        // Test kuru continuous
        val results2 = engine.deinflect("きている")
        assertEquals("Should find くる", "くる", results2.firstOrNull()?.baseForm)
    }

    @Test
    fun testIAdjectives() {
        // Test i-adjective past
        val results1 = engine.deinflect("大きかった")
        assertEquals("Should find 大きい", "大きい", results1.firstOrNull()?.baseForm)
        
        // Test i-adjective negative
        val results2 = engine.deinflect("大きくない")
        assertEquals("Should find 大きい", "大きい", results2.firstOrNull()?.baseForm)
    }

    @Test
    fun testRomajiInput() {
        // Test romaji deinflection
        val results1 = engine.deinflect("tabeta")
        assertTrue("Should find results for tabeta", results1.isNotEmpty())
        assertTrue("Should find taberu-like result", results1.any { it.baseForm.contains("たべ") })
        
        val results2 = engine.deinflect("shiteimasu")
        assertTrue("Should find results for shiteimasu", results2.isNotEmpty())
        assertTrue("Should find suru-like result", results2.any { it.baseForm == "する" })
    }

    @Test
    fun testComplexForms() {
        // Test polite past continuous
        val results1 = engine.deinflect("食べていました")
        assertEquals("Should find 食べる", "食べる", results1.firstOrNull()?.baseForm)
        
        // Test negative past
        val results2 = engine.deinflect("食べなかった")
        assertEquals("Should find 食べる", "食べる", results2.firstOrNull()?.baseForm)
    }

    @Test
    fun testReasonChains() {
        // Test that reason chains are properly built
        val results = engine.deinflect("食べていました")
        val result = results.firstOrNull()
        
        assertNotNull("Should have a result", result)
        assertTrue("Should have reason chain", result!!.reasonChain.isNotEmpty())
        assertTrue("Should contain polite", result.reasonChain.any { it.contains("polite") })
    }

    @Test
    fun testVerbTypeInference() {
        // Test verb type inference
        val results1 = engine.deinflect("食べた")
        assertEquals("Should infer ichidan", VerbType.ICHIDAN, results1.firstOrNull()?.verbType)
        
        val results2 = engine.deinflect("行った")
        assertEquals("Should infer godan", VerbType.GODAN_K, results2.firstOrNull()?.verbType)
        
        val results3 = engine.deinflect("した")
        assertEquals("Should infer suru", VerbType.SURU_IRREGULAR, results3.firstOrNull()?.verbType)
    }

    @Test
    fun testNoFalsePositives() {
        // Test that dictionary forms don't get falsely deinflected
        val results1 = engine.deinflect("食べる")
        assertTrue("Dictionary form should return empty or same", 
            results1.isEmpty() || results1.all { it.baseForm == "食べる" })
        
        val results2 = engine.deinflect("行く")
        assertTrue("Dictionary form should return empty or same", 
            results2.isEmpty() || results2.all { it.baseForm == "行く" })
    }
}