package net.notscape.browser.emu

import android.view.Surface

/**
 * JNI bridge to the libretro frontend shim (Phase J):
 * libnotscape_retro hosts a core loaded by path, presents
 * through EGL/GLES2, and plays audio through oboe.
 */
object RetroNative {
    init {
        System.loadLibrary("notscape_retro")
    }

    /** Loads the core and the ROM. Null on success; a
     * human-readable reason on failure. */
    external fun nativeCreate(
        corePath: String,
        romPath: String,
        systemDir: String,
        saveDir: String
    ): String?

    external fun nativeSetSurface(surface: Surface?)
    external fun nativeStart()
    external fun nativeStop()

    /** The joypad bitmask (libretro device ids): B=0,
     * Y=1, Select=2, Start=3, Up=4, Down=5, Left=6,
     * Right=7, A=8, X=9, L=10, R=11. */
    external fun nativeSetInput(mask: Int)
    external fun nativeDestroy()
}
