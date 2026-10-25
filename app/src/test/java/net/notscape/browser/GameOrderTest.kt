package net.notscape.browser

import net.notscape.browser.emu.GameEntry
import net.notscape.browser.emu.GameOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saved display order (beta.6): group defaults,
 * saved orders with unknown ids dropped and missing
 * members appended, alphabetical game fallback, and
 * the drag-move itself.
 */
class GameOrderTest {
    private fun entry(id: String, title: String) =
        GameEntry(id, title, "gba", "$id.gba", 0L, 0L)

    @Test
    fun consoleDefaultsAreReleaseOrderPerGroup() {
        val empty: (String, String) -> String = { _, d -> d }
        assertEquals(
            listOf("gb", "gg", "gbc", "gba"),
            GameOrder.orderedConsoles(empty, "handheld")
        )
        assertEquals(
            listOf("nes", "sms", "genesis", "snes"),
            GameOrder.orderedConsoles(empty, "home")
        )
    }

    @Test
    fun consoleSaveDropsUnknownAndAppendsMissing() {
        val lookup: (String, String) -> String = { k, d ->
            when (k) {
                "gameConsoleOrderHome" ->
                    "[\"snes\", \"nope\", \"nes\"]"
                else -> d
            }
        }
        assertEquals(
            listOf("snes", "nes", "sms", "genesis"),
            GameOrder.orderedConsoles(lookup, "home")
        )
    }

    @Test
    fun gamesDefaultAlphabeticalAndSavedHeadFirst() {
        val games = listOf(
            entry("b", "Zelda"),
            entry("a", "Mario"),
            entry("c", "Asterix")
        )
        val empty: (String, String) -> String = { _, d -> d }
        assertEquals(
            listOf("c", "a", "b"),
            GameOrder.orderedGames(empty, "gba", games)
                .map { it.id }
        )
        val lookup: (String, String) -> String = { k, d ->
            when (k) {
                "gameOrder:gba" -> "[\"b\", \"gone\"]"
                else -> d
            }
        }
        assertEquals(
            listOf("b", "c", "a"),
            GameOrder.orderedGames(lookup, "gba", games)
                .map { it.id }
        )
    }

    @Test
    fun moveIdInsertsBeforeOrAfterTarget() {
        val order = listOf("a", "b", "c", "d")
        assertEquals(
            listOf("a", "c", "b", "d"),
            GameOrder.moveId(order, "c", "b", false)
        )
        assertEquals(
            listOf("a", "c", "d", "b"),
            GameOrder.moveId(order, "b", "d", true)
        )
        // Dragging an item toward a neighbour without
        // crossing its centre leaves the order alone.
        assertEquals(
            order,
            GameOrder.moveId(order, "b", "c", false)
        )
        assertEquals(
            order,
            GameOrder.moveId(order, "b", "b", false)
        )
        assertEquals(
            order,
            GameOrder.moveId(order, "gone", "a", false)
        )
        assertEquals(
            listOf("b", "c", "d", "a"),
            GameOrder.moveId(order, "a", "gone", true)
        )
    }

    @Test
    fun jsonRoundTrips() {
        val ids = listOf("gb", "snes", "x y")
        assertEquals(
            ids,
            GameOrder.idsFromJson(GameOrder.idsToJson(ids))
        )
        assertTrue(GameOrder.idsFromJson("not json").isEmpty())
        assertTrue(GameOrder.idsFromJson("").isEmpty())
    }
}
