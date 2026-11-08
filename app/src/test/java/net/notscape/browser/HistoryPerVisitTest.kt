package net.notscape.browser

import net.notscape.browser.data.HistoryEntry
import net.notscape.browser.data.StoreJson
import net.notscape.browser.data.collapseHistoryByUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Beta.64 #19: history is per-visit, no cap. */
class HistoryPerVisitTest {

    @Test
    fun collapseKeepsLatestVisitPerUrl() {
        val rows = listOf(
            HistoryEntry(3, "B2", "https://b.example", 300),
            HistoryEntry(2, "A", "https://a.example", 200),
            HistoryEntry(1, "B1", "https://b.example", 100)
        )
        val collapsed = collapseHistoryByUrl(rows)
        assertEquals(2, collapsed.size)
        assertEquals(3L, collapsed[0].id) // latest B visit
        assertEquals(2L, collapsed[1].id)
    }

    @Test
    fun legacyHistoryFileLoadsUnchangedAsSeed() {
        val parsed = StoreJson.parseHistory(
            """
            {"history": [
              {"id": 11, "title": "Docs", "url": "https://example.com/docs", "t": 100},
              {"id": 12, "title": "News", "url": "https://example.com/news", "t": 101}
            ]}
            """.trimIndent()
        )
        assertNotNull(parsed)
        assertEquals(2, parsed!!.size)
        assertEquals(11L, parsed[0].id)
    }
}
