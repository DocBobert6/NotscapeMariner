package net.notscape.browser

import net.notscape.browser.browser.BookmarkHtml
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkHtmlTest {

    @Test
    fun parsesFoldersAndLinks() {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <DL><p>
            <DT><H3>News</H3>
            <DL><p>
            <DT><A HREF="https://example.com/a">Alpha</A>
            <DT><A HREF="https://example.com/b">Beta</A>
            </DL><p>
            <DT><A HREF="https://example.com/c">Gamma</A>
            </DL><p>
        """.trimIndent()
        val items = BookmarkHtml.parse(html)
        assertEquals(3, items.size)
        assertEquals(Triple("Alpha", "https://example.com/a", "News"), items[0])
        assertEquals(Triple("Beta", "https://example.com/b", "News"), items[1])
        assertEquals(Triple("Gamma", "https://example.com/c", ""), items[2])
    }

    @Test
    fun skipsNonHttpLinks() {
        val html = """<DL><p>
            <DT><A HREF="ftp://example.com/x">Nope</A>
            <DT><A HREF="https://example.com/ok">Yes</A>
            </DL><p>"""
        val items = BookmarkHtml.parse(html)
        assertEquals(1, items.size)
        assertEquals("https://example.com/ok", items[0].second)
    }
}
