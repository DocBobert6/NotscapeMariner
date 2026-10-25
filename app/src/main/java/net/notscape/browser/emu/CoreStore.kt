package net.notscape.browser.emu

import android.content.Context
import java.io.File

/**
 * On-device homes for the emulator pieces (Phase J): the
 * core library (downloaded on demand from the
 * cores release and verified before loading, so
 * the shim can dlopen a plain file path), imported
 * ROMs (copies - the user's originals are theirs
 * to keep or delete), battery saves, and the core
 * system dir. No core ships with the app (M3);
 * every ROM here was imported by the user.
 */
object CoreStore {
    /** Console key -> the core library that plays it
     * (J3: the full Phase J set). */
    fun coreFileNameFor(system: String): String? = when (system) {
        "gb", "gbc", "gba" -> "mgba_libretro_android.so"
        "nes" -> "fceumm_libretro_android.so"
        "snes" -> "snes9x_libretro_android.so"
        "genesis", "sms", "gg" -> "genesis_plus_gx_libretro_android.so"
        else -> null
    }

    /** Absolute path of the core for [system]: the
     * verified download, or null when no core has
     * been downloaded for it (M3: no core ships
     * in the APK any more). */
    fun corePathFor(context: Context, system: String): String? =
        CoreDownload.downloadedPath(context, system)

    /** J1's mGBA-only entry point, kept for its caller. */
    fun mgbaCorePath(context: Context): String? =
        corePathFor(context, "gba")

    fun romDir(context: Context): File =
        File(context.filesDir, "roms").apply { mkdirs() }

    fun saveDir(context: Context): File =
        File(context.filesDir, "saves").apply { mkdirs() }

    fun systemDir(context: Context): File =
        File(context.filesDir, "system").apply { mkdirs() }
}
