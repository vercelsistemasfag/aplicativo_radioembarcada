package br.com.radioembarcada.model

/** Modelo interno, sem nomes de campos ou tipos específicos da API. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String,
    val artworkUrl: String?,
    val audioUrl: String,
    val durationMs: Long,
    val license: String,
    val source: String,
    val sourceUrl: String,
) {
    val key: String get() = "$source:$id"
    val artistKey: String get() = "$source:${artistId.ifBlank { artist.lowercase() }}"
}
