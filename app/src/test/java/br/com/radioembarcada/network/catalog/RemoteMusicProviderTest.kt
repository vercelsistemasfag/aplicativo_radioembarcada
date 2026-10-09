package br.com.radioembarcada.network.catalog

import br.com.radioembarcada.data.music.MusicProviderException
import br.com.radioembarcada.model.ConnectionState
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.programming.AutomaticProgramming
import br.com.radioembarcada.programming.QueueBuilder
import br.com.radioembarcada.storage.SavedMusicCatalog
import br.com.radioembarcada.storage.FileMusicCatalog
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemoteMusicProviderTest {
    private class Store(var json: String? = null) : SavedMusicCatalog {
        override fun read() = json
        override fun write(json: String) { this.json = json }
    }
    private fun catalog(count: Int = 77, version: Int = 1, prefix: String = "track"): String = JSONObject()
        .put("station", "Rádio Alce").put("version", version).put("items", JSONArray().apply {
            repeat(count) { id -> put(JSONObject().put("id", "$prefix$id").put("type", "MUSIC")
                .put("title", "Música $id").put("artist", "")
                .put("url", "https://example.org/music/$prefix$id.mp3")) }
        }).toString()

    @Test fun parsesSeventySevenMusicItemsWithBlankArtistsAndIndividualUrls() {
        val parsed = RemoteCatalogParser.parse(catalog())
        assertEquals(77, parsed.tracks.size)
        assertEquals(1L, parsed.version)
        assertTrue(parsed.tracks.all { it.artist.isBlank() && it.audioUrl.startsWith("https://") })
        assertEquals(77, parsed.tracks.map { it.artistKey }.distinct().size)
    }
    @Test fun ignoresInvalidItemsMissingUrlsAndOtherProgramTypesAndDeduplicates() {
        val root = JSONObject(catalog(1))
        val items = root.getJSONArray("items")
        items.put(JSONObject().put("id", "missing").put("type", "MUSIC").put("title", "Missing"))
        items.put(JSONObject().put("id", "bad").put("type", "MUSIC").put("title", "Bad").put("url", "file:///secret"))
        items.put(JSONObject().put("id", "empty").put("type", "MUSIC").put("title", "").put("url", "https://example.org/e.mp3"))
        items.put(JSONObject().put("id", "jingle").put("type", "JINGLE").put("title", "Jingle").put("url", "https://example.org/j.mp3"))
        items.put(items.getJSONObject(0))
        assertEquals(1, RemoteCatalogParser.parse(root.toString()).tracks.size)
    }
    @Test(expected = IllegalArgumentException::class) fun emptyCatalogIsRejected() {
        RemoteCatalogParser.parse(catalog(0))
    }
    @Test fun networkFailureUsesLastValidSavedCatalogEvenInNewProvider() = runBlocking {
        val store = Store()
        val online = RemoteMusicProvider(store, download = { catalog() })
        assertEquals(77, online.fetchTracks(100, 0).size)
        val failing = RemoteMusicProvider(store, download = { throw IOException("private URL") })
        assertEquals(77, failing.fetchTracks(100, 0).size)
        val offline = RemoteMusicProvider(store, connected = { false }, download = { error("No request offline") })
        assertEquals(77, offline.fetchTracks(100, 0).size)
    }
    @Test fun failedCatalogWithoutSavedVersionReturnsSanitizedRetryableUnavailableState() = runBlocking {
        val provider = RemoteMusicProvider(Store(), download = { throw IOException("private URL") })
        try { provider.fetchTracks(20, 0); fail("Missing catalog") } catch (e: MusicProviderException) {
            assertTrue(e.retryable)
            assertEquals("Programação indisponível.", e.userMessage)
            assertEquals(ConnectionState.UNAVAILABLE, ConnectionState.resolve(true, true, false, false,
                true, catalogUnavailable = true))
        }
    }
    @Test fun invalidRemoteResponseDoesNotReplaceSavedCatalog() = runBlocking {
        val saved = catalog(2)
        val store = Store(saved)
        assertEquals(2, RemoteMusicProvider(store, download = { catalog(0) }).fetchTracks(100, 0).size)
        assertEquals(saved, store.json)
    }
    @Test fun catalogIsRefreshedAtIntervalAndNewVersionUpdatesProgramming() = runBlocking {
        var time = 0L
        var calls = 0
        val provider = RemoteMusicProvider(Store(), now = { time }, download = {
            calls++; if (calls == 1) catalog(77) else catalog(4, 2, "new")
        })
        val program = AutomaticProgramming(provider, QueueBuilder(Random(12)))
        val first = program.nextBatch()
        val second = program.nextBatch(first.last())
        assertEquals(1, calls)
        assertTrue(first.map { it.id }.intersect(second.map { it.id }.toSet()).isEmpty())
        time += RemoteCatalogConfiguration.REFRESH_INTERVAL_MS
        val updated = program.nextBatch(second.last())
        assertEquals(2L, provider.catalogRevision)
        assertEquals(4, updated.size)
        assertTrue(updated.all { "new" in it.id })
    }
    @Test fun failedRefreshIsThrottledInsteadOfDownloadingOnEveryQueuePoll() = runBlocking {
        var time = 0L
        var calls = 0
        val provider = RemoteMusicProvider(Store(catalog()), now = { time }, download = { calls++; throw IOException() })
        repeat(10) { provider.fetchTracks(20, 0) }
        assertEquals(1, calls)
        time += RemoteCatalogConfiguration.FAILED_REFRESH_INTERVAL_MS
        provider.fetchTracks(20, 0)
        assertEquals(2, calls)
    }
    @Test fun completeRemoteLibraryMakesTwoDifferentCyclesWithoutImmediateRepetition() = runBlocking {
        val program = AutomaticProgramming(RemoteMusicProvider(Store(), download = { catalog() }), QueueBuilder(Random(33)))
        val first = mutableListOf<br.com.radioembarcada.model.ProgramItem>()
        repeat(4) { first += program.nextBatch(first.lastOrNull()) }
        val next = mutableListOf<br.com.radioembarcada.model.ProgramItem>()
        repeat(4) { next += program.nextBatch(next.lastOrNull() ?: first.last()) }
        assertEquals(77, first.map { it.id }.distinct().size)
        assertEquals(first.map { it.id }.toSet(), next.map { it.id }.toSet())
        assertNotEquals(first.map { it.id }, next.map { it.id })
        assertNotEquals(first.last().id, next.first().id)
        assertTrue(next.all { it.type == ProgramItemType.MUSIC && it.audioUrl.startsWith("https://") })
    }
    @Test fun offlineQueuePrefersPreparedAudioWithoutDownloadingLibrary() = runBlocking {
        val provider = RemoteMusicProvider(Store(catalog()), connected = { false }, download = { error("offline") })
        val queue = AutomaticProgramming(provider).nextBatch(prefer = { it.id.endsWith("track70") })
        assertTrue(queue.first().id.endsWith("track70"))
    }
    @Test fun atomicFileStorageSurvivesAProviderRestart() = runBlocking {
        val directory = Files.createTempDirectory("catalog-test").toFile()
        try {
            val file = directory.resolve("catalog.json")
            RemoteMusicProvider(FileMusicCatalog(file), download = { catalog(3) }).fetchTracks(20, 0)
            assertEquals(3, RemoteMusicProvider(FileMusicCatalog(file), connected = { false }).fetchTracks(20, 0).size)
            assertFalse(directory.resolve("catalog.json.tmp").exists())
        } finally { directory.deleteRecursively() }
    }
    @Test fun corruptSavedJsonCanBeReplacedByValidNetworkResponse() = runBlocking {
        val store = Store("invalid JSON")
        assertEquals(77, RemoteMusicProvider(store, download = { catalog() }).fetchTracks(100, 0).size)
        assertEquals(77, RemoteCatalogParser.parse(checkNotNull(store.json)).tracks.size)
    }
}
