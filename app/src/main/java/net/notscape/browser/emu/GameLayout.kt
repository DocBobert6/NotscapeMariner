package net.notscape.browser.emu

import net.notscape.browser.data.AppStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * One on-screen control's place in a console layout
 * (Phase J, J2): centre point as a fraction of the game
 * surface (so a layout survives rotation and screen
 * sizes) plus its size in dp. [bit] is the libretro
 * joypad id the control drives, or -1 for pure chrome.
 * [pill] renders the wide Select/Start shape instead of
 * a circle.
 */
data class ControlSpec(
    val id: String,
    val label: String,
    val bit: Int,
    val cx: Float,
    val cy: Float,
    val sizeDp: Int,
    val pill: Boolean = false
)

/**
 * Per-console touch layouts, persisted as JSON in a
 * setting (no schema change) and edited by the layout
 * editor. J2 ships the Game Boy family layout; J4 gives
 * every console its own on the same machinery.
 */
object GameLayout {
    const val MIN_SIZE_DP = 36
    const val MAX_SIZE_DP = 120

    private fun key(console: String) = "gameLayout:$console"

    /** The shipping Game Boy arrangement. Refined at
     * Robert's word (2026-10-24): J1's clusters hugged
     * the corners - these sit ~30dp in from the side
     * edges and ~34dp up off the bottom gesture strip,
     * inside the natural thumb arcs, and the buttons are
     * a touch larger (fractions of a 900x500 reference
     * canvas; the editor lets him tune from here). */
    fun defaults(console: String): List<ControlSpec> = listOf(
        ControlSpec("up", "▲", 4, 0.137f, 0.620f, 60),
        ControlSpec("down", "▼", 5, 0.137f, 0.872f, 60),
        ControlSpec("left", "◀", 6, 0.067f, 0.746f, 60),
        ControlSpec("right", "▶", 7, 0.240f, 0.746f, 60),
        ControlSpec("a", "A", 8, 0.929f, 0.628f, 68),
        ControlSpec("b", "B", 0, 0.798f, 0.864f, 68),
        ControlSpec("select", "SELECT", 2, 0.452f, 0.906f, 74, pill = true),
        ControlSpec("start", "START", 3, 0.548f, 0.906f, 74, pill = true)
    )

    fun load(store: AppStore, console: String): List<ControlSpec> {
        val raw = store.setting(key(console), "")
        if (raw.isBlank()) return defaults(console)
        return parse(raw) ?: defaults(console)
    }

    fun save(store: AppStore, console: String, specs: List<ControlSpec>) {
        store.putSetting(key(console), toJson(specs))
    }

    fun reset(store: AppStore, console: String) {
        store.putSetting(key(console), "")
    }

    fun parse(json: String): List<ControlSpec>? = runCatching {
        val arr = JSONArray(json)
        val out = ArrayList<ControlSpec>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += ControlSpec(
                id = o.getString("id"),
                label = o.getString("label"),
                bit = o.getInt("bit"),
                cx = o.getDouble("cx").toFloat().coerceIn(0f, 1f),
                cy = o.getDouble("cy").toFloat().coerceIn(0f, 1f),
                sizeDp = o.getInt("size")
                    .coerceIn(MIN_SIZE_DP, MAX_SIZE_DP),
                pill = o.optBoolean("pill", false)
            )
        }
        out.ifEmpty { null }
    }.getOrNull()

    fun toJson(specs: List<ControlSpec>): String {
        val arr = JSONArray()
        for (s in specs) {
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("label", s.label)
                    .put("bit", s.bit)
                    .put("cx", s.cx.toDouble())
                    .put("cy", s.cy.toDouble())
                    .put("size", s.sizeDp)
                    .put("pill", s.pill)
            )
        }
        return arr.toString()
    }

    /** Snap a centre point (in dp on the editor canvas) to
     * the grid when snapping is on: Robert's 8dp grid. */
    fun snap(dp: Float, gridDp: Int, on: Boolean): Float =
        if (on && gridDp > 0) {
            (dp / gridDp).let { Math.round(it) * gridDp.toFloat() }
        } else {
            dp
        }
}
