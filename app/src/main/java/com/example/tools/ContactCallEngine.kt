package com.example.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

data class DeviceContactItem(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val normalizedDigits: String
)

enum class ContactActionStatus {
    SINGLE_MATCH_READY,
    MULTIPLE_MATCHES_SELECT_ONE,
    NO_MATCH_FOUND,
    PERMISSION_REQUIRED,
    DIALER_OPENED,
    DIRECT_CALL_STARTED
}

data class ContactSearchOutcome(
    val status: ContactActionStatus,
    val query: String,
    val matches: List<DeviceContactItem>,
    val messageHi: String,
    val messageBn: String,
    val messageEn: String
) {
    fun messageForLanguage(langCode: String): String {
        return when {
            langCode.startsWith("bn", ignoreCase = true) -> messageBn
            langCode.startsWith("en", ignoreCase = true) -> messageEn
            else -> messageHi
        }
    }
}

/**
 * Requirement E: Smart Contact and Phone Call Engine for CaroLine AI A3.2.
 *
 * Supports:
 * - "Call Rahul", "Call Papa", "Call 9876543210"
 * - "Find the contact ending in 45", "Search contacts ending in 23"
 * - "Open phone dialer", "Show matching contacts"
 *
 * Safety & Disambiguation Rules:
 * - Uses Android Contacts permission (`READ_CONTACTS`) properly.
 * - Searches all accessible contacts and matches requested ending digits or names.
 * - Shows matching contact names and numbers in UI.
 * - If multiple contacts match, asks the owner to select one (never randomly calls a matching contact).
 * - Uses `Intent.ACTION_DIAL` as the safe default, or `Intent.ACTION_CALL` only if `CALL_PHONE` is granted and confirmed.
 */
class ContactCallEngine(private val context: Context) {

    companion object {
        private const val TAG = "ContactCallEngine"

        fun normalizeDigits(rawPhone: String): String {
            return rawPhone.filter { it.isDigit() }
        }

        /**
         * Pure helper for matching contacts by ending digits (e.g., "45" or "23").
         */
        fun filterContactsByEndingDigits(
            contacts: List<DeviceContactItem>,
            endingDigits: String
        ): List<DeviceContactItem> {
            val cleanEnding = normalizeDigits(endingDigits)
            if (cleanEnding.isEmpty()) return emptyList()
            return contacts
                .filter { it.normalizedDigits.endsWith(cleanEnding) }
                .distinctBy { "${it.displayName.lowercase()}_${it.normalizedDigits.takeLast(10)}" }
        }

        /**
         * Pure helper for matching contacts by name or number substring.
         */
        fun filterContactsByNameOrNumber(
            contacts: List<DeviceContactItem>,
            query: String
        ): List<DeviceContactItem> {
            val cleanQuery = query.trim().lowercase()
            if (cleanQuery.isEmpty()) return emptyList()
            val queryDigits = normalizeDigits(cleanQuery)

            return contacts.filter { item ->
                val nameMatch = item.displayName.lowercase().contains(cleanQuery) ||
                    cleanQuery.contains(item.displayName.lowercase())
                val digitMatch = queryDigits.length >= 2 && item.normalizedDigits.contains(queryDigits)
                nameMatch || digitMatch
            }.distinctBy { "${it.displayName.lowercase()}_${it.normalizedDigits.takeLast(10)}" }
        }
    }

