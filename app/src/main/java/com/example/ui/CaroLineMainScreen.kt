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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInFull
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.live.ConversationMessage
import com.example.live.LiveState
import com.example.tools.DeviceContactItem
import com.example.tools.MessageDraftPreview
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
import kotlin.math.roundToInt

/**
 * CaroLine AI A3.2 – Ultimate Android Smart JARVIS Home Screen.
 *
 * All interface labels, buttons, and controls are in English (Requirement B), while
 * CaroLine speaks in the owner's chosen spoken language (Hindi by default, Bengali, or English).
 * Includes:
 * - Header with A3.2 badge, active RAM Performance Profile badge, Floating Bubble toggle, and Settings button
 * - AI Avatar / Holographic Core
 * - Interactive Contact Selection Card (Requirement E)
 * - Smart Message Preview Confirmation Card (Requirement G)
 * - Listening & Speaking Status Bar
 * - Conversation & Command Card
 * - Primary Voice Activation Microphone Button
 * - Movable Floating CaroLine Bubble & Compact Floating Window (Requirement F)
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

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onUserPrompt("Find the contact ending in 45")
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
                // CONTROL 1: TOP HEADER WITH A3.2 BADGE, PERFORMANCE MODE & SETTINGS
                // =====================================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                                    text = "A3.2",
                                    fontSize = 11.sp,
                                    color = AccentGold,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = JarvisCyan.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = uiState.performanceProfile.displayName.uppercase(),
                                    fontSize = 9.sp,
                                    color = JarvisCyan,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "WAKE: \"${uiState.wakeWordPhrase}\" • ${if (uiState.isBatterySavingListeningMode) "BATTERY-SAVING MODE" else "ACTIVE MODE"} • ${uiState.activeLanguage}",
                            fontSize = 10.sp,
                            color = JarvisCyan,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick toggle for Floating Window
                        Surface(
                            color = if (uiState.isFloatingWindowExpanded) JarvisCyan.copy(alpha = 0.25f) else JarvisHudSurface.copy(alpha = 0.9f),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    viewModel.setFloatingWindowExpanded(!uiState.isFloatingWindowExpanded)
                                }
                                .testTag("floating_window_toggle_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.OpenInFull,
                                    contentDescription = "Toggle Floating CaroLine Window",
                                    tint = JarvisCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
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
                }

                // =====================================================================
                // INTERACTIVE CARDS: SENSITIVE ACTION, CONTACT MATCHES, MESSAGE PREVIEW
                // =====================================================================
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

                AnimatedVisibility(
                    visible = uiState.matchedContacts.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ContactMatchesSelectionCard(
                        title = uiState.contactSearchTitle,
                        contacts = uiState.matchedContacts,
                        onSelectContact = { viewModel.selectMatchedContact(it) },
                        onDismiss = { viewModel.clearMatchedContacts() }
                    )
                }

                AnimatedVisibility(
                    visible = uiState.pendingMessageDraft != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    uiState.pendingMessageDraft?.let { draft ->
                        SmartMessagePreviewCard(
                            draft = draft,
                            onUpdateDraftText = { viewModel.updatePendingMessageDraftText(it) },
                            onConfirmWhatsApp = { viewModel.confirmMessageDraftViaWhatsApp() },
                            onConfirmSms = { viewModel.confirmMessageDraftViaSms() },
                            onCancel = { viewModel.cancelMessageDraft() }
                        )
                    }
                }

                // =====================================================================
                // CONTROL 2: AI AVATAR / ANIMATED AI CORE
                // =====================================================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 210.dp, max = 285.dp),
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
                // CONTROL 3: LISTENING AND SPEAKING STATUS BAR (ALL ENGLISH LABELS)
                // =====================================================================
                ListeningAndSpeakingStatusBar(
                    liveState = uiState.liveState,
                    amplitude = if (uiState.isMicMuted) 0f else audioAmplitude,
                    isMicMuted = uiState.isMicMuted,
                    activeWakeWord = uiState.wakeWordPhrase,
                    isBatterySavingMode = uiState.isBatterySavingListeningMode,
                    isScreenOffSupported = uiState.isScreenOffConditionSupported,
                    isForegroundServiceRunning = uiState.isForegroundServiceRunning,
                    isInEcoStandby = uiState.isInEcoStandby,
                    enableAnimations = uiState.performanceProfile.enableHighFpsAnimations,
                    onWakeFromStandby = { viewModel.wakeAssistantFromStandby() }
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
                            val cmd = textCommandInput
                            textCommandInput = ""
                            val lower = cmd.lowercase()
                            val needsContacts = lower.contains("ending in") || lower.contains("contact") ||
                                (lower.contains("call ") && !lower.any { it.isDigit() })
                            if (needsContacts && ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.READ_CONTACTS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                            viewModel.onUserPrompt(cmd)
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

            // =========================================================================
            // REQUIREMENT F: MOVABLE FLOATING CAROLINE BUBBLE & COMPACT FLOATING WINDOW
            // =========================================================================
            if (uiState.isFloatingBubbleEnabled) {
                MovableFloatingCaroLineOverlay(
                    isExpanded = uiState.isFloatingWindowExpanded,
                    liveState = uiState.liveState,
                    shortResponseSnippet = uiState.speechSnippet,
                    onToggleExpand = {
                        viewModel.setFloatingWindowExpanded(!uiState.isFloatingWindowExpanded)
                    },
                    onMicClick = {
                        val hasAudioPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasAudioPermission) {
                            viewModel.triggerCallSession()
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onSendTextCommand = { cmd ->
                        viewModel.onUserPrompt(cmd)
                    },
                    onOpenFullSettings = {
                        viewModel.setFloatingWindowExpanded(false)
                        showSettings = true
                    },
                    modifier = Modifier.align(Alignment.TopEnd)
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

            // Error Snackbar (English labels)
            if (uiState.errorMessage != null) {
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("OK", color = PureWhite)
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
            // Requirement A: Performance Profiles
            performanceProfile = uiState.performanceProfile,
            isAutoPerformanceProfile = uiState.isAutoPerformanceProfile,
            totalRamMb = uiState.totalRamMb,
            availRamMb = uiState.availRamMb,
            onSelectPerformanceProfile = { profile, auto ->
                viewModel.setPerformanceProfile(profile, auto)
            },
            // Requirement B: Assistant Spoken Language Selector
            spokenLanguageCode = uiState.spokenLanguageCode,
            activeLanguage = uiState.activeLanguage,
            isLanguageLocked = uiState.isLanguageLocked,
            onSelectSpokenLanguage = { langCode ->
                viewModel.setSpokenLanguage(langCode)
            },
            // Requirement C: Female Voice Studio (Real Installed Engines & Voices)
            installedTtsEngines = uiState.installedTtsEngines,
            availableDeviceVoices = uiState.availableDeviceVoices,
            selectedTtsEnginePackage = uiState.selectedTtsEnginePackage,
            selectedVoiceId = uiState.selectedVoiceId,
            selectedVoice = uiState.selectedVoice,
            voicePitch = uiState.voicePitch,
            voiceSpeed = uiState.voiceSpeed,
            isVoiceMuted = uiState.isVoiceMuted,
            useOpenAiSpeechTts = uiState.useOpenAiTts,
            openAiVoiceName = uiState.openAiTtsVoice,
            onSelectTtsEnginePackage = { viewModel.selectTtsEnginePackage(it) },
            onSelectDeviceVoice = { viewModel.selectDeviceVoice(it) },
            onSelectVoice = { viewModel.setSelectedVoice(it) },
            onVoicePitchChange = { viewModel.setVoicePitch(it) },
            onVoiceSpeedChange = { viewModel.setVoiceSpeed(it) },
            onToggleVoiceMute = { viewModel.toggleVoiceMute() },
            onToggleOpenAiSpeechTts = { viewModel.setUseOpenAiTts(!uiState.useOpenAiTts) },
            onSelectOpenAiVoiceName = { viewModel.setOpenAiTtsVoice(it) },
            onPreviewVoice = { viewModel.previewFemaleVoice() },
            // Requirement D: Managed AI Brain (Without Manual API Key Entry)
            selectedAiProvider = uiState.selectedAiProvider,
            autoFailoverEnabled = uiState.autoFailoverEnabled,
            isManagedCloudConfigured = uiState.isManagedCloudConfigured,
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
            // Requirement F: Floating CaroLine Window
            isFloatingBubbleEnabled = uiState.isFloatingBubbleEnabled,
            canDrawSystemOverlays = uiState.canDrawSystemOverlays,
            onToggleFloatingBubble = { viewModel.toggleFloatingBubble() },
            // Requirement K & L: Live Weather, Internet Data Usage & Adaptive AI
            latestWeatherReport = uiState.latestWeatherReport,
            networkAndDataReport = uiState.networkAndDataReport,
            adaptiveAiSnapshot = uiState.adaptiveAiSnapshot,
            onRefreshWeather = { city -> viewModel.refreshLiveWeather(city) },
            onRefreshTelemetry = { viewModel.refreshDeviceTelemetry() },
            // Wake Word, Battery-Saving Mode, Screen-Off Support & Foreground Mic Service
            wakeWordEnabled = uiState.isWakeWordEnabled,
            selectedWakeWord = uiState.wakeWordPhrase,
            listeningPowerMode = uiState.listeningPowerMode,
            isBatterySavingListeningMode = uiState.isBatterySavingListeningMode,
            isScreenOffVoiceEnabled = uiState.isScreenOffVoiceEnabled,
            isScreenOffConditionSupported = uiState.isScreenOffConditionSupported,
            isInEcoStandby = uiState.isInEcoStandby,
            isHandsFreeMode = uiState.isHandsFreeMode,
            isBackgroundServiceEnabled = uiState.isBackgroundServiceEnabled,
            isForegroundServiceRunning = uiState.isForegroundServiceRunning,
            onChangeLanguageWithPin = { pin, lang -> viewModel.changeLanguageWithPin(pin, lang) },
            onLockLanguageToHindi = { viewModel.lockLanguageToHindi() },
            onToggleWakeWord = { viewModel.setWakeWordEnabled(!uiState.isWakeWordEnabled) },
            onSelectWakeWord = { viewModel.setWakeWordPhrase(it) },
            onSelectListeningPowerMode = { viewModel.setListeningPowerMode(it) },
            onToggleBatterySavingListeningMode = { viewModel.toggleBatterySavingListeningMode() },
            onToggleScreenOffVoiceSupport = { viewModel.toggleScreenOffVoiceSupport() },
            onWakeFromEcoStandby = { viewModel.wakeAssistantFromStandby() },
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
 * Requirement E: Interactive Contact Selection Card for partial digit search or multiple matches.
 */
