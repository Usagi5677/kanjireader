package com.example.kanjireader

class ConjugationGenerator(private val engine: TenTenStyleDeinflectionEngine) {

    fun generateConjugations(
        word: String,
        verbType: VerbType,
        baseReading: String,
        meaning: String
    ): List<ConjugationGroup> {
        val stem = getStem(word, verbType)
        val stemReading = getStemReading(baseReading, verbType)
        val hasKanji = hasKanji(word)

        // Get all rules and group them by transformation type
        val rulesByReason = engine.getRulesByReason()
        val groups = mutableListOf<ConjugationGroup>()

        // Basic Forms
        val basicForms = generateBasicForms(word, stem, stemReading, baseReading, verbType, meaning, hasKanji)
        if (basicForms.isNotEmpty()) {
            groups.add(ConjugationGroup("Basic Forms", basicForms))
        }

        // Polite Forms
        val politeForms = generatePoliteForms(stem, stemReading, verbType, meaning, hasKanji)
        if (politeForms.isNotEmpty()) {
            groups.add(ConjugationGroup("Polite Forms", politeForms))
        }

        // Te-form & Continuous
        val teForms = generateTeForms(word, stem, stemReading, verbType, meaning, hasKanji)
        if (teForms.isNotEmpty()) {
            groups.add(ConjugationGroup("Te-form & Continuous", teForms))
        }

        // Volitional Forms
        val volitionalForms = generateVolitionalForms(word, stem, stemReading, baseReading, verbType, meaning, hasKanji)
        if (volitionalForms.isNotEmpty()) {
            groups.add(ConjugationGroup("Volitional Forms", volitionalForms))
        }

        // Conditional Forms
        val conditionalForms = generateConditionalForms(word, stem, stemReading, baseReading, verbType, meaning, hasKanji)
        if (conditionalForms.isNotEmpty()) {
            groups.add(ConjugationGroup("Conditional Forms", conditionalForms))
        }

        // Potential Forms
        val potentialForms = generatePotentialForms(word, stem, stemReading, baseReading, verbType, meaning, hasKanji)
        if (potentialForms.isNotEmpty()) {
            groups.add(ConjugationGroup("Potential Forms", potentialForms))
        }

        // Passive Forms
        val passiveForms = generatePassiveForms(word, stem, stemReading, baseReading, verbType, meaning, hasKanji)
        if (passiveForms.isNotEmpty()) {
            groups.add(ConjugationGroup("Passive Forms", passiveForms))
        }

        // Causative Forms
        val causativeForms = generateCausativeForms(word, stem, stemReading, baseReading, verbType, meaning, hasKanji)
        if (causativeForms.isNotEmpty()) {
            groups.add(ConjugationGroup("Causative Forms", causativeForms))
        }

        // Causative-Passive Forms
        val causativePassiveForms = generateCausativePassiveForms(word, stem, stemReading, baseReading, verbType, meaning, hasKanji)
        if (causativePassiveForms.isNotEmpty()) {
            groups.add(ConjugationGroup("Causative-Passive Forms", causativePassiveForms))
        }

        // Imperative Forms
        val imperativeForms = generateImperativeForms(word, stem, stemReading, baseReading, verbType, meaning, hasKanji)
        if (imperativeForms.isNotEmpty()) {
            groups.add(ConjugationGroup("Imperative Forms", imperativeForms))
        }

        // Desire Forms
        val desireForms = generateDesireForms(word, stem, stemReading, baseReading, verbType, meaning, hasKanji)
        if (desireForms.isNotEmpty()) {
            groups.add(ConjugationGroup("Desire Forms", desireForms))
        }

        // Advanced Forms
        val advancedForms = generateAdvancedForms(word, stem, stemReading, baseReading, verbType, meaning, hasKanji)
        if (advancedForms.isNotEmpty()) {
            groups.add(ConjugationGroup("Advanced Forms", advancedForms))
        }

        val hearsayForms = generateHearsayForms(word, stem, stemReading, baseReading, verbType, meaning, hasKanji)
        if (hearsayForms.isNotEmpty()) {
            groups.add(ConjugationGroup("Hearsay & Presumptive Forms", hearsayForms))
        }

        return groups
    }

    private fun generateFromRules(
        reasonFilter: String,
        word: String,
        stem: String,
        stemReading: String,
        baseReading: String,
        verbType: VerbType,
        meaning: String,
        rulesByReason: Map<String, List<DeinflectionRule>>,
        hasKanji: Boolean
    ): List<ConjugationItem> {
        val items = mutableListOf<ConjugationItem>()
        val wordTypeMask = VerbTypeUtils.getWordTypeMask(verbType)

        // Find rules that match this transformation type
        val relevantRules = rulesByReason.entries
            .filter { it.key.contains(reasonFilter, ignoreCase = true) }
            .flatMap { it.value }
            .filter { rule ->
                // Check if this rule can produce our verb type
                rule.toType and wordTypeMask != 0
            }

        for (rule in relevantRules) {
            val conjugated = applyRuleInReverse(word, stem, rule, verbType)
            if (conjugated != null) {
                val reading = if (hasKanji) {
                    applyRuleInReverse(baseReading, stemReading, rule, verbType)
                } else null

                val meaningText = transformMeaning(meaning, rule.reason)
                items.add(
                    ConjugationItem(
                        formName = formatRuleName(rule.reason),
                        conjugation = conjugated,
                        reading = reading,
                        meaning = meaningText
                    )
                )
            }
        }

        return items
    }

    private fun applyRuleInReverse(
        word: String,
        stem: String,
        rule: DeinflectionRule,
        verbType: VerbType
    ): String? {
        // The rule says: conjugated form (from) → base form (to)
        // We need to go: base form → conjugated form

        return when {
            // If the rule removes an ending (to = ""), we add the conjugated ending
            rule.to.isEmpty() -> {
                when (rule.fromType) {
                    WordType.MASU_STEM -> stem + rule.from
                    WordType.TA_TE_STEM -> {
                        // Need to figure out the te-stem first
                        val teStem = getTeStem(word, verbType)
                        teStem + rule.from
                    }
                    WordType.IRREALIS_STEM -> {
                        val negStem = getNegativeStem(word, verbType)
                        negStem + rule.from
                    }
                    else -> stem + rule.from
                }
            }

            // If the rule replaces an ending
            word.endsWith(rule.to) -> {
                word.dropLast(rule.to.length) + rule.from
            }

            else -> null
        }
    }

    private fun getStem(word: String, verbType: VerbType): String {
        return when (verbType) {
            VerbType.ICHIDAN -> word.dropLast(1)
            VerbType.SURU_IRREGULAR -> word.dropLast(2)
            VerbType.KURU_IRREGULAR -> if (word == "来る") "" else word.dropLast(2)
            VerbType.ADJECTIVE_I -> word.dropLast(1)
            else -> word.dropLast(1) // Godan verbs
        }
    }

    private fun getStemReading(reading: String, verbType: VerbType): String {
        return when (verbType) {
            VerbType.ICHIDAN -> reading.dropLast(1)
            VerbType.SURU_IRREGULAR -> reading.dropLast(2)
            VerbType.KURU_IRREGULAR -> ""
            VerbType.ADJECTIVE_I -> reading.dropLast(1)
            else -> reading.dropLast(1) // Godan verbs
        }
    }

    private fun getTeStem(word: String, verbType: VerbType): String {
        val stem = getStem(word, verbType)
        return when (verbType) {
            VerbType.ICHIDAN -> stem
            VerbType.GODAN_K -> stem + "い"
            VerbType.GODAN_S -> stem + "し"
            VerbType.GODAN_T -> stem + "っ"
            VerbType.GODAN_R -> stem + "っ"
            VerbType.GODAN_U -> stem + "っ"
            VerbType.GODAN_G -> stem + "い"
            VerbType.GODAN_B -> stem + "ん"
            VerbType.GODAN_M -> stem + "ん"
            VerbType.GODAN_N -> stem + "ん"
            VerbType.SURU_IRREGULAR -> stem + "し"
            VerbType.KURU_IRREGULAR -> if (word == "来る") "来" else "き"
            VerbType.IKU_IRREGULAR -> if (word == "行く") "行っ" else "いっ"
            else -> stem
        }
    }

    private fun getNegativeStem(word: String, verbType: VerbType): String {
        val stem = getStem(word, verbType)
        return when (verbType) {
            VerbType.ICHIDAN -> stem
            VerbType.GODAN_K -> stem + "か"
            VerbType.GODAN_S -> stem + "さ"
            VerbType.GODAN_T -> stem + "た"
            VerbType.GODAN_R -> stem + "ら"
            VerbType.GODAN_U -> stem + "わ"
            VerbType.GODAN_G -> stem + "が"
            VerbType.GODAN_B -> stem + "ば"
            VerbType.GODAN_M -> stem + "ま"
            VerbType.GODAN_N -> stem + "な"
            VerbType.SURU_IRREGULAR -> stem + "し"
            VerbType.KURU_IRREGULAR -> if (word == "来る") "来" else "こ"
            VerbType.IKU_IRREGULAR -> stem + "か"
            else -> stem
        }
    }

    // Replace the generateBasicForms method with this updated version:

    private fun generateBasicForms(
        word: String,
        stem: String,
        stemReading: String,
        baseReading: String,
        verbType: VerbType,
        meaning: String,
        hasKanji: Boolean
    ): List<ConjugationItem> {
        val forms = mutableListOf<ConjugationItem>()

        // Dictionary form
        //forms.add(ConjugationItem(
        //    "Dictionary",
        //    word,
        //    if (word != baseReading) baseReading else null,
        //    formatMeaning(meaning, "dictionary")
        //))

        when (verbType) {
            VerbType.ICHIDAN -> {
                forms.add(ConjugationItem("Past", "${stem}た", if (hasKanji) "${stemReading}た" else null, formatMeaning(meaning, "past")))
                forms.add(ConjugationItem("Negative", "${stem}ない", if (hasKanji) "${stemReading}ない" else null, formatMeaning(meaning, "negative")))
                forms.add(ConjugationItem("Past Negative", "${stem}なかった", if (hasKanji) "${stemReading}なかった" else null, formatMeaning(meaning, "past negative")))
                forms.add(ConjugationItem("Archaic Negative", "${stem}ぬ", if (hasKanji) "${stemReading}ぬ" else null, formatMeaning(meaning, "archaic negative")))
                forms.add(ConjugationItem("Noun Form", stem, if (hasKanji) stemReading else null, formatMeaning(meaning, "noun form")))
            }
            VerbType.GODAN_K -> {
                forms.add(ConjugationItem("Past", "${stem}いた", if (hasKanji) "${stemReading}いた" else null, formatMeaning(meaning, "past")))
                forms.add(ConjugationItem("Negative", "${stem}かない", if (hasKanji) "${stemReading}かない" else null, formatMeaning(meaning, "negative")))
                forms.add(ConjugationItem("Past Negative", "${stem}かなかった", if (hasKanji) "${stemReading}かなかった" else null, formatMeaning(meaning, "past negative")))
                forms.add(ConjugationItem("Archaic Negative", "${stem}かぬ", if (hasKanji) "${stemReading}かぬ" else null, formatMeaning(meaning, "archaic negative")))
                forms.add(ConjugationItem("Noun Form", "${stem}き", if (hasKanji) "${stemReading}き" else null, formatMeaning(meaning, "noun form")))
            }
            VerbType.GODAN_S -> {
                forms.add(ConjugationItem("Past", "${stem}した", if (hasKanji) "${stemReading}した" else null, formatMeaning(meaning, "past")))
                forms.add(ConjugationItem("Negative", "${stem}さない", if (hasKanji) "${stemReading}さない" else null, formatMeaning(meaning, "negative")))
                forms.add(ConjugationItem("Past Negative", "${stem}さなかった", if (hasKanji) "${stemReading}さなかった" else null, formatMeaning(meaning, "past negative")))
                forms.add(ConjugationItem("Archaic Negative", "${stem}さぬ", if (hasKanji) "${stemReading}さぬ" else null, formatMeaning(meaning, "archaic negative")))
                forms.add(ConjugationItem("Noun Form", "${stem}し", if (hasKanji) "${stemReading}し" else null, formatMeaning(meaning, "noun form")))
            }
            VerbType.GODAN_T -> {
                forms.add(ConjugationItem("Past", "${stem}った", if (hasKanji) "${stemReading}った" else null, formatMeaning(meaning, "past")))
                forms.add(ConjugationItem("Negative", "${stem}たない", if (hasKanji) "${stemReading}たない" else null, formatMeaning(meaning, "negative")))
                forms.add(ConjugationItem("Past Negative", "${stem}たなかった", if (hasKanji) "${stemReading}たなかった" else null, formatMeaning(meaning, "past negative")))
                forms.add(ConjugationItem("Archaic Negative", "${stem}たぬ", if (hasKanji) "${stemReading}たぬ" else null, formatMeaning(meaning, "archaic negative")))
                forms.add(ConjugationItem("Noun Form", "${stem}ち", if (hasKanji) "${stemReading}ち" else null, formatMeaning(meaning, "noun form")))
            }
            VerbType.GODAN_R -> {
                forms.add(ConjugationItem("Past", "${stem}った", if (hasKanji) "${stemReading}った" else null, formatMeaning(meaning, "past")))
                forms.add(ConjugationItem("Negative", "${stem}らない", if (hasKanji) "${stemReading}らない" else null, formatMeaning(meaning, "negative")))
                forms.add(ConjugationItem("Past Negative", "${stem}らなかった", if (hasKanji) "${stemReading}らなかった" else null, formatMeaning(meaning, "past negative")))
                forms.add(ConjugationItem("Archaic Negative", "${stem}らぬ", if (hasKanji) "${stemReading}らぬ" else null, formatMeaning(meaning, "archaic negative")))
                forms.add(ConjugationItem("Noun Form", "${stem}り", if (hasKanji) "${stemReading}り" else null, formatMeaning(meaning, "noun form")))
            }
            VerbType.GODAN_U -> {
                forms.add(ConjugationItem("Past", "${stem}った", if (hasKanji) "${stemReading}った" else null, formatMeaning(meaning, "past")))
                forms.add(ConjugationItem("Negative", "${stem}わない", if (hasKanji) "${stemReading}わない" else null, formatMeaning(meaning, "negative")))
                forms.add(ConjugationItem("Past Negative", "${stem}わなかった", if (hasKanji) "${stemReading}わなかった" else null, formatMeaning(meaning, "past negative")))
                forms.add(ConjugationItem("Archaic Negative", "${stem}わぬ", if (hasKanji) "${stemReading}わぬ" else null, formatMeaning(meaning, "archaic negative")))
                forms.add(ConjugationItem("Noun Form", "${stem}い", if (hasKanji) "${stemReading}い" else null, formatMeaning(meaning, "noun form")))
            }
            VerbType.GODAN_G -> {
                forms.add(ConjugationItem("Past", "${stem}いだ", if (hasKanji) "${stemReading}いだ" else null, formatMeaning(meaning, "past")))
                forms.add(ConjugationItem("Negative", "${stem}がない", if (hasKanji) "${stemReading}がない" else null, formatMeaning(meaning, "negative")))
                forms.add(ConjugationItem("Past Negative", "${stem}がなかった", if (hasKanji) "${stemReading}がなかった" else null, formatMeaning(meaning, "past negative")))
                forms.add(ConjugationItem("Archaic Negative", "${stem}がぬ", if (hasKanji) "${stemReading}がぬ" else null, formatMeaning(meaning, "archaic negative")))
                forms.add(ConjugationItem("Noun Form", "${stem}ぎ", if (hasKanji) "${stemReading}ぎ" else null, formatMeaning(meaning, "noun form")))
            }
            VerbType.GODAN_B -> {
                forms.add(ConjugationItem("Past", "${stem}んだ", if (hasKanji) "${stemReading}んだ" else null, formatMeaning(meaning, "past")))
                forms.add(ConjugationItem("Negative", "${stem}ばない", if (hasKanji) "${stemReading}ばない" else null, formatMeaning(meaning, "negative")))
                forms.add(ConjugationItem("Past Negative", "${stem}ばなかった", if (hasKanji) "${stemReading}ばなかった" else null, formatMeaning(meaning, "past negative")))
                forms.add(ConjugationItem("Archaic Negative", "${stem}ばぬ", if (hasKanji) "${stemReading}ばぬ" else null, formatMeaning(meaning, "archaic negative")))
                forms.add(ConjugationItem("Noun Form", "${stem}び", if (hasKanji) "${stemReading}び" else null, formatMeaning(meaning, "noun form")))
            }
            VerbType.GODAN_M -> {
                forms.add(ConjugationItem("Past", "${stem}んだ", if (hasKanji) "${stemReading}んだ" else null, formatMeaning(meaning, "past")))
                forms.add(ConjugationItem("Negative", "${stem}まない", if (hasKanji) "${stemReading}まない" else null, formatMeaning(meaning, "negative")))
                forms.add(ConjugationItem("Past Negative", "${stem}まなかった", if (hasKanji) "${stemReading}まなかった" else null, formatMeaning(meaning, "past negative")))
                forms.add(ConjugationItem("Archaic Negative", "${stem}まぬ", if (hasKanji) "${stemReading}まぬ" else null, formatMeaning(meaning, "archaic negative")))
                forms.add(ConjugationItem("Noun Form", "${stem}み", if (hasKanji) "${stemReading}み" else null, formatMeaning(meaning, "noun form")))
            }
            VerbType.GODAN_N -> {
                forms.add(ConjugationItem("Past", "${stem}んだ", if (hasKanji) "${stemReading}んだ" else null, formatMeaning(meaning, "past")))
                forms.add(ConjugationItem("Negative", "${stem}なない", if (hasKanji) "${stemReading}なない" else null, formatMeaning(meaning, "negative")))
                forms.add(ConjugationItem("Past Negative", "${stem}ななかった", if (hasKanji) "${stemReading}ななかった" else null, formatMeaning(meaning, "past negative")))
                forms.add(ConjugationItem("Archaic Negative", "${stem}なぬ", if (hasKanji) "${stemReading}なぬ" else null, formatMeaning(meaning, "archaic negative")))
                forms.add(ConjugationItem("Noun Form", "${stem}に", if (hasKanji) "${stemReading}に" else null, formatMeaning(meaning, "noun form")))
            }
            VerbType.SURU_IRREGULAR -> {
                forms.add(ConjugationItem("Past", "${stem}した", if (hasKanji) "${stemReading}した" else null, formatMeaning(meaning, "past")))
                forms.add(ConjugationItem("Negative", "${stem}しない", if (hasKanji) "${stemReading}しない" else null, formatMeaning(meaning, "negative")))
                forms.add(ConjugationItem("Past Negative", "${stem}しなかった", if (hasKanji) "${stemReading}しなかった" else null, formatMeaning(meaning, "past negative")))
                forms.add(ConjugationItem("Archaic Negative", "${stem}せぬ", if (hasKanji) "${stemReading}せぬ" else null, formatMeaning(meaning, "archaic negative")))
                forms.add(ConjugationItem("Noun Form", "${stem}し", if (hasKanji) "${stemReading}し" else null, formatMeaning(meaning, "noun form")))
            }
            VerbType.KURU_IRREGULAR -> {
                val base = if (word == "来る") "来" else "く"
                forms.add(ConjugationItem("Past", "${base}た", "きた", formatMeaning(meaning, "past")))
                forms.add(ConjugationItem("Negative", "${base}ない", "こない", formatMeaning(meaning, "negative")))
                forms.add(ConjugationItem("Past Negative", "${base}なかった", "こなかった", formatMeaning(meaning, "past negative")))
                forms.add(ConjugationItem("Archaic Negative", "${base}ぬ", "こぬ", formatMeaning(meaning, "archaic negative")))
                forms.add(ConjugationItem("Noun Form", if (word == "来る") "来" else "き", "き", formatMeaning(meaning, "noun form")))
            }
            VerbType.IKU_IRREGULAR -> {
                forms.add(ConjugationItem("Past", if (word == "行く") "行った" else "いった", "いった", formatMeaning(meaning, "past")))
                forms.add(ConjugationItem("Negative", "${stem}かない", if (hasKanji) "${stemReading}かない" else null, formatMeaning(meaning, "negative")))
                forms.add(ConjugationItem("Past Negative", "${stem}かなかった", if (hasKanji) "${stemReading}かなかった" else null, formatMeaning(meaning, "past negative")))
                forms.add(ConjugationItem("Archaic Negative", "${stem}かぬ", if (hasKanji) "${stemReading}かぬ" else null, formatMeaning(meaning, "archaic negative")))
                forms.add(ConjugationItem("Noun Form", "${stem}き", if (hasKanji) "${stemReading}き" else null, formatMeaning(meaning, "noun form")))
            }
            VerbType.ADJECTIVE_I -> {
                forms.add(ConjugationItem("Past", "${stem}かった", if (hasKanji) "${stemReading}かった" else null, "was $meaning"))
                forms.add(ConjugationItem("Negative", "${stem}くない", if (hasKanji) "${stemReading}くない" else null, "is not $meaning"))
                forms.add(ConjugationItem("Past Negative", "${stem}くなかった", if (hasKanji) "${stemReading}くなかった" else null, "was not $meaning"))
            }
            VerbType.ADJECTIVE_NA -> {
                forms.add(ConjugationItem("Attributive", "${word}な", if (hasKanji) "${baseReading}な" else null, formatMeaning(meaning, "attributive")))
                forms.add(ConjugationItem("With copula", "${word}だ", if (hasKanji) "${baseReading}だ" else null, formatMeaning(meaning, "with copula")))
                forms.add(ConjugationItem("Past", "${word}だった", if (hasKanji) "${baseReading}だった" else null, "was $meaning"))
                forms.add(ConjugationItem("Negative", "${word}じゃない", if (hasKanji) "${baseReading}じゃない" else null, "is not $meaning"))
                forms.add(ConjugationItem("Past Negative", "${word}じゃなかった", if (hasKanji) "${baseReading}じゃなかった" else null, "was not $meaning"))
            }
            else -> {}
        }

        return forms
    }


