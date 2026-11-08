package net.notscape.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Browser menu customization: the hidden-row set parsing
 * behind the "Customize menu" editor. */
class BrowserMenuRowsTest {

    @Test
    fun unsetHidesOnlyTheMovedRows() {
        assertEquals(
            setOf(
                BrowserMenuRows.SITE_BLOCKING,
                BrowserMenuRows.GLOBAL_ADS
            ),
            BrowserMenuRows.parseHidden("DEFAULT")
        )
    }

    @Test
    fun emptyStringHidesNothing() {
        assertEquals(emptySet<String>(), BrowserMenuRows.parseHidden(""))
    }

    @Test
    fun commaListParsesAndTrims() {
        assertEquals(
            setOf("newTab", "games", "settings"),
            BrowserMenuRows.parseHidden("newTab, games,, settings ")
        )
    }

    @Test
    fun editorListsEveryRowOnce() {
        val ids = BrowserMenuRows.ALL.map { it.first }
        assertEquals(33, ids.size)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun copyPageUrlRowIsListedAndVisibleByDefault() {
        val ids = BrowserMenuRows.ALL.map { it.first }
        assertTrue("copyPageUrl" in ids)
        assertTrue("copyPageUrl" !in BrowserMenuRows.parseHidden("DEFAULT"))
    }

    @Test
    fun movedRowsAreInTheEditorButHiddenByDefault() {
        assertTrue(BrowserMenuRows.SITE_BLOCKING in BrowserMenuRows.ALL.map { it.first })
        assertTrue(BrowserMenuRows.GLOBAL_ADS in BrowserMenuRows.ALL.map { it.first })
        assertTrue(BrowserMenuRows.SITE_BLOCKING in BrowserMenuRows.DEFAULT_HIDDEN)
        assertTrue(BrowserMenuRows.GLOBAL_ADS in BrowserMenuRows.DEFAULT_HIDDEN)
    }

    @Test
    fun stayingRowsAreVisibleByDefault() {
        val hidden = BrowserMenuRows.parseHidden("DEFAULT")
        assertTrue("newTab" !in hidden)
        assertTrue("blockElement" !in hidden)
        assertTrue("settings" !in hidden)
    }

    @Test
    fun unsetOrderIsTheDefaultOrder() {
        assertEquals(
            BrowserMenuRows.DEFAULT_ORDER,
            BrowserMenuRows.parseOrder("DEFAULT")
        )
    }

    @Test
    fun storedOrderDropsUnknownIdsAndAppendsMissingRows() {
        val parsed = BrowserMenuRows.parseOrder(
            "settings, newTab, notARealRow"
        )
        assertTrue("notARealRow" !in parsed)
        assertEquals("settings", parsed[0])
        assertEquals("newTab", parsed[1])
        assertEquals(33, parsed.size)
        assertEquals(parsed.size, parsed.toSet().size)
        // Every row survives: missing ones append in default order.
        assertEquals(
            BrowserMenuRows.ALL.map { it.first }.toSet(),
            parsed.toSet()
        )
    }

    @Test
    fun moveUpSwapsWithTheRowAbove() {
        val order = listOf("a", "b", "c")
        assertEquals(
            listOf("a", "c", "b"),
            BrowserMenuRows.moved(order, "c", -1)
        )
    }

    @Test
    fun moveDownSwapsWithTheRowBelow() {
        val order = listOf("a", "b", "c")
        assertEquals(
            listOf("b", "a", "c"),
            BrowserMenuRows.moved(order, "a", 1)
        )
    }

    @Test
    fun moveClampsAtTheEndsAndIgnoresUnknownRows() {
        val order = listOf("a", "b", "c")
        assertEquals(order, BrowserMenuRows.moved(order, "a", -1))
        assertEquals(order, BrowserMenuRows.moved(order, "c", 1))
        assertEquals(order, BrowserMenuRows.moved(order, "zzz", 1))
    }
}
