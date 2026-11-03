package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.live.LiveState
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CrimsonRedLight
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DeepSurface
import com.example.ui.theme.ElectricViolet
import com.example.viewmodel.AvatarMode

@Composable
fun AvatarPulseView(
    liveState: LiveState,
    amplitude: Float,
    avatarMode: AvatarMode,
    currentExpression: FaceExpression,
    manualExpressionOverride: FaceExpression?,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_rings")

    // Idle breathing animation
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse"
    )

    // Dynamic scale driven by audio amplitude and state
    val targetScale = when (liveState) {
        LiveState.SPEAKING -> 1.0f + (amplitude * 0.22f).coerceAtMost(0.3f)
        LiveState.LISTENING -> 1.0f + (amplitude * 0.14f).coerceAtMost(0.2f)
        LiveState.CONNECTING, LiveState.THINKING, LiveState.RECONNECTING -> idlePulse * 1.03f
        LiveState.ERROR -> 0.92f
        LiveState.DISCONNECTED -> 0.96f
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "avatar_scale"
    )

    // Ring expansion animations
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = if (liveState != LiveState.DISCONNECTED) 0.8f else 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1_alpha"
    )
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1_scale"
    )

    val glowColors = when (liveState) {
        LiveState.SPEAKING -> listOf(CrimsonRed, ElectricViolet, AccentGold)
        LiveState.LISTENING -> listOf(CyanAccent, CrimsonRed, ElectricViolet)
        LiveState.CONNECTING, LiveState.THINKING, LiveState.RECONNECTING -> listOf(AccentGold, CrimsonRed)
        LiveState.ERROR -> listOf(Color.Red, Color(0xFFFF5252))
        LiveState.DISCONNECTED -> listOf(Color(0xFF4A2038), Color(0xFF1F0D19))
    }

    val activeExpression = manualExpressionOverride ?: currentExpression

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(250.dp)
            .testTag("avatar_pulse_view")
    ) {
        // Outer pulsing ring
        if (liveState != LiveState.DISCONNECTED) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(ring1Scale)
                    .clip(CircleShape)
                    .border(
                        BorderStroke(
                            2.dp,
                            Brush.radialGradient(
                                listOf(
                                    CrimsonRed.copy(alpha = ring1Alpha),
                                    Color.Transparent
                                )
                            )
                        ),
                        CircleShape
                    )
            )
        }

        // Ambient radial glow behind the avatar
        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(animatedScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            glowColors[0].copy(alpha = if (liveState == LiveState.SPEAKING) 0.45f else 0.25f),
                            glowColors.getOrElse(1) { glowColors[0] }.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Main Visual: Switchable between Procedural Face Engine and Anime Artwork
        Crossfade(
            targetState = avatarMode,
            animationSpec = tween(400),
            label = "avatar_mode_crossfade"
        ) { mode ->
            when (mode) {
                AvatarMode.PROCEDURAL_ANIMATED_FACE -> {
                    CaroLineFaceView(
                        liveState = liveState,
                        amplitude = amplitude,
                        manualExpression = manualExpressionOverride,
                        onFaceTapped = onAvatarClick,
                        modifier = Modifier
                            .size(220.dp)
                            .scale(animatedScale)
                    )
                }

                AvatarMode.ANIME_ARTWORK -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(170.dp)
                            .scale(animatedScale)
                            .shadow(
                                elevation = if (liveState == LiveState.SPEAKING) 24.dp else 12.dp,
                                shape = CircleShape,
                                spotColor = CrimsonRed,
                                ambientColor = ElectricViolet
                            )
                            .clip(CircleShape)
                            .border(
                                BorderStroke(
                                    width = if (liveState == LiveState.SPEAKING) 3.5.dp else 2.5.dp,
                                    brush = Brush.sweepGradient(glowColors)
                                ),
                                shape = CircleShape
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onAvatarClick
                            )
                            .testTag("caroline_avatar_image")
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.mahi_avatar),
                            contentDescription = "CaroLine AI Companion for Boss",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(170.dp)
                        )
                    }
                }
            }
        }

        // Live Status Badge Pill at bottom of avatar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DeepSurface.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.6f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 14.dp)
                .clickable { onAvatarClick() }
                .testTag("status_badge_pill")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                // Glowing status LED indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (liveState) {
                                LiveState.SPEAKING -> CrimsonRedLight
                                LiveState.LISTENING -> CyanAccent
                                LiveState.CONNECTING, LiveState.THINKING -> AccentGold
                                LiveState.RECONNECTING -> Color(0xFFFF9800)
                                LiveState.ERROR -> Color.Red
                                LiveState.DISCONNECTED -> Color.Gray
                            }
                        )
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = when (liveState) {
                        LiveState.SPEAKING -> "CAROLINE SPEAKING"
                        LiveState.LISTENING -> "LISTENING TO BOSS"
                        LiveState.CONNECTING -> "SUMAN BRAIN SYNC..."
                        LiveState.THINKING -> "SUMAN THINKING..."
                        LiveState.RECONNECTING -> "RECONNECTING..."
                        LiveState.ERROR -> "SYSTEM NOTICE"
                        LiveState.DISCONNECTED -> "STANDBY FOR BOSS"
                    },
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                // Expression indicator tag
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• ${activeExpression.name}",
                    color = when (activeExpression) {
                        FaceExpression.SMILING -> AccentGold
                        FaceExpression.THINKING -> ElectricViolet
                        FaceExpression.SPEAKING -> CrimsonRedLight
                        FaceExpression.LISTENING -> CyanAccent
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
