package net.notscape.browser

import net.notscape.browser.browser.FilterEngine
import net.notscape.browser.emu.StartShortcuts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Audit4Parity1bTest {

    @Test
    fun abpValidationAcceptsRealRules() {
        val bad = FilterEngine.validateAbpRules(
            "||example.com^\n@@||allow.com^\n##.ad-banner\n! comment\n"
        )
        assertTrue(bad.isEmpty())
    }

    @Test
    fun abpValidationFlagsEmptyPattern() {
        val bad = FilterEngine.validateAbpRules("||")
        assertEquals(listOf(1), bad)
    }

    @Test
    fun startShortcutsSitesLoadFiltersUrls() {
        val pins = StartShortcuts.load { _, _ ->
            """{"sites":["https://example.com","ftp://x","not a url"]}"""
        }
        assertEquals(listOf("https://example.com"), pins.sites)
    }
}
