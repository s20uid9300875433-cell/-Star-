package com.example.tools

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ToolExecutionEvent(
    val toolName: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean = true
)

/**
 * Handles real device and browser actions triggered by Gemini Live function calls.
 */
class ToolExecutor(private val context: Context) {
    companion object {
        private const val TAG = "ToolExecutor"
    }

    private val _toolEvents = MutableSharedFlow<ToolExecutionEvent>(extraBufferCapacity = 10)
    val toolEvents = _toolEvents.asSharedFlow()

    private var isTorchOn = false

    /**
     * Executes a tool by name and arguments map, returns a result map for toolResponse.
     */
    fun execute(toolName: String, args: Map<String, Any?>): Map<String, Any?> {
        Log.d(TAG, "Executing tool: $toolName with args: $args")
        return when (toolName) {
            "openWebsite" -> {
                val rawUrl = args["url"]?.toString() ?: "https://google.com"
                val url = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                    "https://$rawUrl"
                } else rawUrl

                val success = openUrl(url)
                val summary = "Opened $url"
                _toolEvents.tryEmit(ToolExecutionEvent(toolName, summary, isSuccess = success))
                mapOf("status" to if (success) "success" else "error", "message" to summary)
            }

            "toggleFlashlight" -> {
                val enabled = when (val v = args["enabled"]) {
                    is Boolean -> v
                    is String -> v.toBoolean()
                    else -> !isTorchOn
                }
                val success = setFlashlight(enabled)
                val summary = if (enabled) "Turned flashlight ON" else "Turned flashlight OFF"
                _toolEvents.tryEmit(ToolExecutionEvent(toolName, summary, isSuccess = success))
                mapOf("status" to if (success) "success" else "error", "flashlight_enabled" to enabled)
            }

            "getDeviceInfo" -> {
                val info = getSystemInfo()
                val summary = "Battery: ${info["battery_level"]}%, Time: ${info["current_time"]}"
                _toolEvents.tryEmit(ToolExecutionEvent(toolName, summary, isSuccess = true))
                info
            }

            "searchWeb" -> {
                val query = args["query"]?.toString() ?: "Gemini AI"
                val success = searchGoogle(query)
                val summary = "Searching web for '$query'"
                _toolEvents.tryEmit(ToolExecutionEvent(toolName, summary, isSuccess = success))
                mapOf("status" to if (success) "success" else "error", "query" to query)
            }

            "triggerHaptic" -> {
                vibrateDevice()
                val summary = "Sent playful haptic buzz"
                _toolEvents.tryEmit(ToolExecutionEvent(toolName, summary, isSuccess = true))
                mapOf("status" to "success", "message" to "Device vibrated")
            }

            else -> {
                Log.w(TAG, "Unknown tool: $toolName")
                mapOf("status" to "error", "message" to "Unknown tool: $toolName")
            }
        }
    }

    private fun openUrl(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL: $url", e)
            false
        }
    }

    private fun searchGoogle(query: String): Boolean {
        val searchUrl = "https://www.google.com/search?q=" + Uri.encode(query)
        return openUrl(searchUrl)
    }

    private fun setFlashlight(enable: Boolean): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull() ?: return false
            cameraManager.setTorchMode(cameraId, enable)
            isTorchOn = enable
            true
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to toggle flashlight", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flashlight", e)
            false
        }
    }

    private fun getSystemInfo(): Map<String, Any?> {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            batteryManager?.isCharging == true
        } else false

        val timeFormat = SimpleDateFormat("h:mm a, EEEE, MMM d", Locale.getDefault())
        val currentTime = timeFormat.format(Date())

        return mapOf(
            "battery_level" to batteryLevel,
            "is_charging" to isCharging,
            "current_time" to currentTime,
            "device_model" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "android_version" to Build.VERSION.RELEASE
        )
    }

    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(120)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate", e)
        }
    }
}
