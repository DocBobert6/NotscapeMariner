package net.notscape.browser

import net.notscape.browser.data.StoreJson
import net.notscape.browser.data.StoreMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase F migration tests: the real JSON document parser (the same
 * one AppStore's loader and the Room importer share) feeds the pure
 * model -> entity mapping, and the reverse mapping rebuilds the
 * model — including the corrupted-main / good-.bak recovery case.
 */
class StoreMigrationTest {

    private val mainJson = """
        {
          "schemaVersion": 1,
          "bookmarks": [
            {"id": 11, "title": "Docs", "url": "https://example.com/docs", "t": 100, "f": "Work"},
            {"id": 12, "title": "News", "url": "https://example.com/news", "t": 101, "f": ""}
          ],
          "bookmarkFolders": ["Work"],
          "favourites": [
            {"id": 1, "title": "Wikipedia", "url": "https://www.wikipedia.org", "t": 1}
          ],
          "history": [
            {"id": 21, "title": "Old", "url": "https://example.com/old", "t": 200}
          ],
          "playlists": [
            {"id": 31, "name": "Watch later", "t": 300, "items": [
              {"id": 32, "title": "V1", "url": "https://v.example/1"},
              {"id": 33, "title": "V2", "url": "https://v.example/2"}
            ]}
          ],
          "downloads": [
            {"id": 41, "title": "Movie", "url": "https://example.com/m.mp4",
             "fileName": "m.mp4", "dmId": 7, "status": "complete", "t": 400,
             "m": true, "p": 100, "uri": "content://media/1"}
          ],
          "books": [
            {"id": 51, "title": "Novel", "author": "An Author", "path": "/books/n.epub",
             "format": "EPUB", "chapter": 3, "frac": 0.5, "t": 500, "cv": "/books/51-cover.img"}
          ],
          "vault": [
            {"id": 61, "site": "example.com", "user": "robert", "enc": "2:AAAA"}
          ],
          "siteSettings": {
            "example.com": {"js": false, "img": true, "pop": true, "nb": false, "bp": false, "c3": true}
          },
          "settings": {"homeUrl": "https://home.example", "themeMode": "dark"},
          "session": {"active": 1, "tabs": [
            {"url": "https://a.example", "title": "A"},
            {"url": "https://b.example", "title": "B"}
          ]},
          "positions": {"https://v.example/1": 42000}
        }
    """.trimIndent()

    private val historyJson = """
        {"schemaVersion": 1, "history": [
          {"id": 71, "title": "H1", "url": "https://h.example/1", "t": 700},
          {"id": 72, "title": "H2", "url": "https://h.example/2", "t": 701}
        ]}
    """.trimIndent()

    private val positionsJson = """
        {"schemaVersion": 1, "positions": {"https://v.example/9": 9000}}
    """.trimIndent()

    @Test
    fun jsonDocumentParsesIntoCompleteModel() {
        val doc = StoreJson.parseMainDocument(mainJson)
        assertNotNull(doc)
        doc!!
        assertEquals(2, doc.bookmarks.size)
        assertEquals("Work", doc.bookmarks[0].folder)
        assertEquals(listOf("Work"), doc.bookmarkFolders)
        assertEquals(1, doc.favourites.size)
        assertEquals(1, doc.playlists.size)
        assertEquals(2, doc.playlists[0].items.size)
        assertEquals("content://media/1", doc.downloads[0].contentUri)
        assertTrue(doc.downloads[0].media)
        assertEquals("/books/51-cover.img", doc.books[0].coverPath)
        assertEquals(3, doc.books[0].chapterIndex)
        assertEquals("2:AAAA", doc.vault[0].passwordEnc)
        val site = doc.siteSettings["example.com"]
        assertNotNull(site)
        assertEquals(false, site!!.javaScript)
        assertEquals(true, site.allowPopups)
        assertEquals(true, site.allowThirdPartyCookies)
        assertEquals("dark", doc.settings["themeMode"])
        assertEquals(2, doc.tabSession.size)
        assertEquals(1, doc.tabSessionActive)
        // Legacy (pre-sidecar) copies are exposed for migration.
        assertEquals(1, doc.legacyHistory?.size)
        assertEquals(42000L, doc.legacyPositions?.get("https://v.example/1"))
    }

    @Test
    fun documentToEntitiesAndBackRoundTrips() {
        val doc = StoreJson.parseMainDocument(mainJson)!!
        val history = StoreJson.parseHistory(historyJson)!!
        val positions = StoreJson.parsePositions(positionsJson)!!

        val main = StoreMigration.mainSlice(
            doc.bookmarks, doc.bookmarkFolders, doc.favourites, doc.playlists,
            doc.downloads, doc.books, doc.vault, doc.siteSettings, doc.settings,
            doc.tabSession, doc.tabSessionActive
        )
        assertEquals(2, main.bookmarks.size)
        assertEquals(2, main.playlistItems.size)
        assertEquals(31L, main.playlistItems[0].playlistId)
        assertEquals(1, main.playlistItems[1].position)
        assertEquals("/books/51-cover.img", main.books[0].coverPath)
        assertEquals("content://media/1", main.downloads[0].contentUri)
        val hist = StoreMigration.historySlice(history)
        val pos = StoreMigration.positionsSlice(positions)
        assertEquals(2, hist.size)
        assertEquals(9000L, pos[0].positionMs)

        val model = StoreMigration.toModel(
            main.bookmarks, main.folders, main.favourites, hist,
            main.playlists, main.playlistItems, main.downloads, main.books,
            main.vault, main.siteSettings, main.settings, pos, main.sessionJson
        )
        assertEquals("Docs", model.bookmarks[0].title)
        assertEquals(1, model.favourites.size)
        assertEquals("Wikipedia", model.favourites[0].title)
        assertEquals("https://www.wikipedia.org", model.favourites[0].url)
        assertEquals("Work", model.bookmarks[0].folder)
        assertEquals(listOf("Work"), model.bookmarkFolders)
        assertEquals("V2", model.playlists[0].items[1].title)
        assertEquals(0.5f, model.books[0].scrollFraction)
        assertEquals(false, model.siteSettings["example.com"]!!.javaScript)
        assertEquals("https://b.example", model.tabSession[1].url)
        assertEquals(1, model.tabSessionActive)
        assertEquals(9000L, model.playbackPositions["https://v.example/9"])
        assertEquals(2, model.history.size)
    }

    @Test
    fun corruptedMainFallsBackToBackupFixture() {
        // A truncated main document (crash mid-write on an old
        // version) must never shadow a good .bak: pickReadable is
        // the recovery rule, and the backup parses completely.
        val corrupted = mainJson.substring(0, mainJson.length / 2)
        val picked = StoreJson.pickReadable(corrupted, mainJson)
        assertEquals(mainJson, picked)
        val doc = StoreJson.parseMainDocument(picked!!)
        assertNotNull(doc)
        assertEquals(2, doc!!.bookmarks.size)
        assertEquals(1, doc.books.size)
        // And when even JSON syntax survives but the shape is wrong,
        // the document parser still refuses cleanly.
        assertNull(StoreJson.parseMainDocument("[1,2,3]"))
    }
}
