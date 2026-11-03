package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.live.LiveState
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CrimsonRedLight
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DeepSurface
import com.example.ui.theme.ElectricViolet
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class AvatarStyle {
    GOTHIC_CYBER,
    PINK_NEON,
    CLASSIC_ANIME,
    CUSTOM_UPLOADED
}

enum class AvatarEmotion {
    LISTENING,
    SMILING,
    THINKING,
    SPEAKING,
    HAPPY,
    SURPRISED
}

/**
 * Real-time animated anime female avatar for CaroLine AI.
 * Displays a large anime character with real-time lip-synced mouth movement,
 * natural blinking, facial expressions (listening, smiling, thinking, speaking, happy, surprised),
 * and audio-reactive sonic glow.
 */
@Composable
fun CaroLineAnimeAvatarView(
    liveState: LiveState,
    amplitude: Float,
    selectedAvatarStyle: AvatarStyle,
    customAvatarUri: Uri?,
    manualEmotion: AvatarEmotion? = null,
    onAvatarTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Dynamic emotion mapped to state or manual override
    val activeEmotion = manualEmotion ?: when (liveState) {
        LiveState.SPEAKING -> AvatarEmotion.SPEAKING
        LiveState.LISTENING -> AvatarEmotion.LISTENING
        LiveState.CONNECTING, LiveState.RECONNECTING, LiveState.THINKING -> AvatarEmotion.THINKING
        LiveState.ERROR -> AvatarEmotion.SURPRISED
        LiveState.DISCONNECTED -> AvatarEmotion.LISTENING
    }

    // Natural blinking loop
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((2800..4500).random().toLong())
            isBlinking = true
            delay(130)
            isBlinking = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "anime_avatar_fx")

    // Breathing float animation
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Sonic ripple wave scale
    val sonicWaveScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sonic_wave"
    )

    val sonicWaveAlpha by infiniteTransition.animateFloat(
        initialValue = if (liveState == LiveState.SPEAKING || liveState == LiveState.LISTENING) 0.65f else 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sonic_alpha"
    )

    // Thinking SUMAN halo rotation
    val haloRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "halo"
    )

    // Dynamic mouth openness factor for lip-sync
    val mouthOpenFactor by animateFloatAsState(
        targetValue = when (activeEmotion) {
            AvatarEmotion.SPEAKING -> (0.2f + amplitude * 1.6f).coerceIn(0.12f, 1.0f)
            AvatarEmotion.SURPRISED -> 0.65f
            AvatarEmotion.SMILING, AvatarEmotion.HAPPY -> 0.35f
            AvatarEmotion.THINKING -> 0.08f
            AvatarEmotion.LISTENING -> 0.03f
        },
        animationSpec = spring(stiffness = 900f, dampingRatio = 0.65f),
        label = "mouth_morph"
    )

    // Overall interactive scale
    val dynamicScale = when (liveState) {
        LiveState.SPEAKING -> breathingScale * (1f + (amplitude * 0.15f).coerceAtMost(0.18f))
        LiveState.LISTENING -> breathingScale * (1f + (amplitude * 0.08f).coerceAtMost(0.1f))
        else -> breathingScale
    }

    val glowColor = when (activeEmotion) {
        AvatarEmotion.SPEAKING -> CrimsonRed
        AvatarEmotion.LISTENING -> CyanAccent
        AvatarEmotion.THINKING -> ElectricViolet
        AvatarEmotion.SMILING, AvatarEmotion.HAPPY -> Color(0xFFFF2A85)
        AvatarEmotion.SURPRISED -> AccentGold
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(310.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onAvatarTapped
            )
            .testTag("caroline_anime_avatar_stage")
    ) {
        // --- 1. Outer Sonic Waves (Audio-reactive expanding ripples) ---
        if (liveState == LiveState.SPEAKING || liveState == LiveState.LISTENING) {
            Box(
                modifier = Modifier
                    .size(270.dp)
                    .scale(sonicWaveScale)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        brush = Brush.radialGradient(
                            listOf(
                                glowColor.copy(alpha = sonicWaveAlpha),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        // --- 2. Ambient Cyber Aura Glow ---
        Box(
            modifier = Modifier
                .size(280.dp)
                .scale(dynamicScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = if (liveState == LiveState.SPEAKING) 0.5f else 0.3f),
                            ElectricViolet.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        // --- 3. Main Avatar Card / Character Container ---
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(255.dp)
                .scale(dynamicScale)
                .shadow(
                    elevation = if (liveState == LiveState.SPEAKING) 28.dp else 16.dp,
                    shape = CircleShape,
                    spotColor = glowColor,
                    ambientColor = ElectricViolet
                )
                .clip(CircleShape)
                .border(
                    width = if (liveState == LiveState.SPEAKING) 3.5.dp else 2.5.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            glowColor,
                            ElectricViolet,
                            AccentGold,
                            glowColor
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            // Anime Avatar Image
            when (selectedAvatarStyle) {
                AvatarStyle.GOTHIC_CYBER -> {
                    Image(
                        painter = painterResource(id = R.drawable.caroline_avatar_main),
                        contentDescription = "CaroLine AI Gothic Cyber Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                AvatarStyle.PINK_NEON -> {
                    Image(
                        painter = painterResource(id = R.drawable.caroline_ref_avatar),
                        contentDescription = "CaroLine AI Pink Neon Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                AvatarStyle.CLASSIC_ANIME -> {
                    Image(
                        painter = painterResource(id = R.drawable.mahi_avatar),
                        contentDescription = "CaroLine AI Classic Anime Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                AvatarStyle.CUSTOM_UPLOADED -> {
                    if (customAvatarUri != null) {
                        AsyncImage(
                            model = customAvatarUri,
                            contentDescription = "Boss's Custom Avatar for CaroLine",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.caroline_avatar_main),
                            contentDescription = "CaroLine AI Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // --- 4. Real-time Facial Expression Overlay Canvas ---
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val centerX = w * 0.5f

                // A. Blinking / Happy Eyes Overlay
                // Eye region: Y ~ 47%..52% of avatar container, X ~ 36% & 64%
                val eyeLeftX = w * 0.365f
                val eyeRightX = w * 0.635f
                val eyeY = h * 0.495f

                if (activeEmotion == AvatarEmotion.SMILING || activeEmotion == AvatarEmotion.HAPPY || isBlinking) {
                    // Draw smiling / closed curved anime eyelashes
                    val lashW = 26.dp.toPx()
                    val lashColor = Color(0xFF22081C)

                    val leftLash = Path().apply {
                        moveTo(eyeLeftX - lashW / 2, eyeY + 1.dp.toPx())
                        quadraticTo(eyeLeftX, eyeY - 8.dp.toPx(), eyeLeftX + lashW / 2, eyeY + 1.dp.toPx())
                    }
                    drawPath(leftLash, color = lashColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))

                    val rightLash = Path().apply {
                        moveTo(eyeRightX - lashW / 2, eyeY + 1.dp.toPx())
                        quadraticTo(eyeRightX, eyeY - 8.dp.toPx(), eyeRightX + lashW / 2, eyeY + 1.dp.toPx())
                    }
                    drawPath(rightLash, color = lashColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))

                    // Sparkle glint on happy eyes
                    if (activeEmotion == AvatarEmotion.HAPPY || activeEmotion == AvatarEmotion.SMILING) {
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(eyeLeftX + 8.dp.toPx(), eyeY - 4.dp.toPx()))
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(eyeRightX - 8.dp.toPx(), eyeY - 4.dp.toPx()))
                    }
                }

                // B. Rosy Blushing Cheeks (Happy / Smiling / Flirty)
                if (activeEmotion == AvatarEmotion.SMILING || activeEmotion == AvatarEmotion.HAPPY) {
                    val cheekLeftX = w * 0.28f
                    val cheekRightX = w * 0.72f
                    val cheekY = h * 0.58f

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF4081).copy(alpha = 0.55f),
                                Color(0xFFFF7597).copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            center = Offset(cheekLeftX, cheekY),
                            radius = 20.dp.toPx()
                        ),
                        radius = 20.dp.toPx(),
                        center = Offset(cheekLeftX, cheekY)
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF4081).copy(alpha = 0.55f),
                                Color(0xFFFF7597).copy(alpha = 0.25f),
                                Color.Transparent
                            ),
                            center = Offset(cheekRightX, cheekY),
                            radius = 20.dp.toPx()
                        ),
                        radius = 20.dp.toPx(),
                        center = Offset(cheekRightX, cheekY)
                    )
                }

                // C. Real-time Lip-Sync Mouth
                // Mouth center is located at roughly X = 50%, Y = 68%
                val mouthX = centerX
                val mouthY = h * 0.675f
                val mouthWidth = (14.dp.toPx() + mouthOpenFactor * 18.dp.toPx())
                val mouthHeight = (mouthOpenFactor * 22.dp.toPx()).coerceAtLeast(2.5.dp.toPx())

                if (mouthOpenFactor > 0.16f) {
                    // Open speaking / laughing mouth shape
                    val mouthPath = Path().apply {
                        moveTo(mouthX - mouthWidth / 2, mouthY)
                        quadraticTo(mouthX, mouthY + mouthHeight, mouthX + mouthWidth / 2, mouthY)
                        quadraticTo(mouthX, mouthY - mouthHeight * 0.25f, mouthX - mouthWidth / 2, mouthY)
                        close()
                    }

                    // Inner mouth cavity
                    drawPath(
                        path = mouthPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF4A0E2B),
                                Color(0xFF9E1B48)
                            )
                        )
                    )

                    // Teeth highlight
                    drawOval(
                        color = Color.White.copy(alpha = 0.9f),
                        topLeft = Offset(mouthX - mouthWidth * 0.25f, mouthY - 1.5.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(mouthWidth * 0.5f, 3.dp.toPx())
                    )

                    // Tongue curve
                    drawCircle(
                        color = Color(0xFFFF6584),
                        radius = (mouthWidth * 0.22f).coerceAtMost(mouthHeight * 0.65f),
                        center = Offset(mouthX, mouthY + mouthHeight * 0.52f)
                    )

                    // Lip outline
                    drawPath(
                        path = mouthPath,
                        color = Color(0xFF2C0B1E),
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                } else {
                    // Resting cute anime smile curve
                    val smilePath = Path().apply {
                        val curveDepth = if (activeEmotion == AvatarEmotion.THINKING) 1.dp.toPx() else 4.dp.toPx()
                        moveTo(mouthX - 8.dp.toPx(), mouthY)
                        quadraticTo(mouthX, mouthY + curveDepth, mouthX + 8.dp.toPx(), mouthY)
                    }
                    drawPath(
                        path = smilePath,
                        color = Color(0xFF2C0B1E),
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // D. Thinking SUMAN Brain Synaptic Halo
                if (activeEmotion == AvatarEmotion.THINKING) {
                    val haloRadius = w * 0.42f
                    val haloCenter = Offset(centerX, h * 0.24f)
                    val numNodes = 7

                    for (i in 0 until numNodes) {
                        val angle = (haloRotation + i * (360f / numNodes)) * (PI / 180.0)
                        val nx = (haloCenter.x + haloRadius * cos(angle)).toFloat()
                        val ny = (haloCenter.y + haloRadius * 0.45f * sin(angle)).toFloat()

                        drawCircle(
                            color = AccentGold,
                            radius = 3.dp.toPx(),
                            center = Offset(nx, ny)
                        )

                        // Synapse connector
                        val nextAngle = (haloRotation + (i + 1) * (360f / numNodes)) * (PI / 180.0)
                        val nxtX = (haloCenter.x + haloRadius * cos(nextAngle)).toFloat()
                        val nxtY = (haloCenter.y + haloRadius * 0.45f * sin(nextAngle)).toFloat()

                        drawLine(
                            color = CyanAccent.copy(alpha = 0.5f),
                            start = Offset(nx, ny),
                            end = Offset(nxtX, nxtY),
                            strokeWidth = 1.2.dp.toPx()
                        )
                    }
                }
            }
        }

        // --- 5. Interactive Live State Pill at Bottom ---
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DeepSurface.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, glowColor.copy(alpha = 0.7f)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 12.dp)
                .clickable { onAvatarTapped() }
                .testTag("avatar_state_pill")
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
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
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = when (liveState) {
                        LiveState.SPEAKING -> "CAROLINE SPEAKING"
                        LiveState.LISTENING -> "LISTENING TO BOSS"
                        LiveState.CONNECTING -> "SUMAN CONNECTING..."
                        LiveState.THINKING -> "SUMAN BRAIN PROCESSING"
                        LiveState.RECONNECTING -> "RECONNECTING LIVE..."
                        LiveState.ERROR -> "SYSTEM NOTICE"
                        LiveState.DISCONNECTED -> "STANDBY FOR BOSS"
                    },
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "• ${activeEmotion.name}",
                    color = glowColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
