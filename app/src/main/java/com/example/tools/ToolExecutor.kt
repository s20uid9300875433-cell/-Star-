package com.example.tools

import android.app.ActivityManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

data class ToolExecutionEvent(
    val toolName: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean = true
)

data class PendingSensitiveAction(
    val id: String = "action_${System.currentTimeMillis()}",
    val titleBn: String, // Holds confirmation title in English/selected language
    val descriptionBn: String, // Holds confirmation details
    val toolName: String,
    val args: Map<String, Any?>
)

data class YouTubeVideoItem(
    val videoId: String,
    val title: String,
    val channel: String,
    val category: String,
    val keywords: List<String>
)

data class BuiltInMusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val notesHz: List<Double>,
    val tempoMs: Long = 320L
)

/**
 * Built-in Polyphonic Melodic Synthesizer Player.
 * Ensures audible music playback and pause/resume/next/stop controls even when no external streaming player handles the intent.
 */
class BuiltInHindiMusicPlayer {
    companion object {
        val TRACKS = listOf(
            BuiltInMusicTrack(
                id = "arijit_romantic",
                title = "Kesariya & Tum Hi Ho (Arijit Romantic Acoustic)",
                artist = "Arijit Singh Melodies",
                notesHz = listOf(261.63, 293.66, 329.63, 392.00, 440.00, 392.00, 329.63, 293.66, 261.63, 329.63, 392.00, 523.25),
                tempoMs = 300L
            ),
            BuiltInMusicTrack(
                id = "bollywood_lofi",
                title = "Bollywood Lo-Fi Chill (Raataan Lambiyan Vibe)",
                artist = "CaroLine Studio Mix",
                notesHz = listOf(293.66, 349.23, 440.00, 392.00, 349.23, 329.63, 293.66, 261.63, 349.23, 440.00, 523.25, 440.00),
                tempoMs = 280L
            ),
            BuiltInMusicTrack(
                id = "motivational_beats",
                title = "Lakshya & Sultan (High Energy Motivation)",
                artist = "Smart JARVIS Beats",
                notesHz = listOf(329.63, 329.63, 392.00, 440.00, 493.88, 523.25, 493.88, 440.00, 392.00, 440.00, 523.25, 659.25),
                tempoMs = 240L
            ),
            BuiltInMusicTrack(
                id = "classical_flute",
                title = "Peaceful Indian Classical Flute Raga",
                artist = "Instrumental Raga",
                notesHz = listOf(261.63, 293.66, 349.23, 392.00, 440.00, 523.25, 440.00, 392.00, 349.23, 293.66),
                tempoMs = 360L
            )
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var playJob: Job? = null
    private var audioTrack: AudioTrack? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    var currentTrackIndex: Int = 0
        private set

    val currentTrack: BuiltInMusicTrack
        get() = TRACKS[currentTrackIndex % TRACKS.size]

    fun playForQuery(query: String): BuiltInMusicTrack {
        val lower = query.lowercase()
        currentTrackIndex = when {
            lower.contains("अरिजीत") || lower.contains("arijit") || lower.contains("केसरिया") || lower.contains("तुम ही हो") || lower.contains("অরিজিৎ") -> 0
            lower.contains("लो-फाई") || lower.contains("lofi") || lower.contains("पसंदीदा") || lower.contains("favorite") || lower.contains("পছন্দের") -> 1
            lower.contains("मोटिवेशन") || lower.contains("motivation") || lower.contains("workout") -> 2
            lower.contains("शांत") || lower.contains("बांसुरी") || lower.contains("flute") || lower.contains("classical") -> 3
            else -> currentTrackIndex % TRACKS.size
        }
        startSynthPlayback(TRACKS[currentTrackIndex])
        return TRACKS[currentTrackIndex]
    }

    fun nextTrack(): BuiltInMusicTrack {
        currentTrackIndex = (currentTrackIndex + 1) % TRACKS.size
        val next = TRACKS[currentTrackIndex]
        startSynthPlayback(next)
        return next
    }

    fun previousTrack(): BuiltInMusicTrack {
        currentTrackIndex = if (currentTrackIndex - 1 < 0) TRACKS.size - 1 else currentTrackIndex - 1
        val prev = TRACKS[currentTrackIndex]
        startSynthPlayback(prev)
        return prev
    }

    fun pause() {
        playJob?.cancel()
        playJob = null
        _isPlaying.value = false
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Exception) {}
    }

    fun resume(): BuiltInMusicTrack {
        val track = currentTrack
        if (!_isPlaying.value) {
            startSynthPlayback(track)
        }
        return track
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        _isPlaying.value = false
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    private fun startSynthPlayback(track: BuiltInMusicTrack) {
        playJob?.cancel()
        _isPlaying.value = true
        playJob = scope.launch {
            val sampleRate = 22050
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (_: Exception) {}

            val trackOut = try {
                AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                    minBuf,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                ).also { it.play() }
            } catch (e: Exception) {
                Log.w("BuiltInHindiMusicPlayer", "AudioTrack unavailable in test env: ${e.message}")
                null
            }
            audioTrack = trackOut

            var step = 0
            while (isActive && _isPlaying.value && step < 120) {
                val freq = track.notesHz[step % track.notesHz.size]
                val durationSec = track.tempoMs / 1000.0
                val numSamples = (sampleRate * durationSec).toInt()
                val pcm = ByteArray(numSamples * 2)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val env = if (i < numSamples * 0.1) {
                        i / (numSamples * 0.1)
                    } else if (i > numSamples * 0.75) {
                        (numSamples - i) / (numSamples * 0.25)
                    } else 1.0
                    val wave = (sin(2.0 * PI * freq * t) * 0.7 + sin(2.0 * PI * (freq * 1.5) * t) * 0.2) * env
                    val sampleShort = (wave * 6500).toInt().coerceIn(-32767, 32767).toShort()
                    pcm[i * 2] = (sampleShort.toInt() and 0xFF).toByte()
                    pcm[i * 2 + 1] = ((sampleShort.toInt() shr 8) and 0xFF).toByte()
                }

                try {
                    trackOut?.write(pcm, 0, pcm.size)
                } catch (_: Exception) {
                    delay(track.tempoMs)
                }
                step++
            }
            _isPlaying.value = false
        }
    }
}

