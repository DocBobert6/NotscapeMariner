package net.notscape.browser.browser

import net.notscape.browser.data.DownloadRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-M5: the chrome pill must never show a private
 * download's title, on any tab. Pins the shared
 * filter the pill uses.
 */
class DownloadPillFilterTest {

    private fun rec(
        id: Long,
        status: String,
        isPrivate: Boolean
    ): DownloadRecord = DownloadRecord(
        id, "Title $id", "https://example.test/$id",
        "file$id.mp4", -1, status, 0, isPrivate = isPrivate
    )

    @Test
    fun privateDownloadsAreExcludedInEveryStatus() {
        val downloads = listOf(
            rec(1, "queued", true),
            rec(2, "downloading", true),
            rec(3, "paused", true),
            rec(4, "complete", true)
        )
        assertTrue(activePillDownloads(downloads).isEmpty())
    }

    @Test
    fun nonPrivateActiveDownloadsAreIncluded() {
        val downloads = listOf(
            rec(1, "queued", false),
            rec(2, "downloading", false),
            rec(3, "paused", false),
            rec(4, "complete", false),
            rec(5, "failed", false),
            rec(6, "cancelled", false)
        )
        assertEquals(
            listOf(1L, 2L, 3L),
            activePillDownloads(downloads).map { it.id }
        )
    }

    @Test
    fun mixedListKeepsOnlyNonPrivateActive() {
        val downloads = listOf(
            rec(1, "downloading", true),
            rec(2, "downloading", false)
        )
        assertEquals(
            listOf(2L),
            activePillDownloads(downloads).map { it.id }
        )
    }
}
