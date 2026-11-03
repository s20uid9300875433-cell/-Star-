package com.example.live

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.tools.ContactActionStatus
import com.example.tools.DeviceContactItem
import com.example.tools.LiveWeatherReport
import com.example.tools.MessageDraftPreview
import com.example.tools.PendingSensitiveAction
import com.example.tools.SmartAppLauncher
import com.example.tools.SmartMessageWriter
import com.example.tools.ToolExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

data class TtsEngineInfo(
    val packageName: String,
    val label: String
)

data class DeviceVoiceInfo(
    val voiceId: String,
    val displayName: String,
    val localeTag: String,
    val quality: Int,
    val isNetworkRequired: Boolean
)

enum class ContextTargetType {
    NONE,
    APP,
    MUSIC,
    VIDEO,
    FLASHLIGHT,
    CONTACT,
    MESSAGE,
    TOPIC
}

/**
 * CaroLine AI A3.2 – Ultimate Android Smart JARVIS Brain & Voice Studio Engine.
 *
 * Implements:
 * - Requirement B: Default Hindi (`hi-IN`), alternative Bengali (`bn-IN`), optional English (`en-IN`).
 *   Understands Hindi, Bengali, Hinglish, and English commands; speaks ONLY the owner-selected language.
 *   Default greeting: "नमस्ते बॉस! मैं कैरोलाइन हूँ। बताइए, मैं आपकी क्या मदद कर सकती हूँ?"
 * - Requirement C: Real Female Voice Studio inspecting installed Android TTS engines (`tts.engines`)
 *   and device voices (`tts.voices`) without fabricating non-existent voice options.
 * - Requirement E: Smart Contact & Phone Call Engine (name search, partial ending digits, disambiguation).
 * - Requirement G: Smart Message Writer with grammar/tone improvement and confirmation preview.
 * - Requirement H, I, J, K, M: Music/Video, 50+ App Launcher, Mobile Controls, Live Weather & Data Usage,
 *   and Automatic Smart Command Routing.
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
    private val onRemindersUpdated: (List<DailyReminderItem>) -> Unit = {},
    private val onContactMatchesUpdated: (List<DeviceContactItem>, String) -> Unit = { _, _ -> },
    private val onMessageDraftUpdated: (MessageDraftPreview?) -> Unit = {},
    private val onWeatherReportUpdated: (LiveWeatherReport) -> Unit = {},
    private val onVoiceStudioCatalogUpdated: (List<TtsEngineInfo>, List<DeviceVoiceInfo>) -> Unit = { _, _ -> }
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "CaroLineBrainEngine"
        private const val PREFS_SMART = "caroline_smart_features_v29"
        private const val KEY_MEMORIES = "smart_memories_json"
        private const val KEY_REMINDERS = "daily_reminders_json"

        const val DEFAULT_GREETING_HI =
            "नमस्ते बॉस! मैं कैरोलाइन हूँ। बताइए, मैं आपकी क्या मदद कर सकती हूँ?"

        const val DEFAULT_GREETING_BN =
            "নমস্কার বস! আমি ক্যারোলাইন। বলুন, আমি আপনাকে কীভাবে সাহায্য করতে পারি?"

        const val DEFAULT_GREETING_EN =
            "Hello Boss! I am CaroLine. Tell me, how can I help you today?"

        val DEFAULT_SMART_ROUTINES: List<SmartRoutineItem> = listOf(
            SmartRoutineItem(
                id = "morning",
                nameHi = "गुड मॉर्निंग रूटीन",
                nameEn = "Morning Briefing Routine",
                descriptionHi = "Time, Date, Reminders & Live Weather Briefing",
                commands = listOf("What time is it", "Show reminders")
            ),
            SmartRoutineItem(
                id = "study_work",
                nameHi = "स्टडी / वर्क मोड",
                nameEn = "Study & Focus Mode",
                descriptionHi = "Stops music and sets media volume to 25% for focus",
                commands = listOf("Stop music", "Set volume 25")
            ),
            SmartRoutineItem(
                id = "music_vibe",
                nameHi = "म्यूजिक मोड",
                nameEn = "Music Vibe Mode",
                descriptionHi = "Raises media volume and plays your favorite track",
                commands = listOf("Raise volume", "Play favorite song")
            ),
            SmartRoutineItem(
                id = "night",
                nameHi = "नाइट मोड रूटीन",
                nameEn = "Good Night Routine",
                descriptionHi = "Stops music, turns off flashlight, and lowers volume",
                commands = listOf("Stop music", "Turn off flashlight")
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
    private var activeCommandJob: Job? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking = _isSpeaking.asStateFlow()

    var lastSpokenResponse: String = DEFAULT_GREETING_HI
        private set

    var lastQueryTopic: String = ""
        private set

    var lastContextType: ContextTargetType = ContextTargetType.NONE
        private set
    var lastContextTarget: String = ""
        private set

    var pendingSensitiveAction: PendingSensitiveAction? = null
        private set

    var pendingMessageDraft: MessageDraftPreview? = null
        private set

    var lastMatchedContacts: List<DeviceContactItem> = emptyList()
        private set

    // Spoken language code: "hi-IN" (default), "bn-IN" (Bengali), "en-IN" (English)
    var spokenLanguageCode: String = "hi-IN"
        set(value) {
            field = value
            applyConfiguredLanguageAndVoice()
        }

    var isLanguageLockedToBengali: Boolean = true
    var ownerLanguagePin: String = "1234"

    // Female Voice Studio settings (Requirement C)
    var selectedTtsEnginePackage: String = ""
    var selectedVoiceId: String = ""
    var selectedFemaleVoiceProfile: String = "System Default Female (hi-IN)"

    var installedTtsEngines: List<TtsEngineInfo> = emptyList()
        private set

    var availableDeviceVoices: List<DeviceVoiceInfo> = emptyList()
        private set

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
    var useOpenAiNeuralTts: Boolean = false
    var openAiTtsVoiceName: String = "nova"

    var openAiSpeechSynthesizer: (suspend (text: String, voice: String, speed: Float) -> ByteArray?)? = null
    var adaptiveAiManager: AdaptiveAiManager? = null

    init {
        initTtsEngine(null)
    }

    private fun initTtsEngine(enginePackage: String?) {
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        isTtsReady = false
        tts = if (!enginePackage.isNullOrBlank()) {
            TextToSpeech(context.applicationContext, this, enginePackage)
        } else {
            TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            discoverInstalledEnginesAndRealVoices()
            applyConfiguredLanguageAndVoice()

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
     * Requirement C: Real Female Voice Studio inspection.
     * Inspects installed Android TTS engines (`tts.engines`) and real device voices (`tts.voices`).
     * Never fabricates fake voices.
     */
    fun discoverInstalledEnginesAndRealVoices() {
        val engine = tts ?: return

        val enginesList = try {
            engine.engines?.map { info ->
                TtsEngineInfo(
                    packageName = info.name,
                    label = info.label.ifBlank { info.name }
                )
            }.orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Could not inspect TTS engines", e)
            emptyList()
        }

        val realVoices = try {
            val rawVoices: Set<Voice>? = engine.voices
            rawVoices.orEmpty()
                .filter { v ->
                    val lang = v.locale.language.lowercase()
                    (lang == "hi" || lang == "bn" || lang == "en") &&
                        !v.name.contains("male-only", ignoreCase = true)
                }
                .sortedWith(
                    compareByDescending<Voice> { v ->
                        val lang = v.locale.language.lowercase()
                        when {
                            spokenLanguageCode.startsWith(lang, ignoreCase = true) -> 2
                            lang == "hi" -> 1
                            else -> 0
                        }
                    }.thenByDescending { v ->
                        if (v.name.contains("female", ignoreCase = true) ||
                            v.name.contains("hie", ignoreCase = true) ||
                            v.name.contains("hia", ignoreCase = true) ||
                            v.name.contains("bn-in-x-bna", ignoreCase = true) ||
                            v.name.contains("en-in-x-ena", ignoreCase = true)
                        ) 1 else 0
                    }.thenByDescending { it.quality }
                )
                .take(18)
                .map { v ->
                    val langLabel = when (v.locale.language.lowercase()) {
                        "hi" -> "Hindi"
                        "bn" -> "Bengali"
                        else -> "English (${v.locale.country.ifBlank { "IN" }})"
                    }
                    val netTag = if (v.isNetworkConnectionRequired) "Network" else "On-Device"
                    DeviceVoiceInfo(
                        voiceId = v.name,
                        displayName = "$langLabel • ${v.name} ($netTag)",
                        localeTag = v.locale.toLanguageTag(),
                        quality = v.quality,
                        isNetworkRequired = v.isNetworkConnectionRequired
                    )
                }
        } catch (e: Exception) {
            Log.w(TAG, "Could not inspect TTS voices", e)
            emptyList()
        }

        // If the device TTS engine hasn't downloaded voice packs yet, expose the 3 authentic system locale targets
        val finalVoices = realVoices.ifEmpty {
            listOf(
                DeviceVoiceInfo(
                    voiceId = "system_locale_hi-IN",
                    displayName = "System Default Hindi (hi-IN)",
                    localeTag = "hi-IN",
                    quality = Voice.QUALITY_NORMAL,
                    isNetworkRequired = false
                ),
                DeviceVoiceInfo(
                    voiceId = "system_locale_bn-IN",
                    displayName = "System Default Bengali (bn-IN)",
                    localeTag = "bn-IN",
                    quality = Voice.QUALITY_NORMAL,
                    isNetworkRequired = false
                ),
                DeviceVoiceInfo(
                    voiceId = "system_locale_en-IN",
                    displayName = "System Default English (en-IN)",
                    localeTag = "en-IN",
                    quality = Voice.QUALITY_NORMAL,
                    isNetworkRequired = false
                )
            )
        }

        installedTtsEngines = enginesList
        availableDeviceVoices = finalVoices
        onVoiceStudioCatalogUpdated(installedTtsEngines, availableDeviceVoices)
    }

    fun selectTtsEngine(packageName: String) {
        if (packageName.isBlank() || packageName == selectedTtsEnginePackage) return
        selectedTtsEnginePackage = packageName
        initTtsEngine(packageName)
    }

    fun selectDeviceVoiceById(voiceId: String) {
        selectedVoiceId = voiceId
        selectedFemaleVoiceProfile = availableDeviceVoices.firstOrNull { it.voiceId == voiceId }?.displayName ?: voiceId
        applyConfiguredLanguageAndVoice()
    }

    fun configureHindiFemaleVoice() {
        applyConfiguredLanguageAndVoice()
    }

    fun configureBengaliFemaleVoice() {
        applyConfiguredLanguageAndVoice()
    }

    fun applyConfiguredLanguageAndVoice() {
        val engine = tts ?: return
        val targetLocale = Locale.forLanguageTag(spokenLanguageCode)
        val res = engine.setLanguage(targetLocale)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.forLanguageTag("en-IN"))
        }

        try {
            val voices = engine.voices
            if (voices != null && voices.isNotEmpty()) {
                val explicitMatch = if (selectedVoiceId.isNotBlank() && !selectedVoiceId.startsWith("system_locale_")) {
                    voices.firstOrNull { it.name == selectedVoiceId }
                } else null

                val langPrefix = spokenLanguageCode.substringBefore("-").lowercase()
                val bestLangFemale = explicitMatch ?: voices.firstOrNull { v ->
                    v.locale.language.lowercase() == langPrefix &&
                        (v.name.contains("female", ignoreCase = true) ||
                            v.name.contains("hie", ignoreCase = true) ||
                            v.name.contains("hia", ignoreCase = true) ||
                            v.name.contains("bna", ignoreCase = true) ||
                            v.name.contains("ena", ignoreCase = true) ||
                            !v.name.contains("male", ignoreCase = true))
                } ?: voices.firstOrNull { v ->
                    v.locale.language.lowercase() == langPrefix
                }

                if (bestLangFemale != null) {
                    engine.voice = bestLangFemale
                    selectedVoiceId = bestLangFemale.name
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply TTS voice selection", e)
        }

        engine.setPitch(voicePitch)
        engine.setSpeechRate(voiceSpeed)
    }

    fun getGreetingForActiveLanguage(): String {
        return when {
            spokenLanguageCode.startsWith("bn", ignoreCase = true) -> DEFAULT_GREETING_BN
            spokenLanguageCode.startsWith("en", ignoreCase = true) -> DEFAULT_GREETING_EN
            else -> DEFAULT_GREETING_HI
        }
    }

    private fun pickMsg(result: Map<String, Any?>, fallbackHi: String, fallbackBn: String, fallbackEn: String): String {
        return when {
            spokenLanguageCode.startsWith("bn", ignoreCase = true) ->
                result["message_bn"]?.toString()?.takeIf { it.isNotBlank() } ?: fallbackBn
            spokenLanguageCode.startsWith("en", ignoreCase = true) ->
                result["message_en"]?.toString()?.takeIf { it.isNotBlank() } ?: fallbackEn
            else ->
                result["message_hi"]?.toString()?.takeIf { it.isNotBlank() } ?: fallbackHi
        }
    }

    private fun sanitizeSpokenText(raw: String): String {
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

    fun speak(
        text: String,
        rememberAsLast: Boolean = true,
        providerBadge: String = "JARVIS A3.2",
        toolSummary: String? = null
    ) {
        val cleanText = sanitizeSpokenText(text)
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
                    speakWithAndroidTts(cleanText)
                }
            }
            return
        }

        speakWithAndroidTts(cleanText)
    }

    private fun speakWithAndroidTts(cleanText: String) {
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
            Log.w(TAG, "OpenAI TTS playback fallback to Android TTS", e)
            speakWithAndroidTts(lastSpokenResponse)
        }
    }

    fun previewFemaleVoice() {
        val sample = when {
            spokenLanguageCode.startsWith("bn", ignoreCase = true) ->
                "নমস্কার বস! আমি ক্যারোলাইন এ-থ্রি পয়েন্ট টু। আমার বাংলা কণ্ঠস্বর সম্পূর্ণ প্রস্তুত।"
            spokenLanguageCode.startsWith("en", ignoreCase = true) ->
                "Hello Boss! I am CaroLine A3.2. My female voice studio settings are active and ready."
            else ->
                "नमस्ते बॉस! मैं कैरोलाइन ए-थ्री पॉइंट टू हूँ। मेरी हिंदी आवाज़ बिल्कुल साफ और तैयार है।"
        }
        speak(sample, rememberAsLast = false, providerBadge = "VOICE STUDIO")
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
        activeCommandJob?.cancel()
        stopActiveAudioOnly()
        _isSpeaking.value = false
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }

    // --- Smart Memory Management ---

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
                it.fact.contains("song", ignoreCase = true) ||
                it.fact.contains("अरिजीत", ignoreCase = true) ||
                it.fact.contains("केसरिया", ignoreCase = true)
        }
        return favSongMem?.fact ?: "Arijit Singh Kesariya"
    }

    // --- Daily Reminders ---

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
                        timeText = obj.optString("timeText", "Today"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list.filter { it.title.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addDailyReminder(title: String, timeText: String = "Today"): List<DailyReminderItem> {
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

    // --- Smart Routines Execution ---

    fun executeSmartRoutine(routineId: String) {
        val routine = DEFAULT_SMART_ROUTINES.firstOrNull { it.id == routineId }
            ?: DEFAULT_SMART_ROUTINES.first()

        scope.launch {
            onStateChanged(LiveState.THINKING)
            when (routine.id) {
                "morning" -> {
                    val info = toolExecutor.execute("getDeviceInfo", emptyMap())
                    val timeStr = info["current_time"] ?: "Morning"
                    val dateStr = info["current_date"] ?: "Today"
                    val reminders = getDailyReminders()
                    val msg = when {
                        spokenLanguageCode.startsWith("bn") ->
                            "সুপ্রভাত বস! এখন সময় $timeStr এবং আজ $dateStr। আপনার ${reminders.size}টি রিমাইন্ডার আছে।"
                        spokenLanguageCode.startsWith("en") ->
                            "Good morning Boss! It is $timeStr on $dateStr. You have ${reminders.size} active reminders."
                        else ->
                            "सुप्रभात बॉस! अभी समय $timeStr है और आज $dateStr है। आपके ${reminders.size} रिमाइंडर हैं।"
                    }
                    speak(msg, providerBadge = "SMART ROUTINE", toolSummary = "Morning Routine")
                }
                "study_work" -> {
                    toolExecutor.execute("controlMedia", mapOf("action" to "stop"))
                    toolExecutor.execute("adjustVolume", mapOf("level" to 25))
                    val msg = when {
                        spokenLanguageCode.startsWith("bn") ->
                            "বস, স্টাডি ও ফোকাস মোড চালু করা হয়েছে। মিউজিক বন্ধ এবং ভলিউম ২৫ শতাংশে রাখা হয়েছে।"
                        spokenLanguageCode.startsWith("en") ->
                            "Boss, Study and Focus Mode is active. Music is stopped and volume is set to 25 percent."
                        else ->
                            "बॉस, स्टडी और वर्क मोड सक्रिय कर दिया गया है। म्यूजिक बंद है और आवाज़ 25 प्रतिशत पर सेट है।"
                    }
                    speak(msg, providerBadge = "SMART ROUTINE", toolSummary = "Study & Focus Mode")
                }
                "music_vibe" -> {
                    toolExecutor.execute("adjustVolume", mapOf("level" to 70))
                    val favQuery = getFavoriteSongFromMemory()
                    val res = toolExecutor.execute("playMusic", mapOf("query" to favQuery))
                    lastContextType = ContextTargetType.MUSIC
                    lastContextTarget = favQuery
                    val msg = pickMsg(
                        res,
                        "बॉस, म्यूजिक मोड चालू कर दिया गया है।",
                        "বস, মিউজিক মোড চালু করা হয়েছে।",
                        "Boss, Music Mode is now active."
                    )
                    speak(msg, providerBadge = "SMART ROUTINE", toolSummary = "Music Mode")
                }
                "night" -> {
                    toolExecutor.execute("controlMedia", mapOf("action" to "stop"))
                    toolExecutor.execute("toggleFlashlight", mapOf("enabled" to false))
                    toolExecutor.execute("adjustVolume", mapOf("level" to 20))
                    val msg = when {
                        spokenLanguageCode.startsWith("bn") ->
                            "বস, নাইট রুটিন সম্পন্ন হয়েছে। মিউজিক এবং ফ্ল্যাশলাইট বন্ধ করা হয়েছে। শুভ রাত্রি বস!"
                        spokenLanguageCode.startsWith("en") ->
                            "Boss, Night Routine completed. Music and flashlight are off. Good night Boss!"
                        else ->
                            "बॉस, नाइट रूटीन पूरा हो गया है। म्यूजिक और फ्लैशलाइट बंद कर दिए गए हैं। शुभ रात्रि बॉस!"
                    }
                    speak(msg, providerBadge = "SMART ROUTINE", toolSummary = "Night Routine")
                }
            }
        }
    }

    // --- Contact & Message Confirmation Actions (Requirements E & G) ---

    fun selectContactToCall(contact: DeviceContactItem, directCall: Boolean = false) {
        val pending = PendingSensitiveAction(
            titleBn = "Confirm Phone Call: ${contact.displayName}",
            descriptionBn = "Open ${contact.displayName} (${contact.phoneNumber}) in the phone dialer?",
            toolName = "openDialer",
            args = mapOf("phone" to contact.phoneNumber, "directCall" to directCall)
        )
        pendingSensitiveAction = pending
        onSensitiveActionRequested(pending)
        val msg = when {
            spokenLanguageCode.startsWith("bn") ->
                "বস, ${contact.displayName} (${contact.phoneNumber})-কে কল করার জন্য অনুগ্রহ করে কনফার্ম করুন।"
            spokenLanguageCode.startsWith("en") ->
                "Boss, please confirm to call ${contact.displayName} at ${contact.phoneNumber}."
            else ->
                "बॉस, ${contact.displayName} (${contact.phoneNumber}) को कॉल करने के लिए कृपया पुष्टि करें।"
        }
        speak(msg, providerBadge = "CONTACT CALL")
    }

    fun confirmMessageDraftViaWhatsApp() {
        val draft = pendingMessageDraft ?: return
        pendingMessageDraft = null
        onMessageDraftUpdated(null)
        val ok = toolExecutor.smartMessageWriter.sendConfirmedViaWhatsApp(draft)
        val msg = when {
            spokenLanguageCode.startsWith("bn") ->
                if (ok) "বস, হোয়াটসঅ্যাপে আপনার বার্তাটি খুলে দেওয়া হয়েছে।" else "বস, হোয়াটসঅ্যাপ খোলা যায়নি।"
            spokenLanguageCode.startsWith("en") ->
                if (ok) "Boss, I have opened your confirmed message in WhatsApp." else "Boss, could not open WhatsApp."
            else ->
                if (ok) "बॉस, आपका कन्फर्म किया गया मैसेज व्हाट्सएप में खोल दिया गया है।" else "बॉस, व्हाट्सएप नहीं खुल सका।"
        }
        speak(msg, providerBadge = "MESSAGE WRITER", toolSummary = "WhatsApp: ${draft.recipientName}")
    }

    fun confirmMessageDraftViaSms() {
        val draft = pendingMessageDraft ?: return
        pendingMessageDraft = null
        onMessageDraftUpdated(null)
        val ok = toolExecutor.smartMessageWriter.sendConfirmedViaSms(draft)
        val msg = when {
            spokenLanguageCode.startsWith("bn") ->
                if (ok) "বস, এসএমএস বক্সে আপনার বার্তাটি খুলে দেওয়া হয়েছে।" else "বস, এসএমএস অ্যাপ খোলা যায়নি।"
            spokenLanguageCode.startsWith("en") ->
                if (ok) "Boss, I have opened your confirmed message in the SMS app." else "Boss, could not open SMS app."
            else ->
                if (ok) "बॉस, आपका कन्फर्म किया गया मैसेज एसएमएस बॉक्स में खोल दिया गया है।" else "बॉस, एसएमएस ऐप नहीं खुल सका।"
        }
        speak(msg, providerBadge = "MESSAGE WRITER", toolSummary = "SMS: ${draft.recipientName}")
    }

    fun cancelMessageDraft() {
        pendingMessageDraft = null
        onMessageDraftUpdated(null)
        val msg = when {
            spokenLanguageCode.startsWith("bn") -> "ঠিক আছে বস, মেসেজ ড্রাফট বাতিল করা হয়েছে।"
            spokenLanguageCode.startsWith("en") -> "Okay Boss, the message draft has been cancelled."
            else -> "ठीक है बॉस, मैसेज ड्राफ्ट रद्द कर दिया गया है।"
        }
        speak(msg, providerBadge = "MESSAGE WRITER")
    }

    fun updatePendingMessageDraftText(newText: String) {
        val current = pendingMessageDraft ?: return
        val updated = current.copy(draftedText = newText)
        pendingMessageDraft = updated
        onMessageDraftUpdated(updated)
    }

    fun confirmPendingSensitiveAction() {
        val pending = pendingSensitiveAction ?: return
        pendingSensitiveAction = null
        onSensitiveActionRequested(null)

        val result = toolExecutor.execute(pending.toolName, pending.args)
        val msg = pickMsg(
            result,
            "बॉस, आपका अनुमोदित कमांड पूरा कर दिया गया है।",
            "বস, আপনার অনুমোদিত কমান্ড সম্পন্ন হয়েছে।",
            "Boss, your confirmed action has been executed."
        )
        speak(msg, providerBadge = "JARVIS OS", toolSummary = pending.titleBn)
    }

    fun cancelPendingSensitiveAction() {
        pendingSensitiveAction = null
        onSensitiveActionRequested(null)
        val msg = when {
            spokenLanguageCode.startsWith("bn") -> "ঠিক আছে বস, এই অ্যাকশনটি বাতিল করা হয়েছে।"
            spokenLanguageCode.startsWith("en") -> "Okay Boss, this action has been cancelled."
            else -> "ठीक है बॉस, यह एक्शन रद्द कर दिया गया है।"
        }
        speak(msg, providerBadge = "JARVIS OS")
    }

    private fun stripWakeWordPrefix(rawInput: String): String {
        var result = rawInput.trim()
        val prefixes = listOf(
            "hey caroline", "hello caroline", "hi caroline", "caroline",
            "हे कैरोलाइन", "नमस्ते कैरोलाइन", "हेलो कैरोलाइन", "कैरोलाइन", "जार्विस",
            "হ্যালো ক্যারোলাইন", "ক্যারোলাইন"
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
     * Requirement A & M: Single active AI task gate + Automatic Smart Command Routing.
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

        // Cancel any previous in-flight command so we never run multiple heavy AI features simultaneously
        activeCommandJob?.cancel()
        activeCommandJob = scope.launch {
            onStateChanged(LiveState.THINKING)
            delay(80)

            // 0. Pending message draft or sensitive action confirmation by voice
            if (pendingMessageDraft != null) {
                if (lower == "हाँ" || lower == "हां" || lower == "yes" || lower.contains("send") || lower.contains("भेजो") || lower.contains("পাঠাও")) {
                    if (lower.contains("sms") || lower.contains("एसएमएस") || pendingMessageDraft?.preferredChannel == "SMS") {
                        confirmMessageDraftViaSms()
                    } else {
                        confirmMessageDraftViaWhatsApp()
                    }
                    return@launch
                } else if (lower == "नहीं" || lower == "no" || lower.contains("cancel") || lower.contains("रद्द") || lower.contains("বাতিল")) {
                    cancelMessageDraft()
                    return@launch
                }
            }

            if (pendingSensitiveAction != null) {
                if (lower == "हाँ" || lower == "हां" || lower.contains("हाँ करो") || lower.contains("कन्फर्म") ||
                    lower == "yes" || lower.contains("confirm") || lower == "হ্যাঁ"
                ) {
                    confirmPendingSensitiveAction()
                    return@launch
                } else if (lower == "नहीं" || lower.contains("कैंसिल") || lower.contains("रद्द") ||
                    lower == "no" || lower.contains("cancel") || lower == "না"
                ) {
                    cancelPendingSensitiveAction()
                    return@launch
                }
            }

            // 1. Stop voice mode command
            if (lower.contains("वॉइस मोड बंद") ||
                lower.contains("आवाज मोड बंद") ||
                lower.contains("stop voice mode") ||
                lower.contains("turn off voice") ||
                lower.contains("ভয়েস মোড বন্ধ")
            ) {
                shouldStopSessionAfterUtterance = true
                val msg = when {
                    spokenLanguageCode.startsWith("bn") -> "ঠিক আছে বস, ভয়েস মোড বন্ধ করা হচ্ছে।"
                    spokenLanguageCode.startsWith("en") -> "Okay Boss, turning off voice mode now."
                    else -> "ठीक है बॉस, वॉइस मोड बंद किया जा रहा है।"
                }
                speak(msg, rememberAsLast = false, providerBadge = "JARVIS OS")
                return@launch
            }

            // 2. SMART CONTACT & PHONE CALL ENGINE (Requirement E)
            // 2a. Partial number matching: "Find the contact ending in 45", "Search contacts ending in 23",
            //     "45 से खत्म होने वाला नंबर ढूंढो", "जिनके नंबर के अंत में 23 है"
            val endingDigitsMatch = Regex(
                "(?:ending in|ends with|ending with|अंत में|खत्म होने वाला|खत्म होने वाले|শেষ হওয়া|শেষে)\\s*(\\d{1,6})|(\\d{1,6})\\s*(?:से खत्म होने|दीये शेष|দিয়ে শেষ)",
                RegexOption.IGNORE_CASE
            ).find(trimmed)

            if (endingDigitsMatch != null) {
                val digits = endingDigitsMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() }.orEmpty()
                if (digits.isNotEmpty()) {
                    adaptiveAiManager?.recordCommandCategory("Contacts & Calls")
                    lastContextType = ContextTargetType.CONTACT
                    val outcome = toolExecutor.contactCallEngine.searchContactsEndingIn(digits)
                    lastMatchedContacts = outcome.matches
                    onContactMatchesUpdated(outcome.matches, "Contacts ending in $digits")

                    if (outcome.status == ContactActionStatus.SINGLE_MATCH_READY && outcome.matches.size == 1) {
                        val c = outcome.matches.first()
                        val pending = PendingSensitiveAction(
                            titleBn = "Call ${c.displayName} (${c.phoneNumber})",
                            descriptionBn = "Open ${c.displayName} (${c.phoneNumber}) in phone dialer?",
                            toolName = "openDialer",
                            args = mapOf("phone" to c.phoneNumber)
                        )
                        pendingSensitiveAction = pending
                        onSensitiveActionRequested(pending)
                    }
                    speak(
                        outcome.messageForLanguage(spokenLanguageCode),
                        providerBadge = "CONTACT ENGINE",
                        toolSummary = "Contacts ending in $digits (${outcome.matches.size})"
                    )
                    return@launch
                }
            }

            // 2b. "Show matching contacts" / "कॉन्टैक्ट्स दिखाओ"
            if (lower.contains("show matching contacts") || lower.contains("matching contacts") ||
                lower.contains("मैचिंग कॉन्टैक्ट")
            ) {
                val msg = if (lastMatchedContacts.isEmpty()) {
                    when {
                        spokenLanguageCode.startsWith("bn") -> "বস, এখনো কোনো ম্যাচিং কন্টাক্ট তালিকা নেই।"
                        spokenLanguageCode.startsWith("en") -> "Boss, there are no recent matching contacts to display."
                        else -> "बॉस, अभी कोई मैचिंग कॉन्टैक्ट सूची में नहीं है।"
                    }
                } else {
                    val summary = lastMatchedContacts.take(4).joinToString(", ") { "${it.displayName} (${it.phoneNumber})" }
                    onContactMatchesUpdated(lastMatchedContacts, "Matching Contacts")
                    when {
                        spokenLanguageCode.startsWith("bn") -> "বস, ম্যাচিং কন্টাক্টগুলো হলো: $summary।"
                        spokenLanguageCode.startsWith("en") -> "Boss, the matching contacts are: $summary."
                        else -> "बॉस, मैचिंग कॉन्टैक्ट्स हैं: $summary।"
                    }
                }
                speak(msg, providerBadge = "CONTACT ENGINE")
                return@launch
            }

            // 2c. "Open phone dialer" / "डायलर खोलो"
            if (lower == "open phone dialer" || lower == "open dialer" ||
                lower.contains("फोन डायलर खोलो") || lower.contains("डायलर खोलो") ||
                lower.contains("ডায়ালার খোলো")
            ) {
                val res = toolExecutor.execute("openDialer", mapOf("phone" to ""))
                speak(
                    pickMsg(res, "बॉस, फोन डायलर खोल दिया गया है।", "বস, ফোন ডায়ালার খুলে দেওয়া হয়েছে।", "Boss, I have opened the phone dialer."),
                    providerBadge = "CONTACT ENGINE",
                    toolSummary = "Opened Phone Dialer"
                )
                return@launch
            }

            // 2d. Call by Name or Number: "Call Rahul", "Call Papa", "Call 9876543210", "राहुल को कॉल करो"
            val isCallCommand = lower.startsWith("call ") ||
                lower.startsWith("dial ") ||
                lower.contains("को कॉल करो") ||
                lower.contains("को कॉल लगाओ") ||
                lower.contains("कॉल लगाओ") ||
                lower.contains("कॉल करो") ||
                lower.contains("কে কল করো") ||
                lower.contains("কল লাগাও")

            if (isCallCommand && !lower.contains("ending in")) {
                adaptiveAiManager?.recordCommandCategory("Contacts & Calls")
                val targetNameOrNumber = trimmed
                    .replace(Regex("^(call|dial)\\s+", RegexOption.IGNORE_CASE), "")
                    .replace("को कॉल करो", "", ignoreCase = true)
                    .replace("को कॉल लगाओ", "", ignoreCase = true)
                    .replace("कॉल लगाओ", "", ignoreCase = true)
                    .replace("कॉल करो", "", ignoreCase = true)
                    .replace("डायल करो", "", ignoreCase = true)
                    .replace("কে কল করো", "", ignoreCase = true)
                    .replace("কল লাগাও", "", ignoreCase = true)
                    .trim()

                if (targetNameOrNumber.isEmpty()) {
                    val res = toolExecutor.execute("openDialer", mapOf("phone" to ""))
                    speak(
                        pickMsg(res, "बॉस, फोन डायलर खोल दिया गया है।", "বস, ফোন ডায়ালার খোলা হয়েছে।", "Boss, opened the phone dialer."),
                        providerBadge = "CONTACT ENGINE"
                    )
                    return@launch
                }

                val outcome = toolExecutor.contactCallEngine.searchContactsByNameOrNumber(targetNameOrNumber)
                lastMatchedContacts = outcome.matches
                onContactMatchesUpdated(outcome.matches, "Call search: $targetNameOrNumber")

                if (outcome.status == ContactActionStatus.SINGLE_MATCH_READY && outcome.matches.size == 1) {
                    val match = outcome.matches.first()
                    val pending = PendingSensitiveAction(
                        titleBn = "Call ${match.displayName}",
                        descriptionBn = "Open ${match.displayName} (${match.phoneNumber}) in phone dialer?",
                        toolName = "openDialer",
                        args = mapOf("phone" to match.phoneNumber)
                    )
                    pendingSensitiveAction = pending
                    onSensitiveActionRequested(pending)
                }

                speak(
                    outcome.messageForLanguage(spokenLanguageCode),
                    providerBadge = "CONTACT ENGINE",
                    toolSummary = "Contact Search: $targetNameOrNumber"
                )
                return@launch
            }

            // 3. SMART MESSAGE WRITER (Requirement G)
            // Examples:
            // - "Rahul ko bolo main aaj late aaunga"
            // - "Write a birthday message for Priya"
            // - "Write an apology message to Boss"
            // - "राहुल को बोलो मैं आज लेट आऊंगा"
            // - "राहुल को मैसेज भेजो कि मीटिंग कल है"
            val isMessageWriterCommand =
                Regex("\\b(ko|को)\\s+(bolo|बोलो|batao|बताओ|message|मैसेज)", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) ||
                    lower.startsWith("write a message") ||
                    lower.startsWith("write message") ||
                    lower.startsWith("write a birthday") ||
                    lower.startsWith("write an apology") ||
                    lower.startsWith("write a professional") ||
                    lower.startsWith("send message to") ||
                    lower.startsWith("send whatsapp to") ||
                    lower.startsWith("prepare a message") ||
                    lower.contains("मैसेज लिखो") ||
                    lower.contains("मैसेज तैयार करो") ||
                    lower.contains("बर्थडे मैसेज") ||
                    lower.contains("जन्मदिन का मैसेज") ||
                    lower.contains("মেসেজ লেখো") ||
                    lower.contains("বার্তা লেখো")

            if (isMessageWriterCommand) {
                adaptiveAiManager?.recordCommandCategory("Messages")
                lastContextType = ContextTargetType.MESSAGE
                val recipientCandidate = SmartMessageWriter.extractRecipientName(trimmed)
                val resolvedPhone = if (recipientCandidate.isNotBlank() && toolExecutor.contactCallEngine.hasReadContactsPermission()) {
                    val matches = toolExecutor.contactCallEngine.searchContactsByNameOrNumber(recipientCandidate).matches
                    if (matches.size == 1) matches.first().phoneNumber else ""
                } else ""

                val draft = SmartMessageWriter.createSmartDraft(
                    instruction = trimmed,
                    spokenLanguageCode = spokenLanguageCode,
                    resolvedRecipientPhone = resolvedPhone
                )
                pendingMessageDraft = draft
                onMessageDraftUpdated(draft)

                val previewSpeech = when {
                    spokenLanguageCode.startsWith("bn") ->
                        "বস, আমি বার্তাটি সুন্দরভাবে তৈরি করেছি: \"${draft.draftedText}\"। পাঠানোর জন্য প্রিভিউ কার্ডে কনফার্ম করুন।"
                    spokenLanguageCode.startsWith("en") ->
                        "Boss, I have prepared your message: \"${draft.draftedText}\". Please review the preview card and confirm to send via WhatsApp or SMS."
                    else ->
                        "बॉस, मैंने आपका मैसेज तैयार कर दिया है: \"${draft.draftedText}\"। भेजने के लिए स्क्रीन पर प्रीव्यू कार्ड कन्फर्म करें।"
                }
                speak(
                    previewSpeech,
                    providerBadge = "MESSAGE WRITER",
                    toolSummary = "Draft for ${draft.recipientName}"
                )
                return@launch
            }

            // 4. SMART WEATHER & INTERNET / DATA USAGE (Requirement K)
            // 4a. Live Weather: "मौसम कैसा है", "आज बारिश होगी क्या", "What is the weather in Mumbai", "আজকের আবহাওয়া কেমন"
            if (lower.contains("weather") || lower.contains("मौसम") || lower.contains("तापमान") ||
                lower.contains("बारिश होगी") || lower.contains("rain forecast") ||
                lower.contains("temperature") || lower.contains("আবহাওয়া") || lower.contains("বৃষ্টি")
            ) {
                adaptiveAiManager?.recordCommandCategory("Weather & Network")
                val extractedCity = extractCityFromWeatherQuery(trimmed)
                val report = toolExecutor.weatherNetworkEngine.fetchLiveWeather(extractedCity)
                onWeatherReportUpdated(report)
                val summarySpeech = report.formatSpokenSummary(spokenLanguageCode)
                speak(
                    summarySpeech,
                    providerBadge = "LIVE WEATHER",
                    toolSummary = "${report.cityName}: ${report.temperatureCelsius.toInt()}°C • Rain ${report.rainProbabilityPercent}%"
                )
                return@launch
            }

            // 4b. Internet Status & Data Usage: "इंटरनेट और डेटा उपयोग बताओ", "How much mobile data used", "Check internet status"
            if (lower.contains("इंटरनेट") || lower.contains("नेटवर्क") ||
                lower.contains("डेटा कितना") || lower.contains("डेटा उपयोग") ||
                lower.contains("mobile data") || lower.contains("data usage") ||
                lower.contains("internet status") || lower.contains("network status") ||
                lower.contains("ইন্টারনেট") || lower.contains("ডেটা")
            ) {
                adaptiveAiManager?.recordCommandCategory("Weather & Network")
                val netReport = toolExecutor.weatherNetworkEngine.getNetworkAndDataUsage()
                val speech = netReport.formatSpokenSummary(spokenLanguageCode)
                speak(
                    speech,
                    providerBadge = "NETWORK & DATA",
                    toolSummary = "${netReport.transportType} • Used ${String.format(Locale.US, "%.1f MB", netReport.mobileDataUsedMb)}"
                )
                return@launch
            }

            // 5. Context Awareness Follow-Up Commands
            if (lower == "इसे खोलो" || lower == "उसे खोलो" || lower == "open it" || lower == "এটি খোলো") {
                if (lastContextTarget.isNotBlank()) {
                    val res = toolExecutor.execute(
                        "openApp",
                        mapOf("appName" to lastContextTarget, "openPlayStoreIfMissing" to true)
                    )
                    speak(
                        pickMsg(res, "बॉस, मैंने $lastContextTarget खोल दिया है।", "বস, আমি $lastContextTarget খুলে দিয়েছি।", "Boss, I opened $lastContextTarget."),
                        providerBadge = "CONTEXT AI",
                        toolSummary = "Context Open: $lastContextTarget"
                    )
                    return@launch
                }
            }

            if (lower == "यह वीडियो चलाओ" || lower == "ये वीडियो चलाओ" || lower == "play this video") {
                lastContextType = ContextTargetType.VIDEO
                val res = toolExecutor.execute("playVideo", mapOf("query" to "", "useCurrentQueue" to true))
                speak(
                    pickMsg(res, "बॉस, यूट्यूब वीडियो सीधा चला दिया गया है।", "বস, ইউটিউব ভিডিও চালু করা হয়েছে।", "Boss, playing the YouTube video."),
                    providerBadge = "JARVIS MEDIA",
                    toolSummary = res["summary"]?.toString()
                )
                return@launch
            }

            if (lower.contains("अगला वीडियो") || lower.contains("नेक्स्ट वीडियो") || lower.contains("next video") || lower.contains("পরের ভিডিও")) {
                lastContextType = ContextTargetType.VIDEO
                val res = toolExecutor.execute("playVideo", mapOf("query" to "", "nextVideo" to true))
                speak(
                    pickMsg(res, "बॉस, अगला यूट्यूब वीडियो चला दिया गया है।", "বস, পরের ইউটিউব ভিডিওটি চালানো হয়েছে।", "Boss, playing the next YouTube video."),
                    providerBadge = "JARVIS MEDIA",
                    toolSummary = res["summary"]?.toString()
                )
                return@launch
            }

            if (lower == "अब अगला चलाओ" || lower == "अगला चलाओ" || lower == "play next") {
                if (lastContextType == ContextTargetType.VIDEO) {
                    val res = toolExecutor.execute("playVideo", mapOf("query" to "", "nextVideo" to true))
                    speak(
                        pickMsg(res, "बॉस, अगला वीडियो चला दिया गया है।", "বস, পরের ভিডিওটি চালানো হয়েছে।", "Boss, playing next video."),
                        providerBadge = "CONTEXT AI"
                    )
                } else {
                    lastContextType = ContextTargetType.MUSIC
                    val res = toolExecutor.execute("controlMedia", mapOf("action" to "next"))
                    speak(
                        pickMsg(res, "बॉस, अगला गाना चला दिया गया है।", "বস, পরের গানটি চালানো হয়েছে।", "Boss, skipped to next track."),
                        providerBadge = "CONTEXT AI"
                    )
                }
                return@launch
            }

            // 6. Smart Memory Commands
            if (lower.startsWith("याद रखो") || lower.startsWith("मेरी बात याद रखो") ||
                lower.startsWith("remember that") || lower.startsWith("yaad rakho") ||
                lower.startsWith("মনে রেখো")
            ) {
                val fact = trimmed
                    .replace(Regex("^(याद रखो|मेरी बात याद रखो|remember that|yaad rakho|মনে রেখো)\\s*(कि|की|যে)?", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (fact.isNotEmpty()) {
                    addSmartMemory(fact)
                    val msg = when {
                        spokenLanguageCode.startsWith("bn") -> "বস, আমি এটি স্মার্ট মেমোরিতে মনে রেখেছি: '$fact'।"
                        spokenLanguageCode.startsWith("en") -> "Boss, I have saved this in Smart Memory: '$fact'."
                        else -> "जी बॉस, मैंने यह बात अपनी स्मार्ट मेमोरी में याद रख ली है: '$fact'।"
                    }
                    speak(msg, providerBadge = "SMART MEMORY", toolSummary = "Saved Memory")
                    return@launch
                }
            }

            if (lower.contains("मेरी यादें") || lower.contains("तुम्हें क्या याद है") ||
                lower.contains("what do you remember") || lower.contains("show memories")
            ) {
                val memories = getSmartMemories()
                val msg = if (memories.isEmpty()) {
                    when {
                        spokenLanguageCode.startsWith("bn") -> "বস, স্মার্ট মেমোরিতে এখনো কিছু সেভ করা নেই।"
                        spokenLanguageCode.startsWith("en") -> "Boss, there are no saved facts in Smart Memory yet."
                        else -> "बॉस, अभी स्मार्ट मेमोरी में कोई बात सेव नहीं है।"
                    }
                } else {
                    val summary = memories.take(3).joinToString("; ") { it.fact }
                    when {
                        spokenLanguageCode.startsWith("bn") -> "বস, আমার আপনার ${memories.size}টি কথা মনে আছে: $summary।"
                        spokenLanguageCode.startsWith("en") -> "Boss, I remember ${memories.size} facts: $summary."
                        else -> "बॉस, मुझे आपकी ${memories.size} बातें याद हैं: $summary।"
                    }
                }
                speak(msg, providerBadge = "SMART MEMORY")
                return@launch
            }

            // 7. Reminders & Routines
            if (lower.startsWith("रिमाइंडर") || lower.contains("रिमाइंडर सेट करो") ||
                lower.startsWith("set reminder") || lower.startsWith("remind me")
            ) {
                val remTitle = trimmed
                    .replace("रिमाइंडर सेट करो", "", ignoreCase = true)
                    .replace("रिमाइंडर", "", ignoreCase = true)
                    .replace("set reminder", "", ignoreCase = true)
                    .replace("remind me to", "", ignoreCase = true)
                    .replace("remind me", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { "Important Task" }

                addDailyReminder(remTitle)
                val msg = when {
                    spokenLanguageCode.startsWith("bn") -> "বস, আমি রিমাইন্ডার সেট করেছি: '$remTitle'।"
                    spokenLanguageCode.startsWith("en") -> "Boss, I have set a reminder for '$remTitle'."
                    else -> "जी बॉस, मैंने आपका रिमाइंडर सेट कर दिया है: '$remTitle'।"
                }
                speak(msg, providerBadge = "DAILY ASSISTANT", toolSummary = "Reminder: $remTitle")
                return@launch
            }

            if (lower.contains("मॉर्निंग रूटीन") || lower == "गुड मॉर्निंग" || lower == "सुप्रभात" || lower.contains("morning routine")) {
                executeSmartRoutine("morning")
                return@launch
            }
            if (lower.contains("स्टडी मोड") || lower.contains("वर्क मोड") || lower.contains("study mode") || lower.contains("work mode")) {
                executeSmartRoutine("study_work")
                return@launch
            }
            if (lower.contains("म्यूजिक मोड") || lower.contains("music mode")) {
                executeSmartRoutine("music_vibe")
                return@launch
            }
            if (lower.contains("नाइट मोड") || lower == "गुड नाइट" || lower == "शुभ रात्रि" || lower.contains("night routine")) {
                executeSmartRoutine("night")
                return@launch
            }

            // 8. Wake words & Greetings (Single short greeting, never 3 promotional sentences)
            if (lower == "नमस्ते" || lower == "हेलो" || lower == "हाय" ||
                lower == "नमस्ते कैरोलाइन" || lower == "हेलो कैरोलाइन" || lower == "हे कैरोलाइन" ||
                lower == "hey caroline" || lower == "hello caroline" || lower == "hi caroline" ||
                lower == "caroline" || lower == "कैरोलाइन" || lower == "নমস্কার" || lower == "হ্যালো"
            ) {
                speak(getGreetingForActiveLanguage(), providerBadge = "JARVIS CORE")
                return@launch
            }

            // 9. Time & Date check
            if (lower.contains("समय क्या") || lower.contains("टाइम क्या") ||
                lower.contains("कितने बजे") || lower.contains("समय बताओ") ||
                lower.contains("आज की तारीख") || lower.contains("what time") ||
                lower.contains("current time") || lower.contains("সময় কত") || lower.contains("সময় কত")
            ) {
                val info = toolExecutor.execute("getDeviceInfo", emptyMap())
                val timeStr = info["current_time"] ?: ""
                val dateStr = info["current_date"] ?: ""
                val msg = when {
                    spokenLanguageCode.startsWith("bn") -> "বস, এখন সময় $timeStr এবং आजকের তারিখ $dateStr।"
                    spokenLanguageCode.startsWith("en") -> "Boss, the current time is $timeStr and today is $dateStr."
                    else -> "बॉस, अभी समय $timeStr है और आज की तारीख $dateStr है।"
                }
                speak(msg, providerBadge = "DAILY ASSISTANT", toolSummary = "Time: $timeStr")
                return@launch
            }

            // 10. Battery & RAM check (ONLY when explicitly requested)
            if (lower.contains("बैटरी") || lower.contains("चार्ज कितना") ||
                lower.contains("battery") || lower.contains("रैम") || lower.contains("ব্যাটারি")
            ) {
                val info = toolExecutor.execute("getDeviceInfo", emptyMap())
                val bat = info["battery_level"] ?: 100
                val charging = info["is_charging"] == true
                val availRam = info["avail_ram_mb"] ?: 2048
                val msg = when {
                    spokenLanguageCode.startsWith("bn") ->
                        "বস, আপনার ফোনের ব্যাটারি $bat শতাংশ এবং $availRam MB র‍্যাম খালি আছে।"
                    spokenLanguageCode.startsWith("en") ->
                        "Boss, your battery is at $bat% (${if (charging) "charging" else "unplugged"}) with ${availRam}MB available RAM."
                    else ->
                        "बॉस, आपके फोन की बैटरी $bat प्रतिशत है और $availRam एमबी रैम उपलब्ध है।"
                }
                speak(msg, providerBadge = "JARVIS TELEMETRY", toolSummary = "Battery: $bat% • RAM: ${availRam}MB")
                return@launch
            }

            // 11. Flashlight ON/OFF
            if (lower.contains("फ्लैशलाइट") || lower.contains("टॉर्च") ||
                lower.contains("flashlight") || lower.contains("torch") || lower.contains("ফ্ল্যাশলাইট")
            ) {
                val turnOff = lower.contains("बंद") || lower.contains("ऑफ") || lower.contains("off") || lower.contains("বন্ধ")
                val turnOn = !turnOff
                lastContextType = ContextTargetType.FLASHLIGHT
                val res = toolExecutor.execute("toggleFlashlight", mapOf("enabled" to turnOn))
                speak(
                    pickMsg(res, "बॉस, फ्लैशलाइट कमांड पूरा कर दिया गया है।", "বস, ফ্ল্যাশলাইট কমান্ড সম্পন্ন হয়েছে।", "Boss, flashlight updated."),
                    providerBadge = "JARVIS HARDWARE",
                    toolSummary = "Flashlight: ${if (turnOn) "ON" else "OFF"}"
                )
                return@launch
            }

            // 12. Screen Brightness Settings (Requirement J)
            if (lower.contains("brightness") || lower.contains("ब्राइटनेस") || lower.contains("উজ্জ্বলতা")) {
                val res = toolExecutor.execute("openSystemSettings", mapOf("section" to "brightness"))
                speak(
                    pickMsg(res, "बॉस, स्क्रीन ब्राइटनेस सेटिंग्स खोल दी गई हैं।", "বস, স্ক্রিন ব্রাইটনেস সেটিংস খোলা হয়েছে।", "Boss, opened screen brightness settings."),
                    providerBadge = "JARVIS HARDWARE",
                    toolSummary = "Brightness Settings"
                )
                return@launch
            }

            // 13. Volume Controls
            if (lower.contains("आवाज़") || lower.contains("आवाज") ||
                lower.contains("वॉल्यूम") || lower.contains("साउंड बढ़ाओ") ||
                lower.contains("साउंड कम") || lower.contains("volume") || lower.contains("ভলিউম")
            ) {
                val numMatch = Regex("(\\d{1,3})").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val direction = when {
                    lower.contains("कम") || lower.contains("धीमा") || lower.contains("down") || lower.contains("lower") || lower.contains("কমাও") -> "down"
                    lower.contains("म्यूट") || lower.contains("mute") -> "mute"
                    else -> "up"
                }
                val res = toolExecutor.execute("adjustVolume", mapOf("direction" to direction, "level" to numMatch))
                speak(
                    pickMsg(res, "बॉस, फोन की आवाज़ बदल दी गई है।", "বস, ফোনের ভলিউম পরিবর্তন করা হয়েছে।", "Boss, media volume adjusted."),
                    providerBadge = "JARVIS AUDIO",
                    toolSummary = "Volume Adjusted"
                )
                return@launch
            }

            // 14. MUSIC COMMANDS (Requirement H)
            if (lower.contains("गाना पॉज") || lower.contains("म्यूजिक पॉज") ||
                lower.contains("गाना रोको") || lower.contains("pause music") ||
                lower.contains("pause song") || lower.contains("মিউজিক পজ")
            ) {
                adaptiveAiManager?.recordCommandCategory("Music & Video")
                lastContextType = ContextTargetType.MUSIC
                val res = toolExecutor.execute("controlMedia", mapOf("action" to "pause"))
                speak(
                    pickMsg(res, "बॉस, गाना पॉज कर दिया गया है।", "বস, গানটি পজ করা হয়েছে।", "Boss, music paused."),
                    providerBadge = "JARVIS MUSIC",
                    toolSummary = "Music Paused"
                )
                return@launch
            }

            if (lower.contains("गाना चालू") || lower.contains("म्यूजिक चालू") ||
                lower.contains("resume music") || lower.contains("resume song")
            ) {
                adaptiveAiManager?.recordCommandCategory("Music & Video")
                lastContextType = ContextTargetType.MUSIC
                val res = toolExecutor.execute("controlMedia", mapOf("action" to "resume"))
                speak(
                    pickMsg(res, "बॉस, गाना फिर से चालू कर दिया गया है।", "বস, গানটি আবার চালু করা হয়েছে।", "Boss, music resumed."),
                    providerBadge = "JARVIS MUSIC",
                    toolSummary = "Music Resumed"
                )
                return@launch
            }

            if (lower.contains("म्यूजिक बंद") || lower.contains("गाना बंद") ||
                lower.contains("stop music") || lower.contains("গান বন্ধ")
            ) {
                adaptiveAiManager?.recordCommandCategory("Music & Video")
                lastContextType = ContextTargetType.MUSIC
                val res = toolExecutor.execute("controlMedia", mapOf("action" to "stop"))
                speak(
                    pickMsg(res, "बॉस, म्यूजिक बंद कर दिया गया है।", "বস, মিউজিক বন্ধ করা হয়েছে।", "Boss, music stopped."),
                    providerBadge = "JARVIS MUSIC",
                    toolSummary = "Music Stopped"
                )
                return@launch
            }

            if (lower.contains("अगला गाना") || lower.contains("नेक्स्ट गाना") ||
                lower.contains("नेक्स्ट सॉन्ग") || lower.contains("next song") ||
                lower.contains("next track") || lower.contains("পরের গান")
            ) {
                adaptiveAiManager?.recordCommandCategory("Music & Video")
                lastContextType = ContextTargetType.MUSIC
                val res = toolExecutor.execute("controlMedia", mapOf("action" to "next"))
                speak(
                    pickMsg(res, "बॉस, अगला गाना चला दिया गया है।", "বস, পরের গানটি চালানো হয়েছে।", "Boss, skipped to next track."),
                    providerBadge = "JARVIS MUSIC",
                    toolSummary = "Next Track"
                )
                return@launch
            }

            if (lower.contains("गाना चलाओ") || lower.contains("गाना बजाओ") ||
                lower.contains("म्यूजिक चलाओ") || lower.contains("म्यूजिक बजाओ") ||
                lower.contains("सॉन्ग चलाओ") || lower.contains("पसंदीदा गाना") ||
                lower.contains("gaana chalao") || lower.contains("music chalao") ||
                lower.startsWith("play music") || lower.startsWith("play song") ||
                lower.startsWith("play ") && !lower.contains("video") && !lower.contains("youtube") ||
                lower.contains("গান চালাও")
            ) {
                adaptiveAiManager?.recordCommandCategory("Music & Video")
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
                        .replace("gaana chalao", "", ignoreCase = true)
                        .replace("music chalao", "", ignoreCase = true)
                        .replace("play music", "", ignoreCase = true)
                        .replace("play song", "", ignoreCase = true)
                        .replace(Regex("^play\\s+", RegexOption.IGNORE_CASE), "")
                        .replace("গান চালাও", "", ignoreCase = true)
                        .trim()
                        .ifEmpty { "Arijit Singh Kesariya" }
                }

                lastContextType = ContextTargetType.MUSIC
                lastContextTarget = extractedSong
                val res = toolExecutor.execute("playMusic", mapOf("query" to extractedSong))
                speak(
                    pickMsg(res, "बॉस, '$extractedSong' गाना चालू कर दिया गया है।", "বস, '$extractedSong' গানটি চালু করা হয়েছে।", "Boss, playing '$extractedSong'."),
                    providerBadge = "JARVIS MUSIC",
                    toolSummary = res["summary"]?.toString() ?: "Play Music: $extractedSong"
                )
                return@launch
            }

            // 15. VIDEO COMMANDS (Requirement H)
            if (lower.contains("वीडियो चलाओ") || lower.contains("वीडियो दिखाओ") ||
                lower.contains("वीडियो प्ले") || lower.contains("यूट्यूब पर") ||
                lower.contains("video chalao") || lower.contains("video dikhao") ||
                lower.startsWith("play video") || lower.startsWith("play youtube") ||
                lower.startsWith("watch video") || lower.contains("ভিডিও চালাও")
            ) {
                adaptiveAiManager?.recordCommandCategory("Music & Video")
                val videoTopic = trimmed
                    .replace("कैरोलाइन", "", ignoreCase = true)
                    .replace("यूट्यूब पर", "", ignoreCase = true)
                    .replace("वीडियो चलाओ", "", ignoreCase = true)
                    .replace("वीडियो दिखाओ", "", ignoreCase = true)
                    .replace("का वीडियो", "", ignoreCase = true)
                    .replace("video chalao", "", ignoreCase = true)
                    .replace("play youtube video", "", ignoreCase = true)
                    .replace("play video", "", ignoreCase = true)
                    .replace("on youtube", "", ignoreCase = true)
                    .replace("ভিডিও চালাও", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { "Technology" }

                lastContextType = ContextTargetType.VIDEO
                lastContextTarget = videoTopic
                val res = toolExecutor.execute("playVideo", mapOf("query" to videoTopic))
                speak(
                    pickMsg(res, "बॉस, यूट्यूब पर '$videoTopic' का वीडियो चला दिया गया है।", "বস, ইউটিউবে '$videoTopic' ভিডিওটি চালানো হয়েছে।", "Boss, playing '$videoTopic' video on YouTube."),
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
                speak(
                    pickMsg(res, "बॉस, कैमरा खोल दिया गया है।", "বস, ক্যামেরা খোলা হয়েছে।", "Boss, opened the camera."),
                    providerBadge = "JARVIS OS"
                )
                return@launch
            }

            // 17. Android System Settings (Wi-Fi, Bluetooth, System)
            if (lower.contains("सेटिंग्स खोलो") || lower.contains("वाईफाई सेटिंग्स") ||
                lower.contains("ब्लूटूथ सेटिंग्स") || lower.contains("open settings") ||
                lower.contains("wifi settings") || lower.contains("bluetooth settings") ||
                lower.contains("সেটিংস খোলো")
            ) {
                val res = toolExecutor.execute("openSystemSettings", mapOf("section" to lower))
                speak(
                    pickMsg(res, "बॉस, सेटिंग्स खोल दी गई हैं।", "বস, সেটিংস খুলে দেওয়া হয়েছে।", "Boss, opened system settings."),
                    providerBadge = "JARVIS OS"
                )
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
                    .replace("navigate to", "", ignoreCase = true)
                    .replace("open maps for", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { "nearby restaurants" }
                val res = toolExecutor.execute("openMapsNavigation", mapOf("destination" to dest))
                speak(
                    pickMsg(res, "बॉस, गूगल मैप्स खोल दिया गया है।", "বস, গুগল ম্যাপ খোলা হয়েছে।", "Boss, opened Google Maps navigation."),
                    providerBadge = "JARVIS MAPS"
                )
                return@launch
            }

            // 19. Share Text
            if (lower.startsWith("शेयर करो") || lower.startsWith("share ")) {
                val contentToShare = trimmed
                    .replace("शेयर करो", "")
                    .replace("share", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { lastSpokenResponse }
                val res = toolExecutor.execute("shareText", mapOf("text" to contentToShare))
                speak(
                    pickMsg(res, "बॉस, शेयर विकल्प खोल दिया गया है।", "বস, শেয়ার অপশন খোলা হয়েছে।", "Boss, opened share options."),
                    providerBadge = "JARVIS OS"
                )
                return@launch
            }

            // 20. Smart Application Launcher (Requirement I)
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
                    speak(
                        pickMsg(res, "बॉस, ऐप कमांड प्रोसेस कर दिया गया है।", "বস, অ্যাপ কমান্ড সম্পন্ন হয়েছে।", "Boss, processed app launch command."),
                        providerBadge = "APP LAUNCHER",
                        toolSummary = "App: $candidateApp"
                    )
                    return@launch
                }
            }

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
                    pickMsg(res, "", "", ""),
                    providerBadge = "APP LAUNCHER",
                    toolSummary = "App: ${directCatalogMatch.displayName}"
                )
                return@launch
            }

            // 21. Google Web Search & Website
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
                    .ifEmpty { "latest news" }
                lastQueryTopic = query
                lastContextType = ContextTargetType.TOPIC
                lastContextTarget = query
                val res = toolExecutor.execute("searchWeb", mapOf("query" to query))
                speak(
                    pickMsg(res, "बॉस, मैंने गूगल पर सर्च कर दिया है।", "বস, আমি গুগলে সার্চ করেছি।", "Boss, I have searched Google."),
                    providerBadge = "JARVIS WEB"
                )
                return@launch
            }

            // 22. Identity Question
            if (lower.contains("तुम कौन हो") || lower.contains("तुम्हारा नाम") ||
                lower.contains("आप कौन हैं") || lower.contains("who are you") ||
                lower.contains("তুমি কে")
            ) {
                val msg = when {
                    spokenLanguageCode.startsWith("bn") ->
                        "নমস্কার বস! আমি ক্যারোলাইন এ-থ্রি পয়েন্ট টু আল্টিমেট স্মার্ট জার্ভিস। আপনার ফোনের অ্যাপ, কল, মেসেজ, আবহাওয়া এবং মিউজিক নিয়ন্ত্রণের জন্য আমি প্রস্তুত।"
                    spokenLanguageCode.startsWith("en") ->
                        "Hello Boss! I am CaroLine AI A3.2 Ultimate Smart JARVIS, your intelligent assistant for contacts, calls, smart messages, live weather, music, and apps."
                    else ->
                        "नमस्ते बॉस! मैं कैरोलाइन एआई ए-थ्री पॉइंट टू (A3.2) अल्टीमेट स्मार्ट जार्विस हूँ। मैं आपके कॉन्टैक्ट्स, कॉल, स्मार्ट मैसेज, लाइव मौसम, म्यूजिक और ऐप्स में मदद के लिए तैयार हूँ।"
                }
                speak(msg, providerBadge = "JARVIS CORE")
                return@launch
            }

            // 23. Route general questions & conversations to the Managed Cloud AI Brain
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

            // 24. Intelligent On-Device Multi-Language Fallback (Hindi, Bengali, or English)
            val fallbackReply = buildIntelligentLocalReply(trimmed, lower)
            speak(fallbackReply, providerBadge = "JARVIS LOCAL")
        }
    }

    private fun extractCityFromWeatherQuery(raw: String): String {
        val cleaned = raw
            .replace("कैरोलाइन", "", ignoreCase = true)
            .replace("आज का मौसम", "", ignoreCase = true)
            .replace("मौसम कैसा है", "", ignoreCase = true)
            .replace("मौसम बताओ", "", ignoreCase = true)
            .replace("मौसम", "", ignoreCase = true)
            .replace("तापमान", "", ignoreCase = true)
            .replace("बारिश होगी क्या", "", ignoreCase = true)
            .replace("में", "", ignoreCase = true)
            .replace("का", "", ignoreCase = true)
            .replace("what is the weather in", "", ignoreCase = true)
            .replace("weather in", "", ignoreCase = true)
            .replace("weather", "", ignoreCase = true)
            .replace("temperature in", "", ignoreCase = true)
            .replace("আবহাওয়া কেমন", "", ignoreCase = true)
            .replace("আবহাওয়া", "", ignoreCase = true)
            .replace("?", "")
            .trim()
        return cleaned.ifEmpty { "New Delhi" }
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

    private fun buildIntelligentLocalReply(rawInput: String, lower: String): String {
        return when {
            spokenLanguageCode.startsWith("bn", ignoreCase = true) -> when {
                lower.contains("কেমন আছো") || lower.contains("how are you") ->
                    "আমি দারুণ আছি বস! বলুন, আজ কীভাবে আপনাকে সাহায্য করতে পারি?"
                lower.contains("ধন্যবাদ") || lower.contains("thank") ->
                    "আপনাকে অনেক স্বাগতম বস!"
                else ->
                    "বস, আমি আপনার কথা বুঝতে পেরেছি: '$rawInput'। আপনি আমাকে কল করতে, মেসেজ লিখতে, আবহাওয়া জানতে বা গান চালাতে বলতে পারেন।"
            }
            spokenLanguageCode.startsWith("en", ignoreCase = true) -> when {
                lower.contains("how are you") ->
                    "I am doing great, Boss! How can I assist you today?"
                lower.contains("thank") ->
                    "You are most welcome, Boss!"
                else ->
                    "Boss, I understood your request: '$rawInput'. You can ask me to call contacts, find numbers ending in specific digits, draft messages, check live weather, or play music."
            }
            else -> when {
                lower.contains("कैसी हो") || lower.contains("कैसे हो") || lower.contains("how are you") ->
                    "मैं बिल्कुल बढ़िया हूँ बॉस! बताइए, आज हम क्या करें?"
                lower.contains("धन्यवाद") || lower.contains("थैंक यू") || lower.contains("शुक्रिया") || lower.contains("thank") ->
                    "आपका बहुत-बहुत स्वागत है बॉस! आपकी मदद करना मेरा काम है।"
                lower.contains("जोक") || lower.contains("चुटकुला") || lower.contains("joke") ->
                    "बॉस, एक मज़ेदार बात सुनिए—एक प्रोग्रामर चश्मा क्यों पहनता है? क्योंकि वह सी-शार्प नहीं देख पाता!"
                else ->
                    "जी बॉस, मैंने आपकी बात समझ ली: '$rawInput'। आप मुझसे कॉन्टैक्ट ढूंढने, कॉल करने, स्मार्ट मैसेज लिखने, लाइव मौसम जानने या गाना चलाने के लिए कह सकते हैं।"
            }
        }
    }
}
