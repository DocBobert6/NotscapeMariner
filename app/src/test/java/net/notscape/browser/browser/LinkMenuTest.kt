package net.notscape.browser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same-page anchor detection for the "Open links
 * in a new tab" setting: only a fragment-only hop
 * may stay in the current tab. */
class LinkMenuTest {

    @Test
    fun anchorToFragmentStaysPut() {
        assertTrue(
            isAnchorLink(
                "https://example.com/page",
                "https://example.com/page#section"
            )
        )
    }

    @Test
    fun fragmentToFragmentStaysPut() {
        assertTrue(
            isAnchorLink(
                "https://example.com/page#old",
                "https://example.com/page#new"
            )
        )
    }

    @Test
    fun fragmentBackToBarePageStaysPut() {
        assertTrue(
            isAnchorLink(
                "https://example.com/page#old",
                "https://example.com/page"
            )
        )
    }

    @Test
    fun exactSelfLinkIsNotAnAnchor() {
        assertFalse(
            isAnchorLink(
                "https://example.com/page",
                "https://example.com/page"
            )
        )
    }

    @Test
    fun differentPathIsNotAnAnchor() {
        assertFalse(
            isAnchorLink(
                "https://example.com/page",
                "https://example.com/other"
            )
        )
    }

    @Test
    fun differentHostIsNotAnAnchor() {
        assertFalse(
            isAnchorLink(
                "https://example.com/page",
                "https://other.com/page#section"
            )
        )
    }

    @Test
    fun queryChangeIsNotAnAnchor() {
        assertFalse(
            isAnchorLink(
                "https://example.com/page",
                "https://example.com/page?x=1#section"
            )
        )
    }

    @Test
    fun blankUrlsAreNotAnchors() {
        assertFalse(isAnchorLink("", "https://example.com/page#s"))
        assertFalse(isAnchorLink("https://example.com/page", ""))
    }
}
