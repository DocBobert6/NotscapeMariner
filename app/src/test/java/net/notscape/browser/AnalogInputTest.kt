package net.notscape.browser

import net.notscape.browser.emu.AnalogInput
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalogInputTest {
    @Test
    fun centredReadsZero() {
        assertEquals(0, AnalogInput.axis(0f))
        assertEquals(0, AnalogInput.axis(0.14f))
        assertEquals(0, AnalogInput.axis(-0.14f))
    }

    @Test
    fun fullTravelReachesFullScale() {
        assertEquals(32767, AnalogInput.axis(1f))
        assertEquals(-32767, AnalogInput.axis(-1f))
    }

    @Test
    fun deadzoneRescales() {
        // Halfway past the deadzone reads about half scale.
        val mid = AnalogInput.axis(0.575f)
        assert(mid in 16000..17000) { "mid was $mid" }
        val midNeg = AnalogInput.axis(-0.575f)
        assert(midNeg in -17000..-16000) { "midNeg was $midNeg" }
    }

    @Test
    fun outOfRangeClamps() {
        assertEquals(32767, AnalogInput.axis(2f))
        assertEquals(-32767, AnalogInput.axis(-2f))
    }
}
