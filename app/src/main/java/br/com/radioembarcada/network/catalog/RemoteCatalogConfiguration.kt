package br.com.radioembarcada.network.catalog

object RemoteCatalogConfiguration {
    const val URL = "https://pub-e38948fd737d4d969bb4e65eebed0249.r2.dev/music_catalog.json"
    const val REFRESH_INTERVAL_MS = 6L * 60 * 60 * 1_000
    const val FAILED_REFRESH_INTERVAL_MS = 30_000L
    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 10_000
    const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
}
