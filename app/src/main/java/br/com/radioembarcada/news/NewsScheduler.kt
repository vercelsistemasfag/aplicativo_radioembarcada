package br.com.radioembarcada.news

/** Relógio monotônico: pausa, foco e buffering não consomem o intervalo editorial. */
class NewsScheduler(val intervalMs: Long = NewsConfiguration.NEWS_INTERVAL_MS, elapsed: Long = 0) {
    var elapsedMs = elapsed.coerceAtLeast(0)
        private set
    private var lastTick: Long? = null
    private var wasPlaying = false
    private var retryAt = 0L
    val newsDue: Boolean get() = elapsedMs >= intervalMs

    init { require(intervalMs > 0) }

    fun tick(monotonicMs: Long, playing: Boolean) {
        lastTick?.let { if (wasPlaying) elapsedMs += (monotonicMs - it).coerceAtLeast(0) }
        lastTick = monotonicMs
        wasPlaying = playing
    }

    fun boundaryWillBeDue(remainingMs: Long): Boolean = remainingMs >= 0 &&
        elapsedMs >= retryAt && elapsedMs + remainingMs >= intervalMs

    fun retryLater() { retryAt = elapsedMs + NewsConfiguration.RETRY_INTERVAL_MS }
    fun blockStarted() { elapsedMs = 0; retryAt = 0 }
}
