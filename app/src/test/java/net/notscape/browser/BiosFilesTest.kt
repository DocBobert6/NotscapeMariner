package net.notscape.browser

import net.notscape.browser.emu.BiosFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BIOS section: every console that takes a BIOS names
 * real files, and the no-BIOS consoles name none.
 */
class BiosFilesTest {
    @Test
    fun biosConsolesNameBinFiles() {
        for (console in listOf("gb", "gbc", "gba", "genesis")) {
            val files = BiosFiles.expectedFor(console)
            assertTrue("$console should name BIOS files", files.isNotEmpty())
            for (name in files) {
                assertTrue("$name should be a .bin", name.endsWith(".bin"))
            }
        }
        assertEquals(
            listOf("gb_bios.bin", "sgb_bios.bin"),
            BiosFiles.expectedFor("gb")
        )
        assertEquals(
            listOf("bios_MD.bin"),
            BiosFiles.expectedFor("genesis")
        )
    }

    @Test
    fun noBiosConsolesNameNone() {
        for (console in listOf("nes", "snes", "sms", "gg")) {
            assertTrue(
                "$console should need no BIOS",
                BiosFiles.expectedFor(console).isEmpty()
            )
        }
        assertTrue(
            "unknown console should need no BIOS",
            BiosFiles.expectedFor("ps1").isEmpty()
        )
    }
}
