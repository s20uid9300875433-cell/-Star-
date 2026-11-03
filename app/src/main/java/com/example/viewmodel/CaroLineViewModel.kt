package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.audio.AudioPlayer
import com.example.audio.AudioStreamer
import com.example.audio.BengaliSpeechRecognizer
import com.example.live.CaroLineBrainEngine
import com.example.live.GeminiLiveSession
import com.example.live.LiveSessionListener
import com.example.live.LiveState
import com.example.service.CaroLineVoiceService
import com.example.tools.ToolExecutionEvent
import com.example.tools.ToolExecutor
import com.example.ui.components.AvatarEmotion
import com.example.ui.components.AvatarStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ApiMode {
    AUTO_FAILOVER,
    PRIMARY_ONLY,
    BACKUP_ONLY
}

enum class ActiveEngine {
    PRIMARY_GEMINI_LIVE,
    BACKUP_SECONDARY_AI,
    BENGALI_VOICE_ENGINE
}

enum class AvatarMode {
    PROCEDURAL_ANIMATED_FACE,
    ANIME_ARTWORK
}

data class CaroLineUiState(
    val appVersion: String = "GREAT V2.1",
    val liveState: LiveState = LiveState.DISCONNECTED,
    val isMicMuted: Boolean = false,
    val isHandsFreeMode: Boolean = true,
    val isMicHardwareVerified: Boolean = false,
    val speechSnippet: String = "হ্যালো বস! আমি ক্যারোলাইন। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?",
    val activeToolEvent: ToolExecutionEvent? = null,
    val toolHistory: List<ToolExecutionEvent> = emptyList(),
    // Dual API configuration
    val primaryApiKey: String = "",
    val backupApiKey: String = "",
    val apiMode: ApiMode = ApiMode.AUTO_FAILOVER,
    val activeEngine: ActiveEngine = ActiveEngine.BENGALI_VOICE_ENGINE,
    // Voice & Language Lock (Bengali Default)
    val selectedVoice: String = "Aoede",
    val activeLanguage: String = "বাংলা (Bengali)",
    val isLanguageLocked: Boolean = true,
    val languageLockPin: String = "1234",
    // Background Foreground Service
    val isBackgroundServiceEnabled: Boolean = true,
    val isForegroundServiceRunning: Boolean = false,
    // Avatar Styles & Emotion Expressions
    val avatarStyle: AvatarStyle = AvatarStyle.GOTHIC_CYBER,
    val customAvatarUri: Uri? = null,
    val currentEmotion: AvatarEmotion = AvatarEmotion.LISTENING,
    val manualEmotionOverride: AvatarEmotion? = null,
    val errorMessage: String? = null
)

// Backward compatibility alias
typealias MahiUiState = CaroLineUiState

class CaroLineViewModel(application: Application) : AndroidViewModel(application), LiveSessionListener {

    companion object {
        private const val TAG = "CaroLineViewModel"
        private const val PREFS_NAME = "caroline_ai_prefs"
        private const val KEY_PRIMARY_API_KEY = "primary_gemini_api_key"
        private const val KEY_BACKUP_API_KEY = "backup_secondary_api_key"
        private const val KEY_API_MODE = "api_failover_mode"
        private const val KEY_VOICE = "gemini_voice"
        private const val KEY_HANDS_FREE = "hands_free_mode"
        private const val KEY_AVATAR_STYLE = "avatar_style_name"
        private const val KEY_CUSTOM_URI = "custom_avatar_uri"
        private const val KEY_LANGUAGE_LOCKED = "bengali_language_locked"
        private const val KEY_LANGUAGE_PIN = "owner_language_pin"
        private const val KEY_ACTIVE_LANGUAGE = "active_language_display"
        private const val KEY_BG_SERVICE_ENABLED = "background_service_enabled"
    }

    private val appContext = application.applicationContext
    private val sharedPrefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val toolExecutor = ToolExecutor(appContext)

    private val audioPlayer = AudioPlayer(
        onPlaybackStateChanged = { isPlaying ->
            audioStreamer.isPlaybackActive = isPlaying
            if (isPlaying && _uiState.value.liveState == LiveState.LISTENING) {
                _uiState.value = _uiState.value.copy(
                    liveState = LiveState.SPEAKING,
                    currentEmotion = AvatarEmotion.SPEAKING
                )
            } else if (!isPlaying && _uiState.value.liveState == LiveState.SPEAKING) {
                _uiState.value = _uiState.value.copy(
                    liveState = LiveState.LISTENING,
                    currentEmotion = AvatarEmotion.LISTENING
                )
                // Hands-free continuous auto-listen recovery
                if (_uiState.value.isHandsFreeMode && _uiState.value.liveState != LiveState.DISCONNECTED) {
                    ensureContinuousListeningActive()
                }
            }
        }
    )

