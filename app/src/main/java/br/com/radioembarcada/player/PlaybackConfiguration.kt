package br.com.radioembarcada.player

object PlaybackConfiguration {
    const val MIN_BUFFER_MS = 30_000
    const val STEADY_LOW_WATER_MS = 45_000
    const val DESIRED_BUFFER_MS = 60_000
    const val MAX_BUFFER_MS = 90_000
    const val START_BUFFER_MS = 1_500
    const val REBUFFER_MS = 3_000
    const val NEXT_TRACK_PRELOAD_MS = 45_000L
    const val SECOND_TRACK_PRELOAD_MS = 15_000L
    const val CACHE_BYTES = 200L * 1024 * 1024
    const val CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1_000
    const val STATE_POLL_MS = 2_000L
    const val LOADING_CHECK_INTERVAL_BYTES = 64 * 1024
    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 10_000

    fun shouldFill(bufferedMs: Long, filling: Boolean): Boolean = when {
        bufferedMs >= DESIRED_BUFFER_MS -> false
        bufferedMs < STEADY_LOW_WATER_MS -> true
        else -> filling
    }
}
