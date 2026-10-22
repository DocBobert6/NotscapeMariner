package net.notscape.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The solid-frost design kit (Phase C): one material recipe for every
 * floating surface in the app, extracted from the 0.0.63 address bar.
 *
 *  - Floating bars and panels: shadow, clip, a near-solid vertical
 *    gradient (surface @0.97 → @0.92, lighter top edge), and a 0.5dp
 *    hairline (outline @0.45). Plain compositing only — no blur and
 *    no capture passes (the 0.0.61 tombstone and Robert's 0.0.63
 *    verdict are why).
 *  - Sheets: SOLID surfaceContainerLow with 28dp top corners. Never
 *    an alpha container colour — that breaks Material's
 *    content-colour lookup and produced dark text on dark sheets
 *    (the 0.0.59 lesson). Grouped cards in surfaceBright sit on top.
 *  - Radii follow the theme's concentric ladder: cards 20, sheets
 *    28, pills full.
 */
object Frost {
    val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val CardShape = RoundedCornerShape(20.dp)
    val PillShape = RoundedCornerShape(50)

    /** The floating-bar gradient: near-solid, lighter at the top. */
    @Composable
    fun brush(): Brush = Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    )

    @Composable
    fun hairline(): Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    /** The solid sheet colour — never an alpha colour (see above). */
    @Composable
    fun sheetColor(): Color = MaterialTheme.colorScheme.surfaceContainerLow

    /** The grouped-card colour used on sheets and grouped lists. */
    @Composable
    fun cardColor(): Color = MaterialTheme.colorScheme.surfaceBright
}

/**
 * The floating frost material as a modifier: shadow, clip, gradient,
 * hairline — in that order, exactly as the address bar has worn it
 * since 0.0.63.
 */
@Composable
fun Modifier.frostBar(shape: Shape = Frost.PillShape, elevation: Dp = 6.dp): Modifier =
    this.shadow(elevation, shape)
        .clip(shape)
        .background(Frost.brush())
        .border(0.5.dp, Frost.hairline(), shape)

/** A grouped card in the kit's card material and radius. */
@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    shape: Shape = Frost.CardShape,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Frost.cardColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

/** A bottom sheet in the kit's sheet material and shape. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrostSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Frost.sheetColor(),
        shape = Frost.SheetShape,
        content = content
    )
}
