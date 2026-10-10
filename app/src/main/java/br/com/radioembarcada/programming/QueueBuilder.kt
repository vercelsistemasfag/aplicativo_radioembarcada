package br.com.radioembarcada.programming

import br.com.radioembarcada.model.ProgramItem
import br.com.radioembarcada.model.Track
import kotlin.random.Random

class QueueBuilder(private val random: Random = Random.Default) {
    fun build(tracks: List<Track>, size: Int = ProgrammingConfiguration.BATCH_SIZE,
        previous: ProgramItem? = null, allowSameArtist: Boolean = false,
        allowSingleTrackRepeat: Boolean = false,
        prefer: (ProgramItem) -> Boolean = { false },
        selectCandidates: (List<Track>) -> List<Track> = { it },
        onSelected: (ProgramItem) -> Unit = {}): List<ProgramItem> {
        val candidates = tracks.distinctBy(Track::key).shuffled(random).sortedByDescending { prefer(ProgramItem.music(it)) }.toMutableList()
        val result = mutableListOf<ProgramItem>()
        var lastId = previous?.contentId
        var lastArtist = previous?.artistKey
        while (result.size < size) {
            val boundarySafe = candidates.filter { it.key != lastId }
            val allowed = selectCandidates(boundarySafe).map { it.key }.toSet()
            var index = candidates.indexOfFirst { it.key in allowed && it.key != lastId && it.artistKey != lastArtist }
            if (index == -1 && allowSameArtist) index = candidates.indexOfFirst { it.key in allowed && it.key != lastId }
            if (index == -1 && allowSingleTrackRepeat && result.isEmpty() && candidates.size == 1) index = 0
            if (index == -1) break // Não quebrar a regra para completar uma fila pequena.
            val track = candidates.removeAt(index)
            val item = ProgramItem.music(track)
            result += item
            onSelected(item)
            lastId = track.key
            lastArtist = track.artistKey
        }
        return result
    }
}
