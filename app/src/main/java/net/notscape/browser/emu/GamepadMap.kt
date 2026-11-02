package net.notscape.browser.emu

import android.view.KeyEvent

/**
 * Positional gamepad mapping (Phase J, J6): libretro joypad
 * ids are positions, not letters. The south face button is
 * libretro B, east is A, west is Y, north is X - the same
 * diamond on DualShock 4, DualSense and Xbox pads, whatever
 * letter the brand prints on it. KeyEvent constants are
 * compile-time ints, so this is unit-testable on the JVM.
 */
object GamepadMap {
    fun bitForKeyCode(code: Int): Int = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> 4
        KeyEvent.KEYCODE_DPAD_DOWN -> 5
        KeyEvent.KEYCODE_DPAD_LEFT -> 6
        KeyEvent.KEYCODE_DPAD_RIGHT -> 7
        KeyEvent.KEYCODE_BUTTON_A -> 0
        KeyEvent.KEYCODE_BUTTON_B -> 8
        KeyEvent.KEYCODE_BUTTON_X -> 1
        KeyEvent.KEYCODE_BUTTON_Y -> 9
        KeyEvent.KEYCODE_BUTTON_L1 -> 10
        KeyEvent.KEYCODE_BUTTON_R1 -> 11
        KeyEvent.KEYCODE_BUTTON_L2 -> 10
        KeyEvent.KEYCODE_BUTTON_R2 -> 11
        KeyEvent.KEYCODE_BUTTON_SELECT -> 2
        KeyEvent.KEYCODE_BUTTON_START -> 3
        else -> -1
    }

    /** Hat switch or stick deflection to d-pad bits, with a
     * 0.5 deadzone. */
    fun bitsForAxes(x: Float, y: Float): Int {
        var bits = 0
        if (x < -0.5f) bits = bits or (1 shl 6)
        if (x > 0.5f) bits = bits or (1 shl 7)
        if (y < -0.5f) bits = bits or (1 shl 4)
        if (y > 0.5f) bits = bits or (1 shl 5)
        return bits
    }
}
