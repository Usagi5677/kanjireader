package com.example.kanjireader

import android.util.Log

/**
 * Enhanced deinflection engine - simplified version
 * Now just delegates to TenTenDeinflectionEngine
 */
class EnhancedDeinflectionEngine {
    
    companion object {
        private const val TAG = "EnhancedDeinflectionEngine"
    }
    
    // Core components
    private val legacyEngine = TenTenStyleDeinflectionEngine()
    private val romajiConverter = RomajiConverter()
    
    private var initialized = false
    
    /**
     * Initialize the enhanced engine (no-op now, kept for compatibility)
     */
    fun initialize() {
        initialized = true
        Log.d(TAG, "Enhanced deinflection engine ready")
    }
    
    /**
     * Check if engine is initialized
     */
    fun isInitialized(): Boolean = initialized
    
    /**
     * Main deinflection method - delegates to TenTenDeinflectionEngine
     */
    fun deinflect(word: String, tagDictLoader: TagDictSQLiteLoader? = null): List<DeinflectionResult> {
        // Convert romaji if needed
        val processedWord = if (romajiConverter.containsRomaji(word)) {
            romajiConverter.toHiragana(word)
        } else {
            word
        }
        
        // Use the legacy engine and convert results
        val legacyResults = legacyEngine.deinflect(processedWord, tagDictLoader)
        return legacyResults.map { legacyResult ->
            DeinflectionResult(
                originalForm = legacyResult.originalForm,
                baseForm = legacyResult.baseForm,
                reasonChain = legacyResult.reasonChain,
                verbType = legacyResult.verbType,
                transformations = legacyResult.transformations.map { legacyStep ->
                    DeinflectionStep(
                        from = legacyStep.from,
                        to = legacyStep.to,
                        reason = legacyStep.reason,
                        ruleId = legacyStep.ruleId
                    )
                }
            )
        }
    }
    
    /**
     * Get performance stats
     */
    fun getPerformanceStats(): String {
        return "Using TenTenDeinflectionEngine"
    }
}