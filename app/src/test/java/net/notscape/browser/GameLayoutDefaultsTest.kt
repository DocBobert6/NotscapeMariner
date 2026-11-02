package net.notscape.browser

import net.notscape.browser.emu.ControlSpec
import net.notscape.browser.emu.GameLayout
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PS1 variant defaults (beta.17). Parse skip is
 * JVM-tested: the org.json test dependency shadows
 * the Android stub (the favorites round-trip in
 * GameLibraryTest already proved it). */
class GameLayoutDefaultsTest {
    @Test
    fun digitalHasNoSticks() {
        val specs = GameLayout.defaults("ps1", false, "Digital")
        assertTrue(specs.none { it.analog >= 0 })
    }

    @Test
    fun singleHasLeftStickOnly() {
        val specs = GameLayout.defaults("ps1", false, "Single")
        assertEquals(
            listOf(0),
            specs.filter { it.analog >= 0 }.map { it.analog }
        )
        assertEquals(
            "lstick",
            specs.first { it.analog == 0 }.id
        )
    }

    @Test
    fun dualHasBothSticks() {
        val specs = GameLayout.defaults("ps1", false, "Dual")
        assertEquals(
            listOf(0, 1),
            specs.filter { it.analog >= 0 }.map { it.analog }
                .sorted()
        )
    }

    @Test
    fun portraitKeepsSticks() {
        val specs = GameLayout.defaults("ps1", true, "Dual")
        assertEquals(2, specs.count { it.analog >= 0 })
    }

    @Test
    fun unknownVariantFallsBackToDual() {
        val specs = GameLayout.defaults("ps1", false, "Bogus")
        assertEquals(2, specs.count { it.analog >= 0 })
    }

    @Test
    fun nonPs1IgnoresVariant() {
        val specs = GameLayout.defaults("snes", false, "Dual")
        assertFalse(specs.any { it.analog >= 0 })
    }

    @Test
    fun parseSkipsMalformedSpecs() {
        val json = """[
            {"id":"up","label":"Up","bit":4,"cx":0.5,"cy":0.2,"size":48},
            {"id":"bad","label":"Bad","cx":0.5,"cy":0.5,"size":48},
            {"id":"down","label":"Down","bit":5,"cx":0.5,"cy":0.8,"size":48}
        ]"""
        val specs = GameLayout.parse(json)
        assertNotNull(specs)
        assertEquals(2, specs!!.size)
        assertEquals("up", specs[0].id)
        assertEquals("down", specs[1].id)
    }

    @Test
    fun parseAllMalformedIsNull() {
        assertNull(GameLayout.parse("""[{"id":"bad"}]"""))
        assertNull(GameLayout.parse("not json"))
    }

    /** Dpad fix: no two default controls overlap, and
     * none hangs off the canvas, for every console in
     * both orientations. Buttons and sticks are
     * circles; pills are horizontal capsules (a
     * rounded rect is a segment plus its radius), so
     * the check uses centre distances, not boxes - a
     * box check would false-positive on the pad
     * diagonals, whose circles sit 77dp apart.
     * Canvases: 852x393 landscape, 393x852 portrait. */
    @Test
    fun defaultsHaveNoOverlappingControls() {
        for (console in GameLayout.CONSOLES) {
            val variants = if (console == "ps1") {
                listOf("Digital", "Single", "Dual")
            } else {
                listOf("Dual")
            }
            for (variant in variants) {
                checkLayoutNoOverlap(
                    GameLayout.defaults(console, false, variant),
                    852f, 393f, "$console landscape $variant"
                )
                checkLayoutNoOverlap(
                    GameLayout.defaults(console, true, variant),
                    393f, 852f, "$console portrait $variant"
                )
            }
        }
    }

    private fun checkLayoutNoOverlap(
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
                assertTrue(
                    "$tag: ${a.id} overlaps ${b.id}",
                    !controlsOverlap(a, ax, ay, b, bx, by)
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