    private fun generatePoliteForms(
        stem: String,
        stemReading: String,
        verbType: VerbType,
        meaning: String,
        hasKanji: Boolean
    ): List<ConjugationItem> {
        val forms = mutableListOf<ConjugationItem>()

        when (verbType) {
            VerbType.ICHIDAN -> {
                forms.add(ConjugationItem("Polite", "${stem}ます", if (hasKanji) "${stemReading}ます" else null, formatMeaning(meaning, "polite")))
                forms.add(ConjugationItem("Polite Past", "${stem}ました", if (hasKanji) "${stemReading}ました" else null, formatMeaning(meaning, "polite past")))
                forms.add(ConjugationItem("Polite Negative", "${stem}ません", if (hasKanji) "${stemReading}ません" else null, formatMeaning(meaning, "polite negative")))
                forms.add(ConjugationItem("Polite Past Negative", "${stem}ませんでした", if (hasKanji) "${stemReading}ませんでした" else null, formatMeaning(meaning, "polite past negative")))
            }
            VerbType.GODAN_K -> {
                forms.add(ConjugationItem("Polite", "${stem}きます", if (hasKanji) "${stemReading}きます" else null, formatMeaning(meaning, "polite")))
                forms.add(ConjugationItem("Polite Past", "${stem}きました", if (hasKanji) "${stemReading}きました" else null, formatMeaning(meaning, "polite past")))
                forms.add(ConjugationItem("Polite Negative", "${stem}きません", if (hasKanji) "${stemReading}きません" else null, formatMeaning(meaning, "polite negative")))
                forms.add(ConjugationItem("Polite Past Negative", "${stem}きませんでした", if (hasKanji) "${stemReading}きませんでした" else null, formatMeaning(meaning, "polite past negative")))
            }
            VerbType.GODAN_S -> {
                forms.add(ConjugationItem("Polite", "${stem}します", if (hasKanji) "${stemReading}します" else null, formatMeaning(meaning, "polite")))
                forms.add(ConjugationItem("Polite Past", "${stem}しました", if (hasKanji) "${stemReading}しました" else null, formatMeaning(meaning, "polite past")))
                forms.add(ConjugationItem("Polite Negative", "${stem}しません", if (hasKanji) "${stemReading}しません" else null, formatMeaning(meaning, "polite negative")))
                forms.add(ConjugationItem("Polite Past Negative", "${stem}しませんでした", if (hasKanji) "${stemReading}しませんでした" else null, formatMeaning(meaning, "polite past negative")))
            }
            VerbType.GODAN_T -> {
                forms.add(ConjugationItem("Polite", "${stem}ちます", if (hasKanji) "${stemReading}ちます" else null, formatMeaning(meaning, "polite")))
                forms.add(ConjugationItem("Polite Past", "${stem}ちました", if (hasKanji) "${stemReading}ちました" else null, formatMeaning(meaning, "polite past")))
                forms.add(ConjugationItem("Polite Negative", "${stem}ちません", if (hasKanji) "${stemReading}ちません" else null, formatMeaning(meaning, "polite negative")))
                forms.add(ConjugationItem("Polite Past Negative", "${stem}ちませんでした", if (hasKanji) "${stemReading}ちませんでした" else null, formatMeaning(meaning, "polite past negative")))
            }
            VerbType.GODAN_R -> {
                forms.add(ConjugationItem("Polite", "${stem}ります", if (hasKanji) "${stemReading}ります" else null, formatMeaning(meaning, "polite")))
                forms.add(ConjugationItem("Polite Past", "${stem}りました", if (hasKanji) "${stemReading}りました" else null, formatMeaning(meaning, "polite past")))
                forms.add(ConjugationItem("Polite Negative", "${stem}りません", if (hasKanji) "${stemReading}りません" else null, formatMeaning(meaning, "polite negative")))
                forms.add(ConjugationItem("Polite Past Negative", "${stem}りませんでした", if (hasKanji) "${stemReading}りませんでした" else null, formatMeaning(meaning, "polite past negative")))
            }
            VerbType.GODAN_U -> {
                forms.add(ConjugationItem("Polite", "${stem}います", if (hasKanji) "${stemReading}います" else null, formatMeaning(meaning, "polite")))
                forms.add(ConjugationItem("Polite Past", "${stem}いました", if (hasKanji) "${stemReading}いました" else null, formatMeaning(meaning, "polite past")))
                forms.add(ConjugationItem("Polite Negative", "${stem}いません", if (hasKanji) "${stemReading}いません" else null, formatMeaning(meaning, "polite negative")))
                forms.add(ConjugationItem("Polite Past Negative", "${stem}いませんでした", if (hasKanji) "${stemReading}いませんでした" else null, formatMeaning(meaning, "polite past negative")))
            }
            VerbType.GODAN_G -> {
                forms.add(ConjugationItem("Polite", "${stem}ぎます", if (hasKanji) "${stemReading}ぎます" else null, formatMeaning(meaning, "polite")))
                forms.add(ConjugationItem("Polite Past", "${stem}ぎました", if (hasKanji) "${stemReading}ぎました" else null, formatMeaning(meaning, "polite past")))
                forms.add(ConjugationItem("Polite Negative", "${stem}ぎません", if (hasKanji) "${stemReading}ぎません" else null, formatMeaning(meaning, "polite negative")))
                forms.add(ConjugationItem("Polite Past Negative", "${stem}ぎませんでした", if (hasKanji) "${stemReading}ぎませんでした" else null, formatMeaning(meaning, "polite past negative")))
            }
            VerbType.GODAN_B -> {
                forms.add(ConjugationItem("Polite", "${stem}びます", if (hasKanji) "${stemReading}びます" else null, formatMeaning(meaning, "polite")))
                forms.add(ConjugationItem("Polite Past", "${stem}びました", if (hasKanji) "${stemReading}びました" else null, formatMeaning(meaning, "polite past")))
                forms.add(ConjugationItem("Polite Negative", "${stem}びません", if (hasKanji) "${stemReading}びません" else null, formatMeaning(meaning, "polite negative")))
                forms.add(ConjugationItem("Polite Past Negative", "${stem}びませんでした", if (hasKanji) "${stemReading}びませんでした" else null, formatMeaning(meaning, "polite past negative")))
            }
            VerbType.GODAN_M -> {
                forms.add(ConjugationItem("Polite", "${stem}みます", if (hasKanji) "${stemReading}みます" else null, formatMeaning(meaning, "polite")))
                forms.add(ConjugationItem("Polite Past", "${stem}みました", if (hasKanji) "${stemReading}みました" else null, formatMeaning(meaning, "polite past")))
                forms.add(ConjugationItem("Polite Negative", "${stem}みません", if (hasKanji) "${stemReading}みません" else null, formatMeaning(meaning, "polite negative")))
                forms.add(ConjugationItem("Polite Past Negative", "${stem}みませんでした", if (hasKanji) "${stemReading}みませんでした" else null, formatMeaning(meaning, "polite past negative")))
            }
            VerbType.GODAN_N -> {
                forms.add(ConjugationItem("Polite", "${stem}にます", if (hasKanji) "${stemReading}にます" else null, formatMeaning(meaning, "polite")))
                forms.add(ConjugationItem("Polite Past", "${stem}にました", if (hasKanji) "${stemReading}にました" else null, formatMeaning(meaning, "polite past")))
                forms.add(ConjugationItem("Polite Negative", "${stem}にません", if (hasKanji) "${stemReading}にません" else null, formatMeaning(meaning, "polite negative")))
                forms.add(ConjugationItem("Polite Past Negative", "${stem}にませんでした", if (hasKanji) "${stemReading}にませんでした" else null, formatMeaning(meaning, "polite past negative")))
            }
            VerbType.IKU_IRREGULAR -> {
                val base = if (stem.endsWith("行")) "行" else "い"
                forms.add(ConjugationItem("Polite", "${base}きます", "いきます", formatMeaning(meaning, "polite")))
                forms.add(ConjugationItem("Polite Past", "${base}きました", "いきました", formatMeaning(meaning, "polite past")))
                forms.add(ConjugationItem("Polite Negative", "${base}きません", "いきません", formatMeaning(meaning, "polite negative")))
                forms.add(ConjugationItem("Polite Past Negative", "${base}きませんでした", "いきませんでした", formatMeaning(meaning, "polite past negative")))
            }
            VerbType.SURU_IRREGULAR -> {
                forms.add(ConjugationItem("Polite", "${stem}します", if (hasKanji) "${stemReading}します" else null, formatMeaning(meaning, "polite")))
                forms.add(ConjugationItem("Polite Past", "${stem}しました", if (hasKanji) "${stemReading}しました" else null, formatMeaning(meaning, "polite past")))
                forms.add(ConjugationItem("Polite Negative", "${stem}しません", if (hasKanji) "${stemReading}しません" else null, formatMeaning(meaning, "polite negative")))
                forms.add(ConjugationItem("Polite Past Negative", "${stem}しませんでした", if (hasKanji) "${stemReading}しませんでした" else null, formatMeaning(meaning, "polite past negative")))
            }
            VerbType.KURU_IRREGULAR -> {
                val base = if (stem.isEmpty()) "来" else "き"
                forms.add(ConjugationItem("Polite", "${base}ます", "きます", formatMeaning(meaning, "polite")))
                forms.add(ConjugationItem("Polite Past", "${base}ました", "きました", formatMeaning(meaning, "polite past")))
                forms.add(ConjugationItem("Polite Negative", "${base}ません", "きません", formatMeaning(meaning, "polite negative")))
                forms.add(ConjugationItem("Polite Past Negative", "${base}ませんでした", "きませんでした", formatMeaning(meaning, "polite past negative")))
            }
            else -> {}
        }

        return forms
    }

    private fun generateTeForms(
        word: String,
        stem: String,
        stemReading: String,
        verbType: VerbType,
        meaning: String,
        hasKanji: Boolean
    ): List<ConjugationItem> {
        val forms = mutableListOf<ConjugationItem>()
        val teStem = getTeStem(word, verbType)
        val teStemReading = if (hasKanji) getTeStem(stemReading + getEndingChar(verbType), verbType) else null

        when (verbType) {
            VerbType.ICHIDAN -> {
                forms.add(ConjugationItem("Te-form", "${stem}て", if (hasKanji) "${stemReading}て" else null, formatMeaning(meaning, "te-form")))
                forms.add(ConjugationItem("Continuous", "${stem}ている", if (hasKanji) "${stemReading}ている" else null, formatMeaning(meaning, "continuous")))
                forms.add(ConjugationItem("Continuous Past", "${stem}ていた", if (hasKanji) "${stemReading}ていた" else null, formatMeaning(meaning, "continuous past")))
                forms.add(ConjugationItem("Polite Continuous", "${stem}ています", if (hasKanji) "${stemReading}ています" else null, formatMeaning(meaning, "polite continuous")))
                forms.add(ConjugationItem("Polite Past Continuous", "${stem}ていました", if (hasKanji) "${stemReading}ていました" else null, formatMeaning(meaning, "polite past continuous")))
                forms.add(ConjugationItem("Request", "${stem}てください", if (hasKanji) "${stemReading}てください" else null, formatMeaning(meaning, "request")))
            }
            VerbType.GODAN_K -> {
                forms.add(ConjugationItem("Te-form", "${stem}いて", if (hasKanji) "${stemReading}いて" else null, formatMeaning(meaning, "te-form")))
                forms.add(ConjugationItem("Continuous", "${stem}いている", if (hasKanji) "${stemReading}いている" else null, formatMeaning(meaning, "continuous")))
                forms.add(ConjugationItem("Continuous Past", "${stem}いていた", if (hasKanji) "${stemReading}いていた" else null, formatMeaning(meaning, "continuous past")))
                forms.add(ConjugationItem("Polite Continuous", "${stem}いています", if (hasKanji) "${stemReading}いています" else null, formatMeaning(meaning, "polite continuous")))
                forms.add(ConjugationItem("Polite Past Continuous", "${stem}いていました", if (hasKanji) "${stemReading}いていました" else null, formatMeaning(meaning, "polite past continuous")))
                forms.add(ConjugationItem("Request", "${stem}いてください", if (hasKanji) "${stemReading}いてください" else null, formatMeaning(meaning, "request")))
            }
            VerbType.GODAN_S -> {
                forms.add(ConjugationItem("Te-form", "${stem}して", if (hasKanji) "${stemReading}して" else null, formatMeaning(meaning, "te-form")))
                forms.add(ConjugationItem("Continuous", "${stem}している", if (hasKanji) "${stemReading}している" else null, formatMeaning(meaning, "continuous")))
                forms.add(ConjugationItem("Continuous Past", "${stem}していた", if (hasKanji) "${stemReading}していた" else null, formatMeaning(meaning, "continuous past")))
                forms.add(ConjugationItem("Polite Continuous", "${stem}しています", if (hasKanji) "${stemReading}しています" else null, formatMeaning(meaning, "polite continuous")))
                forms.add(ConjugationItem("Polite Past Continuous", "${stem}していました", if (hasKanji) "${stemReading}していました" else null, formatMeaning(meaning, "polite past continuous")))
                forms.add(ConjugationItem("Request", "${stem}してください", if (hasKanji) "${stemReading}してください" else null, formatMeaning(meaning, "request")))
            }
            VerbType.GODAN_T, VerbType.GODAN_R, VerbType.GODAN_U -> {
                val teEnding = if (verbType == VerbType.GODAN_T) "って" else "って"
                forms.add(ConjugationItem("Te-form", "${stem}${teEnding}", if (hasKanji) "${stemReading}${teEnding}" else null, formatMeaning(meaning, "te-form")))
                forms.add(ConjugationItem("Continuous", "${stem}っている", if (hasKanji) "${stemReading}っている" else null, formatMeaning(meaning, "continuous")))
                forms.add(ConjugationItem("Continuous Past", "${stem}っていた", if (hasKanji) "${stemReading}っていた" else null, formatMeaning(meaning, "continuous past")))
                forms.add(ConjugationItem("Polite Continuous", "${stem}っています", if (hasKanji) "${stemReading}っています" else null, formatMeaning(meaning, "polite continuous")))
                forms.add(ConjugationItem("Polite Past Continuous", "${stem}っていました", if (hasKanji) "${stemReading}っていました" else null, formatMeaning(meaning, "polite past continuous")))
                forms.add(ConjugationItem("Request", "${stem}ってください", if (hasKanji) "${stemReading}ってください" else null, formatMeaning(meaning, "request")))
            }
            VerbType.GODAN_G -> {
                forms.add(ConjugationItem("Te-form", "${stem}いで", if (hasKanji) "${stemReading}いで" else null, formatMeaning(meaning, "te-form")))
                forms.add(ConjugationItem("Continuous", "${stem}いでいる", if (hasKanji) "${stemReading}いでいる" else null, formatMeaning(meaning, "continuous")))
                forms.add(ConjugationItem("Continuous Past", "${stem}いでいた", if (hasKanji) "${stemReading}いでいた" else null, formatMeaning(meaning, "continuous past")))
                forms.add(ConjugationItem("Polite Continuous", "${stem}いでいます", if (hasKanji) "${stemReading}いでいます" else null, formatMeaning(meaning, "polite continuous")))
                forms.add(ConjugationItem("Polite Past Continuous", "${stem}いでいました", if (hasKanji) "${stemReading}いでいました" else null, formatMeaning(meaning, "polite past continuous")))
                forms.add(ConjugationItem("Request", "${stem}いでください", if (hasKanji) "${stemReading}いでください" else null, formatMeaning(meaning, "request")))
            }
            VerbType.GODAN_B, VerbType.GODAN_M, VerbType.GODAN_N -> {
                forms.add(ConjugationItem("Te-form", "${stem}んで", if (hasKanji) "${stemReading}んで" else null, formatMeaning(meaning, "te-form")))
                forms.add(ConjugationItem("Continuous", "${stem}んでいる", if (hasKanji) "${stemReading}んでいる" else null, formatMeaning(meaning, "continuous")))
                forms.add(ConjugationItem("Continuous Past", "${stem}んでいた", if (hasKanji) "${stemReading}んでいた" else null, formatMeaning(meaning, "continuous past")))
                forms.add(ConjugationItem("Polite Continuous", "${stem}んでいます", if (hasKanji) "${stemReading}んでいます" else null, formatMeaning(meaning, "polite continuous")))
                forms.add(ConjugationItem("Polite Past Continuous", "${stem}んでいました", if (hasKanji) "${stemReading}んでいました" else null, formatMeaning(meaning, "polite past continuous")))
                forms.add(ConjugationItem("Request", "${stem}んでください", if (hasKanji) "${stemReading}んでください" else null, formatMeaning(meaning, "request")))
            }
            VerbType.SURU_IRREGULAR -> {
                forms.add(ConjugationItem("Te-form", "${stem}して", if (hasKanji) "${stemReading}して" else null, formatMeaning(meaning, "te-form")))
                forms.add(ConjugationItem("Continuous", "${stem}している", if (hasKanji) "${stemReading}している" else null, formatMeaning(meaning, "continuous")))
                forms.add(ConjugationItem("Continuous Past", "${stem}していた", if (hasKanji) "${stemReading}していた" else null, formatMeaning(meaning, "continuous past")))
                forms.add(ConjugationItem("Polite Continuous", "${stem}しています", if (hasKanji) "${stemReading}しています" else null, formatMeaning(meaning, "polite continuous")))
                forms.add(ConjugationItem("Polite Past Continuous", "${stem}していました", if (hasKanji) "${stemReading}していました" else null, formatMeaning(meaning, "polite past continuous")))
                forms.add(ConjugationItem("Request", "${stem}してください", if (hasKanji) "${stemReading}してください" else null, formatMeaning(meaning, "request")))
            }
            VerbType.KURU_IRREGULAR -> {
                val base = if (word == "来る") "来" else "き"
                forms.add(ConjugationItem("Te-form", "${base}て", "きて", formatMeaning(meaning, "te-form")))
                forms.add(ConjugationItem("Continuous", "${base}ている", "きている", formatMeaning(meaning, "continuous")))
                forms.add(ConjugationItem("Continuous Past", "${base}ていた", "きていた", formatMeaning(meaning, "continuous past")))
                forms.add(ConjugationItem("Polite Continuous", "${base}ています", "きています", formatMeaning(meaning, "polite continuous")))
                forms.add(ConjugationItem("Polite Past Continuous", "${base}ていました", "きていました", formatMeaning(meaning, "polite past continuous")))
                forms.add(ConjugationItem("Request", "${base}てください", "きてください", formatMeaning(meaning, "request")))
            }
            VerbType.IKU_IRREGULAR -> {
                forms.add(ConjugationItem("Te-form", if (word == "行く") "行って" else "いって", "いって", formatMeaning(meaning, "te-form")))
                forms.add(ConjugationItem("Continuous", if (word == "行く") "行っている" else "いっている", "いっている", formatMeaning(meaning, "continuous")))
                forms.add(ConjugationItem("Continuous Past", if (word == "行く") "行っていた" else "いっていた", "いっていた", formatMeaning(meaning, "continuous past")))
                forms.add(ConjugationItem("Polite Continuous", if (word == "行く") "行っています" else "いっています", "いっています", formatMeaning(meaning, "polite continuous")))
                forms.add(ConjugationItem("Polite Past Continuous", if (word == "行く") "行っていました" else "いっていました", "いっていました", formatMeaning(meaning, "polite past continuous")))
                forms.add(ConjugationItem("Request", if (word == "行く") "行ってください" else "いってください", "いってください", formatMeaning(meaning, "request")))
            }
            else -> {}
        }

        return forms
    }

