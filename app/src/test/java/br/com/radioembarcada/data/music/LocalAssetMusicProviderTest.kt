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
        val unknown = tracks.filter { it.artist == "Rádio" }
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
            assertEquals("Programação indisponível.", error.userMessage)
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
            previous?.let { assertNotEquals(it.id, batch.first().id) }
            assertTrue(batch.zipWithNext().all { (a, b) -> a.id != b.id })
            previous = batch.last()
            pending = batch.takeLast(4).map { it.id }.toSet()
        }
    }

    @Test fun completeLibraryIsTraversedBeforeAnyTrackRepeatsAndThenReshuffled() = runBlocking {
        val source = provider((0 until 77).map { "music/MPB/$it.mp3" })
        val programming = AutomaticProgramming(source, br.com.radioembarcada.programming.QueueBuilder(kotlin.random.Random(15)))
        val firstCycle = mutableListOf<br.com.radioembarcada.model.ProgramItem>()
        repeat(4) { firstCycle += programming.nextBatch(firstCycle.lastOrNull()) }
        assertEquals(77, firstCycle.size)
        assertEquals(77, firstCycle.map { it.id }.distinct().size)
        val secondCycle = mutableListOf<br.com.radioembarcada.model.ProgramItem>()
        repeat(4) { secondCycle += programming.nextBatch(secondCycle.lastOrNull() ?: firstCycle.last()) }
        assertEquals(firstCycle.map { it.id }.toSet(), secondCycle.map { it.id }.toSet())
        assertNotEquals(firstCycle.map { it.id }, secondCycle.map { it.id })
        assertNotEquals(firstCycle.last().id, secondCycle.first().id)
    }

    @Test fun singleArtistDoesNotPreventFullLocalLibraryFromPlaying() = runBlocking {
        val source = provider((0 until 7).map { "music/MPB/$it.mp3" }) { LocalTrackMetadata(artist = "Same") }
        val batch = AutomaticProgramming(source).nextBatch()
        assertEquals(7, batch.size)
        assertEquals(7, batch.map { it.id }.distinct().size)
    }

    @Test fun failedTracksAreExcludedWithoutBlockingTheNextCycle() = runBlocking {
        val source = provider(listOf("music/good.mp3", "music/bad.mp3"))
        val programming = AutomaticProgramming(source)
        val first = programming.nextBatch()
        val failed = first.filter { it.title == "bad" }.map { it.id }.toSet()
        val next = programming.nextBatch(first.first { it.title == "bad" }, excludedIds = failed)
        assertEquals("good", next.single().title)
        assertTrue(programming.nextBatch(excludedIds = first.map { it.id }.toSet()).isEmpty())
    }

    @Test fun embeddedCoverReachesProgramItemAndOversizedPicturesAreOmitted() = runBlocking {
        val cover = byteArrayOf(1, 2, 3)
        val track = provider(listOf("music/cover.mp3")) { LocalTrackMetadata(artworkData = cover) }
            .fetchTracks(20, 0).single()
        assertArrayEquals(cover, br.com.radioembarcada.model.ProgramItem.music(track).artworkData)
        val large = provider(listOf("music/large.mp3")) {
            LocalTrackMetadata(artworkData = ByteArray(LocalAssetMusicProvider.MAX_ARTWORK_BYTES + 1))
        }.fetchTracks(20, 0).single()
        assertNull(large.artworkData)
    }

    @Test fun unreadableSubfolderDoesNotHideOtherMusicAndIgnoredFilesAreLogged() {
        val messages = mutableListOf<String>()
        val paths = findMp3Assets("music", { path -> when (path) {
            "music" -> listOf("bad", "MPB", "readme.txt")
            "music/bad" -> throw IOException("bad folder")
            "music/MPB" -> listOf("song.mp3")
            else -> emptyList()
        } }, messages::add)
        assertEquals(listOf("music/MPB/song.mp3"), paths)
        assertTrue(messages.any { "bad" in it })
        assertTrue(messages.any { "readme.txt" in it })
    }

    @Test fun singleTrackCanResumeAtTheEndWithoutAnImmediateDuplicateWhileStillQueued() = runBlocking {
        val programming = AutomaticProgramming(provider(listOf("music/one.mp3")))
        val first = programming.nextBatch().single()
        assertTrue(programming.nextBatch(first, setOf(first.id)).isEmpty())
        assertEquals(first.id, programming.nextBatch(first).single().id)
    }

}
