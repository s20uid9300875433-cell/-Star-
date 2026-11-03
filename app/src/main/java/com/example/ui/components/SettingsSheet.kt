package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
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
    avatarMode: AvatarMode,
    toolHistory: List<ToolExecutionEvent>,
    onSavePrimaryApiKey: (String) -> Unit,
    onSaveBackupApiKey: (String) -> Unit,
    onChangeApiMode: (ApiMode) -> Unit,
    onSelectVoice: (String) -> Unit,
    onToggleHandsFree: () -> Unit,
    onToggleAvatarMode: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var primaryKeyInput by remember { mutableStateOf(primaryApiKey) }
    var backupKeyInput by remember { mutableStateOf(backupApiKey) }
    var showPrimaryKey by remember { mutableStateOf(false) }
    var showBackupKey by remember { mutableStateOf(false) }
    var isSavedConfirmation by remember { mutableStateOf(false) }

    val voices = listOf(
        Pair("Aoede", "Confident, lively & charming (Recommended for CaroLine)"),
        Pair("Kore", "Warm, sweet & playful"),
        Pair("Puck", "Playful & cheeky"),
        Pair("Fenrir", "Deep & bold"),
        Pair("Charon", "Calm & steady")
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
                            text = "CaroLine AI Settings",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AI Brain: SUMAN • Developer: Boss",
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
                // Security Notice Pill
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F1B22),
                        border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Security",
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Secure Storage: API keys are stored in encrypted client preferences and never hardcoded in frontend code.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                // Section 1: Dual API Configuration (Primary & Backup)
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
                                    text = "Dual API Configuration",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Real-time two-way voice uses Primary Gemini Live. If rate-limited or unavailable, auto-fails over to Backup API.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // 1A. Primary API Key
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
                                placeholder = { Text("Primary AI Studio Key...", color = TextMuted) },
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

                            // 1B. Backup API Key
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

                            // 1C. Failover Mode Selector
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

                            // Save Button
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
                                    Text("Keys Saved Securely!", fontWeight = FontWeight.Bold)
                                } else {
                                    Text("Save Dual API Keys", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Section 2: Conversation & Face Display Controls
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DeepSurfaceVariant,
                        border = BorderStroke(1.dp, DarkBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Hearing,
                                    contentDescription = "Voice Modes",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Conversation & Persona Mode",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            // Hands-free Toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Hands-Free Continuous Mode",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Auto-listens after CaroLine finishes speaking without tapping mic",
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

                            Spacer(modifier = Modifier.height(12.dp))

                            // Avatar Mode Toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Animated Face Expression Engine",
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (avatarMode == AvatarMode.PROCEDURAL_ANIMATED_FACE)
                                            "Active: Procedural listening, smiling, thinking & speaking face"
                                        else "Active: 2D Anime Avatar Art Mode",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = avatarMode == AvatarMode.PROCEDURAL_ANIMATED_FACE,
                                    onCheckedChange = { onToggleAvatarMode() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = CyanAccent
                                    )
                                )
                            }
                        }
                    }
                }

                // Section 3: Voice Selection
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
                                    text = "CaroLine's Spoken Voice",
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

                // Section 4: Tool Execution Log
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
                                    text = "Tool Execution Log for Boss",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            if (toolHistory.isEmpty()) {
                                Text(
                                    text = "No tools triggered yet, Boss! Say: 'YouTube kholo', 'Flashlight on karo', or 'Battery check karo'.",
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
