package com.example.kanjireader

import android.util.Log

/**
 * Japanese verb lemmatization system
 * Converts conjugated verbs to their dictionary form through proper linguistic analysis
 */
class JapaneseLemmatizer {
    
    companion object {
        private const val TAG = "JapaneseLemmatizer"
    }
    
    /**
     * Lemmatize a Japanese verb by identifying the conjugation and extracting the stem
     * @param conjugatedForm The conjugated verb (e.g., "たべません", "たべます")
     * @return The dictionary form (e.g., "たべる") or null if not a known conjugation
     */
    fun lemmatize(conjugatedForm: String): LemmatizationResult? {
        Log.d(TAG, "Attempting to lemmatize: '$conjugatedForm'")
        
        // Check each conjugation pattern
        for (pattern in CONJUGATION_PATTERNS) {
            if (conjugatedForm.endsWith(pattern.ending)) {
                val stem = conjugatedForm.dropLast(pattern.ending.length)
                if (stem.isNotEmpty()) {
                    val dictionaryForm = reconstructDictionaryForm(stem, pattern.verbType)
                    if (dictionaryForm != null) {
                        Log.d(TAG, "Lemmatized: '$conjugatedForm' → '$dictionaryForm' (${pattern.name}, ${pattern.verbType})")
                        return LemmatizationResult(
                            original = conjugatedForm,
                            dictionaryForm = dictionaryForm,
                            stem = stem,
                            conjugation = pattern,
                            confidence = pattern.confidence
                        )
                    }
                }
            }
        }
        
        Log.d(TAG, "No lemmatization found for: '$conjugatedForm'")
        return null
    }
    
    /**
     * Reconstruct the dictionary form from stem and verb type
     */
    private fun reconstructDictionaryForm(stem: String, verbType: VerbType): String? {
        return when (verbType) {
            VerbType.ICHIDAN -> {
                // Ichidan verbs: stem + "る"
                // e.g., "たべ" + "る" = "たべる"
                stem + "る"
            }
            VerbType.GODAN_U -> {
                // Godan u-verbs: stem + "う"
                // e.g., "か" + "う" = "かう"
                stem + "う"
            }
            VerbType.GODAN_K -> {
                // Godan ku-verbs: stem + "く"
                // e.g., "か" + "く" = "かく"
                stem + "く"
            }
            VerbType.GODAN_G -> {
                // Godan gu-verbs: stem + "ぐ"
                // e.g., "およ" + "ぐ" = "およぐ"
                stem + "ぐ"
            }
            VerbType.GODAN_S -> {
                // Godan su-verbs: stem + "す"
                // e.g., "はな" + "す" = "はなす"
                stem + "す"
            }
            VerbType.GODAN_T -> {
                // Godan tsu-verbs: stem + "つ"
                // e.g., "た" + "つ" = "たつ"
                stem + "つ"
            }
            VerbType.GODAN_N -> {
                // Godan nu-verbs: stem + "ぬ"
                // e.g., "し" + "ぬ" = "しぬ"
                stem + "ぬ"
            }
            VerbType.GODAN_B -> {
                // Godan bu-verbs: stem + "ぶ"
                // e.g., "よ" + "ぶ" = "よぶ"
                stem + "ぶ"
            }
            VerbType.GODAN_M -> {
                // Godan mu-verbs: stem + "む"
                // e.g., "よ" + "む" = "よむ"
                stem + "む"
            }
            VerbType.GODAN_R -> {
                // Godan ru-verbs: stem + "る"
                // e.g., "つく" + "る" = "つくる"
                stem + "る"
            }
            VerbType.SURU_IRREGULAR -> {
                // Irregular suru: "し" stem becomes "する"
                when (stem) {
                    "し" -> "する"
                    else -> stem + "する" // Compound suru verbs
                }
            }
            VerbType.KURU_IRREGULAR -> {
                // Irregular kuru: "き"/"こ" stem becomes "くる"
                when (stem) {
                    "き", "こ" -> "くる"
                    else -> null
                }
            }
            VerbType.IKU_IRREGULAR -> {
                // Irregular iku: special case
                stem + "く"
            }
            VerbType.ADJECTIVE_I, VerbType.ADJECTIVE_NA -> {
                // Not verbs, return null
                null
            }
            else -> {
                // Default case for any unhandled verb types
                // Try ichidan as fallback
                stem + "る"
            }
        }
    }
    
