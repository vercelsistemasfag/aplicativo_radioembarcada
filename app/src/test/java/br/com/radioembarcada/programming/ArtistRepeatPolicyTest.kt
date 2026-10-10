package br.com.radioembarcada.programming

import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.model.Track
import br.com.radioembarcada.storage.FileMusicHistory
import br.com.radioembarcada.storage.MemoryMusicHistory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ArtistRepeatPolicyTest {
    private fun track(id: Int, artist: String) = Track("$id", "Track $id", artist,
        artist.lowercase().ifBlank { "unknown:$id" }, null, "https://example.org/$id.mp3",
        180_000, "", "Fixture", "")

    @Test fun differentSongBySameArtistIsBlockedUntilExactlyNinetyMinutes() {
        var time = 0L
        val policy = MusicRepeatPolicy(MemoryMusicHistory(), { time }, {})
        policy.onPlaying(ProgramItem.music(track(1, "Artist A")))
        val choices = listOf(track(2, " artist a "), track(3, "Artist B"))
        time = ProgrammingConfiguration.ARTIST_REPEAT_INTERVAL_MS - 1
        assertEquals(listOf(choices[1]), policy.selectArtists(choices))
        time++
        assertEquals(choices, policy.selectArtists(choices))
    }

    @Test fun batchReservationsPreventRepeatedArtistWhileAnyOtherArtistRemains() = runBlocking {
        val tracks = (1..6).map { track(it, "Artist A") } + (7..12).map { track(it, "Artist B") } + track(13, "Artist C")
        val provider = object : MusicProvider {
            override val providesCompleteCatalog = true
            override suspend fun fetchTracks(limit: Int, offset: Int) = tracks
        }
        val engine = AutomaticProgramming(provider, now = { 0L })
        val queue = engine.nextBatch()
        assertEquals(13, queue.size) // Exhaustion must not stop playback.
        assertEquals(3, queue.take(3).map { it.artist }.distinct().size)
        assertEquals(13, queue.map { it.contentId }.distinct().size)
    }

    @Test fun exhaustedArtistsUseLeastRecentArtistWithoutBlockingSingleArtistCatalogue() {
        var time = 1L
        val policy = MusicRepeatPolicy(MemoryMusicHistory(), { time }, {})
        policy.onPlaying(ProgramItem.music(track(1, "A")))
        time++
        policy.onPlaying(ProgramItem.music(track(2, "B")))
        assertEquals(listOf(track(3, "A")), policy.selectArtists(listOf(track(3, "A"), track(4, "B"))))
        assertEquals(listOf(track(4, "B")), policy.selectArtists(listOf(track(4, "B"))))
    }

    @Test fun unknownArtistsDoNotShareAnArtificialRestriction() {
        val policy = MusicRepeatPolicy(MemoryMusicHistory(), { 0L }, {})
        policy.scheduled(listOf(ProgramItem.music(track(1, ""))))
        assertEquals(listOf(track(2, "")), policy.selectArtists(listOf(track(2, ""))))
    }

    @Test fun actualArtistPlaybackPersistsAcrossRestartButReservationsDoNot() {
        val directory = kotlin.io.path.createTempDirectory().toFile()
        try {
            val file = File(directory, "history.json")
            val policy = MusicRepeatPolicy(FileMusicHistory(file), { 100L }, {})
            policy.scheduled(listOf(ProgramItem.music(track(1, "A"))))
            assertTrue(FileMusicHistory(file).readArtists().isEmpty())
            policy.onPlaying(ProgramItem.music(track(2, "B")))
            val restored = MusicRepeatPolicy(FileMusicHistory(file), { 200L }, {})
            assertEquals(listOf(track(3, "A")), restored.selectArtists(listOf(track(3, "A"), track(4, "B"))))
            assertEquals(mapOf("Fixture:2" to 100L), FileMusicHistory(file).read())
            assertEquals(mapOf("b" to 100L), FileMusicHistory(file).readArtists())
        } finally { directory.deleteRecursively() }
    }

    @Test fun oldHistoryLoadsAndPauseNewsAndInsertsDoNotResetArtistClock() {
        var time = 0L
        val history = MemoryMusicHistory()
        history.write(mapOf("Fixture:9" to 0L))
        val policy = MusicRepeatPolicy(history, { time }, {})
        val music = ProgramItem.music(track(1, "A"))
        policy.onPlaying(music)
        time = 50L
        policy.onPlaying(music)
        ProgramItemType.entries.filter { it != ProgramItemType.MUSIC }.forEach {
            policy.onPlaying(music.copy(id = it.name, type = it, artist = "A"))
        }
        assertEquals(mapOf("a" to 0L), history.readArtists())
        time = ProgrammingConfiguration.ARTIST_REPEAT_INTERVAL_MS
        assertEquals(listOf(track(2, "A")), policy.selectArtists(listOf(track(2, "A"))))
    }

    @Test fun artistExpiryDoesNotBypassSixHourSongRestriction() {
        var time = 0L
        val policy = MusicRepeatPolicy(MemoryMusicHistory(), { time }, {})
        policy.onPlaying(ProgramItem.music(track(1, "A")))
        time = ProgrammingConfiguration.ARTIST_REPEAT_INTERVAL_MS
        val candidates = policy.select(listOf(track(1, "A"), track(2, "A"), track(3, "B")))
        assertFalse(candidates.any { it.id == "1" })
        assertEquals(2, policy.selectArtists(candidates).size)
    }
    @Test fun eligibleOtherArtistIsPreferredOverUnheardSongByRecentlyPlayedArtist() {
        var time = 0L
        val policy = MusicRepeatPolicy(MemoryMusicHistory(), { time }, {})
        policy.onPlaying(ProgramItem.music(track(1, "B")))
        time = ProgrammingConfiguration.MUSIC_REPEAT_INTERVAL_MS
        policy.onPlaying(ProgramItem.music(track(2, "A")))
        val pool = policy.select(listOf(track(1, "B"), track(3, "A")))
        assertEquals(listOf(track(1, "B")), policy.selectArtists(pool))
    }

}
