package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.live.LiveState
import com.example.ui.components.AudioWaveform
import com.example.ui.components.AvatarEmotion
import com.example.ui.components.AvatarStyle
import com.example.ui.components.CaroLineAnimeAvatarView
import com.example.ui.components.HelpEducationSheet
import com.example.ui.components.SettingsSheet
import com.example.ui.components.ToolToastBanner
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CrimsonRedLight
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.DeepSurface
import com.example.ui.theme.DeepSurfaceVariant
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.ActiveEngine
import com.example.viewmodel.AvatarMode
import com.example.viewmodel.CaroLineViewModel

@Composable
fun CaroLineMainScreen(
    viewModel: CaroLineViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val visualizerAmp by viewModel.visualizerAmplitude.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
        if (isGranted) {
            viewModel.startSession()
        }
    }

    // Zero-permission Android Photo Picker for uploading custom avatar
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setCustomAvatar(uri)
        }
    }

    // Camera action launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Returned from camera
    }

    val quickPrompts = listOf(
        "👋 Hey CaroLine!",
        "🔥 Boss ko roast karo!",
        "💖 Boss ko tareef chahiye",
        "🎬 YouTube kholo",
        "🔦 Flashlight on karo",
        "⚡ Battery kitni bachi hai?",
        "😏 Are you flirting with me?",
        "🍕 Aaj khane me kya lu, Boss?",
        "🤫 Ek secret batao"
    )

    val isCallActive = uiState.liveState != LiveState.DISCONNECTED && uiState.liveState != LiveState.ERROR

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF240638), // Rich futuristic purple
                        Color(0xFF13031F),
                        DeepObsidian,
                        Color(0xFF180424)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- TOP HEADER (CAROLINE AI • DEVELOPED BY BOSS) ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
            ) {
                // Left: Brand title & Subtitle
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    when (uiState.liveState) {
                                        LiveState.SPEAKING -> CrimsonRedLight
                                        LiveState.LISTENING -> CyanAccent
                                        LiveState.CONNECTING, LiveState.THINKING -> AccentGold
                                        LiveState.RECONNECTING -> Color(0xFFFF9800)
                                        LiveState.ERROR -> Color.Red
                                        LiveState.DISCONNECTED -> Color.Gray
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CAROLINE AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (uiState.activeEngine) {
                                ActiveEngine.PRIMARY_GEMINI_LIVE -> CrimsonRed.copy(alpha = 0.25f)
                                ActiveEngine.BACKUP_SECONDARY_AI -> CyanAccent.copy(alpha = 0.25f)
                                ActiveEngine.LOCAL_SUMAN_BRAIN -> ElectricViolet.copy(alpha = 0.25f)
                            },
                            border = BorderStroke(
                                1.dp,
                                when (uiState.activeEngine) {
                                    ActiveEngine.PRIMARY_GEMINI_LIVE -> CrimsonRed.copy(alpha = 0.6f)
                                    ActiveEngine.BACKUP_SECONDARY_AI -> CyanAccent.copy(alpha = 0.6f)
                                    ActiveEngine.LOCAL_SUMAN_BRAIN -> ElectricViolet.copy(alpha = 0.6f)
                                }
                            )
                        ) {
                            Text(
                                text = when (uiState.activeEngine) {
                                    ActiveEngine.PRIMARY_GEMINI_LIVE -> "GEMINI LIVE 24kHz"
                                    ActiveEngine.BACKUP_SECONDARY_AI -> "BACKUP AI"
                                    ActiveEngine.LOCAL_SUMAN_BRAIN -> "SUMAN BRAIN"
                                },
                                color = when (uiState.activeEngine) {
                                    ActiveEngine.PRIMARY_GEMINI_LIVE -> CrimsonRedLight
                                    ActiveEngine.BACKUP_SECONDARY_AI -> CyanAccent
                                    ActiveEngine.LOCAL_SUMAN_BRAIN -> AccentGold
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Your AI Companion • Developed by ${uiState.developerName}",
                        color = Color(0xFFFF75B5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 18.dp, top = 2.dp)
                    )
                }

                // Right: Action Icons (Camera, Upload, Help, Settings)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Camera Button
                    IconButton(
                        onClick = {
                            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                            if (intent.resolveActivity(context.packageManager) != null) {
                                cameraLauncher.launch(intent)
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Upload Avatar Button (Photo Picker)
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("upload_avatar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Upload Avatar",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Help & Education Button
                    IconButton(
                        onClick = { showHelp = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("help_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "Help & Education",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Settings Button
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // Tool Action Toast
            ToolToastBanner(
                event = uiState.activeToolEvent,
                onDismiss = { viewModel.dismissActiveToolToast() }
            )

            // Error banner
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF4A101D),
                    border = BorderStroke(1.dp, CrimsonRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("error_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = CrimsonRedLight,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // --- CENTER STAGE: LARGE ANIMATED ANIME FEMALE AVATAR ---
            CaroLineAnimeAvatarView(
                liveState = uiState.liveState,
                amplitude = visualizerAmp,
                selectedAvatarStyle = uiState.avatarStyle,
                customAvatarUri = uiState.customAvatarUri,
                manualEmotion = uiState.manualEmotionOverride,
                onAvatarTapped = { viewModel.onAvatarTapped() },
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Speech / Thought Bubble (Hindi + English + Hinglish)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = DeepSurfaceVariant.copy(alpha = 0.88f),
                border = BorderStroke(
                    1.dp,
                    if (uiState.liveState == LiveState.SPEAKING) CrimsonRed.copy(alpha = 0.6f)
                    else DarkBorder
                ),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .clickable {
                        // Tapping bubble interrupts speech
                        if (uiState.liveState == LiveState.SPEAKING) {
                            viewModel.handleUserInterruption()
                        }
                    }
                    .testTag("speech_bubble_surface")
            ) {
                Text(
                    text = uiState.speechSnippet,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Real-time Voice Waveform Equalizer (Inspired by Screen 3 of reference screenshot)
            AudioWaveform(
                liveState = uiState.liveState,
                amplitude = visualizerAmp,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .clickable {
                        if (uiState.liveState == LiveState.SPEAKING) {
                            viewModel.handleUserInterruption()
                        }
                    }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // --- BOTTOM CONTROL CARD / DRAWER (Inspired by Phone 2 of Reference Screenshot) ---
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = DeepSurface.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color(0xFF4A1842)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bottom_avatar_control_drawer")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar Thumbnails Selector (Gothic Cyber, Pink Neon, Classic Anime, Custom Upload)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AVATAR STYLES",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "Tap to switch",
                            color = CyanAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Gothic Cyber (Phone 1 Avatar)
                        AvatarThumbnailCard(
                            imageRes = R.drawable.caroline_avatar_main,
                            isSelected = uiState.avatarStyle == AvatarStyle.GOTHIC_CYBER,
                            label = "Gothic",
                            onClick = { viewModel.setAvatarStyle(AvatarStyle.GOTHIC_CYBER) },
                            modifier = Modifier.weight(1f)
                        )

                        // 2. Pink Neon (Phone 2 Avatar)
                        AvatarThumbnailCard(
                            imageRes = R.drawable.caroline_ref_avatar,
                            isSelected = uiState.avatarStyle == AvatarStyle.PINK_NEON,
                            label = "Pink Neon",
                            onClick = { viewModel.setAvatarStyle(AvatarStyle.PINK_NEON) },
                            modifier = Modifier.weight(1f)
                        )

                        // 3. Classic Anime
                        AvatarThumbnailCard(
                            imageRes = R.drawable.mahi_avatar,
                            isSelected = uiState.avatarStyle == AvatarStyle.CLASSIC_ANIME,
                            label = "Classic",
                            onClick = { viewModel.setAvatarStyle(AvatarStyle.CLASSIC_ANIME) },
                            modifier = Modifier.weight(1f)
                        )

                        // 4. Custom Uploaded
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (uiState.avatarStyle == AvatarStyle.CUSTOM_UPLOADED) CrimsonRed.copy(alpha = 0.2f) else DeepSurfaceVariant,
                            border = BorderStroke(
                                1.5.dp,
                                if (uiState.avatarStyle == AvatarStyle.CUSTOM_UPLOADED) CrimsonRed else DarkBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clickable {
                                    if (uiState.customAvatarUri != null) {
                                        viewModel.setAvatarStyle(AvatarStyle.CUSTOM_UPLOADED)
                                    } else {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                }
                                .testTag("avatar_thumbnail_custom")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (uiState.customAvatarUri != null) {
                                    AsyncImage(
                                        model = uiState.customAvatarUri,
                                        contentDescription = "Custom Avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.AddPhotoAlternate,
                                            contentDescription = "Upload",
                                            tint = CyanAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Upload",
                                            color = TextSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Expression Selector (Listening, Smiling, Thinking, Speaking, Happy, Surprised)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Pair(AvatarEmotion.LISTENING, "👂 Listen"),
                            Pair(AvatarEmotion.SMILING, "😊 Smile"),
                            Pair(AvatarEmotion.THINKING, "🤔 Think"),
                            Pair(AvatarEmotion.SPEAKING, "🗣️ Speak"),
                            Pair(AvatarEmotion.HAPPY, "💖 Flirt"),
                            Pair(AvatarEmotion.SURPRISED, "😲 Wow")
                        ).forEach { (emotion, label) ->
                            val isSelected = (uiState.manualEmotionOverride ?: uiState.currentEmotion) == emotion
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) CrimsonRed.copy(alpha = 0.25f) else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) CrimsonRed else DarkBorder
                                ),
                                modifier = Modifier
                                    .clickable {
                                        if (uiState.manualEmotionOverride == emotion) {
                                            viewModel.setManualEmotion(null)
                                        } else {
                                            viewModel.setManualEmotion(emotion)
                                        }
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) CrimsonRedLight else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- LARGE PINK/PURPLE GLOWING CALL BUTTON (MATCHING REFERENCE SCREENSHOT) ---
                    Button(
                        onClick = {
                            if (!hasRecordPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                viewModel.triggerCallSession()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .shadow(
                                elevation = if (isCallActive) 20.dp else 10.dp,
                                shape = RoundedCornerShape(26.dp),
                                spotColor = if (isCallActive) CrimsonRed else ElectricViolet,
                                ambientColor = Color(0xFFFF2A85)
                            )
                            .background(
                                Brush.horizontalGradient(
                                    colors = if (isCallActive) {
                                        listOf(CrimsonRed, Color(0xFFFF2A85), ElectricViolet)
                                    } else {
                                        listOf(Color(0xFFE0218A), Color(0xFFB5179E), Color(0xFF7209B7))
                                    }
                                ),
                                shape = RoundedCornerShape(26.dp)
                            )
                            .testTag("main_call_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isCallActive) Icons.Default.CallEnd else Icons.Default.Call,
                                contentDescription = if (isCallActive) "End Call" else "Start Call",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = when (uiState.liveState) {
                                    LiveState.SPEAKING -> "SPEAKING (TAP TO INTERRUPT)"
                                    LiveState.LISTENING -> "LIVE CALL ACTIVE • END CALL"
                                    LiveState.CONNECTING -> "CONNECTING TO CAROLINE..."
                                    LiveState.THINKING -> "SUMAN BRAIN PROCESSING..."
                                    LiveState.RECONNECTING -> "RECONNECTING LIVE CALL..."
                                    LiveState.ERROR -> "RETRY LIVE CALL"
                                    LiveState.DISCONNECTED -> "START LIVE CALL WITH CAROLINE"
                                },
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary Quick Controls: Hands-Free & Mic Mute toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hands-free toggle chip
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (uiState.isHandsFreeMode) CyanAccent.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (uiState.isHandsFreeMode) CyanAccent else DarkBorder
                            ),
                            modifier = Modifier.clickable { viewModel.toggleHandsFree() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hearing,
                                    contentDescription = "Hands-free",
                                    tint = if (uiState.isHandsFreeMode) CyanAccent else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.isHandsFreeMode) "Hands-Free: ON" else "Hands-Free: OFF",
                                    color = if (uiState.isHandsFreeMode) CyanAccent else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Mute button chip
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (uiState.isMicMuted) AccentGold.copy(alpha = 0.15f) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (uiState.isMicMuted) AccentGold else DarkBorder
                            ),
                            modifier = Modifier.clickable { viewModel.toggleMute() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Mic",
                                    tint = if (uiState.isMicMuted) AccentGold else TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.isMicMuted) "Mic Muted" else "Mic Active",
                                    color = if (uiState.isMicMuted) AccentGold else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- QUICK ACTION SASSY HINGLISH PROMPTS FOR BOSS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPrompts.forEach { prompt ->
                    AssistChip(
                        onClick = { viewModel.onUserPrompt(prompt) },
                        label = {
                            Text(
                                text = prompt,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = DeepSurfaceVariant,
                            labelColor = TextPrimary
                        ),
                        border = BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.testTag("quick_prompt_${prompt.take(6).trim()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Settings Modal Sheet
        if (showSettings) {
            SettingsSheet(
                primaryApiKey = uiState.primaryApiKey,
                backupApiKey = uiState.backupApiKey,
                apiMode = uiState.apiMode,
                selectedVoice = uiState.selectedVoice,
                isHandsFreeMode = uiState.isHandsFreeMode,
                avatarMode = AvatarMode.PROCEDURAL_ANIMATED_FACE,
                toolHistory = uiState.toolHistory,
                onSavePrimaryApiKey = { newKey -> viewModel.setPrimaryApiKey(newKey) },
                onSaveBackupApiKey = { newKey -> viewModel.setBackupApiKey(newKey) },
                onChangeApiMode = { mode -> viewModel.setApiMode(mode) },
                onSelectVoice = { voice -> viewModel.setSelectedVoice(voice) },
                onToggleHandsFree = { viewModel.toggleHandsFree() },
                onToggleAvatarMode = { },
                onDismiss = { showSettings = false }
            )
        }

        // Help & Education Sheet
        if (showHelp) {
            HelpEducationSheet(
                onDismiss = { showHelp = false }
            )
        }
    }
}

@Composable
private fun AvatarThumbnailCard(
    imageRes: Int,
    isSelected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) CrimsonRed.copy(alpha = 0.2f) else DeepSurfaceVariant,
        border = BorderStroke(
            1.5.dp,
            if (isSelected) CrimsonRed else DarkBorder
        ),
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CrimsonRed.copy(alpha = 0.25f))
                )
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                )
            }
        }
    }
}

// Backward compatibility alias
@Composable
fun MahiMainScreen(
    viewModel: CaroLineViewModel,
    modifier: Modifier = Modifier
) {
    CaroLineMainScreen(viewModel, modifier)
}
