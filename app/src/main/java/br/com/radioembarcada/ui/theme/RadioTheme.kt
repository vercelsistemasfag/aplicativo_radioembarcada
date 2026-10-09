package br.com.radioembarcada.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object RadioColors {
    val BackgroundBlack = Color(0xFF070808)
    val AlceGold = Color(0xFFE0B75F)
    val AlceGoldSoft = Color(0xFF8C703B)
    val AlceSilver = Color(0xFFB8BEC7)
    val TextPrimary = Color(0xFFF5F3EE)
    val TextSecondary = Color(0xFFCCC8BE)
}

private val radioScheme = darkColorScheme(
    primary = RadioColors.AlceGold,
    onPrimary = RadioColors.BackgroundBlack,
    background = RadioColors.BackgroundBlack,
    onBackground = RadioColors.TextPrimary,
    surface = RadioColors.BackgroundBlack,
    onSurface = RadioColors.TextPrimary,
    onSurfaceVariant = RadioColors.TextSecondary,
)

@Composable
fun RadioTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = radioScheme, content = content)
}
