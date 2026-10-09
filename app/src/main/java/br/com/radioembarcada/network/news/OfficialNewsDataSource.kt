package br.com.radioembarcada.network.news

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException

/** Revalidar a URL final do HTTP antes que qualquer áudio redirecionado seja lido/cacheado. */
@UnstableApi
internal class OfficialNewsDataSource(private val delegate: DataSource) : DataSource by delegate {
    override fun open(dataSpec: DataSpec): Long {
        if (!OfficialNewsUrls.audio(dataSpec.uri.toString())) throw IOException("Unofficial news source")
        val length = delegate.open(dataSpec)
        if (!OfficialNewsUrls.audio(delegate.uri?.toString().orEmpty())) {
            delegate.close()
            throw IOException("Unofficial news redirect")
        }
        return length
    }
}
