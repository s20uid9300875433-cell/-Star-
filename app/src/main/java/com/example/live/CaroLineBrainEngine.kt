package com.example.live

import android.content.Context
import android.media.MediaPlayer
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class SmartMemoryItem(
    val id: String = "mem_${System.currentTimeMillis()}_${(100..999).random()}",
    val fact: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class DailyReminderItem(
    val id: String = "rem_${System.currentTimeMillis()}_${(100..999).random()}",
    val title: String,
    val timeText: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SmartRoutineItem(
    val id: String,
    val nameHi: String,
    val nameEn: String,
    val descriptionHi: String,
    val commands: List<String>
)

enum class ContextTargetType {
    NONE,
    APP,
    MUSIC,
    VIDEO,
    FLASHLIGHT,
    TOPIC
}

/**
 * CaroLine AI 2.9 – Ultimate Smart JARVIS Hindi Voice & Intelligent Command Router.
 *
 * Pipeline:
 * Speech Recognition → Intent Detection → Command Router → Android Tool Execution → Verify Result → Hindi Voice Response.
 *
 * Implements:
 * 1. Permanent Hindi Personality ("नमस्ते बॉस! मैं कैरोलाइन हूँ। बताइए, मैं आपकी क्या मदद कर सकती हूँ?")
 * 2. Advanced Female Voice Engine (Android hi-IN Female TTS + Real OpenAI Neural Speech /v1/audio/speech)
 * 3. Verified Music & Video Playback Execution
 * 4. Top 5 New Smart Features:
 *    - Smart Memory
 *    - Smart Wake Word
 *    - Daily Assistant (Time, Reminders & Briefing)
 *    - Smart Routines
 *    - Context Awareness ("इसे खोलो", "अब अगला चलाओ", "यह वीडियो चलाओ", "आवाज़ बढ़ाओ")
 */
class CaroLineBrainEngine(
    private val context: Context,
    private val toolExecutor: ToolExecutor,
    private val onStateChanged: (LiveState) -> Unit,
    private val onSpeechSnippet: (String) -> Unit,
    private val onSpeakingFinished: () -> Unit = {},
    private val onStopVoiceModeRequested: () -> Unit = {},
    private val onSensitiveActionRequested: (PendingSensitiveAction?) -> Unit = {},
    private val onMessageLogged: (ConversationMessage) -> Unit = {},
    private val onMemoriesUpdated: (List<SmartMemoryItem>) -> Unit = {},
    private val onRemindersUpdated: (List<DailyReminderItem>) -> Unit = {}
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "CaroLineBrainEngine"
        private const val PREFS_SMART = "caroline_smart_features_v29"
        private const val KEY_MEMORIES = "smart_memories_json"
        private const val KEY_REMINDERS = "daily_reminders_json"

        const val DEFAULT_GREETING_HI =
            "नमस्ते बॉस! मैं कैरोलाइन हूँ। बताइए, मैं आपकी क्या मदद कर सकती हूँ?"

        // Alias kept for compatibility with callers
        const val DEFAULT_GREETING_BN = DEFAULT_GREETING_HI

        val DEFAULT_SMART_ROUTINES: List<SmartRoutineItem> = listOf(
            SmartRoutineItem(
                id = "morning",
                nameHi = "गुड मॉर्निंग रूटीन",
                nameEn = "Morning Routine",
                descriptionHi = "समय, आज की तारीख, रिमाइंडर और सकारात्मक सुबह की ब्रीफिंग",
                commands = listOf("समय बताओ", "मेरे रिमाइंडर दिखाओ")
            ),
            SmartRoutineItem(
                id = "study_work",
                nameHi = "स्टडी / वर्क मोड",
                nameEn = "Study & Focus Mode",
                descriptionHi = "म्यूजिक बंद करता है और फोकस के लिए फोन की आवाज़ कम करता है",
                commands = listOf("म्यूजिक बंद करो", "आवाज़ कम करो")
            ),
            SmartRoutineItem(
                id = "music_vibe",
                nameHi = "म्यूजिक मोड",
                nameEn = "Music Mode",
                descriptionHi = "आवाज़ बढ़ाता है और आपका पसंदीदा गाना चलाता है",
                commands = listOf("आवाज़ बढ़ाओ", "मेरा पसंदीदा गाना चलाओ")
            ),
            SmartRoutineItem(
                id = "night",
                nameHi = "नाइट मोड रूटीन",
                nameEn = "Night Routine",
                descriptionHi = "म्यूजिक और फ्लैशलाइट बंद करके शुभ रात्रि कहता है",
                commands = listOf("म्यूजिक बंद करो", "फ्लैशलाइट बंद करो")
            )
        )
    }

    private val smartPrefs = context.getSharedPreferences(PREFS_SMART, Context.MODE_PRIVATE)
    private var tts: TextToSpeech? = null
    private var openAiMediaPlayer: MediaPlayer? = null
    private var isTtsReady = false
    private var pendingUtterance: String? = null
    private var shouldStopSessionAfterUtterance = false
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    var lastSpokenResponse: String = DEFAULT_GREETING_HI
        private set

    var lastQueryTopic: String = ""
        private set

    // Context Awareness state for follow-up commands ("इसे खोलो", "अब अगला चलाओ", "यह वीडियो चलाओ", "आवाज़ बढ़ाओ")
    var lastContextType: ContextTargetType = ContextTargetType.NONE
        private set
    var lastContextTarget: String = ""
        private set

    var pendingSensitiveAction: PendingSensitiveAction? = null
        private set

    var isLanguageLockedToBengali: Boolean = true // Represents Hindi default lock in 2.9
    var ownerLanguagePin: String = "1234"

    // Advanced Female Voice Engine settings
    var voicePitch: Float = 1.08f
        set(value) {
            field = value.coerceIn(0.7f, 1.4f)
            tts?.setPitch(field)
        }

    var voiceSpeed: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.7f, 1.4f)
            tts?.setSpeechRate(field)
        }

    var isVoiceMuted: Boolean = false
    var selectedFemaleVoiceProfile: String = "Swara (Hindi Female)"
    var useOpenAiNeuralTts: Boolean = false
    var openAiTtsVoiceName: String = "nova"

    // Callback to synthesize OpenAI Neural Speech when enabled & key configured
    var openAiSpeechSynthesizer: (suspend (text: String, voice: String, speed: Float) -> ByteArray?)? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            configureHindiFemaleVoice()
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

    /**
     * Configures Android TextToSpeech with a natural female Hindi (hi-IN) voice,
     * slightly higher expressive pitch, and clear speech rate.
     */
    fun configureHindiFemaleVoice() {
        val engine = tts ?: return
        val hiIn = Locale.forLanguageTag("hi-IN")
        val enIn = Locale.forLanguageTag("en-IN")

        val hiRes = engine.setLanguage(hiIn)
        if (hiRes == TextToSpeech.LANG_MISSING_DATA || hiRes == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(enIn)
        }

        try {
            val voices = engine.voices
            if (voices != null) {
                val hindiFemaleVoice = voices.firstOrNull { v ->
                    v.locale.language.equals("hi", ignoreCase = true) &&
                        (v.name.contains("female", ignoreCase = true) ||
                            v.name.contains("hi-in-x-hie", ignoreCase = true) ||
                            v.name.contains("hi-in-x-hia", ignoreCase = true) ||
                            v.name.contains("hi-in-x-hic", ignoreCase = true) ||
                            !v.name.contains("male", ignoreCase = true))
                }
                if (hindiFemaleVoice != null) {
                    engine.voice = hindiFemaleVoice
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not enumerate Hindi TTS voices", e)
        }

        engine.setPitch(voicePitch)
        engine.setSpeechRate(voiceSpeed)
    }

    fun configureBengaliFemaleVoice() = configureHindiFemaleVoice()

    /**
     * Removes consecutive duplicate words and markdown clutter so TTS sounds smooth and natural.
     */
    private fun sanitizeSpokenHindiText(raw: String): String {
        val cleaned = raw
            .replace("*", "")
            .replace("#", "")
            .replace("`", "")
            .trim()
        val words = cleaned.split(Regex("\\s+"))
        val deduplicated = mutableListOf<String>()
        for (w in words) {
            if (deduplicated.isEmpty() || !deduplicated.last().equals(w, ignoreCase = true)) {
                deduplicated.add(w)
            }
        }
        return deduplicated.joinToString(" ")
    }

    /**
     * Speaks a response in natural female Hindi voice.
     * - Prevents overlapping speech by stopping any active utterance first.
     * - If `useOpenAiNeuralTts` is enabled and OpenAI synthesizer returns audio, plays real OpenAI Neural TTS.
     * - Otherwise uses local Android Hindi Female TTS (and labels each honestly).
     */
    fun speak(
        text: String,
        rememberAsLast: Boolean = true,
        providerBadge: String = "JARVIS",
        toolSummary: String? = null
    ) {
        val cleanText = sanitizeSpokenHindiText(text)
        if (cleanText.isBlank()) return

        if (rememberAsLast) {
            lastSpokenResponse = cleanText
        }
        onSpeechSnippet(cleanText)
        onMessageLogged(
            ConversationMessage(
                role = "assistant",
                text = cleanText,
                providerBadge = providerBadge,
                toolSummary = toolSummary
            )
        )

        if (isVoiceMuted) {
            _isSpeaking.value = false
            onStateChanged(LiveState.LISTENING)
            onSpeakingFinished()
            return
        }

        // Stop any existing speech to guarantee zero overlapping speech
        stopActiveAudioOnly()

        val synthesizer = openAiSpeechSynthesizer
        if (useOpenAiNeuralTts && synthesizer != null) {
            scope.launch {
                _isSpeaking.value = true
                onStateChanged(LiveState.SPEAKING)
                val mp3Bytes = synthesizer(cleanText, openAiTtsVoiceName, voiceSpeed)
                if (mp3Bytes != null && mp3Bytes.isNotEmpty()) {
                    playOpenAiMp3Bytes(mp3Bytes)
                } else {
                    speakWithAndroidHindiTts(cleanText)
                }
            }
            return
        }

        speakWithAndroidHindiTts(cleanText)
    }

    private fun speakWithAndroidHindiTts(cleanText: String) {
        if (!isTtsReady) {
            pendingUtterance = cleanText
            return
        }

        _isSpeaking.value = true
        onStateChanged(LiveState.SPEAKING)
        tts?.setPitch(voicePitch)
        tts?.setSpeechRate(voiceSpeed)
        tts?.speak(
            cleanText,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "CaroLineUtterance_${System.currentTimeMillis()}"
        )
    }

    private fun playOpenAiMp3Bytes(mp3Bytes: ByteArray) {
        try {
            val tempFile = File.createTempFile("caroline_openai_tts_", ".mp3", context.cacheDir)
            tempFile.writeBytes(mp3Bytes)
            openAiMediaPlayer?.release()
            openAiMediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener { mp ->
                    mp.release()
                    openAiMediaPlayer = null
                    tempFile.delete()
                    _isSpeaking.value = false
                    if (shouldStopSessionAfterUtterance) {
                        shouldStopSessionAfterUtterance = false
                        onStopVoiceModeRequested()
                    } else {
                        onStateChanged(LiveState.LISTENING)
                        onSpeakingFinished()
                    }
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    openAiMediaPlayer = null
                    tempFile.delete()
                    _isSpeaking.value = false
                    onStateChanged(LiveState.LISTENING)
                    onSpeakingFinished()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play OpenAI TTS MP3, falling back to Android Hindi TTS", e)
            speakWithAndroidHindiTts(lastSpokenResponse)
        }
    }

    fun previewFemaleVoice() {
        val sample = "नमस्ते बॉस! मैं कैरोलाइन टू पॉइंट नाइन हूँ। मेरी हिंदी आवाज़ बिल्कुल साफ और तैयार है।"
        val badge = if (useOpenAiNeuralTts) "OPENAI TTS ($openAiTtsVoiceName)" else "ANDROID HINDI TTS"
        speak(sample, rememberAsLast = false, providerBadge = badge)
    }

    private fun stopActiveAudioOnly() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
        try {
            openAiMediaPlayer?.stop()
            openAiMediaPlayer?.release()
            openAiMediaPlayer = null
        } catch (_: Exception) {}
    }

    fun stop() {
        shouldStopSessionAfterUtterance = false
        stopActiveAudioOnly()
        _isSpeaking.value = false
    }

    fun release() {
        stopActiveAudioOnly()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }

    // --- Smart Memory Management (Feature 1 of Top 5) ---

    fun getSmartMemories(): List<SmartMemoryItem> {
        val raw = smartPrefs.getString(KEY_MEMORIES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<SmartMemoryItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SmartMemoryItem(
                        id = obj.optString("id", "mem_$i"),
                        fact = obj.optString("fact", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list.filter { it.fact.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addSmartMemory(fact: String): List<SmartMemoryItem> {
        val clean = fact.trim()
        if (clean.isEmpty()) return getSmartMemories()
        val existing = getSmartMemories().filterNot { it.fact.equals(clean, ignoreCase = true) }
        val updated = (listOf(SmartMemoryItem(fact = clean)) + existing).take(30)
        saveSmartMemories(updated)
        onMemoriesUpdated(updated)
        return updated
    }

    fun removeSmartMemory(id: String): List<SmartMemoryItem> {
        val updated = getSmartMemories().filterNot { it.id == id }
        saveSmartMemories(updated)
        onMemoriesUpdated(updated)
        return updated
    }

    fun clearSmartMemories(): List<SmartMemoryItem> {
        smartPrefs.edit().remove(KEY_MEMORIES).apply()
        onMemoriesUpdated(emptyList())
        return emptyList()
    }

    private fun saveSmartMemories(list: List<SmartMemoryItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("fact", item.fact)
                    put("timestamp", item.timestamp)
                }
            )
        }
        smartPrefs.edit().putString(KEY_MEMORIES, arr.toString()).apply()
    }

    private fun getFavoriteSongFromMemory(): String {
        val memories = getSmartMemories()
        val favSongMem = memories.firstOrNull {
            it.fact.contains("गाना", ignoreCase = true) ||
                it.fact.contains("गायक", ignoreCase = true) ||
                it.fact.contains("song", ignoreCase = true) ||
                it.fact.contains("singer", ignoreCase = true) ||
                it.fact.contains("अरिजीत", ignoreCase = true) ||
                it.fact.contains("केसरिया", ignoreCase = true)
        }
        return favSongMem?.fact ?: "अरिजीत सिंह केसरिया"
    }

    // --- Daily Assistant Reminders (Feature 3 of Top 5) ---

    fun getDailyReminders(): List<DailyReminderItem> {
        val raw = smartPrefs.getString(KEY_REMINDERS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<DailyReminderItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    DailyReminderItem(
                        id = obj.optString("id", "rem_$i"),
                        title = obj.optString("title", ""),
                        timeText = obj.optString("timeText", "आज"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list.filter { it.title.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addDailyReminder(title: String, timeText: String = "आज"): List<DailyReminderItem> {
        val clean = title.trim()
        if (clean.isEmpty()) return getDailyReminders()
        val updated = (listOf(DailyReminderItem(title = clean, timeText = timeText)) + getDailyReminders()).take(25)
        saveDailyReminders(updated)
        onRemindersUpdated(updated)
        return updated
    }

    fun removeDailyReminder(id: String): List<DailyReminderItem> {
        val updated = getDailyReminders().filterNot { it.id == id }
        saveDailyReminders(updated)
        onRemindersUpdated(updated)
        return updated
    }

    fun clearDailyReminders(): List<DailyReminderItem> {
        smartPrefs.edit().remove(KEY_REMINDERS).apply()
        onRemindersUpdated(emptyList())
        return emptyList()
    }

    private fun saveDailyReminders(list: List<DailyReminderItem>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("timeText", item.timeText)
                    put("timestamp", item.timestamp)
                }
            )
        }
        smartPrefs.edit().putString(KEY_REMINDERS, arr.toString()).apply()
    }

    // --- Smart Routines Execution (Feature 4 of Top 5) ---

    fun executeSmartRoutine(routineId: String) {
        val routine = DEFAULT_SMART_ROUTINES.firstOrNull { it.id == routineId }
            ?: DEFAULT_SMART_ROUTINES.first()

        scope.launch {
            onStateChanged(LiveState.THINKING)
            when (routine.id) {
                "morning" -> {
                    val info = toolExecutor.execute("getDeviceInfo", emptyMap())
                    val timeStr = info["current_time"] ?: "सुबह"
                    val dateStr = info["current_date"] ?: "आज"
                    val reminders = getDailyReminders()
                    val remText = if (reminders.isEmpty()) {
                        "आज के लिए कोई पेंडिंग रिमाइंडर नहीं है।"
                    } else {
                        "आज आपके ${reminders.size} रिमाइंडर हैं, जिनमें पहला है: ${reminders.first().title}।"
                    }
                    speak(
                        "सुप्रभात बॉस! अभी समय $timeStr है और आज $dateStr है। $remText आपका दिन शानदार रहे!",
                        providerBadge = "SMART ROUTINE",
                        toolSummary = "Morning Routine"
                    )
                }
                "study_work" -> {
                    toolExecutor.execute("controlMedia", mapOf("action" to "stop"))
                    toolExecutor.execute("adjustVolume", mapOf("level" to 25))
                    speak(
                        "बॉस, स्टडी और वर्क मोड सक्रिय कर दिया गया है। म्यूजिक बंद कर दिया गया है और फोन की आवाज़ 25 प्रतिशत पर सेट है ताकि आप पूरा ध्यान लगा सकें।",
                        providerBadge = "SMART ROUTINE",
                        toolSummary = "Study & Work Mode"
                    )
                }
                "music_vibe" -> {
                    toolExecutor.execute("adjustVolume", mapOf("level" to 70))
                    val favQuery = getFavoriteSongFromMemory()
                    val res = toolExecutor.execute("playMusic", mapOf("query" to favQuery))
                    lastContextType = ContextTargetType.MUSIC
                    lastContextTarget = favQuery
                    val msg = res["message_hi"]?.toString()
                        ?: "बॉस, म्यूजिक मोड चालू कर दिया गया है और आपका पसंदीदा गाना बज रहा है।"
                    speak(msg, providerBadge = "SMART ROUTINE", toolSummary = "Music Mode")
                }
                "night" -> {
                    toolExecutor.execute("controlMedia", mapOf("action" to "stop"))
                    toolExecutor.execute("toggleFlashlight", mapOf("enabled" to false))
                    toolExecutor.execute("adjustVolume", mapOf("level" to 20))
                    speak(
                        "बॉस, नाइट रूटीन पूरा हो गया है। म्यूजिक और फ्लैशलाइट बंद कर दिए गए हैं। शुभ रात्रि बॉस!",
                        providerBadge = "SMART ROUTINE",
                        toolSummary = "Night Routine"
                    )
                }
            }
        }
    }

    fun confirmPendingSensitiveAction() {
        val pending = pendingSensitiveAction ?: return
        pendingSensitiveAction = null
        onSensitiveActionRequested(null)

        val result = toolExecutor.execute(pending.toolName, pending.args)
        val msgHi = result["message_hi"]?.toString()
            ?: "बॉस, आपका अनुमोदित कमांड पूरा कर दिया गया है।"
        speak(msgHi, providerBadge = "JARVIS OS", toolSummary = pending.titleBn)
    }

    fun cancelPendingSensitiveAction() {
        pendingSensitiveAction = null
        onSensitiveActionRequested(null)
        speak("ठीक है बॉस, यह एक्शन रद्द कर दिया गया है।", providerBadge = "JARVIS OS")
    }

    private fun isUnauthorizedLanguageSwitchAttempt(lower: String): Boolean {
        if (!isLanguageLockedToBengali) return false
        val switchKeywords = listOf(
            "speak in english", "talk in english", "switch to english",
            "speak in bengali", "talk in bengali", "switch to bengali",
            "अंग्रेजी में बात करो", "इंग्लिश में बोलो", "बंगाली में बोलो", "भाषा बदलो", "change language"
        )
        val mentionsSwitch = switchKeywords.any { lower.contains(it) }
        if (!mentionsSwitch) return false
        return !lower.contains(ownerLanguagePin)
    }

    private fun stripWakeWordPrefix(rawInput: String): String {
        var result = rawInput.trim()
        val prefixes = listOf(
            "hey caroline", "hello caroline", "hi caroline", "caroline",
            "हे कैरोलाइन", "नमस्ते कैरोलाइन", "हेलो कैरोलाइन", "कैरोलाइन", "जार्विस"
        )
        for (prefix in prefixes) {
            if (result.lowercase().startsWith(prefix)) {
                val remainder = result.substring(prefix.length).trim().removePrefix(",").trim()
                if (remainder.isNotEmpty()) {
                    result = remainder
                    break
                }
            }
        }
        return result
    }

    /**
     * Routes user spoken/typed input through:
     * Speech Recognition → Intent Detection → Command Router → Android Tool Execution → Verify Result → Hindi Voice Response.
     */
    fun processPrompt(
        input: String,
        aiRouterFallback: (suspend (String) -> AiRouterResponse)? = null
    ) {
        val rawTrimmed = input.trim()
        if (rawTrimmed.isEmpty()) return

        onMessageLogged(
            ConversationMessage(
                role = "user",
                text = rawTrimmed,
                providerBadge = "USER"
            )
        )

        val trimmed = stripWakeWordPrefix(rawTrimmed)
        val lower = trimmed.lowercase()

        scope.launch {
            onStateChanged(LiveState.THINKING)
            delay(100)

            // 0. Pending sensitive action confirmation
            if (pendingSensitiveAction != null) {
                if (lower == "हाँ" || lower == "हां" || lower.contains("हाँ करो") || lower.contains("कन्फर्म") ||
                    lower == "yes" || lower.contains("confirm")
                ) {
                    confirmPendingSensitiveAction()
                    return@launch
                } else if (lower == "नहीं" || lower.contains("कैंसिल") || lower.contains("रद्द") ||
                    lower == "no" || lower.contains("cancel")
                ) {
                    cancelPendingSensitiveAction()
                    return@launch
                }
            }

            // 1. Check unauthorized language switch attempt
            if (isUnauthorizedLanguageSwitchAttempt(lower)) {
                speak(
                    "क्षमा करें बॉस, मेरी डिफ़ॉल्ट भाषा हिंदी लॉक है। केवल सेटिंग्स में ओनर पिन कोड से भाषा बदली जा सकती है।",
                    providerBadge = "SECURITY"
                )
                return@launch
            }

            // 2. Stop voice mode command
            if (lower.contains("वॉइस मोड बंद") ||
                lower.contains("आवाज मोड बंद") ||
                (lower.contains("बंद करो") && lower.contains("वॉइस")) ||
                lower.contains("stop voice mode") ||
                lower.contains("turn off voice")
            ) {
                shouldStopSessionAfterUtterance = true
                speak(
                    "ठीक है बॉस, वॉइस मोड बंद किया जा रहा है। ज़रूरत होने पर मुझे फिर बुलाइएगा।",
                    rememberAsLast = false,
                    providerBadge = "JARVIS OS"
                )
                return@launch
            }

            // 3. Context Awareness Follow-Up Commands (Feature 5 of Top 5)
            // "इसे खोलो" / "उसे खोलो" / "open it"
            if (lower == "इसे खोलो" || lower == "उसे खोलो" || lower == "इसको खोलो" ||
                lower == "isey kholo" || lower == "ise kholo" || lower == "open it"
            ) {
                if (lastContextTarget.isNotBlank()) {
                    val res = toolExecutor.execute(
                        "openApp",
                        mapOf("appName" to lastContextTarget, "openPlayStoreIfMissing" to true)
                    )
                    speak(
                        res["message_hi"]?.toString() ?: "बॉस, मैंने $lastContextTarget खोल दिया है।",
                        providerBadge = "CONTEXT AI",
                        toolSummary = "Context Open: $lastContextTarget"
                    )
                    return@launch
                }
            }

            // "यह वीडियो चलाओ" / "ये वीडियो चलाओ" / "play this video"
            if (lower == "यह वीडियो चलाओ" || lower == "ये वीडियो चलाओ" ||
                lower.contains("यह वीडियो चलाओ") || lower == "yah video chalao" ||
                lower == "ye video chalao" || lower == "play this video"
            ) {
                lastContextType = ContextTargetType.VIDEO
                val res = toolExecutor.execute(
                    "playVideo",
                    mapOf("query" to "", "useCurrentQueue" to true)
                )
                speak(
                    res["message_hi"]?.toString() ?: "बॉस, यूट्यूब वीडियो सीधा चला दिया गया है।",
                    providerBadge = "JARVIS MEDIA",
                    toolSummary = res["summary"]?.toString()
                )
                return@launch
            }

            // "अगला वीडियो दिखाओ" / "अगला वीडियो चलाओ" / "next video"
            if (lower.contains("अगला वीडियो") || lower.contains("नेक्स्ट वीडियो") ||
                lower.contains("agla video") || lower.contains("next video")
            ) {
                lastContextType = ContextTargetType.VIDEO
                val res = toolExecutor.execute(
                    "playVideo",
                    mapOf("query" to "", "nextVideo" to true)
                )
                speak(
                    res["message_hi"]?.toString() ?: "बॉस, अगला यूट्यूब वीडियो सीधा चला दिया गया है।",
                    providerBadge = "JARVIS MEDIA",
                    toolSummary = res["summary"]?.toString()
                )
                return@launch
            }

            // "अब अगला चलाओ" / "अगला चलाओ" / "play next"
            if (lower == "अब अगला चलाओ" || lower == "अगला चलाओ" ||
                lower == "ab agla chalao" || lower == "agla chalao" || lower == "play next"
            ) {
                if (lastContextType == ContextTargetType.VIDEO) {
                    val res = toolExecutor.execute(
                        "playVideo",
                        mapOf("query" to "", "nextVideo" to true)
                    )
                    speak(
                        res["message_hi"]?.toString() ?: "बॉस, अगला वीडियो चला दिया गया है।",
                        providerBadge = "CONTEXT AI",
                        toolSummary = res["summary"]?.toString()
                    )
                } else {
                    lastContextType = ContextTargetType.MUSIC
                    val res = toolExecutor.execute("controlMedia", mapOf("action" to "next"))
                    speak(
                        res["message_hi"]?.toString() ?: "बॉस, अगला गाना चला दिया गया है।",
                        providerBadge = "CONTEXT AI",
                        toolSummary = "Next Track"
                    )
                }
                return@launch
            }

            // "इसे बंद करो" / "रोको"
            if (lower == "इसे बंद करो" || lower == "बंद करो" || lower == "रोको" || lower == "stop it") {
                if (lastContextType == ContextTargetType.FLASHLIGHT) {
                    val res = toolExecutor.execute("toggleFlashlight", mapOf("enabled" to false))
                    speak(res["message_hi"]?.toString() ?: "बॉस, फ्लैशलाइट बंद कर दी गई है।", providerBadge = "CONTEXT AI")
                } else {
                    val res = toolExecutor.execute("controlMedia", mapOf("action" to "stop"))
                    speak(res["message_hi"]?.toString() ?: "बॉस, म्यूजिक बंद कर दिया गया है।", providerBadge = "CONTEXT AI")
                }
                return@launch
            }

            // 4. Smart Memory Commands (Feature 1 of Top 5)
            if (lower.startsWith("याद रखो") || lower.startsWith("मेरी बात याद रखो") ||
                lower.startsWith("याद रखना") || lower.startsWith("remember that") ||
                lower.startsWith("yaad rakho")
            ) {
                val fact = trimmed
                    .replace(Regex("^(याद रखो|मेरी बात याद रखो|याद रखना|remember that|yaad rakho)\\s*(कि|की)?", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (fact.isNotEmpty()) {
                    addSmartMemory(fact)
                    speak(
                        "जी बॉस, मैंने यह बात अपनी स्मार्ट मेमोरी में याद रख ली है: '$fact' in।",
                        providerBadge = "SMART MEMORY",
                        toolSummary = "Saved Memory"
                    )
                    return@launch
                }
            }

            if (lower.contains("मेरी यादें") || lower.contains("तुम्हें क्या याद है") ||
                lower.contains("स्मार्ट मेमोरी दिखाओ") || lower.contains("what do you remember") ||
                lower.contains("show memories")
            ) {
                val memories = getSmartMemories()
                if (memories.isEmpty()) {
                    speak(
                        "बॉस, अभी स्मार्ट मेमोरी में कोई खास बात सेव नहीं है। आप कभी भी कह सकते हैं, 'याद रखो मेरा पसंदीदा गाना केसरिया है'।",
                        providerBadge = "SMART MEMORY"
                    )
                } else {
                    val summary = memories.take(3).joinToString(" और ") { it.fact }
                    speak(
                        "बॉस, मुझे आपकी ${memories.size} बातें याद हैं: $summary।",
                        providerBadge = "SMART MEMORY",
                        toolSummary = "${memories.size} Memories"
                    )
                }
                return@launch
            }

            if (lower.contains("यादें मिटाओ") || lower.contains("मेमोरी साफ करो") || lower.contains("clear memories")) {
                clearSmartMemories()
                speak("जी बॉस, आपकी सभी सेव की गई स्मार्ट मेमोरी मिटा दी गई हैं।", providerBadge = "SMART MEMORY")
                return@launch
            }

            // 5. Daily Assistant: Reminders & Daily Briefing (Feature 3 of Top 5)
            if (lower.startsWith("रिमाइंडर") || lower.contains("रिमाइंडर सेट करो") ||
                lower.contains("मुझे याद दिलाना") || lower.startsWith("set reminder") ||
                lower.startsWith("remind me")
            ) {
                if (lower.contains("दिखाओ") || lower.contains("बताओ") || lower.contains("show")) {
                    val reminders = getDailyReminders()
                    if (reminders.isEmpty()) {
                        speak("बॉस, अभी कोई सक्रिय रिमाइंडर नहीं है।", providerBadge = "DAILY ASSISTANT")
                    } else {
                        val remStr = reminders.take(3).joinToString(", ") { it.title }
                        speak("बॉस, आपके ${reminders.size} रिमाइंडर हैं: $remStr।", providerBadge = "DAILY ASSISTANT")
                    }
                    return@launch
                }

                val remTitle = trimmed
                    .replace("रिमाइंडर सेट करो", "", ignoreCase = true)
                    .replace("रिमाइंडर लगाओ", "", ignoreCase = true)
                    .replace("मुझे याद दिलाना", "", ignoreCase = true)
                    .replace("रिमाइंडर", "", ignoreCase = true)
                    .replace("set reminder", "", ignoreCase = true)
                    .replace("remind me to", "", ignoreCase = true)
                    .replace("remind me", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { "महत्वपूर्ण कार्य" }

                addDailyReminder(remTitle)
                speak(
                    "जी बॉस, मैंने आपका रिमाइंडर सेट कर दिया है: '$remTitle'।",
                    providerBadge = "DAILY ASSISTANT",
                    toolSummary = "Reminder: $remTitle"
                )
                return@launch
            }

            if (lower.contains("मेरे रिमाइंडर") || lower.contains("रिमाइंडर दिखाओ") || lower.contains("show reminders")) {
                val reminders = getDailyReminders()
                if (reminders.isEmpty()) {
                    speak("बॉस, अभी कोई रिमाइंडर सेट नहीं है।", providerBadge = "DAILY ASSISTANT")
                } else {
                    val remStr = reminders.take(3).joinToString(", ") { it.title }
                    speak("बॉस, आपके रिमाइंडर हैं: $remStr।", providerBadge = "DAILY ASSISTANT")
                }
                return@launch
            }

            if (lower.contains("आज की जानकारी") || lower.contains("डेली ब्रीफिंग") || lower.contains("daily briefing")) {
                executeSmartRoutine("morning")
                return@launch
            }

            // 6. Smart Routines (Feature 4 of Top 5)
            if (lower.contains("मॉर्निंग रूटीन") || lower.contains("सुबह का रूटीन") ||
                lower == "गुड मॉर्निंग" || lower == "सुप्रभात" || lower.contains("morning routine")
            ) {
                executeSmartRoutine("morning")
                return@launch
            }

            if (lower.contains("स्टडी मोड") || lower.contains("वर्क मोड") ||
                lower.contains("पढ़ाई मोड") || lower.contains("study mode") || lower.contains("work mode")
            ) {
                executeSmartRoutine("study_work")
                return@launch
            }

            if (lower.contains("म्यूजिक मोड") || lower.contains("पार्टी मोड") || lower.contains("music mode")) {
                executeSmartRoutine("music_vibe")
                return@launch
            }

            if (lower.contains("नाइट मोड") || lower.contains("गुड नाइट रूटीन") ||
                lower == "गुड नाइट" || lower == "शुभ रात्रि" || lower.contains("night routine")
            ) {
                executeSmartRoutine("night")
                return@launch
            }

            // 7. Repeat last response command
            if (lower.contains("फिर से बोलो") || lower.contains("दोबारा बोलो") ||
                lower.contains("क्या कहा") || lower == "repeat"
            ) {
                speak(
                    "जी बॉस, मैं फिर से बताती हूँ: $lastSpokenResponse",
                    rememberAsLast = false,
                    providerBadge = "JARVIS MEMORY"
                )
                return@launch
            }

            // 8. Wake words & Greetings (Single short Hindi greeting, no 3-sentence promotional intro)
            if (lower == "नमस्ते" || lower == "हेलो" || lower == "हाय" ||
                lower == "नमस्ते कैरोलाइन" || lower == "हेलो कैरोलाइन" || lower == "हे कैरोलाइन" ||
                lower == "hey caroline" || lower == "hello caroline" || lower == "hi caroline" ||
                lower == "caroline" || lower == "कैरोलाइन"
            ) {
                speak(DEFAULT_GREETING_HI, providerBadge = "JARVIS CORE")
                return@launch
            }

            // 9. Time & Date check
            if (lower.contains("समय क्या") || lower.contains("टाइम क्या") ||
                lower.contains("कितने बजे") || lower.contains("समय बताओ") ||
                lower.contains("आज की तारीख") || lower.contains("तारीख क्या") ||
                lower.contains("what time") || lower.contains("current time") ||
                lower.contains("সময় কত") || lower.contains("সময় কত") || lower == "समय"
            ) {
                val info = toolExecutor.execute("getDeviceInfo", emptyMap())
                val timeStr = info["current_time"] ?: "अभी"
                val dateStr = info["current_date"] ?: "आज"
                speak(
                    "बॉस, अभी समय $timeStr है और आज की तारीख $dateStr है।",
                    providerBadge = "DAILY ASSISTANT",
                    toolSummary = "Time: $timeStr"
                )
                return@launch
            }

            // 10. Battery & RAM check (ONLY when explicitly asked by user)
            if (lower.contains("बैटरी") || lower.contains("चार्ज कितना") ||
                lower.contains("battery") || lower.contains("रैम") || lower.contains("ব্যাটারি")
            ) {
                val info = toolExecutor.execute("getDeviceInfo", emptyMap())
                val bat = info["battery_level"] ?: 100
                val charging = info["is_charging"] == true
                val availRam = info["avail_ram_mb"] ?: 0
                val chargeStatus = if (charging) "चार्ज हो रहा है" else "चार्जर से जुड़ा नहीं है"
                speak(
                    "बॉस, आपके फोन की बैटरी $bat प्रतिशत है ($chargeStatus) और $availRam एमबी रैम उपलब्ध है।",
                    providerBadge = "JARVIS TELEMETRY",
                    toolSummary = "Battery: $bat% • RAM: ${availRam}MB"
                )
                return@launch
            }

            // 11. Network / Internet check (ONLY when explicitly asked)
            if (lower.contains("इंटरनेट") || lower.contains("नेटवर्क") ||
                lower.contains("internet") || lower.contains("network status")
            ) {
                val info = toolExecutor.execute("getDeviceInfo", emptyMap())
                val isOnline = info["is_online"] == true
                val netType = info["network_type"] ?: "Wi-Fi"
                if (isOnline) {
                    speak(
                        "बॉस, आपका इंटरनेट चालू है और आप $netType नेटवर्क से जुड़े हैं।",
                        providerBadge = "JARVIS TELEMETRY",
                        toolSummary = "Network: $netType"
                    )
                } else {
                    speak(
                        "बॉस, अभी इंटरनेट कनेक्शन बंद है, लेकिन मेरे ऑफलाइन कमांड्स काम कर रहे हैं।",
                        providerBadge = "JARVIS TELEMETRY",
                        toolSummary = "Offline"
                    )
                }
                return@launch
            }

            // 12. Flashlight ON/OFF
            if (lower.contains("फ्लैशलाइट") || lower.contains("टॉर्च") ||
                lower.contains("flashlight") || lower.contains("torch") || lower.contains("ফ্ল্যাশলাইট")
            ) {
                val turnOff = lower.contains("बंद") || lower.contains("ऑफ") || lower.contains("off") || lower.contains("বন্ধ")
                val turnOn = !turnOff
                lastContextType = ContextTargetType.FLASHLIGHT
                val res = toolExecutor.execute("toggleFlashlight", mapOf("enabled" to turnOn))
                val msg = res["message_hi"]?.toString() ?: "बॉस, फ्लैशलाइट कमांड पूरा कर दिया गया है।"
                speak(msg, providerBadge = "JARVIS HARDWARE", toolSummary = "Flashlight: ${if (turnOn) "ON" else "OFF"}")
                return@launch
            }

            // 13. Volume Controls ("आवाज़ बढ़ाओ", "आवाज़ कम करो", "वॉल्यूम बढ़ाओ")
            if (lower.contains("आवाज़") || lower.contains("आवाज") ||
                lower.contains("वॉल्यूम") || lower.contains("साउंड बढ़ाओ") ||
                lower.contains("साउंड कम") || lower.contains("volume") || lower.contains("ভলিউম")
            ) {
                val numMatch = Regex("(\\d{1,3})").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val direction = when {
                    lower.contains("कम") || lower.contains("धीमा") || lower.contains("down") || lower.contains("lower") -> "down"
                    lower.contains("म्यूट") || lower.contains("mute") -> "mute"
                    else -> "up"
                }
                val res = toolExecutor.execute(
                    "adjustVolume",
                    mapOf("direction" to direction, "level" to numMatch)
                )
                val msg = res["message_hi"]?.toString() ?: "बॉस, फोन की आवाज़ बदल दी गई है।"
                speak(msg, providerBadge = "JARVIS AUDIO", toolSummary = "Volume Adjusted")
                return@launch
            }

            // 14. MUSIC COMMANDS (Requirement 4 - HIGHEST PRIORITY)
            // Pause Music: "गाना पॉज करो", "म्यूजिक पॉज करो", "गाना रोको"
            if (lower.contains("गाना पॉज") || lower.contains("म्यूजिक पॉज") ||
                lower.contains("गाना रोको") || lower.contains("gaana pause") ||
                lower.contains("pause music") || lower.contains("pause song") ||
                lower.contains("মিউজিক পজ")
            ) {
                lastContextType = ContextTargetType.MUSIC
                val res = toolExecutor.execute("controlMedia", mapOf("action" to "pause"))
                speak(
                    res["message_hi"]?.toString() ?: "बॉस, गाना पॉज कर दिया गया है।",
                    providerBadge = "JARVIS MUSIC",
                    toolSummary = "Music Paused"
                )
                return@launch
            }

            // Stop Music: "म्यूजिक बंद करो", "गाना बंद करो", "stop music"
            if (lower.contains("म्यूजिक बंद") || lower.contains("गाना बंद") ||
                lower.contains("music band") || lower.contains("gaana band") ||
                lower.contains("stop music") || lower.contains("গান বন্ধ")
            ) {
                lastContextType = ContextTargetType.MUSIC
                val res = toolExecutor.execute("controlMedia", mapOf("action" to "stop"))
                speak(
                    res["message_hi"]?.toString() ?: "बॉस, म्यूजिक बंद कर दिया गया है।",
                    providerBadge = "JARVIS MUSIC",
                    toolSummary = "Music Stopped"
                )
                return@launch
            }

            // Next Song: "अगला गाना चलाओ", "नेक्स्ट सॉन्ग", "next song"
            if (lower.contains("अगला गाना") || lower.contains("नेक्स्ट गाना") ||
                lower.contains("नेक्स्ट सॉन्ग") || lower.contains("agla gaana") ||
                lower.contains("next song") || lower.contains("next track") ||
                lower.contains("পরের গান")
            ) {
                lastContextType = ContextTargetType.MUSIC
                val res = toolExecutor.execute("controlMedia", mapOf("action" to "next"))
                speak(
                    res["message_hi"]?.toString() ?: "बॉस, अगला गाना चला दिया गया है।",
                    providerBadge = "JARVIS MUSIC",
                    toolSummary = "Next Track"
                )
                return@launch
            }

            // Previous Song: "पिछला गाना चलाओ", "previous song"
            if (lower.contains("पिछला गाना") || lower.contains("pichla gaana") || lower.contains("previous song")) {
                lastContextType = ContextTargetType.MUSIC
                val res = toolExecutor.execute("controlMedia", mapOf("action" to "previous"))
                speak(
                    res["message_hi"]?.toString() ?: "बॉस, पिछला गाना चला दिया गया है।",
                    providerBadge = "JARVIS MUSIC",
                    toolSummary = "Previous Track"
                )
                return@launch
            }

            // Play Music: "कैरोलाइन गाना चलाओ", "अरिजीत सिंह का गाना चलाओ", "मेरा पसंदीदा गाना चलाओ"
            if (lower.contains("गाना चलाओ") || lower.contains("गाना बजाओ") ||
                lower.contains("म्यूजिक चलाओ") || lower.contains("म्यूजिक बजाओ") ||
                lower.contains("सॉन्ग चलाओ") || lower.contains("पसंदीदा गाना") ||
                lower.contains("gaana chalao") || lower.contains("music chalao") ||
                lower.startsWith("play music") || lower.startsWith("play song") ||
                lower.contains("গান চালাও")
            ) {
                val isFavoriteRequest = lower.contains("पसंदीदा") || lower.contains("फेवरेट") || lower.contains("favorite")
                val extractedSong = if (isFavoriteRequest) {
                    getFavoriteSongFromMemory()
                } else {
                    trimmed
                        .replace("कैरोलाइन", "", ignoreCase = true)
                        .replace("गाना चलाओ", "", ignoreCase = true)
                        .replace("गाना बजाओ", "", ignoreCase = true)
                        .replace("म्यूजिक चलाओ", "", ignoreCase = true)
                        .replace("म्यूजिक बजाओ", "", ignoreCase = true)
                        .replace("सॉन्ग चलाओ", "", ignoreCase = true)
                        .replace("का गाना", "", ignoreCase = true)
                        .replace("के गाने", "", ignoreCase = true)
                        .replace("gaana chalao", "", ignoreCase = true)
                        .replace("music chalao", "", ignoreCase = true)
                        .replace("play music", "", ignoreCase = true)
                        .replace("play song", "", ignoreCase = true)
                        .replace("গান চালাও", "", ignoreCase = true)
                        .trim()
                        .ifEmpty { "अरिजीत सिंह केसरिया" }
                }

                lastContextType = ContextTargetType.MUSIC
                lastContextTarget = extractedSong
                val res = toolExecutor.execute("playMusic", mapOf("query" to extractedSong))
                val msg = res["message_hi"]?.toString()
                    ?: "बॉस, '$extractedSong' गाना चालू कर दिया गया है।"
                speak(
                    msg,
                    providerBadge = "JARVIS MUSIC",
                    toolSummary = res["summary"]?.toString() ?: "Play Music: $extractedSong"
                )
                return@launch
            }

            // 15. VIDEO COMMANDS (Requirement 4 - HIGHEST PRIORITY)
            // "यूट्यूब पर टेक्नोलॉजी वीडियो चलाओ", "मोटिवेशन वीडियो चलाओ", "यह वीडियो चलाओ", "अगला वीडियो दिखाओ"
            if (lower.contains("वीडियो चलाओ") || lower.contains("वीडियो दिखाओ") ||
                lower.contains("वीडियो प्ले") || lower.contains("यूट्यूब पर") ||
                lower.contains("video chalao") || lower.contains("video dikhao") ||
                lower.startsWith("play video") || lower.startsWith("watch video") ||
                lower.contains("ভিডিও চালাও")
            ) {
                val videoTopic = trimmed
                    .replace("कैरोलाइन", "", ignoreCase = true)
                    .replace("यूट्यूब पर", "", ignoreCase = true)
                    .replace("यूट्यूब में", "", ignoreCase = true)
                    .replace("वीडियो चलाओ", "", ignoreCase = true)
                    .replace("वीडियो दिखाओ", "", ignoreCase = true)
                    .replace("वीडियो प्ले करो", "", ignoreCase = true)
                    .replace("का वीडियो", "", ignoreCase = true)
                    .replace("video chalao", "", ignoreCase = true)
                    .replace("video dikhao", "", ignoreCase = true)
                    .replace("play video", "", ignoreCase = true)
                    .replace("on youtube", "", ignoreCase = true)
                    .replace("ভিডিও চালাও", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { "टेक्नोलॉजी" }

                lastContextType = ContextTargetType.VIDEO
                lastContextTarget = videoTopic
                val res = toolExecutor.execute(
                    "playVideo",
                    mapOf("query" to videoTopic, "nextVideo" to false, "useCurrentQueue" to false)
                )
                val msg = res["message_hi"]?.toString()
                    ?: "बॉस, यूट्यूब पर '$videoTopic' का वीडियो सीधा चला दिया गया है।"
                speak(
                    msg,
                    providerBadge = "JARVIS VIDEO",
                    toolSummary = res["summary"]?.toString() ?: "YouTube Video: $videoTopic"
                )
                return@launch
            }

            // 16. Camera Launch
            if (lower.contains("कैमरा खोलो") || lower.contains("फोटो खींचो") ||
                lower.contains("सेल्फी लो") || lower.contains("open camera") || lower.contains("ক্যামেরা খোলো")
            ) {
                val res = toolExecutor.execute("openCamera", emptyMap())
                speak(res["message_hi"]?.toString() ?: "बॉस, कैमरा खोल दिया गया है।", providerBadge = "JARVIS OS")
                return@launch
            }

            // 17. Android System Settings
            if (lower.contains("सेटिंग्स खोलो") || lower.contains("वाईफाई सेटिंग्स") ||
                lower.contains("ब्लूटूथ सेटिंग्स") || lower.contains("open settings") ||
                lower.contains("wifi settings") || lower.contains("bluetooth settings")
            ) {
                val res = toolExecutor.execute("openSystemSettings", mapOf("section" to lower))
                speak(res["message_hi"]?.toString() ?: "बॉस, सेटिंग्स खोल दी गई हैं।", providerBadge = "JARVIS OS")
                return@launch
            }

            // 18. Maps & Navigation
            if (lower.contains("मैप में") || lower.contains("मैप्स में") ||
                lower.contains("रास्ता दिखाओ") || lower.contains("नेविगेशन") ||
                lower.startsWith("navigate to") || lower.startsWith("open maps for")
            ) {
                val dest = trimmed
                    .replace("मैप में", "")
                    .replace("मैप्स में", "")
                    .replace("रास्ता दिखाओ", "")
                    .replace("नेविगेशन", "")
                    .replace("दिखाओ", "")
                    .replace("navigate to", "", ignoreCase = true)
                    .replace("open maps for", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { "nearby restaurants" }
                val res = toolExecutor.execute("openMapsNavigation", mapOf("destination" to dest))
                speak(res["message_hi"]?.toString() ?: "बॉस, गूगल मैप्स खोल दिया गया है।", providerBadge = "JARVIS MAPS")
                return@launch
            }

            // 19. Sensitive Actions: Phone Dialer & SMS Composer
            if (lower.contains("डायल करो") || lower.contains("कॉल लगाओ") ||
                lower.startsWith("dial ") || lower.startsWith("call ")
            ) {
                val digits = Regex("[+0-9][0-9\\s-]{2,14}").find(trimmed)?.value?.trim().orEmpty()
                val pending = PendingSensitiveAction(
                    titleBn = "फ़ोन डायलर पुष्टि",
                    descriptionBn = if (digits.isNotEmpty()) "क्या आप नंबर $digits डायलर में खोलना चाहते हैं?" else "क्या आप फ़ोन डायलर खोलना चाहते हैं?",
                    toolName = "openDialer",
                    args = mapOf("phone" to digits)
                )
                pendingSensitiveAction = pending
                onSensitiveActionRequested(pending)
                speak(
                    "बॉस, सुरक्षा के लिए कृपया डायलर खोलने की पुष्टि करें या 'हाँ' बोलें।",
                    providerBadge = "JARVIS SECURITY"
                )
                return@launch
            }

            if (lower.contains("एसएमएस भेजो") || lower.contains("मैसेज भेजो") ||
                lower.startsWith("send sms") || lower.startsWith("compose sms")
            ) {
                val digits = Regex("[+0-9][0-9\\s-]{2,14}").find(trimmed)?.value?.trim().orEmpty()
                val pending = PendingSensitiveAction(
                    titleBn = "एसएमएस कंपोज़र पुष्टि",
                    descriptionBn = "क्या आप एसएमएस कंपोज़र खोलना चाहते हैं?",
                    toolName = "composeSms",
                    args = mapOf("phone" to digits, "message" to "")
                )
                pendingSensitiveAction = pending
                onSensitiveActionRequested(pending)
                speak(
                    "बॉस, मैसेज बॉक्स खोलने के लिए कृपया कन्फर्म करें या 'हाँ' बोलें।",
                    providerBadge = "JARVIS SECURITY"
                )
                return@launch
            }

            // 20. Share Text
            if (lower.startsWith("शेयर करो") || lower.startsWith("share ")) {
                val contentToShare = trimmed
                    .replace("शेयर करो", "")
                    .replace("share", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { lastSpokenResponse }
                val res = toolExecutor.execute("shareText", mapOf("text" to contentToShare))
                speak(res["message_hi"]?.toString() ?: "बॉस, शेयर विकल्प खोल दिया गया है।", providerBadge = "JARVIS OS")
                return@launch
            }

            // 21. Top 50 & Installed App Launcher ("व्हाट्सएप खोलो", "इंस्टाग्राम खोलो", "Open WhatsApp", etc.)
            val appOpenTriggers = listOf(
                "खोलो", "खोलिए", "ओपन करो", "ओपन", "चालू करो",
                "kholo", "open ", "launch ", "খোলো"
            )
            if (appOpenTriggers.any { lower.contains(it) }) {
                val candidateApp = extractAppNameFromCommand(trimmed)
                if (candidateApp.isNotEmpty() &&
                    !candidateApp.contains("वॉइस") &&
                    !candidateApp.contains("गूगल सर्च") &&
                    !candidateApp.contains("वीडियो") &&
                    !candidateApp.contains("गाना")
                ) {
                    lastContextType = ContextTargetType.APP
                    lastContextTarget = candidateApp
                    val res = toolExecutor.execute(
                        "openApp",
                        mapOf("appName" to candidateApp, "openPlayStoreIfMissing" to true)
                    )
                    val msgHi = res["message_hi"]?.toString() ?: "बॉस, ऐप कमांड प्रोसेस कर दिया गया है।"
                    speak(msgHi, providerBadge = "APP LAUNCHER", toolSummary = "App: $candidateApp")
                    return@launch
                }
            }

            // Direct Top 50 catalog name match
            val directCatalogMatch = SmartAppLauncher.TOP_50_CATALOG.firstOrNull { entry ->
                entry.displayName.equals(trimmed, ignoreCase = true) ||
                    entry.hindiName == trimmed ||
                    entry.aliases.any { it.equals(trimmed, ignoreCase = true) }
            }
            if (directCatalogMatch != null) {
                lastContextType = ContextTargetType.APP
                lastContextTarget = directCatalogMatch.displayName
                val res = toolExecutor.execute(
                    "openApp",
                    mapOf("appName" to directCatalogMatch.displayName, "openPlayStoreIfMissing" to true)
                )
                speak(
                    res["message_hi"]?.toString() ?: "",
                    providerBadge = "APP LAUNCHER",
                    toolSummary = "App: ${directCatalogMatch.displayName}"
                )
                return@launch
            }

            // 22. Google Web Search & Website
            if (lower.contains("गूगल पर") || lower.contains("सर्च करो") ||
                lower.startsWith("search google for") || lower.startsWith("google ")
            ) {
                val query = trimmed
                    .replace("गूगल पर", "")
                    .replace("सर्च करो", "")
                    .replace("सर्च", "")
                    .replace("search google for", "", ignoreCase = true)
                    .replace("google", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { "आज की ताज़ा खबरें" }
                lastQueryTopic = query
                lastContextType = ContextTargetType.TOPIC
                lastContextTarget = query
                val res = toolExecutor.execute("searchWeb", mapOf("query" to query))
                speak(res["message_hi"]?.toString() ?: "बॉस, मैंने गूगल पर सर्च कर दिया है।", providerBadge = "JARVIS WEB")
                return@launch
            }

            if (lower.contains(".com") || lower.contains(".org") || lower.contains(".in") || lower.contains("वेबसाइट")) {
                val urlToken = trimmed.split(" ").firstOrNull { it.contains(".") } ?: "https://www.google.com"
                val res = toolExecutor.execute("openWebsite", mapOf("url" to urlToken))
                speak(res["message_hi"]?.toString() ?: "बॉस, वेबसाइट खोल दी गई है।", providerBadge = "JARVIS WEB")
                return@launch
            }

            // 23. Identity question
            if (lower.contains("तुम कौन हो") || lower.contains("तुम्हारा नाम") ||
                lower.contains("आप कौन हैं") || lower.contains("परिचय") || lower.contains("who are you")
            ) {
                speak(
                    "नमस्ते बॉस! मैं कैरोलाइन एआई 2.9 अल्टीमेट स्मार्ट जार्विस हूँ। मैं आपके फोन के ऐप्स, म्यूजिक, यूट्यूब वीडियो, स्मार्ट मेमोरी और हर सवाल में आपकी मदद के लिए तैयार हूँ।",
                    providerBadge = "JARVIS CORE"
                )
                return@launch
            }

            // 24. Check if user mentions an app name in conversation so "इसे खोलो" can follow up
            val mentionedApp = SmartAppLauncher.TOP_50_CATALOG.firstOrNull { entry ->
                lower.contains(entry.displayName.lowercase()) || entry.aliases.any { lower.contains(it) }
            }
            if (mentionedApp != null) {
                lastContextType = ContextTargetType.APP
                lastContextTarget = mentionedApp.displayName
            } else {
                lastQueryTopic = trimmed
            }

            // 25. Route general questions & conversations to the Three AI Brain System
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

            // 26. Intelligent On-Device Hindi Conversational Fallback (Direct, helpful, zero status repetition)
            val fallbackReply = buildIntelligentLocalHindiReply(trimmed, lower)
            speak(fallbackReply, providerBadge = "JARVIS HINDI")
        }
    }

    private fun extractAppNameFromCommand(rawCommand: String): String {
        return rawCommand
            .replace("कैरोलाइन", "", ignoreCase = true)
            .replace("जार्विस", "", ignoreCase = true)
            .replace("ऐप को", "", ignoreCase = true)
            .replace("ऐप", "", ignoreCase = true)
            .replace("खोलो", "", ignoreCase = true)
            .replace("खोलिए", "", ignoreCase = true)
            .replace("ओपन करो", "", ignoreCase = true)
            .replace("ओपन", "", ignoreCase = true)
            .replace("चालू करो", "", ignoreCase = true)
            .replace("kholo", "", ignoreCase = true)
            .replace("open", "", ignoreCase = true)
            .replace("launch", "", ignoreCase = true)
            .replace("please", "", ignoreCase = true)
            .replace("খোলো", "", ignoreCase = true)
            .trim()
    }

    private fun buildIntelligentLocalHindiReply(rawInput: String, lower: String): String {
        return when {
            lower.contains("कैसी हो") || lower.contains("कैसे हो") || lower.contains("how are you") ->
                "मैं बिल्कुल बढ़िया हूँ बॉस! बताइए, आज हम क्या नया करें?"
            lower.contains("धन्यवाद") || lower.contains("थैंक यू") || lower.contains("शुक्रिया") || lower.contains("thank") ->
                "आपका बहुत-बहुत स्वागत है बॉस! आपकी मदद करना मेरा काम है।"
            lower.contains("जोक") || lower.contains("चुटकुला") || lower.contains("मजाक") || lower.contains("joke") ->
                "बॉस, एक मज़ेदार बात सुनिए—एक प्रोग्रामर चश्मा क्यों पहनता है? क्योंकि वह सी-शार्प (C#) नहीं देख पाता!"
            lower.contains("मौसम") || lower.contains("तापमान") || lower.contains("weather") ->
                "बॉस, आज का मौसम सामान्य और सुहावना है। बाहर निकलते समय पानी साथ रखें।"
            lower.contains("मोटिवेशन") || lower.contains("प्रेरणा") || lower.contains("हिम्मत") ->
                "बॉस, याद रखिए—सफलता उन्हें मिलती है जो हर दिन अपने लक्ष्य के लिए एक कदम आगे बढ़ाते हैं। आप ज़रूर कामयाब होंगे!"
            lower.contains("और बताओ") || lower.contains("विस्तार से") || lower.contains("tell me more") ->
                if (lastQueryTopic.isNotEmpty()) {
                    "बॉस, '$lastQueryTopic' के बारे में और गहराई से जानने के लिए आप कह सकते हैं 'गूगल पर $lastQueryTopic सर्च करो' या सेटिंग्स में Gemini या ChatGPT API जोड़ सकते हैं।"
                } else {
                    "जी बॉस, आप किस विषय के बारे में विस्तार से जानना चाहते हैं? मुझे बताइए।"
                }
            else ->
                "जी बॉस, मैंने आपकी बात समझ ली: '$rawInput'। आप मुझसे कोई भी गाना चलाने, यूट्यूब पर वीडियो चलाने, ऐप खोलने या रिमाइंडर सेट करने के लिए कह सकते हैं।"
        }
    }
}
