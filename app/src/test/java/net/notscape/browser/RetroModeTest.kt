package net.notscape.browser

import net.notscape.browser.media.retroBoxInStage
import net.notscape.browser.media.retroCoverOverflow
import net.notscape.browser.media.retroPanTranslation
import net.notscape.browser.media.retroStageSize
import org.junit.Assert.assertEquals
import org.junit.Test

/** Retro mode geometry: frame stage, 4:3 box, cover overflow, crop pan. */
class RetroModeTest {

    @Test
    fun stageFitsFrameAspect() {
        // Landscape screen wider than the frame: stage takes full height.
        val (w, h) = retroStageSize(2560f, 1080f)
        assertEquals(1920f, w, 0.01f)
        assertEquals(1080f, h, 0.01f)
        // Portrait screen narrower than the frame: stage takes full width.
        val (pw, ph) = retroStageSize(1080f, 1920f)
        assertEquals(1080f, pw, 0.01f)
        assertEquals(607.5f, ph, 0.01f)
    }

    @Test
    fun boxIsExactlyFourByThreeCenteredInHole() {
        // The test frame itself: 2000x1125, hole x 303..1687, y 57..1066.
        val box = retroBoxInStage(2000f, 1125f)
        assertEquals(57f, box.top, 0.01f)
        assertEquals(1010f, box.height, 0.5f)
        assertEquals(box.height * 4f / 3f, box.width, 0.01f)
        // Centered: left pillar equals right pillar.
        val holeLeft = 2000f * 0.1515f
        val holeRight = holeLeft + 2000f * 0.6925f
        assertEquals(box.left - holeLeft, holeRight - (box.left + box.width), 0.01f)
    }

    @Test
    fun coverOverflowWideTallAndSquare() {
        // 16:9 video in a 4:3 box: horizontal overflow only.
        val (ox, oy) = retroCoverOverflow(1200f, 900f, 16f / 9f)
        assertEquals(400f, ox, 0.01f)
        assertEquals(0f, oy, 0.01f)
        // 9:16 video: vertical overflow only.
        val (tx, ty) = retroCoverOverflow(1200f, 900f, 9f / 16f)
        assertEquals(0f, tx, 0.01f)
        assertEquals(1233.33f, ty, 0.01f)
        // Exact 4:3: no overflow, so the pan slider is inert by construction.
        val (sx, sy) = retroCoverOverflow(1200f, 900f, 4f / 3f)
        assertEquals(0f, sx, 0.01f)
        assertEquals(0f, sy, 0.01f)
    }

    @Test
    fun panTranslationEdgesAndCenter() {
        assertEquals(200f, retroPanTranslation(400f, -1f), 0.01f)
        assertEquals(0f, retroPanTranslation(400f, 0f), 0.01f)
        assertEquals(-200f, retroPanTranslation(400f, 1f), 0.01f)
        // Zero overflow: inert whatever the slider says.
        assertEquals(0f, retroPanTranslation(0f, 1f), 0.01f)
        assertEquals(0f, retroPanTranslation(0f, -1f), 0.01f)
    }
}
