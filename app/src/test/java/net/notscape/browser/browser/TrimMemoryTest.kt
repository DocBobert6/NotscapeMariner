package net.notscape.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class TrimMemoryTest {
    @Test fun activeTabIsNeverDiscarded() {
        assertEquals(
            listOf(1, 3),
            backgroundTabsToDiscard(listOf(1, 2, 3), 2)
        )
    }

    @Test fun loneActiveTabDiscardsNothing() {
        assertEquals(
            emptyList<Int>(),
            backgroundTabsToDiscard(listOf(7), 7)
        )
    }

    @Test fun noTabsDiscardsNothing() {
        assertEquals(
            emptyList<Int>(),
            backgroundTabsToDiscard(emptyList(), 1)
        )
    }
}
