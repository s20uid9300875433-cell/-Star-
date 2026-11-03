package com.example.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tools.ToolExecutionEvent
import com.example.ui.theme.AccentGold
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CrimsonRedLight
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DeepSurface
import com.example.ui.theme.DeepSurfaceVariant
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.ApiMode
import com.example.viewmodel.AvatarMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    primaryApiKey: String,
    backupApiKey: String,
    apiMode: ApiMode,
    selectedVoice: String,
    isHandsFreeMode: Boolean,
    isBackgroundServiceEnabled: Boolean = true,
    activeLanguage: String = "বাংলা (Bengali)",
    isLanguageLocked: Boolean = true,
    avatarMode: AvatarMode = AvatarMode.PROCEDURAL_ANIMATED_FACE,
    toolHistory: List<ToolExecutionEvent>,
    onSavePrimaryApiKey: (String) -> Unit,
    onSaveBackupApiKey: (String) -> Unit,
    onChangeApiMode: (ApiMode) -> Unit,
    onSelectVoice: (String) -> Unit,
    onToggleHandsFree: () -> Unit,
    onToggleBackgroundService: () -> Unit = {},
    onChangeLanguageWithPin: (String, String) -> Boolean = { _, _ -> false },
    onLockLanguageToBengali: () -> Unit = {},
    onToggleAvatarMode: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var primaryKeyInput by remember { mutableStateOf(primaryApiKey) }
    var backupKeyInput by remember { mutableStateOf(backupApiKey) }
    var showPrimaryKey by remember { mutableStateOf(false) }
    var showBackupKey by remember { mutableStateOf(false) }
    var isSavedConfirmation by remember { mutableStateOf(false) }

    var ownerPinInput by remember { mutableStateOf("") }
    var pinStatusMessage by remember { mutableStateOf<String?>(null) }

    val voices = listOf(
        Pair("Aoede", "স্বাভাবিক ও মিষ্টি নারী কণ্ঠ (ক্যারোলাইনের জন্য প্রস্তাবিত)"),
        Pair("Kore", "উষ্ণ ও বন্ধুসুলভ নারী কণ্ঠ"),
        Pair("Puck", "প্রাণবন্ত ও চঞ্চল কণ্ঠ"),
        Pair("Fenrir", "গম্ভীর ও স্পষ্ট কণ্ঠ"),
        Pair("Charon", "শান্ত ও স্থির কণ্ঠ")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepSurface,
        dragHandle = null,
        modifier = Modifier.testTag("settings_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(CrimsonRed.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = CrimsonRed,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "CaroLine AI সেটিংস",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "GREAT V2.1 • বাংলা ভয়েস অ্যাসিস্ট্যান্ট",
                            color = CyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Section 1: Bengali Language Lock & Owner PIN
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DeepSurfaceVariant,
                        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Language Lock",
                                        tint = CyanAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "বাংলা ভাষা লক (Default Language Lock)",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isLanguageLocked) CyanAccent.copy(alpha = 0.2f) else AccentGold.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (isLanguageLocked) "লক করা (বাংলা)" else activeLanguage,
                                        color = if (isLanguageLocked) CyanAccent else AccentGold,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "ক্যারোলাইন সবসময় ডিফল্টভাবে বাংলায় কথা বলবে। অন্য কেউ অন্য ভাষায় কথা বললেও ভাষা পরিবর্তন হবে না। শুধুমাত্র অনুমোদিত মালিক পিন (ডিফল্ট: 1234) দিয়ে ভাষা পরিবর্তন করতে পারবেন।",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = ownerPinInput,
                                onValueChange = {
                                    ownerPinInput = it
                                    pinStatusMessage = null
                                },
                                placeholder = { Text("মালিকের গোপন পিন দিন (যেমন: 1234)", color = TextMuted) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanAccent,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = CyanAccent
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("owner_pin_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        onLockLanguageToBengali()
                                        pinStatusMessage = "বাংলা ভাষা স্থায়ী ডিফল্ট হিসেবে লক করা হয়েছে।"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent.copy(alpha = 0.25f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("বাংলা লক রাখুন", color = CyanAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val ok = onChangeLanguageWithPin(ownerPinInput, "English")
                                        pinStatusMessage = if (ok) {
                                            "পিন যাচাই সফল! ভাষা পরিবর্তন করা হয়েছে।"
                                        } else {
                                            "ভুল পিন! ভাষা পরিবর্তন অনুমোদিত নয়।"
                                        }
                                    },
                                    border = BorderStroke(1.dp, DarkBorder),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("পিন দিয়ে পরিবর্তন", color = TextSecondary, fontSize = 12.sp)
                                }
                            }

                            if (pinStatusMessage != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = pinStatusMessage!!,
                                    color = AccentGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Section 2: Background Voice Service & Battery Optimization
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DeepSurfaceVariant,
                        border = BorderStroke(1.dp, DarkBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.BatteryChargingFull,
                                    contentDescription = "Background Service",
                                    tint = CrimsonRedLight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ব্যাকগ্রাউন্ড ভয়েস সার্ভিস ও অটো-মাইক্রোফোন",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            // Hands-free Toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "অটোমেটিক কন্টিনিউয়াস মাইক্রোফোন",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "ক্যারোলাইন উত্তর দেওয়ার পর স্বয়ংক্রিয়ভাবে আবার আপনার কথা শুনবে",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = isHandsFreeMode,
                                    onCheckedChange = { onToggleHandsFree() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = CrimsonRed
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Foreground Service Toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "অ্যান্ড্রয়েড ব্যাকগ্রাউন্ড ফোরগ্রাউন্ড সার্ভিস",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "অ্যাপ মিনিমাইজ বা স্ক্রিন বন্ধ থাকলেও নোটিফিকেশনের মাধ্যমে ভয়েস মোড চালু রাখে",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = isBackgroundServiceEnabled,
                                    onCheckedChange = { onToggleBackgroundService() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = CyanAccent
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                    }
                                },
                                border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "ব্যাটারি অপটিমাইজেশন সেটিংস খুলুন (Unrestricted করুন)",
                                    color = CyanAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Section 3: Dual API Configuration (Primary & Backup)
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DeepSurfaceVariant,
                        border = BorderStroke(1.dp, DarkBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "API Keys",
                                    tint = AccentGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Dual API Configuration (ঐচ্ছিক)",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "API Key ছাড়াও ক্যারোলাইনের বিল্ট-ইন বাংলা ভয়েস ইঞ্জিন কাজ করে। রিয়েল-টাইম Gemini Live 24kHz অডিওর জন্য নিচে কী যোগ করতে পারেন।",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Primary API Key
                            Text(
                                text = "Primary API Key (Gemini Live 24kHz)",
                                color = CrimsonRedLight,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = primaryKeyInput,
                                onValueChange = {
                                    primaryKeyInput = it
                                    isSavedConfirmation = false
                                },
                                placeholder = { Text("Primary Gemini API Key...", color = TextMuted) },
                                singleLine = true,
                                visualTransformation = if (showPrimaryKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPrimaryKey = !showPrimaryKey }) {
                                        Icon(
                                            imageVector = if (showPrimaryKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle visibility",
                                            tint = TextMuted
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CrimsonRed,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = CrimsonRed
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("primary_api_key_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Backup API Key
                            Text(
                                text = "Backup API Key (Secondary AI API)",
                                color = CyanAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = backupKeyInput,
                                onValueChange = {
                                    backupKeyInput = it
                                    isSavedConfirmation = false
                                },
                                placeholder = { Text("Secondary Backup Key...", color = TextMuted) },
                                singleLine = true,
                                visualTransformation = if (showBackupKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showBackupKey = !showBackupKey }) {
                                        Icon(
                                            imageVector = if (showBackupKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "Toggle visibility",
                                            tint = TextMuted
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanAccent,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = CyanAccent
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("backup_api_key_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Failover Mode Selector
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.AltRoute,
                                        contentDescription = "Failover Mode",
                                        tint = AccentGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Routing Strategy",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ApiMode.values().forEach { mode ->
                                        val isSelected = apiMode == mode
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) CrimsonRed.copy(alpha = 0.25f) else Color.Transparent,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSelected) CrimsonRed else DarkBorder
                                            ),
                                            modifier = Modifier
                                                .clickable { onChangeApiMode(mode) }
                                                .padding(2.dp)
                                        ) {
                                            Text(
                                                text = when (mode) {
                                                    ApiMode.AUTO_FAILOVER -> "Auto"
                                                    ApiMode.PRIMARY_ONLY -> "Primary"
                                                    ApiMode.BACKUP_ONLY -> "Backup"
                                                },
                                                color = if (isSelected) CrimsonRedLight else TextMuted,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    onSavePrimaryApiKey(primaryKeyInput)
                                    onSaveBackupApiKey(backupKeyInput)
                                    isSavedConfirmation = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CrimsonRed
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("save_api_keys_button")
                            ) {
                                if (isSavedConfirmation) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Saved",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("API কী সংরক্ষিত হয়েছে!", fontWeight = FontWeight.Bold)
                                } else {
                                    Text("API কী সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Section 4: Voice Selection
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DeepSurfaceVariant,
                        border = BorderStroke(1.dp, DarkBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = "Voice",
                                    tint = ElectricViolet,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ক্যারোলাইনের কণ্ঠস্বর (Female Voice)",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            voices.forEach { (voiceId, desc) ->
                                val isSelected = selectedVoice.equals(voiceId, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) CrimsonRed.copy(alpha = 0.15f) else Color.Transparent,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) CrimsonRed else DarkBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { onSelectVoice(voiceId) }
                                        .testTag("voice_option_$voiceId")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = voiceId,
                                                color = if (isSelected) CrimsonRedLight else TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = desc,
                                                color = TextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = CrimsonRed,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 5: Tool Execution Log
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DeepSurfaceVariant,
                        border = BorderStroke(1.dp, DarkBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.SmartToy,
                                    contentDescription = "Tools",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "কমান্ড ও টুল লগ",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            if (toolHistory.isEmpty()) {
                                Text(
                                    text = "এখনও কোনো ডিভাইস টুল ব্যবহৃত হয়নি। বলুন: 'সময় কত?', 'মোবাইলের ব্যাটারি কত?', অথবা 'ফ্ল্যাশলাইট চালু করো'।",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            } else {
                                toolHistory.take(5).forEach { event ->
                                    val timeStr = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
                                        .format(Date(event.timestamp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "•",
                                            color = CrimsonRed,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = event.summary,
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "${event.toolName} • $timeStr",
                                                color = TextMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
