package net.notscape.browser.emu

import android.content.Context
import java.io.File
import net.notscape.browser.data.AppStore
import org.json.JSONArray
import org.json.JSONObject

/** One imported game in the library (Phase J, J2). The
 * ROM itself is a copy in app storage at
 * roms/<system>/<fileName>; [title] is the display name
 * (the user's to rename), [system] the console key the
 * core + layout + box-art folder all key off. */
data class GameEntry(
    val id: String,
    val title: String,
    val system: String,
    val fileName: String,
    val addedAt: Long,
    val lastPlayedAt: Long = 0L
) {
    fun romFile(context: Context): File =
        File(File(CoreStore.romDir(context), system), fileName)

    fun saveFile(context: Context): File = File(
        CoreStore.saveDir(context),
        fileName.substringBeforeLast('.') + ".srm"
    )
}

/**
 * The games library: a JSON list in a setting (the user
 * scripts precedent - no schema change), files on disk
 * under CoreStore's dirs. Import copies; remove deletes
 * the copy, its battery save and its box art - the
 * user's original file is never touched.
 */
object GameLibrary {
    private const val SETTING_KEY = "gamesLibrary"
    const val MAX_GAMES = 500

    /** Console key -> accepted ROM extensions, in
     * display order. Single source of truth: the
     * console pages' "Plays ..." captions read this
     * map, so a caption can never drift from what
     * import actually accepts. J2 plays the Game Boy
     * family; J3 lights up the rest. */
    val SYSTEM_EXTENSIONS: Map<String, List<String>> =
        linkedMapOf(
            "gb" to listOf("gb"),
            "gbc" to listOf("gbc"),
            "gba" to listOf("gba"),
            "nes" to listOf("nes"),
            "snes" to listOf("smc", "sfc"),
            "genesis" to listOf("md", "gen"),
            "sms" to listOf("sms"),
            "gg" to listOf("gg"),
            "ps1" to listOf("cue", "m3u", "chd")
        )

    /** Accepted extensions for a console, or empty
     * for an unknown console key. */
    fun extensionsFor(system: String): List<String> =
        SYSTEM_EXTENSIONS[system] ?: emptyList()

    /** Extension -> console key. */
    fun systemForExtension(ext: String): String? {
        val lower = ext.lowercase()
        return SYSTEM_EXTENSIONS.entries
            .firstOrNull { lower in it.value }?.key
    }

    /** Systems whose core is in the app today (J3:
     * the full Phase J set). */
    fun isPlayable(system: String): Boolean =
        system in setOf(
            "gb", "gbc", "gba", "nes", "snes",
            "genesis", "sms", "gg", "ps1"
        )

    fun load(store: AppStore): List<GameEntry> {
        val raw = store.setting(SETTING_KEY, "")
        if (raw.isBlank()) return emptyList()
        return parse(raw)
    }

    fun save(store: AppStore, games: List<GameEntry>) {
        store.putSetting(SETTING_KEY, toJson(games))
    }

