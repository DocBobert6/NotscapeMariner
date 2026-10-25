package net.notscape.browser.emu

import android.content.Context
import java.io.File
import java.util.zip.ZipFile

/**
 * On-device homes for the emulator pieces (Phase J): the
 * core library (copied out of the APK on first use, so
 * the shim can dlopen a plain file path whatever the
 * install's native-lib extraction mode), imported ROMs
 * (copies - the user's originals are theirs to keep or
 * delete), battery saves, and the core system dir.
 * Nothing ships with the app but the cores themselves;
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

    /** Absolute path of the core for [system]: a
     * verified download wins over the APK copy
     * (M2); the APK copy is the fallback, copied
     * out of the APK if needed; null if this build
     * has no core for it. */
    fun corePathFor(context: Context, system: String): String? {
        CoreDownload.downloadedPath(context, system)
            ?.let { return it }
        val fileName = coreFileNameFor(system) ?: return null
        val dir = File(context.filesDir, "cores").apply { mkdirs() }
        val out = File(dir, fileName)
        return runCatching {
            val zip = ZipFile(context.applicationInfo.sourceDir)
            val entry = zip.getEntry("lib/arm64-v8a/lib$fileName")
                ?: return null
            if (!out.exists() || out.length() != entry.size) {
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            }
            out.absolutePath
        }.getOrNull()
    }

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
