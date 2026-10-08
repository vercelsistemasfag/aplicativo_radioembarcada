package br.com.radioembarcada.network.jamendo

import br.com.radioembarcada.data.music.MusicProviderException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class JamendoConfigurationTest {
    @Test fun multipleFuzzyProfilesAreUsedRatherThanASingleNarrowTag() {
        assertTrue(JamendoConfiguration.tagGroups.size >= 3)
        assertTrue(JamendoConfiguration.tagGroups.all { '+' in it })
        val all = JamendoConfiguration.tagGroups.joinToString("+")
        assertTrue("rock" in all && "newwave" in all && "synthwave" in all && "electronic" in all)
    }

    @Test fun multipleParametersHaveTheSameWireEncodingAsOfficialExamples() {
        val query = encodeJamendoQuery(linkedMapOf("fuzzytags" to "synthwave+newwave+retro",
            "include" to "licenses+musicinfo", "speed" to "low+medium", "type" to "single+albumtrack"))
        assertEquals("fuzzytags=synthwave+newwave+retro&include=licenses+musicinfo&speed=low+medium&type=single+albumtrack", query)
    }

    @Test fun missingClientIdFailsLocallyWithoutCallingTheNetwork() = runBlocking {
        try {
            JamendoMusicProvider("").fetchTracks(20, 0)
            fail("Deveria informar configuração ausente")
        } catch (error: MusicProviderException) {
            assertFalse(error.retryable)
            assertTrue(error.userMessage.contains("não configurada"))
        }
    }
}
