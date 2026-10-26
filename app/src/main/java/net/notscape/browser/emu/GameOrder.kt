package net.notscape.browser.emu

import net.notscape.browser.data.AppStore
import org.json.JSONArray

/**
 * Saved display order (beta.6): the Games home is
 * split into handhelds and home consoles, each in
 * release order by default, and games inside a
 * console page are alphabetical by default. Both
 * are long-press-drag reorderable; custom orders
 * persist in settings. A console added later (PS1)
 * must join its group list here as well as
 * GameLayout.CONSOLES.
 */
object GameOrder {
    val HANDHELDS = listOf("gb", "gg", "gbc", "gba", "ds")
    val HOME = listOf("nes", "sms", "genesis", "snes", "ps1")

    private const val HANDHELD_KEY = "gameConsoleOrderHandheld"
    private const val HOME_KEY = "gameConsoleOrderHome"

    fun consoleOrderKey(group: String): String =
        if (group == "handheld") HANDHELD_KEY else HOME_KEY

    fun gameOrderKey(console: String): String =
        "gameOrder:$console"

    fun idsFromJson(raw: String): List<String> =
        runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())

    fun idsToJson(ids: List<String>): String {
        val arr = JSONArray()
        for (id in ids) arr.put(id)
        return arr.toString()
    }

    /** Saved console order for a group, with any
     * group member missing from the save appended
     * in default order and unknown ids dropped. */
    fun orderedConsoles(
        lookup: (String, String) -> String,
        group: String
    ): List<String> {
        val defaults = if (group == "handheld") HANDHELDS else HOME
        val saved = idsFromJson(lookup(consoleOrderKey(group), ""))
        if (saved.isEmpty()) return defaults
        val known = saved.filter { it in defaults }
        return known + defaults.filter { it !in known }
    }

    fun saveConsoles(
        store: AppStore,
        group: String,
        order: List<String>
    ) {
        store.putSetting(consoleOrderKey(group), idsToJson(order))
    }

    /** Saved game order for a console: the saved ids
     * first (only those still present), then the
     * rest alphabetical. No save means fully
     * alphabetical. */
    fun orderedGames(
        lookup: (String, String) -> String,
        console: String,
        games: List<GameEntry>
    ): List<GameEntry> {
        val saved = idsFromJson(lookup(gameOrderKey(console), ""))
        if (saved.isEmpty()) {
            return games.sortedBy { it.title.lowercase() }
        }
        val byId = games.associateBy { it.id }
        val savedSet = saved.toSet()
        val head = saved.mapNotNull { byId[it] }
        val rest = games.filter { it.id !in savedSet }
            .sortedBy { it.title.lowercase() }
        return head + rest
    }

    fun saveGames(
        store: AppStore,
        console: String,
        order: List<String>
    ) {
        store.putSetting(gameOrderKey(console), idsToJson(order))
    }

    /** Moves [dragId] to [targetId]'s slot (after it
     * when [after] is true), everything else keeping
     * its relative order. */
    fun moveId(
        order: List<String>,
        dragId: String,
        targetId: String,
        after: Boolean
    ): List<String> {
        if (dragId == targetId) return order
        val list = order.toMutableList()
        if (!list.remove(dragId)) return order
        val at = list.indexOf(targetId)
        if (at < 0) {
            list.add(dragId)
        } else {
            list.add(if (after) at + 1 else at, dragId)
        }
        return list
    }
}
