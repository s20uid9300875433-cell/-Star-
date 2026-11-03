package com.example.live

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AdaptiveAiSnapshot(
    val cycleCount: Int = 0,
    val lastUpdatedTime: String = "Just now",
    val adaptedToneSummary: String = "Concise, polite & fast response style",
    val topUsedCategory: String = "Voice & Smart Controls",
    val isBatterySafeIntervalActive: Boolean = true
)

/**
 * Requirement L: Adaptive AI Engine for CaroLine AI A3.2.
 *
 * Every 20–30 minutes (default 25 minutes), CaroLine refreshes its cached conversation
 * preferences, response style, and configuration in a battery-safe way:
 * - Never attempts self-modifying APK behavior.
 * - Never wakes the microphone or drains CPU unnecessarily.
 */
class AdaptiveAiManager(private val context: Context) {

    companion object {
        private const val TAG = "AdaptiveAiManager"
        private const val PREFS_NAME = "caroline_adaptive_ai_a32"
        private const val KEY_CYCLE_COUNT = "adaptive_cycle_count"
        private const val KEY_TOP_CATEGORY = "adaptive_top_category"
        const val ADAPTIVE_INTERVAL_MS = 25 * 60 * 1000L // 25 minutes (within 20-30 min window)
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var periodicJob: Job? = null

    private val commandCategoryCounts = mutableMapOf<String, Int>()

    private val _snapshot = MutableStateFlow(
        AdaptiveAiSnapshot(
            cycleCount = prefs.getInt(KEY_CYCLE_COUNT, 1),
            lastUpdatedTime = formatNow(),
            topUsedCategory = prefs.getString(KEY_TOP_CATEGORY, "Voice & Smart Controls") ?: "Voice & Smart Controls"
        )
    )
    val snapshot = _snapshot.asStateFlow()

    fun startPeriodicAdaptation(onAdaptationCycle: suspend () -> Unit = {}) {
        if (periodicJob?.isActive == true) return
        periodicJob = scope.launch {
            while (isActive) {
                delay(ADAPTIVE_INTERVAL_MS)
                runAdaptationStep(onAdaptationCycle)
            }
        }
    }

    fun recordCommandCategory(category: String) {
        synchronized(commandCategoryCounts) {
            val current = commandCategoryCounts[category] ?: 0
            commandCategoryCounts[category] = current + 1
        }
    }

    suspend fun runAdaptationStep(onAdaptationCycle: suspend () -> Unit = {}) {
        try {
            val nextCycle = (_snapshot.value.cycleCount + 1)
            val topCat = synchronized(commandCategoryCounts) {
                commandCategoryCounts.maxByOrNull { it.value }?.key
            } ?: _snapshot.value.topUsedCategory

            val style = when (topCat) {
                "Music & Video" -> "Media-first quick execution & concise confirmation"
                "Contacts & Calls" -> "Safety-verified contact disambiguation"
                "Messages" -> "Polished grammar & tone enhancement"
                "Weather & Network" -> "Live meteorological & data summary"
                else -> "Balanced, natural, respectful assistant tone"
            }

            prefs.edit()
                .putInt(KEY_CYCLE_COUNT, nextCycle)
                .putString(KEY_TOP_CATEGORY, topCat)
                .apply()

            _snapshot.value = AdaptiveAiSnapshot(
                cycleCount = nextCycle,
                lastUpdatedTime = formatNow(),
                adaptedToneSummary = style,
                topUsedCategory = topCat,
                isBatterySafeIntervalActive = true
            )
            onAdaptationCycle()
            Log.d(TAG, "Adaptive AI cycle #$nextCycle completed safely (25-min interval).")
        } catch (e: Exception) {
            Log.w(TAG, "Adaptive refresh step notice: ${e.message}")
        }
    }

    fun stop() {
        periodicJob?.cancel()
        periodicJob = null
    }

    private fun formatNow(): String {
        return SimpleDateFormat("h:mm a", Locale.US).format(Date())
    }
}
