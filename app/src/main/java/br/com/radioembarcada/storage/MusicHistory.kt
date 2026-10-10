package br.com.radioembarcada.storage

import java.io.File
import org.json.JSONObject

interface MusicHistory {
    fun read(): Map<String, Long>
    fun readArtists(): Map<String, Long> = emptyMap()
    fun writeArtists(playedAt: Map<String, Long>) {}
    fun write(playedAt: Map<String, Long>)
}

class MemoryMusicHistory : MusicHistory {
    private var played = emptyMap<String, Long>()
    private var artists = emptyMap<String, Long>()
    override fun readArtists() = artists.toMap()
    override fun writeArtists(playedAt: Map<String, Long>) { artists = playedAt.toMap() }
    override fun read() = played.toMap()
    override fun write(playedAt: Map<String, Long>) { played = playedAt.toMap() }
}

/** Actual playback only; prefetched/queued songs do not enter the persisted history. */
class FileMusicHistory(file: File) : MusicHistory {
    private val document = FileMusicCatalog(file)
    override fun read() = readField("playedAt")
    override fun readArtists() = readField("artistsPlayedAt")
    private fun readField(field: String): Map<String, Long> = document.read()?.let {
        val root = JSONObject(it).optJSONObject(field) ?: return@let emptyMap()
        root.keys().asSequence().mapNotNull { id ->
            (root.opt(id) as? Number)?.toLong()?.takeIf { time -> time >= 0 }?.let { time -> id to time }
        }.toMap()
    }.orEmpty()
    override fun write(playedAt: Map<String, Long>) = writeField("playedAt", playedAt)
    override fun writeArtists(playedAt: Map<String, Long>) = writeField("artistsPlayedAt", playedAt)
    private fun writeField(field: String, values: Map<String, Long>) {
        val root = document.read()?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        document.write(root.put(field, JSONObject(values)).toString())
    }
}
