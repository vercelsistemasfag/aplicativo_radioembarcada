package br.com.radioembarcada.network.jamendo

object JamendoConfiguration {
    const val TRACKS_ENDPOINT = "https://api.jamendo.com/v3.0/tracks/"
    val tagGroups = listOf(
        "synthwave+synthpop+newwave+retro",
        "rock+pop+softrock+alternative",
        "electronic+retro+instrumental",
    )
    const val AUDIO_FORMAT = "mp31" // 96 kbps: priorizar tolerância à rede neste teste.
    const val SPEED = "low+medium"
    const val DURATION_BETWEEN = "120_480"
    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 15_000
    val excludedTags = setOf("metal", "heavymetal", "hardrock", "hardcore", "aggressive", "explicit")
}
