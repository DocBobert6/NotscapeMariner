package net.notscape.browser

import net.notscape.browser.data.HistoryEntry
import net.notscape.browser.data.StoreJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
