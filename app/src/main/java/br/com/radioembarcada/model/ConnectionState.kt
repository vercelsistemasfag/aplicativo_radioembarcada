package br.com.radioembarcada.model

enum class ConnectionState(val label: String) {
    CONNECTING("Carregando"), LIVE("Ao vivo"), PAUSED("Pausado"),
    OFFLINE("Sem conexão"), RECONNECTING("Reconectando");

    companion object {
        fun resolve(playRequested: Boolean, connected: Boolean, playing: Boolean,
            suppressed: Boolean, recovering: Boolean): ConnectionState = when {
            !playRequested -> PAUSED
            suppressed -> PAUSED
            playing -> LIVE // Áudio preparado continua válido mesmo sem internet.
            !connected -> OFFLINE
            recovering -> RECONNECTING
            else -> CONNECTING
        }
    }
}
