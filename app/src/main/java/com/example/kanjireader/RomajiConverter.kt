package com.example.kanjireader

import android.util.Log

/**
 * Comprehensive romaji to hiragana converter supporting both Hepburn and Kunrei systems
 * Handles special cases like double consonants, long vowels, and combinations
 */
class RomajiConverter {
    
    companion object {
        private const val TAG = "RomajiConverter"
        
        // Basic romaji to hiragana mapping
        private val BASIC_MAPPING = mapOf(
            // Single vowels
            "a" to "あ", "i" to "い", "u" to "う", "e" to "え", "o" to "お",
            
            // K-sounds
            "ka" to "か", "ki" to "き", "ku" to "く", "ke" to "け", "ko" to "こ",
            "ga" to "が", "gi" to "ぎ", "gu" to "ぐ", "ge" to "げ", "go" to "ご",
            
            // S-sounds
            "sa" to "さ", "shi" to "し", "su" to "す", "se" to "せ", "so" to "そ",
            "za" to "ざ", "ji" to "じ", "zu" to "ず", "ze" to "ぜ", "zo" to "ぞ",
            
            // T-sounds
            "ta" to "た", "chi" to "ち", "tsu" to "つ", "te" to "て", "to" to "と",
            "da" to "だ", "di" to "ぢ", "du" to "づ", "de" to "で", "do" to "ど",
            
            // N-sounds
            "na" to "な", "ni" to "に", "nu" to "ぬ", "ne" to "ね", "no" to "の",
            
            // H-sounds
            "ha" to "は", "hi" to "ひ", "fu" to "ふ", "he" to "へ", "ho" to "ほ",
            "ba" to "ば", "bi" to "び", "bu" to "ぶ", "be" to "べ", "bo" to "ぼ",
            "pa" to "ぱ", "pi" to "ぴ", "pu" to "ぷ", "pe" to "ぺ", "po" to "ぽ",
            
            // M-sounds
            "ma" to "ま", "mi" to "み", "mu" to "む", "me" to "め", "mo" to "も",
            
            // Y-sounds
            "ya" to "や", "yu" to "ゆ", "yo" to "よ",
            
            // R-sounds
            "ra" to "ら", "ri" to "り", "ru" to "る", "re" to "れ", "ro" to "ろ",
            
            // W-sounds
            "wa" to "わ", "wi" to "ゐ", "we" to "ゑ", "wo" to "を",
            
            // N
            "n" to "ん"
        )
        
        // Combination sounds (palatalized)
        private val COMBINATION_MAPPING = mapOf(
            // K-combinations
            "kya" to "きゃ", "kyu" to "きゅ", "kyo" to "きょ",
            "gya" to "ぎゃ", "gyu" to "ぎゅ", "gyo" to "ぎょ",
            
            // S-combinations  
            "sha" to "しゃ", "shu" to "しゅ", "sho" to "しょ",
            "ja" to "じゃ", "ju" to "じゅ", "jo" to "じょ",
            
            // T-combinations
            "cha" to "ちゃ", "chu" to "ちゅ", "cho" to "ちょ",
            
            // N-combinations
            "nya" to "にゃ", "nyu" to "にゅ", "nyo" to "にょ",
            
            // H-combinations
            "hya" to "ひゃ", "hyu" to "ひゅ", "hyo" to "ひょ",
            "bya" to "びゃ", "byu" to "びゅ", "byo" to "びょ",
            "pya" to "ぴゃ", "pyu" to "ぴゅ", "pyo" to "ぴょ",
            
            // M-combinations
            "mya" to "みゃ", "myu" to "みゅ", "myo" to "みょ",
            
            // R-combinations
            "rya" to "りゃ", "ryu" to "りゅ", "ryo" to "りょ"
        )
        
        // Alternative spellings (Kunrei-shiki and common variations)
        private val ALTERNATIVE_MAPPING = mapOf(
            // Kunrei-shiki alternatives
            "si" to "し", "ti" to "ち", "tu" to "つ", "hu" to "ふ",
            "zi" to "じ", "sya" to "しゃ", "syu" to "しゅ", "syo" to "しょ",
            "tya" to "ちゃ", "tyu" to "ちゅ", "tyo" to "ちょ",
            "zya" to "じゃ", "zyu" to "じゅ", "zyo" to "じょ",
            
            // Common variations
            "dzu" to "づ", "dzi" to "ぢ",
            
            // Long vowel markers in romaji
            "aa" to "ああ", "ii" to "いい", "uu" to "うう", "ee" to "ええ", "oo" to "おお",
            "ou" to "おう", "ei" to "えい"
        )
        
        // Common particle mappings
        private val PARTICLE_MAPPING = mapOf(
            "wa" to "は", // topic particle は (read as wa)
            "wo" to "を", // object particle を 
            "e" to "へ"   // direction particle へ (read as e)
        )
        
        // Special mappings for common verb endings
        private val VERB_ENDING_MAPPING = mapOf(
            // Complete forms
            "masen" to "ません",
            "mashita" to "ました", 
            "mashou" to "ましょう",
            "mashitara" to "ましたら",
            "masendeshita" to "ませんでした",
            "masu" to "ます",
            "desu" to "です",
            "deshita" to "でした",
            "deshou" to "でしょう",
            
            // Partial forms for typing
            "mase" to "ませ",    // Partial of ません
            "mashi" to "まし",   // Partial of ました/ましょう
            "mas" to "ます",     // Partial of ます
            "desh" to "でし",    // Partial of でした/でしょう
            "des" to "です"      // Partial of です
        )
        
        // All mappings combined, sorted by length (longest first for greedy matching)
        private val ALL_MAPPINGS = (VERB_ENDING_MAPPING + COMBINATION_MAPPING + BASIC_MAPPING + ALTERNATIVE_MAPPING)
            .toList()
            .sortedByDescending { it.first.length }
            .toMap()
    }
    
