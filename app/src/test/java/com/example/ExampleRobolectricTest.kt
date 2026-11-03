package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.live.AiProviderType
import com.example.live.LiveState
import com.example.live.MultiAiProviderRouter
import com.example.live.ProviderConfig
import com.example.tools.SmartAppLauncher
import com.example.tools.ToolExecutor
import com.example.ui.components.AvatarEmotion
import com.example.ui.components.AvatarStyle
import com.example.viewmodel.CaroLineViewModel
import com.example.viewmodel.DashboardTab
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CaroLine AI", appName)
    }

    @Test
    fun `test ToolExecutor getDeviceInfo and volume control`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val toolExecutor = ToolExecutor(context)
        val result = toolExecutor.execute("getDeviceInfo", emptyMap())
        assertNotNull(result["current_time"])
        assertNotNull(result["device_model"])
        assertNotNull(result["battery_level"])
        assertNotNull(result["avail_ram_mb"])

        val volResult = toolExecutor.execute("adjustVolume", mapOf("action" to "up"))
        assertEquals("success", volResult["status"])
    }

    @Test
    fun `test SmartAppLauncher Top 50 catalog`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launcher = SmartAppLauncher(context)
        assertEquals(50, SmartAppLauncher.TOP_50_CATALOG.size)
        val catalog = launcher.getCatalogWithInstalledStatus()
        assertTrue(catalog.size >= 50)
    }

    @Test
    fun `test MultiAiProviderRouter never fakes connection without key`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val router = MultiAiProviderRouter(context)
        val emptyGemini = ProviderConfig(AiProviderType.GOOGLE_GEMINI, apiKey = "")
        val testRes = router.testProviderConnection(emptyGemini)
        assertFalse(testRes.isSuccess)
        assertTrue(testRes.statusMessage.isNotBlank())
    }

    @Test
    fun `test LiveState enum values`() {
        val states = LiveState.values()
        assertTrue(states.contains(LiveState.DISCONNECTED))
        assertTrue(states.contains(LiveState.CONNECTING))
        assertTrue(states.contains(LiveState.LISTENING))
        assertTrue(states.contains(LiveState.THINKING))
        assertTrue(states.contains(LiveState.SPEAKING))
        assertTrue(states.contains(LiveState.RECONNECTING))
        assertTrue(states.contains(LiveState.ERROR))
    }

    @Test
    fun `test AvatarEmotion and AvatarStyle enums`() {
        val emotions = AvatarEmotion.values()
        assertTrue(emotions.contains(AvatarEmotion.LISTENING))
        assertTrue(emotions.contains(AvatarEmotion.SMILING))
        assertTrue(emotions.contains(AvatarEmotion.THINKING))
        assertTrue(emotions.contains(AvatarEmotion.SPEAKING))
        assertTrue(emotions.contains(AvatarEmotion.HAPPY))
        assertTrue(emotions.contains(AvatarEmotion.SURPRISED))

        val styles = AvatarStyle.values()
        assertTrue(styles.contains(AvatarStyle.GOTHIC_CYBER))
        assertTrue(styles.contains(AvatarStyle.PINK_NEON))
        assertTrue(styles.contains(AvatarStyle.CLASSIC_ANIME))
        assertTrue(styles.contains(AvatarStyle.CUSTOM_UPLOADED))
    }

    @Test
    fun `test CaroLineViewModel initial 2_5 Smart JARVIS Bengali state`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = CaroLineViewModel(app)
        val state = viewModel.uiState.value
        assertEquals("2.5 SMART JARVIS", state.appVersion)
        assertEquals(DashboardTab.JARVIS_HUD, state.activeTab)
        assertTrue(state.isJarvisCoreMode)
        assertTrue(state.isLanguageLocked)
        assertTrue(state.activeLanguage.contains("বাংলা"))
        assertEquals(3, state.providerConfigs.size)
        assertEquals(LiveState.DISCONNECTED, state.liveState)
        assertFalse(state.isMicHardwareVerified)
        assertNotNull(state.speechSnippet)
    }
}
