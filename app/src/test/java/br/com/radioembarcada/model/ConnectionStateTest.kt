package br.com.radioembarcada.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionStateTest {
    @Test fun temporaryNetworkLossAndRecoveryHaveClearStatuses() {
        val states = listOf(
            ConnectionState.resolve(true, true, false, false, false),
            ConnectionState.resolve(true, true, true, false, false),
            ConnectionState.resolve(true, false, false, false, true),
            ConnectionState.resolve(true, true, false, false, true),
            ConnectionState.resolve(true, true, true, false, true),
        )
        assertEquals(listOf(ConnectionState.CONNECTING, ConnectionState.LIVE,
            ConnectionState.OFFLINE, ConnectionState.RECONNECTING, ConnectionState.LIVE), states)
    }

    @Test fun userPauseRemainsPausedDuringNetworkLossOrPendingRecovery() {
        assertEquals(ConnectionState.PAUSED,
            ConnectionState.resolve(false, false, false, false, true))
        assertEquals(ConnectionState.PAUSED,
            ConnectionState.resolve(false, true, false, false, true))
    }

    @Test fun preparedAudioRemainsLiveWhenInternetDisappears() {
        assertEquals(ConnectionState.LIVE,
            ConnectionState.resolve(true, false, true, false, true))
        assertEquals(ConnectionState.OFFLINE,
            ConnectionState.resolve(true, false, false, false, true))
    }

    @Test fun audioFocusSuppressionDoesNotLookLikeAConnectionAttempt() {
        assertEquals(ConnectionState.PAUSED,
            ConnectionState.resolve(true, true, false, true, false))
    }
}
