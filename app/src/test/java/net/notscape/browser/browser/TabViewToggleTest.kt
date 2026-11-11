package net.notscape.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tabs-overlay view-toggle label: one word, naming the view a tap
 * switches TO — never the two-word "List view" / "Grid view" that
 * wrapped to two lines in the actions row. */
class TabViewToggleTest {

    @Test
    fun gridModeOffersList() {
        assertEquals("List", tabViewToggleLabel(false))
    }

    @Test
    fun listModeOffersGrid() {
        assertEquals("Grid", tabViewToggleLabel(true))
    }

    @Test
    fun labelsAreSingleWords() {
        assertEquals(1, tabViewToggleLabel(false).split(" ").size)
        assertEquals(1, tabViewToggleLabel(true).split(" ").size)
    }
}