    private fun generateConditionalForms(
        word: String,
        stem: String,
        stemReading: String,
        baseReading: String,
        verbType: VerbType,
        meaning: String,
        hasKanji: Boolean
    ): List<ConjugationItem> {
        val forms = mutableListOf<ConjugationItem>()

        when (verbType) {
            VerbType.ICHIDAN -> {
                forms.add(ConjugationItem("Ba-form", "${stem}れば", if (hasKanji) "${stemReading}れば" else null, formatMeaning(meaning, "ba-form")))
                forms.add(ConjugationItem("Tara-form", "${stem}たら", if (hasKanji) "${stemReading}たら" else null, formatMeaning(meaning, "tara-form")))
                forms.add(ConjugationItem("Nara-form", "${word}なら", if (word != baseReading) "${baseReading}なら" else null, formatMeaning(meaning, "nara-form")))
                forms.add(ConjugationItem("Negative Conditional", "${stem}なければ", if (hasKanji) "${stemReading}なければ" else null, formatMeaning(meaning, "negative conditional")))
            }
            VerbType.GODAN_K -> {
                forms.add(ConjugationItem("Ba-form", "${stem}けば", if (hasKanji) "${stemReading}けば" else null, formatMeaning(meaning, "ba-form")))
                forms.add(ConjugationItem("Tara-form", "${stem}いたら", if (hasKanji) "${stemReading}いたら" else null, formatMeaning(meaning, "tara-form")))
                forms.add(ConjugationItem("Nara-form", "${word}なら", if (word != baseReading) "${baseReading}なら" else null, formatMeaning(meaning, "nara-form")))
                forms.add(ConjugationItem("Negative Conditional", "${stem}かなければ", if (hasKanji) "${stemReading}かなければ" else null, formatMeaning(meaning, "negative conditional")))
            }
            VerbType.GODAN_S -> {
                forms.add(ConjugationItem("Ba-form", "${stem}せば", if (hasKanji) "${stemReading}せば" else null, formatMeaning(meaning, "ba-form")))
                forms.add(ConjugationItem("Tara-form", "${stem}したら", if (hasKanji) "${stemReading}したら" else null, formatMeaning(meaning, "tara-form")))
                forms.add(ConjugationItem("Nara-form", "${word}なら", if (word != baseReading) "${baseReading}なら" else null, formatMeaning(meaning, "nara-form")))
                forms.add(ConjugationItem("Negative Conditional", "${stem}さなければ", if (hasKanji) "${stemReading}さなければ" else null, formatMeaning(meaning, "negative conditional")))
            }
            VerbType.GODAN_T -> {
                forms.add(ConjugationItem("Ba-form", "${stem}てば", if (hasKanji) "${stemReading}てば" else null, formatMeaning(meaning, "ba-form")))
                forms.add(ConjugationItem("Tara-form", "${stem}ったら", if (hasKanji) "${stemReading}ったら" else null, formatMeaning(meaning, "tara-form")))
                forms.add(ConjugationItem("Nara-form", "${word}なら", if (word != baseReading) "${baseReading}なら" else null, formatMeaning(meaning, "nara-form")))
                forms.add(ConjugationItem("Negative Conditional", "${stem}たなければ", if (hasKanji) "${stemReading}たなければ" else null, formatMeaning(meaning, "negative conditional")))
            }
            VerbType.GODAN_N -> {
                forms.add(ConjugationItem("Ba-form", "${stem}ねば", if (hasKanji) "${stemReading}ねば" else null, formatMeaning(meaning, "ba-form")))
                forms.add(ConjugationItem("Tara-form", "${stem}んだら", if (hasKanji) "${stemReading}んだら" else null, formatMeaning(meaning, "tara-form")))
                forms.add(ConjugationItem("Nara-form", "${word}なら", if (word != baseReading) "${baseReading}なら" else null, formatMeaning(meaning, "nara-form")))
                forms.add(ConjugationItem("Negative Conditional", "${stem}ななければ", if (hasKanji) "${stemReading}ななければ" else null, formatMeaning(meaning, "negative conditional")))
            }
            VerbType.GODAN_B -> {
                forms.add(ConjugationItem("Ba-form", "${stem}べば", if (hasKanji) "${stemReading}べば" else null, formatMeaning(meaning, "ba-form")))
                forms.add(ConjugationItem("Tara-form", "${stem}んだら", if (hasKanji) "${stemReading}んだら" else null, formatMeaning(meaning, "tara-form")))
                forms.add(ConjugationItem("Nara-form", "${word}なら", if (word != baseReading) "${baseReading}なら" else null, formatMeaning(meaning, "nara-form")))
                forms.add(ConjugationItem("Negative Conditional", "${stem}ばなければ", if (hasKanji) "${stemReading}ばなければ" else null, formatMeaning(meaning, "negative conditional")))
            }
            VerbType.GODAN_M -> {
                forms.add(ConjugationItem("Ba-form", "${stem}めば", if (hasKanji) "${stemReading}めば" else null, formatMeaning(meaning, "ba-form")))
                forms.add(ConjugationItem("Tara-form", "${stem}んだら", if (hasKanji) "${stemReading}んだら" else null, formatMeaning(meaning, "tara-form")))
                forms.add(ConjugationItem("Nara-form", "${word}なら", if (word != baseReading) "${baseReading}なら" else null, formatMeaning(meaning, "nara-form")))
                forms.add(ConjugationItem("Negative Conditional", "${stem}まなければ", if (hasKanji) "${stemReading}まなければ" else null, formatMeaning(meaning, "negative conditional")))
            }
            VerbType.GODAN_R -> {
                forms.add(ConjugationItem("Ba-form", "${stem}れば", if (hasKanji) "${stemReading}れば" else null, formatMeaning(meaning, "ba-form")))
                forms.add(ConjugationItem("Tara-form", "${stem}ったら", if (hasKanji) "${stemReading}ったら" else null, formatMeaning(meaning, "tara-form")))
                forms.add(ConjugationItem("Nara-form", "${word}なら", if (word != baseReading) "${baseReading}なら" else null, formatMeaning(meaning, "nara-form")))
                forms.add(ConjugationItem("Negative Conditional", "${stem}らなければ", if (hasKanji) "${stemReading}らなければ" else null, formatMeaning(meaning, "negative conditional")))
            }
            VerbType.GODAN_G -> {
                forms.add(ConjugationItem("Ba-form", "${stem}げば", if (hasKanji) "${stemReading}げば" else null, formatMeaning(meaning, "ba-form")))
                forms.add(ConjugationItem("Tara-form", "${stem}いだら", if (hasKanji) "${stemReading}いだら" else null, formatMeaning(meaning, "tara-form")))
                forms.add(ConjugationItem("Nara-form", "${word}なら", if (word != baseReading) "${baseReading}なら" else null, formatMeaning(meaning, "nara-form")))
                forms.add(ConjugationItem("Negative Conditional", "${stem}がなければ", if (hasKanji) "${stemReading}がなければ" else null, formatMeaning(meaning, "negative conditional")))
            }
            VerbType.GODAN_U -> {
                forms.add(ConjugationItem("Ba-form", "${stem}えば", if (hasKanji) "${stemReading}えば" else null, formatMeaning(meaning, "ba-form")))
                forms.add(ConjugationItem("Tara-form", "${stem}ったら", if (hasKanji) "${stemReading}ったら" else null, formatMeaning(meaning, "tara-form")))
                forms.add(ConjugationItem("Nara-form", "${word}なら", if (word != baseReading) "${baseReading}なら" else null, formatMeaning(meaning, "nara-form")))
                forms.add(ConjugationItem("Negative Conditional", "${stem}わなければ", if (hasKanji) "${stemReading}わなければ" else null, formatMeaning(meaning, "negative conditional")))
            }
            VerbType.SURU_IRREGULAR -> {
                forms.add(ConjugationItem("Ba-form", "${stem}すれば", if (hasKanji) "${stemReading}すれば" else null, formatMeaning(meaning, "ba-form")))
                forms.add(ConjugationItem("Tara-form", "${stem}したら", if (hasKanji) "${stemReading}したら" else null, formatMeaning(meaning, "tara-form")))
                forms.add(ConjugationItem("Nara-form", "${word}なら", if (word != baseReading) "${baseReading}なら" else null, formatMeaning(meaning, "nara-form")))
                forms.add(ConjugationItem("Negative Conditional", "${stem}しなければ", if (hasKanji) "${stemReading}しなければ" else null, formatMeaning(meaning, "negative conditional")))
            }
            VerbType.KURU_IRREGULAR -> {
                val base = if (word == "来る") "来" else ""
                forms.add(ConjugationItem("Ba-form", "${base}れば", "くれば", formatMeaning(meaning, "ba-form")))
                forms.add(ConjugationItem("Tara-form", "${base}たら", "きたら", formatMeaning(meaning, "tara-form")))
                forms.add(ConjugationItem("Nara-form", "${word}なら", "くるなら", formatMeaning(meaning, "nara-form")))
                forms.add(ConjugationItem("Negative Conditional", "${base}なければ", "こなければ", formatMeaning(meaning, "negative conditional")))
            }
            VerbType.IKU_IRREGULAR -> {
                val base = if (word == "行く") "行" else "い"
                forms.add(ConjugationItem("Ba-form", "${base}けば", "いけば", formatMeaning(meaning, "ba-form")))
                forms.add(ConjugationItem("Tara-form", if (word == "行く") "行ったら" else "いったら", "いったら", formatMeaning(meaning, "tara-form")))
                forms.add(ConjugationItem("Nara-form", "${word}なら", if (word != baseReading) "${baseReading}なら" else null, formatMeaning(meaning, "nara-form")))
                forms.add(ConjugationItem("Negative Conditional", "${base}かなければ", "いかなければ", formatMeaning(meaning, "negative conditional")))
            }
            VerbType.ADJECTIVE_I -> {
                forms.add(ConjugationItem("Conditional", "${stem}ければ", if (hasKanji) "${stemReading}ければ" else null, formatMeaning(meaning, "conditional")))
                forms.add(ConjugationItem("Negative Conditional", "${stem}くなければ", if (hasKanji) "${stemReading}くなければ" else null, formatMeaning(meaning, "negative conditional")))
            }
            VerbType.ADJECTIVE_NA -> {
                forms.add(ConjugationItem("Conditional", "${word}なら", if (hasKanji) "${baseReading}なら" else null, formatMeaning(meaning, "conditional")))
                forms.add(ConjugationItem("Negative Conditional", "${word}じゃなければ", if (hasKanji) "${baseReading}じゃなければ" else null, formatMeaning(meaning, "negative conditional")))
            }
            else -> {}
        }

        return forms
    }


    private fun generateVolitionalForms(
        word: String,
        stem: String,
        stemReading: String,
        baseReading: String,
        verbType: VerbType,
        meaning: String,
        hasKanji: Boolean
    ): List<ConjugationItem> {
        val forms = mutableListOf<ConjugationItem>()

        when (verbType) {
            VerbType.ICHIDAN -> {
                forms.add(ConjugationItem("Volitional", "${stem}よう", if (hasKanji) "${stemReading}よう" else null, formatMeaning(meaning, "volitional")))
                forms.add(ConjugationItem("Polite Volitional", "${stem}ましょう", if (hasKanji) "${stemReading}ましょう" else null, formatMeaning(meaning, "polite volitional")))
            }
            VerbType.GODAN_K -> {
                forms.add(ConjugationItem("Volitional", "${stem}こう", if (hasKanji) "${stemReading}こう" else null, formatMeaning(meaning, "volitional")))
                forms.add(ConjugationItem("Polite Volitional", "${stem}きましょう", if (hasKanji) "${stemReading}きましょう" else null, formatMeaning(meaning, "polite volitional")))
            }
            VerbType.GODAN_S -> {
                forms.add(ConjugationItem("Volitional", "${stem}そう", if (hasKanji) "${stemReading}そう" else null, formatMeaning(meaning, "volitional")))
                forms.add(ConjugationItem("Polite Volitional", "${stem}しましょう", if (hasKanji) "${stemReading}しましょう" else null, formatMeaning(meaning, "polite volitional")))
            }
            VerbType.GODAN_T -> {
                forms.add(ConjugationItem("Volitional", "${stem}とう", if (hasKanji) "${stemReading}とう" else null, formatMeaning(meaning, "volitional")))
                forms.add(ConjugationItem("Polite Volitional", "${stem}ちましょう", if (hasKanji) "${stemReading}ちましょう" else null, formatMeaning(meaning, "polite volitional")))
            }
            VerbType.GODAN_N -> {
                forms.add(ConjugationItem("Volitional", "${stem}のう", if (hasKanji) "${stemReading}のう" else null, formatMeaning(meaning, "volitional")))
                forms.add(ConjugationItem("Polite Volitional", "${stem}にましょう", if (hasKanji) "${stemReading}にましょう" else null, formatMeaning(meaning, "polite volitional")))
            }
            VerbType.GODAN_B -> {
                forms.add(ConjugationItem("Volitional", "${stem}ぼう", if (hasKanji) "${stemReading}ぼう" else null, formatMeaning(meaning, "volitional")))
                forms.add(ConjugationItem("Polite Volitional", "${stem}びましょう", if (hasKanji) "${stemReading}びましょう" else null, formatMeaning(meaning, "polite volitional")))
            }
            VerbType.GODAN_M -> {
                forms.add(ConjugationItem("Volitional", "${stem}もう", if (hasKanji) "${stemReading}もう" else null, formatMeaning(meaning, "volitional")))
                forms.add(ConjugationItem("Polite Volitional", "${stem}みましょう", if (hasKanji) "${stemReading}みましょう" else null, formatMeaning(meaning, "polite volitional")))
            }
            VerbType.GODAN_R -> {
                forms.add(ConjugationItem("Volitional", "${stem}ろう", if (hasKanji) "${stemReading}ろう" else null, formatMeaning(meaning, "volitional")))
                forms.add(ConjugationItem("Polite Volitional", "${stem}りましょう", if (hasKanji) "${stemReading}りましょう" else null, formatMeaning(meaning, "polite volitional")))
            }
            VerbType.GODAN_G -> {
                forms.add(ConjugationItem("Volitional", "${stem}ごう", if (hasKanji) "${stemReading}ごう" else null, formatMeaning(meaning, "volitional")))
                forms.add(ConjugationItem("Polite Volitional", "${stem}ぎましょう", if (hasKanji) "${stemReading}ぎましょう" else null, formatMeaning(meaning, "polite volitional")))
            }
            VerbType.GODAN_U -> {
                forms.add(ConjugationItem("Volitional", "${stem}おう", if (hasKanji) "${stemReading}おう" else null, formatMeaning(meaning, "volitional")))
                forms.add(ConjugationItem("Polite Volitional", "${stem}いましょう", if (hasKanji) "${stemReading}いましょう" else null, formatMeaning(meaning, "polite volitional")))
            }
            VerbType.SURU_IRREGULAR -> {
                forms.add(ConjugationItem("Volitional", "${stem}しよう", if (hasKanji) "${stemReading}しよう" else null, formatMeaning(meaning, "volitional")))
                forms.add(ConjugationItem("Polite Volitional", "${stem}しましょう", if (hasKanji) "${stemReading}しましょう" else null, formatMeaning(meaning, "polite volitional")))
            }
            VerbType.KURU_IRREGULAR -> {
                val base = if (word == "来る") "来" else "こ"
                forms.add(ConjugationItem("Volitional", "${base}よう", "こよう", formatMeaning(meaning, "volitional")))
                forms.add(ConjugationItem("Polite Volitional", "${base}ましょう", "きましょう", formatMeaning(meaning, "polite volitional")))
            }
            VerbType.IKU_IRREGULAR -> {
                val base = if (word == "行く") "行" else "い"
                forms.add(ConjugationItem("Volitional", "${base}こう", "いこう", formatMeaning(meaning, "volitional")))
                forms.add(ConjugationItem("Polite Volitional", "${base}きましょう", "いきましょう", formatMeaning(meaning, "polite volitional")))
            }
            else -> {}
        }

        return forms
    }

