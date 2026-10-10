package br.com.radioembarcada.programming

object ProgrammingConfiguration {
    const val MUSIC_REPEAT_INTERVAL_MS = 6L * 60 * 60 * 1_000
    const val ARTIST_REPEAT_INTERVAL_MS = 90L * 60 * 1_000
    const val MUSIC_HISTORY_LIMIT = 5_000
    const val BATCH_SIZE = 20
    const val CATALOG_LIMIT_PER_QUERY = 30
    const val CATALOG_PAGES = 4
    const val RECENT_HISTORY_SIZE = 60
    const val REFILL_REMAINING = 3
}
