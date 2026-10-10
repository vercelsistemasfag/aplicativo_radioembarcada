package br.com.radioembarcada.player

import android.media.AudioManager
import org.junit.Assert.*
import org.junit.Test

class MediaVolumeControlTest {
    @Test fun buttonsRequestOnlySystemMediaVolumeWithTheCorrectDirectionAndFeedback() {
        val calls = mutableListOf<Triple<Int, Int, Int>>()
        val control = MediaVolumeControl { stream, direction, flags -> calls += Triple(stream, direction, flags) }
        control.lower(); control.raise()
        assertEquals(listOf(
            Triple(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI),
            Triple(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)), calls)
    }
}