    private val audioStreamer: AudioStreamer = AudioStreamer(
        onAudioChunk = { chunk ->
            if (geminiLiveSession != null && _uiState.value.activeEngine == ActiveEngine.PRIMARY_GEMINI_LIVE) {
                geminiLiveSession?.sendAudioChunk(chunk)
            }
        },
        onUtteranceCaptured = { wavBytes ->
            handleCapturedWavUtterance(wavBytes)
        },
        onMicError = { errorMsg ->
            Log.e(TAG, "AudioStreamer error: $errorMsg")
            _uiState.value = _uiState.value.copy(
                liveState = LiveState.ERROR,
                isMicHardwareVerified = false,
                errorMessage = errorMsg
            )
        }
    )

    private var geminiLiveSession: GeminiLiveSession? = null

    private val fallbackRestClient by lazy {
        GeminiLiveSession(toolExecutor, this)
    }

    private val bengaliSpeechRecognizer = BengaliSpeechRecognizer(
        context = appContext,
        onPartialTranscript = { partial ->
            if (partial.isNotBlank() && _uiState.value.liveState == LiveState.LISTENING) {
                _uiState.value = _uiState.value.copy(speechSnippet = "🎙️ \"$partial...\"")
            }
        },
        onFinalTranscript = { recognizedText ->
            handleRecognizedBengaliSpeech(recognizedText)
        },
        onRmsAmplitude = { rmsNormalized, voiceDetected ->
            audioStreamer.updateExternalAmplitude(rmsNormalized, voiceDetected)
        },
        onListeningStateChanged = { isListening ->
            if (isListening && _uiState.value.liveState != LiveState.SPEAKING && _uiState.value.liveState != LiveState.DISCONNECTED) {
                _uiState.value = _uiState.value.copy(
                    liveState = LiveState.LISTENING,
                    isMicHardwareVerified = true,
                    currentEmotion = AvatarEmotion.LISTENING,
                    errorMessage = null
                )
            }
        },
        onErrorDiagnostic = { notice ->
            _uiState.value = _uiState.value.copy(
                errorMessage = notice
            )
        }
    )

    private val brainEngine: CaroLineBrainEngine = CaroLineBrainEngine(
        context = appContext,
        toolExecutor = toolExecutor,
        onStateChanged = { state ->
            if (_uiState.value.activeEngine != ActiveEngine.PRIMARY_GEMINI_LIVE) {
                val emotion = when (state) {
                    LiveState.SPEAKING -> AvatarEmotion.SPEAKING
                    LiveState.LISTENING -> AvatarEmotion.LISTENING
                    LiveState.CONNECTING, LiveState.RECONNECTING, LiveState.THINKING -> AvatarEmotion.THINKING
                    LiveState.ERROR -> AvatarEmotion.SURPRISED
                    LiveState.DISCONNECTED -> AvatarEmotion.LISTENING
                }
                _uiState.value = _uiState.value.copy(liveState = state, currentEmotion = emotion)
            }
        },
        onSpeechSnippet = { text ->
            _uiState.value = _uiState.value.copy(speechSnippet = text)
        },
        onSpeakingFinished = {
            if (_uiState.value.liveState != LiveState.DISCONNECTED) {
                _uiState.value = _uiState.value.copy(
                    liveState = LiveState.LISTENING,
                    currentEmotion = AvatarEmotion.LISTENING
                )
                if (_uiState.value.isHandsFreeMode) {
                    bengaliSpeechRecognizer.resumeAfterAiSpeech()
                    ensureContinuousListeningActive()
                }
            }
        },
        onStopVoiceModeRequested = {
            stopSession()
        }
    )

    private val _uiState = MutableStateFlow(CaroLineUiState())
    val uiState: StateFlow<CaroLineUiState> = _uiState.asStateFlow()

    // Real-time voice visualizer amplitude
    val inputAmplitude = audioStreamer.inputAmplitude
    val outputAmplitude = audioPlayer.outputAmplitude

