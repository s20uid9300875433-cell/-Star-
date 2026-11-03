package com.example.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

enum class MessageCategory(val labelEn: String) {
    PERSONAL("Personal Message"),
    PROFESSIONAL("Professional Message"),
    BIRTHDAY("Birthday Wish"),
    APOLOGY("Apology Note"),
    TRANSLATION("Translated Message")
}

data class MessageDraftPreview(
    val id: String = "msg_draft_${System.currentTimeMillis()}",
    val recipientName: String,
    val recipientPhone: String = "",
    val category: MessageCategory,
    val originalInstruction: String,
    val draftedText: String,
    val languageCode: String = "hi-IN",
    val preferredChannel: String = "WhatsApp" // "WhatsApp" or "SMS"
)

/**
 * Requirement G: Smart Message Writer for CaroLine AI A3.2.
 *
 * Understands natural-language instructions in Hindi, Bengali, Hinglish, and English:
 * - "Rahul ko bolo main aaj late aaunga"
 * - "Write a birthday message for Priya"
 * - "Send an apology message to Boss"
 * - "Write a professional leave message"
 *
 * Automatically improves grammar and tone, displays a Message Preview Card in UI,
 * and requires explicit user confirmation before launching WhatsApp or SMS.
 */
class SmartMessageWriter(private val context: Context) {

    companion object {
        private const val TAG = "SmartMessageWriter"

        /**
         * Pure function that parses a natural-language instruction and generates a well-written message draft
         * in the user's selected spoken language ("hi-IN", "bn-IN", or "en-IN").
         */
        fun createSmartDraft(
            instruction: String,
            spokenLanguageCode: String = "hi-IN",
            resolvedRecipientPhone: String = ""
        ): MessageDraftPreview {
            val clean = instruction.trim()
            val lower = clean.lowercase()

            val recipient = extractRecipientName(clean)
            val category = detectCategory(lower)
            val channel = if (lower.contains("sms") || lower.contains("एसएमएस")) "SMS" else "WhatsApp"

            val drafted = buildPolishedMessageBody(
                rawInstruction = clean,
                lower = lower,
                recipient = recipient,
                category = category,
                langCode = spokenLanguageCode
            )

            return MessageDraftPreview(
                recipientName = recipient.ifEmpty { "Recipient" },
                recipientPhone = resolvedRecipientPhone,
                category = category,
                originalInstruction = clean,
                draftedText = drafted,
                languageCode = spokenLanguageCode,
                preferredChannel = channel
            )
        }

        private fun detectCategory(lower: String): MessageCategory {
            return when {
                lower.contains("birthday") || lower.contains("जन्मदिन") || lower.contains("বার্থডে") || lower.contains("জন্মদিন") ->
                    MessageCategory.BIRTHDAY
                lower.contains("sorry") || lower.contains("apolog") || lower.contains("माफी") || lower.contains("माफ़") || lower.contains("ক্ষমা") ->
                    MessageCategory.APOLOGY
                lower.contains("meeting") || lower.contains("office") || lower.contains("leave") || lower.contains("professional") || lower.contains("ऑफिस") || lower.contains("मीटिंग") ->
                    MessageCategory.PROFESSIONAL
                lower.contains("translate") || lower.contains("अनुवाद") || lower.contains("অনুবাদ") ->
                    MessageCategory.TRANSLATION
                else -> MessageCategory.PERSONAL
            }
        }

        fun extractRecipientName(raw: String): String {
            // Patterns:
            // 1. "<Name> ko bolo ..." / "<Name> को बोलो ..." / "<Name> को मैसेज भेजो ..."
            val hinglishMatch = Regex("^([\\p{L}\\p{M}]+)\\s+(ko|को|ke|के)\\s+(bolo|बोलो|batao|बताओ|message|मैसेज|whatsapp)", RegexOption.IGNORE_CASE)
                .find(raw.trim())
            if (hinglishMatch != null) {
                return hinglishMatch.groupValues[1].trim()
            }

            // 2. "message to <Name> that ..." / "tell <Name> that ..." / "whatsapp <Name> ..."
            val englishMatch = Regex("(?:message to|send message to|whatsapp to|tell|for)\\s+([\\p{L}\\p{M}]+)", RegexOption.IGNORE_CASE)
                .find(raw.trim())
            if (englishMatch != null) {
                return englishMatch.groupValues[1].trim()
            }

            return ""
        }

        private fun buildPolishedMessageBody(
            rawInstruction: String,
            lower: String,
            recipient: String,
            category: MessageCategory,
            langCode: String
        ): String {
            val greetNameHi = if (recipient.isNotBlank()) "नमस्ते $recipient," else "नमस्ते,"
            val greetNameBn = if (recipient.isNotBlank()) "হ্যালো $recipient," else "হ্যালো,"
            val greetNameEn = if (recipient.isNotBlank()) "Hi $recipient," else "Hello,"

            // Extract core message intent after "bolo", "that", "कि", etc.
            val coreBody = rawInstruction
                .replace(Regex("^([\\p{L}\\p{M}]+)\\s+(ko|को)\\s+(bolo|बोलो|batao|बताओ|मैसेज करो|मैसेज भेजो|message karo)\\s*(ki|कि)?", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^(write a|send a|prepare a|draft a|create a)\\s+(whatsapp|sms)?\\s*(message|note|wish)?\\s*(to|for)?\\s*([\\p{L}\\p{M}]+)?\\s*(that|saying)?", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^(कैरोलाइन|caroline|hey caroline)\\s*", RegexOption.IGNORE_CASE), "")
                .trim()

            // Common scenario: "main aaj late aaunga" / "late" / "देर"
            val mentionsLate = lower.contains("late") || lower.contains("देर") || lower.contains("लेट") || lower.contains("দেরি")

            return when {
                mentionsLate -> {
                    when {
                        langCode.startsWith("bn", ignoreCase = true) ->
                            "$greetNameBn আজ আমার আসতে একটু দেরি হবে। আমি যত দ্রুত সম্ভব পৌঁছে যাব।"
                        langCode.startsWith("en", ignoreCase = true) ->
                            "$greetNameEn I will be arriving a little late today. I will reach as soon as possible."
                        else ->
                            "$greetNameHi मुझे आज आने में थोड़ी देर हो जाएगी। मैं जल्द से जल्द पहुँचने की कोशिश कर रहा हूँ।"
                    }
                }
                category == MessageCategory.BIRTHDAY -> {
                    when {
                        langCode.startsWith("bn", ignoreCase = true) ->
                            "$greetNameBn আপনাকে জন্মদিনের অনেক অনেক শুভেচ্ছা ও ভালোবাসা! আপনার আগামী দিনগুলো আনন্দ ও সাফল্যে ভরে উঠুক।"
                        langCode.startsWith("en", ignoreCase = true) ->
                            "$greetNameEn Wishing you a very Happy Birthday filled with joy, good health, and wonderful memories!"
                        else ->
                            "$greetNameHi आपको जन्मदिन की हार्दिक शुभकामनाएं! ईश्वर से प्रार्थना है कि आपका आने वाला साल खुशियों और सफलता से भरा रहे।"
                    }
                }
                category == MessageCategory.APOLOGY -> {
                    when {
                        langCode.startsWith("bn", ignoreCase = true) ->
                            "$greetNameBn অসুবিধার জন্য আমি আন্তরিকভাবে দুঃখিত। অনুগ্রহ করে আমাকে ক্ষমা করবেন।"
                        langCode.startsWith("en", ignoreCase = true) ->
                            "$greetNameEn I sincerely apologize for the inconvenience caused. Thank you for your patience and understanding."
                        else ->
                            "$greetNameHi असुविधा के लिए मुझे खेद है। कृपया मेरी क्षमा स्वीकार करें, मैं जल्द ही आपसे संपर्क करूँगा।"
                    }
                }
                category == MessageCategory.PROFESSIONAL -> {
                    when {
                        langCode.startsWith("bn", ignoreCase = true) ->
                            "$greetNameBn জরুরি কাজের কারণে আমি আজ উপস্থিত থাকতে পারছি না। প্রয়োজনে অনুগ্রহ করে আমাকে জানাবেন। ধন্যবাদ।"
                        langCode.startsWith("en", ignoreCase = true) ->
                            "$greetNameEn I wanted to inform you regarding our schedule today: ${coreBody.ifEmpty { "I will follow up with you shortly" }}. Best regards."
                        else ->
                            "$greetNameHi मैं आपको सूचित करना चाहता हूँ कि ${coreBody.ifEmpty { "आज एक आवश्यक कार्य के कारण समय में थोड़ा बदलाव है" }}। धन्यवाद।"
                    }
                }
                else -> {
                    val cleanedBody = coreBody.ifEmpty { rawInstruction }
                    when {
                        langCode.startsWith("bn", ignoreCase = true) ->
                            "$greetNameBn $cleanedBody।"
                        langCode.startsWith("en", ignoreCase = true) ->
                            "$greetNameEn ${cleanedBody.replaceFirstChar { it.uppercase() }}."
                        else ->
                            "$greetNameHi $cleanedBody।"
                    }
                }
            }
        }
    }

