package br.com.radioembarcada.programming

import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.model.ProgrammingRules
import br.com.radioembarcada.model.StationProgramming
import kotlin.random.Random

/** Planeja somente itens futuros; mantém contador e alternância entre lotes da rádio. */
internal class ProgrammingSequencer(private val diagnostic: (String) -> Unit = {},
    private val random: Random = Random.Default) {
    private var station: String? = null
    private var rules: ProgrammingRules? = null
    private var songs = 0
    private var nextType = ProgramItemType.STATION_ID
    private var stationIndex = 0L
    private var jingleIndex = 0L
    private var occurrence = 0L

    fun startSession() {
        // A fila anterior foi liberada com o serviço; não reutilizar sua contagem antecipada.
        station = null
        rules = null
        songs = 0
        nextType = ProgramItemType.STATION_ID
    }

    fun interleave(music: List<ProgramItem>, configuration: StationProgramming?): List<ProgramItem> {
        require(music.all { it.type == ProgramItemType.MUSIC })
        if (configuration == null) { songs = 0; return music }
        if (station != configuration.stationId) {
            station = configuration.stationId
            nextType = ProgramItemType.STATION_ID
            stationIndex = 0
            jingleIndex = 0
            songs = 0
        }
        if (rules != configuration.rules) songs = 0
        rules = configuration.rules
        val interval = configuration.rules.songsBetweenInsertions
        return buildList {
            music.forEach { track ->
                add(track)
                songs++
                diagnostic("Fila: música $songs/$interval; item=${track.id}")
                if (songs >= interval) {
                    select(configuration)?.let { piece ->
                        // IDs da ocorrência são únicos; contentId mantém o mesmo áudio no cache.
                        val scheduled = piece.copy(id = "${piece.id}:occurrence:${occurrence++}")
                        add(scheduled)
                        diagnostic("Inserção selecionada: ${piece.type}; conteúdo=${piece.id}; item=${scheduled.id}")
                        nextType = if (piece.type == ProgramItemType.STATION_ID) ProgramItemType.JINGLE
                            else ProgramItemType.STATION_ID
                    }
                    songs = 0
                }
            }
        }
    }

    private fun select(configuration: StationProgramming): ProgramItem? {
        val ids = configuration.stationIds
        val jingles = configuration.jingles
        if (ids.isEmpty() && jingles.isEmpty()) return null
        val type = if (configuration.rules.alternateStationIdAndJingle) nextType
            else if (random.nextBoolean()) ProgramItemType.STATION_ID else ProgramItemType.JINGLE
        return if ((type == ProgramItemType.STATION_ID && ids.isNotEmpty()) || jingles.isEmpty())
            ids[(stationIndex++ % ids.size).toInt()]
        else jingles[(jingleIndex++ % jingles.size).toInt()]
    }
}
