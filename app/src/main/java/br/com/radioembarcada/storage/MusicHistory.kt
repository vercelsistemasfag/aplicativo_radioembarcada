package br.com.radioembarcada.storage

import java.io.File

/** Only execution history and a small opening fingerprint, never the playback timeline. */
data class PlaybackHistoryState(
    val playedAt: Map<String, Long> = emptyMap(),
    val artistsPlayedAt: Map<String, Long> = emptyMap(),
    val lastTrackId: String? = null,
    val lastSessionOpeningSequence: List<String> = emptyList(),
)

interface MusicHistory {
    fun read(): Map<String, Long>
    fun readArtists(): Map<String, Long> = emptyMap()
    fun writeArtists(playedAt: Map<String, Long>) {}
    fun write(playedAt: Map<String, Long>)
    fun readState(): PlaybackHistoryState = PlaybackHistoryState(read(), readArtists())
    fun writeState(state: PlaybackHistoryState) { writeArtists(state.artistsPlayedAt); write(state.playedAt) }
}

class MemoryMusicHistory : MusicHistory {
    private var state = PlaybackHistoryState()
    override fun read() = state.playedAt.toMap()
    override fun readArtists() = state.artistsPlayedAt.toMap()
    override fun write(playedAt: Map<String, Long>) { state = state.copy(playedAt = playedAt.toMap()) }
    override fun writeArtists(playedAt: Map<String, Long>) { state = state.copy(artistsPlayedAt = playedAt.toMap()) }
    override fun readState() = state
    override fun writeState(state: PlaybackHistoryState) { this.state = state.copy(
        playedAt = state.playedAt.toMap(), artistsPlayedAt = state.artistsPlayedAt.toMap(),
        lastSessionOpeningSequence = state.lastSessionOpeningSequence.toList()) }
}

/** Compatibility with the previous store and its on-disk file name/schema. */
class FileMusicHistory(file: File) : MusicHistory by PlaybackHistoryRepository(file)