    val visualizerAmplitude: StateFlow<Float> = combine(
        inputAmplitude,
        outputAmplitude,
        brainEngine.isSpeaking
    ) { inAmp, outAmp, isBrainSpeaking ->
        when {
            outAmp > 0.05f -> outAmp
            isBrainSpeaking -> 0.45f + (Math.random().toFloat() * 0.35f)
            inAmp > 0.02f -> inAmp
            else -> 0f
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    private var bargeInMonitorJob: Job? = null

    init {
        // Load Dual API Keys
        val savedPrimaryKey = sharedPrefs.getString(KEY_PRIMARY_API_KEY, "") ?: ""
        val initialPrimaryKey = if (savedPrimaryKey.isNotBlank()) savedPrimaryKey else {
            val buildKey = try { BuildConfig.GEMINI_API_KEY } catch (e: Exception) { "" }
            if (buildKey != "MY_GEMINI_API_KEY") buildKey else ""
        }

        val savedBackupKey = sharedPrefs.getString(KEY_BACKUP_API_KEY, "") ?: ""
        val savedModeStr = sharedPrefs.getString(KEY_API_MODE, ApiMode.AUTO_FAILOVER.name) ?: ApiMode.AUTO_FAILOVER.name
        val savedMode = try { ApiMode.valueOf(savedModeStr) } catch (e: Exception) { ApiMode.AUTO_FAILOVER }

        val savedVoice = sharedPrefs.getString(KEY_VOICE, "Aoede") ?: "Aoede"
        val savedHandsFree = sharedPrefs.getBoolean(KEY_HANDS_FREE, true)

        val savedStyleStr = sharedPrefs.getString(KEY_AVATAR_STYLE, AvatarStyle.GOTHIC_CYBER.name) ?: AvatarStyle.GOTHIC_CYBER.name
        val savedStyle = try { AvatarStyle.valueOf(savedStyleStr) } catch (e: Exception) { AvatarStyle.GOTHIC_CYBER }

        val customUriStr = sharedPrefs.getString(KEY_CUSTOM_URI, null)
        val customUri = if (customUriStr != null) try { Uri.parse(customUriStr) } catch (e: Exception) { null } else null

        // Bengali language is permanently default
        val savedLanguageLocked = sharedPrefs.getBoolean(KEY_LANGUAGE_LOCKED, true)
        val savedPin = sharedPrefs.getString(KEY_LANGUAGE_PIN, "1234") ?: "1234"
        val savedLanguage = if (savedLanguageLocked) {
            "বাংলা (Bengali)"
        } else {
            sharedPrefs.getString(KEY_ACTIVE_LANGUAGE, "বাংলা (Bengali)") ?: "বাংলা (Bengali)"
        }
        val savedBgService = sharedPrefs.getBoolean(KEY_BG_SERVICE_ENABLED, true)

        brainEngine.isLanguageLockedToBengali = savedLanguageLocked
        brainEngine.ownerLanguagePin = savedPin

        _uiState.value = _uiState.value.copy(
            primaryApiKey = initialPrimaryKey,
            backupApiKey = savedBackupKey,
            apiMode = savedMode,
            selectedVoice = savedVoice,
            isHandsFreeMode = savedHandsFree,
            avatarStyle = savedStyle,
            customAvatarUri = customUri,
            activeLanguage = savedLanguage,
            isLanguageLocked = savedLanguageLocked,
            languageLockPin = savedPin,
            isBackgroundServiceEnabled = savedBgService
        )

        // Listen for tool execution events
        viewModelScope.launch {
            toolExecutor.toolEvents.collect { event ->
                val updatedList = listOf(event) + _uiState.value.toolHistory.take(19)
                _uiState.value = _uiState.value.copy(
                    activeToolEvent = event,
                    toolHistory = updatedList
                )
            }
        }

        // Observe Foreground Voice Service state & actions
        viewModelScope.launch {
            CaroLineVoiceService.isServiceRunning.collect { running ->
                _uiState.value = _uiState.value.copy(isForegroundServiceRunning = running)
            }
        }

        viewModelScope.launch {
            CaroLineVoiceService.stopRequestedEvents.collect {
                stopSession()
            }
        }

        viewModelScope.launch {
            CaroLineVoiceService.networkRestoredEvents.collect {
                if (_uiState.value.liveState == LiveState.ERROR || _uiState.value.liveState == LiveState.RECONNECTING) {
                    Log.i(TAG, "Network restored; reconnecting active voice session")
                    startSession()
                }
            }
        }

        // Barge-in interruption monitor when using AudioRecord
        bargeInMonitorJob = viewModelScope.launch {
            inputAmplitude.collect { amp ->
                if (_uiState.value.liveState == LiveState.SPEAKING &&
                    _uiState.value.activeEngine == ActiveEngine.PRIMARY_GEMINI_LIVE &&
                    amp > 0.22f
                ) {
                    handleUserInterruption()
                }
            }
        }
    }

    /**
     * Main Voice Mode / Call Button handler.
     */
    fun triggerCallSession() {
        if (_uiState.value.liveState == LiveState.DISCONNECTED || _uiState.value.liveState == LiveState.ERROR) {
            startSession()
        } else {
            stopSession()
        }
    }

    fun startSession() {
        _uiState.value = _uiState.value.copy(errorMessage = null)

        val primaryKey = _uiState.value.primaryApiKey
        val backupKey = _uiState.value.backupApiKey
        val mode = _uiState.value.apiMode

        val canUsePrimary = mode != ApiMode.BACKUP_ONLY && primaryKey.isNotBlank() && primaryKey != "MY_GEMINI_API_KEY"
        val canUseBackup = mode != ApiMode.PRIMARY_ONLY && backupKey.isNotBlank()

        if (_uiState.value.isBackgroundServiceEnabled) {
            CaroLineVoiceService.startOrUpdate(
                appContext,
                "ক্যারোলাইন সক্রিয় আছে — বাংলা ভয়েস কমান্ড শুনছে"
            )
        }

        if (canUsePrimary) {
            // Primary: Gemini Live Bidirectional WebSocket + AudioRecord 16kHz PCM
            bengaliSpeechRecognizer.stopListening()

            val micStarted = audioStreamer.start()
            if (!micStarted) {
                _uiState.value = _uiState.value.copy(
                    liveState = LiveState.ERROR,
                    isMicHardwareVerified = false,
                    currentEmotion = AvatarEmotion.SURPRISED,
                    errorMessage = "মাইক্রোফোন চালু করা যায়নি। অনুগ্রহ করে মাইক্রোফোন পারমিশন এবং অডিও অ্যাক্সেস চেক করুন।"
                )
                return
            }

            _uiState.value = _uiState.value.copy(
                activeEngine = ActiveEngine.PRIMARY_GEMINI_LIVE,
                liveState = LiveState.CONNECTING,
                isMicHardwareVerified = true,
                currentEmotion = AvatarEmotion.THINKING,
                speechSnippet = "ক্যারোলাইন লাইভ ভয়েস সেশনের সাথে সংযোগ স্থাপন করছে..."
            )

            geminiLiveSession?.disconnect()
            geminiLiveSession = GeminiLiveSession(toolExecutor, this).apply {
                selectedVoice = _uiState.value.selectedVoice
                activeLanguageName = _uiState.value.activeLanguage
                isLanguageLockedToBengali = _uiState.value.isLanguageLocked
                connect(primaryKey)
            }
        } else {
            // Backup AI or Built-in Bengali Voice Engine
            val engine = if (canUseBackup) ActiveEngine.BACKUP_SECONDARY_AI else ActiveEngine.BENGALI_VOICE_ENGINE

            if (bengaliSpeechRecognizer.isAvailable()) {
                audioStreamer.stop()
                _uiState.value = _uiState.value.copy(
                    activeEngine = engine,
                    liveState = LiveState.LISTENING,
                    isMicHardwareVerified = true,
                    currentEmotion = AvatarEmotion.LISTENING,
                    speechSnippet = "হ্যালো বস! আমি ক্যারোলাইন। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?"
                )
                // Pause recognizer during greeting playback; onSpeakingFinished will automatically resume it
                bengaliSpeechRecognizer.startContinuousListening()
                bengaliSpeechRecognizer.pauseForAiSpeech()
                brainEngine.speak("হ্যালো বস! আমি ক্যারোলাইন। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?")
            } else {
                // Fallback to AudioRecord VAD utterance capture if SpeechRecognizer service is absent
                val micStarted = audioStreamer.start()
                if (!micStarted) {
                    _uiState.value = _uiState.value.copy(
                        liveState = LiveState.ERROR,
                        isMicHardwareVerified = false,
                        currentEmotion = AvatarEmotion.SURPRISED,
                        errorMessage = "মাইক্রোফোন অ্যাক্সেস ব্যর্থ হয়েছে। অনুগ্রহ করে পারমিশন চেক করুন।"
                    )
                    return
                }
                _uiState.value = _uiState.value.copy(
                    activeEngine = engine,
                    liveState = LiveState.LISTENING,
                    isMicHardwareVerified = true,
                    currentEmotion = AvatarEmotion.LISTENING,
                    speechSnippet = "হ্যালো বস! আমি ক্যারোলাইন। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?"
                )
                brainEngine.speak("হ্যালো বস! আমি ক্যারোলাইন। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?")
            }
        }
    }

    private fun ensureContinuousListeningActive() {
        if (_uiState.value.liveState == LiveState.DISCONNECTED) return

        if (_uiState.value.activeEngine == ActiveEngine.PRIMARY_GEMINI_LIVE) {
            if (!audioStreamer.isRecording.value) {
                val ok = audioStreamer.start()
                _uiState.value = _uiState.value.copy(isMicHardwareVerified = ok)
            }
        } else {
            if (bengaliSpeechRecognizer.isAvailable()) {
                bengaliSpeechRecognizer.resumeAfterAiSpeech()
            } else if (!audioStreamer.isRecording.value) {
                val ok = audioStreamer.start()
                _uiState.value = _uiState.value.copy(isMicHardwareVerified = ok)
            }
        }
    }

    /**
     * Handles spoken Bengali text from BengaliSpeechRecognizer
     */
    private fun handleRecognizedBengaliSpeech(recognizedText: String) {
        val clean = recognizedText.trim()
        if (clean.isEmpty()) return

        Log.i(TAG, "Recognized Bengali voice command: $clean")
        _uiState.value = _uiState.value.copy(
            speechSnippet = "🗣️ \"$clean\"",
            errorMessage = null
        )

        bengaliSpeechRecognizer.pauseForAiSpeech()

        val activeKey = getBestAvailableApiKey()
        brainEngine.processPrompt(
            input = clean,
            geminiTextFallback = if (activeKey.isNotBlank()) {
                { prompt ->
                    fallbackRestClient.apply {
                        activeLanguageName = _uiState.value.activeLanguage
                        isLanguageLockedToBengali = _uiState.value.isLanguageLocked
                    }.generateBengaliFromText(activeKey, prompt)
                }
            } else null
        )
    }

    /**
     * Handles VAD-segmented WAV utterances captured by AudioStreamer.
     * Used either when SpeechRecognizer is unavailable on device OR as a safety net
     * if Gemini Live WebSocket does not respond within 2.8 seconds.
     */
    private fun handleCapturedWavUtterance(wavBytes: ByteArray) {
        if (_uiState.value.liveState == LiveState.DISCONNECTED || _uiState.value.liveState == LiveState.SPEAKING) {
            return
        }

        viewModelScope.launch {
            val activeKey = getBestAvailableApiKey()
            if (_uiState.value.activeEngine == ActiveEngine.PRIMARY_GEMINI_LIVE) {
                // Wait 2.8s to see if Gemini Live WebSocket responds with native audio
                delay(2800)
                val lastWsAudio = geminiLiveSession?.lastAudioResponseTimestamp ?: 0L
                val wsRespondedRecently = (System.currentTimeMillis() - lastWsAudio) < 3000L
                if (wsRespondedRecently || _uiState.value.liveState == LiveState.SPEAKING || _uiState.value.liveState == LiveState.DISCONNECTED) {
                    return@launch
                }
            }

            if (activeKey.isNotBlank()) {
                _uiState.value = _uiState.value.copy(
                    liveState = LiveState.THINKING,
                    currentEmotion = AvatarEmotion.THINKING,
                    speechSnippet = "ক্যারোলাইন আপনার কথা প্রসেস করছে..."
                )
                val reply = fallbackRestClient.apply {
                    activeLanguageName = _uiState.value.activeLanguage
                    isLanguageLockedToBengali = _uiState.value.isLanguageLocked
                }.generateBengaliFromWav(activeKey, wavBytes)

                if (!reply.isNullOrBlank()) {
                    brainEngine.speak(reply)
                } else if (_uiState.value.activeEngine != ActiveEngine.PRIMARY_GEMINI_LIVE) {
                    brainEngine.processPrompt("হ্যালো ক্যারোলাইন")
                }
            } else if (_uiState.value.activeEngine != ActiveEngine.PRIMARY_GEMINI_LIVE) {
                brainEngine.processPrompt("হ্যালো ক্যারোলাইন")
            }
        }
    }

    private fun getBestAvailableApiKey(): String {
        val primary = _uiState.value.primaryApiKey
        val backup = _uiState.value.backupApiKey
        return when {
            primary.isNotBlank() && primary != "MY_GEMINI_API_KEY" -> primary
            backup.isNotBlank() -> backup
            else -> ""
        }
    }

    /**
     * Gracefully fails over to Backup AI or Built-in Bengali Voice Engine when Primary WebSocket encounters an error
     */
    private fun failoverToBackup(reason: String) {
        geminiLiveSession?.disconnect()
        geminiLiveSession = null
        audioStreamer.stop()

        val backupKey = _uiState.value.backupApiKey
        val targetEngine = if (_uiState.value.apiMode == ApiMode.AUTO_FAILOVER && backupKey.isNotBlank()) {
            ActiveEngine.BACKUP_SECONDARY_AI
        } else {
            ActiveEngine.BENGALI_VOICE_ENGINE
        }

        Log.w(TAG, "Switching to $targetEngine. Reason: $reason")

        if (bengaliSpeechRecognizer.isAvailable()) {
            _uiState.value = _uiState.value.copy(
                activeEngine = targetEngine,
                liveState = LiveState.LISTENING,
                isMicHardwareVerified = true,
                currentEmotion = AvatarEmotion.LISTENING,
                speechSnippet = "হ্যালো বস! আমি ক্যারোলাইন। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?"
            )
            bengaliSpeechRecognizer.startContinuousListening()
            bengaliSpeechRecognizer.pauseForAiSpeech()
            brainEngine.speak("হ্যালো বস! আমি ক্যারোলাইন। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?")
        } else {
            val micStarted = audioStreamer.start()
            _uiState.value = _uiState.value.copy(
                activeEngine = targetEngine,
                liveState = if (micStarted) LiveState.LISTENING else LiveState.ERROR,
                isMicHardwareVerified = micStarted,
                currentEmotion = if (micStarted) AvatarEmotion.LISTENING else AvatarEmotion.SURPRISED,
                speechSnippet = "হ্যালো বস! আমি ক্যারোলাইন। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?",
                errorMessage = if (!micStarted) "মাইক্রোফোন চালু করা যায়নি।" else null
            )
            if (micStarted) {
                brainEngine.speak("হ্যালো বস! আমি ক্যারোলাইন। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?")
            }
        }
    }

    fun stopSession() {
        bengaliSpeechRecognizer.stopListening()
        audioStreamer.stop()
        audioPlayer.flushAndInterrupt()
        geminiLiveSession?.disconnect()
        geminiLiveSession = null
        brainEngine.stop()
        CaroLineVoiceService.stop(appContext)

        _uiState.value = _uiState.value.copy(
            liveState = LiveState.DISCONNECTED,
            isMicHardwareVerified = false,
            activeEngine = ActiveEngine.BENGALI_VOICE_ENGINE,
            currentEmotion = AvatarEmotion.LISTENING,
            speechSnippet = "ভয়েস মোড বন্ধ আছে। কথা বলতে নিচের ভয়েস বাটনে ট্যাপ করুন।"
        )
    }

    fun toggleMute() {
        audioStreamer.toggleMute()
        val muted = audioStreamer.isMuted.value
        if (muted) {
            bengaliSpeechRecognizer.pauseForAiSpeech()
        } else if (_uiState.value.liveState != LiveState.DISCONNECTED) {
            bengaliSpeechRecognizer.resumeAfterAiSpeech()
        }
        _uiState.value = _uiState.value.copy(isMicMuted = muted)
    }

    fun toggleHandsFree() {
        val newSetting = !_uiState.value.isHandsFreeMode
        sharedPrefs.edit().putBoolean(KEY_HANDS_FREE, newSetting).apply()
        _uiState.value = _uiState.value.copy(isHandsFreeMode = newSetting)
    }

    fun toggleBackgroundService() {
        val newSetting = !_uiState.value.isBackgroundServiceEnabled
        sharedPrefs.edit().putBoolean(KEY_BG_SERVICE_ENABLED, newSetting).apply()
        _uiState.value = _uiState.value.copy(isBackgroundServiceEnabled = newSetting)

        if (!newSetting && _uiState.value.isForegroundServiceRunning) {
            CaroLineVoiceService.stop(appContext)
        } else if (newSetting && _uiState.value.liveState != LiveState.DISCONNECTED) {
            CaroLineVoiceService.startOrUpdate(
                appContext,
                "ক্যারোলাইন সক্রিয় আছে — বাংলা ভয়েস কমান্ড শুনছে"
            )
        }
    }

    /**
     * Owner PIN verification to unlock or change language.
     * Bengali remains the permanent default unless explicitly unlocked with PIN.
     */
    fun changeLanguageWithPin(pinInput: String, newLanguage: String): Boolean {
        if (pinInput.trim() != _uiState.value.languageLockPin) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "ভুল পিন! শুধুমাত্র অনুমোদিত মালিক পিন দিয়ে ভাষা পরিবর্তন করতে পারবেন।"
            )
            return false
        }

        val isBengali = newLanguage.contains("বাংলা") || newLanguage.contains("Bengali", ignoreCase = true)
        sharedPrefs.edit()
            .putString(KEY_ACTIVE_LANGUAGE, newLanguage)
            .putBoolean(KEY_LANGUAGE_LOCKED, isBengali)
            .apply()

        brainEngine.isLanguageLockedToBengali = isBengali
        geminiLiveSession?.activeLanguageName = newLanguage
        geminiLiveSession?.isLanguageLockedToBengali = isBengali

        _uiState.value = _uiState.value.copy(
            activeLanguage = newLanguage,
            isLanguageLocked = isBengali,
            errorMessage = null
        )
        return true
    }

