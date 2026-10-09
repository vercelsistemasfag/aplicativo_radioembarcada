package br.com.radioembarcada.player

import android.app.Application
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.test.utils.*
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.media3.test.utils.robolectric.RobolectricUtil.runMainLooperUntil
import java.util.concurrent.CopyOnWriteArrayList
import br.com.radioembarcada.model.ProgramItemType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class InsertRendererTest {
    @Test fun rendererDetectsEveryStationFromTimelineAndTrimsOnlyItsPrefix() {
        val logs = CopyOnWriteArrayList<String>()
        val prefix = InsertSilenceProcessor(logs::add)
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication(),
            RadioRenderersFactory(RuntimeEnvironment.getApplication(), prefix)).setClock(FakeClock(true)).build()
        val types = (1..7).flatMap { listOf(ProgramItemType.MUSIC, ProgramItemType.STATION_ID) } +
            listOf(ProgramItemType.MUSIC, ProgramItemType.JINGLE, ProgramItemType.MUSIC)
        val ids = types.indices.map { "item-$it" }
        val sources = types.mapIndexed { index, type ->
            val media = MediaItem.Builder().setMediaId(ids[index]).setUri("https://example.invalid/$index")
                .setMediaMetadata(MediaMetadata.Builder().setTitle(ids[index]).setExtras(android.os.Bundle().apply {
                    putString("programType", type.name)
                }).build()).build()
            val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setChannelCount(1).setSampleRate(44_100).setPcmEncoding(C.ENCODING_PCM_16BIT).build()
            val data = ByteBuffer.allocate(8_820).order(ByteOrder.LITTLE_ENDIAN).apply {
                repeat(4_410) { putShort(if (it < 2_205) 0 else 100) }
            }.array()
            val timeline = FakeTimeline(FakeTimeline.TimelineWindowDefinition(
                1, ids[index], true, false, false, false, 100_000, 0, 0,
                listOf(AdPlaybackState.NONE), media))
            FakeMediaSource(timeline, DrmSessionManager.DRM_UNSUPPORTED,
                FakeMediaPeriod.TrackDataFactory { _, _ -> listOf(
                    FakeSampleStream.FakeSampleStreamItem.sample(0, C.BUFFER_FLAG_KEY_FRAME, data),
                    FakeSampleStream.FakeSampleStreamItem.END_OF_STREAM_ITEM) }, format)
        }
        try {
            player.setMediaSources(sources)
            player.prepare(); player.play()
            advance(player).untilState(Player.STATE_READY)
            // O AudioTrack simulado consome instantaneamente dados escritos. Verificar o
            // pipeline de saída, não seu relógio acústico. Avanço completo é testado em PlaylistPreloadTest.
            runMainLooperUntil { logs.count { "Initial digital silence removed:" in it } == 8 }
            types.forEachIndexed { index, type ->
                assertEquals(if (type == ProgramItemType.MUSIC) 0L else 50L, prefix.removedMs(ids[index]))
            }
            assertEquals(8, logs.count { "Initial digital silence removed:" in it })
        } finally { player.release() }
    }
}
