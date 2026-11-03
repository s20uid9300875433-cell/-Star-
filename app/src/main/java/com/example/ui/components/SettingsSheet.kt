package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.live.AiProviderType
import com.example.live.ConversationMessage
import com.example.live.DailyReminderItem
import com.example.live.ProviderConfig
import com.example.live.ProviderTestResult
import com.example.live.SmartMemoryItem
import com.example.live.SmartRoutineItem
import com.example.tools.AppEntry
import com.example.tools.ToolExecutionEvent
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AlertRed
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepNavy
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisEmerald
import com.example.ui.theme.JarvisHudBorder
import com.example.ui.theme.JarvisHudSurface
import com.example.ui.theme.MutedVioletGray
import com.example.ui.theme.PureWhite
import com.example.viewmodel.ActiveEngine
import com.example.viewmodel.ApiMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    selectedAiProvider: AiProviderType = AiProviderType.GOOGLE_GEMINI,
    autoFailoverEnabled: Boolean = true,
    providerConfigs: Map<AiProviderType, ProviderConfig> = emptyMap(),
    providerTestResults: Map<AiProviderType, ProviderTestResult> = emptyMap(),
    testingProvider: AiProviderType? = null,
    onSelectAiProvider: (AiProviderType) -> Unit = {},
    onToggleAutoFailover: () -> Unit = {},
    onSaveProviderConfig: (AiProviderType, String, String, String) -> Unit = { _, _, _, _ -> },
    onRemoveProviderKey: (AiProviderType) -> Unit = {},
    onTestProviderConnection: (AiProviderType) -> Unit = {},
    // Voice Engine 2.9
    selectedVoice: String = "Aoede (Soft Hindi)",
    voicePitch: Float = 1.08f,
    voiceSpeed: Float = 0.98f,
    isVoiceMuted: Boolean = false,
    useOpenAiSpeechTts: Boolean = false,
    openAiVoiceName: String = "nova",
    onSelectVoice: (String) -> Unit = {},
    onVoicePitchChange: (Float) -> Unit = {},
    onVoiceSpeedChange: (Float) -> Unit = {},
    onToggleVoiceMute: () -> Unit = {},
    onToggleOpenAiSpeechTts: () -> Unit = {},
    onSelectOpenAiVoiceName: (String) -> Unit = {},
    onPreviewVoice: () -> Unit = {},
    // Language & Wake Word
    activeLanguage: String = "हिन्दी (Hindi)",
    isLanguageLocked: Boolean = true,
    wakeWordEnabled: Boolean = true,
    selectedWakeWord: String = "Hey CaroLine",
    isHandsFreeMode: Boolean = true,
    isBackgroundServiceEnabled: Boolean = true,
    isForegroundServiceRunning: Boolean = false,
    onChangeLanguageWithPin: (String, String) -> Boolean = { _, _ -> false },
    onLockLanguageToHindi: () -> Unit = {},
    onToggleWakeWord: () -> Unit = {},
    onSelectWakeWord: (String) -> Unit = {},
    onToggleHandsFree: () -> Unit = {},
    onToggleBackgroundService: () -> Unit = {},
    // Music & Video Preferences
    preferredMusicService: String = "Auto",
    builtInMusicFallbackEnabled: Boolean = true,
    onSelectPreferredMusicService: (String) -> Unit = {},
    onToggleBuiltInMusicFallback: () -> Unit = {},
    onExecuteQuickCommand: (String) -> Unit = {},
    // Top 5 Smart Features (Memory, Reminders, Routines)
    smartMemories: List<SmartMemoryItem> = emptyList(),
    dailyReminders: List<DailyReminderItem> = emptyList(),
    smartRoutines: List<SmartRoutineItem> = emptyList(),
    onAddSmartMemory: (String) -> Unit = {},
    onClearSmartMemories: () -> Unit = {},
    onAddDailyReminder: (String, String) -> Unit = { _, _ -> },
    onExecuteRoutine: (String) -> Unit = {},
    // Installed Applications (Top 50) & History
    appCatalog: List<AppEntry> = emptyList(),
    appSearchQuery: String = "",
    onAppSearchQueryChange: (String) -> Unit = {},
    onLaunchApp: (AppEntry) -> Unit = {},
    conversationHistory: List<ConversationMessage> = emptyList(),
    onClearConversationHistory: () -> Unit = {},
    toolHistory: List<ToolExecutionEvent> = emptyList(),
    // Theme & Avatar
    selectedThemeName: String = "Anime-JARVIS Navy",
    isJarvisCoreMode: Boolean = false,
    avatarStyle: AvatarStyle = AvatarStyle.GOTHIC_CYBER,
    onSelectTheme: (String) -> Unit = {},
    onToggleJarvisCoreMode: () -> Unit = {},
    onSelectAvatarStyle: (AvatarStyle) -> Unit = {},
    onUploadCustomAvatar: () -> Unit = {},
    // Legacy compatibility parameters
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
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pinInput by remember { mutableStateOf("") }
    var targetLanguage by remember { mutableStateOf("हिन्दी (Hindi)") }
    var pinFeedback by remember { mutableStateOf<String?>(null) }
    var newMemoryInput by remember { mutableStateOf("") }
    var newReminderTitle by remember { mutableStateOf("") }
    var newReminderTime by remember { mutableStateOf("08:00 AM") }
    var showFullAppsList by remember { mutableStateOf(false) }
    var showFullHistory by remember { mutableStateOf(false) }

    val voices = listOf(
        "Aoede (Soft Hindi)",
        "Kore (Expressive)",
        "Leda (Warm Female)",
        "Nova (OpenAI)",
        "Shimmer (OpenAI)"
    )
    val openAiVoices = listOf("nova", "shimmer", "coral", "alloy")
    val wakeWords = listOf("Hey CaroLine", "CaroLine", "कैरोलाइन")
    val musicServices = listOf("Auto", "Spotify", "YouTube Music", "JioSaavn", "Wynk", "Built-in Hindi Synth")
    val themes = listOf("Anime-JARVIS Navy", "Golden Cyber", "Deep Space")
    val availableLanguages = listOf(
        "हिन्दी (Hindi)",
        "English",
        "বাংলা (Bengali)"
    )

    val hasMicPerm = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    val hasCamPerm = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JarvisDeepNavy,
        contentColor = PureWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
                .testTag("settings_dashboard_sheet")
        ) {
            // HEADER
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "CAROLINE AI 2.9 — ULTIMATE SMART JARVIS",
                        style = MaterialTheme.typography.titleMedium,
                        color = AccentGold,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "यूनिफाइड सेटिंग्स डैशबोर्ड • हिंदी फीमेल वॉइस • स्मार्ट जार्विस कंट्रोल",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // 1. ADVANCED FEMALE VOICE SETTINGS (PITCH, SPEED, PREVIEW, MUTE, OPENAI TTS)
            // =========================================================================
            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, AccentGold.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Female Voice Settings",
                                tint = AccentGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "फीमेल वॉइस इंजन (Female Voice Settings)",
                                style = MaterialTheme.typography.titleSmall,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = onToggleVoiceMute,
                            modifier = Modifier.testTag("voice_mute_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isVoiceMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isVoiceMuted) "Unmute Voice" else "Mute Voice",
                                tint = if (isVoiceMuted) AlertRed else JarvisEmerald
                            )
                        }
                    }

                    Text(
                        text = "प्राकृतिक हिंदी महिला आवाज़ (hi-IN) • उच्चारण, पिच और गति नियंत्रण",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedVioletGray
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        voices.forEach { voice ->
                            FilterChip(
                                selected = voice == selectedVoice,
                                onClick = { onSelectVoice(voice) },
                                label = { Text(voice, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGold.copy(alpha = 0.22f),
                                    selectedLabelColor = AccentGold,
                                    containerColor = JarvisDeepNavy,
                                    labelColor = PureWhite
                                ),
                                modifier = Modifier.testTag("voice_chip_${voice.take(5).lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Pitch Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "वॉइस पिच (Voice Pitch)",
                            fontSize = 12.sp,
                            color = PureWhite,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = String.format(Locale.US, "%.2fx", voicePitch),
                            fontSize = 12.sp,
                            color = JarvisCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = voicePitch,
                        onValueChange = onVoicePitchChange,
                        valueRange = 0.7f..1.4f,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentGold,
                            activeTrackColor = JarvisCyan
                        ),
                        modifier = Modifier.testTag("voice_pitch_slider")
                    )

                    // Speed Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "बोलने की गति (Speech Speed)",
                            fontSize = 12.sp,
                            color = PureWhite,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = String.format(Locale.US, "%.2fx", voiceSpeed),
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
                            thumbColor = AccentGold,
                            activeTrackColor = JarvisCyan
                        ),
                        modifier = Modifier.testTag("voice_speed_slider")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Real OpenAI Neural TTS Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OpenAI Neural Speech (tts-1) ऑडियो",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "OpenAI API Key सेट होने पर असली OpenAI न्यूरल आवाज़ का उपयोग करें (अन्यथा Android hi-IN फीमेल TTS)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedVioletGray
                            )
                        }
                        Switch(
                            checked = useOpenAiSpeechTts,
                            onCheckedChange = { onToggleOpenAiSpeechTts() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = JarvisCyan
                            ),
                            modifier = Modifier.testTag("openai_tts_switch")
                        )
                    }

                    if (useOpenAiSpeechTts) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            openAiVoices.forEach { oVoice ->
                                FilterChip(
                                    selected = openAiVoiceName == oVoice,
                                    onClick = { onSelectOpenAiVoiceName(oVoice) },
                                    label = { Text("OpenAI: ${oVoice.uppercase()}", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = JarvisCyan.copy(alpha = 0.25f),
                                        selectedLabelColor = JarvisCyan,
                                        containerColor = JarvisDeepNavy,
                                        labelColor = PureWhite
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onPreviewVoice,
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("voice_preview_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("आवाज़ टेस्ट करें (Preview)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onToggleVoiceMute,
                            border = BorderStroke(1.dp, if (isVoiceMuted) AlertRed else JarvisEmerald),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isVoiceMuted) "वॉइस अनम्यूट करें" else "वॉइस म्यूट करें",
                                color = if (isVoiceMuted) AlertRed else JarvisEmerald,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // 2. THREE AI BRAIN SYSTEM (GEMINI, CHATGPT, OPENROUTER)
            // =========================================================================
            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SyncAlt,
                            contentDescription = "Three AI Brain Selector",
                            tint = JarvisCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "इंटेलिजेंट AI ब्रेन (AI Provider Configuration)",
                            style = MaterialTheme.typography.titleSmall,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "डिफ़ॉल्ट AI प्रोवाइडर चुनें। एक प्रोवाइडर विफल होने पर ऑटो-फ़ेलओवर अपने आप दूसरे प्रोवाइडर का उपयोग करेगा।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedVioletGray
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiProviderType.values().forEach { provider ->
                            val isSelected = selectedAiProvider == provider
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectAiProvider(provider) },
                                label = {
                                    Text(
                                        text = provider.shortBadge,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JarvisCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = JarvisCyan,
                                    containerColor = JarvisDeepNavy.copy(alpha = 0.8f),
                                    labelColor = PureWhite
                                ),
                                modifier = Modifier.testTag("select_provider_${provider.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ऑटोमैटिक फ़ेलओवर (Auto-Fallback)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "प्राथमिक AI उपलब्ध न होने पर अपने आप बैकअप AI और स्मार्ट लोकल इंजन का उपयोग करें",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedVioletGray
                            )
                        }
                        Switch(
                            checked = autoFailoverEnabled,
                            onCheckedChange = { onToggleAutoFailover() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = JarvisElectricBlue
                            ),
                            modifier = Modifier.testTag("auto_failover_switch")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. INDIVIDUAL PROVIDER CREDENTIAL CARDS (GEMINI, CHATGPT, OPENROUTER)
            AiProviderType.values().forEach { providerType ->
                val currentConfig = providerConfigs[providerType] ?: ProviderConfig(providerType)
                val testResult = providerTestResults[providerType]
                val isTesting = testingProvider == providerType

                ProviderCredentialCard(
                    providerType = providerType,
                    config = currentConfig,
                    isSelectedDefault = selectedAiProvider == providerType,
                    testResult = testResult,
                    isTesting = isTesting,
                    onSave = { key, endpoint, model ->
                        onSaveProviderConfig(providerType, key, endpoint, model)
                        if (providerType == AiProviderType.GOOGLE_GEMINI) onSavePrimaryKey(key)
                        if (providerType == AiProviderType.OPENAI_CHATGPT) onSaveBackupKey(key)
                    },
                    onRemove = { onRemoveProviderKey(providerType) },
                    onTest = { onTestProviderConnection(providerType) }
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // =========================================================================
            // 4. TOP 5 NEW SMART FEATURES: SMART ROUTINES, SMART MEMORY & DAILY REMINDERS
            // =========================================================================
            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, AccentGold.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Smart Features",
                            tint = AccentGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "स्मार्ट रूटीन, मेमोरी और डेली असिस्टेंट",
                            style = MaterialTheme.typography.titleSmall,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1-टैप स्मार्ट रूटीन (Smart Routines):",
                        fontSize = 12.sp,
                        color = JarvisCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        smartRoutines.forEach { routine ->
                            OutlinedButton(
                                onClick = {
                                    onExecuteRoutine(routine.id)
                                    onDismiss()
                                },
                                border = BorderStroke(1.dp, AccentGold.copy(alpha = 0.65f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("routine_btn_${routine.id}")
                            ) {
                                Text(
                                    text = "⚡ ${routine.nameHi}",
                                    color = AccentGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MutedVioletGray.copy(alpha = 0.2f)
                    )

                    // Smart Memory Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "स्मार्ट मेमोरी (${smartMemories.size} यादें सहेजी गईं)",
                            fontSize = 12.sp,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                        if (smartMemories.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearSmartMemories,
                                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("clear_memories_button")
                            ) {
                                Text("मेमोरी साफ़ करें", color = AlertRed, fontSize = 10.sp)
                            }
                        }
                    }

                    smartMemories.take(4).forEach { mem ->
                        Text(
                            text = "• ${mem.fact}",
                            fontSize = 11.sp,
                            color = JarvisEmerald,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newMemoryInput,
                            onValueChange = { newMemoryInput = it },
                            placeholder = { Text("नई बात याद दिलाएं (जैसे: मेरा पसंदीदा गायक अरिजीत है)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_memory_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvisCyan,
                                unfocusedBorderColor = MutedVioletGray.copy(alpha = 0.4f),
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite
                            )
                        )
                        Button(
                            onClick = {
                                if (newMemoryInput.isNotBlank()) {
                                    onAddSmartMemory(newMemoryInput)
                                    newMemoryInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("add_memory_button")
                        ) {
                            Text("याद रखें", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MutedVioletGray.copy(alpha = 0.2f)
                    )

                    // Daily Reminders Section
                    Text(
                        text = "डेली रिमाइंडर (${dailyReminders.size}):",
                        fontSize = 12.sp,
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                    dailyReminders.take(4).forEach { rem ->
                        Text(
                            text = "⏰ ${rem.title} — ${rem.timeText}",
                            fontSize = 11.sp,
                            color = AccentGold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newReminderTitle,
                            onValueChange = { newReminderTitle = it },
                            placeholder = { Text("रिमाइंडर लिखें (जैसे: मीटिंग)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvisCyan,
                                unfocusedBorderColor = MutedVioletGray.copy(alpha = 0.4f),
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite
                            )
                        )
                        Button(
                            onClick = {
                                if (newReminderTitle.isNotBlank()) {
                                    onAddDailyReminder(newReminderTitle, newReminderTime)
                                    newReminderTitle = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("add_reminder_button")
                        ) {
                            Text("जोड़ें", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // 5. MUSIC & VIDEO PREFERENCES + QUICK TEST CONTROLS
            // =========================================================================
            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Music and Video Preferences",
                            tint = JarvisCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "म्यूजिक और वीडियो प्राथमिकताएं (Music & Video)",
                            style = MaterialTheme.typography.titleSmall,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "पसंदीदा म्यूजिक सर्विस चुनें:",
                        fontSize = 12.sp,
                        color = MutedVioletGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        musicServices.forEach { svc ->
                            FilterChip(
                                selected = preferredMusicService == svc,
                                onClick = { onSelectPreferredMusicService(svc) },
                                label = { Text(svc, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JarvisCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = JarvisCyan,
                                    containerColor = JarvisDeepNavy,
                                    labelColor = PureWhite
                                )
                            )
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
                                text = "इन-बिल्ट हिंदी मेलोडी सिंथेसाइज़र (Built-in Music)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "एक्सटर्नल म्यूजिक ऐप न होने पर तुरंत इन-ऐप हिंदी धुन बजाएं",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedVioletGray
                            )
                        }
                        Switch(
                            checked = builtInMusicFallbackEnabled,
                            onCheckedChange = { onToggleBuiltInMusicFallback() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = JarvisCyan
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Music & Video Verification Controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { onExecuteQuickCommand("कैरोलाइन गाना चलाओ") },
                            border = BorderStroke(1.dp, JarvisCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_music_play_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("गाना चलाओ", color = JarvisCyan, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onExecuteQuickCommand("अगला गाना चलाओ") },
                            border = BorderStroke(1.dp, AccentGold),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_music_next_button")
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = null, tint = AccentGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("अगला", color = AccentGold, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onExecuteQuickCommand("म्यूजिक बंद करो") },
                            border = BorderStroke(1.dp, AlertRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_music_stop_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = AlertRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("बंद करो", color = AlertRed, fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // 6. PERMANENT HINDI LANGUAGE LOCK & OWNER PIN
            // =========================================================================
            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Hindi Language Lock",
                            tint = JarvisCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "स्थायी हिंदी भाषा लॉक (Permanent Hindi Lock)",
                            style = MaterialTheme.typography.titleSmall,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "वर्तमान भाषा: $activeLanguage • स्थिति: ${if (isLanguageLocked) "लॉक (केवल हिंदी)" else "अनलॉक"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisEmerald,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "कैरोलाइन हमेशा डिफ़ॉल्ट रूप से सम्मानजनक हिंदी में बात करेगी। केवल बॉस पिन (डिफ़ॉल्ट: 1234) से ही भाषा बदली जा सकती है।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedVioletGray
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!isLanguageLocked) {
                        Button(
                            onClick = {
                                onLockLanguageToHindi()
                                onLockLanguageToBengali()
                                pinFeedback = "हिंदी भाषा फिर से स्थायी रूप से लॉक कर दी गई है।"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lock_hindi_button")
                        ) {
                            Text("हिंदी भाषा में फिर से लॉक करें", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableLanguages.forEach { lang ->
                            FilterChip(
                                selected = targetLanguage == lang,
                                onClick = { targetLanguage = lang },
                                label = { Text(lang) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JarvisCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = JarvisCyan,
                                    containerColor = JarvisDeepNavy,
                                    labelColor = PureWhite
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { pinInput = it },
                            label = { Text("बॉस पिन (Owner PIN)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("owner_pin_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvisCyan,
                                unfocusedBorderColor = MutedVioletGray.copy(alpha = 0.4f),
                                focusedTextColor = PureWhite,
                                unfocusedTextColor = PureWhite
                            )
                        )
                        Button(
                            onClick = {
                                val ok = onChangeLanguageWithPin(pinInput, targetLanguage)
                                pinFeedback = if (ok) {
                                    "भाषा सफलतापूर्वक बदली गई: $targetLanguage"
                                } else {
                                    "गलत पिन! भाषा बदलने की अनुमति नहीं है।"
                                }
                                pinInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("verify_pin_language_button")
                        ) {
                            Text("बदलें")
                        }
                    }

                    if (pinFeedback != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = pinFeedback!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pinFeedback!!.contains("गलत")) AlertRed else JarvisEmerald,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // 7. WAKE WORD, HANDS-FREE & BACKGROUND ASSISTANT
            // =========================================================================
            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "स्मार्ट वेक वर्ड (Smart Wake Word)",
                                style = MaterialTheme.typography.titleSmall,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "सक्रिय वेक वर्ड: \"$selectedWakeWord\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = JarvisCyan
                            )
                        }
                        Switch(
                            checked = wakeWordEnabled,
                            onCheckedChange = { onToggleWakeWord() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = AccentGold
                            ),
                            modifier = Modifier.testTag("wake_word_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        wakeWords.forEach { word ->
                            FilterChip(
                                selected = selectedWakeWord == word,
                                onClick = { onSelectWakeWord(word) },
                                label = { Text(word, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGold.copy(alpha = 0.22f),
                                    selectedLabelColor = AccentGold,
                                    containerColor = JarvisDeepNavy,
                                    labelColor = PureWhite
                                )
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MutedVioletGray.copy(alpha = 0.2f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "हैंड्स-फ़्री निरंतर मोड (Hands-Free)",
                                style = MaterialTheme.typography.titleSmall,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "बिना बार-बार बटन दबाए लगातार बातचीत और तुरंत इंटरप्शन (Barge-in)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedVioletGray
                            )
                        }
                        Switch(
                            checked = isHandsFreeMode,
                            onCheckedChange = { onToggleHandsFree() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = JarvisElectricBlue
                            ),
                            modifier = Modifier.testTag("hands_free_switch")
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MutedVioletGray.copy(alpha = 0.2f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "बैकग्राउंड असिस्टेंट सर्विस (Background Assistant)",
                                style = MaterialTheme.typography.titleSmall,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isForegroundServiceRunning) {
                                    "सक्रिय — ऐप मिनिमाइज़ होने पर भी फ़ोरग्राउंड सर्विस चालू है"
                                } else {
                                    "वॉइस मोड शुरू करने पर बैकग्राउंड में सक्रिय रहेगा"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isForegroundServiceRunning) JarvisEmerald else MutedVioletGray
                            )
                        }
                        Switch(
                            checked = isBackgroundServiceEnabled,
                            onCheckedChange = { onToggleBackgroundService() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = JarvisCyan
                            ),
                            modifier = Modifier.testTag("bg_service_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                } else {
                                    Intent(Settings.ACTION_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val fallback = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(fallback)
                                } catch (_: Exception) {}
                            }
                        },
                        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("battery_optimization_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = JarvisCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "बैटरी ऑप्टिमाइज़ेशन सेटिंग्स (Battery Optimization)",
                            color = JarvisCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // 8. INSTALLED APPLICATIONS (TOP 50 SMART APP LAUNCHER)
            // =========================================================================
            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = "Installed Apps",
                                tint = JarvisCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "इंस्टॉल्ड ऐप्स (Top 50 App Control)",
                                style = MaterialTheme.typography.titleSmall,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { showFullAppsList = !showFullAppsList },
                            border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("toggle_apps_list_button")
                        ) {
                            Text(
                                text = if (showFullAppsList) "छुपाएं" else "सभी 50 देखें",
                                color = JarvisCyan,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = onAppSearchQueryChange,
                        placeholder = { Text("ऐप खोजें (हिंदी या English: WhatsApp, YouTube, Spotify)...", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_app_search_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = MutedVioletGray.copy(alpha = 0.4f),
                            focusedTextColor = PureWhite,
                            unfocusedTextColor = PureWhite
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val filteredApps = remember(appCatalog, appSearchQuery, showFullAppsList) {
                        val list = if (appSearchQuery.isBlank()) {
                            appCatalog
                        } else {
                            val q = appSearchQuery.trim().lowercase()
                            appCatalog.filter {
                                it.displayName.lowercase().contains(q) ||
                                    it.hindiName.lowercase().contains(q) ||
                                    it.bengaliName.lowercase().contains(q) ||
                                    it.category.lowercase().contains(q)
                            }
                        }
                        if (showFullAppsList || appSearchQuery.isNotBlank()) list else list.take(8)
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredApps.forEach { app ->
                            Surface(
                                color = if (app.isInstalled) JarvisCyan.copy(alpha = 0.16f) else JarvisDeepNavy,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (app.isInstalled) JarvisCyan else MutedVioletGray.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .clickable {
                                        onLaunchApp(app)
                                        onDismiss()
                                    }
                                    .testTag("launch_app_${app.displayName.lowercase().replace(" ", "_")}")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${app.displayName} (${app.hindiName})",
                                        fontSize = 11.sp,
                                        color = PureWhite,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // 9. CONVERSATION HISTORY & PRIVACY CONTROLS
            // =========================================================================
            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Conversation History",
                                tint = JarvisCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "बातचीत का इतिहास (${conversationHistory.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { showFullHistory = !showFullHistory },
                                border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (showFullHistory) "छोटा करें" else "पूरा देखें",
                                    color = JarvisCyan,
                                    fontSize = 10.sp
                                )
                            }
                            if (conversationHistory.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = onClearConversationHistory,
                                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.testTag("clear_history_button")
                                ) {
                                    Text("साफ़ करें", color = AlertRed, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val itemsToShow = if (showFullHistory) conversationHistory.takeLast(20) else conversationHistory.takeLast(4)
                    if (itemsToShow.isEmpty()) {
                        Text(
                            text = "अभी तक कोई बातचीत दर्ज नहीं हुई है।",
                            fontSize = 11.sp,
                            color = MutedVioletGray
                        )
                    } else {
                        itemsToShow.forEach { msg ->
                            val isUserMsg = msg.role == "user"
                            Surface(
                                color = JarvisDeepNavy.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "${if (isUserMsg) "बॉस" else "कैरोलाइन"} • ${msg.providerBadge}",
                                        fontSize = 10.sp,
                                        color = if (isUserMsg) JarvisCyan else AccentGold,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = msg.text,
                                        fontSize = 12.sp,
                                        color = PureWhite,
                                        maxLines = if (showFullHistory) 10 else 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // 10. THEME, AVATAR MODE, PERMISSIONS & PRIVACY
            // =========================================================================
            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, JarvisHudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Theme & Avatar",
                            tint = AccentGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "थीम, अवतार, प्राइवेसी और अनुमतियां",
                            style = MaterialTheme.typography.titleSmall,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        themes.forEach { themeName ->
                            FilterChip(
                                selected = selectedThemeName == themeName,
                                onClick = { onSelectTheme(themeName) },
                                label = { Text(themeName, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AccentGold.copy(alpha = 0.22f),
                                    selectedLabelColor = AccentGold,
                                    containerColor = JarvisDeepNavy,
                                    labelColor = PureWhite
                                )
                            )
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
                                text = "होलोग्राफिक JARVIS कोर मोड (Holographic Core)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "बंद होने पर एनीमे अवतार (caroline_avatar_main) दिखेगा",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedVioletGray
                            )
                        }
                        Switch(
                            checked = isJarvisCoreMode,
                            onCheckedChange = { onToggleJarvisCoreMode() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = JarvisCyan
                            ),
                            modifier = Modifier.testTag("toggle_jarvis_core_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AvatarStyle.values().take(3).forEach { style ->
                            FilterChip(
                                selected = avatarStyle == style,
                                onClick = { onSelectAvatarStyle(style) },
                                label = { Text(style.name.replace("_", " "), fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = JarvisCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = JarvisCyan,
                                    containerColor = JarvisDeepNavy,
                                    labelColor = PureWhite
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onUploadCustomAvatar,
                        border = BorderStroke(1.dp, AccentGold.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("गैलरी से कस्टम अवतार चुनें (Upload Avatar)", color = AccentGold, fontSize = 11.sp)
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MutedVioletGray.copy(alpha = 0.2f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Permissions & Privacy",
                            tint = JarvisEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "माइक्रोफ़ोन: ${if (hasMicPerm) "अनुमति प्राप्त ✓" else "आवश्यक"} • कैमरा: ${if (hasCamPerm) "अनुमति प्राप्त ✓" else "वैकल्पिक"}",
                            fontSize = 11.sp,
                            color = JarvisEmerald,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "प्राइवेसी सुरक्षा: आपकी सभी API Keys और स्मार्ट मेमोरी आपके डिवाइस पर सुरक्षित रूप से एन्क्रिप्टेड/लोकल रहती हैं।",
                        fontSize = 11.sp,
                        color = MutedVioletGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =========================================================================
            // 11. ABOUT CAROLINE 2.9 & RECENT TOOL LOGS
            // =========================================================================
            Surface(
                color = JarvisHudSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, AccentGold.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About CaroLine 2.9",
                            tint = AccentGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "About CaroLine AI 2.9 – Ultimate Smart JARVIS",
                            style = MaterialTheme.typography.titleSmall,
                            color = AccentGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "संस्करण: 2.9 (Build 3) • स्थायी हिंदी पर्सनैलिटी • 3-AI ब्रेन (Gemini, ChatGPT, OpenRouter) • इन-बिल्ट हिंदी म्यूजिक और डायरेक्ट यूट्यूब वीडियो इंजन • Android 8–15 सपोर्ट।",
                        fontSize = 11.sp,
                        color = PureWhite
                    )

                    if (toolHistory.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "हाल ही में निष्पादित कमांड (Executed Tools):",
                            fontSize = 11.sp,
                            color = JarvisCyan,
                            fontWeight = FontWeight.Bold
                        )
                        toolHistory.take(4).forEach { event ->
                            Text(
                                text = "⚡ ${event.toolName}: ${event.summary}",
                                fontSize = 11.sp,
                                color = MutedVioletGray,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCredentialCard(
    providerType: AiProviderType,
    config: ProviderConfig,
    isSelectedDefault: Boolean,
    testResult: ProviderTestResult?,
    isTesting: Boolean,
    onSave: (String, String, String) -> Unit,
    onRemove: () -> Unit,
    onTest: () -> Unit
) {
    var keyDraft by remember(config.apiKey) { mutableStateOf(config.apiKey) }
    var endpointDraft by remember(config.endpointUrl) { mutableStateOf(config.endpointUrl) }
    var modelDraft by remember(config.modelName) { mutableStateOf(config.modelName) }
    var showKey by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }

    val borderColor = if (isSelectedDefault) JarvisCyan else JarvisHudBorder

    Surface(
        color = JarvisHudSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (isSelectedDefault) 1.5.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_card_${providerType.name.lowercase()}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = if (config.hasValidKey()) JarvisEmerald else JarvisCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = providerType.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (config.hasValidKey()) "API Key सुरक्षित है • Model: ${config.modelName}"
                            else "कोई API Key सेट नहीं है (Secrets या नीचे दर्ज करें)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (config.hasValidKey()) JarvisEmerald else MutedVioletGray
                        )
                    }
                }

                if (isSelectedDefault) {
                    Surface(
                        color = JarvisCyan.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "DEFAULT",
                            color = JarvisCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_key_input_${providerType.name.lowercase()}"),
                placeholder = { Text("${providerType.shortBadge} API Key यहाँ पेस्ट करें...") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Key Visibility",
                                tint = MutedVioletGray
                            )
                        }
                        if (keyDraft.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    keyDraft = ""
                                    onRemove()
                                },
                                modifier = Modifier.testTag("remove_key_${providerType.name.lowercase()}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Remove API Key",
                                    tint = AlertRed
                                )
                            }
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = MutedVioletGray.copy(alpha = 0.4f),
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showAdvanced = !showAdvanced },
                    border = BorderStroke(1.dp, MutedVioletGray.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (showAdvanced) "Endpoint छुपाएं" else "Endpoint व Model",
                        color = MutedVioletGray,
                        fontSize = 11.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onSave(keyDraft, endpointDraft, modelDraft)
                            onTest()
                        },
                        enabled = !isTesting,
                        border = BorderStroke(1.dp, JarvisCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("test_conn_${providerType.name.lowercase()}")
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                color = JarvisCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = null,
                                tint = JarvisCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "टेस्ट",
                            color = JarvisCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { onSave(keyDraft, endpointDraft, modelDraft) },
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_key_${providerType.name.lowercase()}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("सेव", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (showAdvanced) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = endpointDraft,
                    onValueChange = { endpointDraft = it },
                    label = { Text("Endpoint / Secure Proxy URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = MutedVioletGray.copy(alpha = 0.4f),
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = modelDraft,
                    onValueChange = { modelDraft = it },
                    label = { Text("AI Model Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = MutedVioletGray.copy(alpha = 0.4f),
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite
                    )
                )
            }

            if (testResult != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = if (testResult.isSuccess) JarvisEmerald.copy(alpha = 0.14f) else AlertRed.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (testResult.isSuccess) JarvisEmerald.copy(alpha = 0.5f) else AlertRed.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(10.dp)
                    ) {
                        Icon(
                            imageVector = if (testResult.isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (testResult.isSuccess) JarvisEmerald else AlertRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = testResult.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = PureWhite,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
