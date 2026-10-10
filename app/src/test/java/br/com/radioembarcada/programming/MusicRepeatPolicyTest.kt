package br.com.radioembarcada.programming

import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.model.*
import br.com.radioembarcada.storage.MemoryMusicHistory
import br.com.radioembarcada.storage.FileMusicHistory
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MusicRepeatPolicyTest {
    private fun track(id: Int) = Track("$id", "Track $id", "", "unknown:$id", null,
        "https://example.org/$id.mp3", 180_000, "", "Fixture", "")
    private fun provider(count: Int = 77) = object : MusicProvider {
        override val providesCompleteCatalog = true
        override suspend fun fetchTracks(limit: Int, offset: Int) = (1..count).map(::track)
    }
    @Test fun seventySevenSongsAreDiscoveredBeforeRepeatingEvenAcrossMetadataRevisions() = runBlocking {
        var titleRevision = 0
        val changing = object : MusicProvider {
            override val providesCompleteCatalog = true
            override val catalogRevision get() = (++titleRevision).toLong()
            override suspend fun fetchTracks(limit: Int, offset: Int) = (1..77).map { track(it).copy(title = "Revision $titleRevision") }
        }
        val engine = AutomaticProgramming(changing, now = { 0L })
        val all = buildList { repeat(4) { addAll(engine.nextBatch()) } }
        assertEquals(77, all.size)
        assertEquals(77, all.map { it.contentId }.distinct().size)
    }
    @Test fun recentlyPlayedSongIsBlockedWhileOtherSongsRemainAndUnlocksAtSixHours() {
        var time = 0L
        val history = MemoryMusicHistory()
        val policy = MusicRepeatPolicy(history, { time }, {})
        policy.onPlaying(ProgramItem.music(track(1)))
        assertEquals(listOf(track(2)), policy.select(listOf(track(1), track(2))))
        time = ProgrammingConfiguration.MUSIC_REPEAT_INTERVAL_MS - 1
        policy.onPlaying(ProgramItem.music(track(2)))
        assertEquals(track(1), policy.select(listOf(track(1), track(2))).first()) // both exhausted: oldest exception
        time++
        assertEquals(listOf(track(1)), policy.select(listOf(track(1), track(2)))) // first track now eligible normally
    }
    @Test fun smallCatalogueExceptionRotatesOldestTracksWithoutImmediateBoundaryRepeat() = runBlocking {
        val engine = AutomaticProgramming(provider(3), now = { 0L })
        val first = engine.nextBatch()
        first.forEach(engine::onPlaying)
        val second = engine.nextBatch(first.last())
        assertEquals(3, second.size)
        assertNotEquals(first.last().id, second.first().id)
        assertEquals(first.map { it.id }.toSet(), second.map { it.id }.toSet())
    }
    @Test fun restartRetainsPlayedHistoryButDoesNotTreatPrefetchAsPlayed() = runBlocking {
        val history = MemoryMusicHistory()
        val engine = AutomaticProgramming(provider(), history = history, now = { 1_000L })
        val queued = engine.nextBatch()
        assertTrue(history.read().isEmpty())
        engine.onPlaying(queued.first())
        val restarted = AutomaticProgramming(provider(), history = history, now = { 2_000L })
        val next = restarted.nextBatch()
        assertFalse(next.any { it.contentId == queued.first().contentId })
        assertEquals(1, history.read().size)
    }
    @Test fun newsAndStationPiecesDoNotEnterMusicHistoryAndPauseDoesNotResetTimestamp() {
        var time = 1_000L
        val history = MemoryMusicHistory()
        val policy = MusicRepeatPolicy(history, { time }, {})
        val music = ProgramItem.music(track(1))
        policy.onPlaying(music)
        time = 2_000L
        policy.onPlaying(music)
        assertEquals(1_000L, history.read()[music.contentId])
        listOf(ProgramItemType.STATION_ID, ProgramItemType.JINGLE, ProgramItemType.NEWS_INTRO, ProgramItemType.NEWS_DROP).forEach {
            policy.onPlaying(music.copy(id = it.name, type = it, contentId = it.name))
        }
        assertEquals(1, history.read().size)
    }
    @Test fun historyPersistsOnDiskAndMalformedFileDoesNotStopProgramming() {
        val directory = kotlin.io.path.createTempDirectory().toFile()
        try {
            val file = File(directory, "history.json")
            FileMusicHistory(file).write(mapOf("Fixture:1" to 123L))
            assertEquals(mapOf("Fixture:1" to 123L), FileMusicHistory(file).read())
            file.writeText("invalid")
            val policy = MusicRepeatPolicy(FileMusicHistory(file), { 0L }, {})
            assertEquals(listOf(track(1)), policy.select(listOf(track(1))))
        } finally { directory.deleteRecursively() }
    }
    @Test fun queuedSongsAreNeverReaddedAndFreshPoolIsNotFilledWithRecentRepeats() = runBlocking {
        val engine = AutomaticProgramming(provider(23), now = { 0L })
        val first = engine.nextBatch()
        val next = engine.nextBatch(first.last(), first.map { it.id }.toSet())
        assertEquals(3, next.size)
        assertTrue(next.none { it.id in first.map { item -> item.id } })
    }
}
