package br.com.radioembarcada.player

import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType
import org.junit.Assert.*
import org.junit.Test

class AudioCacheKeyTest {
    @Test fun changedAudioUrlCannotReuseStaleAudioUnderSameCatalogId() {
        val item = ProgramItem("id", ProgramItemType.MUSIC, "Title", "https://example.org/old.mp3", 0)
        assertNotEquals(audioCacheKey(item), audioCacheKey(item.copy(audioUrl = "https://example.org/new.mp3")))
        assertEquals(audioCacheKey(item), audioCacheKey(item.copy(title = "Updated title")))
        assertFalse(audioCacheKey(item).contains("https://"))
    }

    @Test fun repeatedInsertionOccurrencesReuseAudioCacheButHaveDifferentQueueIds() {
        val piece = ProgramItem("station", ProgramItemType.STATION_ID, "Rádio", "https://example.org/id.mp3", 4_000)
        val first = piece.copy(id = "station:occurrence:1")
        val second = piece.copy(id = "station:occurrence:2")
        assertNotEquals(first.id, second.id)
        assertEquals(audioCacheKey(first), audioCacheKey(second))
        assertNotEquals(audioCacheKey(first), audioCacheKey(second.copy(audioUrl = "https://example.org/new.mp3")))
    }
}
