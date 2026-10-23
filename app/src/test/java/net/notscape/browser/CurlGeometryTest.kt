package net.notscape.browser

import androidx.compose.ui.geometry.Offset
import net.notscape.browser.ui.foldPolygons
import net.notscape.browser.ui.reflectAcross
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlGeometryTest {

    private fun area(poly: List<Offset>): Float {
        var a = 0f
        for (i in poly.indices) {
            val p = poly[i]
            val q = poly[(i + 1) % poly.size]
            a += p.x * q.y - q.x * p.y
        }
        return kotlin.math.abs(a) / 2f
    }

    @Test
    fun noLiftWhenFingerOnCorner() {
        val (lifted, _, flat) = foldPolygons(
            100f, 200f, Offset(100f, 0f), Offset(100f, 0f)
        )
        assertTrue(lifted.isEmpty())
        assertEquals(20000f, area(flat), 1f)
    }

    @Test
    fun verticalBisectorSplitsEvenly() {
        // Corner top-right, finger top-left: fold line x = 50.
        val (lifted, reflected, flat) = foldPolygons(
            100f, 200f, Offset(100f, 0f), Offset(0f, 0f)
        )
        assertEquals(10000f, area(lifted), 1f)
        assertEquals(10000f, area(flat), 1f)
        assertEquals(10000f, area(reflected), 1f)
        assertTrue(lifted.all { it.x >= 49f })
        assertTrue(reflected.all { it.x <= 51f })
    }

    @Test
    fun bottomCornerDragUp() {
        // Corner bottom-right, finger straight above it:
        // fold line y = 150, lifted is the bottom strip.
        val (lifted, _, _) = foldPolygons(
            100f, 200f, Offset(100f, 200f), Offset(100f, 100f)
        )
        assertEquals(5000f, area(lifted), 1f)
        assertTrue(lifted.all { it.y >= 149f })
    }

    @Test
    fun sheetBackCrossesTheSpine() {
        // Finger dragged past the spine (0.0.989): the
        // reflected sheet must reach over the other page
        // (negative x in this page's space) at full area,
        // not be cut at the gutter.
        val (lifted, reflected, _) = foldPolygons(
            100f, 200f, Offset(100f, 0f), Offset(-60f, 0f)
        )
        assertEquals(16000f, area(lifted), 1f)
        assertEquals(16000f, area(reflected), 1f)
        assertTrue(reflected.all { it.x >= -61f })
        assertTrue(reflected.any { it.x < 0f })
    }

    @Test
    fun reflectAcrossVerticalLine() {
        val r = reflectAcross(
            Offset(100f, 30f), 50f, 0f, 0f, 1f
        )
        assertEquals(0f, r.x, 0.01f)
        assertEquals(30f, r.y, 0.01f)
    }
}
