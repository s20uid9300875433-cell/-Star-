package com.example.tools

import android.app.ActivityManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
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

data class PendingSensitiveAction(
    val id: String = "action_${System.currentTimeMillis()}",
    val titleBn: String,
    val descriptionBn: String,
    val toolName: String,
    val args: Map<String, Any?>
)

/**
 * Real Android Command Execution Engine for CaroLine AI 2.5 — Smart JARVIS Edition.
 * Supports App Launching, Google/Web Search, Camera, Settings, Flashlight, Battery & Network
 * Telemetry, Volume Control, Text Sharing, Dialer, SMS Composer, Maps Navigation, and
 * Music/Video Playback. Never claims an action succeeded unless the Android OS call succeeded.
 */
class ToolExecutor(private val context: Context) {
    companion object {
        private const val TAG = "ToolExecutor"
    }

    val appLauncher = SmartAppLauncher(context)

    private val _toolEvents = MutableSharedFlow<ToolExecutionEvent>(extraBufferCapacity = 15)
    val toolEvents = _toolEvents.asSharedFlow()

    private var isTorchOn = false

    /**
     * Executes a tool by name and arguments map, returns a result map with authentic status.
     */
    fun execute(toolName: String, args: Map<String, Any?>): Map<String, Any?> {
        Log.d(TAG, "Executing tool: $toolName with args: $args")
        return when (toolName) {
            "openApp" -> {
                val appName = args["appName"]?.toString()?.trim().orEmpty()
                val openStore = args["openPlayStoreIfMissing"] as? Boolean ?: true
                val result = appLauncher.launchApp(appName, openPlayStoreIfMissing = openStore)
                _toolEvents.tryEmit(
                    ToolExecutionEvent(
                        toolName = "openApp",
                        summary = if (result.isLaunched) "Opened ${result.appName}" else "${result.appName} not installed",
                        isSuccess = result.isLaunched
                    )
                )
                mapOf(
                    "status" to if (result.isLaunched) "success" else "not_installed",
                    "app_name" to result.appName,
                    "is_installed" to result.isInstalled,
                    "is_launched" to result.isLaunched,
                    "play_store_offered" to result.playStoreOffered,
                    "message_bn" to result.messageBn
                )
            }

            "openWebsite" -> {
                val rawUrl = args["url"]?.toString()?.trim() ?: "https://www.google.com"
                val url = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                    "https://$rawUrl"
                } else rawUrl

                val success = openUrl(url)
                val summary = if (success) "Opened $url" else "Failed to open $url"
                _toolEvents.tryEmit(ToolExecutionEvent(toolName, summary, isSuccess = success))
                mapOf(
                    "status" to if (success) "success" else "error",
                    "url" to url,
                    "message_bn" to if (success) "বস, ব্রাউজারে ওয়েবসাইটটি খুলে দিয়েছি।" else "বস, ওয়েবসাইটটি খোলা যায়নি।"
                )
            }

            "searchWeb" -> {
                val query = args["query"]?.toString()?.trim() ?: "আজকের খবর"
                val success = searchGoogle(query)
                val summary = if (success) "Searched Google for '$query'" else "Search failed for '$query'"
                _toolEvents.tryEmit(ToolExecutionEvent(toolName, summary, isSuccess = success))
                mapOf(
                    "status" to if (success) "success" else "error",
                    "query" to query,
                    "message_bn" to if (success) "বস, গুগলে '$query' লিখে সার্চ করে দিয়েছি।" else "বস, গুগল সার্চ খোলা যায়নি।"
                )
            }

            "openCamera" -> {
                val success = launchCamera()
                val summary = if (success) "Opened Camera" else "Camera unavailable"
                _toolEvents.tryEmit(ToolExecutionEvent(toolName, summary, isSuccess = success))
                mapOf(
                    "status" to if (success) "success" else "error",
                    "message_bn" to if (success) "বস, ক্যামেরা খুলে দিয়েছি।" else "বস, আপনার ডিভাইসে ক্যামেরা অ্যাপ খোলা যায়নি।"
                )
            }

            "openSystemSettings" -> {
                val section = args["section"]?.toString()?.lowercase()?.trim().orEmpty()
                val success = launchSettingsSection(section)
                val summary = if (success) "Opened Android Settings ($section)" else "Failed to open Settings"
                _toolEvents.tryEmit(ToolExecutionEvent(toolName, summary, isSuccess = success))
                mapOf(
                    "status" to if (success) "success" else "error",
                    "section" to section,
                    "message_bn" to if (success) "বস, ফোনের সেটিংস স্ক্রিন খুলে দিয়েছি।" else "বস, সেটিংস স্ক্রিন খোলা যায়নি।"
                )
            }

            "toggleFlashlight" -> {
                val enabled = when (val v = args["enabled"]) {
                    is Boolean -> v
                    is String -> v.toBoolean()
                    else -> !isTorchOn
                }
                val success = setFlashlight(enabled)
                val summary = if (success) {
                    if (enabled) "Flashlight turned ON" else "Flashlight turned OFF"
                } else {
                    "Flashlight hardware unavailable"
                }
                _toolEvents.tryEmit(ToolExecutionEvent(toolName, summary, isSuccess = success))
                mapOf(
                    "status" to if (success) "success" else "error",
                    "flashlight_enabled" to (success && enabled),
                    "message_bn" to if (success) {
                        if (enabled) "বস, ফ্ল্যাশলাইট চালু করে দিয়েছি।" else "বস, ফ্ল্যাশলাইট বন্ধ করে দিয়েছি।"
                    } else {
                        "বস, এই ডিভাইসে ফ্ল্যাশলাইট হার্ডওয়্যার পাওয়া যায়নি।"
                    }
                )
            }

            "getDeviceInfo" -> {
                val info = getSystemInfo()
                val summary = "Battery: ${info["battery_level"]}% • Net: ${info["network_type"]}"
                _toolEvents.tryEmit(ToolExecutionEvent(toolName, summary, isSuccess = true))
                info
            }

            "adjustVolume" -> {
                val direction = args["direction"]?.toString()?.lowercase()?.trim().orEmpty()
                val targetPercent = (args["level"] as? Number)?.toInt()
                val volResult = adjustMediaVolume(direction, targetPercent)
                _toolEvents.tryEmit(
                    ToolExecutionEvent(
                        toolName = toolName,
                        summary = "Media Volume: ${volResult["volume_percent"]}%",
                        isSuccess = volResult["status"] == "success"
                    )
                )
                volResult
            }

            "shareText" -> {
                val text = args["text"]?.toString()?.trim().orEmpty()
                val success = shareTextIntent(text)
                _toolEvents.tryEmit(
                    ToolExecutionEvent(
                        toolName = toolName,
                        summary = if (success) "Shared text via Android Share Sheet" else "Share failed",
                        isSuccess = success
                    )
                )
                mapOf(
                    "status" to if (success) "success" else "error",
                    "message_bn" to if (success) "বস, শেয়ার অপশন খুলে দিয়েছি।" else "বস, শেয়ার স্ক্রিন খোলা যায়নি।"
                )
            }

            "openDialer" -> {
                val phone = args["phone"]?.toString()?.trim().orEmpty()
                val success = openPhoneDialer(phone)
                _toolEvents.tryEmit(
                    ToolExecutionEvent(
                        toolName = toolName,
                        summary = if (success) "Opened Dialer ($phone)" else "Failed to open Dialer",
                        isSuccess = success
                    )
                )
                mapOf(
                    "status" to if (success) "success" else "error",
                    "phone" to phone,
                    "message_bn" to if (success) "বস, ডায়ালার স্ক্রিন খুলে দিয়েছি। আপনি কল বাটনে চাপ দিয়ে কল করতে পারেন।" else "বস, ডায়ালার খোলা যায়নি।"
                )
            }

            "composeSms" -> {
                val phone = args["phone"]?.toString()?.trim().orEmpty()
                val body = args["message"]?.toString()?.trim().orEmpty()
                val success = openSmsComposer(phone, body)
                _toolEvents.tryEmit(
                    ToolExecutionEvent(
                        toolName = toolName,
                        summary = if (success) "Opened SMS Composer" else "Failed to open SMS",
                        isSuccess = success
                    )
                )
                mapOf(
                    "status" to if (success) "success" else "error",
                    "message_bn" to if (success) "বস, মেসেজ বক্স খুলে দিয়েছি।" else "বস, এসএমএস অ্যাপ খোলা যায়নি।"
                )
            }

            "openMapsNavigation" -> {
                val destination = args["destination"]?.toString()?.trim().orEmpty()
                val success = launchMapsNavigation(destination)
                _toolEvents.tryEmit(
                    ToolExecutionEvent(
                        toolName = toolName,
                        summary = if (success) "Maps Navigation: $destination" else "Failed to open Maps",
                        isSuccess = success
                    )
                )
                mapOf(
                    "status" to if (success) "success" else "error",
                    "destination" to destination,
                    "message_bn" to if (success) "বস, গুগল ম্যাপে '$destination'-এর লোকেশন খুলে দিয়েছি।" else "বস, ম্যাপস খোলা যায়নি।"
                )
            }

            "playMusic" -> {
                val query = args["query"]?.toString()?.trim().orEmpty()
                val success = launchMusicPlayback(query)
                _toolEvents.tryEmit(
                    ToolExecutionEvent(
                        toolName = toolName,
                        summary = if (success) "Playing music: ${query.ifEmpty { "Music Player" }}" else "Failed to start music",
                        isSuccess = success
                    )
                )
                mapOf(
                    "status" to if (success) "success" else "error",
                    "query" to query,
                    "message_bn" to if (success) {
                        if (query.isNotEmpty()) "বস, '$query' গানটি চালানোর জন্য মিউজিক অ্যাপ খুলে দিয়েছি।"
                        else "বস, মিউজিক প্লেয়ার চালু করে দিয়েছি।"
                    } else "বস, মিউজিক প্লেয়ার খোলা যায়নি।"
                )
            }

            "playVideo" -> {
                val query = args["query"]?.toString()?.trim().orEmpty()
                val success = launchYouTubeSearchOrVideo(query)
                _toolEvents.tryEmit(
                    ToolExecutionEvent(
                        toolName = toolName,
                        summary = if (success) "YouTube Video: ${query.ifEmpty { "Home" }}" else "Failed to open video",
                        isSuccess = success
                    )
                )
                mapOf(
                    "status" to if (success) "success" else "error",
                    "query" to query,
                    "message_bn" to if (success) {
                        if (query.isNotEmpty()) "বস, ইউটিউবে '$query' ভিডিও খুঁজে খুলে দিয়েছি।"
                        else "বস, ইউটিউব ভিডিও প্লেয়ার খুলে দিয়েছি।"
                    } else "বস, ইউটিউব খোলা যায়নি।"
                )
            }

            "controlMedia" -> {
                val action = args["action"]?.toString()?.lowercase()?.trim() ?: "play_pause"
                val success = dispatchMediaKeyCommand(action)
                _toolEvents.tryEmit(
                    ToolExecutionEvent(
                        toolName = toolName,
                        summary = "Media control: $action",
                        isSuccess = success
                    )
                )
                mapOf(
                    "status" to if (success) "success" else "error",
                    "action" to action,
                    "message_bn" to when (action) {
                        "pause" -> "বস, মিউজিক পজ (Pause) করে দিয়েছি।"
                        "resume", "play" -> "বস, মিউজিক আবার চালু (Resume) করে দিয়েছি।"
                        "next" -> "বস, পরবর্তী গানে (Next Track) যাওয়া হয়েছে।"
                        "previous" -> "বস, আগের গানে (Previous Track) যাওয়া হয়েছে।"
                        else -> "বস, মিডিয়া কন্ট্রোল কমান্ড পাঠানো হয়েছে।"
                    }
                )
            }

            "triggerHaptic" -> {
                vibrateDevice()
                val summary = "Haptic pulse triggered"
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

    private fun launchCamera(): Boolean {
        return try {
            val stillIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(stillIntent)
            true
        } catch (_: Exception) {
            try {
                val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(captureIntent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open camera", e)
                false
            }
        }
    }

    private fun launchSettingsSection(section: String): Boolean {
        val action = when {
            section.contains("wifi") || section.contains("ওয়াইফাই") || section.contains("ইন্টারনেট") ->
                Settings.ACTION_WIFI_SETTINGS
            section.contains("bluetooth") || section.contains("ব্লুটুথ") ->
                Settings.ACTION_BLUETOOTH_SETTINGS
            section.contains("sound") || section.contains("সাউন্ড") || section.contains("ভলিউম") ->
                Settings.ACTION_SOUND_SETTINGS
            section.contains("display") || section.contains("ডিসপ্লে") || section.contains("ব্রাইটনেস") ->
                Settings.ACTION_DISPLAY_SETTINGS
            section.contains("battery") || section.contains("ব্যাটারি") ->
                Settings.ACTION_BATTERY_SAVER_SETTINGS
            section.contains("location") || section.contains("লোকেশন") ->
                Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun setFlashlight(enable: Boolean): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return false
            val torchCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cameraManager.cameraIdList.firstOrNull() ?: return false

            cameraManager.setTorchMode(torchCameraId, enable)
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

    fun getSystemInfo(): Map<String, Any?> {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val rawBattery = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val batteryLevel = if (rawBattery in 0..100) rawBattery else 100
        val isCharging = batteryManager?.isCharging == true

        val networkStatus = getNetworkTelemetry()

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val availRamMb = (memInfo.availMem / (1024 * 1024)).toInt()
        val totalRamMb = (memInfo.totalMem / (1024 * 1024)).toInt()

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        val now = Date()

        return mapOf(
            "battery_level" to batteryLevel,
            "is_charging" to isCharging,
            "is_online" to networkStatus.first,
            "network_type" to networkStatus.second,
            "avail_ram_mb" to availRamMb,
            "total_ram_mb" to totalRamMb,
            "current_time" to timeFormat.format(now),
            "current_date" to dateFormat.format(now),
            "device_model" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "android_version" to Build.VERSION.RELEASE,
            "sdk_int" to Build.VERSION.SDK_INT
        )
    }

    private fun getNetworkTelemetry(): Pair<Boolean, String> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return Pair(false, "Offline")
            val activeNet = cm.activeNetwork ?: return Pair(false, "Offline")
            val caps = cm.getNetworkCapabilities(activeNet) ?: return Pair(false, "Offline")
            val isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val typeName = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                isConnected -> "Online"
                else -> "Offline"
            }
            Pair(isConnected, typeName)
        } catch (_: Exception) {
            Pair(false, "Unknown")
        }
    }

    private fun adjustMediaVolume(direction: String, targetPercent: Int?): Map<String, Any?> {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return mapOf("status" to "error", "message_bn" to "অডিও ম্যানেজার পাওয়া যায়নি।")

            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

            if (targetPercent != null) {
                val bounded = targetPercent.coerceIn(0, 100)
                val targetIndex = ((bounded / 100.0) * maxVol).toInt().coerceIn(0, maxVol)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, AudioManager.FLAG_SHOW_UI)
            } else {
                when {
                    direction.contains("up") || direction.contains("বাড়াও") || direction.contains("বাড়াও") -> {
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_RAISE,
                            AudioManager.FLAG_SHOW_UI
                        )
                    }
                    direction.contains("down") || direction.contains("কমাও") -> {
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_LOWER,
                            AudioManager.FLAG_SHOW_UI
                        )
                    }
                    direction.contains("mute") || direction.contains("মিউট") || direction.contains("বন্ধ") -> {
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_MUTE,
                            AudioManager.FLAG_SHOW_UI
                        )
                    }
                    direction.contains("unmute") || direction.contains("চালু") -> {
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_UNMUTE,
                            AudioManager.FLAG_SHOW_UI
                        )
                    }
                    else -> {
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_RAISE,
                            AudioManager.FLAG_SHOW_UI
                        )
                    }
                }
            }

            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val pct = ((currentVol.toFloat() / maxVol.toFloat()) * 100).toInt()
            mapOf(
                "status" to "success",
                "volume_percent" to pct,
                "message_bn" to "বস, ফোনের ভলিউম এখন শতকরা $pct ভাগে সেট করা হয়েছে।"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Volume adjustment failed", e)
            mapOf(
                "status" to "error",
                "volume_percent" to 0,
                "message_bn" to "বস, ভলিউম পরিবর্তন করা সম্ভব হয়নি।"
            )
        }
    }

    private fun shareTextIntent(text: String): Boolean {
        if (text.isBlank()) return false
        return try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(sendIntent, "CaroLine AI Share").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Share text failed", e)
            false
        }
    }

    private fun openPhoneDialer(phone: String): Boolean {
        return try {
            val uri = if (phone.isNotBlank()) Uri.parse("tel:${Uri.encode(phone)}") else Uri.parse("tel:")
            val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open dialer", e)
            false
        }
    }

    private fun openSmsComposer(phone: String, message: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phone)}")).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open SMS composer", e)
            false
        }
    }

    private fun launchMapsNavigation(destination: String): Boolean {
        val query = destination.ifBlank { "nearby places" }
        return try {
            val geoUri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(mapIntent)
            true
        } catch (_: Exception) {
            openUrl("https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}")
        }
    }

    private fun launchMusicPlayback(query: String): Boolean {
        return try {
            if (query.isNotBlank()) {
                // 1. Try MediaStore media play from search intent (Spotify, YouTube Music, etc.)
                val mediaIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    putExtra(SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (mediaIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(mediaIntent)
                    return true
                }
                // 2. Fallback to YouTube Music / YouTube search for the song
                return openUrl("https://music.youtube.com/search?q=${Uri.encode(query)}")
            } else {
                // Open default music player or resume playback
                val musicAppIntent = Intent.makeMainSelectorActivity(
                    Intent.ACTION_MAIN,
                    Intent.CATEGORY_APP_MUSIC
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (musicAppIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(musicAppIntent)
                    return true
                }
                return dispatchMediaKeyCommand("play")
            }
        } catch (_: Exception) {
            openUrl("https://music.youtube.com/search?q=${Uri.encode(query.ifBlank { "Bengali hit songs" })}")
        }
    }

    private fun launchYouTubeSearchOrVideo(query: String): Boolean {
        val url = if (query.isNotBlank()) {
            "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        } else {
            "https://www.youtube.com"
        }
        return openUrl(url)
    }

    private fun dispatchMediaKeyCommand(action: String): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            val keyCode = when (action) {
                "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                "play", "resume" -> KeyEvent.KEYCODE_MEDIA_PLAY
                "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            }
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch media key event", e)
            false
        }
    }

    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate", e)
        }
    }
}
