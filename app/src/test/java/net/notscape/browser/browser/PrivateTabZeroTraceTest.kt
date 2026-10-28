package net.notscape.browser.browser

import net.notscape.browser.media.shouldSavePlaybackPosition
import net.notscape.browser.ui.bookSaveAllowed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Private-tab zero-trace: nothing browsed in a private tab may
 * persist anywhere afterward. The UI gates hide the save actions
 * outright (no row that looks usable but does nothing); these
 * tests pin the gate conditions behind them, plus the surfaces
 * that were already clean. */
class PrivateTabZeroTraceTest {

    @Test
    fun privateHiddenSetIsExactlyThePersistingRows() {
        assertEquals(
            setOf("bookmark", "favorites", "screenshot", "addToHome"),
            BrowserMenuRows.PRIVATE_HIDDEN
        )
    }

    @Test
    fun persistingMenuRowsHideInPrivateTabsOnly() {
        for (id in BrowserMenuRows.PRIVATE_HIDDEN) {
            assertFalse("$id must hide in a private tab",
                BrowserMenuRows.visibleForTab(id, true))
            assertTrue("$id must show in a normal tab",
                BrowserMenuRows.visibleForTab(id, false))
        }
    }

    @Test
    fun nonPersistingMenuRowsStayInPrivateTabs() {
        for ((id, _) in BrowserMenuRows.ALL) {
            if (id !in BrowserMenuRows.PRIVATE_HIDDEN) {
                assertTrue("$id must stay in a private tab",
                    BrowserMenuRows.visibleForTab(id, true))
            }
        }
    }

    @Test
    fun playlistAddBlockedForPrivateTabs() {
        assertFalse(playlistAddAllowed(true))
        assertTrue(playlistAddAllowed(false))
    }

    @Test
    fun historyRecordedForNormalTabsOnly() {
        assertFalse(shouldRecordHistory(true))
        assertTrue(shouldRecordHistory(false))
    }

    @Test
    fun privateTabNeverRecordedAsClosed() {
        val list = TabList()
        list.recordClosed(TabState(1, "https://example.com", isPrivate = true))
        assertTrue(list.closedSnapshots.isEmpty())
        list.recordClosed(TabState(2, "https://example.com", isPrivate = false))
        assertEquals(1, list.closedSnapshots.size)
        assertEquals("https://example.com", list.closedSnapshots[0].url)
    }

    @Test
    fun privateSessionNeverSavesPlaybackPosition() {
        assertFalse(shouldSavePlaybackPosition(true))
        assertTrue(shouldSavePlaybackPosition(false))
    }

    @Test
    fun privateOriginNeverSavesBooks() {
        assertFalse(bookSaveAllowed(true))
        assertTrue(bookSaveAllowed(false))
    }
}
