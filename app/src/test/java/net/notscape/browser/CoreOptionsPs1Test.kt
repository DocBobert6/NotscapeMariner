package net.notscape.browser

import net.notscape.browser.emu.CoreOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PlayStation core options: the table is curated from the
 * pinned SwanStation option table, so these tests guard
 * the curation itself - exact keys, defaults inside their
 * value lists, no duplicate keys, no runaway dropdowns,
 * and the two dangerous mappings stayed out (a hardware
 * renderer the shim cannot provide, and BIOS paths the
 * app's own import flow owns).
 */
class CoreOptionsPs1Test {
    @Test
    fun ps1TableIsCuratedAndWellFormed() {
        val options = CoreOptions.forConsole("ps1")
        assertTrue("ps1 should have core options", options.isNotEmpty())
        val keys = options.map { it.key }
        assertEquals("no duplicate keys", keys.size, keys.toSet().size)
        for (option in options) {
            assertTrue(
                "${option.key} should be a swanstation_ key",
                option.key.startsWith("swanstation_")
            )
            assertTrue(
                "${option.key} default ${option.default} should be a value",
                option.default in option.values
            )
            assertTrue(
                "${option.key} should have a label",
                option.label.isNotBlank()
            )
            assertTrue(
                "${option.key} dropdown should stay short",
                option.values.size <= 12
            )
        }
    }

    @Test
    fun ps1TableHasTheExpectedHeadliners() {
        val keys = CoreOptions.forConsole("ps1").map { it.key }.toSet()
        for (key in listOf(
            "swanstation_Console_Region",
            "swanstation_CPU_ExecutionMode",
            "swanstation_GPU_TextureFilter",
            "swanstation_GPU_PGXPEnable",
            "swanstation_Display_CropMode",
            "swanstation_CDROM_ReadSpeedup"
        )) {
            assertTrue("$key should be mapped", key in keys)
        }
    }

    @Test
    fun ps1TableLeavesOutFrontendOwnedKeys() {
        val keys = CoreOptions.forConsole("ps1").map { it.key }.toSet()
        assertFalse(
            "hardware renderer must stay out (shim has no HW context)",
            "swanstation_GPU_Renderer" in keys
        )
        assertFalse(
            "BIOS paths must stay out (app owns BIOS import)",
            "swanstation_BIOS_PathNTSCU" in keys
        )
        assertFalse(
            "memory card types must stay out (app owns the .srm card)",
            "swanstation_MemoryCards_Card1Type" in keys
        )
    }

    @Test
    fun otherConsolesAreUnchanged() {
        for (console in listOf("gb", "nes", "snes", "genesis")) {
            assertTrue(
                "$console should still have options",
                CoreOptions.forConsole(console).isNotEmpty()
            )
        }
        assertTrue(
            "unknown consoles still map to nothing",
            CoreOptions.forConsole("atari").isEmpty()
        )
    }
}
