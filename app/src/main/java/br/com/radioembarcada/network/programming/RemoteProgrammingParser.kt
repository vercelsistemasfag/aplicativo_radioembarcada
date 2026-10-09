package br.com.radioembarcada.network.programming

import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.ProgramItemType
import br.com.radioembarcada.model.ProgrammingRules
import br.com.radioembarcada.model.StationProgramming
import java.net.URI
import org.json.JSONObject

object RemoteProgrammingParser {
    fun parse(json: String): StationProgramming {
        val root = JSONObject(json)
        val stationId = (root.opt("stationId") as? String)?.trim().orEmpty()
        val version = positiveInteger(root.opt("version"))
        val rules = root.getJSONObject("rules")
        val interval = positiveInteger(rules.opt("songsBetweenInsertions"))
        require(interval <= Int.MAX_VALUE)
        val alternate = rules.opt("alternateStationIdAndJingle") as? Boolean
            ?: error("Invalid alternation rule")
        return StationProgramming(stationId, version, ProgrammingRules(interval.toInt(), alternate),
            pieces(root, "stationIds", stationId, ProgramItemType.STATION_ID),
            pieces(root, "jingles", stationId, ProgramItemType.JINGLE))
    }

    private fun positiveInteger(value: Any?): Long {
        val number = value as? Number ?: error("Invalid number")
        require(number.toLong() > 0 && number.toDouble() == number.toLong().toDouble())
        return number.toLong()
    }

    private fun pieces(root: JSONObject, field: String, station: String, type: ProgramItemType): List<ProgramItem> {
        if (!root.has(field) || root.isNull(field)) return emptyList()
        val array = root.getJSONArray(field)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            if (item.optString("type") != type.name) return@mapNotNull null
            val id = (item.opt("id") as? String)?.trim().orEmpty()
            val title = (item.opt("title") as? String)?.trim().orEmpty()
            val url = (item.opt("url") as? String)?.trim().orEmpty()
            if (id.isEmpty() || title.isEmpty() || !isAudioUrl(url)) return@mapNotNull null
            ProgramItem("programming:$station:${type.name}:$id", type, title, url,
                (item.opt("durationMs") as? Number)?.toLong()?.coerceAtLeast(0) ?: 0,
                artist = (item.opt("artist") as? String)?.trim().orEmpty(), source = "R2 programming")
        }.distinctBy { it.id }
    }

    private fun isAudioUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
    }.getOrDefault(false)
}
