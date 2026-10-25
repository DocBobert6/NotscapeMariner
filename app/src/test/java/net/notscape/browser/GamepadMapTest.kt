package net.notscape.browser

import android.view.KeyEvent
import net.notscape.browser.emu.GamepadMap
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phase J (J6): positional gamepad mapping. */
class GamepadMapTest {

    @Test
    fun faceButtonsArePositional() {
        // South = B, east = A, west = Y, north = X, on any
        // brand's pad.
        assertEquals(0, GamepadMap.bitForKeyCode(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(8, GamepadMap.bitForKeyCode(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(1, GamepadMap.bitForKeyCode(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(9, GamepadMap.bitForKeyCode(KeyEvent.KEYCODE_BUTTON_Y))
    }

    @Test
    fun dpadAndSystemButtons() {
        assertEquals(4, GamepadMap.bitForKeyCode(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(7, GamepadMap.bitForKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(10, GamepadMap.bitForKeyCode(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(2, GamepadMap.bitForKeyCode(KeyEvent.KEYCODE_BUTTON_SELECT))
        assertEquals(-1, GamepadMap.bitForKeyCode(KeyEvent.KEYCODE_A))
    }

    @Test
    fun axesDeadzone() {
        assertEquals(0, GamepadMap.bitsForAxes(0.2f, -0.2f))
        assertEquals(
            (1 shl 6) or (1 shl 4),
            GamepadMap.bitsForAxes(-0.8f, -0.8f)
        )
    }
}
