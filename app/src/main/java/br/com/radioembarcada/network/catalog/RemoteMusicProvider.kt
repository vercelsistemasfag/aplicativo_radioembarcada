package br.com.radioembarcada.network.catalog

import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.data.music.MusicProviderException
import br.com.radioembarcada.model.Track
import br.com.radioembarcada.storage.SavedMusicCatalog
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RemoteMusicProvider(
    private val saved: SavedMusicCatalog,
    private val connected: () -> Boolean = { true },
    private val download: () -> String = ::downloadRemoteCatalog,
    private val now: () -> Long = System::currentTimeMillis,
) : MusicProvider {
    override val providesCompleteCatalog = true
    override val supportsSavedCatalog = true
    override val catalogRevision: Long? get() = catalog?.version
    private val mutex = Mutex()
    private var catalog: RemoteCatalog? = null
    private var diskRead = false
    private var nextRefresh = 0L

    override suspend fun fetchTracks(limit: Int, offset: Int): List<Track> = withContext(Dispatchers.IO) {
        require(limit > 0 && offset >= 0)
        mutex.withLock {
            if (!diskRead) {
                diskRead = true
                catalog = runCatching { saved.read()?.let(RemoteCatalogParser::parse) }.getOrNull()
            }
            if (connected() && now() >= nextRefresh) {
                try {
                    val json = download()
                    val loaded = RemoteCatalogParser.parse(json)
                    catalog = loaded
                    nextRefresh = now() + RemoteCatalogConfiguration.REFRESH_INTERVAL_MS
                    try { saved.write(json) } catch (_: IOException) { /* Mantém o catálogo válido em memória. */ }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    nextRefresh = now() + RemoteCatalogConfiguration.FAILED_REFRESH_INTERVAL_MS
                }
            }
            val tracks = catalog?.tracks ?: throw MusicProviderException("Programação indisponível.", true)
            val start = offset % tracks.size
            List(minOf(limit, tracks.size)) { tracks[(start + it) % tracks.size] }
        }
    }
}

private fun downloadRemoteCatalog(): String {
    val connection = URL(RemoteCatalogConfiguration.URL).openConnection() as HttpsURLConnection
    try {
        connection.connectTimeout = RemoteCatalogConfiguration.CONNECT_TIMEOUT_MS
        connection.readTimeout = RemoteCatalogConfiguration.READ_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        if (connection.responseCode != HttpsURLConnection.HTTP_OK) throw IOException("Catalog unavailable")
        val bytes = connection.inputStream.use { source ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                if (output.size() + count > RemoteCatalogConfiguration.MAX_RESPONSE_BYTES) throw IOException("Catalog too large")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return bytes.toString(Charsets.UTF_8)
    } finally { connection.disconnect() }
}
