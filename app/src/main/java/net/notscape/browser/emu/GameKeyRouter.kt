package net.notscape.browser.emu

/**
 * Routes gamepad keys and axes into the core's input mask
 * (Phase J, J6). Hat/stick state is remembered so a stick
 * returning to centre clears exactly the bits it set.
 */
class GameKeyRouter(
    private val setBit: (Int, Boolean) -> Unit,
    private val onActivity: () -> Unit,
    private val onTick: () -> Unit
) {
    private var axesBits = 0

    fun onKey(code: Int, down: Boolean): Boolean {
        val bit = GamepadMap.bitForKeyCode(code)
        if (bit < 0) return false
        setBit(bit, down)
        onActivity()
        if (down) onTick()
        return true
    }

    fun onAxes(hatX: Float, hatY: Float, stickX: Float, stickY: Float) {
        val bits = GamepadMap.bitsForAxes(hatX, hatY) or
            GamepadMap.bitsForAxes(stickX, stickY)
        if (bits == axesBits) return
        val changed = bits xor axesBits
        axesBits = bits
        for (bit in 4..7) {
            if (changed and (1 shl bit) != 0) {
                setBit(bit, bits and (1 shl bit) != 0)
            }
        }
        if (bits != 0) {
            onActivity()
            onTick()
        }
    }
}
