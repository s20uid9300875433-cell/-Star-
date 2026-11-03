package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.AiProviderType
import com.example.live.ProviderConfig
import com.example.live.ProviderTestResult
import com.example.tools.ToolExecutionEvent
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
    // Legacy compatibility parameters
    primaryApiKey: String = "",
    backupApiKey: String = "",
    apiMode: ApiMode = ApiMode.AUTO_FAILOVER,
    activeEngine: ActiveEngine = ActiveEngine.BENGALI_VOICE_ENGINE,
    selectedVoice: String = "Aoede",
    activeLanguage: String = "বাংলা (Bengali)",
    isLanguageLocked: Boolean = true,
    isHandsFreeMode: Boolean = true,
    isBackgroundServiceEnabled: Boolean = true,
    isForegroundServiceRunning: Boolean = false,
    toolHistory: List<ToolExecutionEvent> = emptyList(),
    onSavePrimaryKey: (String) -> Unit = {},
    onSaveBackupKey: (String) -> Unit = {},
    onSelectApiMode: (ApiMode) -> Unit = {},
    onSelectVoice: (String) -> Unit = {},
    onChangeLanguageWithPin: (String, String) -> Boolean = { _, _ -> false },
    onLockLanguageToBengali: () -> Unit = {},
    onToggleHandsFree: () -> Unit = {},
    onToggleBackgroundService: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var pinInput by remember { mutableStateOf("") }
    var targetLanguage by remember { mutableStateOf("English") }
    var pinFeedback by remember { mutableStateOf<String?>(null) }

    val voices = listOf("Aoede", "Kore", "Leda", "Zephyr", "Puck")
    val availableLanguages = listOf(
        "বাংলা (Bengali)",
        "English",
        "Hindi (हिन्दी)"
    )

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
                .padding(bottom = 36.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "CAROLINE AI 2.5 — SMART JARVIS",
                        style = MaterialTheme.typography.titleLarge,
                        color = PureWhite,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "থ্রি-এআই ব্রেইন সিস্টেম • বাংলা ভয়েস অ্যাসিস্ট্যান্ট • স্মার্ট কন্ট্রোল",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. THREE AI BRAIN SYSTEM SELECTOR & AUTO-FAILOVER
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
                            text = "থ্রি-এআই ব্রেইন সিস্টেম (Three AI Brain System)",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ডিফল্ট এআই প্রোভাইডার নির্বাচন করুন। একটি প্রোভাইডার ব্যর্থ হলে স্বয়ংক্রিয়ভাবে পরবর্তী সক্রিয় প্রোভাইডার ব্যবহার হবে।",
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
                                text = "অটো-ফেইলওভার (Automatic Failover)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureWhite,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "প্রাথমিক এআই প্রোভাইডার সাড়া না দিলে স্বয়ংক্রিয়ভাবে ব্যাকআপ এআই ও জারভিস লোকাল ইঞ্জিনে স্যুইচ করবে",
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

            Spacer(modifier = Modifier.height(16.dp))

            // 2. INDIVIDUAL PROVIDER CREDENTIAL CARDS (GEMINI, CHATGPT, OPENROUTER)
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

                Spacer(modifier = Modifier.height(14.dp))
            }

            // 3. BENGALI LANGUAGE LOCK & OWNER PIN SECTION
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
                            contentDescription = "Bengali Language Lock",
                            tint = JarvisCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "স্থায়ী বাংলা ভাষা লক (Bengali Default Lock)",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "বর্তমান ভাষা: $activeLanguage • স্ট্যাটাস: ${if (isLanguageLocked) "লক করা (শুধুমাত্র বাংলা)" else "আনলক করা"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = JarvisEmerald,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "অন্য কেউ অন্য ভাষায় কথা বললেও ক্যারোলাইন স্বয়ংক্রিয়ভাবে ভাষা পরিবর্তন করবে না। শুধুমাত্র অনুমোদিত মালিক পিন (ডিফল্ট: 1234) দিয়ে ভাষা পরিবর্তন করা যাবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedVioletGray
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!isLanguageLocked) {
                        Button(
                            onClick = {
                                onLockLanguageToBengali()
                                pinFeedback = "বাংলা ভাষা পুনরায় স্থায়ীভাবে লক করা হয়েছে।"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lock_bengali_button")
                        ) {
                            Text("বাংলা ভাষায় পুনরায় লক করুন", fontWeight = FontWeight.Bold)
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
                            label = { Text("মালিকের পিন (Owner PIN)") },
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
                                    "ভাষা পরিবর্তন সফল: $targetLanguage"
                                } else {
                                    "ভুল পিন! ভাষা পরিবর্তন অনুমোদিত নয়।"
                                }
                                pinInput = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisElectricBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("verify_pin_language_button")
                        ) {
                            Text("পরিবর্তন")
                        }
                    }

                    if (pinFeedback != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = pinFeedback!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pinFeedback!!.contains("ভুল")) AlertRed else JarvisEmerald,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. HANDS-FREE & FOREGROUND BACKGROUND SERVICE
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
                                text = "হ্যান্ডস-ফ্রি কন্টিনিউয়াস মোড (Hands-Free)",
                                style = MaterialTheme.typography.titleSmall,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "বারবার মাইক্রোফোন বাটনে ট্যাপ না করেই একটানা বাংলা কথোপকথন ও ইন্টারাপশন",
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
                                text = "ব্যাকগ্রাউন্ড ভয়েস সার্ভিস (Background Mic)",
                                style = MaterialTheme.typography.titleSmall,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isForegroundServiceRunning) {
                                    "সচল আছে — স্ক্রিন বন্ধ বা অ্যাপ মিনিমাইজ থাকলেও নোটিফিকেশনের মাধ্যমে সক্রিয় থাকবে"
                                } else {
                                    "ভয়েস মোড চালু করলে ফোরগ্রাউন্ড সার্ভিস হিসেবে ব্যাকগ্রাউন্ডে সচল থাকবে"
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
                            text = "ব্যাটারি অপটিমাইজেশন সেটিংস (Background Stability)",
                            color = JarvisCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. FEMALE VOICE SELECTOR
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = "Voice Profile",
                    tint = JarvisCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ক্যারোলাইনের কণ্ঠস্বর (Female Voice Profile)",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                voices.forEach { voice ->
                    FilterChip(
                        selected = voice == selectedVoice,
                        onClick = { onSelectVoice(voice) },
                        label = { Text(voice) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JarvisCyan.copy(alpha = 0.25f),
                            selectedLabelColor = JarvisCyan,
                            containerColor = JarvisHudSurface,
                            labelColor = PureWhite
                        ),
                        modifier = Modifier.testTag("voice_chip_${voice.lowercase()}")
                    )
                }
            }

            // 6. RECENT TOOL EXECUTION LOG
            if (toolHistory.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = MutedVioletGray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "সাম্প্রতিক স্মার্ট কমান্ড লগ (Executed Android Tools)",
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite
                )
                Spacer(modifier = Modifier.height(8.dp))

                toolHistory.take(5).forEach { event ->
                    Surface(
                        color = JarvisHudSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "⚡ ${event.toolName}",
                                style = MaterialTheme.typography.labelLarge,
                                color = JarvisCyan
                            )
                            Text(
                                text = event.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedVioletGray
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
                            text = if (config.hasValidKey()) "API Key সংরক্ষিত আছে • Model: ${config.modelName}"
                            else "কোনো API Key সেট করা নেই (Secrets বা নিচে যোগ করুন)",
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
                placeholder = { Text("${providerType.shortBadge} API Key পেস্ট করুন...") },
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
                        text = if (showAdvanced) "Endpoint ও Model লুকান" else "Endpoint ও Model কাস্টমাইজ",
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
                            text = "টেস্ট",
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
                        Text("সেভ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
