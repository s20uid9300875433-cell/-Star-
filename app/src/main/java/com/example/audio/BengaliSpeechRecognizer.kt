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
 * Continuous Hindi + Hinglish + English SpeechRecognizer engine for CaroLine AI 2.9 – Ultimate Smart JARVIS.
 * Default language: Hindi (hi-IN).
 * Understands Hindi ("नमस्ते कैरोलाइन", "अरिजीत सिंह का गाना चलाओ", "व्हाट्सएप खोलो"),
 * Hinglish ("Arijit Singh ka gaana chalao", "WhatsApp kholo"), and English ("Open WhatsApp", "Play music").
 * Supports configurable Wake Word detection ("CaroLine", "Hey CaroLine", "कैरोलाइन") and prevents duplicate mic sessions.
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
        val DEFAULT_WAKE_WORDS = listOf(
            "caroline",
            "hey caroline",
            "hello caroline",
            "कैरोलाइन",
            "हे कैरोलाइन",
            "नमस्ते कैरोलाइन",
            "हेलो कैरोलाइन"
        )
    }

    private val mainScope = CoroutineScope(Dispatchers.Main)
    private var speechRecognizer: SpeechRecognizer? = null
    private var restartJob: Job? = null

    @Volatile
    private var isContinuousModeEnabled = false

    @Volatile
    private var isPausedForPlayback = false

    @Volatile
    private var isCurrentlyListening = false

    // Default to Hindi (India) for CaroLine 2.9
    var preferredLanguageCode: String = "hi-IN"
    var isWakeWordEnabled: Boolean = true
    var configuredWakeWord: String = "Hey CaroLine"

    fun isAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (e: Exception) {
            false
        }
    }

    fun startContinuousListening() {
        isContinuousModeEnabled = true
        isPausedForPlayback = false
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
        restartJob?.cancel()
        restartJob = mainScope.launch {
            delay(250)
            if (isContinuousModeEnabled && !isPausedForPlayback && !isCurrentlyListening) {
                initAndStartInternal()
            }
        }
    }

    fun stopListening() {
        isContinuousModeEnabled = false
        isPausedForPlayback = false
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
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1100L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            }

            speechRecognizer?.startListening(intent)
            isCurrentlyListening = true
            onListeningStateChanged(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            isCurrentlyListening = false
            onListeningStateChanged(false)
            onErrorDiagnostic("वॉइस रिकग्निशन शुरू करने में समस्या हुई: ${e.localizedMessage ?: "Unknown"}")
        }
    }

    private fun scheduleRestart(delayMs: Long = 320L) {
        if (!isContinuousModeEnabled || isPausedForPlayback) return
        restartJob?.cancel()
        restartJob = mainScope.launch {
            delay(delayMs)
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
                        scheduleRestart(240L)
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        scheduleRestart(550L)
                    }
                    SpeechRecognizer.ERROR_CLIENT -> {
                        try {
                            speechRecognizer?.destroy()
                        } catch (_: Exception) {}
                        speechRecognizer = null
                        scheduleRestart(400L)
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        onListeningStateChanged(false)
                        onErrorDiagnostic("माइक्रोफ़ोन अनुमति आवश्यक है (Microphone permission required)।")
                    }
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        scheduleRestart(1100L)
                    }
                    else -> {
                        try {
                            speechRecognizer?.destroy()
                        } catch (_: Exception) {}
                        speechRecognizer = null
                        scheduleRestart(750L)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                isCurrentlyListening = false
                onRmsAmplitude(0f, false)

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val bestText = matches?.firstOrNull()?.trim().orEmpty()

                if (bestText.isNotEmpty()) {
                    Log.d(TAG, "Recognized Hindi/Hinglish voice command: $bestText")
                    if (checkWakeWord(bestText)) {
                        onWakeWordDetected?.invoke(bestText)
                    }
                    onFinalTranscript(bestText)
                } else {
                    scheduleRestart(240L)
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
