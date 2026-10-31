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
 * of offering a useless import. DS lists its three files but
 * boots without them: the pinned melonDS core falls back to
 * its built-in firmware when BIOS/firmware are missing
 * (verified against the pinned source), so the DS page
 * must say BIOS is optional, never required. Sega CD
 * lists its three region BIOS files and needs one:
 * Genesis Plus GX treats the Mega CD BIOS as required
 * files (verified against the GX source), so the Sega
 * CD page says so. 32X needs no BIOS: PicoDrive's 32X
 * BIOS pointers are NULL-tolerant, so it boots
 * without one.
 */
object BiosFiles {
    fun expectedFor(console: String): List<String> = when (console) {
        "gb" -> listOf("gb_bios.bin", "sgb_bios.bin")
        "gbc" -> listOf("gbc_bios.bin")
        "gba" -> listOf("gba_bios.bin")
        "genesis" -> listOf("bios_MD.bin")
        "ps1" -> listOf("scph5500.bin", "scph5501.bin", "scph5502.bin")
        "ds" -> listOf("bios7.bin", "bios9.bin", "firmware.bin")
        "segacd" -> listOf(
            "bios_CD_U.bin", "bios_CD_E.bin", "bios_CD_J.bin"
        )
        else -> emptyList()
    }

    fun file(context: Context, name: String): File =
        File(CoreStore.systemDir(context), name)

    fun isInstalled(context: Context, name: String): Boolean =
        file(context, name).exists()
}