/**
 * Real Android Command Execution Engine for CaroLine AI A3.2 – Ultimate Android Smart JARVIS.
 *
 * Includes:
 * - Smart Application Launcher (50+ Catalog + Dynamic PackageManager discovery + Play Store fallback)
 * - Smart Contact & Phone Call Engine (Name lookup, partial ending digit matching, disambiguation, ACTION_DIAL default)
 * - Smart Message Writer (Grammar/tone enhancement + WhatsApp/SMS confirmation preview)
 * - Smart Weather & Internet Engine (Live Open-Meteo HTTPS API + TrafficStats data usage)
 * - Smart Music & Video Engine (MEDIA_PLAY_FROM_SEARCH, direct YouTube watch links, media keys, built-in synth)
 * - Smart Mobile Controls (Flashlight, Camera, Wi-Fi, Bluetooth, Brightness settings, Volume, Battery, Maps, Share)
 */
class ToolExecutor(private val context: Context) {
    companion object {
        private const val TAG = "ToolExecutor"

        val CURATED_YOUTUBE_VIDEOS: List<YouTubeVideoItem> = listOf(
            YouTubeVideoItem(
                videoId = "aircAruvnKk",
                title = "Neural Networks & AI Technology Explained",
                channel = "3Blue1Brown Tech",
                category = "technology",
                keywords = listOf("technology", "टेक्नोलॉजी", "टेक", "tech", "ai", "एआई", "कंप्यूटर", "coding", "कोडिंग", "science", "साइंस", "প্রযুক্তি")
            ),
            YouTubeVideoItem(
                videoId = "JMUxmLyrhSk",
                title = "Future of Artificial Intelligence & Robotics",
                channel = "Tech Vision",
                category = "technology",
                keywords = listOf("technology", "टेक्नोलॉजी", "टेक", "robotics", "रोबोटिक्स", "future", "भविष्य")
            ),
            YouTubeVideoItem(
                videoId = "ZXsQAXx_ao0",
                title = "Best Motivational Video — Never Give Up",
                channel = "Motivation Hub",
                category = "motivation",
                keywords = listOf("motivation", "motivational", "मोटिवेशन", "मोटिवेशनल", "प्रेरणा", "सफलता", "success", "study", "पढ़ाई", "মোটিভেশন")
            ),
            YouTubeVideoItem(
                videoId = "mgmVOuLgFB0",
                title = "Unstoppable Morning Motivation Speech",
                channel = "Inspire Daily",
                category = "motivation",
                keywords = listOf("motivation", "मोटिवेशन", "morning", "सुबह", "हिम्मत", "focus")
            ),
            YouTubeVideoItem(
                videoId = "BddP6PYo2gs",
                title = "Kesariya — Brahmāstra | Arijit Singh Official Video",
                channel = "Sony Music India",
                category = "music",
                keywords = listOf("arijit", "अरिजीत", "केसरिया", "kesariya", "गाना", "song", "music", "म्यूजिक", "hindi song", "हिंदी गाना", "অরিজিৎ")
            ),
            YouTubeVideoItem(
                videoId = "IJq0yyWug1k",
                title = "Tum Hi Ho — Aashiqui 2 | Arijit Singh",
                channel = "T-Series",
                category = "music",
                keywords = listOf("arijit", "अरिजीत", "तुम ही हो", "tum hi ho", "romantic", "रोमांटिक", "गाना", "पसंदीदा")
            ),
            YouTubeVideoItem(
                videoId = "sCbbMZ-q4-I",
                title = "Apna Bana Le — Bhediya | Arijit Singh & Sachin-Jigar",
                channel = "Zee Music Company",
                category = "music",
                keywords = listOf("arijit", "अरिजीत", "अपना बना ले", "apna bana le", "गाना", "song")
            ),
            YouTubeVideoItem(
                videoId = "gl1aHhXnN1k",
                title = "Top Science & Space Discoveries Documentary",
                channel = "Space & Science",
                category = "science",
                keywords = listOf("space", "अंतरिक्ष", "विज्ञान", "science", "universe", "ब्रह्मांड", "বিজ্ঞান")
            )
        )
    }

