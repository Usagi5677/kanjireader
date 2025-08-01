package com.example.kanjireader

import android.util.Log

// Enhanced deinflection result with reason chain
data class LegacyDeinflectionResult(
    val originalForm: String,
    val baseForm: String,
    val reasonChain: List<String>,
    val verbType: VerbType?,
    val transformations: List<LegacyDeinflectionStep>
)

data class LegacyDeinflectionStep(
    val from: String,
    val to: String,
    val reason: String,
    val ruleId: String
)

// Word types that match 10ten's Type enum
object WordType {
    const val ICHIDAN_VERB = 1 shl 0     // v1 - ru-verbs
    const val GODAN_VERB = 1 shl 1       // v5 - u-verbs
    const val I_ADJ = 1 shl 2            // adj-i
    const val KURU_VERB = 1 shl 3        // vk
    const val SURU_VERB = 1 shl 4        // vs
    const val SPECIAL_SURU_VERB = 1 shl 5 // vs-s
    const val NOUN_VS = 1 shl 6          // n (suru noun)

    // All final types
    const val ALL = ICHIDAN_VERB or GODAN_VERB or I_ADJ or KURU_VERB or
            SURU_VERB or SPECIAL_SURU_VERB or NOUN_VS

    // Intermediate types (not final forms)
    const val INITIAL = 1 shl 7
    const val TA_TE_STEM = 1 shl 8
    const val DA_DE_STEM = 1 shl 9
    const val MASU_STEM = 1 shl 10
    const val IRREALIS_STEM = 1 shl 11
}

// Legacy deinflection rule with validation
data class LegacyDeinflectionRule(
    val from: String,
    val to: String,
    val reason: String,
    val fromType: Int,  // Bit mask of types this rule can apply to
    val toType: Int,    // Bit mask of types this rule produces
    val reasons: List<String> = emptyList()
)

// Legacy candidate word during processing
data class LegacyCandidateWord(
    val word: String,
    val type: Int,  // Bit mask of possible types
    val reasonChains: MutableList<List<String>> = mutableListOf()
) {
    fun hasReasonInChain(reason: String): Boolean {
        if (reason.isEmpty()) return false
        return reasonChains.flatten().contains(reason)
    }

    fun clone(): LegacyCandidateWord {
        return LegacyCandidateWord(
            word = word,
            type = type,
            reasonChains = reasonChains.map { it.toList() }.toMutableList()
        )
    }
}

class TenTenStyleDeinflectionEngine {

    companion object {
        private const val TAG = "TenTenDeinflection"
        private const val MAX_CANDIDATES = 100
    }

    // Build comprehensive rule set following 10ten's approach
    private val deinflectionRules = buildDeinflectionRules()
    
    // Romaji converter for handling romaji input
    private val romajiConverter = RomajiConverter()

    fun getAllRules(): List<LegacyDeinflectionRule> = deinflectionRules

    fun getRulesForVerbType(verbType: VerbType): List<LegacyDeinflectionRule> {
        val typeMask = when (verbType) {
            VerbType.ICHIDAN -> WordType.ICHIDAN_VERB
            VerbType.GODAN_K -> WordType.GODAN_VERB
            VerbType.GODAN_S -> WordType.GODAN_VERB
            VerbType.GODAN_T -> WordType.GODAN_VERB
            VerbType.GODAN_N -> WordType.GODAN_VERB
            VerbType.GODAN_B -> WordType.GODAN_VERB
            VerbType.GODAN_M -> WordType.GODAN_VERB
            VerbType.GODAN_R -> WordType.GODAN_VERB
            VerbType.GODAN_G -> WordType.GODAN_VERB
            VerbType.GODAN_U -> WordType.GODAN_VERB
            VerbType.SURU_IRREGULAR -> WordType.SURU_VERB or WordType.SPECIAL_SURU_VERB
            VerbType.KURU_IRREGULAR -> WordType.KURU_VERB
            VerbType.IKU_IRREGULAR -> WordType.GODAN_VERB  // 行く is a special godan-k verb
            VerbType.ADJECTIVE_I -> WordType.I_ADJ
            VerbType.ADJECTIVE_NA -> 0  // Na-adjectives aren't handled by verb deinflection rules
            VerbType.UNKNOWN -> WordType.ALL  // Return all rules if unknown
        }

        return deinflectionRules.filter { rule ->
            rule.toType and typeMask != 0
        }
    }

    fun getRulesByReason(): Map<String, List<LegacyDeinflectionRule>> {
        return deinflectionRules.groupBy { it.reason }
    }

    /**
     * Main deinflection method - follows 10ten's queue-based algorithm
     * Now supports romaji input
     */
    fun deinflect(word: String, tagDictLoader: TagDictSQLiteLoader? = null): List<LegacyDeinflectionResult> {
        Log.d(TAG, "Starting deinflection for: '$word'")
        
        // Check if input contains romaji and convert it first
        val processedWord = if (romajiConverter.containsRomaji(word)) {
            val converted = romajiConverter.toHiragana(word)
            Log.d(TAG, "Converted romaji input: '$word' -> '$converted'")
            converted
        } else {
            word
        }
        
        // Special handling for ください forms
        if (processedWord.endsWith("ください")) {
            Log.d(TAG, "Detected ください form: '$processedWord'")
            val teForm = processedWord.removeSuffix("ください")
            if (teForm.isNotEmpty()) {
                Log.d(TAG, "Extracting te-form: '$teForm' from '$processedWord'")
                // Recursively deinflect the te-form part
                val teFormResults = deinflectInternal(teForm, tagDictLoader)
                if (teFormResults.isNotEmpty()) {
                    Log.d(TAG, "Found ${teFormResults.size} results for te-form '$teForm'")
                    return teFormResults.map { result ->
                        result.copy(
                            originalForm = processedWord,
                            reasonChain = result.reasonChain + "polite request form",
                            transformations = result.transformations + LegacyDeinflectionStep(
                                from = processedWord,
                                to = teForm,
                                reason = "ください removal",
                                ruleId = "kudasai"
                            )
                        )
                    }
                } else {
                    Log.d(TAG, "No results found for te-form '$teForm'")
                }
            }
        }
        
        return deinflectWithProgressiveMatching(processedWord, tagDictLoader)
    }
    
