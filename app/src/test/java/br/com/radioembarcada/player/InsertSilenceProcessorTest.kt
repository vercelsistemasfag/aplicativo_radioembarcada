package br.com.radioembarcada.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import br.com.radioembarcada.model.ProgramItemType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class InsertSilenceProcessorTest {
    private fun processor(type: ProgramItemType, channels: Int = 1, beginning: Boolean = true) =
        InsertSilenceProcessor().apply {
            beginStream("insert", type, beginning)
            configure(AudioProcessor.AudioFormat(1_000, channels, C.ENCODING_PCM_16BIT))
            flush()
        }
    private fun pcm(vararg samples: Int): ByteBuffer = ByteBuffer.allocateDirect(samples.size * 2)
        .order(ByteOrder.nativeOrder()).apply { samples.forEach { putShort(it.toShort()) }; flip() }
    private fun output(processor: InsertSilenceProcessor, vararg input: Int): List<Int> {
        processor.queueInput(pcm(*input))
        val out = processor.output.order(ByteOrder.nativeOrder())
        return List(out.remaining() / 2) { out.short.toInt() }
    }

    @Test fun trimsOnlyInitialExactZeroAcrossBuffersAndPreservesEveryAudibleSampleAndInteriorPause() {
        for (type in listOf(ProgramItemType.STATION_ID, ProgramItemType.JINGLE)) {
            val p = processor(type)
            assertEquals(emptyList<Int>(), output(p, 0, 0, 0))
            assertEquals(listOf(1, -1, 0, 0, 22), output(p, 0, 0, 1, -1, 0, 0, 22))
            assertEquals(listOf(0, 0, 0), output(p, 0, 0, 0)) // cauda preservada
            assertEquals(5L, p.skippedFrames)
            assertEquals(5L, p.removedMs("insert"))
            p.queueEndOfStream()
            assertTrue(p.isEnded)
        }
    }

    @Test fun aStereoFrameIsKeptIfEitherChannelHasEvenTheSmallestNonZeroSample() {
        val p = processor(ProgramItemType.STATION_ID, channels = 2)
        assertEquals(listOf(0, 1, 0, 0, -1, 0), output(p, 0, 0, 0, 1, 0, 0, -1, 0))
        assertEquals(1L, p.skippedFrames)
    }

    @Test fun musicOtherContentAndMidItemRecoveryNeverLoseAnySamples() {
        for (type in listOf(ProgramItemType.MUSIC, ProgramItemType.ADVERTISEMENT, ProgramItemType.ANNOUNCEMENT)) {
            val p = processor(type)
            assertEquals(listOf(0, 0, 1, 0), output(p, 0, 0, 1, 0))
            assertEquals(0L, p.skippedFrames)
        }
        val p = processor(ProgramItemType.STATION_ID, beginning = false)
        assertEquals(listOf(0, 0, 1), output(p, 0, 0, 1))
    }

    @Test fun clockChainAccountsForRemovedFramesInsteadOfLeavingASilentTail() {
        val p = processor(ProgramItemType.JINGLE)
        val chain = InsertAudioProcessorChain(p)
        output(p, 0, 0, 0, 1, 2, 3)
        assertEquals(3L, chain.skippedOutputFrameCount)
        assertEquals(6_000L, chain.getMediaDuration(3_000) + chain.skippedOutputFrameCount * 1_000)
        // Retomar não chama flush: todos os samples posteriores e pausas continuam intactos.
        assertEquals(listOf(0, 0, 4), output(p, 0, 0, 4))
        assertEquals(3L, chain.skippedOutputFrameCount)
    }

    @Test fun nextMusicAndEachNewInsertHaveIndependentPrefixState() {
        val p = processor(ProgramItemType.STATION_ID)
        output(p, 0, 0, 1)
        p.beginStream("music", ProgramItemType.MUSIC, true); p.flush()
        assertEquals(listOf(0, 0, 1), output(p, 0, 0, 1))
        assertEquals(0L, p.skippedFrames)
        p.beginStream("jingle", ProgramItemType.JINGLE, true); p.flush()
        assertEquals(listOf(1), output(p, 0, 0, 0, 1))
        assertEquals(2L, p.removedMs("insert"))
        assertEquals(3L, p.removedMs("jingle"))
        p.forget("insert")
        assertEquals(0L, p.removedMs("insert"))
    }

    @Test fun unsupportedPcmIsBypassedRatherThanGuessingOrClippingAudio() {
        val p = InsertSilenceProcessor()
        p.beginStream("insert", ProgramItemType.STATION_ID, true)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET,
            p.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT)))
        assertFalse(p.isActive)
    }

    @Test fun processorFlushWithinAnItemNeverStartsTrimmingAnInteriorPause() {
        val p = processor(ProgramItemType.STATION_ID)
        assertEquals(listOf(1), output(p, 0, 0, 1))
        p.flush()
        assertEquals(listOf(0, 0, 2), output(p, 0, 0, 2))
        assertEquals(2L, p.removedMs("insert"))
    }
}
