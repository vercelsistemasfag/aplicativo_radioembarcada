package br.com.radioembarcada.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection

/** DefaultLoadControl mantém as garantias de início/rebuffer e o teto de 90 s.
 * A histerese adicional concentra o buffer estável entre 45 e 60 s. */
@UnstableApi
class RadioLoadControl(private val delegate: LoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(PlaybackConfiguration.MIN_BUFFER_MS, PlaybackConfiguration.MAX_BUFFER_MS,
        PlaybackConfiguration.START_BUFFER_MS, PlaybackConfiguration.REBUFFER_MS)
    .setPrioritizeTimeOverSizeThresholds(true).build()) : LoadControl by delegate {
    private var filling = true
    // Kotlin `by` não encaminha os métodos default desta interface Java.
    // Implementá-los explicitamente evita os defaults que lançam IllegalStateException.
    override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)
    override fun onTracksSelected(parameters: LoadControl.Parameters, trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>) =
        delegate.onTracksSelected(parameters, trackGroups, trackSelections)
    override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)
    override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)
    override fun getBackBufferDurationUs(playerId: PlayerId): Long = delegate.getBackBufferDurationUs(playerId)
    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean = delegate.retainBackBufferFromKeyframe(playerId)
    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean = delegate.shouldStartPlayback(parameters)
    override fun shouldContinuePreloading(timeline: Timeline, mediaPeriodId: MediaPeriodId,
        bufferedDurationUs: Long): Boolean = delegate.shouldContinuePreloading(timeline, mediaPeriodId, bufferedDurationUs)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        val permitted = delegate.shouldContinueLoading(parameters)
        filling = PlaybackConfiguration.shouldFill(parameters.bufferedDurationUs / 1_000, filling)
        return permitted && filling
    }
}
