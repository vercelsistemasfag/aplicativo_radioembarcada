package br.com.radioembarcada.programming

import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.model.StationProgramming
import kotlin.random.Random

/** Uma inserção em cada troca. O ciclo e os bags persistem entre lotes e Pause/Play. */
internal class ProgrammingSequencer(private val diagnostic: (String) -> Unit = {},
    private val random: Random = Random.Default) {
    private var station: String? = null
    private var version: Long? = null
    private var pattern = emptyList<ProgramItemType>()
    private var insertionPosition = 0
    private var stations = ShuffleBag(random)
    private var jingles = ShuffleBag(random)
    private var occurrence = 0L

    fun startSession() {
        station = null
        version = null
        pattern = emptyList()
        insertionPosition = 0
        stations = ShuffleBag(random)
        jingles = ShuffleBag(random)
    }

    fun interleave(music: List<ProgramItem>, configuration: StationProgramming?): List<ProgramItem> {
        require(music.all { it.type == ProgramItemType.MUSIC })
        if (configuration == null) return music
        if (station != configuration.stationId) { startSession(); station = configuration.stationId }
        if (version != configuration.version) {
            stations.invalidate()
            jingles.invalidate()
            version = configuration.version
            diagnostic("Programming config version: $version; Station IDs loaded: ${configuration.stationIds.size}; Jingles loaded: ${configuration.jingles.size}")
        }
        if (pattern != configuration.rules.insertionPattern) {
            pattern = configuration.rules.insertionPattern.toList()
            insertionPosition = 0
        }
        return buildList {
            music.forEach { track ->
                add(track)
                val requested = pattern[insertionPosition]
                diagnostic("Fila: troca ${insertionPosition + 1}/${pattern.size}; item=${track.id}; inserção=$requested")
                val selected = when (requested) {
                    ProgramItemType.STATION_ID -> stations.next(configuration.stationIds) ?: jingles.next(configuration.jingles)
                    else -> jingles.next(configuration.jingles) ?: stations.next(configuration.stationIds)
                }
                selected?.let { piece ->
                    val scheduled = piece.copy(id = "${piece.id}:occurrence:${occurrence++}")
                    add(scheduled)
                    diagnostic("Inserção selecionada: ${piece.type}; conteúdo=${piece.contentId}; item=${scheduled.id}")
                    diagnostic("${if (piece.type == ProgramItemType.STATION_ID) "StationIdBag" else "JingleBag"} remaining: ${if (piece.type == ProgramItemType.STATION_ID) stations.remaining else jingles.remaining}; Selected: ${piece.contentId.substringAfterLast(':')}")
                }
                insertionPosition = (insertionPosition + 1) % pattern.size
            }
        }
    }
}