    val appLauncher = SmartAppLauncher(context)
    val builtInMusicPlayer = BuiltInHindiMusicPlayer()
    val contactCallEngine = ContactCallEngine(context)
    val smartMessageWriter = SmartMessageWriter(context)
    val weatherNetworkEngine = WeatherNetworkEngine(context)
    val performanceProfileManager = PerformanceProfileManager(context)

    private val _toolEvents = MutableSharedFlow<ToolExecutionEvent>(extraBufferCapacity = 20)
    val toolEvents = _toolEvents.asSharedFlow()

    private var isTorchOn = false

    var lastMusicQuery: String = "Arijit Singh"
        private set
    var lastVideoQuery: String = "Technology"
        private set
    var activeVideoQueue: List<YouTubeVideoItem> = CURATED_YOUTUBE_VIDEOS
        private set
    var currentVideoQueueIndex: Int = 0
        private set
    var lastPlayedVideo: YouTubeVideoItem = CURATED_YOUTUBE_VIDEOS.first()
        private set

    var preferredMusicService: String = "Auto (Smart Play)"
    var enableBuiltInMusicFallback: Boolean = true
    var useDirectCallWhenPermitted: Boolean = false

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
                val msgBn = if (result.isLaunched) {
                    "বস, আমি আপনার ফোনে ${result.appName} খুলে দিয়েছি।"
                } else {
                    "বস, আপনার ফোনে ${result.appName} ইনস্টল করা নেই। আমি প্লে স্টোর খুলে দিয়েছি।"
                }
                val msgEn = if (result.isLaunched) {
                    "Boss, I have opened ${result.appName} on your phone."
                } else {
                    "Boss, ${result.appName} is not installed. I have opened Google Play Store for you."
                }
                mapOf(
                    "status" to if (result.isLaunched) "success" else "not_installed",
                    "app_name" to result.appName,
                    "is_installed" to result.isInstalled,
                    "is_launched" to result.isLaunched,
                    "play_store_offered" to result.playStoreOffered,
                    "message_hi" to result.messageHi,
                    "message_bn" to msgBn,
                    "message_en" to msgEn
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
                val msgHi = if (success) "बॉस, मैंने ब्राउज़र में वेबसाइट खोल दी है।" else "बॉस, वेबसाइट खुल नहीं सकी।"
                val msgBn = if (success) "বস, আমি ব্রাউজারে ওয়েবসাইটটি খুলে দিয়েছি।" else "বস, ওয়েবসাইটটি খোলা যায়নি।"
                val msgEn = if (success) "Boss, I have opened the website in your browser." else "Boss, the website could not be opened."
                mapOf(
                    "status" to if (success) "success" else "error",
                    "url" to url,
                    "message_hi" to msgHi,
                    "message_bn" to msgBn,
                    "message_en" to msgEn
                )
            }

            "searchWeb" -> {
                val query = args["query"]?.toString()?.trim() ?: "latest news"
                val success = searchGoogle(query)
                val summary = if (success) "Searched Google for '$query'" else "Search failed for '$query'"
                _toolEvents.tryEmit(ToolExecutionEvent(toolName, summary, isSuccess = success))
                val msgHi = if (success) "बॉस, मैंने गूगल पर '$query' सर्च कर दिया है।" else "बॉस, गूगल सर्च खुल नहीं सका।"
                val msgBn = if (success) "বস, আমি গুগলে '$query' সার্চ করেছি।" else "বস, গুগল সার্চ খোলা যায়নি।"
                val msgEn = if (success) "Boss, I have searched Google for '$query'." else "Boss, Google search could not be opened."
                mapOf(
                    "status" to if (success) "success" else "error",
                    "query" to query,
                    "message_hi" to msgHi,
                    "message_bn" to msgBn,
                    "message_en" to msgEn
                )
            }