    fun hasReadContactsPermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun hasCallPhonePermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Loads all accessible phone contacts from ContactsContract.CommonDataKinds.Phone.CONTENT_URI.
     */
    fun getAllAccessibleContacts(): List<DeviceContactItem> {
        if (!hasReadContactsPermission()) {
            return emptyList()
        }

        val list = mutableListOf<DeviceContactItem>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone._ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getString(idIdx).orEmpty() else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    val number = if (numIdx >= 0) cursor.getString(numIdx)?.trim().orEmpty() else ""
                    val digits = normalizeDigits(number)
                    if (name.isNotEmpty() && digits.isNotEmpty()) {
                        list.add(
                            DeviceContactItem(
                                id = id.ifEmpty { "${name}_$digits" },
                                displayName = name,
                                phoneNumber = number,
                                normalizedDigits = digits
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query contacts", e)
        }

        return list.distinctBy { "${it.displayName.lowercase()}_${it.normalizedDigits.takeLast(10)}" }
    }

    /**
     * Searches contacts ending in the specified digits (e.g. "Find the contact ending in 45").
     */
    fun searchContactsEndingIn(endingDigits: String): ContactSearchOutcome {
        val cleanDigits = normalizeDigits(endingDigits)
        if (!hasReadContactsPermission()) {
            return ContactSearchOutcome(
                status = ContactActionStatus.PERMISSION_REQUIRED,
                query = cleanDigits,
                matches = emptyList(),
                messageHi = "बॉस, कॉन्टैक्ट्स ढूंढने के लिए कृपया READ_CONTACTS अनुमति (Contacts Permission) दें।",
                messageBn = "বস, কন্টাক্ট খোঁজার জন্য অনুগ্রহ করে কন্টাক্ট পারমিশন (Contacts Permission) দিন।",
                messageEn = "Boss, please grant Contacts permission so I can search contacts ending in $cleanDigits."
            )
        }

        val allContacts = getAllAccessibleContacts()
        val matches = filterContactsByEndingDigits(allContacts, cleanDigits)

        return when {
            matches.isEmpty() -> ContactSearchOutcome(
                status = ContactActionStatus.NO_MATCH_FOUND,
                query = cleanDigits,
                matches = emptyList(),
                messageHi = "बॉस, आपके कॉन्टैक्ट्स में '$cleanDigits' से खत्म होने वाला कोई नंबर नहीं मिला।",
                messageBn = "বস, আপনার কন্টাক্টে '$cleanDigits' দিয়ে শেষ হওয়া কোনো নম্বর পাওয়া যায়নি।",
                messageEn = "Boss, no contact phone number ending in '$cleanDigits' was found."
            )
            matches.size == 1 -> {
                val c = matches.first()
                ContactSearchOutcome(
                    status = ContactActionStatus.SINGLE_MATCH_READY,
                    query = cleanDigits,
                    matches = matches,
                    messageHi = "बॉस, '$cleanDigits' से खत्म होने वाला 1 कॉन्टैक्ट मिला: ${c.displayName} (${c.phoneNumber})। क्या मैं इसे डायलर में खोलूँ?",
                    messageBn = "বস, '$cleanDigits' দিয়ে শেষ হওয়া १টি কন্টাক্ট পাওয়া গেছে: ${c.displayName} (${c.phoneNumber})। আমি কি ডায়ালার খুলব?",
                    messageEn = "Boss, found 1 contact ending in '$cleanDigits': ${c.displayName} (${c.phoneNumber}). Tap Call or confirm to open the dialer."
                )
            }
            else -> {
                val namesPreview = matches.take(4).joinToString(", ") { "${it.displayName} (${it.phoneNumber})" }
                ContactSearchOutcome(
                    status = ContactActionStatus.MULTIPLE_MATCHES_SELECT_ONE,
                    query = cleanDigits,
                    matches = matches,
                    messageHi = "बॉस, '$cleanDigits' से खत्म होने वाले ${matches.size} कॉन्टैक्ट मिले हैं: $namesPreview। कृपया स्क्रीन से सही कॉन्टैक्ट चुनें।",
                    messageBn = "বস, '$cleanDigits' দিয়ে শেষ হওয়া ${matches.size}টি কন্টাক্ট পাওয়া গেছে: $namesPreview। অনুগ্রহ করে তালিকা থেকে একটি বেছে নিন।",
                    messageEn = "Boss, found ${matches.size} contacts ending in '$cleanDigits': $namesPreview. Please select the contact you want to call."
                )
            }
        }
    }

    /**
     * Searches contacts by name or phone number for calling/lookup.
     * Never randomly calls when multiple contacts match.
     */
    fun searchContactsByNameOrNumber(rawTarget: String): ContactSearchOutcome {
        val cleanTarget = rawTarget.trim()
        val onlyDigits = normalizeDigits(cleanTarget)

        // Direct full phone number (7+ digits with no alphabetic name)
        val hasLetters = cleanTarget.any { it.isLetter() }
        if (!hasLetters && onlyDigits.length >= 7) {
            val directItem = DeviceContactItem(
                id = "direct_$onlyDigits",
                displayName = cleanTarget,
                phoneNumber = cleanTarget,
                normalizedDigits = onlyDigits
            )
            return ContactSearchOutcome(
                status = ContactActionStatus.SINGLE_MATCH_READY,
                query = cleanTarget,
                matches = listOf(directItem),
                messageHi = "बॉस, नंबर $cleanTarget तैयार है। कृपया कॉल या डायलर खोलने की पुष्टि करें।",
                messageBn = "বস, $cleanTarget নম্বরটি প্রস্তুত। কল করার জন্য নিশ্চিত করুন।",
                messageEn = "Boss, number $cleanTarget is ready. Please confirm to open the dialer."
            )
        }

        if (!hasReadContactsPermission()) {
            return ContactSearchOutcome(
                status = ContactActionStatus.PERMISSION_REQUIRED,
                query = cleanTarget,
                matches = emptyList(),
                messageHi = "बॉस, '$cleanTarget' का नंबर ढूंढने के लिए कृपया कॉन्टैक्ट्स अनुमति (Contacts Permission) दें।",
                messageBn = "বস, '$cleanTarget'-এর নম্বর খুঁজতে অনুগ্রহ করে কন্টাক্ট পারমিশন দিন।",
                messageEn = "Boss, please grant Contacts permission to search for '$cleanTarget'."
            )
        }

        val allContacts = getAllAccessibleContacts()
        val matches = filterContactsByNameOrNumber(allContacts, cleanTarget)

        return when {
            matches.isEmpty() -> ContactSearchOutcome(
                status = ContactActionStatus.NO_MATCH_FOUND,
                query = cleanTarget,
                matches = emptyList(),
                messageHi = "बॉस, आपके कॉन्टैक्ट्स में '$cleanTarget' नाम का कोई नंबर नहीं मिला।",
                messageBn = "বস, আপনার কন্টাক্টে '$cleanTarget' নামের কোনো নম্বর পাওয়া যায়নি।",
                messageEn = "Boss, I could not find any contact matching '$cleanTarget' on your device."
            )
            matches.size == 1 -> {
                val c = matches.first()
                ContactSearchOutcome(
                    status = ContactActionStatus.SINGLE_MATCH_READY,
                    query = cleanTarget,
                    matches = matches,
                    messageHi = "बॉस, ${c.displayName} (${c.phoneNumber}) मिल गया है। डायलर खोलने के लिए पुष्टि करें।",
                    messageBn = "বস, ${c.displayName} (${c.phoneNumber}) পাওয়া গেছে। ডায়ালার খুলতে নিশ্চিত করুন।",
                    messageEn = "Boss, found ${c.displayName} (${c.phoneNumber}). Confirm to open the phone dialer."
                )
            }
            else -> {
                val preview = matches.take(4).joinToString(", ") { "${it.displayName} (${it.phoneNumber})" }
                ContactSearchOutcome(
                    status = ContactActionStatus.MULTIPLE_MATCHES_SELECT_ONE,
                    query = cleanTarget,
                    matches = matches,
                    messageHi = "बॉस, '$cleanTarget' नाम से ${matches.size} कॉन्टैक्ट मिले हैं: $preview। कृपया सूची में से किसी एक को चुनें।",
                    messageBn = "বস, '$cleanTarget' নামে ${matches.size}টি কন্টাক্ট পাওয়া গেছে: $preview। অনুগ্রহ করে তালিকা থেকে একজনকে বেছে নিন।",
                    messageEn = "Boss, found ${matches.size} contacts matching '$cleanTarget': $preview. Please select which one to call."
                )
            }
        }
    }

    /**
     * Opens the Android Phone Dialer (`Intent.ACTION_DIAL`) as the safe default,
     * or places a direct call (`Intent.ACTION_CALL`) ONLY when `directCall = true` AND `CALL_PHONE` permission is granted.
     */
    fun placeCallOrOpenDialer(phoneNumber: String, directCall: Boolean = false): Boolean {
        val cleanPhone = phoneNumber.trim()
        val uri = if (cleanPhone.isNotEmpty()) {
            Uri.parse("tel:${Uri.encode(cleanPhone)}")
        } else {
            Uri.parse("tel:")
        }

        if (directCall && cleanPhone.isNotEmpty() && hasCallPhonePermission()) {
            try {
                val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Direct call failed, falling back to ACTION_DIAL", e)
            }
        }

        return try {
            val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open dialer", e)
            false
        }
    }
}
