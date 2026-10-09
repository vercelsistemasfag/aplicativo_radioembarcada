package br.com.radioembarcada.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import java.util.concurrent.ConcurrentHashMap

/** Mede os MediaPeriods reais; URL/timeline/prepareSource não significam áudio pronto. */
@UnstableApi
internal class PlaybackReadiness(private val diagnostic: (String) -> Unit = {}) {
    data class Snapshot(val started: Boolean = false, val prepared: Boolean = false,
        val selected: Boolean = false, val bufferedUs: Long = 0) {
        val ready: Boolean get() = prepared && selected &&
            (bufferedUs == C.TIME_END_OF_SOURCE || bufferedUs >= PlaybackConfiguration.START_BUFFER_MS * 1_000L)
    }
    private class State { @Volatile var snapshot = Snapshot() }
    private val states = ConcurrentHashMap<String, State>()
    fun get(id: String): Snapshot = states[id]?.snapshot ?: Snapshot()

    fun observe(source: MediaSource): MediaSource = object : WrappingMediaSource(source) {
        override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod {
            val state = State()
            states[mediaItem.mediaId] = state
            return ObservedPeriod(mediaSource.createPeriod(id, allocator, startPositionUs), state, mediaItem.mediaId, diagnostic)
        }
        override fun releasePeriod(mediaPeriod: MediaPeriod) {
            val observed = mediaPeriod as ObservedPeriod
            states.remove(mediaItem.mediaId, observed.state)
            mediaSource.releasePeriod(observed.delegate)
        }
    }

    private class ObservedPeriod(val delegate: MediaPeriod, val state: State,
        private val id: String, private val diagnostic: (String) -> Unit) : MediaPeriod by delegate {
        override fun prepare(callback: MediaPeriod.Callback, positionUs: Long) {
            state.snapshot = state.snapshot.copy(started = true)
            delegate.prepare(object : MediaPeriod.Callback {
                override fun onPrepared(mediaPeriod: MediaPeriod) {
                    state.snapshot = state.snapshot.copy(prepared = true)
                    callback.onPrepared(this@ObservedPeriod)
                }
                override fun onContinueLoadingRequested(source: MediaPeriod) {
                    updateBuffer()
                    callback.onContinueLoadingRequested(this@ObservedPeriod)
                }
            }, positionUs)
        }
        override fun selectTracks(selections: Array<out ExoTrackSelection?>,
            mayRetainStreamFlags: BooleanArray, streams: Array<out SampleStream?>,
            streamResetFlags: BooleanArray, positionUs: Long): Long {
            val result = delegate.selectTracks(selections, mayRetainStreamFlags, streams, streamResetFlags, positionUs)
            state.snapshot = state.snapshot.copy(selected = selections.indices.any { index ->
                selections[index]?.selectedFormat?.sampleMimeType?.let(MimeTypes::getTrackType) == C.TRACK_TYPE_AUDIO && streams[index] != null
            })
            updateBuffer()
            return result
        }
        override fun continueLoading(loadingInfo: LoadingInfo): Boolean = delegate.continueLoading(loadingInfo).also { updateBuffer() }
        override fun getBufferedPositionUs(): Long = updateBuffer()
        private fun updateBuffer(): Long = delegate.bufferedPositionUs.also {
            val previous = state.snapshot
            state.snapshot = previous.copy(bufferedUs = it)
            if (!previous.ready && state.snapshot.ready) diagnostic("Áudio pronto no pipeline: item=$id; prepared=${state.snapshot.prepared}; selected=${state.snapshot.selected}; bufferedUs=$it")
        }
    }
}