    fun parse(json: String): List<GameEntry> = runCatching {
        val arr = JSONArray(json)
        val out = ArrayList<GameEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += GameEntry(
                id = o.getString("id"),
                title = o.getString("title"),
                system = o.getString("system"),
                fileName = o.getString("file"),
                addedAt = o.optLong("added", 0L),
                lastPlayedAt = o.optLong("played", 0L)
            )
        }
        out
    }.getOrElse { emptyList() }

    fun toJson(games: List<GameEntry>): String {
        val arr = JSONArray()
        for (g in games) {
            arr.put(
                JSONObject()
                    .put("id", g.id)
                    .put("title", g.title)
                    .put("system", g.system)
                    .put("file", g.fileName)
                    .put("added", g.addedAt)
                    .put("played", g.lastPlayedAt)
            )
        }
        return arr.toString()
    }

    fun newId(): String =
        "g" + System.currentTimeMillis().toString(36) +
            (0..9999).random().toString(36)

    /** A file name safe for app storage, derived from the
     * picked file's display name. */
    fun safeFileName(displayName: String): String =
        displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** Registers an already-copied ROM file. */
    fun add(
        store: AppStore,
        title: String,
        system: String,
        fileName: String
    ): GameEntry {
        val entry = GameEntry(
            id = newId(),
            title = title,
            system = system,
            fileName = fileName,
            addedAt = System.currentTimeMillis()
        )
        save(store, (load(store) + entry).takeLast(MAX_GAMES))
        return entry
    }

    fun rename(store: AppStore, id: String, newTitle: String) {
        save(store, load(store).map {
            if (it.id == id) it.copy(title = newTitle) else it
        })
    }

    fun touchPlayed(store: AppStore, id: String) {
        save(store, load(store).map {
            if (it.id == id) {
                it.copy(lastPlayedAt = System.currentTimeMillis())
            } else {
                it
            }
        })
    }

    /** Favorites (J9): a JSON id set in its own
     * setting, so starring never touches the library
     * record itself. Stale ids (removed games) simply
     * never match; remove() prunes them anyway. */
    private const val FAVORITES_KEY = "gameFavorites"

    fun favoritesFromJson(raw: String): Set<String> =
        runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length())
                .mapTo(HashSet()) { arr.getString(it) }
        }.getOrDefault(emptySet())

    fun favoritesToJson(ids: Set<String>): String {
        val arr = JSONArray()
        for (id in ids) arr.put(id)
        return arr.toString()
    }

    fun favorites(store: AppStore): Set<String> =
        favoritesFromJson(store.setting(FAVORITES_KEY, ""))

    fun isFavorite(store: AppStore, id: String): Boolean =
        id in favorites(store)

    fun setFavorite(store: AppStore, id: String, fav: Boolean) {
        val cur = favorites(store).toMutableSet()
        if (fav) cur += id else cur -= id
        store.putSetting(FAVORITES_KEY, favoritesToJson(cur))
    }

    /** Removes the entry and its on-disk traces. */
    fun remove(context: Context, store: AppStore, id: String) {
        val games = load(store)
        val victim = games.firstOrNull { it.id == id } ?: return
        save(store, games.filter { it.id != id })
        if (isFavorite(store, id)) setFavorite(store, id, false)
        StartShortcuts.pruneGame(store, id)
        runCatching { victim.romFile(context).delete() }
        runCatching { victim.saveFile(context).delete() }
        runCatching { BoxArt.artFile(context, victim).delete() }
        runCatching { BoxArt.titleFile(context, victim).delete() }
        runCatching { BoxArt.customFile(context, victim).delete() }
    }

    /** The most recently played game, for the Continue
     * row - the library's own record (J2 replaces J1's
     * lastRom settings). */
    fun lastPlayed(store: AppStore): GameEntry? =
        load(store).filter { it.lastPlayedAt > 0 }
            .maxByOrNull { it.lastPlayedAt }

    /** One-time J1 -> J2 move: J1 kept a single ROM loose
     * in roms/ with lastRom settings; fold it into the
     * library (file moved under its system folder) so
     * nobody's first game vanishes in the upgrade. */
    fun migrateLegacy(context: Context, store: AppStore) {
        if (load(store).isNotEmpty()) return
        val path = store.setting("lastRomPath", "")
        if (path.isBlank()) return
        val src = File(path)
        if (!src.exists()) return
        val system = systemForExtension(src.extension) ?: "gb"
        val dir = File(CoreStore.romDir(context), system)
            .apply { mkdirs() }
        val dest = File(dir, src.name)
        val moved = runCatching {
            if (src.absolutePath == dest.absolutePath) {
                true
            } else if (src.renameTo(dest)) {
                true
            } else {
                src.copyTo(dest, overwrite = true)
                src.delete()
                true
            }
        }.getOrDefault(false)
        if (!moved && !dest.exists()) return
        val name = store.setting("lastRomName", "")
            .ifBlank { dest.name }
        val entry = add(
            store,
            title = name.substringBeforeLast('.'),
            system = system,
            fileName = dest.name
        )
        save(store, load(store).map {
            if (it.id == entry.id) {
                it.copy(lastPlayedAt = System.currentTimeMillis())
            } else {
                it
            }
        })
        store.putSetting("lastRomPath", "")
        store.putSetting("lastRomName", "")
    }
}
