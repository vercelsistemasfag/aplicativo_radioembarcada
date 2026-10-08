package br.com.radioembarcada.data.music

import br.com.radioembarcada.model.ConnectionState
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.programming.AutomaticProgramming
import java.net.URI
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalAssetMusicProviderTest {
    private fun provider(paths: List<String>, metadata: (String) -> LocalTrackMetadata = { LocalTrackMetadata() }) =
        LocalAssetMusicProvider({ parent -> paths.filter { it.startsWith("$parent/") }
            .map { it.removePrefix("$parent/").substringBefore('/') }.distinct() }, metadata)

    @Test fun findsMp3RecursivelyAcrossMultipleFoldersAndIgnoresOtherFiles() = runBlocking {
        val source = provider(listOf("music/root.mp3", "music/MPB/one.MP3",
            "music/MPB/album/disc/two.mp3", "music/other/three.mp3", "music/cover.jpg", "music/readme.txt"))
        assertEquals(setOf("music/root.mp3", "music/MPB/one.MP3", "music/MPB/album/disc/two.mp3",
            "music/other/three.mp3"), source.fetchTracks(20, 0).map { it.id }.toSet())
    }

    @Test fun usesTagsAndEncodesAssetPathsWithoutChangingTheirIdentity() = runBlocking {
        val path = "music/MPB/João #1 + verão.mp3"
        val source = provider(listOf(path)) { LocalTrackMetadata(" Título ", " Artista ", 183_000) }
        val track = source.fetchTracks(20, 0).single()
        assertEquals(path, track.id)
        assertEquals("Título", track.title)
        assertEquals("Artista", track.artist)
        assertEquals(183_000L, track.durationMs)
        assertEquals("asset", URI(track.audioUrl).scheme)
        assertEquals("/$path", URI(track.audioUrl).path)
        assertNull(URI(track.audioUrl).fragment)
        assertTrue(track.license.isEmpty() && track.sourceUrl.isEmpty())
    }

    @Test fun missingTagsFallBackToFilenameAndUnknownArtistsDoNotCollapseQueue() = runBlocking {
        val source = provider(listOf("music/MPB/Artist - Song.mp3", "music/MPB/other.mp3", "music/MPB/last.mp3"))
        val tracks = source.fetchTracks(20, 0)
        val named = tracks.first { it.title == "Artist - Song" }
        assertEquals("Artist", named.artist)
        val unknown = tracks.filter { it.artist == "Artista não informado" }
        assertEquals(2, unknown.map { it.artistKey }.distinct().size)
        assertTrue(tracks.all { it.durationMs == 0L })
    }

    @Test fun invalidMetadataOfOneFileDoesNotDiscardTheOtherTracks() = runBlocking {
        val source = provider(listOf("music/rock80/bad.mp3", "music/pop/good.mp3")) {
            if (it.endsWith("bad.mp3")) throw IllegalArgumentException("invalid metadata")
            LocalTrackMetadata("Good title", "Good artist", 123_000)
        }
        val tracks = source.fetchTracks(20, 0)
        assertEquals(2, tracks.size)
        assertEquals("bad", tracks.first { it.id.endsWith("bad.mp3") }.title)
        assertEquals("Good title", tracks.first { it.id.endsWith("good.mp3") }.title)
    }

    @Test fun unreadableMetadataFallsBackToWholeFilenameIncludingArtistPrefix() = runBlocking {
        val source = provider(listOf("music/instrumental/Artist - Title.mp3")) {
            throw IOException("broken tags")
        }
        assertEquals("Artist - Title", source.fetchTracks(20, 0).single().title)
    }

    @Test fun paginationWrapsWithoutRepeatingIdsInsideThePage() = runBlocking {
        val source = provider((0 until 77).map { "music/MPB/%02d.mp3".format(it) })
        val page = source.fetchTracks(30, 60)
        assertEquals(30, page.size)
        assertEquals(30, page.map { it.id }.distinct().size)
        assertEquals("music/MPB/60.mp3", page.first().id)
        assertEquals("music/MPB/00.mp3", page[17].id)
        assertEquals(77, source.fetchTracks(200, 0).size)
    }

    @Test fun emptyLibraryReportsConfigurationErrorWithoutRetryLoop() = runBlocking {
        try {
            provider(emptyList()).fetchTracks(20, 0)
            fail("Deve informar biblioteca ausente")
        } catch (error: MusicProviderException) {
            assertFalse(error.retryable)
            assertTrue(error.userMessage.contains("assets/music"))
        }
    }

    @Test fun unreadableAssetListingIsReportedAsSanitizedProviderError() = runBlocking {
        val source = LocalAssetMusicProvider({ throw IOException("private path") }, { LocalTrackMetadata() })
        try {
            source.fetchTracks(20, 0)
            fail("Deve informar falha de leitura")
        } catch (error: MusicProviderException) {
            assertFalse(error.retryable)
            assertFalse(error.userMessage.contains("private path"))
        }
    }

    @Test fun localCatalogAndStateRemainAvailableInAirplaneMode() {
        val local = provider(listOf("music/test.mp3"))
        assertFalse(local.requiresNetwork)
        assertTrue(local.isAvailable(false))
        assertEquals(ConnectionState.CONNECTING,
            ConnectionState.resolve(true, local.isAvailable(false), false, false, false))
        assertEquals(ConnectionState.LIVE,
            ConnectionState.resolve(true, local.isAvailable(false), true, false, false))
        val remote = br.com.radioembarcada.network.jamendo.JamendoMusicProvider("")
        assertFalse(remote.isAvailable(false))
        assertTrue(remote.isAvailable(true))
    }

    @Test fun seventySevenLocalTracksKeepAutomaticProgramRefillingOffline() = runBlocking {
        val source = provider((0 until 77).map { "music/MPB/%02d.mp3".format(it) }) {
            val id = it.substringAfterLast('/').substringBefore('.').toInt()
            LocalTrackMetadata("Song $id", "Artist ${id % 12}", 180_000)
        }
        val program = AutomaticProgramming(source)
        var previous: br.com.radioembarcada.model.ProgramItem? = null
        var pending = emptySet<String>()
        repeat(12) {
            val batch = program.nextBatch(previous, pending)
            assertTrue(batch.isNotEmpty())
            assertTrue(batch.all { it.type == ProgramItemType.MUSIC && it.audioUrl.startsWith("asset:///music/") })
            assertTrue(batch.none { it.id in pending })
            previous?.let { assertNotEquals(it.artistKey, batch.first().artistKey) }
            assertTrue(batch.zipWithNext().all { (a, b) -> a.id != b.id && a.artistKey != b.artistKey })
            previous = batch.last()
            pending = batch.takeLast(4).map { it.id }.toSet()
        }
    }
}
