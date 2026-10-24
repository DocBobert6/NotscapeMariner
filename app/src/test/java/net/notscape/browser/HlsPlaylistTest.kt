package net.notscape.browser

import net.notscape.browser.media.HlsPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** HLS playlist parsing (Phase D security/correctness tests). */
class HlsPlaylistTest {

    private val master = """
        #EXTM3U
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="English",URI="audio/en.m3u8"
        #EXT-X-STREAM-INF:BANDWIDTH=800000,AUDIO="aud"
        low/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2400000,AUDIO="aud"
        hi/index.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=1400000
        mid/index.m3u8
    """.trimIndent()

    @Test
    fun masterParsesVariantResolutions() {
        // 0.0.967: RESOLUTION feeds the player's quality labels;
        // a variant without one reports 0x0 (label falls back to
        // its bitrate).
        val m = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
            hd/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=900000
            sd/index.m3u8
        """.trimIndent()
        val plan = HlsPlaylist.parseMaster(m, "https://cdn.example.com/m.m3u8")
        val hd = plan.variants.first { it.url.contains("hd") }
        assertEquals(1920, hd.width)
        assertEquals(1080, hd.height)
        val sd = plan.variants.first { it.url.contains("sd") }
        assertEquals(0, sd.height)
    }

    @Test
    fun masterPicksHighestBandwidthAndDetectsSeparateAudio() {
        val plan = HlsPlaylist.parseMaster(master, "https://cdn.example.com/master.m3u8")
        val best = plan.bestVariant()!!
        assertEquals("https://cdn.example.com/hi/index.m3u8", best.url)
        assertTrue(plan.variantHasSeparateAudio(best))
        val mid = plan.variants.first { it.url.contains("mid") }
        assertFalse(plan.variantHasSeparateAudio(mid))
    }

    @Test
    fun mediaParsesSegmentsKeysAndSequence() {
        val media = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:42
            #EXT-X-KEY:METHOD=AES-128,URI="keys/k.bin",IV=0x0000000000000000000000000000002a
            #EXTINF:6.0,
            seg1.ts
            #EXTINF:6.0,
            seg2.ts
        """.trimIndent()
        val plan = HlsPlaylist.parseMedia(media, "https://cdn.example.com/hi/index.m3u8")
        assertEquals(2, plan.segments.size)
        assertEquals("https://cdn.example.com/hi/seg1.ts", plan.segments[0].url)
        assertEquals(42L, plan.segments[0].sequence)
        assertEquals(43L, plan.segments[1].sequence)
        assertEquals("https://cdn.example.com/hi/keys/k.bin", plan.segments[0].key?.uri)
        assertFalse(plan.isCmaf)
        assertNull(plan.unsupportedKeyMethod)
    }

    @Test
    fun cmafInitAndByteRangesParse() {
        val media = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4",BYTERANGE="720@0"
            #EXT-X-BYTERANGE:1000@720
            main.mp4
            #EXT-X-BYTERANGE:1000
            main.mp4
        """.trimIndent()
        val plan = HlsPlaylist.parseMedia(media, "https://cdn.example.com/v/index.m3u8")
        assertTrue(plan.isCmaf)
        assertEquals("https://cdn.example.com/v/init.mp4", plan.initUrl)
        assertEquals(720L, plan.initRange?.length)
        assertEquals(1000L, plan.segments[0].range?.length)
        assertEquals(720L, plan.segments[0].range?.offset)
        // Second range continues where the first ended.
        assertEquals(1720L, plan.segments[1].range?.offset)
    }

    @Test
    fun unsupportedKeyMethodIsFlagged() {
        val media = """
            #EXTM3U
            #EXT-X-KEY:METHOD=SAMPLE-AES,URI="k"
            #EXTINF:6.0,
            seg.ts
        """.trimIndent()
        val plan = HlsPlaylist.parseMedia(media, "https://x.example/i.m3u8")
        assertEquals("SAMPLE-AES", plan.unsupportedKeyMethod)
    }

    @Test
    fun sequenceIvIsBigEndian() {
        val iv = HlsPlaylist.sequenceIv(258)
        assertEquals(16, iv.size)
        assertEquals(1.toByte(), iv[14])
        assertEquals(2.toByte(), iv[15])
    }
}
