package net.notscape.browser

import net.notscape.browser.emu.CoreDownload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phase M2: only the JVM-safe helpers are tested here
 * (SHA-256, console -> core mapping). Manifest parsing
 * uses org.json, which is an Android stub under local
 * unit tests, so it is verified live against the real
 * cores.json instead. */
class CoreDownloadTest {
    @Test
    fun sha256KnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            CoreDownload.sha256Hex("abc".toByteArray())
        )
    }

    @Test
    fun coreKeyMapping() {
        assertEquals("mgba", CoreDownload.coreKeyFor("gb"))
        assertEquals("mgba", CoreDownload.coreKeyFor("gbc"))
        assertEquals("mgba", CoreDownload.coreKeyFor("gba"))
        assertEquals("fceumm", CoreDownload.coreKeyFor("nes"))
        assertEquals("snes9x", CoreDownload.coreKeyFor("snes"))
        assertEquals(
            "genesis_plus_gx", CoreDownload.coreKeyFor("genesis")
        )
        assertEquals("genesis_plus_gx", CoreDownload.coreKeyFor("sms"))
        assertEquals("genesis_plus_gx", CoreDownload.coreKeyFor("gg"))
        assertNull(CoreDownload.coreKeyFor("ps1"))
        assertNull(CoreDownload.coreKeyFor(""))
    }

    @Test
    fun allKeysCoversEveryConsole() {
        val consoles = listOf(
            "gb", "gbc", "gba", "nes", "snes",
            "genesis", "sms", "gg"
        )
        for (system in consoles) {
            val key = CoreDownload.coreKeyFor(system)
            assertEquals(
                true,
                key != null && CoreDownload.allKeys.contains(key)
            )
        }
        assertEquals(4, CoreDownload.allKeys.size)
    }
}
