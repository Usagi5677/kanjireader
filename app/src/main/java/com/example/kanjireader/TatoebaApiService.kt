package com.example.kanjireader

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Data models for Tatoeba API
data class TatoebaResponse(
    @SerializedName("data")
    val data: List<TatoebaSentence>
)

data class TatoebaSentence(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("text")
    val text: String,
    
    @SerializedName("lang")
    val language: String,
    
    @SerializedName("script")
    val script: String? = null,
    
    @SerializedName("license")
    val license: String? = null,
    
    @SerializedName("translations")
    val translations: List<List<TatoebaTranslation>>? = null,
    
    @SerializedName("transcriptions")
    val transcriptions: List<TatoebaTranscription>? = null,
    
    @SerializedName("owner")
    val owner: String? = null
)

data class TatoebaTranslation(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("text")
    val text: String,
    
    @SerializedName("lang")
    val language: String,
    
    @SerializedName("script")
    val script: String? = null,
    
    @SerializedName("license")
    val license: String? = null,
    
    @SerializedName("owner")
    val owner: String? = null
)

data class TatoebaTranscription(
    @SerializedName("script")
    val script: String,
    
    @SerializedName("text")
    val text: String,
    
    @SerializedName("needsReview")
    val needsReview: Boolean? = null,
    
    @SerializedName("type")
    val type: String? = null,
    
    @SerializedName("html")
    val html: String? = null
)

// Internal model for displaying sentences in the UI
data class ExampleSentence(
    val japanese: String,
    val english: String? = null,
    val sentenceId: Int,
    val searchWord: String? = null,  // Word to highlight in the sentence
    val furiganaSegments: List<FuriganaSegment>? = null  // Furigana information from our processor
)

class TatoebaApiService(private val context: Context) {
    
    private val gson = Gson()
    private val baseUrl = "https://api.tatoeba.org/unstable"
    
    // Get furigana processor using SQLite components
    private fun getFuriganaProcessor(): FuriganaProcessor {
        val deinflectionEngine = TenTenStyleDeinflectionEngine()
        val tagDictLoader = TagDictSQLiteLoader(context)
        val dictionaryRepository = DictionaryRepository.getInstance(context)
        
        return FuriganaProcessor(dictionaryRepository, deinflectionEngine, tagDictLoader)
    }
    
    /**
     * Search for sentences containing the exact word
     * @param word The Japanese word to search for
     * @param limit Maximum number of sentences to return (default: 10)
     * @return List of example sentences with Japanese text and English translations
     */
    suspend fun searchSentences(word: String, limit: Int = 10): List<ExampleSentence> {
        return withContext(Dispatchers.IO) {
            try {
                // Encode the word for URL
                val encodedWord = URLEncoder.encode(word, "UTF-8")
                
                // Build the API URL - using quotes for exact word match
                val url = "$baseUrl/sentences?" +
                        "lang=jpn&" +
                        "q=\"${encodedWord}\"&" +  // Use quotes for exact match
                        "word_count=10-30&" +
                        "trans:count=!0&" +  // Ensures sentences have translations
                        "sort=relevance&" +
                        "limit=${limit}&" +
                        "showtrans=eng"
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()
                    
                    // Parse the JSON response - wrapped in data object
                    val tatoebaResponse = gson.fromJson(response, TatoebaResponse::class.java)
                    
                    // Convert to ExampleSentence objects
                    return@withContext tatoebaResponse.data.mapNotNull { sentence ->
                        if (sentence.language == "jpn") {
                            // Find English translation - translations is a list of lists
                            val englishTranslation = sentence.translations?.firstOrNull { translationList ->
                                translationList.isNotEmpty()
                            }?.firstOrNull { translation ->
                                translation.language == "eng"
                            }?.text
                            
                            // Process furigana using our own processor
                            val furiganaProcessor = getFuriganaProcessor()
                            val furiganaSegments = furiganaProcessor?.processText(sentence.text)?.segments
                            
                            ExampleSentence(
                                japanese = sentence.text,
                                english = englishTranslation,
                                sentenceId = sentence.id,
                                searchWord = word,
                                furiganaSegments = furiganaSegments
                            )
                        } else null
                    }
                } else {
                    // Handle error response
                    val errorReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                    val errorResponse = errorReader.readText()
                    errorReader.close()
                    
                    throw Exception("API Error: $responseCode - $errorResponse")
                }
            } catch (e: Exception) {
                throw Exception("Failed to fetch sentences: ${e.message}", e)
            }
        }
    }
    
    /**
     * Alternative search method with exact word matching
     * Uses quotes around the query for more precise results
     */
    suspend fun searchSentencesByWord(word: String, limit: Int = 10): List<ExampleSentence> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedWord = URLEncoder.encode(word, "UTF-8")
                
                // Use the same endpoint but with quoted query for exact match
                val url = "$baseUrl/sentences?" +
                        "lang=jpn&" +
                        "q=\"${encodedWord}\"&" +  // Use quotes for exact match
                        "word_count=5-40&" +  // Slightly wider range
                        "trans:count=!0&" +
                        "sort=random&" +  // Random to get variety
                        "limit=${limit}&" +
                        "showtrans=eng"
                
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "KanjiReader-Android")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()
                    
                    val tatoebaResponse = gson.fromJson(response, TatoebaResponse::class.java)
                    
                    return@withContext tatoebaResponse.data.mapNotNull { sentence ->
                        if (sentence.language == "jpn") {
                            // Find English translation - translations is a list of lists
                            val englishTranslation = sentence.translations?.firstOrNull { translationList ->
                                translationList.isNotEmpty()
                            }?.firstOrNull { translation ->
                                translation.language == "eng"
                            }?.text
                            
                            // Process furigana using our own processor
                            val furiganaProcessor = getFuriganaProcessor()
                            val furiganaSegments = furiganaProcessor?.processText(sentence.text)?.segments
                            
                            ExampleSentence(
                                japanese = sentence.text,
                                english = englishTranslation,
                                sentenceId = sentence.id,
                                searchWord = word,
                                furiganaSegments = furiganaSegments
                            )
                        } else null
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                // Fallback to regular search if word-based search fails
                searchSentences(word, limit)
            }
        }
    }
}