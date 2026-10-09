package br.com.radioembarcada.network.news

import android.app.Application
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class OfficialNewsDataSourceTest {
    @Test fun thirdPartyRedirectIsClosedBeforeReadingAnyAudio() {
        val raw = ByteArrayDataSource(byteArrayOf(1, 2))
        var closed = false
        val source = object : DataSource by raw {
            override fun getUri() = Uri.parse("https://unofficial.example/news.mp3")
            override fun close() { closed = true; raw.close() }
        }
        try {
            OfficialNewsDataSource(source).open(DataSpec(Uri.parse("https://audios.ebc.com.br/news.mp3")))
            fail("Redirect externo não pode ser reproduzido")
        } catch (_: IOException) { assertTrue(closed) }
    }
    @Test fun officialAudioPassesThroughWithoutModifyingSamples() {
        val bytes = byteArrayOf(1, 2, 3)
        val source = OfficialNewsDataSource(ByteArrayDataSource(bytes))
        try {
            assertEquals(3L, source.open(DataSpec(Uri.parse("https://audios.ebc.com.br/news.mp3"))))
            val actual = ByteArray(3)
            assertEquals(3, source.read(actual, 0, 3))
            assertArrayEquals(bytes, actual)
        } finally { source.close() }
    }
}
