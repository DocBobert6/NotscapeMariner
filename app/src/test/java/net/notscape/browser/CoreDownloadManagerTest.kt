package net.notscape.browser

import net.notscape.browser.emu.CoreDownloadManager
import org.junit.Assert.assertEquals
import org.junit.Test

/** Beta.69 #38: the bulk update-check summary is pure,
 *  so the unit suite pins its wording and ordering. */
class CoreDownloadManagerTest {
    @Test
    fun allUpToDate() {
        assertEquals(
            "All cores up to date",
            CoreDownloadManager.bulkUpdateSummary(0, 7, 0)
        )
    }

    @Test
    fun someUpdated() {
        assertEquals(
            "Core updates: 1 updated, 6 up to date",
            CoreDownloadManager.bulkUpdateSummary(1, 6, 0)
        )
    }

    @Test
    fun someFailed() {
        assertEquals(
            "Core updates: 5 up to date, 2 failed",
            CoreDownloadManager.bulkUpdateSummary(0, 5, 2)
        )
    }

    @Test
    fun mixedCountsInFixedOrder() {
        assertEquals(
            "Core updates: 2 updated, 3 up to date, 1 failed",
            CoreDownloadManager.bulkUpdateSummary(2, 3, 1)
        )
    }

    @Test
    fun onlyFailed() {
        assertEquals(
            "Core updates: 3 failed",
            CoreDownloadManager.bulkUpdateSummary(0, 0, 3)
        )
    }

    @Test
    fun nothingCountedReadsUpToDate() {
        assertEquals(
            "All cores up to date",
            CoreDownloadManager.bulkUpdateSummary(0, 0, 0)
        )
    }
}
