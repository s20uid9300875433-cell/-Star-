package com.example

import com.example.audio.BengaliSpeechRecognizer
import com.example.audio.ListeningPowerMode
import com.example.live.AiProviderType
import com.example.live.CaroLineBrainEngine
import com.example.live.LiveState
import com.example.live.ProviderConfig
import com.example.tools.ContactCallEngine
import com.example.tools.DeviceContactItem
import com.example.tools.PerformanceProfile
import com.example.tools.SmartAppLauncher
import com.example.tools.SmartMessageWriter
import com.example.tools.ToolExecutor
import com.example.tools.WeatherNetworkEngine
import com.example.ui.components.AvatarEmotion
import com.example.ui.components.AvatarStyle
import com.example.viewmodel.CaroLineUiState
import com.example.viewmodel.DashboardTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun `test CaroLine A3_2 default Hindi greeting and Smart Routines`() {
        assertEquals(
            "नमस्ते बॉस! मैं कैरोलाइन हूँ। बताइए, मैं आपकी क्या मदद कर सकती हूँ?",
            CaroLineBrainEngine.DEFAULT_GREETING_HI
        )
        assertTrue(CaroLineBrainEngine.DEFAULT_SMART_ROUTINES.size >= 4)
        assertTrue(CaroLineBrainEngine.DEFAULT_SMART_ROUTINES.all { it.nameHi.isNotBlank() && it.commands.isNotEmpty() })
    }

    @Test
    fun `test Hey CaroLine wake word detection and command extraction`() {
        assertTrue(BengaliSpeechRecognizer.matchesWakeWord("Hey CaroLine", "Hey CaroLine"))
        assertTrue(BengaliSpeechRecognizer.matchesWakeWord("hey caroline what is the weather", "Hey CaroLine"))
        assertTrue(BengaliSpeechRecognizer.matchesWakeWord("हे कैरोलाइन राहुल को कॉल करो", "Hey CaroLine"))
        assertTrue(BengaliSpeechRecognizer.matchesWakeWord("হ্যালো ক্যারোলাইন समय बोलो", "Hey CaroLine"))
        assertFalse(BengaliSpeechRecognizer.matchesWakeWord("Open calculator please", "Hey CaroLine"))

        assertEquals(
            "",
            BengaliSpeechRecognizer.extractCommandAfterWakeWord("Hey CaroLine!", "Hey CaroLine")
        )
        assertEquals(
            "call Rahul",
            BengaliSpeechRecognizer.extractCommandAfterWakeWord("Hey CaroLine, call Rahul", "Hey CaroLine")
        )
        assertEquals(
            "राहुल को कॉल करो",
            BengaliSpeechRecognizer.extractCommandAfterWakeWord("हे कैरोलाइन राहुल को कॉल करो", "Hey CaroLine")
        )
    }

    @Test
    fun `test Battery-Saving Listening Mode duty-cycle intervals and Screen-Off supported conditions`() {
        val screenOnBatterySaverDelay = BengaliSpeechRecognizer.computeBatterySavingDelayMs(
            consecutiveSilentCycles = 1,
            isScreenInteractive = true,
            isCharging = false,
            batteryLevel = 80,
            mode = ListeningPowerMode.BATTERY_SAVING
        )
        val screenOnContinuousDelay = BengaliSpeechRecognizer.computeBatterySavingDelayMs(
            consecutiveSilentCycles = 1,
            isScreenInteractive = true,
            isCharging = false,
            batteryLevel = 80,
            mode = ListeningPowerMode.CONTINUOUS_ACTIVE
        )
        val screenOffBatterySaverDelay = BengaliSpeechRecognizer.computeBatterySavingDelayMs(
            consecutiveSilentCycles = 2,
            isScreenInteractive = false,
            isCharging = false,
            batteryLevel = 80,
            mode = ListeningPowerMode.BATTERY_SAVING
        )

        // Battery-saving mode rests significantly longer than continuous mode to save battery
        assertTrue(screenOnBatterySaverDelay > screenOnContinuousDelay)
        // Screen-off battery-saving mode rests even longer between wake-word windows
        assertTrue(screenOffBatterySaverDelay > screenOnBatterySaverDelay)

        // Screen-Off supported conditions verification
        assertTrue(
            BengaliSpeechRecognizer.isScreenOffListeningSupported(
                isForegroundServiceRunning = true,
                isScreenOffSupportEnabled = true,
                hasMicPermission = true,
                batteryLevel = 55,
                isCharging = false
            )
        )
        // Low battery (<15%) unplugged disables screen-off mic listening to protect device battery
        assertFalse(
            BengaliSpeechRecognizer.isScreenOffListeningSupported(
                isForegroundServiceRunning = true,
                isScreenOffSupportEnabled = true,
                hasMicPermission = true,
                batteryLevel = 10,
                isCharging = false
            )
        )
        // Low battery (10%) while charging is supported
        assertTrue(
            BengaliSpeechRecognizer.isScreenOffListeningSupported(
                isForegroundServiceRunning = true,
                isScreenOffSupportEnabled = true,
                hasMicPermission = true,
                batteryLevel = 10,
                isCharging = true
            )
        )
    }

    @Test
    fun `test PerformanceProfile automatic RAM selection for 2GB 4GB and 6GB devices`() {
        assertEquals(PerformanceProfile.LITE_MODE, PerformanceProfile.fromTotalRamMb(2048))
        assertEquals(PerformanceProfile.STANDARD_MODE, PerformanceProfile.fromTotalRamMb(4096))
        assertEquals(PerformanceProfile.ADVANCED_MODE, PerformanceProfile.fromTotalRamMb(6144))
        assertEquals(PerformanceProfile.ADVANCED_MODE, PerformanceProfile.fromTotalRamMb(8192))
    }

    @Test
    fun `test ContactCallEngine ending digits search and name matching`() {
        val sampleContacts = listOf(
            DeviceContactItem("1", "Rahul Sharma", "+91 98765 43245", "919876543245"),
            DeviceContactItem("2", "Amit Verma", "9811122245", "9811122245"),
            DeviceContactItem("3", "Papa", "9876500023", "9876500023")
        )

        val ending45 = ContactCallEngine.filterContactsByEndingDigits(sampleContacts, "45")
        assertEquals(2, ending45.size)
        assertTrue(ending45.any { it.displayName == "Rahul Sharma" })
        assertTrue(ending45.any { it.displayName == "Amit Verma" })

        val ending23 = ContactCallEngine.filterContactsByEndingDigits(sampleContacts, "23")
        assertEquals(1, ending23.size)
        assertEquals("Papa", ending23.first().displayName)

        val byName = ContactCallEngine.filterContactsByNameOrNumber(sampleContacts, "Rahul")
        assertEquals(1, byName.size)
        assertEquals("Rahul Sharma", byName.first().displayName)
    }

    @Test
    fun `test SmartMessageWriter drafts polished Hindi Bengali and English messages`() {
        val lateDraftHi = SmartMessageWriter.createSmartDraft(
            instruction = "Rahul ko bolo main aaj late aaunga",
            spokenLanguageCode = "hi-IN"
        )
        assertEquals("Rahul", lateDraftHi.recipientName)
        assertTrue(lateDraftHi.draftedText.isNotBlank())

        val bdayDraftEn = SmartMessageWriter.createSmartDraft(
            instruction = "Write a birthday wish for Priya",
            spokenLanguageCode = "en-IN"
        )
        assertEquals("Birthday Wish", bdayDraftEn.category.labelEn)
        assertTrue(bdayDraftEn.draftedText.contains("Birthday", ignoreCase = true))
    }

    @Test
    fun `test WeatherNetworkEngine WMO code mapping`() {
        val (enClear, hiClear, bnClear) = WeatherNetworkEngine.mapWmoWeatherCode(0)
        assertTrue(enClear.contains("Clear", ignoreCase = true))
        assertTrue(hiClear.isNotBlank())
        assertTrue(bnClear.isNotBlank())

        val (enRain, _, _) = WeatherNetworkEngine.mapWmoWeatherCode(63)
        assertTrue(enRain.contains("Rain", ignoreCase = true))
    }

    @Test
    fun `test SmartAppLauncher Top 50 catalog with Hindi names and aliases`() {
        assertEquals(50, SmartAppLauncher.TOP_50_CATALOG.size)
        assertTrue(SmartAppLauncher.TOP_50_CATALOG.all { it.displayName.isNotBlank() && it.hindiName.isNotBlank() })
        val youtubeEntry = SmartAppLauncher.TOP_50_CATALOG.firstOrNull { it.displayName == "YouTube" }
        assertNotNull(youtubeEntry)
        assertTrue(youtubeEntry!!.aliases.any { it.contains("यूट्यूब") || it.contains("youtube") })
    }

    @Test
    fun `test ToolExecutor curated YouTube direct video IDs`() {
        val videos = ToolExecutor.CURATED_YOUTUBE_VIDEOS
        assertTrue(videos.isNotEmpty())
        assertTrue(videos.all { it.videoId.length >= 8 && it.title.isNotBlank() })
    }

    @Test
    fun `test ProviderConfig key validation`() {
        val emptyGemini = ProviderConfig(AiProviderType.GOOGLE_GEMINI, apiKey = "")
        assertFalse(emptyGemini.hasValidKey())

        val placeholderOpenAi = ProviderConfig(AiProviderType.OPENAI_CHATGPT, apiKey = "MY_OPENAI_API_KEY")
        assertFalse(placeholderOpenAi.hasValidKey())

        val validOpenRouter = ProviderConfig(AiProviderType.OPENROUTER_CUSTOM, apiKey = "sk-or-v1-test123456")
        assertTrue(validOpenRouter.hasValidKey())
    }

    @Test
    fun `test LiveState, AvatarEmotion and AvatarStyle enums`() {
        val states = LiveState.values()
        assertTrue(states.contains(LiveState.DISCONNECTED))
        assertTrue(states.contains(LiveState.LISTENING))
        assertTrue(states.contains(LiveState.THINKING))
        assertTrue(states.contains(LiveState.SPEAKING))

        val emotions = AvatarEmotion.values()
        assertTrue(emotions.contains(AvatarEmotion.LISTENING))
        assertTrue(emotions.contains(AvatarEmotion.SPEAKING))

        val styles = AvatarStyle.values()
        assertTrue(styles.contains(AvatarStyle.GOTHIC_CYBER))
    }

    @Test
    fun `test CaroLineUiState initial A3_2 Ultimate Smart JARVIS defaults`() {
        val state = CaroLineUiState()
        assertTrue(state.appVersion.contains("A3.2"))
        assertEquals(DashboardTab.JARVIS_HUD, state.activeTab)
        assertFalse(state.isJarvisCoreMode)
        assertEquals("hi-IN", state.spokenLanguageCode)
        assertTrue(state.activeLanguage.contains("Hindi"))
        assertEquals(CaroLineBrainEngine.DEFAULT_GREETING_HI, state.speechSnippet)
        assertEquals(LiveState.DISCONNECTED, state.liveState)
        assertEquals("Hey CaroLine", state.wakeWordPhrase)
        assertTrue(state.isWakeWordEnabled)
        assertEquals(ListeningPowerMode.BATTERY_SAVING, state.listeningPowerMode)
        assertTrue(state.isBatterySavingListeningMode)
        assertTrue(state.isScreenOffVoiceEnabled)
        assertTrue(state.isBackgroundServiceEnabled)
        assertTrue(state.isFloatingBubbleEnabled)
        assertTrue(state.smartRoutines.isNotEmpty())
    }
}
