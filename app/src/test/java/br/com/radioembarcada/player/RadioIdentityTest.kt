package br.com.radioembarcada.player

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import br.com.radioembarcada.R
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RadioIdentityTest {
    @Test @Config(sdk = [28]) @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun olderAndroidLoadsTheAdaptiveIconWithoutThemedIconSupport() {
        val context = RuntimeEnvironment.getApplication()
        assertTrue(context.getDrawable(R.mipmap.ic_launcher) is AdaptiveIconDrawable)
        assertEquals("Rádio Alce", context.packageManager.getApplicationLabel(context.applicationInfo).toString())
    }
    @Test fun launcherUsesRadioAlceLabelAndOfficialLogoAdaptiveIcon() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals("Rádio Alce", context.getString(R.string.app_name))
        val drawable = checkNotNull(context.getDrawable(R.mipmap.ic_launcher))
        assertTrue(drawable is AdaptiveIconDrawable)
        val icon = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 192, 192)
        drawable.draw(Canvas(icon))
        File("build/reports/ui/launcher-icon.png").also { file ->
            file.parentFile?.mkdirs()
            file.outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertNotEquals(icon.getPixel(96, 96), icon.getPixel(20, 96))
    }
}
