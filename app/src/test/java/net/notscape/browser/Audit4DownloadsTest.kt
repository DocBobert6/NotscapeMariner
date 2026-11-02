package net.notscape.browser

import net.notscape.browser.media.DashManifest
import net.notscape.browser.media.Downloads
import net.notscape.browser.media.HlsPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Fourth-audit downloads fixes: pure-function pins. */
class Audit4DownloadsTest {

    @Test
    fun livePlaylistHasNoEndlist() {
        val live = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            seg1.ts
        """.trimIndent()
        assertTrue(HlsPlaylist.parseMedia(live, "https://cdn.example.com/l.m3u8").isLive)
        val vod = """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXTINF:6.0,
            seg1.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        assertFalse(HlsPlaylist.parseMedia(vod, "https://cdn.example.com/v.m3u8").isLive)
    }

    @Test
    fun multiMapDistinctInitFlagged() {
        val multi = """
            #EXTM3U
            #EXT-X-MAP:URI="init1.mp4"
            #EXTINF:6.0,
            seg1.m4s
            #EXT-X-MAP:URI="init2.mp4"
            #EXTINF:6.0,
            seg2.m4s
            #EXT-X-ENDLIST
        """.trimIndent()
        assertTrue(HlsPlaylist.parseMedia(multi, "https://cdn.example.com/m.m3u8").multiInit)
        val same = """
            #EXTM3U
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.0,
            seg1.m4s
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.0,
            seg2.m4s
            #EXT-X-ENDLIST
        """.trimIndent()
        assertFalse(HlsPlaylist.parseMedia(same, "https://cdn.example.com/m.m3u8").multiInit)
    }

    @Test
    fun segmentListCapped() {
        val sb = StringBuilder("#EXTM3U\n")
        repeat(10_001) { i ->
            sb.append("#EXTINF:6.0,\nseg$i.ts\n")
        }
        sb.append("#EXT-X-ENDLIST\n")
        val plan = HlsPlaylist.parseMedia(sb.toString(), "https://cdn.example.com/m.m3u8")
        assertTrue(plan.tooLarge)
        assertEquals(10_000, plan.segments.size)
    }

    @Test
    fun extensionOnlyNameFallsBackToResolved() {
        assertEquals(
            "video.mp4",
            Downloads.applyFileNameOverride(".mp4", "video.mp4", false, false)
        )
        assertEquals(
            "my_video.mp4",
            Downloads.applyFileNameOverride("my video", "video.mp4", false, false)
        )
    }

    @Test
    fun renameOrCopyMovesFile() {
        val dir = createTempDir("audit4")
        try {
            val src = File(dir, "a.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val dst = File(dir, "b.bin")
            assertTrue(Downloads.renameOrCopy(src, dst))
            assertTrue(dst.exists())
            assertFalse(src.exists())
            assertEquals(3, dst.length().toInt())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun dashDrmDetected() {
        val drm = """<MPD type="static"><Period>
            <AdaptationSet mimeType="video/mp4">
              <ContentProtection schemeIdUri="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ca"/>
              <Representation id="v1" bandwidth="800000"/>
            </AdaptationSet>
          </Period></MPD>"""
        assertTrue(DashManifest.hasDrm(drm))
        assertFalse(DashManifest.hasDrm("<MPD type=\"static\"></MPD>"))
    }

    private fun mpd(body: String) = body.replace('§', '$')

    @Test
    fun dashOversizedTimelineRefused() {
        val text = mpd(
            """<MPD type="static" mediaPresentationDuration="PT600S">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentTemplate timescale="1" duration="1"
                      initialization="init.m4s" media="seg-§Number§.m4s">
                    <SegmentTimeline><S d="1" r="20000"/></SegmentTimeline>
                  </SegmentTemplate>
                  <Representation id="v1" bandwidth="800000"/>
                </AdaptationSet>
              </Period>
            </MPD>"""
        )
        assertNull(
            DashManifest.parseDownloadPlan(text, "https://cdn.example.com/manifest.mpd")
        )
    }

    @Test
    fun dashBandwidthCapPicksLowerRepresentation() {
        val text = mpd(
            """<MPD type="static" mediaPresentationDuration="PT8S">
              <BaseURL>https://cdn.example.com/v/</BaseURL>
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentTemplate timescale="1" duration="4" startNumber="1"
                      initialization="init-§RepresentationID§.m4s"
                      media="seg-§RepresentationID§-§Number§.m4s"/>
                  <Representation id="hi" bandwidth="5000000" width="1920" height="1080"/>
                  <Representation id="lo" bandwidth="900000" width="640" height="360"/>
                </AdaptationSet>
              </Period>
            </MPD>"""
        )
        val plan = DashManifest.parseDownloadPlan(
            text, "https://cdn.example.com/v/manifest.mpd", maxBandwidth = 1_000_000
        )
        assertNotNull(plan)
        plan!!
        assertEquals("https://cdn.example.com/v/init-lo.m4s", plan.video.initUrl)
        assertEquals("https://cdn.example.com/v/seg-lo-1.m4s", plan.video.segmentUrls[0])
    }
}