    private fun deinflectWithProgressiveMatching(word: String, tagDictLoader: TagDictSQLiteLoader? = null): List<LegacyDeinflectionResult> {
        // First try the full word
        val fullResults = deinflectInternal(word, tagDictLoader)
        if (fullResults.isNotEmpty()) {
            return fullResults
        }
        
        // If no results, try progressively shorter substrings (minimum 2 characters)
        for (length in word.length - 1 downTo 2) {
            val substring = word.substring(0, length)
            Log.d(TAG, "Trying progressive match: '$substring' (from '$word')")
            val results = deinflectInternal(substring, tagDictLoader)
            if (results.isNotEmpty()) {
                Log.d(TAG, "Found progressive match at length $length: '$substring'")
                return results
            }
        }
        
        // No matches found
        return emptyList()
    }
    
    private fun deinflectInternal(word: String, tagDictLoader: TagDictSQLiteLoader? = null): List<LegacyDeinflectionResult> {
        Log.d(TAG, "deinflectInternal called for: '$word'")

        val candidates = mutableListOf<LegacyCandidateWord>()
        val candidateIndex = mutableMapOf<String, Int>()

        // Start with the word
        val original = LegacyCandidateWord(
            word = word,
            type = WordType.ALL or WordType.INITIAL,  // Can be any final type initially
            reasonChains = mutableListOf()
        )
        candidates.add(original)
        candidateIndex[word] = 0

        var i = 0
        while (i < candidates.size && i < MAX_CANDIDATES) {
            val candidate = candidates[i]

            // Don't deinflect masu-stem results of Ichidan verbs any further
            if (candidate.type and WordType.ICHIDAN_VERB != 0 &&
                candidate.reasonChains.size == 1 &&
                candidate.reasonChains[0].size == 1 &&
                candidate.reasonChains[0][0] == "masu stem") {
                i++
                continue
            }

            val word = candidate.word
            val type = candidate.type

            // Handle ichidan verb stems
            if (type and (WordType.MASU_STEM or WordType.TA_TE_STEM or WordType.IRREALIS_STEM) != 0) {
                val reason = mutableListOf<String>()

                // Add masu stem reason only if it's solely the masu stem
                if (type and WordType.MASU_STEM != 0 && candidate.reasonChains.isEmpty()) {
                    reason.add("masu stem")
                }

                // Check if this is an inapplicable form for ichidan verbs
                val inapplicableForm = type and WordType.IRREALIS_STEM != 0 &&
                        candidate.reasonChains.isNotEmpty() &&
                        candidate.reasonChains[0].isNotEmpty() &&
                        candidate.reasonChains[0][0] in listOf("passive", "causative", "causative passive")
                
                // Skip adding る to single character stems that are likely する stems
                val isSuruStem = word == "し" && type and WordType.MASU_STEM != 0

                if (!inapplicableForm && !isSuruStem) {
                    val newCandidate = LegacyCandidateWord(
                        word = word + "る",
                        type = WordType.ICHIDAN_VERB or WordType.KURU_VERB,
                        reasonChains = mutableListOf()
                    )

                    // Copy existing reason chains
                    candidate.reasonChains.forEach { chain ->
                        newCandidate.reasonChains.add(chain.toList())
                    }

                    // Add new reason if needed
                    if (reason.isNotEmpty()) {
                        newCandidate.reasonChains.add(reason)
                    }

                    candidates.add(newCandidate)
                }
            }

            // Apply deinflection rules
            for (rule in deinflectionRules) {
                if (rule.from.length > word.length) continue
                if (type and rule.fromType == 0) continue

                val ending = word.takeLast(rule.from.length)
                if (ending != rule.from && toHiragana(ending) != rule.from) continue

                // Check phonological compatibility before applying rule
                if (!isPhonologicallyValid(word, rule)) {
                    Log.d(TAG, "Phonological validation failed for '${rule.from}' -> '${rule.to}' on '${word}'")
                    continue
                }
                
                Log.d(TAG, "Applying rule '${rule.from}' -> '${rule.to}' (${rule.reason}) to '${word}'")
                val newWord = word.dropLast(rule.from.length) + rule.to
                if (newWord.isEmpty()) {
                    Log.d(TAG, "New word is empty, skipping")
                    continue
                }
                Log.d(TAG, "Generated candidate: '$newWord'")

                // Check for duplicate reasons in chain
                val ruleReasons = rule.reasons.toSet()
                if (candidate.reasonChains.flatten().any { it in ruleReasons }) continue

                // Check if we already have this candidate with same type
                val existingIndex = candidateIndex[newWord]
                if (existingIndex != null) {
                    val existingCandidate = candidates[existingIndex]
                    if (existingCandidate.type == rule.toType) {
                        if (rule.reasons.isNotEmpty()) {
                            existingCandidate.reasonChains.add(0, rule.reasons.toList())
                        }
                        continue
                    }
                }

                // Create new candidate
                candidateIndex[newWord] = candidates.size

                val newCandidate = LegacyCandidateWord(
                    word = newWord,
                    type = rule.toType,
                    reasonChains = mutableListOf()
                )

                // Deep clone reason chains
                candidate.reasonChains.forEach { chain ->
                    newCandidate.reasonChains.add(chain.toMutableList())
                }

                // Add new reasons
                if (rule.reasons.isNotEmpty()) {
                    if (newCandidate.reasonChains.isNotEmpty()) {
                        val firstChain = newCandidate.reasonChains[0] as MutableList<String>

                        // Handle causative + passive = causative passive
                        if (rule.reasons[0] == "causative" &&
                            firstChain.isNotEmpty() &&
                            firstChain[0] == "potential or passive") {
                            firstChain.removeAt(0)
                            firstChain.add(0, "causative passive")
                        } else if (rule.reasons[0] != "masu stem" || firstChain.isEmpty()) {
                            // Add all reasons to the beginning of the chain
                            for (i in rule.reasons.indices.reversed()) {
                                firstChain.add(0, rule.reasons[i])
                            }
                        }
                    } else {
                        newCandidate.reasonChains.add(rule.reasons.toMutableList())
                    }
                }

                candidates.add(newCandidate)
            }

            i++
        }

        // Special handling for する continuous forms
        val isSuruContinuous = word.matches(Regex("して(い|る|た|いる|いた|います|いました|ます|ました|る|た)"))
        
        // Convert candidates to results, filtering out intermediate forms
        val results = candidates
            .filter { it.type and WordType.ALL != 0 }
            .map { candidate ->
                Log.d(TAG, "Processing candidate: '${candidate.word}' with type mask ${candidate.type}")
                
                // Map to verb types based on heuristics
                val verbType = when {
                    // If it's the original word (no transformations), return null
                    candidate.reasonChains.isEmpty() && candidate.word == word -> null
                    
                    // Use existing heuristics method
                    else -> mapToVerbType(candidate.type, candidate.word)
                }

                LegacyDeinflectionResult(
                    originalForm = word,
                    baseForm = candidate.word,
                    reasonChain = candidate.reasonChains.firstOrNull() ?: emptyList(),
                    verbType = verbType,
                    transformations = emptyList()
                )
            }
            .distinctBy { it.baseForm }
            .filter { result ->
                // Only return base forms that are likely actual dictionary forms
                // Skip intermediate forms and the original word
                result.baseForm != word && (
                    result.reasonChain.isNotEmpty() || 
                    result.verbType != null ||
                    result.baseForm.endsWith("る") || 
                    result.baseForm.endsWith("う") ||
                    result.baseForm.endsWith("い")  // For adjectives
                )
            }
        
        // If this is a する continuous form, filter to only return する-related results
        return if (isSuruContinuous) {
            results.filter { it.baseForm == "する" || it.baseForm.endsWith("する") }
        } else {
            results
        }
    }
    
