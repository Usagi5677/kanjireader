package com.example.kanjireader

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GoogleTranslateService {
    
    companion object {
        private const val TAG = "GoogleTranslateService"
        private const val BASE_URL = "https://translate.googleapis.com/translate_a/single"
    }
    
    /**
     * Translate text from Japanese to English using Google Translate API
     * 
     * @param text The Japanese text to translate
     * @return The translated English text, or null if translation fails
     */
    suspend fun translateText(text: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Translating text: ${text.take(50)}${if (text.length > 50) "..." else ""}")
            
            // URL encode the text
            val encodedText = URLEncoder.encode(text, "UTF-8")
            
            // Build the URL with parameters
            val urlString = "$BASE_URL?client=gtx&sl=ja&tl=en&dt=t&q=$encodedText&ie=UTF-8&oe=UTF-8"
            
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "API Response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                
                Log.d(TAG, "API Response: $response")
                
                // Parse the JSON response
                val translatedText = parseTranslationResponse(response)
                Log.d(TAG, "Translated text: $translatedText")
                
                translatedText
            } else {
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream))
                val errorResponse = errorReader.readText()
                errorReader.close()
                
                Log.e(TAG, "API Error: $responseCode - $errorResponse")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed: ${e.message}", e)
            null
        }
    }
    
    /**
     * Parse the Google Translate API response
     * Expected format: [[["father","父親",null,null,2]],null,"ja",null,null,null,null,[]]
     */
    private fun parseTranslationResponse(response: String): String? {
        return try {
            val jsonArray = JsonParser.parseString(response).asJsonArray
            
            // The first element is an array of translation segments
            val translationSegments = jsonArray[0].asJsonArray
            
            val translatedText = StringBuilder()
            
            // Each segment is an array where the first element is the translated text
            for (segment in translationSegments) {
                val segmentArray = segment.asJsonArray
                if (segmentArray.size() > 0) {
                    val translatedSegment = segmentArray[0].asString
                    translatedText.append(translatedSegment)
                }
            }
            
            val result = translatedText.toString().trim()
            if (result.isNotEmpty()) result else null
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse translation response: ${e.message}", e)
            null
        }
    }
}