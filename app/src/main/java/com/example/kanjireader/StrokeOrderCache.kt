package com.example.kanjireader

import android.content.Context
import android.util.Log
import android.util.LruCache

object StrokeOrderCache {
    private const val TAG = "StrokeOrderCache"
    private val cache = LruCache<String, String>(100) // Cache 100 recent SVGs

    fun getSvgData(context: Context, kanji: String): String? {
        // Check cache first
        cache.get(kanji)?.let {
            Log.d(TAG, "SVG cache hit for: $kanji")
            return it
        }

        return try {
            // Convert kanji to unicode filename
            val unicode = kanji.codePointAt(0).toString(16).padStart(5, '0')
            val filename = "kanji/$unicode.svg"

            Log.d(TAG, "Loading SVG from assets: $filename")

            val svgData = context.assets.open(filename).use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            }

            // Cache for next time
            cache.put(kanji, svgData)
            Log.d(TAG, "SVG loaded and cached for: $kanji")

            svgData

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load SVG for $kanji", e)
            null
        }
    }

    fun clearCache() {
        cache.evictAll()
    }
}