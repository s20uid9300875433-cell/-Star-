package com.example.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Listening Power Mode for CaroLine AI A3.2 Background Voice Assistant.
 *
 * - [BATTERY_SAVING]: Default mode ("लगातार सुनने के बजाय battery-saving listening mode").
 *   Uses smart duty-cycled listening bursts with adaptive rest intervals between cycles,
 *   longer rest windows when the screen is off under supported conditions, and automatic
 *   eco-standby after consecutive silent cycles.
 * - [CONTINUOUS_ACTIVE]: Fast back-to-back listening when plugged in or actively conversing.
 * - [PUSH_TO_TALK_ECO]: Listens for a single command per manual/notification trigger, then rests.
 */
enum class ListeningPowerMode(
    val id: String,
    val titleEn: String,
    val subtitleEn: String,
    val baseRestIntervalMs: Long,
    val screenOffRestIntervalMs: Long,
    val maxSilentCyclesScreenOn: Int,
    val maxSilentCyclesScreenOff: Int
) {
    BATTERY_SAVING(
        id = "battery_saving",
        titleEn = "Battery-Saving Mode (Recommended)",
        subtitleEn = "Smart duty-cycled wake-word listening instead of continuous mic drain",
        baseRestIntervalMs = 1500L,
        screenOffRestIntervalMs = 3200L,
        maxSilentCyclesScreenOn = 6,
        maxSilentCyclesScreenOff = 4
    ),
    CONTINUOUS_ACTIVE(
        id = "continuous_active",
        titleEn = "Continuous Active Mode",
        subtitleEn = "Fast back-to-back voice turn-taking while screen is active",
        baseRestIntervalMs = 450L,
        screenOffRestIntervalMs = 2000L,
        maxSilentCyclesScreenOn = 8,
        maxSilentCyclesScreenOff = 4
    ),
    PUSH_TO_TALK_ECO(
        id = "push_to_talk_eco",
        titleEn = "Single-Burst Eco Mode",
        subtitleEn = "Listens once per tap or lock-screen notification trigger",
        baseRestIntervalMs = 2500L,
        screenOffRestIntervalMs = 4500L,
        maxSilentCyclesScreenOn = 1,
        maxSilentCyclesScreenOff = 1
    );

    companion object {
        fun fromId(id: String?): ListeningPowerMode {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BATTERY_SAVING
        }
    }
}

/**
 * Multi-Language SpeechRecognizer engine for CaroLine AI A3.2 – Ultimate Android Smart JARVIS.
 *
 * Implements:
 * - Background Voice Assistant with Wake Word: "Hey CaroLine"
 * - Screen-off operation under supported conditions (Foreground Microphone Service + Battery check)
 * - Battery-saving listening mode instead of aggressive continuous listening
 */
