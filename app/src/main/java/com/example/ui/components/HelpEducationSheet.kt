package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpEducationSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepSurface,
        dragHandle = null,
        modifier = Modifier.testTag("help_education_sheet")
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
                            .background(ElectricViolet.copy(alpha = 0.25f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = "Help and Education",
                            tint = CyanAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "CaroLine AI Academy & Help",
                            color = TextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Guide & Voice Manual for Boss",
                            color = CrimsonRedLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_help_button")
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Identity Banner
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E0A24),
                        border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Identity",
                                    tint = AccentGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "System Architecture",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "• Assistant: CaroLine AI\n• AI Brain: SUMAN Neural Engine\n• Developer & Owner: Boss\n• Wake Word: \"Hey CaroLine\"\n• Spoken Idiom: Fluid Hindi + English + Hinglish",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Voice Commands in Hindi & Hinglish
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
                                    contentDescription = "Commands",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Spoken Hindi & Hinglish Commands",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            listOf(
                                Pair("\"Hey CaroLine, YouTube open karo\"", "Launches YouTube instantly"),
                                Pair("\"Boss ko roast karo\"", "Playful sassy comedy for Boss"),
                                Pair("\"Flashlight on / off kar do\"", "Controls device camera torch"),
                                Pair("\"Battery kitni bachi hai?\"", "Checks battery percentage & clock"),
                                Pair("\"Google par trending search karo\"", "Performs web search for Boss"),
                                Pair("\"Kya tum mujhse flirt kar rahi ho?\"", "Expressive flirty Hinglish response")
                            ).forEach { (cmd, desc) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "•",
                                        color = CrimsonRed,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                    Column {
                                        Text(text = cmd, color = CrimsonRedLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Text(text = desc, color = TextMuted, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Real-time Voice Features
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
                                    contentDescription = "Voice",
                                    tint = AccentGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Real-time Voice Capabilities",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "1. Barge-In Interruption: Boss can speak anytime while CaroLine is talking to immediately interrupt her.\n\n" +
                                        "2. Hands-Free Continuous Mode: When active, CaroLine automatically listens again after answering without tapping any button.\n\n" +
                                        "3. Dual API Auto-Failover: If Gemini Live rate-limits, CaroLine smoothly fails over to the backup AI engine.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