@Composable
private fun ContactMatchesSelectionCard(
    title: String,
    contacts: List<DeviceContactItem>,
    onSelectContact: (DeviceContactItem) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = JarvisHudSurface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.2.dp, JarvisCyan.copy(alpha = 0.8f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("contact_matches_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title.ifBlank { "Select a Contact to Call (${contacts.size})" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = AccentGold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Contact Matches",
                        tint = MutedVioletGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 150.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                contacts.forEach { contact ->
                    Surface(
                        color = Color(0xFF07152B),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, JarvisHudBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectContact(contact) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.displayName,
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = contact.phoneNumber,
                                    color = JarvisCyan,
                                    fontSize = 11.sp
                                )
                            }
                            Surface(
                                color = JarvisEmerald.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, JarvisEmerald)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = "Dial Contact",
                                        tint = JarvisEmerald,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Dial",
                                        color = JarvisEmerald,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Requirement G: Smart Message Preview & Confirmation Card before sending via WhatsApp or SMS.
 */
@Composable
private fun SmartMessagePreviewCard(
    draft: MessageDraftPreview,
    onUpdateDraftText: (String) -> Unit,
    onConfirmWhatsApp: () -> Unit,
    onConfirmSms: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = JarvisHudSurface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.2.dp, AccentGold.copy(alpha = 0.85f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("smart_message_preview_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SMART MESSAGE WRITER • PREVIEW",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentGold
                    )
                    Text(
                        text = "Recipient: ${draft.recipientName.ifBlank { "Select in App" }} • Category: ${draft.category.labelEn}",
                        fontSize = 11.sp,
                        color = JarvisCyan
                    )
                }
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel Message Draft",
                        tint = MutedVioletGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = draft.draftedText,
                onValueChange = onUpdateDraftText,
                label = { Text("Polished Message (Edit before sending)", fontSize = 11.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("message_draft_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisHudBorder,
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConfirmWhatsApp,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisEmerald,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("confirm_whatsapp_button")
                ) {
                    Text("Send WhatsApp", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = onConfirmSms,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisCyan,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("confirm_sms_button")
                ) {
                    Text("Send SMS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onCancel,
                    border = BorderStroke(1.dp, MutedVioletGray),
                    modifier = Modifier.height(42.dp)
                ) {
                    Text("Cancel", color = PureWhite, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Requirement F: Movable Floating CaroLine Bubble and Compact Floating Window with all 6 controls:
 * 1. Microphone button
 * 2. Text command field
 * 3. Send button
 * 4. Short AI response
 * 5. Close button
 * 6. Expand assistant button
 */
@Composable
private fun MovableFloatingCaroLineOverlay(
    isExpanded: Boolean,
    liveState: LiveState,
    shortResponseSnippet: String,
    onToggleExpand: () -> Unit,
    onMicClick: () -> Unit,
    onSendTextCommand: (String) -> Unit,
    onOpenFullSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(-12f) }
    var offsetY by remember { mutableFloatStateOf(160f) }
    var floatingInput by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX = (offsetX + dragAmount.x).coerceIn(-700f, 40f)
                    offsetY = (offsetY + dragAmount.y).coerceIn(60f, 1200f)
                }
            }
    ) {
        if (!isExpanded) {
            // Movable Floating CaroLine Bubble
            Surface(
                color = Color(0xFF0A192F).copy(alpha = 0.95f),
                shape = CircleShape,
                border = BorderStroke(2.dp, if (liveState == LiveState.LISTENING) JarvisEmerald else JarvisCyan),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .clickable { onToggleExpand() }
                    .testTag("movable_floating_bubble")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Open Floating CaroLine Assistant",
                            tint = AccentGold,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "AI",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = JarvisCyan
                        )
                    }
                }
            }
        } else {
            // Compact Floating CaroLine Window with 6 controls
            Surface(
                color = Color(0xFF071326).copy(alpha = 0.97f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, JarvisCyan),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .width(300.dp)
                    .padding(8.dp)
                    .testTag("compact_floating_window")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CAROLINE FLOATING AI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AccentGold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Control 6: Expand assistant button
                            IconButton(
                                onClick = onOpenFullSettings,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("floating_expand_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInFull,
                                    contentDescription = "Expand Assistant Dashboard",
                                    tint = JarvisCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            // Control 5: Close button
                            IconButton(
                                onClick = onToggleExpand,
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("floating_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Floating Window",
                                    tint = PureWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Control 4: Short AI response
                    Surface(
                        color = Color(0xFF0D213F),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = shortResponseSnippet,
                            fontSize = 12.sp,
                            color = PureWhite,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(8.dp)
                                .testTag("floating_short_response")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Controls 1, 2, 3: Microphone button, Text command field, Send button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Control 1: Microphone button
                        IconButton(
                            onClick = onMicClick,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (liveState == LiveState.LISTENING) JarvisEmerald.copy(alpha = 0.25f)
                                    else AccentGold.copy(alpha = 0.2f)
                                )
                                .testTag("floating_mic_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Floating Voice Command",
                                tint = if (liveState == LiveState.LISTENING) JarvisEmerald else AccentGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Control 2: Text command field
                        OutlinedTextField(
                            value = floatingInput,
                            onValueChange = { floatingInput = it },
                            placeholder = {
                                Text("Ask CaroLine...", fontSize = 11.sp, color = MutedVioletGray)
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvisCyan,
                                unfocusedBorderColor = JarvisHudBorder,
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("floating_text_input")
                        )

                        // Control 3: Send button
                        IconButton(
                            onClick = {
                                if (floatingInput.isNotBlank()) {
                                    onSendTextCommand(floatingInput)
                                    floatingInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(JarvisCyan)
                                .testTag("floating_send_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Floating Command",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Control 3: Listening and Speaking Status Bar with real-time waveform (English UI labels).
 */
@Composable
private fun ListeningAndSpeakingStatusBar(
    liveState: LiveState,
    amplitude: Float,
    isMicMuted: Boolean,
    activeWakeWord: String,
    isBatterySavingMode: Boolean = true,
    isScreenOffSupported: Boolean = true,
    isForegroundServiceRunning: Boolean = false,
    isInEcoStandby: Boolean = false,
    enableAnimations: Boolean = true,
    onWakeFromStandby: () -> Unit = {}
) {
    val modeTag = if (isBatterySavingMode) "BATTERY-SAVING" else "ACTIVE"
    val statusText = when {
        isMicMuted -> "MICROPHONE MUTED"
        liveState == LiveState.SPEAKING -> "CAROLINE SPEAKING..."
        liveState == LiveState.THINKING || liveState == LiveState.CONNECTING -> "CAROLINE THINKING..."
        isInEcoStandby -> "ECO STANDBY • TAP OR SAY \"$activeWakeWord\""
        liveState == LiveState.LISTENING -> "$modeTag LISTENING • SAY \"$activeWakeWord\""
        liveState == LiveState.ERROR -> "VOICE ENGINE READY — TAP MIC TO RETRY"
        else -> "STANDBY ($modeTag) • WAKE WORD: \"$activeWakeWord\""
    }

    val statusColor = when {
        isMicMuted -> AlertRed
        liveState == LiveState.SPEAKING -> AccentGold
        liveState == LiveState.THINKING || liveState == LiveState.CONNECTING -> SoftLilac
        isInEcoStandby -> AccentGold
        liveState == LiveState.LISTENING -> JarvisEmerald
        else -> JarvisCyan
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            color = statusColor.copy(alpha = 0.14f),
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.7f)),
            modifier = Modifier
                .clickable {
                    if (isInEcoStandby) {
                        onWakeFromStandby()
                    }
                }
                .testTag("listening_speaking_status_pill")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isForegroundServiceRunning) "● Foreground Mic Service Active" else "○ Foreground Mic Service Ready",
                fontSize = 10.sp,
                color = if (isForegroundServiceRunning) JarvisEmerald else MutedVioletGray,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "•",
                fontSize = 10.sp,
                color = MutedVioletGray
            )
            Text(
                text = if (isScreenOffSupported) "Screen-Off Supported" else "Screen-On Mode",
                fontSize = 10.sp,
                color = if (isScreenOffSupported) JarvisCyan else MutedVioletGray,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (enableAnimations) {
            AudioWaveform(
                liveState = liveState,
                amplitude = amplitude,
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .height(26.dp)
            )
        }
    }
}

/**
 * Control 4: Single Conversation Area Card (English UI placeholder & labels).
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
        color = JarvisHudSurface.copy(alpha = 0.88f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.2.dp, JarvisHudBorder),
        shadowElevation = 10.dp,
        modifier = modifier.testTag("single_conversation_area")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Active response display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp, max = 120.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val lastUserMsg = recentMessages.lastOrNull { it.role == "user" }
                if (lastUserMsg != null && !speechSnippet.startsWith("🗣️")) {
                    Text(
                        text = "You: \"${lastUserMsg.text}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisCyan.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = speechSnippet,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    ),
                    color = PureWhite,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Compact command input inside the same conversation card
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
                            text = "Type a command (Call Rahul, Weather, Message...)",
                            fontSize = 12.sp,
                            color = MutedVioletGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisHudBorder,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedContainerColor = Color(0xFF050E1D),
                        unfocusedContainerColor = Color(0xFF050E1D)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("command_text_input")
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
                        .testTag("send_command_button")
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
 * Control 5: Primary Glowing JARVIS Microphone Button (English UI labels).
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
        targetValue = if (liveState == LiveState.LISTENING) 1.12f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val buttonBrush = when {
        isMicMuted -> Brush.linearGradient(listOf(AlertRed, Color(0xFFB71C1C)))
        liveState == LiveState.SPEAKING -> Brush.linearGradient(listOf(AccentGold, HotMagentaPink))
        isSessionActive -> Brush.linearGradient(listOf(JarvisCyan, JarvisElectricBlue))
        else -> Brush.linearGradient(listOf(AccentGold, Color(0xFFFF8F00)))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(76.dp)
                .scale(if (isSessionActive) pulseScale else 1f)
                .shadow(
                    elevation = 18.dp,
                    shape = CircleShape,
                    ambientColor = if (isSessionActive) JarvisCyan else AccentGold,
                    spotColor = if (isSessionActive) JarvisCyan else AccentGold
                )
                .clip(CircleShape)
                .background(buttonBrush)
                .border(
                    width = 2.dp,
                    color = PureWhite.copy(alpha = 0.85f),
                    shape = CircleShape
                )
                .clickable { onMicClick() }
                .testTag("central_call_mic_button")
        ) {
            Icon(
                imageVector = when {
                    isMicMuted -> Icons.Default.MicOff
                    liveState == LiveState.SPEAKING -> Icons.Default.GraphicEq
                    else -> Icons.Default.Mic
                },
                contentDescription = if (isSessionActive) "Stop Voice Mode" else "Start CaroLine Voice Mode",
                tint = Color.Black,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (isSessionActive) "Listening • Tap to Pause" else "Tap to Speak with CaroLine",
            fontSize = 11.sp,
            color = MutedVioletGray,
            fontWeight = FontWeight.SemiBold
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
        color = AlertRed.copy(alpha = 0.16f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.2.dp, AlertRed),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "Sensitive Action Confirmation",
                    tint = AccentGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SECURITY CONFIRMATION: ${pendingAction.titleBn}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = PureWhite
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = pendingAction.descriptionBn,
                fontSize = 12.sp,
                color = SoftLilac
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisEmerald,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onCancel,
                    border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.6f)),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("Cancel", color = PureWhite, fontSize = 12.sp)
                }
            }
        }
    }
}

typealias MahiMainScreen = Unit
