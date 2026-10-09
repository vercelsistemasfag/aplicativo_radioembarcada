package br.com.radioembarcada.news

import br.com.radioembarcada.data.news.NewsProvider
import br.com.radioembarcada.model.NewsItem
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.storage.*
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NewsProgrammingTest {
    @get:Rule val temporary = TemporaryFolder()
    private class MemoryHistory(var state: NewsHistoryState = NewsHistoryState()) : NewsHistory {
        override fun read() = state
        override fun write(state: NewsHistoryState) { this.state = state }
    }
    private val time = 100_000_000L
    private fun item(id: String = "drop") = NewsItem(id, "Notícia", time - 60_000,
        "https://agenciabrasil.ebc.com.br/article", "https://audios.ebc.com.br/$id.mp3", 60_000)
    private fun dueHistory() = MemoryHistory(NewsHistoryState(activeElapsedMs = NewsConfiguration.NEWS_INTERVAL_MS))

    @Test fun dueBlockIsIndivisibleAndDoesNotRecordAnUnplayedReservation() = runBlocking {
        val history = dueHistory()
        val editor = NewsProgramming(NewsProvider { item() }, history, now = { time })
        editor.prefetch()
        val block = checkNotNull(editor.reserve(10_000))
        assertEquals(listOf(ProgramItemType.NEWS_INTRO, ProgramItemType.NEWS_DROP), block.map { it.type })
        assertEquals("Radioagência Nacional", block[1].artist)
        assertTrue(history.state.playedNewsIds.isEmpty())
        assertNull(editor.reserve(10_000))
    }
    @Test fun unavailableOrMissingAudioNeverProducesIntroAlone() = runBlocking {
        for (provider in listOf(NewsProvider { null }, NewsProvider { item().copy(audioUrl = null) },
            NewsProvider { throw IOException("RSS failure") })) {
            val editor = NewsProgramming(provider, dueHistory(), now = { time })
            editor.prefetch()
            assertNull(editor.reserve(10_000))
            assertFalse(editor.hasReservation)
        }
    }
    @Test fun historyPreventsImmediateRepeatAndSurvivesRestart() = runBlocking {
        val history = dueHistory()
        val seen = mutableListOf<Set<String>>()
        val provider = NewsProvider { excluded ->
            seen += excluded
            listOf(item(), item("second")).firstOrNull { it.id !in excluded }
        }
        val editor = NewsProgramming(provider, history, now = { time })
        editor.prefetch(); val block = checkNotNull(editor.reserve(10_000))
        editor.onPlaying(block[0]); assertTrue(history.state.playedNewsIds.isEmpty())
        editor.onPlaying(block[1]); editor.onPlaying(block[1])
        assertEquals(listOf("drop"), history.state.playedNewsIds)
        assertEquals(time, history.state.lastNewsPlayedAt)
        assertFalse(editor.scheduler.newsDue)
        val restored = NewsProgramming(provider, history, now = { time })
        restored.prefetch()
        assertEquals(setOf("drop"), seen.last())
        assertNull(restored.reserve(10_000)) // reinício não dispara notícia imediatamente
        restored.tick(0, true); restored.tick(1_800_000, true)
        assertEquals("news:second", restored.reserve(10_000)?.get(1)?.contentId)
    }
    @Test fun pauseRetainsReservationAndActiveClockAcrossPersistence() = runBlocking {
        val history = dueHistory()
        val editor = NewsProgramming(NewsProvider { item() }, history, now = { time })
        editor.prefetch(); val block = checkNotNull(editor.reserve(10_000))
        editor.tick(0, false); editor.tick(600_000, false)
        assertTrue(editor.hasReservation)
        assertNull(editor.reserve(10_000))
        editor.onPlaying(block[1]); editor.tick(600_000, true); editor.tick(900_000, false); editor.persist()
        assertEquals(300_000L, NewsProgramming(NewsProvider { null }, history, now = { time }).scheduler.elapsedMs)
    }
    @Test fun failedPreparationDefersAttemptAndKeepsUnplayedHistoryUntouched() = runBlocking {
        val history = dueHistory()
        val editor = NewsProgramming(NewsProvider { item() }, history, now = { time })
        editor.prefetch(); assertNotNull(editor.reserve(10_000)); editor.cancel()
        assertFalse(editor.hasReservation)
        assertTrue(history.state.playedNewsIds.isEmpty())
        editor.prefetch(); assertNull(editor.reserve(10_000))
    }
    @Test fun privateJsonHistoryIsAtomicBoundedAndRetainsTimestampAndElapsedTime() {
        val file = temporary.newFolder().resolve("history.json")
        val store = FileNewsHistory(file)
        store.write(NewsHistoryState(time, (1..120).map { "news$it" }, 900_000))
        val restored = FileNewsHistory(file).read()
        assertEquals(time, restored.lastNewsPlayedAt)
        assertEquals(900_000L, restored.activeElapsedMs)
        assertEquals(100, restored.playedNewsIds.size)
        assertEquals("news21", restored.playedNewsIds.first())
        assertFalse(file.resolveSibling("history.json.tmp").exists())
    }
    @Test fun sourceUnavailableDuringPendingBlockCanCancelWithoutChangingAudio() = runBlocking {
        var now = time
        val editor = NewsProgramming(NewsProvider { item() }, dueHistory(), now = { now })
        editor.prefetch(); editor.reserve(10_000)
        now += 86_400_000
        assertFalse(editor.reservationStillEligible())
        editor.cancel(); assertFalse(editor.hasReservation)
    }
}
