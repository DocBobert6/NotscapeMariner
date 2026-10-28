package net.notscape.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

/** Address-bar display: the full URL, verbatim. The host-only display
 * used to break copy — the bar has no copy button, so a long-press copy
 * takes the displayed field text, and a copied host lands on the
 * homepage. */
class AddressDisplayTest {

    @Test
    fun fullUrlPassesThroughVerbatim() {
        assertEquals(
            "https://github.com/DocBobert6/NotscapeMariner",
            displayFor("https://github.com/DocBobert6/NotscapeMariner")
        )
    }

    @Test
    fun queryAndWwwKeptVerbatim() {
        assertEquals(
            "https://www.example.com/search?q=notscape&page=2",
            displayFor("https://www.example.com/search?q=notscape&page=2")
        )
    }

    @Test
    fun blankAndAboutBlankDisplayEmpty() {
        assertEquals("", displayFor(""))
        assertEquals("", displayFor("   "))
        assertEquals("", displayFor("about:blank"))
    }
}
