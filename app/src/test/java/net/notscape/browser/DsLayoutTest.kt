package net.notscape.browser

import net.notscape.browser.emu.DsArrangement
import net.notscape.browser.emu.DsContent
import net.notscape.browser.emu.DsLayout
import net.notscape.browser.emu.DsPipCorner
import net.notscape.browser.emu.DsRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DS geometry: the design's pixel budgets, the
 * narrow-window rules, and the pointer encoding the
 * pinned core expects. Pixel budgets at reference
 * density: gap 42, margins 220, portrait PiP
 * 384x288, landscape PiP 480x360, minimum band 240,
 * minimum screen width 480. */
class DsLayoutTest {
    private fun portrait(
        w: Float,
        h: Float,
        arrangement: DsArrangement =
            DsArrangement.STACKED,
        swapped: Boolean = false,
        integer: Boolean = false,
        cutout: Float = 0f,
        forceStacked: Boolean = false
    ) = DsLayout.geometry(
        w, h, false, arrangement, swapped,
        DsPipCorner.BOTTOM_RIGHT, integer, cutout,
        42f, 220f, 384f, 288f, 240f, 480f, forceStacked
    )

    private fun landscape(
        w: Float,
        h: Float,
        arrangement: DsArrangement =
            DsArrangement.SIDE_BY_SIDE,
        swapped: Boolean = false,
        integer: Boolean = false
    ) = DsLayout.geometry(
        w, h, true, arrangement, swapped,
        DsPipCorner.BOTTOM_RIGHT, integer, 0f,
        42f, 220f, 480f, 360f, 240f, 480f, false
    )

    @Test
    fun portraitStackedMatchesDesignBudget() {
        val g = portrait(1080f, 2400f)
        assertEquals(2, g.quads.size)
        val top = g.quads[0]
        val bottom = g.quads[1]
        assertEquals(DsContent.TOP, top.content)
        assertEquals(DsContent.BOTTOM, bottom.content)
        assertEquals(1080f, top.rect.width, 0.01f)
        assertEquals(810f, top.rect.height, 0.01f)
        assertEquals(852f, bottom.rect.top, 0.01f)
        assertEquals(42f, bottom.rect.top - top.rect.bottom, 0.01f)
        // Controls band: 2400 - 1662 = 738.
        assertEquals(738f, 2400f - bottom.rect.bottom, 0.01f)
        assertNotNull(g.hinge)
        assertEquals(bottom.rect, g.touchRect)
        assertNull(g.pipRect)
        assertFalse(g.focusFallback)
    }

    @Test
    fun portraitStackedHonoursCutout() {
        val g = portrait(1080f, 2400f, cutout = 100f)
        assertEquals(100f, g.quads[0].rect.top, 0.01f)
        assertEquals(638f, 2400f - g.quads[1].rect.bottom, 0.01f)
    }

    @Test
    fun portraitSwapMovesTouchToTopQuad() {
        val g = portrait(1080f, 2400f, swapped = true)
        assertEquals(DsContent.BOTTOM, g.quads[0].content)
        assertEquals(DsContent.TOP, g.quads[1].content)
        assertEquals(g.quads[0].rect, g.touchRect)
    }

    @Test
    fun portraitFocusBottomIsTouchableMain() {
        val g = portrait(
            1080f, 2400f,
            arrangement = DsArrangement.FOCUS_BOTTOM
        )
        assertEquals(DsContent.BOTTOM, g.quads[0].content)
        assertEquals(g.quads[0].rect, g.touchRect)
        assertNotNull(g.pipRect)
        assertEquals(384f, g.pipRect!!.width, 0.01f)
        assertEquals(288f, g.pipRect!!.height, 0.01f)
        assertNull(g.hinge)
    }

    @Test
    fun portraitFocusTopHasNoTouchRect() {
        val g = portrait(
            1080f, 2400f,
            arrangement = DsArrangement.FOCUS_TOP
        )
        assertEquals(DsContent.TOP, g.quads[0].content)
        assertNull(g.touchRect)
        assertNotNull(g.pipRect)
    }

    @Test
    fun compactStackedShrinksToKeepBand() {
        // 720x1280 at the test's 42px gap: screens
        // shrink until the band is exactly 240px.
        // (The design's 672x504 example uses a 32px
        // gap at a lower density; same rule.)
        val g = portrait(720f, 1280f)
        assertFalse(g.focusFallback)
        assertEquals(665.33f, g.quads[0].rect.width, 0.01f)
        assertEquals(499f, g.quads[0].rect.height, 0.01f)
        assertEquals(240f, 1280f - g.quads[1].rect.bottom, 0.01f)
    }

