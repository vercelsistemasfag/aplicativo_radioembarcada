package br.com.radioembarcada.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import br.com.radioembarcada.model.ProgramItemType
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/** Remove apenas frames PCM exatamente zero ANTES do primeiro som da vinheta/jingle.
 * Executado depois do trimming Xing/LAME do sink; não corta fala, pausas ou caudas. */
@UnstableApi
internal class InsertSilenceProcessor(private val diagnostic: (String) -> Unit = {}) : BaseAudioProcessor() {
    private data class Stream(val id: String, val enabled: Boolean)
    private var pending = Stream("", false)
    private var active = pending
    private var leading = false
    private var streamGeneration = 0L
    private var flushedGeneration = -1L
    private var totalRemovedFrames = 0L
    var skippedFrames = 0L
        private set
    private val removedUs = ConcurrentHashMap<String, Long>()

    fun beginStream(id: String, type: ProgramItemType?, fromBeginning: Boolean) {
        pending = Stream(id, fromBeginning && type in setOf(ProgramItemType.STATION_ID, ProgramItemType.JINGLE))
        streamGeneration++
    }

    fun removedMs(id: String): Long = (removedUs[id] ?: 0L) / 1_000
    fun forget(id: String) { removedUs.remove(id) }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET

    override fun onFlush() {
        // O sink drena o áudio antigo antes de trocar configuração/offset do stream.
        if (flushedGeneration != streamGeneration) {
            active = pending
            leading = active.enabled
            totalRemovedFrames = 0
            removedUs.remove(active.id)
            flushedGeneration = streamGeneration
        }
        skippedFrames = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val frameSize = inputAudioFormat.bytesPerFrame
        if (leading) {
            while (inputBuffer.remaining() >= frameSize) {
                val start = inputBuffer.position()
                val zero = (start until start + frameSize).all { inputBuffer.get(it) == 0.toByte() }
                if (!zero) {
                    leading = false
                    diagnostic("Initial digital silence removed: item=${active.id}; durationUs=${totalRemovedFrames * 1_000_000 / inputAudioFormat.sampleRate}; audible content preserved")
                    break
                }
                inputBuffer.position(start + frameSize)
                skippedFrames++
                totalRemovedFrames++
            }
            removedUs[active.id] = totalRemovedFrames * 1_000_000 / inputAudioFormat.sampleRate
        }
        if (inputBuffer.hasRemaining()) replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
    }

    override fun onReset() {
        pending = Stream("", false); active = pending; leading = false
        streamGeneration = 0; flushedGeneration = -1; totalRemovedFrames = 0; removedUs.clear()
    }
}

/** Contabilizar os frames removidos no relógio oficial evita deslocar o gap para INSERT → MUSIC. */
@UnstableApi
internal class InsertAudioProcessorChain(private val prefix: InsertSilenceProcessor) :
    DefaultAudioSink.DefaultAudioProcessorChain(prefix) {
    override fun getSkippedOutputFrameCount(): Long = super.getSkippedOutputFrameCount() + prefix.skippedFrames
}
