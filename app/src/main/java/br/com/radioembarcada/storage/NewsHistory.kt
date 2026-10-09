package br.com.radioembarcada.storage

import br.com.radioembarcada.news.NewsConfiguration
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class NewsHistoryState(val lastNewsPlayedAt: Long = 0, val playedNewsIds: List<String> = emptyList(),
    val activeElapsedMs: Long = 0)

interface NewsHistory {
    fun read(): NewsHistoryState
    fun write(state: NewsHistoryState)
}

class FileNewsHistory(file: File) : NewsHistory {
    private val document = FileMusicCatalog(file)
    override fun read(): NewsHistoryState = document.read()?.let { json ->
        val root = JSONObject(json)
        val ids = root.optJSONArray("playedNewsIds") ?: JSONArray()
        NewsHistoryState(root.optLong("lastNewsPlayedAt").coerceAtLeast(0),
            (0 until ids.length()).mapNotNull { ids.optString(it).takeIf(String::isNotBlank) }
                .distinct().takeLast(NewsConfiguration.HISTORY_LIMIT),
            root.optLong("activeElapsedMs").coerceAtLeast(0))
    } ?: NewsHistoryState()

    override fun write(state: NewsHistoryState) = document.write(JSONObject().apply {
        put("lastNewsPlayedAt", state.lastNewsPlayedAt)
        put("playedNewsIds", JSONArray(state.playedNewsIds.takeLast(NewsConfiguration.HISTORY_LIMIT)))
        put("activeElapsedMs", state.activeElapsedMs)
    }.toString())
}
