package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.AdaptiveAiSnapshot
import com.example.live.AiProviderType
import com.example.live.CaroLineBrainEngine
import com.example.live.ConversationMessage
import com.example.live.DailyReminderItem
import com.example.live.DeviceVoiceInfo
import com.example.live.ProviderConfig
import com.example.live.ProviderTestResult
import com.example.live.SmartMemoryItem
import com.example.live.SmartRoutineItem
import com.example.live.TtsEngineInfo
import com.example.tools.AppEntry
import com.example.tools.LiveWeatherReport
import com.example.tools.NetworkAndDataReport
import com.example.tools.PerformanceProfile
import com.example.tools.ToolExecutionEvent
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AlertRed
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.HotMagentaPink
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisHudBorder
import com.example.ui.theme.JarvisHudSurface
import com.example.ui.theme.MutedVioletGray
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftLilac
import com.example.viewmodel.ActiveEngine
import com.example.viewmodel.ApiMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    // Requirement A: Performance Profiles (2GB Lite, 4GB Standard, 6GB+ Advanced)
    performanceProfile: PerformanceProfile = PerformanceProfile.STANDARD_MODE,
    isAutoPerformanceProfile: Boolean = true,
    totalRamMb: Int = 4096,
    availRamMb: Int = 2048,
    onSelectPerformanceProfile: (PerformanceProfile, Boolean) -> Unit = { _, _ -> },
    // Requirement B: Assistant Spoken Language Selector (Hindi Default, Bengali, English)
    spokenLanguageCode: String = "hi-IN",
    activeLanguage: String = "Hindi (हिन्दी)",
    isLanguageLocked: Boolean = true,
    onSelectSpokenLanguage: (String) -> Unit = {},
    // Requirement C: Female Voice Studio (Real Installed TTS Engines & Device Voices)
    installedTtsEngines: List<TtsEngineInfo> = emptyList(),
    availableDeviceVoices: List<DeviceVoiceInfo> = emptyList(),
    selectedTtsEnginePackage: String = "",
    selectedVoiceId: String = "system_locale_hi-IN",
    selectedVoice: String = "System Default Hindi (hi-IN)",
    voicePitch: Float = 1.08f,
    voiceSpeed: Float = 1.0f,
    isVoiceMuted: Boolean = false,
    useOpenAiSpeechTts: Boolean = false,
    openAiVoiceName: String = "nova",
    onSelectTtsEnginePackage: (String) -> Unit = {},
    onSelectDeviceVoice: (DeviceVoiceInfo) -> Unit = {},
    onSelectVoice: (String) -> Unit = {},
    onVoicePitchChange: (Float) -> Unit = {},
    onVoiceSpeedChange: (Float) -> Unit = {},
    onToggleVoiceMute: () -> Unit = {},
    onToggleOpenAiSpeechTts: () -> Unit = {},
    onSelectOpenAiVoiceName: (String) -> Unit = {},
    onPreviewVoice: () -> Unit = {},
    // Requirement D: Managed AI Brain (Without Manual API Key Entry)
    selectedAiProvider: AiProviderType = AiProviderType.GOOGLE_GEMINI,
    autoFailoverEnabled: Boolean = true,
    isManagedCloudConfigured: Boolean = true,
    providerConfigs: Map<AiProviderType, ProviderConfig> = emptyMap(),
    providerTestResults: Map<AiProviderType, ProviderTestResult> = emptyMap(),
    testingProvider: AiProviderType? = null,
    onSelectAiProvider: (AiProviderType) -> Unit = {},
    onToggleAutoFailover: () -> Unit = {},
    onSaveProviderConfig: (AiProviderType, String, String, String) -> Unit = { _, _, _, _ -> },
    onRemoveProviderKey: (AiProviderType) -> Unit = {},
    onTestProviderConnection: (AiProviderType) -> Unit = {},
    // Requirement F: Floating CaroLine Window
    isFloatingBubbleEnabled: Boolean = true,
    canDrawSystemOverlays: Boolean = false,
    onToggleFloatingBubble: () -> Unit = {},
    // Requirement K & L: Live Weather, Internet Data Usage & Adaptive AI
    latestWeatherReport: LiveWeatherReport? = null,
    networkAndDataReport: NetworkAndDataReport? = null,
    adaptiveAiSnapshot: AdaptiveAiSnapshot = AdaptiveAiSnapshot(),
    onRefreshWeather: (String) -> Unit = {},
    onRefreshTelemetry: () -> Unit = {},
    // Wake Word & Background Service
    wakeWordEnabled: Boolean = true,
    selectedWakeWord: String = "Hey CaroLine",
    isHandsFreeMode: Boolean = true,
    isBackgroundServiceEnabled: Boolean = true,
    isForegroundServiceRunning: Boolean = false,
    onChangeLanguageWithPin: (String, String) -> Boolean = { _, _ -> true },
    onLockLanguageToHindi: () -> Unit = {},
    onToggleWakeWord: () -> Unit = {},
    onSelectWakeWord: (String) -> Unit = {},
    onToggleHandsFree: () -> Unit = {},
    onToggleBackgroundService: () -> Unit = {},
    // Music & Video Preferences
    preferredMusicService: String = "Auto (Smart Play)",
    builtInMusicFallbackEnabled: Boolean = true,
    onSelectPreferredMusicService: (String) -> Unit = {},
    onToggleBuiltInMusicFallback: () -> Unit = {},
    onExecuteQuickCommand: (String) -> Unit = {},
    // Smart Features (Memory, Reminders, Routines)
    smartMemories: List<SmartMemoryItem> = emptyList(),
    dailyReminders: List<DailyReminderItem> = emptyList(),
    smartRoutines: List<SmartRoutineItem> = emptyList(),
    onAddSmartMemory: (String) -> Unit = {},
    onClearSmartMemories: () -> Unit = {},
    onAddDailyReminder: (String, String) -> Unit = { _, _ -> },
    onExecuteRoutine: (String) -> Unit = {},
    // Apps & Conversation History
    appCatalog: List<AppEntry> = emptyList(),
    appSearchQuery: String = "",
    onAppSearchQueryChange: (String) -> Unit = {},
    onLaunchApp: (AppEntry) -> Unit = {},
    conversationHistory: List<ConversationMessage> = emptyList(),
    onClearConversationHistory: () -> Unit = {},
    toolHistory: List<ToolExecutionEvent> = emptyList(),
    // Theme & Avatar
    selectedThemeName: String = "Anime-JARVIS Gold & Cyan",
    isJarvisCoreMode: Boolean = false,
    avatarStyle: AvatarStyle = AvatarStyle.GOTHIC_CYBER,
    onSelectTheme: (String) -> Unit = {},
    onToggleJarvisCoreMode: () -> Unit = {},
    onSelectAvatarStyle: (AvatarStyle) -> Unit = {},
    onUploadCustomAvatar: () -> Unit = {},
    // Legacy compatibility
    primaryApiKey: String = "",
    backupApiKey: String = "",
    apiMode: ApiMode = ApiMode.AUTO_FAILOVER,
    activeEngine: ActiveEngine = ActiveEngine.BENGALI_VOICE_ENGINE,
    onSavePrimaryKey: (String) -> Unit = {},
    onSaveBackupKey: (String) -> Unit = {},
    onSelectApiMode: (ApiMode) -> Unit = {},
    onLockLanguageToBengali: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var newMemoryText by remember { mutableStateOf("") }
    var newReminderTitle by remember { mutableStateOf("") }
    var newReminderTime by remember { mutableStateOf("08:00 AM") }
    var weatherCityInput by remember { mutableStateOf("New Delhi") }
    var showAdvancedSettings by remember { mutableStateOf(false) }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onExecuteQuickCommand("Find the contact ending in 45")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepObsidian,
        contentColor = PureWhite,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // =========================================================================
            // HEADER
            // =========================================================================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "CAROLINE AI A3.2 • SMART SETTINGS",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = PureWhite
                    )
                    Text(
                        text = "Automatic Smart Configuration • All Controls in English",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisCyan
                    )
                }

                Surface(
                    color = AccentGold.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, AccentGold)
                ) {
                    Text(
                        text = "A3.2",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentGold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // 1. ASSISTANT SPOKEN LANGUAGE & WAKE WORD (REQUIREMENT B)
            // =========================================================================
            SectionHeader(
                icon = Icons.Default.Language,
                title = "1. Assistant Spoken Language & Wake Word",
                tint = AccentGold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "App interface is always in English. Select CaroLine's spoken voice language:",
                        fontSize = 11.sp,
                        color = SoftLilac
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val languageChoices = listOf(
                        Triple("hi-IN", "Hindi (Default)", "हिन्दी"),
                        Triple("bn-IN", "Bengali", "বাংলা"),
                        Triple("en-IN", "English", "English")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        languageChoices.forEach { (code, label, nativeSub) ->
                            val isSelected = spokenLanguageCode.startsWith(code.take(2), ignoreCase = true)
                            Surface(
                                color = if (isSelected) AccentGold.copy(alpha = 0.22f) else Color(0xFF06101E),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) AccentGold else JarvisHudBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectSpokenLanguage(code) }
                                    .testTag("lang_option_$code")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) AccentGold else PureWhite
                                    )
                                    Text(
                                        text = nativeSub,
                                        fontSize = 10.sp,
                                        color = JarvisCyan
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wake Word Detection (\"$selectedWakeWord\")",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = PureWhite
                            )
                            Text(
                                text = "Understands Hindi, Bengali, Hinglish & English commands",
                                fontSize = 11.sp,
                                color = MutedVioletGray
                            )
                        }
                        Switch(
                            checked = wakeWordEnabled,
                            onCheckedChange = { onToggleWakeWord() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = JarvisCyan
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val supportedWakeWords = listOf(
                            "Hey CaroLine",
                            "Hi CaroLine",
                            "Hello CaroLine",
                            "CaroLine",
                            "कैरोलाइन"
                        )
                        supportedWakeWords.forEach { phrase ->
                            val isSelected = selectedWakeWord.equals(phrase, ignoreCase = true)
                            Surface(
                                color = if (isSelected) JarvisCyan.copy(alpha = 0.22f) else Color(0xFF06101E),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) JarvisCyan else JarvisHudBorder
                                ),
                                modifier = Modifier.clickable { onSelectWakeWord(phrase) }
                            ) {
                                Text(
                                    text = phrase,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) JarvisCyan else SoftLilac,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // 2. FEMALE VOICE STUDIO (REQUIREMENT C — REAL INSTALLED ENGINES & VOICES)
            // =========================================================================
            SectionHeader(
                icon = Icons.Default.RecordVoiceOver,
                title = "2. Female Voice Studio (Real Installed Voices)",
                tint = HotMagentaPink
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Voice: $selectedVoice",
                                fontWeight = FontWeight.Bold,
                                color = PureWhite,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (isVoiceMuted) "Voice Output Muted (Text Only)" else "Real Android TTS Voice Active",
                                fontSize = 11.sp,
                                color = if (isVoiceMuted) AlertRed else JarvisEmerald
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onPreviewVoice,
                                border = BorderStroke(1.dp, JarvisCyan),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("preview_voice_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Preview Voice",
                                    tint = JarvisCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Preview Voice", fontSize = 11.sp, color = JarvisCyan)
                            }

                            IconButton(
                                onClick = onToggleVoiceMute,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isVoiceMuted) AlertRed.copy(alpha = 0.25f) else JarvisCyan.copy(alpha = 0.16f))
                            ) {
                                Icon(
                                    imageVector = if (isVoiceMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Mute or Unmute Voice",
                                    tint = if (isVoiceMuted) AlertRed else JarvisCyan
                                )
                            }
                        }
                    }

                    // Installed TTS Engine Selector
                    if (installedTtsEngines.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Installed Speech Engines on Device (${installedTtsEngines.size}):",
                            fontSize = 11.sp,
                            color = AccentGold,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            installedTtsEngines.forEachIndexed { idx, engine ->
                                val isSelected = selectedTtsEnginePackage == engine.packageName ||
                                    (selectedTtsEnginePackage.isBlank() && idx == 0)
                                Surface(
                                    color = if (isSelected) JarvisCyan.copy(alpha = 0.2f) else Color(0xFF06101E),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (isSelected) JarvisCyan else JarvisHudBorder),
                                    modifier = Modifier.clickable { onSelectTtsEnginePackage(engine.packageName) }
                                ) {
                                    Text(
                                        text = "${engine.label} (${engine.packageName.substringAfterLast('.')})",
                                        fontSize = 11.sp,
                                        color = if (isSelected) JarvisCyan else SoftLilac,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Real Available Device Voices
                    Text(
                        text = "Available Device Voices (${availableDeviceVoices.size}):",
                        fontSize = 11.sp,
                        color = JarvisCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (availableDeviceVoices.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableDeviceVoices.forEach { voiceInfo ->
                                val isSelected = selectedVoiceId == voiceInfo.voiceId || selectedVoice == voiceInfo.displayName
                                val qualityDesc = if (voiceInfo.quality >= 400) "High Quality" else "Standard"
                                Surface(
                                    color = if (isSelected) HotMagentaPink.copy(alpha = 0.2f) else Color(0xFF06101E),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) HotMagentaPink else JarvisHudBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectDeviceVoice(voiceInfo) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = voiceInfo.displayName,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) PureWhite else SoftLilac
                                            )
                                            Text(
                                                text = "Locale: ${voiceInfo.localeTag} • Quality: $qualityDesc",
                                                fontSize = 10.sp,
                                                color = MutedVioletGray
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected Voice",
                                                tint = HotMagentaPink,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Pitch Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Voice Pitch", fontSize = 12.sp, color = SoftLilac)
                        Text(
                            String.format(Locale.US, "%.2fx", voicePitch),
                            fontSize = 12.sp,
                            color = AccentGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = voicePitch,
                        onValueChange = onVoicePitchChange,
                        valueRange = 0.7f..1.4f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentGold,
                            activeTrackColor = AccentGold
                        )
                    )

                    // Speed Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Speaking Speed", fontSize = 12.sp, color = SoftLilac)
                        Text(
                            String.format(Locale.US, "%.2fx", voiceSpeed),
                            fontSize = 12.sp,
                            color = JarvisCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = voiceSpeed,
                        onValueChange = onVoiceSpeedChange,
                        valueRange = 0.7f..1.4f,
                        colors = SliderDefaults.colors(
                            thumbColor = JarvisCyan,
                            activeTrackColor = JarvisCyan
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // 3. FLOATING CAROLINE WINDOW (REQUIREMENT F)
            // =========================================================================
            SectionHeader(
                icon = Icons.Default.OpenInFull,
                title = "3. Floating CaroLine Window & Overlay",
                tint = JarvisCyan
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Floating Assistant Bubble",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = PureWhite
                            )
                            Text(
                                text = "Movable floating bubble with Mic, Text Command, Send, AI Reply, Close & Expand",
                                fontSize = 11.sp,
                                color = SoftLilac
                            )
                        }
                        Switch(
                            checked = isFloatingBubbleEnabled,
                            onCheckedChange = { onToggleFloatingBubble() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = JarvisEmerald
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (canDrawSystemOverlays) {
                                "System Overlay Permission: Granted (Works over other apps)"
                            } else {
                                "In-App Floating Bubble Active (Grant permission for system-wide overlay)"
                            },
                            fontSize = 11.sp,
                            color = if (canDrawSystemOverlays) JarvisEmerald else AccentGold,
                            modifier = Modifier.weight(1f)
                        )
                        if (!canDrawSystemOverlays) {
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                    }
                                },
                                border = BorderStroke(1.dp, AccentGold)
                            ) {
                                Text("Grant Overlay", fontSize = 11.sp, color = AccentGold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // 4. PERFORMANCE PROFILES (REQUIREMENT A — 2GB LITE, 4GB STANDARD, 6GB+ ADVANCED)
            // =========================================================================
            SectionHeader(
                icon = Icons.Default.Speed,
                title = "4. Device RAM & Performance Profiles",
                tint = JarvisEmerald
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Detected Memory: ${availRamMb}MB Free / ${totalRamMb}MB Total RAM • Active: ${performanceProfile.displayName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = JarvisEmerald
                    )
                    Text(
                        text = performanceProfile.ramTierDescription,
                        fontSize = 11.sp,
                        color = SoftLilac
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PerformanceProfile.values().forEach { profile ->
                            val isSelected = performanceProfile == profile
                            Surface(
                                color = if (isSelected) JarvisEmerald.copy(alpha = 0.22f) else Color(0xFF06101E),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) JarvisEmerald else JarvisHudBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSelectPerformanceProfile(profile, false) }
                                    .testTag("perf_profile_${profile.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = profile.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) JarvisEmerald else PureWhite
                                    )
                                    Text(
                                        text = profile.id,
                                        fontSize = 10.sp,
                                        color = AccentGold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // 5. SMART CONTACT, CALL & MESSAGE QUICK ACTIONS (REQUIREMENTS E & G)
            // =========================================================================
            SectionHeader(
                icon = Icons.Default.Contacts,
                title = "5. Smart Contacts, Phone Call & Message Writer",
                tint = AccentGold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Search contacts by name or ending digits, dial safely, or draft polished messages with confirmation:",
                        fontSize = 11.sp,
                        color = SoftLilac
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val contactCommands = listOf(
                        "Find the contact ending in 45" to "Find Contact Ending in 45",
                        "Search contacts ending in 23" to "Contacts Ending in 23",
                        "Open phone dialer" to "Open Phone Dialer",
                        "Rahul ko bolo main aaj late aaunga" to "Draft Late Message to Rahul",
                        "Write a birthday wish for Priya" to "Draft Birthday Wish"
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            },
                            border = BorderStroke(1.dp, AccentGold)
                        ) {
                            Text("Grant Contacts Access", fontSize = 11.sp, color = AccentGold)
                        }

                        contactCommands.forEach { (cmd, label) ->
                            Surface(
                                color = Color(0xFF06101E),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f)),
                                modifier = Modifier.clickable {
                                    onExecuteQuickCommand(cmd)
                                    onDismiss()
                                }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    color = JarvisCyan,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // 6. LIVE WEATHER & INTERNET DATA USAGE (REQUIREMENT K)
            // =========================================================================
            SectionHeader(
                icon = Icons.Default.Cloud,
                title = "6. Live Weather & Internet Data Usage",
                tint = JarvisCyan
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = weatherCityInput,
                            onValueChange = { weatherCityInput = it },
                            label = { Text("City for Weather Forecast", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvisCyan,
                                unfocusedBorderColor = JarvisHudBorder,
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite
                            )
                        )
                        Button(
                            onClick = { onRefreshWeather(weatherCityInput) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisCyan,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Fetch Live Weather",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Weather", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    if (latestWeatherReport != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFF06101E),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "${latestWeatherReport.cityName}: ${latestWeatherReport.temperatureCelsius}°C • ${latestWeatherReport.conditionEn}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = AccentGold
                                )
                                Text(
                                    text = "Rain Probability: ${latestWeatherReport.rainProbabilityPercent}% • Wind: ${latestWeatherReport.windSpeedKmh} km/h",
                                    fontSize = 11.sp,
                                    color = PureWhite
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Network & Data Usage
                    val net = networkAndDataReport
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Network: ${net?.transportType ?: "Wi-Fi"} (${if (net?.isOnline == true) "Connected" else "Offline"})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = PureWhite
                            )
                            Text(
                                text = "Device Boot Data: Mobile ${net?.mobileDataUsedMb ?: 0.0} MB • Total ${net?.totalDataUsedMb ?: 0.0} MB",
                                fontSize = 11.sp,
                                color = JarvisCyan
                            )
                            Text(
                                text = "Exact remaining daily carrier balance requires carrier USSD/app as Android restricts SIM billing APIs.",
                                fontSize = 10.sp,
                                color = MutedVioletGray
                            )
                        }
                        IconButton(onClick = onRefreshTelemetry) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Network Data Usage",
                                tint = JarvisCyan
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // 7. MANAGED AI BRAIN & ADAPTIVE AI ENGINE (REQUIREMENTS D & L)
            // =========================================================================
            SectionHeader(
                icon = Icons.Default.AutoAwesome,
                title = "7. Managed AI Brain & Adaptive Engine (No Manual Key Required)",
                tint = JarvisCyan
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cloud AI Brain + Offline Smart Engine",
                                fontWeight = FontWeight.Bold,
                                color = PureWhite,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Configured automatically via managed backend service with instant offline fallback. No manual API key required.",
                                fontSize = 11.sp,
                                color = SoftLilac
                            )
                        }
                        Button(
                            onClick = { onTestProviderConnection(AiProviderType.GOOGLE_GEMINI) },
                            enabled = testingProvider == null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JarvisEmerald,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.height(38.dp)
                        ) {
                            if (testingProvider != null) {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Text("Verify AI", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }

                    providerTestResults[AiProviderType.GOOGLE_GEMINI]?.let { result ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${result.statusMessage} (${result.latencyMs}ms)",
                            fontSize = 11.sp,
                            color = if (result.isSuccess) JarvisEmerald else AccentGold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = JarvisHudBorder)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Adaptive AI Engine (25-Minute Cycle #${adaptiveAiSnapshot.cycleCount}): ${adaptiveAiSnapshot.adaptedToneSummary}",
                        fontSize = 11.sp,
                        color = AccentGold,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // 8. MUSIC, YOUTUBE & DEVICE CONTROLS (REQUIREMENTS H & J)
            // =========================================================================
            SectionHeader(
                icon = Icons.Default.MusicNote,
                title = "8. Music, YouTube & Mobile Controls",
                tint = JarvisEmerald
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val quickMediaCommands = listOf(
                        "Play Hindi romantic song on YouTube" to "Hindi Song",
                        "Play Arijit Singh songs" to "Arijit Singh",
                        "Play Bengali songs" to "Bengali Song",
                        "Play music" to "Built-in Synth",
                        "Increase volume" to "Volume Up",
                        "Decrease volume" to "Volume Down",
                        "Turn on flashlight" to "Flashlight ON",
                        "Turn off flashlight" to "Flashlight OFF",
                        "Check battery" to "Battery Status"
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickMediaCommands.forEach { (cmd, label) ->
                            Surface(
                                color = Color(0xFF06101E),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, JarvisEmerald.copy(alpha = 0.6f)),
                                modifier = Modifier.clickable {
                                    onExecuteQuickCommand(cmd)
                                    onDismiss()
                                }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    color = JarvisEmerald,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // 9. SMART ROUTINES, MEMORY & DAILY REMINDERS
            // =========================================================================
            SectionHeader(
                icon = Icons.Default.Memory,
                title = "9. Smart Routines, Memory & Daily Reminders",
                tint = AccentGold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        smartRoutines.forEach { routine ->
                            OutlinedButton(
                                onClick = {
                                    onExecuteRoutine(routine.id)
                                    onDismiss()
                                },
                                border = BorderStroke(1.dp, AccentGold)
                            ) {
                                Text(routine.nameEn, fontSize = 11.sp, color = AccentGold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newMemoryText,
                            onValueChange = { newMemoryText = it },
                            placeholder = { Text("Save a preference or note...", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvisCyan,
                                unfocusedBorderColor = JarvisHudBorder,
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite
                            )
                        )
                        IconButton(
                            onClick = {
                                if (newMemoryText.isNotBlank()) {
                                    onAddSmartMemory(newMemoryText)
                                    newMemoryText = ""
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(JarvisCyan)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Save Memory", tint = Color.Black)
                        }
                    }

                    if (smartMemories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        smartMemories.take(4).forEach { item ->
                            Text("• ${item.fact}", fontSize = 11.sp, color = SoftLilac)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newReminderTitle,
                            onValueChange = { newReminderTitle = it },
                            placeholder = { Text("Add daily reminder...", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGold,
                                unfocusedBorderColor = JarvisHudBorder,
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite
                            )
                        )
                        IconButton(
                            onClick = {
                                if (newReminderTitle.isNotBlank()) {
                                    onAddDailyReminder(newReminderTitle, newReminderTime)
                                    newReminderTitle = ""
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentGold)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Reminder", tint = Color.Black)
                        }
                    }

                    if (dailyReminders.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        dailyReminders.take(4).forEach { rem ->
                            Text("⏰ ${rem.title} (${rem.timeText})", fontSize = 11.sp, color = PureWhite)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // 10. DYNAMIC 50+ APP LAUNCHER (REQUIREMENT I)
            // =========================================================================
            SectionHeader(
                icon = Icons.Default.Apps,
                title = "10. Smart 50+ App Launcher",
                tint = JarvisCyan
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = onAppSearchQueryChange,
                        placeholder = { Text("Search 50+ apps (WhatsApp, YouTube, Maps, UPI...)", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisHudBorder,
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = PureWhite
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val filteredApps = remember(appCatalog, appSearchQuery) {
                        if (appSearchQuery.isBlank()) appCatalog.take(16)
                        else appCatalog.filter {
                            it.displayName.contains(appSearchQuery, ignoreCase = true) ||
                                it.hindiName.contains(appSearchQuery, ignoreCase = true) ||
                                it.category.contains(appSearchQuery, ignoreCase = true)
                        }.take(20)
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredApps.forEach { app ->
                            Surface(
                                color = Color(0xFF06101E),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (app.isInstalled) JarvisEmerald.copy(alpha = 0.6f) else JarvisHudBorder
                                ),
                                modifier = Modifier
                                    .width(125.dp)
                                    .clickable {
                                        onLaunchApp(app)
                                        onDismiss()
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = app.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = PureWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (app.isInstalled) "Installed" else "Play Store",
                                        fontSize = 10.sp,
                                        color = if (app.isInstalled) JarvisEmerald else AccentGold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // 11. THEME, AVATAR & VISUAL MODE
            // =========================================================================
            SectionHeader(
                icon = Icons.Default.Palette,
                title = "11. Theme & Avatar Customization",
                tint = SoftLilac
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Display Mode: ${if (isJarvisCoreMode) "JARVIS Holographic Core" else "CaroLine Anime Avatar"}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = PureWhite
                            )
                            Text(
                                text = "Switch between Anime Avatar and Holographic AI Core",
                                fontSize = 11.sp,
                                color = SoftLilac
                            )
                        }
                        Switch(
                            checked = isJarvisCoreMode,
                            onCheckedChange = { onToggleJarvisCoreMode() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = AccentGold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val themes = listOf("Anime-JARVIS Gold & Cyan", "Golden Cyber", "Deep Space")
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        themes.forEach { tName ->
                            val isSelected = selectedThemeName == tName
                            Surface(
                                color = if (isSelected) AccentGold.copy(alpha = 0.22f) else Color(0xFF06101E),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, if (isSelected) AccentGold else JarvisHudBorder),
                                modifier = Modifier.clickable { onSelectTheme(tName) }
                            ) {
                                Text(
                                    text = tName,
                                    fontSize = 11.sp,
                                    color = if (isSelected) AccentGold else PureWhite,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onUploadCustomAvatar,
                        border = BorderStroke(1.dp, JarvisCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose Custom Avatar Photo from Gallery", color = JarvisCyan, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // 12. CONVERSATION HISTORY, PRIVACY & ADVANCED SETTINGS (REQUIREMENTS M, N, O, P)
            // =========================================================================
            SectionHeader(
                icon = Icons.Default.Security,
                title = "12. Privacy, Battery Optimization & Advanced Settings",
                tint = AccentGold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Conversations (${conversationHistory.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = PureWhite
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onClearConversationHistory,
                                border = BorderStroke(1.dp, AlertRed),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Clear History", fontSize = 11.sp, color = AlertRed)
                            }
                            OutlinedButton(
                                onClick = onClearSmartMemories,
                                border = BorderStroke(1.dp, MutedVioletGray),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Clear Memory", fontSize = 11.sp, color = SoftLilac)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Battery-Safe Background Voice Service",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = PureWhite
                            )
                            Text(
                                text = "Pauses recognition when screen is off & prevents infinite restart loops",
                                fontSize = 11.sp,
                                color = SoftLilac
                            )
                        }
                        Switch(
                            checked = isBackgroundServiceEnabled,
                            onCheckedChange = { onToggleBackgroundService() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = JarvisCyan
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                }
                            },
                            border = BorderStroke(1.dp, JarvisCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Battery Optimization", fontSize = 11.sp, color = JarvisCyan)
                        }

                        OutlinedButton(
                            onClick = { showAdvancedSettings = !showAdvancedSettings },
                            border = BorderStroke(1.dp, AccentGold),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (showAdvancedSettings) "Hide Build Info" else "APK Build Guide",
                                fontSize = 11.sp,
                                color = AccentGold
                            )
                        }
                    }

                    if (showAdvancedSettings) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFF06101E),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, JarvisHudBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "APK Build & Installation Commands (A3.2):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = AccentGold
                                )
                                Text(
                                    text = "• Debug APK: gradle :app:assembleDebug\n• Release APK: gradle :app:assembleRelease\n• Output Path: app/build/outputs/apk/debug/app-debug.apk",
                                    fontSize = 11.sp,
                                    color = PureWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = PureWhite
        )
    }
}