    /**
     * Convert romaji text to hiragana
     */
    fun toHiragana(romaji: String): String {
        if (romaji.isEmpty()) return romaji
        
        val lowercaseRomaji = romaji.lowercase()
        val result = StringBuilder()
        var i = 0
        
        
        while (i < lowercaseRomaji.length) {
            var matched = false
            
            // Try to match the longest possible sequence first
            for ((romajiPattern, hiragana) in ALL_MAPPINGS) {
                if (i + romajiPattern.length <= lowercaseRomaji.length) {
                    val substring = lowercaseRomaji.substring(i, i + romajiPattern.length)
                    if (substring == romajiPattern) {
                        result.append(hiragana)
                        i += romajiPattern.length
                        matched = true
                        break
                    }
                }
            }
            
            // Handle double consonants (っ)
            if (!matched && i < lowercaseRomaji.length - 1) {
                val currentChar = lowercaseRomaji[i]
                val nextChar = lowercaseRomaji[i + 1]
                
                // Double consonant rule: same consonant repeated (except 'n')
                if (currentChar == nextChar && currentChar != 'n' && currentChar != 'a' && 
                    currentChar != 'i' && currentChar != 'u' && currentChar != 'e' && currentChar != 'o') {
                    result.append("っ")
                    i++
                    matched = true
                }
                // Special cases for double consonants
                else if ((currentChar == 't' && nextChar == 'c') ||  // "tch" -> "っち"
                         (currentChar == 'k' && nextChar == 'k') ||   // "kk" -> "っk"
                         (currentChar == 'p' && nextChar == 'p') ||   // "pp" -> "っp"
                         (currentChar == 's' && nextChar == 's')) {   // "ss" -> "っs"
                    result.append("っ")
                    i++
                    matched = true
                }
            }
            
            // Handle 'n' before consonants (but not before vowels or 'y')
            if (!matched && lowercaseRomaji[i] == 'n' && i < lowercaseRomaji.length - 1) {
                val nextChar = lowercaseRomaji[i + 1]
                if (nextChar !in "aiueoyn") {
                    result.append("ん")
                    i++
                    matched = true
                }
            }
            
            // If no match found, keep the original character
            if (!matched) {
                result.append(lowercaseRomaji[i])
                i++
            }
        }
        
        // Handle trailing 'n'
        if (lowercaseRomaji.endsWith("n")) {
            val resultStr = result.toString()
            if (!resultStr.endsWith("ん") && lowercaseRomaji.length > 1) {
                result.setLength(result.length - 1) // Remove last character
                result.append("ん")
            }
        }
        
        return result.toString()
    }
    
    /**
     * Convert hiragana to katakana
     */
    private fun hiraganaToKatakana(hiragana: String): String {
        val result = StringBuilder()
        for (char in hiragana) {
            when (char.code) {
                in 0x3041..0x3096 -> {
                    // Convert hiragana to katakana (offset by 0x60)
                    result.append((char.code + 0x60).toChar())
                }
                else -> result.append(char)
            }
        }
        return result.toString()
    }
    
