package net.notscape.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** U4: the in-app notices list covers every shipped core and library. */
class ThirdPartyNoticesTest {
    @Test
    fun coversAllComponents() {
        val names = ThirdPartyNotices.entries.map { it.name }
        assertEquals(12, names.size)
        assertTrue(names.any { it.startsWith("mGBA") })
        assertTrue(names.any { it.startsWith("FCEUmm") })
        assertTrue(names.any { it.startsWith("Snes9x") })
        assertTrue(names.any { it.startsWith("Genesis Plus GX") })
        assertTrue(names.any { it.startsWith("SwanStation") })
        assertTrue(names.any { it.startsWith("melonDS") })
        assertTrue(names.any { it.startsWith("PicoDrive") })
        assertTrue(names.any { it.startsWith("oboe") })
        assertTrue(names.any { it.startsWith("sherpa-onnx") })
        assertTrue(names.any { it.startsWith("Media3") })
        assertTrue(names.any { it.startsWith("lcd3x") })
        assertTrue(names.any { it.startsWith("crt-guest-advanced-fast") })
    }

    @Test
    fun noBlankFields() {
        ThirdPartyNotices.entries.forEach { entry ->
            assertTrue(entry.name.isNotBlank())
            assertTrue(entry.license.isNotBlank())
            assertTrue(entry.summary.isNotBlank())
        }
    }
}
