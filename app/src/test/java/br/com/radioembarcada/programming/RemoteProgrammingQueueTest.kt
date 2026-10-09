package br.com.radioembarcada.programming

import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.data.programming.ProgrammingProvider
import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.model.Track
import br.com.radioembarcada.network.programming.RemoteProgrammingParser
import br.com.radioembarcada.network.programming.RemoteProgrammingProvider
import br.com.radioembarcada.storage.SavedProgrammingConfiguration
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemoteProgrammingQueueTest {
    private fun configuration(interval: Int = 3, stationIds: Boolean = true, jingles: Boolean = true) =
        RemoteProgrammingParser.parse(JSONObject(checkNotNull(javaClass.getResource("/programming.json")).readText()).apply {
            getJSONObject("rules").put("songsBetweenInsertions", interval)
            if (!stationIds) put("stationIds", JSONArray())
            if (!jingles) put("jingles", JSONArray())
        }.toString())

    private fun musicProvider(count: Int = 77) = object : MusicProvider {
        override val providesCompleteCatalog = true
        override suspend fun fetchTracks(limit: Int, offset: Int): List<Track> = (1..count).map {
            Track("$it", "Music $it", "Artist $it", "$it", null, "https://example.org/$it.mp3",
                180_000, "", "Fixture", "")
        }
    }

    @Test fun threeSongsThenStationIdThenThreeSongsThenJingleAndAlternationAcrossBatches() = runBlocking {
        val engine = AutomaticProgramming(musicProvider(), QueueBuilder(Random(1)), ProgrammingProvider { configuration() })
        val first = engine.nextBatch()
        val second = engine.nextBatch(first.last(), first.takeLast(4).map { it.id }.toSet())
        val all = first + second
        assertEquals(listOf(ProgramItemType.MUSIC, ProgramItemType.MUSIC, ProgramItemType.MUSIC,
            ProgramItemType.STATION_ID, ProgramItemType.MUSIC, ProgramItemType.MUSIC, ProgramItemType.MUSIC,
            ProgramItemType.JINGLE, ProgramItemType.MUSIC, ProgramItemType.MUSIC, ProgramItemType.MUSIC,
            ProgramItemType.STATION_ID), all.take(12).map { it.type })
        val insertions = all.filter { it.type != ProgramItemType.MUSIC }
        assertTrue(insertions.zipWithNext().all { (a, b) -> a.type != b.type })
        assertTrue(all.chunked(4).dropLast(1).all { it.take(3).all { i -> i.type == ProgramItemType.MUSIC } })
        assertEquals(40, all.count { it.type == ProgramItemType.MUSIC })
        assertEquals(all.size, all.map { it.id }.distinct().size)
        assertEquals(2, insertions.map { it.contentId }.distinct().size)
    }

    @Test fun jsonIntervalIsUsedInsteadOfAHardcodedThreeAndCanChangeBetweenBatches() = runBlocking {
        var config = configuration(interval = 5)
        val engine = AutomaticProgramming(musicProvider(), programmingProvider = ProgrammingProvider { config })
        val first = engine.nextBatch()
        assertEquals(5, first.indexOfFirst { it.type != ProgramItemType.MUSIC })
        assertEquals(4, first.count { it.type != ProgramItemType.MUSIC })
        config = configuration(interval = 2).copy(version = 2)
        val second = engine.nextBatch(first.last(), first.takeLast(3).map { it.id }.toSet())
        assertEquals(2, second.indexOfFirst { it.type != ProgramItemType.MUSIC })
        assertEquals(10, second.count { it.type != ProgramItemType.MUSIC })
    }

    @Test fun noStationIdsStillAllowsJinglesSeparatedByMusic() = runBlocking {
        val engine = AutomaticProgramming(musicProvider(), programmingProvider = ProgrammingProvider { configuration(stationIds = false) })
        val batch = engine.nextBatch()
        assertEquals(6, batch.count { it.type == ProgramItemType.JINGLE })
        assertTrue(batch.none { it.type == ProgramItemType.STATION_ID })
        assertNoConsecutivePieces(batch)
    }

    @Test fun noJinglesStillAllowsStationIdsSeparatedByMusic() = runBlocking {
        val engine = AutomaticProgramming(musicProvider(), programmingProvider = ProgrammingProvider { configuration(jingles = false) })
        val batch = engine.nextBatch()
        assertEquals(6, batch.count { it.type == ProgramItemType.STATION_ID })
        assertTrue(batch.none { it.type == ProgramItemType.JINGLE })
        assertNoConsecutivePieces(batch)
    }

    @Test fun bothPoolsAbsentMeansOnlyMusic() = runBlocking {
        val engine = AutomaticProgramming(musicProvider(), programmingProvider = ProgrammingProvider {
            configuration(stationIds = false, jingles = false)
        })
        assertTrue(engine.nextBatch().all { it.type == ProgramItemType.MUSIC })
    }

    @Test fun failedProgrammingJsonNeverBlocksMusicalRefillAndCanRecoverLater() = runBlocking {
        var fail = true
        val saved = object : SavedProgrammingConfiguration {
            override fun read(): String? = null
            override fun write(json: String) {}
        }
        var time = 0L
        val remote = RemoteProgrammingProvider("alce", saved, now = { time }, download = {
            if (fail) throw IOException()
            checkNotNull(javaClass.getResource("/programming.json")).readText()
        })
        val engine = AutomaticProgramming(musicProvider(), programmingProvider = remote)
        val first = engine.nextBatch()
        assertEquals(20, first.size)
        assertTrue(first.all { it.type == ProgramItemType.MUSIC })
        fail = false
        time += 30_000
        val next = engine.nextBatch(first.last())
        assertEquals(20, next.count { it.type == ProgramItemType.MUSIC })
        assertTrue(next.any { it.type == ProgramItemType.STATION_ID })
    }

    @Test fun unexpectedProgrammingProviderFailureAlsoKeepsMusicPlaying() = runBlocking {
        val engine = AutomaticProgramming(musicProvider(), programmingProvider = ProgrammingProvider { throw IOException() })
        assertEquals(20, engine.nextBatch().size)
    }

    @Test fun intervalOneNeverCreatesAdjacentPiecesEvenAcrossCyclesAndWithoutAlternation() = runBlocking {
        val config = configuration(interval = 1).let {
            it.copy(rules = it.rules.copy(alternateStationIdAndJingle = false))
        }
        val engine = AutomaticProgramming(musicProvider(7), programmingProvider = ProgrammingProvider { config })
        val all = mutableListOf<ProgramItem>()
        repeat(6) { all += engine.nextBatch(all.lastOrNull()) }
        assertNoConsecutivePieces(all)
        assertEquals(42, all.count { it.type == ProgramItemType.MUSIC })
        assertEquals(42, all.count { it.type != ProgramItemType.MUSIC })
        assertEquals(all.size - 42 + 7, all.map { it.id }.distinct().size)
        val songs = all.filter { it.type == ProgramItemType.MUSIC }
        assertTrue(songs.zipWithNext().all { (a, b) -> a.id != b.id })
    }

    @Test fun diagnosticsIncludeBlockCountersAndSelectedInsertion() = runBlocking {
        val logs = mutableListOf<String>()
        val engine = AutomaticProgramming(musicProvider(), programmingProvider = ProgrammingProvider { configuration() },
            diagnostic = logs::add)
        engine.nextBatch()
        listOf("música 1/3", "música 2/3", "música 3/3", "Inserção selecionada: STATION_ID",
            "Inserção selecionada: JINGLE").forEach { expected -> assertTrue(logs.any { expected in it }) }
    }

    @Test fun newPlaybackSessionStartsAFullMusicBlockInsteadOfUsingDiscardedQueuedItems() = runBlocking {
        val engine = AutomaticProgramming(musicProvider(), programmingProvider = ProgrammingProvider { configuration() })
        engine.nextBatch() // 20 músicas agendadas: o lote termina com contagem 2/3.
        engine.startSession() // Serviço anterior liberado; a fila será construída novamente.
        val restarted = engine.nextBatch()
        assertEquals(3, restarted.indexOfFirst { it.type != ProgramItemType.MUSIC })
        assertEquals(ProgramItemType.STATION_ID, restarted[3].type)
    }

    private fun assertNoConsecutivePieces(items: List<ProgramItem>) {
        assertTrue(items.zipWithNext().all { (a, b) ->
            a.type == ProgramItemType.MUSIC || b.type == ProgramItemType.MUSIC
        })
    }
}
