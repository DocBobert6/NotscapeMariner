package net.notscape.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Beta.37: console -> core credit caption mapping. */
class CoreCreditTest {
    @Test
    fun credits() {
        assertEquals("mGBA", coreCreditFor("gb"))
        assertEquals("mGBA", coreCreditFor("gbc"))
        assertEquals("mGBA", coreCreditFor("gba"))
        assertEquals("FCEUmm", coreCreditFor("nes"))
        assertEquals("Snes9x", coreCreditFor("snes"))
        assertEquals("Genesis Plus GX", coreCreditFor("genesis"))
        assertEquals("Genesis Plus GX", coreCreditFor("sms"))
        assertEquals("Genesis Plus GX", coreCreditFor("gg"))
        assertEquals("Genesis Plus GX", coreCreditFor("segacd"))
        assertEquals("SwanStation", coreCreditFor("ps1"))
        assertEquals("melonDS", coreCreditFor("ds"))
        assertEquals("PicoDrive", coreCreditFor("32x"))
        assertEquals("", coreCreditFor("unknown"))
    }
}
