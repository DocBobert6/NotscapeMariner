package net.notscape.browser.emu

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Zip ROM import (beta.7): a .zip picked as a ROM, or
 * found during a folder import, is opened in place -
 * the zip itself is never kept, only the ROMs inside
 * it. Entries match by extension against the
 * supported systems; directory entries and nested
 * zips are skipped, never recursed into. Entry paths
 * cannot escape: only the bare file name is ever
 * used, so a hostile "../" entry lands nowhere but
 * the console's ROM folder. Sizes are capped (a
 * single entry over 64MB, or 256MB total, is
 * skipped) - these consoles' ROMs top out at 32MB.
 * DS is the exception: DS cards run to 256MB, so a
 * .nds entry may reach 256MB (the total cap still
 * binds, so a zip holds at most one max-size DS
 * ROM). Direct .nds picks stream and never hit
 * either cap.
 */
object ZipRomImport {
    const val MAX_ENTRY_BYTES = 64 * 1024 * 1024
    const val MAX_DS_ENTRY_BYTES = 256 * 1024 * 1024
    const val MAX_TOTAL_BYTES = 256 * 1024 * 1024

    /** Per-entry cap for one system's ROMs: DS
     * cards run to 256MB, everything else to 64MB. */
    fun entryCapBytes(system: String): Long =
        if (system == "ds") {
            MAX_DS_ENTRY_BYTES.toLong()
        } else {
            MAX_ENTRY_BYTES.toLong()
        }

    data class Result(
        val matched: Int,
        val skipped: Int,
        val firstOtherSystem: String?
    )

    /**
     * Streams [input] as a zip, calling [onEntry] with
     * each matching ROM's storage file name, its
     * original entry name, system and bytes. The
     * title must come from the original name (like
     * the single-file path, which titles from the
     * picked display name): deriving it from the
     * sanitized file name underscores every space
     * and breaks cover-art lookup, which needs the
     * No-Intro form with spaces.
     * When [targetSystem] is set, only that system's
     * ROMs match (single-file import); other systems'
     * ROMs are noted in [Result.firstOtherSystem] so
     * the caller can point at the right console page.
     * With no target, every supported system matches
     * (folder import).
     */
    fun forEachEntry(
        input: InputStream,
        targetSystem: String?,
        onEntry: (
            fileName: String,
            displayName: String,
            system: String,
            bytes: ByteArray
        ) -> Unit
    ): Result {
        var matched = 0
        var skipped = 0
        var firstOther: String? = null
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val rawName = entry.name
                if (rawName.endsWith("/")) continue
                val name = rawName.substringAfterLast('/')
                if (name.isBlank()) continue
                if (name.endsWith(".zip", ignoreCase = true)) {
                    skipped++
                    continue
                }
                val system = GameLibrary.systemForExtension(
                    name.substringAfterLast('.', "")
                )
                if (system == null) {
                    skipped++
                    continue
                }
                if (targetSystem != null && system != targetSystem) {
                    if (firstOther == null) firstOther = system
                    skipped++
                    continue
                }
                val bytes = ByteArrayOutputStream()
                val buf = ByteArray(32 * 1024)
                val entryCap = entryCapBytes(system)
                var over = false
                while (true) {
                    val n = zip.read(buf)
                    if (n < 0) break
                    total += n
                    if (bytes.size() + n > entryCap ||
                        total > MAX_TOTAL_BYTES
                    ) {
                        over = true
                        break
                    }
                    bytes.write(buf, 0, n)
                }
                if (over) {
                    skipped++
                    continue
                }
                onEntry(
                    GameLibrary.safeFileName(name),
                    name,
                    system,
                    bytes.toByteArray()
                )
                matched++
            }
        }
        return Result(matched, skipped, firstOther)
    }

    /**
     * Second pass over a zip that already yielded
     * .cue/.m3u entries (beta.17): streams each disc
     * file the track lists name straight to [destDir]
     * under the reference's exact spelling, so a .cue
     * that names "Game.BIN" finds it even when the
     * zip entry differs in case. Disc images are
     * streamed, never held in memory: a PS1 .bin runs
     * several hundred megabytes, far past the 64MB
     * entry cap above, so this pass has its own
     * 1024MB per-file guard and no total cap. Files
     * already present are overwritten (re-import
     * heals a lone .cue entry). Returns the names
     * written. [wanted] maps a lowercased bare name
     * to the reference's exact spelling.
     */
    fun streamBinSiblings(
        input: InputStream,
        wanted: Map<String, String>,
        destDir: File
    ): List<String> {
        if (wanted.isEmpty()) return emptyList()
        destDir.mkdirs()
        val written = mutableListOf<String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val rawName = entry.name
                if (rawName.endsWith("/")) continue
                val bare = rawName.substringAfterLast('/')
                if (bare.isBlank()) continue
                val target = wanted[bare.lowercase()] ?: continue
                val dest = File(destDir, target)
                var total = 0L
                var over = false
                dest.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = zip.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BIN_BYTES) {
                            over = true
                            break
                        }
                        out.write(buf, 0, n)
                    }
                }
                if (over) {
                    dest.delete()
                    continue
                }
                written.add(target)
            }
        }
        return written
    }

    private const val MAX_BIN_BYTES = 1024L * 1024L * 1024L
}
