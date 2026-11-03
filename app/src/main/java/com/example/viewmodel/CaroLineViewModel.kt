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
import com.example.live.AiProviderType
import com.example.live.CaroLineBrainEngine
import com.example.live.ConversationMessage
import com.example.live.DailyReminderItem
import com.example.live.GeminiLiveSession
import com.example.live.LiveSessionListener
import com.example.live.LiveState
import com.example.live.MultiAiProviderRouter
import com.example.live.ProviderConfig
import com.example.live.ProviderTestResult
import com.example.live.SmartMemoryItem
import com.example.live.SmartRoutineItem
import com.example.service.CaroLineVoiceService
import com.example.tools.AppEntry
import com.example.tools.PendingSensitiveAction
import com.example.tools.ToolExecutionEvent
import com.example.tools.ToolExecutor
import com.example.ui.components.AvatarEmotion
import com.example.ui.components.AvatarStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ApiMode {
    AUTO_FAILOVER,
    PRIMARY_ONLY,
    BACKUP_ONLY
}

enum class ActiveEngine {
    PRIMARY_GEMINI_LIVE,
    BACKUP_SECONDARY_AI,
    BENGALI_VOICE_ENGINE // Represents Local Hindi Voice Engine in 2.9
}

enum class AvatarMode {
    PROCEDURAL_ANIMATED_FACE,
    ANIME_ARTWORK
}

enum class DashboardTab {
    JARVIS_HUD,
    CONVERSATION_HISTORY,
    APP_LAUNCHER_50
}

data class CaroLineUiState(
    val appVersion: String = "2.9 ULTIMATE JARVIS",
    val activeTab: DashboardTab = DashboardTab.JARVIS_HUD,
    val isJarvisCoreMode: Boolean = false, // Defaults to Anime-JARVIS Avatar + AI Ring on Home Screen
    val liveState: LiveState = LiveState.DISCONNECTED,
    val isMicMuted: Boolean = false,
    val isHandsFreeMode: Boolean = true,
    val isMicHardwareVerified: Boolean = false,
    val speechSnippet: String = CaroLineBrainEngine.DEFAULT_GREETING_HI,
    val activeToolEvent: ToolExecutionEvent? = null,
    val toolHistory: List<ToolExecutionEvent> = emptyList(),
    // Three AI Brain System
    val selectedAiProvider: AiProviderType = AiProviderType.GOOGLE_GEMINI,
    val autoFailoverEnabled: Boolean = true,
    val providerConfigs: Map<AiProviderType, ProviderConfig> = mapOf(
        AiProviderType.GOOGLE_GEMINI to ProviderConfig(AiProviderType.GOOGLE_GEMINI),
        AiProviderType.OPENAI_CHATGPT to ProviderConfig(AiProviderType.OPENAI_CHATGPT),
        AiProviderType.OPENROUTER_CUSTOM to ProviderConfig(AiProviderType.OPENROUTER_CUSTOM)
    ),
    val providerTestResults: Map<AiProviderType, ProviderTestResult> = emptyMap(),
    val testingProvider: AiProviderType? = null,
    val activeProviderBadge: String = "GEMINI • JARVIS 2.9",
    // Legacy compatibility fields
    val primaryApiKey: String = "",
    val backupApiKey: String = "",
    val apiMode: ApiMode = ApiMode.AUTO_FAILOVER,
    val activeEngine: ActiveEngine = ActiveEngine.BENGALI_VOICE_ENGINE,
    // Conversation Context & History
    val conversationHistory: List<ConversationMessage> = listOf(
        ConversationMessage(
            role = "assistant",
            text = CaroLineBrainEngine.DEFAULT_GREETING_HI,
            providerBadge = "CAROLINE 2.9"
        )
    ),
    // Top 50 & Installed App Launcher
    val appCatalog: List<AppEntry> = emptyList(),
    val appSearchQuery: String = "",
    // Pending Sensitive Action Confirmation
    val pendingSensitiveAction: PendingSensitiveAction? = null,
    // Device Telemetry
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val isOnline: Boolean = true,
    val networkType: String = "Wi-Fi",
    val availRamMb: Int = 2048,
    // Advanced Female Voice Engine & Hindi Default Lock
    val selectedVoice: String = "Swara (Hindi Female)",
    val voicePitch: Float = 1.08f,
    val voiceSpeed: Float = 1.0f,
    val isVoiceMuted: Boolean = false,
    val useOpenAiTts: Boolean = false,
    val openAiTtsVoice: String = "nova",
    val activeLanguage: String = "हिन्दी (Hindi)",
    val isLanguageLocked: Boolean = true,
    val languageLockPin: String = "1234",
    // Smart Wake Word Settings
    val isWakeWordEnabled: Boolean = true,
    val wakeWordPhrase: String = "Hey CaroLine",
    // Music & Video Preferences
    val preferredMusicService: String = "Auto (Smart Play)",
    val enableBuiltInMusicFallback: Boolean = true,
    val isMusicPlaying: Boolean = false,
    // Top 5 Smart Features State
    val smartMemories: List<SmartMemoryItem> = emptyList(),
    val dailyReminders: List<DailyReminderItem> = emptyList(),
    val smartRoutines: List<SmartRoutineItem> = CaroLineBrainEngine.DEFAULT_SMART_ROUTINES,
    // Theme & Privacy Settings
    val selectedThemeName: String = "Anime-JARVIS Gold & Cyan",
    val requireSensitiveConfirmation: Boolean = true,
    // Background Foreground Service
    val isBackgroundServiceEnabled: Boolean = true,
    val isForegroundServiceRunning: Boolean = false,
    val isScreenInteractive: Boolean = true,
    // Avatar Styles & Emotion Expressions
    val avatarStyle: AvatarStyle = AvatarStyle.GOTHIC_CYBER,
    val customAvatarUri: Uri? = null,
    val currentEmotion: AvatarEmotion = AvatarEmotion.LISTENING,
    val manualEmotionOverride: AvatarEmotion? = null,
    val errorMessage: String? = null
)

typealias MahiUiState = CaroLineUiState

class CaroLineViewModel(application: Application) : AndroidViewModel(application), LiveSessionListener {

