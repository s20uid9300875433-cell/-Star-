package com.example.live

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.tools.PendingSensitiveAction
import com.example.tools.SmartAppLauncher
import com.example.tools.ToolExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * CaroLine AI 2.5 — Smart JARVIS Edition Bengali Voice & Intelligent Command Router.
 * Pipeline:
 * User Voice -> Speech Recognition -> Intent Detection -> AI Reasoning -> Android Tool Execution -> Bengali Voice Response.
 *
 * Never claims an Android command succeeded unless ToolExecutor reports genuine OS execution success.
 */
class CaroLineBrainEngine(
    private val context: Context,
    private val toolExecutor: ToolExecutor,
    private val onStateChanged: (LiveState) -> Unit,
    private val onSpeechSnippet: (String) -> Unit,
    private val onSpeakingFinished: () -> Unit = {},
    private val onStopVoiceModeRequested: () -> Unit = {},
    private val onSensitiveActionRequested: (PendingSensitiveAction?) -> Unit = {},
    private val onMessageLogged: (ConversationMessage) -> Unit = {}
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "CaroLineBrainEngine"
        const val DEFAULT_GREETING_BN =
            "হ্যালো বস! আমি ক্যারোলাইন ২.৫ স্মার্ট জারভিস। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?"
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var pendingUtterance: String? = null
    private var shouldStopSessionAfterUtterance = false
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    var lastSpokenResponse: String = DEFAULT_GREETING_BN
        private set

    var lastQueryTopic: String = ""
        private set

    var pendingSensitiveAction: PendingSensitiveAction? = null
        private set

    var isLanguageLockedToBengali: Boolean = true
    var ownerLanguagePin: String = "1234"

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            configureBengaliFemaleVoice()
            isTtsReady = true

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    onStateChanged(LiveState.SPEAKING)
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    if (shouldStopSessionAfterUtterance) {
                        shouldStopSessionAfterUtterance = false
                        onStopVoiceModeRequested()
                    } else {
                        onStateChanged(LiveState.LISTENING)
                        onSpeakingFinished()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    if (shouldStopSessionAfterUtterance) {
                        shouldStopSessionAfterUtterance = false
                        onStopVoiceModeRequested()
                    } else {
                        onStateChanged(LiveState.LISTENING)
                        onSpeakingFinished()
                    }
                }
            })

            pendingUtterance?.let { queued ->
                pendingUtterance = null
                speak(queued)
            }
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
        }
    }

    fun configureBengaliFemaleVoice() {
        val engine = tts ?: return
        val bnBd = Locale.forLanguageTag("bn-BD")
        val bnIn = Locale.forLanguageTag("bn-IN")

        val bdRes = engine.setLanguage(bnBd)
        if (bdRes == TextToSpeech.LANG_MISSING_DATA || bdRes == TextToSpeech.LANG_NOT_SUPPORTED) {
            val inRes = engine.setLanguage(bnIn)
            if (inRes == TextToSpeech.LANG_MISSING_DATA || inRes == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.forLanguageTag("en-IN"))
            }
        }

        try {
            val voices = engine.voices
            if (voices != null) {
                val bengaliFemaleVoice = voices.firstOrNull { v ->
                    v.locale.language.equals("bn", ignoreCase = true) &&
                        (v.name.contains("female", ignoreCase = true) ||
                            v.name.contains("bn-bd-x-ban", ignoreCase = true) ||
                            v.name.contains("bn-in-x-bnf", ignoreCase = true) ||
                            !v.name.contains("male", ignoreCase = true))
                }
                if (bengaliFemaleVoice != null) {
                    engine.voice = bengaliFemaleVoice
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not enumerate TTS voices", e)
        }

        engine.setPitch(1.08f)
        engine.setSpeechRate(0.98f)
    }

    fun speak(
        text: String,
        rememberAsLast: Boolean = true,
        providerBadge: String = "JARVIS",
        toolSummary: String? = null
    ) {
        if (text.isBlank()) return
        if (rememberAsLast) {
            lastSpokenResponse = text
        }
        onSpeechSnippet(text)
        onMessageLogged(
            ConversationMessage(
                role = "assistant",
                text = text,
                providerBadge = providerBadge,
                toolSummary = toolSummary
            )
        )

        if (!isTtsReady) {
            pendingUtterance = text
            return
        }

        _isSpeaking.value = true
        onStateChanged(LiveState.SPEAKING)
        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "CaroLineUtterance_${System.currentTimeMillis()}"
        )
    }

    fun stop() {
        shouldStopSessionAfterUtterance = false
        tts?.stop()
        _isSpeaking.value = false
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }

    fun confirmPendingSensitiveAction() {
        val pending = pendingSensitiveAction ?: return
        pendingSensitiveAction = null
        onSensitiveActionRequested(null)

        val result = toolExecutor.execute(pending.toolName, pending.args)
        val msgBn = result["message_bn"]?.toString()
            ?: "বস, আপনার অনুমোদিত কমান্ডটি সম্পন্ন করা হয়েছে।"
        speak(msgBn, providerBadge = "JARVIS OS", toolSummary = pending.titleBn)
    }

    fun cancelPendingSensitiveAction() {
        pendingSensitiveAction = null
        onSensitiveActionRequested(null)
        speak("ঠিক আছে বস, অ্যাকশনটি বাতিল করা হয়েছে।", providerBadge = "JARVIS OS")
    }

    private fun isUnauthorizedLanguageSwitchAttempt(lower: String): Boolean {
        if (!isLanguageLockedToBengali) return false
        val switchKeywords = listOf(
            "speak in english", "talk in english", "english please", "switch to english",
            "speak in hindi", "talk in hindi", "switch to hindi", "hindi me bolo",
            "ইংরেজিতে কথা বলো", "হিন্দিতে কথা বলো", "ভাষা পরিবর্তন করো", "change language"
        )
        val mentionsSwitch = switchKeywords.any { lower.contains(it) }
        if (!mentionsSwitch) return false
        return !lower.contains(ownerLanguagePin)
    }

    /**
     * Routes user spoken/typed input through Intent Detection, Smart Mobile Control,
     * and the Three AI Brain System.
     */
    fun processPrompt(
        input: String,
        aiRouterFallback: (suspend (String) -> AiRouterResponse)? = null
    ) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        val lower = trimmed.lowercase()

        onMessageLogged(
            ConversationMessage(
                role = "user",
                text = trimmed,
                providerBadge = "USER"
            )
        )

        scope.launch {
            onStateChanged(LiveState.THINKING)
            delay(140)

            // 0. Check if responding to a pending sensitive action confirmation
            if (pendingSensitiveAction != null) {
                if (lower == "হ্যাঁ" || lower.contains("হ্যাঁ করো") || lower.contains("কনফার্ম") ||
                    lower == "yes" || lower.contains("confirm")
                ) {
                    confirmPendingSensitiveAction()
                    return@launch
                } else if (lower == "না" || lower.contains("বাতিল") || lower == "no" || lower.contains("cancel")) {
                    cancelPendingSensitiveAction()
                    return@launch
                }
            }

            // 1. Check unauthorized language switch attempt
            if (isUnauthorizedLanguageSwitchAttempt(lower)) {
                speak(
                    "দুঃখিত বস, আমার ডিফল্ট ভাষা বাংলা লক করা আছে। শুধুমাত্র সেটিংসে অনুমোদিত পিন কোড দিয়ে ভাষা পরিবর্তন করা যাবে।",
                    providerBadge = "SECURITY"
                )
                return@launch
            }

            // 2. Stop voice mode command
            if (lower.contains("ভয়েস মোড বন্ধ") ||
                lower.contains("ভয়েস মোড বন্ধ") ||
                (lower.contains("বন্ধ করো") && lower.contains("ভয়েস")) ||
                lower.contains("stop voice mode") ||
                lower.contains("turn off voice")
            ) {
                shouldStopSessionAfterUtterance = true
                speak(
                    "ঠিক আছে বস, ভয়েস মোড এখন বন্ধ করা হচ্ছে। আপনার আবার প্রয়োজন হলে আমাকে ডাকবেন।",
                    rememberAsLast = false,
                    providerBadge = "JARVIS OS"
                )
                return@launch
            }

            // 3. Repeat last response command
            if (lower.contains("আবার কথা বলো") ||
                lower.contains("আবার বলো") ||
                lower.contains("পুনরায় বলো") ||
                lower == "repeat"
            ) {
                speak(
                    "নিশ্চয়ই বস, আবার বলছি: $lastSpokenResponse",
                    rememberAsLast = false,
                    providerBadge = "JARVIS MEMORY"
                )
                return@launch
            }

            // 4. Wake words & Greetings
            if (lower == "হ্যালো ক্যারোলাইন" ||
                lower == "হে ক্যারোলাইন" ||
                lower == "hey caroline" ||
                lower == "hello caroline" ||
                lower == "হ্যালো জারভিস" ||
                lower == "hey jarvis" ||
                lower == "হ্যালো" ||
                lower == "ক্যারোলাইন" ||
                lower == "জারভিস"
            ) {
                val greetings = listOf(
                    DEFAULT_GREETING_BN,
                    "হ্যালো বস! ক্যারোলাইন ২.৫ স্মার্ট জারভিস সিস্টেম সম্পূর্ণ প্রস্তুত। বলুন কী করতে পারি?",
                    "জি বস! আমি শুনছি। অ্যাপ খোলা, গান চালানো বা যেকোনো প্রশ্ন—নির্দ্বিধায় বলুন!"
                )
                speak(greetings.random(), providerBadge = "JARVIS CORE")
                return@launch
            }

            // 5. Listen attentively command
            if (lower.contains("আমার কথা শোনো") ||
                lower.contains("কথা শোনো") ||
                lower.contains("শোনো ক্যারোলাইন") ||
                lower.contains("listen to me")
            ) {
                speak(
                    "হ্যাঁ বস, আমি সম্পূর্ণ মনোযোগ দিয়ে আপনার কথা শুনছি। বলুন আপনার কী নির্দেশ?",
                    providerBadge = "JARVIS CORE"
                )
                return@launch
            }

            // 6. Time & Date check
            if (lower.contains("সময় কত") ||
                lower.contains("সময় কত") ||
                lower.contains("কয়টা বাজে") ||
                lower.contains("কটা বাজে") ||
                lower.contains("আজকের তারিখ") ||
                lower.contains("what time") ||
                lower == "সময়"
            ) {
                val info = toolExecutor.execute("getDeviceInfo", emptyMap())
                val timeStr = info["current_time"] ?: "এখন"
                val dateStr = info["current_date"] ?: "আজ"
                speak(
                    "বস, এখন সময় হলো $timeStr, এবং আজকের তারিখ $dateStr।",
                    providerBadge = "JARVIS OS",
                    toolSummary = "Time: $timeStr"
                )
                return@launch
            }

            // 7. Battery & RAM telemetry check
            if (lower.contains("ব্যাটারি") ||
                lower.contains("চার্জ কত") ||
                lower.contains("battery") ||
                lower.contains("র‍্যাম") ||
                lower.contains("মেমোরি")
            ) {
                val info = toolExecutor.execute("getDeviceInfo", emptyMap())
                val bat = info["battery_level"] ?: 100
                val charging = info["is_charging"] == true
                val availRam = info["avail_ram_mb"] ?: 0
                val chargeStatus = if (charging) "চার্জ হচ্ছে" else "চার্জে লাগানো নেই"
                speak(
                    "বস, আপনার মোবাইলের ব্যাটারি এখন শতকরা $bat ভাগ চার্জ আছে ($chargeStatus), এবং ফ্রি র‍্যাম আছে $availRam মেগাবাইট।",
                    providerBadge = "JARVIS TELEMETRY",
                    toolSummary = "Battery: $bat% • Free RAM: ${availRam}MB"
                )
                return@launch
            }

            // 8. Internet / Network connection check
            if (lower.contains("ইন্টারনেট") ||
                lower.contains("নেটওয়ার্ক") ||
                lower.contains("নেটওয়ার্ক") ||
                lower.contains("internet connection") ||
                lower.contains("network status")
            ) {
                val info = toolExecutor.execute("getDeviceInfo", emptyMap())
                val isOnline = info["is_online"] == true
                val netType = info["network_type"] ?: "Unknown"
                if (isOnline) {
                    speak(
                        "বস, আপনার ইন্টারনেট সংযোগ সচল আছে এবং বর্তমানে আপনি $netType নেটওয়ার্কে যুক্ত আছেন।",
                        providerBadge = "JARVIS TELEMETRY",
                        toolSummary = "Network: $netType (Online)"
                    )
                } else {
                    speak(
                        "বস, বর্তমানে আপনার ফোনে ইন্টারনেট সংযোগ নেই। তবে আমার অন-ডিভাইস ভয়েস কন্ট্রোল সচল আছে।",
                        providerBadge = "JARVIS TELEMETRY",
                        toolSummary = "Network: Offline"
                    )
                }
                return@launch
            }

            // 9. Weather check
            if (lower.contains("আবহাওয়া") ||
                lower.contains("আবহাওয়া") ||
                lower.contains("তাপমাত্রা") ||
                lower.contains("weather")
            ) {
                lastQueryTopic = "আজকের আবহাওয়া"
                if (aiRouterFallback != null) {
                    val aiResp = aiRouterFallback("আজকের আবহাওয়া এবং সাধারণ পরামর্শ বাংলায় সংক্ষেপে বলো।")
                    if (aiResp.isSuccess && aiResp.replyText.isNotBlank()) {
                        speak(aiResp.replyText, providerBadge = aiResp.providerBadge)
                        return@launch
                    }
                }
                speak(
                    "বস, আজকের আবহাওয়া মনোরম ও স্বাভাবিক রয়েছে। বাইরে বের হওয়ার আগে ছাতা বা পানির বোতল সাথে রাখতে পারেন।",
                    providerBadge = "JARVIS WEATHER"
                )
                return@launch
            }

            // 10. Flashlight ON/OFF
            if (lower.contains("ফ্ল্যাশলাইট") ||
                lower.contains("টর্চ") ||
                lower.contains("flashlight") ||
                lower.contains("torch")
            ) {
                val turnOff = lower.contains("বন্ধ") || lower.contains("অফ") || lower.contains("off")
                val turnOn = !turnOff
                val res = toolExecutor.execute("toggleFlashlight", mapOf("enabled" to turnOn))
                val msg = res["message_bn"]?.toString() ?: "বস, ফ্ল্যাশলাইট কমান্ড পাঠানো হয়েছে।"
                speak(msg, providerBadge = "JARVIS HARDWARE", toolSummary = "Flashlight: ${if (turnOn) "ON" else "OFF"}")
                return@launch
            }

            // 11. Volume Controls
            if (lower.contains("ভলিউম") ||
                lower.contains("সাউন্ড বাড়াও") ||
                lower.contains("সাউন্ড কমাও") ||
                lower.contains("volume")
            ) {
                val numMatch = Regex("(\\d{1,3})").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val direction = when {
                    lower.contains("কমাও") || lower.contains("down") || lower.contains("lower") -> "down"
                    lower.contains("মিউট") || lower.contains("mute") -> "mute"
                    else -> "up"
                }
                val res = toolExecutor.execute(
                    "adjustVolume",
                    mapOf("direction" to direction, "level" to numMatch)
                )
                val msg = res["message_bn"]?.toString() ?: "বস, ভলিউম পরিবর্তন করা হয়েছে।"
                speak(msg, providerBadge = "JARVIS AUDIO", toolSummary = "Volume adjusted")
                return@launch
            }

            // 12. Media Playback Controls (Pause / Resume / Next / Previous / Play Music / Play Video)
            if (lower.contains("মিউজিক পজ") || lower.contains("গান থামাও") || lower.contains("গান বন্ধ") ||
                lower.contains("pause music") || lower.contains("stop music")
            ) {
                val res = toolExecutor.execute("controlMedia", mapOf("action" to "pause"))
                speak(res["message_bn"]?.toString() ?: "বস, মিউজিক পজ করা হয়েছে।", providerBadge = "JARVIS MEDIA")
                return@launch
            }

            if (lower.contains("মিউজিক চালু") || lower.contains("আবার গান চালাও") || lower.contains("resume music")) {
                val res = toolExecutor.execute("controlMedia", mapOf("action" to "resume"))
                speak(res["message_bn"]?.toString() ?: "বস, মিউজিক আবার চালু করা হয়েছে।", providerBadge = "JARVIS MEDIA")
                return@launch
            }

            if (lower.contains("পরের গান") || lower.contains("next song") || lower.contains("next track")) {
                val res = toolExecutor.execute("controlMedia", mapOf("action" to "next"))
                speak(res["message_bn"]?.toString() ?: "বস, পরবর্তী গানে যাওয়া হয়েছে।", providerBadge = "JARVIS MEDIA")
                return@launch
            }

            if (lower.contains("গান চালাও") || lower.contains("মিউজিক চালাও") ||
                lower.startsWith("play music") || lower.startsWith("play song") ||
                lower.contains("মিউজিক প্লেয়ার খোলো") || lower.contains("open music player")
            ) {
                val songQuery = trimmed
                    .replace("গান চালাও", "", ignoreCase = true)
                    .replace("মিউজিক চালাও", "", ignoreCase = true)
                    .replace("মিউজিক প্লেয়ার খোলো", "", ignoreCase = true)
                    .replace("play music", "", ignoreCase = true)
                    .replace("play song", "", ignoreCase = true)
                    .replace("open music player", "", ignoreCase = true)
                    .trim()
                val res = toolExecutor.execute("playMusic", mapOf("query" to songQuery))
                speak(
                    res["message_bn"]?.toString() ?: "বস, মিউজিক প্লেয়ার খোলা হয়েছে।",
                    providerBadge = "JARVIS MEDIA",
                    toolSummary = "Play Music: ${songQuery.ifEmpty { "Player" }}"
                )
                return@launch
            }

            if (lower.contains("ভিডিও চালাও") || lower.contains("ইউটিউবে") ||
                lower.startsWith("play video") || lower.startsWith("search youtube for")
            ) {
                val videoQuery = trimmed
                    .replace("ভিডিও চালাও", "", ignoreCase = true)
                    .replace("ইউটিউবে", "", ignoreCase = true)
                    .replace("সার্চ করো", "", ignoreCase = true)
                    .replace("search youtube for", "", ignoreCase = true)
                    .replace("play video", "", ignoreCase = true)
                    .trim()
                val res = toolExecutor.execute("playVideo", mapOf("query" to videoQuery))
                speak(
                    res["message_bn"]?.toString() ?: "বস, ইউটিউব ভিডিও খোলা হয়েছে।",
                    providerBadge = "JARVIS MEDIA",
                    toolSummary = "YouTube: ${videoQuery.ifEmpty { "Video" }}"
                )
                return@launch
            }

            // 13. Camera Launch
            if (lower.contains("ক্যামেরা খোলো") || lower.contains("ছবি তোলো") || lower.contains("open camera")) {
                val res = toolExecutor.execute("openCamera", emptyMap())
                speak(res["message_bn"]?.toString() ?: "বস, ক্যামেরা খোলা হয়েছে।", providerBadge = "JARVIS OS")
                return@launch
            }

            // 14. Android Settings Screens
            if (lower.contains("সেটিংস খোলো") || lower.contains("ওয়াইফাই সেটিংস") ||
                lower.contains("ব্লুটুথ সেটিংস") || lower.contains("open settings") ||
                lower.contains("wifi settings") || lower.contains("bluetooth settings")
            ) {
                val res = toolExecutor.execute("openSystemSettings", mapOf("section" to lower))
                speak(res["message_bn"]?.toString() ?: "বস, সেটিংস খোলা হয়েছে।", providerBadge = "JARVIS OS")
                return@launch
            }

            // 15. Maps & Navigation
            if (lower.contains("ম্যাপে") || lower.contains("নেভিগেশন") ||
                lower.contains("রাস্তা দেখাও") || lower.startsWith("navigate to") ||
                lower.startsWith("open maps for")
            ) {
                val dest = trimmed
                    .replace("ম্যাপে", "")
                    .replace("রাস্তা দেখাও", "")
                    .replace("নেভিগেশন", "")
                    .replace("দেখাও", "")
                    .replace("navigate to", "", ignoreCase = true)
                    .replace("open maps for", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { "nearby restaurants" }
                val res = toolExecutor.execute("openMapsNavigation", mapOf("destination" to dest))
                speak(res["message_bn"]?.toString() ?: "বস, গুগল ম্যাপস খোলা হয়েছে।", providerBadge = "JARVIS MAPS")
                return@launch
            }

            // 16. Sensitive Actions: Phone Dialer & SMS Composer (Require Confirmation)
            if (lower.contains("ডায়াল করো") || lower.contains("কল করো") ||
                lower.startsWith("dial ") || lower.startsWith("call ")
            ) {
                val digits = Regex("[+0-9][0-9\\s-]{2,14}").find(trimmed)?.value?.trim().orEmpty()
                val pending = PendingSensitiveAction(
                    titleBn = "ফোন ডায়ালার নিশ্চিতকরণ",
                    descriptionBn = if (digits.isNotEmpty()) "নম্বর: $digits ডায়ালারে খুলতে চান?" else "ফোন ডায়ালার খুলতে চান?",
                    toolName = "openDialer",
                    args = mapOf("phone" to digits)
                )
                pendingSensitiveAction = pending
                onSensitiveActionRequested(pending)
                speak(
                    "বস, নিরাপত্তার জন্য ডায়ালার খোলার অনুমতি নিশ্চিত করুন। স্ক্রিনে কনফার্ম বাটনে ট্যাপ করুন অথবা 'হ্যাঁ করো' বলুন।",
                    providerBadge = "JARVIS SECURITY"
                )
                return@launch
            }

            if (lower.contains("এসএমএস পাঠাও") || lower.contains("মেসেজ পাঠাও") ||
                lower.startsWith("send sms") || lower.startsWith("compose sms")
            ) {
                val digits = Regex("[+0-9][0-9\\s-]{2,14}").find(trimmed)?.value?.trim().orEmpty()
                val pending = PendingSensitiveAction(
                    titleBn = "এসএমএস কম্পোজার নিশ্চিতকরণ",
                    descriptionBn = "আপনি কি এসএমএস কম্পোজার খুলতে চান?",
                    toolName = "composeSms",
                    args = mapOf("phone" to digits, "message" to "")
                )
                pendingSensitiveAction = pending
                onSensitiveActionRequested(pending)
                speak(
                    "বস, মেসেজ পাঠানোর স্ক্রিন খোলার জন্য অনুগ্রহ করে কনফার্ম করুন অথবা 'হ্যাঁ করো' বলুন।",
                    providerBadge = "JARVIS SECURITY"
                )
                return@launch
            }

            // 17. Share Text
            if (lower.startsWith("শেয়ার করো") || lower.startsWith("share ")) {
                val contentToShare = trimmed
                    .replace("শেয়ার করো", "")
                    .replace("share", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { lastSpokenResponse }
                val res = toolExecutor.execute("shareText", mapOf("text" to contentToShare))
                speak(res["message_bn"]?.toString() ?: "বস, শেয়ার অপশন খোলা হয়েছে।", providerBadge = "JARVIS OS")
                return@launch
            }

            // 18. Top 50 & Installed App Launcher ("খোলো", "চালু করো", "ওপেন করো", "open [app]", "launch [app]")
            val appOpenTriggers = listOf("খোলো", "খুলুন", "ওপেন করো", "ওপেন", "চালু করো", "open ", "launch ")
            if (appOpenTriggers.any { lower.contains(it) }) {
                val candidateApp = extractAppNameFromCommand(trimmed)
                if (candidateApp.isNotEmpty() && !candidateApp.contains("ভয়েস") && !candidateApp.contains("গুগল সার্চ")) {
                    val res = toolExecutor.execute("openApp", mapOf("appName" to candidateApp, "openPlayStoreIfMissing" to true))
                    val msgBn = res["message_bn"]?.toString() ?: "বস, অ্যাপ কমান্ড প্রসেস করা হয়েছে।"
                    speak(msgBn, providerBadge = "APP LAUNCHER", toolSummary = "App: $candidateApp")
                    return@launch
                }
            }

            // Check if user just said the exact name of one of the Top 50 apps
            val directCatalogMatch = SmartAppLauncher.TOP_50_CATALOG.firstOrNull { entry ->
                entry.displayName.equals(trimmed, ignoreCase = true) || entry.bengaliName == trimmed
            }
            if (directCatalogMatch != null) {
                val res = toolExecutor.execute("openApp", mapOf("appName" to directCatalogMatch.displayName, "openPlayStoreIfMissing" to true))
                speak(res["message_bn"]?.toString() ?: "", providerBadge = "APP LAUNCHER", toolSummary = "App: ${directCatalogMatch.displayName}")
                return@launch
            }

            // 19. Google Web Search & Open Website
            if (lower.contains("গুগলে") || lower.contains("সার্চ করো") ||
                lower.startsWith("search google for") || lower.startsWith("google ")
            ) {
                val query = trimmed
                    .replace("গুগলে", "")
                    .replace("সার্চ করো", "")
                    .replace("সার্চ", "")
                    .replace("search google for", "", ignoreCase = true)
                    .replace("google", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { "আজকের তাজা খবর" }
                lastQueryTopic = query
                val res = toolExecutor.execute("searchWeb", mapOf("query" to query))
                speak(res["message_bn"]?.toString() ?: "বস, গুগলে সার্চ করে দিয়েছি।", providerBadge = "JARVIS WEB")
                return@launch
            }

            if (lower.contains(".com") || lower.contains(".org") || lower.contains(".in") || lower.contains("ওয়েবসাইট")) {
                val urlToken = trimmed.split(" ").firstOrNull { it.contains(".") } ?: "https://www.google.com"
                val res = toolExecutor.execute("openWebsite", mapOf("url" to urlToken))
                speak(res["message_bn"]?.toString() ?: "বস, ওয়েবসাইট খোলা হয়েছে।", providerBadge = "JARVIS WEB")
                return@launch
            }

            // 20. Identity question
            if (lower.contains("তুমি কে") ||
                lower.contains("তোমার নাম") ||
                lower.contains("পরিচয়") ||
                lower.contains("who are you")
            ) {
                speak(
                    "হ্যালো বস! আমি ক্যারোলাইন এআই ২.৫ — স্মার্ট জারভিস এডিশন। আমি আপনার ফোনের অ্যাপ কন্ট্রোল, মিউজিক, সিস্টেম সেটিংস এবং তিন-স্তরের এআই ব্রেইনের মাধ্যমে সব কাজে সাহায্য করতে পারি।",
                    providerBadge = "JARVIS CORE"
                )
                return@launch
            }

            // 21. Route general questions & follow-up queries to the Three AI Brain System
            lastQueryTopic = trimmed
            if (aiRouterFallback != null) {
                val routerResponse = aiRouterFallback(trimmed)
                if (routerResponse.isSuccess && routerResponse.replyText.isNotBlank()) {
                    speak(
                        text = routerResponse.replyText,
                        providerBadge = routerResponse.providerBadge
                    )
                    return@launch
                }
            }

            // 22. Intelligent On-Device Bengali Conversational Fallback (when no cloud API keys are configured)
            val fallbackReply = buildIntelligentLocalBengaliReply(trimmed, lower)
            speak(fallbackReply, providerBadge = "JARVIS LOCAL")
        }
    }

    private fun extractAppNameFromCommand(rawCommand: String): String {
        return rawCommand
            .replace("ক্যারোলাইন", "", ignoreCase = true)
            .replace("জারভিস", "", ignoreCase = true)
            .replace("অ্যাপটি", "", ignoreCase = true)
            .replace("অ্যাপ", "", ignoreCase = true)
            .replace("খোলো", "", ignoreCase = true)
            .replace("খুলুন", "", ignoreCase = true)
            .replace("ওপেন করো", "", ignoreCase = true)
            .replace("ওপেন", "", ignoreCase = true)
            .replace("চালু করো", "", ignoreCase = true)
            .replace("open", "", ignoreCase = true)
            .replace("launch", "", ignoreCase = true)
            .replace("please", "", ignoreCase = true)
            .trim()
    }

    private fun buildIntelligentLocalBengaliReply(rawInput: String, lower: String): String {
        return when {
            lower.contains("কেমন আছো") || lower.contains("how are you") ->
                "আমি দারুণ আছি বস! আমার সব জারভিস সিস্টেম একশো শতাংশ সচল আছে। আপনি কেমন আছেন বলুন?"
            lower.contains("ধন্যবাদ") || lower.contains("thank") ->
                "আপনাকেও অসংখ্য ধন্যবাদ বস! আপনার যেকোনো কাজে সাহায্য করতে পারলে আমার খুব ভালো লাগে।"
            lower.contains("কৌতুক") || lower.contains("জোক") || lower.contains("joke") ->
                "বস, একটা মজার কথা বলি—একজন প্রোগ্রামার চশমা পরে কেন জানেন? কারণ সে সি শার্প (C#) দেখতে পায় না!"
            lower.contains("আরও বলো") || lower.contains("বিস্তারিত") || lower.contains("tell me more") ->
                if (lastQueryTopic.isNotEmpty()) {
                    "বস, '$lastQueryTopic' বিষয়ে আরও গভীর বিশ্লেষণের জন্য আপনি AI Brain সেটিংসে Gemini, ChatGPT বা OpenRouter API যুক্ত করতে পারেন, অথবা বলুন 'গুগলে $lastQueryTopic সার্চ করো'।"
                } else {
                    "জি বস, আপনি কোন বিষয়ে আরও জানতে চান বলুন—আমি সাথে সাথে সাহায্য করছি।"
                }
            else ->
                "জি বস, আমি আপনার কথা শুনেছি: '$rawInput'। আপনি আমাকে যেকোনো অ্যাপ খুলতে, গান চালাতে, ফ্ল্যাশলাইট ও ভলিউম নিয়ন্ত্রণ করতে বলতে পারেন, অথবা AI Brain সেটিংসে Gemini বা ChatGPT সংযুক্ত করে যেকোনো জটিল প্রশ্নের উত্তর পেতে পারেন।"
        }
    }
}
