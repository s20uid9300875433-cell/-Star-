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
import com.example.live.CaroLineBrainEngine
import com.example.live.GeminiLiveSession
import com.example.live.LiveSessionListener
import com.example.live.LiveState
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
    LOCAL_SUMAN_BRAIN
}

enum class AvatarMode {
    PROCEDURAL_ANIMATED_FACE,
    ANIME_ARTWORK
}

data class CaroLineUiState(
    val liveState: LiveState = LiveState.DISCONNECTED,
    val isMicMuted: Boolean = false,
    val isHandsFreeMode: Boolean = true,
    val speechSnippet: String = "Hello Boss! Main CaroLine hoon. SUMAN Brain online hai. Bataiye Boss, aaj kya karna hai?",
    val activeToolEvent: ToolExecutionEvent? = null,
    val toolHistory: List<ToolExecutionEvent> = emptyList(),
    // Dual API configuration
    val primaryApiKey: String = "",
    val backupApiKey: String = "",
    val apiMode: ApiMode = ApiMode.AUTO_FAILOVER,
    val activeEngine: ActiveEngine = ActiveEngine.LOCAL_SUMAN_BRAIN,
    // Voice & Personality
    val selectedVoice: String = "Aoede",
    val developerName: String = "Boss",
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
        private const val KEY_DEVELOPER_NAME = "developer_display_name"
    }

    private val sharedPrefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val toolExecutor = ToolExecutor(application.applicationContext)

    private val audioPlayer = AudioPlayer(
        onPlaybackStateChanged = { isPlaying ->
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
                // Hands-free continuous auto-listen
                if (_uiState.value.isHandsFreeMode && _uiState.value.liveState != LiveState.DISCONNECTED) {
                    audioStreamer.start()
                }
            }
        }
    )

    private val audioStreamer = AudioStreamer(
        onAudioChunk = { chunk ->
            if (geminiLiveSession != null && _uiState.value.activeEngine == ActiveEngine.PRIMARY_GEMINI_LIVE) {
                geminiLiveSession?.sendAudioChunk(chunk)
            }
        }
    )

    private var geminiLiveSession: GeminiLiveSession? = null

    // SUMAN AI Brain engine for Hindi/English/Hinglish dialog & tools
    private val brainEngine = CaroLineBrainEngine(
        context = application.applicationContext,
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
            if (_uiState.value.isHandsFreeMode && _uiState.value.liveState != LiveState.DISCONNECTED) {
                audioStreamer.start()
            }
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
        // Load Developer Name (Permanently saved as "Boss")
        var savedDevName = sharedPrefs.getString(KEY_DEVELOPER_NAME, "Boss") ?: "Boss"
        if (savedDevName.isBlank()) {
            savedDevName = "Boss"
            sharedPrefs.edit().putString(KEY_DEVELOPER_NAME, "Boss").apply()
        }

        // Load initial Dual Keys
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

        _uiState.value = _uiState.value.copy(
            developerName = savedDevName,
            primaryApiKey = initialPrimaryKey,
            backupApiKey = savedBackupKey,
            apiMode = savedMode,
            selectedVoice = savedVoice,
            isHandsFreeMode = savedHandsFree,
            avatarStyle = savedStyle,
            customAvatarUri = customUri
        )

        // Listen for tool events
        viewModelScope.launch {
            toolExecutor.toolEvents.collect { event ->
                val updatedList = listOf(event) + _uiState.value.toolHistory.take(19)
                _uiState.value = _uiState.value.copy(
                    activeToolEvent = event,
                    toolHistory = updatedList
                )
            }
        }

        // Barge-in interruption monitor: when CaroLine is speaking, if user speaks with sufficient volume, interrupt!
        bargeInMonitorJob = viewModelScope.launch {
            inputAmplitude.collect { amp ->
                if (_uiState.value.liveState == LiveState.SPEAKING && amp > 0.18f) {
                    handleUserInterruption()
                }
            }
        }
    }

    /**
     * Main CALL Button handler.
     * Starts or stops live conversation session.
     */
    fun triggerCallSession() {
        if (_uiState.value.liveState == LiveState.DISCONNECTED || _uiState.value.liveState == LiveState.ERROR) {
            startSession()
        } else {
            stopSession()
        }
    }

    fun startSession() {
        val primaryKey = _uiState.value.primaryApiKey
        val backupKey = _uiState.value.backupApiKey
        val mode = _uiState.value.apiMode

        val canUsePrimary = mode != ApiMode.BACKUP_ONLY && primaryKey.isNotBlank() && primaryKey != "MY_GEMINI_API_KEY"
        val canUseBackup = mode != ApiMode.PRIMARY_ONLY && backupKey.isNotBlank()

        if (canUsePrimary) {
            // Primary: Gemini Live Bidirectional WebSocket Mode
            _uiState.value = _uiState.value.copy(
                activeEngine = ActiveEngine.PRIMARY_GEMINI_LIVE,
                liveState = LiveState.CONNECTING,
                currentEmotion = AvatarEmotion.THINKING,
                speechSnippet = "Connecting to Gemini Live 24kHz for Boss..."
            )

            geminiLiveSession = GeminiLiveSession(toolExecutor, this).apply {
                selectedVoice = _uiState.value.selectedVoice
                connect(primaryKey)
            }
            audioStreamer.start()
        } else if (canUseBackup) {
            // Backup: Secondary AI API Mode
            _uiState.value = _uiState.value.copy(
                activeEngine = ActiveEngine.BACKUP_SECONDARY_AI,
                liveState = LiveState.LISTENING,
                currentEmotion = AvatarEmotion.LISTENING,
                speechSnippet = "Hey Boss! Connected via Backup AI Engine. SUMAN brain ready!"
            )
            audioStreamer.start()
            brainEngine.speak("Ji Boss! Backup AI connect ho gaya hai. Aapka hukum mere sir aankhon par!")
        } else {
            // Local SUMAN Brain Voice Engine
            _uiState.value = _uiState.value.copy(
                activeEngine = ActiveEngine.LOCAL_SUMAN_BRAIN,
                liveState = LiveState.LISTENING,
                currentEmotion = AvatarEmotion.LISTENING,
                speechSnippet = "Arey Boss! CaroLine hazir hai! Tap prompt ya 'Hey CaroLine' boliye~"
            )
            audioStreamer.start()
            brainEngine.speak("Hello Boss! Main CaroLine hoon. SUMAN Brain online hai. Bataiye Boss, aaj kya karna hai?")
        }
    }

    /**
     * Gracefully fails over to Backup API when Primary encounters an error
     */
    private fun failoverToBackup(reason: String) {
        geminiLiveSession?.disconnect()
        geminiLiveSession = null

        val backupKey = _uiState.value.backupApiKey
        if (_uiState.value.apiMode == ApiMode.AUTO_FAILOVER && backupKey.isNotBlank()) {
            Log.w(TAG, "Failing over to Backup API. Reason: $reason")
            _uiState.value = _uiState.value.copy(
                activeEngine = ActiveEngine.BACKUP_SECONDARY_AI,
                liveState = LiveState.LISTENING,
                currentEmotion = AvatarEmotion.LISTENING,
                speechSnippet = "⚡ Primary Live switched to Backup AI API for Boss!"
            )
            brainEngine.speak("Boss, Primary Live connection me issue tha, toh maine automatically Backup AI par switch kar liya hai! I'm listening Boss.")
        } else {
            // Fallback to local SUMAN brain
            _uiState.value = _uiState.value.copy(
                activeEngine = ActiveEngine.LOCAL_SUMAN_BRAIN,
                liveState = LiveState.LISTENING,
                currentEmotion = AvatarEmotion.LISTENING,
                speechSnippet = "Active: SUMAN Brain Mode. Boliye Boss!"
            )
            brainEngine.speak("Ji Boss! Main SUMAN Brain mode se fully active hu. Bataiye Boss, kya hukum hai?")
        }
    }

    fun stopSession() {
        audioStreamer.stop()
        audioPlayer.flushAndInterrupt()
        geminiLiveSession?.disconnect()
        geminiLiveSession = null
        brainEngine.stop()

        _uiState.value = _uiState.value.copy(
            liveState = LiveState.DISCONNECTED,
            activeEngine = ActiveEngine.LOCAL_SUMAN_BRAIN,
            currentEmotion = AvatarEmotion.LISTENING,
            speechSnippet = "Call ended, Boss. Tap CALL whenever you want CaroLine back~"
        )
    }

    fun toggleMute() {
        audioStreamer.toggleMute()
        _uiState.value = _uiState.value.copy(isMicMuted = audioStreamer.isMuted.value)
    }

    fun toggleHandsFree() {
        val newSetting = !_uiState.value.isHandsFreeMode
        sharedPrefs.edit().putBoolean(KEY_HANDS_FREE, newSetting).apply()
        _uiState.value = _uiState.value.copy(isHandsFreeMode = newSetting)
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
     * User sends a prompt or taps a quick chip
     */
    fun onUserPrompt(promptText: String) {
        if (_uiState.value.liveState == LiveState.DISCONNECTED) {
            startSession()
        }

        // Barge-in check: stop any playing speech
        if (_uiState.value.liveState == LiveState.SPEAKING) {
            handleUserInterruption()
        }

        _uiState.value = _uiState.value.copy(speechSnippet = "Boss: \"$promptText\"")

        if (_uiState.value.activeEngine == ActiveEngine.PRIMARY_GEMINI_LIVE && geminiLiveSession != null) {
            geminiLiveSession?.sendTextPrompt(promptText)
        } else {
            brainEngine.processPrompt(promptText)
        }
    }

    /**
     * Barge-in interruption handler
     */
    fun handleUserInterruption() {
        Log.d(TAG, "Barge-in triggered by Boss")
        audioPlayer.flushAndInterrupt()
        brainEngine.stop()
        _uiState.value = _uiState.value.copy(
            liveState = LiveState.LISTENING,
            currentEmotion = AvatarEmotion.LISTENING,
            speechSnippet = "Ji Boss, sun rahi hu! (Barge-in)"
        )
    }

    /**
     * Tapping CaroLine's avatar generates affectionate, witty remarks for Boss and smiles!
     */
    fun onAvatarTapped() {
        _uiState.value = _uiState.value.copy(
            manualEmotionOverride = AvatarEmotion.SMILING
        )

        val bossRemarks = listOf(
            "Hello Boss! Main CaroLine hoon. SUMAN Brain online hai. Bataiye Boss, aaj kya karna hai?",
            "Boss! Bar-bar poke karoge toh main aapke device ka wallpaper change kar dungi!",
            "Arey Boss, bataiye na kya hukum hai? SUMAN AI brain aapke order ka wait kar raha hai!",
            "Boss, aapke bina yeh application kitna adhura lagta hai! Always happy to assist you, Boss~",
            "CaroLine reporting for duty, Boss! What exciting project are we conquering today?"
        )
        val remark = bossRemarks.random()
        _uiState.value = _uiState.value.copy(speechSnippet = remark)
        brainEngine.speak(remark)

        // Reset expression override after 3.2 seconds
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

        val emotion = when (state) {
            LiveState.SPEAKING -> AvatarEmotion.SPEAKING
            LiveState.LISTENING -> AvatarEmotion.LISTENING
            LiveState.CONNECTING, LiveState.RECONNECTING, LiveState.THINKING -> AvatarEmotion.THINKING
            LiveState.ERROR -> AvatarEmotion.SURPRISED
            LiveState.DISCONNECTED -> AvatarEmotion.LISTENING
        }
        _uiState.value = _uiState.value.copy(liveState = state, currentEmotion = emotion)
        if (state == LiveState.LISTENING) {
            _uiState.value = _uiState.value.copy(speechSnippet = "Ji Boss, I'm listening... bolo!")
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
            speechSnippet = "Listening Boss... aapne rok diya! (Barge-in)"
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
        audioStreamer.stop()
        audioPlayer.release()
        geminiLiveSession?.disconnect()
        brainEngine.release()
    }
}

// Backward compatibility alias
typealias MahiViewModel = CaroLineViewModel
