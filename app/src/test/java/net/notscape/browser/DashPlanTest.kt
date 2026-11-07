package net.notscape.browser

import net.notscape.browser.media.DashManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.0.9996: the DASH download-plan parser. Fixtures use the
 * section sign in place of the dollar sign so the templates
 * stay literal inside Kotlin strings. */
class DashPlanTest {

    private fun mpd(body: String) = body.replace('§', '$')

    @Test fun templatePlanResolvesNumbersAndBase() {
        val text = mpd(
            """<MPD type="static" mediaPresentationDuration="PT20S">
              <BaseURL>https://cdn.example.com/v/</BaseURL>
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentTemplate timescale="1" duration="4" startNumber="1"
                      initialization="init-§RepresentationID§.m4s"
                      media="seg-§Number%03d§.m4s"/>
                  <Representation id="v1" bandwidth="800000" width="640" height="360"/>
                  <Representation id="v2" bandwidth="400000" width="320" height="180"/>
                </AdaptationSet>
                <AdaptationSet mimeType="audio/mp4">
                  <SegmentTemplate timescale="1" duration="4"
                      initialization="a-init.m4s" media="a-§Number§.m4s"/>
                  <Representation id="a1" bandwidth="96000"/>
                </AdaptationSet>
              </Period>
            </MPD>"""
        )
        val plan = DashManifest.parseDownloadPlan(
            text, "https://cdn.example.com/v/manifest.mpd"
        )
        assertNotNull(plan)
        plan!!
        // Best video rendition wins; 20 s / 4 s = 5 segments.
        assertEquals("https://cdn.example.com/v/init-v1.m4s", plan.video.initUrl)
        assertEquals(5, plan.video.segmentUrls.size)
        assertEquals("https://cdn.example.com/v/seg-001.m4s", plan.video.segmentUrls[0])
        assertEquals("https://cdn.example.com/v/seg-005.m4s", plan.video.segmentUrls[4])
        assertNotNull(plan.audio)
        assertEquals("https://cdn.example.com/v/a-1.m4s", plan.audio!!.segmentUrls[0])
    }

    @Test fun segmentListPlan() {
        val text = mpd(
            """<MPD type="static">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentList>
                    <Initialization sourceURL="init.mp4"/>
                    <SegmentURL media="s1.m4s"/>
                    <SegmentURL media="s2.m4s"/>
                  </SegmentList>
                  <Representation id="v1" bandwidth="500000"/>
                </AdaptationSet>
              </Period>
            </MPD>"""
        )
        val plan = DashManifest.parseDownloadPlan(
            text, "https://x.example.com/d/m.mpd"
        )
        assertNotNull(plan)
        plan!!
        assertEquals("https://x.example.com/d/init.mp4", plan.video.initUrl)
        assertEquals(
            listOf(
                "https://x.example.com/d/s1.m4s",
                "https://x.example.com/d/s2.m4s"
            ),
            plan.video.segmentUrls
        )
        assertNull(plan.audio)
    }

    @Test fun timelineDrivesTimeTemplates() {
        val text = mpd(
            """<MPD type="static">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentTemplate timescale="1000"
                      initialization="i-§RepresentationID§.m4s"
                      media="m-§Time§.m4s">
                    <SegmentTimeline>
                      <S t="0" d="2000" r="2"/>
                    </SegmentTimeline>
                  </SegmentTemplate>
                  <Representation id="v9" bandwidth="1000"/>
                </AdaptationSet>
              </Period>
            </MPD>"""
        )
        val plan = DashManifest.parseDownloadPlan(
            text, "https://h.example.com/m.mpd"
        )
        assertNotNull(plan)
        assertEquals(
            listOf(
                "https://h.example.com/m-0.m4s",
                "https://h.example.com/m-2000.m4s",
                "https://h.example.com/m-4000.m4s"
            ),
            plan!!.video.segmentUrls
        )
    }

    @Test fun dynamicAndMultiPeriodAreUnsupported() {
        val live = mpd(
            """<MPD type="dynamic"><Period><AdaptationSet mimeType="video/mp4">
               <Representation id="v" bandwidth="1"/></AdaptationSet></Period></MPD>"""
        )
        assertNull(
            DashManifest.parseDownloadPlan(live, "https://h/m.mpd")
        )
        val twoPeriods = mpd(
            """<MPD type="static"><Period/><Period/></MPD>"""
        )
        assertNull(
            DashManifest.parseDownloadPlan(twoPeriods, "https://h/m.mpd")
        )
        assertNull(
            DashManifest.parseDownloadPlan("not an mpd", "https://h/m.mpd")
        )
        assertTrue(true)
    }
}
