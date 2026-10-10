package br.com.radioembarcada.programming

object ProgrammingConfiguration {
    const val MUSIC_REPEAT_BLOCK_WINDOW = 6L * 60 * 60 * 1_000
    const val MUSIC_REPEAT_INTERVAL_MS = MUSIC_REPEAT_BLOCK_WINDOW
    val MUSIC_REPEAT_RELAXATION_WINDOWS = listOf(MUSIC_REPEAT_BLOCK_WINDOW,
        MUSIC_REPEAT_BLOCK_WINDOW / 2, MUSIC_REPEAT_BLOCK_WINDOW / 4, 0L)
    const val MUSIC_HISTORY_RETENTION_MS = 48L * 60 * 60 * 1_000
    const val SESSION_OPENING_SIZE = 5
    const val ARTIST_REPEAT_INTERVAL_MS = 90L * 60 * 1_000
    const val MUSIC_HISTORY_LIMIT = 500
    const val BATCH_SIZE = 20
    const val CATALOG_LIMIT_PER_QUERY = 30
    const val CATALOG_PAGES = 4
    const val RECENT_HISTORY_SIZE = 60
    const val REFILL_REMAINING = 3
}
