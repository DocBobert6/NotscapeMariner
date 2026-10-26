package net.notscape.browser.emu

import net.notscape.browser.data.AppStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Start-page shortcuts (beta.7): opt-in pins for the
 * four library sections, the eight consoles, and
 * individual games. Stored as one JSON object under
 * "startShortcuts": {"sections":[…], "consoles":
 * […], "games":[…]}. Reads tolerate garbage (bad
 * JSON, unknown ids) by dropping what doesn't
 * parse - a corrupt pin list hides pins, never
 * crashes the start page.
 */
object StartShortcuts {
    const val KEY = "startShortcuts"

    data class Pins(
        val sections: List<String> = emptyList(),
        val consoles: List<String> = emptyList(),
        val games: List<String> = emptyList()
    )

    fun sectionIds(): List<String> =
        listOf("playlists", "library", "bookshelf", "games")

    fun sectionLabel(id: String): String = when (id) {
        "playlists" -> "Playlists"
        "library" -> "Offline library"
        "bookshelf" -> "Bookshelf"
        "games" -> "Games"
        else -> id
    }

    fun consoleLabel(console: String): String = when (console) {
        "gb" -> "Game Boy"
        "gbc" -> "Game Boy Color"
        "gba" -> "Game Boy Advance"
        "nes" -> "Nintendo Entertainment System"
        "snes" -> "Super Nintendo Entertainment System"
        "genesis" -> "Genesis"
        "sms" -> "Master System"
        "gg" -> "Game Gear"
        "ps1" -> "PlayStation"
        else -> console
    }

    fun load(lookup: (String, String) -> String): Pins {
        val raw = lookup(KEY, "")
        if (raw.isBlank()) return Pins()
        return runCatching {
            val obj = JSONObject(raw)
            Pins(
                sections = strings(obj, "sections")
                    .filter { it in sectionIds() },
                consoles = strings(obj, "consoles")
                    .filter { it in GameLayout.CONSOLES },
                games = strings(obj, "games")
            )
        }.getOrDefault(Pins())
    }

    private fun strings(obj: JSONObject, key: String): List<String> {
        val arr = obj.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).mapNotNull {
            arr.optString(it).takeIf { s -> s.isNotBlank() }
        }
    }

    fun save(store: AppStore, pins: Pins) {
        val obj = JSONObject()
        obj.put("sections", JSONArray(pins.sections))
        obj.put("consoles", JSONArray(pins.consoles))
        obj.put("games", JSONArray(pins.games))
        store.putSetting(KEY, obj.toString())
    }

    private fun toggle(list: List<String>, id: String): List<String> =
        if (id in list) list - id else list + id

    fun toggleSection(store: AppStore, id: String) {
        val pins = load(store::setting)
        save(store, pins.copy(sections = toggle(pins.sections, id)))
    }

    fun toggleConsole(store: AppStore, console: String) {
        val pins = load(store::setting)
        save(store, pins.copy(consoles = toggle(pins.consoles, console)))
    }

    fun toggleGame(store: AppStore, gameId: String) {
        val pins = load(store::setting)
        save(store, pins.copy(games = toggle(pins.games, gameId)))
    }

    /** A removed game's pin dies with it. */
    fun pruneGame(store: AppStore, gameId: String) {
        val pins = load(store::setting)
        if (gameId in pins.games) {
            save(store, pins.copy(games = pins.games - gameId))
        }
    }
}
