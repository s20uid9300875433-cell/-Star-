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
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
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
                            text = "CaroLine AI — GREAT V2.1",
                            color = TextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "বাংলা ভয়েস কমান্ড গাইড ও নির্দেশিকা",
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
                        color = DeepSurfaceVariant,
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
                                    text = "CaroLine AI • GREAT V2.1",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "• অ্যাপের নাম: CaroLine AI\n• সংস্করণ: GREAT V2.1\n• ডিফল্ট ভাষা: বাংলা (স্থায়ীভাবে লক করা)\n• ওয়েক ওয়ার্ড: \"হ্যালো ক্যারোলাইন\" / \"Hey CaroLine\"\n• কণ্ঠস্বর: স্বাভাবিক বাংলা নারী কণ্ঠস্বর",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Bengali Voice Commands
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
                                    text = "উন্নত বাংলা ভয়েস কমান্ডসমূহ",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            listOf(
                                Pair("\"হ্যালো ক্যারোলাইন\"", "ক্যারোলাইনকে জাগিয়ে তোলা এবং কথা শুরু করা"),
                                Pair("\"ক্যারোলাইন, আমার কথা শোনো\"", "তাৎক্ষণিক মনোযোগ ও ভয়েস লিসেনিং সক্রিয় করা"),
                                Pair("\"সময় কত?\"", "বর্তমান সময় ও তারিখ বাংলায় বলা"),
                                Pair("\"আজকের আবহাওয়া বলো\"", "আবহাওয়ার আপডেট বাংলায় শোনানো"),
                                Pair("\"মোবাইলের ব্যাটারি কত?\"", "ফোনের ব্যাটারি চার্জ কত শতাংশ আছে তা বলা"),
                                Pair("\"আবার কথা বলো\"", "সর্বশেষ উত্তরটি আবার বাংলায় শোনানো"),
                                Pair("\"ভয়েস মোড বন্ধ করো\"", "ভয়েস সেশন ও ব্যাকগ্রাউন্ড সার্ভিস বন্ধ করা")
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
                                        Text(text = desc, color = TextMuted, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Real-time Voice & Background Capabilities
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
                                    text = "স্বয়ংক্রিয় মাইক্রোফোন ও ব্যাকগ্রাউন্ড সার্ভিস",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "১. স্বয়ংক্রিয় মাইক্রোফোন: ভয়েস মোড চালু করলে মাইক্রোফোন স্বয়ংক্রিয়ভাবে আপনার কথা শোনে এবং প্রতিটি উত্তরের পর আবার লিসেনিং মোডে ফিরে আসে।\n\n" +
                                        "২. বাংলা ভাষা লক: অন্য কেউ অন্য ভাষায় কথা বললেও ক্যারোলাইন বাংলা পরিবর্তন করবে না।\n\n" +
                                        "৩. অ্যান্ড্রয়েড ফোরগ্রাউন্ড সার্ভিস: অ্যাপ মিনিমাইজ করলেও বা স্ক্রিন লক থাকলেও ব্যাকগ্রাউন্ডে ভয়েস মোড সচল থাকে।",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
