package net.notscape.browser.emu

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Save states (Phase J, J5): 4 numbered slots per game plus
 * one auto-state written when leaving a game, each with a
 * frame-grab thumbnail. State files live under
 * filesDir/saves/<gameId>/; the full save manager UI lands
 * in J7's console pages, and the in-game States dialog
 * covers the same files.
 */
object SaveStates {
    const val SLOT_COUNT = 4
    const val AUTO_SLOT = -2
    const val NO_SLOT = -1

    fun gameIdFor(libraryId: String?, romPath: String): String =
        libraryId ?: (
            "rom-" + File(romPath).nameWithoutExtension
                .ifBlank { "game" }
            )

    fun dirFor(filesDir: File, gameId: String): File =
        File(File(filesDir, "saves"), gameId)

    fun stateFileName(slot: Int): String =
        if (slot == AUTO_SLOT) "auto.state" else "slot$slot.state"

    fun thumbFileName(slot: Int): String =
        if (slot == AUTO_SLOT) "auto.png" else "slot$slot.png"

    fun stateFile(context: Context, gameId: String, slot: Int): File =
        File(dirFor(context.filesDir, gameId), stateFileName(slot))

    fun thumbFile(context: Context, gameId: String, slot: Int): File =
        File(dirFor(context.filesDir, gameId), thumbFileName(slot))

    data class SlotInfo(
        val slot: Int,
        val exists: Boolean,
        val modifiedAt: Long,
        val hasThumb: Boolean
    )

    fun slotInfo(context: Context, gameId: String, slot: Int): SlotInfo {
        val f = stateFile(context, gameId, slot)
        return SlotInfo(
            slot,
            f.exists(),
            if (f.exists()) f.lastModified() else 0L,
            thumbFile(context, gameId, slot).exists()
        )
    }

    fun autoExists(context: Context, gameId: String): Boolean =
        stateFile(context, gameId, AUTO_SLOT).exists()

    /** Saves to [slot] via a temp file + rename, so a failed
     * save never clobbers the previous state. */
    fun save(context: Context, gameId: String, slot: Int): Boolean {
        val dir = dirFor(context.filesDir, gameId)
        if (!dir.exists()) dir.mkdirs()
        val tmp = File(dir, stateFileName(slot) + ".tmp")
        if (!RetroNative.nativeSaveState(tmp.absolutePath)) {
            tmp.delete()
            return false
        }
        val dst = stateFile(context, gameId, slot)
        if (!tmp.renameTo(dst)) {
            tmp.delete()
            return false
        }
        saveThumb(context, gameId, slot)
        return true
    }

    fun load(context: Context, gameId: String, slot: Int): Boolean {
        val f = stateFile(context, gameId, slot)
        return f.exists() && RetroNative.nativeLoadState(f.absolutePath)
    }

    fun delete(context: Context, gameId: String, slot: Int) {
        stateFile(context, gameId, slot).delete()
        thumbFile(context, gameId, slot).delete()
    }

    fun autoSave(context: Context, gameId: String): Boolean =
        save(context, gameId, AUTO_SLOT)

    /** Archived auto-saves (beta.7): picking Start
     * fresh never deletes the old run's auto-state -
     * it is renamed to auto-<millis>.state (+ .png)
     * and listed alongside the current auto-state.
     * Nothing here is ever deleted automatically;
     * deletion is manual, via the save manager. */
    fun archiveFileName(millis: Long): String =
        "auto-$millis.state"

    fun archiveThumbName(millis: Long): String =
        "auto-$millis.png"

    fun isArchiveName(name: String): Boolean =
        name.startsWith("auto-") && name.endsWith(".state")

    fun archiveMillis(name: String): Long =
        name.removePrefix("auto-")
            .removeSuffix(".state").toLongOrNull() ?: 0L

    data class AutoArchive(
        val stateFile: File,
        val thumbFile: File,
        val modifiedAt: Long
    )

    fun autoArchives(
        context: Context, gameId: String
    ): List<AutoArchive> {
        val dir = dirFor(context.filesDir, gameId)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { isArchiveName(it.name) }
            ?.sortedByDescending { archiveMillis(it.name) }
            ?.map {
                AutoArchive(
                    it,
                    File(dir, it.nameWithoutExtension + ".png"),
                    it.lastModified()
                )
            } ?: emptyList()
    }

    /** Moves the current auto-state aside so a fresh
     * run starts clean without losing the old run. */
    /** Newest auto-save archives kept (audit P-m8); older
     *  archived runs are deleted when a new one lands.
     *  Manual saves are never touched. */
    const val MAX_AUTO_ARCHIVES = 5

    fun archiveAuto(context: Context, gameId: String): Boolean {
        val dir = dirFor(context.filesDir, gameId)
        val src = stateFile(context, gameId, AUTO_SLOT)
        if (!src.exists()) return false
        val stamp = System.currentTimeMillis()
        val dst = File(dir, archiveFileName(stamp))
        if (!src.renameTo(dst)) return false
        val thumb = thumbFile(context, gameId, AUTO_SLOT)
        if (thumb.exists()) {
            thumb.renameTo(File(dir, archiveThumbName(stamp)))
        }
        autoArchives(context, gameId)
            .drop(MAX_AUTO_ARCHIVES)
            .forEach { deleteArchive(it) }
        return true
    }

    fun deleteArchive(archive: AutoArchive) {
        archive.stateFile.delete()
        archive.thumbFile.delete()
    }

    private fun saveThumb(context: Context, gameId: String, slot: Int) {
        val grab = RetroNative.nativeFrameGrab() ?: return
        if (grab.size < 8) return
        val buf = ByteBuffer.wrap(grab).order(ByteOrder.LITTLE_ENDIAN)
        val w = buf.int
        val h = buf.int
        if (w <= 0 || h <= 0 || grab.size < 8 + w * h * 4) return
        val pixels = IntArray(w * h)
        var i = 8
        for (p in pixels.indices) {
            val r = grab[i].toInt() and 0xFF
            val g = grab[i + 1].toInt() and 0xFF
            val b = grab[i + 2].toInt() and 0xFF
            pixels[p] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            i += 4
        }
        val bmp = Bitmap.createBitmap(
            pixels, w, h, Bitmap.Config.ARGB_8888
        )
        runCatching {
            thumbFile(context, gameId, slot).outputStream().use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
