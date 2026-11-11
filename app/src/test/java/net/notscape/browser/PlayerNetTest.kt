package net.notscape.browser

import net.notscape.browser.media.PlayerNet
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Beta.67 (1a): the playlist-URL recogniser that decides which
 * player requests bypass the cache. Playlists (.m3u8 / .mpd)
 * bypass; segments and plain media never do.
 */
class PlayerNetTest {

    @Test
    fun hlsPlaylistIsRecognised() {
        assertTrue(PlayerNet.isPlaylistUrl("https://cdn.example.com/live/master.m3u8"))
        assertTrue(PlayerNet.isPlaylistUrl("https://cdn.example.com/live/master.m3u8?token=abc"))
        assertTrue(PlayerNet.isPlaylistUrl("https://cdn.example.com/live/MASTER.M3U8"))
    }

    @Test
    fun dashManifestIsRecognised() {
        assertTrue(PlayerNet.isPlaylistUrl("https://cdn.example.com/live/manifest.mpd"))
        assertTrue(PlayerNet.isPlaylistUrl("https://cdn.example.com/live/manifest.mpd?x=1#frag"))
        assertTrue(PlayerNet.isPlaylistUrl("https://cdn.example.com/live/MANIFEST.MPD"))
    }

    @Test
    fun segmentsAndPlainMediaAreNotPlaylists() {
        assertFalse(PlayerNet.isPlaylistUrl("https://cdn.example.com/live/seg-001.ts"))
        assertFalse(PlayerNet.isPlaylistUrl("https://cdn.example.com/live/seg-001.m4s"))
        assertFalse(PlayerNet.isPlaylistUrl("https://cdn.example.com/live/init.mp4"))
        assertFalse(PlayerNet.isPlaylistUrl("https://cdn.example.com/video.mp4"))
        assertFalse(PlayerNet.isPlaylistUrl("https://cdn.example.com/live/key.bin"))
    }

    @Test
    fun lookalikeExtensionsAreNotPlaylists() {
        // The extension must be the path's own ending, not a
        // substring: a segment named *.m3u8.ts is a segment.
        assertFalse(PlayerNet.isPlaylistUrl("https://cdn.example.com/live/master.m3u8.ts"))
        assertFalse(PlayerNet.isPlaylistUrl("https://cdn.example.com/mpd/video.mp4"))
    }
}
