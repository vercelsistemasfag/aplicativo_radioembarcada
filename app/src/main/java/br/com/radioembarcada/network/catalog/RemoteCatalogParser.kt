package br.com.radioembarcada.network.catalog

import br.com.radioembarcada.model.Track
import java.net.URI
import java.util.Locale
import org.json.JSONObject

data class RemoteCatalog(val station: String, val version: Long, val tracks: List<Track>)

/** O JSON externo termina aqui; programação/player recebem somente modelos internos. */
object RemoteCatalogParser {
    fun parse(json: String): RemoteCatalog {
        val root = JSONObject(json)
        val version = root.opt("version") as? Number ?: error("Invalid version")
        require(version.toLong() >= 1 && version.toDouble() == version.toLong().toDouble())
        val station = (root.opt("station") as? String)?.trim().orEmpty()
        require(station.isNotEmpty())
        val items = root.getJSONArray("items")
        val tracks = (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            if (item.optString("type") != "MUSIC") return@mapNotNull null
            val id = (item.opt("id") as? String)?.trim().orEmpty()
            val title = (item.opt("title") as? String)?.trim().orEmpty()
            val url = (item.opt("url") as? String)?.trim().orEmpty()
            if (id.isEmpty() || title.isEmpty() || !isPublicAudioUrl(url)) return@mapNotNull null
            val artist = (item.opt("artist") as? String)?.trim().orEmpty()
            Track(id, title, artist, artist.lowercase(Locale.ROOT).ifBlank { "unknown:$id" },
                null, url, (item.opt("durationMs") as? Number)?.toLong()?.coerceAtLeast(0) ?: 0,
                "", "R2 catalog", "")
        }.distinctBy { it.key }
        require(tracks.isNotEmpty())
        return RemoteCatalog(station, version.toLong(), tracks)
    }

    private fun isPublicAudioUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
    }.getOrDefault(false)
}
