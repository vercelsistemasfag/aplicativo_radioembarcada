package br.com.radioembarcada.programming

import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType

/** O bloco editorial ocupa UM intervalo, adiando a inserção normal sem consumir seu ciclo/bag. */
internal object NewsQueuePlanner {
    data class Plan(val upcoming: List<ProgramItem>, val deferred: List<ProgramItem>)
    fun insert(upcoming: List<ProgramItem>, block: List<ProgramItem>): Plan {
        require(block.map { it.type } == listOf(ProgramItemType.NEWS_INTRO, ProgramItemType.NEWS_DROP))
        require(upcoming.any { it.type == ProgramItemType.MUSIC })
        val held = ArrayDeque<ProgramItem>()
        val tail = if (upcoming.first().type in normalTypes) {
            held.addLast(upcoming.first())
            restore(upcoming.drop(1), held)
        } else upcoming
        require(tail.first().type == ProgramItemType.MUSIC)
        return Plan(block + tail, held.toList())
    }

    fun restore(batch: List<ProgramItem>, deferred: ArrayDeque<ProgramItem>): List<ProgramItem> = batch.map { item ->
        if (item.type in normalTypes && deferred.isNotEmpty()) {
            val next = deferred.removeFirst()
            deferred.addLast(item)
            next
        } else item
    }
    private val normalTypes = setOf(ProgramItemType.STATION_ID, ProgramItemType.JINGLE)
}
