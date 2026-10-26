package net.notscape.browser

import net.notscape.browser.emu.GameLayout
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
}