    private fun generatePotentialForms(
        word: String,
        stem: String,
        stemReading: String,
        baseReading: String,
        verbType: VerbType,
        meaning: String,
        hasKanji: Boolean
    ): List<ConjugationItem> {
        val forms = mutableListOf<ConjugationItem>()

        when (verbType) {
            VerbType.ICHIDAN -> {
                forms.add(ConjugationItem("Potential", "${stem}られる", if (hasKanji) "${stemReading}られる" else null, formatMeaning(meaning, "potential")))
                forms.add(ConjugationItem("Potential Negative", "${stem}られない", if (hasKanji) "${stemReading}られない" else null, formatMeaning(meaning, "potential negative")))
                forms.add(ConjugationItem("Polite Potential", "${stem}られます", if (hasKanji) "${stemReading}られます" else null, formatMeaning(meaning, "polite potential")))
            }
            VerbType.GODAN_K -> {
                forms.add(ConjugationItem("Potential", "${stem}ける", if (hasKanji) "${stemReading}ける" else null, formatMeaning(meaning, "potential")))
                forms.add(ConjugationItem("Potential Negative", "${stem}けない", if (hasKanji) "${stemReading}けない" else null, formatMeaning(meaning, "potential negative")))
                forms.add(ConjugationItem("Polite Potential", "${stem}けます", if (hasKanji) "${stemReading}けます" else null, formatMeaning(meaning, "polite potential")))
            }
            VerbType.GODAN_S -> {
                forms.add(ConjugationItem("Potential", "${stem}せる", if (hasKanji) "${stemReading}せる" else null, formatMeaning(meaning, "potential")))
                forms.add(ConjugationItem("Potential Negative", "${stem}せない", if (hasKanji) "${stemReading}せない" else null, formatMeaning(meaning, "potential negative")))
                forms.add(ConjugationItem("Polite Potential", "${stem}せます", if (hasKanji) "${stemReading}せます" else null, formatMeaning(meaning, "polite potential")))
            }
            VerbType.GODAN_T -> {
                forms.add(ConjugationItem("Potential", "${stem}てる", if (hasKanji) "${stemReading}てる" else null, formatMeaning(meaning, "potential")))
                forms.add(ConjugationItem("Potential Negative", "${stem}てない", if (hasKanji) "${stemReading}てない" else null, formatMeaning(meaning, "potential negative")))
                forms.add(ConjugationItem("Polite Potential", "${stem}てます", if (hasKanji) "${stemReading}てます" else null, formatMeaning(meaning, "polite potential")))
            }
            VerbType.GODAN_N -> {
                forms.add(ConjugationItem("Potential", "${stem}ねる", if (hasKanji) "${stemReading}ねる" else null, formatMeaning(meaning, "potential")))
                forms.add(ConjugationItem("Potential Negative", "${stem}ねない", if (hasKanji) "${stemReading}ねない" else null, formatMeaning(meaning, "potential negative")))
                forms.add(ConjugationItem("Polite Potential", "${stem}ねます", if (hasKanji) "${stemReading}ねます" else null, formatMeaning(meaning, "polite potential")))
            }
            VerbType.GODAN_B -> {
                forms.add(ConjugationItem("Potential", "${stem}べる", if (hasKanji) "${stemReading}べる" else null, formatMeaning(meaning, "potential")))
                forms.add(ConjugationItem("Potential Negative", "${stem}べない", if (hasKanji) "${stemReading}べない" else null, formatMeaning(meaning, "potential negative")))
                forms.add(ConjugationItem("Polite Potential", "${stem}べます", if (hasKanji) "${stemReading}べます" else null, formatMeaning(meaning, "polite potential")))
            }
            VerbType.GODAN_M -> {
                forms.add(ConjugationItem("Potential", "${stem}める", if (hasKanji) "${stemReading}める" else null, formatMeaning(meaning, "potential")))
                forms.add(ConjugationItem("Potential Negative", "${stem}めない", if (hasKanji) "${stemReading}めない" else null, formatMeaning(meaning, "potential negative")))
                forms.add(ConjugationItem("Polite Potential", "${stem}めます", if (hasKanji) "${stemReading}めます" else null, formatMeaning(meaning, "polite potential")))
            }
            VerbType.GODAN_R -> {
                forms.add(ConjugationItem("Potential", "${stem}れる", if (hasKanji) "${stemReading}れる" else null, formatMeaning(meaning, "potential")))
                forms.add(ConjugationItem("Potential Negative", "${stem}れない", if (hasKanji) "${stemReading}れない" else null, formatMeaning(meaning, "potential negative")))
                forms.add(ConjugationItem("Polite Potential", "${stem}れます", if (hasKanji) "${stemReading}れます" else null, formatMeaning(meaning, "polite potential")))
            }
            VerbType.GODAN_G -> {
                forms.add(ConjugationItem("Potential", "${stem}げる", if (hasKanji) "${stemReading}げる" else null, formatMeaning(meaning, "potential")))
                forms.add(ConjugationItem("Potential Negative", "${stem}げない", if (hasKanji) "${stemReading}げない" else null, formatMeaning(meaning, "potential negative")))
                forms.add(ConjugationItem("Polite Potential", "${stem}げます", if (hasKanji) "${stemReading}げます" else null, formatMeaning(meaning, "polite potential")))
            }
            VerbType.GODAN_U -> {
                forms.add(ConjugationItem("Potential", "${stem}える", if (hasKanji) "${stemReading}える" else null, formatMeaning(meaning, "potential")))
                forms.add(ConjugationItem("Potential Negative", "${stem}えない", if (hasKanji) "${stemReading}えない" else null, formatMeaning(meaning, "potential negative")))
                forms.add(ConjugationItem("Polite Potential", "${stem}えます", if (hasKanji) "${stemReading}えます" else null, formatMeaning(meaning, "polite potential")))
            }
            VerbType.SURU_IRREGULAR -> {
                forms.add(ConjugationItem("Potential", "${stem}できる", if (hasKanji) "${stemReading}できる" else null, formatMeaning(meaning, "potential")))
                forms.add(ConjugationItem("Potential Negative", "${stem}できない", if (hasKanji) "${stemReading}できない" else null, formatMeaning(meaning, "potential negative")))
                forms.add(ConjugationItem("Polite Potential", "${stem}できます", if (hasKanji) "${stemReading}できます" else null, formatMeaning(meaning, "polite potential")))
            }
            VerbType.KURU_IRREGULAR -> {
                val base = if (word == "来る") "来" else "こ"
                forms.add(ConjugationItem("Potential", "${base}られる", "こられる", formatMeaning(meaning, "potential")))
                forms.add(ConjugationItem("Potential Negative", "${base}られない", "こられない", formatMeaning(meaning, "potential negative")))
                forms.add(ConjugationItem("Polite Potential", "${base}られます", "こられます", formatMeaning(meaning, "polite potential")))
            }
            VerbType.IKU_IRREGULAR -> {
                val base = if (word == "行く") "行" else "い"
                forms.add(ConjugationItem("Potential", "${base}ける", "いける", formatMeaning(meaning, "potential")))
                forms.add(ConjugationItem("Potential Negative", "${base}けない", "いけない", formatMeaning(meaning, "potential negative")))
                forms.add(ConjugationItem("Polite Potential", "${base}けます", "いけます", formatMeaning(meaning, "polite potential")))
            }
            else -> {}
        }

        return forms
    }

    private fun generatePassiveForms(
        word: String,
        stem: String,
        stemReading: String,
        baseReading: String,
        verbType: VerbType,
        meaning: String,
        hasKanji: Boolean
    ): List<ConjugationItem> {
        val forms = mutableListOf<ConjugationItem>()

        when (verbType) {
            VerbType.ICHIDAN -> {
                forms.add(ConjugationItem("Passive", "${stem}られる", if (hasKanji) "${stemReading}られる" else null, formatMeaning(meaning, "passive")))
                forms.add(ConjugationItem("Passive Past", "${stem}られた", if (hasKanji) "${stemReading}られた" else null, formatMeaning(meaning, "passive past")))
                forms.add(ConjugationItem("Passive Negative", "${stem}られない", if (hasKanji) "${stemReading}られない" else null, formatMeaning(meaning, "passive negative")))
            }
            VerbType.GODAN_K -> {
                forms.add(ConjugationItem("Passive", "${stem}かれる", if (hasKanji) "${stemReading}かれる" else null, formatMeaning(meaning, "passive")))
                forms.add(ConjugationItem("Passive Past", "${stem}かれた", if (hasKanji) "${stemReading}かれた" else null, formatMeaning(meaning, "passive past")))
                forms.add(ConjugationItem("Passive Negative", "${stem}かれない", if (hasKanji) "${stemReading}かれない" else null, formatMeaning(meaning, "passive negative")))
            }
            VerbType.GODAN_S -> {
                forms.add(ConjugationItem("Passive", "${stem}される", if (hasKanji) "${stemReading}される" else null, formatMeaning(meaning, "passive")))
                forms.add(ConjugationItem("Passive Past", "${stem}された", if (hasKanji) "${stemReading}された" else null, formatMeaning(meaning, "passive past")))
                forms.add(ConjugationItem("Passive Negative", "${stem}されない", if (hasKanji) "${stemReading}されない" else null, formatMeaning(meaning, "passive negative")))
            }
            VerbType.GODAN_T -> {
                forms.add(ConjugationItem("Passive", "${stem}たれる", if (hasKanji) "${stemReading}たれる" else null, formatMeaning(meaning, "passive")))
                forms.add(ConjugationItem("Passive Past", "${stem}たれた", if (hasKanji) "${stemReading}たれた" else null, formatMeaning(meaning, "passive past")))
                forms.add(ConjugationItem("Passive Negative", "${stem}たれない", if (hasKanji) "${stemReading}たれない" else null, formatMeaning(meaning, "passive negative")))
            }
            VerbType.GODAN_N -> {
                forms.add(ConjugationItem("Passive", "${stem}なれる", if (hasKanji) "${stemReading}なれる" else null, formatMeaning(meaning, "passive")))
                forms.add(ConjugationItem("Passive Past", "${stem}なれた", if (hasKanji) "${stemReading}なれた" else null, formatMeaning(meaning, "passive past")))
                forms.add(ConjugationItem("Passive Negative", "${stem}なれない", if (hasKanji) "${stemReading}なれない" else null, formatMeaning(meaning, "passive negative")))
            }
            VerbType.GODAN_B -> {
                forms.add(ConjugationItem("Passive", "${stem}ばれる", if (hasKanji) "${stemReading}ばれる" else null, formatMeaning(meaning, "passive")))
                forms.add(ConjugationItem("Passive Past", "${stem}ばれた", if (hasKanji) "${stemReading}ばれた" else null, formatMeaning(meaning, "passive past")))
                forms.add(ConjugationItem("Passive Negative", "${stem}ばれない", if (hasKanji) "${stemReading}ばれない" else null, formatMeaning(meaning, "passive negative")))
            }
            VerbType.GODAN_M -> {
                forms.add(ConjugationItem("Passive", "${stem}まれる", if (hasKanji) "${stemReading}まれる" else null, formatMeaning(meaning, "passive")))
                forms.add(ConjugationItem("Passive Past", "${stem}まれた", if (hasKanji) "${stemReading}まれた" else null, formatMeaning(meaning, "passive past")))
                forms.add(ConjugationItem("Passive Negative", "${stem}まれない", if (hasKanji) "${stemReading}まれない" else null, formatMeaning(meaning, "passive negative")))
            }
            VerbType.GODAN_R -> {
                forms.add(ConjugationItem("Passive", "${stem}られる", if (hasKanji) "${stemReading}られる" else null, formatMeaning(meaning, "passive")))
                forms.add(ConjugationItem("Passive Past", "${stem}られた", if (hasKanji) "${stemReading}られた" else null, formatMeaning(meaning, "passive past")))
                forms.add(ConjugationItem("Passive Negative", "${stem}られない", if (hasKanji) "${stemReading}られない" else null, formatMeaning(meaning, "passive negative")))
            }
            VerbType.GODAN_G -> {
                forms.add(ConjugationItem("Passive", "${stem}がれる", if (hasKanji) "${stemReading}がれる" else null, formatMeaning(meaning, "passive")))
                forms.add(ConjugationItem("Passive Past", "${stem}がれた", if (hasKanji) "${stemReading}がれた" else null, formatMeaning(meaning, "passive past")))
                forms.add(ConjugationItem("Passive Negative", "${stem}がれない", if (hasKanji) "${stemReading}がれない" else null, formatMeaning(meaning, "passive negative")))
            }
            VerbType.GODAN_U -> {
                forms.add(ConjugationItem("Passive", "${stem}われる", if (hasKanji) "${stemReading}われる" else null, formatMeaning(meaning, "passive")))
                forms.add(ConjugationItem("Passive Past", "${stem}われた", if (hasKanji) "${stemReading}われた" else null, formatMeaning(meaning, "passive past")))
                forms.add(ConjugationItem("Passive Negative", "${stem}われない", if (hasKanji) "${stemReading}われない" else null, formatMeaning(meaning, "passive negative")))
            }
            VerbType.SURU_IRREGULAR -> {
                forms.add(ConjugationItem("Passive", "${stem}される", if (hasKanji) "${stemReading}される" else null, formatMeaning(meaning, "passive")))
                forms.add(ConjugationItem("Passive Past", "${stem}された", if (hasKanji) "${stemReading}された" else null, formatMeaning(meaning, "passive past")))
                forms.add(ConjugationItem("Passive Negative", "${stem}されない", if (hasKanji) "${stemReading}されない" else null, formatMeaning(meaning, "passive negative")))
            }
            VerbType.KURU_IRREGULAR -> {
                val base = if (word == "来る") "来" else "こ"
                forms.add(ConjugationItem("Passive", "${base}られる", "こられる", formatMeaning(meaning, "causative-passive")))
                forms.add(ConjugationItem("Passive Past", "${base}られた", "こられた", formatMeaning(meaning, "causative-passive past")))
                forms.add(ConjugationItem("Passive Negative", "${base}られない", "こられない", formatMeaning(meaning, "causative-passive negative")))
            }
            VerbType.IKU_IRREGULAR -> {
                val base = if (word == "行く") "行" else "い"
                forms.add(ConjugationItem("Passive", "${base}かれる", "いかれる", formatMeaning(meaning, "passive")))
                forms.add(ConjugationItem("Passive Past", "${base}かれた", "いかれた", formatMeaning(meaning, "passive past")))
                forms.add(ConjugationItem("Passive Negative", "${base}かれない", "いかれない", formatMeaning(meaning, "passive negative")))
            }
            else -> {}
        }

        return forms
    }

