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
 * Multi-Language SpeechRecognizer engine for CaroLine AI A3.2 – Ultimate Android Smart JARVIS.
 *
 * Understands:
 * - Hindi (`hi-IN`, default)
 * - Bengali (`bn-IN`, alternative)
 * - Hinglish & English (`en-IN` / `en-US`, optional)
 *
 * Battery & Restart Fix (Requirement N):
 * - Prevents duplicate recognizer instances or audio session conflicts.
 * - Uses exponential backoff and caps consecutive timeouts/no-match errors so the microphone
 *   never loops infinitely in the background or drains the battery.
 */
class BengaliSpeechRecognizer(
    private val context: Context,
    private val onPartialTranscript: (String) -> Unit,
    private val onFinalTranscript: (String) -> Unit,
    private val onRmsAmplitude: (Float, Boolean) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onErrorDiagnostic: (String) -> Unit,
    private val onWakeWordDetected: ((String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "CaroLineSpeechRec"
        private const val MAX_CONSECUTIVE_EMPTY_CYCLES = 5

        val DEFAULT_WAKE_WORDS = listOf(
            "caroline",
            "hey caroline",
            "hello caroline",
            "कैरोलाइन",
            "हे कैरोलाइन",
            "नमस्ते कैरोलाइन",
            "हेलो कैरोलाइन",
            "ক্যারোলাইন",
            "হ্যালো ক্যারোলাইন"
        )
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

    // Default to Hindi (India) for CaroLine A3.2; updated when user selects Bengali or English
    var preferredLanguageCode: String = "hi-IN"
    var isWakeWordEnabled: Boolean = true
    var configuredWakeWord: String = "Hey CaroLine"

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
        consecutiveErrorCount = 0
        mainScope.launch {
            initAndStartInternal()
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
        isPausedForPlayback = false
        consecutiveErrorCount = 0
        restartJob?.cancel()
        restartJob = mainScope.launch {
            delay(300)
            if (isContinuousModeEnabled && !isPausedForPlayback && !isCurrentlyListening) {
                initAndStartInternal()
            }
        }
    }

    fun stopListening() {
        isContinuousModeEnabled = false
        isPausedForPlayback = false
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

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                    setRecognitionListener(createListener())
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLanguageCode)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, preferredLanguageCode)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 950L)
            }

            speechRecognizer?.startListening(intent)
            isCurrentlyListening = true
            onListeningStateChanged(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            isCurrentlyListening = false
            onListeningStateChanged(false)
            onErrorDiagnostic("Speech recognition error: ${e.localizedMessage ?: "Unavailable"}")
        }
    }

    private fun scheduleRestart(baseDelayMs: Long = 450L) {
        if (!isContinuousModeEnabled || isPausedForPlayback) return
        consecutiveErrorCount++
        if (consecutiveErrorCount > MAX_CONSECUTIVE_EMPTY_CYCLES) {
            // Pause aggressive loop to save battery and prevent repeated system beeps
            isContinuousModeEnabled = false
            isCurrentlyListening = false
            onListeningStateChanged(false)
            Log.i(TAG, "Paused continuous listening after $consecutiveErrorCount silent cycles to save battery.")
            return
        }

        val backoffDelay = (baseDelayMs * consecutiveErrorCount).coerceIn(400L, 2800L)
        restartJob?.cancel()
        restartJob = mainScope.launch {
            delay(backoffDelay)
            if (isContinuousModeEnabled && !isPausedForPlayback && !isCurrentlyListening) {
                initAndStartInternal()
            }
        }
    }

    private fun checkWakeWord(transcript: String): Boolean {
        if (!isWakeWordEnabled) return false
        val lower = transcript.lowercase().trim()
        val customLower = configuredWakeWord.lowercase().trim()
        return (customLower.isNotEmpty() && lower.contains(customLower)) ||
            DEFAULT_WAKE_WORDS.any { lower.contains(it) }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isCurrentlyListening = true
                onListeningStateChanged(true)
            }

            override fun onBeginningOfSpeech() {
                consecutiveErrorCount = 0
                onRmsAmplitude(0.35f, true)
            }

            override fun onRmsChanged(rmsdB: Float) {
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
                        scheduleRestart(450L)
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        scheduleRestart(900L)
                    }
                    SpeechRecognizer.ERROR_CLIENT -> {
                        try {
                            speechRecognizer?.destroy()
                        } catch (_: Exception) {}
                        speechRecognizer = null
                        scheduleRestart(650L)
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        isContinuousModeEnabled = false
                        onListeningStateChanged(false)
                        onErrorDiagnostic("Microphone permission is required for voice commands.")
                    }
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        scheduleRestart(1400L)
                    }
                    else -> {
                        try {
                            speechRecognizer?.destroy()
                        } catch (_: Exception) {}
                        speechRecognizer = null
                        scheduleRestart(950L)
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
                    scheduleRestart(450L)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
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
