package net.notscape.browser.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.notscape.browser.data.AppStore

/**
 * iOS 26 design tokens (Phase A): Apple's semantic system colors, the SF
 * type scale mapped onto the platform font, and the concentric-radius
 * shape ladder. Chrome material is the solid-frost recipe (see
 * FrostKit.kt — near-solid gradient, hairline, real shadow; no blur
 * anywhere). The old Mariner teal is retired everywhere; the single
 * accent is systemBlue.
 */
val IosBlue = Color(0xFF007AFF)
val IosBlueDark = Color(0xFF0A84FF)
val IosRed = Color(0xFFFF3B30)
val IosRedDark = Color(0xFFFF453A)

fun notscapeLightColors(): ColorScheme = lightColorScheme(
    primary = IosBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F2FF),
    onPrimaryContainer = Color(0xFF003E73),
    secondary = IosBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2F2F7),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = IosBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8F2FF),
    onTertiaryContainer = Color(0xFF003E73),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF3C3C43),
    surfaceDim = Color(0xFFE5E5EA),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F2F7),
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFE9E9EE),
    surfaceContainerHighest = Color(0xFFE5E5EA),
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE5E5EA),
    error = IosRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFE9E7),
    onErrorContainer = Color(0xFF7A1008),
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color(0xFFF2F2F7),
    inversePrimary = Color(0xFF82B7FF),
    scrim = Color.Black
)

fun notscapeDarkColors(): ColorScheme = darkColorScheme(
    primary = IosBlueDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0B355E),
    onPrimaryContainer = Color(0xFFBFD9FF),
    secondary = IosBlueDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFF2F2F7),
    tertiary = IosBlueDark,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF0B355E),
    onTertiaryContainer = Color(0xFFBFD9FF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF1C1C1E),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF98989F),
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF2C2C2E),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = Color(0xFF38383A),
    outlineVariant = Color(0xFF48484A),
    error = IosRedDark,
    onError = Color.White,
    errorContainer = Color(0xFF4A1712),
    onErrorContainer = Color(0xFFFFDAD4),
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color(0xFF1C1C1E),
    inversePrimary = Color(0xFF007AFF),
    scrim = Color.Black
)

/** SF type scale (Dynamic Type) mapped to Material slots. */
private fun notscapeTypography(): Typography = Typography(
    displayLarge = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)
)

/** Concentric-radius ladder: sections 20, sheets 28, small controls 12. */
private fun notscapeShapes(): Shapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
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
        typography = notscapeTypography(),
        shapes = notscapeShapes(),
        content = content
    )
}

/**
 * The shared screen header in the app's iOS-26 language: a chevron back
 * button, a large title, optional trailing actions, and a hairline below.
 * Used by every sub-screen so navigation looks and feels identical
 * everywhere (library screens, settings, vault, bookshelf, reader).
 * [contentColor]/[dividerColor] let the reader header sit on its own
 * page palette instead of the app theme.
 */
@Composable
fun AppHeader(
    title: String,
    onBack: () -> Unit,
    contentColor: Color = Color.Unspecified,
    dividerColor: Color = Color.Unspecified,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val tint = if (contentColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else contentColor
    Column {
        CompositionLocalProvider(LocalContentColor provides tint) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "Back",
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                actions()
            }
        }
        HorizontalDivider(
            color = if (dividerColor == Color.Unspecified) {
                MaterialTheme.colorScheme.outlineVariant
            } else dividerColor
        )
    }
}
