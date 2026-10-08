package br.com.radioembarcada.data.music

import android.content.res.AssetManager
import android.media.MediaMetadataRetriever
import android.util.Log
import br.com.radioembarcada.BuildConfig
import br.com.radioembarcada.model.Track
import java.io.IOException
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Catálogo de desenvolvimento empacotado no APK, sem rede nem extração para o disco. */
class LocalAssetMusicProvider internal constructor(
    private val listChildren: (String) -> List<String>,
    private val readMetadata: (String) -> LocalTrackMetadata,
    private val diagnostic: (String) -> Unit = {},
) : MusicProvider {
    constructor(assets: AssetManager) : this(
        { path -> assets.list(path)?.toList().orEmpty() },
        { path -> readAssetMetadata(assets, path) },
        { message -> if (BuildConfig.DEBUG) Log.d("RadioDiagnostics", message) },
    )

    override val providesCompleteCatalog = true
    override val requiresNetwork = false
    private val catalog: List<Track> by lazy {
        try {
            findMp3Assets(ROOT, listChildren, diagnostic).also {
                diagnostic("Provider=LocalAssetMusicProvider; músicas encontradas=${it.size}")
            }.map { path ->
                val metadata = try {
                    readMetadata(path)
                } catch (_: IOException) {
                    diagnostic("Metadados ilegíveis: $path; usando nome da faixa")
                    LocalTrackMetadata()
                } catch (_: RuntimeException) {
                    diagnostic("Metadados inválidos: $path; usando nome da faixa")
                    LocalTrackMetadata()
                }
                val filename = path.substringAfterLast('/').dropLast(4)
                val parts = filename.split(" - ", limit = 2)
                val artist = metadata.artist?.trim()?.takeIf { it.isNotEmpty() }
                    ?: if (parts.size == 2) parts[0].trim() else ""
                Track(id = path,
                    title = metadata.title?.trim()?.takeIf { it.isNotEmpty() }
                        ?: filename.trim(),
                    artist = artist.ifBlank { "Rádio" },
                    // Sem artista conhecido, não presumir que todos os arquivos são do mesmo artista.
                    artistId = artist.lowercase(Locale.ROOT).ifBlank { "unknown:$path" },
                    artworkUrl = null,
                    audioUrl = URI("asset", "", "/$path", null).toASCIIString(),
                    durationMs = metadata.durationMs?.takeIf { it > 0 } ?: 0L,
                    license = "", source = SOURCE, sourceUrl = "", artworkData = metadata.artworkData?.takeIf { it.size <= MAX_ARTWORK_BYTES })
            }
        } catch (_: IOException) {
            throw MusicProviderException("Programação indisponível.", false)
        }
    }

    override suspend fun fetchTracks(limit: Int, offset: Int): List<Track> = withContext(Dispatchers.IO) {
        require(limit > 0 && offset >= 0)
        val tracks = catalog
        if (tracks.isEmpty()) throw MusicProviderException(
            "Programação indisponível.", false)
        // Circular: o fim da biblioteca não encerra a estação nem gera IDs duplicados na página.
        val start = offset % tracks.size
        List(minOf(limit, tracks.size)) { tracks[(start + it) % tracks.size] }
    }

    companion object {
        const val ROOT = "music"
        const val SOURCE = "Local assets"
        const val MAX_ARTWORK_BYTES = 256 * 1024
    }
}

internal data class LocalTrackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
    val artworkData: ByteArray? = null,
)

internal fun findMp3Assets(root: String, listChildren: (String) -> List<String>,
    diagnostic: (String) -> Unit = {}): List<String> =
    buildList {
        fun visit(path: String) {
            val children = try { listChildren(path) } catch (_: IOException) {
                diagnostic("Caminho ignorado por erro de leitura: $path")
                if (path == root) throw IOException("Catálogo inacessível")
                return
            }
            if (children.isEmpty()) {
                if (path.endsWith(".mp3", ignoreCase = true)) add(path)
                else if (path != root) diagnostic("Arquivo ignorado: $path")
            } else children.sorted().forEach { visit("$path/$it") }
        }
        visit(root)
    }

private fun readAssetMetadata(assets: AssetManager, path: String): LocalTrackMetadata {
    val retriever = MediaMetadataRetriever()
    try {
        assets.openFd(path).use { descriptor ->
            retriever.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            return LocalTrackMetadata(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                retriever.embeddedPicture,
            )
        }
    } finally {
        runCatching { retriever.release() }
    }
}
