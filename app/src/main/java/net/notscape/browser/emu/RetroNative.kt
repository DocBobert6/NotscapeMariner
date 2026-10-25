package net.notscape.browser.emu

import android.view.Surface

/**
 * JNI bridge to the libretro frontend shim (Phase J):
 * libnotscape_retro hosts a core loaded by path, presents
 * through EGL/GLES3, and plays audio through oboe.
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
        saveDir: String,
        options: Array<String>
    ): String?

    external fun nativeSetSurface(surface: Surface?)

    /** Portrait top inset in pixels (J7): how far
     * below the top edge the picture's top edge
     * sits in portrait; ignored in landscape. */
    external fun nativeSetTopInset(px: Int)
    external fun nativeStart()
    external fun nativeStop()

    /** The joypad bitmask (libretro device ids): B=0,
     * Y=1, Select=2, Start=3, Up=4, Down=5, Left=6,
     * Right=7, A=8, X=9, L=10, R=11. */
    external fun nativeSaveState(path: String): Boolean
    external fun nativeLoadState(path: String): Boolean
    external fun nativeFrameGrab(): ByteArray?

    external fun nativeSetInput(mask: Int)

    /** Analogue sticks (beta.17, PS1): int16 axes,
     * -32767..32767, negative Y = up, in left-X,
     * left-Y, right-X, right-Y order. */
    external fun nativeSetAnalog(lx: Int, ly: Int, rx: Int, ry: Int)

    /** Controller device for port 0 (beta.17, PS1):
     * 1 = joypad, 261 = DualShock (see AnalogInput).
     * A no-op when the core lacks the entry point. */
    external fun nativeSetControllerDevice(device: Int)

    external fun nativeDestroy()

    /** Fresh game frames the presenter has consumed
     * since the session started (the FPS counter
     * reads this twice a second and diffs it). */
    external fun nativeGetPresentedFrames(): Long

    /** Shader pass (J8): on/off plus this console's
     * preset values. Read at session start. */
    external fun nativeSetShader(
        on: Boolean,
        scan: Float,
        grid: Float,
        blur: Float
    )

    /** LCD pass (L1, GB/GBC/GBA): on/off plus the
     * sine-grid strengths. Read at session start. */
    external fun nativeSetLcdShader(
        on: Boolean,
        brightScan: Float,
        brightLcd: Float
    )

    /** Per-console color profile: saturation, white
     * point warmth (-100 cool .. 100 warm), gamma
     * lift (>1 brightens), brightness, black level,
     * and the Genesis DAC range fix. Read at session
     * start; every shader path grades with it. */
    external fun nativeSetColorProfile(
        saturation: Float,
        warm: Float,
        gamma: Float,
        bright: Float,
        black: Float,
        segaFix: Boolean
    )

    /** CRT chain (L2, NES/SNES/Genesis): on/off.
     * Read at session start; the chain falls back
     * to the legacy pass if it cannot run. */
    external fun nativeSetCrtShader(on: Boolean)

    /** Display curvature (beta.4): mesh pull-in
     * factor, 0 = the flat quad. Read at session
     * start; applies whatever the shader toggle is. */
    external fun nativeSetCurve(k: Float)

    /** Rounded corners (beta.5): corner-arc
     * strength, 0 = square corners. Same mesh
     * path as the curve; read at session start. */
    external fun nativeSetRound(strength: Float)

    /** Pause menu (J9): idle the emu loop (audio
     * silences itself). */
    external fun nativeSetPaused(paused: Boolean)

    /** Fast-forward (J9): run-loop speed multiple
     * (1 = normal, 4 = fast-forward). */
    external fun nativeSetSpeed(mul: Int)

    /** Integer scaling (J9): snap the presenter's
     * fit scale down to a whole number. */
    external fun nativeSetIntegerScale(on: Boolean)

    /** Reset the running game (J9 pause menu). */
    external fun nativeReset()

    /** PS1 multi-disc (N3): disc count, current index,
     * label (null when the core gives none), and the
     * atomic eject -> set -> uneject swap. */
    external fun nativeDiskGetCount(): Int
    external fun nativeDiskGetIndex(): Int
    external fun nativeDiskGetLabel(index: Int): String?
    external fun nativeDiskSwap(index: Int): Boolean
}
