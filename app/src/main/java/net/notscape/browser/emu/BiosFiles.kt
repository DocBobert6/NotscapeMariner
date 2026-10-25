package net.notscape.browser.emu

import android.content.Context
import java.io.File

/**
 * Console BIOS files (Phase J): which BIOS a core can
 * use, by console. The app ships no BIOS files - the
 * user supplies their own, and they land in the cores'
 * system directory, which the shim already hands the
 * cores. Game Boy family (mGBA) and Genesis (Genesis
 * Plus GX, via its System Boot ROM option) take BIOS
 * files; NES, Super Nintendo, Master System and Game
 * Gear boot without one, so their pages say so instead
 * of offering a useless import.
 */
object BiosFiles {
    fun expectedFor(console: String): List<String> = when (console) {
        "gb" -> listOf("gb_bios.bin", "sgb_bios.bin")
        "gbc" -> listOf("gbc_bios.bin")
        "gba" -> listOf("gba_bios.bin")
        "genesis" -> listOf("bios_MD.bin")
        "ps1" -> listOf("scph5500.bin", "scph5501.bin", "scph5502.bin")
        else -> emptyList()
    }

    fun file(context: Context, name: String): File =
        File(CoreStore.systemDir(context), name)

    fun isInstalled(context: Context, name: String): Boolean =
        file(context, name).exists()
}