            "openCamera" -> {
                val success = launchCamera()
                val summary = if (success) "Opened Camera" else "Camera unavailable"
                _toolEvents.tryEmit(ToolExecutionEvent(toolName, summary, isSuccess = success))
                val msgHi = if (success) "बॉस, कैमरा खोल दिया गया है।" else "बॉस, इस डिवाइस में कैमरा ऐप नहीं खुल सका।"
                val msgBn = if (success) "বস, ক্যামেরা খুলে দেওয়া হয়েছে।" else "বস, ক্যামেরা খোলা যায়নি।"
                val msgEn = if (success) "Boss, I have opened the camera." else "Boss, the camera app could not be opened."
                mapOf(
                    "status" to if (success) "success" else "error",
                    "message_hi" to msgHi,
                    "message_bn" to msgBn,
                    "message_en" to msgEn
                )
            }

            "openSystemSettings" -> {
                val section = args["section"]?.toString()?.lowercase()?.trim().orEmpty()
                val success = launchSettingsSection(section)
                val summary = if (success) "Opened Settings ($section)" else "Failed to open Settings"
                _toolEvents.tryEmit(ToolExecutionEvent(toolName, summary, isSuccess = success))
                val isBrightness = section.contains("brightness") || section.contains("display") || section.contains("ब्राइटनेस") || section.contains("উজ্জ্বলতা")
                val msgHi = when {
                    !success -> "बॉस, सेटिंग्स स्क्रीन नहीं खुल सकी।"
                    isBrightness -> "बॉस, स्क्रीन ब्राइटनेस और डिस्प्ले सेटिंग्स खोल दी गई हैं।"
                    else -> "बॉस, फोन की सेटिंग्स खोल दी गई हैं।"
                }
                val msgBn = when {
                    !success -> "বস, সেটিংস খোলা যায়নি।"
                    isBrightness -> "বস, স্ক্রিন ব্রাইটনেস ও ডিসপ্লে সেটিংস খুলে দেওয়া হয়েছে।"
                    else -> "বস, ফোনের সেটিংস খুলে দেওয়া হয়েছে।"
                }
                val msgEn = when {
                    !success -> "Boss, could not open Android Settings."
                    isBrightness -> "Boss, I have opened the screen brightness and display settings."
                    else -> "Boss, I have opened Android system settings."
                }
                mapOf(
                    "status" to if (success) "success" else "error",
                    "section" to section,
                    "message_hi" to msgHi,
                    "message_bn" to msgBn,
                    "message_en" to msgEn
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
                val msgHi = if (success) {
                    if (enabled) "बॉस, फ्लैशलाइट चालू कर दी गई है।" else "बॉस, फ्लैशलाइट बंद कर दी गई है।"
                } else {
                    "बॉस, इस डिवाइस पर फ्लैशलाइट हार्डवेयर उपलब्ध नहीं है।"
                }
                val msgBn = if (success) {
                    if (enabled) "বস, ফ্ল্যাশলাইট চালু করা হয়েছে।" else "বস, ফ্ল্যাশলাইট বন্ধ করা হয়েছে।"
                } else {
                    "বস, এই ডিভাইসে ফ্ল্যাশলাইট হার্ডওয়্যার উপলব্ধ নেই।"
                }
                val msgEn = if (success) {
                    if (enabled) "Boss, the flashlight is now turned on." else "Boss, the flashlight is now turned off."
                } else {
                    "Boss, flashlight hardware is unavailable on this device."
                }
                mapOf(
                    "status" to if (success) "success" else "error",
                    "flashlight_enabled" to (success && enabled),
                    "message_hi" to msgHi,
                    "message_bn" to msgBn,
                    "message_en" to msgEn
                )
            }

            "getDeviceInfo" -> {
                val info = getSystemInfo()
                val summary = "Battery: ${info["battery_level"]}% • Net: ${info["network_type"]}"
                _toolEvents.tryEmit(ToolExecutionEvent(toolName, summary, isSuccess = true))
                info
            }

            "adjustVolume" -> {
                val direction = (args["direction"] ?: args["action"])?.toString()?.lowercase()?.trim().orEmpty()
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
                val msgHi = if (success) "बॉस, शेयर विकल्प खोल दिया गया है।" else "बॉस, शेयर स्क्रीन नहीं खुल सकी।"
                val msgBn = if (success) "বস, শেয়ার অপশন খুলে দেওয়া হয়েছে।" else "বস, শেয়ার স্ক্রিন খোলা যায়নি।"
                val msgEn = if (success) "Boss, I have opened the Android share sheet." else "Boss, could not open share options."
                mapOf(
                    "status" to if (success) "success" else "error",
                    "message_hi" to msgHi,
                    "message_bn" to msgBn,
                    "message_en" to msgEn
                )
            }

            "openDialer" -> {
                val phone = args["phone"]?.toString()?.trim().orEmpty()
                val direct = (args["directCall"] as? Boolean) ?: useDirectCallWhenPermitted
                val success = contactCallEngine.placeCallOrOpenDialer(phone, directCall = direct)
                _toolEvents.tryEmit(
                    ToolExecutionEvent(
                        toolName = toolName,
                        summary = if (success) "Opened Dialer ($phone)" else "Failed to open Dialer",
                        isSuccess = success
                    )
                )
                val msgHi = if (success) {
                    if (phone.isNotEmpty()) "बॉस, नंबर $phone फोन डायलर में खोल दिया गया है।" else "बॉस, फोन डायलर खोल दिया गया है।"
                } else "बॉस, डायलर नहीं खुल सका।"
                val msgBn = if (success) {
                    if (phone.isNotEmpty()) "বস, $phone নম্বরটি ডায়ালারে খোলা হয়েছে।" else "বস, ফোন ডায়ালার খোলা হয়েছে।"
                } else "বস, ডায়ালার খোলা যায়নি।"
                val msgEn = if (success) {
                    if (phone.isNotEmpty()) "Boss, I have opened $phone in the phone dialer." else "Boss, I have opened the phone dialer."
                } else "Boss, could not open the phone dialer."
                mapOf(
                    "status" to if (success) "success" else "error",
                    "phone" to phone,
                    "message_hi" to msgHi,
                    "message_bn" to msgBn,
                    "message_en" to msgEn
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
                val msgHi = if (success) "बॉस, मैसेज बॉक्स खोल दिया गया है।" else "बॉस, एसएमएस ऐप नहीं खुल सका।"
                val msgBn = if (success) "বস, মেসেজ বক্স খুলে দেওয়া হয়েছে।" else "বস, এসএমএস অ্যাপ খোলা যায়নি।"
                val msgEn = if (success) "Boss, I have opened the SMS composer." else "Boss, could not open the SMS app."
                mapOf(
                    "status" to if (success) "success" else "error",
                    "message_hi" to msgHi,
                    "message_bn" to msgBn,
                    "message_en" to msgEn
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
                val msgHi = if (success) "बॉस, गूगल मैप्स में '$destination' का रास्ता खोल दिया गया है।" else "बॉस, मैप्स नहीं खुल सका।"
                val msgBn = if (success) "বস, গুগল ম্যাপে '$destination'-এর রাস্তা খুলে দেওয়া হয়েছে।" else "বস, ম্যাপ খোলা যায়নি।"
                val msgEn = if (success) "Boss, I have opened navigation to '$destination' in Google Maps." else "Boss, could not open Google Maps."
                mapOf(
                    "status" to if (success) "success" else "error",
                    "destination" to destination,
                    "message_hi" to msgHi,
                    "message_bn" to msgBn,
                    "message_en" to msgEn
                )
            }

            "playMusic" -> {
                val rawQuery = args["query"]?.toString()?.trim().orEmpty()
                val effectiveQuery = rawQuery.ifBlank { lastMusicQuery }.ifBlank { "Arijit Singh" }
                lastMusicQuery = effectiveQuery
                val musicResult = executeAndVerifyMusicPlayback(effectiveQuery)
                _toolEvents.tryEmit(
                    ToolExecutionEvent(
                        toolName = toolName,
                        summary = musicResult["summary"]?.toString() ?: "Music: $effectiveQuery",
                        isSuccess = musicResult["status"] == "success"
                    )
                )
                musicResult
            }

            "playVideo" -> {
                val rawQuery = args["query"]?.toString()?.trim().orEmpty()
                val nextVideoFlag = args["nextVideo"] as? Boolean ?: false
                val currentTopicFlag = args["useCurrentQueue"] as? Boolean ?: false

                val videoResult = executeAndVerifyVideoPlayback(
                    query = rawQuery,
                    playNextInQueue = nextVideoFlag,
                    useCurrentQueue = currentTopicFlag
                )
                _toolEvents.tryEmit(
                    ToolExecutionEvent(
                        toolName = toolName,
                        summary = videoResult["summary"]?.toString() ?: "Video: $rawQuery",
                        isSuccess = videoResult["status"] == "success"
                    )
                )
                videoResult
            }

            "controlMedia" -> {
                val action = args["action"]?.toString()?.lowercase()?.trim() ?: "play_pause"
                val mediaRes = executeAndVerifyMediaControl(action)
                _toolEvents.tryEmit(
                    ToolExecutionEvent(
                        toolName = toolName,
                        summary = "Media control: $action (${mediaRes["status"]})",
                        isSuccess = mediaRes["status"] == "success"
                    )
                )
                mediaRes
            }

            "triggerHaptic" -> {
                vibrateDevice()
                val summary = "Haptic pulse triggered"
                _toolEvents.tryEmit(ToolExecutionEvent(toolName, summary, isSuccess = true))
                mapOf(
                    "status" to "success",
                    "message" to "Device vibrated",
                    "message_hi" to "बॉस, हैप्टिक वाइब्रेशन सक्रिय किया गया है।",
                    "message_bn" to "বস, হ্যাপটিক ভাইব্রেশন চালু করা হয়েছে।",
                    "message_en" to "Boss, haptic vibration triggered."
                )
            }

            else -> {
                Log.w(TAG, "Unknown tool: $toolName")
                mapOf("status" to "error", "message" to "Unknown tool: $toolName")
            }
        }
    }

    private fun executeAndVerifyMusicPlayback(query: String): Map<String, Any?> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        var externalIntentLaunched = false
        var launchedAppName = ""

        try {
            val playFromSearchIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolvedSearchActivity = playFromSearchIntent.resolveActivity(context.packageManager)
            if (resolvedSearchActivity != null) {
                context.startActivity(playFromSearchIntent)
                externalIntentLaunched = true
                launchedAppName = resolvedSearchActivity.packageName
            } else {
                val musicPackages = listOf(
                    "com.spotify.music" to "Spotify",
                    "com.google.android.apps.youtube.music" to "YouTube Music",
                    "com.jio.media.jiobeats" to "JioSaavn",
                    "com.bsbportal.music" to "Wynk Music",
                    "com.gaana" to "Gaana"
                )
                val installedMusicApp = musicPackages.firstOrNull { (pkg, _) ->
                    context.packageManager.getLaunchIntentForPackage(pkg) != null
                }
                if (installedMusicApp != null) {
                    val pkg = installedMusicApp.first
                    launchedAppName = installedMusicApp.second
                    val uri = when (pkg) {
                        "com.spotify.music" -> Uri.parse("spotify:search:${Uri.encode(query)}")
                        else -> Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")
                    }
                    val deepIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(deepIntent)
                        externalIntentLaunched = true
                    } catch (_: Exception) {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            externalIntentLaunched = true
                        }
                    }
                }
            }

