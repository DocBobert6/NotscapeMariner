package net.notscape.browser

import net.notscape.browser.browser.TabList
import net.notscape.browser.browser.TabState
import net.notscape.browser.data.StoreMigration
import net.notscape.browser.data.TabSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fourth-audit parity 1A: session restore + tab list operations. */
class Audit4Parity1aTest {

    @Test
    fun sessionJsonRoundTripsNewFields() {
        val snaps = listOf(
            TabSnapshot("https://a.example/", "A", "work", true, 420, "AAA=", 1),
            TabSnapshot("https://b.example/", "B", "", false, 0, "", 0)
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
        val old = """{"active":0,"tabs":[{"url":"https://a.example/","title":"A","group":""}]}"""
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
    fun renameGroupOnlyTouchesItsClass() {
        val list = TabList()
        val a = TabState(1, "https://a.example/")
        a.group = "work"
        val p = TabState(2, "https://p.example/", isPrivate = true)
        p.group = "work"
        listOf(a, p).forEach { list.add(it) }
        list.renameGroup("work", "job", false)
        assertEquals("job", a.group)
        assertEquals("work", p.group)
    }

    @Test
    fun tabsInGroupFiltersByClass() {
        val list = TabList()
        val a = TabState(1, "https://a.example/")
        a.group = "g"
        val b = TabState(2, "https://b.example/")
        b.group = "g"
        listOf(a, b).forEach { list.add(it) }
        assertEquals(2, list.tabsInGroup("g", false).size)
        assertEquals(0, list.tabsInGroup("g", true).size)
    }
}
