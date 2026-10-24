package net.notscape.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Company brand marks for the Games home rows (replaces
 * the console-shape glyphs, which read as tiny flags at
 * row size). These are brand WORDMARKS in brand colours
 * - Nintendo red, SEGA blue - not trademarked logo
 * artwork. The console name already sits beside the
 * mark, so the mark only needs to say the company.
 */
@Composable
fun BrandMark(console: String) {
    val sega = console == "genesis" ||
        console == "sms" || console == "gg"
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (sega) Color(0xFF17569B)
                else Color(0xFFE60012)
            )
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = if (sega) "SEGA" else "Nintendo",
            color = Color.White,
            fontWeight = if (sega) FontWeight.ExtraBold
                else FontWeight.Bold,
            fontSize = if (sega) 12.sp else 11.sp
        )
    }
}