    private fun generateCausativeForms(
        word: String,
        stem: String,
        stemReading: String,
        baseReading: String,
        verbType: VerbType,
        meaning: String,
        hasKanji: Boolean
    ): List<ConjugationItem> {
        val forms = mutableListOf<ConjugationItem>()

        when (verbType) {
            VerbType.ICHIDAN -> {
                forms.add(ConjugationItem("Causative", "${stem}させる", if (hasKanji) "${stemReading}させる" else null, formatMeaning(meaning, "causative")))
                forms.add(ConjugationItem("Causative Past", "${stem}させた", if (hasKanji) "${stemReading}させた" else null, formatMeaning(meaning, "causative past")))
                forms.add(ConjugationItem("Causative Negative", "${stem}させない", if (hasKanji) "${stemReading}させない" else null, formatMeaning(meaning, "causative negative")))
                forms.add(ConjugationItem("Polite Causative", "${stem}させます", if (hasKanji) "${stemReading}させます" else null, formatMeaning(meaning, "polite causative")))
            }
            VerbType.GODAN_K -> {
                forms.add(ConjugationItem("Causative", "${stem}かせる", if (hasKanji) "${stemReading}かせる" else null, formatMeaning(meaning, "causative")))
                forms.add(ConjugationItem("Causative Past", "${stem}かせた", if (hasKanji) "${stemReading}かせた" else null, formatMeaning(meaning, "causative past")))
                forms.add(ConjugationItem("Causative Negative", "${stem}かせない", if (hasKanji) "${stemReading}かせない" else null, formatMeaning(meaning, "causative negative")))
                forms.add(ConjugationItem("Polite Causative", "${stem}かせます", if (hasKanji) "${stemReading}かせます" else null, formatMeaning(meaning, "polite causative")))
                forms.add(ConjugationItem("Causative (short)", "${stem}かす", if (hasKanji) "${stemReading}かす" else null, formatMeaning(meaning, "causative (short)")))
            }
            VerbType.GODAN_S -> {
                forms.add(ConjugationItem("Causative", "${stem}させる", if (hasKanji) "${stemReading}させる" else null, formatMeaning(meaning, "causative")))
                forms.add(ConjugationItem("Causative Past", "${stem}させた", if (hasKanji) "${stemReading}させた" else null, formatMeaning(meaning, "causative past")))
                forms.add(ConjugationItem("Causative Negative", "${stem}させない", if (hasKanji) "${stemReading}させない" else null, formatMeaning(meaning, "causative negative")))
                forms.add(ConjugationItem("Polite Causative", "${stem}させます", if (hasKanji) "${stemReading}させます" else null, formatMeaning(meaning, "polite causative")))
                forms.add(ConjugationItem("Causative (short)", "${stem}さす", if (hasKanji) "${stemReading}さす" else null, formatMeaning(meaning, "causative (short)")))
            }
            VerbType.GODAN_T -> {
                forms.add(ConjugationItem("Causative", "${stem}たせる", if (hasKanji) "${stemReading}たせる" else null, formatMeaning(meaning, "causative")))
                forms.add(ConjugationItem("Causative Past", "${stem}たせた", if (hasKanji) "${stemReading}たせた" else null, formatMeaning(meaning, "causative past")))
                forms.add(ConjugationItem("Causative Negative", "${stem}たせない", if (hasKanji) "${stemReading}たせない" else null, formatMeaning(meaning, "causative negative")))
                forms.add(ConjugationItem("Polite Causative", "${stem}たせます", if (hasKanji) "${stemReading}たせます" else null, formatMeaning(meaning, "polite causative")))
                forms.add(ConjugationItem("Causative (short)", "${stem}たす", if (hasKanji) "${stemReading}たす" else null, formatMeaning(meaning, "causative (short)")))
            }
            VerbType.GODAN_N -> {
                forms.add(ConjugationItem("Causative", "${stem}なせる", if (hasKanji) "${stemReading}なせる" else null, formatMeaning(meaning, "causative")))
                forms.add(ConjugationItem("Causative Past", "${stem}なせた", if (hasKanji) "${stemReading}なせた" else null, formatMeaning(meaning, "causative past")))
                forms.add(ConjugationItem("Causative Negative", "${stem}なせない", if (hasKanji) "${stemReading}なせない" else null, formatMeaning(meaning, "causative negative")))
                forms.add(ConjugationItem("Polite Causative", "${stem}なせます", if (hasKanji) "${stemReading}なせます" else null, formatMeaning(meaning, "polite causative")))
                forms.add(ConjugationItem("Causative (short)", "${stem}なす", if (hasKanji) "${stemReading}なす" else null, formatMeaning(meaning, "causative (short)")))
            }
            VerbType.GODAN_B -> {
                forms.add(ConjugationItem("Causative", "${stem}ばせる", if (hasKanji) "${stemReading}ばせる" else null, formatMeaning(meaning, "causative")))
                forms.add(ConjugationItem("Causative Past", "${stem}ばせた", if (hasKanji) "${stemReading}ばせた" else null, formatMeaning(meaning, "causative past")))
                forms.add(ConjugationItem("Causative Negative", "${stem}ばせない", if (hasKanji) "${stemReading}ばせない" else null, formatMeaning(meaning, "causative negative")))
                forms.add(ConjugationItem("Polite Causative", "${stem}ばせます", if (hasKanji) "${stemReading}ばせます" else null, formatMeaning(meaning, "polite causative")))
                forms.add(ConjugationItem("Causative (short)", "${stem}ばす", if (hasKanji) "${stemReading}ばす" else null, formatMeaning(meaning, "causative (short)")))
            }
            VerbType.GODAN_M -> {
                forms.add(ConjugationItem("Causative", "${stem}ませる", if (hasKanji) "${stemReading}ませる" else null, formatMeaning(meaning, "causative")))
                forms.add(ConjugationItem("Causative Past", "${stem}ませた", if (hasKanji) "${stemReading}ませた" else null, formatMeaning(meaning, "causative past")))
                forms.add(ConjugationItem("Causative Negative", "${stem}ませない", if (hasKanji) "${stemReading}ませない" else null, formatMeaning(meaning, "causative negative")))
                forms.add(ConjugationItem("Polite Causative", "${stem}ませます", if (hasKanji) "${stemReading}ませます" else null, formatMeaning(meaning, "polite causative")))
                forms.add(ConjugationItem("Causative (short)", "${stem}ます", if (hasKanji) "${stemReading}ます" else null, formatMeaning(meaning, "causative (short)")))
            }
            VerbType.GODAN_R -> {
                forms.add(ConjugationItem("Causative", "${stem}らせる", if (hasKanji) "${stemReading}らせる" else null, formatMeaning(meaning, "causative")))
                forms.add(ConjugationItem("Causative Past", "${stem}らせた", if (hasKanji) "${stemReading}らせた" else null, formatMeaning(meaning, "causative past")))
                forms.add(ConjugationItem("Causative Negative", "${stem}らせない", if (hasKanji) "${stemReading}らせない" else null, formatMeaning(meaning, "causative negative")))
                forms.add(ConjugationItem("Polite Causative", "${stem}らせます", if (hasKanji) "${stemReading}らせます" else null, formatMeaning(meaning, "polite causative")))
                forms.add(ConjugationItem("Causative (short)", "${stem}らす", if (hasKanji) "${stemReading}らす" else null, formatMeaning(meaning, "causative (short)")))
            }
            VerbType.GODAN_G -> {
                forms.add(ConjugationItem("Causative", "${stem}がせる", if (hasKanji) "${stemReading}がせる" else null, formatMeaning(meaning, "causative")))
                forms.add(ConjugationItem("Causative Past", "${stem}がせた", if (hasKanji) "${stemReading}がせた" else null, formatMeaning(meaning, "causative past")))
                forms.add(ConjugationItem("Causative Negative", "${stem}がせない", if (hasKanji) "${stemReading}がせない" else null, formatMeaning(meaning, "causative negative")))
                forms.add(ConjugationItem("Polite Causative", "${stem}がせます", if (hasKanji) "${stemReading}がせます" else null, formatMeaning(meaning, "polite causative")))
                forms.add(ConjugationItem("Causative (short)", "${stem}がす", if (hasKanji) "${stemReading}がす" else null, formatMeaning(meaning, "causative (short)")))
            }
            VerbType.GODAN_U -> {
                forms.add(ConjugationItem("Causative", "${stem}わせる", if (hasKanji) "${stemReading}わせる" else null, formatMeaning(meaning, "causative")))
                forms.add(ConjugationItem("Causative Past", "${stem}わせた", if (hasKanji) "${stemReading}わせた" else null, formatMeaning(meaning, "causative past")))
                forms.add(ConjugationItem("Causative Negative", "${stem}わせない", if (hasKanji) "${stemReading}わせない" else null, formatMeaning(meaning, "causative negative")))
                forms.add(ConjugationItem("Polite Causative", "${stem}わせます", if (hasKanji) "${stemReading}わせます" else null, formatMeaning(meaning, "polite causative")))
                forms.add(ConjugationItem("Causative (short)", "${stem}わす", if (hasKanji) "${stemReading}わす" else null, formatMeaning(meaning, "causative (short)")))
            }
            VerbType.SURU_IRREGULAR -> {
                forms.add(ConjugationItem("Causative", "${stem}させる", if (hasKanji) "${stemReading}させる" else null, formatMeaning(meaning, "causative")))
                forms.add(ConjugationItem("Causative Past", "${stem}させた", if (hasKanji) "${stemReading}させた" else null, formatMeaning(meaning, "causative past")))
                forms.add(ConjugationItem("Causative Negative", "${stem}させない", if (hasKanji) "${stemReading}させない" else null, formatMeaning(meaning, "causative negative")))
                forms.add(ConjugationItem("Polite Causative", "${stem}させます", if (hasKanji) "${stemReading}させます" else null, formatMeaning(meaning, "polite causative")))
            }
            VerbType.KURU_IRREGULAR -> {
                val base = if (word == "来る") "来" else "こ"
                forms.add(ConjugationItem("Causative", "${base}させる", "こさせる", formatMeaning(meaning, "causative")))
                forms.add(ConjugationItem("Causative Past", "${base}させた", "こさせた", formatMeaning(meaning, "causative past")))
                forms.add(ConjugationItem("Causative Negative", "${base}させない", "こさせない", formatMeaning(meaning, "causative negative")))
                forms.add(ConjugationItem("Polite Causative", "${base}させます", "こさせます", formatMeaning(meaning, "polite causative")))
                forms.add(ConjugationItem("Causative (short)", "${stem}かす", if (hasKanji) "${stemReading}かす" else null, formatMeaning(meaning, "causative (short)")))
            }
            VerbType.IKU_IRREGULAR -> {
                val base = if (word == "行く") "行" else "い"
                forms.add(ConjugationItem("Causative", "${base}かせる", "いかせる", formatMeaning(meaning, "causative")))
                forms.add(ConjugationItem("Causative Past", "${base}かせた", "いかせた", formatMeaning(meaning, "causative past")))
                forms.add(ConjugationItem("Causative Negative", "${base}かせない", "いかせない", formatMeaning(meaning, "causative negative")))
                forms.add(ConjugationItem("Polite Causative", "${base}かせます", "いかせます", formatMeaning(meaning, "polite causative")))
            }
            else -> {}
        }

        return forms
    }

    private fun generateCausativePassiveForms(
        word: String,
        stem: String,
        stemReading: String,
        baseReading: String,
        verbType: VerbType,
        meaning: String,
        hasKanji: Boolean
    ): List<ConjugationItem> {
        val forms = mutableListOf<ConjugationItem>()

        when (verbType) {
            VerbType.ICHIDAN -> {
                forms.add(ConjugationItem("Causative-Passive", "${stem}させられる", if (hasKanji) "${stemReading}させられる" else null, formatMeaning(meaning, "causative-passive")))
                forms.add(ConjugationItem("Causative-Passive Past", "${stem}させられた", if (hasKanji) "${stemReading}させられた" else null, formatMeaning(meaning, "causative-passive past")))
                forms.add(ConjugationItem("Causative-Passive Negative", "${stem}させられない", if (hasKanji) "${stemReading}させられない" else null, formatMeaning(meaning, "causative-passive negative")))
            }
            VerbType.GODAN_K -> {
                forms.add(ConjugationItem("Causative-Passive", "${stem}かされる", if (hasKanji) "${stemReading}かされる" else null, formatMeaning(meaning, "causative-passive")))
                forms.add(ConjugationItem("Causative-Passive Past", "${stem}かされた", if (hasKanji) "${stemReading}かされた" else null, formatMeaning(meaning, "causative-passive past")))
                forms.add(ConjugationItem("Causative-Passive Negative", "${stem}かされない", if (hasKanji) "${stemReading}かされない" else null, formatMeaning(meaning, "causative-passive negative")))
            }
            VerbType.GODAN_S -> {
                forms.add(ConjugationItem("Causative-Passive", "${stem}させられる", if (hasKanji) "${stemReading}させられる" else null, formatMeaning(meaning, "causative-passive")))
                forms.add(ConjugationItem("Causative-Passive Past", "${stem}させられた", if (hasKanji) "${stemReading}させられた" else null, formatMeaning(meaning, "causative-passive past")))
                forms.add(ConjugationItem("Causative-Passive Negative", "${stem}させられない", if (hasKanji) "${stemReading}させられない" else null, formatMeaning(meaning, "causative-passive negative")))
            }
            VerbType.GODAN_T -> {
                forms.add(ConjugationItem("Causative-Passive", "${stem}たされる", if (hasKanji) "${stemReading}たされる" else null, formatMeaning(meaning, "causative-passive")))
                forms.add(ConjugationItem("Causative-Passive Past", "${stem}たされた", if (hasKanji) "${stemReading}たされた" else null, formatMeaning(meaning, "causative-passive past")))
                forms.add(ConjugationItem("Causative-Passive Negative", "${stem}たされない", if (hasKanji) "${stemReading}たされない" else null, formatMeaning(meaning, "causative-passive negative")))
            }
            VerbType.GODAN_N -> {
                forms.add(ConjugationItem("Causative-Passive", "${stem}なされる", if (hasKanji) "${stemReading}なされる" else null, formatMeaning(meaning, "causative-passive")))
                forms.add(ConjugationItem("Causative-Passive Past", "${stem}なされた", if (hasKanji) "${stemReading}なされた" else null, formatMeaning(meaning, "causative-passive past")))
                forms.add(ConjugationItem("Causative-Passive Negative", "${stem}なされない", if (hasKanji) "${stemReading}なされない" else null, formatMeaning(meaning, "causative-passive negative")))
            }
            VerbType.GODAN_B -> {
                forms.add(ConjugationItem("Causative-Passive", "${stem}ばされる", if (hasKanji) "${stemReading}ばされる" else null, formatMeaning(meaning, "causative-passive")))
                forms.add(ConjugationItem("Causative-Passive Past", "${stem}ばされた", if (hasKanji) "${stemReading}ばされた" else null, formatMeaning(meaning, "causative-passive past")))
                forms.add(ConjugationItem("Causative-Passive Negative", "${stem}ばされない", if (hasKanji) "${stemReading}ばされない" else null, formatMeaning(meaning, "causative-passive negative")))
            }
            VerbType.GODAN_M -> {
                forms.add(ConjugationItem("Causative-Passive", "${stem}まされる", if (hasKanji) "${stemReading}まされる" else null, formatMeaning(meaning, "causative-passive")))
                forms.add(ConjugationItem("Causative-Passive Past", "${stem}まされた", if (hasKanji) "${stemReading}まされた" else null, formatMeaning(meaning, "causative-passive past")))
                forms.add(ConjugationItem("Causative-Passive Negative", "${stem}まされない", if (hasKanji) "${stemReading}まされない" else null, formatMeaning(meaning, "causative-passive negative")))
            }
            VerbType.GODAN_R -> {
                forms.add(ConjugationItem("Causative-Passive", "${stem}らされる", if (hasKanji) "${stemReading}らされる" else null, formatMeaning(meaning, "causative-passive")))
                forms.add(ConjugationItem("Causative-Passive Past", "${stem}らされた", if (hasKanji) "${stemReading}らされた" else null, formatMeaning(meaning, "causative-passive past")))
                forms.add(ConjugationItem("Causative-Passive Negative", "${stem}らされない", if (hasKanji) "${stemReading}らされない" else null, formatMeaning(meaning, "causative-passive negative")))
            }
            VerbType.GODAN_G -> {
                forms.add(ConjugationItem("Causative-Passive", "${stem}がされる", if (hasKanji) "${stemReading}がされる" else null, formatMeaning(meaning, "causative-passive")))
                forms.add(ConjugationItem("Causative-Passive Past", "${stem}がされた", if (hasKanji) "${stemReading}がされた" else null, formatMeaning(meaning, "causative-passive past")))
                forms.add(ConjugationItem("Causative-Passive Negative", "${stem}がされない", if (hasKanji) "${stemReading}がされない" else null, formatMeaning(meaning, "causative-passive negative")))
            }
            VerbType.GODAN_U -> {
                forms.add(ConjugationItem("Causative-Passive", "${stem}わされる", if (hasKanji) "${stemReading}わされる" else null, formatMeaning(meaning, "causative-passive")))
                forms.add(ConjugationItem("Causative-Passive Past", "${stem}わされた", if (hasKanji) "${stemReading}わされた" else null, formatMeaning(meaning, "causative-passive past")))
                forms.add(ConjugationItem("Causative-Passive Negative", "${stem}わされない", if (hasKanji) "${stemReading}わされない" else null, formatMeaning(meaning, "causative-passive negative")))
            }
            VerbType.SURU_IRREGULAR -> {
                forms.add(ConjugationItem("Causative-Passive", "${stem}させられる", if (hasKanji) "${stemReading}させられる" else null, formatMeaning(meaning, "causative-passive")))
                forms.add(ConjugationItem("Causative-Passive Past", "${stem}させられた", if (hasKanji) "${stemReading}させられた" else null, formatMeaning(meaning, "causative-passive past")))
                forms.add(ConjugationItem("Causative-Passive Negative", "${stem}させられない", if (hasKanji) "${stemReading}させられない" else null, formatMeaning(meaning, "causative-passive negative")))
            }
            VerbType.KURU_IRREGULAR -> {
                val base = if (word == "来る") "来" else "こ"
                forms.add(ConjugationItem("Causative-Passive", "${base}させられる", "こさせられる", formatMeaning(meaning, "causative-passive")))
                forms.add(ConjugationItem("Causative-Passive Past", "${base}させられた", "こさせられた", formatMeaning(meaning, "causative-passive past")))
                forms.add(ConjugationItem("Causative-Passive Negative", "${base}させられない", "こさせられない", formatMeaning(meaning, "causative-passive negative")))
            }
            VerbType.IKU_IRREGULAR -> {
                val base = if (word == "行く") "行" else "い"
                forms.add(ConjugationItem("Causative-Passive", "${base}かされる", "いかされる", formatMeaning(meaning, "causative-passive")))
                forms.add(ConjugationItem("Causative-Passive Past", "${base}かされた", "いかされた", formatMeaning(meaning, "causative-passive past")))
                forms.add(ConjugationItem("Causative-Passive Negative", "${base}かされない", "いかされない", formatMeaning(meaning, "causative-passive negative")))
            }
            else -> {}
        }

        return forms
    }

