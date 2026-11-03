package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Streams raw PCM 16-bit 16kHz mono audio from the device microphone
 * for the Gemini Live Multimodal WebSocket API.
 * Features:
 * - Acoustic Echo Canceler (AEC)
 * - Noise Suppressor (NS)
 * - Real-time Voice Activity Detection (VAD)
 * - Automatic microphone recovery & error resilient recording loop
 */
class AudioStreamer(
    private val onAudioChunk: (ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "AudioStreamer"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
        private const val VAD_THRESHOLD = 0.045f
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _inputAmplitude = MutableStateFlow(0f)
    val inputAmplitude = _inputAmplitude.asStateFlow()

    private val _isVoiceDetected = MutableStateFlow(false)
    val isVoiceDetected = _isVoiceDetected.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted = _isMuted.asStateFlow()

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    fun start() {
        if (_isRecording.value) return

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size calculation: $minBufferSize")
            return
        }

        val bufferSize = (minBufferSize * BUFFER_SIZE_FACTOR).coerceAtLeast(2048)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to MIC if VOICE_COMMUNICATION fails on certain hardware
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            val record = audioRecord
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord could not be initialized")
                return
            }

            // Enable Hardware Acoustic Echo Canceler if supported
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)?.apply {
                        enabled = true
                        Log.d(TAG, "Hardware AcousticEchoCanceler enabled")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not enable AcousticEchoCanceler", e)
            }

            // Enable Hardware Noise Suppressor if supported
            try {
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.apply {
                        enabled = true
                        Log.d(TAG, "Hardware NoiseSuppressor enabled")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not enable NoiseSuppressor", e)
            }

            record.startRecording()
            _isRecording.value = true

            recordingJob = scope.launch {
                val readChunkSize = 2048
                val audioBuffer = ByteArray(readChunkSize)
                var consecutiveErrors = 0

                while (isActive && _isRecording.value) {
                    val bytesRead = record.read(audioBuffer, 0, audioBuffer.size)
                    if (bytesRead > 0) {
                        consecutiveErrors = 0
                        val amplitude = calculateRms(audioBuffer, bytesRead)
                        _inputAmplitude.value = amplitude
                        _isVoiceDetected.value = amplitude > VAD_THRESHOLD

                        if (!_isMuted.value) {
                            val chunkToSend = audioBuffer.copyOf(bytesRead)
                            onAudioChunk(chunkToSend)
                        }
                    } else {
                        consecutiveErrors++
                        Log.w(TAG, "AudioRecord read returned $bytesRead (errors: $consecutiveErrors)")
                        if (consecutiveErrors >= 5) {
                            // Attempt microphone auto-recovery
                            Log.w(TAG, "Triggering microphone auto-recovery")
                            delay(200)
                            try {
                                record.stop()
                                delay(100)
                                record.startRecording()
                                consecutiveErrors = 0
                            } catch (re: Exception) {
                                Log.e(TAG, "Microphone recovery failed", re)
                                break
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioStreamer", e)
            stop()
        }
    }

    @Synchronized
    fun stop() {
        _isRecording.value = false
        _inputAmplitude.value = 0f
        _isVoiceDetected.value = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            echoCanceler?.release()
            echoCanceler = null
            noiseSuppressor?.release()
            noiseSuppressor = null

            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord", e)
        }
    }

    private fun calculateRms(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        val sampleCount = length / 2
        if (sampleCount == 0) return 0f

        for (i in 0 until length step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val shortSample = sample.toShort()
            sum += shortSample * shortSample
        }

        val rms = sqrt(sum / sampleCount)
        return (rms / 6000.0).toFloat().coerceIn(0f, 1f)
    }
}
