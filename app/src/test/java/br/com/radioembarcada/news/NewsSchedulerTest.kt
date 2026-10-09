package br.com.radioembarcada.news

import br.com.radioembarcada.BuildConfig
import org.junit.Assert.*
import org.junit.Test

class NewsSchedulerTest {
    @Test fun thirtyActiveMinutesBecomeDueWithoutChangingAnyPlayingItem() {
        val clock = NewsScheduler()
        clock.tick(0, true); clock.tick(NewsConfiguration.NEWS_INTERVAL_MS - 1, true)
        assertFalse(clock.newsDue)
        clock.tick(NewsConfiguration.NEWS_INTERVAL_MS, true)
        assertTrue(clock.newsDue)
        assertTrue(clock.boundaryWillBeDue(0)) // apenas permissão; não existe seek/stop neste relógio
    }
    @Test fun pauseBufferingAndAudioFocusDoNotConsumeTime() {
        val clock = NewsScheduler()
        clock.tick(0, true); clock.tick(900_000, false)
        clock.tick(1_500_000, false); clock.tick(1_500_000, true)
        assertEquals(900_000L, clock.elapsedMs)
        clock.tick(2_399_999, true); assertFalse(clock.newsDue)
        clock.tick(2_400_000, true); assertTrue(clock.newsDue)
    }
    @Test fun futureBoundaryCanBePreparedEarlyButAnUnavailableBlockIsDeferred() {
        val clock = NewsScheduler(elapsed = 1_700_000)
        assertFalse(clock.boundaryWillBeDue(99_999))
        assertTrue(clock.boundaryWillBeDue(100_000))
        clock.retryLater()
        assertFalse(clock.boundaryWillBeDue(100_000))
    }
    @Test fun releaseAlwaysUsesThirtyMinutesAndDebugOverrideIsSeparate() {
        assertEquals(1_800_000L, NewsConfiguration.NEWS_INTERVAL_MS)
        if (!BuildConfig.DEBUG) assertEquals(0L, BuildConfig.NEWS_INTERVAL_DEBUG)
        assertTrue(BuildConfig.NEWS_INTERVAL_DEBUG in listOf(0L, 180_000L, 300_000L))
    }
}
