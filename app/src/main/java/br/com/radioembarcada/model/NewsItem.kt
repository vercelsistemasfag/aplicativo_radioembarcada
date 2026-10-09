package br.com.radioembarcada.model

data class NewsItem(
    val id: String,
    val title: String,
    val publishedAt: Long,
    val articleUrl: String,
    val audioUrl: String? = null,
    val durationMs: Long = 0,
    val source: String = "Radioagência Nacional",
    val category: String? = null,
) {
    fun program(occurrence: Long) = ProgramItem("news:$id:$occurrence", ProgramItemType.NEWS_DROP,
        title, requireNotNull(audioUrl), durationMs, artist = source, source = source,
        sourceUrl = articleUrl, contentId = "news:$id")
}
