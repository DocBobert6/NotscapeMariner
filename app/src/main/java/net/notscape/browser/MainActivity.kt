package net.notscape.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
        store = AppStore.get(this)
        initialUrl = intent?.dataString
        FilterEngine.initAsync(this)
        UpdateChecker.checkAsync(store)
        setContent {
            val rev = store.revision
            NotscapeTheme(store) {
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
        intent.dataString?.let {
            pendingUrl = it
            screen = Screen.Browser
        }
    }
}
