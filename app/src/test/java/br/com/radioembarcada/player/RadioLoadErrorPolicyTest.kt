package br.com.radioembarcada.player

import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceException
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class RadioLoadErrorPolicyTest {
    @Test fun brokenAudioFormatIsTerminalInsteadOfRetriedForever() {
        assertTrue(isPermanentLoadError(ParserException.createForMalformedDataOfUnknownType("bad audio", null)))
    }

    @Test fun missingOrUnreadableLocalAssetsAreTerminalAndCanAdvanceTheQueue() {
        listOf(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE).forEach {
            assertTrue(isPermanentLoadError(DataSourceException(it)))
        }
    }

    @Test fun transientNetworkErrorsKeepRetryingWithoutDiscardingPreparedAudio() {
        assertFalse(isPermanentLoadError(IOException("network unavailable")))
        assertEquals(Int.MAX_VALUE, RadioLoadErrorPolicy().getMinimumLoadableRetryCount(0))
    }
}
