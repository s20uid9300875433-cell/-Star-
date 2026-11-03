package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.live.ConversationMessage
import com.example.live.LiveState
import com.example.tools.PendingSensitiveAction
import com.example.ui.components.AudioWaveform
import com.example.ui.components.CaroLineAnimeAvatarView
import com.example.ui.components.JarvisHolographicCore
import com.example.ui.components.SettingsSheet
import com.example.ui.components.ToolToastBanner
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AlertRed
import com.example.ui.theme.HotMagentaPink
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepNavy
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisHudBorder
import com.example.ui.theme.JarvisHudSurface
import com.example.ui.theme.MutedVioletGray
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftLilac
import com.example.viewmodel.CaroLineViewModel

/**
 * CaroLine AI 2.9 – Ultimate Smart JARVIS Home Screen.
 *
 * Clean, minimal, futuristic anime-JARVIS design featuring ONLY the 5 primary controls:
 * 1. Settings icon (opens the unified Settings Dashboard with all secondary controls)
 * 2. AI avatar / animated AI core (`caroline_avatar_main.png` inside glowing cyan & golden yellow JARVIS rings)
 * 3. Listening and speaking status (Hindi status pill + real-time audio waveform)
 * 4. One conversation area (glassmorphism card displaying the current conversation exchange + optional command input)
 * 5. Microphone button (glowing cyan/golden voice activation button)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaroLineMainScreen(
    viewModel: CaroLineViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val audioAmplitude by viewModel.visualizerAmplitude.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSettings by remember { mutableStateOf(false) }
    var textCommandInput by remember { mutableStateOf("") }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.triggerCallSession()
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.triggerCallSession()
            }
        }
    }

    val avatarPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setCustomAvatar(uri)
        }
    }

    val displayEmotion = uiState.manualEmotionOverride ?: uiState.currentEmotion

    val backgroundGradient = when (uiState.selectedThemeName) {
        "Golden Cyber" -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0A0E17),
                Color(0xFF141824),
                Color(0xFF1A140B),
                Color(0xFF0A0E17)
            )
        )
        "Deep Space" -> Brush.verticalGradient(
            colors = listOf(
                Color(0xFF030712),
                Color(0xFF081026),
                Color(0xFF050B1A),
                Color(0xFF030712)
            )
        )
        else -> Brush.verticalGradient(
            colors = listOf(
                JarvisDeepNavy,
                Color(0xFF061329),
                Color(0xFF0B1C3A),
                JarvisDeepNavy
            )
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = Color.Transparent
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 640.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = if (isWideScreen) 720.dp else 560.dp)
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // =====================================================================
                // CONTROL 1: TOP HEADER WITH SINGLE SETTINGS ICON
                // =====================================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CAROLINE AI",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 2.sp
                                ),
                                color = PureWhite
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = AccentGold.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, AccentGold.copy(alpha = 0.75f))
                            ) {
                                Text(
                                    text = "2.9",
                                    fontSize = 11.sp,
                                    color = AccentGold,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "ULTIMATE SMART JARVIS • हिन्दी एआई असिस्टेंट",
                            fontSize = 11.sp,
                            color = JarvisCyan,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Single Settings Icon (houses all secondary functions)
                    Surface(
                        color = JarvisHudSurface.copy(alpha = 0.9f),
                        shape = CircleShape,
                        border = BorderStroke(1.2.dp, JarvisCyan.copy(alpha = 0.7f)),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { showSettings = true }
                            .testTag("top_settings_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Open CaroLine Settings Dashboard",
                                tint = AccentGold,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Optional Sensitive Action Confirmation Card (only visible when a sensitive action is pending)
                AnimatedVisibility(
                    visible = uiState.pendingSensitiveAction != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    uiState.pendingSensitiveAction?.let { pending ->
                        SensitiveActionConfirmationCard(
                            pendingAction = pending,
                            onConfirm = { viewModel.confirmSensitiveAction() },
                            onCancel = { viewModel.cancelSensitiveAction() }
                        )
                    }
                }

                // =====================================================================
                // CONTROL 2: AI AVATAR / ANIMATED AI CORE
                // =====================================================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 305.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isJarvisCoreMode) {
                        JarvisHolographicCore(
                            liveState = uiState.liveState,
                            amplitude = audioAmplitude,
                            activeProviderBadge = uiState.activeProviderBadge,
                            onCoreTapped = { viewModel.onAvatarTapped() }
                        )
                    } else {
                        CaroLineAnimeAvatarView(
                            liveState = uiState.liveState,
                            amplitude = audioAmplitude,
                            selectedAvatarStyle = uiState.avatarStyle,
                            customAvatarUri = uiState.customAvatarUri,
                            manualEmotion = displayEmotion,
                            onAvatarTapped = { viewModel.onAvatarTapped() }
                        )
                    }
                }

                // =====================================================================
                // CONTROL 3: LISTENING AND SPEAKING STATUS BAR
                // =====================================================================
                ListeningAndSpeakingStatusBar(
                    liveState = uiState.liveState,
                    amplitude = if (uiState.isMicMuted) 0f else audioAmplitude,
                    isMicMuted = uiState.isMicMuted,
                    activeWakeWord = uiState.wakeWordPhrase
                )

                // =====================================================================
                // CONTROL 4: ONE CONVERSATION AREA (GLASSMORPHISM CARD)
                // =====================================================================
                SingleConversationAreaCard(
                    speechSnippet = uiState.speechSnippet,
                    recentMessages = uiState.conversationHistory.takeLast(2),
                    textCommandInput = textCommandInput,
                    onTextCommandChange = { textCommandInput = it },
                    onSubmitTextCommand = {
                        if (textCommandInput.isNotBlank()) {
                            viewModel.onUserPrompt(textCommandInput)
                            textCommandInput = ""
                        }
                    },
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                )

                // =====================================================================
                // CONTROL 5: MICROPHONE BUTTON
                // =====================================================================
                PrimaryJarvisMicrophoneButton(
                    liveState = uiState.liveState,
                    isMicMuted = uiState.isMicMuted,
                    onMicClick = {
                        if (uiState.liveState == LiveState.DISCONNECTED || uiState.liveState == LiveState.ERROR) {
                            val hasAudioPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasAudioPermission) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.triggerCallSession()
                                }
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        } else {
                            viewModel.triggerCallSession()
                        }
                    }
                )
            }

            // Floating Tool Execution Banner
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                ToolToastBanner(
                    event = uiState.activeToolEvent,
                    onDismiss = { viewModel.dismissActiveToolToast() }
                )
            }

            // Error Snackbar
            if (uiState.errorMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("ठीक है", color = PureWhite)
                        }
                    },
                    containerColor = AlertRed.copy(alpha = 0.94f),
                    contentColor = PureWhite
                ) {
                    Text(uiState.errorMessage ?: "")
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            selectedAiProvider = uiState.selectedAiProvider,
            autoFailoverEnabled = uiState.autoFailoverEnabled,
            providerConfigs = uiState.providerConfigs,
            providerTestResults = uiState.providerTestResults,
            testingProvider = uiState.testingProvider,
            onSelectAiProvider = { viewModel.selectAiProvider(it) },
            onToggleAutoFailover = { viewModel.toggleAutoFailover() },
            onSaveProviderConfig = { type, key, endpoint, model ->
                viewModel.updateProviderConfig(type, key, endpoint, model)
            },
            onRemoveProviderKey = { viewModel.removeProviderCredential(it) },
            onTestProviderConnection = { viewModel.testProviderConnection(it) },
            // Female Voice Settings 2.9
            selectedVoice = uiState.selectedVoice,
            voicePitch = uiState.voicePitch,
            voiceSpeed = uiState.voiceSpeed,
            isVoiceMuted = uiState.isVoiceMuted,
            useOpenAiSpeechTts = uiState.useOpenAiTts,
            openAiVoiceName = uiState.openAiTtsVoice,
            onSelectVoice = { viewModel.setSelectedVoice(it) },
            onVoicePitchChange = { viewModel.setVoicePitch(it) },
            onVoiceSpeedChange = { viewModel.setVoiceSpeed(it) },
            onToggleVoiceMute = { viewModel.toggleVoiceMute() },
            onToggleOpenAiSpeechTts = { viewModel.setUseOpenAiTts(!uiState.useOpenAiTts) },
            onSelectOpenAiVoiceName = { viewModel.setOpenAiTtsVoice(it) },
            onPreviewVoice = { viewModel.previewFemaleVoice() },
            // Language & Wake Word
            activeLanguage = uiState.activeLanguage,
            isLanguageLocked = uiState.isLanguageLocked,
            wakeWordEnabled = uiState.isWakeWordEnabled,
            selectedWakeWord = uiState.wakeWordPhrase,
            isHandsFreeMode = uiState.isHandsFreeMode,
            isBackgroundServiceEnabled = uiState.isBackgroundServiceEnabled,
            isForegroundServiceRunning = uiState.isForegroundServiceRunning,
            onChangeLanguageWithPin = { pin, lang -> viewModel.changeLanguageWithPin(pin, lang) },
            onLockLanguageToHindi = { viewModel.lockLanguageToHindi() },
            onToggleWakeWord = { viewModel.setWakeWordEnabled(!uiState.isWakeWordEnabled) },
            onSelectWakeWord = { viewModel.setWakeWordPhrase(it) },
            onToggleHandsFree = { viewModel.toggleHandsFree() },
            onToggleBackgroundService = { viewModel.toggleBackgroundService() },
            // Music & Video Preferences
            preferredMusicService = uiState.preferredMusicService,
            builtInMusicFallbackEnabled = uiState.enableBuiltInMusicFallback,
            onSelectPreferredMusicService = { viewModel.setPreferredMusicService(it) },
            onToggleBuiltInMusicFallback = { viewModel.setEnableBuiltInMusicFallback(!uiState.enableBuiltInMusicFallback) },
            onExecuteQuickCommand = { cmd -> viewModel.onUserPrompt(cmd) },
            // Smart Features
            smartMemories = uiState.smartMemories,
            dailyReminders = uiState.dailyReminders,
            smartRoutines = uiState.smartRoutines,
            onAddSmartMemory = { viewModel.addSmartMemory(it) },
            onClearSmartMemories = { viewModel.clearSmartMemories() },
            onAddDailyReminder = { title, time -> viewModel.addDailyReminder(title, time) },
            onExecuteRoutine = { viewModel.executeSmartRoutine(it) },
            // Apps & History
            appCatalog = uiState.appCatalog,
            appSearchQuery = uiState.appSearchQuery,
            onAppSearchQueryChange = { viewModel.setAppSearchQuery(it) },
            onLaunchApp = { viewModel.launchAppFromCatalog(it) },
            conversationHistory = uiState.conversationHistory,
            onClearConversationHistory = { viewModel.clearConversationHistory() },
            toolHistory = uiState.toolHistory,
            // Theme & Avatar
            selectedThemeName = uiState.selectedThemeName,
            isJarvisCoreMode = uiState.isJarvisCoreMode,
            avatarStyle = uiState.avatarStyle,
            onSelectTheme = { viewModel.setSelectedThemeName(it) },
            onToggleJarvisCoreMode = { viewModel.toggleJarvisCoreMode() },
            onSelectAvatarStyle = { viewModel.setAvatarStyle(it) },
            onUploadCustomAvatar = {
                avatarPhotoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            // Legacy compatibility
            primaryApiKey = uiState.primaryApiKey,
            backupApiKey = uiState.backupApiKey,
            apiMode = uiState.apiMode,
            activeEngine = uiState.activeEngine,
            onSavePrimaryKey = { viewModel.setPrimaryApiKey(it) },
            onSaveBackupKey = { viewModel.setBackupApiKey(it) },
            onSelectApiMode = { viewModel.setApiMode(it) },
            onLockLanguageToBengali = { viewModel.lockLanguageToBengali() },
            onDismiss = { showSettings = false }
        )
    }
}

/**
 * Control 3: Listening and Speaking Status Pill + Live Waveform.
 */
