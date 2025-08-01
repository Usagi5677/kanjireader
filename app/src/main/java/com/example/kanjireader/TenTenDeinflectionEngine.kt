package com.example.kanjireader

import android.util.Log

/**
 * Deinflection engine based exactly on 10ten's TypeScript implementation
 * Provides comprehensive Japanese conjugation handling with identical logic
 */

// Reason enum matching 10ten's implementation
object Reason {
    const val PolitePastNegative = 0
    const val PoliteNegative = 1
    const val PoliteVolitional = 2
    const val Chau = 3
    const val Sugiru = 4
    const val PolitePast = 5
    const val Tara = 6
    const val Tari = 7
    const val Causative = 8
    const val PotentialOrPassive = 9
    const val Toku = 10
    const val Sou = 11
    const val Tai = 12
    const val Polite = 13
    const val Respectful = 14
    const val Humble = 15
    const val HumbleOrKansaiDialect = 16
    const val Past = 17
    const val Negative = 18
    const val Passive = 19
    const val Ba = 20
    const val Volitional = 21
    const val Potential = 22
    const val EruUru = 23
    const val CausativePassive = 24
    const val Te = 25
    const val Zu = 26
    const val Imperative = 27
    const val MasuStem = 28
    const val Adv = 29
    const val Noun = 30
    const val ImperativeNegative = 31
    const val Continuous = 32
    const val Ki = 33
    const val SuruNoun = 34
    const val ZaruWoEnai = 35
    const val NegativeTe = 36
    const val Irregular = 37
}

// Type system matching 10ten exactly
object Type {
    const val IchidanVerb = 1 shl 0     // ru-verbs
    const val GodanVerb = 1 shl 1       // u-verbs
    const val IAdj = 1 shl 2
    const val KuruVerb = 1 shl 3
    const val SuruVerb = 1 shl 4
    const val SpecialSuruVerb = 1 shl 5
    const val NounVS = 1 shl 6
    const val All = IchidanVerb or GodanVerb or IAdj or KuruVerb or SuruVerb or SpecialSuruVerb or NounVS
    
    // Intermediate types
    const val Initial = 1 shl 7
    const val TaTeStem = 1 shl 8
    const val DaDeStem = 1 shl 9
    const val MasuStem = 1 shl 10
    const val IrrealisStem = 1 shl 11
}

// Helper function for reason conversion
private fun reasonToString(reason: Int): String {
    return when (reason) {
        Reason.PolitePastNegative -> "polite past negative"
        Reason.PoliteNegative -> "polite negative"
        Reason.PoliteVolitional -> "polite volitional"
        Reason.Chau -> "chau"
        Reason.Sugiru -> "sugiru"
        Reason.PolitePast -> "polite past"
        Reason.Tara -> "tara"
        Reason.Tari -> "tari"
        Reason.Causative -> "causative"
        Reason.PotentialOrPassive -> "potential or passive"
        Reason.Toku -> "toku"
        Reason.Sou -> "sou"
        Reason.Tai -> "tai"
        Reason.Polite -> "polite"
        Reason.Respectful -> "respectful"
        Reason.Humble -> "humble"
        Reason.HumbleOrKansaiDialect -> "humble or kansai dialect"
        Reason.Past -> "past"
        Reason.Negative -> "negative"
        Reason.Passive -> "passive"
        Reason.Ba -> "ba"
        Reason.Volitional -> "volitional"
        Reason.Potential -> "potential"
        Reason.EruUru -> "eru/uru"
        Reason.CausativePassive -> "causative passive"
        Reason.Te -> "te"
        Reason.Zu -> "zu"
        Reason.Imperative -> "imperative"
        Reason.MasuStem -> "masu stem"
        Reason.Adv -> "adv"
        Reason.Noun -> "noun"
        Reason.ImperativeNegative -> "imperative negative"
        Reason.Continuous -> "continuous"
        Reason.Ki -> "ki"
        Reason.SuruNoun -> "suru noun"
        Reason.ZaruWoEnai -> "zaru wo enai"
        Reason.NegativeTe -> "negative te"
        Reason.Irregular -> "irregular"
        else -> "unknown"
    }
}

// Deinflection rule matching 10ten structure and old interface
data class DeinflectionRule(
    val from: String,
    val to: String,
    val fromType: Int,
    val toType: Int,
    val reasons: List<Int>
) {
    // Compatibility properties with old ConjugationGenerator
    val reason: String get() = if (reasons.isNotEmpty()) reasonToString(reasons.first()) else ""
    val reasonStrings: List<String> get() = reasons.map { reasonToString(it) }
}