            dispatchMediaKeyCommand("play")
        } catch (e: Exception) {
            Log.w(TAG, "External music player launch notice: ${e.message}")
        }

        val osMusicActive = audioManager?.isMusicActive == true

        val builtInTrack = if (!osMusicActive && enableBuiltInMusicFallback) {
            builtInMusicPlayer.playForQuery(query)
        } else null

        if (!externalIntentLaunched && !osMusicActive && builtInTrack == null) {
            val matchedMusicVideo = resolveMatchingYouTubeVideos(query).firstOrNull()
            if (matchedMusicVideo != null) {
                openDirectYouTubeVideo(matchedMusicVideo.videoId)
            }
        }

        val isVerifiedPlaying = osMusicActive || (builtInTrack != null && builtInMusicPlayer.isPlaying.value)
        val trackTitle = builtInTrack?.title ?: query

        val hindiMessage = when {
            isVerifiedPlaying && externalIntentLaunched ->
                "बॉस, '$query' गाना $launchedAppName पर चालू कर दिया गया है।"
            isVerifiedPlaying && builtInTrack != null ->
                "बॉस, '$trackTitle' गाना चालू कर दिया गया है।"
            externalIntentLaunched ->
                "बॉस, मैंने $launchedAppName में '$query' गाना खोल दिया है।"
            else ->
                "बॉस, '$query' गाना चलाने की कोशिश की गई, लेकिन कोई म्यूजिक प्लेयर उपलब्ध नहीं मिला।"
        }

        val bengaliMessage = when {
            isVerifiedPlaying && externalIntentLaunched ->
                "বস, $launchedAppName-এ '$query' গানটি চালু করা হয়েছে।"
            isVerifiedPlaying && builtInTrack != null ->
                "বস, '$trackTitle' গানটি চালু করা হয়েছে।"
            externalIntentLaunched ->
                "বস, আমি $launchedAppName-এ '$query' গানটি খুলে দিয়েছি।"
            else ->
                "বস, কোনো মিউজিক প্লেয়ার সক্রিয় করা যায়নি।"
        }

        val englishMessage = when {
            isVerifiedPlaying && externalIntentLaunched ->
                "Boss, playing '$query' on $launchedAppName."
            isVerifiedPlaying && builtInTrack != null ->
                "Boss, playing '$trackTitle' now."
            externalIntentLaunched ->
                "Boss, I opened '$query' in $launchedAppName."
            else ->
                "Boss, no active music player handled the request."
        }

        return mapOf(
            "status" to if (isVerifiedPlaying || externalIntentLaunched) "success" else "error",
            "query" to query,
            "track_title" to trackTitle,
            "playback_verified" to isVerifiedPlaying,
            "external_app_launched" to externalIntentLaunched,
            "summary" to if (isVerifiedPlaying) "Playing: $trackTitle" else "Music opened: $query",
            "message_hi" to hindiMessage,
            "message_bn" to bengaliMessage,
            "message_en" to englishMessage
        )
    }

    private fun executeAndVerifyMediaControl(action: String): Map<String, Any?> {
        val osDispatched = dispatchMediaKeyCommand(action)
        var builtInHandled = false
        var trackInfo = ""

        when (action) {
            "pause" -> {
                if (builtInMusicPlayer.isPlaying.value) {
                    builtInMusicPlayer.pause()
                    builtInHandled = true
                }
            }
            "stop" -> {
                builtInMusicPlayer.stop()
                builtInHandled = true
            }
            "resume", "play" -> {
                val track = builtInMusicPlayer.resume()
                builtInHandled = true
                trackInfo = track.title
            }
            "next" -> {
                val nextTrack = builtInMusicPlayer.nextTrack()
                builtInHandled = true
                trackInfo = nextTrack.title
            }
            "previous" -> {
                val prevTrack = builtInMusicPlayer.previousTrack()
                builtInHandled = true
                trackInfo = prevTrack.title
            }
        }

        val success = osDispatched || builtInHandled
        val hindiMsg = when (action) {
            "pause" -> "बॉस, गाना पॉज कर दिया गया है।"
            "stop" -> "बॉस, म्यूजिक बंद कर दिया गया है।"
            "resume", "play" -> "बॉस, म्यूजिक फिर से चालू कर दिया गया है।"
            "next" -> "बॉस, अगला गाना चला दिया गया है।"
            "previous" -> "बॉस, पिछला गाना चला दिया गया है।"
            else -> "बॉस, मीडिया कमांड पूरा कर दिया गया है।"
        }
        val bengaliMsg = when (action) {
            "pause" -> "বস, গানটি পজ করা হয়েছে।"
            "stop" -> "বস, মিউজিক বন্ধ করা হয়েছে।"
            "resume", "play" -> "বস, মিউজিক আবার চালু করা হয়েছে।"
            "next" -> "বস, পরের গানটি চালানো হয়েছে।"
            "previous" -> "বস, আগের গানটি চালানো হয়েছে।"
            else -> "বস, মিডিয়া কমান্ড সম্পন্ন হয়েছে।"
        }
        val englishMsg = when (action) {
            "pause" -> "Boss, music playback has been paused."
            "stop" -> "Boss, music playback has been stopped."
            "resume", "play" -> "Boss, music playback resumed."
            "next" -> "Boss, skipped to the next track."
            "previous" -> "Boss, returned to the previous track."
            else -> "Boss, media command completed."
        }

        return mapOf(
            "status" to if (success) "success" else "error",
            "action" to action,
            "track_title" to trackInfo,
            "playback_verified" to builtInMusicPlayer.isPlaying.value,
            "message_hi" to hindiMsg,
            "message_bn" to bengaliMsg,
            "message_en" to englishMsg
        )
    }

    private fun executeAndVerifyVideoPlayback(
        query: String,
        playNextInQueue: Boolean = false,
        useCurrentQueue: Boolean = false
    ): Map<String, Any?> {
        if (builtInMusicPlayer.isPlaying.value) {
            builtInMusicPlayer.pause()
        }

        val selectedVideo: YouTubeVideoItem = when {
            playNextInQueue -> {
                if (activeVideoQueue.isEmpty()) {
                    activeVideoQueue = CURATED_YOUTUBE_VIDEOS
                }
                currentVideoQueueIndex = (currentVideoQueueIndex + 1) % activeVideoQueue.size
                activeVideoQueue[currentVideoQueueIndex]
            }
            useCurrentQueue && query.isBlank() -> {
                activeVideoQueue.getOrElse(currentVideoQueueIndex) { lastPlayedVideo }
            }
            else -> {
                val effectiveTopic = query.ifBlank { lastVideoQuery }.ifBlank { "Technology" }
                lastVideoQuery = effectiveTopic
                val matchedList = resolveMatchingYouTubeVideos(effectiveTopic)
                activeVideoQueue = matchedList
                currentVideoQueueIndex = 0
                matchedList.first()
            }
        }

        lastPlayedVideo = selectedVideo
        val directLaunched = openDirectYouTubeVideo(selectedVideo.videoId)

        val hindiMsg = if (directLaunched) {
            "बॉस, यूट्यूब पर '${selectedVideo.title}' वीडियो सीधा चला दिया गया है।"
        } else {
            "बॉस, यूट्यूब वीडियो शुरू करने में समस्या आई।"
        }
        val bengaliMsg = if (directLaunched) {
            "বস, ইউটিউবে '${selectedVideo.title}' ভিডিওটি সরাসরি চালু করা হয়েছে।"
        } else {
            "বস, ইউটিউব ভিডিওটি চালু করা যায়নি।"
        }
        val englishMsg = if (directLaunched) {
            "Boss, playing YouTube video '${selectedVideo.title}'."
        } else {
            "Boss, could not launch the YouTube video player."
        }

        return mapOf(
            "status" to if (directLaunched) "success" else "error",
            "query" to query.ifBlank { selectedVideo.category },
            "video_id" to selectedVideo.videoId,
            "video_title" to selectedVideo.title,
            "direct_video_launched" to directLaunched,
            "summary" to "Playing YouTube Video: ${selectedVideo.title}",
            "message_hi" to hindiMsg,
            "message_bn" to bengaliMsg,
            "message_en" to englishMsg
        )
    }

    private fun resolveMatchingYouTubeVideos(query: String): List<YouTubeVideoItem> {
        val lower = query.lowercase().trim()
        if (lower.isEmpty()) return CURATED_YOUTUBE_VIDEOS

        val matches = CURATED_YOUTUBE_VIDEOS.filter { item ->
            item.title.lowercase().contains(lower) ||
                item.category.lowercase().contains(lower) ||
                item.keywords.any { kw -> lower.contains(kw) || kw.contains(lower) }
        }
        return matches.ifEmpty { CURATED_YOUTUBE_VIDEOS }
    }

    private fun openDirectYouTubeVideo(videoId: String): Boolean {
        try {
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")).apply {
                putExtra("VIDEO_ID", videoId)
                putExtra("force_fullscreen", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (appIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(appIntent)
                return true
            }
        } catch (_: Exception) {}

        return openUrl("https://www.youtube.com/watch?v=$videoId&autoplay=1")
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
            section.contains("wifi") || section.contains("वाईफाई") || section.contains("इंटरनेट") || section.contains("ওয়াইফাই") ->
                Settings.ACTION_WIFI_SETTINGS
            section.contains("bluetooth") || section.contains("ब्लूटूथ") || section.contains("ব্লুটুথ") ->
                Settings.ACTION_BLUETOOTH_SETTINGS
            section.contains("sound") || section.contains("साउंड") || section.contains("आवाज़") || section.contains("वॉल्यूम") ->
                Settings.ACTION_SOUND_SETTINGS
            section.contains("display") || section.contains("brightness") || section.contains("डिस्प्ले") || section.contains("ब्राइटनेस") || section.contains("উজ্জ্বলতা") ->
                Settings.ACTION_DISPLAY_SETTINGS
            section.contains("battery") || section.contains("बैटरी") || section.contains("ব্যাটারি") ->
                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            section.contains("location") || section.contains("लोकेशन") ->
                Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
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
        val resSnapshot = performanceProfileManager.inspectDeviceResources()

        val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US)
        val now = Date()

        return mapOf(
            "battery_level" to batteryLevel,
            "is_charging" to isCharging,
            "is_online" to networkStatus.first,
            "network_type" to networkStatus.second,
            "avail_ram_mb" to resSnapshot.availableRamMb,
            "total_ram_mb" to resSnapshot.totalRamMb,
            "recommended_profile" to resSnapshot.recommendedProfile.id,
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
                ?: return mapOf(
                    "status" to "error",
                    "message_hi" to "ऑडियो मैनेजर उपलब्ध नहीं है।",
                    "message_bn" to "অডিও ম্যানেজার উপলব্ধ নেই।",
                    "message_en" to "Audio manager is unavailable."
                )

            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

            if (targetPercent != null) {
                val bounded = targetPercent.coerceIn(0, 100)
                val targetIndex = ((bounded / 100.0) * maxVol).toInt().coerceIn(0, maxVol)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, AudioManager.FLAG_SHOW_UI)
            } else {
                when {
                    direction.contains("up") || direction.contains("बढ़ाओ") || direction.contains("तेज") || direction.contains("বাড়াও") -> {
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_RAISE,
                            AudioManager.FLAG_SHOW_UI
                        )
                    }
                    direction.contains("down") || direction.contains("कम") || direction.contains("धीमा") || direction.contains("কমাও") -> {
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_LOWER,
                            AudioManager.FLAG_SHOW_UI
                        )
                    }
                    direction.contains("mute") || direction.contains("म्यूट") || direction.contains("बंद") -> {
                        audioManager.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_MUTE,
                            AudioManager.FLAG_SHOW_UI
                        )
                    }
                    direction.contains("unmute") || direction.contains("चालू") -> {
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
                "message_hi" to "बॉस, फोन की आवाज़ अब $pct प्रतिशत पर सेट कर दी गई है।",
                "message_bn" to "বস, ফোনের ভলিউম এখন $pct শতাংশে সেট করা হয়েছে।",
                "message_en" to "Boss, media volume is now set to $pct percent."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Volume adjustment failed", e)
            mapOf(
                "status" to "error",
                "volume_percent" to 0,
                "message_hi" to "बॉस, आवाज़ बदलने में समस्या आई।",
                "message_bn" to "বস, ভলিউম পরিবর্তন করতে সমস্যা হয়েছে।",
                "message_en" to "Boss, failed to adjust volume."
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

    private fun dispatchMediaKeyCommand(action: String): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            val keyCode = when (action) {
                "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
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
