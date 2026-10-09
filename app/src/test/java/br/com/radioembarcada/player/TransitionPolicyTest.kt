package br.com.radioembarcada.player

import br.com.radioembarcada.model.ProgramItemType
import org.junit.Assert.*
import org.junit.Test

class TransitionPolicyTest {
    @Test fun musicHasFifteenSecondSmoothExitOnlyBeforeAnInsertion() {
        assertEquals(15_000L, TransitionConfiguration.MUSIC_FADE_OUT_MS)
        val values = (165_000L..180_000L step 100).map {
            TransitionPolicy.volume(ProgramItemType.MUSIC, ProgramItemType.STATION_ID, it, 180_000)
        }
        assertEquals(TransitionConfiguration.MUSIC_GAIN, values.first(), 0.00001f)
        assertEquals(TransitionConfiguration.MUSIC_GAIN * TransitionConfiguration.MUSIC_EXIT_FLOOR, values.last(), 0.00001f)
        assertTrue(values.zipWithNext().all { (a,b) -> a >= b })
        assertEquals(TransitionConfiguration.MUSIC_GAIN * (1 + TransitionConfiguration.MUSIC_EXIT_FLOOR) / 2,
            TransitionPolicy.volume(ProgramItemType.MUSIC, ProgramItemType.JINGLE, 172_500, 180_000), 0.00001f)
    }

    @Test fun finalThreeSecondsNeverBecomeDigitalOrPerceptualMute() {
        for (type in listOf(ProgramItemType.STATION_ID, ProgramItemType.JINGLE)) {
            for (remaining in 1L..3_000L) {
                assertTrue(TransitionPolicy.volume(ProgramItemType.MUSIC, type, 180_000 - remaining, 180_000) >=
                    TransitionConfiguration.MUSIC_GAIN * TransitionConfiguration.MUSIC_EXIT_FLOOR)
            }
            assertTrue(TransitionPolicy.volume(type, ProgramItemType.MUSIC, 0, 8_000) > 0f)
            assertEquals(TransitionPolicy.gain(type), TransitionPolicy.volume(type, ProgramItemType.MUSIC,
                TransitionConfiguration.INSERT_ENTRY_MS, 8_000), 0.00001f)
        }
    }

    @Test fun eightTwelveAndTwentySecondInsertsKeepTheirGainThroughTheirEntireEnding() {
        for (type in ProgramItemType.entries.filter { it != ProgramItemType.MUSIC }) {
            for (duration in listOf(8_000L, 12_000L, 20_000L)) {
                for (position in listOf(duration / 2, duration - 1, duration)) {
                    assertEquals(TransitionPolicy.gain(type), TransitionPolicy.volume(type,
                        ProgramItemType.MUSIC, position, duration), 0.00001f)
                }
            }
            assertEquals(TransitionType.INSERT_TO_MUSIC, TransitionPolicy.resolve(type, ProgramItemType.MUSIC))
        }
    }

    @Test fun entriesAreShortAndBoundedWithoutOverlapsOrClipping() {
        assertTrue(TransitionConfiguration.INSERT_ENTRY_MS in 150..400)
        assertTrue(TransitionConfiguration.MUSIC_ENTRY_MS in 50..200)
        assertTrue(TransitionConfiguration.STATION_ID_GAIN < TransitionConfiguration.MUSIC_GAIN)
        for (type in ProgramItemType.entries) {
            assertEquals(if (type == ProgramItemType.MUSIC) 0f else TransitionPolicy.gain(type) * TransitionConfiguration.INSERT_ENTRY_FLOOR,
                TransitionPolicy.volume(type, ProgramItemType.MUSIC, 0, 20_000), 0f)
            assertEquals(TransitionPolicy.gain(type), TransitionPolicy.volume(type, ProgramItemType.MUSIC, 400, 20_000), 0.00001f)
            assertTrue(TransitionPolicy.gain(type) in 0f..1f)
        }
    }

    @Test fun everyShortToShortCombinationIsForbiddenAndMissingPoolsAllowNaturalMusic() {
        val shorts = ProgramItemType.entries.filter { it != ProgramItemType.MUSIC }
        for (from in shorts) for (to in shorts) assertEquals(TransitionType.FORBIDDEN, TransitionPolicy.resolve(from, to))
        assertEquals(TransitionType.HARD_TRANSITION, TransitionPolicy.resolve(ProgramItemType.MUSIC, ProgramItemType.MUSIC))
    }
}
