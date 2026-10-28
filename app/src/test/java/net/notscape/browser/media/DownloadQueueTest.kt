package net.notscape.browser.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the downloads round (beta.44): per-download notification
 * ids, the queue limit, which engines resume, the job-bundle codec
 * the queue and resume paths depend on, and the rename rules.
 * State transitions that need WorkManager or a Store are covered
 * by the engine guards themselves, not here.
 */
class DownloadQueueTest {

    @Test
    fun notificationIdsArePerDownload() {
        val a = 123456789L
        val b = 987654321L
        assertNotEquals(
            Downloads.notificationIdFor(a, false),
            Downloads.notificationIdFor(b, false)
        )
        assertNotEquals(
            Downloads.notificationIdFor(a, true),
            Downloads.notificationIdFor(b, true)
        )
        assertNotEquals(
            Downloads.notificationIdFor(a, false),
            Downloads.notificationIdFor(a, true)
        )
        assertEquals(
            Downloads.notificationIdFor(a, false),
            Downloads.notificationIdFor(a, false)
        )
        assertTrue(Downloads.notificationIdFor(a, false) in 800000..899999)
        assertTrue(Downloads.notificationIdFor(a, true) in 900000..999999)
    }

    @Test
    fun queueLimitIsThree() {
        assertEquals(3, Downloads.MAX_CONCURRENT_DOWNLOADS)
        assertFalse(Downloads.shouldQueue(0))
        assertFalse(Downloads.shouldQueue(2))
        assertTrue(Downloads.shouldQueue(3))
        assertTrue(Downloads.shouldQueue(4))
    }

    @Test
    fun resumableEngines() {
        assertTrue(Downloads.isResumableEngine("hls"))
        assertTrue(Downloads.isResumableEngine("dash"))
        assertTrue(Downloads.isResumableEngine("seg"))
        assertFalse(Downloads.isResumableEngine("dm"))
        assertFalse(Downloads.isResumableEngine("merge"))
        assertFalse(Downloads.isResumableEngine(""))
    }

    @Test
    fun jobBundleRoundTrip() {
        val fields = mapOf(
            "url" to "https://example.com/a?b=1&c=2",
            "title" to "A & B = C",
            "referer" to "https://example.com/page?x=1&y=2",
            "engine" to "seg",
            "total" to "12345678"
        )
        assertEquals(fields, Downloads.decodeJobBundle(Downloads.encodeJobBundle(fields)))
        assertEquals(emptyMap<String, String>(), Downloads.decodeJobBundle(""))
    }

    @Test
    fun renameKeepsEngineExtension() {
        assertEquals(
            "show.ts",
            Downloads.applyFileNameOverride(null, "show.ts", true, false)
        )
        assertEquals(
            "My_Video.ts",
            Downloads.applyFileNameOverride("My Video", "old.ts", true, false)
        )
        assertEquals(
            "Show.ts",
            Downloads.applyFileNameOverride("Show.mp4", "old.ts", true, false)
        )
        assertEquals(
            "Clip.mp4",
            Downloads.applyFileNameOverride("Clip", "old.mp4", false, true)
        )
        assertEquals(
            "photo.jpg",
            Downloads.applyFileNameOverride("photo.jpg", "photo.png", false, false)
        )
        assertEquals(
            "report.pdf",
            Downloads.applyFileNameOverride("report", "report.pdf", false, false)
        )
        assertEquals(
            "notes.txt",
            Downloads.applyFileNameOverride("   ", "notes.txt", false, false)
        )
    }

    @Test
    fun privateNoticeTextsCarryNoTitle() {
        assertEquals(
            "Downloading • 42%",
            Downloads.progressNoticeText(true, "Secret Video", 42)
        )
        assertEquals(
            "Downloading",
            Downloads.foregroundNoticeText(true, "Secret Video")
        )
    }

    @Test
    fun nonPrivateNoticeTextsCarryTheTitle() {
        assertEquals(
            "Show • 7%",
            Downloads.progressNoticeText(false, "Show", 7)
        )
        assertEquals(
            "Show",
            Downloads.foregroundNoticeText(false, "Show")
        )
    }
}
