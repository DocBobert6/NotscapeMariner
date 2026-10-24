package net.notscape.browser.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Console glyphs (Phase J): a small original vector
 * glyph per console for the Games menu, drawn in each
 * system's own design colours. These are original
 * shapes, not the manufacturers' logo artwork - no
 * trademarked marks or lettering are reproduced. A
 * faint outline keeps the dark-bodied glyphs legible
 * on the dark menu background.
 */
@Composable
fun ConsoleGlyph(console: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.then(Modifier.size(32.dp))) {
        val u = size.width / 100f
        fun rr(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            color: Color,
            radius: Float = 6f
        ) {
            drawRoundRect(
                color = color,
                topLeft = Offset(left * u, top * u),
                size = Size((right - left) * u, (bottom - top) * u),
                cornerRadius = CornerRadius(radius * u, radius * u)
            )
        }
        fun rect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            color: Color
        ) {
            drawRect(
                color = color,
                topLeft = Offset(left * u, top * u),
                size = Size((right - left) * u, (bottom - top) * u)
            )
        }
        fun dot(cx: Float, cy: Float, r: Float, color: Color) {
            drawCircle(
                color = color,
                radius = r * u,
                center = Offset(cx * u, cy * u)
            )
        }
        fun cross(cx: Float, cy: Float, arm: Float, color: Color) {
            rect(cx - arm / 3, cy - arm, cx + arm / 3, cy + arm, color)
            rect(cx - arm, cy - arm / 3, cx + arm, cy + arm / 3, color)
        }
        fun outline(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            radius: Float = 6f
        ) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(left * u, top * u),
                size = Size((right - left) * u, (bottom - top) * u),
                cornerRadius = CornerRadius(radius * u, radius * u),
                style = Stroke(width = 2.5f * u)
            )
        }
        when (console) {
            "gb" -> {
                // Dot-matrix handheld: grey body, green screen.
                rr(18f, 8f, 82f, 92f, Color(0xFFC9C0B4), 10f)
                rect(28f, 18f, 72f, 44f, Color(0xFF0F380F))
                rect(32f, 22f, 68f, 40f, Color(0xFF306230))
                cross(40f, 62f, 10f, Color(0xFF2B2B2B))
                dot(62f, 60f, 4.5f, Color(0xFF9E1B32))
                dot(72f, 70f, 4.5f, Color(0xFF9E1B32))
                outline(18f, 8f, 82f, 92f, 10f)
            }
            "gbc" -> {
                // Colour handheld: atomic purple, colour screen.
                rr(20f, 6f, 80f, 94f, Color(0xFF8E7CC3), 12f)
                rect(30f, 16f, 70f, 42f, Color(0xFF1A1A2E))
                rect(34f, 20f, 42f, 38f, Color(0xFFD43D2A))
                rect(44f, 20f, 52f, 38f, Color(0xFF3AA33A))
                rect(54f, 20f, 62f, 38f, Color(0xFF2A5BD7))
                cross(38f, 62f, 9f, Color(0xFF2B2B2B))
                dot(62f, 62f, 4.5f, Color(0xFFD94F30))
                dot(70f, 70f, 4.5f, Color(0xFFD94F30))
                outline(20f, 6f, 80f, 94f, 12f)
            }
            "gba" -> {
                // Wide indigo handheld.
                rr(8f, 30f, 92f, 70f, Color(0xFF5B5BD6), 16f)
                rect(35f, 38f, 65f, 62f, Color(0xFF202820))
                rect(39f, 42f, 47f, 58f, Color(0xFF8BAC0F))
                rect(49f, 42f, 57f, 58f, Color(0xFF306230))
                cross(20f, 50f, 8f, Color(0xFF2B2B2B))
                dot(78f, 46f, 4f, Color(0xFFD43D2A))
                dot(86f, 54f, 4f, Color(0xFFD43D2A))
                outline(8f, 30f, 92f, 70f, 16f)
            }
            "nes" -> {
                // Grey box, black slot, red stripe.
                rr(10f, 30f, 90f, 70f, Color(0xFFC9C9C9), 4f)
                rect(10f, 44f, 90f, 50f, Color(0xFF2B2B2B))
                rect(10f, 58f, 90f, 64f, Color(0xFFD43D2A))
                dot(78f, 37f, 3f, Color(0xFFD43D2A))
                outline(10f, 30f, 90f, 70f, 4f)
            }
            "snes" -> {
                // Grey box, lavender stripe, four coloured buttons.
                rr(10f, 28f, 90f, 72f, Color(0xFFD8D8D8), 6f)
                rect(10f, 40f, 90f, 48f, Color(0xFF6B5CA5))
                dot(68f, 52f, 5f, Color(0xFF2A5BD7))
                dot(58f, 60f, 5f, Color(0xFF3AA33A))
                dot(78f, 60f, 5f, Color(0xFFD43D2A))
                dot(68f, 68f, 5f, Color(0xFFE8C832))
                outline(10f, 28f, 90f, 72f, 6f)
            }
            "genesis" -> {
                // Black wedge, red stripe.
                rr(10f, 32f, 90f, 68f, Color(0xFF171717), 4f)
                rect(18f, 38f, 82f, 42f, Color(0xFF333333))
                rect(10f, 46f, 90f, 54f, Color(0xFFE60012))
                dot(80f, 60f, 2.5f, Color(0xFFFF2222))
                outline(10f, 32f, 90f, 68f, 4f)
            }
            "sms" -> {
                // Dark box, red and blue stripes.
                rr(10f, 30f, 90f, 70f, Color(0xFF222222), 4f)
                rect(10f, 44f, 90f, 50f, Color(0xFFD43D2A))
                rect(10f, 52f, 90f, 58f, Color(0xFF2A5BD7))
                dot(78f, 37f, 3f, Color(0xFFE8E8E8))
                outline(10f, 30f, 90f, 70f, 4f)
            }
            "gg" -> {
                // Black handheld, blue screen.
                rr(22f, 6f, 78f, 94f, Color(0xFF141414), 14f)
                rect(32f, 16f, 68f, 48f, Color(0xFF2F6DB3))
                rect(36f, 20f, 64f, 26f, Color(0xFF8FC3E8))
                cross(40f, 66f, 9f, Color(0xFF3A3A3A))
                dot(60f, 64f, 4.5f, Color(0xFFD43D2A))
                dot(68f, 72f, 4.5f, Color(0xFF2A5BD7))
                outline(22f, 6f, 78f, 94f, 14f)
            }
            else -> {
                rr(20f, 20f, 80f, 80f, Color(0xFF888888), 10f)
                outline(20f, 20f, 80f, 80f, 10f)
            }
        }
    }
}
