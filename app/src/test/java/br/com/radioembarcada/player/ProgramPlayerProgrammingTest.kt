package br.com.radioembarcada.player

import android.app.Application
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.data.programming.ProgrammingProvider
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.model.Track
import br.com.radioembarcada.network.programming.RemoteProgrammingParser
import br.com.radioembarcada.programming.AutomaticProgramming
import kotlinx.coroutines.runBlocking
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
class ProgramPlayerProgrammingTest {
    @Test fun changingOnlyFutureTimelinePreservesCurrentMusicAndNewsMetadata() {
        val engine = ProgramPlayer(RuntimeEnvironment.getApplication())
        try {
            val current = br.com.radioembarcada.model.ProgramItem("current", ProgramItemType.MUSIC, "Music", "https://example.org/current.mp3", 180_000)
            val next = current.copy(id = "next", audioUrl = "https://example.org/next.mp3")
            engine.player.setMediaItems(engine.register(listOf(current, next)))
            val intro = current.copy(id = "intro", type = ProgramItemType.NEWS_INTRO, title = "Notícias")
            val drop = current.copy(id = "drop", type = ProgramItemType.NEWS_DROP, title = "Notícia", artist = "Radioagência Nacional", source = "Radioagência Nacional")
            engine.replaceUpcoming(listOf(intro, drop, next))
            assertEquals("current", engine.player.currentMediaItem?.mediaId)
            assertEquals(0, engine.player.currentMediaItemIndex)
            assertEquals(0L, engine.player.currentPosition)
            assertEquals(listOf("current", "intro", "drop", "next"), engine.items.map { it.id })
            assertEquals("Radioagência Nacional", engine.player.getMediaItemAt(2).mediaMetadata.artist)
            assertEquals("Radioagência Nacional", engine.player.getMediaItemAt(2).mediaMetadata.extras?.getString("source"))
        } finally { engine.release() }
    }

    @Test fun allSevenStationsUseIdenticalMediaItemPipelineGainAndCachePolicy() {
        val config = RemoteProgrammingParser.parse(checkNotNull(javaClass.getResource("/programming-v4.json")).readText())
        val engine = ProgramPlayer(RuntimeEnvironment.getApplication())
        try {
            val batch = config.stationIds.flatMapIndexed { index, item -> listOf(
                br.com.radioembarcada.model.ProgramItem("music-$index", ProgramItemType.MUSIC,
                    "Music", "https://example.org/music-$index.mp3", 180_000), item) }
            val media = engine.register(batch)
            engine.player.setMediaItems(media)
            val pieces = media.filter { it.mediaMetadata.extras?.getString("programType") == "STATION_ID" }
            assertEquals(7, pieces.size)
            assertEquals(config.stationIds.map { it.id }, pieces.map { it.mediaId })
            pieces.zip(config.stationIds).forEach { (item, program) ->
                assertEquals(program.audioUrl, item.localConfiguration?.uri.toString())
                assertEquals(audioCacheKey(program), item.localConfiguration?.customCacheKey)
                assertEquals(program.title, item.mediaMetadata.title)
                assertEquals(TransitionConfiguration.STATION_ID_GAIN, TransitionPolicy.gain(program.type), 0f)
                assertEquals(45_000L, PreloadPlan.durationMs(1, program.type, false))
            }
        } finally { engine.release() }
    }
    @Test fun repeatedPiecesEnterTheActualPlayerPipelineWithSharedCacheAndMetadata() = runBlocking {
        val provider = object : MusicProvider {
            override val providesCompleteCatalog = true
            override suspend fun fetchTracks(limit: Int, offset: Int) = (1..40).map {
                Track("$it", "Music $it", "Artist $it", "$it", null,
                    "https://example.org/$it.mp3", 180_000, "", "Fixture", "")
            }
        }
        val programming = AutomaticProgramming(provider, programmingProvider = ProgrammingProvider {
            RemoteProgrammingParser.parse(checkNotNull(javaClass.getResource("/programming.json")).readText())
        })
        val engine = ProgramPlayer(RuntimeEnvironment.getApplication())
        try {
            val batch = programming.nextBatch()
            val media = engine.register(batch)
            engine.player.setMediaItems(media)
            assertEquals(batch.map { it.id }, media.map { it.mediaId })
            val ids = media.filter { it.mediaMetadata.extras?.getString("programType") == ProgramItemType.STATION_ID.name }
            assertEquals(14, ids.size)
            assertEquals(ids.size, ids.map { it.mediaId }.distinct().size)
            assertEquals(1, ids.map { it.localConfiguration?.customCacheKey }.distinct().size)
            assertTrue(ids.all { it.mediaMetadata.title == "Rádio Alce" &&
                it.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_MIXED &&
                it.localConfiguration?.uri.toString() == "https://example.org/station.mp3" })
            val next = programming.nextBatch(batch.last(), batch.map { it.id }.toSet())
            engine.append(next)
            assertEquals(batch.size + next.size, engine.player.mediaItemCount)
            assertEquals(engine.items.size, engine.items.map { it.id }.distinct().size)
        } finally { engine.release() }
    }
}
