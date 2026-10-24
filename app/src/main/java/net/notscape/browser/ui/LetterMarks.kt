package net.notscape.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Company letter badges for the Games home rows
 * (Robert's pick, 2026-10-24): a single initial in
 * brand colours - N on Nintendo red, S on Sega blue.
 * The console name already sits beside the mark, so
 * the mark only needs to say the company. These are
 * plain initial badges, not trademarked logo artwork.
 */
@Composable
fun LetterMark(console: String) {
    val sega = console == "genesis" ||
        console == "sms" || console == "gg"
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (sega) Color(0xFF17569B)
                else Color(0xFFE60012)
            )
    ) {
        Text(
            text = if (sega) "S" else "N",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )
    }
}