    @Test
    fun tinyWindowFallsBackToFocus() {
        val g = portrait(360f, 640f)
        assertTrue(g.focusFallback)
        assertEquals(
            DsContent.BOTTOM, g.quads[0].content
        )
        assertNotNull(g.pipRect)
    }

    @Test
    fun tinyWindowForceStackedStaysStacked() {
        val g = portrait(360f, 640f, forceStacked = true)
        assertFalse(g.focusFallback)
        assertEquals(DsContent.TOP, g.quads[0].content)
    }

    @Test
    fun landscapeSideBySideMatchesDesignBudget() {
        val g = landscape(2400f, 1080f)
        assertEquals(DsContent.TOP, g.quads[0].content)
        assertEquals(DsContent.BOTTOM, g.quads[1].content)
        assertEquals(959f, g.quads[0].rect.width, 0.01f)
        assertEquals(719.25f, g.quads[0].rect.height, 0.01f)
        assertEquals(220f, g.quads[0].rect.left, 0.01f)
        assertEquals(42f, g.quads[1].rect.left - g.quads[0].rect.right, 0.01f)
        assertEquals(g.quads[1].rect, g.touchRect)
        assertNotNull(g.hinge)
    }

    @Test
    fun landscapeStackedColumnHasHugeFlanks() {
        val g = landscape(
            2400f, 1080f,
            arrangement = DsArrangement.STACKED_COLUMN
        )
        assertEquals(692f, g.quads[0].rect.width, 0.01f)
        assertEquals(519f, g.quads[0].rect.height, 0.01f)
        assertEquals(854f, g.quads[0].rect.left, 0.01f)
    }

    @Test
    fun landscapeFocusMainIsHeightLimited() {
        val g = landscape(
            2400f, 1080f,
            arrangement = DsArrangement.FOCUS_BOTTOM
        )
        assertEquals(1440f, g.quads[0].rect.width, 0.01f)
        assertEquals(1080f, g.quads[0].rect.height, 0.01f)
        assertEquals(g.quads[0].rect, g.touchRect)
        assertEquals(480f, g.pipRect!!.width, 0.01f)
        assertEquals(360f, g.pipRect!!.height, 0.01f)
    }

    @Test
    fun integerScaleSnapsToWholeMultiples() {
        val g = portrait(1080f, 2400f, integer = true)
        assertEquals(1024f, g.quads[0].rect.width, 0.01f)
        assertEquals(768f, g.quads[0].rect.height, 0.01f)
    }

    @Test
    fun layoutCycleSkipsNonApplicableArrangements() {
        assertEquals(
            DsArrangement.FOCUS_TOP,
            DsLayout.nextArrangement(
                DsArrangement.STACKED, false
            )
        )
        assertEquals(
            DsArrangement.STACKED,
            DsLayout.nextArrangement(
                DsArrangement.FOCUS_BOTTOM, false
            )
        )
        assertEquals(
            DsArrangement.STACKED_COLUMN,
            DsLayout.nextArrangement(
                DsArrangement.SIDE_BY_SIDE, true
            )
        )
        assertEquals(
            DsArrangement.SIDE_BY_SIDE,
            DsLayout.nextArrangement(
                DsArrangement.FOCUS_BOTTOM, true
            )
        )
    }

    @Test
    fun pointerEncodingMatchesCoreBuffer() {
        // Corners of the touch quad against the
        // full 256x384 buffer, clamped to int16.
        assertEquals(-32768 to 0, DsLayout.pointerFor(0f, 0f))
        assertEquals(32767 to 32767, DsLayout.pointerFor(1f, 1f))
        assertEquals(0 to 16384, DsLayout.pointerFor(0.5f, 0.5f))
        // Out-of-quad drags clamp, never wrap.
        assertEquals(-32768 to 0, DsLayout.pointerFor(-1f, -1f))
        assertEquals(32767 to 32767, DsLayout.pointerFor(2f, 2f))
    }

    @Test
    fun ndcQuadMapsUvSubRects() {
        val top = DsLayout.ndcQuad(
            DsRect(0f, 0f, 1080f, 810f),
            DsContent.TOP, 1080f, 2400f
        )
        assertEquals(-1f, top[0], 0.001f)
        assertEquals(0.325f, top[1], 0.001f)
        assertEquals(1f, top[2], 0.001f)
        assertEquals(1f, top[3], 0.001f)
        assertEquals(0f, top[5], 0.001f)
        assertEquals(0.5f, top[7], 0.001f)
        val bottom = DsLayout.ndcQuad(
            DsRect(0f, 852f, 1080f, 1662f),
            DsContent.BOTTOM, 1080f, 2400f
        )
        assertEquals(-0.385f, bottom[1], 0.001f)
        assertEquals(0.29f, bottom[3], 0.001f)
        assertEquals(0.5f, bottom[5], 0.001f)
        assertEquals(1f, bottom[7], 0.001f)
    }
}
