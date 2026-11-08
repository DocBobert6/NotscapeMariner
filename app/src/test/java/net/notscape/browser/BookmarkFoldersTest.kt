package net.notscape.browser

import net.notscape.browser.data.Bookmark
import net.notscape.browser.data.StoreJson
import net.notscape.browser.data.folderAncestors
import net.notscape.browser.data.folderChild
import net.notscape.browser.data.folderChildren
import net.notscape.browser.data.folderName
import net.notscape.browser.data.folderParent
import net.notscape.browser.data.isFolderSegment
import net.notscape.browser.data.refileBookmarksAfterRename
import net.notscape.browser.data.renameFolderPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Beta.64 #18: nested bookmark folders are paths. */
class BookmarkFoldersTest {

    @Test
    fun folderPathHelpers() {
        assertEquals("", folderParent("News"))
        assertEquals("News", folderParent("News/Tech"))
        assertEquals("Tech", folderName("News/Tech"))
        assertEquals("News", folderName("News"))
        assertEquals("News/Tech", folderChild("News", "Tech"))
        assertEquals("News", folderChild("", "News"))
        assertTrue(isFolderSegment("Tech"))
        assertFalse(isFolderSegment(""))
        assertFalse(isFolderSegment("A/B"))
        assertEquals(listOf("A", "A/B", "A/B/C"), folderAncestors("A/B/C"))
        assertEquals(
            listOf("A/B", "A/C"),
            folderChildren(listOf("A", "A/B", "A/C", "B"), "A")
        )
        assertEquals(
            listOf("A", "B"),
            folderChildren(listOf("A", "A/B", "B"), "")
        )
    }

    @Test
    fun renameReRootsFoldersAndBookmarks() {
        val folders = listOf("News", "News/Tech", "News/Sport", "Other")
        assertEquals(
            listOf("Press", "Press/Tech", "Press/Sport", "Other"),
            renameFolderPath(folders, "News", "Press")
        )
        val marks = listOf(
            Bookmark(1, "A", "https://a.example", 1, "News"),
            Bookmark(2, "B", "https://b.example", 2, "News/Tech"),
            Bookmark(3, "C", "https://c.example", 3, "Other"),
            Bookmark(4, "D", "https://d.example", 4, "")
        )
        val refiled = refileBookmarksAfterRename(marks, "News", "Press")
        assertEquals("Press", refiled[0].folder)
        assertEquals("Press/Tech", refiled[1].folder)
        assertEquals("Other", refiled[2].folder)
        assertEquals("", refiled[3].folder)
    }

    @Test
    fun restoreRebuildsAncestorFolders() {
        val doc = StoreJson.parseMainDocument(
            """
            {
              "schemaVersion": 1,
              "bookmarks": [
                {"id": 11, "title": "Deep", "url": "https://example.com/deep", "t": 100, "f": "Work/Projects"}
              ],
              "bookmarkFolders": []
            }
            """.trimIndent()
        )
        assertNotNull(doc)
        assertEquals(listOf("Work", "Work/Projects"), doc!!.bookmarkFolders)
    }
}
