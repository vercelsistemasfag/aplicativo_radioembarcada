package br.com.radioembarcada.player

import br.com.radioembarcada.model.ProgramItemType

/** Todos os ganhos/tempos são de envelope; não normalizam loudness do arquivo original. */
object TransitionConfiguration {
    const val MUSIC_FADE_OUT_MS = 15_000L
    const val INSERT_ENTRY_MS = 150L
    const val MUSIC_EXIT_FLOOR = 0.10f
    const val INSERT_ENTRY_FLOOR = 0.25f
    const val MUSIC_ENTRY_MS = 100L
    const val UPDATE_MS = 20L
    const val IDLE_UPDATE_MS = 500L
    const val MUSIC_GAIN = 0.90f
    const val STATION_ID_GAIN = 0.80f
    const val JINGLE_GAIN = 0.80f
    const val ADVERTISEMENT_GAIN = 0.80f
    const val ANNOUNCEMENT_GAIN = 0.80f
}

enum class TransitionType { MUSIC_TO_INSERT, INSERT_TO_MUSIC, HARD_TRANSITION, FORBIDDEN }

/** A regra corrigida não sobrepõe áudios nem antecipa o fim de qualquer item. */
object TransitionPolicy {
    fun resolve(current: ProgramItemType, next: ProgramItemType): TransitionType = when {
        current == ProgramItemType.MUSIC && next != ProgramItemType.MUSIC -> TransitionType.MUSIC_TO_INSERT
        current != ProgramItemType.MUSIC && next == ProgramItemType.MUSIC -> TransitionType.INSERT_TO_MUSIC
        current != ProgramItemType.MUSIC && next != ProgramItemType.MUSIC -> TransitionType.FORBIDDEN
        else -> TransitionType.HARD_TRANSITION // Fallback sem peças: avanço natural MUSIC → MUSIC.
    }

    fun gain(type: ProgramItemType): Float = when (type) {
        ProgramItemType.MUSIC -> TransitionConfiguration.MUSIC_GAIN
        ProgramItemType.STATION_ID -> TransitionConfiguration.STATION_ID_GAIN
        ProgramItemType.JINGLE -> TransitionConfiguration.JINGLE_GAIN
        ProgramItemType.ADVERTISEMENT -> TransitionConfiguration.ADVERTISEMENT_GAIN
        ProgramItemType.ANNOUNCEMENT -> TransitionConfiguration.ANNOUNCEMENT_GAIN
    }

    fun volume(type: ProgramItemType, next: ProgramItemType?, positionMs: Long, durationMs: Long): Float {
        val rampMs = if (type == ProgramItemType.MUSIC) TransitionConfiguration.MUSIC_ENTRY_MS
            else TransitionConfiguration.INSERT_ENTRY_MS
        val progress = smooth(positionMs.toFloat() / rampMs)
        val entry = if (type == ProgramItemType.MUSIC) progress else
            TransitionConfiguration.INSERT_ENTRY_FLOOR + (1f - TransitionConfiguration.INSERT_ENTRY_FLOOR) * progress
        val exit = if (next != null && durationMs > 0 &&
            resolve(type, next) == TransitionType.MUSIC_TO_INSERT) {
            TransitionConfiguration.MUSIC_EXIT_FLOOR + (1f - TransitionConfiguration.MUSIC_EXIT_FLOOR) *
                smooth((durationMs - positionMs).toFloat() / minOf(TransitionConfiguration.MUSIC_FADE_OUT_MS, durationMs))
        } else 1f
        return gain(type) * entry * exit
    }

    private fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
}
