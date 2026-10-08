package br.com.radioembarcada.data.music

import android.content.res.AssetManager
import android.media.MediaMetadataRetriever
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
) : MusicProvider {
    constructor(assets: AssetManager) : this(
        { path -> assets.list(path)?.toList().orEmpty() },
        { path -> readAssetMetadata(assets, path) },
    )

    override val requiresNetwork = false
    private val catalog: List<Track> by lazy {
        try {
            findMp3Assets(ROOT, listChildren).map { path ->
                val metadata = readMetadata(path)
                val filename = path.substringAfterLast('/').dropLast(4)
                val parts = filename.split(" - ", limit = 2)
                val artist = metadata.artist?.trim()?.takeIf { it.isNotEmpty() }
                    ?: if (parts.size == 2) parts[0].trim() else ""
                Track(id = path,
                    title = metadata.title?.trim()?.takeIf { it.isNotEmpty() }
                        ?: parts.last().trim(),
                    artist = artist.ifBlank { "Artista não informado" },
                    // Sem artista conhecido, não presumir que todos os arquivos são do mesmo artista.
                    artistId = artist.lowercase(Locale.ROOT).ifBlank { "unknown:$path" },
                    artworkUrl = null,
                    audioUrl = URI("asset", "", "/$path", null).toASCIIString(),
                    durationMs = metadata.durationMs?.takeIf { it > 0 } ?: 0L,
                    license = "", source = SOURCE, sourceUrl = "")
            }
        } catch (_: IOException) {
            throw MusicProviderException("Não foi possível ler a biblioteca local de testes.", false)
        }
    }

    override suspend fun fetchTracks(limit: Int, offset: Int): List<Track> = withContext(Dispatchers.IO) {
        require(limit > 0 && offset >= 0)
        val tracks = catalog
        if (tracks.isEmpty()) throw MusicProviderException(
            "Biblioteca local vazia. Adicione MP3 em assets/music e gere novamente o APK.", false)
        // Circular: o fim da biblioteca não encerra a estação nem gera IDs duplicados na página.
        val start = offset % tracks.size
        List(minOf(limit, tracks.size)) { tracks[(start + it) % tracks.size] }
    }

    companion object {
        const val ROOT = "music"
        const val SOURCE = "Local assets"
    }
}

internal data class LocalTrackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
)

internal fun findMp3Assets(root: String, listChildren: (String) -> List<String>): List<String> =
    buildList {
        fun visit(path: String) {
            val children = listChildren(path)
            if (children.isEmpty()) {
                if (path.endsWith(".mp3", ignoreCase = true)) add(path)
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
            )
        }
    } catch (_: IOException) {
        return LocalTrackMetadata()
    } catch (_: RuntimeException) {
        // Tags ausentes/corrompidas não removem a faixa; o ExoPlayer valida o áudio ao reproduzir.
        return LocalTrackMetadata()
    } finally {
        runCatching { retriever.release() }
    }
}
