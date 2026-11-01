package net.notscape.browser

import net.notscape.browser.emu.CoreDownload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Core update checks: the pure hash-compare and
 * key-dedupe logic. Manifest fetching, file hashing
 * and downloading are verified live, not here. */
class CoreUpdateCheckTest {
    @Test
    fun updateAvailableWhenHashesDiffer() {
        assertTrue(
            CoreDownload.isUpdateAvailable(
                "aaaa", "bbbb"
            )
        )
    }

    @Test
    fun noUpdateWhenHashesMatch() {
        assertFalse(
            CoreDownload.isUpdateAvailable(
                "aaaa", "aaaa"
            )
        )
    }

    @Test
    fun hashCompareIsCaseInsensitive() {
        assertFalse(
            CoreDownload.isUpdateAvailable(
                "AABBCC", "aabbcc"
            )
        )
    }

    @Test
    fun missingInstalledHashIsNotAnUpdate() {
        assertFalse(
            CoreDownload.isUpdateAvailable(
                null, "bbbb"
            )
        )
    }

    @Test
    fun dedupeCollapsesGenesisPlusGxConsoles() {
        assertEquals(
            listOf("genesis_plus_gx"),
            CoreDownload.dedupeKeys(
                listOf("genesis", "sms", "gg", "segacd")
            )
        )
    }

    @Test
    fun dedupeKeepsFirstSeenOrder() {
        assertEquals(
            listOf("mgba", "fceumm", "snes9x"),
            CoreDownload.dedupeKeys(
                listOf("gba", "gb", "nes", "gbc", "snes")
            )
        )
    }

    @Test
    fun dedupeDropsUnknownSystems() {
        assertEquals(
            listOf("mgba"),
            CoreDownload.dedupeKeys(
                listOf("atari", "gb", "")
            )
        )
    }

    @Test
    fun dedupeEmptyStaysEmpty() {
        assertEquals(
            emptyList<String>(),
            CoreDownload.dedupeKeys(emptyList())
        )
    }
}
