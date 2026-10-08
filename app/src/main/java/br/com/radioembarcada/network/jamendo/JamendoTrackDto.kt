package br.com.radioembarcada.network.jamendo

/** DTO privado ao adaptador do catálogo, independente dos modelos de reprodução. */
data class JamendoTrackDto(
    val id: String,
    val name: String,
    val artistName: String,
    val artistId: String,
    val image: String,
    val audio: String,
    val durationSeconds: Long,
    val licenseUrl: String,
    val downloadAllowed: Boolean,
    val tags: Set<String>,
)
