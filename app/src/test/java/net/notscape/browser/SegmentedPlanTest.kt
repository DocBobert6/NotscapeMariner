package net.notscape.browser

import net.notscape.browser.media.SegmentedPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentedPlanTest {

    @Test
    fun planSplitsEvenlyWithRemainderOnLastChunk() {
        val chunks = SegmentedPlan.plan(100L * 1024 * 1024)
        assertEquals(4, chunks.size)
        assertEquals(0L, chunks[0].start)
        assertEquals(25L * 1024 * 1024 - 1, chunks[0].end)
        assertEquals(75L * 1024 * 1024, chunks[3].start)
        assertEquals(100L * 1024 * 1024 - 1, chunks[3].end)
        // Ranges are contiguous with no gaps or overlaps.
        for (i in 1 until chunks.size) {
            assertEquals(chunks[i - 1].end + 1, chunks[i].start)
        }
        assertEquals(100L * 1024 * 1024, chunks.sumOf { it.length })
    }

    @Test
    fun planAtFloorUsesFourChunks() {
        val chunks = SegmentedPlan.plan(8L * 1024 * 1024)
        assertEquals(4, chunks.size)
        assertEquals(2L * 1024 * 1024, chunks[0].length)
        assertEquals(8L * 1024 * 1024 - 1, chunks[3].end)
    }

    @Test
    fun planBelowFloorIsEmpty() {
        assertTrue(SegmentedPlan.plan(8L * 1024 * 1024 - 1).isEmpty())
        assertTrue(SegmentedPlan.plan(1024).isEmpty())
    }

    @Test
    fun planReducesConnectionsForSmallChunks() {
        // 10 MB across 16 requested connections: the 1 MB minimum
        // chunk caps it at 10 chunks, each exactly 1 MB.
        val chunks = SegmentedPlan.plan(10L * 1024 * 1024, 16)
        assertEquals(10, chunks.size)
        assertTrue(chunks.all { it.length == 1L * 1024 * 1024 })
    }

    @Test
    fun planSingleChunkWhenForcedToOneConnection() {
        val chunks = SegmentedPlan.plan(20L * 1024 * 1024, 1)
        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].start)
        assertEquals(20L * 1024 * 1024 - 1, chunks[0].end)
    }

    @Test
    fun parseContentRangeVariants() {
        assertEquals(12345L, SegmentedPlan.parseContentRange("bytes 0-0/12345"))
        assertEquals(12345L, SegmentedPlan.parseContentRange("bytes */12345"))
        assertEquals(null, SegmentedPlan.parseContentRange("bytes 0-0/*"))
        assertEquals(null, SegmentedPlan.parseContentRange("bytes 0-0/"))
        assertEquals(null, SegmentedPlan.parseContentRange("garbage"))
        assertEquals(null, SegmentedPlan.parseContentRange(null))
        assertEquals(null, SegmentedPlan.parseContentRange("bytes 0-0/0"))
    }

    @Test
    fun decideProbeEligibleOn206WithTotal() {
        val d = SegmentedPlan.decideProbe(206, 20L * 1024 * 1024, null, null)
        assertTrue(d is SegmentedPlan.ProbeDecision.Eligible)
        assertEquals(20L * 1024 * 1024, (d as SegmentedPlan.ProbeDecision.Eligible).total)
    }

    @Test
    fun decideProbeFallbackMatrix() {
        // 200 on a Range request: the server ignores Range.
        assertTrue(
            SegmentedPlan.decideProbe(200, null, null, null)
                is SegmentedPlan.ProbeDecision.Fallback
        )
        // 206 but below the floor.
        assertTrue(
            SegmentedPlan.decideProbe(206, 4L * 1024 * 1024, null, null)
                is SegmentedPlan.ProbeDecision.Fallback
        )
        // Probe refused.
        assertTrue(
            SegmentedPlan.decideProbe(403, null, null, null)
                is SegmentedPlan.ProbeDecision.Fallback
        )
        assertTrue(
            SegmentedPlan.decideProbe(429, null, null, null)
                is SegmentedPlan.ProbeDecision.Fallback
        )
        // No length anywhere.
        assertTrue(
            SegmentedPlan.decideProbe(null, null, null, null)
                is SegmentedPlan.ProbeDecision.Fallback
        )
        // HEAD length without Accept-Ranges: Range support unproven.
        assertTrue(
            SegmentedPlan.decideProbe(null, null, 20L * 1024 * 1024, null)
                is SegmentedPlan.ProbeDecision.Fallback
        )
        // HEAD length with Accept-Ranges: bytes proves support.
        val viaHead = SegmentedPlan.decideProbe(null, null, 20L * 1024 * 1024, "bytes")
        assertTrue(viaHead is SegmentedPlan.ProbeDecision.Eligible)
        assertEquals(
            20L * 1024 * 1024,
            (viaHead as SegmentedPlan.ProbeDecision.Eligible).total
        )
        // HEAD length below the floor still falls back.
        assertTrue(
            SegmentedPlan.decideProbe(null, null, 4L * 1024 * 1024, "bytes")
                is SegmentedPlan.ProbeDecision.Fallback
        )
    }

    @Test
    fun journalRoundTrip() {
        val chunks = SegmentedPlan.plan(40L * 1024 * 1024)
        val done = longArrayOf(10L * 1024 * 1024, 0L, 5L * 1024 * 1024, 10L * 1024 * 1024)
        val text = SegmentedPlan.journalText(chunks, done)
        val parsed = SegmentedPlan.parseJournal(text)
        assertEquals(10L * 1024 * 1024, parsed[0])
        assertEquals(0L, parsed[1])
        assertEquals(5L * 1024 * 1024, parsed[2])
        assertEquals(10L * 1024 * 1024, parsed[3])
    }

    @Test
    fun journalSkipsMalformedLines() {
        val text = "0:0:99:50\nnot a journal line\n1:100:199:notanumber\n2:200:299:75\n"
        val parsed = SegmentedPlan.parseJournal(text)
        assertEquals(50L, parsed[0])
        assertEquals(75L, parsed[2])
        assertEquals(2, parsed.size)
    }

    @Test
    fun journalClampsDoneToChunkLength() {
        val chunks = SegmentedPlan.plan(40L * 1024 * 1024)
        val done = longArrayOf(999L * 1024 * 1024, 0L, 0L, 0L)
        val parsed = SegmentedPlan.parseJournal(SegmentedPlan.journalText(chunks, done))
        assertEquals(chunks[0].length, parsed[0])
    }
}
