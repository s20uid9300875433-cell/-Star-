package com.example.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log

data class AppEntry(
    val displayName: String,
    val bengaliName: String, // Holds Hindi display name in 2.9 (kept property name for compatibility)
    val category: String,
    val knownPackages: List<String>,
    val aliases: List<String>,
    val isInstalled: Boolean = false,
    val resolvedPackage: String? = null
) {
    val hindiName: String get() = bengaliName
}

data class AppLaunchResult(
    val appName: String,
    val packageName: String?,
    val isInstalled: Boolean,
    val isLaunched: Boolean,
    val playStoreOffered: Boolean,
    val messageBn: String // Holds natural Hindi response in 2.9
) {
    val messageHi: String get() = messageBn
}

/**
 * Dynamic Top 50 + Installed Application Launcher for CaroLine AI 2.9 – Ultimate Smart JARVIS.
 * Uses Android PackageManager with Android 8–15 compatible package visibility queries.
 * Supports Hindi, Hinglish, and English voice commands ("व्हाट्सएप खोलो", "Open Instagram", etc.).
 */
class SmartAppLauncher(private val context: Context) {

    companion object {
        private const val TAG = "SmartAppLauncher"

        val TOP_50_CATALOG: List<AppEntry> = listOf(
            AppEntry("WhatsApp", "व्हाट्सएप", "Social", listOf("com.whatsapp", "com.whatsapp.w4b"), listOf("whatsapp", "व्हाट्सएप", "वाट्सएप", "व्हाट्सऐप", "wa")),
            AppEntry("YouTube", "यूट्यूब", "Media", listOf("com.google.android.youtube"), listOf("youtube", "यूट्यूब", "यू ट्यूब", "yt")),
            AppEntry("Instagram", "इंस्टाग्राम", "Social", listOf("com.instagram.android"), listOf("instagram", "इंस्टाग्राम", "इंस्टा", "insta")),
            AppEntry("Facebook", "फेसबुक", "Social", listOf("com.facebook.katana", "com.facebook.lite"), listOf("facebook", "फेसबुक", "fb")),
            AppEntry("Messenger", "मैसेंजर", "Social", listOf("com.facebook.orca", "com.facebook.mlite"), listOf("messenger", "मैसेंजर")),
            AppEntry("Chrome", "गूगल क्रोम", "Browser", listOf("com.android.chrome"), listOf("chrome", "क्रोम", "गूगल क्रोम", "browser", "ब्राउज़र")),
            AppEntry("Gmail", "जीमेल", "Productivity", listOf("com.google.android.gm"), listOf("gmail", "जीमेल", "ईमेल", "मेल", "email")),
            AppEntry("Google Maps", "गूगल मैप्स", "Navigation", listOf("com.google.android.apps.maps"), listOf("google maps", "maps", "मैप्स", "गूगल मैप्स", "मैप", "नक्शा")),
            AppEntry("Spotify", "स्पॉटिफाई", "Music", listOf("com.spotify.music"), listOf("spotify", "स्पॉटिफाई", "स्पॉटीफाई")),
            AppEntry("Telegram", "टेलीग्राम", "Social", listOf("org.telegram.messenger", "org.telegram.messenger.web"), listOf("telegram", "टेलीग्राम")),
            AppEntry("Snapchat", "स्नैपचैट", "Social", listOf("com.snapchat.android"), listOf("snapchat", "स्नैपचैट", "snap")),
            AppEntry("X (Twitter)", "एक्स / ट्विटर", "Social", listOf("com.twitter.android"), listOf("twitter", "x", "ट्विटर", "एक्स")),
            AppEntry("LinkedIn", "लिंक्डइन", "Business", listOf("com.linkedin.android"), listOf("linkedin", "लिंक्डइन")),
            AppEntry("Pinterest", "पिंटरेस्ट", "Lifestyle", listOf("com.pinterest"), listOf("pinterest", "पिंटरेस्ट")),
            AppEntry("Reddit", "रेडिट", "Social", listOf("com.reddit.frontpage"), listOf("reddit", "रेडिट")),
            AppEntry("Amazon", "अमेज़न", "Shopping", listOf("in.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping"), listOf("amazon", "अमेज़न", "अमेजन")),
            AppEntry("Flipkart", "फ्लिपकार्ट", "Shopping", listOf("com.flipkart.android"), listOf("flipkart", "फ्लिपकार्ट")),
            AppEntry("PhonePe", "फोनपे", "Finance", listOf("com.phonepe.app"), listOf("phonepe", "फोनपे", "फ़ोनपे")),
            AppEntry("Google Pay", "गूगल पे", "Finance", listOf("com.google.android.apps.nbu.paisa.user"), listOf("google pay", "gpay", "गूगल पे", "जीपे")),
            AppEntry("Paytm", "पेटीएम", "Finance", listOf("net.one97.paytm"), listOf("paytm", "पेटीएम")),
            AppEntry("Camera", "कैमरा", "System", listOf("com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera", "com.sec.android.app.camera"), listOf("camera", "कैमरा", "फोटो")),
            AppEntry("Gallery", "गैलरी / फोटोज़", "System", listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery"), listOf("gallery", "photos", "गैलरी", "फोटोज़")),
            AppEntry("Settings", "सेटिंग्स", "System", listOf("com.android.settings"), listOf("settings", "सेटिंग्स", "सेटिंग")),
            AppEntry("YouTube Music", "यूट्यूब म्यूजिक", "Music", listOf("com.google.android.apps.youtube.music"), listOf("youtube music", "यूट्यूब म्यूजिक", "yt music")),
            AppEntry("Netflix", "नेटफ्लिक्स", "Media", listOf("com.netflix.mediaclient"), listOf("netflix", "नेटफ्लिक्स")),
            AppEntry("Prime Video", "प्राइम वीडियो", "Media", listOf("com.amazon.avod.thirdpartyclient"), listOf("prime video", "प्राइम वीडियो")),
            AppEntry("Disney+ Hotstar", "हॉटस्टार", "Media", listOf("in.startv.hotstar"), listOf("hotstar", "हॉटस्टार", "disney")),
            AppEntry("JioCinema", "जियो सिनेमा", "Media", listOf("com.jio.media.ondemand"), listOf("jiocinema", "जियो सिनेमा")),
            AppEntry("VLC Player", "वीएलसी प्लेयर", "Media", listOf("org.videolan.vlc"), listOf("vlc", "वीएलसी")),
            AppEntry("MX Player", "एमएक्स प्लेयर", "Media", listOf("com.mxtech.videoplayer.ad"), listOf("mx player", "एमएक्स प्लेयर")),
            AppEntry("Google Play Store", "प्ले स्टोर", "System", listOf("com.android.vending"), listOf("play store", "प्ले स्टोर", "google play")),
            AppEntry("Calculator", "कैलकुलेटर", "Tools", listOf("com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator"), listOf("calculator", "कैलकुलेटर", "हिसाब")),
            AppEntry("Clock & Alarm", "घड़ी और अलार्म", "Tools", listOf("com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage"), listOf("clock", "alarm", "घड़ी", "अलार्म")),
            AppEntry("Calendar", "कैलेंडर", "Productivity", listOf("com.google.android.calendar", "com.android.calendar"), listOf("calendar", "कैलेंडर")),
            AppEntry("Contacts", "कॉन्टैक्ट्स", "System", listOf("com.google.android.contacts", "com.android.contacts"), listOf("contacts", "कॉन्टैक्ट्स", "फोनबुक")),
            AppEntry("Phone Dialer", "फोन डायलर", "System", listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer"), listOf("dialer", "phone", "डायलर", "फोन")),
            AppEntry("Messages (SMS)", "मैसेज / एसएमएस", "System", listOf("com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging"), listOf("messages", "sms", "मैसेज", "एसएमएस")),
            AppEntry("Files", "फाइल मैनेजर", "Tools", listOf("com.google.android.apps.nbu.files", "com.android.documentsui"), listOf("files", "file manager", "फाइल", "फाइल मैनेजर")),
            AppEntry("Google Drive", "गूगल ड्राइव", "Productivity", listOf("com.google.android.apps.docs"), listOf("google drive", "drive", "गूगल ड्राइव", "ड्राइव")),
            AppEntry("Google Keep", "गूगल कीप नोट्स", "Productivity", listOf("com.google.android.keep"), listOf("keep", "notes", "नोट्स", "गूगल कीप")),
            AppEntry("Google Meet", "गूगल मीट", "Communication", listOf("com.google.android.apps.tachyon"), listOf("google meet", "meet", "गूगल मीट")),
            AppEntry("Zoom", "ज़ूम", "Communication", listOf("us.zoom.videomeetings"), listOf("zoom", "ज़ूम", "जूम")),
            AppEntry("Discord", "डिस्कॉर्ड", "Social", listOf("com.discord"), listOf("discord", "डिस्कॉर्ड")),
            AppEntry("GitHub", "गिटहब", "Developer", listOf("com.github.android"), listOf("github", "गिटहब")),
            AppEntry("ChatGPT", "चैटजीपीटी ऐप", "AI", listOf("com.openai.chatgpt"), listOf("chatgpt", "चैटजीपीटी")),
            AppEntry("Truecaller", "ट्रूकॉलर", "Tools", listOf("com.truecaller"), listOf("truecaller", "ट्रूकॉलर")),
            AppEntry("Uber", "उबर", "Travel", listOf("com.ubercab"), listOf("uber", "उबर")),
            AppEntry("Ola", "ओला", "Travel", listOf("com.olacabs.customer"), listOf("ola", "ओला")),
            AppEntry("Zomato", "ज़ोमैटो", "Food", listOf("com.application.zomato"), listOf("zomato", "ज़ोमैटो", "जोमैटो")),
            AppEntry("Swiggy", "स्विगी", "Food", listOf("in.swiggy.android"), listOf("swiggy", "स्विगी"))
        )
    }

    /**
     * Returns the full Top 50 catalog enriched with real installation status from PackageManager,
     * plus any additional installed user/system launcher apps discovered on the device.
     */
    fun getCatalogWithInstalledStatus(): List<AppEntry> {
        val pm = context.packageManager
        val installedPackages = getInstalledLauncherPackages(pm)

        val enrichedTop50 = TOP_50_CATALOG.map { entry ->
            val matchedPkg = entry.knownPackages.firstOrNull { pkg ->
                installedPackages.containsKey(pkg) || pm.getLaunchIntentForPackage(pkg) != null
            } ?: resolveSystemFallbackPackage(entry.displayName, pm)

            entry.copy(
                isInstalled = matchedPkg != null,
                resolvedPackage = matchedPkg
            )
        }

        val knownPkgSet = enrichedTop50.mapNotNull { it.resolvedPackage }.toSet() +
            TOP_50_CATALOG.flatMap { it.knownPackages }.toSet()

        val extraInstalled = installedPackages.entries
            .filter { (pkg, _) -> pkg !in knownPkgSet && pkg != context.packageName }
            .take(25)
            .map { (pkg, label) ->
                AppEntry(
                    displayName = label,
                    bengaliName = label,
                    category = "Installed",
                    knownPackages = listOf(pkg),
                    aliases = listOf(label.lowercase()),
                    isInstalled = true,
                    resolvedPackage = pkg
                )
            }

        return enrichedTop50.sortedByDescending { it.isInstalled } + extraInstalled
    }

    private fun getInstalledLauncherPackages(pm: PackageManager): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, 0)
            }
            for (info in resolveInfos) {
                val pkg = info.activityInfo?.packageName ?: continue
                val label = try {
                    info.loadLabel(pm).toString()
                } catch (_: Exception) {
                    pkg
                }
                result[pkg] = label
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query launcher activities", e)
        }
        return result
    }

    private fun resolveSystemFallbackPackage(displayName: String, pm: PackageManager): String? {
        val intent = when (displayName) {
            "Camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            "Settings" -> Intent(Settings.ACTION_SETTINGS)
            "Clock & Alarm" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            "Calendar" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
            "Contacts" -> Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
            "Phone Dialer" -> Intent(Intent.ACTION_DIAL)
            "Messages (SMS)" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
            "Gallery" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)
            "Calculator" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
            "Chrome" -> Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
            "Google Maps" -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=restaurants"))
            else -> null
        } ?: return null

        return try {
            intent.resolveActivity(pm)?.packageName
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Attempts to launch an application by its English/Hindi/Hinglish name or package name.
     * Never claims success unless an Activity was genuinely started.
     */
    fun launchApp(query: String, openPlayStoreIfMissing: Boolean = true): AppLaunchResult {
        val cleanQuery = query.trim()
        val lower = cleanQuery.lowercase()
        val pm = context.packageManager

        val catalogEntry = TOP_50_CATALOG.firstOrNull { entry ->
            entry.displayName.equals(cleanQuery, ignoreCase = true) ||
                entry.bengaliName == cleanQuery ||
                entry.aliases.any { alias -> lower.contains(alias) || alias.contains(lower) } ||
                entry.knownPackages.any { pkg -> pkg.equals(cleanQuery, ignoreCase = true) }
        }

        if (catalogEntry != null) {
            for (pkg in catalogEntry.knownPackages) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    return try {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        AppLaunchResult(
                            appName = catalogEntry.displayName,
                            packageName = pkg,
                            isInstalled = true,
                            isLaunched = true,
                            playStoreOffered = false,
                            messageBn = "बॉस, मैंने आपके फोन में ${catalogEntry.hindiName} (${catalogEntry.displayName}) खोल दिया है।"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error launching $pkg", e)
                        AppLaunchResult(
                            appName = catalogEntry.displayName,
                            packageName = pkg,
                            isInstalled = true,
                            isLaunched = false,
                            playStoreOffered = false,
                            messageBn = "बॉस, ${catalogEntry.displayName} खोलने में समस्या आई: ${e.localizedMessage ?: "त्रुटि"}।"
                        )
                    }
                }
            }

            if (launchSystemFallback(catalogEntry.displayName)) {
                return AppLaunchResult(
                    appName = catalogEntry.displayName,
                    packageName = catalogEntry.knownPackages.firstOrNull(),
                    isInstalled = true,
                    isLaunched = true,
                    playStoreOffered = false,
                    messageBn = "बॉस, आपके फोन में ${catalogEntry.hindiName} खोल दिया गया है।"
                )
            }

            val playStoreOpened = if (openPlayStoreIfMissing) {
                openPlayStoreSearch(catalogEntry.knownPackages.firstOrNull() ?: catalogEntry.displayName)
            } else false

            return AppLaunchResult(
                appName = catalogEntry.displayName,
                packageName = catalogEntry.knownPackages.firstOrNull(),
                isInstalled = false,
                isLaunched = false,
                playStoreOffered = playStoreOpened,
                messageBn = if (playStoreOpened) {
                    "बॉस, आपके फोन में ${catalogEntry.displayName} इंस्टॉल नहीं है। मैंने इसे गूगल प्ले स्टोर पर खोल दिया है।"
                } else {
                    "बॉस, आपके फोन में ${catalogEntry.displayName} ऐप इंस्टॉल नहीं है।"
                }
            )
        }

        val installedApps = getInstalledLauncherPackages(pm)
        val matchedInstalled = installedApps.entries.firstOrNull { (pkg, label) ->
            label.lowercase().contains(lower) || lower.contains(label.lowercase()) || pkg.equals(cleanQuery, ignoreCase = true)
        }

        if (matchedInstalled != null) {
            val launchIntent = pm.getLaunchIntentForPackage(matchedInstalled.key)
            if (launchIntent != null) {
                return try {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    AppLaunchResult(
                        appName = matchedInstalled.value,
                        packageName = matchedInstalled.key,
                        isInstalled = true,
                        isLaunched = true,
                        playStoreOffered = false,
                        messageBn = "बॉस, मैंने आपके फोन में ${matchedInstalled.value} खोल दिया है।"
                    )
                } catch (e: Exception) {
                    AppLaunchResult(
                        appName = matchedInstalled.value,
                        packageName = matchedInstalled.key,
                        isInstalled = true,
                        isLaunched = false,
                        playStoreOffered = false,
                        messageBn = "बॉस, ${matchedInstalled.value} ऐप शुरू करने में समस्या हुई।"
                    )
                }
            }
        }

        val playStoreOpened = if (openPlayStoreIfMissing) {
            openPlayStoreSearch(cleanQuery)
        } else false

        return AppLaunchResult(
            appName = cleanQuery,
            packageName = null,
            isInstalled = false,
            isLaunched = false,
            playStoreOffered = playStoreOpened,
            messageBn = if (playStoreOpened) {
                "बॉस, '$cleanQuery' ऐप आपके फोन में इंस्टॉल नहीं है। मैंने प्ले स्टोर पर इसकी खोज खोल दी है।"
            } else {
                "बॉस, '$cleanQuery' ऐप आपके फोन में इंस्टॉल नहीं है।"
            }
        )
    }

    private fun launchSystemFallback(displayName: String): Boolean {
        val intent = when (displayName) {
            "Camera" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            "Settings" -> Intent(Settings.ACTION_SETTINGS)
            "Clock & Alarm" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            "Calendar" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
            "Contacts" -> Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
            "Phone Dialer" -> Intent(Intent.ACTION_DIAL)
            "Messages (SMS)" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
            "Gallery" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)
            "Calculator" -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
            "Google Maps" -> Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="))
            else -> null
        } ?: return false

        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openPlayStoreSearch(queryOrPkg: String): Boolean {
        return try {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://search?q=${Uri.encode(queryOrPkg)}&c=apps")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
            true
        } catch (_: Exception) {
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/search?q=${Uri.encode(queryOrPkg)}&c=apps")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open Play Store fallback", e)
                false
            }
        }
    }
}
