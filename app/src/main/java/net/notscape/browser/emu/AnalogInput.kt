package net.notscape.browser.emu

/**
 * Touch-stick to libretro analogue conversion (Phase N,
 * beta.17). The stick reports a -1..1 vector per axis;
 * the core wants int16 (-32767..32767, negative Y = up).
 * A small per-axis deadzone keeps a centred stick
 * from drifting, and the range beyond it rescales to
 * full travel so the stick still reaches the corners.
 */
object AnalogInput {
    /** Below this magnitude the stick reads centred. */
    const val DEADZONE = 0.15f

    /** libretro device ids (libretro.h): JOYPAD = 1,
     * ANALOG = 5, TYPE_SHIFT = 8; DualShock is
     * SUBCLASS(ANALOG, 0) = (1 << 8) | 5. */
    const val DEVICE_JOYPAD = 1
    const val DEVICE_DUALSHOCK = 261

    fun axis(v: Float): Int {
        val clamped = v.coerceIn(-1f, 1f)
        val mag = kotlin.math.abs(clamped)
        if (mag < DEADZONE) return 0
        val scaled = (mag - DEADZONE) / (1f - DEADZONE)
        val out = (scaled * 32767f).toInt()
        return if (clamped < 0) -out else out
    }
}
