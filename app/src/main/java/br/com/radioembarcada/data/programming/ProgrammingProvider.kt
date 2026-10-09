package br.com.radioembarcada.data.programming

import br.com.radioembarcada.model.StationProgramming

/** null significa continuar somente com músicas, sem bloquear a rádio. */
fun interface ProgrammingProvider {
    suspend fun load(): StationProgramming?
}
