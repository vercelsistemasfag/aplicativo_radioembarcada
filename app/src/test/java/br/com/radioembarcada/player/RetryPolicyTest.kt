package br.com.radioembarcada.player

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryPolicyTest {
    @Test fun retriesUseBackoffWithABoundedDelay() {
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 32000L, 32000L),
            (0..6).map(RetryPolicy::delayMillis))
        assertEquals(32000L, RetryPolicy.delayMillis(Int.MAX_VALUE))
    }
    @Test fun negativeAttemptDoesNotCreateInvalidDelay() {
        assertEquals(1000L, RetryPolicy.delayMillis(-1))
    }
}
