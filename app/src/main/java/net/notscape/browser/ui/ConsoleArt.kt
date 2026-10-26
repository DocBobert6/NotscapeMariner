package net.notscape.browser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Original console artwork for the Games home cards
 * (2026-10-24): simplified geometric silhouettes
 * drawn from each system's form factor - a tall
 * brick for Game Boy, a wide bar for Game Boy
 * Advance, a boxy deck for NES, and so on. These
 * are our own drawings, not company or console
 * logo artwork: no wordmarks, no logo shapes, no
 * trademarked emblems. The console name beside
 * the art already says what it is.
 */
@Composable
fun ConsoleArt(
    console: String,
    modifier: Modifier = Modifier,
    // Inset inside the canvas: the default 10dp leaves
    // almost nothing to draw at 24dp shortcut size,
    // so small usages pass a smaller inset.
    padding: Dp = 10.dp
) {
    // No background (Robert, 2026-10-24): the
    // silhouette sits transparent on whatever card
    // or row holds it.
    val body = when (console) {
        "gb" -> Color(0xFFB9B9B2)
        "gbc" -> Color(0xFF8B7AB8)
        "gba" -> Color(0xFF5B5FBF)
        "nes" -> Color(0xFFC9C9C9)
        "snes" -> Color(0xFFB8B8C8)
        "genesis" -> Color(0xFF17171A)
        "sms" -> Color(0xFF3F3F46)
        "ds" -> Color(0xFF2E3A52)
        else -> Color(0xFF17171A)
    }
    val screen = Color(0xFF101812)
    val dark = Color(0xFF2B2B2E)
    Canvas(
        modifier = modifier
            .padding(padding)
    ) {
        // Fixed-proportion frame (beta.4): the art
        // is drawn in a centred square, so a wide
        // landscape card cannot stretch it.
        val s = minOf(size.width, size.height)
        val ox = (size.width - s) / 2f
        val oy = (size.height - s) / 2f
        val w = s
        val h = s
        when (console) {
            "gb", "gbc" -> {
                // Tall brick, screen up top, cross
                // pad lower left, two buttons right.
                val bw = w * 0.42f
                val bh = h * 0.92f
                val bx = ox + (w - bw) / 2f
                val by = oy + (h - bh) / 2f
                drawRoundRect(
                    color = body,
                    topLeft = Offset(bx, by),
                    size = Size(bw, bh),
                    cornerRadius = CornerRadius(bw * 0.10f)
                )
                drawRect(
                    color = screen,
                    topLeft = Offset(bx + bw * 0.14f, by + bh * 0.10f),
                    size = Size(bw * 0.72f, bh * 0.34f)
                )
                val cy = by + bh * 0.62f
                val cx = bx + bw * 0.30f
                val t = bw * 0.07f
                drawRect(
                    color = dark,
                    topLeft = Offset(cx - t, cy - bw * 0.16f),
                    size = Size(t * 2f, bw * 0.32f)
                )
                drawRect(
                    color = dark,
                    topLeft = Offset(cx - bw * 0.16f, cy - t),
                    size = Size(bw * 0.32f, t * 2f)
                )
                drawCircle(
                    color = if (console == "gbc") {
                        Color(0xFFC03030)
                    } else {
                        Color(0xFF7A2020)
                    },
                    radius = bw * 0.10f,
                    center = Offset(bx + bw * 0.68f, cy - bw * 0.05f)
                )
                drawCircle(
                    color = if (console == "gbc") {
                        Color(0xFF2B6CB0)
                    } else {
                        Color(0xFF7A2020)
                    },
                    radius = bw * 0.10f,
                    center = Offset(bx + bw * 0.68f, cy + bw * 0.18f)
                )
            }
            "gba", "gg" -> {
                // Wide handheld, screen centre,
                // pad left, buttons right.
                val bw = w * 0.88f
                val bh = h * 0.62f
                val bx = ox + (w - bw) / 2f
                val by = oy + (h - bh) / 2f
                drawRoundRect(
                    color = body,
                    topLeft = Offset(bx, by),
                    size = Size(bw, bh),
                    cornerRadius = CornerRadius(bh * 0.42f)
                )
                drawRect(
                    color = screen,
                    topLeft = Offset(bx + bw * 0.32f, by + bh * 0.18f),
                    size = Size(bw * 0.36f, bh * 0.64f)
                )
                val cy = by + bh * 0.50f
                val cx = bx + bw * 0.16f
                val t = bw * 0.035f
                drawRect(
                    color = dark,
                    topLeft = Offset(cx - t, cy - bw * 0.07f),
                    size = Size(t * 2f, bw * 0.14f)
                )
                drawRect(
                    color = dark,
                    topLeft = Offset(cx - bw * 0.07f, cy - t),
                    size = Size(bw * 0.14f, t * 2f)
                )
                drawCircle(
                    color = Color(0xFFC03030),
                    radius = bw * 0.045f,
                    center = Offset(bx + bw * 0.80f, cy - bw * 0.02f)
                )
                drawCircle(
                    color = Color(0xFFC03030),
                    radius = bw * 0.045f,
                    center = Offset(bx + bw * 0.88f, cy + bw * 0.05f)
                )
            }
            "ds" -> {
                // Clamshell, suggested: two stacked
                // rounded rectangles with a hinge bar
                // between them, the lower screen
                // carrying the faintest touch dot.
                val bw = w * 0.62f
                val bh = h * 0.34f
                val bx = ox + (w - bw) / 2f
                val topY = oy + h * 0.12f
                val botY = oy + h * 0.54f
                drawRoundRect(
                    color = body,
                    topLeft = Offset(bx, topY),
                    size = Size(bw, bh),
                    cornerRadius = CornerRadius(bh * 0.08f)
                )
                drawRect(
                    color = screen,
                    topLeft = Offset(
                        bx + bw * 0.08f, topY + bh * 0.12f
                    ),
                    size = Size(bw * 0.84f, bh * 0.76f)
                )
                drawRect(
                    color = dark,
                    topLeft = Offset(bx, topY + bh + h * 0.02f),
                    size = Size(bw, h * 0.04f)
                )
                drawRoundRect(
                    color = body,
                    topLeft = Offset(bx, botY),
                    size = Size(bw, bh),
                    cornerRadius = CornerRadius(bh * 0.08f)
                )
                drawRect(
                    color = screen,
                    topLeft = Offset(
                        bx + bw * 0.08f, botY + bh * 0.12f
                    ),
                    size = Size(bw * 0.84f, bh * 0.76f)
                )
                drawCircle(
                    color = Color(0xFF8A8A8A),
                    radius = bh * 0.07f,
                    center = Offset(
                        bx + bw * 0.50f, botY + bh * 0.50f
                    )
                )
            }
            "ps1" -> {
                // Flat grey deck, round disc lid
                // on the left, power dot right.
                val bw = w * 0.88f
                val bh = h * 0.52f
                val bx = ox + (w - bw) / 2f
                val by = oy + (h - bh) / 2f
                drawRoundRect(
                    color = body,
                    topLeft = Offset(bx, by),
                    size = Size(bw, bh),
                    cornerRadius = CornerRadius(bh * 0.12f)
                )
                drawCircle(
                    color = dark,
                    radius = bh * 0.34f,
                    center = Offset(
                        bx + bw * 0.28f, by + bh * 0.50f
                    )
                )
                drawCircle(
                    color = Color(0xFFC03030),
                    radius = bh * 0.08f,
                    center = Offset(
                        bx + bw * 0.85f, by + bh * 0.24f
                    )
                )
            }
            else -> {
                // Home decks: wide box, front
                // stripe, small power/detail marks.
                val bw = w * 0.88f
                val bh = h * 0.52f
                val bx = ox + (w - bw) / 2f
                val by = oy + (h - bh) / 2f
                drawRoundRect(
                    color = body,
                    topLeft = Offset(bx, by),
                    size = Size(bw, bh),
                    cornerRadius = CornerRadius(bh * 0.12f)
                )
                val stripe = when (console) {
                    "nes" -> Color(0xFF1A1A1A)
                    "snes" -> Color(0xFF6B6B8A)
                    "sms" -> Color(0xFF5A7AB0)
                    else -> Color(0xFFC03030)
                }
                drawRect(
                    color = stripe,
                    topLeft = Offset(bx, by + bh * 0.42f),
                    size = Size(bw, bh * 0.14f)
                )
                if (console == "genesis") {
                    // Raised centre block on the deck.
                    drawRoundRect(
                        color = dark,
                        topLeft = Offset(
                            bx + bw * 0.30f, by - bh * 0.16f
                        ),
                        size = Size(bw * 0.40f, bh * 0.22f),
                        cornerRadius = CornerRadius(bh * 0.06f)
                    )
                }
                if (console == "sms") {
                    // Long top slot on the flatter deck.
                    drawRect(
                        color = dark,
                        topLeft = Offset(
                            bx + bw * 0.15f, by + bh * 0.08f
                        ),
                        size = Size(bw * 0.70f, bh * 0.07f)
                    )
                }
                drawRect(
                    color = dark,
                    topLeft = Offset(bx + bw * 0.08f, by + bh * 0.18f),
                    size = Size(bw * 0.18f, bh * 0.12f)
                )
                if (console == "snes") {
                    val dots = listOf(
                        Color(0xFFC03030),
                        Color(0xFFD8A020),
                        Color(0xFF2B8A3E),
                        Color(0xFF2B6CB0)
                    )
                    for (i in dots.indices) {
                        drawCircle(
                            color = dots[i],
                            radius = bh * 0.09f,
                            center = Offset(
                                bx + bw * (0.62f + i * 0.08f),
                                by + bh * 0.24f
                            )
                        )
                    }
                } else if (console == "sms") {
                    drawRect(
                        color = dark,
                        topLeft = Offset(
                            bx + bw * 0.70f, by + bh * 0.18f
                        ),
                        size = Size(bw * 0.06f, bh * 0.12f)
                    )
                    drawRect(
                        color = dark,
                        topLeft = Offset(
                            bx + bw * 0.80f, by + bh * 0.18f
                        ),
                        size = Size(bw * 0.06f, bh * 0.12f)
                    )
                } else {
                    drawCircle(
                        color = Color(0xFFC03030),
                        radius = bh * 0.11f,
                        center = Offset(bx + bw * 0.78f, by + bh * 0.24f)
                    )
                }
            }
        }
    }
}
