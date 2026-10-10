package br.com.radioembarcada.programming

import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.model.Track
import br.com.radioembarcada.storage.MusicHistory

/** Content identity survives session restarts and metadata/catalogue revisions. */
internal class MusicRepeatPolicy(
    private val history: MusicHistory,
    private val now: () -> Long,
    private val diagnostic: (String) -> Unit,
) {
    private val played = runCatching { history.read().toMutableMap() }.getOrElse {
        diagnostic("Histórico musical ilegível; iniciando histórico vazio"); mutableMapOf()
    }
    private val reserved = mutableMapOf<String, Long>()
    private val reservationOrder = mutableMapOf<String, Long>()
    private var sequence = 0L
    private var playingOccurrence: String? = null

    fun startSession() { reserved.clear(); reservationOrder.clear(); playingOccurrence = null }

    fun select(available: List<Track>): List<Track> {
        val timestamp = now()
        val eligible = available.filter { track ->
            lastUsed(track.key)?.let { timestamp - it >= ProgrammingConfiguration.MUSIC_REPEAT_INTERVAL_MS } != false
        }
        val unheard = eligible.filter { it.key !in played }
        if (unheard.isNotEmpty()) return unheard
        if (eligible.isNotEmpty()) return eligible
        // No unused/expired song remains outside the timeline. Rotate the oldest group,
        // rather than randomly favouring recently played/cached tracks from the whole catalogue.
        if (available.isNotEmpty()) diagnostic("Exceção de repetição: alternativas inéditas/fora das 6 h esgotadas")
        return available.sortedWith(compareBy<Track> { lastUsed(it.key) ?: Long.MIN_VALUE }
            .thenBy { reservationOrder[it.key] ?: Long.MIN_VALUE })
            .take(ProgrammingConfiguration.BATCH_SIZE)
    }

    fun scheduled(items: List<ProgramItem>) { items.forEach {
        reserved[it.contentId] = now(); reservationOrder[it.contentId] = sequence++
    } }

    fun onPlaying(item: ProgramItem) {
        if (playingOccurrence == item.id) return // Pause/resume is the same occurrence.
        playingOccurrence = item.id
        if (item.type != ProgramItemType.MUSIC) return
        played[item.contentId] = now()
        if (played.size > ProgrammingConfiguration.MUSIC_HISTORY_LIMIT) {
            val retain = played.entries.sortedByDescending { it.value }.take(ProgrammingConfiguration.MUSIC_HISTORY_LIMIT).associate { it.toPair() }
            played.clear(); played.putAll(retain)
        }
        runCatching { history.write(played) }.onFailure { diagnostic("Falha ao salvar histórico musical; histórico em memória preservado") }
    }

    private fun lastUsed(id: String): Long? = listOfNotNull(played[id], reserved[id]).maxOrNull()
}
