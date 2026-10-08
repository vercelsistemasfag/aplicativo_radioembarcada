package br.com.radioembarcada.programming

import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.data.music.MusicProviderException
import br.com.radioembarcada.model.ProgramItem

class AutomaticProgramming(private val provider: MusicProvider,
    private val queueBuilder: QueueBuilder = QueueBuilder()) {
    private var page = 0
    private val recentlyScheduled = ArrayDeque<String>()

    suspend fun nextBatch(previous: ProgramItem? = null, queuedIds: Set<String> = emptySet()): List<ProgramItem> {
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
}