    fun lockLanguageToBengali() {
        val bengali = "বাংলা (Bengali)"
        sharedPrefs.edit()
            .putString(KEY_ACTIVE_LANGUAGE, bengali)
            .putBoolean(KEY_LANGUAGE_LOCKED, true)
            .apply()

        brainEngine.isLanguageLockedToBengali = true
        geminiLiveSession?.activeLanguageName = bengali
        geminiLiveSession?.isLanguageLockedToBengali = true

        _uiState.value = _uiState.value.copy(
            activeLanguage = bengali,
            isLanguageLocked = true,
            errorMessage = null
        )
    }

    fun updateOwnerPin(currentPin: String, newPin: String): Boolean {
        if (currentPin.trim() != _uiState.value.languageLockPin || newPin.trim().length < 4) {
            return false
        }
        val cleanPin = newPin.trim()
        sharedPrefs.edit().putString(KEY_LANGUAGE_PIN, cleanPin).apply()
        brainEngine.ownerLanguagePin = cleanPin
        _uiState.value = _uiState.value.copy(languageLockPin = cleanPin)
        return true
    }

    fun setAvatarStyle(style: AvatarStyle) {
        sharedPrefs.edit().putString(KEY_AVATAR_STYLE, style.name).apply()
        _uiState.value = _uiState.value.copy(avatarStyle = style)
    }

