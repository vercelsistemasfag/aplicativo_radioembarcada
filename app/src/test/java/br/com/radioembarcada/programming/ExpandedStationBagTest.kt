package br.com.radioembarcada.programming

import br.com.radioembarcada.model.*
import br.com.radioembarcada.network.programming.RemoteProgrammingParser
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class ExpandedStationBagTest {
    private fun configuration() = RemoteProgrammingParser.parse(
        checkNotNull(javaClass.getResource("/programming-v4.json")).readText())
    private fun music(count: Int) = List(count) { ProgramItem("music-$it", ProgramItemType.MUSIC,
        "Music", "https://example.org/music-$it.mp3", 180_000) }

    @Test fun versionFourParsesEveryStationAndKeepsTheThreeJingles() {
        val config = configuration()
        assertEquals(4L, config.version)
        assertEquals(7, config.stationIds.size)
        assertEquals(3, config.jingles.size)
        assertEquals((1..7).map { "Vinheta Alce $it" }.toSet(), config.stationIds.map { it.title }.toSet())
        assertTrue(config.stationIds.all { it.type == ProgramItemType.STATION_ID })
    }

    @Test fun eachBagUsesAllSevenExactlyOnceAndAllThreeJinglesIndependently() {
        val config = configuration()
        val items = ProgrammingSequencer(random = Random(42)).interleave(music(126), config)
        val stations = items.filter { it.type == ProgramItemType.STATION_ID }.map { it.contentId }
        val jingles = items.filter { it.type == ProgramItemType.JINGLE }.map { it.contentId }
        stations.chunked(7).forEach { assertEquals(config.stationIds.map { it.contentId }.toSet(), it.toSet()) }
        jingles.chunked(3).forEach { assertEquals(config.jingles.map { it.contentId }.toSet(), it.toSet()) }
        assertTrue(stations.zipWithNext().all { (a,b) -> a != b })
        assertTrue(jingles.zipWithNext().all { (a,b) -> a != b })
        assertTrue(stations.chunked(7).distinct().size > 1)
        items.chunked(2).forEachIndexed { index, pair ->
            assertEquals(ProgramItemType.MUSIC, pair[0].type)
            assertEquals(ProgrammingRules.DEFAULT_INSERTION_PATTERN[index % 3], pair[1].type)
        }
    }

    @Test fun fourToSevenRefreshRebuildsImmediatelyWithoutResettingPatternOrRepeatingLastStation() {
        val config = configuration()
        val old = config.copy(version = 3, stationIds = config.stationIds.take(4))
        val sequencer = ProgrammingSequencer(random = Random(16))
        val previous = sequencer.interleave(music(1), old).last()
        val changed = sequencer.interleave(music(11), config)
        val stations = changed.filter { it.type == ProgramItemType.STATION_ID }.map { it.contentId }
        assertNotEquals(previous.contentId, stations.first())
        assertEquals(config.stationIds.map { it.contentId }.toSet(), stations.take(7).toSet())
        assertEquals(ProgramItemType.STATION_ID, changed[1].type) // posição 2, não reiniciar
        assertEquals(ProgramItemType.JINGLE, changed[3].type) // posição 3 substitui a vinheta
    }

    @Test fun versionChangeEvenWithSameIdsDiscardsPendingBagButKeepsCycle() {
        val config = configuration()
        val logs = mutableListOf<String>()
        val sequencer = ProgrammingSequencer(logs::add, Random(17))
        sequencer.interleave(music(1), config)
        sequencer.interleave(music(1), config.copy(version = 5))
        assertEquals(2, logs.count { "StationIdBag remaining: 6" in it })
        assertTrue(logs.any { "Programming config version: 5" in it })
        assertEquals(ProgramItemType.JINGLE, sequencer.interleave(music(1), config.copy(version = 5)).last().type)
    }

    @Test fun unchangedConfigurationAndPauseKeepPendingBagAndCycle() {
        val config = configuration()
        val sequencer = ProgrammingSequencer(random = Random(23))
        val items = (1..11).flatMap { sequencer.interleave(music(1), config) }
        assertEquals(config.stationIds.map { it.contentId }.toSet(),
            items.filter { it.type == ProgramItemType.STATION_ID }.take(7).map { it.contentId }.toSet())
        items.chunked(2).forEachIndexed { i, pair ->
            assertEquals(ProgrammingRules.DEFAULT_INSERTION_PATTERN[i % 3], pair.last().type)
        }
    }

    @Test fun futurePoolsHaveNoSevenItemLimitAndDiagnosticsShowRealRemainingCount() {
        val config = configuration()
        val expanded = config.copy(version = 6, stationIds = List(12) { index -> config.stationIds.first()
            .copy(id = "station-$index", contentId = "station-$index") })
        val logs = mutableListOf<String>()
        val items = ProgrammingSequencer(logs::add, Random(9)).interleave(music(18), expanded)
        assertEquals(12, items.filter { it.type == ProgramItemType.STATION_ID }.map { it.contentId }.toSet().size)
        assertTrue(logs.any { "Station IDs loaded: 12" in it })
        assertTrue(logs.any { "StationIdBag remaining: 11" in it })
    }
}
