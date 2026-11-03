package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.live.LiveState
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricViolet
import kotlin.math.sin

@Composable
fun AudioWaveform(
    liveState: LiveState,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val barCount = 28

    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    // Palette gradient for bars
    val waveBrush = Brush.horizontalGradient(
        colors = when (liveState) {
            LiveState.SPEAKING -> listOf(CrimsonRed, ElectricViolet, AccentGold, CrimsonRed)
            LiveState.LISTENING -> listOf(CyanAccent, ElectricViolet, CrimsonRed, CyanAccent)
            LiveState.CONNECTING, LiveState.THINKING, LiveState.RECONNECTING -> listOf(AccentGold, CrimsonRed, AccentGold)
            LiveState.ERROR -> listOf(Color.Red, Color(0xFFFF5252))
            LiveState.DISCONNECTED -> listOf(Color(0xFF331B2B), Color(0xFF5A2A48), Color(0xFF331B2B))
        }
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("audio_waveform_canvas")
    ) {
        val totalWidth = size.width
        val canvasHeight = size.height
        val barWidth = (totalWidth / barCount) * 0.55f
        val spacing = (totalWidth / barCount) * 0.45f
        val centerY = canvasHeight / 2f

        for (i in 0 until barCount) {
            // Wave harmonics
            val normalizedIndex = i.toFloat() / barCount
            val sinWave = sin(normalizedIndex * 4.0 + phase).toFloat()
            val cosWave = sin((normalizedIndex * 7.0 - phase * 1.5)).toFloat()

            val baseIdleHeight = 6f
            val dynamicHeight = when (liveState) {
                LiveState.SPEAKING -> {
                    val energy = (amplitude * canvasHeight * 0.9f).coerceAtLeast(10f)
                    val modulation = 0.4f + 0.6f * ((sinWave + cosWave + 2f) / 4f)
                    energy * modulation
                }
                LiveState.LISTENING -> {
                    val energy = (amplitude * canvasHeight * 0.7f).coerceAtLeast(6f)
                    val modulation = 0.3f + 0.7f * ((sinWave + 1f) / 2f)
                    energy * modulation
                }
                LiveState.CONNECTING, LiveState.THINKING, LiveState.RECONNECTING -> {
                    val wave = ((sin(phase + i * 0.4f) + 1f) / 2f) * 22f
                    baseIdleHeight + wave
                }
                LiveState.ERROR -> {
                    baseIdleHeight + (sin(phase * 2f + i * 0.5f) * 3f)
                }
                LiveState.DISCONNECTED -> {
                    baseIdleHeight + (sin(phase * 0.5f + i * 0.2f) * 2f)
                }
            }

            val barHeight = dynamicHeight.coerceIn(4f, canvasHeight - 4f)
            val left = i * (barWidth + spacing) + (spacing / 2f)
            val top = centerY - (barHeight / 2f)

            drawRoundRect(
                brush = waveBrush,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
