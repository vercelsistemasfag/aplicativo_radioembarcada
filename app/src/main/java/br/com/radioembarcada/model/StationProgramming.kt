package br.com.radioembarcada.model

data class ProgrammingRules(val songsBetweenInsertions: Int, val alternateStationIdAndJingle: Boolean) {
    init { require(songsBetweenInsertions > 0) }
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
