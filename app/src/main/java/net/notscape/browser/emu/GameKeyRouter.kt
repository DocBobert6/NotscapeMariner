package net.notscape.browser.emu

/**
 * Routes gamepad keys and axes into the core's input mask
 * (Phase J, J6). Hat/stick state is remembered so a stick
 * returning to centre clears exactly the bits it set.
 * In analogue mode (F1, PS1) the hat still maps to D-pad
 * bits, but the sticks drive the core's analogue axes
 * through [onAnalog] instead of the D-pad.
 */
class GameKeyRouter(
    private val setBit: (Int, Boolean) -> Unit,
    private val onActivity: () -> Unit,
    private val onTick: () -> Unit
) {
    private var axesBits = 0

    /** PS1 analogue mode (F1): sticks drive the core's
     * analogue axes through [onAnalog]; only the hat
     * still maps to D-pad bits. Off (default) keeps the
     * old behaviour - hat and stick both map to the
     * D-pad - which is the Digital fallback. */
    var analogMode: Boolean = false
    var onAnalog: ((Int, Float, Float) -> Unit)? = null

    fun onKey(code: Int, down: Boolean): Boolean {
        val bit = GamepadMap.bitForKeyCode(code)
        if (bit < 0) return false
        setBit(bit, down)
        onActivity()
        if (down) onTick()
        return true
    }

    fun onAxes(
        hatX: Float, hatY: Float,
        stickX: Float, stickY: Float,
        stickRX: Float = 0f, stickRY: Float = 0f
    ) {
        if (analogMode) {
            val bits = GamepadMap.bitsForAxes(hatX, hatY)
            if (bits != axesBits) {
                val changed = bits xor axesBits
                axesBits = bits
                for (bit in 4..7) {
                    if (changed and (1 shl bit) != 0) {
                        setBit(bit, bits and (1 shl bit) != 0)
                    }
                }
            }
            onAnalog?.invoke(0, stickX, stickY)
            onAnalog?.invoke(1, stickRX, stickRY)
            if (bits != 0) {
                onActivity()
                onTick()
            } else if (stickX != 0f || stickY != 0f ||
                stickRX != 0f || stickRY != 0f
            ) {
                onActivity()
            }
            return
        }
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
