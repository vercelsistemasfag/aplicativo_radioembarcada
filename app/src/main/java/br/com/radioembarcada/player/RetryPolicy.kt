package br.com.radioembarcada.player

object RetryPolicy {
    fun delayMillis(attempt: Int): Long = 1_000L shl attempt.coerceIn(0, 5)
}
