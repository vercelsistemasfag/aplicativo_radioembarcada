package br.com.radioembarcada.programming

import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.data.music.MusicProviderException
import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.model.Track
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class QueueBuilderTest {
    private fun track(id: Int, artist: Int = id % 8) = Track(id.toString(), "Track $id", "Artist $artist",
        artist.toString(), null, "https://example.org/$id.mp3", 180_000,
        "https://creativecommons.org/licenses/by/4.0/", "Fixture", "https://example.org/$id")
    private val builder = QueueBuilder(Random(42))

    @Test fun queueHasTwentyUniqueMusicProgramItemsAndNoConsecutiveArtist() {
        val catalog = (0..59).map(::track)
        val queue = builder.build(catalog + catalog)
        assertEquals(20, queue.size)
        assertEquals(20, queue.map { it.id }.toSet().size)
        assertTrue(queue.all { it.type == ProgramItemType.MUSIC })
        assertTrue(queue.zipWithNext().all { (a, b) -> a.artistKey != b.artistKey && a.id != b.id })
    }

    @Test fun queueBoundaryDoesNotRepeatThePreviousTrackOrArtist() {
        val previous = ProgramItem.music(track(1))
        val queue = builder.build((0..59).map(::track), previous = previous)
        assertNotEquals(previous.id, queue.first().id)
        assertNotEquals(previous.artistKey, queue.first().artistKey)
    }

    @Test fun smallSingleArtistCatalogNeverBreaksTheArtistRuleToFillTwentySlots() {
        assertEquals(1, builder.build((0..5).map { track(it, 1) }).size)
        assertTrue(builder.build(listOf(track(1)), previous = ProgramItem.music(track(1))).isEmpty())
    }

    @Test fun programCanRepresentFutureNonMusicContentWithoutATrackModel() {
        val jingle = ProgramItem("station-id", ProgramItemType.STATION_ID, "Identificação",
            "https://example.org/station-id.mp3", 4_000)
        val queue = builder.build((0..20).map(::track)) + jingle
        assertEquals(ProgramItemType.STATION_ID, queue.last().type)
        assertEquals(4_000L, queue.last().durationMs)
    }

    @Test fun programmingUsesProviderAndRefillsWithoutRepeatingPendingItems() = runBlocking {
        val calls = mutableListOf<Pair<Int, Int>>()
        val provider = object : MusicProvider {
            override suspend fun fetchTracks(limit: Int, offset: Int): List<Track> {
                calls += limit to offset
                return (0..99).map(::track)
            }
        }
        val programming = AutomaticProgramming(provider, builder)
        val first = programming.nextBatch()
        val second = programming.nextBatch(first.last(), first.map { it.id }.toSet())
        assertEquals(20, second.size)
        assertTrue(first.map { it.id }.toSet().intersect(second.map { it.id }.toSet()).isEmpty())
        assertNotEquals(first.last().artistKey, second.first().artistKey)
        assertEquals(listOf(30 to 0, 30 to 30), calls)
    }

    @Test(expected = MusicProviderException::class)
    fun emptyCatalogProducesAnActionableFailureInsteadOfAnEmptyPlayingQueue() = runBlocking {
        val provider = object : MusicProvider {
            override suspend fun fetchTracks(limit: Int, offset: Int) = emptyList<Track>()
        }
        AutomaticProgramming(provider).nextBatch()
        Unit
    }
}
