package br.com.radioembarcada.storage

import java.io.File
import org.json.JSONObject

interface MusicHistory {
    fun read(): Map<String, Long>
    fun write(playedAt: Map<String, Long>)
}

class MemoryMusicHistory : MusicHistory {
    private var played = emptyMap<String, Long>()
    override fun read() = played.toMap()
    override fun write(playedAt: Map<String, Long>) { played = playedAt.toMap() }
}

/** Actual playback only; prefetched/queued songs do not enter the persisted history. */
class FileMusicHistory(file: File) : MusicHistory {
    private val document = FileMusicCatalog(file)
    override fun read(): Map<String, Long> = document.read()?.let {
        val root = JSONObject(it).getJSONObject("playedAt")
        root.keys().asSequence().mapNotNull { id ->
            (root.opt(id) as? Number)?.toLong()?.takeIf { time -> time >= 0 }?.let { time -> id to time }
        }.toMap()
    }.orEmpty()
    override fun write(playedAt: Map<String, Long>) = document.write(JSONObject()
        .put("playedAt", JSONObject(playedAt)).toString())
}
