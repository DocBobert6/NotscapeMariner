package net.notscape.browser

import net.notscape.browser.emu.ControlSpec
import net.notscape.browser.emu.GameLayout
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DS defaults: the exact button set (no sticks,
 * no invented buttons), the same ids in both
 * orientations, and zero overlap on the D-pad
 * test's canvases (852x393 landscape, 393x852
 * portrait), using its circle/capsule geometry. */
class DsDefaultsTest {
    private val expectedIds = listOf(
        "up", "down", "left", "right",
        "x", "y", "b", "a",
        "l", "r", "select", "start"
    )

    private val expectedBits = mapOf(
        "up" to 4, "down" to 5, "left" to 6, "right" to 7,
        "x" to 9, "y" to 1, "b" to 0, "a" to 8,
        "l" to 10, "r" to 11, "select" to 2, "start" to 3
    )

    @Test
    fun dsHasExactButtonSetBothOrientations() {
        for (portrait in listOf(false, true)) {
            val specs = GameLayout.defaults("ds", portrait)
            assertEquals(expectedIds, specs.map { it.id })
            assertTrue(specs.none { it.analog >= 0 })
            for (spec in specs) {
                assertEquals(
                    expectedBits[spec.id], spec.bit
                )
            }
        }
    }

    @Test
    fun dsDefaultsHaveNoOverlappingControls() {
        checkNoOverlap(
            GameLayout.defaults("ds", false),
            852f, 393f, "ds landscape"
        )
        checkNoOverlap(
            GameLayout.defaults("ds", true),
            393f, 852f, "ds portrait"
        )
    }

    private fun checkNoOverlap(
        specs: List<ControlSpec>,
        canvasW: Float,
        canvasH: Float,
        tag: String
    ) {
        for (i in specs.indices) {
            for (j in i + 1 until specs.size) {
                val a = specs[i]
                val b = specs[j]
                val ax = a.cx * canvasW
                val ay = a.cy * canvasH
                val bx = b.cx * canvasW
                val by = b.cy * canvasH
                assertFalse(
                    "$tag: ${a.id} overlaps ${b.id}",
                    controlsOverlap(a, ax, ay, b, bx, by)
                )
            }
        }
        for (s in specs) {
            val w = s.sizeDp.toFloat()
            val h = if (s.pill) s.sizeDp * 0.46f else w
            val x = s.cx * canvasW
            val y = s.cy * canvasH
            assertTrue(
                "$tag: ${s.id} hangs off the canvas",
                x - w / 2f >= 0f && x + w / 2f <= canvasW &&
                    y - h / 2f >= 0f && y + h / 2f <= canvasH
            )
        }
    }

    private fun controlsOverlap(
        a: ControlSpec, ax: Float, ay: Float,
        b: ControlSpec, bx: Float, by: Float
    ): Boolean {
        fun halfLen(s: ControlSpec): Float =
            if (s.pill) {
                (s.sizeDp - s.sizeDp * 0.46f) / 2f
            } else {
                0f
            }
        fun radius(s: ControlSpec): Float =
            if (s.pill) s.sizeDp * 0.46f / 2f else s.sizeDp / 2f
        if (!a.pill && !b.pill) {
            val dx = ax - bx
            val dy = ay - by
            return sqrt(dx * dx + dy * dy) < radius(a) + radius(b)
        }
        if (a.pill && !b.pill) {
            return controlsOverlap(b, bx, by, a, ax, ay)
        }
        if (!a.pill && b.pill) {
            val cx = minOf(maxOf(ax, bx - halfLen(b)), bx + halfLen(b))
            val dx = ax - cx
            val dy = ay - by
            return sqrt(dx * dx + dy * dy) < radius(a) + radius(b)
        }
        val dx = maxOf(
            0f, abs(ax - bx) - (halfLen(a) + halfLen(b))
        )
        val dy = abs(ay - by)
        return sqrt(dx * dx + dy * dy) < radius(a) + radius(b)
    }
}
