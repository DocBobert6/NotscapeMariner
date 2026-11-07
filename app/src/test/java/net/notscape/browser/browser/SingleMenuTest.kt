package net.notscape.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Single menu (Option B): one scrollable menu page carrying
 * every row, grouped under section labels, rendered in the
 * user's customized order. No short sheet anymore. */
class SingleMenuTest {

    @Test
    fun singleMenuCarriesAllThirtyThreeRows() {
        val ids = BrowserMenuRows.ALL.map { it.first }
        assertEquals(33, ids.size)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun defaultOrderCoversEveryRowExactlyOnce() {
        val all = BrowserMenuRows.ALL.map { it.first }.toSet()
        val order = BrowserMenuRows.DEFAULT_ORDER
        assertEquals(33, order.size)
        assertEquals(order.size, order.toSet().size)
        assertEquals(all, order.toSet())
    }

    @Test
    fun defaultOrderGroupsIntoFourSectionsInOrder() {
        val sections = BrowserMenuRows.DEFAULT_ORDER
            .map { BrowserMenuRows.sectionOf(it) }
            .fold(mutableListOf<BrowserMenuRows.MenuSection>()) { acc, s ->
                if (acc.lastOrNull() != s) acc.add(s)
                acc
            }
        assertEquals(
            listOf(
                BrowserMenuRows.MenuSection.TABS,
                BrowserMenuRows.MenuSection.PAGE,
                BrowserMenuRows.MenuSection.LIBRARY,
                BrowserMenuRows.MenuSection.SETTINGS
            ),
            sections
        )
    }

    @Test
    fun everyRowHasASection() {
        for ((id, _) in BrowserMenuRows.ALL) {
            // sectionOf never throws and always returns a value.
            assertTrue(BrowserMenuRows.sectionOf(id) in BrowserMenuRows.MenuSection.values())
        }
    }

    @Test
    fun privateHiddenRowsStayHiddenOnPrivateTabs() {
        // Bookmark / favorites persist something, so the private-tab
        // rule must keep them out of the single menu too.
        assertTrue(!BrowserMenuRows.visibleForTab("bookmark", true))
        assertTrue(!BrowserMenuRows.visibleForTab("favorites", true))
        assertTrue(BrowserMenuRows.visibleForTab("bookmark", false))
        assertTrue(BrowserMenuRows.visibleForTab("newTab", true))
    }
}
