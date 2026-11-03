package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisDeepNavy
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisHudSurface
import com.example.ui.theme.MutedVioletGray
import com.example.ui.theme.PureWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpEducationSheet(
    isEducationMode: Boolean,
    onSelectQuickPrompt: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Text(
                text = if (isEducationMode) {
                    "CAROLINE AI 2.5 — স্মার্ট জারভিস শিক্ষা ও গাইড"
                } else {
                    "CAROLINE AI 2.5 — হেল্প ও ভয়েস কমান্ড গাইড"
                },
                style = MaterialTheme.typography.titleLarge,
                color = PureWhite,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "স্মার্ট জারভিস এডিশন • বাংলা, হিন্দি ও ইংরেজি কমান্ড সাপোর্ট",
                style = MaterialTheme.typography.labelMedium,
                color = JarvisCyan
            )

            Spacer(modifier = Modifier.height(16.dp))

            GuideCard(
                icon = Icons.Default.Hub,
                title = "১. থ্রি-এআই ব্রেইন সিস্টেম (Three AI Brain)",
                description = "Google Gemini API, OpenAI ChatGPT API এবং OpenRouter / Custom Cloud AI একসাথে যুক্ত। যেকোনো একটি প্রোভাইডার ব্যর্থ হলে স্বয়ংক্রিয়ভাবে পরবর্তী প্রোভাইডার ও লোকাল জারভিস ইঞ্জিন বাংলায় উত্তর দেবে।"
            )

            Spacer(modifier = Modifier.height(10.dp))

            GuideCard(
                icon = Icons.Default.Mic,
                title = "২. হ্যান্ডস-ফ্রি বাংলা ভয়েস ও ব্যাকগ্রাউন্ড সার্ভিস",
                description = "ভয়েস মোড চালু করলে ফোরগ্রাউন্ড সার্ভিস সচল থাকে। আপনি 'হ্যালো ক্যারোলাইন', 'Hey CaroLine' বা 'হ্যালো জারভিস' বলে যেকোনো প্রশ্ন বা মোবাইল কমান্ড দিতে পারেন।"
            )

            Spacer(modifier = Modifier.height(10.dp))

            GuideCard(
                icon = Icons.Default.Apps,
                title = "৩. টপ ৫০ অ্যাপ লঞ্চার (Top 50 App Launcher)",
                description = "হোয়াটসঅ্যাপ, ইউটিউব, ফেসবুক, ইনস্টাগ্রাম, ক্রোম, জিমেইল, ম্যাপস, স্পটিফাই, টেলিগ্রাম সহ ৫০টি জনপ্রিয় অ্যাপ ভয়েস কমান্ড দিয়ে সরাসরি খুলুন। অ্যাপ ইনস্টল না থাকলে প্লে স্টোরে খুঁজে দেবে।"
            )

            Spacer(modifier = Modifier.height(10.dp))

            GuideCard(
                icon = Icons.Default.LibraryMusic,
                title = "৪. মিউজিক ও ভিডিও প্লেব্যাক কমান্ড",
                description = "'গান চালাও', 'মিউজিক পজ করো', 'মিউজিক চালু করো', 'পরের গান চালাও' বা 'ইউটিউবে বাংলা গান সার্চ করো' বললে ক্যারোলাইন সাথে সাথে মিডিয়া কন্ট্রোল করবে।"
            )

            Spacer(modifier = Modifier.height(10.dp))

            GuideCard(
                icon = Icons.Default.PhoneAndroid,
                title = "৫. স্মার্ট মোবাইল সিস্টেম কন্ট্রোল",
                description = "ফ্ল্যাশলাইট অন/অফ, ভলিউম বাড়ানো-কমানো, ব্যাটারি ও র‍্যাম স্ট্যাটাস, ইন্টারনেট চেক, ক্যামেরা, ওয়াইফাই/ব্লুটুথ সেটিংস এবং সেনসিটিভ অ্যাকশনের জন্য কনফার্মেশন প্রম্পট।"
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "ট্যাপ করে সরাসরি বাংলা জারভিস কমান্ড পরীক্ষা করুন:",
                style = MaterialTheme.typography.titleMedium,
                color = PureWhite,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))

            val samplePrompts = listOf(
                "হ্যালো ক্যারোলাইন, কেমন আছো?",
                "হোয়াটসঅ্যাপ খোলো",
                "ইউটিউব খোলো",
                "গান চালাও",
                "ফ্ল্যাশলাইট চালু করো",
                "ভলিউম বাড়াও",
                "মোবাইলের ব্যাটারি কত?",
                "ইন্টারনেট কানেকশন চেক করো",
                "ক্যামেরা খোলো",
                "এখন কয়টা বাজে?"
            )

            samplePrompts.forEachIndexed { idx, prompt ->
                Button(
                    onClick = {
                        onSelectQuickPrompt(prompt)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .testTag("sample_prompt_button_$idx"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JarvisElectricBlue.copy(alpha = 0.28f),
                        contentColor = PureWhite
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "🗣️ \"$prompt\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Surface(
        color = JarvisHudSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = JarvisCyan,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = PureWhite,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedVioletGray
                )
            }
        }
    }
}
