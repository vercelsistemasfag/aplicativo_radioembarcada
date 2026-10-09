package br.com.radioembarcada.network.news

import br.com.radioembarcada.data.news.NewsProvider
import br.com.radioembarcada.model.NewsItem
import br.com.radioembarcada.news.NewsConfiguration
import java.net.URL
import java.io.IOException
import java.io.ByteArrayOutputStream
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RemoteNewsProvider(
    private val download: (String) -> String = ::downloadNewsDocument,
    private val now: () -> Long = System::currentTimeMillis,
    private val diagnostic: (String) -> Unit = {},
    private val audioProbe: (String) -> String? = ::probeNewsAudio,
) : NewsProvider {
    private val mutex = Mutex()
    private var items = emptyList<NewsItem>()
    private val resolved = mutableMapOf<String, String>()
    private var refreshAt = 0L
    private var available = false

    override suspend fun latestUnplayed(excludedIds: Set<String>): NewsItem? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (now() >= refreshAt) {
                try {
                    items = NewsRssParser.parse(download(NewsConfiguration.RSS_URL))
                    resolved.keys.retainAll(items.map { it.id }.toSet())
                    refreshAt = now() + NewsConfiguration.REFRESH_INTERVAL_MS
                    available = true
                    diagnostic("NewsProvider: RSS refreshed; items=${items.size}")
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    refreshAt = now() + NewsConfiguration.RETRY_INTERVAL_MS
                    available = false
                    diagnostic("NewsProvider: RSS unavailable; mantendo programação musical")
                }
            }
            if (!available) return@withLock null
            val eligible = items.filter { eligible(it, now(), excludedIds) }.sortedByDescending { it.publishedAt }
            diagnostic("NewsProvider: Eligible items: ${eligible.size}")
            for (item in eligible.take(NewsConfiguration.MAX_ARTICLES_PER_REFRESH)) {
                try {
                    val cached = resolved[item.id]
                    val audio = cached ?: item.audioUrl?.takeIf(OfficialNewsUrls::audio)
                        ?: NewsAudioResolver.resolve(item.articleUrl, download(item.articleUrl)) ?: continue
                    val playable = cached ?: audioProbe(audio)?.takeIf(OfficialNewsUrls::audio)
                    if (playable == null) {
                        diagnostic("NewsProvider: audio unavailable; item=${item.id.take(120)}; tentando próximo")
                        continue
                    }
                    resolved[item.id] = playable
                    diagnostic("NewsProvider: Selected: ${item.id.take(120)}; Published: ${item.publishedAt}; Audio resolved: true")
                    return@withLock item.copy(audioUrl = playable)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { diagnostic("NewsProvider: item sem áudio oficial; tentando próximo") }
            }
            null
        }
    }

    companion object {
        fun eligible(item: NewsItem, now: Long, excluded: Set<String>): Boolean = item.id !in excluded &&
            now - item.publishedAt in -300_000L..NewsConfiguration.NEWS_MAX_AGE_HOURS * 60 * 60 * 1_000 &&
            (item.durationMs == 0L || item.durationMs in NewsConfiguration.NEWS_MIN_DURATION_MS..NewsConfiguration.NEWS_MAX_DURATION_MS)
    }
}

/** Verifica disponibilidade sem baixar o drop; HEAD, ou um byte se HEAD não for permitido. */
private fun probeNewsAudio(initial: String): String? {
    var address = initial
    var useGet = false
    repeat(5) {
        if (!OfficialNewsUrls.audio(address)) return null
        val connection = URL(address).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = NewsConfiguration.HTTP_TIMEOUT_MS
            connection.readTimeout = NewsConfiguration.HTTP_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.requestMethod = if (useGet) "GET" else "HEAD"
            if (useGet) connection.setRequestProperty("Range", "bytes=0-0")
            val status = connection.responseCode
            when {
                status in listOf(301, 302, 303, 307, 308) -> address = URL(URL(address), connection.getHeaderField("Location") ?: return null).toString()
                status == 405 && !useGet -> useGet = true
                status == 200 || status == 206 -> {
                    val mime = connection.contentType.orEmpty().substringBefore(';').lowercase()
                    if (mime.isNotBlank() && !mime.startsWith("audio/") && mime != "application/octet-stream") return null
                    if (useGet) connection.inputStream.use { if (it.read() < 0) return null }
                    return address
                }
                else -> return null
            }
        } finally { connection.disconnect() }
    }
    return null
}

/** Redirecionamentos e tamanho limitados, sempre HTTPS dentro de domínios oficiais. */
private fun downloadNewsDocument(initial: String): String {
    var address = initial
    repeat(4) {
        require(OfficialNewsUrls.valid(address))
        val connection = URL(address).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = NewsConfiguration.HTTP_TIMEOUT_MS
            connection.readTimeout = NewsConfiguration.HTTP_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            if (connection.responseCode in listOf(301, 302, 303, 307, 308)) {
                address = URL(URL(address), connection.getHeaderField("Location") ?: throw IOException("Missing redirect")).toString()
            } else {
                if (connection.responseCode != 200) throw IOException("News unavailable")
                return connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8_192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > NewsConfiguration.MAX_DOCUMENT_BYTES) throw IOException("News document too large")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray().toString(Charsets.UTF_8)
                }
            }
        } finally { connection.disconnect() }
    }
    throw IOException("Too many redirects")
}
