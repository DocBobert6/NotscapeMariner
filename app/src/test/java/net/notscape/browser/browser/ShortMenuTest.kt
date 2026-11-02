package net.notscape.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Two-tier menu: the short sheet's row set, and the guarantee
 * that the full menu page still carries every row. */
class ShortMenuTest {

    @Test
    fun shortSheetRowsAreTheEverydayNineInOrder() {
        assertEquals(
            listOf(
                "newTab", "newPrivateTab", "reload", "sharePage",
                "copyPageUrl", "bookmark", "favorites", "desktopSite",
                "findInPage"
            ),
            BrowserMenuRows.SHORT
        )
    }

    @Test
    fun shortSheetRowsAllExistOnTheFullPage() {
        val all = BrowserMenuRows.ALL.map { it.first }.toSet()
        assertEquals(33, all.size)
        for (id in BrowserMenuRows.SHORT) {
            assertTrue("short row $id missing from ALL", id in all)
        }
        assertEquals(BrowserMenuRows.SHORT.size, BrowserMenuRows.SHORT.toSet().size)
    }

    @Test
    fun privateHiddenShortRowsStayHiddenOnPrivateTabs() {
        // Bookmark / favorites persist something, so the private-tab
        // rule must keep them out of the short sheet too.
        assertTrue(!BrowserMenuRows.visibleForTab("bookmark", true))
        assertTrue(!BrowserMenuRows.visibleForTab("favorites", true))
        assertTrue(BrowserMenuRows.visibleForTab("bookmark", false))
        assertTrue(BrowserMenuRows.visibleForTab("newTab", true))
    }
}
