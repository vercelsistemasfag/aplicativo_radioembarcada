package br.com.radioembarcada.model

enum class ConnectionState(val label: String) {
    LOADING_PROGRAMMING("Carregando programação"), UNAVAILABLE("Programação indisponível"),
    CONNECTING("Conectando"), LIVE("Ao vivo"), PAUSED("Pausado"),
    OFFLINE("Sem conexão"), RECONNECTING("Reconectando");

    companion object {
        fun resolve(playRequested: Boolean, connected: Boolean, playing: Boolean,
            suppressed: Boolean, recovering: Boolean, loadingCatalog: Boolean = false,
            catalogUnavailable: Boolean = false): ConnectionState = when {
            !playRequested -> if (catalogUnavailable) UNAVAILABLE else PAUSED
            suppressed -> PAUSED
            playing -> LIVE // Áudio preparado continua válido mesmo sem internet.
            catalogUnavailable -> UNAVAILABLE
            loadingCatalog -> LOADING_PROGRAMMING
            !connected -> OFFLINE
            recovering -> RECONNECTING
            else -> CONNECTING
        }
    }
}