    /**
     * Convenience method specifically for romaji deinflection
     * Example: "shiteimasu" -> "する" 
     */
    fun deinflectRomaji(romajiWord: String, tagDictLoader: TagDictSQLiteLoader? = null): List<LegacyDeinflectionResult> {
        if (!romajiConverter.containsRomaji(romajiWord)) {
            Log.w(TAG, "Input '$romajiWord' doesn't appear to contain romaji")
            return emptyList()
        }
        
        return deinflect(romajiWord, tagDictLoader)
    }

    private fun buildDeinflectionRules(): List<LegacyDeinflectionRule> {
        val rules = mutableListOf<LegacyDeinflectionRule>()

        // Add full processing chain for ってました forms
        // First: ていました → ている (removing polite past)
        rules.add(LegacyDeinflectionRule("ていました", "ている", "polite past continuous",
            WordType.INITIAL, WordType.ICHIDAN_VERB, listOf("polite past continuous")))
        rules.add(LegacyDeinflectionRule("でいました", "でいる", "polite past continuous",
            WordType.INITIAL, WordType.ICHIDAN_VERB, listOf("polite past continuous")))

        // Also handle ってました → っている for proper chaining
        rules.add(LegacyDeinflectionRule("ってました", "っている", "polite past continuous",
            WordType.INITIAL, WordType.ICHIDAN_VERB, listOf("polite past continuous")))
        rules.add(LegacyDeinflectionRule("んでました", "んでいる", "polite past continuous",
            WordType.INITIAL, WordType.ICHIDAN_VERB, listOf("polite past continuous")))

        // Continuous forms - handle all variations
        rules.add(LegacyDeinflectionRule("ている", "", "continuous",
            WordType.ICHIDAN_VERB, WordType.TA_TE_STEM, listOf("continuous")))
        rules.add(LegacyDeinflectionRule("でいる", "", "continuous",
            WordType.ICHIDAN_VERB, WordType.DA_DE_STEM, listOf("continuous")))
        // These should go to intermediate stems, not directly to て/で
        rules.add(LegacyDeinflectionRule("っている", "", "continuous",
            WordType.ICHIDAN_VERB, WordType.TA_TE_STEM, listOf("continuous")))
        rules.add(LegacyDeinflectionRule("んでいる", "", "continuous",
            WordType.ICHIDAN_VERB, WordType.DA_DE_STEM, listOf("continuous")))
        rules.add(LegacyDeinflectionRule("てる", "", "continuous",
            WordType.ICHIDAN_VERB, WordType.TA_TE_STEM, listOf("continuous")))
        rules.add(LegacyDeinflectionRule("でる", "", "continuous",
            WordType.ICHIDAN_VERB, WordType.DA_DE_STEM, listOf("continuous")))
        rules.add(LegacyDeinflectionRule("ってる", "", "continuous",
            WordType.ICHIDAN_VERB, WordType.TA_TE_STEM, listOf("continuous")))
        rules.add(LegacyDeinflectionRule("んでる", "", "continuous",
            WordType.ICHIDAN_VERB, WordType.DA_DE_STEM, listOf("continuous")))
        rules.add(LegacyDeinflectionRule("ていた", "", "continuous past",
            WordType.INITIAL, WordType.TA_TE_STEM, listOf("continuous", "past")))
        rules.add(LegacyDeinflectionRule("でいた", "", "continuous past",
            WordType.INITIAL, WordType.DA_DE_STEM, listOf("continuous", "past")))
        rules.add(LegacyDeinflectionRule("てた", "", "continuous past",
            WordType.INITIAL, WordType.TA_TE_STEM, listOf("continuous", "past")))
        rules.add(LegacyDeinflectionRule("でた", "", "continuous past",
            WordType.INITIAL, WordType.DA_DE_STEM, listOf("continuous", "past")))

        // Special する continuous forms - MUST come before general polite forms
        rules.add(LegacyDeinflectionRule("しています", "する", "polite continuous",
            WordType.INITIAL, WordType.SURU_VERB, listOf("polite continuous")))
        rules.add(LegacyDeinflectionRule("していました", "する", "polite past continuous",
            WordType.INITIAL, WordType.SURU_VERB, listOf("polite past continuous")))
        rules.add(LegacyDeinflectionRule("している", "する", "continuous",
            WordType.ICHIDAN_VERB, WordType.SURU_VERB, listOf("continuous")))
        rules.add(LegacyDeinflectionRule("していた", "する", "continuous past",
            WordType.INITIAL, WordType.SURU_VERB, listOf("continuous", "past")))
        rules.add(LegacyDeinflectionRule("してる", "する", "continuous",
            WordType.ICHIDAN_VERB, WordType.SURU_VERB, listOf("continuous")))
        rules.add(LegacyDeinflectionRule("してた", "する", "continuous past",
            WordType.INITIAL, WordType.SURU_VERB, listOf("continuous", "past")))
        
        // Add rule to catch してい before it gets broken down further
        rules.add(LegacyDeinflectionRule("してい", "する", "continuous stem",
            WordType.INITIAL, WordType.SURU_VERB, listOf("continuous stem")))

        // Polite forms
        rules.add(LegacyDeinflectionRule("ました", "", "polite past",
            WordType.INITIAL, WordType.MASU_STEM, listOf("polite past")))
        rules.add(LegacyDeinflectionRule("ます", "", "polite",
            WordType.INITIAL, WordType.MASU_STEM, listOf("polite")))
        rules.add(LegacyDeinflectionRule("ません", "", "polite negative",
            WordType.INITIAL, WordType.MASU_STEM, listOf("polite negative")))
        rules.add(LegacyDeinflectionRule("ませんでした", "", "polite past negative",
            WordType.INITIAL, WordType.MASU_STEM, listOf("polite past negative")))

        // Past forms
        rules.add(LegacyDeinflectionRule("た", "", "past",
            WordType.INITIAL, WordType.TA_TE_STEM, listOf("past")))
        rules.add(LegacyDeinflectionRule("だ", "", "past",
            WordType.INITIAL, WordType.DA_DE_STEM, listOf("past")))
        
        // Direct ichidan past form rules (for better matching)
        rules.add(LegacyDeinflectionRule("た", "る", "ichidan past",
            WordType.INITIAL, WordType.ICHIDAN_VERB, listOf("past")))

        // Te forms - need to handle って forms
        rules.add(LegacyDeinflectionRule("て", "", "te-form",
            WordType.INITIAL, WordType.TA_TE_STEM, listOf("te-form")))
        rules.add(LegacyDeinflectionRule("で", "", "te-form",
            WordType.INITIAL, WordType.DA_DE_STEM, listOf("te-form")))

        // SPECIAL RULES FOR IRREGULAR VERBS - MUST COME BEFORE GENERIC RULES!
        // These prevent generic rules from interfering with irregular verb deinflection
        rules.add(LegacyDeinflectionRule("し", "する", "",
            WordType.MASU_STEM, WordType.SURU_VERB, listOf("masu stem")))
        rules.add(LegacyDeinflectionRule("し", "する", "",
            WordType.TA_TE_STEM, WordType.SURU_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("き", "くる", "",
            WordType.MASU_STEM, WordType.KURU_VERB, listOf("masu stem")))
        rules.add(LegacyDeinflectionRule("き", "くる", "",
            WordType.TA_TE_STEM, WordType.KURU_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("いき", "いく", "",
            WordType.MASU_STEM, WordType.GODAN_VERB, listOf("masu stem")))
        rules.add(LegacyDeinflectionRule("いっ", "いく", "",
            WordType.TA_TE_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("あり", "ある", "",
            WordType.MASU_STEM, WordType.GODAN_VERB, listOf("masu stem")))
        rules.add(LegacyDeinflectionRule("あっ", "ある", "",
            WordType.TA_TE_STEM, WordType.GODAN_VERB, emptyList()))

        // Ichidan stem to dictionary form conversion (MISSING RULE - this fixes the cache issue)
        rules.add(LegacyDeinflectionRule("", "る", "",
            WordType.TA_TE_STEM, WordType.ICHIDAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("", "る", "",
            WordType.DA_DE_STEM, WordType.ICHIDAN_VERB, emptyList()))

        // Direct te-form to dictionary form for common patterns
        rules.add(LegacyDeinflectionRule("って", "う", "te-form",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("te-form")))
        rules.add(LegacyDeinflectionRule("んで", "ぬ", "te-form",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("te-form")))
        rules.add(LegacyDeinflectionRule("んで", "ぶ", "te-form",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("te-form")))
        rules.add(LegacyDeinflectionRule("んで", "む", "te-form",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("te-form")))
        rules.add(LegacyDeinflectionRule("いて", "く", "te-form",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("te-form")))
        rules.add(LegacyDeinflectionRule("いで", "ぐ", "te-form",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("te-form")))
        rules.add(LegacyDeinflectionRule("して", "す", "te-form",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("te-form")))

        // Negative forms
        rules.add(LegacyDeinflectionRule("ない", "", "negative",
            WordType.I_ADJ, WordType.IRREALIS_STEM, listOf("negative")))
        rules.add(LegacyDeinflectionRule("ぬ", "", "negative",
            WordType.INITIAL, WordType.IRREALIS_STEM, listOf("negative")))
        rules.add(LegacyDeinflectionRule("ないで", "", "negative te",
            WordType.INITIAL, WordType.IRREALIS_STEM, listOf("negative te")))

        // Volitional forms
        rules.add(LegacyDeinflectionRule("よう", "る", "volitional",
            WordType.INITIAL, WordType.ICHIDAN_VERB or WordType.KURU_VERB, listOf("volitional")))
        rules.add(LegacyDeinflectionRule("おう", "う", "volitional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("volitional")))
        rules.add(LegacyDeinflectionRule("こう", "く", "volitional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("volitional")))
        rules.add(LegacyDeinflectionRule("ごう", "ぐ", "volitional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("volitional")))
        rules.add(LegacyDeinflectionRule("そう", "す", "volitional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("volitional")))
        rules.add(LegacyDeinflectionRule("とう", "つ", "volitional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("volitional")))
        rules.add(LegacyDeinflectionRule("のう", "ぬ", "volitional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("volitional")))
        rules.add(LegacyDeinflectionRule("ぼう", "ぶ", "volitional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("volitional")))
        rules.add(LegacyDeinflectionRule("もう", "む", "volitional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("volitional")))
        rules.add(LegacyDeinflectionRule("ろう", "る", "volitional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("volitional")))

        // Passive/Potential forms
        rules.add(LegacyDeinflectionRule("られる", "る", "potential or passive",
            WordType.ICHIDAN_VERB, WordType.ICHIDAN_VERB or WordType.KURU_VERB, listOf("potential or passive")))
        rules.add(LegacyDeinflectionRule("れる", "", "passive",
            WordType.ICHIDAN_VERB, WordType.IRREALIS_STEM, listOf("passive")))

        // Causative forms
        rules.add(LegacyDeinflectionRule("させる", "る", "causative",
            WordType.ICHIDAN_VERB, WordType.ICHIDAN_VERB or WordType.KURU_VERB, listOf("causative")))
        rules.add(LegacyDeinflectionRule("せる", "", "causative",
            WordType.ICHIDAN_VERB, WordType.IRREALIS_STEM, listOf("causative")))
        rules.add(LegacyDeinflectionRule("させる", "する", "causative",
            WordType.ICHIDAN_VERB, WordType.SURU_VERB, listOf("causative")))

        // Imperative forms
        rules.add(LegacyDeinflectionRule("ろ", "る", "imperative",
            WordType.INITIAL, WordType.ICHIDAN_VERB, listOf("imperative")))
        rules.add(LegacyDeinflectionRule("よ", "る", "imperative",
            WordType.INITIAL, WordType.ICHIDAN_VERB, listOf("imperative")))

        // Conditional forms
        rules.add(LegacyDeinflectionRule("れば", "る", "conditional",
            WordType.INITIAL, WordType.ICHIDAN_VERB or WordType.GODAN_VERB or WordType.KURU_VERB or WordType.SURU_VERB, listOf("ba")))
        rules.add(LegacyDeinflectionRule("えば", "う", "conditional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("ba")))
        rules.add(LegacyDeinflectionRule("けば", "く", "conditional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("ba")))
        rules.add(LegacyDeinflectionRule("げば", "ぐ", "conditional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("ba")))
        rules.add(LegacyDeinflectionRule("せば", "す", "conditional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("ba")))
        rules.add(LegacyDeinflectionRule("てば", "つ", "conditional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("ba")))
        rules.add(LegacyDeinflectionRule("ねば", "ぬ", "conditional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("ba")))
        rules.add(LegacyDeinflectionRule("べば", "ぶ", "conditional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("ba")))
        rules.add(LegacyDeinflectionRule("めば", "む", "conditional",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("ba")))

        // Godan te-stem to dictionary forms
        rules.add(LegacyDeinflectionRule("って", "う", "",  // Add this specific rule
            WordType.TA_TE_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("っ", "う", "",
            WordType.TA_TE_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("っ", "つ", "",
            WordType.TA_TE_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("っ", "る", "",
            WordType.TA_TE_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("い", "く", "",
            WordType.TA_TE_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("い", "ぐ", "",
            WordType.DA_DE_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("し", "す", "",
            WordType.TA_TE_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("ん", "ぬ", "",
            WordType.DA_DE_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("ん", "ぶ", "",
            WordType.DA_DE_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("ん", "む", "",
            WordType.DA_DE_STEM, WordType.GODAN_VERB, emptyList()))

        // Special case for 行く
        rules.add(LegacyDeinflectionRule("いっ", "いく", "",
            WordType.TA_TE_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("行っ", "行く", "",
            WordType.TA_TE_STEM, WordType.GODAN_VERB, emptyList()))

        // Add past form rules for 行く
        rules.add(LegacyDeinflectionRule("いった", "いく", "past",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("past")))
        rules.add(LegacyDeinflectionRule("行った", "行く", "past",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("past")))

        // SPECIAL IRREGULAR VERB RULES - MUST COME BEFORE GENERIC MASU_STEM RULE!
        // (Note: These are duplicated from above to ensure they're processed before ALL generic rules)
        rules.add(LegacyDeinflectionRule("し", "する", "",
            WordType.MASU_STEM, WordType.SURU_VERB, listOf("masu stem")))
        rules.add(LegacyDeinflectionRule("し", "する", "",
            WordType.TA_TE_STEM, WordType.SURU_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("き", "くる", "",
            WordType.MASU_STEM, WordType.KURU_VERB, listOf("masu stem")))
        rules.add(LegacyDeinflectionRule("き", "くる", "",
            WordType.TA_TE_STEM, WordType.KURU_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("いき", "いく", "",
            WordType.MASU_STEM, WordType.GODAN_VERB, listOf("masu stem")))
        rules.add(LegacyDeinflectionRule("いっ", "いく", "",
            WordType.TA_TE_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("あり", "ある", "",
            WordType.MASU_STEM, WordType.GODAN_VERB, listOf("masu stem")))
        rules.add(LegacyDeinflectionRule("あっ", "ある", "",
            WordType.TA_TE_STEM, WordType.GODAN_VERB, emptyList()))

        // Ichidan masu stem to dictionary form (MISSING RULE!)
        rules.add(LegacyDeinflectionRule("", "る", "",
            WordType.MASU_STEM, WordType.ICHIDAN_VERB, emptyList()))

        // Godan masu stems to dictionary forms
        rules.add(LegacyDeinflectionRule("い", "う", "",
            WordType.MASU_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("き", "く", "",
            WordType.MASU_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("ぎ", "ぐ", "",
            WordType.MASU_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("し", "す", "",
            WordType.MASU_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("ち", "つ", "",
            WordType.MASU_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("に", "ぬ", "",
            WordType.MASU_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("び", "ぶ", "",
            WordType.MASU_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("み", "む", "",
            WordType.MASU_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("り", "る", "",
            WordType.MASU_STEM, WordType.GODAN_VERB, emptyList()))

        // Noun forms (masu stem)
        rules.add(LegacyDeinflectionRule("い", "う", "noun form",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("noun form")))
        rules.add(LegacyDeinflectionRule("き", "く", "noun form",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("noun form")))
        rules.add(LegacyDeinflectionRule("ぎ", "ぐ", "noun form",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("noun form")))
        rules.add(LegacyDeinflectionRule("し", "す", "noun form",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("noun form")))
        rules.add(LegacyDeinflectionRule("ち", "つ", "noun form",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("noun form")))
        rules.add(LegacyDeinflectionRule("に", "ぬ", "noun form",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("noun form")))
        rules.add(LegacyDeinflectionRule("び", "ぶ", "noun form",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("noun form")))
        rules.add(LegacyDeinflectionRule("み", "む", "noun form",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("noun form")))
        rules.add(LegacyDeinflectionRule("り", "る", "noun form",
            WordType.INITIAL, WordType.GODAN_VERB, listOf("noun form")))

        // Godan negative stem patterns
        rules.add(LegacyDeinflectionRule("わ", "う", "",
            WordType.IRREALIS_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("か", "く", "",
            WordType.IRREALIS_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("が", "ぐ", "",
            WordType.IRREALIS_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("さ", "す", "",
            WordType.IRREALIS_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("た", "つ", "",
            WordType.IRREALIS_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("な", "ぬ", "",
            WordType.IRREALIS_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("ば", "ぶ", "",
            WordType.IRREALIS_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("ま", "む", "",
            WordType.IRREALIS_STEM, WordType.GODAN_VERB, emptyList()))
        rules.add(LegacyDeinflectionRule("ら", "る", "",
            WordType.IRREALIS_STEM, WordType.GODAN_VERB, emptyList()))

        // Special verbs
        rules.add(LegacyDeinflectionRule("した", "する", "past",
            WordType.INITIAL, WordType.SURU_VERB, listOf("past")))
        rules.add(LegacyDeinflectionRule("して", "する", "te-form",
            WordType.INITIAL, WordType.SURU_VERB, listOf("te-form")))
        rules.add(LegacyDeinflectionRule("しない", "する", "negative",
            WordType.I_ADJ, WordType.SURU_VERB, listOf("negative")))
        rules.add(LegacyDeinflectionRule("しよう", "する", "volitional",
            WordType.INITIAL, WordType.SURU_VERB, listOf("volitional")))
        rules.add(LegacyDeinflectionRule("しろ", "する", "imperative",
            WordType.INITIAL, WordType.SURU_VERB, listOf("imperative")))
        rules.add(LegacyDeinflectionRule("させる", "する", "causative",
            WordType.ICHIDAN_VERB, WordType.SURU_VERB, listOf("causative")))
        rules.add(LegacyDeinflectionRule("される", "する", "passive",
            WordType.ICHIDAN_VERB, WordType.SURU_VERB, listOf("passive")))
        rules.add(LegacyDeinflectionRule("できる", "する", "potential",
            WordType.ICHIDAN_VERB, WordType.SURU_VERB, listOf("potential")))

        // NOTE: する rules moved earlier in the file to prevent generic rule interference

        rules.add(LegacyDeinflectionRule("きた", "くる", "past",
            WordType.INITIAL, WordType.KURU_VERB, listOf("past")))
        rules.add(LegacyDeinflectionRule("きて", "くる", "te-form",
            WordType.INITIAL, WordType.KURU_VERB, listOf("te-form")))
        rules.add(LegacyDeinflectionRule("こない", "くる", "negative",
            WordType.I_ADJ, WordType.KURU_VERB, listOf("negative")))
        rules.add(LegacyDeinflectionRule("こよう", "くる", "volitional",
            WordType.INITIAL, WordType.KURU_VERB, listOf("volitional")))
        rules.add(LegacyDeinflectionRule("こい", "くる", "imperative",
            WordType.INITIAL, WordType.KURU_VERB, listOf("imperative")))
        rules.add(LegacyDeinflectionRule("こさせる", "くる", "causative",
            WordType.ICHIDAN_VERB, WordType.KURU_VERB, listOf("causative")))
        rules.add(LegacyDeinflectionRule("こられる", "くる", "potential or passive",
            WordType.ICHIDAN_VERB, WordType.KURU_VERB, listOf("potential or passive")))

        // NOTE: くる rules moved earlier in the file to prevent generic rule interference
        rules.add(LegacyDeinflectionRule("こ", "くる", "",
            WordType.IRREALIS_STEM, WordType.KURU_VERB, emptyList()))

        // I-adjective patterns
        rules.add(LegacyDeinflectionRule("かった", "い", "past",
            WordType.INITIAL, WordType.I_ADJ, listOf("past")))
        rules.add(LegacyDeinflectionRule("くない", "い", "negative",
            WordType.I_ADJ, WordType.I_ADJ, listOf("negative")))
        rules.add(LegacyDeinflectionRule("くなかった", "い", "negative past",
            WordType.INITIAL, WordType.I_ADJ, listOf("negative past")))
        rules.add(LegacyDeinflectionRule("く", "い", "adverb",
            WordType.INITIAL, WordType.I_ADJ, listOf("adverb")))
        rules.add(LegacyDeinflectionRule("くて", "い", "te-form",
            WordType.INITIAL, WordType.I_ADJ, listOf("te-form")))
        rules.add(LegacyDeinflectionRule("ければ", "い", "conditional",
            WordType.INITIAL, WordType.I_ADJ, listOf("ba")))

        // Special patterns for 良い/いい
        rules.add(LegacyDeinflectionRule("よく", "良い", "adverb",
            WordType.INITIAL, WordType.I_ADJ, listOf("adverb")))
        rules.add(LegacyDeinflectionRule("よくない", "良い", "negative",
            WordType.INITIAL, WordType.I_ADJ, listOf("negative")))
        rules.add(LegacyDeinflectionRule("よかった", "良い", "past",
            WordType.INITIAL, WordType.I_ADJ, listOf("past")))
        rules.add(LegacyDeinflectionRule("よくなかった", "良い", "negative past",
            WordType.INITIAL, WordType.I_ADJ, listOf("negative past")))
        rules.add(LegacyDeinflectionRule("よければ", "良い", "conditional",
            WordType.INITIAL, WordType.I_ADJ, listOf("ba")))

        // いい → 良い mapping
        rules.add(LegacyDeinflectionRule("いい", "良い", "",
            WordType.INITIAL, WordType.I_ADJ, emptyList()))

        // Additional patterns for completeness
        rules.add(LegacyDeinflectionRule("たい", "", "want to",
            WordType.I_ADJ, WordType.MASU_STEM, listOf("tai")))
        rules.add(LegacyDeinflectionRule("そう", "", "seems like",
            WordType.INITIAL, WordType.MASU_STEM, listOf("sou")))
        rules.add(LegacyDeinflectionRule("すぎる", "", "too much",
            WordType.ICHIDAN_VERB, WordType.MASU_STEM, listOf("sugiru")))
        rules.add(LegacyDeinflectionRule("ながら", "", "while",
            WordType.INITIAL, WordType.MASU_STEM, listOf("nagara")))

        // Add specific combined rules for common patterns
        // This helps ensure proper chaining for specific cases

        // Sort by from length (longest first) for efficient matching
        return rules.sortedByDescending { it.from.length }
    }

    private fun mapToVerbType(typeMask: Int, word: String): VerbType? {
        return when {
            typeMask and WordType.ICHIDAN_VERB != 0 -> VerbType.ICHIDAN
            typeMask and WordType.GODAN_VERB != 0 -> {
                // Determine specific godan type by ending
                when (word.lastOrNull()) {
                    'く' -> if (word == "行く" || word == "いく") VerbType.IKU_IRREGULAR else VerbType.GODAN_K
                    'ぐ' -> VerbType.GODAN_G
                    'す' -> VerbType.GODAN_S
                    'つ' -> VerbType.GODAN_T
                    'ぬ' -> VerbType.GODAN_N
                    'ぶ' -> VerbType.GODAN_B
                    'む' -> VerbType.GODAN_M
                    'る' -> VerbType.GODAN_R
                    'う' -> VerbType.GODAN_U
                    else -> VerbType.UNKNOWN
                }
            }
            typeMask and WordType.SURU_VERB != 0 -> VerbType.SURU_IRREGULAR
            typeMask and WordType.KURU_VERB != 0 -> VerbType.KURU_IRREGULAR
            typeMask and WordType.I_ADJ != 0 -> VerbType.ADJECTIVE_I
            else -> null
        }
    }

    // Simple hiragana conversion (you should use a proper library)
    private fun toHiragana(text: String): String {
        // This is a simplified version - you should use a proper kana conversion library
        return text.map { char ->
            when (char) {
                'ア' -> 'あ'
                'イ' -> 'い'
                'ウ' -> 'う'
                'エ' -> 'え'
                'オ' -> 'お'
                'カ' -> 'か'
                'キ' -> 'き'
                'ク' -> 'く'
                'ケ' -> 'け'
                'コ' -> 'こ'
                'ガ' -> 'が'
                'ギ' -> 'ぎ'
                'グ' -> 'ぐ'
                'ゲ' -> 'げ'
                'ゴ' -> 'ご'
                'サ' -> 'さ'
                'シ' -> 'し'
                'ス' -> 'す'
                'セ' -> 'せ'
                'ソ' -> 'そ'
                'ザ' -> 'ざ'
                'ジ' -> 'じ'
                'ズ' -> 'ず'
                'ゼ' -> 'ぜ'
                'ゾ' -> 'ぞ'
                'タ' -> 'た'
                'チ' -> 'ち'
                'ツ' -> 'つ'
                'テ' -> 'て'
                'ト' -> 'と'
                'ダ' -> 'だ'
                'ヂ' -> 'ぢ'
                'ヅ' -> 'づ'
                'デ' -> 'で'
                'ド' -> 'ど'
                'ナ' -> 'な'
                'ニ' -> 'に'
                'ヌ' -> 'ぬ'
                'ネ' -> 'ね'
                'ノ' -> 'の'
                'ハ' -> 'は'
                'ヒ' -> 'ひ'
                'フ' -> 'ふ'
                'ヘ' -> 'へ'
                'ホ' -> 'ほ'
                'バ' -> 'ば'
                'ビ' -> 'び'
                'ブ' -> 'ぶ'
                'ベ' -> 'べ'
                'ボ' -> 'ぼ'
                'パ' -> 'ぱ'
                'ピ' -> 'ぴ'
                'プ' -> 'ぷ'
                'ペ' -> 'ぺ'
                'ポ' -> 'ぽ'
                'マ' -> 'ま'
                'ミ' -> 'み'
                'ム' -> 'む'
                'メ' -> 'め'
                'モ' -> 'も'
                'ヤ' -> 'や'
                'ユ' -> 'ゆ'
                'ヨ' -> 'よ'
                'ラ' -> 'ら'
                'リ' -> 'り'
                'ル' -> 'る'
                'レ' -> 'れ'
                'ロ' -> 'ろ'
                'ワ' -> 'わ'
                'ヲ' -> 'を'
                'ン' -> 'ん'
                'ッ' -> 'っ'
                'ャ' -> 'ゃ'
                'ュ' -> 'ゅ'
                'ョ' -> 'ょ'
                else -> char
            }
        }.joinToString("")
    }
    
    /**
     * Check if a text looks like a valid conjugation pattern
     * This centralizes the validation logic that was duplicated in DictionaryRepository
     */
    fun looksLikeValidConjugation(text: String): Boolean {
        // Remove spaces that might interfere with conjugation detection
        val cleanText = text.replace(Regex("\\s+"), "")
        
        // If the text is just spaces or becomes too short after cleaning, it's not a conjugation
        if (cleanText.length <= 1) {
            return false
        }
        
        // Only allow clear conjugation patterns, not standalone words
        val validConjugationEndings = listOf(
            // Polite forms
            "ます", "ません", "ました", "ませんでした",
            // Progressive forms
            "ている", "ていない", "ていた", "ていなかった",
            "てる", "てない", "てた", "てなかった",
            // Negative forms
            "ない", "なかった", "なく",
            // Conditional forms
            "たら", "だら", "ば", "なら",
            // Volitional
            "よう", "う",
            // Passive/Potential
            "れる", "られる", "れない", "られない",
            // Causative
            "せる", "させる", "せない", "させない",
            // Imperative
            "ろ", "よ"
        )
        
        // Check if the text ends with any clear conjugation pattern
        for (ending in validConjugationEndings) {
            if (cleanText.endsWith(ending) && cleanText.length > ending.length) {
                return true
            }
        }
        
        // For te-form endings (て/で), be more strict
        if (cleanText.endsWith("て") || cleanText.endsWith("で")) {
            // Only accept if it looks like a proper te-form (ends with appropriate sounds)
            if (cleanText.length >= 3) {
                // Common te-form patterns: いて, って, して, んで, etc.
                val validTeForms = listOf("いて", "って", "して", "んで", "いで", "ぎで", "じで", "びで", "みで", "りで")
                return validTeForms.any { cleanText.endsWith(it) }
            }
            return false
        }
        
        // For past tense "た", be more strict
        if (cleanText.endsWith("た")) {
            if (cleanText.length >= 3) {
                // Common past tense patterns
                val validTaForms = listOf("いた", "った", "した", "んだ", "いだ", "ぎだ", "じだ", "びだ", "みだ", "りだ")
                return validTaForms.any { cleanText.endsWith(it) }
            }
            return false
        }
        
        // Also check for common verb stem patterns (masu-stem forms)
        // These should be at least 2 characters and not end with random characters
        if (cleanText.length >= 2) {
            val lastChar = cleanText.last()
            // Valid masu-stem endings (i-sound for godan, e-sound for ichidan)
            val validStemEndings = "いきしちにひみりぎじびぴえけせてねへめれげぜべぺ"
            if (validStemEndings.contains(lastChar)) {
                // Extra check: make sure it's not a random word ending in these sounds
                // Only allow if it's likely a verb stem (not too short, not a known word pattern)
                return cleanText.length >= 3 || cleanText.matches(Regex(".*[あかがさざただなはばぱまやらわ][いきしちにひみりぎじびぴえけせてねへめれげぜべぺ]"))
            }
        }
        
        return false
    }
    
    /**
     * Check if a deinflection rule is phonologically valid for the given word
     * Based on the 8 comprehensive Japanese phonological rules
     */
    private fun isPhonologicallyValid(word: String, rule: LegacyDeinflectionRule): Boolean {
        val stemBeforePattern = word.dropLast(rule.from.length)
        if (stemBeforePattern.isEmpty()) return false
        
        return when (rule.from) {
            // Te-form continuous patterns
            "ている", "ていた", "てる", "ていました", "てた" -> {
                validateTeForm(stemBeforePattern)
            }
            "でいる", "でいた", "でる", "でいました", "でた" -> {
                validateDeForm(stemBeforePattern)
            }
            "っている", "っていた", "ってる", "っていました", "ってた" -> {
                validateTteForm(stemBeforePattern)
            }
            "んでいる", "んでいた", "んでる", "んでいました", "んでた" -> {
                validateNdeForm(stemBeforePattern)
            }
            "いている", "いていた", "いてる", "いていました", "いてた" -> {
                validateIteForm(stemBeforePattern)
            }
            "いでいる", "いでいた", "いでる", "いでいました", "いでた" -> {
                validateIdeForm(stemBeforePattern)
            }
            "している", "していた", "してる", "していました", "してた" -> {
                validateShiteForm(stemBeforePattern)
            }
            
            // Te-form base patterns
            "て" -> validateTeForm(stemBeforePattern)
            "で" -> validateDeForm(stemBeforePattern)
            "って" -> validateTteForm(stemBeforePattern)
            "んで" -> validateNdeForm(stemBeforePattern)
            "いて" -> validateIteForm(stemBeforePattern)
            "いで" -> validateIdeForm(stemBeforePattern)
            "して" -> validateShiteForm(stemBeforePattern)
            
            // Ta-form patterns (past tense)
            "た" -> validateTaForm(stemBeforePattern)
            "だ" -> validateDaForm(stemBeforePattern)
            "った" -> validateTtaForm(stemBeforePattern)
            "んだ" -> validateNdaForm(stemBeforePattern)
            "いた" -> validateItaForm(stemBeforePattern)
            "いだ" -> validateIdaForm(stemBeforePattern)
            "した" -> validateShitaForm(stemBeforePattern)
            
            else -> true // Other patterns pass through
        }
    }
    
    // Rule 1: Ichidan verbs - always て after dropping る
    private fun validateTeForm(stem: String): Boolean {
        // て form should follow ichidan stems or appropriate godan stems
        // NOT stems ending in ん (those use で)
        val lastChar = stem.lastOrNull()
        if (lastChar == 'ん') {
            Log.d(TAG, "て form rejected: stem '$stem' ends in ん, should use で")
            return false
        }
        return true
    }
    
    // Rule 2B: Godan verbs ending in む/ぶ/ぬ -> んで
    private fun validateDeForm(stem: String): Boolean {
        val lastChar = stem.lastOrNull()
        if (lastChar != 'ん') {
            Log.d(TAG, "で form rejected: stem '$stem' doesn't end in ん")
            return false
        }
        return true
    }
    
    // Rule 2A: Godan verbs ending in う/つ/る -> って
    private fun validateTteForm(stem: String): Boolean {
        // って form should follow stems from う/つ/る verbs
        // The stem should be appropriate for sokuonbin
        return true // Accept for now, can be refined
    }
    
    // Rule 2B: Godan verbs ending in む/ぶ/ぬ -> んで
    private fun validateNdeForm(stem: String): Boolean {
        // んで form should follow stems from む/ぶ/ぬ verbs
        return true // Accept for now, can be refined
    }
    
    // Rule 2C: Godan verbs ending in く -> いて
    private fun validateIteForm(stem: String): Boolean {
        // いて form should follow stems from く verbs
        return true // Accept for now, can be refined
    }
    
    // Rule 2D: Godan verbs ending in ぐ -> いで
    private fun validateIdeForm(stem: String): Boolean {
        // いで form should follow stems from ぐ verbs
        return true // Accept for now, can be refined
    }
    
    // Rule 2E: Godan verbs ending in す -> して
    private fun validateShiteForm(stem: String): Boolean {
        // して form should follow stems from す verbs, or be the irregular する
        return true // Accept for now, can be refined
    }
    
    // Ta-form validations (parallel to te-form)
    private fun validateTaForm(stem: String): Boolean {
        val lastChar = stem.lastOrNull()
        if (lastChar == 'ん') {
            Log.d(TAG, "た form rejected: stem '$stem' ends in ん, should use だ")
            return false
        }
        
        // Special case: み -> みる (ichidan verb past tense)
        // This should be allowed as みた -> みる is a valid ichidan conjugation
        if (stem == "み") {
            Log.d(TAG, "た form accepted: stem '$stem' is valid ichidan verb stem (み -> みる)")
            return true
        }
        
        return true
    }
    
    private fun validateDaForm(stem: String): Boolean {
        val lastChar = stem.lastOrNull()
        if (lastChar != 'ん') {
            Log.d(TAG, "だ form rejected: stem '$stem' doesn't end in ん")
            return false
        }
        return true
    }
    
    private fun validateTtaForm(stem: String): Boolean {
        return true // Accept for now, can be refined
    }
    
    private fun validateNdaForm(stem: String): Boolean {
        return true // Accept for now, can be refined
    }
    
    private fun validateItaForm(stem: String): Boolean {
        return true // Accept for now, can be refined
    }
    
    private fun validateIdaForm(stem: String): Boolean {
        return true // Accept for now, can be refined
    }
    
    private fun validateShitaForm(stem: String): Boolean {
        return true // Accept for now, can be refined
    }
}