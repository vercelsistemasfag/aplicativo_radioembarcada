package br.com.radioembarcada.programming

import br.com.radioembarcada.model.*
import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.data.programming.ProgrammingProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NewsQueuePlannerTest {
    private fun piece(id: String, type: ProgramItemType) = ProgramItem(id, type, id, "https://example.org/$id.mp3", 60_000)
    private val block = listOf(piece("intro", ProgramItemType.NEWS_INTRO), piece("news", ProgramItemType.NEWS_DROP))
    private fun config() = StationProgramming("alce", 4, ProgrammingRules(3, true),
        (1..7).map { piece("station$it", ProgramItemType.STATION_ID) },
        (1..3).map { piece("jingle$it", ProgramItemType.JINGLE) })
    private fun normal(count: Int) = ProgrammingSequencer().interleave(
        (1..count).map { piece("music$it", ProgramItemType.MUSIC) }, config())
    private val normalTypes = setOf(ProgramItemType.STATION_ID, ProgramItemType.JINGLE)

    @Test fun newsReplacesExactlyOneNormalIntervalAndKeepsBothBagsOrder() {
        val original = normal(12)
        val plan = NewsQueuePlanner.insert(original.drop(1), block)
        assertEquals(listOf(ProgramItemType.NEWS_INTRO, ProgramItemType.NEWS_DROP, ProgramItemType.MUSIC), plan.upcoming.take(3).map { it.type })
        val originals = original.filter { it.type in normalTypes }
        assertEquals(originals, plan.upcoming.filter { it.type in normalTypes } + plan.deferred)
        assertEquals(originals.first(), plan.upcoming[3]) // primeira STATION_ID continua sendo a primeira normal
    }
    @Test fun newsHasPriorityOverJingleButJingleRemainsNextNormalInsertion() {
        val original = normal(9)
        val firstJingle = original.indexOfFirst { it.type == ProgramItemType.JINGLE }
        val plan = NewsQueuePlanner.insert(original.drop(firstJingle), block)
        assertEquals(ProgramItemType.NEWS_INTRO, plan.upcoming[0].type)
        assertEquals(ProgramItemType.NEWS_DROP, plan.upcoming[1].type)
        assertEquals(ProgramItemType.MUSIC, plan.upcoming[2].type)
        assertEquals(original[firstJingle], plan.upcoming[3])
    }
    @Test fun boundaryAfterAnInstitutionalPieceDoesNotShiftOrDuplicateTheNormalCycle() {
        val tail = normal(4).drop(2) // a peça anterior já terminou; próxima MUSIC está preservada
        val plan = NewsQueuePlanner.insert(tail, block)
        assertEquals(block + tail, plan.upcoming)
        assertTrue(plan.deferred.isEmpty())
    }
    @Test fun repeatedEditorialBlocksAndRefillRetainEveryReservedNormalInsertion() {
        val original = normal(12)
        val first = NewsQueuePlanner.insert(original.drop(1), block)
        val currentMusic = first.upcoming.indexOfFirst { it.type == ProgramItemType.MUSIC }
        val second = NewsQueuePlanner.insert(first.upcoming.drop(currentMusic + 1), block.map { it.copy(id = it.id + "2") })
        val held = ArrayDeque(second.deferred + first.deferred)
        val refill = normal(6).map { it.copy(id = it.id + "refill") }
        val restored = NewsQueuePlanner.restore(refill, held)
        val actual = second.upcoming.filter { it.type in normalTypes } + restored.filter { it.type in normalTypes } + held
        assertEquals(original.filter { it.type in normalTypes } + refill.filter { it.type in normalTypes }, actual)
    }
    @Test fun actualProgrammingRefillAndCancelledReservationPreserveInsertionPosition() = runBlocking {
        val provider = object : MusicProvider {
            override val providesCompleteCatalog = true
            override suspend fun fetchTracks(limit: Int, offset: Int) = (1..77).map {
                Track("music$it", "Music $it", "", "unknown:$it", null, "https://example.org/music$it.mp3", 180_000, "", "Fixture", "")
            }
        }
        val engine = AutomaticProgramming(provider, programmingProvider = ProgrammingProvider { config() })
        val first = engine.nextBatch()
        engine.insertNews(first.drop(1), block)
        assertEquals(first.drop(1), engine.cancelPendingNews())
        val planned = engine.insertNews(first.drop(1), block)
        engine.newsEntered()
        val refill = engine.nextBatch(first.last(), first.map { it.id }.toSet())
        val types = (planned + refill).filter { it.type in normalTypes }.map { it.type }
        assertEquals(List(types.size) { ProgrammingRules.DEFAULT_INSERTION_PATTERN[it % 3] }, types)
        assertEquals(first.last().id, refill.first { it.type in normalTypes }.id)
    }

    @Test fun deferredInsertionIsDrainedWithoutSelectingAnExtraPieceOrConsumingCycleAgain() {
        val sequencer = ProgrammingSequencer()
        val original = sequencer.interleave((1..2).map { piece("m$it", ProgramItemType.MUSIC) }, config())
        val retained = original.last() // segunda STATION_ID já reservada, adiada pelo bloco
        val refill = sequencer.interleave((3..5).map { piece("m$it", ProgramItemType.MUSIC) }, config(), listOf(retained))
        val shorts = refill.filter { it.type in normalTypes }
        assertEquals(retained.id, shorts.first().id)
        assertEquals(listOf(ProgramItemType.STATION_ID, ProgramItemType.JINGLE, ProgramItemType.STATION_ID), shorts.map { it.type })
        val next = sequencer.interleave(listOf(piece("m6", ProgramItemType.MUSIC)), config())
        assertEquals(ProgramItemType.STATION_ID, next.last().type)
    }
}
