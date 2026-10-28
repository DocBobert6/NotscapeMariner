package net.notscape.browser.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F-M1: a progress tick must never move a record out
 * of a paused, cancelled, or terminal status — that
 * resurrection hid Resume and froze the bar. The
 * Store method itself needs a Store (not JVM-safe),
 * so these tests pin the pure transition it applies.
 */
class ProgressTargetStatusTest {

    @Test
    fun pausedAndCancelledArePreserved() {
        assertEquals("paused", progressTargetStatus("paused"))
        assertEquals("cancelled", progressTargetStatus("cancelled"))
    }

    @Test
    fun terminalStatusesArePreserved() {
        assertEquals("complete", progressTargetStatus("complete"))
        assertEquals("failed", progressTargetStatus("failed"))
    }

    @Test
    fun liveStatusesBecomeDownloading() {
        assertEquals("downloading", progressTargetStatus("downloading"))
        assertEquals("downloading", progressTargetStatus("queued"))
        assertEquals("downloading", progressTargetStatus(null))
    }
}