    companion object {
        private const val TAG = "CaroLineViewModel"
        private const val PREFS_NAME = "caroline_ai_prefs_v29"
        private const val KEY_PRIMARY_API_KEY = "primary_gemini_api_key"
        private const val KEY_BACKUP_API_KEY = "backup_secondary_api_key"
        private const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
        private const val KEY_GEMINI_ENDPOINT = "gemini_endpoint_url"
        private const val KEY_GEMINI_MODEL = "gemini_model_name"
        private const val KEY_OPENAI_ENDPOINT = "openai_endpoint_url"
        private const val KEY_OPENAI_MODEL = "openai_model_name"
        private const val KEY_OPENROUTER_ENDPOINT = "openrouter_endpoint_url"
        private const val KEY_OPENROUTER_MODEL = "openrouter_model_name"
        private const val KEY_SELECTED_PROVIDER = "selected_ai_provider"
        private const val KEY_AUTO_FAILOVER = "auto_failover_enabled"
        private const val KEY_JARVIS_CORE_MODE = "jarvis_core_mode_v29"
        private const val KEY_API_MODE = "api_failover_mode"
        private const val KEY_VOICE = "female_voice_profile"
        private const val KEY_VOICE_PITCH = "female_voice_pitch"
        private const val KEY_VOICE_SPEED = "female_voice_speed"
        private const val KEY_VOICE_MUTED = "female_voice_muted"
        private const val KEY_USE_OPENAI_TTS = "use_openai_neural_tts"
        private const val KEY_OPENAI_TTS_VOICE = "openai_tts_voice_name"
        private const val KEY_HANDS_FREE = "hands_free_mode"
        private const val KEY_AVATAR_STYLE = "avatar_style_name"
        private const val KEY_CUSTOM_URI = "custom_avatar_uri"
        private const val KEY_LANGUAGE_LOCKED = "hindi_language_locked"
        private const val KEY_LANGUAGE_PIN = "owner_language_pin"
        private const val KEY_ACTIVE_LANGUAGE = "active_language_display_v29"
        private const val KEY_BG_SERVICE_ENABLED = "background_service_enabled"
        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_WAKE_WORD_PHRASE = "wake_word_phrase"
        private const val KEY_PREF_MUSIC_SERVICE = "preferred_music_service"
        private const val KEY_BUILTIN_MUSIC_FALLBACK = "builtin_music_fallback"
        private const val KEY_SELECTED_THEME = "selected_theme_name"
    }

    private val appContext = application.applicationContext
    private val sharedPrefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val toolExecutor = ToolExecutor(appContext)
    private val aiRouter = MultiAiProviderRouter(appContext)

    private var hasSpokenInitialGreetingInSession = false

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

