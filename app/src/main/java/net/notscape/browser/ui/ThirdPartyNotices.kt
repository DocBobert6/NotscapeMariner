package net.notscape.browser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** One third-party component and the substance of its licence. */
data class ThirdPartyEntry(
    val name: String,
    val license: String,
    val summary: String
)

/**
 * Third-party notices (U4). The full notices, with the
 * non-commercial conditions quoted in full, live in
 * THIRD-PARTY-NOTICES.md at the repo root; this list is the
 * in-app summary of the same material.
 */
object ThirdPartyNotices {
    val entries: List<ThirdPartyEntry> = listOf(
        ThirdPartyEntry(
            "mGBA (Game Boy / Game Boy Color / Game Boy Advance core)",
            "Mozilla Public License 2.0",
            "Source files carry the MPL Exhibit A notice. No Exhibit B " +
                "\"Incompatible With Secondary Licenses\" notice is attached " +
                "to any source file at the pinned commit."
        ),
        ThirdPartyEntry(
            "FCEUmm (NES core)",
            "GNU General Public License v2 or later",
            "The Copying file carries the GPLv2 text; source headers grant " +
                "version 2 or any later version."
        ),
        ThirdPartyEntry(
            "Snes9x (Super NES core)",
            "Non-commercial custom licence",
            "Freeware for personal, non-commercial use only. The licence " +
                "information and copyright notice must appear with all " +
                "copies and derived works; commercial rights are never given."
        ),
        ThirdPartyEntry(
            "Genesis Plus GX (Genesis / Master System / Game Gear / Sega CD core)",
            "Non-commercial licence",
            "Redistributions may not be sold nor used in a commercial " +
                "product or activity; modified redistributions must include " +
                "the complete source code; the copyright notice, conditions " +
                "and disclaimer must be reproduced in the distribution " +
                "materials."
        ),
        ThirdPartyEntry(
            "SwanStation (PlayStation core)",
            "GNU General Public License v3.0",
            "The LICENSE file carries the GPLv3 text. Two files " +
                "(pgxp.cpp, pgxp.h) carry a GPLv2-or-later header, taken " +
                "under GPLv3."
        ),
        ThirdPartyEntry(
            "melonDS (Nintendo DS core)",
            "GNU General Public License v3 or later",
            "The LICENSE file carries the GPLv3 text; source headers grant " +
                "version 3 or any later version."
        ),
        ThirdPartyEntry(
            "PicoDrive (Sega 32X core)",
            "Non-commercial licence",
            "Same conditions as Genesis Plus GX: no sale, no commercial " +
                "product or activity, complete source with modified " +
                "redistributions, and the notice, conditions and disclaimer " +
                "reproduced in the distribution materials."
        ),
        ThirdPartyEntry(
            "oboe 1.9.0 (audio output)",
            "Apache License 2.0",
            "Licensed under the Apache License, Version 2.0."
        ),
        ThirdPartyEntry(
            "sherpa-onnx 1.13.8 (on-device text-to-speech)",
            "Apache License 2.0",
            "Licensed under the Apache License, Version 2.0."
        ),
        ThirdPartyEntry(
            "Media3 1.9.4 (media playback and transcode)",
            "Apache License 2.0",
            "Licensed under the Apache License, Version 2.0."
        ),
        ThirdPartyEntry(
            "lcd3x (handheld LCD shading)",
            "Public domain",
            "By Gigaherz. No licence obligation attaches; the credit is " +
                "kept as good practice."
        ),
        ThirdPartyEntry(
            "crt-guest-advanced-fast (CRT shading)",
            "GNU General Public License v2 or later",
            "By guest(r). The upstream shader headers grant version 2 or " +
                "any later version; the port carries the same attribution."
        )
    )
}

/** Scrollable notices dialog, opened from Settings > About. */
@Composable
fun ThirdPartyNoticesDialog(onDismiss: () -> Unit) {
    FrostDialog(
        onDismissRequest = onDismiss,
        title = { Text("Third-party notices") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Notscape Mariner itself is licensed under the GNU " +
                        "General Public License v3.0. The components below " +
                        "are covered by their own licences; the full " +
                        "notices live in THIRD-PARTY-NOTICES.md.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                ThirdPartyNotices.entries.forEach { entry ->
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        entry.license,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        entry.summary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