    fun setCustomAvatar(uri: Uri) {
        sharedPrefs.edit().putString(KEY_CUSTOM_URI, uri.toString()).apply()
        _uiState.value = _uiState.value.copy(
            customAvatarUri = uri,
            avatarStyle = AvatarStyle.CUSTOM_UPLOADED
        )
    }

    fun setManualEmotion(emotion: AvatarEmotion?) {
        _uiState.value = _uiState.value.copy(manualEmotionOverride = emotion)
    }

    /**
     * User sends a voice command or taps a quick Bengali command chip
     */
    fun onUserPrompt(promptText: String) {
        if (_uiState.value.liveState == LiveState.DISCONNECTED) {
            startSession()
        }

        if (_uiState.value.liveState == LiveState.SPEAKING) {
            handleUserInterruption()
        }

        // Remove leading emoji for cleaner speech recognition handling
        val cleanCommand = promptText.replace(Regex("^[\\p{So}\\p{Cn}\\s]+"), "").trim()
        _uiState.value = _uiState.value.copy(speechSnippet = "🗣️ \"$cleanCommand\"")

        if (_uiState.value.activeEngine == ActiveEngine.PRIMARY_GEMINI_LIVE && geminiLiveSession != null) {
            geminiLiveSession?.sendTextPrompt(cleanCommand)
        } else {
            bengaliSpeechRecognizer.pauseForAiSpeech()
            val activeKey = getBestAvailableApiKey()
            brainEngine.processPrompt(
                input = cleanCommand,
                geminiTextFallback = if (activeKey.isNotBlank()) {
                    { prompt ->
                        fallbackRestClient.apply {
                            activeLanguageName = _uiState.value.activeLanguage
                            isLanguageLockedToBengali = _uiState.value.isLanguageLocked
                        }.generateBengaliFromText(activeKey, prompt)
                    }
                } else null
            )
        }
    }

