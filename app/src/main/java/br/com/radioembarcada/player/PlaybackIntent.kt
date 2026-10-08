package br.com.radioembarcada.player

/** Invalida conclusões assíncronas de comandos que já foram pausados/substituídos. */
class PlaybackIntent {
    var requested = false
        private set
    var generation = 0L
        private set
    fun requestPlay() { requested = true; generation++ }
    fun pause() { requested = false; generation++ }
    fun isCurrent(requestGeneration: Long): Boolean = requestGeneration == generation
}
