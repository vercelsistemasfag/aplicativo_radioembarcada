package br.com.radioembarcada.programming

import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.model.Track
import br.com.radioembarcada.storage.MusicHistory
import java.util.Locale

/** Content identity survives session restarts and metadata/catalogue revisions. */
internal class MusicRepeatPolicy(
    private val history: MusicHistory,
    private val now: () -> Long,
    private val diagnostic: (String) -> Unit,
) {
    private val played = runCatching { history.read().toMutableMap() }.getOrElse {
        diagnostic("Histórico musical ilegível; iniciando histórico vazio"); mutableMapOf()
    }
    private val playedArtists = runCatching { history.readArtists().toMutableMap() }.getOrElse {
        diagnostic("Histórico de artistas ilegível; iniciando histórico vazio"); mutableMapOf()
    }
    private val reservedArtists = mutableMapOf<String, Long>()
    private val reserved = mutableMapOf<String, Long>()
    private val reservationOrder = mutableMapOf<String, Long>()
    private var sequence = 0L
    private var playingOccurrence: String? = null

    fun startSession() { reserved.clear(); reservedArtists.clear(); reservationOrder.clear(); playingOccurrence = null }

    fun select(available: List<Track>): List<Track> {
        val timestamp = now()
        val eligible = available.filter { track ->
            lastUsed(track.key)?.let { timestamp - it >= ProgrammingConfiguration.MUSIC_REPEAT_INTERVAL_MS } != false
        }
        if (eligible.isNotEmpty()) return eligible
        // No unused/expired song remains outside the timeline. Rotate the oldest group,
        // rather than randomly favouring recently played/cached tracks from the whole catalogue.
        if (available.isNotEmpty()) diagnostic("Exceção de repetição: alternativas inéditas/fora das 6 h esgotadas")
        return available.sortedWith(compareBy<Track> { lastUsed(it.key) ?: Long.MIN_VALUE }
            .thenBy { reservationOrder[it.key] ?: Long.MIN_VALUE })
            .take(ProgrammingConfiguration.BATCH_SIZE)
    }

    /** Evaluated after every selection, including songs already reserved in this batch. */
    fun selectArtists(available: List<Track>): List<Track> {
        val eligible = available.filter { track ->
            val artist = artistIdentity(track.artist, track.artistKey)
            artist == null || listOfNotNull(playedArtists[artist], reservedArtists[artist]).maxOrNull()
                ?.let { now() - it >= ProgrammingConfiguration.ARTIST_REPEAT_INTERVAL_MS } != false
        }
        if (eligible.isNotEmpty()) return preferUnheard(eligible)
        if (available.isNotEmpty()) diagnostic("Exceção de artista: opções fora dos 90 min esgotadas")
        // Prefer the least recently used artist when all remaining alternatives are blocked.
        val oldest = available.minOfOrNull {
            val artist = artistIdentity(it.artist, it.artistKey)
            listOfNotNull(playedArtists[artist], reservedArtists[artist]).maxOrNull() ?: Long.MIN_VALUE
        }
        return available.filter {
            val artist = artistIdentity(it.artist, it.artistKey)
            (listOfNotNull(playedArtists[artist], reservedArtists[artist]).maxOrNull() ?: Long.MIN_VALUE) == oldest
        }
    }

    fun scheduled(items: List<ProgramItem>) { items.forEach {
        reserved[it.contentId] = now(); reservationOrder[it.contentId] = sequence++
        artistIdentity(it.artist, it.artistKey)?.let { artist -> reservedArtists[artist] = now() }
    } }

    fun onPlaying(item: ProgramItem) {
        if (playingOccurrence == item.id) return // Pause/resume is the same occurrence.
        playingOccurrence = item.id
        if (item.type != ProgramItemType.MUSIC) return
        played[item.contentId] = now()
        artistIdentity(item.artist, item.artistKey)?.let { playedArtists[it] = now() }
        if (playedArtists.size > ProgrammingConfiguration.MUSIC_HISTORY_LIMIT) {
            val retain = playedArtists.entries.sortedByDescending { it.value }
                .take(ProgrammingConfiguration.MUSIC_HISTORY_LIMIT).associate { it.toPair() }
            playedArtists.clear(); playedArtists.putAll(retain)
        }
        if (played.size > ProgrammingConfiguration.MUSIC_HISTORY_LIMIT) {
            val retain = played.entries.sortedByDescending { it.value }.take(ProgrammingConfiguration.MUSIC_HISTORY_LIMIT).associate { it.toPair() }
            played.clear(); played.putAll(retain)
        }
        runCatching { history.writeArtists(playedArtists) }.onFailure { diagnostic("Falha ao salvar histórico de artistas; histórico em memória preservado") }
        runCatching { history.write(played) }.onFailure { diagnostic("Falha ao salvar histórico musical; histórico em memória preservado") }
    }

    private fun preferUnheard(tracks: List<Track>): List<Track> =
        tracks.filter { it.key !in played }.ifEmpty { tracks }

    private fun artistIdentity(name: String, key: String): String? =
        name.trim().takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT)
            ?: key.takeIf { it.isNotBlank() && !it.contains("unknown:") && !it.endsWith(":") }

    private fun lastUsed(id: String): Long? = listOfNotNull(played[id], reserved[id]).maxOrNull()
}
