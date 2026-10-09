package br.com.radioembarcada.player

import br.com.radioembarcada.model.ProgramItemType

/** Prepara somente dois itens. Antes de uma peça, já preparar também a música seguinte. */
internal object PreloadPlan {
    fun durationMs(distance: Int, nextType: ProgramItemType?, local: Boolean): Long? = when (distance) {
        1 -> if (local) PlaybackConfiguration.LOCAL_NEXT_TRACK_PRELOAD_MS else PlaybackConfiguration.NEXT_TRACK_PRELOAD_MS
        2 -> if (local) PlaybackConfiguration.LOCAL_SECOND_TRACK_PRELOAD_MS
            else if (nextType != null && nextType != ProgramItemType.MUSIC) PlaybackConfiguration.NEXT_TRACK_PRELOAD_MS
            else PlaybackConfiguration.SECOND_TRACK_PRELOAD_MS
        else -> null
    }
}
