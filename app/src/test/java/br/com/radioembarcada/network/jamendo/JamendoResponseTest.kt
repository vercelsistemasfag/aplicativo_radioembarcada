package br.com.radioembarcada.network.jamendo

import br.com.radioembarcada.data.music.MusicProviderException
import org.junit.Assert.*
import org.junit.Test

class JamendoResponseTest {
    private fun fixture() = checkNotNull(javaClass.getResource("/jamendo-tracks.json")).readText()

    @Test fun responseIsConvertedToIndependentModelWithLicenseAndDuration() {
        val dto = JamendoResponseParser.parse(fixture()).first()
        assertEquals(setOf("synthpop", "synthesizer", "retro"), dto.tags)
        val track = checkNotNull(JamendoTrackMapper.toTrack(dto))
        assertEquals("101", track.id)
        assertEquals("Retro fixture", track.title)
        assertEquals("Fixture artist", track.artist)
        assertEquals(185_000L, track.durationMs)
        assertEquals("https://creativecommons.org/licenses/by-nc-sa/3.0/", track.license)
        assertEquals("https://www.jamendo.com/track/101", track.sourceUrl)
        assertEquals("Jamendo", track.source)
    }

    @Test fun missingPermissionAndUnknownLicensesAreNotUsedForCachedPlayback() {
        val mapped = JamendoResponseParser.parse(fixture()).mapNotNull(JamendoTrackMapper::toTrack)
        assertEquals(listOf("101"), mapped.map { it.id })
    }

    @Test fun malformedAudioAndAggressiveTagsAreRejected() {
        val dto = JamendoResponseParser.parse(fixture()).first()
        assertNull(JamendoTrackMapper.toTrack(dto.copy(audio = "not-a-url")))
        assertNull(JamendoTrackMapper.toTrack(dto.copy(tags = setOf("heavy metal"))))
        assertNull(JamendoTrackMapper.toTrack(dto.copy(durationSeconds = 0)))
    }

    @Test fun singlesWithoutAlbumImageStillKeepTheirCover() {
        val dto = JamendoResponseParser.parse(fixture()).first()
        assertEquals("https://example.org/cover.jpg", checkNotNull(JamendoTrackMapper.toTrack(dto)).artworkUrl)
    }

    @Test(expected = MusicProviderException::class)
    fun apiFailureDoesNotLeakItsErrorMessageOrRequestUrl() {
        JamendoResponseParser.parse("""{"headers":{"status":"failed","error_message":"sensitive request"}}""")
    }
}
