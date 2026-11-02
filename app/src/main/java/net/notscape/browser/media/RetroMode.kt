package net.notscape.browser.media

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Retro mode (video player, experimental Phase 1): TV frame + exact 4:3 +
 * cover crop + CRT glass overlay. Layout + overlay only — no change to the
 * video surface or decode path, so subtitles, DRM, HDR, and Cast are
 * unaffected by design.
 *
 * The pure helpers here are unit-testable on purpose (RetroModeTest); the
 * PlayerActivity composition applies them.
 */

/** Frame hole fractions, measured from tv-frame.png's alpha channel. */
object RetroFrame {
    const val HOLE_LEFT = 0.1515f
    const val HOLE_TOP = 0.050667f
    const val HOLE_WIDTH = 0.6925f
    const val HOLE_HEIGHT = 0.897778f

    /** Frame aspect (width / height): 2000 / 1125. */
    const val ASPECT = 2000f / 1125f
}

/** The exactly-4:3 picture box, relative to the stage (frame) origin. */
data class RetroBox(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

/** Largest frame-aspect rect fitting (maxW, maxH). Returns (stageW, stageH). */
fun retroStageSize(maxW: Float, maxH: Float): Pair<Float, Float> {
    val stageW = minOf(maxW, maxH * RetroFrame.ASPECT)
    val stageH = stageW / RetroFrame.ASPECT
    return stageW to stageH
}

/**
 * The exactly-4:3 box centered in the frame's hole. All values in the same
 * units as the stage inputs (dp or px — the math is unit-agnostic).
 * The hole (aspect 1.3716) is wider than 4:3, so the box takes the hole's
 * full height and leaves ~1.4% black pillars each side inside the glass.
 */
fun retroBoxInStage(stageW: Float, stageH: Float): RetroBox {
    val holeLeft = stageW * RetroFrame.HOLE_LEFT
    val holeTop = stageH * RetroFrame.HOLE_TOP
    val holeW = stageW * RetroFrame.HOLE_WIDTH
    val holeH = stageH * RetroFrame.HOLE_HEIGHT
    val boxH = holeH
    val boxW = boxH * 4f / 3f
    val boxLeft = holeLeft + (holeW - boxW) / 2f
    return RetroBox(boxLeft, holeTop, boxW, boxH)
}

/**
 * Cover overflow for a video of the given aspect inside a (boxW, boxH) box.
 * Returns (overflowX, overflowY): how much wider/taller the cover-scaled
 * picture is than the box. Exactly one axis is non-zero except at 4:3,
 * where both are zero.
 */
fun retroCoverOverflow(boxW: Float, boxH: Float, aspect: Float): Pair<Float, Float> {
    if (aspect <= 0f || boxW <= 0f || boxH <= 0f) return 0f to 0f
    val coverW = maxOf(boxW, boxH * aspect)
    val coverH = maxOf(boxH, boxW / aspect)
    return (coverW - boxW) to (coverH - boxH)
}

/**
 * Pan translation for one axis. overflow = cover size - box size on that axis
 * (>= 0 to pan). pan in [-1, 1]: -1 shows the leading edge (left/top),
 * +1 the trailing edge (right/bottom), 0 centered. Zero overflow means
 * the slider is inert on that axis by construction.
 */
fun retroPanTranslation(overflow: Float, pan: Float): Float =
    if (overflow <= 0f) 0f else -pan * overflow / 2f

/** Retro layout geometry in Dp, plus the picture-box size in px (the
 *  graphicsLayer pan translation is in px). */
data class RetroGeom(
    val stageW: androidx.compose.ui.unit.Dp,
    val stageH: androidx.compose.ui.unit.Dp,
    val boxLeft: androidx.compose.ui.unit.Dp,
    val boxTop: androidx.compose.ui.unit.Dp,
    val boxW: androidx.compose.ui.unit.Dp,
    val boxH: androidx.compose.ui.unit.Dp,
    val boxWpx: Float,
    val boxHpx: Float
)

/**
 * CRT glass: scanlines + aperture grille + vignette + slight warm tint,
 * drawn over the picture box only. Static content (drawn once per
 * composition, not animated), so the cost is one canvas pass.
 * The overlay must never consume touches: it carries no pointer-input
 * modifiers, so taps pass through to the player below.
 */
@Composable
fun RetroGlassOverlay(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        // Slight warm tint, like phosphor white.
        drawRect(color = Color(0xFFFFE8C0), alpha = 0.05f)
        // Scanlines: one dark line per ~h/240 rows.
        val pitchY = (h / 240f).coerceAtLeast(2f)
        val lineH = (pitchY * 0.35f).coerceAtLeast(1f)
        var y = 0f
        while (y < h) {
            drawRect(
                color = Color.Black, alpha = 0.22f,
                topLeft = Offset(0f, y), size = Size(w, lineH)
            )
            y += pitchY
        }
        // Aperture grille: faint vertical stripes.
        val pitchX = (w / 320f).coerceAtLeast(2f)
        val lineW = (pitchX * 0.30f).coerceAtLeast(1f)
        var x = 0f
        while (x < w) {
            drawRect(
                color = Color.Black, alpha = 0.10f,
                topLeft = Offset(x, 0f), size = Size(lineW, h)
            )
            x += pitchX
        }
        // Vignette: transparent center fading to dark corners.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
                center = Offset(w / 2f, h / 2f),
                radius = maxOf(w, h) * 0.75f
            )
        )
    }
}
