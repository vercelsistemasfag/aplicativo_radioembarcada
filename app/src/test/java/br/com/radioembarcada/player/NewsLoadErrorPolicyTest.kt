package br.com.radioembarcada.player

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NewsLoadErrorPolicyTest {
    @Test fun newsLoadingHasBoundedRecoveryWhileMusicKeepsItsExistingBackoff() {
        fun error(count: Int) = LoadErrorHandlingPolicy.LoadErrorInfo(
            LoadEventInfo(1, DataSpec(Uri.parse("https://audios.ebc.com.br/test.mp3")), 0),
            MediaLoadData(C.DATA_TYPE_MEDIA), IOException("connection"), count)
        val editorial = RadioLoadErrorPolicy(finiteRetries = true)
        assertTrue(editorial.getRetryDelayMsFor(error(1)) > 0)
        assertTrue(editorial.getRetryDelayMsFor(error(2)) > 0)
        assertEquals(C.TIME_UNSET, editorial.getRetryDelayMsFor(error(3)))
        assertTrue(RadioLoadErrorPolicy().getRetryDelayMsFor(error(100)) > 0)
    }
}
