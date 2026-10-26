package net.notscape.browser.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import net.notscape.browser.MainActivity
import net.notscape.browser.emu.BoxArt
import net.notscape.browser.emu.GameEntry
import net.notscape.browser.emu.StartShortcuts

/**
 * Launcher pins (beta.7): home-screen shortcuts for
 * the four library sections and for individual
 * games, via ShortcutManagerCompat.requestPinShortcut
 * (the same route the browser's "Add to home
 * screen" row uses). Console pages are NOT pinned
 * to the launcher - that was never asked for.
 * Dynamic-shortcut fallback (beta.12): when the
 * launcher does not support pinning, the same
 * shortcut is published as a dynamic shortcut
 * instead, so it appears on a long-press of the
 * Notscape icon (launchers show at most a handful
 * of those, so the newest ones push the oldest
 * out - the pin request stays the first try).
 * Tapping a pinned shortcut opens MainActivity with
 * an extra, which MainActivity consumes on create
 * or new intent. A removed game's shortcut lands on
 * a polite "no longer in your library" toast.
 */
object LauncherPins {
    const val SECTION_EXTRA = "notscape_section"
    const val GAME_EXTRA = "notscape_game_id"

    private fun sectionColor(id: String): Int = when (id) {
        "playlists" -> 0xFF7C4DBE.toInt()
        "library" -> 0xFF2E7D6B.toInt()
        "bookshelf" -> 0xFFB7791F.toInt()
        else -> 0xFFC03030.toInt()
    }

    private fun letterIcon(letter: String, bg: Int): IconCompat {
        val size = 192
        val bmp = Bitmap.createBitmap(
            size, size, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = bg
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = Color.WHITE
        paint.textSize = size * 0.55f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(letter, size / 2f, y, paint)
        return IconCompat.createWithBitmap(bmp)
    }

    private fun gameIcon(context: Context, game: GameEntry): IconCompat {
        val art = listOf(
            BoxArt.customFile(context, game),
            BoxArt.artFile(context, game),
            BoxArt.titleFile(context, game)
        ).firstOrNull { it.exists() }
        val bmp = art?.let {
            runCatching {
                BitmapFactory.decodeFile(it.absolutePath)
            }.getOrNull()
        }
        if (bmp != null) {
            val scaled = Bitmap.createScaledBitmap(bmp, 192, 192, true)
            return IconCompat.createWithBitmap(scaled)
        }
        val consoleColor = when (game.system) {
            "genesis", "sms", "gg" -> 0xFF17569B.toInt()
            else -> 0xFFE60012.toInt()
        }
        return letterIcon(
            game.title.trim().firstOrNull()?.uppercase() ?: "?",
            consoleColor
        )
    }

    fun requestSectionPin(context: Context, sectionId: String) {
        val label = StartShortcuts.sectionLabel(sectionId)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(SECTION_EXTRA, sectionId)
        val info = ShortcutInfoCompat.Builder(
            context, "section-$sectionId"
        )
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(
                letterIcon(
                    label.trim().firstOrNull()?.uppercase() ?: "?",
                    sectionColor(sectionId)
                )
            )
            .setIntent(intent)
            .build()
        request(context, info)
    }

    fun requestGamePin(context: Context, game: GameEntry) {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(GAME_EXTRA, game.id)
        val info = ShortcutInfoCompat.Builder(
            context, "game-" + game.id
        )
            .setShortLabel(game.title)
            .setLongLabel(game.title)
            .setIcon(gameIcon(context, game))
            .setIntent(intent)
            .build()
        request(context, info)
    }

    private fun request(context: Context, info: ShortcutInfoCompat) {
        val ok = runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, info, null)
        }.getOrDefault(false)
        if (ok) {
            Toast.makeText(
                context,
                "Shortcut requested - your launcher may ask you " +
                    "to confirm",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        // The launcher does not pin: fall back to a
        // dynamic shortcut, which shows on a
        // long-press of the Notscape icon instead.
        // The intent is the same one the pin would
        // have carried, so tapping it behaves
        // identically.
        val dyn = runCatching {
            ShortcutManagerCompat.pushDynamicShortcut(context, info)
        }.getOrDefault(false)
        // Diagnostic split for the failure case: the
        // support check and the request can disagree,
        // and which one failed decides where the bug
        // lives - the launcher, or our request.
        val supported = ShortcutManagerCompat
            .isRequestPinShortcutSupported(context)
        Toast.makeText(
            context,
            if (dyn) {
                "Your launcher doesn't pin shortcuts, so this " +
                    "is on the Notscape icon's long-press menu instead"
            } else if (supported) {
                "Pin request failed even though your launcher " +
                    "supports pinning"
            } else {
                "Your launcher doesn't support pinned shortcuts"
            },
            Toast.LENGTH_LONG
        ).show()
    }
}
