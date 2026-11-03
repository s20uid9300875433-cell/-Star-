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
    val bengaliName: String,
    val category: String,
    val knownPackages: List<String>,
    val aliases: List<String>,
    val isInstalled: Boolean = false,
    val resolvedPackage: String? = null
)

data class AppLaunchResult(
    val appName: String,
    val packageName: String?,
    val isInstalled: Boolean,
    val isLaunched: Boolean,
    val playStoreOffered: Boolean,
    val messageBn: String
)

/**
 * Dynamic Top 50 + Installed Application Launcher for CaroLine AI 2.5 — Smart JARVIS Edition.
 * Uses Android PackageManager with Android 11–15 compatible package visibility queries.
 * Never assumes an application is installed; offers Play Store search when an app is missing.
 */
class SmartAppLauncher(private val context: Context) {

    companion object {
        private const val TAG = "SmartAppLauncher"

        val TOP_50_CATALOG: List<AppEntry> = listOf(
            AppEntry("WhatsApp", "হোয়াটসঅ্যাপ", "Social", listOf("com.whatsapp", "com.whatsapp.w4b"), listOf("whatsapp", "হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপ", "wa")),
            AppEntry("YouTube", "ইউটিউব", "Media", listOf("com.google.android.youtube"), listOf("youtube", "ইউটিউব", "yt")),
            AppEntry("Instagram", "ইনস্টাগ্রাম", "Social", listOf("com.instagram.android"), listOf("instagram", "ইনস্টাগ্রাম", "insta")),
            AppEntry("Facebook", "ফেসবুক", "Social", listOf("com.facebook.katana", "com.facebook.lite"), listOf("facebook", "ফেসবুক", "fb")),
            AppEntry("Messenger", "মেসেঞ্জার", "Social", listOf("com.facebook.orca", "com.facebook.mlite"), listOf("messenger", "মেসেঞ্জার")),
            AppEntry("Chrome", "গুগল ক্রোম", "Browser", listOf("com.android.chrome"), listOf("chrome", "ক্রোম", "browser", "ব্রাউজার")),
            AppEntry("Gmail", "জিমেইল", "Productivity", listOf("com.google.android.gm"), listOf("gmail", "জিমেইল", "ইমেইল", "মেইল", "email")),
            AppEntry("Google Maps", "গুগল ম্যাপস", "Navigation", listOf("com.google.android.apps.maps"), listOf("google maps", "maps", "ম্যাপস", "গুগল ম্যাপস", "ম্যাপ")),
            AppEntry("Spotify", "স্পটিফাই", "Music", listOf("com.spotify.music"), listOf("spotify", "স্পটিফাই")),
            AppEntry("Telegram", "টেলিগ্রাম", "Social", listOf("org.telegram.messenger", "org.telegram.messenger.web"), listOf("telegram", "টেলিগ্রাম")),
            AppEntry("Snapchat", "স্ন্যাপচ্যাট", "Social", listOf("com.snapchat.android"), listOf("snapchat", "স্ন্যাপচ্যাট", "snap")),
            AppEntry("X (Twitter)", "এক্স / টুইটার", "Social", listOf("com.twitter.android"), listOf("twitter", "x", "টুইটার", "এক্স")),
            AppEntry("LinkedIn", "লিঙ্কডইন", "Business", listOf("com.linkedin.android"), listOf("linkedin", "লিঙ্কডইন")),
            AppEntry("Pinterest", "পিন্টারেস্ট", "Lifestyle", listOf("com.pinterest"), listOf("pinterest", "পিন্টারেস্ট")),
            AppEntry("Reddit", "রেডিট", "Social", listOf("com.reddit.frontpage"), listOf("reddit", "রেডিট")),
            AppEntry("Amazon", "অ্যামাজন", "Shopping", listOf("in.amazon.mShop.android.shopping", "com.amazon.mShop.android.shopping"), listOf("amazon", "অ্যামাজন")),
            AppEntry("Flipkart", "ফ্লিপকার্ট", "Shopping", listOf("com.flipkart.android"), listOf("flipkart", "ফ্লিপকার্ট")),
            AppEntry("PhonePe", "ফোনপে", "Finance", listOf("com.phonepe.app"), listOf("phonepe", "ফোনপে")),
            AppEntry("Google Pay", "গুগল পে", "Finance", listOf("com.google.android.apps.nbu.paisa.user"), listOf("google pay", "gpay", "গুগল পে", "জিপে")),
            AppEntry("Paytm", "পেটিএম", "Finance", listOf("net.one97.paytm"), listOf("paytm", "পেটিএম")),
            AppEntry("Camera", "ক্যামেরা", "System", listOf("com.android.camera", "com.android.camera2", "com.google.android.GoogleCamera", "com.sec.android.app.camera"), listOf("camera", "ক্যামেরা", "ছবি")),
            AppEntry("Gallery", "গ্যালারি / ফটোজ", "System", listOf("com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery"), listOf("gallery", "photos", "গ্যালারি", "ফটোজ")),
            AppEntry("Settings", "সেটিংস", "System", listOf("com.android.settings"), listOf("settings", "সেটিংস", "সেটিং")),
            AppEntry("YouTube Music", "ইউটিউব মিউজিক", "Music", listOf("com.google.android.apps.youtube.music"), listOf("youtube music", "ইউটিউব মিউজিক", "yt music")),
            AppEntry("Netflix", "নেটফ্লিক্স", "Media", listOf("com.netflix.mediaclient"), listOf("netflix", "নেটফ্লিক্স")),
            AppEntry("Prime Video", "প্রাইম ভিডিও", "Media", listOf("com.amazon.avod.thirdpartyclient"), listOf("prime video", "প্রাইম ভিডিও")),
            AppEntry("Disney+ Hotstar", "হটস্টার", "Media", listOf("in.startv.hotstar"), listOf("hotstar", "হটস্টার", "disney")),
            AppEntry("JioCinema", "জিও সিনেমা", "Media", listOf("com.jio.media.ondemand"), listOf("jiocinema", "জিও সিনেমা")),
            AppEntry("VLC Player", "ভিএলসি প্লেয়ার", "Media", listOf("org.videolan.vlc"), listOf("vlc", "ভিএলসি")),
            AppEntry("MX Player", "এমএক্স প্লেয়ার", "Media", listOf("com.mxtech.videoplayer.ad"), listOf("mx player", "এমএক্স প্লেয়ার")),
            AppEntry("Google Play Store", "প্লে স্টোর", "System", listOf("com.android.vending"), listOf("play store", "প্লে স্টোর", "google play")),
            AppEntry("Calculator", "ক্যালকুলেটর", "Tools", listOf("com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator"), listOf("calculator", "ক্যালকুলেটর", "হিসাব")),
            AppEntry("Clock & Alarm", "ঘড়ি ও অ্যালার্ম", "Tools", listOf("com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage"), listOf("clock", "alarm", "ঘড়ি", "অ্যালার্ম")),
            AppEntry("Calendar", "ক্যালেন্ডার", "Productivity", listOf("com.google.android.calendar", "com.android.calendar"), listOf("calendar", "ক্যালেন্ডার")),
            AppEntry("Contacts", "কন্টাক্টস", "System", listOf("com.google.android.contacts", "com.android.contacts"), listOf("contacts", "কন্টাক্টস", "ফোনবুক")),
            AppEntry("Phone Dialer", "ফোন ডায়ালার", "System", listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer"), listOf("dialer", "phone", "ডায়ালার", "ফোন")),
            AppEntry("Messages (SMS)", "মেসেজ / এসএমএস", "System", listOf("com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging"), listOf("messages", "sms", "মেসেজ", "এসএমএস")),
            AppEntry("Files", "ফাইল ম্যানেজার", "Tools", listOf("com.google.android.apps.nbu.files", "com.android.documentsui"), listOf("files", "file manager", "ফাইল", "ফাইল ম্যানেজার")),
            AppEntry("Google Drive", "গুগল ড্রাইভ", "Productivity", listOf("com.google.android.apps.docs"), listOf("google drive", "drive", "গুগল ড্রাইভ", "ড্রাইভ")),
            AppEntry("Google Keep", "গুগল কিপ নোটস", "Productivity", listOf("com.google.android.keep"), listOf("keep", "notes", "নোটস", "গুগল কিপ")),
            AppEntry("Google Meet", "গুগল মিট", "Communication", listOf("com.google.android.apps.tachyon"), listOf("google meet", "meet", "গুগল মিট")),
            AppEntry("Zoom", "জুম", "Communication", listOf("us.zoom.videomeetings"), listOf("zoom", "জুম")),
            AppEntry("Discord", "ডিসকর্ড", "Social", listOf("com.discord"), listOf("discord", "ডিসকর্ড")),
            AppEntry("GitHub", "গিটহাব", "Developer", listOf("com.github.android"), listOf("github", "গিটহাব")),
            AppEntry("ChatGPT", "চ্যাটজিপিটি অ্যাপ", "AI", listOf("com.openai.chatgpt"), listOf("chatgpt", "চ্যাটজিপিটি")),
            AppEntry("Truecaller", "ট্রুকলার", "Tools", listOf("com.truecaller"), listOf("truecaller", "ট্রুকলার")),
            AppEntry("Uber", "উবার", "Travel", listOf("com.ubercab"), listOf("uber", "উবার")),
            AppEntry("Ola", "ওলা", "Travel", listOf("com.olacabs.customer"), listOf("ola", "ওলা")),
            AppEntry("Zomato", "জোমাটো", "Food", listOf("com.application.zomato"), listOf("zomato", "জোমাটো")),
            AppEntry("Swiggy", "সুইগি", "Food", listOf("in.swiggy.android"), listOf("swiggy", "সুইগি"))
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

        // Also append additional installed apps on the device that aren't already in Top 50
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
     * Attempts to launch an application by its English/Bengali name or package name.
     * Never claims success unless an Activity was genuinely started.
     */
    fun launchApp(query: String, openPlayStoreIfMissing: Boolean = true): AppLaunchResult {
        val cleanQuery = query.trim()
        val lower = cleanQuery.lowercase()
        val pm = context.packageManager

        // 1. Check catalog match first
        val catalogEntry = TOP_50_CATALOG.firstOrNull { entry ->
            entry.displayName.equals(cleanQuery, ignoreCase = true) ||
                entry.bengaliName == cleanQuery ||
                entry.aliases.any { alias -> lower.contains(alias) || alias.contains(lower) } ||
                entry.knownPackages.any { pkg -> pkg.equals(cleanQuery, ignoreCase = true) }
        }

        if (catalogEntry != null) {
            // Try known packages
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
                            messageBn = "বস, আপনার ফোনে ${catalogEntry.bengaliName} (${catalogEntry.displayName}) খুলে দিয়েছি।"
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error launching $pkg", e)
                        AppLaunchResult(
                            appName = catalogEntry.displayName,
                            packageName = pkg,
                            isInstalled = true,
                            isLaunched = false,
                            playStoreOffered = false,
                            messageBn = "বস, ${catalogEntry.displayName} খুলতে সমস্যা হয়েছে: ${e.localizedMessage ?: "ত্রুটি"}।"
                        )
                    }
                }
            }

            // Try system intent fallback for system apps (Camera, Settings, Dialer, etc.)
            if (launchSystemFallback(catalogEntry.displayName)) {
                return AppLaunchResult(
                    appName = catalogEntry.displayName,
                    packageName = catalogEntry.knownPackages.firstOrNull(),
                    isInstalled = true,
                    isLaunched = true,
                    playStoreOffered = false,
                    messageBn = "বস, আপনার ফোনে ${catalogEntry.bengaliName} খুলে দিয়েছি।"
                )
            }

            // App is in Top 50 catalog but not installed on this device
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
                    "বস, আপনার ফোনে ${catalogEntry.displayName} ইনস্টল করা নেই। আমি প্লে স্টোরে এটি খুঁজে দিচ্ছি।"
                } else {
                    "বস, আপনার ফোনে ${catalogEntry.displayName} অ্যাপটি ইনস্টল করা নেই।"
                }
            )
        }

        // 2. Dynamic search across all installed launcher apps on device
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
                        messageBn = "বস, আপনার ফোনে ${matchedInstalled.value} খুলে দিয়েছি।"
                    )
                } catch (e: Exception) {
                    AppLaunchResult(
                        appName = matchedInstalled.value,
                        packageName = matchedInstalled.key,
                        isInstalled = true,
                        isLaunched = false,
                        playStoreOffered = false,
                        messageBn = "বস, ${matchedInstalled.value} অ্যাপটি চালু করতে সমস্যা হয়েছে।"
                    )
                }
            }
        }

        // 3. Not installed — offer Play Store search
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
                "বস, '$cleanQuery' অ্যাপটি আপনার ফোনে ইনস্টল করা নেই। আমি গুগল প্লে স্টোরে এটি খুঁজে দিচ্ছি।"
            } else {
                "বস, '$cleanQuery' অ্যাপটি আপনার ফোনে ইনস্টল করা নেই।"
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