// Rule group for organizing by string length
data class DeinflectionRuleGroup(
    val rules: List<DeinflectionRule>,
    val fromLen: Int
)

// Candidate word exactly matching 10ten interface
data class CandidateWord(
    val word: String,
    val reasonChains: MutableList<MutableList<Int>>,
    val type: Int
)

// Result with comprehensive transformation tracking
data class DeinflectionResult(
    val originalForm: String,
    val baseForm: String,
    val reasonChain: List<String>,
    val verbType: VerbType?,
    val transformations: List<DeinflectionStep>
)

data class DeinflectionStep(
    val from: String,
    val to: String,
    val reason: String,
    val ruleId: String
)

class TenTenDeinflectionEngine {
    
    companion object {
        private const val TAG = "TenTenDeinflection"
        private const val MAX_CANDIDATES = 500
    }
    
    private val romajiConverter = RomajiConverter()
    private val deinflectionRuleGroups = buildDeinflectionRuleGroups()
    
    /**
     * Main deinflection method following 10ten's exact algorithm
     */
    fun deinflect(word: String, tagDictLoader: TagDictSQLiteLoader? = null): List<DeinflectionResult> {
        Log.d(TAG, "Starting deinflection for: '$word'")
        
        // Convert romaji to hiragana if needed
        val processedWord = if (romajiConverter.containsRomaji(word)) {
            val converted = romajiConverter.toHiragana(word)
            Log.d(TAG, "Converted romaji input: '$word' -> '$converted'")
            converted
        } else {
            word
        }
        
        return deinflectInternal(processedWord, word)
    }
    
