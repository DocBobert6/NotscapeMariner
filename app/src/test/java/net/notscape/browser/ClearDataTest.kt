package net.notscape.browser

import net.notscape.browser.data.HistoryEntry
import net.notscape.browser.data.clearRangeCutoff
import net.notscape.browser.data.countHistoryInRange
import org.junit.Assert.assertEquals
import org.junit.Test

class ClearDataTest {

    private val now = 1_700_000_000_000L
    private val history = listOf(
        HistoryEntry(1, "a", "https://a.example", now - 30 * 60 * 1000),
        HistoryEntry(2, "b", "https://b.example", now - 12 * 3_600_000),
        HistoryEntry(3, "c", "https://c.example", now - 3 * 86_400_000),
        HistoryEntry(4, "d", "https://d.example", now - 30 * 86_400_000L)
    )

    @Test
    fun cutoffsAreExact() {
        assertEquals(now - 3_600_000L, clearRangeCutoff("hour", now))
        assertEquals(now - 86_400_000L, clearRangeCutoff("day", now))
        assertEquals(now - 7 * 86_400_000L, clearRangeCutoff("week", now))
        assertEquals(0L, clearRangeCutoff("all", now))
        assertEquals(0L, clearRangeCutoff("nonsense", now))
    }

    @Test
    fun countsRespectTheRange() {
        assertEquals(1, countHistoryInRange(history, clearRangeCutoff("hour", now)))
        assertEquals(2, countHistoryInRange(history, clearRangeCutoff("day", now)))
        assertEquals(3, countHistoryInRange(history, clearRangeCutoff("week", now)))
        assertEquals(4, countHistoryInRange(history, clearRangeCutoff("all", now)))
    }

    @Test
    fun emptyHistoryCountsZero() {
        assertEquals(0, countHistoryInRange(emptyList(), clearRangeCutoff("day", now)))
        assertEquals(0, countHistoryInRange(emptyList(), 0L))
    }
}
