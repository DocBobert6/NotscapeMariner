package net.notscape.browser.emu

/**
 * Shader presets (Phase J, J8): one look per console,
 * behind a single shared master toggle (off by
 * default). The UI shows the NAME ONLY - never the
 * numbers below, and never what a preset is made of.
 * The numbers are internal starting values for
 * on-device tuning.
 */
data class ShaderPreset(
    val name: String,
    val scan: Float,
    val mask: Float,
    val blur: Float
)

object ShaderPresets {
    private val presets = mapOf(
        "gb" to ShaderPreset("Jade", 0.30f, 0.10f, 0.15f),
        "gbc" to ShaderPreset("Prism", 0.30f, 0.12f, 0.10f),
        "gba" to ShaderPreset("Dusk", 0.25f, 0.10f, 0.10f),
        "nes" to ShaderPreset("Ember", 0.42f, 0.18f, 0.15f),
        "snes" to ShaderPreset("Violet", 0.30f, 0.12f, 0.10f),
        "genesis" to ShaderPreset("Blast", 0.35f, 0.20f, 0.20f),
        "sms" to ShaderPreset("Cobalt", 0.42f, 0.18f, 0.15f),
        "gg" to ShaderPreset("Lantern", 0.30f, 0.12f, 0.10f)
    )

    fun forConsole(console: String): ShaderPreset =
        presets[console] ?: presets.getValue("gb")
}
