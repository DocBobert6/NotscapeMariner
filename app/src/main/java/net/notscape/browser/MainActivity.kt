package net.notscape.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import net.notscape.browser.browser.BrowserScreen
import net.notscape.browser.browser.FilterEngine
import net.notscape.browser.browser.UpdateChecker
import net.notscape.browser.data.AppStore
import net.notscape.browser.ui.NotscapeTheme
import net.notscape.browser.ui.BookmarksScreen
import net.notscape.browser.ui.BookshelfScreen
import net.notscape.browser.ui.HistoryScreen
import net.notscape.browser.ui.LibraryScreen
import net.notscape.browser.ui.PlaylistsScreen
import net.notscape.browser.ui.ReaderScreen
import net.notscape.browser.ui.Screen
import net.notscape.browser.ui.SettingsScreen
import net.notscape.browser.ui.VaultScreen

class MainActivity : FragmentActivity() {

    private lateinit var store: AppStore
    private var screen by mutableStateOf(Screen.Browser)
    private var pendingUrl by mutableStateOf<String?>(null)
    private var readerBookId by mutableStateOf<Long?>(null)
    private var readerArticleUrl by mutableStateOf<String?>(null)
    private var initialUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        store = AppStore.get(this)
        initialUrl = sanitizeIncomingUrl(intent?.dataString)
        FilterEngine.initAsync(this)
        UpdateChecker.checkAsync(store)
        setContent {
            val rev = store.revision
            var crashReport by remember {
                mutableStateOf(
                    runCatching {
                        val f = java.io.File(filesDir, "last_crash.txt")
                        if (f.exists()) f.readText() else null
                    }.getOrNull()
                )
            }
            NotscapeTheme(store) {
                // Immersive full screen (Settings → Appearance): hide the
                // system bars and let content run edge to edge; the app's
                // own bars pad for the camera cutout and gesture area.
                val immersive = store.setting("immersive", "true") == "true"
                val view = LocalView.current
                SideEffect {
                    val controller = WindowInsetsControllerCompat(window, view)
                    if (immersive) {
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                        controller.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                crashReport?.let { report ->
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("Notscape Mariner crashed") },
                        text = {
                            Column(
                                modifier = Modifier.heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    "The last session ended in a crash. Sharing this " +
                                        "report helps get it fixed.",
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    report,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Notscape Mariner crash report")
                                    putExtra(Intent.EXTRA_TEXT, report)
                                }
                                runCatching {
                                    startActivity(Intent.createChooser(send, "Share crash report"))
                                }
                            }) { Text("Share report") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                runCatching {
                                    java.io.File(filesDir, "last_crash.txt").delete()
                                }
                                crashReport = null
                            }) { Text("Dismiss") }
                        }
                    )
                }
                BackHandler(enabled = screen != Screen.Browser) {
                    screen = Screen.Browser
                }
                // The browser stays composed underneath sub-screens so its
                // WebViews (and their loaded pages) survive the round trip —
                // no repaint flash when coming back.
                Box(modifier = Modifier.fillMaxSize()) {
                    BrowserScreen(
                        store = store,
                        onOpenScreen = { screen = it },
                        onReadArticle = { url ->
                            readerBookId = null
                            readerArticleUrl = url
                            screen = Screen.Reader
                        },
                        initialUrl = initialUrl,
                        pendingUrl = pendingUrl,
                        onPendingConsumed = { pendingUrl = null },
                        onOpenBook = { id ->
                            readerBookId = id
                            readerArticleUrl = null
                            screen = Screen.Reader
                        },
                        browserActive = screen == Screen.Browser
                    )
                    if (screen != Screen.Browser) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            when (screen) {
                                Screen.Browser -> Unit
                                Screen.Bookmarks -> BookmarksScreen(
                        store = store,
                        onBack = { screen = Screen.Browser },
                        onOpenUrl = { url ->
                            pendingUrl = url
                            screen = Screen.Browser
                        }
                    )
                    Screen.History -> HistoryScreen(
                        store = store,
                        onBack = { screen = Screen.Browser },
                        onOpenUrl = { url ->
                            pendingUrl = url
                            screen = Screen.Browser
                        }
                    )
                    Screen.Playlists -> PlaylistsScreen(
                        store = store,
                        onBack = { screen = Screen.Browser }
                    )
                    Screen.Library -> LibraryScreen(
                        store = store,
                        onBack = { screen = Screen.Browser }
                    )
                    Screen.Bookshelf -> BookshelfScreen(
                        store = store,
                        onBack = { screen = Screen.Browser },
                        onOpenBook = { id ->
                            readerBookId = id
                            readerArticleUrl = null
                            screen = Screen.Reader
                        }
                    )
                    Screen.Reader -> ReaderScreen(
                        store = store,
                        bookId = readerBookId,
                        articleUrl = readerArticleUrl,
                        onOpenUrl = { url ->
                            pendingUrl = url
                            screen = Screen.Browser
                        },
                        onBack = {
                            screen = if (readerBookId != null) Screen.Bookshelf else Screen.Browser
                        }
                    )
                    Screen.Vault -> VaultScreen(
                        store = store,
                        onBack = { screen = Screen.Browser }
                    )
                                Screen.Settings -> SettingsScreen(
                                    store = store,
                                    onBack = { screen = Screen.Browser }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sanitizeIncomingUrl(intent.dataString)?.let {
            pendingUrl = it
            screen = Screen.Browser
        }
    }

    /**
     * Only ordinary web URLs may enter from other apps. Anything else
     * (custom schemes, file:, intent:, javascript:…) is dropped, so an
     * outside app can't make the browser load something odd.
     */
    private fun sanitizeIncomingUrl(raw: String?): String? {
        val u = raw?.trim().orEmpty()
        if (u.isEmpty()) return null
        val scheme = u.substringBefore("://", "").lowercase()
        return if (scheme == "http" || scheme == "https") u else null
    }
}
