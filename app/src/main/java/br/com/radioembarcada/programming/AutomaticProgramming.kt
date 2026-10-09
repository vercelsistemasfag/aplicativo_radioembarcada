package br.com.radioembarcada.programming

import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.data.music.MusicProviderException
import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.Track

class AutomaticProgramming(private val provider: MusicProvider,
    private val queueBuilder: QueueBuilder = QueueBuilder()) {
    private var completeCatalog: List<Track>? = null
    private val remaining = mutableListOf<Track>()
    private var revision: Long? = null
    private var page = 0
    private val recentlyScheduled = ArrayDeque<String>()

    suspend fun nextBatch(previous: ProgramItem? = null, queuedIds: Set<String> = emptySet(),
        excludedIds: Set<String> = emptySet(), prefer: (ProgramItem) -> Boolean = { false }): List<ProgramItem> {
        if (provider.providesCompleteCatalog) return nextCompleteBatch(previous, queuedIds, excludedIds, prefer)
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

    private suspend fun nextCompleteBatch(previous: ProgramItem?, queuedIds: Set<String>, excludedIds: Set<String>,
        prefer: (ProgramItem) -> Boolean): List<ProgramItem> {
        val catalog = provider.fetchTracks(Int.MAX_VALUE, 0).distinctBy { it.key }
        if (completeCatalog != catalog || revision != provider.catalogRevision) {
            completeCatalog = catalog
            revision = provider.catalogRevision
            remaining.clear()
        }
        if (catalog.isEmpty()) throw MusicProviderException("Programação indisponível.", false)
        remaining.removeAll { it.key in excludedIds }
        if (remaining.isEmpty()) remaining.addAll(catalog.filter { it.key !in excludedIds })
        val batch = queueBuilder.build(remaining.filter { it.key !in queuedIds }, previous = previous,
            allowSameArtist = true, allowSingleTrackRepeat = catalog.count { it.key !in excludedIds } == 1 && queuedIds.isEmpty(), prefer = prefer)
        val scheduled = batch.map { it.id }.toSet()
        remaining.removeAll { it.key in scheduled }
        return batch
    }
}
