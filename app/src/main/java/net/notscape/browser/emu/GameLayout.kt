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
    const val MAX_SIZE_DP = 220

    private fun key(console: String, landscape: Boolean) =
        "gameLayout:$console:${if (landscape) "l" else "p"}"

    private fun legacyKey(console: String) = "gameLayout:$console"

    val CONSOLES = listOf(
        "gb", "gbc", "gba", "nes", "snes", "genesis", "sms", "gg"
    )

    /** Which editing cluster a control belongs to: the
     * pad (four directions) or the face buttons. Others
     * (shoulders, pills) are ungrouped. */
    fun groupOf(id: String): String? = when (id) {
        "up", "down", "left", "right" -> "pad"
        "a", "b", "c", "x", "y", "z", "one", "two" -> "face"
        else -> null
    }

    /** The shipping Game Boy arrangement. Refined at
     * Robert's word (2026-10-24): J1's clusters hugged
     * the corners - these sit ~30dp in from the side
     * edges and ~34dp up off the bottom gesture strip,
     * inside the natural thumb arcs, and the buttons are
     * a touch larger (fractions of a 900x500 reference
     * canvas; the editor lets him tune from here). */
    fun defaults(
        console: String,
        portrait: Boolean = false
    ): List<ControlSpec> {
        val land = when (console) {
            "nes" -> nesDefaults()
            "gba" -> gbaDefaults()
            "snes" -> snesDefaults()
            "genesis" -> genesisDefaults()
            "sms" -> smsDefaults()
            "gg" -> ggDefaults()
            else -> gbDefaults()
        }
        return if (portrait) portraitize(land) else land
    }

    /** Portrait defaults: the landscape arrangement
     * remapped around portrait thumb anchors - the pad
     * low-left, the face buttons low-right, the pills
     * centred at the very bottom, shoulders above the
     * pad. Relative offsets are kept, tightened
     * slightly. (J9: bottom-anchored at Robert's word;
     * the editor still moves anything anywhere.) */
    private fun portraitize(land: List<ControlSpec>): List<ControlSpec> {
        fun anchor(ids: Set<String>): Pair<Float, Float>? {
            val sel = land.filter { it.id in ids }
            if (sel.isEmpty()) return null
            return sel.map { it.cx }.average().toFloat() to
                sel.map { it.cy }.average().toFloat()
        }
        val groups = listOf(
            setOf("up", "down", "left", "right") to (0.22f to 0.86f),
            setOf("a", "b", "c", "x", "y", "z", "one", "two") to
                (0.78f to 0.86f),
            setOf("select", "start") to (0.50f to 0.96f),
            setOf("l") to (0.12f to 0.62f),
            setOf("r") to (0.88f to 0.62f)
        )
        return land.map { spec ->
            val g = groups.firstOrNull { spec.id in it.first }
                ?: return@map spec
            val la = anchor(g.first) ?: return@map spec
            val pa = g.second
            spec.copy(
                cx = (pa.first + (spec.cx - la.first) * 0.8f)
                    .coerceIn(0.03f, 0.97f),
                cy = (pa.second + (spec.cy - la.second) * 0.8f)
                    .coerceIn(0.03f, 0.97f)
            )
        }
    }

    private fun gbDefaults(): List<ControlSpec> = listOf(
        ControlSpec("up", "▲", 4, 0.137f, 0.620f, 60),
        ControlSpec("down", "▼", 5, 0.137f, 0.872f, 60),
        ControlSpec("left", "◀", 6, 0.067f, 0.746f, 60),
        ControlSpec("right", "▶", 7, 0.240f, 0.746f, 60),
        ControlSpec("a", "A", 8, 0.929f, 0.628f, 68),
        ControlSpec("b", "B", 0, 0.798f, 0.864f, 68),
        ControlSpec("select", "SELECT", 2, 0.452f, 0.906f, 74, pill = true),
        ControlSpec("start", "START", 3, 0.548f, 0.906f, 74, pill = true)
    )

    private fun nesDefaults(): List<ControlSpec> = listOf(
        ControlSpec("up", "\u25B2", 4, 0.137f, 0.620f, 60),
        ControlSpec("down", "\u25BC", 5, 0.137f, 0.872f, 60),
        ControlSpec("left", "\u25C0", 6, 0.067f, 0.746f, 60),
        ControlSpec("right", "\u25B6", 7, 0.240f, 0.746f, 60),
        ControlSpec("a", "A", 8, 0.935f, 0.830f, 64),
        ControlSpec("b", "B", 0, 0.815f, 0.830f, 64),
        ControlSpec("select", "SELECT", 2, 0.452f, 0.906f, 74, pill = true),
        ControlSpec("start", "START", 3, 0.548f, 0.906f, 74, pill = true)
    )

    private fun gbaDefaults(): List<ControlSpec> = gbDefaults() + listOf(
        ControlSpec("l", "L", 10, 0.100f, 0.300f, 74, pill = true),
        ControlSpec("r", "R", 11, 0.900f, 0.300f, 74, pill = true)
    )

    private fun snesDefaults(): List<ControlSpec> = listOf(
        ControlSpec("up", "\u25B2", 4, 0.137f, 0.620f, 60),
        ControlSpec("down", "\u25BC", 5, 0.137f, 0.872f, 60),
        ControlSpec("left", "\u25C0", 6, 0.067f, 0.746f, 60),
        ControlSpec("right", "\u25B6", 7, 0.240f, 0.746f, 60),
        ControlSpec("x", "X", 9, 0.868f, 0.600f, 60),
        ControlSpec("y", "Y", 1, 0.790f, 0.720f, 60),
        ControlSpec("b", "B", 0, 0.868f, 0.840f, 60),
        ControlSpec("a", "A", 8, 0.946f, 0.720f, 60),
        ControlSpec("l", "L", 10, 0.100f, 0.300f, 74, pill = true),
        ControlSpec("r", "R", 11, 0.900f, 0.300f, 74, pill = true),
        ControlSpec("select", "SELECT", 2, 0.452f, 0.906f, 74, pill = true),
        ControlSpec("start", "START", 3, 0.548f, 0.906f, 74, pill = true)
    )

    // Genesis bits are POSITIONS (the core's mapping):
    // the button labelled A drives the RetroPad B id,
    // B the A id, C the X id, X the Y id, Y the L id,
    // Z the R id.
    private fun genesisDefaults(): List<ControlSpec> = listOf(
        ControlSpec("up", "\u25B2", 4, 0.137f, 0.620f, 60),
        ControlSpec("down", "\u25BC", 5, 0.137f, 0.872f, 60),
        ControlSpec("left", "\u25C0", 6, 0.067f, 0.746f, 60),
        ControlSpec("right", "\u25B6", 7, 0.240f, 0.746f, 60),
        ControlSpec("x", "X", 1, 0.790f, 0.600f, 58),
        ControlSpec("y", "Y", 10, 0.868f, 0.585f, 58),
        ControlSpec("z", "Z", 11, 0.946f, 0.600f, 58),
        ControlSpec("a", "A", 0, 0.790f, 0.830f, 58),
        ControlSpec("b", "B", 8, 0.868f, 0.815f, 58),
        ControlSpec("c", "C", 9, 0.946f, 0.830f, 58),
        ControlSpec("start", "START", 3, 0.500f, 0.906f, 74, pill = true)
    )

    private fun smsDefaults(): List<ControlSpec> = listOf(
        ControlSpec("up", "\u25B2", 4, 0.137f, 0.620f, 60),
        ControlSpec("down", "\u25BC", 5, 0.137f, 0.872f, 60),
        ControlSpec("left", "\u25C0", 6, 0.067f, 0.746f, 60),
        ControlSpec("right", "\u25B6", 7, 0.240f, 0.746f, 60),
        ControlSpec("one", "1", 0, 0.815f, 0.830f, 64),
        ControlSpec("two", "2", 8, 0.935f, 0.830f, 64)
    )

    private fun ggDefaults(): List<ControlSpec> =
        smsDefaults() + ControlSpec(
            "start", "START", 3, 0.500f, 0.906f, 74, pill = true
        )

    /** Load the layout for one orientation. A legacy
     * combined (J2/J3) layout, if one exists, seeds both
     * orientations until each is saved on its own. */
    fun load(
        store: AppStore,
        console: String,
        landscape: Boolean
    ): List<ControlSpec> {
        val raw = store.setting(key(console, landscape), "")
        if (raw.isNotBlank()) {
            return parse(raw) ?: defaults(console, !landscape)
        }
        val legacy = store.setting(legacyKey(console), "")
        if (legacy.isNotBlank()) {
            return parse(legacy) ?: defaults(console, !landscape)
        }
        return defaults(console, !landscape)
    }

    fun save(
        store: AppStore,
        console: String,
        landscape: Boolean,
        specs: List<ControlSpec>
    ) {
        store.putSetting(key(console, landscape), toJson(specs))
    }

    fun reset(store: AppStore, console: String, landscape: Boolean) {
        store.putSetting(key(console, landscape), "")
    }

    /** One-time J9 portrait migration: clear every
     * saved portrait layout (and the legacy combined
     * key, which would otherwise reseed portrait)
     * so the new bottom-anchored defaults take
     * effect. Landscape layouts saved per orientation
     * are untouched; one that only lived in the
     * legacy key falls back to defaults. */
    fun resetPortrait(store: AppStore) {
        for (c in CONSOLES) {
            store.putSetting(key(c, false), "")
            store.putSetting(legacyKey(c), "")
        }
    }

    fun resetAll(store: AppStore) {
        for (c in CONSOLES) {
            reset(store, c, true)
            reset(store, c, false)
            store.putSetting(legacyKey(c), "")
        }
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
