package br.com.radioembarcada.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import br.com.radioembarcada.ui.theme.RadioColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

/** Decorative waveform, not an audio analyser. No permission, FFT or background timer. */
@Composable
fun WaveformVisualizer(active: Boolean, modifier: Modifier = Modifier) {
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (active) while (true) {
            delay(50) // 20 Hz; state is read only during drawing, not by the layout.
            phase.floatValue = (phase.floatValue + .12f) % (Math.PI.toFloat() * 2)
        }
    }
    Canvas(modifier.testTag("waveform").semantics {
        stateDescription = if (active) "Ativa" else "Pausada"
    }) {
        val bars = 41
        val step = size.width / bars
        repeat(bars) { index ->
            val envelope = sin(Math.PI * (index + .5) / bars).toFloat()
            val movement = if (active) .18f + .70f * abs(sin(index * .73f + phase.floatValue) *
                sin(index * .21f - phase.floatValue)) else .055f
            val height = (size.height * envelope * movement).coerceAtLeast(3f)
            val x = step * (index + .5f)
            drawLine(RadioColors.AlceGold.copy(alpha = if (active) .85f else .35f),
                Offset(x, (size.height - height) / 2), Offset(x, (size.height + height) / 2),
                strokeWidth = (step * .38f).coerceAtMost(4f), cap = StrokeCap.Round)
        }
    }
}
