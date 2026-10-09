package br.com.radioembarcada.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackConfigurationTest {
    @Test fun startIsQuickAndBufferLimitsRemainCoherent() {
        assertTrue(PlaybackConfiguration.START_BUFFER_MS in 500..2_500)
        assertEquals(30_000, PlaybackConfiguration.MIN_BUFFER_MS)
        assertEquals(45_000, PlaybackConfiguration.STEADY_LOW_WATER_MS)
        assertEquals(60_000, PlaybackConfiguration.DESIRED_BUFFER_MS)
        assertEquals(90_000, PlaybackConfiguration.MAX_BUFFER_MS)
        assertTrue(PlaybackConfiguration.START_BUFFER_MS < PlaybackConfiguration.REBUFFER_MS)
        assertTrue(PlaybackConfiguration.REBUFFER_MS < PlaybackConfiguration.MIN_BUFFER_MS)
        assertTrue(PlaybackConfiguration.MIN_BUFFER_MS <= PlaybackConfiguration.STEADY_LOW_WATER_MS)
        assertTrue(PlaybackConfiguration.DESIRED_BUFFER_MS <= PlaybackConfiguration.MAX_BUFFER_MS)
    }

    @Test fun steadyLoadingHasHysteresisAndNeverLoadsPastTheConfiguredTarget() {
        var filling = true
        filling = PlaybackConfiguration.shouldFill(60_000, filling)
        assertFalse(filling)
        filling = PlaybackConfiguration.shouldFill(50_000, filling)
        assertFalse(filling)
        filling = PlaybackConfiguration.shouldFill(44_000, filling)
        assertTrue(filling)
        assertTrue(PlaybackConfiguration.shouldFill(50_000, filling))
        assertFalse(PlaybackConfiguration.shouldFill(90_000, filling))
        assertTrue(PlaybackConfiguration.shouldFill(0, false))
    }

    @Test fun futureTracksHaveBoundedPreparationAndCacheIsOperationalInSize() {
        assertEquals(64 * 1024, PlaybackConfiguration.LOADING_CHECK_INTERVAL_BYTES)
        assertEquals(45_000L, PlaybackConfiguration.NEXT_TRACK_PRELOAD_MS)
        assertEquals(15_000L, PlaybackConfiguration.SECOND_TRACK_PRELOAD_MS)
        assertEquals(300L * 1024 * 1024, PlaybackConfiguration.CACHE_BYTES)
    }
}
