package br.com.radioembarcada.player

import br.com.radioembarcada.model.ProgramItemType
import org.junit.Assert.*
import org.junit.Test

class PreloadPlanTest {
    @Test fun preparesTheInsertionAndFollowingMusicBeforeTheCurrentSongEnds() {
        for (type in listOf(ProgramItemType.STATION_ID, ProgramItemType.JINGLE)) {
            assertEquals(45_000L, PreloadPlan.durationMs(1, type, false))
            assertEquals(45_000L, PreloadPlan.durationMs(2, type, false))
        }
        assertNull(PreloadPlan.durationMs(3, ProgramItemType.STATION_ID, false))
    }
    @Test fun duringTheInsertionNextMusicHasFullPreparationTargetWithoutThirtySecondWait() {
        assertEquals(45_000L, PreloadPlan.durationMs(1, ProgramItemType.MUSIC, false))
        assertTrue(PlaybackConfiguration.canPreload(false, 500, 8_000))
    }
    @Test fun missingPiecesAndLocalAssetsKeepTheirExistingBoundedTargets() {
        assertEquals(15_000L, PreloadPlan.durationMs(2, ProgramItemType.MUSIC, false))
        assertEquals(3_000L, PreloadPlan.durationMs(1, ProgramItemType.STATION_ID, true))
        assertEquals(1_000L, PreloadPlan.durationMs(2, ProgramItemType.STATION_ID, true))
    }
}
