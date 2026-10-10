package br.com.radioembarcada.programming

import br.com.radioembarcada.data.music.MusicProvider
import br.com.radioembarcada.data.music.MusicProviderException
import br.com.radioembarcada.data.programming.ProgrammingProvider
import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.model.Track
import kotlinx.coroutines.CancellationException
import br.com.radioembarcada.storage.MusicHistory
import br.com.radioembarcada.storage.MemoryMusicHistory

class AutomaticProgramming(private val provider: MusicProvider,
    private val queueBuilder: QueueBuilder = QueueBuilder(),
    private val programmingProvider: ProgrammingProvider? = null,
    private val diagnostic: (String) -> Unit = {},
    history: MusicHistory = MemoryMusicHistory(),
    now: () -> Long = System::currentTimeMillis) {
    private val repetition = MusicRepeatPolicy(history, now, diagnostic)
    fun onPlaying(item: ProgramItem) = repetition.onPlaying(item)
    private val sequencer = ProgrammingSequencer(diagnostic)
    private var lastMusic: ProgramItem? = null
    private var page = 0
    private val recentlyScheduled = ArrayDeque<String>()
    private val deferredInserts = ArrayDeque<ProgramItem>()
    private var newsRollback: Pair<List<ProgramItem>, List<ProgramItem>>? = null

    fun startSession() { lastMusic = null; repetition.startSession(); sequencer.startSession(); deferredInserts.clear(); newsRollback = null }

    fun insertNews(upcoming: List<ProgramItem>, block: List<ProgramItem>): List<ProgramItem> {
        check(newsRollback == null)
        val plan = NewsQueuePlanner.insert(upcoming, block)
        newsRollback = upcoming.toList() to deferredInserts.toList()
        plan.deferred.asReversed().forEach(deferredInserts::addFirst)
        return plan.upcoming
    }

    fun cancelPendingNews(): List<ProgramItem> {
        val rollback = checkNotNull(newsRollback)
        deferredInserts.clear(); deferredInserts.addAll(rollback.second)
        newsRollback = null
        return rollback.first
    }

    fun newsEntered() { newsRollback = null }

    suspend fun nextBatch(previous: ProgramItem? = null, queuedIds: Set<String> = emptySet(),
        excludedIds: Set<String> = emptySet(), prefer: (ProgramItem) -> Boolean = { false }): List<ProgramItem> {
        val configuration = try { programmingProvider?.load() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { diagnostic("Falha de carregamento da programação; continuando com músicas."); null }
        val musicPrevious = previous?.takeIf { it.type == ProgramItemType.MUSIC } ?: lastMusic
        val music = nextMusicBatch(musicPrevious, queuedIds, excludedIds, prefer)
        lastMusic = music.lastOrNull() ?: lastMusic
        val retained = List(minOf(deferredInserts.size, music.size)) { deferredInserts.removeFirst() }
        return sequencer.interleave(music, configuration, alreadyScheduled = retained)
    }

    private suspend fun nextMusicBatch(previous: ProgramItem?, queuedIds: Set<String>,
        excludedIds: Set<String>, prefer: (ProgramItem) -> Boolean): List<ProgramItem> {
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
        if (catalog.isEmpty()) throw MusicProviderException("Programação indisponível.", false)
        repetition.reconcile(catalog)
        val boundary = previous ?: catalog.find { it.key == repetition.lastTrackId }?.let(ProgramItem::music)
        val opening = mutableListOf<String>()
        val available = catalog.filter { it.key !in queuedIds && it.key !in excludedIds }
        val candidates = repetition.select(available)
        val batch = queueBuilder.build(candidates, previous = boundary,
            allowSameArtist = true, allowSingleTrackRepeat = catalog.count { it.key !in excludedIds } == 1 && queuedIds.isEmpty(), prefer = prefer,
            selectCandidates = { repetition.avoidOpeningRepeat(repetition.selectArtists(it), opening) },
            onSelected = { repetition.scheduled(listOf(it)); opening += it.contentId })
        repetition.openingBuilt(batch)
        return batch
    }
}
