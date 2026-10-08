package br.com.radioembarcada.programming

import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.data.music.MusicProviderException
import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.Track

class AutomaticProgramming(private val provider: MusicProvider,
    private val queueBuilder: QueueBuilder = QueueBuilder()) {
    private var localCatalog: List<Track>? = null
    private val remainingLocal = mutableListOf<Track>()
    private var page = 0
    private val recentlyScheduled = ArrayDeque<String>()

    suspend fun nextBatch(previous: ProgramItem? = null, queuedIds: Set<String> = emptySet(),
        excludedIds: Set<String> = emptySet()): List<ProgramItem> {
        if (provider.providesCompleteCatalog) return nextLocalBatch(previous, queuedIds, excludedIds)
        val catalog = provider.fetchTracks(ProgrammingConfiguration.CATALOG_LIMIT_PER_QUERY,
            page * ProgrammingConfiguration.CATALOG_LIMIT_PER_QUERY)
        page = (page + 1) % ProgrammingConfiguration.CATALOG_PAGES
        val available = catalog.filter { it.key !in queuedIds }
        val fresh = available.filter { it.key !in recentlyScheduled }
        val batch = queueBuilder.build(fresh, previous = previous).ifEmpty {
            // Reutilizar faixas antigas somente quando necessário, preservando a fronteira.
            queueBuilder.build(available, previous = previous)
        }
        if (batch.isEmpty()) throw MusicProviderException(
            "Não há faixas elegíveis para continuar a programação. Tente novamente.", true)
        batch.forEach { recentlyScheduled.addLast(it.id) }
        while (recentlyScheduled.size > ProgrammingConfiguration.RECENT_HISTORY_SIZE) recentlyScheduled.removeFirst()
        return batch
    }

    private suspend fun nextLocalBatch(previous: ProgramItem?, queuedIds: Set<String>, excludedIds: Set<String>): List<ProgramItem> {
        val catalog = localCatalog ?: provider.fetchTracks(Int.MAX_VALUE, 0).distinctBy { it.key }
            .also { localCatalog = it }
        if (catalog.isEmpty()) throw MusicProviderException("Programação indisponível.", false)
        remainingLocal.removeAll { it.key in excludedIds }
        if (remainingLocal.isEmpty()) remainingLocal.addAll(catalog.filter { it.key !in excludedIds })
        val batch = queueBuilder.build(remainingLocal.filter { it.key !in queuedIds }, previous = previous,
            allowSameArtist = true, allowSingleTrackRepeat = catalog.count { it.key !in excludedIds } == 1 && queuedIds.isEmpty())
        val scheduled = batch.map { it.id }.toSet()
        remainingLocal.removeAll { it.key in scheduled }
        return batch
    }
}