    /**
     * All Japanese verb conjugation patterns ordered by specificity
     */
    private val CONJUGATION_PATTERNS = listOf(
        // Negative polite forms (most specific)
        ConjugationPattern("ませんでした", "past negative polite", VerbType.ICHIDAN, 0.95f),
        ConjugationPattern("ません", "negative polite", VerbType.ICHIDAN, 0.95f),
        
        // Polite forms
        ConjugationPattern("ました", "past polite", VerbType.ICHIDAN, 0.95f),
        ConjugationPattern("ます", "polite", VerbType.ICHIDAN, 0.95f),
        
        // Past forms
        ConjugationPattern("った", "past (godan t/r)", VerbType.GODAN_R, 0.90f),
        ConjugationPattern("んだ", "past (godan b/m/n)", VerbType.GODAN_M, 0.90f),
        ConjugationPattern("いだ", "past (godan g)", VerbType.GODAN_G, 0.90f),
        ConjugationPattern("いた", "past (godan k)", VerbType.GODAN_K, 0.90f),
        ConjugationPattern("した", "past (godan s)", VerbType.GODAN_S, 0.90f),
        ConjugationPattern("った", "past (godan u)", VerbType.GODAN_U, 0.90f),
        ConjugationPattern("た", "past (ichidan)", VerbType.ICHIDAN, 0.90f),
        
        // Te-forms
        ConjugationPattern("って", "te-form (godan t/r)", VerbType.GODAN_R, 0.85f),
        ConjugationPattern("んで", "te-form (godan b/m/n)", VerbType.GODAN_M, 0.85f),
        ConjugationPattern("いで", "te-form (godan g)", VerbType.GODAN_G, 0.85f),
        ConjugationPattern("いて", "te-form (godan k)", VerbType.GODAN_K, 0.85f),
        ConjugationPattern("して", "te-form (godan s)", VerbType.GODAN_S, 0.85f),
        ConjugationPattern("って", "te-form (godan u)", VerbType.GODAN_U, 0.85f),
        ConjugationPattern("て", "te-form (ichidan)", VerbType.ICHIDAN, 0.85f),
        
        // Negative forms
        ConjugationPattern("わない", "negative (godan)", VerbType.GODAN_U, 0.80f),
        ConjugationPattern("かない", "negative (godan k)", VerbType.GODAN_K, 0.80f),
        ConjugationPattern("がない", "negative (godan g)", VerbType.GODAN_G, 0.80f),
        ConjugationPattern("さない", "negative (godan s)", VerbType.GODAN_S, 0.80f),
        ConjugationPattern("たない", "negative (godan t)", VerbType.GODAN_T, 0.80f),
        ConjugationPattern("なない", "negative (godan n)", VerbType.GODAN_N, 0.80f),
        ConjugationPattern("ばない", "negative (godan b)", VerbType.GODAN_B, 0.80f),
        ConjugationPattern("まない", "negative (godan m)", VerbType.GODAN_M, 0.80f),
        ConjugationPattern("らない", "negative (godan r)", VerbType.GODAN_R, 0.80f),
        ConjugationPattern("ない", "negative (ichidan)", VerbType.ICHIDAN, 0.80f),
        
        // Potential forms
        ConjugationPattern("える", "potential (godan)", VerbType.GODAN_U, 0.75f),
        ConjugationPattern("られる", "potential (ichidan)", VerbType.ICHIDAN, 0.75f),
        
        // Volitional forms
        ConjugationPattern("おう", "volitional (godan)", VerbType.GODAN_U, 0.70f),
        ConjugationPattern("よう", "volitional (ichidan)", VerbType.ICHIDAN, 0.70f),
        
        // Irregular verbs (specific patterns)
        ConjugationPattern("きます", "kuru polite", VerbType.KURU_IRREGULAR, 0.95f),
        ConjugationPattern("きました", "kuru past polite", VerbType.KURU_IRREGULAR, 0.95f),
        ConjugationPattern("きません", "kuru negative polite", VerbType.KURU_IRREGULAR, 0.95f),
        ConjugationPattern("しました", "suru past polite", VerbType.SURU_IRREGULAR, 0.95f),
        ConjugationPattern("します", "suru polite", VerbType.SURU_IRREGULAR, 0.95f),
        ConjugationPattern("しません", "suru negative polite", VerbType.SURU_IRREGULAR, 0.95f),
    )
}

/**
 * Result of lemmatization process
 */
data class LemmatizationResult(
    val original: String,
    val dictionaryForm: String,
    val stem: String,
    val conjugation: ConjugationPattern,
    val confidence: Float
)

/**
 * Represents a Japanese verb conjugation pattern
 */
data class ConjugationPattern(
    val ending: String,
    val name: String,
    val verbType: VerbType,
    val confidence: Float
)

