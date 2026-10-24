package net.notscape.browser

import net.notscape.browser.data.BookmarkEntity
import net.notscape.browser.data.SettingEntity
import net.notscape.browser.data.StoreMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.0.9994: the store's diff-write decision. */
class SliceDiffTest {

    private fun slice(
        bookmarks: List<BookmarkEntity> = emptyList(),
        settings: List<SettingEntity> = emptyList(),
        session: String = "s"
    ) = StoreMigration.MainSlice(
        bookmarks, emptyList(), emptyList(), emptyList(), emptyList(),
        emptyList(), emptyList(), emptyList(), emptyList(), settings,
        session
    )

    @Test fun nothingWrittenYetMeansEverythingChanged() {
        assertEquals(11, StoreMigration.mainSliceChanges(null, slice()).size)
    }

    @Test fun identicalSlicesChangeNothing() {
        val a = slice(bookmarks = listOf(BookmarkEntity(1, "t", "u", 5, "")))
        val b = slice(bookmarks = listOf(BookmarkEntity(1, "t", "u", 5, "")))
        assertTrue(StoreMigration.mainSliceChanges(a, b).isEmpty())
    }

    @Test fun oneTableChangeIsNamedExactly() {
        val a = slice()
        val b = slice(bookmarks = listOf(BookmarkEntity(2, "x", "y", 6, "f")))
        assertEquals(setOf("bookmarks"), StoreMigration.mainSliceChanges(a, b))
        val c = slice(settings = listOf(SettingEntity("k", "v")))
        assertEquals(setOf("settings"), StoreMigration.mainSliceChanges(a, c))
        val d = slice(session = "other")
        assertEquals(setOf("session"), StoreMigration.mainSliceChanges(a, d))
    }
}
