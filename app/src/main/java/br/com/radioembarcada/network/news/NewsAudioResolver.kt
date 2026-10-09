package br.com.radioembarcada.network.news

/** Links do player/download oficial, em ordem de documento; não usar audios relacionados primeiro. */
object NewsAudioResolver {
    fun resolve(articleUrl: String, html: String): String? {
        if (!OfficialNewsUrls.valid(articleUrl)) return null
        val tags = Regex("<(?:audio|source|a|button)\\b[^>]*>", RegexOption.IGNORE_CASE)
        val attributes = Regex("([\\w-]+)\\s*=\\s*([\"'])(.*?)\\2", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (tag in tags.findAll(html)) {
            val attrs = attributes.findAll(tag.value).associate { it.groupValues[1].lowercase() to decode(it.groupValues[3]) }
            for (key in listOf("data-source", "src", "href", "data-permalink")) {
                val url = attrs[key]?.let { OfficialNewsUrls.resolve(articleUrl, it) }
                if (url != null) return url
            }
        }
        return null
    }

    private fun decode(value: String): String = value.replace("&amp;", "&")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace(Regex("&#(x[0-9a-fA-F]+|[0-9]+);")) { match ->
            val text = match.groupValues[1]
            runCatching { String(Character.toChars(if (text.startsWith("x")) text.drop(1).toInt(16) else text.toInt())) }.getOrDefault("")
        }
}
