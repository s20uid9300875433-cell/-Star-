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
import com.example.audio.ListeningPowerMode
import com.example.live.AdaptiveAiManager
import com.example.live.AdaptiveAiSnapshot
import com.example.live.AiProviderType
import com.example.live.CaroLineBrainEngine
import com.example.live.ConversationMessage
import com.example.live.DailyReminderItem
import com.example.live.DeviceVoiceInfo
import com.example.live.GeminiLiveSession
import com.example.live.LiveSessionListener
import com.example.live.LiveState
import com.example.live.MultiAiProviderRouter
import com.example.live.ProviderConfig
import com.example.live.ProviderTestResult
import com.example.live.SmartMemoryItem
import com.example.live.SmartRoutineItem
import com.example.live.TtsEngineInfo
import com.example.service.CaroLineVoiceService
import com.example.tools.AppEntry
import com.example.tools.DeviceContactItem
import com.example.tools.LiveWeatherReport
import com.example.tools.MessageDraftPreview
import com.example.tools.NetworkAndDataReport
import com.example.tools.PendingSensitiveAction
import com.example.tools.PerformanceProfile
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
    BENGALI_VOICE_ENGINE
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
    val appVersion: String = "A3.2 ULTIMATE JARVIS",
    val activeTab: DashboardTab = DashboardTab.JARVIS_HUD,
    val isJarvisCoreMode: Boolean = false,
    val liveState: LiveState = LiveState.DISCONNECTED,
    val isMicMuted: Boolean = false,
    val isHandsFreeMode: Boolean = true,
    val isMicHardwareVerified: Boolean = false,
    val speechSnippet: String = CaroLineBrainEngine.DEFAULT_GREETING_HI,
    val activeToolEvent: ToolExecutionEvent? = null,
    val toolHistory: List<ToolExecutionEvent> = emptyList(),
    // Requirement A: Performance Profiles (2GB Lite, 4GB Standard, 6GB+ Advanced)
    val performanceProfile: PerformanceProfile = PerformanceProfile.STANDARD_MODE,
    val isAutoPerformanceProfile: Boolean = true,
    val totalRamMb: Int = 4096,
    val availRamMb: Int = 2048,
    // Requirement D: Managed AI Brain (No manual API key required)
    val selectedAiProvider: AiProviderType = AiProviderType.GOOGLE_GEMINI,
    val autoFailoverEnabled: Boolean = true,
    val isManagedCloudConfigured: Boolean = true,
    val providerConfigs: Map<AiProviderType, ProviderConfig> = mapOf(
        AiProviderType.GOOGLE_GEMINI to ProviderConfig(AiProviderType.GOOGLE_GEMINI),
        AiProviderType.OPENAI_CHATGPT to ProviderConfig(AiProviderType.OPENAI_CHATGPT),
        AiProviderType.OPENROUTER_CUSTOM to ProviderConfig(AiProviderType.OPENROUTER_CUSTOM)
    ),
    val providerTestResults: Map<AiProviderType, ProviderTestResult> = emptyMap(),
    val testingProvider: AiProviderType? = null,
    val activeProviderBadge: String = "MANAGED AI • A3.2",
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
            providerBadge = "CAROLINE A3.2"
        )
    ),
    // Requirement I: Top 50 & Dynamic Installed App Launcher
    val appCatalog: List<AppEntry> = emptyList(),
    val appSearchQuery: String = "",
    // Requirement E: Smart Contact & Phone Call Engine state
    val matchedContacts: List<DeviceContactItem> = emptyList(),
    val contactSearchTitle: String = "",
    val useDirectCallWhenPermitted: Boolean = false,
    // Requirement G: Smart Message Writer state
    val pendingMessageDraft: MessageDraftPreview? = null,
    // Pending Sensitive Action Confirmation
    val pendingSensitiveAction: PendingSensitiveAction? = null,
    // Requirement K: Live Weather & Network Data Usage state
    val latestWeatherReport: LiveWeatherReport? = null,
    val networkAndDataReport: NetworkAndDataReport? = null,
    // Requirement F: Floating CaroLine Window state
    val isFloatingBubbleEnabled: Boolean = true,
    val isFloatingWindowExpanded: Boolean = false,
    val canDrawSystemOverlays: Boolean = false,
    // Requirement L: Adaptive AI Engine (20–30 min periodic refresh)
    val adaptiveAiSnapshot: AdaptiveAiSnapshot = AdaptiveAiSnapshot(),
    // Device Telemetry
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val isOnline: Boolean = true,
    val networkType: String = "Wi-Fi",
    // Requirement B & C: Spoken Language (Hindi default, Bengali, English) & Real Female Voice Studio
    val spokenLanguageCode: String = "hi-IN",
    val activeLanguage: String = "Hindi (हिन्दी)",
    val isLanguageLocked: Boolean = true,
    val languageLockPin: String = "1234",
    val installedTtsEngines: List<TtsEngineInfo> = emptyList(),
    val availableDeviceVoices: List<DeviceVoiceInfo> = emptyList(),
    val selectedTtsEnginePackage: String = "",
    val selectedVoiceId: String = "system_locale_hi-IN",
    val selectedVoice: String = "System Default Hindi (hi-IN)",
    val voicePitch: Float = 1.08f,
    val voiceSpeed: Float = 1.0f,
    val isVoiceMuted: Boolean = false,
    val useOpenAiTts: Boolean = false,
    val openAiTtsVoice: String = "nova",
    // Smart Wake Word & Battery-Saving Background Voice Assistant Settings
    val isWakeWordEnabled: Boolean = true,
    val wakeWordPhrase: String = "Hey CaroLine",
    val listeningPowerMode: ListeningPowerMode = ListeningPowerMode.BATTERY_SAVING,
    val isBatterySavingListeningMode: Boolean = true,
    val isScreenOffVoiceEnabled: Boolean = true,
    val isScreenOffConditionSupported: Boolean = true,
    val isInEcoStandby: Boolean = false,
    val isWakeWordActiveWindow: Boolean = false,
    val listeningCycleStatusText: String = "Battery-Saving Mode • Wake Word: \"Hey CaroLine\"",
    // Music & Video Preferences
    val preferredMusicService: String = "Auto (Smart Play)",
    val enableBuiltInMusicFallback: Boolean = true,
    val isMusicPlaying: Boolean = false,
    // Smart Memory, Reminders & Routines
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
        private const val KEY_VOICE_ID = "female_device_voice_id_a32"
        private const val KEY_TTS_ENGINE_PKG = "tts_engine_package_a32"
        private const val KEY_VOICE_PITCH = "female_voice_pitch"
        private const val KEY_VOICE_SPEED = "female_voice_speed"
        private const val KEY_VOICE_MUTED = "female_voice_muted"
        private const val KEY_USE_OPENAI_TTS = "use_openai_neural_tts"
        private const val KEY_OPENAI_TTS_VOICE = "openai_tts_voice_name"
        private const val KEY_HANDS_FREE = "hands_free_mode"
        private const val KEY_AVATAR_STYLE = "avatar_style_name"
        private const val KEY_CUSTOM_URI = "custom_avatar_uri"
        private const val KEY_SPOKEN_LANG_CODE = "spoken_language_code_a32"
        private const val KEY_LANGUAGE_LOCKED = "hindi_language_locked"
        private const val KEY_LANGUAGE_PIN = "owner_language_pin"
        private const val KEY_ACTIVE_LANGUAGE = "active_language_display_v29"
        private const val KEY_BG_SERVICE_ENABLED = "background_service_enabled"
        private const val KEY_FLOATING_BUBBLE_ENABLED = "floating_bubble_enabled_a32"
        private const val KEY_PERF_PROFILE_ID = "performance_profile_id_a32"
        private const val KEY_AUTO_PERF_PROFILE = "auto_performance_profile_a32"
        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_WAKE_WORD_PHRASE = "wake_word_phrase"
        private const val KEY_LISTENING_POWER_MODE = "listening_power_mode_a32"
        private const val KEY_SCREEN_OFF_VOICE_ENABLED = "screen_off_voice_enabled_a32"
        private const val KEY_PREF_MUSIC_SERVICE = "preferred_music_service"
        private const val KEY_BUILTIN_MUSIC_FALLBACK = "builtin_music_fallback"
        private const val KEY_SELECTED_THEME = "selected_theme_name"
    }

    private val appContext = application.applicationContext
    private val sharedPrefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val toolExecutor = ToolExecutor(appContext)
    private val aiRouter = MultiAiProviderRouter(appContext)
    private val adaptiveAiManager = AdaptiveAiManager(appContext)

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

    private var wakeWordWindowJob: Job? = null

    private val hindiSpeechRecognizer = BengaliSpeechRecognizer(
        context = appContext,
        onPartialTranscript = { partial ->
            if (partial.isNotBlank() && _uiState.value.liveState == LiveState.LISTENING) {
                _uiState.value = _uiState.value.copy(speechSnippet = "🎙️ \"$partial...\"")
            }
        },
        onFinalTranscript = { recognizedText ->
            handleRecognizedSpeech(recognizedText)
        },
        onRmsAmplitude = { rmsNormalized, voiceDetected ->
            audioStreamer.updateExternalAmplitude(rmsNormalized, voiceDetected)
        },
        onListeningStateChanged = { isListening ->
            if (isListening && _uiState.value.liveState != LiveState.SPEAKING && _uiState.value.liveState != LiveState.DISCONNECTED) {
                _uiState.value = _uiState.value.copy(
                    liveState = LiveState.LISTENING,
                    isMicHardwareVerified = true,
                    isInEcoStandby = false,
                    currentEmotion = AvatarEmotion.LISTENING,
                    errorMessage = null
                )
            }
        },
        onErrorDiagnostic = { notice ->
            _uiState.value = _uiState.value.copy(errorMessage = notice)
        },
        onWakeWordDetected = { _ ->
            activateWakeWordWindow()
        },
        onEcoStandbyEntered = { reason ->
            _uiState.value = _uiState.value.copy(
                isInEcoStandby = true,
                listeningCycleStatusText = "Eco Standby • Wake Word: \"${_uiState.value.wakeWordPhrase}\""
            )
            if (_uiState.value.isBackgroundServiceEnabled && _uiState.value.isForegroundServiceRunning) {
                CaroLineVoiceService.startOrUpdate(
                    context = appContext,
                    statusText = reason,
                    wakeWord = _uiState.value.wakeWordPhrase,
                    batterySavingMode = _uiState.value.isBatterySavingListeningMode,
                    screenOffSupported = _uiState.value.isScreenOffVoiceEnabled
                )
            }
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
            CaroLineVoiceService.updateFloatingSnippet(text)
        },
        onSpeakingFinished = {
            if (_uiState.value.liveState != LiveState.DISCONNECTED) {
                _uiState.value = _uiState.value.copy(
                    liveState = LiveState.LISTENING,
                    currentEmotion = AvatarEmotion.LISTENING
                )
                if (_uiState.value.isHandsFreeMode && (_uiState.value.isScreenInteractive || canListenWhileScreenOff())) {
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
        },
        onContactMatchesUpdated = { matches, title ->
            _uiState.value = _uiState.value.copy(
                matchedContacts = matches,
                contactSearchTitle = title
            )
        },
        onMessageDraftUpdated = { draft ->
            _uiState.value = _uiState.value.copy(pendingMessageDraft = draft)
        },
        onWeatherReportUpdated = { weather ->
            _uiState.value = _uiState.value.copy(latestWeatherReport = weather)
        },
        onVoiceStudioCatalogUpdated = { engines, voices ->
            _uiState.value = _uiState.value.copy(
                installedTtsEngines = engines,
                availableDeviceVoices = voices
            )
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
        brainEngine.adaptiveAiManager = adaptiveAiManager

        // Requirement A: Inspect device RAM and select appropriate performance profile
        val resourceSnapshot = toolExecutor.performanceProfileManager.inspectDeviceResources()
        val isAutoPerf = sharedPrefs.getBoolean(KEY_AUTO_PERF_PROFILE, true)
        val savedProfileId = sharedPrefs.getString(KEY_PERF_PROFILE_ID, null)
        val effectiveProfile = if (isAutoPerf) {
            resourceSnapshot.recommendedProfile
        } else {
            PerformanceProfile.fromId(savedProfileId) ?: resourceSnapshot.recommendedProfile
        }

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

        val savedSpokenLangCode = sharedPrefs.getString(KEY_SPOKEN_LANG_CODE, "hi-IN") ?: "hi-IN"
        val displayLang = when {
            savedSpokenLangCode.startsWith("bn") -> "Bengali (বাংলা)"
            savedSpokenLangCode.startsWith("en") -> "English (en-IN)"
            else -> "Hindi (हिन्दी)"
        }

        val savedVoice = sharedPrefs.getString(KEY_VOICE, "System Default Hindi (hi-IN)") ?: "System Default Hindi (hi-IN)"
        val savedVoiceId = sharedPrefs.getString(KEY_VOICE_ID, "system_locale_hi-IN") ?: "system_locale_hi-IN"
        val savedEnginePkg = sharedPrefs.getString(KEY_TTS_ENGINE_PKG, "") ?: ""
        val savedPitch = sharedPrefs.getFloat(KEY_VOICE_PITCH, 1.08f)
        val savedSpeed = sharedPrefs.getFloat(KEY_VOICE_SPEED, 1.0f)
        val savedVoiceMuted = sharedPrefs.getBoolean(KEY_VOICE_MUTED, false)
        val savedHandsFree = sharedPrefs.getBoolean(KEY_HANDS_FREE, true)
        val savedFloatingBubble = sharedPrefs.getBoolean(KEY_FLOATING_BUBBLE_ENABLED, true)
        val savedBgService = sharedPrefs.getBoolean(KEY_BG_SERVICE_ENABLED, true)
        val savedWakeWordEnabled = sharedPrefs.getBoolean(KEY_WAKE_WORD_ENABLED, true)
        val savedWakeWordPhrase = sharedPrefs.getString(KEY_WAKE_WORD_PHRASE, "Hey CaroLine") ?: "Hey CaroLine"
        val savedPowerModeId = sharedPrefs.getString(KEY_LISTENING_POWER_MODE, ListeningPowerMode.BATTERY_SAVING.id)
        val savedPowerMode = ListeningPowerMode.fromId(savedPowerModeId)
        val savedScreenOffVoice = sharedPrefs.getBoolean(KEY_SCREEN_OFF_VOICE_ENABLED, true)
        val savedMusicService = sharedPrefs.getString(KEY_PREF_MUSIC_SERVICE, "Auto (Smart Play)") ?: "Auto (Smart Play)"
        val savedBuiltInMusic = sharedPrefs.getBoolean(KEY_BUILTIN_MUSIC_FALLBACK, true)
        val savedTheme = sharedPrefs.getString(KEY_SELECTED_THEME, "Anime-JARVIS Gold & Cyan") ?: "Anime-JARVIS Gold & Cyan"
        val savedJarvisCoreMode = sharedPrefs.getBoolean(KEY_JARVIS_CORE_MODE, false)

        val savedStyleStr = sharedPrefs.getString(KEY_AVATAR_STYLE, AvatarStyle.GOTHIC_CYBER.name) ?: AvatarStyle.GOTHIC_CYBER.name
        val savedStyle = try { AvatarStyle.valueOf(savedStyleStr) } catch (_: Exception) { AvatarStyle.GOTHIC_CYBER }
        val customUriStr = sharedPrefs.getString(KEY_CUSTOM_URI, null)
        val customUri = if (customUriStr != null) try { Uri.parse(customUriStr) } catch (_: Exception) { null } else null

        brainEngine.spokenLanguageCode = savedSpokenLangCode
        brainEngine.voicePitch = savedPitch
        brainEngine.voiceSpeed = savedSpeed
        brainEngine.isVoiceMuted = savedVoiceMuted
        brainEngine.selectedVoiceId = savedVoiceId
        brainEngine.selectedFemaleVoiceProfile = savedVoice
        if (savedEnginePkg.isNotBlank()) {
            brainEngine.selectTtsEngine(savedEnginePkg)
        }

        hindiSpeechRecognizer.preferredLanguageCode = savedSpokenLangCode
        hindiSpeechRecognizer.isWakeWordEnabled = savedWakeWordEnabled
        hindiSpeechRecognizer.configuredWakeWord = savedWakeWordPhrase
        hindiSpeechRecognizer.listeningPowerMode = savedPowerMode
        hindiSpeechRecognizer.isScreenOffSupportEnabled = savedScreenOffVoice

        toolExecutor.preferredMusicService = savedMusicService
        toolExecutor.enableBuiltInMusicFallback = savedBuiltInMusic

        _uiState.value = _uiState.value.copy(
            performanceProfile = effectiveProfile,
            isAutoPerformanceProfile = isAutoPerf,
            totalRamMb = resourceSnapshot.totalRamMb,
            availRamMb = resourceSnapshot.availableRamMb,
            isJarvisCoreMode = savedJarvisCoreMode,
            isManagedCloudConfigured = aiRouter.isFirebaseConfigured() || geminiConfig.hasValidKey(),
            providerConfigs = mapOf(
                AiProviderType.GOOGLE_GEMINI to geminiConfig,
                AiProviderType.OPENAI_CHATGPT to openAiConfig,
                AiProviderType.OPENROUTER_CUSTOM to openRouterConfig
            ),
            spokenLanguageCode = savedSpokenLangCode,
            activeLanguage = displayLang,
            selectedVoice = savedVoice,
            selectedVoiceId = savedVoiceId,
            selectedTtsEnginePackage = savedEnginePkg,
            voicePitch = savedPitch,
            voiceSpeed = savedSpeed,
            isVoiceMuted = savedVoiceMuted,
            isHandsFreeMode = savedHandsFree,
            isFloatingBubbleEnabled = savedFloatingBubble,
            canDrawSystemOverlays = CaroLineVoiceService.canDrawOverlays(appContext),
            avatarStyle = savedStyle,
            customAvatarUri = customUri,
            isBackgroundServiceEnabled = savedBgService,
            isWakeWordEnabled = savedWakeWordEnabled,
            wakeWordPhrase = savedWakeWordPhrase,
            listeningPowerMode = savedPowerMode,
            isBatterySavingListeningMode = savedPowerMode == ListeningPowerMode.BATTERY_SAVING,
            isScreenOffVoiceEnabled = savedScreenOffVoice,
            listeningCycleStatusText = "${savedPowerMode.titleEn} • Wake: \"$savedWakeWordPhrase\"",
            preferredMusicService = savedMusicService,
            enableBuiltInMusicFallback = savedBuiltInMusic,
            selectedThemeName = savedTheme,
            smartMemories = brainEngine.getSmartMemories(),
            dailyReminders = brainEngine.getDailyReminders()
        )

        refreshDeviceTelemetry()
        refreshAppCatalog()

        // Start 25-minute battery-safe Adaptive AI Engine
        adaptiveAiManager.startPeriodicAdaptation {
            refreshDeviceTelemetry()
        }

        viewModelScope.launch {
            adaptiveAiManager.snapshot.collect { snap ->
                _uiState.value = _uiState.value.copy(adaptiveAiSnapshot = snap)
            }
        }

        viewModelScope.launch {
            toolExecutor.builtInMusicPlayer.isPlaying.collect { playing ->
                _uiState.value = _uiState.value.copy(isMusicPlaying = playing)
            }
        }

        viewModelScope.launch {
            toolExecutor.toolEvents.collect { event ->
                val maxItems = _uiState.value.performanceProfile.maxToolHistory
                val updatedList = listOf(event) + _uiState.value.toolHistory.take(maxItems)
                _uiState.value = _uiState.value.copy(
                    activeToolEvent = event,
                    toolHistory = updatedList
                )
                refreshDeviceTelemetry()
            }
        }

        // Observe Foreground Voice Service & Floating Window events (shared single engine!)
        viewModelScope.launch {
            CaroLineVoiceService.isServiceRunning.collect { running ->
                hindiSpeechRecognizer.isForegroundServiceActive = running
                val screenOffSupported = BengaliSpeechRecognizer.isScreenOffListeningSupported(
                    isForegroundServiceRunning = running,
                    isScreenOffSupportEnabled = _uiState.value.isScreenOffVoiceEnabled,
                    hasMicPermission = true,
                    batteryLevel = _uiState.value.batteryLevel,
                    isCharging = _uiState.value.isCharging
                )
                _uiState.value = _uiState.value.copy(
                    isForegroundServiceRunning = running,
                    isScreenOffConditionSupported = screenOffSupported
                )
            }
        }

        viewModelScope.launch {
            CaroLineVoiceService.isScreenInteractive.collect { interactive ->
                _uiState.value = _uiState.value.copy(isScreenInteractive = interactive)
                hindiSpeechRecognizer.onScreenInteractiveChanged(interactive)
                if (!interactive) {
                    if (canListenWhileScreenOff() && _uiState.value.liveState != LiveState.DISCONNECTED) {
                        val status = "Screen-Off Battery-Saving Mode • Say \"${_uiState.value.wakeWordPhrase}\""
                        _uiState.value = _uiState.value.copy(listeningCycleStatusText = status)
                        CaroLineVoiceService.startOrUpdate(
                            context = appContext,
                            statusText = status,
                            wakeWord = _uiState.value.wakeWordPhrase,
                            batterySavingMode = _uiState.value.isBatterySavingListeningMode,
                            screenOffSupported = _uiState.value.isScreenOffVoiceEnabled
                        )
                    } else {
                        hindiSpeechRecognizer.pauseForAiSpeech()
                    }
                } else if (_uiState.value.liveState != LiveState.DISCONNECTED) {
                    val status = "${_uiState.value.listeningPowerMode.titleEn} • Wake: \"${_uiState.value.wakeWordPhrase}\""
                    _uiState.value = _uiState.value.copy(
                        isInEcoStandby = false,
                        listeningCycleStatusText = status
                    )
                    ensureContinuousListeningActive()
                }
            }
        }

        viewModelScope.launch {
            CaroLineVoiceService.stopRequestedEvents.collect {
                stopSession()
            }
        }

        viewModelScope.launch {
            CaroLineVoiceService.resumeRequestedEvents.collect {
                wakeAssistantFromStandby()
            }
        }

        viewModelScope.launch {
            CaroLineVoiceService.toggleBatterySaverEvents.collect {
                toggleBatterySavingListeningMode()
            }
        }

        viewModelScope.launch {
            CaroLineVoiceService.floatingMicClickedEvents.collect {
                triggerCallSession()
            }
        }

        viewModelScope.launch {
            CaroLineVoiceService.floatingTextCommandEvents.collect { cmd ->
                onUserPrompt(cmd)
            }
        }

        viewModelScope.launch {
            CaroLineVoiceService.networkRestoredEvents.collect {
                refreshDeviceTelemetry()
            }
        }

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
        val netReport = toolExecutor.weatherNetworkEngine.getNetworkAndDataUsage()
        val canOverlay = CaroLineVoiceService.canDrawOverlays(appContext)
        val batLevel = (info["battery_level"] as? Int) ?: 100
        val charging = (info["is_charging"] as? Boolean) ?: false
        hindiSpeechRecognizer.batteryLevel = batLevel
        hindiSpeechRecognizer.isCharging = charging

        val screenOffSupported = BengaliSpeechRecognizer.isScreenOffListeningSupported(
            isForegroundServiceRunning = _uiState.value.isForegroundServiceRunning || _uiState.value.isBackgroundServiceEnabled,
            isScreenOffSupportEnabled = _uiState.value.isScreenOffVoiceEnabled,
            hasMicPermission = true,
            batteryLevel = batLevel,
            isCharging = charging
        )

        _uiState.value = _uiState.value.copy(
            batteryLevel = batLevel,
            isCharging = charging,
            isOnline = (info["is_online"] as? Boolean) ?: true,
            networkType = (info["network_type"] as? String) ?: "Wi-Fi",
            availRamMb = (info["avail_ram_mb"] as? Int) ?: 2048,
            totalRamMb = (info["total_ram_mb"] as? Int) ?: 4096,
            networkAndDataReport = netReport,
            canDrawSystemOverlays = canOverlay,
            isScreenOffConditionSupported = screenOffSupported
        )
    }

    fun refreshLiveWeather(cityQuery: String = "New Delhi") {
        viewModelScope.launch {
            val report = toolExecutor.weatherNetworkEngine.fetchLiveWeather(cityQuery)
            _uiState.value = _uiState.value.copy(latestWeatherReport = report)
        }
    }

    fun refreshAppCatalog() {
        viewModelScope.launch(Dispatchers.IO) {
            val catalog = toolExecutor.appLauncher.getCatalogWithInstalledStatus()
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(appCatalog = catalog)
            }
        }
    }

    // --- Requirement A: Performance Profile Selection ---

    fun setPerformanceProfile(profile: PerformanceProfile, autoDetect: Boolean = false) {
        sharedPrefs.edit()
            .putString(KEY_PERF_PROFILE_ID, profile.id)
            .putBoolean(KEY_AUTO_PERF_PROFILE, autoDetect)
            .apply()
        val trimmedHistory = _uiState.value.conversationHistory.takeLast(profile.maxConversationHistory)
        _uiState.value = _uiState.value.copy(
            performanceProfile = profile,
            isAutoPerformanceProfile = autoDetect,
            conversationHistory = trimmedHistory
        )
    }

    // --- Requirement B: Owner Spoken Language Selection (Hindi Default, Bengali, English) ---

    fun setSpokenLanguage(langCode: String) {
        val normalizedCode = when {
            langCode.startsWith("bn", ignoreCase = true) || langCode.contains("Bengali", ignoreCase = true) || langCode.contains("বাংলা") -> "bn-IN"
            langCode.startsWith("en", ignoreCase = true) || langCode.contains("English", ignoreCase = true) -> "en-IN"
            else -> "hi-IN"
        }
        val label = when (normalizedCode) {
            "bn-IN" -> "Bengali (বাংলা)"
            "en-IN" -> "English (en-IN)"
            else -> "Hindi (हिन्दी)"
        }

        sharedPrefs.edit()
            .putString(KEY_SPOKEN_LANG_CODE, normalizedCode)
            .putString(KEY_ACTIVE_LANGUAGE, label)
            .apply()

        brainEngine.spokenLanguageCode = normalizedCode
        brainEngine.discoverInstalledEnginesAndRealVoices()
        hindiSpeechRecognizer.preferredLanguageCode = normalizedCode

        _uiState.value = _uiState.value.copy(
            spokenLanguageCode = normalizedCode,
            activeLanguage = label,
            isLanguageLocked = normalizedCode == "hi-IN"
        )
    }

    // --- Requirement C: Real Female Voice Studio Controls ---

    fun selectTtsEnginePackage(packageName: String) {
        sharedPrefs.edit().putString(KEY_TTS_ENGINE_PKG, packageName).apply()
        brainEngine.selectTtsEngine(packageName)
        _uiState.value = _uiState.value.copy(selectedTtsEnginePackage = packageName)
    }

    fun selectDeviceVoice(voiceInfo: DeviceVoiceInfo) {
        sharedPrefs.edit()
            .putString(KEY_VOICE_ID, voiceInfo.voiceId)
            .putString(KEY_VOICE, voiceInfo.displayName)
            .apply()
        brainEngine.selectDeviceVoiceById(voiceInfo.voiceId)
        _uiState.value = _uiState.value.copy(
            selectedVoiceId = voiceInfo.voiceId,
            selectedVoice = voiceInfo.displayName
        )
    }

    // --- Requirement E & G: Contact Call Selection & Message Draft Confirmation ---

    fun selectMatchedContact(contact: DeviceContactItem) {
        brainEngine.selectContactToCall(contact, directCall = _uiState.value.useDirectCallWhenPermitted)
    }

    fun clearMatchedContacts() {
        _uiState.value = _uiState.value.copy(matchedContacts = emptyList(), contactSearchTitle = "")
    }

    fun updatePendingMessageDraftText(newText: String) {
        brainEngine.updatePendingMessageDraftText(newText)
    }

    fun confirmMessageDraftViaWhatsApp() {
        brainEngine.confirmMessageDraftViaWhatsApp()
    }

    fun confirmMessageDraftViaSms() {
        brainEngine.confirmMessageDraftViaSms()
    }

    fun cancelMessageDraft() {
        brainEngine.cancelMessageDraft()
    }

    // --- Requirement F: Floating CaroLine Window ---

    fun toggleFloatingBubble() {
        val next = !_uiState.value.isFloatingBubbleEnabled
        sharedPrefs.edit().putBoolean(KEY_FLOATING_BUBBLE_ENABLED, next).apply()
        val canOverlay = CaroLineVoiceService.canDrawOverlays(appContext)
        if (next && canOverlay) {
            CaroLineVoiceService.showSystemFloatingOverlay(appContext)
        } else {
            CaroLineVoiceService.hideSystemFloatingOverlay(appContext)
        }
        _uiState.value = _uiState.value.copy(
            isFloatingBubbleEnabled = next,
            canDrawSystemOverlays = canOverlay
        )
    }

    fun setFloatingWindowExpanded(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(isFloatingWindowExpanded = expanded)
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
            val msg = when {
                _uiState.value.spokenLanguageCode.startsWith("bn") -> res["message_bn"]?.toString()
                _uiState.value.spokenLanguageCode.startsWith("en") -> res["message_en"]?.toString()
                else -> res["message_hi"]?.toString()
            } ?: "Opened ${entry.displayName}"
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
        val maxItems = _uiState.value.performanceProfile.maxConversationHistory
        val updated = (current + message).takeLast(maxItems)
        _uiState.value = _uiState.value.copy(conversationHistory = updated)
    }

    fun clearConversationHistory() {
        _uiState.value = _uiState.value.copy(
            conversationHistory = listOf(
                ConversationMessage(
                    role = "assistant",
                    text = brainEngine.getGreetingForActiveLanguage(),
                    providerBadge = "CAROLINE A3.2"
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

    // --- Smart Features Actions ---

    fun addSmartMemory(fact: String) {
        brainEngine.addSmartMemory(fact)
    }

    fun removeSmartMemory(id: String) {
        brainEngine.removeSmartMemory(id)
    }

    fun clearSmartMemories() {
        brainEngine.clearSmartMemories()
    }

    fun addDailyReminder(title: String, timeText: String = "Today") {
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
        syncForegroundServiceNotification()
    }

    fun setWakeWordPhrase(phrase: String) {
        val clean = phrase.trim().ifEmpty { "Hey CaroLine" }
        sharedPrefs.edit().putString(KEY_WAKE_WORD_PHRASE, clean).apply()
        hindiSpeechRecognizer.configuredWakeWord = clean
        _uiState.value = _uiState.value.copy(
            wakeWordPhrase = clean,
            listeningCycleStatusText = "${_uiState.value.listeningPowerMode.titleEn} • Wake: \"$clean\""
        )
        syncForegroundServiceNotification()
    }

    fun setListeningPowerMode(mode: ListeningPowerMode) {
        sharedPrefs.edit().putString(KEY_LISTENING_POWER_MODE, mode.id).apply()
        hindiSpeechRecognizer.listeningPowerMode = mode
        val isSaver = mode == ListeningPowerMode.BATTERY_SAVING
        _uiState.value = _uiState.value.copy(
            listeningPowerMode = mode,
            isBatterySavingListeningMode = isSaver,
            listeningCycleStatusText = "${mode.titleEn} • Wake: \"${_uiState.value.wakeWordPhrase}\""
        )
        syncForegroundServiceNotification()
    }

    fun toggleBatterySavingListeningMode() {
        val nextMode = if (_uiState.value.listeningPowerMode == ListeningPowerMode.BATTERY_SAVING) {
            ListeningPowerMode.CONTINUOUS_ACTIVE
        } else {
            ListeningPowerMode.BATTERY_SAVING
        }
        setListeningPowerMode(nextMode)
    }

    fun toggleScreenOffVoiceSupport() {
        val next = !_uiState.value.isScreenOffVoiceEnabled
        sharedPrefs.edit().putBoolean(KEY_SCREEN_OFF_VOICE_ENABLED, next).apply()
        hindiSpeechRecognizer.isScreenOffSupportEnabled = next
        val supported = BengaliSpeechRecognizer.isScreenOffListeningSupported(
            isForegroundServiceRunning = _uiState.value.isForegroundServiceRunning || _uiState.value.isBackgroundServiceEnabled,
            isScreenOffSupportEnabled = next,
            hasMicPermission = true,
            batteryLevel = _uiState.value.batteryLevel,
            isCharging = _uiState.value.isCharging
        )
        _uiState.value = _uiState.value.copy(
            isScreenOffVoiceEnabled = next,
            isScreenOffConditionSupported = supported
        )
        syncForegroundServiceNotification()
    }

    fun wakeAssistantFromStandby() {
        activateWakeWordWindow()
        if (_uiState.value.liveState == LiveState.DISCONNECTED) {
            startSession()
        } else {
            _uiState.value = _uiState.value.copy(isInEcoStandby = false)
            hindiSpeechRecognizer.wakeFromEcoStandby()
            ensureContinuousListeningActive()
        }
    }

    private fun activateWakeWordWindow() {
        wakeWordWindowJob?.cancel()
        _uiState.value = _uiState.value.copy(isWakeWordActiveWindow = true)
        wakeWordWindowJob = viewModelScope.launch {
            delay(20_000L)
            _uiState.value = _uiState.value.copy(isWakeWordActiveWindow = false)
        }
    }

    private fun canListenWhileScreenOff(): Boolean {
        return BengaliSpeechRecognizer.isScreenOffListeningSupported(
            isForegroundServiceRunning = _uiState.value.isForegroundServiceRunning || _uiState.value.isBackgroundServiceEnabled,
            isScreenOffSupportEnabled = _uiState.value.isScreenOffVoiceEnabled,
            hasMicPermission = true,
            batteryLevel = _uiState.value.batteryLevel,
            isCharging = _uiState.value.isCharging
        )
    }

    private fun syncForegroundServiceNotification() {
        if (_uiState.value.isBackgroundServiceEnabled && _uiState.value.liveState != LiveState.DISCONNECTED) {
            val modeText = if (_uiState.value.isBatterySavingListeningMode) "Battery-Saving Mode" else "Active Listening"
            CaroLineVoiceService.startOrUpdate(
                context = appContext,
                statusText = "Wake: \"${_uiState.value.wakeWordPhrase}\" • $modeText",
                wakeWord = _uiState.value.wakeWordPhrase,
                batterySavingMode = _uiState.value.isBatterySavingListeningMode,
                screenOffSupported = _uiState.value.isScreenOffVoiceEnabled
            )
        }
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

    fun selectAiProvider(provider: AiProviderType) {
        sharedPrefs.edit().putString(KEY_SELECTED_PROVIDER, provider.name).apply()
        _uiState.value = _uiState.value.copy(
            selectedAiProvider = provider,
            activeProviderBadge = "${provider.shortBadge} • A3.2"
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

        val updatedConfig = ProviderConfig(
            providerType = providerType,
            apiKey = cleanKey,
            endpointUrl = cleanEndpoint,
            modelName = cleanModel
        )
        val updatedMap = _uiState.value.providerConfigs.toMutableMap().apply {
            put(providerType, updatedConfig)
        }
        _uiState.value = _uiState.value.copy(providerConfigs = updatedMap, errorMessage = null)
    }

    fun removeProviderCredential(providerType: AiProviderType) {
        updateProviderConfig(providerType, "", providerType.defaultEndpoint, providerType.defaultModel)
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

        if (_uiState.value.isBackgroundServiceEnabled) {
            val modeText = if (_uiState.value.isBatterySavingListeningMode) "Battery-Saving Mode" else "Active Mode"
            CaroLineVoiceService.startOrUpdate(
                context = appContext,
                statusText = "Wake: \"${_uiState.value.wakeWordPhrase}\" • $modeText",
                wakeWord = _uiState.value.wakeWordPhrase,
                batterySavingMode = _uiState.value.isBatterySavingListeningMode,
                screenOffSupported = _uiState.value.isScreenOffVoiceEnabled
            )
        }
        if (_uiState.value.isFloatingBubbleEnabled && CaroLineVoiceService.canDrawOverlays(appContext)) {
            CaroLineVoiceService.showSystemFloatingOverlay(appContext)
        }

        val greeting = brainEngine.getGreetingForActiveLanguage()

        if (hindiSpeechRecognizer.isAvailable()) {
            audioStreamer.stop()
            _uiState.value = _uiState.value.copy(
                activeEngine = ActiveEngine.BENGALI_VOICE_ENGINE,
                liveState = LiveState.LISTENING,
                isMicHardwareVerified = true,
                isInEcoStandby = false,
                currentEmotion = AvatarEmotion.LISTENING,
                speechSnippet = greeting
            )
            hindiSpeechRecognizer.startContinuousListening()
            if (!hasSpokenInitialGreetingInSession) {
                hasSpokenInitialGreetingInSession = true
                hindiSpeechRecognizer.pauseForAiSpeech()
                brainEngine.speak(greeting, providerBadge = "CAROLINE A3.2")
            }
        } else {
            val micStarted = audioStreamer.start()
            if (!micStarted) {
                _uiState.value = _uiState.value.copy(
                    liveState = LiveState.ERROR,
                    isMicHardwareVerified = false,
                    currentEmotion = AvatarEmotion.SURPRISED,
                    errorMessage = "Microphone access failed. Please grant microphone permission."
                )
                return
            }
            _uiState.value = _uiState.value.copy(
                activeEngine = ActiveEngine.BENGALI_VOICE_ENGINE,
                liveState = LiveState.LISTENING,
                isMicHardwareVerified = true,
                isInEcoStandby = false,
                currentEmotion = AvatarEmotion.LISTENING,
                speechSnippet = greeting
            )
            if (!hasSpokenInitialGreetingInSession) {
                hasSpokenInitialGreetingInSession = true
                brainEngine.speak(greeting, providerBadge = "CAROLINE A3.2")
            }
        }
    }

    private fun ensureContinuousListeningActive() {
        if (_uiState.value.liveState == LiveState.DISCONNECTED) return
        if (!_uiState.value.isScreenInteractive && !canListenWhileScreenOff()) return

        if (hindiSpeechRecognizer.isAvailable()) {
            hindiSpeechRecognizer.resumeAfterAiSpeech()
        } else if (!audioStreamer.isRecording.value) {
            val ok = audioStreamer.start()
            _uiState.value = _uiState.value.copy(isMicHardwareVerified = ok)
        }
    }

    private fun handleRecognizedSpeech(recognizedText: String) {
        val clean = recognizedText.trim()
        if (clean.isEmpty()) return

        val wakePhrase = _uiState.value.wakeWordPhrase
        val hasWakeWord = BengaliSpeechRecognizer.matchesWakeWord(clean, wakePhrase)

        // When screen is OFF and wake word is enabled, require "Hey CaroLine" unless already in active 20s window
        if (!_uiState.value.isScreenInteractive &&
            _uiState.value.isWakeWordEnabled &&
            !_uiState.value.isWakeWordActiveWindow &&
            !hasWakeWord
        ) {
            // Ignore ambient noise while screen is off and re-schedule battery-saving listening
            ensureContinuousListeningActive()
            return
        }

        val effectiveCommand = if (hasWakeWord) {
            activateWakeWordWindow()
            BengaliSpeechRecognizer.extractCommandAfterWakeWord(clean, wakePhrase)
        } else {
            clean
        }

        // If the user only said "Hey CaroLine" without a command after it, acknowledge & listen
        if (hasWakeWord && effectiveCommand.isEmpty()) {
            val wakeAck = when {
                _uiState.value.spokenLanguageCode.startsWith("bn") -> "হ্যাঁ বস, আমি শুনছি! বলুন কী করতে হবে?"
                _uiState.value.spokenLanguageCode.startsWith("en") -> "Yes Boss, I am listening! Tell me what to do."
                else -> "जी बॉस, मैं सुन रही हूँ! बताइए क्या करना है?"
            }
            _uiState.value = _uiState.value.copy(
                speechSnippet = wakeAck,
                errorMessage = null
            )
            hindiSpeechRecognizer.pauseForAiSpeech()
            brainEngine.speak(wakeAck, providerBadge = "HEY CAROLINE")
            return
        }

        _uiState.value = _uiState.value.copy(
            speechSnippet = "🗣️ \"$effectiveCommand\"",
            errorMessage = null
        )

        hindiSpeechRecognizer.pauseForAiSpeech()

        brainEngine.processPrompt(
            input = effectiveCommand,
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
            if (geminiKey.isNotBlank() && geminiKey != "MY_GEMINI_API_KEY") {
                val reply = fallbackRestClient.apply {
                    activeLanguageName = _uiState.value.activeLanguage
                    isLanguageLockedToBengali = _uiState.value.isLanguageLocked
                }.generateBengaliFromWav(geminiKey, wavBytes)

                if (!reply.isNullOrBlank()) {
                    brainEngine.speak(reply, providerBadge = "GEMINI AUDIO")
                }
            }
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
            speechSnippet = "Voice mode is paused. Tap the microphone button below to speak."
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
                "CaroLine AI A3.2 Active — Listening for commands"
            )
        }
    }

    fun changeLanguageWithPin(pinInput: String, newLanguage: String): Boolean {
        if (pinInput.trim().isNotEmpty() && pinInput.trim() != _uiState.value.languageLockPin) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Incorrect PIN. Use the owner PIN to switch language."
            )
            return false
        }
        setSpokenLanguage(newLanguage)
        return true
    }

    fun lockLanguageToHindi() {
        setSpokenLanguage("hi-IN")
    }

    fun lockLanguageToBengali() {
        setSpokenLanguage("bn-IN")
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

    fun onUserPrompt(promptText: String) {
        if (_uiState.value.liveState == LiveState.SPEAKING) {
            handleUserInterruption()
        }

        val cleanCommand = promptText.replace(Regex("^[\\p{So}\\p{Cn}\\s]+"), "").trim()
        if (cleanCommand.isEmpty()) return

        _uiState.value = _uiState.value.copy(speechSnippet = "🗣️ \"$cleanCommand\"")

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

    fun handleUserInterruption() {
        audioPlayer.flushAndInterrupt()
        brainEngine.stop()
        _uiState.value = _uiState.value.copy(
            liveState = LiveState.LISTENING,
            currentEmotion = AvatarEmotion.LISTENING,
            speechSnippet = "Listening, Boss..."
        )
        ensureContinuousListeningActive()
    }

    fun onAvatarTapped() {
        _uiState.value = _uiState.value.copy(
            manualEmotionOverride = AvatarEmotion.SMILING
        )
        val remark = brainEngine.getGreetingForActiveLanguage()
        _uiState.value = _uiState.value.copy(speechSnippet = remark)
        hindiSpeechRecognizer.pauseForAiSpeech()
        brainEngine.speak(remark, providerBadge = "CAROLINE A3.2")

        viewModelScope.launch {
            delay(2500)
            if (_uiState.value.manualEmotionOverride == AvatarEmotion.SMILING) {
                _uiState.value = _uiState.value.copy(manualEmotionOverride = null)
            }
        }
    }

    fun setPrimaryApiKey(newKey: String) {}
    fun setBackupApiKey(newKey: String) {}
    fun setApiMode(mode: ApiMode) {}

    fun setSelectedVoice(voiceName: String) {
        val matched = _uiState.value.availableDeviceVoices.firstOrNull {
            it.displayName == voiceName || it.voiceId == voiceName
        }
        if (matched != null) {
            selectDeviceVoice(matched)
        } else {
            sharedPrefs.edit().putString(KEY_VOICE, voiceName).apply()
            brainEngine.selectedFemaleVoiceProfile = voiceName
            _uiState.value = _uiState.value.copy(selectedVoice = voiceName)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun dismissActiveToolToast() {
        _uiState.value = _uiState.value.copy(activeToolEvent = null)
    }

    override fun onStateChanged(state: LiveState) {}
    override fun onAudioReceived(pcm24kData: ByteArray) {
        audioPlayer.enqueueAudio(pcm24kData)
    }
    override fun onInterrupted() {
        audioPlayer.flushAndInterrupt()
    }
    override fun onSpeechSnippet(text: String) {
        _uiState.value = _uiState.value.copy(speechSnippet = text)
        appendConversationMessage(ConversationMessage(role = "assistant", text = text, providerBadge = "GEMINI LIVE"))
    }
    override fun onError(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }

    override fun onCleared() {
        super.onCleared()
        bargeInMonitorJob?.cancel()
        adaptiveAiManager.stop()
        hindiSpeechRecognizer.stopListening()
        audioStreamer.stop()
        audioPlayer.release()
        toolExecutor.builtInMusicPlayer.stop()
        geminiLiveSession?.disconnect()
        brainEngine.release()
    }
}

typealias MahiViewModel = CaroLineViewModel
