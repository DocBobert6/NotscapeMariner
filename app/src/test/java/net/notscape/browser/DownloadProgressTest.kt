package net.notscape.browser

import net.notscape.browser.data.DownloadRecord
import net.notscape.browser.media.Downloads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Beta.64 #30: size / speed / ETA formatting. */
class DownloadProgressTest {

    private fun rec(downloaded: Long, total: Long, speed: Long): DownloadRecord {
        val r = DownloadRecord(
            1, "T", "https://example.com/f", "f.bin",
            -1, "downloading", 0
        )
        r.bytesDownloaded = downloaded
        r.totalBytes = total
        r.speedBps = speed
        return r
    }

    @Test
    fun formatBytes() {
        assertEquals("48 B", Downloads.formatBytes(48))
        assertEquals("832.0 KB", Downloads.formatBytes(832 * 1024))
        assertEquals("12.4 MB", Downloads.formatBytes((12.4 * 1024 * 1024).toLong()))
        assertEquals("2.50 GB", Downloads.formatBytes((2.5 * 1024 * 1024 * 1024).toLong()))
        assertEquals("—", Downloads.formatBytes(-1))
    }

    @Test
    fun formatSpeedAndEta() {
        assertEquals("1.2 MB/s", Downloads.formatSpeed((1.2 * 1024 * 1024).toLong()))
        assertEquals("30s left", Downloads.formatEta(30))
        assertEquals("2m 15s left", Downloads.formatEta(135))
        assertEquals("1h 5m left", Downloads.formatEta(3900))
    }

    @Test
    fun detailLineFull() {
        assertEquals(
            "12.4 MB of 48.0 MB • 1.2 MB/s • 29s left",
            Downloads.progressDetailLine(
                rec(
                    (12.4 * 1024 * 1024).toLong(),
                    (48.0 * 1024 * 1024).toLong(),
                    (1.2 * 1024 * 1024).toLong()
                )
            )
        )
    }

    @Test
    fun detailLineUnknownTotal() {
        assertEquals(
            "12.4 MB",
            Downloads.progressDetailLine(rec((12.4 * 1024 * 1024).toLong(), -1, -1))
        )
    }

    @Test
    fun detailLineNothingKnown() {
        assertNull(Downloads.progressDetailLine(rec(-1, -1, -1)))
    }
}
