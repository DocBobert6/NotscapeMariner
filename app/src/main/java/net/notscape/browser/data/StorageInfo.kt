package net.notscape.browser.data

import android.content.Context
import java.io.File

/**
 * On-device storage accounting and housekeeping (0.0.996):
 * how much room the app's own areas take, a safe cache
 * clear, and a cold-start tidy of leftovers (interrupted
 * font imports, uninstalled update downloads, voice packs
 * that never finished installing). Everything here is
 * local file work - call it off the main thread.
 */
object StorageInfo {

    data class Area(val label: String, val bytes: Long)

    fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { f ->
            if (f.isFile) {
                total += runCatching { f.length() }.getOrDefault(0L)
            }
        }
        return total
    }

    /** The areas worth showing, largest first. Areas that
     * take no room are omitted, except the cache, which is
     * always listed because it can always be cleared. */
    fun measure(context: Context): List<Area> {
        val files = context.filesDir
        val dbDir = runCatching {
            context.getDatabasePath("notscape.db").parentFile
        }.getOrNull()
        val raw = listOf(
            Area("Books", dirSize(File(files, "books"))),
            Area("Read-aloud voices", dirSize(File(files, "tts"))),
            Area("Filter lists", dirSize(File(files, "filters"))),
            Area("Fonts", dirSize(File(files, "fonts"))),
            Area("Wallpaper", dirSize(File(files, "wallpaper"))),
            Area("Site icons", dirSize(File(files, "favicons"))),
            Area(
                "Library database",
                dbDir?.let { dirSize(it) } ?: 0L
            ),
            Area("Cache", dirSize(context.cacheDir))
        )
        return raw.filter { it.bytes > 0 || it.label == "Cache" }
            .sortedByDescending { it.bytes }
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", kb)
        }
        val mb = kb / 1024.0
        if (mb < 1024) {
            return String.format(java.util.Locale.US, "%.1f MB", mb)
        }
        return String.format(
            java.util.Locale.US, "%.2f GB", mb / 1024.0
        )
    }

    /** Empties the app cache (and code cache); returns the
     * bytes freed. The cache holds temporary copies only -
     * clearing it is always safe. */
    fun clearCache(context: Context): Long {
        val before = dirSize(context.cacheDir) +
            dirSize(context.codeCacheDir)
        listOf(context.cacheDir, context.codeCacheDir)
            .forEach { dir ->
                runCatching {
                    dir.listFiles()?.forEach { child ->
                        if (child.isDirectory) {
                            child.deleteRecursively()
                        } else {
                            child.delete()
                        }
                    }
                }
            }
        val after = dirSize(context.cacheDir) +
            dirSize(context.codeCacheDir)
        return (before - after).coerceAtLeast(0L)
    }

    /** Cold-start housekeeping: staging leftovers that can
     * never be used again. Runs once the store is ready;
     * nothing here touches live data. */
    fun tidy(context: Context) {
        runCatching {
            // Interrupted font imports leave their staging
            // file behind in fonts/.
            File(context.filesDir, "fonts").listFiles()
                ?.forEach { f ->
                    if (f.name.endsWith(".tmp")) f.delete()
                }
        }
        runCatching {
            // A downloaded update that was never installed
            // is stale by the next cold start; the update
            // check will offer it again if it is still new.
            File(context.cacheDir, "updates").deleteRecursively()
        }
        runCatching {
            // Voice packs that never finished installing
            // (no completion marker) are re-downloaded on
            // demand; don't let the partial files sit.
            val root = File(context.filesDir, "tts")
            root.listFiles()?.forEach { child ->
                if (child.isDirectory &&
                    !File(child, ".tts-complete").exists()
                ) {
                    child.deleteRecursively()
                } else if (child.isFile &&
                    (child.name.endsWith(".tmp") ||
                        child.name.endsWith(".part"))
                ) {
                    child.delete()
                }
            }
        }
    }
}
