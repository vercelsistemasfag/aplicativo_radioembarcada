package br.com.radioembarcada.player

import br.com.radioembarcada.model.ProgramItemType

/** Dois itens normalmente; bloco editorial permite o terceiro (música) parcialmente preparado. */
internal object PreloadPlan {
    fun durationMs(distance: Int, nextType: ProgramItemType?, local: Boolean): Long? = when (distance) {
        1 -> if (local) PlaybackConfiguration.LOCAL_NEXT_TRACK_PRELOAD_MS else PlaybackConfiguration.NEXT_TRACK_PRELOAD_MS
        2 -> if (local) PlaybackConfiguration.LOCAL_SECOND_TRACK_PRELOAD_MS
            else if (nextType != null && nextType != ProgramItemType.MUSIC) PlaybackConfiguration.NEXT_TRACK_PRELOAD_MS
            else PlaybackConfiguration.SECOND_TRACK_PRELOAD_MS
        3 -> if (!local && nextType == ProgramItemType.NEWS_INTRO) PlaybackConfiguration.SECOND_TRACK_PRELOAD_MS else null
        else -> null
    }
}
