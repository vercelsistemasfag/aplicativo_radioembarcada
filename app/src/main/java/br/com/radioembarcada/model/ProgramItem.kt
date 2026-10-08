package br.com.radioembarcada.model

enum class ProgramItemType { MUSIC, STATION_ID, JINGLE, ADVERTISEMENT, ANNOUNCEMENT }

/** Conteúdo genérico da programação; o player não conhece o catálogo de origem. */
data class ProgramItem(
    val id: String,
    val type: ProgramItemType,
    val title: String,
    val audioUrl: String,
    val durationMs: Long,
    val artist: String = "",
    val artistKey: String = "",
    val artworkUrl: String? = null,
    val license: String = "",
    val source: String = "",
    val sourceUrl: String = "",
) {
    companion object {
        fun music(track: Track) = ProgramItem(
            track.key, ProgramItemType.MUSIC, track.title, track.audioUrl, track.durationMs,
            track.artist, track.artistKey, track.artworkUrl, track.license, track.source, track.sourceUrl,
        )
    }
}
