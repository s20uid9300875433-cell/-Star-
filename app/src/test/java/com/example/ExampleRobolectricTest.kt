package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.live.LiveState
import com.example.tools.ToolExecutor
import com.example.ui.components.AvatarEmotion
import com.example.ui.components.AvatarStyle
import com.example.viewmodel.CaroLineViewModel
import org.junit.Assert.assertEquals
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
    fun `test ToolExecutor getDeviceInfo`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val toolExecutor = ToolExecutor(context)
        val result = toolExecutor.execute("getDeviceInfo", emptyMap())
        assertNotNull(result["current_time"])
        assertNotNull(result["device_model"])
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
    fun `test CaroLineViewModel initial state for Boss`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = CaroLineViewModel(app)
        val state = viewModel.uiState.value
        assertEquals("Boss", state.developerName)
        assertEquals(LiveState.DISCONNECTED, state.liveState)
        assertNotNull(state.speechSnippet)
    }
}
