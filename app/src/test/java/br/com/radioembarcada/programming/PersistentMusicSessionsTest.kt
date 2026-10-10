package br.com.radioembarcada.programming

import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.model.Track
import br.com.radioembarcada.storage.PlaybackHistoryRepository
import br.com.radioembarcada.storage.PlaybackHistoryState
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersistentMusicSessionsTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun track(id: Int) = Track("$id", "Song $id", "", "unknown:$id", null,
        "https://example.org/$id.mp3", 180_000, "", "Fixture", "")
    private fun provider(tracks: List<Track>) = object : MusicProvider {
        override val providesCompleteCatalog = true
        override suspend fun fetchTracks(limit: Int, offset: Int) = tracks
    }
    private fun engine(file: File, tracks: List<Track>, time: Long = 1_000L) =
        AutomaticProgramming(provider(tracks), QueueBuilder(Random(42)),
            history = PlaybackHistoryRepository(file), now = { time }).also { it.startSession() }

    @Test fun forceStopReinstantiationKeepsRecentSongsBlockedAndLastSongOffTheOpening() = runBlocking {
        val file = temporary.newFolder().resolve("history.json")
        val tracks = (1..77).map(::track)
        val first = engine(file, tracks)
        val queue = first.nextBatch()
        val played = queue.take(5)
        played.forEach(first::onPlaying)
        // No release/onDestroy checkpoint: discard all objects and reopen only the file.
        val restored = engine(file, tracks, 2_000L).nextBatch()
        assertTrue(restored.none { item -> played.any { it.contentId == item.contentId } })
        assertNotEquals(played.last().contentId, restored.first().contentId)
        assertEquals(5, PlaybackHistoryRepository(file).read().size)
    }

    @Test fun lastSongDoesNotOpenNewSessionEvenAfterTheSixHourWindowExpires() = runBlocking {
        val file = temporary.newFile()
        val tracks = (1..10).map(::track)
        PlaybackHistoryRepository(file).writeState(PlaybackHistoryState(
            tracks.associate { it.key to 0L }, lastTrackId = tracks[4].key))
        val queue = engine(file, tracks, ProgrammingConfiguration.MUSIC_REPEAT_BLOCK_WINDOW).nextBatch()
        assertFalse(queue.isEmpty())
        assertNotEquals(tracks[4].key, queue.first().contentId)
        assertTrue(queue.zipWithNext().all { (a, b) -> a.contentId != b.contentId })
    }

    @Test fun savedOpeningFingerprintPreventsExactOrderEvenWithIdenticalTestRandomSeed() = runBlocking {
        val file = temporary.newFile()
        val tracks = (1..77).map(::track)
        val first = engine(file, tracks).nextBatch().take(5).map { it.contentId }
        assertTrue(PlaybackHistoryRepository(file).read().isEmpty()) // Preload is not execution.
        val second = engine(file, tracks).nextBatch().take(5).map { it.contentId }
        assertNotEquals(first, second)
        assertEquals(second, PlaybackHistoryRepository(file).readState().lastSessionOpeningSequence)
    }

    @Test fun catalogueRefreshOnlyRemovesObsoleteHistoryAndImmediatelyAllowsNewMusic() = runBlocking {
        val file = temporary.newFile()
        PlaybackHistoryRepository(file).writeState(PlaybackHistoryState(
            (1..3).associate { track(it).key to 100L }, lastTrackId = track(3).key,
            lastSessionOpeningSequence = (1..3).map { track(it).key }))
        val queue = engine(file, listOf(track(2), track(4)), 200L).nextBatch()
        assertEquals(listOf(track(4).key), queue.map { it.contentId })
        assertEquals(mapOf(track(2).key to 100L), PlaybackHistoryRepository(file).read())
    }

    @Test fun retentionRemovesOldEntriesWithoutClearingRecentPlayback() = runBlocking {
        val file = temporary.newFile()
        val now = ProgrammingConfiguration.MUSIC_HISTORY_RETENTION_MS + 1
        PlaybackHistoryRepository(file).writeState(PlaybackHistoryState(
            mapOf(track(1).key to 0L, track(2).key to now - 1), lastTrackId = track(2).key))
        engine(file, listOf(track(1), track(2)), now).nextBatch()
        assertEquals(mapOf(track(2).key to now - 1), PlaybackHistoryRepository(file).read())
        assertEquals(track(2).key, PlaybackHistoryRepository(file).readState().lastTrackId)
    }

    @Test fun smallCatalogueRelaxesWithoutImmediateRepeatsAcrossManyProcessRestarts() = runBlocking {
        val file = temporary.newFile()
        val tracks = (1..3).map(::track)
        var last: ProgramItem? = null
        repeat(12) { session ->
            val radio = engine(file, tracks, 1_000L + session)
            val batch = radio.nextBatch()
            assertTrue(batch.isNotEmpty())
            last?.let { assertNotEquals(it.contentId, batch.first().contentId) }
            batch.forEach(radio::onPlaying)
            last = batch.last()
        }
    }

    @Test fun singleSongCatalogueIsTheOnlyImmediateRepeatException() = runBlocking {
        val file = temporary.newFile()
        val tracks = listOf(track(1))
        val first = engine(file, tracks)
        first.nextBatch().forEach(first::onPlaying)
        assertEquals(track(1).key, engine(file, tracks).nextBatch().single().contentId)
    }

    @Test fun pauseAndEveryNonMusicTypePreserveMusicalTimestampAndLastTrack() {
        val file = temporary.newFile()
        var time = 1_000L
        val policy = MusicRepeatPolicy(PlaybackHistoryRepository(file), { time }, {})
        val song = ProgramItem.music(track(1))
        policy.onPlaying(song)
        time = 2_000L
        policy.onPlaying(song) // Resume, same occurrence.
        ProgramItemType.entries.filter { it != ProgramItemType.MUSIC }.forEach {
            policy.onPlaying(song.copy(id = it.name, type = it, contentId = it.name))
        }
        val saved = PlaybackHistoryRepository(file).readState()
        assertEquals(mapOf(song.contentId to 1_000L), saved.playedAt)
        assertEquals(song.contentId, saved.lastTrackId)
    }

    @Test fun leastRecentGroupWinsOverCachedRecentSongsAndRelaxationIsLogged() {
        val file = temporary.newFile()
        val tracks = (1..50).map(::track)
        PlaybackHistoryRepository(file).writeState(PlaybackHistoryState(
            tracks.associate { it.key to it.id.toLong() }, lastTrackId = track(50).key))
        val logs = mutableListOf<String>()
        val policy = MusicRepeatPolicy(PlaybackHistoryRepository(file), { 100L }, logs::add)
        val relaxed = policy.select(tracks)
        assertEquals((1..20).map { track(it).key }, relaxed.map { it.key })
        assertTrue(logs.any { it.contains("janela relaxada para 0 min") })
        assertFalse(relaxed.any { it.key == track(50).key })
    }

    @Test fun existingEngineReloadsDiskHistoryWhenStartingANewSession() = runBlocking {
        val file = temporary.newFile()
        val tracks = (1..77).map(::track)
        val radio = engine(file, tracks)
        PlaybackHistoryRepository(file).writeState(PlaybackHistoryState(
            mapOf(track(1).key to 1_000L), lastTrackId = track(1).key))
        radio.startSession()
        assertFalse(radio.nextBatch().any { it.contentId == track(1).key })
    }
}
