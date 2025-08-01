package com.example.kanjireader

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Performance monitoring for search operations
 * Tracks search latency, throughput, and morphological analysis performance
 */
object SearchPerformanceMonitor {
    
    private const val TAG = "SearchPerformanceMonitor"
    private const val STATS_INTERVAL_MS = 30000L // Log stats every 30 seconds
    
    // Performance metrics
    private val searchCount = AtomicInteger(0)
    private val totalSearchTime = AtomicLong(0)
    private val morphologicalSearchCount = AtomicInteger(0)
    private val morphologicalSearchTime = AtomicLong(0)
    
    // Search type counters
    private val searchTypeCounters = ConcurrentHashMap<String, AtomicInteger>()
    private val searchTypeTimings = ConcurrentHashMap<String, AtomicLong>()
    
    // Query length distribution
    private val queryLengthDistribution = ConcurrentHashMap<Int, AtomicInteger>()
    
    // Result count distribution
    private val resultCountDistribution = ConcurrentHashMap<String, AtomicInteger>()
    
    // Performance tracking
    private var lastStatsTime = System.currentTimeMillis()
    private var isMonitoringEnabled = true
    
    /**
     * Track a search operation
     * @param searchType Type of search (e.g., "morphological", "direct", "prefix")
     * @param query The search query
     * @param resultCount Number of results found
     * @param searchTimeMs Time taken for search in milliseconds
     */
    fun trackSearch(searchType: String, query: String, resultCount: Int, searchTimeMs: Long) {
        if (!isMonitoringEnabled) return
        
        try {
            // Update overall counters
            searchCount.incrementAndGet()
            totalSearchTime.addAndGet(searchTimeMs)
            
            // Update type-specific counters
            searchTypeCounters.computeIfAbsent(searchType) { AtomicInteger(0) }.incrementAndGet()
            searchTypeTimings.computeIfAbsent(searchType) { AtomicLong(0) }.addAndGet(searchTimeMs)
            
            // Track morphological searches specifically
            if (searchType.startsWith("morphological")) {
                morphologicalSearchCount.incrementAndGet()
                morphologicalSearchTime.addAndGet(searchTimeMs)
            }
            
            // Track query length distribution
            val queryLength = query.length
            queryLengthDistribution.computeIfAbsent(queryLength) { AtomicInteger(0) }.incrementAndGet()
            
            // Track result count distribution
            val resultBucket = when {
                resultCount == 0 -> "0"
                resultCount <= 5 -> "1-5"
                resultCount <= 10 -> "6-10"
                resultCount <= 20 -> "11-20"
                resultCount <= 50 -> "21-50"
                else -> "50+"
            }
            resultCountDistribution.computeIfAbsent(resultBucket) { AtomicInteger(0) }.incrementAndGet()
            
            // Log performance warnings for slow searches
            if (searchTimeMs > 50) {
                Log.w(TAG, "Slow search detected: type='$searchType', query='$query', time=${searchTimeMs}ms, results=$resultCount")
            }
            
            // Periodic stats logging
            val now = System.currentTimeMillis()
            if (now - lastStatsTime > STATS_INTERVAL_MS) {
                logPerformanceStats()
                lastStatsTime = now
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking search performance", e)
        }
    }
    
    /**
     * Track morphological analysis performance specifically
     */
    fun trackMorphologicalAnalysis(query: String, baseForms: Int, analysisTimeMs: Long) {
        if (!isMonitoringEnabled) return
        
        try {
            Log.d(TAG, "Morphological analysis: query='$query', baseForms=$baseForms, time=${analysisTimeMs}ms")
            
            // Track in overall search metrics
            trackSearch("morphological_analysis", query, baseForms, analysisTimeMs)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking morphological analysis", e)
        }
    }
    
    /**
     * Log current performance statistics
     */
    fun logPerformanceStats() {
        if (!isMonitoringEnabled) return
        
        try {
            val totalSearches = searchCount.get()
            if (totalSearches == 0) return
            
            val avgSearchTime = totalSearchTime.get() / totalSearches
            val morphSearches = morphologicalSearchCount.get()
            val avgMorphTime = if (morphSearches > 0) morphologicalSearchTime.get() / morphSearches else 0
            
            Log.i(TAG, "=== Search Performance Stats ===")
            Log.i(TAG, "Total searches: $totalSearches")
            Log.i(TAG, "Average search time: ${avgSearchTime}ms")
            Log.i(TAG, "Morphological searches: $morphSearches (${morphSearches * 100 / totalSearches}%)")
            Log.i(TAG, "Average morphological time: ${avgMorphTime}ms")
            
            // Log search type distribution
            Log.i(TAG, "Search type distribution:")
            searchTypeCounters.entries.sortedByDescending { it.value.get() }.forEach { (type, count) ->
                val avgTime = searchTypeTimings[type]?.get()?.div(count.get()) ?: 0
                Log.i(TAG, "  $type: ${count.get()} searches (avg ${avgTime}ms)")
            }
            
            // Log query length distribution
            Log.i(TAG, "Query length distribution:")
            queryLengthDistribution.entries.sortedBy { it.key }.forEach { (length, count) ->
                Log.i(TAG, "  ${length} chars: ${count.get()} queries")
            }
            
            // Log result count distribution
            Log.i(TAG, "Result count distribution:")
            resultCountDistribution.entries.forEach { (bucket, count) ->
                Log.i(TAG, "  ${bucket} results: ${count.get()} searches")
            }
            
            Log.i(TAG, "==============================")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error logging performance stats", e)
        }
    }
    
    /**
     * Get current performance metrics
     */
    fun getPerformanceMetrics(): SearchPerformanceMetrics {
        val totalSearches = searchCount.get()
        val avgSearchTime = if (totalSearches > 0) totalSearchTime.get() / totalSearches else 0
        val morphSearches = morphologicalSearchCount.get()
        val avgMorphTime = if (morphSearches > 0) morphologicalSearchTime.get() / morphSearches else 0
        
        return SearchPerformanceMetrics(
            totalSearches = totalSearches,
            averageSearchTimeMs = avgSearchTime,
            morphologicalSearches = morphSearches,
            averageMorphologicalTimeMs = avgMorphTime,
            searchTypeDistribution = searchTypeCounters.mapValues { it.value.get() },
            queryLengthDistribution = queryLengthDistribution.mapValues { it.value.get() },
            resultCountDistribution = resultCountDistribution.mapValues { it.value.get() }
        )
    }
    
    /**
     * Reset all performance metrics
     */
    fun resetMetrics() {
        searchCount.set(0)
        totalSearchTime.set(0)
        morphologicalSearchCount.set(0)
        morphologicalSearchTime.set(0)
        searchTypeCounters.clear()
        searchTypeTimings.clear()
        queryLengthDistribution.clear()
        resultCountDistribution.clear()
        lastStatsTime = System.currentTimeMillis()
        
        Log.i(TAG, "Performance metrics reset")
    }
    
    /**
     * Enable/disable performance monitoring
     */
    fun setMonitoringEnabled(enabled: Boolean) {
        isMonitoringEnabled = enabled
        Log.i(TAG, "Performance monitoring ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Check if monitoring is enabled
     */
    fun isMonitoringEnabled(): Boolean = isMonitoringEnabled
}

/**
 * Performance metrics data class
 */
data class SearchPerformanceMetrics(
    val totalSearches: Int,
    val averageSearchTimeMs: Long,
    val morphologicalSearches: Int,
    val averageMorphologicalTimeMs: Long,
    val searchTypeDistribution: Map<String, Int>,
    val queryLengthDistribution: Map<Int, Int>,
    val resultCountDistribution: Map<String, Int>
)

/**
 * Performance tracking extension functions
 */
inline fun <T> trackSearchPerformance(
    searchType: String,
    query: String,
    operation: () -> T
): T {
    val startTime = System.currentTimeMillis()
    val result = operation()
    val endTime = System.currentTimeMillis()
    
    val resultCount = when (result) {
        is List<*> -> result.size
        is Collection<*> -> result.size
        else -> 1
    }
    
    SearchPerformanceMonitor.trackSearch(searchType, query, resultCount, endTime - startTime)
    return result
}

/**
 * Morphological analysis performance tracking
 */
inline fun <T> trackMorphologicalPerformance(
    query: String,
    operation: () -> T
): T {
    val startTime = System.currentTimeMillis()
    val result = operation()
    val endTime = System.currentTimeMillis()
    
    val baseFormCount = when (result) {
        is List<*> -> result.size
        is Collection<*> -> result.size
        else -> 1
    }
    
    SearchPerformanceMonitor.trackMorphologicalAnalysis(query, baseFormCount, endTime - startTime)
    return result
}