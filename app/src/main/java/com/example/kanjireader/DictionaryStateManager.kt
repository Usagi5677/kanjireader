package com.example.kanjireader

import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Manages dictionary loading state and notifies observers when dictionaries become ready.
 * This allows activities to respond to dictionary state changes even after they've been created.
 */
// Define the interface outside the object for better accessibility
interface DictionaryStateObserver {
    fun onDictionaryStateChanged(isReady: Boolean)
}

object DictionaryStateManager {
    private const val TAG = "DictionaryStateManager"

    // Thread-safe set for observers
    private val observers = CopyOnWriteArraySet<DictionaryStateObserver>()

    // Current state
    @Volatile
    private var isDictionaryReady = false

    /**
     * Add an observer to be notified of dictionary state changes.
     * If dictionaries are already ready, the observer will be notified immediately.
     */
    fun addObserver(observer: DictionaryStateObserver) {
        observers.add(observer)
        Log.d(TAG, "Observer added. Total observers: ${observers.size}")

        // If dictionaries are already ready, notify immediately
        if (isDictionaryReady) {
            Log.d(TAG, "Dictionaries already ready, notifying new observer immediately")
            observer.onDictionaryStateChanged(true)
        }
    }

    /**
     * Remove an observer
     */
    fun removeObserver(observer: DictionaryStateObserver) {
        observers.remove(observer)
        Log.d(TAG, "Observer removed. Total observers: ${observers.size}")
    }

    /**
     * Check current dictionary state
     */
    fun isDictionaryReady(): Boolean = isDictionaryReady

    /**
     * Notify that dictionaries are now ready
     */
    fun notifyDictionaryReady() {
        if (!isDictionaryReady) {
            Log.d(TAG, "Dictionaries are now ready! Notifying ${observers.size} observers")
            isDictionaryReady = true

            // Notify all observers
            observers.forEach { observer ->
                try {
                    observer.onDictionaryStateChanged(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying observer", e)
                }
            }
        }
    }

    /**
     * Reset state (for testing or app restart)
     */
    fun reset() {
        Log.d(TAG, "Resetting dictionary state")
        isDictionaryReady = false
        observers.clear()
    }

    /**
     * Check dictionary status - SQLite is always ready
     */
    fun checkAndUpdateDictionaryState() {
        // SQLite database is always ready
        val actuallyReady = true

        if (actuallyReady && !isDictionaryReady) {
            Log.d(TAG, "SQLite dictionary confirmed ready, updating state")
            notifyDictionaryReady()
        }
    }
}