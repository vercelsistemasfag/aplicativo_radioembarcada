package br.com.radioembarcada.storage

import br.com.radioembarcada.programming.ProgrammingConfiguration
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

/** Synchronous, atomic checkpoints in private filesDir: no dependency on onDestroy or cache. */
class PlaybackHistoryRepository(private val file: File) : MusicHistory {
    @Synchronized override fun readState(): PlaybackHistoryState {
        if (!file.isFile) return PlaybackHistoryState()
        val root = JSONObject(file.readText(Charsets.UTF_8))
        fun times(field: String): Map<String, Long> {
            val values = root.optJSONObject(field) ?: return emptyMap()
            return values.keys().asSequence().mapNotNull { id ->
                (values.opt(id) as? Number)?.toLong()?.takeIf { it >= 0 }?.let { id to it }
            }.toMap()
        }
        val played = times("playedAt")
        val opening = root.optJSONArray("lastSessionOpeningSequence") ?: JSONArray()
        return PlaybackHistoryState(played, times("artistsPlayedAt"),
            (root.opt("lastTrackId") as? String)?.takeIf(String::isNotBlank)
                ?: played.maxByOrNull { it.value }?.key, // Migrate the prior timestamp-only format.
            (0 until opening.length()).mapNotNull { (opening.opt(it) as? String)?.takeIf(String::isNotBlank) }
                .take(ProgrammingConfiguration.SESSION_OPENING_SIZE))
    }

    @Synchronized override fun writeState(state: PlaybackHistoryState) {
        fun bounded(values: Map<String, Long>) = values.entries.sortedByDescending { it.value }
            .take(ProgrammingConfiguration.MUSIC_HISTORY_LIMIT).associate { it.toPair() }
        val root = JSONObject().put("playedAt", JSONObject(bounded(state.playedAt)))
            .put("artistsPlayedAt", JSONObject(bounded(state.artistsPlayedAt)))
            .put("lastTrackId", state.lastTrackId ?: JSONObject.NULL)
            .put("lastSessionOpeningSequence", JSONArray(state.lastSessionOpeningSequence
                .take(ProgrammingConfiguration.SESSION_OPENING_SIZE)))
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(root.toString().toByteArray(Charsets.UTF_8))
                output.fd.sync() // Commit playback before returning; survives process death/reboot.
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temporary.delete() }
    }

    override fun read() = readState().playedAt
    override fun readArtists() = readState().artistsPlayedAt
    @Synchronized override fun write(playedAt: Map<String, Long>) =
        writeState(readState().copy(playedAt = playedAt))
    @Synchronized override fun writeArtists(playedAt: Map<String, Long>) =
        writeState(readState().copy(artistsPlayedAt = playedAt))
}