    private fun generateImperativeForms(
        word: String,
        stem: String,
        stemReading: String,
        baseReading: String,
        verbType: VerbType,
        meaning: String,
        hasKanji: Boolean
    ): List<ConjugationItem> {
        val forms = mutableListOf<ConjugationItem>()

        when (verbType) {
            VerbType.ICHIDAN -> {
                forms.add(ConjugationItem("Imperative", "${stem}ろ", if (hasKanji) "${stemReading}ろ" else null, formatMeaning(meaning, "imperative")))
                forms.add(ConjugationItem("Imperative (formal)", "${stem}よ", if (hasKanji) "${stemReading}よ" else null, formatMeaning(meaning, "imperative (formal)")))
                forms.add(ConjugationItem("Prohibitive", "${word}な", if (word != baseReading) "${baseReading}な" else null, formatMeaning(meaning, "prohibitive")))
                forms.add(ConjugationItem("Polite Command", "${stem}なさい", if (hasKanji) "${stemReading}なさい" else null, formatMeaning(meaning, "polite command")))
                forms.add(ConjugationItem("Formal Command", "${stem}たまえ", if (hasKanji) "${stemReading}たまえ" else null, formatMeaning(meaning, "formal command")))
            }
            VerbType.GODAN_K -> {
                forms.add(ConjugationItem("Imperative", "${stem}け", if (hasKanji) "${stemReading}け" else null, formatMeaning(meaning, "imperative")))
                forms.add(ConjugationItem("Prohibitive", "${word}な", if (word != baseReading) "${baseReading}な" else null, formatMeaning(meaning, "prohibitive")))
                forms.add(ConjugationItem("Polite Command", "${stem}きなさい", if (hasKanji) "${stemReading}きなさい" else null, formatMeaning(meaning, "polite command")))
                forms.add(ConjugationItem("Formal Command", "${stem}きたまえ", if (hasKanji) "${stemReading}きたまえ" else null, formatMeaning(meaning, "formal command")))
            }
            VerbType.GODAN_S -> {
                forms.add(ConjugationItem("Imperative", "${stem}せ", if (hasKanji) "${stemReading}せ" else null, formatMeaning(meaning, "imperative")))
                forms.add(ConjugationItem("Prohibitive", "${word}な", if (word != baseReading) "${baseReading}な" else null, formatMeaning(meaning, "prohibitive")))
                forms.add(ConjugationItem("Polite Command", "${stem}しなさい", if (hasKanji) "${stemReading}しなさい" else null, formatMeaning(meaning, "polite command")))
                forms.add(ConjugationItem("Formal Command", "${stem}したまえ", if (hasKanji) "${stemReading}したまえ" else null, formatMeaning(meaning, "formal command")))
            }
            VerbType.GODAN_T -> {
                forms.add(ConjugationItem("Imperative", "${stem}て", if (hasKanji) "${stemReading}て" else null, formatMeaning(meaning, "imperative")))
                forms.add(ConjugationItem("Prohibitive", "${word}な", if (word != baseReading) "${baseReading}な" else null, formatMeaning(meaning, "prohibitive")))
                forms.add(ConjugationItem("Polite Command", "${stem}ちなさい", if (hasKanji) "${stemReading}ちなさい" else null, formatMeaning(meaning, "polite command")))
                forms.add(ConjugationItem("Formal Command", "${stem}ちたまえ", if (hasKanji) "${stemReading}ちたまえ" else null, formatMeaning(meaning, "formal command")))
            }
            VerbType.GODAN_N -> {
                forms.add(ConjugationItem("Imperative", "${stem}ね", if (hasKanji) "${stemReading}ね" else null, formatMeaning(meaning, "imperative")))
                forms.add(ConjugationItem("Prohibitive", "${word}な", if (word != baseReading) "${baseReading}な" else null, formatMeaning(meaning, "prohibitive")))
                forms.add(ConjugationItem("Polite Command", "${stem}になさい", if (hasKanji) "${stemReading}になさい" else null, formatMeaning(meaning, "polite command")))
                forms.add(ConjugationItem("Formal Command", "${stem}にたまえ", if (hasKanji) "${stemReading}にたまえ" else null, formatMeaning(meaning, "formal command")))
            }
            VerbType.GODAN_B -> {
                forms.add(ConjugationItem("Imperative", "${stem}べ", if (hasKanji) "${stemReading}べ" else null, formatMeaning(meaning, "imperative")))
                forms.add(ConjugationItem("Prohibitive", "${word}な", if (word != baseReading) "${baseReading}な" else null, formatMeaning(meaning, "prohibitive")))
                forms.add(ConjugationItem("Polite Command", "${stem}びなさい", if (hasKanji) "${stemReading}びなさい" else null, formatMeaning(meaning, "polite command")))
                forms.add(ConjugationItem("Formal Command", "${stem}びたまえ", if (hasKanji) "${stemReading}びたまえ" else null, formatMeaning(meaning, "formal command")))
            }
            VerbType.GODAN_M -> {
                forms.add(ConjugationItem("Imperative", "${stem}め", if (hasKanji) "${stemReading}め" else null, formatMeaning(meaning, "imperative")))
                forms.add(ConjugationItem("Prohibitive", "${word}な", if (word != baseReading) "${baseReading}な" else null, formatMeaning(meaning, "prohibitive")))
                forms.add(ConjugationItem("Polite Command", "${stem}みなさい", if (hasKanji) "${stemReading}みなさい" else null, formatMeaning(meaning, "polite command")))
                forms.add(ConjugationItem("Formal Command", "${stem}みたまえ", if (hasKanji) "${stemReading}みたまえ" else null, formatMeaning(meaning, "formal command")))
            }
            VerbType.GODAN_R -> {
                forms.add(ConjugationItem("Imperative", "${stem}れ", if (hasKanji) "${stemReading}れ" else null, formatMeaning(meaning, "imperative")))
                forms.add(ConjugationItem("Prohibitive", "${word}な", if (word != baseReading) "${baseReading}な" else null, formatMeaning(meaning, "prohibitive")))
                forms.add(ConjugationItem("Polite Command", "${stem}りなさい", if (hasKanji) "${stemReading}りなさい" else null, formatMeaning(meaning, "polite command")))
                forms.add(ConjugationItem("Formal Command", "${stem}りたまえ", if (hasKanji) "${stemReading}りたまえ" else null, formatMeaning(meaning, "formal command")))
            }
            VerbType.GODAN_G -> {
                forms.add(ConjugationItem("Imperative", "${stem}げ", if (hasKanji) "${stemReading}げ" else null, formatMeaning(meaning, "imperative")))
                forms.add(ConjugationItem("Prohibitive", "${word}な", if (word != baseReading) "${baseReading}な" else null, formatMeaning(meaning, "prohibitive")))
                forms.add(ConjugationItem("Polite Command", "${stem}ぎなさい", if (hasKanji) "${stemReading}ぎなさい" else null, formatMeaning(meaning, "polite command")))
                forms.add(ConjugationItem("Formal Command", "${stem}ぎたまえ", if (hasKanji) "${stemReading}ぎたまえ" else null, formatMeaning(meaning, "formal command")))
            }
            VerbType.GODAN_U -> {
                forms.add(ConjugationItem("Imperative", "${stem}え", if (hasKanji) "${stemReading}え" else null, formatMeaning(meaning, "imperative")))
                forms.add(ConjugationItem("Prohibitive", "${word}な", if (word != baseReading) "${baseReading}な" else null, formatMeaning(meaning, "prohibitive")))
                forms.add(ConjugationItem("Polite Command", "${stem}いなさい", if (hasKanji) "${stemReading}いなさい" else null, formatMeaning(meaning, "polite command")))
                forms.add(ConjugationItem("Formal Command", "${stem}いたまえ", if (hasKanji) "${stemReading}いたまえ" else null, formatMeaning(meaning, "formal command")))
            }
            VerbType.SURU_IRREGULAR -> {
                forms.add(ConjugationItem("Imperative", "${stem}しろ", if (hasKanji) "${stemReading}しろ" else null, formatMeaning(meaning, "imperative")))
                forms.add(ConjugationItem("Imperative (formal)", "${stem}せよ", if (hasKanji) "${stemReading}せよ" else null, formatMeaning(meaning, "imperative (formal)")))
                forms.add(ConjugationItem("Prohibitive", "${word}な", if (word != baseReading) "${baseReading}な" else null, formatMeaning(meaning, "prohibitive")))
                forms.add(ConjugationItem("Polite Command", "${stem}しなさい", if (hasKanji) "${stemReading}しなさい" else null, formatMeaning(meaning, "polite command")))
                forms.add(ConjugationItem("Formal Command", "${stem}したまえ", if (hasKanji) "${stemReading}したまえ" else null, formatMeaning(meaning, "formal command")))
            }
            VerbType.KURU_IRREGULAR -> {
                val base = if (word == "来る") "来" else "こ"
                forms.add(ConjugationItem("Imperative", "${base}い", "こい", formatMeaning(meaning, "imperative")))
                forms.add(ConjugationItem("Prohibitive", "${word}な", "くるな", formatMeaning(meaning, "prohibitive")))
                forms.add(ConjugationItem("Polite Command", "${base}なさい", "きなさい", formatMeaning(meaning, "polite command")))
                forms.add(ConjugationItem("Formal Command", "${base}たまえ", "きたまえ", formatMeaning(meaning, "formal command")))
            }
            VerbType.IKU_IRREGULAR -> {
                val base = if (word == "行く") "行" else "い"
                forms.add(ConjugationItem("Imperative", "${base}け", "いけ", formatMeaning(meaning, "imperative")))
                forms.add(ConjugationItem("Prohibitive", "${word}な", if (word != baseReading) "${baseReading}な" else null, formatMeaning(meaning, "prohibitive")))
                forms.add(ConjugationItem("Polite Command", "${base}きなさい", "いきなさい", formatMeaning(meaning, "polite command")))
                forms.add(ConjugationItem("Formal Command", "${base}きたまえ", "いきたまえ", formatMeaning(meaning, "formal command")))
            }
            else -> {}
        }

        return forms
    }

    private fun generateDesireForms(
        word: String,
        stem: String,
        stemReading: String,
        baseReading: String,
        verbType: VerbType,
        meaning: String,
        hasKanji: Boolean
    ): List<ConjugationItem> {
        val forms = mutableListOf<ConjugationItem>()

        when (verbType) {
            VerbType.ICHIDAN -> {
                forms.add(ConjugationItem("Want to", "${stem}たい", if (hasKanji) "${stemReading}たい" else null, formatMeaning(meaning, "want to")))
                forms.add(ConjugationItem("Want to (past)", "${stem}たかった", if (hasKanji) "${stemReading}たかった" else null, formatMeaning(meaning, "want to (past)")))
                forms.add(ConjugationItem("Don't want to", "${stem}たくない", if (hasKanji) "${stemReading}たくない" else null, formatMeaning(meaning, "don't want to")))
                forms.add(ConjugationItem("Didn't want to", "${stem}たくなかった", if (hasKanji) "${stemReading}たくなかった" else null, formatMeaning(meaning, "didn't want to")))
                forms.add(ConjugationItem("Shows desire", "${stem}たがる", if (hasKanji) "${stemReading}たがる" else null, formatMeaning(meaning, "shows desire")))
            }
            VerbType.GODAN_K -> {
                forms.add(ConjugationItem("Want to", "${stem}きたい", if (hasKanji) "${stemReading}きたい" else null, formatMeaning(meaning, "want to")))
                forms.add(ConjugationItem("Want to (past)", "${stem}きたかった", if (hasKanji) "${stemReading}きたかった" else null, formatMeaning(meaning, "want to (past)")))
                forms.add(ConjugationItem("Don't want to", "${stem}きたくない", if (hasKanji) "${stemReading}きたくない" else null, formatMeaning(meaning, "don't want to")))
                forms.add(ConjugationItem("Didn't want to", "${stem}きたくなかった", if (hasKanji) "${stemReading}きたくなかった" else null, formatMeaning(meaning, "didn't want to")))
                forms.add(ConjugationItem("Shows desire", "${stem}きたがる", if (hasKanji) "${stemReading}きたがる" else null, formatMeaning(meaning, "shows desire")))
            }
            VerbType.GODAN_S -> {
                forms.add(ConjugationItem("Want to", "${stem}したい", if (hasKanji) "${stemReading}したい" else null, formatMeaning(meaning, "want to")))
                forms.add(ConjugationItem("Want to (past)", "${stem}したかった", if (hasKanji) "${stemReading}したかった" else null, formatMeaning(meaning, "want to (past)")))
                forms.add(ConjugationItem("Don't want to", "${stem}したくない", if (hasKanji) "${stemReading}したくない" else null, formatMeaning(meaning, "don't want to")))
                forms.add(ConjugationItem("Didn't want to", "${stem}したくなかった", if (hasKanji) "${stemReading}したくなかった" else null, formatMeaning(meaning, "didn't want to")))
                forms.add(ConjugationItem("Shows desire", "${stem}したがる", if (hasKanji) "${stemReading}したがる" else null, formatMeaning(meaning, "shows desire")))
            }
            VerbType.GODAN_T -> {
                forms.add(ConjugationItem("Want to", "${stem}ちたい", if (hasKanji) "${stemReading}ちたい" else null, formatMeaning(meaning, "want to")))
                forms.add(ConjugationItem("Want to (past)", "${stem}ちたかった", if (hasKanji) "${stemReading}ちたかった" else null, formatMeaning(meaning, "want to (past)")))
                forms.add(ConjugationItem("Don't want to", "${stem}ちたくない", if (hasKanji) "${stemReading}ちたくない" else null, formatMeaning(meaning, "don't want to")))
                forms.add(ConjugationItem("Didn't want to", "${stem}ちたくなかった", if (hasKanji) "${stemReading}ちたくなかった" else null, formatMeaning(meaning, "didn't want to")))
                forms.add(ConjugationItem("Shows desire", "${stem}ちたがる", if (hasKanji) "${stemReading}ちたがる" else null, formatMeaning(meaning, "shows desire")))
            }
            VerbType.GODAN_N -> {
                forms.add(ConjugationItem("Want to", "${stem}にたい", if (hasKanji) "${stemReading}にたい" else null, formatMeaning(meaning, "want to")))
                forms.add(ConjugationItem("Want to (past)", "${stem}にたかった", if (hasKanji) "${stemReading}にたかった" else null, formatMeaning(meaning, "want to (past)")))
                forms.add(ConjugationItem("Don't want to", "${stem}にたくない", if (hasKanji) "${stemReading}にたくない" else null, formatMeaning(meaning, "don't want to")))
                forms.add(ConjugationItem("Didn't want to", "${stem}にたくなかった", if (hasKanji) "${stemReading}にたくなかった" else null, formatMeaning(meaning, "didn't want to")))
                forms.add(ConjugationItem("Shows desire", "${stem}にたがる", if (hasKanji) "${stemReading}にたがる" else null, formatMeaning(meaning, "shows desire")))
            }
            VerbType.GODAN_B -> {
                forms.add(ConjugationItem("Want to", "${stem}びたい", if (hasKanji) "${stemReading}びたい" else null, formatMeaning(meaning, "want to")))
                forms.add(ConjugationItem("Want to (past)", "${stem}びたかった", if (hasKanji) "${stemReading}びたかった" else null, formatMeaning(meaning, "want to (past)")))
                forms.add(ConjugationItem("Don't want to", "${stem}びたくない", if (hasKanji) "${stemReading}びたくない" else null, formatMeaning(meaning, "don't want to")))
                forms.add(ConjugationItem("Didn't want to", "${stem}びたくなかった", if (hasKanji) "${stemReading}びたくなかった" else null, formatMeaning(meaning, "didn't want to")))
                forms.add(ConjugationItem("Shows desire", "${stem}びたがる", if (hasKanji) "${stemReading}びたがる" else null, formatMeaning(meaning, "shows desire")))
            }
            VerbType.GODAN_M -> {
                forms.add(ConjugationItem("Want to", "${stem}みたい", if (hasKanji) "${stemReading}みたい" else null, formatMeaning(meaning, "want to")))
                forms.add(ConjugationItem("Want to (past)", "${stem}みたかった", if (hasKanji) "${stemReading}みたかった" else null, formatMeaning(meaning, "want to (past)")))
                forms.add(ConjugationItem("Don't want to", "${stem}みたくない", if (hasKanji) "${stemReading}みたくない" else null, formatMeaning(meaning, "don't want to")))
                forms.add(ConjugationItem("Didn't want to", "${stem}みたくなかった", if (hasKanji) "${stemReading}みたくなかった" else null, formatMeaning(meaning, "didn't want to")))
                forms.add(ConjugationItem("Shows desire", "${stem}みたがる", if (hasKanji) "${stemReading}みたがる" else null, formatMeaning(meaning, "shows desire")))
            }
            VerbType.GODAN_R -> {
                forms.add(ConjugationItem("Want to", "${stem}りたい", if (hasKanji) "${stemReading}りたい" else null, formatMeaning(meaning, "want to")))
                forms.add(ConjugationItem("Want to (past)", "${stem}りたかった", if (hasKanji) "${stemReading}りたかった" else null, formatMeaning(meaning, "want to (past)")))
                forms.add(ConjugationItem("Don't want to", "${stem}りたくない", if (hasKanji) "${stemReading}りたくない" else null, formatMeaning(meaning, "don't want to")))
                forms.add(ConjugationItem("Didn't want to", "${stem}りたくなかった", if (hasKanji) "${stemReading}りたくなかった" else null, formatMeaning(meaning, "didn't want to")))
                forms.add(ConjugationItem("Shows desire", "${stem}りたがる", if (hasKanji) "${stemReading}りたがる" else null, formatMeaning(meaning, "shows desire")))
            }
            VerbType.GODAN_G -> {
                forms.add(ConjugationItem("Want to", "${stem}ぎたい", if (hasKanji) "${stemReading}ぎたい" else null, formatMeaning(meaning, "want to")))
                forms.add(ConjugationItem("Want to (past)", "${stem}ぎたかった", if (hasKanji) "${stemReading}ぎたかった" else null, formatMeaning(meaning, "want to (past)")))
                forms.add(ConjugationItem("Don't want to", "${stem}ぎたくない", if (hasKanji) "${stemReading}ぎたくない" else null, formatMeaning(meaning, "don't want to")))
                forms.add(ConjugationItem("Didn't want to", "${stem}ぎたくなかった", if (hasKanji) "${stemReading}ぎたくなかった" else null, formatMeaning(meaning, "didn't want to")))
                forms.add(ConjugationItem("Shows desire", "${stem}ぎたがる", if (hasKanji) "${stemReading}ぎたがる" else null, formatMeaning(meaning, "shows desire")))
            }
            VerbType.GODAN_U -> {
                forms.add(ConjugationItem("Want to", "${stem}いたい", if (hasKanji) "${stemReading}いたい" else null, formatMeaning(meaning, "want to")))
                forms.add(ConjugationItem("Want to (past)", "${stem}いたかった", if (hasKanji) "${stemReading}いたかった" else null, formatMeaning(meaning, "want to (past)")))
                forms.add(ConjugationItem("Don't want to", "${stem}いたくない", if (hasKanji) "${stemReading}いたくない" else null, formatMeaning(meaning, "don't want to")))
                forms.add(ConjugationItem("Didn't want to", "${stem}いたくなかった", if (hasKanji) "${stemReading}いたくなかった" else null, formatMeaning(meaning, "didn't want to")))
                forms.add(ConjugationItem("Shows desire", "${stem}いたがる", if (hasKanji) "${stemReading}いたがる" else null, formatMeaning(meaning, "shows desire")))
            }
            VerbType.SURU_IRREGULAR -> {
                forms.add(ConjugationItem("Want to", "${stem}したい", if (hasKanji) "${stemReading}したい" else null, formatMeaning(meaning, "want to")))
                forms.add(ConjugationItem("Want to (past)", "${stem}したかった", if (hasKanji) "${stemReading}したかった" else null, formatMeaning(meaning, "want to (past)")))
                forms.add(ConjugationItem("Don't want to", "${stem}したくない", if (hasKanji) "${stemReading}したくない" else null, formatMeaning(meaning, "don't want to")))
                forms.add(ConjugationItem("Didn't want to", "${stem}したくなかった", if (hasKanji) "${stemReading}したくなかった" else null, formatMeaning(meaning, "didn't want to")))
                forms.add(ConjugationItem("Shows desire", "${stem}したがる", if (hasKanji) "${stemReading}したがる" else null, formatMeaning(meaning, "shows desire")))
            }
            VerbType.KURU_IRREGULAR -> {
                val base = if (word == "来る") "来" else "き"
                forms.add(ConjugationItem("Want to", "${base}たい", "きたい", formatMeaning(meaning, "want to")))
                forms.add(ConjugationItem("Want to (past)", "${base}たかった", "きたかった", formatMeaning(meaning, "want to (past)")))
                forms.add(ConjugationItem("Don't want to", "${base}たくない", "きたくない", formatMeaning(meaning, "don't want to")))
                forms.add(ConjugationItem("Didn't want to", "${base}たくなかった", "きたくなかった", formatMeaning(meaning, "didn't want to")))
                forms.add(ConjugationItem("Shows desire", "${base}たがる", "きたがる", formatMeaning(meaning, "shows desire")))
            }
            VerbType.IKU_IRREGULAR -> {
                val base = if (word == "行く") "行" else "い"
                forms.add(ConjugationItem("Want to", "${base}きたい", "いきたい", formatMeaning(meaning, "want to")))
                forms.add(ConjugationItem("Want to (past)", "${base}きたかった", "いきたかった", formatMeaning(meaning, "want to (past)")))
                forms.add(ConjugationItem("Don't want to", "${base}きたくない", "いきたくない", formatMeaning(meaning, "don't want to")))
                forms.add(ConjugationItem("Didn't want to", "${base}きたくなかった", "いきたくなかった", formatMeaning(meaning, "didn't want to")))
                forms.add(ConjugationItem("Shows desire", "${base}きたがる", "いきたがる", formatMeaning(meaning, "shows desire")))
            }
            else -> {}
        }

        return forms
    }

