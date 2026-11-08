package net.notscape.browser

import net.notscape.browser.emu.GameLayout
import net.notscape.browser.emu.ColorProfile
import net.notscape.browser.emu.ShaderPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shader presets: every console has one, every name
 * is a single word, names are unique, and every
 * value sits in [0, 1].
 */
class ShaderPresetsTest {
    @Test
    fun everyConsoleHasOnePreset() {
        val names = mutableSetOf<String>()
        for (console in GameLayout.CONSOLES) {
            val preset = ShaderPresets.forConsole(console)
            assertFalse(
                "${preset.name} should be one word",
                preset.name.contains(' ')
            )
            assertTrue(
                "${preset.name} should be unique",
                names.add(preset.name)
            )
            for (value in listOf(
                preset.scan, preset.grid, preset.blur
            )) {
                assertTrue("$console value $value in range",
                    value in 0f..1f)
            }
        }
        assertEquals(
            GameLayout.CONSOLES.size, names.size
        )
    }

    @Test
    fun lcdConsolesUseLcdPath() {
        for (console in listOf("gb", "gbc", "gba", "ds")) {
            val preset = ShaderPresets.forConsole(console)
            assertTrue("$console should use the LCD pass", preset.lcd)
            assertTrue("$console brightScan positive", preset.brightScan > 0f)
            assertTrue("$console brightLcd positive", preset.brightLcd > 0f)
        }
        for (console in listOf("nes", "snes", "genesis", "sms", "gg")) {
            assertFalse(
                "$console should use the legacy pass",
                ShaderPresets.forConsole(console).lcd
            )
        }
    }

    @Test
    fun curvePullTakesFullStrengthForCrtAndHalfForLcd() {
        val crt = ShaderPresets.forConsole("nes")
        val lcd = ShaderPresets.forConsole("gb")
        val other = ShaderPresets.forConsole("gg")
        assertEquals(0.010f, ShaderPresets.curvePull(crt, true, 50), 0.0001f)
        assertEquals(0.020f, ShaderPresets.curvePull(crt, true, 100), 0.0001f)
        assertEquals(0.005f, ShaderPresets.curvePull(lcd, true, 50), 0.0001f)
        assertEquals(0.010f, ShaderPresets.curvePull(lcd, true, 100), 0.0001f)
        assertEquals(0f, ShaderPresets.curvePull(other, true, 100), 0.0001f)
        assertEquals(0f, ShaderPresets.curvePull(crt, false, 100), 0.0001f)
        assertEquals(0f, ShaderPresets.curvePull(crt, true, 0), 0.0001f)
    }

    @Test
    fun displayDefaultsAreGentleAndPerPreset() {
        // CRT + LCD presets curve lightly; every
        // preset rounds lightly; the two legacy
        // consoles round but do not curve.
        for (console in listOf(
            "gb", "gbc", "gba", "nes", "snes", "genesis"
        )) {
            val preset = ShaderPresets.forConsole(console)
            assertTrue("$console curves by default", preset.curveOn)
            assertEquals("$console curve amount", 30, preset.curveAmount)
        }
        for (console in listOf("sms", "gg")) {
            val preset = ShaderPresets.forConsole(console)
            assertFalse("$console does not curve", preset.curveOn)
        }
        for (console in GameLayout.CONSOLES) {
            val preset = ShaderPresets.forConsole(console)
            assertTrue("$console rounds by default", preset.roundOn)
            assertEquals("$console round amount", 20, preset.roundAmount)
        }
    }

    @Test
    fun displayResolversPreferSetValuesThenPreset() {
        // Empty store: the preset decides.
        val empty: (String, String) -> String = { _, d -> d }
        assertTrue(ShaderPresets.curveOn(empty, "snes"))
        assertEquals(30, ShaderPresets.curveAmount(empty, "snes"))
        assertTrue(ShaderPresets.roundOn(empty, "snes"))
        assertEquals(20, ShaderPresets.roundAmount(empty, "snes"))
        assertFalse(ShaderPresets.curveOn(empty, "gg"))
        // A shared value beats the preset, and a
        // per-console value beats the shared one.
        val shared: (String, String) -> String = { k, d ->
            when (k) {
                "gameCurvature" -> "false"
                "gameCurvatureAmount" -> "80"
                else -> d
            }
        }
        assertFalse(ShaderPresets.curveOn(shared, "snes"))
        assertEquals(80, ShaderPresets.curveAmount(shared, "snes"))
        val per: (String, String) -> String = { k, d ->
            when (k) {
                "gameCurvature:snes" -> "true"
                "gameCurvatureAmount:snes" -> "45"
                "gameRoundCorners:snes" -> "false"
                "gameRoundAmount:snes" -> "60"
                else -> shared(k, d)
            }
        }
        assertTrue(ShaderPresets.curveOn(per, "snes"))
        assertEquals(45, ShaderPresets.curveAmount(per, "snes"))
        assertFalse(ShaderPresets.roundOn(per, "snes"))
        assertEquals(60, ShaderPresets.roundAmount(per, "snes"))
    }

    @Test
    fun crtConsolesUseCrtChain() {
        for (console in listOf("nes", "snes", "genesis")) {
            assertTrue(
                "$console should use the CRT chain",
                ShaderPresets.forConsole(console).crt
            )
        }
        for (console in listOf("gb", "gbc", "gba", "sms", "gg")) {
            assertFalse(
                "$console should not use the CRT chain",
                ShaderPresets.forConsole(console).crt
            )
        }
    }

    @Test
    fun everyConsoleHasAColorProfileInRange() {
        for (console in GameLayout.CONSOLES) {
            val c = ShaderPresets.forConsole(console).color
            assertTrue("$console saturation in range", c.saturation in 0.5f..1.5f)
            assertTrue("$console warm in range", c.warm in -100f..100f)
            assertTrue("$console gamma in range", c.gamma in 0.5f..2f)
            assertTrue("$console bright in range", c.bright in 0.5f..1.5f)
            assertTrue("$console black in range", c.black in -20f..20f)
        }
        // The Genesis DAC range fix belongs to
        // Genesis alone; every other console leaves
        // it off.
        assertTrue(ShaderPresets.forConsole("genesis").color.segaFix)
        for (console in GameLayout.CONSOLES - "genesis") {
            assertFalse(
                "$console should not use the Sega fix",
                ShaderPresets.forConsole(console).color.segaFix
            )
        }
        // GB stays neutral: its color character is
        // the core palette, and a shader tint would
        // fight it.
        assertEquals(
            ColorProfile(), ShaderPresets.forConsole("gb").color
        )
    }
}
