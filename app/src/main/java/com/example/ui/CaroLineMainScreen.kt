package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.live.AiProviderType
import com.example.live.ConversationMessage
import com.example.live.LiveState
import com.example.tools.AppEntry
import com.example.tools.PendingSensitiveAction
import com.example.ui.components.AudioWaveform
import com.example.ui.components.AvatarEmotion
import com.example.ui.components.AvatarStyle
import com.example.ui.components.CaroLineAnimeAvatarView
import com.example.ui.components.HelpEducationSheet
import com.example.ui.components.JarvisHolographicCore
import com.example.ui.components.SettingsSheet
import com.example.ui.components.ToolToastBanner
import com.example.ui.theme.AlertRed
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.HotMagentaPink
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepNavy
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisHudBorder
import com.example.ui.theme.JarvisHudSurface
import com.example.ui.theme.MutedVioletGray
import com.example.ui.theme.NeonPurpleGlow
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftLilac
import com.example.viewmodel.CaroLineViewModel
import com.example.viewmodel.DashboardTab

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
    var showHelpSheet by remember { mutableStateOf(false) }
    var isEducationSheet by remember { mutableStateOf(false) }
    var textCommandInput by remember { mutableStateOf("") }

    // Navigate back to JARVIS_HUD when on secondary tabs
    if (uiState.activeTab != DashboardTab.JARVIS_HUD) {
        BackHandler {
            viewModel.setActiveTab(DashboardTab.JARVIS_HUD)
        }
    }

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

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.onUserPrompt("ক্যামেরা খোলো")
    }

    val avatarPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setCustomAvatar(uri)
        }
    }

    val quickBengaliPrompts = listOf(
        "👋 হ্যালো ক্যারোলাইন",
        "📱 হোয়াটসঅ্যাপ খোলো",
        "🎬 ইউটিউব খোলো",
        "🎵 গান চালাও",
        "🔦 ফ্ল্যাশলাইট চালু করো",
        "🔊 ভলিউম বাড়াও",
        "🔋 মোবাইলের ব্যাটারি কত?",
        "🌐 ইন্টারনেট চেক করো",
        "⏰ এখন কয়টা বাজে?",
        "🛑 ভয়েস মোড বন্ধ করো"
    )

    val displayEmotion = uiState.manualEmotionOverride ?: uiState.currentEmotion

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = Color.Transparent
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            JarvisDeepNavy,
                            Color(0xFF061329),
                            Color(0xFF0A1933),
                            JarvisDeepNavy
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 640.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = if (isWideScreen) 920.dp else 600.dp)
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // 1. TOP HOLOGRAPHIC HUD HEADER & TELEMETRY STRIP
                JarvisHudTopHeader(
                    appVersion = uiState.appVersion,
                    batteryLevel = uiState.batteryLevel,
                    isCharging = uiState.isCharging,
                    isOnline = uiState.isOnline,
                    networkType = uiState.networkType,
                    availRamMb = uiState.availRamMb,
                    isForegroundServiceRunning = uiState.isForegroundServiceRunning,
                    isLanguageLocked = uiState.isLanguageLocked,
                    selectedAiProvider = uiState.selectedAiProvider,
                    activeProviderBadge = uiState.activeProviderBadge,
                    isJarvisCoreMode = uiState.isJarvisCoreMode,
                    onSelectAiProvider = { viewModel.selectAiProvider(it) },
                    onToggleCoreAvatarMode = { viewModel.toggleJarvisCoreMode() },
                    onOpenCamera = {
                        val hasCamPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCamPerm) {
                            viewModel.onUserPrompt("ক্যামেরা খোলো")
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onUploadAvatar = {
                        avatarPhotoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onOpenHelp = {
                        isEducationSheet = false
                        showHelpSheet = true
                    },
                    onOpenSettings = { showSettings = true }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 2. DASHBOARD NAVIGATION TABS (JARVIS HUD | CONVERSATION | TOP 50 APPS)
                DashboardTabSelector(
                    activeTab = uiState.activeTab,
                    conversationCount = uiState.conversationHistory.size,
                    installedAppsCount = uiState.appCatalog.count { it.isInstalled },
                    onSelectTab = { viewModel.setActiveTab(it) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 3. SENSITIVE ACTION CONFIRMATION BANNER (IF PENDING)
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

                // 4. ACTIVE TAB CONTENT
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (uiState.activeTab) {
                        DashboardTab.JARVIS_HUD -> {
                            JarvisMainHudView(
                                isJarvisCoreMode = uiState.isJarvisCoreMode,
                                liveState = uiState.liveState,
                                displayEmotion = displayEmotion,
                                avatarStyle = uiState.avatarStyle,
                                customAvatarUri = uiState.customAvatarUri,
                                audioAmplitude = audioAmplitude,
                                isMicMuted = uiState.isMicMuted,
                                isMicHardwareVerified = uiState.isMicHardwareVerified,
                                activeProviderBadge = uiState.activeProviderBadge,
                                speechSnippet = uiState.speechSnippet,
                                quickPrompts = quickBengaliPrompts,
                                textCommandInput = textCommandInput,
                                onTextCommandChange = { textCommandInput = it },
                                onSubmitTextCommand = {
                                    if (textCommandInput.isNotBlank()) {
                                        viewModel.onUserPrompt(textCommandInput)
                                        textCommandInput = ""
                                    }
                                },
                                onAvatarTap = { viewModel.onAvatarTapped() },
                                onSelectAvatarStyle = { viewModel.setAvatarStyle(it) },
                                onSelectEmotion = { viewModel.setManualEmotion(it) },
                                onSelectQuickPrompt = { viewModel.onUserPrompt(it) }
                            )
                        }

                        DashboardTab.CONVERSATION_HISTORY -> {
                            ConversationHistoryTabView(
                                messages = uiState.conversationHistory,
                                textCommandInput = textCommandInput,
                                onTextCommandChange = { textCommandInput = it },
                                onSubmitTextCommand = {
                                    if (textCommandInput.isNotBlank()) {
                                        viewModel.onUserPrompt(textCommandInput)
                                        textCommandInput = ""
                                    }
                                },
                                onClearHistory = { viewModel.clearConversationHistory() }
                            )
                        }

                        DashboardTab.APP_LAUNCHER_50 -> {
                            Top50AppLauncherTabView(
                                appCatalog = uiState.appCatalog,
                                searchQuery = uiState.appSearchQuery,
                                onSearchQueryChange = { viewModel.setAppSearchQuery(it) },
                                onLaunchApp = { viewModel.launchAppFromCatalog(it) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 5. PERSISTENT BOTTOM VOICE CONTROL DOCK
                BottomCallControlDock(
                    liveState = uiState.liveState,
                    isMicMuted = uiState.isMicMuted,
                    isHandsFreeMode = uiState.isHandsFreeMode,
                    onCallClick = {
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
                    },
                    onToggleMute = { viewModel.toggleMute() },
                    onToggleHandsFree = { viewModel.toggleHandsFree() },
                    onOpenSettings = { showSettings = true }
                )

                Spacer(modifier = Modifier.height(8.dp))
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
                            Text("ঠিক আছে", color = PureWhite)
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
            primaryApiKey = uiState.primaryApiKey,
            backupApiKey = uiState.backupApiKey,
            apiMode = uiState.apiMode,
            activeEngine = uiState.activeEngine,
            selectedVoice = uiState.selectedVoice,
            activeLanguage = uiState.activeLanguage,
            isLanguageLocked = uiState.isLanguageLocked,
            isHandsFreeMode = uiState.isHandsFreeMode,
            isBackgroundServiceEnabled = uiState.isBackgroundServiceEnabled,
            isForegroundServiceRunning = uiState.isForegroundServiceRunning,
            toolHistory = uiState.toolHistory,
            onSavePrimaryKey = { viewModel.setPrimaryApiKey(it) },
            onSaveBackupKey = { viewModel.setBackupApiKey(it) },
            onSelectApiMode = { viewModel.setApiMode(it) },
            onSelectVoice = { viewModel.setSelectedVoice(it) },
            onChangeLanguageWithPin = { pin, lang -> viewModel.changeLanguageWithPin(pin, lang) },
            onLockLanguageToBengali = { viewModel.lockLanguageToBengali() },
            onToggleHandsFree = { viewModel.toggleHandsFree() },
            onToggleBackgroundService = { viewModel.toggleBackgroundService() },
            onDismiss = { showSettings = false }
        )
    }

    if (showHelpSheet) {
        HelpEducationSheet(
            isEducationMode = isEducationSheet,
            onSelectQuickPrompt = { prompt -> viewModel.onUserPrompt(prompt) },
            onDismiss = { showHelpSheet = false }
        )
    }
}

@Composable
private fun JarvisHudTopHeader(
    appVersion: String,
    batteryLevel: Int,
    isCharging: Boolean,
    isOnline: Boolean,
    networkType: String,
    availRamMb: Int,
    isForegroundServiceRunning: Boolean,
    isLanguageLocked: Boolean,
    selectedAiProvider: AiProviderType,
    activeProviderBadge: String,
    isJarvisCoreMode: Boolean,
    onSelectAiProvider: (AiProviderType) -> Unit,
    onToggleCoreAvatarMode: () -> Unit,
    onOpenCamera: () -> Unit,
    onUploadAvatar: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        color = JarvisHudSurface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, JarvisHudBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // Top Row: Title + Quick Action Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CAROLINE AI",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.6.sp
                            ),
                            color = PureWhite
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = JarvisCyan.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.65f))
                        ) {
                            Text(
                                text = appVersion,
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisCyan,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Real-Time Device Telemetry Strip
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        // Battery Telemetry
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                contentDescription = "Battery",
                                tint = if (batteryLevel > 20) JarvisEmerald else AlertRed,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "$batteryLevel%",
                                fontSize = 11.sp,
                                color = PureWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Network Telemetry
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                                contentDescription = "Network",
                                tint = if (isOnline) JarvisCyan else AlertRed,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (isOnline) networkType else "Offline",
                                fontSize = 11.sp,
                                color = if (isOnline) JarvisCyan else AlertRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // RAM Telemetry
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = "RAM",
                                tint = SoftLilac,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${availRamMb}MB",
                                fontSize = 11.sp,
                                color = SoftLilac
                            )
                        }

                        // Bengali Lock Indicator
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Bengali Lock",
                                tint = JarvisEmerald,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = if (isLanguageLocked) "বাংলা" else "Multi",
                                fontSize = 11.sp,
                                color = JarvisEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isForegroundServiceRunning) {
                            Text(
                                text = "• BG MIC",
                                fontSize = 10.sp,
                                color = JarvisCyan,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Right Action Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TopCircleIconButton(
                        icon = if (isJarvisCoreMode) Icons.Default.Face else Icons.Default.Hub,
                        contentDescription = "Toggle JARVIS Core or Anime Avatar",
                        testTag = "toggle_core_avatar_button",
                        onClick = onToggleCoreAvatarMode
                    )
                    TopCircleIconButton(
                        icon = Icons.Default.CameraAlt,
                        contentDescription = "Open Camera",
                        testTag = "top_camera_button",
                        onClick = onOpenCamera
                    )
                    TopCircleIconButton(
                        icon = Icons.Default.Image,
                        contentDescription = "Upload Custom Avatar",
                        testTag = "top_upload_avatar_button",
                        onClick = onUploadAvatar
                    )
                    TopCircleIconButton(
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "Help & Voice Commands",
                        testTag = "top_help_button",
                        onClick = onOpenHelp
                    )
                    TopCircleIconButton(
                        icon = Icons.Default.Settings,
                        contentDescription = "AI Brain & System Settings",
                        testTag = "top_settings_button",
                        onClick = onOpenSettings
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Three-AI Provider Switcher Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI BRAIN:",
                    fontSize = 10.sp,
                    color = MutedVioletGray,
                    fontWeight = FontWeight.Bold
                )
                AiProviderType.values().forEach { provider ->
                    val isSelected = selectedAiProvider == provider
                    Surface(
                        color = if (isSelected) JarvisCyan.copy(alpha = 0.22f) else JarvisDeepNavy.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) JarvisCyan else MutedVioletGray.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSelectAiProvider(provider) }
                            .testTag("hud_provider_chip_${provider.name.lowercase()}")
                    ) {
                        Text(
                            text = if (isSelected) "● ${provider.shortBadge}" else provider.shortBadge,
                            fontSize = 10.sp,
                            color = if (isSelected) JarvisCyan else MutedVioletGray,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = activeProviderBadge,
                    fontSize = 10.sp,
                    color = JarvisEmerald,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DashboardTabSelector(
    activeTab: DashboardTab,
    conversationCount: Int,
    installedAppsCount: Int,
    onSelectTab: (DashboardTab) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tabs = listOf(
            Triple(DashboardTab.JARVIS_HUD, Icons.Default.Hub, "জারভিস কোর"),
            Triple(DashboardTab.CONVERSATION_HISTORY, Icons.Default.Chat, "কথোপকথন ($conversationCount)"),
            Triple(
                DashboardTab.APP_LAUNCHER_50,
                Icons.Default.Apps,
                "টপ ৫০ অ্যাপস" + if (installedAppsCount > 0) " ($installedAppsCount)" else ""
            )
        )

        tabs.forEach { (tab, icon, label) ->
            val isSelected = activeTab == tab
            Surface(
                color = if (isSelected) JarvisElectricBlue.copy(alpha = 0.28f) else JarvisHudSurface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) JarvisCyan else JarvisHudBorder.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectTab(tab) }
                    .testTag("dashboard_tab_${tab.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) JarvisCyan else MutedVioletGray,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        color = if (isSelected) PureWhite else MutedVioletGray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
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
            .padding(bottom = 8.dp)
            .testTag("sensitive_action_confirmation_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "Sensitive Action",
                    tint = HotMagentaPink,
                    modifier = Modifier.size(20.dp)
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
            Spacer(modifier = Modifier.height(10.dp))
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
                    Text("হ্যাঁ, অনুমোদন করুন", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onCancel,
                    border = BorderStroke(1.dp, MutedVioletGray),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cancel_sensitive_action_button")
                ) {
                    Text("বাতিল করুন", color = PureWhite, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun JarvisMainHudView(
    isJarvisCoreMode: Boolean,
    liveState: LiveState,
    displayEmotion: AvatarEmotion,
    avatarStyle: AvatarStyle,
    customAvatarUri: android.net.Uri?,
    audioAmplitude: Float,
    isMicMuted: Boolean,
    isMicHardwareVerified: Boolean,
    activeProviderBadge: String,
    speechSnippet: String,
    quickPrompts: List<String>,
    textCommandInput: String,
    onTextCommandChange: (String) -> Unit,
    onSubmitTextCommand: () -> Unit,
    onAvatarTap: () -> Unit,
    onSelectAvatarStyle: (AvatarStyle) -> Unit,
    onSelectEmotion: (AvatarEmotion?) -> Unit,
    onSelectQuickPrompt: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Center Core or Anime Avatar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 230.dp, max = 300.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isJarvisCoreMode) {
                JarvisHolographicCore(
                    liveState = liveState,
                    amplitude = audioAmplitude,
                    activeProviderBadge = activeProviderBadge,
                    onCoreTapped = onAvatarTap
                )
            } else {
                CaroLineAnimeAvatarView(
                    liveState = liveState,
                    amplitude = audioAmplitude,
                    selectedAvatarStyle = avatarStyle,
                    customAvatarUri = customAvatarUri,
                    manualEmotion = displayEmotion,
                    onAvatarTapped = onAvatarTap
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Live Audio Waveform + Bengali Speech Output Card
        Surface(
            color = JarvisHudSurface.copy(alpha = 0.90f),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, JarvisHudBorder.copy(alpha = 0.65f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("speech_bubble_card")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AudioWaveform(
                    liveState = liveState,
                    amplitude = if (isMicMuted) 0f else audioAmplitude,
                    modifier = Modifier.height(22.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = speechSnippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureWhite,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Text Command Bar on HUD
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
                        text = "বাংলা বা ইংরেজিতে যেকোনো কমান্ড লিখুন (যেমন: হোয়াটসঅ্যাপ খোলো)...",
                        fontSize = 12.sp,
                        color = MutedVioletGray
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("hud_command_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisHudBorder.copy(alpha = 0.5f),
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite,
                    focusedContainerColor = JarvisHudSurface.copy(alpha = 0.7f),
                    unfocusedContainerColor = JarvisHudSurface.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(14.dp)
            )

            IconButton(
                onClick = onSubmitTextCommand,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(JarvisElectricBlue)
                    .testTag("hud_command_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Command",
                    tint = PureWhite
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Bengali JARVIS Command Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickPrompts.forEachIndexed { index, promptText ->
                FilterChip(
                    selected = false,
                    onClick = { onSelectQuickPrompt(promptText) },
                    label = {
                        Text(
                            text = promptText,
                            style = MaterialTheme.typography.labelMedium,
                            color = PureWhite
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = JarvisHudSurface.copy(alpha = 0.85f)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = JarvisCyan.copy(alpha = 0.45f)
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("quick_chip_$index")
                )
            }
        }
    }
}

@Composable
private fun ConversationHistoryTabView(
    messages: List<ConversationMessage>,
    textCommandInput: String,
    onTextCommandChange: (String) -> Unit,
    onSubmitTextCommand: () -> Unit,
    onClearHistory: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "কথোপকথনের ইতিহাস (Multi-Turn Context)",
                style = MaterialTheme.typography.titleSmall,
                color = JarvisCyan,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                onClick = onClearHistory,
                modifier = Modifier.testTag("clear_conversation_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear History",
                    tint = MutedVioletGray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("মুছে ফেলুন", color = MutedVioletGray, fontSize = 12.sp)
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isUser = msg.role == "user"
                Surface(
                    color = if (isUser) JarvisElectricBlue.copy(alpha = 0.24f) else JarvisHudSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isUser) JarvisCyan.copy(alpha = 0.5f) else JarvisHudBorder.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isUser) "আপনি (BOSS)" else "CAROLINE • ${msg.providerBadge}",
                                fontSize = 10.sp,
                                color = if (isUser) JarvisCyan else JarvisEmerald,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (!msg.toolSummary.isNullOrBlank()) {
                                Surface(
                                    color = JarvisCyan.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "⚡ ${msg.toolSummary}",
                                        fontSize = 10.sp,
                                        color = JarvisCyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PureWhite
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = textCommandInput,
                onValueChange = onTextCommandChange,
                placeholder = { Text("ক্যারোলাইনকে প্রশ্ন বা কমান্ড লিখুন...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("history_command_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisHudBorder.copy(alpha = 0.5f),
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite
                ),
                shape = RoundedCornerShape(14.dp)
            )
            IconButton(
                onClick = onSubmitTextCommand,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(JarvisElectricBlue)
                    .testTag("history_command_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Message",
                    tint = PureWhite
                )
            }
        }
    }
}

@Composable
private fun Top50AppLauncherTabView(
    appCatalog: List<AppEntry>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onLaunchApp: (AppEntry) -> Unit
) {
    val filteredApps = remember(appCatalog, searchQuery) {
        if (searchQuery.isBlank()) {
            appCatalog
        } else {
            val q = searchQuery.trim().lowercase()
            appCatalog.filter { entry ->
                entry.displayName.lowercase().contains(q) ||
                    entry.bengaliName.contains(q) ||
                    entry.category.lowercase().contains(q) ||
                    entry.aliases.any { it.lowercase().contains(q) }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("টপ ৫০ বা ইনস্টল করা অ্যাপ খুঁজুন (যেমন: WhatsApp, ইউটিউব)...", fontSize = 12.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Apps",
                    tint = JarvisCyan
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Search",
                            tint = MutedVioletGray
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_catalog_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisCyan,
                unfocusedBorderColor = JarvisHudBorder.copy(alpha = 0.5f),
                focusedTextColor = PureWhite,
                unfocusedTextColor = PureWhite
            ),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredApps, key = { it.displayName + "_" + it.category }) { appEntry ->
                Surface(
                    color = JarvisHudSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (appEntry.isInstalled) JarvisEmerald.copy(alpha = 0.45f) else JarvisHudBorder.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onLaunchApp(appEntry) }
                        .testTag("launch_app_item_${appEntry.displayName.lowercase().replace(" ", "_")}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = appEntry.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "(${appEntry.bengaliName})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JarvisCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "ক্যাটাগরি: ${appEntry.category} • ভয়েস কমান্ড: \"${appEntry.bengaliName} খোলো\"",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedVioletGray
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (appEntry.isInstalled) JarvisEmerald.copy(alpha = 0.16f) else JarvisCyan.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(
                                    1.dp,
                                    if (appEntry.isInstalled) JarvisEmerald.copy(alpha = 0.6f) else JarvisCyan.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = if (appEntry.isInstalled) "INSTALLED" else "PLAY STORE",
                                    fontSize = 9.sp,
                                    color = if (appEntry.isInstalled) JarvisEmerald else JarvisCyan,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Launch ${appEntry.displayName}",
                                tint = JarvisCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = JarvisDeepNavy.copy(alpha = 0.85f),
        shape = CircleShape,
        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.45f)),
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = PureWhite,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun BottomCallControlDock(
    liveState: LiveState,
    isMicMuted: Boolean,
    isHandsFreeMode: Boolean,
    onCallClick: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleHandsFree: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isSessionActive = liveState != LiveState.DISCONNECTED && liveState != LiveState.ERROR

    Surface(
        color = JarvisHudSurface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, JarvisHudBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Mic Mute Toggle
            DockActionPill(
                icon = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (isMicMuted) "আনমিউট" else "মিউট",
                isActive = !isMicMuted && isSessionActive,
                activeColor = if (isMicMuted) AlertRed else JarvisCyan,
                testTag = "mute_toggle_button",
                onClick = onToggleMute
            )

            // Center: Glowing JARVIS Voice Mode / Call Button
            Button(
                onClick = onCallClick,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .height(54.dp)
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(50),
                        spotColor = if (isSessionActive) AlertRed else JarvisCyan
                    )
                    .background(
                        brush = if (isSessionActive) {
                            Brush.horizontalGradient(listOf(AlertRed, HotMagentaPink))
                        } else {
                            Brush.horizontalGradient(listOf(JarvisElectricBlue, JarvisCyan, ElectricViolet))
                        },
                        shape = RoundedCornerShape(50)
                    )
                    .testTag("central_mic_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = if (isSessionActive) Icons.Default.CallEnd else Icons.Default.Call,
                        contentDescription = if (isSessionActive) "End Voice Session" else "Start CaroLine Voice Call",
                        tint = if (isSessionActive) PureWhite else Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSessionActive) "কল শেষ করুন" else "কথা বলুন (JARVIS)",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isSessionActive) PureWhite else Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            // Right: Hands-Free Continuous Mode Toggle
            DockActionPill(
                icon = if (isHandsFreeMode) Icons.Default.RecordVoiceOver else Icons.Default.AutoAwesome,
                label = if (isHandsFreeMode) "অটো মাইক" else "ম্যানুয়াল",
                isActive = isHandsFreeMode,
                activeColor = JarvisCyan,
                testTag = "hands_free_dock_button",
                onClick = onToggleHandsFree
            )
        }
    }
}

@Composable
private fun DockActionPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag(testTag)
    ) {
        Surface(
            color = if (isActive) activeColor.copy(alpha = 0.2f) else JarvisDeepNavy,
            shape = CircleShape,
            border = BorderStroke(
                width = 1.dp,
                color = if (isActive) activeColor else MutedVioletGray.copy(alpha = 0.35f)
            ),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) activeColor else MutedVioletGray,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (isActive) PureWhite else MutedVioletGray,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// Backward compatibility composable alias
@Composable
fun MahiMainScreen(viewModel: CaroLineViewModel, modifier: Modifier = Modifier) {
    CaroLineMainScreen(viewModel = viewModel, modifier = modifier)
}
