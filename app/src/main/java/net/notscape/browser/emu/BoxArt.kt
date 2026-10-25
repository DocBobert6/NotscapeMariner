package net.notscape.browser.emu

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Box art from the libretro thumbnail server (Phase J
 * amendment, Robert 2026-10-24): the public distribution
 * RetroArch itself uses - per-system folders, box art in
 * Named_Boxarts and title screens in Named_Titles, PNGs
 * named by the No-Intro game name. Fetched lazily, one
 * game at a time, cached in app storage beside nothing
 * else; never bulk-mirrored. A ROM whose name doesn't
 * match the No-Intro form simply gets no art (the tile
 * falls back to title art, then a styled placeholder) -
 * renaming the library entry to the proper title is the
 * fix, and J2's rename is right there.
 */
object BoxArt {
    private const val SERVER = "https://thumbnails.libretro.com"
    private const val MAX_ART_BYTES = 4 * 1024 * 1024

    /** The server's per-system folder names. */
    fun folderFor(system: String): String? = when (system) {
        "gb" -> "Nintendo - Game Boy"
        "gbc" -> "Nintendo - Game Boy Color"
        "gba" -> "Nintendo - Game Boy Advance"
        "nes" -> "Nintendo - Nintendo Entertainment System"
        "snes" -> "Nintendo - Super Nintendo Entertainment System"
        "genesis" -> "Sega - Mega Drive - Genesis"
        "sms" -> "Sega - Master System - Mark III"
        "gg" -> "Sega - Game Gear"
        else -> null
    }

    /** The server's file-name form of a game title: the
     * libretro convention replaces filename-hostile
     * characters with underscores. */
    fun serverName(title: String): String =
        title.replace(Regex("[&*/:`<>?\\\\|\"]"), "_").trim()

    fun artDir(context: Context): File =
        File(context.filesDir, "boxart").apply { mkdirs() }

    fun artFile(context: Context, game: GameEntry): File =
        File(artDir(context), game.id + "-box.png")

    /** A user-chosen cover (beta.7): shown first, and
     * never touched by server fetches, which write
     * -box.png only. */
    fun customFile(context: Context, game: GameEntry): File =
        File(artDir(context), game.id + "-custom.png")

    fun titleFile(context: Context, game: GameEntry): File =
        File(artDir(context), game.id + "-title.png")

    private fun urlFor(folder: String, kind: String, name: String): String {
        fun enc(s: String): String =
            URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        return "$SERVER/${enc(folder)}/$kind/${enc(name)}.png"
    }

    /** Downloads one image; true when it landed and is a
     * plausible PNG. Blocking - call off the main thread. */
    private fun download(url: String, dest: File): Boolean {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        return runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "NotscapeMariner")
            if (conn.responseCode != 200) return false
            var total = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_ART_BYTES) {
                            throw IllegalStateException("art too large")
                        }
                        out.write(buf, 0, n)
                    }
                }
            }
            val bytes = tmp.readBytes()
            val isPng = bytes.size > 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte()
            if (!isPng) {
                tmp.delete()
                return false
            }
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
            true
        }.getOrElse {
            runCatching { tmp.delete() }
            false
        }
    }

    /**
     * Fetches whatever art exists for [game]: box art
     * first, then the title screen. Returns which kinds
     * are now on disk. Blocking - call off the main
     * thread. Already-cached files are kept, not
     * re-fetched, unless [force].
     */
    fun fetch(
        context: Context,
        game: GameEntry,
        force: Boolean = false
    ): Pair<Boolean, Boolean> {
        val folder = folderFor(game.system) ?: return false to false
        val name = serverName(game.title)
        val box = artFile(context, game)
        val title = titleFile(context, game)
        val hasBox = if (force || !box.exists()) {
            download(urlFor(folder, "Named_Boxarts", name), box)
        } else {
            true
        }
        val hasTitle = if (force || !title.exists()) {
            download(urlFor(folder, "Named_Titles", name), title)
        } else {
            true
        }
        return (hasBox || box.exists()) to (hasTitle || title.exists())
    }
}
