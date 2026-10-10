package br.com.radioembarcada.programming

import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.model.Track
import br.com.radioembarcada.storage.MusicHistory
import br.com.radioembarcada.storage.PlaybackHistoryState
import java.util.Locale

/** Persistent execution history is independent of the ephemeral queue and its reservations. */
internal class MusicRepeatPolicy(
    private val history: MusicHistory,
    private val now: () -> Long,
    private val diagnostic: (String) -> Unit,
) {
    private var state = load(PlaybackHistoryState())
    private val reserved = mutableMapOf<String, Long>()
    private val reservedArtists = mutableMapOf<String, Long>()
    private val reservationOrder = mutableMapOf<String, Long>()
    private var sequence = 0L
    private var playingOccurrence: String? = null
    private var openingPending = true
    val lastTrackId: String? get() = state.lastTrackId

    fun startSession() {
        state = load(state)
        reserved.clear(); reservedArtists.clear(); reservationOrder.clear()
        playingOccurrence = null; openingPending = true
    }

    /** Called only with a complete valid catalogue, never with a partial page/failed fetch. */
    fun reconcile(catalog: List<Track>) {
        val ids = catalog.map { it.key }.toSet()
        val artists = catalog.mapNotNull { artistIdentity(it.artist, it.artistKey) }.toSet()
        val cleaned = state.copy(playedAt = retained(state.playedAt.filterKeys { it in ids }),
            artistsPlayedAt = retained(state.artistsPlayedAt.filterKeys { it in artists }),
            lastTrackId = state.lastTrackId?.takeIf { it in ids },
            lastSessionOpeningSequence = state.lastSessionOpeningSequence.filter { it in ids })
        if (cleaned != state) { state = cleaned; persist() }
        reserved.keys.retainAll(ids); reservationOrder.keys.retainAll(ids)
        reservedArtists.keys.retainAll(artists)
    }

    fun select(available: List<Track>): List<Track> {
        for (window in ProgrammingConfiguration.MUSIC_REPEAT_RELAXATION_WINDOWS) {
            val eligible = available.filter { track ->
                window == 0L || lastUsed(track.key)?.let { now() - it >= window } != false
            }
            if (window == ProgrammingConfiguration.MUSIC_REPEAT_BLOCK_WINDOW) diagnostic(
                "PlaybackHistory: entries loaded=${state.playedAt.size}; last played=${state.lastTrackId}; " +
                    "blocked by 6h rule=${available.size - eligible.size}; eligible now=${eligible.size}")
            if (eligible.isNotEmpty()) {
                if (window < ProgrammingConfiguration.MUSIC_REPEAT_BLOCK_WINDOW) diagnostic(
                    "PlaybackHistory: catálogo insuficiente; janela relaxada para ${window / 60_000} min; priorizando menos recentes")
                return if (window == ProgrammingConfiguration.MUSIC_REPEAT_BLOCK_WINDOW) eligible
                    else eligible.sortedWith(recencyOrder()).take(ProgrammingConfiguration.BATCH_SIZE)
            }
        }
        return emptyList()
    }

    /** Re-evaluated per selection, including artists already reserved in this batch. */
    fun selectArtists(available: List<Track>): List<Track> {
        val eligible = available.filter {
            artistLastUsed(artistIdentity(it.artist, it.artistKey))
                ?.let { time -> now() - time >= ProgrammingConfiguration.ARTIST_REPEAT_INTERVAL_MS } != false
        }
        if (eligible.isNotEmpty()) return oldestPool(eligible)
        if (available.isNotEmpty()) diagnostic("Exceção de artista: opções fora dos 90 min esgotadas")
        val oldest = available.minOfOrNull { artistLastUsed(artistIdentity(it.artist, it.artistKey)) ?: Long.MIN_VALUE }
        return oldestPool(available.filter { (artistLastUsed(artistIdentity(it.artist, it.artistKey)) ?: Long.MIN_VALUE) == oldest })
    }

    /** Break the old prefix at the first possible choice; only five IDs are ever persisted. */
    fun avoidOpeningRepeat(available: List<Track>, prefix: List<String>): List<Track> {
        val old = state.lastSessionOpeningSequence
        if (!openingPending || prefix.size >= old.size || prefix != old.take(prefix.size)) return available
        return available.filter { it.key != old[prefix.size] }.ifEmpty { available }
    }

    fun openingBuilt(items: List<ProgramItem>) {
        if (!openingPending || items.isEmpty()) return
        openingPending = false
        state = state.copy(lastSessionOpeningSequence = items.take(ProgrammingConfiguration.SESSION_OPENING_SIZE).map { it.contentId })
        persist()
        diagnostic("New session queue: first ids=${state.lastSessionOpeningSequence.joinToString()}")
    }

    fun scheduled(items: List<ProgramItem>) { items.filter { it.type == ProgramItemType.MUSIC }.forEach {
        reserved[it.contentId] = now(); reservationOrder[it.contentId] = sequence++
        artistIdentity(it.artist, it.artistKey)?.let { artist -> reservedArtists[artist] = now() }
    } }

    fun onPlaying(item: ProgramItem) {
        if (playingOccurrence == item.id) return // Pause/resume must not extend the block window.
        playingOccurrence = item.id
        if (item.type != ProgramItemType.MUSIC) return
        val artist = artistIdentity(item.artist, item.artistKey)
        state = state.copy(playedAt = retained(state.playedAt + (item.contentId to now())),
            artistsPlayedAt = retained(if (artist == null) state.artistsPlayedAt else state.artistsPlayedAt + (artist to now())),
            lastTrackId = item.contentId)
        persist() // Commit at actual playback, not shutdown/preload.
    }

    /** New tracks first; shuffle inside the oldest group, never cache affinity over history. */
    private fun oldestPool(available: List<Track>): List<Track> {
        val candidates = available.filter { it.key !in state.playedAt }.ifEmpty { available }
        val order = recencyOrder()
        val cutoff = candidates.sortedWith(order).getOrNull(minOf(ProgrammingConfiguration.BATCH_SIZE, candidates.size) - 1)
            ?: return emptyList()
        return candidates.filter { order.compare(it, cutoff) <= 0 }
    }

    private fun recencyOrder() = compareBy<Track> { lastUsed(it.key) ?: Long.MIN_VALUE }
        .thenBy { reservationOrder[it.key] ?: Long.MIN_VALUE }

    private fun retained(values: Map<String, Long>) = values.entries
        .filter { now() - it.value <= ProgrammingConfiguration.MUSIC_HISTORY_RETENTION_MS }
        .sortedByDescending { it.value }.take(ProgrammingConfiguration.MUSIC_HISTORY_LIMIT).associate { it.toPair() }

    private fun artistIdentity(name: String, key: String): String? {
        if (key.contains("unknown:")) return null
        return name.trim().takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)
            ?: key.takeIf { it.isNotBlank() && !it.endsWith(":") }
    }
    private fun lastUsed(id: String) = listOfNotNull(state.playedAt[id], reserved[id]).maxOrNull()
    private fun artistLastUsed(id: String?) = listOfNotNull(state.artistsPlayedAt[id], reservedArtists[id]).maxOrNull()
    private fun load(fallback: PlaybackHistoryState): PlaybackHistoryState = runCatching { history.readState() }.getOrElse {
        diagnostic("PlaybackHistory: histórico ilegível; preservando estado disponível em memória"); fallback
    }
    private fun persist() {
        runCatching { history.writeState(state) }.onFailure {
            diagnostic("PlaybackHistory: falha no checkpoint (${it.javaClass.simpleName}); estado em memória preservado")
        }
    }
}
