package br.com.radioembarcada.network.jamendo

import br.com.radioembarcada.model.Track
import java.net.URI

object JamendoTrackMapper {
    private val licensePath = Regex("""/licenses/(by|by-sa|by-nd|by-nc|by-nc-sa|by-nc-nd)/(2\.0|2\.5|3\.0|4\.0)/?""")

    fun toTrack(dto: JamendoTrackDto): Track? {
        val license = runCatching { URI(dto.licenseUrl) }.getOrNull() ?: return null
        if (license.host != "creativecommons.org" || !licensePath.matches(license.path.orEmpty()) ||
            license.scheme !in setOf("http", "https")) return null
        // Cache operacional somente quando o catálogo autoriza cópia/download.
        if (!dto.downloadAllowed || dto.id.isBlank() || dto.name.isBlank() || dto.artistName.isBlank()) return null
        if (dto.durationSeconds !in 120..480 || !isHttps(dto.audio)) return null
        val normalizedTags = dto.tags.map { it.replace(" ", "").replace("-", "") }.toSet()
        if (normalizedTags.any { it in JamendoConfiguration.excludedTags }) return null
        return Track(dto.id, dto.name, dto.artistName, dto.artistId,
            dto.image.takeIf(::isHttps), dto.audio, dto.durationSeconds * 1_000,
            dto.licenseUrl.replaceFirst("http://", "https://"), "Jamendo",
            "https://www.jamendo.com/track/${dto.id}")
    }

    private fun isHttps(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
