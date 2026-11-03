package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.live.LiveState
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CrimsonRedLight
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.DeepSurfaceVariant
import com.example.ui.theme.ElectricViolet

@Composable
fun CentralMicButton(
    liveState: LiveState,
    isMuted: Boolean,
    onTogglePower: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_glow")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_pulse"
    )

    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_alpha"
    )

    val isActive = liveState != LiveState.DISCONNECTED

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(110.dp)
            .testTag("central_mic_container")
    ) {
        // Glowing Outer Halo when active
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (liveState == LiveState.SPEAKING) CrimsonRed.copy(alpha = auraAlpha)
                                else CyanAccent.copy(alpha = auraAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Outer ring border
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .border(
                        BorderStroke(
                            2.dp,
                            if (liveState == LiveState.SPEAKING) CrimsonRed.copy(alpha = 0.5f)
                            else CyanAccent.copy(alpha = 0.5f)
                        ),
                        CircleShape
                    )
            )
        }

        // Central Power / Mic Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .shadow(
                    elevation = if (isActive) 16.dp else 4.dp,
                    shape = CircleShape,
                    spotColor = if (liveState == LiveState.SPEAKING) CrimsonRed else CyanAccent
                )
                .clip(CircleShape)
                .background(
                    if (isActive) {
                        Brush.linearGradient(
                            colors = if (liveState == LiveState.SPEAKING) {
                                listOf(CrimsonRed, ElectricViolet)
                            } else {
                                listOf(ElectricViolet, CrimsonRedLight)
                            }
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(DeepSurfaceVariant, DeepObsidian)
                        )
                    }
                )
                .border(
                    BorderStroke(
                        width = 2.dp,
                        brush = if (isActive) {
                            Brush.sweepGradient(listOf(CrimsonRed, AccentGold, ElectricViolet, CrimsonRed))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFF4A253A), Color(0xFF2E1724)))
                        }
                    ),
                    shape = CircleShape
                )
                .clickable(
                    onClick = onTogglePower
                )
                .testTag("central_mic_button")
        ) {
            val icon = when {
                !isActive -> Icons.Default.PowerSettingsNew
                isMuted -> Icons.Default.MicOff
                liveState == LiveState.SPEAKING -> Icons.Default.Mic
                else -> Icons.Default.Mic
            }

            val iconColor = when {
                !isActive -> Color.White.copy(alpha = 0.7f)
                isMuted -> AccentGold
                else -> Color.White
            }

            Icon(
                imageVector = icon,
                contentDescription = if (isActive) "Mute or disconnect" else "Connect with Mahi",
                tint = iconColor,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
