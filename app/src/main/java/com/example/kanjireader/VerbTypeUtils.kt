package com.example.kanjireader

object VerbTypeUtils {

    fun getVerbTypeFromTags(tags: List<String>): VerbType {
        return when {
            tags.contains("v1") -> VerbType.ICHIDAN
            tags.contains("v5k-s") -> VerbType.IKU_IRREGULAR  // Special 行く
            tags.contains("v5k") -> VerbType.GODAN_K
            tags.contains("v5s") -> VerbType.GODAN_S
            tags.contains("v5t") -> VerbType.GODAN_T
            tags.contains("v5n") -> VerbType.GODAN_N
            tags.contains("v5b") -> VerbType.GODAN_B
            tags.contains("v5m") -> VerbType.GODAN_M
            tags.contains("v5r") -> VerbType.GODAN_R
            tags.contains("v5g") -> VerbType.GODAN_G
            tags.contains("v5u") -> VerbType.GODAN_U
            tags.contains("vs-i") -> VerbType.SURU_IRREGULAR
            tags.contains("vs") -> VerbType.SURU_IRREGULAR
            tags.contains("vk") -> VerbType.KURU_IRREGULAR
            tags.contains("adj-i") -> VerbType.ADJECTIVE_I
            tags.contains("adj-na") -> VerbType.ADJECTIVE_NA
            else -> VerbType.UNKNOWN
        }
    }

    fun getWordTypeMask(verbType: VerbType): Int {
        return when (verbType) {
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
            VerbType.SURU_IRREGULAR -> WordType.SURU_VERB or WordType.SPECIAL_SURU_VERB or WordType.NOUN_VS
            VerbType.KURU_IRREGULAR -> WordType.KURU_VERB
            VerbType.IKU_IRREGULAR -> WordType.GODAN_VERB
            VerbType.ADJECTIVE_I -> WordType.I_ADJ
            VerbType.ADJECTIVE_NA -> 0  // Na-adjectives don't have a WordType mask
            VerbType.UNKNOWN -> WordType.ALL
        }
    }

    fun isVerb(tags: List<String>): Boolean {
        return tags.any { it.startsWith("v") }
    }

    fun isIAdjective(tags: List<String>): Boolean {
        return tags.contains("adj-i")
    }

    fun isNaAdjective(tags: List<String>): Boolean {
        return tags.contains("adj-na")
    }

    // Helper function to determine if a word can be conjugated
    fun isConjugatable(tags: List<String>): Boolean {
        return isVerb(tags) || isIAdjective(tags) || isNaAdjective(tags)
    }

    // Get the specific godan ending character for proper conjugation
    fun getGodanEndingChar(word: String, verbType: VerbType): Char? {
        return when (verbType) {
            VerbType.GODAN_K -> 'く'
            VerbType.GODAN_S -> 'す'
            VerbType.GODAN_T -> 'つ'
            VerbType.GODAN_N -> 'ぬ'
            VerbType.GODAN_B -> 'ぶ'
            VerbType.GODAN_M -> 'む'
            VerbType.GODAN_R -> 'る'
            VerbType.GODAN_G -> 'ぐ'
            VerbType.GODAN_U -> 'う'
            VerbType.IKU_IRREGULAR -> 'く'
            else -> null
        }
    }

    // Determine verb type from the word itself (useful for deinflection results)
    fun detectVerbTypeFromWord(word: String, typeMask: Int): VerbType? {
        return when {
            // Special cases first
            word == "来る" || word == "くる" -> VerbType.KURU_IRREGULAR
            word == "行く" || word == "いく" -> VerbType.IKU_IRREGULAR
            word.endsWith("する") -> VerbType.SURU_IRREGULAR

            // Check type mask
            typeMask and WordType.I_ADJ != 0 -> VerbType.ADJECTIVE_I
            typeMask and WordType.KURU_VERB != 0 -> VerbType.KURU_IRREGULAR
            typeMask and WordType.SURU_VERB != 0 -> VerbType.SURU_IRREGULAR
            typeMask and WordType.ICHIDAN_VERB != 0 -> VerbType.ICHIDAN

            // For godan verbs, check the ending
            typeMask and WordType.GODAN_VERB != 0 -> {
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

            else -> null
        }
    }
}