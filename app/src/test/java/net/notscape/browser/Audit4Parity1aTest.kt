package net.notscape.browser

import net.notscape.browser.browser.TabList
import net.notscape.browser.browser.TabState
import net.notscape.browser.data.StoreMigration
import net.notscape.browser.data.TabSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fourth-audit parity 1A: session restore + tab list operations. */
class Audit4Parity1aTest {

    @Test
    fun sessionJsonRoundTripsNewFields() {
        val snaps = listOf(
            TabSnapshot("https://a.example/", "A", true, 420, "AAA=", 1),
            TabSnapshot("https://b.example/", "B", false, 0, "", 0)
        )
        val json = StoreMigration.sessionToJson(snaps, 1)
        val (back, active) = StoreMigration.sessionFromJson(json)
        assertEquals(1, active)
        assertEquals(2, back.size)
        assertTrue(back[0].desktopMode)
        assertEquals(420, back[0].scrollY)
        assertEquals("AAA=", back[0].stateB64)
        assertEquals(1, back[0].stateVersion)
        assertFalse(back[1].desktopMode)
    }

    @Test
    fun sessionJsonOldShapeStillParses() {
        // Beta.61: tab groups are gone — an old saved session still
        // carries a "group" key, and it must parse with the key
        // ignored (grouped tabs flatten to ungrouped on load).
        val old = """{"active":0,"tabs":[{"url":"https://a.example/","title":"A","group":"work"}]}"""
        val (back, active) = StoreMigration.sessionFromJson(old)
        assertEquals(0, active)
        assertEquals(1, back.size)
        assertFalse(back[0].desktopMode)
        assertEquals(0, back[0].scrollY)
        assertEquals("", back[0].stateB64)
    }

    @Test
    fun moveWithinClassKeepsOtherClassInPlace() {
        val list = TabList()
        val a = TabState(1, "https://a.example/")
        val b = TabState(2, "https://b.example/")
        val p = TabState(3, "https://p.example/", isPrivate = true)
        val c = TabState(4, "https://c.example/")
        listOf(a, b, p, c).forEach { list.add(it) }
        list.moveWithinClass(c, 0)
        val order = list.tabs.map { it.id }
        assertEquals(listOf(4, 1, 3, 2), order)
    }

    @Test
    fun tabsInClassFiltersByPrivacyClass() {
        val list = TabList()
        val a = TabState(1, "https://a.example/")
        val b = TabState(2, "https://b.example/")
        val p = TabState(3, "https://p.example/", isPrivate = true)
        listOf(a, b, p).forEach { list.add(it) }
        assertEquals(listOf(a, b), list.tabsInClass(a))
        assertEquals(listOf(p), list.tabsInClass(p))
        assertEquals(2, list.classCount(false))
        assertEquals(1, list.classCount(true))
    }

    @Test
    fun neighbourAfterClosePicksPreviousNeighbour() {
        val list = TabList()
        val a = TabState(1, "https://a.example/")
        val b = TabState(2, "https://b.example/")
        val c = TabState(3, "https://c.example/")
        listOf(a, b, c).forEach { list.add(it) }
        assertEquals(b, list.neighbourAfterClose(c))
        assertEquals(a, list.neighbourAfterClose(b))
    }

    @Test
    fun neighbourAfterClosePicksNextWhenFirst() {
        val list = TabList()
        val a = TabState(1, "https://a.example/")
        val b = TabState(2, "https://b.example/")
        listOf(a, b).forEach { list.add(it) }
        assertEquals(b, list.neighbourAfterClose(a))
    }

    @Test
    fun neighbourAfterCloseNullWhenAlone() {
        val list = TabList()
        val a = TabState(1, "https://a.example/")
        list.add(a)
        assertNull(list.neighbourAfterClose(a))
    }

    @Test
    fun neighbourAfterCloseStaysInPrivacyClass() {
        val list = TabList()
        val a = TabState(1, "https://a.example/")
        val p = TabState(2, "https://p.example/", isPrivate = true)
        val b = TabState(3, "https://b.example/")
        listOf(a, p, b).forEach { list.add(it) }
        assertEquals(b, list.neighbourAfterClose(a))
        assertNull(list.neighbourAfterClose(p))
    }
}
