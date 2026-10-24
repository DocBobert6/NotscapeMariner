package net.notscape.browser

import net.notscape.browser.emu.SaveStates
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Phase J (J5): state file naming and layout. */
class SaveStatesTest {

    @Test
    fun fileNames() {
        assertEquals(
            "auto.state",
            SaveStates.stateFileName(SaveStates.AUTO_SLOT)
        )
        assertEquals("slot2.state", SaveStates.stateFileName(2))
        assertEquals(
            "auto.png",
            SaveStates.thumbFileName(SaveStates.AUTO_SLOT)
        )
        assertEquals("slot0.png", SaveStates.thumbFileName(0))
    }

    @Test
    fun dirLayout() {
        val tmp = File(
            System.getProperty("java.io.tmpdir"),
            "saves-test-" + System.nanoTime()
        )
        assertEquals(
            File(File(tmp, "saves"), "game-1"),
            SaveStates.dirFor(tmp, "game-1")
        )
    }

    @Test
    fun gameIdFallback() {
        assertEquals(
            "rom-sonic",
            SaveStates.gameIdFor(null, "/sdcard/roms/sonic.md")
        )
        assertEquals(
            "abc",
            SaveStates.gameIdFor("abc", "/x/y.md")
        )
    }
}