    private val hindiSpeechRecognizer = BengaliSpeechRecognizer(
        context = appContext,
        onPartialTranscript = { partial ->
            if (partial.isNotBlank() && _uiState.value.liveState == LiveState.LISTENING) {
                _uiState.value = _uiState.value.copy(speechSnippet = "🎙️ \"$partial...\"")
            }
        },
        onFinalTranscript = { recognizedText ->
            handleRecognizedHindiSpeech(recognizedText)
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
                    hindiSpeechRecognizer.resumeAfterAiSpeech()
                    ensureContinuousListeningActive()
                }
            }
        },
        onStopVoiceModeRequested = {
            stopSession()
        },
        onSensitiveActionRequested = { pending ->
            _uiState.value = _uiState.value.copy(pendingSensitiveAction = pending)
        },
        onMessageLogged = { msg ->
            appendConversationMessage(msg)
        },
        onMemoriesUpdated = { memories ->
            _uiState.value = _uiState.value.copy(smartMemories = memories)
        },
        onRemindersUpdated = { reminders ->
            _uiState.value = _uiState.value.copy(dailyReminders = reminders)
        }
    )

    private val _uiState = MutableStateFlow(CaroLineUiState())
    val uiState: StateFlow<CaroLineUiState> = _uiState.asStateFlow()

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
        // NOTE: Requirement 1 — When the app starts, do NOT automatically speak three welcome or promotional sentences.
        // We initialize state silently and speak ONLY one short greeting when the user first activates the assistant.
        val buildGeminiKey = readBuildConfigSecret("GEMINI_API_KEY", "MY_GEMINI_API_KEY")
        val buildOpenAiKey = readBuildConfigSecret("OPENAI_API_KEY", "MY_OPENAI_API_KEY")
        val buildOpenRouterKey = readBuildConfigSecret("OPENROUTER_API_KEY", "MY_OPENROUTER_API_KEY")

        val savedGeminiKey = (sharedPrefs.getString(KEY_PRIMARY_API_KEY, "") ?: "").ifBlank { buildGeminiKey }
        val savedOpenAiKey = (sharedPrefs.getString(KEY_BACKUP_API_KEY, "") ?: "").ifBlank { buildOpenAiKey }
        val savedOpenRouterKey = (sharedPrefs.getString(KEY_OPENROUTER_API_KEY, "") ?: "").ifBlank { buildOpenRouterKey }

        val geminiConfig = ProviderConfig(
            providerType = AiProviderType.GOOGLE_GEMINI,
            apiKey = savedGeminiKey,
            endpointUrl = sharedPrefs.getString(KEY_GEMINI_ENDPOINT, AiProviderType.GOOGLE_GEMINI.defaultEndpoint)
                ?: AiProviderType.GOOGLE_GEMINI.defaultEndpoint,
            modelName = sharedPrefs.getString(KEY_GEMINI_MODEL, AiProviderType.GOOGLE_GEMINI.defaultModel)
                ?: AiProviderType.GOOGLE_GEMINI.defaultModel
        )

        val openAiConfig = ProviderConfig(
            providerType = AiProviderType.OPENAI_CHATGPT,
            apiKey = savedOpenAiKey,
            endpointUrl = sharedPrefs.getString(KEY_OPENAI_ENDPOINT, AiProviderType.OPENAI_CHATGPT.defaultEndpoint)
                ?: AiProviderType.OPENAI_CHATGPT.defaultEndpoint,
            modelName = sharedPrefs.getString(KEY_OPENAI_MODEL, AiProviderType.OPENAI_CHATGPT.defaultModel)
                ?: AiProviderType.OPENAI_CHATGPT.defaultModel
        )

        val openRouterConfig = ProviderConfig(
            providerType = AiProviderType.OPENROUTER_CUSTOM,
            apiKey = savedOpenRouterKey,
            endpointUrl = sharedPrefs.getString(KEY_OPENROUTER_ENDPOINT, AiProviderType.OPENROUTER_CUSTOM.defaultEndpoint)
                ?: AiProviderType.OPENROUTER_CUSTOM.defaultEndpoint,
            modelName = sharedPrefs.getString(KEY_OPENROUTER_MODEL, AiProviderType.OPENROUTER_CUSTOM.defaultModel)
                ?: AiProviderType.OPENROUTER_CUSTOM.defaultModel
        )

        val savedProviderStr = sharedPrefs.getString(KEY_SELECTED_PROVIDER, AiProviderType.GOOGLE_GEMINI.name)
            ?: AiProviderType.GOOGLE_GEMINI.name
        val selectedProvider = try {
            AiProviderType.valueOf(savedProviderStr)
        } catch (_: Exception) {
            AiProviderType.GOOGLE_GEMINI
        }

        val savedAutoFailover = sharedPrefs.getBoolean(KEY_AUTO_FAILOVER, true)
        val savedJarvisCoreMode = sharedPrefs.getBoolean(KEY_JARVIS_CORE_MODE, false)

        val savedModeStr = sharedPrefs.getString(KEY_API_MODE, ApiMode.AUTO_FAILOVER.name) ?: ApiMode.AUTO_FAILOVER.name
        val savedMode = try { ApiMode.valueOf(savedModeStr) } catch (_: Exception) { ApiMode.AUTO_FAILOVER }

        val savedVoice = sharedPrefs.getString(KEY_VOICE, "Swara (Hindi Female)") ?: "Swara (Hindi Female)"
        val savedPitch = sharedPrefs.getFloat(KEY_VOICE_PITCH, 1.08f)
        val savedSpeed = sharedPrefs.getFloat(KEY_VOICE_SPEED, 1.0f)
        val savedVoiceMuted = sharedPrefs.getBoolean(KEY_VOICE_MUTED, false)
        val savedUseOpenAiTts = sharedPrefs.getBoolean(KEY_USE_OPENAI_TTS, false)
        val savedOpenAiTtsVoice = sharedPrefs.getString(KEY_OPENAI_TTS_VOICE, "nova") ?: "nova"

        val savedHandsFree = sharedPrefs.getBoolean(KEY_HANDS_FREE, true)

        val savedStyleStr = sharedPrefs.getString(KEY_AVATAR_STYLE, AvatarStyle.GOTHIC_CYBER.name) ?: AvatarStyle.GOTHIC_CYBER.name
        val savedStyle = try { AvatarStyle.valueOf(savedStyleStr) } catch (_: Exception) { AvatarStyle.GOTHIC_CYBER }

        val customUriStr = sharedPrefs.getString(KEY_CUSTOM_URI, null)
        val customUri = if (customUriStr != null) try { Uri.parse(customUriStr) } catch (_: Exception) { null } else null

        val savedLanguageLocked = sharedPrefs.getBoolean(KEY_LANGUAGE_LOCKED, true)
        val savedPin = sharedPrefs.getString(KEY_LANGUAGE_PIN, "1234") ?: "1234"
        val savedLanguage = if (savedLanguageLocked) {
            "हिन्दी (Hindi)"
        } else {
            sharedPrefs.getString(KEY_ACTIVE_LANGUAGE, "हिन्दी (Hindi)") ?: "हिन्दी (Hindi)"
        }
        val savedBgService = sharedPrefs.getBoolean(KEY_BG_SERVICE_ENABLED, true)
        val savedWakeWordEnabled = sharedPrefs.getBoolean(KEY_WAKE_WORD_ENABLED, true)
        val savedWakeWordPhrase = sharedPrefs.getString(KEY_WAKE_WORD_PHRASE, "Hey CaroLine") ?: "Hey CaroLine"
        val savedMusicService = sharedPrefs.getString(KEY_PREF_MUSIC_SERVICE, "Auto (Smart Play)") ?: "Auto (Smart Play)"
        val savedBuiltInMusic = sharedPrefs.getBoolean(KEY_BUILTIN_MUSIC_FALLBACK, true)
        val savedTheme = sharedPrefs.getString(KEY_SELECTED_THEME, "Anime-JARVIS Gold & Cyan") ?: "Anime-JARVIS Gold & Cyan"

        brainEngine.isLanguageLockedToBengali = savedLanguageLocked
        brainEngine.ownerLanguagePin = savedPin
        brainEngine.voicePitch = savedPitch
        brainEngine.voiceSpeed = savedSpeed
        brainEngine.isVoiceMuted = savedVoiceMuted
        brainEngine.selectedFemaleVoiceProfile = savedVoice
        brainEngine.useOpenAiNeuralTts = savedUseOpenAiTts
        brainEngine.openAiTtsVoiceName = savedOpenAiTtsVoice
        brainEngine.openAiSpeechSynthesizer = { text, voice, speed ->
            val openAiKey = _uiState.value.providerConfigs[AiProviderType.OPENAI_CHATGPT]?.apiKey.orEmpty()
            aiRouter.generateOpenAiSpeechMp3(openAiKey, text, voice, speed)
        }

        hindiSpeechRecognizer.isWakeWordEnabled = savedWakeWordEnabled
        hindiSpeechRecognizer.configuredWakeWord = savedWakeWordPhrase

        toolExecutor.preferredMusicService = savedMusicService
        toolExecutor.enableBuiltInMusicFallback = savedBuiltInMusic

        _uiState.value = _uiState.value.copy(
            isJarvisCoreMode = savedJarvisCoreMode,
            selectedAiProvider = selectedProvider,
            autoFailoverEnabled = savedAutoFailover,
            providerConfigs = mapOf(
                AiProviderType.GOOGLE_GEMINI to geminiConfig,
                AiProviderType.OPENAI_CHATGPT to openAiConfig,
                AiProviderType.OPENROUTER_CUSTOM to openRouterConfig
            ),
            activeProviderBadge = "${selectedProvider.shortBadge} • JARVIS 2.9",
            primaryApiKey = savedGeminiKey,
            backupApiKey = savedOpenAiKey,
            apiMode = savedMode,
            selectedVoice = savedVoice,
            voicePitch = savedPitch,
            voiceSpeed = savedSpeed,
            isVoiceMuted = savedVoiceMuted,
            useOpenAiTts = savedUseOpenAiTts,
            openAiTtsVoice = savedOpenAiTtsVoice,
            isHandsFreeMode = savedHandsFree,
            avatarStyle = savedStyle,
            customAvatarUri = customUri,
            activeLanguage = savedLanguage,
            isLanguageLocked = savedLanguageLocked,
            languageLockPin = savedPin,
            isBackgroundServiceEnabled = savedBgService,
            isWakeWordEnabled = savedWakeWordEnabled,
            wakeWordPhrase = savedWakeWordPhrase,
            preferredMusicService = savedMusicService,
            enableBuiltInMusicFallback = savedBuiltInMusic,
            selectedThemeName = savedTheme,
            smartMemories = brainEngine.getSmartMemories(),
            dailyReminders = brainEngine.getDailyReminders()
        )

        refreshDeviceTelemetry()
        refreshAppCatalog()

        // Observe Built-in Music Player state
        viewModelScope.launch {
            toolExecutor.builtInMusicPlayer.isPlaying.collect { playing ->
                _uiState.value = _uiState.value.copy(isMusicPlaying = playing)
            }
        }

        // Listen for tool execution events
        viewModelScope.launch {
            toolExecutor.toolEvents.collect { event ->
                val updatedList = listOf(event) + _uiState.value.toolHistory.take(24)
                _uiState.value = _uiState.value.copy(
                    activeToolEvent = event,
                    toolHistory = updatedList
                )
                refreshDeviceTelemetry()
            }
        }

        // Observe Foreground Voice Service state & actions
        viewModelScope.launch {
            CaroLineVoiceService.isServiceRunning.collect { running ->
                _uiState.value = _uiState.value.copy(isForegroundServiceRunning = running)
            }
        }

        viewModelScope.launch {
            CaroLineVoiceService.isScreenInteractive.collect { interactive ->
                _uiState.value = _uiState.value.copy(isScreenInteractive = interactive)
            }
        }

        viewModelScope.launch {
            CaroLineVoiceService.stopRequestedEvents.collect {
                stopSession()
            }
        }

        viewModelScope.launch {
            CaroLineVoiceService.resumeRequestedEvents.collect {
                if (_uiState.value.liveState != LiveState.DISCONNECTED) {
                    ensureContinuousListeningActive()
                }
            }
        }

        viewModelScope.launch {
            CaroLineVoiceService.networkRestoredEvents.collect {
                refreshDeviceTelemetry()
                if (_uiState.value.liveState == LiveState.ERROR || _uiState.value.liveState == LiveState.RECONNECTING) {
                    Log.i(TAG, "Network restored; reconnecting active voice session")
                    startSession()
                }
            }
        }

        // Barge-in interruption monitor when using AudioRecord
        bargeInMonitorJob = viewModelScope.launch {
            inputAmplitude.collect { amp ->
                if (_uiState.value.liveState == LiveState.SPEAKING && amp > 0.22f) {
                    handleUserInterruption()
                }
            }
        }
    }

    private fun readBuildConfigSecret(fieldName: String, placeholder: String): String {
        return try {
            val field = BuildConfig::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            val value = field.get(null) as? String ?: ""
            if (value != placeholder) value else ""
        } catch (_: Exception) {
            ""
        }
    }

    fun refreshDeviceTelemetry() {
        val info = toolExecutor.getSystemInfo()
        _uiState.value = _uiState.value.copy(
            batteryLevel = (info["battery_level"] as? Int) ?: 100,
            isCharging = (info["is_charging"] as? Boolean) ?: false,
            isOnline = (info["is_online"] as? Boolean) ?: true,
            networkType = (info["network_type"] as? String) ?: "Wi-Fi",
            availRamMb = (info["avail_ram_mb"] as? Int) ?: 2048
        )
    }

    fun refreshAppCatalog() {
        viewModelScope.launch(Dispatchers.IO) {
            val catalog = toolExecutor.appLauncher.getCatalogWithInstalledStatus()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(appCatalog = catalog)
            }
        }
    }

    fun setAppSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(appSearchQuery = query)
    }

    fun launchAppFromCatalog(entry: AppEntry) {
        viewModelScope.launch {
            val res = toolExecutor.execute(
                "openApp",
                mapOf("appName" to entry.displayName, "openPlayStoreIfMissing" to true)
            )
            val msg = res["message_hi"]?.toString()
                ?: "बॉस, ${entry.displayName} खोलने की कोशिश की गई है।"
            _uiState.value = _uiState.value.copy(speechSnippet = msg)
            hindiSpeechRecognizer.pauseForAiSpeech()
            brainEngine.speak(msg, providerBadge = "APP LAUNCHER", toolSummary = entry.displayName)
        }
    }

    fun setActiveTab(tab: DashboardTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
        if (tab == DashboardTab.APP_LAUNCHER_50 && _uiState.value.appCatalog.isEmpty()) {
            refreshAppCatalog()
        }
    }

    fun toggleJarvisCoreMode() {
        val next = !_uiState.value.isJarvisCoreMode
        sharedPrefs.edit().putBoolean(KEY_JARVIS_CORE_MODE, next).apply()
        _uiState.value = _uiState.value.copy(isJarvisCoreMode = next)
    }

    private fun appendConversationMessage(message: ConversationMessage) {
        val current = _uiState.value.conversationHistory
        if (current.lastOrNull()?.text == message.text && current.lastOrNull()?.role == message.role) {
            return
        }
        val updated = (current + message).takeLast(60)
        _uiState.value = _uiState.value.copy(conversationHistory = updated)
    }

    fun clearConversationHistory() {
        _uiState.value = _uiState.value.copy(
            conversationHistory = listOf(
                ConversationMessage(
                    role = "assistant",
                    text = "बॉस, बातचीत का इतिहास साफ कर दिया गया है। बताइए, अब क्या करें?",
                    providerBadge = "CAROLINE 2.9"
                )
            )
        )
    }

    fun confirmSensitiveAction() {
        brainEngine.confirmPendingSensitiveAction()
    }

    fun cancelSensitiveAction() {
        brainEngine.cancelPendingSensitiveAction()
    }

    // --- Smart Features Actions (Memories, Reminders, Routines, Wake Word, Music/Video Prefs) ---

    fun addSmartMemory(fact: String) {
        brainEngine.addSmartMemory(fact)
    }

    fun removeSmartMemory(id: String) {
        brainEngine.removeSmartMemory(id)
    }

    fun clearSmartMemories() {
        brainEngine.clearSmartMemories()
    }

    fun addDailyReminder(title: String, timeText: String = "आज") {
        brainEngine.addDailyReminder(title, timeText)
    }

    fun removeDailyReminder(id: String) {
        brainEngine.removeDailyReminder(id)
    }

    fun clearDailyReminders() {
        brainEngine.clearDailyReminders()
    }

    fun executeSmartRoutine(routineId: String) {
        hindiSpeechRecognizer.pauseForAiSpeech()
        brainEngine.executeSmartRoutine(routineId)
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, enabled).apply()
        hindiSpeechRecognizer.isWakeWordEnabled = enabled
        _uiState.value = _uiState.value.copy(isWakeWordEnabled = enabled)
    }

    fun setWakeWordPhrase(phrase: String) {
        val clean = phrase.trim().ifEmpty { "Hey CaroLine" }
        sharedPrefs.edit().putString(KEY_WAKE_WORD_PHRASE, clean).apply()
        hindiSpeechRecognizer.configuredWakeWord = clean
        _uiState.value = _uiState.value.copy(wakeWordPhrase = clean)
    }

    fun setPreferredMusicService(serviceName: String) {
        sharedPrefs.edit().putString(KEY_PREF_MUSIC_SERVICE, serviceName).apply()
        toolExecutor.preferredMusicService = serviceName
        _uiState.value = _uiState.value.copy(preferredMusicService = serviceName)
    }

    fun setEnableBuiltInMusicFallback(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_BUILTIN_MUSIC_FALLBACK, enabled).apply()
        toolExecutor.enableBuiltInMusicFallback = enabled
        _uiState.value = _uiState.value.copy(enableBuiltInMusicFallback = enabled)
    }

    fun setSelectedThemeName(themeName: String) {
        sharedPrefs.edit().putString(KEY_SELECTED_THEME, themeName).apply()
        _uiState.value = _uiState.value.copy(selectedThemeName = themeName)
    }

    // --- Advanced Female Voice Engine Controls ---

    fun setVoicePitch(pitch: Float) {
        val bounded = pitch.coerceIn(0.7f, 1.4f)
        sharedPrefs.edit().putFloat(KEY_VOICE_PITCH, bounded).apply()
        brainEngine.voicePitch = bounded
        _uiState.value = _uiState.value.copy(voicePitch = bounded)
    }

    fun setVoiceSpeed(speed: Float) {
        val bounded = speed.coerceIn(0.7f, 1.4f)
        sharedPrefs.edit().putFloat(KEY_VOICE_SPEED, bounded).apply()
        brainEngine.voiceSpeed = bounded
        _uiState.value = _uiState.value.copy(voiceSpeed = bounded)
    }

    fun toggleVoiceMute() {
        val next = !_uiState.value.isVoiceMuted
        sharedPrefs.edit().putBoolean(KEY_VOICE_MUTED, next).apply()
        brainEngine.isVoiceMuted = next
        if (next) {
            brainEngine.stop()
        }
        _uiState.value = _uiState.value.copy(isVoiceMuted = next)
    }

    fun setUseOpenAiTts(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_USE_OPENAI_TTS, enabled).apply()
        brainEngine.useOpenAiNeuralTts = enabled
        _uiState.value = _uiState.value.copy(useOpenAiTts = enabled)
    }

    fun setOpenAiTtsVoice(voiceName: String) {
        sharedPrefs.edit().putString(KEY_OPENAI_TTS_VOICE, voiceName).apply()
        brainEngine.openAiTtsVoiceName = voiceName
        _uiState.value = _uiState.value.copy(openAiTtsVoice = voiceName)
    }

    fun previewFemaleVoice() {
        hindiSpeechRecognizer.pauseForAiSpeech()
        brainEngine.previewFemaleVoice()
    }

    // --- Three AI Brain System Management ---

    fun selectAiProvider(provider: AiProviderType) {
        sharedPrefs.edit().putString(KEY_SELECTED_PROVIDER, provider.name).apply()
        _uiState.value = _uiState.value.copy(
            selectedAiProvider = provider,
            activeProviderBadge = "${provider.shortBadge} • JARVIS 2.9"
        )
    }

    fun toggleAutoFailover() {
        val next = !_uiState.value.autoFailoverEnabled
        sharedPrefs.edit().putBoolean(KEY_AUTO_FAILOVER, next).apply()
        _uiState.value = _uiState.value.copy(autoFailoverEnabled = next)
    }

    fun updateProviderConfig(
        providerType: AiProviderType,
        apiKey: String,
        endpointUrl: String,
        modelName: String
    ) {
        val cleanKey = apiKey.trim()
        val cleanEndpoint = endpointUrl.trim().ifBlank { providerType.defaultEndpoint }
        val cleanModel = modelName.trim().ifBlank { providerType.defaultModel }

        val editor = sharedPrefs.edit()
        when (providerType) {
            AiProviderType.GOOGLE_GEMINI -> {
                editor.putString(KEY_PRIMARY_API_KEY, cleanKey)
                editor.putString(KEY_GEMINI_ENDPOINT, cleanEndpoint)
                editor.putString(KEY_GEMINI_MODEL, cleanModel)
            }
            AiProviderType.OPENAI_CHATGPT -> {
                editor.putString(KEY_BACKUP_API_KEY, cleanKey)
                editor.putString(KEY_OPENAI_ENDPOINT, cleanEndpoint)
                editor.putString(KEY_OPENAI_MODEL, cleanModel)
            }
            AiProviderType.OPENROUTER_CUSTOM -> {
                editor.putString(KEY_OPENROUTER_API_KEY, cleanKey)
                editor.putString(KEY_OPENROUTER_ENDPOINT, cleanEndpoint)
                editor.putString(KEY_OPENROUTER_MODEL, cleanModel)
            }
        }
        editor.apply()

        val updatedConfig = ProviderConfig(
            providerType = providerType,
            apiKey = cleanKey,
            endpointUrl = cleanEndpoint,
            modelName = cleanModel
        )
        val updatedMap = _uiState.value.providerConfigs.toMutableMap().apply {
            put(providerType, updatedConfig)
        }

        _uiState.value = _uiState.value.copy(
            providerConfigs = updatedMap,
            primaryApiKey = updatedMap[AiProviderType.GOOGLE_GEMINI]?.apiKey.orEmpty(),
            backupApiKey = updatedMap[AiProviderType.OPENAI_CHATGPT]?.apiKey.orEmpty(),
            errorMessage = null
        )
    }

    fun removeProviderCredential(providerType: AiProviderType) {
        updateProviderConfig(
            providerType = providerType,
            apiKey = "",
            endpointUrl = providerType.defaultEndpoint,
            modelName = providerType.defaultModel
        )
        val updatedResults = _uiState.value.providerTestResults.toMutableMap().apply {
            remove(providerType)
        }
        _uiState.value = _uiState.value.copy(providerTestResults = updatedResults)
    }

    fun testProviderConnection(providerType: AiProviderType) {
        val config = _uiState.value.providerConfigs[providerType] ?: ProviderConfig(providerType)
        _uiState.value = _uiState.value.copy(testingProvider = providerType)

        viewModelScope.launch {
            val result = aiRouter.testProviderConnection(config)
            val updatedResults = _uiState.value.providerTestResults.toMutableMap().apply {
                put(providerType, result)
            }
            _uiState.value = _uiState.value.copy(
                testingProvider = null,
                providerTestResults = updatedResults
            )
        }
    }

    /**
     * Main Voice Mode / Microphone Button handler.
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
        refreshDeviceTelemetry()

        val geminiConfig = _uiState.value.providerConfigs[AiProviderType.GOOGLE_GEMINI]
        val canUseGeminiLive = _uiState.value.selectedAiProvider == AiProviderType.GOOGLE_GEMINI &&
            geminiConfig?.hasValidKey() == true &&
            _uiState.value.apiMode != ApiMode.BACKUP_ONLY

        if (_uiState.value.isBackgroundServiceEnabled) {
            CaroLineVoiceService.startOrUpdate(
                appContext,
                "कैरोलाइन 2.9 सक्रिय — हिंदी वॉइस कमांड सुन रही है"
            )
        }

        if (canUseGeminiLive && !hindiSpeechRecognizer.isAvailable()) {
            val micStarted = audioStreamer.start()
            if (!micStarted) {
                _uiState.value = _uiState.value.copy(
                    liveState = LiveState.ERROR,
                    isMicHardwareVerified = false,
                    currentEmotion = AvatarEmotion.SURPRISED,
                    errorMessage = "माइक्रोफ़ोन शुरू नहीं हो सका। कृपया माइक्रोफ़ोन अनुमति जांचें।"
                )
                return
            }

            _uiState.value = _uiState.value.copy(
                activeEngine = ActiveEngine.PRIMARY_GEMINI_LIVE,
                liveState = LiveState.CONNECTING,
                isMicHardwareVerified = true,
                currentEmotion = AvatarEmotion.THINKING,
                speechSnippet = "कैरोलाइन लाइव सेशन से जुड़ रही है..."
            )

            geminiLiveSession?.disconnect()
            geminiLiveSession = GeminiLiveSession(toolExecutor, this).apply {
                selectedVoice = _uiState.value.selectedVoice
                activeLanguageName = _uiState.value.activeLanguage
                isLanguageLockedToBengali = _uiState.value.isLanguageLocked
                connect(geminiConfig?.apiKey.orEmpty())
            }
        } else {
            val engine = if (_uiState.value.providerConfigs.values.any { it.hasValidKey() }) {
                ActiveEngine.BACKUP_SECONDARY_AI
            } else {
                ActiveEngine.BENGALI_VOICE_ENGINE
            }

            if (hindiSpeechRecognizer.isAvailable()) {
                audioStreamer.stop()
                _uiState.value = _uiState.value.copy(
                    activeEngine = engine,
                    liveState = LiveState.LISTENING,
                    isMicHardwareVerified = true,
                    currentEmotion = AvatarEmotion.LISTENING,
                    speechSnippet = CaroLineBrainEngine.DEFAULT_GREETING_HI
                )
                hindiSpeechRecognizer.startContinuousListening()
                if (!hasSpokenInitialGreetingInSession) {
                    hasSpokenInitialGreetingInSession = true
                    hindiSpeechRecognizer.pauseForAiSpeech()
                    brainEngine.speak(CaroLineBrainEngine.DEFAULT_GREETING_HI, providerBadge = "CAROLINE 2.9")
                }
            } else {
                val micStarted = audioStreamer.start()
                if (!micStarted) {
                    _uiState.value = _uiState.value.copy(
                        liveState = LiveState.ERROR,
                        isMicHardwareVerified = false,
                        currentEmotion = AvatarEmotion.SURPRISED,
                        errorMessage = "माइक्रोफ़ोन एक्सेस विफल रहा। कृपया अनुमति जांचें।"
                    )
                    return
                }
                _uiState.value = _uiState.value.copy(
                    activeEngine = engine,
                    liveState = LiveState.LISTENING,
                    isMicHardwareVerified = true,
                    currentEmotion = AvatarEmotion.LISTENING,
                    speechSnippet = CaroLineBrainEngine.DEFAULT_GREETING_HI
                )
                if (!hasSpokenInitialGreetingInSession) {
                    hasSpokenInitialGreetingInSession = true
                    brainEngine.speak(CaroLineBrainEngine.DEFAULT_GREETING_HI, providerBadge = "CAROLINE 2.9")
                }
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
            if (hindiSpeechRecognizer.isAvailable()) {
                hindiSpeechRecognizer.resumeAfterAiSpeech()
            } else if (!audioStreamer.isRecording.value) {
                val ok = audioStreamer.start()
                _uiState.value = _uiState.value.copy(isMicHardwareVerified = ok)
            }
        }
    }

    private fun handleRecognizedHindiSpeech(recognizedText: String) {
        val clean = recognizedText.trim()
        if (clean.isEmpty()) return

        Log.i(TAG, "Recognized Hindi/Hinglish voice command: $clean")
        _uiState.value = _uiState.value.copy(
            speechSnippet = "🗣️ \"$clean\"",
            errorMessage = null
        )

        hindiSpeechRecognizer.pauseForAiSpeech()

        brainEngine.processPrompt(
            input = clean,
            aiRouterFallback = { query ->
                val resp = aiRouter.generateResponse(
                    userPrompt = query,
                    history = _uiState.value.conversationHistory,
                    configs = _uiState.value.providerConfigs,
                    preferredProvider = _uiState.value.selectedAiProvider,
                    autoFailover = _uiState.value.autoFailoverEnabled,
                    isLanguageLockedToBengali = _uiState.value.isLanguageLocked,
                    activeLanguageName = _uiState.value.activeLanguage,
                    smartMemories = _uiState.value.smartMemories.map { it.fact }
                )
                if (resp.usedProvider != null) {
                    _uiState.value = _uiState.value.copy(
                        activeProviderBadge = "${resp.usedProvider.shortBadge} • ACTIVE"
                    )
                }
                resp
            }
        )
    }

    private fun handleCapturedWavUtterance(wavBytes: ByteArray) {
        if (_uiState.value.liveState == LiveState.DISCONNECTED || _uiState.value.liveState == LiveState.SPEAKING) {
            return
        }

        viewModelScope.launch {
            val geminiKey = _uiState.value.providerConfigs[AiProviderType.GOOGLE_GEMINI]?.apiKey.orEmpty()
            if (_uiState.value.activeEngine == ActiveEngine.PRIMARY_GEMINI_LIVE) {
                delay(2800)
                val lastWsAudio = geminiLiveSession?.lastAudioResponseTimestamp ?: 0L
                val wsRespondedRecently = (System.currentTimeMillis() - lastWsAudio) < 3000L
                if (wsRespondedRecently || _uiState.value.liveState == LiveState.SPEAKING || _uiState.value.liveState == LiveState.DISCONNECTED) {
                    return@launch
                }
            }

            if (geminiKey.isNotBlank() && geminiKey != "MY_GEMINI_API_KEY") {
                _uiState.value = _uiState.value.copy(
                    liveState = LiveState.THINKING,
                    currentEmotion = AvatarEmotion.THINKING,
                    speechSnippet = "कैरोलाइन आपकी बात समझ रही है..."
                )
                val reply = fallbackRestClient.apply {
                    activeLanguageName = _uiState.value.activeLanguage
                    isLanguageLockedToBengali = _uiState.value.isLanguageLocked
                }.generateBengaliFromWav(geminiKey, wavBytes)

                if (!reply.isNullOrBlank()) {
                    brainEngine.speak(reply, providerBadge = "GEMINI AUDIO")
                } else if (_uiState.value.activeEngine != ActiveEngine.PRIMARY_GEMINI_LIVE) {
                    brainEngine.processPrompt("नमस्ते कैरोलाइन")
                }
            } else if (_uiState.value.activeEngine != ActiveEngine.PRIMARY_GEMINI_LIVE) {
                brainEngine.processPrompt("नमस्ते कैरोलाइन")
            }
        }
    }

    private fun failoverToBackup(reason: String) {
        geminiLiveSession?.disconnect()
        geminiLiveSession = null
        audioStreamer.stop()

        val targetEngine = ActiveEngine.BENGALI_VOICE_ENGINE
        Log.w(TAG, "Switching to $targetEngine. Reason: $reason")

        if (hindiSpeechRecognizer.isAvailable()) {
            _uiState.value = _uiState.value.copy(
                activeEngine = targetEngine,
                liveState = LiveState.LISTENING,
                isMicHardwareVerified = true,
                currentEmotion = AvatarEmotion.LISTENING,
                speechSnippet = CaroLineBrainEngine.DEFAULT_GREETING_HI
            )
            hindiSpeechRecognizer.startContinuousListening()
        } else {
            val micStarted = audioStreamer.start()
            _uiState.value = _uiState.value.copy(
                activeEngine = targetEngine,
                liveState = if (micStarted) LiveState.LISTENING else LiveState.ERROR,
                isMicHardwareVerified = micStarted,
                currentEmotion = if (micStarted) AvatarEmotion.LISTENING else AvatarEmotion.SURPRISED,
                speechSnippet = CaroLineBrainEngine.DEFAULT_GREETING_HI,
                errorMessage = if (!micStarted) "माइक्रोफ़ोन शुरू नहीं हो सका।" else null
            )
        }
    }

    fun stopSession() {
        hindiSpeechRecognizer.stopListening()
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
            speechSnippet = "वॉइस मोड बंद है। बात करने के लिए नीचे माइक बटन दबाएं।"
        )
    }

    fun toggleMute() {
        audioStreamer.toggleMute()
        val muted = audioStreamer.isMuted.value
        if (muted) {
            hindiSpeechRecognizer.pauseForAiSpeech()
        } else if (_uiState.value.liveState != LiveState.DISCONNECTED) {
            hindiSpeechRecognizer.resumeAfterAiSpeech()
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
                "कैरोलाइन 2.9 सक्रिय — हिंदी वॉइस कमांड सुन रही है"
            )
        }
    }

    fun changeLanguageWithPin(pinInput: String, newLanguage: String): Boolean {
        if (pinInput.trim() != _uiState.value.languageLockPin) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "गलत पिन! केवल अधिकृत मालिक ही पिन से भाषा बदल सकते हैं।"
            )
            return false
        }

        val isHindi = newLanguage.contains("हिन्दी") || newLanguage.contains("Hindi", ignoreCase = true)
        sharedPrefs.edit()
            .putString(KEY_ACTIVE_LANGUAGE, newLanguage)
            .putBoolean(KEY_LANGUAGE_LOCKED, isHindi)
            .apply()

        brainEngine.isLanguageLockedToBengali = isHindi
        geminiLiveSession?.activeLanguageName = newLanguage
        geminiLiveSession?.isLanguageLockedToBengali = isHindi

        _uiState.value = _uiState.value.copy(
            activeLanguage = newLanguage,
            isLanguageLocked = isHindi,
            errorMessage = null
        )
        return true
    }

    fun lockLanguageToHindi() {
        val hindi = "हिन्दी (Hindi)"
        sharedPrefs.edit()
            .putString(KEY_ACTIVE_LANGUAGE, hindi)
            .putBoolean(KEY_LANGUAGE_LOCKED, true)
            .apply()

        brainEngine.isLanguageLockedToBengali = true
        geminiLiveSession?.activeLanguageName = hindi
        geminiLiveSession?.isLanguageLockedToBengali = true

        _uiState.value = _uiState.value.copy(
            activeLanguage = hindi,
            isLanguageLocked = true,
            errorMessage = null
        )
    }

    fun lockLanguageToBengali() = lockLanguageToHindi()

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
     * User sends a voice command or text query.
     */
    fun onUserPrompt(promptText: String) {
        if (_uiState.value.liveState == LiveState.SPEAKING) {
            handleUserInterruption()
        }

        val cleanCommand = promptText.replace(Regex("^[\\p{So}\\p{Cn}\\s]+"), "").trim()
        if (cleanCommand.isEmpty()) return

        _uiState.value = _uiState.value.copy(speechSnippet = "🗣️ \"$cleanCommand\"")

        if (_uiState.value.activeEngine == ActiveEngine.PRIMARY_GEMINI_LIVE && geminiLiveSession != null) {
            appendConversationMessage(ConversationMessage(role = "user", text = cleanCommand, providerBadge = "USER"))
            geminiLiveSession?.sendTextPrompt(cleanCommand)
        } else {
            hindiSpeechRecognizer.pauseForAiSpeech()
            brainEngine.processPrompt(
                input = cleanCommand,
                aiRouterFallback = { query ->
                    val resp = aiRouter.generateResponse(
                        userPrompt = query,
                        history = _uiState.value.conversationHistory,
                        configs = _uiState.value.providerConfigs,
                        preferredProvider = _uiState.value.selectedAiProvider,
                        autoFailover = _uiState.value.autoFailoverEnabled,
                        isLanguageLockedToBengali = _uiState.value.isLanguageLocked,
                        activeLanguageName = _uiState.value.activeLanguage,
                        smartMemories = _uiState.value.smartMemories.map { it.fact }
                    )
                    if (resp.usedProvider != null) {
                        _uiState.value = _uiState.value.copy(
                            activeProviderBadge = "${resp.usedProvider.shortBadge} • ACTIVE"
                        )
                    }
                    resp
                }
            )
        }
    }

    /**
     * Immediate barge-in interruption stop (Requirement 2).
     */
    fun handleUserInterruption() {
        Log.d(TAG, "Barge-in interruption triggered — stopping speech immediately")
        audioPlayer.flushAndInterrupt()
        brainEngine.stop()
        _uiState.value = _uiState.value.copy(
            liveState = LiveState.LISTENING,
            currentEmotion = AvatarEmotion.LISTENING,
            speechSnippet = "जी बॉस, मैं सुन रही हूँ! बताइए..."
        )
        ensureContinuousListeningActive()
    }

    fun onAvatarTapped() {
        _uiState.value = _uiState.value.copy(
            manualEmotionOverride = AvatarEmotion.SMILING
        )

        // Never repeat battery, network, or engine status here (Requirement 1)
        val remark = CaroLineBrainEngine.DEFAULT_GREETING_HI
        _uiState.value = _uiState.value.copy(speechSnippet = remark)
        hindiSpeechRecognizer.pauseForAiSpeech()
        brainEngine.speak(remark, providerBadge = "CAROLINE 2.9")

        viewModelScope.launch {
            delay(2800)
            if (_uiState.value.manualEmotionOverride == AvatarEmotion.SMILING) {
                _uiState.value = _uiState.value.copy(manualEmotionOverride = null)
            }
        }
    }

    // --- Legacy Dual API Key Methods (delegated to 3-Provider Config) ---

    fun setPrimaryApiKey(newKey: String) {
        val current = _uiState.value.providerConfigs[AiProviderType.GOOGLE_GEMINI]
            ?: ProviderConfig(AiProviderType.GOOGLE_GEMINI)
        updateProviderConfig(AiProviderType.GOOGLE_GEMINI, newKey, current.endpointUrl, current.modelName)
    }

    fun setBackupApiKey(newKey: String) {
        val current = _uiState.value.providerConfigs[AiProviderType.OPENAI_CHATGPT]
            ?: ProviderConfig(AiProviderType.OPENAI_CHATGPT)
        updateProviderConfig(AiProviderType.OPENAI_CHATGPT, newKey, current.endpointUrl, current.modelName)
    }

    fun setApiMode(mode: ApiMode) {
        sharedPrefs.edit().putString(KEY_API_MODE, mode.name).apply()
        _uiState.value = _uiState.value.copy(
            apiMode = mode,
            autoFailoverEnabled = mode == ApiMode.AUTO_FAILOVER
        )
    }

    fun setSelectedVoice(voiceName: String) {
        sharedPrefs.edit().putString(KEY_VOICE, voiceName).apply()
        brainEngine.selectedFemaleVoiceProfile = voiceName
        when {
            voiceName.contains("Ananya", ignoreCase = true) -> setVoicePitch(1.14f)
            voiceName.contains("Kavya", ignoreCase = true) -> setVoicePitch(1.04f)
            voiceName.contains("Swara", ignoreCase = true) -> setVoicePitch(1.08f)
        }
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

        if (state == LiveState.LISTENING && !audioStreamer.isRecording.value) {
            val started = audioStreamer.start()
            if (!started) {
                _uiState.value = _uiState.value.copy(
                    liveState = LiveState.ERROR,
                    isMicHardwareVerified = false,
                    currentEmotion = AvatarEmotion.SURPRISED,
                    errorMessage = "माइक्रोफ़ोन एक्सेस नहीं मिला।"
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
        if (state == LiveState.LISTENING && !hasSpokenInitialGreetingInSession) {
            hasSpokenInitialGreetingInSession = true
            _uiState.value = _uiState.value.copy(
                speechSnippet = CaroLineBrainEngine.DEFAULT_GREETING_HI
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
            speechSnippet = "जी बॉस, मैं सुन रही हूँ..."
        )
    }

    override fun onSpeechSnippet(text: String) {
        _uiState.value = _uiState.value.copy(speechSnippet = text)
        appendConversationMessage(
            ConversationMessage(role = "assistant", text = text, providerBadge = "GEMINI LIVE")
        )
    }

    override fun onError(message: String) {
        Log.e(TAG, "LiveSession error: $message")
        failoverToBackup(message)
    }

    override fun onCleared() {
        super.onCleared()
        bargeInMonitorJob?.cancel()
        hindiSpeechRecognizer.stopListening()
        audioStreamer.stop()
        audioPlayer.release()
        toolExecutor.builtInMusicPlayer.stop()
        geminiLiveSession?.disconnect()
        brainEngine.release()
    }
}

typealias MahiViewModel = CaroLineViewModel
