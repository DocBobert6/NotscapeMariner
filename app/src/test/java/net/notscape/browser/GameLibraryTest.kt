package net.notscape.browser

import net.notscape.browser.emu.BoxArt
import net.notscape.browser.emu.GameEntry
import net.notscape.browser.emu.GameLayout
import net.notscape.browser.emu.GameLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase J (J2): library records, layouts, box-art names. */
class GameLibraryTest {

    @Test
    fun libraryRoundTrips() {
        val games = listOf(
            GameEntry("g1", "Tetris (World)", "gb", "Tetris.gb", 10L, 20L),
            GameEntry("g2", "Zelda & Link's Awakening", "gbc", "z.gbc", 11L)
        )
        val back = GameLibrary.parse(GameLibrary.toJson(games))
        assertEquals(games, back)
    }

    @Test
    fun libraryParseFailsSoft() {
        assertTrue(GameLibrary.parse("not json").isEmpty())
        assertTrue(GameLibrary.parse("").isEmpty())
    }

    @Test
    fun extensionsMapToSystems() {
        assertEquals("gb", GameLibrary.systemForExtension("GB"))
        assertEquals("gbc", GameLibrary.systemForExtension("gbc"))
        assertEquals("gba", GameLibrary.systemForExtension("gba"))
        assertEquals("nes", GameLibrary.systemForExtension("nes"))
        assertEquals("snes", GameLibrary.systemForExtension("smc"))
        assertEquals("genesis", GameLibrary.systemForExtension("md"))
        assertNull(GameLibrary.systemForExtension("exe"))
    }

    @Test
    fun layoutRoundTripsAndClamps() {
        val specs = GameLayout.defaults("gb")
        val back = GameLayout.parse(GameLayout.toJson(specs))
        assertEquals(specs.size, back?.size)
        assertEquals(specs.map { it.id }, back?.map { it.id })
        assertEquals(specs.map { it.sizeDp }, back?.map { it.sizeDp })
    }

    @Test
    fun snapRoundsToGrid() {
        assertEquals(16f, GameLayout.snap(17f, 8, true), 0.01f)
        assertEquals(17f, GameLayout.snap(17f, 8, false), 0.01f)
        assertEquals(24f, GameLayout.snap(21f, 8, true), 0.01f)
    }

    @Test
    fun boxArtServerNamesFollowTheConvention() {
        assertEquals(
            "Tetris (World)",
            BoxArt.serverName("Tetris (World)")
        )
        // Filename-hostile characters become underscores.
        assertEquals(
            "Game_ A_B",
            BoxArt.serverName("Game: A/B")
        )
        assertEquals(
            "Nintendo - Game Boy Advance",
            BoxArt.folderFor("gba")
        )
        assertNull(BoxArt.folderFor("atari"))
    }
}
