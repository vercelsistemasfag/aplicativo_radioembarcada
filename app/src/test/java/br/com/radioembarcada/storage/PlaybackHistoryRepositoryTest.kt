package br.com.radioembarcada.storage

import br.com.radioembarcada.programming.ProgrammingConfiguration
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlaybackHistoryRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun completeCheckpointSurvivesNewRepositoryAndOrphanFromKilledProcess() {
        val file = temporary.newFolder().resolve("music_history_alce.json")
        val state = PlaybackHistoryState(mapOf("song" to 123L), mapOf("artist" to 123L),
            "song", listOf("a", "b", "c", "d", "e"))
        PlaybackHistoryRepository(file).writeState(state)
        file.resolveSibling(file.name + ".tmp").writeText("partial write before process death")
        assertEquals(state, PlaybackHistoryRepository(file).readState())
        PlaybackHistoryRepository(file).writeState(state.copy(lastTrackId = "other"))
        assertFalse(file.resolveSibling(file.name + ".tmp").exists())
        assertEquals("other", PlaybackHistoryRepository(file).readState().lastTrackId)
    }

    @Test fun oldTimestampFormatMigratesWithoutErasingPlayedSongsOrArtists() {
        val file = temporary.newFile()
        file.writeText("""{"playedAt":{"old":10,"last":20},"artistsPlayedAt":{"artist":20}}""")
        val store = PlaybackHistoryRepository(file)
        val migrated = store.readState()
        assertEquals(mapOf("old" to 10L, "last" to 20L), migrated.playedAt)
        assertEquals("last", migrated.lastTrackId)
        assertEquals(mapOf("artist" to 20L), migrated.artistsPlayedAt)
        store.writeState(migrated.copy(lastSessionOpeningSequence = listOf("x", "y")))
        assertEquals(migrated.playedAt, PlaybackHistoryRepository(file).readState().playedAt)
    }

    @Test fun storageIsBoundedAndNeverStoresTheFullQueue() {
        val file = temporary.newFile()
        val entries = (1..600).associate { "track$it" to it.toLong() }
        PlaybackHistoryRepository(file).writeState(PlaybackHistoryState(entries, entries, "track600", entries.keys.toList()))
        val state = PlaybackHistoryRepository(file).readState()
        assertEquals(ProgrammingConfiguration.MUSIC_HISTORY_LIMIT, state.playedAt.size)
        assertEquals(ProgrammingConfiguration.MUSIC_HISTORY_LIMIT, state.artistsPlayedAt.size)
        assertEquals(5, state.lastSessionOpeningSequence.size)
        assertFalse(state.playedAt.containsKey("track1"))
        assertEquals(600L, state.playedAt["track600"])
    }
}
