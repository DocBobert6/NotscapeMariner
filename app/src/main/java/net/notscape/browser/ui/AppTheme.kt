package net.notscape.browser.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import net.notscape.browser.data.AppStore

/** Mariner teal accent on neutral, Safari-like surfaces. */
val MarinerTeal = Color(0xFF0E7C8C)
val MarinerTealDark = Color(0xFF57C1D4)
val MarinerGold = Color(0xFFE8C468)

fun notscapeLightColors(): ColorScheme = lightColorScheme(
    primary = MarinerTeal,
    onPrimary = Color.White,
    secondary = MarinerTeal,
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE9E9EE),
    onSurfaceVariant = Color(0xFF3A3A3C),
    outline = Color(0xFFC7C7CC)
)

fun notscapeDarkColors(): ColorScheme = darkColorScheme(
    primary = MarinerTealDark,
    onPrimary = Color(0xFF062A31),
    secondary = MarinerTealDark,
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFD1D1D6),
    outline = Color(0xFF48484A)
)

@Composable
fun NotscapeTheme(store: AppStore, content: @Composable () -> Unit) {
    val rev = store.revision
    val mode = store.setting("themeMode", "system")
    val dark = when (mode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) notscapeDarkColors() else notscapeLightColors(),
        content = content
    )
}
