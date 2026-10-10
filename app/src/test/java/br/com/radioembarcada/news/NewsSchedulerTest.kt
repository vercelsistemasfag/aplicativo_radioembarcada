package br.com.radioembarcada.news

import br.com.radioembarcada.BuildConfig
import org.junit.Assert.*
import org.junit.Test

class NewsSchedulerTest {
    @Test fun sixtyActiveMinutesBecomeDueWithoutChangingAnyPlayingItem() {
        val clock = NewsScheduler()
        clock.tick(0, true); clock.tick(30L * 60 * 1_000, true)
        assertFalse(clock.newsDue) // The former 30-minute boundary no longer triggers news.
        clock.tick(NewsConfiguration.NEWS_INTERVAL_MS - 1, true)
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
        clock.tick(NewsConfiguration.NEWS_INTERVAL_MS + 600_000 - 1, true); assertFalse(clock.newsDue)
        clock.tick(NewsConfiguration.NEWS_INTERVAL_MS + 600_000, true); assertTrue(clock.newsDue)
    }
    @Test fun futureBoundaryCanBePreparedEarlyButAnUnavailableBlockIsDeferred() {
        val clock = NewsScheduler(elapsed = NewsConfiguration.NEWS_INTERVAL_MS - 100_000)
        assertFalse(clock.boundaryWillBeDue(99_999))
        assertTrue(clock.boundaryWillBeDue(100_000))
        clock.retryLater()
        assertFalse(clock.boundaryWillBeDue(100_000))
    }
    @Test fun releaseAlwaysUsesSixtyMinutesAndDebugOverrideIsSeparate() {
        assertEquals(3_600_000L, NewsConfiguration.NEWS_INTERVAL_MS)
        if (!BuildConfig.DEBUG) assertEquals(0L, BuildConfig.NEWS_INTERVAL_DEBUG)
        assertTrue(BuildConfig.NEWS_INTERVAL_DEBUG in listOf(0L, 180_000L, 300_000L))
    }
}
