package aenu.aps3e.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Aps3eColors {
    val Background = Color(0xFF0A0E27)
    val Surface = Color(0xFF1A1F3A)
    val Primary = Color(0xFF4A90E2)
    val Secondary = Color(0xFF2BC4D9)
    val OnBackground = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFFE0E0E0)
    val CardBackground = Color(0xFF141829)
    val Accent = Color(0xFF6EE7F2)
    val Warning = Color(0xFFF2C94C)
    val Danger = Color(0xFFEB5757)
}

@Composable
fun Aps3eTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Aps3eColors.Background,
            surface = Aps3eColors.Surface,
            primary = Aps3eColors.Primary,
            secondary = Aps3eColors.Secondary,
            onBackground = Aps3eColors.OnBackground,
            onSurface = Aps3eColors.OnSurface
        ),
        content = content
    )
}
