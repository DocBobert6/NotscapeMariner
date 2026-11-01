package net.notscape.browser.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Audit4Parity2Test {

    @Test
    fun castMimeGuesses() {
        assertEquals("video/mp4", CastHelper.mimeFor("https://x.test/v.mp4?token=1"))
        assertEquals("video/webm", CastHelper.mimeFor("https://x.test/v.webm"))
        assertEquals("application/x-mpegURL", CastHelper.mimeFor("https://x.test/list.m3u8"))
        assertEquals("application/dash+xml", CastHelper.mimeFor("https://x.test/manifest.mpd"))
        assertEquals("audio/mpeg", CastHelper.mimeFor("https://x.test/a.mp3"))
        assertEquals("video/mp4", CastHelper.mimeFor("https://x.test/unknown"))
    }

    @Test
    fun castRemoteGate() {
        assertTrue(CastHelper.isRemote("https://x.test/v.mp4"))
        assertTrue(CastHelper.isRemote("http://x.test/v.mp4"))
        assertFalse(CastHelper.isRemote("file:///sdcard/v.mp4"))
        assertFalse(CastHelper.isRemote("content://media/v.mp4"))
    }
}
