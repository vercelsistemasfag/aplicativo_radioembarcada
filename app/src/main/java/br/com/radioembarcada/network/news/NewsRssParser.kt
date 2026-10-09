package br.com.radioembarcada.network.news

import br.com.radioembarcada.model.NewsItem
import java.io.StringReader
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

object NewsRssParser {
    fun parse(xml: String): List<NewsItem> {
        require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true)) { "DTD not allowed" }
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(StringReader(xml))
        val result = mutableListOf<NewsItem>()
        var fields: MutableMap<String, String>? = null
        var enclosure: String? = null
        var itemDepth = -1
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            require(parser.eventType != XmlPullParser.DOCDECL) { "DTD not allowed" }
            if (parser.eventType == XmlPullParser.START_TAG) {
                when {
                    parser.name == "item" -> { fields = linkedMapOf(); enclosure = null; itemDepth = parser.depth }
                    fields != null && parser.depth == itemDepth + 1 -> {
                        val name = parser.name
                        if (name == "enclosure" || name == "content") {
                            val url = parser.getAttributeValue(null, "url").orEmpty()
                            val mime = parser.getAttributeValue(null, "type").orEmpty()
                            if (OfficialNewsUrls.audio(url) && (mime.isBlank() || mime.startsWith("audio/"))) enclosure = url
                        } else if (name in setOf("title", "link", "guid", "pubDate", "category", "duration", "description")) {
                            // Description aceita CDATA; o feed oficial utiliza HTML escapado.
                            val text = parser.nextText().trim()
                            fields.putIfAbsent(name, text)
                        }
                    }
                }
            } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == "item") {
                val map = fields.orEmpty()
                val title = map["title"].orEmpty()
                val link = map["link"].orEmpty()
                val published = date(map["pubDate"].orEmpty())
                if (title.isNotBlank() && OfficialNewsUrls.valid(link) && published != null) {
                    val duration = map["duration"]?.let(::duration) ?: Regex("class=[\"'][^\"']*hms[^\"']*[\"'][^>]*>([0-9:]+)")
                        .find(map["description"].orEmpty())?.groupValues?.get(1)?.let(::duration) ?: 0
                    result += NewsItem(map["guid"].orEmpty().ifBlank { link }, title, published, link,
                        enclosure, duration, category = map["category"]?.takeIf { it.isNotBlank() })
                }
                fields = null
            }
            parser.next()
        }
        return result.distinctBy { it.id }
    }

    private fun date(value: String): Long? = runCatching {
        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.recoverCatching { Instant.parse(value).toEpochMilli() }.getOrNull()

    private fun duration(value: String): Long = runCatching {
        val parts = value.split(':')
        require(parts.size in 1..3)
        parts.fold(0L) { total, part -> Math.addExact(Math.multiplyExact(total, 60), part.toLong()) } * 1_000
    }.getOrDefault(0).coerceAtLeast(0)
}