    /**
     * Barge-in interruption handler
     */
    fun handleUserInterruption() {
        Log.d(TAG, "Barge-in interruption triggered")
        audioPlayer.flushAndInterrupt()
        brainEngine.stop()
        _uiState.value = _uiState.value.copy(
            liveState = LiveState.LISTENING,
            currentEmotion = AvatarEmotion.LISTENING,
            speechSnippet = "হ্যাঁ বস, আমি শুনছি! বলুন..."
        )
        ensureContinuousListeningActive()
    }

    /**
     * Tapping CaroLine's avatar speaks an affectionate Bengali greeting and smiles
     */
    fun onAvatarTapped() {
        _uiState.value = _uiState.value.copy(
            manualEmotionOverride = AvatarEmotion.SMILING
        )

        val bengaliRemarks = listOf(
            "হ্যালো বস! আমি ক্যারোলাইন। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?",
            "হ্যাঁ বস, আমি একদম প্রস্তুত! আপনার যেকোনো বাংলা ভয়েস কমান্ড বলুন।",
            "বস, আজ আপনাকে দারুণ লাগছে! বলুন, আপনার জন্য কী করতে পারি?",
            "আমি সবসময় আপনার কথা শুনছি বস! সময়, আবহাওয়া বা ফোনের ব্যাটারি—যেকোনো কিছু জিজ্ঞেস করতে পারেন।"
        )
        val remark = bengaliRemarks.random()
        _uiState.value = _uiState.value.copy(speechSnippet = remark)
        bengaliSpeechRecognizer.pauseForAiSpeech()
        brainEngine.speak(remark)

        viewModelScope.launch {
            delay(3200)
            if (_uiState.value.manualEmotionOverride == AvatarEmotion.SMILING) {
                _uiState.value = _uiState.value.copy(manualEmotionOverride = null)
            }
        }
    }

