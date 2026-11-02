package net.notscape.browser

import net.notscape.browser.data.HistoryEntry
import net.notscape.browser.data.StoreJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Store recovery + sidecar round-trips (Phase D.0 security-logic tests). */
class StoreRecoveryTest {

    @Test
    fun pickReadablePrefersValidMain() {
        val main = """{"schemaVersion":1,"bookmarks":[]}"""
        val bak = """{"schemaVersion":1,"bookmarks":[{"id":1}]}"""
        assertEquals(main, StoreJson.pickReadable(main, bak))
    }

    @Test
    fun pickReadableFallsBackToBakWhenMainCorrupt() {
        val bak = """{"schemaVersion":1}"""
        assertEquals(bak, StoreJson.pickReadable("{not json", bak))
        assertEquals(bak, StoreJson.pickReadable(null, bak))
    }

    @Test
    fun pickReadableNullWhenBothBad() {
        assertNull(StoreJson.pickReadable("garbage", "also garbage"))
        assertNull(StoreJson.pickReadable(null, null))
    }

    @Test
    fun historyRoundTrips() {
        val entries = listOf(
            HistoryEntry(3, "Example", "https://example.com/", 1729000000000),
            HistoryEntry(2, "", "https://second.example/path?q=1", 1729000001000)
        )
        val parsed = StoreJson.parseHistory(StoreJson.historyToJson(entries))
        assertEquals(entries, parsed)
    }

    @Test
    fun positionsRoundTripAndCorruptInput() {
        val map = mapOf("https://a.example/v" to 12345L, "https://b.example/v" to 0L)
        assertEquals(map, StoreJson.parsePositions(StoreJson.positionsToJson(map)))
        assertNull(StoreJson.parseHistory("{broken"))
        assertNull(StoreJson.parsePositions("{}"))
    }
    // Backup restore (0.0.997): a document in exportBackup's
    // shape parses back with its contents intact - and the
    // folder a bookmark references is restored even when the
    // folder list forgot it.
    @Test
    fun backupDocumentParsesBack() {
        val json = """
            {"schemaVersion":1,
             "bookmarks":[{"id":1,"title":"Example","url":"https://example.com","t":100,"f":"Reading"}],
             "bookmarkFolders":[],
             "favourites":[{"id":2,"title":"Fav","url":"https://fav.example","t":200}],
             "history":[{"id":3,"title":"Old","url":"https://old.example","t":300}],
             "playlists":[{"id":4,"name":"Queue","t":400,"items":[{"id":5,"title":"Clip","url":"https://clip.example"}]}]}
        """.trimIndent()
        val doc = net.notscape.browser.data.StoreJson.parseMainDocument(json)
        assertTrue(doc != null)
        assertEquals(1, doc!!.bookmarks.size)
        assertEquals("Reading", doc.bookmarks[0].folder)
        assertEquals(listOf("Reading"), doc.bookmarkFolders)
        assertEquals(1, doc.favourites.size)
        assertEquals(1, doc.legacyHistory?.size)
        assertEquals(1, doc.playlists.size)
        assertEquals(1, doc.playlists[0].items.size)
    }

    @Test
    fun backupDocumentCorruptParsesNull() {
        assertNull(
            net.notscape.browser.data.StoreJson
                .parseMainDocument("{broken json")
        )
    }
}
