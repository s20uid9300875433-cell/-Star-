package com.example

import com.example.live.AiProviderType
import com.example.live.CaroLineBrainEngine
import com.example.live.LiveState
import com.example.live.ProviderConfig
import com.example.tools.SmartAppLauncher
import com.example.tools.ToolExecutor
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
    fun `test CaroLine 2_9 permanent Hindi greeting and Smart Routines`() {
        assertEquals(
            "नमस्ते बॉस! मैं कैरोलाइन हूँ। बताइए, मैं आपकी क्या मदद कर सकती हूँ?",
            CaroLineBrainEngine.DEFAULT_GREETING_HI
        )
        assertTrue(CaroLineBrainEngine.DEFAULT_SMART_ROUTINES.size >= 4)
        assertTrue(CaroLineBrainEngine.DEFAULT_SMART_ROUTINES.all { it.nameHi.isNotBlank() && it.commands.isNotEmpty() })
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
    fun `test ProviderConfig key validation for Three AI Brain System`() {
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
    fun `test CaroLineUiState initial 2_9 Ultimate Smart JARVIS Hindi defaults`() {
        val state = CaroLineUiState()
        assertTrue(state.appVersion.contains("2.9"))
        assertEquals(DashboardTab.JARVIS_HUD, state.activeTab)
        assertFalse(state.isJarvisCoreMode) // Defaults to Anime Avatar inside JARVIS AI circle
        assertTrue(state.isLanguageLocked)
        assertTrue(state.activeLanguage.contains("हिन्दी") || state.activeLanguage.contains("Hindi"))
        assertEquals(CaroLineBrainEngine.DEFAULT_GREETING_HI, state.speechSnippet)
        assertEquals(3, state.providerConfigs.size)
        assertEquals(LiveState.DISCONNECTED, state.liveState)
        assertTrue(state.smartRoutines.isNotEmpty())
    }
}
