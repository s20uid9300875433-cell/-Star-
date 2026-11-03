package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.filled.Lock
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

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        hasRecordPermission = audioGranted
        if (audioGranted) {
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
    }

    val quickPrompts = listOf(
        "👋 হ্যালো ক্যারোলাইন",
        "🎧 ক্যারোলাইন, আমার কথা শোনো",
        "⏰ সময় কত?",
        "🌤️ আজকের আবহাওয়া বলো",
        "🔋 মোবাইলের ব্যাটারি কত?",
        "🔁 আবার কথা বলো",
        "🔦 ফ্ল্যাশলাইট চালু করো",
        "🛑 ভয়েস মোড বন্ধ করো"
    )

    val isCallActive = uiState.liveState != LiveState.DISCONNECTED && uiState.liveState != LiveState.ERROR

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF240638),
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
            // --- TOP HEADER (CaroLine AI • GREAT V2.1) ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
            ) {
                // Left: Brand title & Version Badge (Zero developer or brain branding)
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
                            text = "CaroLine AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CrimsonRed.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = uiState.appVersion,
                                color = AccentGold,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Status Bar Row: Language Lock + Engine + Background Service
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 18.dp, top = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Language Lock",
                                tint = CyanAccent,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (uiState.isLanguageLocked) "ভাষা: বাংলা (লক করা)" else "ভাষা: ${uiState.activeLanguage}",
                                color = CyanAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = "•",
                            color = TextMuted,
                            fontSize = 10.sp
                        )

                        Text(
                            text = if (uiState.isForegroundServiceRunning) "BG সার্ভিস সচল" else "BG সার্ভিস প্রস্তুত",
                            color = if (uiState.isForegroundServiceRunning) Color(0xFF4CAF50) else Color(0xFFFF75B5),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Right: Action Icons (Camera, Upload, Help, Settings)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                        .clickable { viewModel.clearError() }
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

            // Bengali Speech / Thought Bubble
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

            // Real-time Voice Waveform Equalizer
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

            // --- BOTTOM CONTROL CARD / DRAWER ---
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
                            text = when (uiState.activeEngine) {
                                ActiveEngine.PRIMARY_GEMINI_LIVE -> "GEMINI LIVE 24kHz"
                                ActiveEngine.BACKUP_SECONDARY_AI -> "BACKUP AI ENGINE"
                                ActiveEngine.BENGALI_VOICE_ENGINE -> "বাংলা ভয়েস ইঞ্জিন"
                            },
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
                        AvatarThumbnailCard(
                            imageRes = R.drawable.caroline_avatar_main,
                            isSelected = uiState.avatarStyle == AvatarStyle.GOTHIC_CYBER,
                            label = "Gothic",
                            onClick = { viewModel.setAvatarStyle(AvatarStyle.GOTHIC_CYBER) },
                            modifier = Modifier.weight(1f)
                        )

                        AvatarThumbnailCard(
                            imageRes = R.drawable.caroline_ref_avatar,
                            isSelected = uiState.avatarStyle == AvatarStyle.PINK_NEON,
                            label = "Pink Neon",
                            onClick = { viewModel.setAvatarStyle(AvatarStyle.PINK_NEON) },
                            modifier = Modifier.weight(1f)
                        )

                        AvatarThumbnailCard(
                            imageRes = R.drawable.mahi_avatar,
                            isSelected = uiState.avatarStyle == AvatarStyle.CLASSIC_ANIME,
                            label = "Classic",
                            onClick = { viewModel.setAvatarStyle(AvatarStyle.CLASSIC_ANIME) },
                            modifier = Modifier.weight(1f)
                        )

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

                    // Expression Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Pair(AvatarEmotion.LISTENING, "👂 শুনছে"),
                            Pair(AvatarEmotion.SMILING, "😊 হাসি"),
                            Pair(AvatarEmotion.THINKING, "🤔 চিন্তা"),
                            Pair(AvatarEmotion.SPEAKING, "🗣️ কথা বলছে"),
                            Pair(AvatarEmotion.HAPPY, "💖 আনন্দ"),
                            Pair(AvatarEmotion.SURPRISED, "😲 অবাক")
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

                    // --- LARGE PINK/PURPLE GLOWING VOICE ACTIVATION BUTTON ---
                    Button(
                        onClick = {
                            val currentAudioPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            hasRecordPermission = currentAudioPerm

                            if (!currentAudioPerm) {
                                val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    arrayOf(Manifest.permission.RECORD_AUDIO)
                                }
                                permissionsLauncher.launch(perms)
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
                                contentDescription = if (isCallActive) "End Voice Mode" else "Start Voice Mode",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = when (uiState.liveState) {
                                    LiveState.SPEAKING -> "ক্যারোলাইন কথা বলছে • থামাতে ট্যাপ করুন"
                                    LiveState.LISTENING -> "ভয়েস মোড সচল • বন্ধ করতে ট্যাপ করুন"
                                    LiveState.CONNECTING -> "ক্যারোলাইনের সাথে সংযোগ হচ্ছে..."
                                    LiveState.THINKING -> "ক্যারোলাইন উত্তর তৈরি করছে..."
                                    LiveState.RECONNECTING -> "পুনরায় সংযোগ করা হচ্ছে..."
                                    LiveState.ERROR -> "পুনরায় ভয়েস মোড চালু করুন"
                                    LiveState.DISCONNECTED -> "ক্যারোলাইনের সাথে কথা শুরু করুন (VOICE CALL)"
                                },
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Secondary Quick Controls: Auto-Mic Hands-Free & Mic Mute toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                    contentDescription = "Auto-Mic",
                                    tint = if (uiState.isHandsFreeMode) CyanAccent else TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.isHandsFreeMode) "অটো-মাইক: চালু" else "অটো-মাইক: বন্ধ",
                                    color = if (uiState.isHandsFreeMode) CyanAccent else TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

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
                                    text = if (uiState.isMicMuted) "মাইক মিউট" else if (uiState.isMicHardwareVerified) "মাইক্রোফোন সক্রিয়" else "মাইক্রোফোন প্রস্তুত",
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

            // --- QUICK BENGALI VOICE COMMAND CHIPS ---
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
                isBackgroundServiceEnabled = uiState.isBackgroundServiceEnabled,
                activeLanguage = uiState.activeLanguage,
                isLanguageLocked = uiState.isLanguageLocked,
                avatarMode = AvatarMode.PROCEDURAL_ANIMATED_FACE,
                toolHistory = uiState.toolHistory,
                onSavePrimaryApiKey = { newKey -> viewModel.setPrimaryApiKey(newKey) },
                onSaveBackupApiKey = { newKey -> viewModel.setBackupApiKey(newKey) },
                onChangeApiMode = { mode -> viewModel.setApiMode(mode) },
                onSelectVoice = { voice -> viewModel.setSelectedVoice(voice) },
                onToggleHandsFree = { viewModel.toggleHandsFree() },
                onToggleBackgroundService = { viewModel.toggleBackgroundService() },
                onChangeLanguageWithPin = { pin, lang -> viewModel.changeLanguageWithPin(pin, lang) },
                onLockLanguageToBengali = { viewModel.lockLanguageToBengali() },
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
