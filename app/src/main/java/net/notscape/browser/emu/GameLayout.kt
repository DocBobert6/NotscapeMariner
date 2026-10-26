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
 * a circle. [analog] (beta.17) marks a stick: 0 = left,
 * 1 = right, -1 = not a stick; a stick's bit is -1 and
 * it reports an axis vector instead of a button.
 */
data class ControlSpec(
    val id: String,
    val label: String,
    val bit: Int,
    val cx: Float,
    val cy: Float,
    val sizeDp: Int,
    val pill: Boolean = false,
    val analog: Int = -1
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

    /** PS1 controller variant (beta.17): Digital /
     * Single analogue / Dual analogue, default Dual.
     * Other consoles have no variants. */
    const val PS1_VARIANT_KEY = "gamePs1Controller"

    fun controllerVariant(store: AppStore, console: String): String =
        if (console == "ps1") {
            store.setting(PS1_VARIANT_KEY, "Dual")
        } else {
            "Digital"
        }

    /** Variant-aware key: digital PS1 keeps the legacy
     * key (beta.16 saves carry over); single/dual get
     * their own so switching variants never clobbers
     * a tuned layout. Non-PS1 consoles ignore it. */
    private fun variantKey(
        console: String,
        landscape: Boolean,
        variant: String
    ): String {
        val orient = if (landscape) "l" else "p"
        return if (console == "ps1" && variant != "Digital") {
            "gameLayout:ps1:$variant:$orient"
        } else {
            key(console, landscape)
        }
    }

    private fun legacyKey(console: String) = "gameLayout:$console"

    val CONSOLES = listOf(
        "gb", "gbc", "gba", "nes", "snes", "genesis", "sms", "gg", "ps1"
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
        portrait: Boolean = false,
        ps1Variant: String = "Dual"
    ): List<ControlSpec> {
        val land = when (console) {
            "nes" -> nesDefaults()
            "gba" -> gbaDefaults()
            "snes" -> snesDefaults()
            "genesis" -> genesisDefaults()
            "sms" -> smsDefaults()
            "gg" -> ggDefaults()
            "ps1" -> ps1Defaults(ps1Variant)
            else -> gbDefaults()
        }
        return if (portrait) portraitize(land) else land
    }

    /** Portrait defaults: the landscape arrangement
     * remapped around portrait thumb anchors - the pad
     * low-left, the face buttons low-right, the pills
     * centred at the very bottom, shoulders above the
     * pad. (J9: bottom-anchored at Robert's word; the
     * editor still moves anything anywhere.) Dpad fix:
     * each cluster gets its OWN spread, because one
     * uniform 0.8 shrink was a double squeeze - it
     * narrowed the fraction gaps on an already narrow
     * canvas until pills, pad directions, and face
     * rows overlapped. The pad keeps its full
     * horizontal spread, the face cluster spreads
     * wider, the pills spread to a 0.20 gap, and the
     * shoulders group with their second pair (PS1
     * L2/R2 previously kept landscape fractions in
     * portrait and floated mid-screen). The numbers
     * are pinned by the zero-overlap test in
     * GameLayoutDefaultsTest at 393x852 portrait and
     * 852x393 landscape.
     *
     * Group entry: ids, portrait anchor, x-spread,
     * y-spread. */
    private fun portraitize(land: List<ControlSpec>): List<ControlSpec> {
        fun anchor(ids: Set<String>): Pair<Float, Float>? {
            val sel = land.filter { it.id in ids }
            if (sel.isEmpty()) return null
            return sel.map { it.cx }.average().toFloat() to
                sel.map { it.cy }.average().toFloat()
        }
        // Triple: ids, portrait anchor, spreads
        // (x-spread, y-spread).
        val groups = listOf(
            Triple(
                setOf("up", "down", "left", "right"),
                0.22f to 0.86f, 1.0f to 0.8f
            ),
            Triple(
                setOf("a", "b", "c", "x", "y", "z", "one", "two"),
                0.74f to 0.80f, 2.1f to 0.8f
            ),
            Triple(
                setOf("select", "start"),
                0.50f to 0.96f, 1.667f to 0.8f
            ),
            Triple(
                setOf("l", "l2"),
                0.12f to 0.58f, 0.8f to 0.8f
            ),
            Triple(
                setOf("r", "r2"),
                0.88f to 0.58f, 0.8f to 0.8f
            ),
            Triple(
                setOf("lstick"),
                0.33f to 0.60f, 0.8f to 0.8f
            ),
            Triple(
                setOf("rstick"),
                0.67f to 0.60f, 0.8f to 0.8f
            )
        )
        return land.map { spec ->
            val g = groups.firstOrNull { spec.id in it.first }
                ?: return@map spec
            val la = anchor(g.first) ?: return@map spec
            val pa = g.second
            spec.copy(
                cx = (pa.first + (spec.cx - la.first) * g.third.first)
                    .coerceIn(0.03f, 0.97f),
                cy = (pa.second + (spec.cy - la.second) * g.third.second)
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
        ControlSpec("select", "SELECT", 2, 0.44f, 0.906f, 74, pill = true),
        ControlSpec("start", "START", 3, 0.56f, 0.906f, 74, pill = true)
    )

    private fun nesDefaults(): List<ControlSpec> = listOf(
        ControlSpec("up", "\u25B2", 4, 0.137f, 0.620f, 60),
        ControlSpec("down", "\u25BC", 5, 0.137f, 0.872f, 60),
        ControlSpec("left", "\u25C0", 6, 0.067f, 0.746f, 60),
        ControlSpec("right", "\u25B6", 7, 0.240f, 0.746f, 60),
        ControlSpec("a", "A", 8, 0.935f, 0.830f, 64),
        ControlSpec("b", "B", 0, 0.815f, 0.830f, 64),
        ControlSpec("select", "SELECT", 2, 0.44f, 0.906f, 74, pill = true),
        ControlSpec("start", "START", 3, 0.56f, 0.906f, 74, pill = true)
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
        ControlSpec("select", "SELECT", 2, 0.44f, 0.906f, 74, pill = true),
        ControlSpec("start", "START", 3, 0.56f, 0.906f, 74, pill = true)
    )

    // PlayStation bits are RetroPad positions, labelled
      // with the pad shapes (the Genesis precedent: the
      // label follows the core's mapping, the bit is the
      // position). Cross bottom, Circle right, Square
      // left, Triangle top; L2/R2 pills below L1/R1.
    /** PS1 digital pad (beta.16); Single adds the
     * left stick, Dual adds both (beta.17). Sticks
     * sit low-centre, between pad and face, clear of
     * the Select/Start pills. */
    private fun ps1Defaults(variant: String): List<ControlSpec> {
        val base = listOf(
            ControlSpec("up", "\u25B2", 4, 0.137f, 0.620f, 60),
            ControlSpec("down", "\u25BC", 5, 0.137f, 0.872f, 60),
            ControlSpec("left", "\u25C0", 6, 0.067f, 0.746f, 60),
            ControlSpec("right", "\u25B6", 7, 0.240f, 0.746f, 60),
            ControlSpec("x", "\u25B3", 9, 0.868f, 0.600f, 60),
            ControlSpec("y", "\u25A2", 1, 0.790f, 0.720f, 60),
            ControlSpec("b", "\u2715", 0, 0.868f, 0.840f, 60),
            ControlSpec("a", "\u25EF", 8, 0.946f, 0.720f, 60),
            ControlSpec("l", "L1", 10, 0.100f, 0.300f, 74, pill = true),
            ControlSpec("r", "R1", 11, 0.900f, 0.300f, 74, pill = true),
            ControlSpec("l2", "L2", 12, 0.100f, 0.420f, 74, pill = true),
            ControlSpec("r2", "R2", 13, 0.900f, 0.420f, 74, pill = true),
            ControlSpec("select", "SELECT", 2, 0.44f, 0.906f, 74, pill = true),
            ControlSpec("start", "START", 3, 0.56f, 0.906f, 74, pill = true)
        )
        return when (variant) {
            "Digital" -> base
            "Single" -> base + ControlSpec(
                "lstick", "LS", -1, 0.335f, 0.800f, 84,
                analog = 0
            )
            else -> base + listOf(
                ControlSpec(
                    "lstick", "LS", -1, 0.335f, 0.800f, 84,
                    analog = 0
                ),
                ControlSpec(
                    "rstick", "RS", -1, 0.665f, 0.800f, 84,
                    analog = 1
                )
            )
        }
    }

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
        val variant = controllerVariant(store, console)
        val raw = store.setting(
            variantKey(console, landscape, variant), ""
        )
        if (raw.isNotBlank()) {
            return parse(raw) ?: defaults(console, !landscape, variant)
        }
        val legacy = store.setting(legacyKey(console), "")
        if (legacy.isNotBlank()) {
            return parse(legacy) ?: defaults(console, !landscape, variant)
        }
        return defaults(console, !landscape, variant)
    }

    fun save(
        store: AppStore,
        console: String,
        landscape: Boolean,
        specs: List<ControlSpec>
    ) {
        val variant = controllerVariant(store, console)
        store.putSetting(
            variantKey(console, landscape, variant), toJson(specs)
        )
    }

    fun reset(store: AppStore, console: String, landscape: Boolean) {
        val variant = controllerVariant(store, console)
        store.putSetting(variantKey(console, landscape, variant), "")
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

    /** One console, every orientation and (PS1)
     * every variant: what the console page's
     * "Reset this console's layout" means. */
    fun resetConsole(store: AppStore, console: String) {
        reset(store, console, true)
        reset(store, console, false)
        store.putSetting(legacyKey(console), "")
        if (console == "ps1") {
            for (v in listOf("Digital", "Single", "Dual")) {
                store.putSetting(variantKey(console, true, v), "")
                store.putSetting(variantKey(console, false, v), "")
            }
        }
    }

    fun resetAll(store: AppStore) {
        for (c in CONSOLES) {
            reset(store, c, true)
            reset(store, c, false)
            store.putSetting(legacyKey(c), "")
            if (c == "ps1") {
                // The current-variant reset above
                // leaves the other variants behind;
                // a full reset clears all three.
                for (v in listOf("Digital", "Single", "Dual")) {
                    store.putSetting(variantKey(c, true, v), "")
                    store.putSetting(variantKey(c, false, v), "")
                }
            }
        }
    }

    fun parse(json: String): List<ControlSpec>? = runCatching {
        val arr = JSONArray(json)
        val out = ArrayList<ControlSpec>(arr.length())
        for (i in 0 until arr.length()) {
            // Skip a malformed spec, keep the rest:
            // one bad entry used to null the whole
            // parse and hand the editor defaults that
            // its next Done would save over the tuned
            // layout.
            runCatching {
            val o = arr.getJSONObject(i)
            out += ControlSpec(
                id = o.getString("id"),
                label = o.getString("label"),
                bit = o.getInt("bit"),
                cx = o.getDouble("cx").toFloat().coerceIn(0f, 1f),
                cy = o.getDouble("cy").toFloat().coerceIn(0f, 1f),
                sizeDp = o.getInt("size")
                    .coerceIn(MIN_SIZE_DP, MAX_SIZE_DP),
                pill = o.optBoolean("pill", false),
                analog = o.optInt("analog", -1)
            )
            }
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
                    .put("analog", s.analog)
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
