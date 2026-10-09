package br.com.radioembarcada.news

import br.com.radioembarcada.data.news.NewsProvider
import br.com.radioembarcada.model.NewsItem
import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.network.news.RemoteNewsProvider
import br.com.radioembarcada.storage.NewsHistory
import br.com.radioembarcada.storage.NewsHistoryState
import kotlinx.coroutines.CancellationException

/** Candidato resolvido antes da fronteira; histórico só é consumido quando o drop começa. */
class NewsProgramming(private val provider: NewsProvider, private val history: NewsHistory,
    intervalMs: Long = NewsConfiguration.NEWS_INTERVAL_MS,
    private val now: () -> Long = System::currentTimeMillis, private val diagnostic: (String) -> Unit = {}) {
    private var saved = runCatching { history.read() }.getOrDefault(NewsHistoryState())
    val scheduler = NewsScheduler(intervalMs, saved.activeElapsedMs)
    private var candidate: NewsItem? = null
    private var reserved: NewsItem? = null
    private var occurrence = 0L
    private var started = false
    private val rejected = mutableSetOf<String>()
    private var lastLogMinute = -1L
    val hasReservation: Boolean get() = reserved != null

    suspend fun prefetch(): Boolean {
        if (hasReservation) return true
        candidate = try { provider.latestUnplayed(saved.playedNewsIds.toSet() + rejected) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { diagnostic("NewsProvider: falha; mantendo músicas"); null }
        return candidate != null
    }

    fun tick(monotonicMs: Long, playing: Boolean) {
        scheduler.tick(monotonicMs, playing)
        val minute = scheduler.elapsedMs / 60_000
        if (minute != lastLogMinute) {
            lastLogMinute = minute
            diagnostic("NewsScheduler: elapsed=${scheduler.elapsedMs} ms; newsDue=${scheduler.newsDue}; waiting safe boundary")
        }
    }

    fun reserve(remainingMs: Long): List<ProgramItem>? {
        if (hasReservation || !scheduler.boundaryWillBeDue(remainingMs)) return null
        val selected = candidate?.takeIf {
            it.audioUrl != null && RemoteNewsProvider.eligible(it, now(), saved.playedNewsIds.toSet() + rejected)
        }
        if (selected == null) {
            if (scheduler.newsDue) scheduler.retryLater()
            return null
        }
        candidate = null
        reserved = selected
        started = false
        val token = occurrence++
        return listOf(ProgramItem("news-intro:$token", ProgramItemType.NEWS_INTRO, "Notícias",
            NewsConfiguration.INTRO_URL, 0, artist = "Rádio Alce", source = "Rádio Alce", contentId = "news-intro"),
            selected.program(token))
    }

    fun onPlaying(item: ProgramItem) {
        if (item.type != ProgramItemType.NEWS_DROP || started) return
        val selected = reserved ?: return
        if (item.contentId != "news:${selected.id}") return
        started = true
        scheduler.blockStarted()
        saved = saved.copy(lastNewsPlayedAt = now(),
            playedNewsIds = (saved.playedNewsIds.filter { it != selected.id } + selected.id).takeLast(NewsConfiguration.HISTORY_LIMIT))
        persist()
    }

    fun finished() { reserved = null; started = false; persist() }
    fun abandonTimeline() {
        if (!started) candidate = reserved ?: candidate
        reserved = null; started = false
    }
    fun cancel() {
        reserved?.let { rejected += it.id }
        while (rejected.size > NewsConfiguration.HISTORY_LIMIT) rejected.remove(rejected.first())
        reserved = null; started = false
        scheduler.retryLater()
        diagnostic("News block cancelled; mantendo programação musical; nova tentativa posterior")
    }

    fun reservationStillEligible(): Boolean = reserved?.let {
        RemoteNewsProvider.eligible(it, now(), emptySet())
    } == true

    fun persist() {
        saved = saved.copy(activeElapsedMs = scheduler.elapsedMs)
        runCatching { history.write(saved) }.onFailure { diagnostic("NewsHistory: falha ao salvar; mantendo histórico em memória") }
    }
}
