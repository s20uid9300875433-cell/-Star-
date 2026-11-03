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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.live.LiveState
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CrimsonRedDark
import com.example.ui.theme.CrimsonRedLight
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.ElectricViolet
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class FaceExpression {
    LISTENING,
    SMILING,
    THINKING,
    SPEAKING
}

/**
 * Real-time procedural animated face engine for CaroLine AI.
 * Renders dynamic facial expressions: Listening, Smiling, Thinking, and Speaking.
 * Integrates smooth blinking, pupil saccades, lip-sync mouth morphing with audio amplitude,
 * blushing cheeks, and brain thinking halos.
 */
@Composable
fun CaroLineFaceView(
    liveState: LiveState,
    amplitude: Float,
    manualExpression: FaceExpression? = null,
    onFaceTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine active expression based on state or manual override
    val activeExpression = manualExpression ?: when (liveState) {
        LiveState.SPEAKING -> FaceExpression.SPEAKING
        LiveState.LISTENING -> FaceExpression.LISTENING
        LiveState.CONNECTING, LiveState.THINKING, LiveState.RECONNECTING -> FaceExpression.THINKING
        LiveState.ERROR -> FaceExpression.SMILING
        LiveState.DISCONNECTED -> FaceExpression.LISTENING
    }

    // Natural blinking loop
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((2800..5000).random().toLong())
            isBlinking = true
            delay(150)
            isBlinking = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "face_animations")

    // Idle breathing oscillation
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.99f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    // Halo rotation for THINKING state
    val haloRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "halo_rot"
    )

    // Thinking glance shift
    val thinkingPupilShift by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinking_pupil"
    )

    // Smiling cheek blush pulsing
    val blushPulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blush_pulse"
    )

    // Dynamic mouth openness for speech lip-sync
    val mouthOpenFactor by animateFloatAsState(
        targetValue = when (activeExpression) {
            FaceExpression.SPEAKING -> (0.25f + amplitude * 1.5f).coerceIn(0.15f, 1.0f)
            FaceExpression.SMILING -> 0.35f
            FaceExpression.THINKING -> 0.08f
            FaceExpression.LISTENING -> 0.04f
        },
        animationSpec = spring(stiffness = 800f, dampingRatio = 0.7f),
        label = "mouth_open"
    )

    // Eye openness factor (1.0 = wide open, 0.0 = completely closed blink)
    val eyeOpenFactor by animateFloatAsState(
        targetValue = if (isBlinking && activeExpression != FaceExpression.SMILING) 0f else when (activeExpression) {
            FaceExpression.SMILING -> 0.05f // Smiling crescent eyes
            FaceExpression.LISTENING -> 1.0f
            FaceExpression.SPEAKING -> 0.92f
            FaceExpression.THINKING -> 0.75f
        },
        animationSpec = tween(durationMillis = if (isBlinking) 70 else 220),
        label = "eye_open"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(240.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFaceTapped
            )
            .testTag("caroline_face_engine")
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val canvasW = size.width
            val canvasH = size.height
            val center = Offset(canvasW / 2f, canvasH / 2f)

            // --- 1. Ambient Background Neural Glow ---
            val glowColor = when (activeExpression) {
                FaceExpression.SPEAKING -> CrimsonRed
                FaceExpression.LISTENING -> CyanAccent
                FaceExpression.THINKING -> ElectricViolet
                FaceExpression.SMILING -> CrimsonRedLight
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.35f * breathScale),
                        glowColor.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = canvasW * 0.48f * breathScale
                ),
                radius = canvasW * 0.48f * breathScale,
                center = center
            )

            // --- 2. Thinking Halo (SUMAN Brain Activity) ---
            if (activeExpression == FaceExpression.THINKING) {
                drawThinkingNeuralHalo(center, haloRotation, canvasW * 0.46f)
            }

            // --- 3. Headset / Cyber Earpieces ---
            drawHeadsetChassis(center, canvasW * 0.41f, glowColor, amplitude)

            // --- 4. Main Head Shape (Soft Cyber Anime Style) ---
            val faceRadius = canvasW * 0.36f * breathScale
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF281122),
                        Color(0xFF1B0B17),
                        DeepObsidian
                    ),
                    start = Offset(center.x, center.y - faceRadius),
                    end = Offset(center.x, center.y + faceRadius)
                ),
                radius = faceRadius,
                center = center
            )

            // Outer Neon Face Rim
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        glowColor,
                        ElectricViolet,
                        AccentGold,
                        glowColor
                    )
                ),
                radius = faceRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // --- 5. Cheeks (Blushing / Cyber Highlights) ---
            val blushAlpha = if (activeExpression == FaceExpression.SMILING) blushPulse else 0.35f
            val leftCheek = Offset(center.x - faceRadius * 0.52f, center.y + faceRadius * 0.22f)
            val rightCheek = Offset(center.x + faceRadius * 0.52f, center.y + faceRadius * 0.22f)

            drawCircle(
                color = CrimsonRedLight.copy(alpha = blushAlpha * 0.5f),
                radius = 16.dp.toPx(),
                center = leftCheek
            )
            drawCircle(
                color = CrimsonRedLight.copy(alpha = blushAlpha * 0.5f),
                radius = 16.dp.toPx(),
                center = rightCheek
            )

            // Cyber dots on cheeks
            drawCircle(color = glowColor.copy(alpha = 0.8f), radius = 2.dp.toPx(), center = leftCheek)
            drawCircle(color = glowColor.copy(alpha = 0.8f), radius = 2.dp.toPx(), center = rightCheek)

            // --- 6. Eyebrows ---
            drawEyebrows(center, faceRadius, activeExpression)

            // --- 7. Eyes Engine ---
            val eyeSpacing = faceRadius * 0.45f
            val eyeCenterY = center.y - faceRadius * 0.12f
            val leftEyeCenter = Offset(center.x - eyeSpacing, eyeCenterY)
            val rightEyeCenter = Offset(center.x + eyeSpacing, eyeCenterY)

            if (activeExpression == FaceExpression.SMILING || (isBlinking && activeExpression != FaceExpression.THINKING)) {
                // Curved happy crescent eyes
                drawSmilingEye(leftEyeCenter, isLeft = true, glowColor)
                drawSmilingEye(rightEyeCenter, isLeft = false, glowColor)
            } else {
                // Open expressive eyes
                val pupilShiftX = if (activeExpression == FaceExpression.THINKING) thinkingPupilShift else 0f
                val pupilShiftY = if (activeExpression == FaceExpression.THINKING) -8f else 0f

                drawOpenEye(
                    center = leftEyeCenter,
                    openFactor = eyeOpenFactor,
                    pupilOffset = Offset(pupilShiftX, pupilShiftY),
                    glowColor = glowColor,
                    amplitude = amplitude
                )
                drawOpenEye(
                    center = rightEyeCenter,
                    openFactor = eyeOpenFactor,
                    pupilOffset = Offset(pupilShiftX, pupilShiftY),
                    glowColor = glowColor,
                    amplitude = amplitude
                )
            }

            // --- 8. Nose (Cute minimal anime highlight) ---
            val noseY = center.y + faceRadius * 0.08f
            drawCircle(
                color = CrimsonRedLight.copy(alpha = 0.6f),
                radius = 1.8.dp.toPx(),
                center = Offset(center.x, noseY)
            )

            // --- 9. Mouth (Expressive lip-sync morphing) ---
            drawMouth(
                center = Offset(center.x, center.y + faceRadius * 0.38f),
                openFactor = mouthOpenFactor,
                expression = activeExpression,
                glowColor = glowColor
            )

            // --- 10. Cute Forehead Symbol / SUMAN Neuro-Core ---
            val coreY = center.y - faceRadius * 0.68f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CyanAccent, ElectricViolet, Color.Transparent)
                ),
                radius = 7.dp.toPx(),
                center = Offset(center.x, coreY)
            )
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = Offset(center.x, coreY)
            )
        }
    }
}

