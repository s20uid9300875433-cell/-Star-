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
 * Continuous Bengali SpeechRecognizer engine for CaroLine AI (GREAT V2.1).
 * Recognizes spoken Bengali commands ("হ্যালো ক্যারোলাইন", "সময় কত?", etc.)
 * and wake words ("Hey CaroLine") with automatic continuous listening recovery.
 */
class BengaliSpeechRecognizer(
    private val context: Context,
    private val onPartialTranscript: (String) -> Unit,
    private val onFinalTranscript: (String) -> Unit,
    private val onRmsAmplitude: (Float, Boolean) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onErrorDiagnostic: (String) -> Unit
) {
    companion object {
        private const val TAG = "BengaliSpeechRecognizer"
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

    var preferredLanguageCode: String = "bn-BD"

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
            delay(280)
            if (isContinuousModeEnabled && !isPausedForPlayback) {
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
        if (!isContinuousModeEnabled || isPausedForPlayback) return

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
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            }

            speechRecognizer?.startListening(intent)
            isCurrentlyListening = true
            onListeningStateChanged(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            isCurrentlyListening = false
            onListeningStateChanged(false)
            onErrorDiagnostic("ভয়েস রিকগনিশন শুরু করতে সমস্যা হয়েছে: ${e.localizedMessage ?: "Unknown"}")
        }
    }

    private fun scheduleRestart(delayMs: Long = 350L) {
        if (!isContinuousModeEnabled || isPausedForPlayback) return
        restartJob?.cancel()
        restartJob = mainScope.launch {
            delay(delayMs)
            if (isContinuousModeEnabled && !isPausedForPlayback) {
                initAndStartInternal()
            }
        }
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
                // Convert rmsdB (-2..10 dB typical) to 0.0..1.0 normalized amplitude
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
                        // Normal silence in continuous mode — immediately restart listening
                        scheduleRestart(250L)
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        scheduleRestart(600L)
                    }
                    SpeechRecognizer.ERROR_CLIENT -> {
                        // Recreate recognizer cleanly and restart
                        try {
                            speechRecognizer?.destroy()
                        } catch (_: Exception) {}
                        speechRecognizer = null
                        scheduleRestart(450L)
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        onListeningStateChanged(false)
                        onErrorDiagnostic("মাইক্রোফোন অনুমতি প্রদান করা হয়নি (Microphone permission required)।")
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
                        scheduleRestart(800L)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                isCurrentlyListening = false
                onRmsAmplitude(0f, false)

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val bestText = matches?.firstOrNull()?.trim().orEmpty()

                if (bestText.isNotEmpty()) {
                    Log.d(TAG, "Recognized Bengali voice command: $bestText")
                    onFinalTranscript(bestText)
                } else {
                    scheduleRestart(250L)
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
