package br.com.radioembarcada.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.radioembarcada.ui.theme.RadioTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], qualifiers = "w360dp-h740dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadioPlayerScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var host: View
    private fun screen(playing: Boolean = false, requested: Boolean = playing, enabled: Boolean = true,
        toggle: () -> Unit = {}) {
        compose.setContent { host = LocalView.current; RadioTheme { RadioPlayerScreen("Rádio Alce", playing, requested, enabled, toggle) } }
    }
    @Test fun logoBadgeAndTaglineAreVisible() {
        screen()
        compose.onNodeWithTag("alce-logo").assertIsDisplayed()
        compose.onNodeWithText("AO VIVO").assertIsDisplayed()
        compose.onNodeWithText("Sua Rádio em Movimento").assertIsDisplayed()
    }
    @Test fun pauseAndPlayFollowRequestAndExecuteOnlyToggle() {
        val requested = mutableStateOf(false)
        var clicks = 0
        compose.setContent { host = LocalView.current; RadioTheme {
            RadioPlayerScreen("Rádio Alce", requested.value, requested.value, true, {
                requested.value = !requested.value; clicks++
            })
        } }
        compose.onNodeWithContentDescription("Reproduzir rádio").performClick()
        compose.onNodeWithContentDescription("Pausar rádio").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Reproduzir rádio").assertIsDisplayed()
        assertEquals(2, clicks)
    }
    @Test fun screenExposesOnlyOneActionAndNoNavigationSeekOrFooter() {
        screen()
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        listOf("Next", "Previous", "Próxima", "Anterior", "Músicas sem interrupções", "Energia limpa",
            "Notícias a cada 30 min", "Duração", "Reconectando", "Conectando").forEach {
            compose.onNodeWithText(it, substring = true).assertDoesNotExist()
        }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        // Exact visible copy: there is no title/artist input to this presentation component.
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(2)
    }
    @Test fun waveformIsActiveOnlyWhileActuallyPlaying() {
        val playing = mutableStateOf(false)
        compose.setContent { host = LocalView.current; RadioTheme {
            RadioPlayerScreen("Rádio Alce", playing.value, true, true, {})
        } }
        compose.onNodeWithTag("waveform").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Pausada"))
        compose.runOnIdle { playing.value = true }
        compose.onNodeWithTag("waveform").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ativa"))
        compose.runOnIdle { playing.value = false }
        compose.onNodeWithTag("waveform").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Pausada"))
    }
    @Test fun pausedWaveformDoesNotChangeAcrossTime() {
        screen()
        val before = capture("waveform")
        compose.mainClock.advanceTimeBy(1_000)
        val after = capture("waveform")
        assertTrue(before.sameAs(after))
    }
    @Test fun backgroundStopsAnimationWithoutChangingPlayPause() {
        compose.setContent { host = LocalView.current; RadioTheme { RadioPlayerScreen("Rádio Alce", true, true, true, {}, animateWaveform = false) } }
        compose.onNodeWithTag("waveform").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Pausada"))
        compose.onNodeWithContentDescription("Pausar rádio").assertIsDisplayed()
    }
    @Test fun disabledControllerCannotBeInvoked() {
        screen(enabled = false)
        compose.onNodeWithTag("play-pause").assertIsNotEnabled()
    }
    @Test @Config(qualifiers = "w320dp-h568dp-mdpi")
    fun smallScreenKeepsLogoAndButtonInBounds() { screen(); verifyAndCapture("small") }
    @Test @Config(qualifiers = "w480dp-h960dp-mdpi")
    fun largeScreenKeepsLogoAndButtonInBounds() { screen(); verifyAndCapture("large") }
    @Test @Config(qualifiers = "w640dp-h360dp-mdpi")
    fun landscapeKeepsPrimaryControlVisible() { screen(); verifyAndCapture("landscape") }
    @Test @Config(qualifiers = "w320dp-h568dp-mdpi")
    fun largeFontOnSmallScreenKeepsPrimaryControlVisible() {
        compose.setContent {
            host = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                RadioTheme { RadioPlayerScreen("Rádio Alce", false, false, true, {}) }
            }
        }
        verifyAndCapture("small-large-font")
    }
    private fun capture(tag: String): Bitmap {
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        lateinit var full: Bitmap
        compose.runOnIdle {
            full = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
            host.draw(Canvas(full))
        }
        return Bitmap.createBitmap(full, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
    }
    private fun verifyAndCapture(name: String) {
        val root = compose.onNodeWithTag("radio-screen").fetchSemanticsNode().boundsInRoot
        listOf("alce-logo", "play-pause", "waveform").forEach { tag ->
            compose.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag is outside $root", bounds.left >= root.left && bounds.right <= root.right &&
                bounds.top >= root.top && bounds.bottom <= root.bottom)
        }
        val image = capture("radio-screen")
        File("build/reports/ui/radio-$name.png").also { file ->
            file.parentFile?.mkdirs()
            file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
