package net.notscape.browser

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import net.notscape.browser.ui.FrostDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.SideEffect
import net.notscape.browser.browser.readerThemeKey
import net.notscape.browser.browser.readerPalette
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
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
import net.notscape.browser.media.Downloads
import net.notscape.browser.browser.PrivateProfile
import net.notscape.browser.browser.UpdateChecker
import net.notscape.browser.data.AppStore
import net.notscape.browser.ui.NotscapeTheme
import net.notscape.browser.ui.BookmarksScreen
import net.notscape.browser.ui.BookshelfScreen
import net.notscape.browser.ui.HistoryScreen
import net.notscape.browser.ui.LibraryScreen
import net.notscape.browser.ui.PlaylistsScreen
import net.notscape.browser.ui.AppViewModel
import net.notscape.browser.ui.ReaderScreen
import net.notscape.browser.ui.Screen
import net.notscape.browser.ui.SettingsScreen
import net.notscape.browser.ui.VaultScreen

class MainActivity : FragmentActivity() {

    private val appViewModel: AppViewModel by viewModels()
    private lateinit var store: AppStore
    private var screen by mutableStateOf(Screen.Browser)
    private var pendingUrl by mutableStateOf<String?>(null)
    private var pendingBookUri by mutableStateOf<android.net.Uri?>(null)
    private var renewSignal by mutableStateOf(0)
    private var readerBookId by mutableStateOf<Long?>(null)
    private var readerArticleUrl by mutableStateOf<String?>(null)
    private var initialUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        harvestSystemCrashReport()
        // A previous session may have died with private tabs open. The
        // private WebView profile persists on disk, so wipe it now,
        // before any WebView exists in this process.
        PrivateProfile.wipe()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        store = appViewModel.store
        initialUrl = sanitizeIncomingUrl(intent?.dataString)
        pendingBookUri = bookUriFrom(intent)
        Downloads.sweepStaging(this, store)
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
                // The window itself wears the theme background
                // (0.0.956): no white frame can flash between
                // screens — the reader-exit flash lived here.
                val windowBg = MaterialTheme.colorScheme.background
                SideEffect {
                    window.setBackgroundDrawable(
                        android.graphics.drawable.ColorDrawable(windowBg.toArgb())
                    )
                }
                crashReport?.let { report ->
                    FrostDialog(
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
                // A book file handed over by the system (Open with):
                // import it to the bookshelf, say so, and show the
                // shelf with the new book on it.
                pendingBookUri?.let { uri ->
                    LaunchedEffect(uri) {
                        val result = kotlinx.coroutines.withContext(
                            kotlinx.coroutines.Dispatchers.IO
                        ) {
                            runCatching {
                                net.notscape.browser.ui.importBookFromUri(
                                    this@MainActivity, store, uri
                                )
                            }
                        }
                        pendingBookUri = null
                        result.onSuccess { msg ->
                            android.widget.Toast.makeText(
                                this@MainActivity, msg,
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            screen = Screen.Bookshelf
                        }.onFailure {
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "That file couldn't be added to the bookshelf",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
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
                        browserActive = screen == Screen.Browser,
                        renewSignal = renewSignal
                    )
                    if (screen != Screen.Browser) {
                        // Sub-screens sit on the kit's tinted
                        // backdrop (0.0.955): grouped cards
                        // (surfaceBright) read as cards on
                        // surfaceContainerLow, the pairing the
                        // sheets have used all along.
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = if (screen == Screen.Reader) {
                                // While reading, the surface behind
                                // wears the reader's own page colour,
                                // so leaving the reader can't flash
                                // the backdrop through (0.0.956).
                                Color(
                                    android.graphics.Color.parseColor(
                                        readerPalette(
                                            readerThemeKey(
                                                store.setting("readerTheme", "white")
                                            )
                                        ).bg
                                    )
                                )
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
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
                                    onBack = { screen = Screen.Browser },
                                    onRenew = {
                                        renewSignal++
                                        screen = Screen.Browser
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The in-app reporter (NotscapeApp) only sees crashes that surface as
     * Java exceptions. A crash in native code — the graphics layer, for
     * instance — kills the process without one, leaving no report. For
     * those, Android itself keeps an exit record (with a native
     * backtrace for native crashes) that an app may read for its own
     * package. Harvest the newest crash/ANR exit we haven't already
     * recorded into last_crash.txt so the dialog on this launch can
     * show it. A watermark file keeps each exit from being filed twice.
     */
    private fun harvestSystemCrashReport() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return
        runCatching {
            val am = getSystemService(android.app.ActivityManager::class.java) ?: return
            val exits = am.getHistoricalProcessExitReasons(packageName, 0, 5)
            val wmFile = java.io.File(filesDir, "exit_watermark.txt")
            val watermark = runCatching { wmFile.readText().trim().toLong() }.getOrDefault(0L)
            val crash = exits.firstOrNull {
                (it.reason == android.app.ApplicationExitInfo.REASON_CRASH ||
                    it.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE ||
                    it.reason == android.app.ApplicationExitInfo.REASON_ANR) &&
                    it.timestamp > watermark
            } ?: return
            wmFile.writeText(crash.timestamp.toString())
            val reportFile = java.io.File(filesDir, "last_crash.txt")
            // If the Java reporter already filed a fresh report for this
            // same event, it is the richer record — keep it.
            val fresh = reportFile.exists() &&
                kotlin.math.abs(reportFile.lastModified() - crash.timestamp) < 5 * 60_000L
            if (fresh && crash.reason == android.app.ApplicationExitInfo.REASON_CRASH) return
            val trace = runCatching {
                crash.traceInputStream?.bufferedReader()?.use { r ->
                    val sb = StringBuilder()
                    val buf = CharArray(8192)
                    var total = 0
                    while (total < 200_000) {
                        val n = r.read(buf)
                        if (n <= 0) break
                        sb.append(buf, 0, n)
                        total += n
                    }
                    sb.toString()
                }
            }.getOrNull().orEmpty()
            val kind = when (crash.reason) {
                android.app.ApplicationExitInfo.REASON_CRASH_NATIVE ->
                    "Native crash (from Android's exit records)"
                android.app.ApplicationExitInfo.REASON_ANR ->
                    "App not responding (from Android's exit records)"
                else -> "Crash (from Android's exit records)"
            }
            val header = buildString {
                append("Notscape Mariner ").append(BuildConfig.VERSION_NAME)
                append(" (build ").append(BuildConfig.VERSION_CODE).append(")\n")
                append(kind).append('\n')
                append("The in-app reporter saw no Java exception for this one — ")
                append("the fault was below the Java layer.\n")
                append("Exit: ").append(crash.description ?: "").append('\n')
                append("Process: ").append(crash.processName).append("\n\n")
            }
            reportFile.writeText((header + trace).take(120_000))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sanitizeIncomingUrl(intent.dataString)?.let {
            pendingUrl = it
            screen = Screen.Browser
        }
        bookUriFrom(intent)?.let { pendingBookUri = it }
    }

    /**
     * A VIEW intent carrying a book file (the Open-with flow). Only
     * EPUB-shaped handovers count: the EPUB MIME type, or a path
     * that ends in .epub. The import itself re-parses the file, so
     * a mislabelled handover fails politely rather than shelving
     * junk.
     */
    private fun bookUriFrom(intent: Intent?): android.net.Uri? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        val type = intent.type?.lowercase().orEmpty()
        val path = data.toString().lowercase().substringBefore('?')
        return if (type == "application/epub+zip" || path.endsWith(".epub")) {
            data
        } else {
            null
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
