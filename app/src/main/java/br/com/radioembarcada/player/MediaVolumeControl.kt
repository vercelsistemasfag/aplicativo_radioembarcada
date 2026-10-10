package br.com.radioembarcada.player

import android.content.Context
import android.media.AudioManager

/** Device media volume, independent from the per-item transition envelope. */
class MediaVolumeControl internal constructor(private val adjust: (Int, Int, Int) -> Unit) {
    constructor(context: Context) : this(context.getSystemService(AudioManager::class.java)::adjustStreamVolume)
    fun lower() { adjust(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
    fun raise() { adjust(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
}