    /**
     * Opens WhatsApp with the confirmed message text (and phone number if available).
     * Never called without prior user confirmation of the preview card.
     */
    fun sendConfirmedViaWhatsApp(draft: MessageDraftPreview): Boolean {
        val cleanDigits = ContactCallEngine.normalizeDigits(draft.recipientPhone)
        try {
            if (cleanDigits.length >= 10) {
                val formattedPhone = if (cleanDigits.length == 10) "91$cleanDigits" else cleanDigits
                val waUri = Uri.parse(
                    "https://api.whatsapp.com/send?phone=$formattedPhone&text=${Uri.encode(draft.draftedText)}"
                )
                val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(waIntent)
                return true
            }

            // Otherwise share directly to WhatsApp if installed, or open wa.me text link
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, draft.draftedText)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (sendIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(sendIntent)
                return true
            }

            val fallbackWeb = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/?text=${Uri.encode(draft.draftedText)}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackWeb)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch WhatsApp message", e)
            return false
        }
    }

    /**
     * Opens the Android SMS composer with the confirmed message text.
     */
    fun sendConfirmedViaSms(draft: MessageDraftPreview): Boolean {
        return try {
            val smsUri = Uri.parse("smsto:${Uri.encode(draft.recipientPhone)}")
            val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                putExtra("sms_body", draft.draftedText)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open SMS composer", e)
            false
        }
    }
}
