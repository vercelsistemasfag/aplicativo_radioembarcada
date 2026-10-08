package br.com.radioembarcada.player

import androidx.media3.common.C
import androidx.media3.common.ParserException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/** Uma falha de carregamento temporária não deve descartar o áudio já preparado. */
@UnstableApi
class RadioLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getMinimumLoadableRetryCount(dataType: Int): Int = Int.MAX_VALUE
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val error = loadErrorInfo.exception
        if (error is ParserException || (error is HttpDataSource.InvalidResponseCodeException &&
                error.responseCode in setOf(400, 401, 403, 404, 410))) return C.TIME_UNSET
        return RetryPolicy.delayMillis(loadErrorInfo.errorCount - 1)
    }
}
