package net.notscape.browser

import net.notscape.browser.reader.bookProgressFraction
import net.notscape.browser.reader.bookProgressText
import org.junit.Assert.assertEquals
import org.junit.Test

class BookProgressTest {

    @Test
    fun fractionIsZeroAtStart() {
        assertEquals(0f, bookProgressFraction(0, 0f, 10), 0.001f)
    }

    @Test
    fun fractionCombinesChapterAndScroll() {
        // Chapter 5 of 10, scrolled halfway through it = 0.55.
        assertEquals(0.55f, bookProgressFraction(5, 0.5f, 10), 0.001f)
    }

    @Test
    fun fractionIsOneAtTheEnd() {
        assertEquals(1f, bookProgressFraction(9, 1f, 10), 0.001f)
    }

    @Test
    fun fractionIsZeroWithNoChapters() {
        assertEquals(0f, bookProgressFraction(0, 0.5f, 0), 0.001f)
    }

    @Test
    fun textShowsPercentAndTimeLeft() {
        // 9,500 chars total, at the start: 10 minutes left at 950 chars/min.
        assertEquals(
            "0% · ≈ 10 min left",
            bookProgressText(0, 0f, 10, 9_500)
        )
    }

    @Test
    fun textShowsTimeLeftAsHoursAndMinutes() {
        // 95,000 chars = 100 minutes total; 20% through leaves
        // 76,000 chars = 80 minutes = 1 h 20 min.
        assertEquals(
            "20% · ≈ 1 h 20 min left",
            bookProgressText(2, 0f, 10, 95_000)
        )
    }

    @Test
    fun textIsEmptyWhenTotalsUnknown() {
        assertEquals("", bookProgressText(0, 0f, 0, 0))
        assertEquals("", bookProgressText(0, 0f, 10, 0))
    }
}
