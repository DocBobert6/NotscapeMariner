package net.notscape.browser.ui

import android.os.Build
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
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
 * anywhere). The old Mariner teal is retired everywhere. Phase G
 * (Robert's direction, 2026-10-22): the systemBlue accent is retired
 * too — the default theme's single accent is now a monotone ink
 * (near-black in light mode, near-white in dark), matching the
 * iOS-style neutral look he asked for. Material You mode still uses
 * the phone's dynamic colours; that is its purpose.
 */
// The banner typefaces (Phase G, Robert's pairing): Cormorant
// Garamond Bold carries the wordmark; Montserrat carries the
// letterspaced MARINER line and the subtitle. Both OFL-licensed.
// (Cinzel — the logo artwork's literal letterforms — was tried
// first in 0.0.948; Robert preferred this pairing, and so did the
// honest comparison.)
val WordmarkFontFamily = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(net.notscape.browser.R.font.cormorant_bold)
)
val SmallCapsFontFamily = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(
        net.notscape.browser.R.font.montserrat_regular,
        androidx.compose.ui.text.font.FontWeight.Normal
    ),
    androidx.compose.ui.text.font.Font(
        net.notscape.browser.R.font.montserrat_semibold,
        androidx.compose.ui.text.font.FontWeight.SemiBold
    )
)

val IosInk = Color(0xFF1C1C1E)
val IosInkDark = Color(0xFFF2F2F7)
val IosRed = Color(0xFFFF3B30)
val IosRedDark = Color(0xFFFF453A)

fun notscapeLightColors(): ColorScheme = lightColorScheme(
    primary = IosInk,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E5EA),
    onPrimaryContainer = Color(0xFF1C1C1E),
    secondary = IosInk,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2F2F7),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = IosInk,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5E5EA),
    onTertiaryContainer = Color(0xFF1C1C1E),
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
    inversePrimary = Color(0xFFF2F2F7),
    scrim = Color.Black
)

fun notscapeDarkColors(): ColorScheme = darkColorScheme(
    primary = IosInkDark,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3A3A3C),
    onPrimaryContainer = Color(0xFFF2F2F7),
    secondary = IosInkDark,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFF2F2F7),
    tertiary = IosInkDark,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF3A3A3C),
    onTertiaryContainer = Color(0xFFF2F2F7),
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
    inversePrimary = Color(0xFF1C1C1E),
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
    // Material You gained manual variants (Phase G, Robert's
    // direction): materialyou-light / materialyou-dark pin the
    // dynamic palette instead of following the system.
    val dark = when (mode) {
        "dark", "materialyou-dark" -> true
        "light", "materialyou-light" -> false
        else -> isSystemInDarkTheme()
    }
    // Material You (Robert's addition, Phase C): only the COLOURS come
    // from the phone's dynamic palette — shapes, type and the frost
    // recipe are untouched. Follows the system's light/dark state.
    // Android 12+; older devices fall back to the standard schemes.
    val context = LocalContext.current
    val scheme = when {
        mode.startsWith("materialyou") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> notscapeDarkColors()
        else -> notscapeLightColors()
    }
    MaterialTheme(
        colorScheme = scheme,
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
