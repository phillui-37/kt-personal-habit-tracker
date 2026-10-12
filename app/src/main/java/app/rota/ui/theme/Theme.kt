package app.rota.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.rota.data.prefs.ThemeMode

val Paper = Color(0xFFF6F1E8)
val Ink = Color(0xFF1C1B19)
val Sage = Color(0xFF4E5D4C)
val SageSoft = Color(0xFFD7DFD1)
val Mist = Color(0xFFE8E2D6)
val Night = Color(0xFF161513)
val Moon = Color(0xFFE8E4DC)
val SageMoon = Color(0xFFA8B5A4)

val HabitPalette = listOf(
    Color(0xFF4E5D4C),
    Color(0xFF5C6B7A),
    Color(0xFF8A5A44),
    Color(0xFF4A5368),
    Color(0xFF6B6A40),
    Color(0xFF7A5360),
)

private val LightColors = lightColorScheme(
    primary = Sage,
    onPrimary = Color.White,
    primaryContainer = SageSoft,
    onPrimaryContainer = Ink,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Color(0xFF5C5850),
    outline = Color(0xFFC4BDB0),
    secondary = Color(0xFF6A6258),
    onSecondary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = SageMoon,
    onPrimary = Night,
    primaryContainer = Color(0xFF2E352C),
    onPrimaryContainer = Moon,
    background = Night,
    onBackground = Moon,
    surface = Night,
    onSurface = Moon,
    surfaceVariant = Color(0xFF2A2824),
    onSurfaceVariant = Color(0xFFB9B3A8),
    outline = Color(0xFF4A4741),
    secondary = Color(0xFFC2B8AA),
    onSecondary = Night,
)

@Composable
fun RotaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}

fun habitColor(index: Int): Color = HabitPalette[index.mod(HabitPalette.size)]
