package br.com.radioembarcada.model

data class NowPlaying(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long?,
    val license: String,
    val source: String,
    val sourceUrl: String,
)