    private fun generateAdvancedForms(
        word: String,
        stem: String,
        stemReading: String,
        baseReading: String,
        verbType: VerbType,
        meaning: String,
        hasKanji: Boolean
    ): List<ConjugationItem> {
        val forms = mutableListOf<ConjugationItem>()

        when (verbType) {
            VerbType.ICHIDAN -> {
                forms.add(ConjugationItem("Seems like", "${stem}そう", if (hasKanji) "${stemReading}そう" else null, formatMeaning(meaning, "seems like")))
                forms.add(ConjugationItem("Seems like (neg)", "${stem}なさそう", if (hasKanji) "${stemReading}なさそう" else null, formatMeaning(meaning, "seems like (neg)")))
                forms.add(ConjugationItem("Too much", "${stem}すぎる", if (hasKanji) "${stemReading}すぎる" else null, formatMeaning(meaning, "too much")))
                forms.add(ConjugationItem("Too much (past)", "${stem}すぎた", if (hasKanji) "${stemReading}すぎた" else null, formatMeaning(meaning, "too much (past)")))
                forms.add(ConjugationItem("While", "${stem}ながら", if (hasKanji) "${stemReading}ながら" else null, formatMeaning(meaning, "while")))
                forms.add(ConjugationItem("Without doing", "${stem}ないで", if (hasKanji) "${stemReading}ないで" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("Without doing (ずに)", "${stem}ずに", if (hasKanji) "${stemReading}ずに" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("After", "${stem}てから", if (hasKanji) "${stemReading}てから" else null, formatMeaning(meaning, "after")))
                forms.add(ConjugationItem("Things like", "${stem}たり", if (hasKanji) "${stemReading}たり" else null, formatMeaning(meaning, "things like")))
                forms.add(ConjugationItem("Even if", "${stem}ても", if (hasKanji) "${stemReading}ても" else null, formatMeaning(meaning, "even if")))
                forms.add(ConjugationItem("Casual continuous", "${stem}てる", if (hasKanji) "${stemReading}てる" else null, formatMeaning(meaning, "casual continuous")))
                forms.add(ConjugationItem("Completion (ちゃう)", "${stem}ちゃう", if (hasKanji) "${stemReading}ちゃう" else null, formatMeaning(meaning, "completion (ちゃう)")))
                forms.add(ConjugationItem("Negative (ず)", "${stem}ず", if (hasKanji) "${stemReading}ず" else null, formatMeaning(meaning, "negative (ず)")))
            }

            VerbType.GODAN_K -> {
                forms.add(ConjugationItem("Seems like", "${stem}きそう", if (hasKanji) "${stemReading}きそう" else null, formatMeaning(meaning, "seems like")))
                forms.add(ConjugationItem("Seems like (neg)", "${stem}かなさそう", if (hasKanji) "${stemReading}かなさそう" else null, formatMeaning(meaning, "seems like (neg)")))
                forms.add(ConjugationItem("Too much", "${stem}きすぎる", if (hasKanji) "${stemReading}きすぎる" else null, formatMeaning(meaning, "too much")))
                forms.add(ConjugationItem("Too much (past)", "${stem}きすぎた", if (hasKanji) "${stemReading}きすぎた" else null, formatMeaning(meaning, "too much (past)")))
                forms.add(ConjugationItem("While", "${stem}きながら", if (hasKanji) "${stemReading}きながら" else null, formatMeaning(meaning, "while")))
                forms.add(ConjugationItem("Without doing", "${stem}かないで", if (hasKanji) "${stemReading}かないで" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("Without doing (ずに)", "${stem}かずに", if (hasKanji) "${stemReading}かずに" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("After", "${stem}いてから", if (hasKanji) "${stemReading}いてから" else null, formatMeaning(meaning, "after")))
                forms.add(ConjugationItem("Things like", "${stem}いたり", if (hasKanji) "${stemReading}いたり" else null, formatMeaning(meaning, "things like")))
                forms.add(ConjugationItem("Even if", "${stem}いても", if (hasKanji) "${stemReading}いても" else null, formatMeaning(meaning, "even if")))
                forms.add(ConjugationItem("Casual continuous", "${stem}いてる", if (hasKanji) "${stemReading}いてる" else null, formatMeaning(meaning, "casual continuous")))
                forms.add(ConjugationItem("Completion (ちゃう)", "${stem}いちゃう", if (hasKanji) "${stemReading}いちゃう" else null, formatMeaning(meaning, "completion (ちゃう)")))
                forms.add(ConjugationItem("Negative (ず)", "${stem}かず", if (hasKanji) "${stemReading}かず" else null, formatMeaning(meaning, "negative (ず)")))
            }

            VerbType.GODAN_S -> {
                forms.add(ConjugationItem("Seems like", "${stem}しそう", if (hasKanji) "${stemReading}しそう" else null, formatMeaning(meaning, "seems like")))
                forms.add(ConjugationItem("Seems like (neg)", "${stem}さなさそう", if (hasKanji) "${stemReading}さなさそう" else null, formatMeaning(meaning, "seems like (neg)")))
                forms.add(ConjugationItem("Too much", "${stem}しすぎる", if (hasKanji) "${stemReading}しすぎる" else null, formatMeaning(meaning, "too much")))
                forms.add(ConjugationItem("Too much (past)", "${stem}しすぎた", if (hasKanji) "${stemReading}しすぎた" else null, formatMeaning(meaning, "too much (past)")))
                forms.add(ConjugationItem("While", "${stem}しながら", if (hasKanji) "${stemReading}しながら" else null, formatMeaning(meaning, "while")))
                forms.add(ConjugationItem("Without doing", "${stem}さないで", if (hasKanji) "${stemReading}さないで" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("Without doing (ずに)", "${stem}さずに", if (hasKanji) "${stemReading}さずに" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("After", "${stem}してから", if (hasKanji) "${stemReading}してから" else null, formatMeaning(meaning, "after")))
                forms.add(ConjugationItem("Things like", "${stem}したり", if (hasKanji) "${stemReading}したり" else null, formatMeaning(meaning, "things like")))
                forms.add(ConjugationItem("Even if", "${stem}しても", if (hasKanji) "${stemReading}しても" else null, formatMeaning(meaning, "even if")))
                forms.add(ConjugationItem("Casual continuous", "${stem}してる", if (hasKanji) "${stemReading}してる" else null, formatMeaning(meaning, "casual continuous")))
                forms.add(ConjugationItem("Completion (ちゃう)", "${stem}しちゃう", if (hasKanji) "${stemReading}しちゃう" else null, formatMeaning(meaning, "completion (ちゃう)")))
                forms.add(ConjugationItem("Negative (ず)", "${stem}さず", if (hasKanji) "${stemReading}さず" else null, formatMeaning(meaning, "negative (ず)")))
            }

            VerbType.GODAN_T -> {
                forms.add(ConjugationItem("Seems like", "${stem}ちそう", if (hasKanji) "${stemReading}ちそう" else null, formatMeaning(meaning, "seems like")))
                forms.add(ConjugationItem("Seems like (neg)", "${stem}たなさそう", if (hasKanji) "${stemReading}たなさそう" else null, formatMeaning(meaning, "seems like (neg)")))
                forms.add(ConjugationItem("Too much", "${stem}ちすぎる", if (hasKanji) "${stemReading}ちすぎる" else null, formatMeaning(meaning, "too much")))
                forms.add(ConjugationItem("Too much (past)", "${stem}ちすぎた", if (hasKanji) "${stemReading}ちすぎた" else null, formatMeaning(meaning, "too much (past)")))
                forms.add(ConjugationItem("While", "${stem}ちながら", if (hasKanji) "${stemReading}ちながら" else null, formatMeaning(meaning, "while")))
                forms.add(ConjugationItem("Without doing", "${stem}たないで", if (hasKanji) "${stemReading}たないで" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("Without doing (ずに)", "${stem}たずに", if (hasKanji) "${stemReading}たずに" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("After", "${stem}ってから", if (hasKanji) "${stemReading}ってから" else null, formatMeaning(meaning, "after")))
                forms.add(ConjugationItem("Things like", "${stem}ったり", if (hasKanji) "${stemReading}ったり" else null, formatMeaning(meaning, "things like")))
                forms.add(ConjugationItem("Even if", "${stem}っても", if (hasKanji) "${stemReading}っても" else null, formatMeaning(meaning, "even if")))
                forms.add(ConjugationItem("Casual continuous", "${stem}ってる", if (hasKanji) "${stemReading}ってる" else null, formatMeaning(meaning, "casual continuous")))
                forms.add(ConjugationItem("Completion (ちゃう)", "${stem}っちゃう", if (hasKanji) "${stemReading}っちゃう" else null, formatMeaning(meaning, "completion (ちゃう)")))
                forms.add(ConjugationItem("Negative (ず)", "${stem}たず", if (hasKanji) "${stemReading}たず" else null, formatMeaning(meaning, "negative (ず)")))
            }

            VerbType.GODAN_N -> {
                forms.add(ConjugationItem("Seems like", "${stem}にそう", if (hasKanji) "${stemReading}にそう" else null, formatMeaning(meaning, "seems like")))
                forms.add(ConjugationItem("Seems like (neg)", "${stem}ななさそう", if (hasKanji) "${stemReading}ななさそう" else null, formatMeaning(meaning, "seems like (neg)")))
                forms.add(ConjugationItem("Too much", "${stem}にすぎる", if (hasKanji) "${stemReading}にすぎる" else null, formatMeaning(meaning, "too much")))
                forms.add(ConjugationItem("Too much (past)", "${stem}にすぎた", if (hasKanji) "${stemReading}にすぎた" else null, formatMeaning(meaning, "too much (past)")))
                forms.add(ConjugationItem("While", "${stem}にながら", if (hasKanji) "${stemReading}にながら" else null, formatMeaning(meaning, "while")))
                forms.add(ConjugationItem("Without doing", "${stem}なないで", if (hasKanji) "${stemReading}なないで" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("Without doing (ずに)", "${stem}なずに", if (hasKanji) "${stemReading}なずに" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("After", "${stem}んでから", if (hasKanji) "${stemReading}んでから" else null, formatMeaning(meaning, "after")))
                forms.add(ConjugationItem("Things like", "${stem}んだり", if (hasKanji) "${stemReading}んだり" else null, formatMeaning(meaning, "things like")))
                forms.add(ConjugationItem("Even if", "${stem}んでも", if (hasKanji) "${stemReading}んでも" else null, formatMeaning(meaning, "even if")))
                forms.add(ConjugationItem("Casual continuous", "${stem}んでる", if (hasKanji) "${stemReading}んでる" else null, formatMeaning(meaning, "casual continuous")))
                forms.add(ConjugationItem("Completion (じゃう)", "${stem}んじゃう", if (hasKanji) "${stemReading}んじゃう" else null, formatMeaning(meaning, "completion (じゃう)")))
                forms.add(ConjugationItem("Negative (ず)", "${stem}なず", if (hasKanji) "${stemReading}なず" else null, formatMeaning(meaning, "negative (ず)")))
            }

            VerbType.GODAN_B -> {
                forms.add(ConjugationItem("Seems like", "${stem}びそう", if (hasKanji) "${stemReading}びそう" else null, formatMeaning(meaning, "seems like")))
                forms.add(ConjugationItem("Seems like (neg)", "${stem}ばなさそう", if (hasKanji) "${stemReading}ばなさそう" else null, formatMeaning(meaning, "seems like (neg)")))
                forms.add(ConjugationItem("Too much", "${stem}びすぎる", if (hasKanji) "${stemReading}びすぎる" else null, formatMeaning(meaning, "too much")))
                forms.add(ConjugationItem("Too much (past)", "${stem}びすぎた", if (hasKanji) "${stemReading}びすぎた" else null, formatMeaning(meaning, "too much (past)")))
                forms.add(ConjugationItem("While", "${stem}びながら", if (hasKanji) "${stemReading}びながら" else null, formatMeaning(meaning, "while")))
                forms.add(ConjugationItem("Without doing", "${stem}ばないで", if (hasKanji) "${stemReading}ばないで" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("Without doing (ずに)", "${stem}ばずに", if (hasKanji) "${stemReading}ばずに" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("After", "${stem}んでから", if (hasKanji) "${stemReading}んでから" else null, formatMeaning(meaning, "after")))
                forms.add(ConjugationItem("Things like", "${stem}んだり", if (hasKanji) "${stemReading}んだり" else null, formatMeaning(meaning, "things like")))
                forms.add(ConjugationItem("Even if", "${stem}んでも", if (hasKanji) "${stemReading}んでも" else null, formatMeaning(meaning, "even if")))
                forms.add(ConjugationItem("Casual continuous", "${stem}んでる", if (hasKanji) "${stemReading}んでる" else null, formatMeaning(meaning, "casual continuous")))
                forms.add(ConjugationItem("Completion (じゃう)", "${stem}んじゃう", if (hasKanji) "${stemReading}んじゃう" else null, formatMeaning(meaning, "completion (じゃう)")))
                forms.add(ConjugationItem("Negative (ず)", "${stem}ばず", if (hasKanji) "${stemReading}ばず" else null, formatMeaning(meaning, "negative (ず)")))
            }

            VerbType.GODAN_M -> {
                forms.add(ConjugationItem("Seems like", "${stem}みそう", if (hasKanji) "${stemReading}みそう" else null, formatMeaning(meaning, "seems like")))
                forms.add(ConjugationItem("Seems like (neg)", "${stem}まなさそう", if (hasKanji) "${stemReading}まなさそう" else null, formatMeaning(meaning, "seems like (neg)")))
                forms.add(ConjugationItem("Too much", "${stem}みすぎる", if (hasKanji) "${stemReading}みすぎる" else null, formatMeaning(meaning, "too much")))
                forms.add(ConjugationItem("Too much (past)", "${stem}みすぎた", if (hasKanji) "${stemReading}みすぎた" else null, formatMeaning(meaning, "too much (past)")))
                forms.add(ConjugationItem("While", "${stem}みながら", if (hasKanji) "${stemReading}みながら" else null, formatMeaning(meaning, "while")))
                forms.add(ConjugationItem("Without doing", "${stem}まないで", if (hasKanji) "${stemReading}まないで" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("Without doing (ずに)", "${stem}まずに", if (hasKanji) "${stemReading}まずに" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("After", "${stem}んでから", if (hasKanji) "${stemReading}んでから" else null, formatMeaning(meaning, "after")))
                forms.add(ConjugationItem("Things like", "${stem}んだり", if (hasKanji) "${stemReading}んだり" else null, formatMeaning(meaning, "things like")))
                forms.add(ConjugationItem("Even if", "${stem}んでも", if (hasKanji) "${stemReading}んでも" else null, formatMeaning(meaning, "even if")))
                forms.add(ConjugationItem("Casual continuous", "${stem}んでる", if (hasKanji) "${stemReading}んでる" else null, formatMeaning(meaning, "casual continuous")))
                forms.add(ConjugationItem("Completion (じゃう)", "${stem}んじゃう", if (hasKanji) "${stemReading}んじゃう" else null, formatMeaning(meaning, "completion (じゃう)")))
                forms.add(ConjugationItem("Negative (ず)", "${stem}まず", if (hasKanji) "${stemReading}まず" else null, formatMeaning(meaning, "negative (ず)")))
            }

            VerbType.GODAN_R -> {
                forms.add(ConjugationItem("Seems like", "${stem}りそう", if (hasKanji) "${stemReading}りそう" else null, formatMeaning(meaning, "seems like")))
                forms.add(ConjugationItem("Seems like (neg)", "${stem}らなさそう", if (hasKanji) "${stemReading}らなさそう" else null, formatMeaning(meaning, "seems like (neg)")))
                forms.add(ConjugationItem("Too much", "${stem}りすぎる", if (hasKanji) "${stemReading}りすぎる" else null, formatMeaning(meaning, "too much")))
                forms.add(ConjugationItem("Too much (past)", "${stem}りすぎた", if (hasKanji) "${stemReading}りすぎた" else null, formatMeaning(meaning, "too much (past)")))
                forms.add(ConjugationItem("While", "${stem}りながら", if (hasKanji) "${stemReading}りながら" else null, formatMeaning(meaning, "while")))
                forms.add(ConjugationItem("Without doing", "${stem}らないで", if (hasKanji) "${stemReading}らないで" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("Without doing (ずに)", "${stem}らずに", if (hasKanji) "${stemReading}らずに" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("After", "${stem}ってから", if (hasKanji) "${stemReading}ってから" else null, formatMeaning(meaning, "after")))
                forms.add(ConjugationItem("Things like", "${stem}ったり", if (hasKanji) "${stemReading}ったり" else null, formatMeaning(meaning, "things like")))
                forms.add(ConjugationItem("Even if", "${stem}っても", if (hasKanji) "${stemReading}っても" else null, formatMeaning(meaning, "even if")))
                forms.add(ConjugationItem("Casual continuous", "${stem}ってる", if (hasKanji) "${stemReading}ってる" else null, formatMeaning(meaning, "casual continuous")))
                forms.add(ConjugationItem("Completion (ちゃう)", "${stem}っちゃう", if (hasKanji) "${stemReading}っちゃう" else null, formatMeaning(meaning, "completion (ちゃう)")))
                forms.add(ConjugationItem("Negative (ず)", "${stem}らず", if (hasKanji) "${stemReading}らず" else null, formatMeaning(meaning, "negative (ず)")))
            }

            VerbType.GODAN_G -> {
                forms.add(ConjugationItem("Seems like", "${stem}ぎそう", if (hasKanji) "${stemReading}ぎそう" else null, formatMeaning(meaning, "seems like")))
                forms.add(ConjugationItem("Seems like (neg)", "${stem}がなさそう", if (hasKanji) "${stemReading}がなさそう" else null, formatMeaning(meaning, "seems like (neg)")))
                forms.add(ConjugationItem("Too much", "${stem}ぎすぎる", if (hasKanji) "${stemReading}ぎすぎる" else null, formatMeaning(meaning, "too much")))
                forms.add(ConjugationItem("Too much (past)", "${stem}ぎすぎた", if (hasKanji) "${stemReading}ぎすぎた" else null, formatMeaning(meaning, "too much (past)")))
                forms.add(ConjugationItem("While", "${stem}ぎながら", if (hasKanji) "${stemReading}ぎながら" else null, formatMeaning(meaning, "while")))
                forms.add(ConjugationItem("Without doing", "${stem}がないで", if (hasKanji) "${stemReading}がないで" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("Without doing (ずに)", "${stem}がずに", if (hasKanji) "${stemReading}がずに" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("After", "${stem}いでから", if (hasKanji) "${stemReading}いでから" else null, formatMeaning(meaning, "after")))
                forms.add(ConjugationItem("Things like", "${stem}いだり", if (hasKanji) "${stemReading}いだり" else null, formatMeaning(meaning, "things like")))
                forms.add(ConjugationItem("Even if", "${stem}いでも", if (hasKanji) "${stemReading}いでも" else null, formatMeaning(meaning, "even if")))
                forms.add(ConjugationItem("Casual continuous", "${stem}いでる", if (hasKanji) "${stemReading}いでる" else null, formatMeaning(meaning, "casual continuous")))
                forms.add(ConjugationItem("Completion (じゃう)", "${stem}いじゃう", if (hasKanji) "${stemReading}いじゃう" else null, formatMeaning(meaning, "completion (じゃう)")))
                forms.add(ConjugationItem("Negative (ず)", "${stem}がず", if (hasKanji) "${stemReading}がず" else null, formatMeaning(meaning, "negative (ず)")))
            }

            VerbType.GODAN_U -> {
                forms.add(ConjugationItem("Seems like", "${stem}いそう", if (hasKanji) "${stemReading}いそう" else null, formatMeaning(meaning, "seems like")))
                forms.add(ConjugationItem("Seems like (neg)", "${stem}わなさそう", if (hasKanji) "${stemReading}わなさそう" else null, formatMeaning(meaning, "seems like (neg)")))
                forms.add(ConjugationItem("Too much", "${stem}いすぎる", if (hasKanji) "${stemReading}いすぎる" else null, formatMeaning(meaning, "too much")))
                forms.add(ConjugationItem("Too much (past)", "${stem}いすぎた", if (hasKanji) "${stemReading}いすぎた" else null, formatMeaning(meaning, "too much (past)")))
                forms.add(ConjugationItem("While", "${stem}いながら", if (hasKanji) "${stemReading}いながら" else null, formatMeaning(meaning, "while")))
                forms.add(ConjugationItem("Without doing", "${stem}わないで", if (hasKanji) "${stemReading}わないで" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("Without doing (ずに)", "${stem}わずに", if (hasKanji) "${stemReading}わずに" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("After", "${stem}ってから", if (hasKanji) "${stemReading}ってから" else null, formatMeaning(meaning, "after")))
                forms.add(ConjugationItem("Things like", "${stem}ったり", if (hasKanji) "${stemReading}ったり" else null, formatMeaning(meaning, "things like")))
                forms.add(ConjugationItem("Even if", "${stem}っても", if (hasKanji) "${stemReading}っても" else null, formatMeaning(meaning, "even if")))
                forms.add(ConjugationItem("Casual continuous", "${stem}ってる", if (hasKanji) "${stemReading}ってる" else null, formatMeaning(meaning, "casual continuous")))
                forms.add(ConjugationItem("Completion (ちゃう)", "${stem}っちゃう", if (hasKanji) "${stemReading}っちゃう" else null, formatMeaning(meaning, "completion (ちゃう)")))
                forms.add(ConjugationItem("Negative (ず)", "${stem}わず", if (hasKanji) "${stemReading}わず" else null, formatMeaning(meaning, "negative (ず)")))
            }

            // Continue with special verbs...
            VerbType.SURU_IRREGULAR -> {
                forms.add(ConjugationItem("Seems like", "${stem}しそう", if (hasKanji) "${stemReading}しそう" else null, formatMeaning(meaning, "seems like")))
                forms.add(ConjugationItem("Seems like (neg)", "${stem}しなさそう", if (hasKanji) "${stemReading}しなさそう" else null, formatMeaning(meaning, "seems like (neg)")))
                forms.add(ConjugationItem("Too much", "${stem}しすぎる", if (hasKanji) "${stemReading}しすぎる" else null, formatMeaning(meaning, "too much")))
                forms.add(ConjugationItem("Too much (past)", "${stem}しすぎた", if (hasKanji) "${stemReading}しすぎた" else null, formatMeaning(meaning, "too much (past)")))
                forms.add(ConjugationItem("While", "${stem}しながら", if (hasKanji) "${stemReading}しながら" else null, formatMeaning(meaning, "while")))
                forms.add(ConjugationItem("Without doing", "${stem}しないで", if (hasKanji) "${stemReading}しないで" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("Without doing (ずに)", "${stem}せずに", if (hasKanji) "${stemReading}せずに" else null, formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("After", "${stem}してから", if (hasKanji) "${stemReading}してから" else null, formatMeaning(meaning, "after")))
                forms.add(ConjugationItem("Things like", "${stem}したり", if (hasKanji) "${stemReading}したり" else null, formatMeaning(meaning, "things like")))
                forms.add(ConjugationItem("Even if", "${stem}しても", if (hasKanji) "${stemReading}しても" else null, formatMeaning(meaning, "even if")))
                forms.add(ConjugationItem("Casual continuous", "${stem}してる", if (hasKanji) "${stemReading}してる" else null, formatMeaning(meaning, "casual continuous")))
                forms.add(ConjugationItem("Completion (ちゃう)", "${stem}しちゃう", if (hasKanji) "${stemReading}しちゃう" else null, formatMeaning(meaning, "completion (ちゃう)")))
                forms.add(ConjugationItem("Negative (ず)", "${stem}せず", if (hasKanji) "${stemReading}せず" else null, formatMeaning(meaning, "negative (ず)")))
            }

            VerbType.KURU_IRREGULAR -> {
                val base = if (word == "来る") "来" else ""
                forms.add(ConjugationItem("Seems like", "${base}そう", "きそう", formatMeaning(meaning, "seems like")))
                forms.add(ConjugationItem("Seems like (neg)", "${base}なさそう", "こなさそう", formatMeaning(meaning, "seems like (neg)")))
                forms.add(ConjugationItem("Too much", "${base}すぎる", "きすぎる", formatMeaning(meaning, "too much")))
                forms.add(ConjugationItem("Too much (past)", "${base}すぎた", "きすぎた", formatMeaning(meaning, "too much (past)")))
                forms.add(ConjugationItem("While", "${base}ながら", "きながら", formatMeaning(meaning, "while")))
                forms.add(ConjugationItem("Without doing", "${base}ないで", "こないで", formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("Without doing (ずに)", "${base}ずに", "こずに", formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("After", "${base}てから", "きてから", formatMeaning(meaning, "after")))
                forms.add(ConjugationItem("Things like", "${base}たり", "きたり", formatMeaning(meaning, "things like")))
                forms.add(ConjugationItem("Even if", "${base}ても", "きても", formatMeaning(meaning, "even if")))
                forms.add(ConjugationItem("Casual continuous", "${base}てる", "きてる", formatMeaning(meaning, "casual continuous")))
                forms.add(ConjugationItem("Completion (ちゃう)", "${base}ちゃう", "きちゃう", formatMeaning(meaning, "completion (ちゃう)")))
                forms.add(ConjugationItem("Negative (ず)", "${base}ず", "こず", formatMeaning(meaning, "negative (ず)")))
            }

            VerbType.IKU_IRREGULAR -> {
                val base = if (word == "行く") "行" else "い"
                forms.add(ConjugationItem("Seems like", "${base}きそう", "いきそう", formatMeaning(meaning, "seems like")))
                forms.add(ConjugationItem("Seems like (neg)", "${base}かなさそう", "いかなさそう", formatMeaning(meaning, "seems like (neg)")))
                forms.add(ConjugationItem("Too much", "${base}きすぎる", "いきすぎる", formatMeaning(meaning, "too much")))
                forms.add(ConjugationItem("Too much (past)", "${base}きすぎた", "いきすぎた", formatMeaning(meaning, "too much (past)")))
                forms.add(ConjugationItem("While", "${base}きながら", "いきながら", formatMeaning(meaning, "while")))
                forms.add(ConjugationItem("Without doing", "${base}かないで", "いかないで", formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("Without doing (ずに)", "${base}かずに", "いかずに", formatMeaning(meaning, "without doing")))
                forms.add(ConjugationItem("After", if (word == "行く") "行ってから" else "いってから", "いってから", formatMeaning(meaning, "after")))
                forms.add(ConjugationItem("Things like", if (word == "行く") "行ったり" else "いったり", "いったり", formatMeaning(meaning, "things like")))
                forms.add(ConjugationItem("Even if", if (word == "行く") "行っても" else "いっても", "いっても", formatMeaning(meaning, "even if")))
                forms.add(ConjugationItem("Casual continuous", if (word == "行く") "行ってる" else "いってる", "いってる", formatMeaning(meaning, "casual continuous")))
                forms.add(ConjugationItem("Completion (ちゃう)", if (word == "行く") "行っちゃう" else "いっちゃう", "いっちゃう", formatMeaning(meaning, "completion (ちゃう)")))
                forms.add(ConjugationItem("Negative (ず)", "${stem}かず", if (hasKanji) "${stemReading}かず" else null, formatMeaning(meaning, "negative (ず)")))
            }
            else -> {}
        }

        return forms
    }

    private fun generateHearsayForms(
        word: String,
        stem: String,
        stemReading: String,
        baseReading: String,
        verbType: VerbType,
        meaning: String,
        hasKanji: Boolean
    ): List<ConjugationItem> {
        val forms = mutableListOf<ConjugationItem>()

        // Hearsay form (I heard that...)
        forms.add(ConjugationItem("Hearsay", "${word}そうだ", if (word != baseReading) "${baseReading}そうだ" else null, formatMeaning(meaning, "hearsay")))
        forms.add(ConjugationItem("Hearsay Past", "${word}そうだった", if (word != baseReading) "${baseReading}そうだった" else null, formatMeaning(meaning, "hearsay past")))

        // Presumptive form
        forms.add(ConjugationItem("Presumptive", "${word}でしょう", if (word != baseReading) "${baseReading}でしょう" else null, formatMeaning(meaning, "presumptive")))
        forms.add(ConjugationItem("Presumptive Negative", "${word}ないでしょう", if (word != baseReading) "${baseReading}ないでしょう" else null, formatMeaning(meaning, "presumptive negative")))

        return forms
    }


    private fun getEndingChar(verbType: VerbType): String {
        return when (verbType) {
            VerbType.ICHIDAN -> "る"
            VerbType.GODAN_K -> "く"
            VerbType.GODAN_S -> "す"
            VerbType.GODAN_T -> "つ"
            VerbType.GODAN_N -> "ぬ"
            VerbType.GODAN_B -> "ぶ"
            VerbType.GODAN_M -> "む"
            VerbType.GODAN_R -> "る"
            VerbType.GODAN_G -> "ぐ"
            VerbType.GODAN_U -> "う"
            VerbType.SURU_IRREGULAR -> "する"
            VerbType.KURU_IRREGULAR -> "る"
            VerbType.IKU_IRREGULAR -> "く"
            VerbType.ADJECTIVE_I -> "い"
            else -> ""
        }
    }

    private fun transformMeaning(baseMeaning: String, reason: String): String {
        return when (reason) {
            "past" -> "$baseMeaning (past)"
            "negative" -> "don't/doesn't $baseMeaning"
            "past negative" -> "didn't $baseMeaning"
            "polite" -> "$baseMeaning (polite)"
            "polite past" -> "$baseMeaning (polite past)"
            "polite negative" -> "don't/doesn't $baseMeaning (polite)"
            "polite past negative" -> "didn't $baseMeaning (polite)"
            "te-form" -> "$baseMeaning and..."
            "continuous" -> "is ${baseMeaning}ing"
            "continuous past" -> "was ${baseMeaning}ing"
            "volitional" -> "let's $baseMeaning"
            "potential" -> "can $baseMeaning"
            "passive" -> "be ${baseMeaning}ed"
            "causative" -> "make/let someone $baseMeaning"
            "causative passive" -> "be made to $baseMeaning"
            "imperative" -> "$baseMeaning! (command)"
            "tai" -> "want to $baseMeaning"
            else -> baseMeaning
        }
    }

    private fun formatRuleName(reason: String): String {
        return reason.split(' ')
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    /**
     * Smart meaning formatter that removes "to" where it doesn't make sense
     */
    private fun formatMeaning(meaning: String, conjugationType: String): String {
        val cleanMeaning = meaning.removePrefix("to ") // Remove "to" prefix if present

        return when (conjugationType.lowercase()) {
            // Dictionary form - basic meaning
            "dictionary" -> cleanMeaning

            // Past forms
            "past" -> "when this action happened (past tense)"
            "polite past" -> "when this action happened (polite past)"

            // Negative forms
            "negative" -> "when you don't do this action"
            "polite negative" -> "when you don't do this action (polite)"
            "archaic negative" -> "when you don't do this action (archaic)"

            // Past negative forms
            "past negative" -> "when you didn't do this action"
            "polite past negative" -> "when you didn't do this action (polite)"

            // Polite forms
            "polite" -> "when doing this action politely"

            // Continuous forms
            "continuous" -> "when you are doing this action"
            "continuous past" -> "when you were doing this action"
            "polite continuous" -> "when you are doing this action (polite)"
            "polite past continuous" -> "when you were doing this action (polite)"
            "casual continuous" -> "when you are doing this action (casual)"

            // Te-form and related
            "te-form" -> "connecting form - for continuing actions"
            "request" -> "when asking someone to do this action"

            // Volitional forms
            "volitional" -> "when suggesting to do this action together"
            "polite volitional" -> "when suggesting to do this action together (polite)"

            // Conditional forms
            "ba-form" -> "when expressing condition - if this action"
            "tara-form" -> "when expressing condition - if/when this action"
            "nara-form" -> "when expressing condition - if it's about this action"
            "conditional" -> "when expressing condition - if this action"
            "negative conditional" -> "when expressing condition - if not this action"

            // Potential forms
            "potential" -> "when you can/are able to do this action"
            "potential negative" -> "when you can't/are unable to do this action"
            "polite potential" -> "when you can do this action (polite)"

            // Passive forms
            "passive" -> "when this action happens to you/it"
            "passive past" -> "when this action happened to you/it"
            "passive negative" -> "when this action doesn't happen to you/it"

            // Causative forms
            "causative" -> "when you make someone do this action"
            "causative past" -> "when you made someone do this action"
            "causative negative" -> "when you don't make someone do this action"
            "polite causative" -> "when you make someone do this action (polite)"
            "causative (short)" -> "when you make someone do this action"

            // Causative-passive forms
            "causative-passive" -> "when you are made to do this action"
            "causative-passive past" -> "when you were made to do this action"
            "causative-passive negative" -> "when you aren't made to do this action"

            // Imperative forms
            "imperative" -> "when commanding someone to do this action"
            "imperative (formal)" -> "when commanding someone to do this action (formal)"
            "prohibitive" -> "when telling someone not to do this action"
            "polite command" -> "when politely asking someone to do this action"
            "formal command" -> "when formally commanding someone to do this action"

            // Desire forms
            "want to" -> "when you want to do this action"
            "want to (past)" -> "when you wanted to do this action"
            "don't want to" -> "when you don't want to do this action"
            "didn't want to" -> "when you didn't want to do this action"
            "shows desire" -> "when showing desire to do this action"

            // Advanced forms
            "seems like" -> "when it seems like this action will happen"
            "seems like (neg)" -> "when it seems like this action won't happen"
            "too much" -> "when doing this action excessively"
            "too much (past)" -> "when you did this action excessively"
            "while" -> "while doing this action"
            "without doing" -> "without doing this action"
            "without doing (ずに)" -> "without doing this action"
            "after" -> "after doing this action"
            "things like" -> "when doing things like this action"
            "even if" -> "even if you do this action"
            "completion (ちゃう)", "completion (じゃう)" -> "when completing this action (casual)"
            "negative (ず)" -> "when not doing this action (formal negative)"

            // Hearsay forms
            "hearsay" -> "when hearing/reporting that this action happens"
            "hearsay past" -> "when hearing/reporting that this action happened"
            "presumptive" -> "when presuming this action will happen"
            "presumptive negative" -> "when presuming this action won't happen"

            // Noun form
            "noun form" -> "the act/concept of doing this action"

            // Adjective forms
            "attributive" -> "when describing something with this quality"
            "with copula" -> "when stating this quality exists"
            "adverb", "adverbial" -> "when doing something in this manner"

            // Default fallback
            else -> cleanMeaning
        }
    }

    private fun hasKanji(text: String): Boolean {
        return text.any { char ->
            val code = char.code
            (code in 0x4E00..0x9FAF) || (code in 0x3400..0x4DBF)
        }
    }
}