package net.notscape.browser

import net.notscape.browser.emu.StartShortcuts
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase K, beta.7: start-page shortcut pin storage. */
class StartShortcutsTest {

    @Test
    fun emptyForBlank() {
        assertEquals(
            StartShortcuts.Pins(),
            StartShortcuts.load { _, _ -> "" }
        )
    }

    @Test
    fun garbageTolerated() {
        assertEquals(
            StartShortcuts.Pins(),
            StartShortcuts.load { _, _ -> "{not json" }
        )
    }

    @Test
    fun unknownIdsDropped() {
        val pins = StartShortcuts.load { _, _ ->
            "{\"sections\":[\"games\",\"nope\"]," +
                "\"consoles\":[\"snes\",\"atari\"]," +
                "\"games\":[\"g123\"]}"
        }
        assertEquals(listOf("games"), pins.sections)
        assertEquals(listOf("snes"), pins.consoles)
        assertEquals(listOf("g123"), pins.games)
    }

    @Test
    fun labels() {
        assertEquals(
            "Offline library",
            StartShortcuts.sectionLabel("library")
        )
        assertEquals(
            "Game Boy Advance",
            StartShortcuts.consoleLabel("gba")
        )
    }
}
