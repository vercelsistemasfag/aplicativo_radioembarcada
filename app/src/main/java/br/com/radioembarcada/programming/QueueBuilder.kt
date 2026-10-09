package br.com.radioembarcada.programming

import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.Track
import kotlin.random.Random

class QueueBuilder(private val random: Random = Random.Default) {
    fun build(tracks: List<Track>, size: Int = ProgrammingConfiguration.BATCH_SIZE,
        previous: ProgramItem? = null, allowSameArtist: Boolean = false,
        allowSingleTrackRepeat: Boolean = false,
        prefer: (ProgramItem) -> Boolean = { false }): List<ProgramItem> {
        val candidates = tracks.distinctBy(Track::key).shuffled(random).sortedByDescending { prefer(ProgramItem.music(it)) }.toMutableList()
        val result = mutableListOf<ProgramItem>()
        var lastId = previous?.id
        var lastArtist = previous?.artistKey
        while (result.size < size) {
            var index = candidates.indexOfFirst { it.key != lastId && it.artistKey != lastArtist }
            if (index == -1 && allowSameArtist) index = candidates.indexOfFirst { it.key != lastId }
            if (index == -1 && allowSingleTrackRepeat && result.isEmpty() && candidates.size == 1) index = 0
            if (index == -1) break // Não quebrar a regra para completar uma fila pequena.
            val track = candidates.removeAt(index)
            result += ProgramItem.music(track)
            lastId = track.key
            lastArtist = track.artistKey
        }
        return result
    }
}