    // --- Dual API Key Management ---

    fun setPrimaryApiKey(newKey: String) {
        val trimmed = newKey.trim()
        sharedPrefs.edit().putString(KEY_PRIMARY_API_KEY, trimmed).apply()
        _uiState.value = _uiState.value.copy(primaryApiKey = trimmed, errorMessage = null)
    }

    fun setBackupApiKey(newKey: String) {
        val trimmed = newKey.trim()
        sharedPrefs.edit().putString(KEY_BACKUP_API_KEY, trimmed).apply()
        _uiState.value = _uiState.value.copy(backupApiKey = trimmed, errorMessage = null)
    }

    fun setApiMode(mode: ApiMode) {
        sharedPrefs.edit().putString(KEY_API_MODE, mode.name).apply()
        _uiState.value = _uiState.value.copy(apiMode = mode)
    }

    fun setSelectedVoice(voiceName: String) {
        sharedPrefs.edit().putString(KEY_VOICE, voiceName).apply()
        _uiState.value = _uiState.value.copy(selectedVoice = voiceName)
        geminiLiveSession?.selectedVoice = voiceName
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun dismissActiveToolToast() {
        _uiState.value = _uiState.value.copy(activeToolEvent = null)
    }

    // --- LiveSessionListener Callbacks ---

    override fun onStateChanged(state: LiveState) {
        if (_uiState.value.activeEngine != ActiveEngine.PRIMARY_GEMINI_LIVE) return

        // Do not mark as LISTENING unless microphone input is actually running
        if (state == LiveState.LISTENING && !audioStreamer.isRecording.value) {
            val started = audioStreamer.start()
            if (!started) {
                _uiState.value = _uiState.value.copy(
                    liveState = LiveState.ERROR,
                    isMicHardwareVerified = false,
                    currentEmotion = AvatarEmotion.SURPRISED,
                    errorMessage = "মাইক্রোফোন অ্যাক্সেস পাওয়া যায়নি।"
                )
                return
            }
        }

        val emotion = when (state) {
            LiveState.SPEAKING -> AvatarEmotion.SPEAKING
            LiveState.LISTENING -> AvatarEmotion.LISTENING
            LiveState.CONNECTING, LiveState.RECONNECTING, LiveState.THINKING -> AvatarEmotion.THINKING
            LiveState.ERROR -> AvatarEmotion.SURPRISED
            LiveState.DISCONNECTED -> AvatarEmotion.LISTENING
        }
        _uiState.value = _uiState.value.copy(
            liveState = state,
            isMicHardwareVerified = audioStreamer.isRecording.value,
            currentEmotion = emotion
        )
        if (state == LiveState.LISTENING) {
            _uiState.value = _uiState.value.copy(
                speechSnippet = "হ্যালো বস! আমি ক্যারোলাইন। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?"
            )
        }
    }

    override fun onAudioReceived(pcm24kData: ByteArray) {
        audioPlayer.enqueueAudio(pcm24kData)
    }

    override fun onInterrupted() {
        Log.d(TAG, "Server signaled interruption")
        audioPlayer.flushAndInterrupt()
        _uiState.value = _uiState.value.copy(
            liveState = LiveState.LISTENING,
            currentEmotion = AvatarEmotion.LISTENING,
            speechSnippet = "হ্যাঁ বস, আমি শুনছি..."
        )
    }

    override fun onSpeechSnippet(text: String) {
        _uiState.value = _uiState.value.copy(speechSnippet = text)
    }

    override fun onError(message: String) {
        Log.e(TAG, "LiveSession error: $message")
        failoverToBackup(message)
    }

    override fun onCleared() {
        super.onCleared()
        bargeInMonitorJob?.cancel()
        bengaliSpeechRecognizer.stopListening()
        audioStreamer.stop()
        audioPlayer.release()
        geminiLiveSession?.disconnect()
        brainEngine.release()
    }
}

// Backward compatibility alias
typealias MahiViewModel = CaroLineViewModel
