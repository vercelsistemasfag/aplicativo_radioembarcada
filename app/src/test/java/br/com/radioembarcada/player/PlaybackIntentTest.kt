package br.com.radioembarcada.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackIntentTest {
    @Test fun pauseInvalidatesThePendingCatalogCompletion() {
        val intent = PlaybackIntent()
        intent.requestPlay()
        val pending = intent.generation
        intent.pause()
        assertFalse(intent.requested)
        assertFalse(intent.isCurrent(pending))
    }

    @Test fun aNewPlayDoesNotMakeAnOlderCompletionCurrentAgain() {
        val intent = PlaybackIntent()
        intent.requestPlay()
        val old = intent.generation
        intent.pause()
        intent.requestPlay()
        assertTrue(intent.requested)
        assertFalse(intent.isCurrent(old))
        assertTrue(intent.isCurrent(intent.generation))
    }
}
