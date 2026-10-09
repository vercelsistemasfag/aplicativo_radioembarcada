package br.com.radioembarcada.player

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.test.utils.FakeClock
import br.com.radioembarcada.model.ProgramItemType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class InsertAudioSinkTest {
    // Observar PCM antes da rampa nativa de início do AudioTrack, que atenua amostras pequenas.
    private fun observedChain(processor: InsertSilenceProcessor, written: MutableList<Int>): AudioProcessorChain {
        val chain = InsertAudioProcessorChain(processor)
        val tee = TeeAudioProcessor(object : TeeAudioProcessor.AudioBufferSink {
            override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) = Unit
            override fun handleBuffer(buffer: ByteBuffer) {
                val data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                while (data.remaining() >= 2) written += data.short.toInt()
            }
        })
        return object : AudioProcessorChain by chain {
            override fun getAudioProcessors(): Array<AudioProcessor> = chain.audioProcessors + tee
        }
    }

    @Test fun removedPrefixDoesNotBecomeAnExtraWaitAtTheFollowingMusicBoundary() {
        val processor = InsertSilenceProcessor()
        val written = mutableListOf<Int>()
        val sink = DefaultAudioSink.Builder(RuntimeEnvironment.getApplication())
            .setAudioProcessorChain(observedChain(processor, written)).build()
        try {
            val clock = FakeClock(true)
            sink.setClock(clock)
            sink.play()
            val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setPcmEncoding(C.ENCODING_PCM_16BIT).setSampleRate(44_100).setChannelCount(1).build()
            val types = listOf(ProgramItemType.MUSIC, ProgramItemType.STATION_ID, ProgramItemType.MUSIC)
            types.forEachIndexed { index, type ->
                processor.beginStream("item-$index", type, true)
                sink.configure(format, 0, null)
                sink.handleDiscontinuity()
                val data = ByteBuffer.allocateDirect(882).order(ByteOrder.LITTLE_ENDIAN).apply {
                    repeat(441) { putShort(if (index == 1 && it < 220) 0 else (index + 1).toShort()) }; flip()
                }
                assertTrue(sink.handleBuffer(data, index * 10_000L, 1))
            }
            sink.playToEndOfStream()
            clock.advanceTime(1_000)
            assertEquals(List(441) { 1 } + List(221) { 2 } + List(441) { 3 }, written)
            assertEquals(4L, processor.removedMs("item-1"))
            assertEquals(30_000.0, sink.getCurrentPositionUs(true).toDouble(), 100.0)
        } finally { sink.release() }
    }
    @Test fun actualMedia3SinkRemovesEncoderPaddingThenDigitalPrefixWithoutCuttingFirstVoiceSample() {
        val processor = InsertSilenceProcessor()
        val written = mutableListOf<Int>()
        val sink = DefaultAudioSink.Builder(RuntimeEnvironment.getApplication())
            .setAudioProcessorChain(observedChain(processor, written)).build()
        try {
            val clock = FakeClock(true)
            sink.setClock(clock)
            sink.play()
            processor.beginStream("station", ProgramItemType.STATION_ID, true)
            sink.configure(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setPcmEncoding(C.ENCODING_PCM_16BIT).setSampleRate(44_100).setChannelCount(1)
                .setEncoderDelay(2).setEncoderPadding(2).build(), 0, null)
            // 2 frames de encoder, 3 de silêncio real, fala/pausa/cauda e 2 de encoder padding.
            val data = ByteBuffer.allocateDirect(22).order(ByteOrder.LITTLE_ENDIAN)
                .apply { listOf(99, 98, 0, 0, 0, 1, 20, 0, 30, 97, 96).forEach { putShort(it.toShort()) }; flip() }
            assertTrue(sink.handleBuffer(data, 0, 1))
            // A configuração do próximo stream permite ao trimming reconhecer padding de fim.
            sink.configure(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setPcmEncoding(C.ENCODING_PCM_16BIT).setSampleRate(44_100).setChannelCount(1).build(), 0, null)
            sink.playToEndOfStream()
            clock.advanceTime(1_000)
            assertEquals(listOf(1, 20, 0, 30), written)
            assertEquals(3L, processor.skippedFrames)
            // O relógio expõe os frames descartados através do mecanismo oficial do sink.
            assertTrue(sink.getCurrentPositionUs(true) >= 3L * 1_000_000 / 44_100)
        } finally { sink.release() }
    }
}