@Composable
private fun ListeningAndSpeakingStatusBar(
    liveState: LiveState,
    amplitude: Float,
    isMicMuted: Boolean,
    activeWakeWord: String
) {
    val statusText = when {
        isMicMuted && liveState != LiveState.DISCONNECTED -> "माइक्रोफ़ोन म्यूट है (Muted)"
        liveState == LiveState.SPEAKING -> "कैरोलाइन बोल रही है (Speaking in Hindi)..."
        liveState == LiveState.LISTENING -> "कैरोलाइन सुन रही है • बोलिए बॉस ($activeWakeWord)"
        liveState == LiveState.THINKING -> "कैरोलाइन सोच रही है (Processing)..."
        liveState == LiveState.CONNECTING || liveState == LiveState.RECONNECTING -> "कनेक्ट हो रहा है..."
        liveState == LiveState.ERROR -> "सिस्टम तैयार है — फिर से बोलें"
        else -> "वॉइस मोड तैयार है • माइक दबाएं या बोलें \"$activeWakeWord\""
    }

    val statusAccent = when (liveState) {
        LiveState.SPEAKING -> AccentGold
        LiveState.LISTENING -> JarvisCyan
        LiveState.THINKING, LiveState.CONNECTING -> JarvisElectricBlue
        LiveState.ERROR -> AlertRed
        else -> JarvisCyan.copy(alpha = 0.75f)
    }

    Surface(
        color = JarvisHudSurface.copy(alpha = 0.88f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, statusAccent.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("listening_speaking_status_bar")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = statusAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = PureWhite,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            AudioWaveform(
                liveState = liveState,
                amplitude = amplitude,
                modifier = Modifier
                    .width(72.dp)
                    .height(20.dp)
            )
        }
    }
}

