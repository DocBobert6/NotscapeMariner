package net.notscape.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Bridge pick parsing (Phase 4): the picker JSON is the
 *  only thing standing between a page element and a
 *  block rule, so its edges are pinned. */
class ParsePickTest {

    @Test
    fun validPickParsesAllFields() {
        val pick = parsePick(
            """{"selector":"div.ad","tag":"DIV","id":"top",""" +
                """"cls":"ad box","w":300,"h":250,"src":"x.png"}"""
        )
        assertNotNull(pick)
        assertEquals("div.ad", pick!!.selector)
        assertEquals("DIV", pick.tag)
        assertEquals("top", pick.id)
        assertEquals("ad box", pick.classes)
        assertEquals(300, pick.width)
        assertEquals(250, pick.height)
        assertEquals("x.png", pick.src)
    }

    @Test
    fun blankSelectorIsNull() {
        assertNull(parsePick("""{"selector":"  ","tag":"DIV"}"""))
        assertNull(parsePick("""{"tag":"DIV"}"""))
    }

    @Test
    fun garbageIsNull() {
        assertNull(parsePick("not json"))
        assertNull(parsePick(""))
    }

    @Test
    fun selectorOnlyUsesDefaults() {
        val pick = parsePick("""{"selector":"#promo"}""")
        assertNotNull(pick)
        assertEquals("#promo", pick!!.selector)
        assertEquals("", pick.tag)
        assertEquals("", pick.id)
        assertEquals("", pick.classes)
        assertEquals(0, pick.width)
        assertEquals(0, pick.height)
        assertEquals("", pick.src)
    }
}
