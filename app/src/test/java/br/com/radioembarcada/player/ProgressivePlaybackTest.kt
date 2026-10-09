package br.com.radioembarcada.player

import android.app.Application
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.test.utils.*
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Extração real do ProgressiveMediaPeriod; sem rede, codec do aparelho ou músicas privadas. */
@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ProgressivePlaybackTest {
    // WAV PCM curto gerado em memória: exercício do mesmo ciclo de preparação usado pelo MP3.
    private fun audio(): ByteArray {
        val samples = 8_000 * 3
        return ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + samples * 2); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8_000); putInt(16_000)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(samples * 2)
            repeat(samples) { putShort(if (it % 16 < 8) 1_000 else -1_000) }
        }.array()
    }

    private fun verifyProgressivePlayback(ids: List<String>) {
        val readiness = PlaybackReadiness()
        val control = RadioLoadControl(playlistPreload = true).apply { currentAudio = readiness::get }
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication(),
            RenderersFactory { _, _, _, _, _ -> arrayOf(FakeRenderer(C.TRACK_TYPE_AUDIO)) })
            .setLoadControl(control).setClock(FakeClock(true)).build()
        val automatic = mutableListOf<String>()
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                control.currentMediaId = mediaItem?.mediaId
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) automatic += mediaItem!!.mediaId
            }
        })
        val data = audio()
        val factory = ProgressiveMediaSource.Factory { ByteArrayDataSource(data) }
        try {
            player.setPreloadConfiguration(ExoPlayer.PreloadConfiguration(45_000_000))
            player.setMediaSources(ids.map { id -> readiness.observe(factory.createMediaSource(
                MediaItem.Builder().setMediaId(id).setUri("https://example.invalid/$id.wav").build())) })
            assertFalse(readiness.get(ids.first()).ready)
            player.prepare()
            advance(player).untilState(Player.STATE_READY)
            assertNull(player.playerError)
            assertTrue(readiness.get(ids.first()).ready)
            player.play()
            advance(player).untilPosition(0, 500)
            player.pause()
            advance(player).untilPendingCommandsAreFullyHandled()
            assertTrue(readiness.get(ids.first()).ready)
            player.play()
            advance(player).untilState(Player.STATE_ENDED)
            assertNull(player.playerError)
            assertEquals(ids.drop(1), automatic)
        } finally { player.release() }
    }

    @Test fun progressiveAudioLoadsBeforePreparationWithoutReadingAnInvalidBuffer() {
        verifyProgressivePlayback(listOf("music"))
    }

    @Test fun progressiveQueuePreloadsAndAdvancesAfterPauseWithoutLatePrepare() {
        verifyProgressivePlayback(listOf("music1", "station", "music2", "jingle", "music3"))
    }
}