/**
 * Control 4: Single Unified Glassmorphism Conversation Area.
 */
@Composable
private fun SingleConversationAreaCard(
    speechSnippet: String,
    recentMessages: List<ConversationMessage>,
    textCommandInput: String,
    onTextCommandChange: (String) -> Unit,
    onSubmitTextCommand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = JarvisHudSurface.copy(alpha = 0.86f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.horizontalGradient(
                listOf(
                    JarvisCyan.copy(alpha = 0.75f),
                    AccentGold.copy(alpha = 0.65f),
                    JarvisCyan.copy(alpha = 0.75f)
                )
            )
        ),
        shadowElevation = 12.dp,
        modifier = modifier.testTag("single_conversation_area")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (recentMessages.isNotEmpty()) {
                val lastUserMsg = recentMessages.lastOrNull { it.role == "user" }
                if (lastUserMsg != null) {
                    Text(
                        text = "बॉस: \"${lastUserMsg.text}\"",
                        fontSize = 12.sp,
                        color = JarvisCyan,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            Text(
                text = speechSnippet,
                style = MaterialTheme.typography.bodyMedium,
                color = PureWhite,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("speech_bubble_card")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Subtle inline command input inside the same single conversation card
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = textCommandInput,
                    onValueChange = onTextCommandChange,
                    placeholder = {
                        Text(
                            text = "हिंदी, Hinglish या English में कमांड लिखें (जैसे: गाना चलाओ)...",
                            fontSize = 11.sp,
                            color = MutedVioletGray
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hud_command_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisHudBorder.copy(alpha = 0.45f),
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedContainerColor = JarvisDeepNavy.copy(alpha = 0.7f),
                        unfocusedContainerColor = JarvisDeepNavy.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                IconButton(
                    onClick = onSubmitTextCommand,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(JarvisCyan, JarvisElectricBlue)
                            )
                        )
                        .testTag("hud_command_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Command",
                        tint = Color.Black
                    )
                }
            }
        }
    }
}

/**
 * Control 5: Glowing Cyan & Golden Yellow JARVIS Microphone Button.
 */
@Composable
private fun PrimaryJarvisMicrophoneButton(
    liveState: LiveState,
    isMicMuted: Boolean,
    onMicClick: () -> Unit
) {
    val isSessionActive = liveState != LiveState.DISCONNECTED && liveState != LiveState.ERROR
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isSessionActive) 1.08f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )

    val ringBrush = if (isSessionActive) {
        Brush.sweepGradient(listOf(AccentGold, JarvisCyan, AccentGold))
    } else {
        Brush.sweepGradient(listOf(JarvisCyan, JarvisElectricBlue, AccentGold, JarvisCyan))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .scale(pulseScale)
                .shadow(
                    elevation = if (isSessionActive) 22.dp else 12.dp,
                    shape = CircleShape,
                    spotColor = if (isSessionActive) AccentGold else JarvisCyan
                )
                .clip(CircleShape)
                .background(
                    if (isSessionActive) {
                        Brush.radialGradient(
                            colors = listOf(
                                AccentGold.copy(alpha = 0.92f),
                                JarvisCyan.copy(alpha = 0.85f)
                            )
                        )
                    } else {
                        Brush.radialGradient(
                            colors = listOf(
                                JarvisCyan.copy(alpha = 0.9f),
                                JarvisElectricBlue.copy(alpha = 0.9f)
                            )
                        )
                    }
                )
                .border(width = 2.5.dp, brush = ringBrush, shape = CircleShape)
                .clickable(onClick = onMicClick)
                .testTag("start_end_call_button")
        ) {
            Icon(
                imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isSessionActive) "Stop CaroLine Voice Session" else "Start CaroLine Voice Session",
                tint = Color(0xFF0A0E17),
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (isSessionActive) "सक्रिय • रोकने के लिए टैप करें" else "बोलने के लिए माइक दबाएं",
            fontSize = 11.sp,
            color = if (isSessionActive) AccentGold else JarvisCyan,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SensitiveActionConfirmationCard(
    pendingAction: PendingSensitiveAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = Color(0xFF2B1228),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, HotMagentaPink),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("sensitive_action_confirmation_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "Sensitive Action",
                    tint = HotMagentaPink,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pendingAction.titleBn,
                    style = MaterialTheme.typography.titleSmall,
                    color = PureWhite,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = pendingAction.descriptionBn,
                style = MaterialTheme.typography.bodySmall,
                color = SoftLilac
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisEmerald),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("confirm_sensitive_action_button")
                ) {
                    Text("हाँ, अनुमति दें", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onCancel,
                    border = BorderStroke(1.dp, MutedVioletGray),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cancel_sensitive_action_button")
                ) {
                    Text("रद्द करें", color = PureWhite, fontSize = 12.sp)
                }
            }
        }
    }
}
