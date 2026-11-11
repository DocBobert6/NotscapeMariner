package net.notscape.browser

import net.notscape.browser.media.Downloads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Beta.58 (2): tap-time cookie snapshot — host-scoped replay. */
class CookieSnapshotTest {

    private val snap = Downloads.CookieSnapshot(
        host = "cdn.example.com",
        cookie = "session=abc123"
    )

    @Test
    fun snapshotWinsForItsOwnHost() {
        assertEquals(
            "session=abc123",
            Downloads.cookieForRequest(
                "https://cdn.example.com/seg-001.ts", snap, "session=live"
            )
        )
    }

    @Test
    fun liveCookieUsedForOtherHosts() {
        assertEquals(
            "session=live",
            Downloads.cookieForRequest(
                "https://other.example.net/seg-001.ts", snap, "session=live"
            )
        )
    }

    @Test
    fun blankSnapshotFallsBackToLive() {
        val blank = Downloads.CookieSnapshot("cdn.example.com", "")
        assertEquals(
            "session=live",
            Downloads.cookieForRequest(
                "https://cdn.example.com/seg-001.ts", blank, "session=live"
            )
        )
    }

    @Test
    fun noSnapshotAndNoLiveIsNull() {
        assertNull(
            Downloads.cookieForRequest(
                "https://cdn.example.com/seg-001.ts", null, null
            )
        )
    }

    @Test
    fun snapshotWithNoLiveStillApplies() {
        assertEquals(
            "session=abc123",
            Downloads.cookieForRequest(
                "https://cdn.example.com/seg-001.ts", snap, null
            )
        )
    }

    @Test
    fun bundleRoundTripsCookieKeys() {
        val fields = mapOf(
            "url" to "https://example.com/master.m3u8",
            "cookie" to "session=abc123; path=/",
            "cookieHost" to "example.com",
            "referer" to "https://example.com/watch"
        )
        val decoded = Downloads.decodeJobBundle(Downloads.encodeJobBundle(fields))
        assertEquals(fields, decoded)
    }
}
