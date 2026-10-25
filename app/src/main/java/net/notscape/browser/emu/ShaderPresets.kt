package net.notscape.browser.emu

/**
 * Shader presets (Phase J, J8; LCD port in Phase L):
 * one look per console, behind a single shared master
 * toggle (off by default). The UI shows the NAME ONLY
 * - never the numbers below, and never what a preset
 * is made of. The numbers are internal starting values
 * for on-device tuning. Game Gear keeps the legacy
 * grid; GB/GBC/GBA (lcd = true) use the sine-modulated
 * LCD pass, whose two strengths start at the port's
 * own defaults for every console; NES/SNES/Genesis
 * (crt = true) use the multi-pass CRT chain. The
 * scan/grid/blur values still ride along for the
 * legacy fallback if the chain ever cannot run.
 * Each preset also carries its own gentle display
 * defaults (beta.6): curvature on for CRT and LCD
 * consoles, rounding on for every console, both
 * very light - see the field comments. The
 * resolvers below apply them only where the user
 * has not set a value of their own.
 */
/**
 * A preset's color profile: the per-console color
 * grade, applied first in whatever shader path the
 * console uses (the CRT chain's pre pass, the LCD
 * pass, or the legacy pass), so the grade shapes
 * the picture before scanlines or grille do. The
 * numbers are starting values for on-device tuning,
 * like scan/grid/blur - not measured profiles.
 * Neutral (1, 0, 1, 1, 0, false) reproduces the
 * pre-profile output exactly. segaFix is the
 * Genesis DAC range expansion (255/239) the CRT
 * chain's pre pass already carried, switched off.
 */
data class ColorProfile(
    val saturation: Float = 1f,
    val warm: Float = 0f,
    val gamma: Float = 1f,
    val bright: Float = 1f,
    val black: Float = 0f,
    val segaFix: Boolean = false
)

data class ShaderPreset(
    val name: String,
    val scan: Float,
    val grid: Float,
    val blur: Float,
    val lcd: Boolean = false,
    val brightScan: Float = 16f,
    val brightLcd: Float = 4f,
    val crt: Boolean = false,
    // Display defaults (beta.6): gentle curvature
    // and rounding per preset. Very light by design:
    // curve 30% of an already slight base, rounding
    // 20% (a radius of 5% of the picture's smaller
    // side). Home CRT consoles curve; handheld LCD
    // consoles curve at half strength; the two
    // legacy consoles round but do not curve.
    val curveOn: Boolean = false,
    val curveAmount: Int = 30,
    val roundOn: Boolean = false,
    val roundAmount: Int = 20,
    val color: ColorProfile = ColorProfile()
)

object ShaderPresets {
    private val presets = mapOf(
        "gb" to ShaderPreset("Jade", 0.00f, 0.25f, 0.10f, lcd = true,
            curveOn = true, roundOn = true),
        "gbc" to ShaderPreset("Prism", 0.00f, 0.25f, 0.10f, lcd = true,
            curveOn = true, roundOn = true,
            color = ColorProfile(saturation = 1.05f)),
        "gba" to ShaderPreset("Dusk", 0.00f, 0.20f, 0.10f, lcd = true,
            curveOn = true, roundOn = true,
            color = ColorProfile(saturation = 0.92f, bright = 1.06f)),
        "nes" to ShaderPreset("Ember", 0.42f, 0.00f, 0.15f, crt = true,
            curveOn = true, roundOn = true,
            color = ColorProfile(saturation = 1.05f, warm = 25f)),
        "snes" to ShaderPreset("Violet", 0.30f, 0.00f, 0.10f, crt = true,
            curveOn = true, roundOn = true,
            color = ColorProfile(warm = 10f)),
        "genesis" to ShaderPreset("Blast", 0.35f, 0.00f, 0.20f, crt = true,
            curveOn = true, roundOn = true,
            color = ColorProfile(segaFix = true)),
        "sms" to ShaderPreset("Cobalt", 0.42f, 0.00f, 0.15f,
            roundOn = true),
        "ps1" to ShaderPreset("Fog", 0.35f, 0.00f, 0.15f, crt = true,
            curveOn = true, roundOn = true,
            color = ColorProfile(bright = 1.03f)),
        "gg" to ShaderPreset("Lantern", 0.00f, 0.30f, 0.10f,
            roundOn = true,
            color = ColorProfile(saturation = 1.05f, bright = 1.04f))
    )

    fun forConsole(console: String): ShaderPreset =
        presets[console] ?: presets.getValue("gb")

    /** Curved-corner pull (beta.4): CRT consoles
     * take the full strength, LCD consoles half,
     * anything else none. Slider percent 0..100;
     * the toggle off forces zero. */
    fun curvePull(
        preset: ShaderPreset,
        enabled: Boolean,
        amountPct: Int
    ): Float {
        if (!enabled) return 0f
        val base = when {
            preset.crt -> 0.020f
            preset.lcd -> 0.010f
            else -> 0f
        }
        return base * amountPct.coerceIn(0, 100) / 100f
    }

    /** Effective display settings (beta.6). A console
     * the user already set keeps its own values; a
     * console set only through the old shared keys
     * keeps those; anything untouched falls through
     * to the preset's defaults above. The lookup is
     * store::setting, so tests pass a map instead.
     * Rounding never had a shared key, so its chain
     * is per-console, then preset. */
    fun curveOn(
        lookup: (String, String) -> String,
        console: String
    ): Boolean {
        val per = lookup("gameCurvature:$console", "")
        if (per != "") return per == "true"
        val shared = lookup("gameCurvature", "")
        if (shared != "") return shared == "true"
        return forConsole(console).curveOn
    }

    fun curveAmount(
        lookup: (String, String) -> String,
        console: String
    ): Int {
        val per = lookup("gameCurvatureAmount:$console", "")
            .toIntOrNull()
        if (per != null) return per.coerceIn(0, 100)
        val shared = lookup("gameCurvatureAmount", "")
            .toIntOrNull()
        if (shared != null) return shared.coerceIn(0, 100)
        return forConsole(console).curveAmount
    }

    fun roundOn(
        lookup: (String, String) -> String,
        console: String
    ): Boolean {
        val per = lookup("gameRoundCorners:$console", "")
        if (per != "") return per == "true"
        return forConsole(console).roundOn
    }

    fun roundAmount(
        lookup: (String, String) -> String,
        console: String
    ): Int {
        val per = lookup("gameRoundAmount:$console", "")
            .toIntOrNull()
        if (per != null) return per.coerceIn(0, 100)
        return forConsole(console).roundAmount
    }
}
