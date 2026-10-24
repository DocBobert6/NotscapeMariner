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
 */
data class ShaderPreset(
    val name: String,
    val scan: Float,
    val grid: Float,
    val blur: Float,
    val lcd: Boolean = false,
    val brightScan: Float = 16f,
    val brightLcd: Float = 4f,
    val crt: Boolean = false
)

object ShaderPresets {
    private val presets = mapOf(
        "gb" to ShaderPreset("Jade", 0.00f, 0.25f, 0.10f, lcd = true),
        "gbc" to ShaderPreset("Prism", 0.00f, 0.25f, 0.10f, lcd = true),
        "gba" to ShaderPreset("Dusk", 0.00f, 0.20f, 0.10f, lcd = true),
        "nes" to ShaderPreset("Ember", 0.42f, 0.00f, 0.15f, crt = true),
        "snes" to ShaderPreset("Violet", 0.30f, 0.00f, 0.10f, crt = true),
        "genesis" to ShaderPreset("Blast", 0.35f, 0.00f, 0.20f, crt = true),
        "sms" to ShaderPreset("Cobalt", 0.42f, 0.00f, 0.15f),
        "gg" to ShaderPreset("Lantern", 0.00f, 0.30f, 0.10f)
    )

    fun forConsole(console: String): ShaderPreset =
        presets[console] ?: presets.getValue("gb")
}
