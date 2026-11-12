package net.notscape.browser

import net.notscape.browser.data.DownloadRecord
import net.notscape.browser.data.findMediaDownload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Beta.64 #53: playlist rows show download/offline state. */
class PlaylistDownloadStateTest {

    private fun rec(url: String, media: Boolean, status: String): DownloadRecord =
        DownloadRecord(
            1, "T", url, "f.mp4", -1, status, 0, media
        )

    @Test
    fun matchesMediaDownloadByExactUrl() {
        val downloads = listOf(
            rec("https://v.example/1", true, "complete"),
            rec("https://v.example/2", false, "complete")
        )
        assertEquals("complete", findMediaDownload(downloads, "https://v.example/1")?.status)
        // A plain file download of the same URL is not an
        // offline copy of the video.
        assertNull(findMediaDownload(downloads, "https://v.example/2"))
        assertNull(findMediaDownload(downloads, "https://v.example/3"))
    }
}
