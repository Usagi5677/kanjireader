package com.example.kanjireader

import android.util.Log

// Enhanced data class for morphological analysis results
data class MorphologyResult(
    val originalForm: String,     // What user selected: "食べていました"
    val baseForm: String,         // Dictionary form: "食べる"
    val conjugationType: String,  // "past continuous polite"
    val partOfSpeech: String,     // "verb", "adjective", etc.
    val verbType: VerbType?,      // ICHIDAN, GODAN_K, etc.
)

class MorphologicalAnalyzer {

    companion object {
        private const val TAG = "MorphologyAnalyzer"
    }

    // private val tentenEngine = TenTenDeinflectionEngine()  // Temporarily disabled
    private val kuromojiAnalyzer = KuromojiMorphologicalAnalyzer()
    private var tagDictLoader: TagDictSQLiteLoader? = null  // Changed from TagDictLoader

    fun setTagDictLoader(loader: TagDictSQLiteLoader) {  // Changed parameter type
        this.tagDictLoader = loader
    }

    fun analyzeWord(word: String): MorphologyResult? {
        Log.d(TAG, "Analyzing word: '$word'")

        // **Step 1: Quick validation**
        if (word.length <= 1) {
            Log.d(TAG, "Word '$word' too short for morphological analysis")
            return null
        }

        if (!looksLikeJapaneseWord(word)) {
            Log.d(TAG, "Word '$word' doesn't look like a real Japanese word")
            return null
        }

        // **Step 2: Use Kuromoji morphological analyzer**
        val morphologyResult = kuromojiAnalyzer.analyzeWord(word)

        if (morphologyResult == null) {
            Log.d(TAG, "No morphology analysis found for '$word'")
            return null
        }

        // **Step 3: Return the result**
        Log.d(TAG, "Kuromoji analysis: '$word' → '${morphologyResult.baseForm}' (${morphologyResult.conjugationType})")

        return morphologyResult
    }

    /**
     * Enhanced word validation
     */
    private fun looksLikeJapaneseWord(word: String): Boolean {
        if (word.length < 2) return false

        val hasKanji = word.any { isKanji(it.toString()) }
        val hasHiragana = word.any { isHiragana(it.toString()) }
        val hasKatakana = word.any { isKatakana(it.toString()) }

        if (!hasKanji && !hasHiragana && !hasKatakana) {
            return false
        }

        // Check for common conjugation patterns
        val commonEndings = setOf(
            "る", "た", "って", "ない", "ます", "ました", "ている", "ていた",
            "くない", "かった", "した", "して", "いた", "いて", "んだ", "んで"
        )

        val hasCommonEnding = commonEndings.any { ending -> word.endsWith(ending) }

        // Allow if it has common endings OR is longer than 2 characters with mixed types
        return hasCommonEnding || (word.length >= 3 && (hasKanji || hasHiragana))
    }

    /**
     * Convert VerbType to part of speech string
     */
    private fun getPartOfSpeechFromVerbType(verbType: VerbType?): String {
        return when (verbType) {
            VerbType.ICHIDAN,
            VerbType.GODAN_K, VerbType.GODAN_S, VerbType.GODAN_T, VerbType.GODAN_N,
            VerbType.GODAN_B, VerbType.GODAN_M, VerbType.GODAN_R, VerbType.GODAN_G, VerbType.GODAN_U,
            VerbType.SURU_IRREGULAR, VerbType.KURU_IRREGULAR, VerbType.IKU_IRREGULAR -> "verb"

            VerbType.ADJECTIVE_I -> "i-adjective"
            VerbType.ADJECTIVE_NA -> "na-adjective"
            else -> "unknown"
        }
    }


    private fun isKanji(char: String): Boolean {
        if (char.isEmpty()) return false
        val codePoint = char.codePointAt(0)
        return (codePoint in 0x4E00..0x9FAF) || (codePoint in 0x3400..0x4DBF)
    }

    private fun isHiragana(char: String): Boolean {
        if (char.isEmpty()) return false
        val codePoint = char.codePointAt(0)
        return codePoint in 0x3040..0x309F
    }

    private fun isKatakana(char: String): Boolean {
        if (char.isEmpty()) return false
        val codePoint = char.codePointAt(0)
        return codePoint in 0x30A0..0x30FF
    }





}