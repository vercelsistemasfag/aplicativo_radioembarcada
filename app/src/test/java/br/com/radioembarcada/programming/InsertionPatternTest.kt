package br.com.radioembarcada.programming

import br.com.radioembarcada.model.*
import br.com.radioembarcada.network.programming.RemoteProgrammingParser
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class InsertionPatternTest {
    private fun piece(id: String, type: ProgramItemType) = ProgramItem(id, type, id, "https://example.org/$id.mp3", 8_000)
    private fun configuration() = StationProgramming("alce", 3, ProgrammingRules(3, true),
        (1..4).map { piece("station-$it", ProgramItemType.STATION_ID) },
        (1..3).map { piece("jingle-$it", ProgramItemType.JINGLE) })
    private fun music(count: Int) = (1..count).map { piece("music-$it", ProgramItemType.MUSIC) }

    @Test fun fourStationsAndThreeJinglesHaveIndependentBagsWithoutBoundaryRepeats() {
        val sequencer = ProgrammingSequencer(random = Random(7))
        val items = sequencer.interleave(music(90), configuration())
        val stations = items.filter { it.type == ProgramItemType.STATION_ID }.map { it.contentId }
        val jingles = items.filter { it.type == ProgramItemType.JINGLE }.map { it.contentId }
        stations.chunked(4).forEach { assertEquals(4, it.toSet().size) }
        jingles.chunked(3).forEach { assertEquals(3, it.toSet().size) }
        assertTrue(stations.zipWithNext().all { (a,b) -> a != b })
        assertTrue(jingles.zipWithNext().all { (a,b) -> a != b })
        assertTrue(stations.chunked(4).distinct().size > 1)
        assertTrue(jingles.chunked(3).distinct().size > 1)
        assertTrue(items.zipWithNext().all { (a,b) -> a.type == ProgramItemType.MUSIC || b.type == ProgramItemType.MUSIC })
    }

    @Test fun pauseAndResumeDoNotResetTheInsertionCycleOrPendingBag() {
        val sequencer = ProgrammingSequencer(random = Random(10))
        val config = configuration()
        val first = sequencer.interleave(music(2), config)
        // Pause não monta nem descarta programação; continuar no mesmo motor.
        val rest = sequencer.interleave(music(4), config)
        val ids = (first + rest).filter { it.type != ProgramItemType.MUSIC }
        assertEquals(listOf(ProgramItemType.STATION_ID, ProgramItemType.STATION_ID, ProgramItemType.JINGLE,
            ProgramItemType.STATION_ID, ProgramItemType.STATION_ID, ProgramItemType.JINGLE), ids.map { it.type })
        assertEquals(4, ids.filter { it.type == ProgramItemType.STATION_ID }.map { it.contentId }.toSet().size)
    }

    @Test fun versionThreePublishedSchemaSupportsNewRulesAndBothCompletePools() {
        val parsed = RemoteProgrammingParser.parse(checkNotNull(javaClass.getResource("/programming-v3.json")).readText())
        assertEquals(4, parsed.stationIds.size)
        assertEquals(3, parsed.jingles.size)
        assertEquals(ProgrammingRules.DEFAULT_INSERTION_PATTERN, parsed.rules.insertionPattern)
        val output = ProgrammingSequencer(random = Random(3)).interleave(music(4), parsed)
        assertEquals(listOf(ProgramItemType.STATION_ID, ProgramItemType.STATION_ID,
            ProgramItemType.JINGLE, ProgramItemType.STATION_ID), output.filter { it.type != ProgramItemType.MUSIC }.map { it.type })
    }

    @Test fun refreshedPoolNeverSelectsRemovedItemsAndMetadataRefreshKeepsTheBag() {
        val bag = ShuffleBag(Random(4))
        val pool = configuration().stationIds
        val first = checkNotNull(bag.next(pool))
        val second = checkNotNull(bag.next(pool.map { it.copy(title = "Changed") }))
        assertNotEquals(first.contentId, second.contentId)
        repeat(10) { assertEquals(pool.last().contentId, bag.next(listOf(pool.last()))?.contentId) }
        assertNull(bag.next(emptyList()))
    }
}
