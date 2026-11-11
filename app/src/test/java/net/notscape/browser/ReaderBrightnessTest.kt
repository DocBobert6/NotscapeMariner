package net.notscape.browser

import net.notscape.browser.reader.brightnessDim
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderBrightnessTest {

    @Test
    fun fullBrightnessMeansNoDim() {
        assertEquals(0f, brightnessDim(100), 0.001f)
    }

    @Test
    fun floorIs065() {
        assertEquals(0.65f, brightnessDim(35), 0.001f)
    }

    @Test
    fun halfIsHalf() {
        assertEquals(0.5f, brightnessDim(50), 0.001f)
    }

    @Test
    fun outOfRangeClamps() {
        assertEquals(0.65f, brightnessDim(0), 0.001f)
        assertEquals(0f, brightnessDim(200), 0.001f)
    }
}
