package net.notscape.browser

import net.notscape.browser.emu.CueSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Disc track lists: FILE lines (quoted, bare,
 * several) and .m3u lines reduce to bare names,
 * and matching ignores case.
 */
class CueSheetTest {
    @Test
    fun cueQuotedAndBareFileLines() {
        val cue = """
            FILE "Game Name (USA).bin" BINARY
              TRACK 1 MODE2/2352
                INDEX 1 00:00:00
            FILE game2.bin BINARY
              TRACK 2 MODE2/2352
        """.trimIndent()
        assertEquals(
            listOf("Game Name (USA).bin", "game2.bin"),
            CueSheet.cueFiles(cue)
        )
    }

    @Test
    fun cueIgnoresNonFileLinesAndStripsPaths() {
        // The backslash is built, not written: a
        // literal backslash in a patch doubles.
        val bs = 0x5C.toChar()
        val cue = "REM Comment\n" +
            "FILE \"subdir${bs}disc.bin\" BINARY\n" +
            "TRACK 1 AUDIO\n" +
            "PREGAP 00:02:00\n"
        assertEquals(listOf("disc.bin"), CueSheet.cueFiles(cue))
    }

    @Test
    fun m3uSkipsCommentsAndBlanks() {
        val m3u = """
            #EXTM3U
            disc1.cue

            # A comment
            sub/disc2.chd
        """.trimIndent()
        assertEquals(
            listOf("disc1.cue", "disc2.chd"),
            CueSheet.m3uFiles(m3u)
        )
    }

    @Test
    fun matchingIgnoresCase() {
        assertTrue(CueSheet.matches("Game.BIN", "game.bin"))
        assertFalse(CueSheet.matches("game.bin", "other.bin"))
    }
}