/**
 * Draws animated rotating neural particles for THINKING state.
 */
private fun DrawScope.drawThinkingNeuralHalo(center: Offset, rotationDeg: Float, radius: Float) {
    val numNodes = 6
    for (i in 0 until numNodes) {
        val angle = (rotationDeg + i * (360f / numNodes)) * (PI / 180.0)
        val nodeX = (center.x + radius * cos(angle)).toFloat()
        val nodeY = (center.y + radius * 0.7f * sin(angle)).toFloat()

        drawCircle(
            color = AccentGold.copy(alpha = 0.85f),
            radius = 3.5.dp.toPx(),
            center = Offset(nodeX, nodeY)
        )

        // Synaptic connector line
        val nextAngle = (rotationDeg + (i + 1) * (360f / numNodes)) * (PI / 180.0)
        val nextX = (center.x + radius * cos(nextAngle)).toFloat()
        val nextY = (center.y + radius * 0.7f * sin(nextAngle)).toFloat()

        drawLine(
            color = ElectricViolet.copy(alpha = 0.45f),
            start = Offset(nodeX, nodeY),
            end = Offset(nextX, nextY),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

/**
 * Draws futuristic cyber headset on sides of CaroLine's face.
 */
private fun DrawScope.drawHeadsetChassis(
    center: Offset,
    outerRadius: Float,
    glowColor: Color,
    amplitude: Float
) {
    val earHeight = 44.dp.toPx()
    val earWidth = 14.dp.toPx()
    val reactiveGlow = (amplitude * 20.dp.toPx()).coerceAtMost(12.dp.toPx())

    // Left earpiece
    val leftEar = Offset(center.x - outerRadius - 4.dp.toPx(), center.y - 6.dp.toPx())
    drawRoundRect(
        color = Color(0xFF1E0A1B),
        topLeft = Offset(leftEar.x - earWidth, leftEar.y - earHeight / 2),
        size = Size(earWidth, earHeight),
        cornerRadius = CornerRadius(6.dp.toPx())
    )
    drawRoundRect(
        color = glowColor,
        topLeft = Offset(leftEar.x - earWidth, leftEar.y - earHeight / 2),
        size = Size(earWidth, earHeight),
        cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )
    drawCircle(
        color = glowColor.copy(alpha = 0.9f),
        radius = 4.dp.toPx() + reactiveGlow,
        center = leftEar
    )

    // Right earpiece
    val rightEar = Offset(center.x + outerRadius + 4.dp.toPx(), center.y - 6.dp.toPx())
    drawRoundRect(
        color = Color(0xFF1E0A1B),
        topLeft = Offset(rightEar.x, rightEar.y - earHeight / 2),
        size = Size(earWidth, earHeight),
        cornerRadius = CornerRadius(6.dp.toPx())
    )
    drawRoundRect(
        color = glowColor,
        topLeft = Offset(rightEar.x, rightEar.y - earHeight / 2),
        size = Size(earWidth, earHeight),
        cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(width = 2.dp.toPx())
    )
    drawCircle(
        color = glowColor.copy(alpha = 0.9f),
        radius = 4.dp.toPx() + reactiveGlow,
        center = rightEar
    )
}

/**
 * Draws expressive eyebrows matching the current mood.
 */
private fun DrawScope.drawEyebrows(center: Offset, faceRadius: Float, expression: FaceExpression) {
    val browSpacing = faceRadius * 0.44f
    val browY = center.y - faceRadius * 0.38f
    val browWidth = 24.dp.toPx()

    val (leftTiltY, rightTiltY) = when (expression) {
        FaceExpression.SMILING -> Pair(-4.dp.toPx(), -4.dp.toPx()) // Uplifted happy
        FaceExpression.THINKING -> Pair(-8.dp.toPx(), 4.dp.toPx()) // One arched up, one down
        FaceExpression.LISTENING -> Pair(-2.dp.toPx(), -2.dp.toPx()) // Attentive
        FaceExpression.SPEAKING -> Pair(-5.dp.toPx(), -5.dp.toPx()) // Expressive
    }

    // Left eyebrow
    val leftBrow = Path().apply {
        moveTo(center.x - browSpacing - browWidth / 2, browY)
        quadraticTo(
            center.x - browSpacing,
            browY + leftTiltY,
            center.x - browSpacing + browWidth / 2,
            browY + 2.dp.toPx()
        )
    }
    drawPath(leftBrow, color = CrimsonRedLight, style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round))

    // Right eyebrow
    val rightBrow = Path().apply {
        moveTo(center.x + browSpacing - browWidth / 2, browY + 2.dp.toPx())
        quadraticTo(
            center.x + browSpacing,
            browY + rightTiltY,
            center.x + browSpacing + browWidth / 2,
            browY
        )
    }
    drawPath(rightBrow, color = CrimsonRedLight, style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round))
}

/**
 * Draws open luminous cyber eyes with gradient irises, pupils, and anime gleams.
 */
private fun DrawScope.drawOpenEye(
    center: Offset,
    openFactor: Float,
    pupilOffset: Offset,
    glowColor: Color,
    amplitude: Float
) {
    val eyeW = 26.dp.toPx()
    val eyeH = (28.dp.toPx() * openFactor).coerceAtLeast(2.dp.toPx())

    // Sclera / Eye White Base
    drawOval(
        color = Color(0xFFF7F3FF),
        topLeft = Offset(center.x - eyeW / 2, center.y - eyeH / 2),
        size = Size(eyeW, eyeH)
    )

    if (openFactor > 0.25f) {
        // Iris
        val irisRadius = 10.dp.toPx() * openFactor
        val irisCenter = Offset(center.x + pupilOffset.x, center.y + pupilOffset.y)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CyanAccent,
                    glowColor,
                    Color(0xFF0F0018)
                ),
                center = irisCenter,
                radius = irisRadius
            ),
            radius = irisRadius,
            center = irisCenter
        )

        // Inner Pupil with reactive dilation
        val pupilRadius = (4.5.dp.toPx() + amplitude * 3.dp.toPx()) * openFactor
        drawCircle(
            color = DeepObsidian,
            radius = pupilRadius,
            center = irisCenter
        )

        // Cyber Ring inside iris
        drawCircle(
            color = glowColor.copy(alpha = 0.7f),
            radius = irisRadius * 0.75f,
            center = irisCenter,
            style = Stroke(width = 1.dp.toPx())
        )

        // Big Sparkle Highlight
        drawCircle(
            color = Color.White,
            radius = 3.dp.toPx() * openFactor,
            center = Offset(irisCenter.x - 3.dp.toPx(), irisCenter.y - 3.dp.toPx())
        )

        // Small Sparkle Highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.8f),
            radius = 1.5.dp.toPx() * openFactor,
            center = Offset(irisCenter.x + 3.dp.toPx(), irisCenter.y + 3.dp.toPx())
        )
    }

    // Upper Eyelash line
    val lashPath = Path().apply {
        moveTo(center.x - eyeW / 2 - 2.dp.toPx(), center.y - eyeH / 2)
        quadraticTo(
            center.x,
            center.y - eyeH / 2 - 4.dp.toPx(),
            center.x + eyeW / 2 + 2.dp.toPx(),
            center.y - eyeH / 2
        )
    }
    drawPath(lashPath, color = Color(0xFF22081B), style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round))
}

