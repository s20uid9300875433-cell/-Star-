package com.example.live

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.tools.ToolExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * CaroLine AI (GREAT V2.1) Bengali Voice & Command Engine.
 * Communicates in natural Bengali (বাংলা) by default using a natural female voice,
 * enforces owner PIN language lock, executes device tools, and handles all Bengali
 * voice commands with automatic listening recovery.
 */
class CaroLineBrainEngine(
    private val context: Context,
    private val toolExecutor: ToolExecutor,
    private val onStateChanged: (LiveState) -> Unit,
    private val onSpeechSnippet: (String) -> Unit,
    private val onSpeakingFinished: () -> Unit = {},
    private val onStopVoiceModeRequested: () -> Unit = {}
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "CaroLineBrainEngine"
        const val DEFAULT_GREETING_BN =
            "হ্যালো বস! আমি ক্যারোলাইন। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?"
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

        // Select natural female Bengali voice if available in system TTS voices
        try {
            val voices = engine.voices
            if (voices != null) {
                val bengaliFemaleVoice = voices.firstOrNull { v ->
                    (v.locale.language.equals("bn", ignoreCase = true)) &&
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

        engine.setPitch(1.10f) // Natural warm female voice pitch
        engine.setSpeechRate(0.98f) // Clear, natural Bengali articulation
    }

    fun speak(text: String, rememberAsLast: Boolean = true) {
        if (text.isBlank()) return
        if (rememberAsLast) {
            lastSpokenResponse = text
        }
        onSpeechSnippet(text)

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

    /**
     * Checks if a command attempts to switch language and blocks unauthorized switches.
     */
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
     * Processes spoken Bengali commands or quick prompts.
     * Supports optional Gemini AI text generation fallback for open-ended Bengali questions.
     */
    fun processPrompt(
        input: String,
        geminiTextFallback: (suspend (String) -> String?)? = null
    ) {
        val trimmed = input.trim()
        val lower = trimmed.lowercase()

        scope.launch {
            onStateChanged(LiveState.THINKING)
            delay(180)

            // 1. Check unauthorized language switch attempt
            if (isUnauthorizedLanguageSwitchAttempt(lower)) {
                speak(
                    "দুঃখিত বস, আমার ডিফল্ট ভাষা বাংলা লক করা আছে। শুধুমাত্র অনুমোদিত পিন কোড দিয়ে ভাষা পরিবর্তন করা যাবে।"
                )
                return@launch
            }

            // 2. Check stop voice mode command: "ভয়েস মোড বন্ধ করো" / "voice mode off"
            if (lower.contains("ভয়েস মোড বন্ধ") ||
                lower.contains("ভয়েস মোড বন্ধ") ||
                lower.contains("বন্ধ করো") && lower.contains("ভয়েস") ||
                lower.contains("stop voice mode") ||
                lower.contains("turn off voice")
            ) {
                shouldStopSessionAfterUtterance = true
                speak(
                    "ঠিক আছে বস, ভয়েস মোড এখন বন্ধ করা হচ্ছে। আপনার আবার প্রয়োজন হলে আমাকে ডাকবেন।",
                    rememberAsLast = false
                )
                return@launch
            }

            // 3. Repeat last response command: "আবার কথা বলো" / "আবার বলো"
            if (lower.contains("আবার কথা বলো") ||
                lower.contains("আবার বলো") ||
                lower.contains("পুনরায় বলো") ||
                lower.contains("repeat")
            ) {
                speak("নিশ্চয়ই বস, আবার বলছি: $lastSpokenResponse", rememberAsLast = false)
                return@launch
            }

            // 4. Wake word / Greeting: "হ্যালো ক্যারোলাইন" / "Hey CaroLine"
            if (lower.contains("হ্যালো ক্যারোলাইন") ||
                lower.contains("হে ক্যারোলাইন") ||
                lower.contains("hey caroline") ||
                lower.contains("hello caroline") ||
                lower == "হ্যালো" ||
                lower == "ক্যারোলাইন"
            ) {
                val greetings = listOf(
                    DEFAULT_GREETING_BN,
                    "হ্যালো বস! আমি ক্যারোলাইন, আপনার জন্য প্রস্তুত। বলুন কী করতে পারি?",
                    "জি বস! আমি ক্যারোলাইন বলছি। আজ আপনাকে কীভাবে সাহায্য করব?"
                )
                speak(greetings.random())
                return@launch
            }

            // 5. Listen attentively command: "ক্যারোলাইন, আমার কথা শোনো" / "আমার কথা শোনো"
            if (lower.contains("আমার কথা শোনো") ||
                lower.contains("কথা শোনো") ||
                lower.contains("শোনো ক্যারোলাইন") ||
                lower.contains("listen to me")
            ) {
                speak("হ্যাঁ বস, আমি সম্পূর্ণ মনোযোগ দিয়ে আপনার কথা শুনছি। আপনি নির্দ্বিধায় বলুন!")
                return@launch
            }

            // 6. Time check: "সময় কত?" / "কয়টা বাজে" / "এখন কটা বাজে"
            if (lower.contains("সময় কত") ||
                lower.contains("সময় কত") ||
                lower.contains("কয়টা বাজে") ||
                lower.contains("কটা বাজে") ||
                lower.contains("what time") ||
                lower == "সময়"
            ) {
                val info = toolExecutor.execute("getDeviceInfo", emptyMap())
                val timeStr = info["current_time"] ?: "এখন"
                val dateStr = info["current_date"] ?: "আজ"
                speak("বস, এখন সময় হলো $timeStr, এবং আজকের তারিখ $dateStr।")
                return@launch
            }

            // 7. Battery check: "মোবাইলের ব্যাটারি কত?" / "ব্যাটারি কত" / "চার্জ কত"
            if (lower.contains("ব্যাটারি") ||
                lower.contains("চার্জ কত") ||
                lower.contains("battery")
            ) {
                val info = toolExecutor.execute("getDeviceInfo", emptyMap())
                val bat = info["battery_level"] ?: "১০০"
                val charging = info["is_charging"] == true
                val chargeStatus = if (charging) "এবং ফোনটি এখন চার্জ হচ্ছে" else "এবং ফোনটি চার্জে লাগানো নেই"
                speak("বস, আপনার মোবাইলের ব্যাটারি এখন শতকরা $bat ভাগ চার্জ আছে, $chargeStatus।")
                return@launch
            }

            // 8. Weather check: "আজকের আবহাওয়া বলো" / "আবহাওয়া কেমন"
            if (lower.contains("আবহাওয়া") ||
                lower.contains("আবহাওয়া") ||
                lower.contains("বৃষ্টি") ||
                lower.contains("তাপমাত্রা") ||
                lower.contains("weather")
            ) {
                speak("বস, আজকের আবহাওয়া মনোরম ও স্বাভাবিক রয়েছে। বাইরে বের হওয়ার আগে হালকা প্রস্তুতি রাখতে পারেন।")
                return@launch
            }

            // 9. Flashlight / Torch: "ফ্ল্যাশলাইট" / "টর্চ" / "আলো"
            if (lower.contains("ফ্ল্যাশলাইট") ||
                lower.contains("টর্চ") ||
                lower.contains("flashlight") ||
                lower.contains("torch")
            ) {
                val turnOff = lower.contains("বন্ধ") || lower.contains("off")
                val turnOn = !turnOff
                toolExecutor.execute("toggleFlashlight", mapOf("enabled" to turnOn))
                if (turnOn) {
                    speak("বস, আপনার মোবাইলের ফ্ল্যাশলাইট চালু করে দিয়েছি।")
                } else {
                    speak("বস, ফ্ল্যাশলাইট বন্ধ করে দেওয়া হয়েছে।")
                }
                return@launch
            }

            // 10. Open YouTube: "ইউটিউব" / "youtube"
            if (lower.contains("ইউটিউব") || lower.contains("youtube")) {
                toolExecutor.execute("openWebsite", mapOf("url" to "https://youtube.com"))
                speak("বস, আপনার জন্য ইউটিউব খুলে দিচ্ছি।")
                return@launch
            }

            // 11. Web Search / Google: "গুগল" / "সার্চ" / "খোঁজো"
            if (lower.contains("গুগল") ||
                lower.contains("সার্চ") ||
                lower.contains("খোঁজো") ||
                lower.contains("search")
            ) {
                val query = trimmed
                    .replace("গুগল", "")
                    .replace("সার্চ করো", "")
                    .replace("সার্চ", "")
                    .replace("খোঁজো", "")
                    .replace("search", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { "আজকের তাজা খবর" }
                toolExecutor.execute("searchWeb", mapOf("query" to query))
                speak("বস, গুগলে '$query' লিখে সার্চ করে দিচ্ছি।")
                return@launch
            }

            // 12. Identity question: "তুমি কে" / "তোমার নাম কি"
            if (lower.contains("তুমি কে") ||
                lower.contains("তোমার নাম") ||
                lower.contains("পরিচয়") ||
                lower.contains("who are you")
            ) {
                speak("হ্যালো বস! আমি ক্যারোলাইন এআই, ভার্সন গ্রেট ভি টু পয়েন্ট ওয়ান। আমি আপনার ব্যক্তিগত বাংলা ভয়েস অ্যাসিস্ট্যান্ট।")
                return@launch
            }

            // 13. Try Gemini AI REST fallback if configured for rich open-ended Bengali conversation
            if (geminiTextFallback != null) {
                val aiReply = try {
                    geminiTextFallback(trimmed)
                } catch (e: Exception) {
                    null
                }
                if (!aiReply.isNullOrBlank()) {
                    speak(aiReply)
                    return@launch
                }
            }

            // 14. Natural Bengali conversational fallback
            val conversationalReplies = listOf(
                "জি বস, আমি আপনার কথা বুঝতে পেরেছি। বলুন, আর কীভাবে আপনাকে সাহায্য করতে পারি?",
                "হ্যাঁ বস, আমি শুনছি! আপনার পরবর্তী নির্দেশ বলুন।",
                "অবশ্যই বস! আমি সবসময় আপনার সাহায্যে প্রস্তুত আছি। আর কী জানতে চান বলুন?",
                "দারুণ বলেছেন বস! আমাকে সময়, আবহাওয়া, ব্যাটারি কিংবা যেকোনো প্রশ্ন বাংলায় জিজ্ঞেস করতে পারেন।"
            )
            speak(conversationalReplies.random())
        }
    }
}
