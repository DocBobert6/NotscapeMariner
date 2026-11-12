package net.notscape.browser

import net.notscape.browser.browser.FilterEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterListConfigTest {
    @Test fun parsesCommaSeparatedIndices() {
        assertEquals(
            setOf(1, 3),
            FilterEngine.parseDisabledFilterLists("1,3")
        )
    }

    @Test fun emptyStringParsesToEmpty() {
        assertEquals(
            emptySet<Int>(),
            FilterEngine.parseDisabledFilterLists("")
        )
    }

    @Test fun unparseableEntriesAreDropped() {
        assertEquals(
            setOf(2, 5),
            FilterEngine.parseDisabledFilterLists("a,2,,5")
        )
    }
}