    private fun deinflectInternal(word: String, originalQuery: String): List<DeinflectionResult> {
        val result = mutableListOf<CandidateWord>()
        val resultIndex = mutableMapOf<String, Int>()
        
        // Initialize with original word (matching 10ten exactly)
        val original = CandidateWord(
            word = word,
            type = 0xffff xor (Type.TaTeStem or Type.DaDeStem or Type.IrrealisStem),
            reasonChains = mutableListOf()
        )
        result.add(original)
        resultIndex[word] = 0
        
        var i = 0
        while (i < result.size && result.size < MAX_CANDIDATES) {
            val thisCandidate = result[i]
            
            // Don't deinflect masu-stem results of Ichidan verbs any further
            // (exact 10ten logic)
            if (thisCandidate.type and Type.IchidanVerb != 0 &&
                thisCandidate.reasonChains.size == 1 &&
                thisCandidate.reasonChains[0].size == 1 &&
                thisCandidate.reasonChains[0][0] == Reason.MasuStem) {
                i++
                continue
            }
            
            val candidateWord = thisCandidate.word
            val candidateType = thisCandidate.type
            
            // Ichidan verb stem handling (matching 10ten exactly)
            if (candidateType and (Type.MasuStem or Type.TaTeStem or Type.IrrealisStem) != 0) {
                val reason = mutableListOf<Int>()
                
                // Add masu reason only if word is solely the masu stem
                if (candidateType and Type.MasuStem != 0 && thisCandidate.reasonChains.isEmpty()) {
                    reason.add(Reason.MasuStem)
                }
                
                // Check for inapplicable forms (exact 10ten logic)
                val inapplicableForm = candidateType and Type.IrrealisStem != 0 &&
                    thisCandidate.reasonChains.isNotEmpty() &&
                    thisCandidate.reasonChains[0].isNotEmpty() &&
                    (thisCandidate.reasonChains[0][0] == Reason.Passive ||
                     thisCandidate.reasonChains[0][0] == Reason.Causative ||
                     thisCandidate.reasonChains[0][0] == Reason.CausativePassive)
                
                if (!inapplicableForm) {
                    val newReasonChains = mutableListOf<MutableList<Int>>()
                    // Deep clone reason chains
                    for (chain in thisCandidate.reasonChains) {
                        newReasonChains.add(chain.toMutableList())
                    }
                    // Add reason if needed
                    if (reason.isNotEmpty()) {
                        newReasonChains.add(reason)
                    }
                    
                    result.add(CandidateWord(
                        word = candidateWord + "る",
                        type = Type.IchidanVerb or Type.KuruVerb,
                        reasonChains = newReasonChains
                    ))
                }
            }
            
            // Apply deinflection rules (matching 10ten exactly)
            for (ruleGroup in deinflectionRuleGroups) {
                if (ruleGroup.fromLen > candidateWord.length) {
                    continue
                }
                
                val ending = candidateWord.takeLast(ruleGroup.fromLen)
                val hiraganaEnding = toHiragana(ending)
                
                for (rule in ruleGroup.rules) {
                    if (candidateType and rule.fromType == 0) {
                        continue
                    }
                    
                    if (ending != rule.from && hiraganaEnding != rule.from) {
                        continue
                    }
                    
                    val newWord = candidateWord.dropLast(rule.from.length) + rule.to
                    if (newWord.isEmpty()) {
                        continue
                    }
                    
                    // Check for duplicate reasons (exact 10ten logic)
                    val ruleReasons = rule.reasons.toSet()
                    if (thisCandidate.reasonChains.flatten().any { it in ruleReasons }) {
                        continue
                    }
                    
                    // Handle existing candidates (exact 10ten logic)
                    val existingIndex = resultIndex[newWord]
                    if (existingIndex != null) {
                        val candidate = result[existingIndex]
                        if (candidate.type == rule.toType) {
                            if (rule.reasons.isNotEmpty()) {
                                // Start new reason chain
                                candidate.reasonChains.add(0, rule.reasons.toMutableList())
                            }
                            continue
                        }
                    }
                    
                    resultIndex[newWord] = result.size
                    
                    // Deep clone reason chains
                    val newReasonChains = mutableListOf<MutableList<Int>>()
                    for (chain in thisCandidate.reasonChains) {
                        newReasonChains.add(chain.toMutableList())
                    }
                    
                    // Add new reasons (exact 10ten logic)
                    if (rule.reasons.isNotEmpty()) {
                        if (newReasonChains.isNotEmpty()) {
                            val firstReasonChain = newReasonChains[0]
                            
                            // Special causative + passive combination
                            if (rule.reasons[0] == Reason.Causative &&
                                firstReasonChain.isNotEmpty() &&
                                firstReasonChain[0] == Reason.PotentialOrPassive) {
                                firstReasonChain[0] = Reason.CausativePassive
                            } else if (rule.reasons[0] == Reason.MasuStem &&
                                      firstReasonChain.isNotEmpty()) {
                                // Do nothing for masu stem when chain exists
                            } else {
                                // Add reasons to beginning
                                for (j in rule.reasons.indices.reversed()) {
                                    firstReasonChain.add(0, rule.reasons[j])
                                }
                            }
                        } else {
                            // Add new reason chain
                            newReasonChains.add(rule.reasons.toMutableList())
                        }
                    }
                    
                    val candidate = CandidateWord(
                        word = newWord,
                        type = rule.toType,
                        reasonChains = newReasonChains
                    )
                    
                    result.add(candidate)
                }
            }
            
            i++
        }
        
        // Post-process to filter out intermediate forms (exact 10ten logic)
        val filteredResult = result.filter { it.type and Type.All != 0 }
        
        // Convert to our result format
        val finalResults = filteredResult
            .filter { it.word != word } // Exclude original word
            .map { candidate ->
                DeinflectionResult(
                    originalForm = originalQuery,
                    baseForm = candidate.word,
                    reasonChain = candidate.reasonChains.firstOrNull()?.map { reasonToString(it) } ?: emptyList(),
                    verbType = inferVerbType(candidate.word, candidate.type),
                    transformations = emptyList() // Can be enhanced later
                )
            }
            .distinctBy { it.baseForm }
        
        Log.d(TAG, "Deinflection complete: ${finalResults.size} results for '$word'")
        return finalResults
    }
    
    
    private fun inferVerbType(word: String, type: Int): VerbType? {
        return when {
            type and Type.IchidanVerb != 0 -> VerbType.ICHIDAN
            type and Type.GodanVerb != 0 -> when {
                word.endsWith("く") -> VerbType.GODAN_K
                word.endsWith("す") -> VerbType.GODAN_S
                word.endsWith("つ") -> VerbType.GODAN_T
                word.endsWith("ぬ") -> VerbType.GODAN_N
                word.endsWith("ぶ") -> VerbType.GODAN_B
                word.endsWith("む") -> VerbType.GODAN_M
                word.endsWith("る") -> VerbType.GODAN_R
                word.endsWith("ぐ") -> VerbType.GODAN_G
                word.endsWith("う") -> VerbType.GODAN_U
                else -> VerbType.GODAN_U
            }
            type and Type.SuruVerb != 0 -> VerbType.SURU_IRREGULAR
            type and Type.KuruVerb != 0 -> VerbType.KURU_IRREGULAR
            type and Type.IAdj != 0 -> VerbType.ADJECTIVE_I
            else -> null
        }
    }
    
