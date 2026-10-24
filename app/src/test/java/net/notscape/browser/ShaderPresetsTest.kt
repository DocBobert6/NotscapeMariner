package net.notscape.browser

import net.notscape.browser.emu.GameLayout
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
                preset.scan, preset.mask, preset.blur
            )) {
                assertTrue("$console value $value in range",
                    value in 0f..1f)
            }
        }
        assertEquals(
            GameLayout.CONSOLES.size, names.size
        )
    }
}
