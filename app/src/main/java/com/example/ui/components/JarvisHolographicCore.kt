package com.example.ui.components

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.LiveState
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CrimsonRedLight
import com.example.ui.theme.DeepSurface
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisEmerald
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated Circular Holographic AI Core for CaroLine AI 2.5 — Smart JARVIS Edition.
 * Renders multi-layered counter-rotating arc reactor rings, degree tick telemetry,
 * and audio-reactive harmonic waves.
 */
@Composable
fun JarvisHolographicCore(
    liveState: LiveState,
    amplitude: Float,
    activeProviderBadge: String,
    onCoreTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_core_transition")

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_ring_rot"
    )

    val innerCounterRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_ring_rot"
    )

    val reactorPulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reactor_pulse"
    )

    val reactiveScale by animateFloatAsState(
        targetValue = when (liveState) {
            LiveState.SPEAKING -> 1.0f + (amplitude * 0.28f).coerceAtMost(0.32f)
            LiveState.LISTENING -> 1.0f + (amplitude * 0.18f).coerceAtMost(0.22f)
            LiveState.THINKING, LiveState.CONNECTING -> reactorPulse * 1.04f
            else -> reactorPulse
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "reactive_scale"
    )

    val coreColor = when (liveState) {
        LiveState.LISTENING -> JarvisCyan
        LiveState.SPEAKING -> AccentGold
        LiveState.THINKING, LiveState.CONNECTING, LiveState.RECONNECTING -> ElectricViolet
        LiveState.ERROR -> CrimsonRed
        LiveState.DISCONNECTED -> JarvisElectricBlue
    }

    val secondaryColor = when (liveState) {
        LiveState.LISTENING -> JarvisElectricBlue
        LiveState.SPEAKING -> JarvisCyan
        LiveState.THINKING -> AccentGold
        LiveState.ERROR -> CrimsonRedLight
        else -> JarvisCyan.copy(alpha = 0.75f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(275.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCoreTapped
            )
            .testTag("jarvis_holographic_core")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val maxR = (w.coerceAtMost(h) / 2f) * 0.92f

            // 1. Ambient Holographic Glow Field
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = if (liveState == LiveState.SPEAKING) 0.42f else 0.26f),
                        secondaryColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR * reactiveScale
                ),
                radius = maxR * reactiveScale,
                center = center
            )

            // 2. Outer Degree Telemetry Tick Ring (36 ticks)
            rotate(degrees = outerRotation * 0.5f, pivot = center) {
                val tickRadius = maxR * 0.94f
                for (i in 0 until 36) {
                    val angleRad = (i * 10.0) * (PI / 180.0)
                    val isMajor = i % 3 == 0
                    val innerTickR = if (isMajor) tickRadius - 9.dp.toPx() else tickRadius - 4.dp.toPx()
                    val start = Offset(
                        x = center.x + (innerTickR * cos(angleRad)).toFloat(),
                        y = center.y + (innerTickR * sin(angleRad)).toFloat()
                    )
                    val end = Offset(
                        x = center.x + (tickRadius * cos(angleRad)).toFloat(),
                        y = center.y + (tickRadius * sin(angleRad)).toFloat()
                    )
                    drawLine(
                        color = if (isMajor) coreColor.copy(alpha = 0.85f) else secondaryColor.copy(alpha = 0.4f),
                        start = start,
                        end = end,
                        strokeWidth = if (isMajor) 2.2.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 3. Rotating Segmented Outer Reactor Ring
            rotate(degrees = outerRotation, pivot = center) {
                val arcR = maxR * 0.80f
                val arcSize = Size(arcR * 2, arcR * 2)
                val topLeft = Offset(center.x - arcR, center.y - arcR)
                val sweepAngles = listOf(0f to 65f, 90f to 55f, 180f to 65f, 270f to 55f)
                sweepAngles.forEach { (startAngle, sweep) ->
                    drawArc(
                        color = coreColor.copy(alpha = 0.85f),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // 4. Counter-Rotating Inner Reactor Ring
            rotate(degrees = innerCounterRotation, pivot = center) {
                val innerR = maxR * 0.64f * reactiveScale.coerceIn(0.92f, 1.15f)
                val arcSize = Size(innerR * 2, innerR * 2)
                val topLeft = Offset(center.x - innerR, center.y - innerR)
                for (idx in 0 until 6) {
                    drawArc(
                        color = secondaryColor.copy(alpha = 0.9f),
                        startAngle = idx * 60f,
                        sweepAngle = 36f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // 5. Audio-Reactive Harmonic Nodes Ring
            val nodeRingR = maxR * 0.49f * reactiveScale
            val nodeCount = 12
            for (i in 0 until nodeCount) {
                val angleRad = ((innerCounterRotation * 0.8f) + i * (360f / nodeCount)) * (PI / 180.0)
                val dynamicOffset = if (liveState == LiveState.SPEAKING || liveState == LiveState.LISTENING) {
                    (amplitude * 12.dp.toPx()) * (if (i % 2 == 0) 1f else -0.5f)
                } else 0f
                val r = nodeRingR + dynamicOffset
                val nx = center.x + (r * cos(angleRad)).toFloat()
                val ny = center.y + (r * sin(angleRad)).toFloat()
                drawCircle(
                    color = coreColor,
                    radius = if (i % 2 == 0) 3.5.dp.toPx() else 2.dp.toPx(),
                    center = Offset(nx, ny)
                )
            }

            // 6. Central Plasma Reactor Core
            val plasmaR = maxR * 0.36f * reactiveScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        coreColor.copy(alpha = 0.85f),
                        secondaryColor.copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = plasmaR * 1.25f
                ),
                radius = plasmaR * 1.25f,
                center = center
            )

            drawCircle(
                color = DeepSurface.copy(alpha = 0.78f),
                radius = plasmaR * 0.82f,
                center = center
            )

            drawCircle(
                color = coreColor,
                radius = plasmaR * 0.82f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Center HUD Readout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "JARVIS 2.9",
                color = coreColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.8.sp
            )
            Text(
                text = activeProviderBadge,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Bottom Live Status Pill
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DeepSurface.copy(alpha = 0.95f),
            border = BorderStroke(1.dp, coreColor.copy(alpha = 0.75f)),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 10.dp)
                .clickable { onCoreTapped() }
                .testTag("jarvis_core_status_pill")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (liveState) {
                                LiveState.SPEAKING -> AccentGold
                                LiveState.LISTENING -> JarvisEmerald
                                LiveState.CONNECTING, LiveState.THINKING -> JarvisCyan
                                LiveState.RECONNECTING -> Color(0xFFFF9800)
                                LiveState.ERROR -> CrimsonRed
                                LiveState.DISCONNECTED -> JarvisElectricBlue
                            }
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (liveState) {
                        LiveState.SPEAKING -> "कैरोलाइन बोल रही है"
                        LiveState.LISTENING -> "कैरोलाइन सुन रही है"
                        LiveState.CONNECTING -> "कनेक्ट हो रहा है..."
                        LiveState.THINKING -> "जार्विस प्रोसेस कर रहा है..."
                        LiveState.RECONNECTING -> "पुनः कनेक्ट हो रहा है..."
                        LiveState.ERROR -> "सिस्टम नोटिस"
                        LiveState.DISCONNECTED -> "जार्विस कोर तैयार है"
                    },
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
