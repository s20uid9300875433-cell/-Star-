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
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Streams raw PCM 16-bit 16kHz mono audio from the device microphone
 * for the Gemini Live Multimodal WebSocket API and captures complete
 * VAD-segmented WAV utterances for reliable voice command recognition.
 */
class AudioStreamer(
    private val onAudioChunk: (ByteArray) -> Unit,
    private val onUtteranceCaptured: (ByteArray) -> Unit = {},
    private val onMicError: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "AudioStreamer"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
        private const val VAD_START_THRESHOLD = 0.042f
        private const val VAD_SILENCE_THRESHOLD = 0.030f
        // Minimum ~350ms of speech (16000 * 2 bytes * 0.35s = ~11200 bytes)
        private const val MIN_UTTERANCE_BYTES = 11200
        // Maximum ~12s per single utterance buffer (16000 * 2 * 12 = 384000 bytes)
        private const val MAX_UTTERANCE_BYTES = 384000
        // Silence frames (~64ms each) before closing utterance (~850ms = 13 frames)
        private const val SILENCE_FRAMES_TO_COMPLETE = 13
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

    @Volatile
    var isPlaybackActive: Boolean = false

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    /**
     * Allows external SpeechRecognizer RMS updates to drive the visualizer amplitude
     * when SpeechRecognizer holds the microphone instead of AudioRecord.
     */
    fun updateExternalAmplitude(normalizedAmp: Float, voiceDetected: Boolean) {
        _inputAmplitude.value = normalizedAmp.coerceIn(0f, 1f)
        _isVoiceDetected.value = voiceDetected
    }

    /**
     * Initializes and starts the hardware microphone at 16kHz PCM16 Mono.
     * Returns true ONLY if the microphone is genuinely initialized and recording.
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun start(): Boolean {
        if (_isRecording.value && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            return true
        }

        stop()

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize <= 0) {
            val msg = "মাইক্রোফোন বাফার ত্রুটি (Invalid AudioRecord buffer size: $minBufferSize)"
            Log.e(TAG, msg)
            onMicError(msg)
            return false
        }

        val bufferSize = (minBufferSize * BUFFER_SIZE_FACTOR).coerceAtLeast(4096)

        try {
            // Try VOICE_COMMUNICATION first for hardware echo cancellation
            var record = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            } catch (e: Exception) {
                null
            }

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                record?.release()
                // Fallback to VOICE_RECOGNITION or MIC
                record = try {
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                    )
                } catch (e: Exception) {
                    null
                }
            }

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                record?.release()
                record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
            }

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                val msg = "মাইক্রোফোন চালু করা যায়নি। অনুগ্রহ করে মাইক্রোফোন অনুমতি এবং অন্য অ্যাপ মাইক্রোফোন ব্যবহার করছে কিনা তা পরীক্ষা করুন।"
                Log.e(TAG, "AudioRecord failed to initialize")
                onMicError(msg)
                return false
            }

            audioRecord = record

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
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                record.release()
                audioRecord = null
                val msg = "মাইক্রোফোন রেকর্ডিং শুরু করতে ব্যর্থ হয়েছে (Recording state inactive)।"
                Log.e(TAG, msg)
                onMicError(msg)
                return false
            }

            _isRecording.value = true

            recordingJob = scope.launch {
                val readChunkSize = 2048 // 1024 samples = 64ms at 16kHz
                val audioBuffer = ByteArray(readChunkSize)
                var consecutiveErrors = 0

                val utteranceStream = ByteArrayOutputStream()
                var isCapturingUtterance = false
                var silenceFrames = 0

                while (isActive && _isRecording.value) {
                    val bytesRead = record.read(audioBuffer, 0, audioBuffer.size)
                    if (bytesRead > 0) {
                        consecutiveErrors = 0
                        val amplitude = calculateRms(audioBuffer, bytesRead)
                        _inputAmplitude.value = amplitude
                        val voiceActive = amplitude > VAD_START_THRESHOLD
                        _isVoiceDetected.value = voiceActive

                        if (!_isMuted.value) {
                            val chunkToSend = audioBuffer.copyOf(bytesRead)
                            onAudioChunk(chunkToSend)

                            // Only capture VAD utterance when AI is not currently playing audio out loud
                            if (!isPlaybackActive) {
                                if (amplitude > VAD_START_THRESHOLD) {
                                    isCapturingUtterance = true
                                    silenceFrames = 0
                                    utteranceStream.write(chunkToSend)
                                } else if (isCapturingUtterance) {
                                    utteranceStream.write(chunkToSend)
                                    if (amplitude < VAD_SILENCE_THRESHOLD) {
                                        silenceFrames++
                                    } else {
                                        silenceFrames = 0
                                    }

                                    if (silenceFrames >= SILENCE_FRAMES_TO_COMPLETE || utteranceStream.size() >= MAX_UTTERANCE_BYTES) {
                                        val pcmBytes = utteranceStream.toByteArray()
                                        utteranceStream.reset()
                                        isCapturingUtterance = false
                                        silenceFrames = 0

                                        if (pcmBytes.size >= MIN_UTTERANCE_BYTES) {
                                            val wavBytes = encodePcmToWav(pcmBytes, SAMPLE_RATE, 1, 16)
                                            onUtteranceCaptured(wavBytes)
                                        }
                                    }
                                }
                            } else {
                                // Reset partial utterance while AI is speaking unless barge-in occurred
                                if (isCapturingUtterance) {
                                    utteranceStream.reset()
                                    isCapturingUtterance = false
                                    silenceFrames = 0
                                }
                            }
                        }
                    } else {
                        consecutiveErrors++
                        Log.w(TAG, "AudioRecord read returned $bytesRead (errors: $consecutiveErrors)")
                        if (consecutiveErrors >= 8) {
                            Log.w(TAG, "Triggering microphone auto-recovery")
                            delay(250)
                            try {
                                record.stop()
                                delay(120)
                                record.startRecording()
                                consecutiveErrors = 0
                            } catch (re: Exception) {
                                Log.e(TAG, "Microphone recovery failed", re)
                                onMicError("মাইক্রোফোন সংযোগ বিচ্ছিন্ন হয়েছে। পুনরায় চেষ্টা করা হচ্ছে...")
                                break
                            }
                        }
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioStreamer", e)
            onMicError("মাইক্রোফোন ত্রুটি: ${e.localizedMessage ?: "Unknown error"}")
            stop()
            return false
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
            if (i + 1 < length) {
                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                val shortSample = sample.toShort()
                sum += shortSample * shortSample
            }
        }

        val rms = sqrt(sum / sampleCount)
        return (rms / 6000.0).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Wraps raw 16-bit PCM mono samples in a standard 44-byte RIFF WAV header.
     */
    fun encodePcmToWav(
        pcmData: ByteArray,
        sampleRate: Int = SAMPLE_RATE,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val totalDataLen = pcmData.size + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // Subchunk1Size for PCM
        header.putShort(1.toShort()) // AudioFormat 1 = PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmData.size)

        val output = ByteArray(44 + pcmData.size)
        System.arraycopy(header.array(), 0, output, 0, 44)
        System.arraycopy(pcmData, 0, output, 44, pcmData.size)
        return output
    }
}
