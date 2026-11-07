package net.notscape.browser

import net.notscape.browser.emu.CoreOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase J (J7): core-option tables are well formed. */
class CoreOptionsTest {

    @Test
    fun everyDefaultIsAValueAndKeysAreUnique() {
        for (console in CoreOptionsTestConsoles.ALL) {
            val options = CoreOptions.forConsole(console)
            assertTrue(console, options.isNotEmpty())
            val keys = options.map { it.key }
            assertEquals(keys.size, keys.toSet().size)
            for (option in options) {
                assertTrue(
                    console + ":" + option.key,
                    option.default in option.values
                )
            }
        }
    }

    @Test
    fun colorCorrectionPresentForMgbaConsoles() {
        for (console in listOf("gb", "gbc", "gba")) {
            val option = CoreOptions.forConsole(console)
                .firstOrNull { it.key == "mgba_color_correction" }
            assertTrue(option != null)
            assertEquals(
                listOf("OFF", "GBA", "GBC", "Auto"),
                option!!.values
            )
        }
    }

    @Test
    fun unknownConsoleHasNoOptions() {
        assertTrue(CoreOptions.forConsole("psp").isEmpty())
    }

    @Test
    fun ps1HasNoResolutionScaleOption() {
        // Beta.60: swanstation_GPU_ResolutionScale is
        // a no-op under the software renderer, so the
        // row is hidden - it must not appear in the
        // options the UI renders or the pairs the
        // frontend sends the core.
        val keys = CoreOptions.forConsole("ps1").map { it.key }
        assertTrue(keys.isNotEmpty())
        assertFalse(keys.contains("swanstation_GPU_ResolutionScale"))
    }
}

private object CoreOptionsTestConsoles {
    val ALL = listOf(
        "gb", "gbc", "gba", "nes", "snes", "genesis", "sms", "gg", "ds",
        "segacd", "32x"
    )
}
