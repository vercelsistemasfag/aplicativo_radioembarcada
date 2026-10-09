package br.com.radioembarcada.network.programming

object RemoteProgrammingConfiguration {
    const val URL = "https://pub-e38948fd737d4d969bb4e65eebed0249.r2.dev/programming.json"
    const val REFRESH_INTERVAL_MS = 15L * 60 * 1_000
    const val FAILED_REFRESH_INTERVAL_MS = 30_000L
    const val CONNECT_TIMEOUT_MS = 5_000
    const val READ_TIMEOUT_MS = 5_000
    const val MAX_RESPONSE_BYTES = 256 * 1024
}