class BengaliSpeechRecognizer(
    private val context: Context,
    private val onPartialTranscript: (String) -> Unit,
    private val onFinalTranscript: (String) -> Unit,
    private val onRmsAmplitude: (Float, Boolean) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onErrorDiagnostic: (String) -> Unit,
    private val onWakeWordDetected: ((String) -> Unit)? = null,
    private val onEcoStandbyEntered: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "CaroLineSpeechRec"
        const val DEFAULT_WAKE_WORD_PHRASE = "Hey CaroLine"

        val DEFAULT_WAKE_WORDS = listOf(
            "hey caroline",
            "hi caroline",
            "hello caroline",
            "ok caroline",
            "okay caroline",
            "caroline",
            "हे कैरोलाइन",
            "हाय कैरोलाइन",
            "हेलो कैरोलाइन",
            "नमस्ते कैरोलाइन",
            "कैरोलाइन",
            "হ্যালো ক্যারোলাইন",
            "হে ক্যারোলাইন",
            "ক্যারোলাইন"
        )

        /**
         * Checks whether [transcript] contains the configured wake word ("Hey CaroLine")
         * or any supported Hindi/Bengali/English wake-word variation.
         */
        fun matchesWakeWord(
            transcript: String,
            configuredPhrase: String = DEFAULT_WAKE_WORD_PHRASE
        ): Boolean {
            val normalized = transcript.lowercase().trim()
            if (normalized.isEmpty()) return false
            val customLower = configuredPhrase.lowercase().trim()
            if (customLower.isNotEmpty() && normalized.contains(customLower)) {
                return true
            }
            return DEFAULT_WAKE_WORDS.any { normalized.contains(it) }
        }

        /**
         * Strips the wake word ("Hey CaroLine", "हे कैरोलाइन", etc.) from the beginning of
         * [transcript] and returns the remaining command text. If the user only spoke the wake
         * word itself, returns an empty string so the assistant can open an active listening window.
         */
        fun extractCommandAfterWakeWord(
            transcript: String,
            configuredPhrase: String = DEFAULT_WAKE_WORD_PHRASE
        ): String {
            var working = transcript.trim()
            if (working.isEmpty()) return ""

            val candidates = buildList {
                if (configuredPhrase.isNotBlank()) add(configuredPhrase.trim())
                addAll(DEFAULT_WAKE_WORDS.sortedByDescending { it.length })
            }.distinct()

            for (wake in candidates) {
                val regex = Regex(
                    pattern = "^[\\s,!.?:;\"'“”‘’]*${Regex.escape(wake)}[\\s,!.?:;\"'“”‘’]*",
                    options = setOf(RegexOption.IGNORE_CASE)
                )
                if (regex.containsMatchIn(working)) {
                    working = working.replaceFirst(regex, "").trim()
                    break
                }
            }
            return working.trim()
        }

        /**
         * Computes the adaptive battery-saving rest delay (in milliseconds) before the next
         * speech recognition listening window.
         */
        fun computeBatterySavingDelayMs(
            consecutiveSilentCycles: Int,
            isScreenInteractive: Boolean,
            isCharging: Boolean,
            batteryLevel: Int,
            mode: ListeningPowerMode = ListeningPowerMode.BATTERY_SAVING
        ): Long {
            val safeCycles = consecutiveSilentCycles.coerceAtLeast(1)
            val baseInterval = if (isScreenInteractive) {
                mode.baseRestIntervalMs
            } else {
                mode.screenOffRestIntervalMs
            }

            // Increase rest window progressively on silent cycles to save battery
            val progressiveMultiplier = when {
                mode == ListeningPowerMode.BATTERY_SAVING && !isScreenInteractive -> 1.0 + (safeCycles * 0.65)
                mode == ListeningPowerMode.BATTERY_SAVING -> 1.0 + (safeCycles * 0.45)
                else -> 1.0 + (safeCycles * 0.25)
            }

            // Extra battery protection when battery is under 30% and unplugged
            val lowBatteryFactor = if (!isCharging && batteryLevel in 1..29) 1.35 else 1.0

            val rawDelay = (baseInterval * progressiveMultiplier * lowBatteryFactor).toLong()
            val maxDelay = if (isScreenInteractive) 5500L else 9000L
            return rawDelay.coerceIn(400L, maxDelay)
        }

        /**
         * Determines whether screen-off voice listening is supported under current Android
         * and device battery conditions ("स्क्रीन बंद होने पर भी समर्थित परिस्थितियों में काम").
         */
        fun isScreenOffListeningSupported(
            isForegroundServiceRunning: Boolean,
            isScreenOffSupportEnabled: Boolean,
            hasMicPermission: Boolean,
            batteryLevel: Int,
            isCharging: Boolean
        ): Boolean {
            if (!isForegroundServiceRunning || !isScreenOffSupportEnabled || !hasMicPermission) {
                return false
            }
            // Supported when charging or when battery is at least 15%
            return isCharging || batteryLevel >= 15
        }
    }

    private val mainScope = CoroutineScope(Dispatchers.Main)
    private var speechRecognizer: SpeechRecognizer? = null
    private var restartJob: Job? = null
    private var consecutiveErrorCount: Int = 0

    @Volatile
    private var isContinuousModeEnabled = false

    @Volatile
    private var isPausedForPlayback = false

    @Volatile
    private var isCurrentlyListening = false

    @Volatile
    var isInEcoStandby: Boolean = false
        private set

    // Default to Hindi (India) for CaroLine A3.2; updated when user selects Bengali or English
    var preferredLanguageCode: String = "hi-IN"
    var isWakeWordEnabled: Boolean = true
    var configuredWakeWord: String = DEFAULT_WAKE_WORD_PHRASE

    // Battery-saving & screen-off awareness properties
    var listeningPowerMode: ListeningPowerMode = ListeningPowerMode.BATTERY_SAVING
    var isScreenInteractive: Boolean = true
    var isScreenOffSupportEnabled: Boolean = true
    var isForegroundServiceActive: Boolean = false
    var batteryLevel: Int = 100
    var isCharging: Boolean = false

    fun isAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Exception) {
            false
        }
    }

    fun startContinuousListening() {
        isContinuousModeEnabled = true
        isPausedForPlayback = false
        isInEcoStandby = false
        consecutiveErrorCount = 0
        restartJob?.cancel()
        mainScope.launch {
            initAndStartInternal()
        }
    }

    /**
     * Wakes the recognizer from Battery-Saving Eco Standby (e.g., when screen turns on,
     * user taps notification "Hey CaroLine", or user taps the mic button).
     */
    fun wakeFromEcoStandby() {
        if (!isContinuousModeEnabled) {
            isContinuousModeEnabled = true
        }
        isPausedForPlayback = false
        isInEcoStandby = false
        consecutiveErrorCount = 0
        restartJob?.cancel()
        mainScope.launch {
            if (!isCurrentlyListening) {
                initAndStartInternal()
            }
        }
    }

    fun pauseForAiSpeech() {
        isPausedForPlayback = true
        restartJob?.cancel()
        mainScope.launch {
            try {
                speechRecognizer?.cancel()
                isCurrentlyListening = false
                onRmsAmplitude(0f, false)
            } catch (e: Exception) {
                Log.w(TAG, "Error pausing speech recognizer", e)
            }
        }
    }

    fun resumeAfterAiSpeech() {
        if (!isContinuousModeEnabled) return
        if (!isScreenInteractive && !canListenWhileScreenOff()) {
            enterEcoStandby("Screen is off. Tap notification or turn screen on to resume.")
            return
        }
        isPausedForPlayback = false
        isInEcoStandby = false
        consecutiveErrorCount = 0
        restartJob?.cancel()
        val resumeDelay = if (listeningPowerMode == ListeningPowerMode.BATTERY_SAVING) {
            if (isScreenInteractive) 650L else 1500L
        } else {
            350L
        }
        restartJob = mainScope.launch {
            delay(resumeDelay)
            if (isContinuousModeEnabled && !isPausedForPlayback && !isCurrentlyListening) {
                initAndStartInternal()
            }
        }
    }

    fun onScreenInteractiveChanged(interactive: Boolean) {
        isScreenInteractive = interactive
        if (!interactive) {
            // Screen turned OFF: check if screen-off operation is supported
            if (!canListenWhileScreenOff()) {
                enterEcoStandby("Screen-off standby active to save battery.")
            } else {
                // Keep listening in low-duty battery-saving mode!
                Log.i(TAG, "Screen off: switching to supported low-power wake-word duty cycle.")
            }
        } else {
            // Screen turned ON: if we were in eco-standby while session was active, gently resume
            if (isInEcoStandby && isContinuousModeEnabled && !isPausedForPlayback) {
                wakeFromEcoStandby()
            }
        }
    }

    private fun canListenWhileScreenOff(): Boolean {
        return isScreenOffListeningSupported(
            isForegroundServiceRunning = isForegroundServiceActive,
            isScreenOffSupportEnabled = isScreenOffSupportEnabled,
            hasMicPermission = true,
            batteryLevel = batteryLevel,
            isCharging = isCharging
        )
    }

    private fun enterEcoStandby(reason: String) {
        isInEcoStandby = true
        isCurrentlyListening = false
        restartJob?.cancel()
        mainScope.launch {
            try {
                speechRecognizer?.cancel()
                onRmsAmplitude(0f, false)
                onListeningStateChanged(false)
                onEcoStandbyEntered?.invoke(reason)
            } catch (e: Exception) {
                Log.w(TAG, "Error entering eco standby", e)
            }
        }
    }

    fun stopListening() {
        isContinuousModeEnabled = false
        isPausedForPlayback = false
        isInEcoStandby = false
        consecutiveErrorCount = 0
        restartJob?.cancel()
        mainScope.launch {
            try {
                isCurrentlyListening = false
                onRmsAmplitude(0f, false)
                onListeningStateChanged(false)
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping speech recognizer", e)
            }
        }
    }

    private fun initAndStartInternal() {
        if (!isContinuousModeEnabled || isPausedForPlayback || isCurrentlyListening) return
        if (!isScreenInteractive && !canListenWhileScreenOff()) {
            enterEcoStandby("Screen off standby")
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                    setRecognitionListener(createListener())
                }
            }

            // In Battery-Saving Mode, use shorter silence windows so the mic doesn't stay open unnecessarily
            val completeSilenceMs = when {
                !isScreenInteractive -> 950L
                listeningPowerMode == ListeningPowerMode.BATTERY_SAVING -> 1050L
                else -> 1250L
            }
            val possiblyCompleteSilenceMs = when {
                !isScreenInteractive -> 750L
                listeningPowerMode == ListeningPowerMode.BATTERY_SAVING -> 850L
                else -> 950L
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLanguageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, preferredLanguageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, isScreenInteractive)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, completeSilenceMs)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, possiblyCompleteSilenceMs)
            }

            speechRecognizer?.startListening(intent)
            isCurrentlyListening = true
            isInEcoStandby = false
            onListeningStateChanged(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            isCurrentlyListening = false
            onListeningStateChanged(false)
            onErrorDiagnostic("Speech recognition error: ${e.localizedMessage ?: "Unavailable"}")
        }
    }

    private fun scheduleRestart(extraPenaltyMs: Long = 0L) {
        if (!isContinuousModeEnabled || isPausedForPlayback) return
        if (!isScreenInteractive && !canListenWhileScreenOff()) {
            enterEcoStandby("Battery-saving screen-off standby")
            return
        }

        consecutiveErrorCount++
        val maxAllowedSilentCycles = if (isScreenInteractive) {
            listeningPowerMode.maxSilentCyclesScreenOn
        } else {
            listeningPowerMode.maxSilentCyclesScreenOff
        }

        if (consecutiveErrorCount > maxAllowedSilentCycles) {
            // Enter Eco Standby instead of looping forever and draining battery
            enterEcoStandby(
                "Battery-Saving Eco Standby active after $consecutiveErrorCount quiet cycles. Say \"$configuredWakeWord\" after tapping Resume or turning screen on."
            )
            Log.i(TAG, "Entered Battery-Saving Eco Standby after $consecutiveErrorCount silent cycles.")
            return
        }

        val adaptiveDelay = computeBatterySavingDelayMs(
            consecutiveSilentCycles = consecutiveErrorCount,
            isScreenInteractive = isScreenInteractive,
            isCharging = isCharging,
            batteryLevel = batteryLevel,
            mode = listeningPowerMode
        ) + extraPenaltyMs

        restartJob?.cancel()
        restartJob = mainScope.launch {
            delay(adaptiveDelay)
            if (isContinuousModeEnabled && !isPausedForPlayback && !isCurrentlyListening) {
                initAndStartInternal()
            }
        }
    }

    fun checkWakeWord(transcript: String): Boolean {
        if (!isWakeWordEnabled) return false
        return matchesWakeWord(transcript, configuredWakeWord)
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isCurrentlyListening = true
                isInEcoStandby = false
                onListeningStateChanged(true)
            }

            override fun onBeginningOfSpeech() {
                consecutiveErrorCount = 0
                onRmsAmplitude(0.35f, true)
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (!isScreenInteractive) return
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                onRmsAmplitude(normalized, normalized > 0.22f)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isCurrentlyListening = false
                onRmsAmplitude(0f, false)
            }

            override fun onError(error: Int) {
                isCurrentlyListening = false
                onRmsAmplitude(0f, false)

                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        scheduleRestart(0L)
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        scheduleRestart(600L)
                    }
                    SpeechRecognizer.ERROR_CLIENT -> {
                        try {
                            speechRecognizer?.destroy()
                        } catch (_: Exception) {}
                        speechRecognizer = null
                        scheduleRestart(400L)
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        isContinuousModeEnabled = false
                        onListeningStateChanged(false)
                        onErrorDiagnostic("Microphone permission is required for voice commands.")
                    }
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        scheduleRestart(1200L)
                    }
                    else -> {
                        try {
                            speechRecognizer?.destroy()
                        } catch (_: Exception) {}
                        speechRecognizer = null
                        scheduleRestart(700L)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                isCurrentlyListening = false
                consecutiveErrorCount = 0
                onRmsAmplitude(0f, false)

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val bestText = matches?.firstOrNull()?.trim().orEmpty()

                if (bestText.isNotEmpty()) {
                    Log.d(TAG, "Recognized voice command ($preferredLanguageCode): $bestText")
                    if (checkWakeWord(bestText)) {
                        onWakeWordDetected?.invoke(bestText)
                    }
                    onFinalTranscript(bestText)
                } else {
                    scheduleRestart(0L)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isScreenInteractive) return
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim().orEmpty()
                if (partial.isNotEmpty()) {
                    onPartialTranscript(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
}

typealias HindiSpeechRecognizer = BengaliSpeechRecognizer