    private fun toHiragana(text: String): String {
        // Convert katakana to hiragana
        return text.map { char ->
            when (char) {
                in 'ァ'..'ヶ' -> (char.code - 0x60).toChar()
                'ヽ' -> 'ゝ'
                'ヾ' -> 'ゞ'
                else -> char
            }
        }.joinToString("")
    }
    
    /**
     * Build deinflection rule groups exactly matching 10ten's data
     */
    private fun buildDeinflectionRuleGroups(): List<DeinflectionRuleGroup> {
        // This is the exact rule data from 10ten, converted to Kotlin
        val deinflectRuleData = listOf(
            // 7 characters
            Triple("ていらっしゃい", "", Triple(Type.Initial, Type.TaTeStem, listOf(Reason.Respectful, Reason.Continuous, Reason.Imperative))),
            Triple("ていらっしゃる", "", Triple(Type.GodanVerb, Type.TaTeStem, listOf(Reason.Respectful, Reason.Continuous))),
            Triple("でいらっしゃい", "", Triple(Type.Initial, Type.DaDeStem, listOf(Reason.Respectful, Reason.Continuous, Reason.Imperative))),
            Triple("でいらっしゃる", "", Triple(Type.GodanVerb, Type.DaDeStem, listOf(Reason.Respectful, Reason.Continuous))),
            
            // 6 characters
            Triple("いらっしゃい", "いらっしゃる", Triple(Type.MasuStem, Type.GodanVerb, listOf(Reason.MasuStem))),
            Triple("いらっしゃい", "いらっしゃる", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Imperative))),
            Triple("くありません", "い", Triple(Type.Initial, Type.IAdj, listOf(Reason.PoliteNegative))),
            Triple("ざるをえない", "", Triple(Type.IAdj, Type.IrrealisStem, listOf(Reason.ZaruWoEnai))),
            Triple("ざるを得ない", "", Triple(Type.IAdj, Type.IrrealisStem, listOf(Reason.ZaruWoEnai))),
            Triple("ませんでした", "", Triple(Type.Initial, Type.MasuStem, listOf(Reason.PolitePastNegative))),
            
