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
    .setPrioritizeTimeOverSizeThresholds(true).build(), private val localSource: Boolean = false,
    private val playlistPreload: Boolean = false) : LoadControl by delegate {
    private var filling = true
    @Volatile internal var currentMediaId: String? = null
    internal var currentAudio: (String) -> PlaybackReadiness.Snapshot = { PlaybackReadiness.Snapshot() }
    private var currentPositionUs = 0L
    private var currentBufferedUs = 0L
    private var currentFullyBuffered = false
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
        bufferedDurationUs: Long): Boolean {
        if (!playlistPreload) return delegate.shouldContinuePreloading(timeline, mediaPeriodId, bufferedDurationUs)
        val current = currentMediaId ?: return false
        val window = Timeline.Window()
        val index = (0 until timeline.windowCount).firstOrNull {
            timeline.getWindow(it, window).mediaItem.mediaId == current
        } ?: return false
        val target = timeline.getPeriodByUid(mediaPeriodId.periodUid, Timeline.Period()).windowIndex
        val nextType = if (index + 1 < timeline.windowCount) timeline.getWindow(index + 1, window)
            .mediaItem.mediaMetadata.extras?.getString("programType")?.let {
                runCatching { br.com.radioembarcada.model.ProgramItemType.valueOf(it) }.getOrNull()
            } else null
        val duration = PreloadPlan.durationMs(target - index, nextType, localSource) ?: return false
        // Prioridade ao início atual; depois preparar a peça, não esperar os últimos 15 s.
        val actual = currentAudio(current)
        val enoughCurrentAudio = actual.bufferedUs == androidx.media3.common.C.TIME_END_OF_SOURCE ||
            actual.prepared && actual.selected && actual.bufferedUs - currentPositionUs >= PlaybackConfiguration.PRELOAD_START_BUFFER_MS * 1_000L
        return (localSource || enoughCurrentAudio || currentFullyBuffered || currentBufferedUs >= PlaybackConfiguration.PRELOAD_START_BUFFER_MS * 1_000L) &&
            bufferedDurationUs != androidx.media3.common.C.TIME_END_OF_SOURCE && bufferedDurationUs < duration * 1_000
    }

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        currentBufferedUs = parameters.bufferedDurationUs
        currentPositionUs = parameters.playbackPositionUs
        if (playlistPreload && parameters.timeline.getIndexOfPeriod(parameters.mediaPeriodId.periodUid) != androidx.media3.common.C.INDEX_UNSET) {
            val period = parameters.timeline.getPeriodByUid(parameters.mediaPeriodId.periodUid, Timeline.Period())
            currentFullyBuffered = period.durationUs != androidx.media3.common.C.TIME_UNSET &&
                period.durationUs - parameters.playbackPositionUs <= currentBufferedUs
        }
        val permitted = delegate.shouldContinueLoading(parameters)
        if (localSource) return permitted
        filling = PlaybackConfiguration.shouldFill(parameters.bufferedDurationUs / 1_000, filling)
        return permitted && filling
    }
}