/**
 * Draws happy curved crescent eyes (`^ ^`) for SMILING state.
 */
private fun DrawScope.drawSmilingEye(center: Offset, isLeft: Boolean, glowColor: Color) {
    val eyeW = 28.dp.toPx()
    val smilePath = Path().apply {
        moveTo(center.x - eyeW / 2, center.y + 2.dp.toPx())
        quadraticTo(
            center.x,
            center.y - 12.dp.toPx(),
            center.x + eyeW / 2,
            center.y + 2.dp.toPx()
        )
    }
    drawPath(smilePath, color = glowColor, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))

    // Cute double lash
    val lashX = if (isLeft) center.x - eyeW / 2 else center.x + eyeW / 2
    drawLine(
        color = glowColor,
        start = Offset(lashX, center.y + 2.dp.toPx()),
        end = Offset(lashX + (if (isLeft) -4.dp.toPx() else 4.dp.toPx()), center.y - 4.dp.toPx()),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round
    )
}

/**
 * Draws the morphing mouth for speech, smiling, thinking, and listening.
 */
private fun DrawScope.drawMouth(
    center: Offset,
    openFactor: Float,
    expression: FaceExpression,
    glowColor: Color
) {
    val mouthW = when (expression) {
        FaceExpression.SPEAKING -> (24.dp.toPx() + openFactor * 16.dp.toPx())
        FaceExpression.SMILING -> 32.dp.toPx()
        FaceExpression.THINKING -> 12.dp.toPx()
        FaceExpression.LISTENING -> 18.dp.toPx()
    }
    val mouthH = (openFactor * 26.dp.toPx()).coerceAtLeast(3.dp.toPx())

    if (openFactor > 0.15f) {
        // Open mouth (speaking / big smile)
        val mouthPath = Path().apply {
            moveTo(center.x - mouthW / 2, center.y)
            quadraticTo(
                center.x,
                center.y + mouthH,
                center.x + mouthW / 2,
                center.y
            )
            quadraticTo(
                center.x,
                center.y - (mouthH * 0.2f),
                center.x - mouthW / 2,
                center.y
            )
            close()
        }

        // Inner mouth cavity
        drawPath(
            mouthPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF380820), CrimsonRedDark)
            )
        )

        // Tongue / Teeth detail
        drawCircle(
            color = CrimsonRedLight.copy(alpha = 0.85f),
            radius = (mouthW * 0.28f).coerceAtMost(mouthH * 0.6f),
            center = Offset(center.x, center.y + mouthH * 0.5f)
        )

        // Outer lip contour
        drawPath(
            mouthPath,
            color = glowColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    } else {
        // Gentle resting smile line or thinking pursed curve
        val restingCurve = Path().apply {
            val tiltY = if (expression == FaceExpression.THINKING) 3.dp.toPx() else 5.dp.toPx()
            moveTo(center.x - mouthW / 2, center.y)
            quadraticTo(
                center.x,
                center.y + tiltY,
                center.x + mouthW / 2,
                center.y
            )
        }
        drawPath(
            restingCurve,
            color = CrimsonRedLight,
            style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
