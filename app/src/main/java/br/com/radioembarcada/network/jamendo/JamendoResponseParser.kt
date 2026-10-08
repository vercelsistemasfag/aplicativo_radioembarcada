package br.com.radioembarcada.network.jamendo

import br.com.radioembarcada.data.music.MusicProviderException
import org.json.JSONObject

object JamendoResponseParser {
    fun parse(json: String): List<JamendoTrackDto> {
        val document = JSONObject(json)
        if (document.optJSONObject("headers")?.optString("status") != "success") {
            throw MusicProviderException("O catálogo recusou a consulta. Verifique a configuração do provedor.", false)
        }
        val results = document.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { index ->
            val track = results.optJSONObject(index) ?: return@mapNotNull null
            val tagObject = track.optJSONObject("musicinfo")?.optJSONObject("tags")
            val tags = buildSet {
                listOf("genres", "instruments", "vartags").forEach { field ->
                    val array = tagObject?.optJSONArray(field)
                    if (array != null) for (i in 0 until array.length()) add(array.optString(i).lowercase())
                }
            }
            JamendoTrackDto(
                track.optString("id"), track.optString("name"), track.optString("artist_name"),
                track.optString("artist_id"), track.optString("image").ifBlank { track.optString("album_image") },
                track.optString("audio"), track.optLong("duration"), track.optString("license_ccurl"),
                track.optBoolean("audiodownload_allowed", false), tags,
            )
        }
    }
}
