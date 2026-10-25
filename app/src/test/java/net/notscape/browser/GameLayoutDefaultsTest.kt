package net.notscape.browser

import net.notscape.browser.emu.GameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PS1 variant defaults (beta.17). Parse/round-trip
 * is not JVM-tested: org.json is an Android stub
 * under local unit tests. */
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
}
