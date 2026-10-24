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
    private const val MGBA_APK_ENTRY =
        "lib/arm64-v8a/libmgba_libretro_android.so"

    /** Absolute path of the mGBA core, copied out of the
     * APK if needed; null if this build has no core. */
    fun mgbaCorePath(context: Context): String? {
        val dir = File(context.filesDir, "cores").apply { mkdirs() }
        val out = File(dir, "mgba_libretro_android.so")
        return runCatching {
            val zip = ZipFile(context.applicationInfo.sourceDir)
            val entry = zip.getEntry(MGBA_APK_ENTRY) ?: return null
            if (!out.exists() || out.length() != entry.size) {
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            }
            out.absolutePath
        }.getOrNull()
    }

    fun romDir(context: Context): File =
        File(context.filesDir, "roms").apply { mkdirs() }

    fun saveDir(context: Context): File =
        File(context.filesDir, "saves").apply { mkdirs() }

    fun systemDir(context: Context): File =
        File(context.filesDir, "system").apply { mkdirs() }
}