            // 5 characters
            Triple("おっしゃい", "おっしゃる", Triple(Type.MasuStem, Type.GodanVerb, listOf(Reason.MasuStem))),
            Triple("おっしゃい", "おっしゃる", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Imperative))),
            Triple("ざるえない", "", Triple(Type.IAdj, Type.IrrealisStem, listOf(Reason.ZaruWoEnai))),
            Triple("ざる得ない", "", Triple(Type.IAdj, Type.IrrealisStem, listOf(Reason.ZaruWoEnai))),
            
            // 4 characters
            Triple("かったら", "い", Triple(Type.Initial, Type.IAdj, listOf(Reason.Tara))),
            Triple("かったり", "い", Triple(Type.Initial, Type.IAdj, listOf(Reason.Tari))),
            Triple("ください", "くださる", Triple(Type.MasuStem, Type.GodanVerb, listOf(Reason.MasuStem))),
            Triple("ください", "くださる", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Imperative))),
            Triple("こさせる", "くる", Triple(Type.IchidanVerb, Type.KuruVerb, listOf(Reason.Causative))),
            Triple("こられる", "くる", Triple(Type.IchidanVerb, Type.KuruVerb, listOf(Reason.PotentialOrPassive))),
            Triple("しないで", "する", Triple(Type.Initial, Type.SuruVerb, listOf(Reason.NegativeTe))),
            Triple("しさせる", "する", Triple(Type.IchidanVerb, Type.SpecialSuruVerb, listOf(Reason.Irregular, Reason.Causative))),
            Triple("しられる", "する", Triple(Type.IchidanVerb, Type.SpecialSuruVerb, listOf(Reason.Irregular, Reason.PotentialOrPassive))),
            Triple("せさせる", "する", Triple(Type.IchidanVerb, Type.SpecialSuruVerb, listOf(Reason.Irregular, Reason.Causative))),
            Triple("せられる", "する", Triple(Type.IchidanVerb, Type.SpecialSuruVerb, listOf(Reason.Irregular, Reason.PotentialOrPassive))),
            Triple("ましたら", "", Triple(Type.Initial, Type.MasuStem, listOf(Reason.Polite, Reason.Tara))),
            Triple("ましたり", "", Triple(Type.Initial, Type.MasuStem, listOf(Reason.Polite, Reason.Tari))),
            Triple("ましょう", "", Triple(Type.Initial, Type.MasuStem, listOf(Reason.PoliteVolitional))),
            
            // 3 characters
            Triple("かった", "い", Triple(Type.Initial, Type.IAdj, listOf(Reason.Past))),
            Triple("くない", "い", Triple(Type.IAdj, Type.IAdj, listOf(Reason.Negative))),
            Triple("ければ", "い", Triple(Type.Initial, Type.IAdj, listOf(Reason.Ba))),
            Triple("こよう", "くる", Triple(Type.Initial, Type.KuruVerb, listOf(Reason.Volitional))),
            Triple("これる", "くる", Triple(Type.IchidanVerb, Type.KuruVerb, listOf(Reason.Potential))),
            Triple("させる", "る", Triple(Type.IchidanVerb, Type.IchidanVerb or Type.KuruVerb, listOf(Reason.Causative))),
            Triple("させる", "する", Triple(Type.IchidanVerb, Type.SuruVerb, listOf(Reason.Causative))),
            Triple("される", "", Triple(Type.IchidanVerb, Type.IrrealisStem, listOf(Reason.CausativePassive))),
            Triple("される", "する", Triple(Type.IchidanVerb, Type.SuruVerb, listOf(Reason.Passive))),
            Triple("しない", "する", Triple(Type.IAdj, Type.SuruVerb, listOf(Reason.Negative))),
            Triple("しよう", "する", Triple(Type.Initial, Type.SuruVerb, listOf(Reason.Volitional))),
            Triple("すぎる", "い", Triple(Type.IchidanVerb, Type.IAdj, listOf(Reason.Sugiru))),
            Triple("すぎる", "", Triple(Type.IchidanVerb, Type.MasuStem, listOf(Reason.Sugiru))),
            Triple("ちゃう", "", Triple(Type.GodanVerb, Type.TaTeStem, listOf(Reason.Chau))),
            Triple("ている", "", Triple(Type.IchidanVerb, Type.TaTeStem, listOf(Reason.Continuous))),
            Triple("ておる", "", Triple(Type.GodanVerb, Type.TaTeStem, listOf(Reason.HumbleOrKansaiDialect, Reason.Continuous))),
            Triple("でいる", "", Triple(Type.IchidanVerb, Type.DaDeStem, listOf(Reason.Continuous))),
            Triple("でおる", "", Triple(Type.GodanVerb, Type.DaDeStem, listOf(Reason.HumbleOrKansaiDialect, Reason.Continuous))),
            Triple("できる", "する", Triple(Type.IchidanVerb, Type.SuruVerb, listOf(Reason.Potential))),
            Triple("ないで", "", Triple(Type.Initial, Type.IrrealisStem, listOf(Reason.NegativeTe))),
            Triple("なさい", "", Triple(Type.Initial, Type.MasuStem, listOf(Reason.Respectful, Reason.Imperative))),
            Triple("ました", "", Triple(Type.Initial, Type.MasuStem, listOf(Reason.PolitePast))),
            Triple("まして", "", Triple(Type.Initial, Type.MasuStem, listOf(Reason.Polite, Reason.Te))),
            Triple("ません", "", Triple(Type.Initial, Type.MasuStem, listOf(Reason.PoliteNegative))),
            Triple("られる", "る", Triple(Type.IchidanVerb, Type.IchidanVerb or Type.KuruVerb, listOf(Reason.PotentialOrPassive))),
            
            // 2 characters
            Triple("えば", "う", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Ba))),
            Triple("える", "う", Triple(Type.IchidanVerb, Type.GodanVerb, listOf(Reason.Potential))),
            Triple("おう", "う", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Volitional))),
            Triple("くて", "い", Triple(Type.Initial, Type.IAdj, listOf(Reason.Te))),
            Triple("けば", "く", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Ba))),
            Triple("げば", "ぐ", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Ba))),
            Triple("ける", "く", Triple(Type.IchidanVerb, Type.GodanVerb, listOf(Reason.Potential))),
            Triple("げる", "ぐ", Triple(Type.IchidanVerb, Type.GodanVerb, listOf(Reason.Potential))),
            Triple("こい", "くる", Triple(Type.Initial, Type.KuruVerb, listOf(Reason.Imperative))),
            Triple("こう", "く", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Volitional))),
            Triple("ごう", "ぐ", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Volitional))),
            Triple("しろ", "する", Triple(Type.Initial, Type.SuruVerb, listOf(Reason.Imperative))),
            Triple("せず", "する", Triple(Type.Initial, Type.SuruVerb, listOf(Reason.Zu))),
            Triple("せぬ", "する", Triple(Type.Initial, Type.SuruVerb, listOf(Reason.Negative))),
            Triple("せん", "する", Triple(Type.Initial, Type.SuruVerb, listOf(Reason.Negative))),
            Triple("せば", "す", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Ba))),
            Triple("せよ", "する", Triple(Type.Initial, Type.SuruVerb, listOf(Reason.Imperative))),
            Triple("せる", "す", Triple(Type.IchidanVerb, Type.GodanVerb, listOf(Reason.Potential))),
            Triple("せる", "", Triple(Type.IchidanVerb, Type.IrrealisStem, listOf(Reason.Causative))),
            Triple("そう", "", Triple(Type.Initial, Type.MasuStem, listOf(Reason.Sou))),
            Triple("そう", "い", Triple(Type.Initial, Type.IAdj, listOf(Reason.Sou))),
            Triple("そう", "す", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Volitional))),
            Triple("たい", "", Triple(Type.IAdj, Type.MasuStem, listOf(Reason.Tai))),
            Triple("たら", "", Triple(Type.Initial, Type.TaTeStem, listOf(Reason.Tara))),
            Triple("だら", "", Triple(Type.Initial, Type.DaDeStem, listOf(Reason.Tara))),
            Triple("たり", "", Triple(Type.Initial, Type.TaTeStem, listOf(Reason.Tari))),
            Triple("だり", "", Triple(Type.Initial, Type.DaDeStem, listOf(Reason.Tari))),
            Triple("てば", "つ", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Ba))),
            Triple("てる", "つ", Triple(Type.IchidanVerb, Type.GodanVerb, listOf(Reason.Potential))),
            Triple("てる", "", Triple(Type.IchidanVerb, Type.TaTeStem, listOf(Reason.Continuous))),
            Triple("でる", "", Triple(Type.IchidanVerb, Type.DaDeStem, listOf(Reason.Continuous))),
            Triple("とう", "つ", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Volitional))),
            Triple("とく", "", Triple(Type.GodanVerb, Type.TaTeStem, listOf(Reason.Toku))),
            Triple("とる", "", Triple(Type.GodanVerb, Type.TaTeStem, listOf(Reason.HumbleOrKansaiDialect, Reason.Continuous))),
            Triple("どく", "", Triple(Type.GodanVerb, Type.DaDeStem, listOf(Reason.Toku))),
            Triple("どる", "", Triple(Type.GodanVerb, Type.DaDeStem, listOf(Reason.HumbleOrKansaiDialect, Reason.Continuous))),
            Triple("ない", "", Triple(Type.IAdj, Type.IrrealisStem, listOf(Reason.Negative))),
            Triple("ねば", "ぬ", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Ba))),
            Triple("ねる", "ぬ", Triple(Type.IchidanVerb, Type.GodanVerb, listOf(Reason.Potential))),
            Triple("のう", "ぬ", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Volitional))),
            Triple("べば", "ぶ", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Ba))),
            Triple("べる", "ぶ", Triple(Type.IchidanVerb, Type.GodanVerb, listOf(Reason.Potential))),
            Triple("ぼう", "ぶ", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Volitional))),
            Triple("ます", "", Triple(Type.Initial, Type.MasuStem, listOf(Reason.Polite))),
            Triple("ませ", "", Triple(Type.Initial, Type.MasuStem, listOf(Reason.Polite, Reason.Imperative))),
            Triple("めば", "む", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Ba))),
            Triple("める", "む", Triple(Type.IchidanVerb, Type.GodanVerb, listOf(Reason.Potential))),
            Triple("もう", "む", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Volitional))),
            Triple("よう", "る", Triple(Type.Initial, Type.IchidanVerb or Type.KuruVerb, listOf(Reason.Volitional))),
            Triple("られ", "る", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.PotentialOrPassive))),  // For incomplete passive/potential: おわられ -> おわる
            Triple("れば", "る", Triple(Type.Initial, Type.IchidanVerb or Type.GodanVerb or Type.KuruVerb or Type.SuruVerb, listOf(Reason.Ba))),
            Triple("れる", "る", Triple(Type.IchidanVerb, Type.IchidanVerb or Type.GodanVerb, listOf(Reason.Potential))),
            Triple("れる", "", Triple(Type.IchidanVerb, Type.IrrealisStem, listOf(Reason.Passive))),
            Triple("ろう", "る", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Volitional))),
            
            // Irregular te-form stems
            Triple("いっ", "いく", Triple(Type.TaTeStem, Type.GodanVerb, emptyList())),
            Triple("行っ", "行く", Triple(Type.TaTeStem, Type.GodanVerb, emptyList())),
            
            // 1 character
            Triple("い", "う", Triple(Type.MasuStem, Type.GodanVerb, listOf(Reason.MasuStem))),
            Triple("い", "く", Triple(Type.TaTeStem, Type.GodanVerb, emptyList())),
            Triple("い", "ぐ", Triple(Type.DaDeStem, Type.GodanVerb, emptyList())),
            Triple("い", "る", Triple(Type.Initial, Type.KuruVerb, listOf(Reason.Imperative))),
            Triple("え", "う", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Imperative))),
            Triple("か", "く", Triple(Type.IrrealisStem, Type.GodanVerb, emptyList())),
            Triple("が", "ぐ", Triple(Type.IrrealisStem, Type.GodanVerb, emptyList())),
            Triple("き", "い", Triple(Type.Initial, Type.IAdj, listOf(Reason.Ki))),
            Triple("き", "く", Triple(Type.MasuStem, Type.GodanVerb, listOf(Reason.MasuStem))),
            Triple("き", "くる", Triple(Type.TaTeStem, Type.KuruVerb, emptyList())),
            Triple("き", "くる", Triple(Type.MasuStem, Type.KuruVerb, listOf(Reason.MasuStem))),
            Triple("ぎ", "ぐ", Triple(Type.MasuStem, Type.GodanVerb, listOf(Reason.MasuStem))),
            Triple("く", "い", Triple(Type.Initial, Type.IAdj, listOf(Reason.Adv))),
            Triple("け", "く", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Imperative))),
            Triple("げ", "ぐ", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Imperative))),
            Triple("こ", "くる", Triple(Type.IrrealisStem, Type.KuruVerb, emptyList())),
            Triple("さ", "い", Triple(Type.Initial, Type.IAdj, listOf(Reason.Noun))),
            Triple("さ", "す", Triple(Type.IrrealisStem, Type.GodanVerb, emptyList())),
            Triple("し", "す", Triple(Type.MasuStem, Type.GodanVerb, listOf(Reason.MasuStem))),
            Triple("し", "する", Triple(Type.MasuStem, Type.SuruVerb, listOf(Reason.MasuStem))),
            Triple("し", "す", Triple(Type.TaTeStem, Type.GodanVerb, emptyList())),
            Triple("し", "する", Triple(Type.TaTeStem, Type.SuruVerb, emptyList())),
            Triple("ず", "", Triple(Type.Initial, Type.IrrealisStem, listOf(Reason.Zu))),
            Triple("せ", "す", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Imperative))),
            Triple("た", "つ", Triple(Type.IrrealisStem, Type.GodanVerb, emptyList())),
            Triple("た", "", Triple(Type.Initial, Type.TaTeStem, listOf(Reason.Past))),
            Triple("だ", "", Triple(Type.Initial, Type.DaDeStem, listOf(Reason.Past))),
            Triple("ち", "つ", Triple(Type.MasuStem, Type.GodanVerb, listOf(Reason.MasuStem))),
            Triple("っ", "う", Triple(Type.TaTeStem, Type.GodanVerb, emptyList())),
            Triple("っ", "つ", Triple(Type.TaTeStem, Type.GodanVerb, emptyList())),
            Triple("っ", "る", Triple(Type.TaTeStem, Type.GodanVerb, emptyList())),
            Triple("て", "", Triple(Type.Initial, Type.TaTeStem, listOf(Reason.Te))),
            Triple("て", "つ", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Imperative))),
            Triple("で", "", Triple(Type.Initial, Type.DaDeStem, listOf(Reason.Te))),
            Triple("な", "ぬ", Triple(Type.IrrealisStem, Type.GodanVerb, emptyList())),
            Triple("な", "", Triple(Type.Initial, Type.IchidanVerb or Type.GodanVerb or Type.KuruVerb or Type.SuruVerb, listOf(Reason.ImperativeNegative))),
            Triple("に", "ぬ", Triple(Type.MasuStem, Type.GodanVerb, listOf(Reason.MasuStem))),
            Triple("ぬ", "", Triple(Type.Initial, Type.IrrealisStem, listOf(Reason.Negative))),
            Triple("ね", "ぬ", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Imperative))),
            Triple("ば", "ぶ", Triple(Type.IrrealisStem, Type.GodanVerb, emptyList())),
            Triple("び", "ぶ", Triple(Type.MasuStem, Type.GodanVerb, listOf(Reason.MasuStem))),
            Triple("べ", "ぶ", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Imperative))),
            Triple("ま", "む", Triple(Type.IrrealisStem, Type.GodanVerb, emptyList())),
            Triple("み", "む", Triple(Type.MasuStem, Type.GodanVerb, listOf(Reason.MasuStem))),
            Triple("め", "む", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Imperative))),
            Triple("よ", "る", Triple(Type.Initial, Type.IchidanVerb, listOf(Reason.Imperative))),
            Triple("ら", "る", Triple(Type.IrrealisStem, Type.GodanVerb, emptyList())),
            Triple("り", "る", Triple(Type.MasuStem, Type.GodanVerb, listOf(Reason.MasuStem))),
            Triple("れ", "る", Triple(Type.Initial, Type.GodanVerb, listOf(Reason.Imperative))),
            Triple("ろ", "る", Triple(Type.Initial, Type.IchidanVerb, listOf(Reason.Imperative))),
            Triple("わ", "う", Triple(Type.IrrealisStem, Type.GodanVerb, emptyList())),
            Triple("ん", "ぬ", Triple(Type.DaDeStem, Type.GodanVerb, emptyList())),
            Triple("ん", "ぶ", Triple(Type.DaDeStem, Type.GodanVerb, emptyList())),
            Triple("ん", "む", Triple(Type.DaDeStem, Type.GodanVerb, emptyList())),
            Triple("ん", "", Triple(Type.Initial, Type.IrrealisStem, listOf(Reason.Negative)))
        )
        
        // Group rules by length (exactly like 10ten)
        val ruleGroups = mutableListOf<DeinflectionRuleGroup>()
        var prevLen = -1
        var currentRules = mutableListOf<DeinflectionRule>()
        
        for ((from, to, ruleInfo) in deinflectRuleData) {
            val (fromType, toType, reasons) = ruleInfo
            
            if (prevLen != from.length) {
                if (currentRules.isNotEmpty()) {
                    ruleGroups.add(DeinflectionRuleGroup(currentRules.toList(), prevLen))
                }
                prevLen = from.length
                currentRules = mutableListOf()
            }
            
            currentRules.add(DeinflectionRule(from, to, fromType, toType, reasons))
        }
        
        if (currentRules.isNotEmpty()) {
            ruleGroups.add(DeinflectionRuleGroup(currentRules.toList(), prevLen))
        }
        
        return ruleGroups
    }
    
    fun getAllRules(): List<DeinflectionRule> {
        return deinflectionRuleGroups.flatMap { it.rules }
    }
    
    fun getRulesForVerbType(verbType: VerbType): List<DeinflectionRule> {
        val typeMask = when (verbType) {
            VerbType.ICHIDAN -> Type.IchidanVerb
            VerbType.GODAN_K, VerbType.GODAN_S, VerbType.GODAN_T, VerbType.GODAN_N,
            VerbType.GODAN_B, VerbType.GODAN_M, VerbType.GODAN_R, VerbType.GODAN_G,
            VerbType.GODAN_U -> Type.GodanVerb
            VerbType.SURU_IRREGULAR -> Type.SuruVerb
            VerbType.KURU_IRREGULAR -> Type.KuruVerb
            VerbType.ADJECTIVE_I -> Type.IAdj
            else -> Type.All
        }
        
        return getAllRules().filter { rule ->
            rule.toType and typeMask != 0
        }
    }
    
    fun getRulesByReason(): Map<String, List<DeinflectionRule>> {
        return getAllRules().groupBy { rule ->
            if (rule.reasons.isNotEmpty()) {
                reasonToString(rule.reasons.first())
            } else {
                "stem"
            }
        }
    }
}