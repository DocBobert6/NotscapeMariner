package net.notscape.browser

import net.notscape.browser.browser.BookmarkHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkHtmlExportTest {

    @Test
    fun roundTripsFlatAndNestedFolders() {
        val items = listOf(
            BookmarkHtml.ExportItem(
                "Alpha", "https://example.com/a", "News", 1_700_000_000_000L
            ),
            BookmarkHtml.ExportItem(
                "Beta", "https://example.com/b", "News/Tech", 1_700_000_000_000L
            ),
            BookmarkHtml.ExportItem(
                "Gamma", "https://example.com/c", "", 1_700_000_000_000L
            )
        )
        val parsed = BookmarkHtml.parse(BookmarkHtml.export(items))
        assertEquals(
            listOf(
                Triple("Alpha", "https://example.com/a", "News"),
                Triple("Beta", "https://example.com/b", "News/Tech"),
                Triple("Gamma", "https://example.com/c", "")
            ),
            parsed
        )
    }

    @Test
    fun escapesTitlesUrlsAndFolderNames() {
        val items = listOf(
            BookmarkHtml.ExportItem(
                "A & B <C>", "https://example.com/?x=1&y=2",
                "Tom & Jerry", 0L
            )
        )
        val html = BookmarkHtml.export(items)
        assertFalse(html.contains("A & B"))
        assertTrue(html.contains("A &amp; B &lt;C&gt;"))
        val parsed = BookmarkHtml.parse(html)
        assertEquals(1, parsed.size)
        assertEquals(
            Triple(
                "A & B <C>", "https://example.com/?x=1&y=2",
                "Tom & Jerry"
            ),
            parsed[0]
        )
    }

    @Test
    fun emptyExportParsesToNothing() {
        assertEquals(
            emptyList<Triple<String, String, String>>(),
            BookmarkHtml.parse(BookmarkHtml.export(emptyList()))
        )
    }
}
