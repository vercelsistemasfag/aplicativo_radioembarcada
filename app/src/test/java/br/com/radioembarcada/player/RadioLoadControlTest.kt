package br.com.radioembarcada.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class RadioLoadControlTest {
    @Test fun exoPlayerCanReadBackBufferSettingsDuringConstruction() {
        val control = RadioLoadControl()
        assertEquals(0L, control.getBackBufferDurationUs(PlayerId.UNSET))
        assertFalse(control.retainBackBufferFromKeyframe(PlayerId.UNSET))
    }

    @Test fun playerLifecycleReachesDefaultLoadControlWithoutThrowing() {
        val control = RadioLoadControl()
        control.onPrepared(PlayerId.UNSET)
        control.onStopped(PlayerId.UNSET)
        control.onReleased(PlayerId.UNSET)
    }

    @Test fun playbackStartsAfterSmallBufferAndTrackSelectionIsForwarded() {
        val control = RadioLoadControl()
        val period = MediaPeriodId("test")
        fun parameters(bufferMs: Long) = LoadControl.Parameters(PlayerId.UNSET,
            Timeline.EMPTY, period, 0, bufferMs * 1_000, 1f, true, false,
            C.TIME_UNSET, C.TIME_UNSET)
        control.onPrepared(PlayerId.UNSET)
        control.onTracksSelected(parameters(0), TrackGroupArray.EMPTY, emptyArray())
        assertFalse(control.shouldStartPlayback(parameters(500)))
        assertTrue(control.shouldStartPlayback(parameters(PlaybackConfiguration.START_BUFFER_MS.toLong())))
        control.shouldContinuePreloading(Timeline.EMPTY, period, 0)
        control.onReleased(PlayerId.UNSET)
    }

    @Test fun localPlaybackUsesSmallBufferAndDoesNotWaitForRemoteBufferTargets() {
        val control = RadioLoadControl(androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(PlaybackConfiguration.LOCAL_MIN_BUFFER_MS, PlaybackConfiguration.LOCAL_MAX_BUFFER_MS,
                PlaybackConfiguration.LOCAL_START_BUFFER_MS, PlaybackConfiguration.LOCAL_REBUFFER_MS)
            .setPrioritizeTimeOverSizeThresholds(true).build(), localSource = true)
        val period = MediaPeriodId("local")
        fun parameters(ms: Long) = LoadControl.Parameters(PlayerId.UNSET, Timeline.EMPTY, period,
            0, ms * 1_000, 1f, true, false, C.TIME_UNSET, C.TIME_UNSET)
        control.onPrepared(PlayerId.UNSET)
        control.onTracksSelected(parameters(0), TrackGroupArray.EMPTY, emptyArray())
        assertTrue(control.shouldStartPlayback(parameters(250)))
        assertFalse(control.shouldContinueLoading(parameters(6_000)))
        assertTrue(PlaybackConfiguration.LOCAL_NEXT_TRACK_PRELOAD_MS < PlaybackConfiguration.MIN_BUFFER_MS)
        control.onReleased(PlayerId.UNSET)
    }

}
