package br.com.radioembarcada.player

import android.content.Context
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import br.com.radioembarcada.model.ProgramItemType

/** Identificação no thread do renderer, na troca REAL de saída, não no callback tardio da UI. */
@UnstableApi
internal class RadioRenderersFactory(context: Context, private val prefix: InsertSilenceProcessor) :
    DefaultRenderersFactory(context) {
    private data class Stream(val id: String, val type: ProgramItemType?, val beginning: Boolean)
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean): AudioSink = DefaultAudioSink.Builder(context)
        .setAudioProcessorChain(InsertAudioProcessorChain(prefix)).build()

    override fun buildAudioRenderers(context: Context, extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector, enableDecoderFallback: Boolean, audioSink: AudioSink,
        eventHandler: Handler, eventListener: AudioRendererEventListener, out: ArrayList<Renderer>) {
        out += object : MediaCodecAudioRenderer(context, codecAdapterFactory, mediaCodecSelector,
            enableDecoderFallback, eventHandler, eventListener, audioSink) {
            private val streams = mutableMapOf<Long, Stream>()
            private var latest: Stream? = null

            override fun onStreamChanged(formats: Array<out Format>, startPositionUs: Long,
                offsetUs: Long, mediaPeriodId: MediaPeriodId) {
                val index = timeline.getIndexOfPeriod(mediaPeriodId.periodUid)
                val media = if (index != C.INDEX_UNSET) timeline.getWindow(
                    timeline.getPeriod(index, Timeline.Period()).windowIndex, Timeline.Window()).mediaItem else null
                val type = media?.mediaMetadata?.extras?.getString("programType")?.let {
                    runCatching { ProgramItemType.valueOf(it) }.getOrNull()
                }
                val stream = Stream(media?.mediaId.orEmpty(), type, startPositionUs <= offsetUs)
                streams[offsetUs] = stream
                latest = stream
                super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId)
            }

            override fun onOutputStreamOffsetUsChanged(outputStreamOffsetUs: Long) {
                streams.remove(outputStreamOffsetUs)?.let { prefix.beginStream(it.id, it.type, it.beginning) }
                super.onOutputStreamOffsetUsChanged(outputStreamOffsetUs)
            }

            override fun onPositionReset(positionUs: Long, joining: Boolean) {
                latest?.let { prefix.beginStream(it.id, it.type, positionUs <= streamOffsetUs) }
                streams.clear()
                super.onPositionReset(positionUs, joining)
            }

            override fun onDisabled() { streams.clear(); latest = null; super.onDisabled() }
        }
    }
}
