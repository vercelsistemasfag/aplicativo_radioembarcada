package br.com.radioembarcada.network.news

import android.app.Application
import br.com.radioembarcada.news.NewsConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RemoteNewsProviderTest {
    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/$name")).readText()
    private val time = Instant.parse("2026-10-09T16:00:00Z").toEpochMilli()
    @Test fun parsesOfficialRssDatesCategoryDurationAndOptionalEnclosure() {
        val items = NewsRssParser.parse(fixture("news-rss.xml"))
        assertEquals(3, items.size)
        assertEquals("Economia", items.first().category)
        assertEquals(145_000L, items.first().durationMs)
        assertEquals("Radioagência Nacional", items.first().source)
        assertNull(items.first().audioUrl)
        assertEquals(90_000L, items[1].durationMs)
        assertEquals("https://audios.ebc.com.br/news.mp3", items[1].audioUrl)
        assertNull(items[2].audioUrl)
    }
    @Test fun htmlResolvesPrimaryOfficialDownloadInsteadOfRelatedAudio() {
        val url = NewsAudioResolver.resolve("https://agenciabrasil.ebc.com.br/news", fixture("news-article.html"))
        assertEquals("https://agenciabrasil.ebc.com.br/sites/default/files/atoms/audio/noticia.mp3?download&filename=noticia.mp3", url)
    }
    @Test fun audioAndSourceElementsSupportRelativeUrlsButRejectSpoofedDomains() {
        val base = "https://agenciabrasil.ebc.com.br/article/"
        assertEquals("https://agenciabrasil.ebc.com.br/article/audio.mp3", NewsAudioResolver.resolve(base, "<audio><source src='audio.mp3'></audio>"))
        for (url in listOf("https://ebc.com.br.attacker.example/audio.mp3", "https://attacker.example/audio.mp3", "http://audios.ebc.com.br/audio.mp3", "https://user@audios.ebc.com.br/audio.mp3")) {
            assertNull(NewsAudioResolver.resolve(base, "<audio src='$url'>"))
        }
    }
    @Test fun choosesNewestUnplayedAndDoesNotDownloadEnclosureArticles() = runBlocking {
        val requests = mutableListOf<String>()
        val provider = RemoteNewsProvider(download = { url ->
            requests += url
            if (url == NewsConfiguration.RSS_URL) fixture("news-rss.xml") else fixture("news-article.html")
        }, now = { time }, audioProbe = { it })
        assertEquals("latest", provider.latestUnplayed(emptySet())?.id)
        assertEquals("enclosure", provider.latestUnplayed(setOf("latest"))?.id)
        assertEquals(2, requests.size) // um RSS e apenas a página do item sem enclosure
    }
    @Test fun unresolvableItemIsIgnoredAndNextEligibleAudioWins() = runBlocking {
        val provider = RemoteNewsProvider(download = { url ->
            if (url == NewsConfiguration.RSS_URL) fixture("news-rss.xml") else "<html>No audio</html>"
        }, now = { time }, audioProbe = { it })
        assertEquals("enclosure", provider.latestUnplayed(emptySet())?.id)
    }
    @Test fun rssFailureIsNonFatalAndRetryIsThrottled() = runBlocking {
        var calls = 0
        val provider = RemoteNewsProvider(download = { calls++; throw IOException("unavailable") }, now = { time }, audioProbe = { it })
        assertNull(provider.latestUnplayed(emptySet())); assertNull(provider.latestUnplayed(emptySet()))
        assertEquals(1, calls)
    }
    @Test fun rejectsOldFutureAndVeryLongItemsWhileUnknownDurationRemainsEligible() {
        val item = NewsRssParser.parse(fixture("news-rss.xml")).first()
        assertFalse(RemoteNewsProvider.eligible(item.copy(publishedAt = time - 86_400_001), time, emptySet()))
        assertFalse(RemoteNewsProvider.eligible(item.copy(publishedAt = time + 300_001), time, emptySet()))
        assertFalse(RemoteNewsProvider.eligible(item.copy(durationMs = 240_001), time, emptySet()))
        assertFalse(RemoteNewsProvider.eligible(item.copy(durationMs = 29_999), time, emptySet()))
        assertTrue(RemoteNewsProvider.eligible(item.copy(durationMs = 0), time, emptySet()))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsDtdWithoutResolvingEntities() {
        NewsRssParser.parse("<!DOCTYPE rss [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><rss/>")
    }
    @Test fun brokenOfficialAudioIsSkippedBeforeIntroCanBeScheduled() = runBlocking {
        val provider = RemoteNewsProvider(download = { url ->
            if (url == NewsConfiguration.RSS_URL) fixture("news-rss.xml") else fixture("news-article.html")
        }, now = { time }, audioProbe = { if (it.contains("noticia.mp3")) null else it })
        assertEquals("enclosure", provider.latestUnplayed(emptySet())?.id)
    }
}
