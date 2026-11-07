package net.notscape.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Beta.58 (5): the video pop-up's anti-spoof gate. */
class VideoPopupTest {

    private val known = listOf(
        DetectedMedia(
            "https://cdn.example.com/v.mp4", "video", "clip"
        ),
        DetectedMedia(
            "https://www.youtube.com/embed/abc", "page", "embed"
        ),
        DetectedMedia(
            "https://cdn.example.com/a.mp3", "audio", "song"
        )
    )

    @Test
    fun matchingVideoEntryIsAllowed() {
        val got = VideoPopup.allowCheck(
            "https://cdn.example.com/v.mp4", known
        )
        assertEquals("https://cdn.example.com/v.mp4", got?.url)
    }

    @Test
    fun unknownUrlIsIgnored() {
        assertNull(
            VideoPopup.allowCheck(
                "https://evil.example.com/x.mp4", known
            )
        )
    }

    @Test
    fun embedPageKindIsIgnored() {
        assertNull(
            VideoPopup.allowCheck(
                "https://www.youtube.com/embed/abc", known
            )
        )
    }

    @Test
    fun audioKindIsIgnored() {
        assertNull(
            VideoPopup.allowCheck(
                "https://cdn.example.com/a.mp3", known
            )
        )
    }

    @Test
    fun blobUrlIsIgnored() {
        assertNull(
            VideoPopup.allowCheck("blob:https://x/123", known)
        )
    }
}
