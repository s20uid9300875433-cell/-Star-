package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.sqrt

/**
 * Plays 24kHz 16-bit PCM mono response audio received from the Gemini Live API.
 * Provides instant flushing when interruptions occur.
 */
class AudioPlayer(
    private val onPlaybackStateChanged: (Boolean) -> Unit = {}
) {
    companion object {
        private const val TAG = "AudioPlayer"
        const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _outputAmplitude = MutableStateFlow(0f)
    val outputAmplitude = _outputAmplitude.asStateFlow()

    init {
        initAudioTrack()
    }

    @Synchronized
    private fun initAudioTrack() {
        try {
            val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = (minBufferSize * 4).coerceAtLeast(8192)

            audioTrack = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .setEncoding(AUDIO_FORMAT)
                    .build(),
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            audioTrack?.play()
            startPlaybackLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack", e)
        }
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            while (isActive) {
                try {
                    // Poll with timeout so we can reset isPlaying if queue is empty
                    val chunk = audioQueue.poll(60, java.util.concurrent.TimeUnit.MILLISECONDS)
                    if (chunk != null) {
                        if (!_isPlaying.value) {
                            _isPlaying.value = true
                            onPlaybackStateChanged(true)
                        }

                        val amplitude = calculateRms(chunk)
                        _outputAmplitude.value = amplitude

                        audioTrack?.write(chunk, 0, chunk.size)
                    } else {
                        // Queue empty for at least 60ms
                        if (_isPlaying.value) {
                            _isPlaying.value = false
                            _outputAmplitude.value = 0f
                            onPlaybackStateChanged(false)
                        }
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Playback loop error", e)
                }
            }
        }
    }

    /**
     * Enqueues incoming PCM16 audio bytes from the Gemini Live stream.
     */
    fun enqueueAudio(data: ByteArray) {
        if (data.isNotEmpty()) {
            audioQueue.offer(data)
        }
    }

    /**
     * Instantly stops playback and clears all pending audio when the user
     * interrupts or when the server sends an interruption signal.
     */
    @Synchronized
    fun flushAndInterrupt() {
        audioQueue.clear()
        _outputAmplitude.value = 0f
        _isPlaying.value = false
        onPlaybackStateChanged(false)

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing AudioTrack", e)
        }
    }

    @Synchronized
    fun release() {
        audioQueue.clear()
        playbackJob?.cancel()
        playbackJob = null
        _isPlaying.value = false
        _outputAmplitude.value = 0f

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        }
    }

    private fun calculateRms(buffer: ByteArray): Float {
        var sum = 0.0
        val sampleCount = buffer.size / 2
        if (sampleCount == 0) return 0f

        for (i in buffer.indices step 2) {
            if (i + 1 < buffer.size) {
                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                val shortSample = sample.toShort()
                sum += shortSample * shortSample
            }
        }

        val rms = sqrt(sum / sampleCount)
        return (rms / 7000.0).toFloat().coerceIn(0f, 1f)
    }
}
