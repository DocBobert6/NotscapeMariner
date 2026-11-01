package net.notscape.browser

import net.notscape.browser.browser.isEmbedPageUrl
import net.notscape.browser.browser.parseDetectedMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDetectTest {

    @Test
    fun embedPagesAreRecognised() {
        assertTrue(isEmbedPageUrl("https://www.youtube.com/embed/abc123"))
        assertTrue(isEmbedPageUrl("https://www.youtube-nocookie.com/embed/abc123"))
        assertTrue(isEmbedPageUrl("https://www.youtube.com/watch?v=abc123"))
        assertTrue(isEmbedPageUrl("https://player.vimeo.com/video/12345"))
        assertFalse(isEmbedPageUrl("https://cdn.example.com/movie.mp4"))
        assertFalse(isEmbedPageUrl("https://example.com/stream.m3u8"))
    }

    @Test
    fun ogVideoEmbedIsReclassifiedAsPage() {
        // evaluateJavascript double-encodes: a JSON string containing JSON.
        val inner = """[{"url":"https://www.youtube.com/embed/abc123","kind":"video","label":"featured"}]"""
        val raw = org.json.JSONObject.quote(inner)
        val out = parseDetectedMedia(raw)
        assertEquals(1, out.size)
        assertEquals("page", out[0].kind)
    }

    @Test
    fun directStreamStaysPlayable() {
        val inner = """[{"url":"https://example.com/v.mp4","kind":"video","label":""}]"""
        val raw = org.json.JSONObject.quote(inner)
        val out = parseDetectedMedia(raw)
        assertEquals(1, out.size)
        assertEquals("video", out[0].kind)
    }
}