    /**
     * Convert romaji text to katakana
     */
    fun toKatakana(romaji: String): String {
        // First convert to hiragana, then convert hiragana to katakana
        return hiraganaToKatakana(toHiragana(romaji))
    }
    
    /**
     * Convert romaji to both hiragana and katakana variants
     * Returns a list of [hiragana, katakana]
     */
    fun toBothKanaVariants(romaji: String): List<String> {
        val hiragana = toHiragana(romaji)
        val katakana = hiraganaToKatakana(hiragana)
        return listOf(hiragana, katakana)
    }
    
    /**
     * Convert romaji text treating particles specially
     */
    fun toHiraganaWithParticles(romaji: String): String {
        if (romaji.isEmpty()) return romaji
        
        // First check if the entire string is a particle
        val lowercaseRomaji = romaji.lowercase()
        PARTICLE_MAPPING[lowercaseRomaji]?.let { return it }
        
        // Otherwise, do normal conversion
        return toHiragana(romaji)
    }
    
    /**
     * Check if text is primarily hiragana
     */
    fun isHiragana(text: String): Boolean {
        if (text.isEmpty()) return false
        return text.any { char ->
            char.code in 0x3040..0x309F // Hiragana range
        }
    }
    
    /**
     * Check if text is primarily katakana
     */
    fun isKatakana(text: String): Boolean {
        if (text.isEmpty()) return false
        return text.any { char ->
            char.code in 0x30A0..0x30FF // Katakana range
        }
    }
    
    /**
     * Convert katakana to hiragana
     */
    fun katakanaToHiragana(katakana: String): String {
        val result = StringBuilder()
        for (char in katakana) {
            when (char.code) {
                in 0x30A1..0x30F6 -> {
                    // Convert katakana to hiragana (offset by -0x60)
                    result.append((char.code - 0x60).toChar())
                }
                else -> result.append(char)
            }
        }
        return result.toString()
    }
    
    /**
     * Get both kana variants of Japanese text (works for both hiragana and katakana input)
     */
    fun getBothKanaVariants(text: String): List<String> {
        return when {
            isHiragana(text) -> {
                // Input is hiragana, return [hiragana, katakana]
                listOf(text, hiraganaToKatakana(text))
            }
            isKatakana(text) -> {
                // Input is katakana, return [hiragana, katakana]
                listOf(katakanaToHiragana(text), text)
            }
            else -> {
                // Not kana, return as-is
                listOf(text)
            }
        }
    }
    
    /**
     * Check if a string contains romaji characters
     */
    fun containsRomaji(text: String): Boolean {
        return text.any { it.code in 0x0041..0x007A } // Has Latin letters
    }
    
    /**
     * Check if a string is likely pure romaji (no Japanese characters)
     */
    fun isPureRomaji(text: String): Boolean {
        if (text.isEmpty()) return false
        
        // Must contain Latin letters
        val hasLatin = text.any { it.code in 0x0041..0x007A }
        if (!hasLatin) return false
        
        // Must not contain Japanese characters
        val hasJapanese = text.any { char ->
            val code = char.code
            (code in 0x3040..0x309F) ||  // Hiragana
            (code in 0x30A0..0x30FF) ||  // Katakana  
            (code in 0x4E00..0x9FAF)     // Kanji
        }
        
        return !hasJapanese
    }
    
    /**
     * Extract words that look like romaji from mixed text
     */
    fun extractRomajiWords(text: String): List<String> {
        // Split on common delimiters and filter for romaji-like words
        val words = text.split(Regex("[\\s\\p{P}]+"))
        return words.filter { word ->
            word.isNotEmpty() && isPureRomaji(word)
        }
    }
    
    /**
     * Convert a mixed script sentence, converting only romaji parts
     */
    fun convertMixedScript(text: String): String {
        if (text.isEmpty()) return text
        
        val result = StringBuilder()
        var i = 0
        
        while (i < text.length) {
            // Check if current position starts a romaji word
            val remainingText = text.substring(i)
            val romajiMatch = Regex("^[a-zA-Z]+").find(remainingText)
            
            if (romajiMatch != null) {
                val romajiWord = romajiMatch.value
                val converted = toHiraganaWithParticles(romajiWord)
                result.append(converted)
                i += romajiWord.length
            } else {
                // Keep Japanese characters and other symbols as-is
                result.append(text[i])
                i++
            }
        }
        
        return result.toString()
    }
}