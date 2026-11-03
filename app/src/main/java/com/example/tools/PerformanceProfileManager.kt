package com.example.tools

import android.app.ActivityManager
import android.content.Context

/**
 * Requirement A: Automatic Performance Profiles for Android 8.0–15 devices (2GB, 3GB, 4GB, 6GB+ RAM).
 * Optimizes memory footprint, animation complexity, and conversation history limits for entry-level phones
 * while preventing simultaneous heavy AI tasks, memory leaks, and ANRs.
 */
enum class PerformanceProfile(
    val id: String,
    val displayName: String,
    val ramTierDescription: String,
    val maxConversationHistory: Int,
    val maxToolHistory: Int,
    val enableHighFpsAnimations: Boolean,
    val enableParticleRings: Boolean
) {
    LITE_MODE(
        id = "LITE",
        displayName = "Lite Mode (2GB RAM)",
        ramTierDescription = "Optimized for 2GB–3GB RAM entry-level phones • Low memory & battery footprint",
        maxConversationHistory = 20,
        maxToolHistory = 10,
        enableHighFpsAnimations = false,
        enableParticleRings = false
    ),
    STANDARD_MODE(
        id = "STANDARD",
        displayName = "Standard Mode (4GB RAM)",
        ramTierDescription = "Balanced mode for 3GB–4GB RAM devices • Smooth UI & standard context cache",
        maxConversationHistory = 40,
        maxToolHistory = 18,
        enableHighFpsAnimations = true,
        enableParticleRings = false
    ),
    ADVANCED_MODE(
        id = "ADVANCED",
        displayName = "Advanced Mode (6GB+ RAM)",
        ramTierDescription = "Full holographic effects & extended context for 6GB+ RAM devices",
        maxConversationHistory = 60,
        maxToolHistory = 25,
        enableHighFpsAnimations = true,
        enableParticleRings = true
    );

    companion object {
        fun fromTotalRamMb(totalRamMb: Int, isLowMemoryDevice: Boolean = false): PerformanceProfile {
            if (isLowMemoryDevice || totalRamMb <= 2700) {
                return LITE_MODE
            }
            if (totalRamMb <= 4800) {
                return STANDARD_MODE
            }
            return ADVANCED_MODE
        }

        fun fromId(id: String?): PerformanceProfile? {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }
    }
}

data class DeviceResourceSnapshot(
    val totalRamMb: Int,
    val availableRamMb: Int,
    val isLowMemory: Boolean,
    val recommendedProfile: PerformanceProfile
)

class PerformanceProfileManager(private val context: Context) {

    fun inspectDeviceResources(): DeviceResourceSnapshot {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            val totalMb = ((memInfo.totalMem) / (1024L * 1024L)).toInt().coerceAtLeast(1024)
            val availMb = ((memInfo.availMem) / (1024L * 1024L)).toInt().coerceAtLeast(256)
            val isLowRam = memInfo.lowMemory || (am?.isLowRamDevice == true)
            val recommended = PerformanceProfile.fromTotalRamMb(totalMb, isLowRam)
            DeviceResourceSnapshot(
                totalRamMb = totalMb,
                availableRamMb = availMb,
                isLowMemory = isLowRam,
                recommendedProfile = recommended
            )
        } catch (_: Exception) {
            DeviceResourceSnapshot(
                totalRamMb = 4096,
                availableRamMb = 2048,
                isLowMemory = false,
                recommendedProfile = PerformanceProfile.STANDARD_MODE
            )
        }
    }
}
