package br.com.radioembarcada.model

data class ProgrammingRules(val songsBetweenInsertions: Int, val alternateStationIdAndJingle: Boolean,
    val insertionPattern: List<ProgramItemType> = DEFAULT_INSERTION_PATTERN) {
    init {
        require(songsBetweenInsertions > 0) // Campo legado do JSON, não espaça músicas nesta fase.
        require(insertionPattern.isNotEmpty() && insertionPattern.all {
            it == ProgramItemType.STATION_ID || it == ProgramItemType.JINGLE
        })
    }
    companion object {
        // Uma única inserção por troca: o jingle substitui a vinheta na terceira posição.
        val DEFAULT_INSERTION_PATTERN = listOf(ProgramItemType.STATION_ID,
            ProgramItemType.STATION_ID, ProgramItemType.JINGLE)
    }
}

/** Configuração interna da estação, independente do formato JSON e do catálogo musical. */
data class StationProgramming(
    val stationId: String,
    val version: Long,
    val rules: ProgrammingRules,
    val stationIds: List<ProgramItem>,
    val jingles: List<ProgramItem>,
) {
    init {
        require(stationId.isNotBlank() && version > 0)
        require(stationIds.all { it.type == ProgramItemType.STATION_ID })
        require(jingles.all { it.type == ProgramItemType.JINGLE })
    }
}
