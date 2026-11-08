package net.notscape.browser

import net.notscape.browser.emu.DiscSwap
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscSwapTest {
    @Test
    fun labelUsedWhenPresent() {
        assertEquals("Disc 2: The Journey", DiscSwap.displayLabel(1, "Disc 2: The Journey"))
    }

    @Test
    fun blankLabelFallsBackToDiscNumber() {
        assertEquals("Disc 1", DiscSwap.displayLabel(0, ""))
        assertEquals("Disc 2", DiscSwap.displayLabel(1, "   "))
    }

    @Test
    fun nullLabelFallsBackToDiscNumber() {
        assertEquals("Disc 3", DiscSwap.displayLabel(2, null))
    }

    @Test
    fun labelIsTrimmed() {
        assertEquals("Final Disc", DiscSwap.displayLabel(0, "  Final Disc  "))
    }
}
