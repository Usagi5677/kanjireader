// Create new file: KanjiVGParser.kt
package com.example.kanjireader

import android.util.Log
import com.example.kanjireader.KanjiDetailAdapter.StrokeData
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import javax.xml.parsers.DocumentBuilderFactory

object KanjiVGParser {
    private const val TAG = "KanjiVGParser"

    fun parseStrokes(svgContent: String): List<StrokeData> {
        val strokes = mutableListOf<StrokeData>()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false // Important for KanjiVG files
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(svgContent.byteInputStream())

            // Get the root SVG element for viewBox
            val svgElement = doc.documentElement
            val viewBox = svgElement.getAttribute("viewBox")

            // Find all stroke paths
            val pathElements = doc.getElementsByTagName("path")

            for (i in 0 until pathElements.length) {
                val pathElement = pathElements.item(i) as? Element ?: continue
                val pathId = pathElement.getAttribute("id")

                // Look for stroke paths (format: kvg:XXXXX-s1, kvg:XXXXX-s2, etc.)
                if (pathId.contains("-s")) {
                    val strokeNumberMatch = Regex("-s(\\d+)").find(pathId)
                    val strokeNumber = strokeNumberMatch?.groupValues?.get(1)?.toIntOrNull() ?: continue
                    val pathData = pathElement.getAttribute("d")

                    if (pathData.isNotEmpty()) {
                        strokes.add(StrokeData(
                            strokeNumber = strokeNumber,
                            path = pathData,
                            viewBox = viewBox
                        ))
                    }
                }
            }

            // Sort by stroke number
            strokes.sortBy { it.strokeNumber }

            Log.d(TAG, "Parsed ${strokes.size} strokes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse SVG", e)
        }

        return strokes
    }
}