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
}
