package net.notscape.browser

import net.notscape.browser.media.DashManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashManifestTest {

    private val mpd = """
        <?xml version="1.0" encoding="UTF-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static">
          <Period>
            <AdaptationSet mimeType="video/mp4" contentType="video">
              <Representation id="v1" bandwidth="4500000" width="1920" height="1080" codecs="avc1.640028"/>
              <Representation id="v2" bandwidth="1800000" width="1280" height="720" codecs="avc1.4d401f"/>
              <Representation id="v3" bandwidth="700000" width="854" height="480" codecs="avc1.42c01e"/>
            </AdaptationSet>
            <AdaptationSet mimeType="audio/mp4" contentType="audio">
              <Representation id="a1" bandwidth="128000" codecs="mp4a.40.2"/>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    @Test
    fun parsesVideoRepresentationsOnly() {
        val v = DashManifest.parse(mpd)
        assertEquals(3, v.size)
        assertEquals(1080, v[0].height)
        assertEquals(4500000L, v[0].bandwidth)
        assertEquals(480, v[2].height)
    }

    @Test
    fun representationMimeOverridesSet() {
        val text = """
            <MPD><Period><AdaptationSet>
              <Representation mimeType="video/webm" bandwidth="900000" width="640" height="360"/>
              <Representation mimeType="audio/webm" bandwidth="96000"/>
            </AdaptationSet></Period></MPD>
        """.trimIndent()
        val v = DashManifest.parse(text)
        assertEquals(1, v.size)
        assertEquals(360, v[0].height)
    }

    @Test
    fun notAManifestParsesEmpty() {
        assertTrue(DashManifest.parse("<html>nope</html>").isEmpty())
        assertTrue(DashManifest.parse("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1").isEmpty())
    }
}
