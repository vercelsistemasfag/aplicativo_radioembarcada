package br.com.radioembarcada.news

object NewsConfiguration {
    const val RSS_URL = "https://agenciabrasil.ebc.com.br/radioagencia-nacional/rss/ultimasnoticias/feed.xml"
    const val INTRO_URL = "https://pub-e38948fd737d4d969bb4e65eebed0249.r2.dev/Vinheta_Noticias_Masterizada.mp3"
    const val NEWS_INTERVAL_MS = 30L * 60 * 1_000
    const val NEWS_MAX_AGE_HOURS = 24L
    const val NEWS_MIN_DURATION_MS = 30_000L
    const val NEWS_MAX_DURATION_MS = 4L * 60 * 1_000
    const val REFRESH_INTERVAL_MS = 15L * 60 * 1_000
    const val RETRY_INTERVAL_MS = 5L * 60 * 1_000
    const val HISTORY_LIMIT = 100
    const val MAX_ARTICLES_PER_REFRESH = 8
    const val HTTP_TIMEOUT_MS = 5_000
    const val MAX_DOCUMENT_BYTES = 2 * 1024 * 1024
    const val PRELOAD_GUARD_MS = 2_000L
}
