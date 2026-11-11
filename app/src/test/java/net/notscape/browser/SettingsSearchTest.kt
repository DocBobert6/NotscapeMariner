package net.notscape.browser

import net.notscape.browser.browser.filterSettingsSearch
import net.notscape.browser.browser.SETTINGS_SEARCH_ENTRIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    @Test
    fun blankQueryMatchesNothing() {
        assertTrue(filterSettingsSearch("").isEmpty())
        assertTrue(filterSettingsSearch("   ").isEmpty())
    }

    @Test
    fun titleMatchIsCaseInsensitive() {
        val hits = filterSettingsSearch("PRIVACY")
        assertTrue(hits.any { it.title == "Privacy & blocking" })
    }

    @Test
    fun subtitleMatchFindsDeepRows() {
        val hits = filterSettingsSearch("cookies")
        assertTrue(hits.any { it.title == "Clear browsing data" })
        assertTrue(hits.any { it.title == "Third-party cookies" })
    }

    @Test
    fun noMatchIsEmpty() {
        assertTrue(filterSettingsSearch("zzzz-no-such-setting").isEmpty())
    }

    @Test
    fun everyEntryOpensARealPage() {
        val pages = setOf(
            "appearance", "browsing", "search", "privacy", "data",
            "formFill",
            "video", "scripts", "favorites", "readingList",
            "accessibility", "blocked"
        )
        for (e in SETTINGS_SEARCH_ENTRIES) {
            assertTrue("entry ${e.title} -> ${e.page}", e.page in pages)
        }
    }

    @Test
    fun queryIsTrimmed() {
        assertEquals(
            filterSettingsSearch("privacy"),
            filterSettingsSearch("  privacy  ")
        )
    }
}
