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
import com.example.live.GeminiLiveSession
import com.example.live.LiveSessionListener
import com.example.live.LiveState
import com.example.live.MultiAiProviderRouter
import com.example.live.ProviderConfig
import com.example.live.ProviderTestResult
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
    val appVersion: String = "2.5 SMART JARVIS",
    val activeTab: DashboardTab = DashboardTab.JARVIS_HUD,
    val isJarvisCoreMode: Boolean = true,
    val liveState: LiveState = LiveState.DISCONNECTED,
    val isMicMuted: Boolean = false,
    val isHandsFreeMode: Boolean = true,
    val isMicHardwareVerified: Boolean = false,
    val speechSnippet: String = "হ্যালো বস! আমি ক্যারোলাইন ২.৫ স্মার্ট জারভিস। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?",
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
    val activeProviderBadge: String = "GEMINI • JARVIS",
    // Legacy compatibility fields
    val primaryApiKey: String = "",
    val backupApiKey: String = "",
    val apiMode: ApiMode = ApiMode.AUTO_FAILOVER,
    val activeEngine: ActiveEngine = ActiveEngine.BENGALI_VOICE_ENGINE,
    // Conversation Context & History
    val conversationHistory: List<ConversationMessage> = listOf(
        ConversationMessage(
            role = "assistant",
            text = "হ্যালো বস! আমি ক্যারোলাইন ২.৫ স্মার্ট জারভিস। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?",
            providerBadge = "JARVIS CORE"
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
    // Voice & Language Lock (Bengali Default)
    val selectedVoice: String = "Aoede",
    val activeLanguage: String = "বাংলা (Bengali)",
    val isLanguageLocked: Boolean = true,
    val languageLockPin: String = "1234",
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

// Backward compatibility alias
typealias MahiUiState = CaroLineUiState

class CaroLineViewModel(application: Application) : AndroidViewModel(application), LiveSessionListener {

    companion object {
        private const val TAG = "CaroLineViewModel"
        private const val PREFS_NAME = "caroline_ai_prefs"
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
        private const val KEY_JARVIS_CORE_MODE = "jarvis_core_mode"
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
    private val aiRouter = MultiAiProviderRouter(appContext)

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
        },
        onSensitiveActionRequested = { pending ->
            _uiState.value = _uiState.value.copy(pendingSensitiveAction = pending)
        },
        onMessageLogged = { msg ->
            appendConversationMessage(msg)
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
        val savedJarvisCoreMode = sharedPrefs.getBoolean(KEY_JARVIS_CORE_MODE, true)

        val savedModeStr = sharedPrefs.getString(KEY_API_MODE, ApiMode.AUTO_FAILOVER.name) ?: ApiMode.AUTO_FAILOVER.name
        val savedMode = try { ApiMode.valueOf(savedModeStr) } catch (_: Exception) { ApiMode.AUTO_FAILOVER }

        val savedVoice = sharedPrefs.getString(KEY_VOICE, "Aoede") ?: "Aoede"
        val savedHandsFree = sharedPrefs.getBoolean(KEY_HANDS_FREE, true)

        val savedStyleStr = sharedPrefs.getString(KEY_AVATAR_STYLE, AvatarStyle.GOTHIC_CYBER.name) ?: AvatarStyle.GOTHIC_CYBER.name
        val savedStyle = try { AvatarStyle.valueOf(savedStyleStr) } catch (_: Exception) { AvatarStyle.GOTHIC_CYBER }

        val customUriStr = sharedPrefs.getString(KEY_CUSTOM_URI, null)
        val customUri = if (customUriStr != null) try { Uri.parse(customUriStr) } catch (_: Exception) { null } else null

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
            isJarvisCoreMode = savedJarvisCoreMode,
            selectedAiProvider = selectedProvider,
            autoFailoverEnabled = savedAutoFailover,
            providerConfigs = mapOf(
                AiProviderType.GOOGLE_GEMINI to geminiConfig,
                AiProviderType.OPENAI_CHATGPT to openAiConfig,
                AiProviderType.OPENROUTER_CUSTOM to openRouterConfig
            ),
            activeProviderBadge = "${selectedProvider.shortBadge} • JARVIS",
            primaryApiKey = savedGeminiKey,
            backupApiKey = savedOpenAiKey,
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

        refreshDeviceTelemetry()
        refreshAppCatalog()

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
                if (_uiState.value.liveState == LiveState.SPEAKING &&
                    _uiState.value.activeEngine == ActiveEngine.PRIMARY_GEMINI_LIVE &&
                    amp > 0.22f
                ) {
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
            val msg = res["message_bn"]?.toString()
                ?: "বস, ${entry.displayName} খোলার চেষ্টা করা হয়েছে।"
            _uiState.value = _uiState.value.copy(speechSnippet = msg)
            bengaliSpeechRecognizer.pauseForAiSpeech()
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
                    text = "কথোপকথনের ইতিহাস মুছে ফেলা হয়েছে বস। নতুন করে শুরু করুন!",
                    providerBadge = "JARVIS CORE"
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

    // --- Three AI Brain System Management ---

    fun selectAiProvider(provider: AiProviderType) {
        sharedPrefs.edit().putString(KEY_SELECTED_PROVIDER, provider.name).apply()
        _uiState.value = _uiState.value.copy(
            selectedAiProvider = provider,
            activeProviderBadge = "${provider.shortBadge} • JARVIS"
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
        refreshDeviceTelemetry()

        val geminiConfig = _uiState.value.providerConfigs[AiProviderType.GOOGLE_GEMINI]
        val canUseGeminiLive = _uiState.value.selectedAiProvider == AiProviderType.GOOGLE_GEMINI &&
            geminiConfig?.hasValidKey() == true &&
            _uiState.value.apiMode != ApiMode.BACKUP_ONLY

        if (_uiState.value.isBackgroundServiceEnabled) {
            CaroLineVoiceService.startOrUpdate(
                appContext,
                "ক্যারোলাইন ২.৫ সক্রিয় — বাংলা ভয়েস কমান্ড শুনছে"
            )
        }

        if (canUseGeminiLive && !bengaliSpeechRecognizer.isAvailable()) {
            // Use Gemini Live Bidirectional WebSocket + AudioRecord 16kHz PCM when SpeechRecognizer is unavailable
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
                connect(geminiConfig?.apiKey.orEmpty())
            }
        } else {
            // Smart JARVIS Voice Pipeline: BengaliSpeechRecognizer -> Intent Detection -> 3-AI Brain / Android Tool -> Bengali TTS
            val engine = if (_uiState.value.providerConfigs.values.any { it.hasValidKey() }) {
                ActiveEngine.BACKUP_SECONDARY_AI
            } else {
                ActiveEngine.BENGALI_VOICE_ENGINE
            }

            if (bengaliSpeechRecognizer.isAvailable()) {
                audioStreamer.stop()
                _uiState.value = _uiState.value.copy(
                    activeEngine = engine,
                    liveState = LiveState.LISTENING,
                    isMicHardwareVerified = true,
                    currentEmotion = AvatarEmotion.LISTENING,
                    speechSnippet = CaroLineBrainEngine.DEFAULT_GREETING_BN
                )
                bengaliSpeechRecognizer.startContinuousListening()
                bengaliSpeechRecognizer.pauseForAiSpeech()
                brainEngine.speak(CaroLineBrainEngine.DEFAULT_GREETING_BN, providerBadge = "JARVIS CORE")
            } else {
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
                    speechSnippet = CaroLineBrainEngine.DEFAULT_GREETING_BN
                )
                brainEngine.speak(CaroLineBrainEngine.DEFAULT_GREETING_BN, providerBadge = "JARVIS CORE")
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

    private fun handleRecognizedBengaliSpeech(recognizedText: String) {
        val clean = recognizedText.trim()
        if (clean.isEmpty()) return

        Log.i(TAG, "Recognized Bengali voice command: $clean")
        _uiState.value = _uiState.value.copy(
            speechSnippet = "🗣️ \"$clean\"",
            errorMessage = null
        )

        bengaliSpeechRecognizer.pauseForAiSpeech()

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
                    activeLanguageName = _uiState.value.activeLanguage
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
                    speechSnippet = "ক্যারোলাইন আপনার কথা প্রসেস করছে..."
                )
                val reply = fallbackRestClient.apply {
                    activeLanguageName = _uiState.value.activeLanguage
                    isLanguageLockedToBengali = _uiState.value.isLanguageLocked
                }.generateBengaliFromWav(geminiKey, wavBytes)

                if (!reply.isNullOrBlank()) {
                    brainEngine.speak(reply, providerBadge = "GEMINI AUDIO")
                } else if (_uiState.value.activeEngine != ActiveEngine.PRIMARY_GEMINI_LIVE) {
                    brainEngine.processPrompt("হ্যালো ক্যারোলাইন")
                }
            } else if (_uiState.value.activeEngine != ActiveEngine.PRIMARY_GEMINI_LIVE) {
                brainEngine.processPrompt("হ্যালো ক্যারোলাইন")
            }
        }
    }

    private fun failoverToBackup(reason: String) {
        geminiLiveSession?.disconnect()
        geminiLiveSession = null
        audioStreamer.stop()

        val targetEngine = ActiveEngine.BENGALI_VOICE_ENGINE
        Log.w(TAG, "Switching to $targetEngine. Reason: $reason")

        if (bengaliSpeechRecognizer.isAvailable()) {
            _uiState.value = _uiState.value.copy(
                activeEngine = targetEngine,
                liveState = LiveState.LISTENING,
                isMicHardwareVerified = true,
                currentEmotion = AvatarEmotion.LISTENING,
                speechSnippet = CaroLineBrainEngine.DEFAULT_GREETING_BN
            )
            bengaliSpeechRecognizer.startContinuousListening()
            bengaliSpeechRecognizer.pauseForAiSpeech()
            brainEngine.speak(CaroLineBrainEngine.DEFAULT_GREETING_BN, providerBadge = "JARVIS CORE")
        } else {
            val micStarted = audioStreamer.start()
            _uiState.value = _uiState.value.copy(
                activeEngine = targetEngine,
                liveState = if (micStarted) LiveState.LISTENING else LiveState.ERROR,
                isMicHardwareVerified = micStarted,
                currentEmotion = if (micStarted) AvatarEmotion.LISTENING else AvatarEmotion.SURPRISED,
                speechSnippet = CaroLineBrainEngine.DEFAULT_GREETING_BN,
                errorMessage = if (!micStarted) "মাইক্রোফোন চালু করা যায়নি।" else null
            )
            if (micStarted) {
                brainEngine.speak(CaroLineBrainEngine.DEFAULT_GREETING_BN, providerBadge = "JARVIS CORE")
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
                "ক্যারোলাইন ২.৫ সক্রিয় — বাংলা ভয়েস কমান্ড শুনছে"
            )
        }
    }

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
     * User sends a voice command, text query, or taps a quick Bengali command chip
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
            bengaliSpeechRecognizer.pauseForAiSpeech()
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
                        activeLanguageName = _uiState.value.activeLanguage
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

    fun onAvatarTapped() {
        _uiState.value = _uiState.value.copy(
            manualEmotionOverride = AvatarEmotion.SMILING
        )

        val bengaliRemarks = listOf(
            "হ্যালো বস! আমি ক্যারোলাইন ২.৫ স্মার্ট জারভিস। বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?",
            "হ্যাঁ বস, জারভিস কোর সম্পূর্ণ সচল! যেকোনো অ্যাপ খুলতে বা প্রশ্ন করতে বলুন।",
            "বস, আপনার ফোনের ব্যাটারি, নেটওয়ার্ক এবং ভয়েস ইঞ্জিন সব একদম প্রস্তুত!",
            "আমি সবসময় আপনার কথা শুনছি বস! গান চালানো, হোয়াটসঅ্যাপ খোলা বা যেকোনো কিছু বলুন।"
        )
        val remark = bengaliRemarks.random()
        _uiState.value = _uiState.value.copy(speechSnippet = remark)
        bengaliSpeechRecognizer.pauseForAiSpeech()
        brainEngine.speak(remark, providerBadge = "JARVIS CORE")

        viewModelScope.launch {
            delay(3200)
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
                speechSnippet = CaroLineBrainEngine.DEFAULT_GREETING_BN
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
        bengaliSpeechRecognizer.stopListening()
        audioStreamer.stop()
        audioPlayer.release()
        geminiLiveSession?.disconnect()
        brainEngine.release()
    }
}

// Backward compatibility alias
typealias MahiViewModel = CaroLineViewModel
