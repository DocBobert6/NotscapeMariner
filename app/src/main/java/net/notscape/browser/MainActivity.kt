package net.notscape.browser

import android.content.Intent
import android.widget.Toast
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import net.notscape.browser.ui.WelcomeScreen
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
import net.notscape.browser.ui.FavoritesScreen
import net.notscape.browser.ui.ReadingListScreen
import net.notscape.browser.ui.BookshelfScreen
import net.notscape.browser.ui.HistoryScreen
import net.notscape.browser.ui.LibraryScreen
import net.notscape.browser.ui.DownloadsScreen
import net.notscape.browser.ui.PdfViewerScreen
import net.notscape.browser.ui.PlaylistsScreen
import net.notscape.browser.ui.AppViewModel
import net.notscape.browser.ui.ReaderScreen
import net.notscape.browser.ui.Screen
import net.notscape.browser.ui.SettingsScreen
import net.notscape.browser.browser.SelectionActions
import net.notscape.browser.ui.StartPageSettingsScreen
import net.notscape.browser.ui.GamesScreen
import net.notscape.browser.ui.LauncherPins
import net.notscape.browser.emu.GameActivity
import net.notscape.browser.emu.GameLibrary
import net.notscape.browser.emu.SaveStates
import net.notscape.browser.ui.UpdatesScreen
import net.notscape.browser.ui.VaultScreen

class MainActivity : FragmentActivity() {

    private val appViewModel: AppViewModel by viewModels()
    private lateinit var store: AppStore
    private var screen by mutableStateOf(Screen.Browser)
    private var pendingUrl by mutableStateOf<String?>(null)
    private var pendingAppMode by mutableStateOf(false)
    private var initialAppMode = false
    private var pendingBookUri by mutableStateOf<android.net.Uri?>(null)
    private var pendingPdfPath by mutableStateOf<String?>(null)
    private var pendingPdfUrl by mutableStateOf<String?>(null)
    private var renewSignal by mutableStateOf(0)
    private var erasePrivateSignal by mutableStateOf(0)
    private var trimMemorySignal by mutableStateOf(0)
    private var pendingOfflinePage by mutableStateOf<net.notscape.browser.data.OfflinePage?>(null)
    private var erasePrivateResult by mutableStateOf<Boolean?>(null)
    private var readerBookId by mutableStateOf<Long?>(null)
    private var readerArticleUrl by mutableStateOf<String?>(null)
    private var readerPrivateOrigin by mutableStateOf(false)
    private var pendingSection by mutableStateOf<Screen?>(null)
    private var pendingGameId by mutableStateOf<String?>(null)
    private var pendingGamesConsole by mutableStateOf<String?>(null)
    private var initialUrl: String? = null

    /** Beta.66 #60: the saved filter-list config, applied
     *  from the store. Called early (best effort) and again
     *  after awaitReady() (the guarantee). */
    private fun applySavedFilterConfig() {
        FilterEngine.setListConfig(
            FilterEngine.parseDisabledFilterLists(
                store.setting("filterListsOff", "")
            ),
            store.setting("customFilterUrl", ""),
            store.setting("customAbpRules", "")
        )
    }

    /** Beta.63 #25: delete-on-exit wipe — history, cookies,
     *  cache, site storage, and the open-tab session. Called at
     *  cold start (the guarantee: data never survives a session
     *  boundary) and from onDestroy when the activity finishes. */
    private fun wipeBrowsingData() {
        store.clearBrowsingData()
        runCatching { android.webkit.WebStorage.getInstance().deleteAllData() }
        runCatching {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
        }
        runCatching { net.notscape.browser.browser.WebCache.clear(this) }
    }

    /** Beta.66 #2: memory pressure — ask the browser to
     *  shed background tabs' WebViews (their state is
     *  captured first, so switching back restores them).
     *  Fires at RUNNING_LOW and anything worse. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            trimMemorySignal++
        }
    }

    override fun onDestroy() {
        if (isFinishing && ::store.isInitialized &&
            store.setting("deleteOnExit", "false") == "true"
        ) {
            runCatching { wipeBrowsingData() }
        }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        harvestSystemCrashReport()
        // A previous session may have died with private tabs open. The
        // private WebView profile persists on disk, so wipe it now,
        // before any WebView exists in this process.
        PrivateProfile.wipe()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        store = appViewModel.store
        // A previous session may have died with private
        // downloads in flight: wipe them once the store
        // has loaded (the profile wipe above cannot see
        // download records).
        Downloads.wipePrivateDownloadsAfterLoad(this, store)
        initialUrl = sanitizeIncomingUrl(intent?.dataString)
            ?: if (intent?.action == Intent.ACTION_SEND) {
                sanitizeIncomingUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
            } else null
        initialAppMode = intent?.getBooleanExtra(
            net.notscape.browser.browser.APP_MODE_EXTRA, false
        ) == true
        pendingBookUri = bookUriFrom(intent)
        // Audit S-m9: section/game extras are honoured only on
        // explicit intents to this activity (below).
        pendingSection = if (isOwnMainIntent(intent)) sectionFrom(intent) else null
        pendingGameId = if (isOwnMainIntent(intent)) intent?.getStringExtra(LauncherPins.GAME_EXTRA) else null
        // Audit P-m14: the filter-list config the last
        // session saved, applied before lists load (best
        // effort — the store may not have loaded yet; the
        // loaded config is re-applied after awaitReady(),
        // beta.66 #60).
        applySavedFilterConfig()
        FilterEngine.initAsync(this)
        UpdateChecker.checkAsync(store)
        // The store loads off the main thread now (0.0.988):
        // compose only once it holds the full truth, and run
        // the staging sweep against loaded data, not an
        // empty store.
        lifecycleScope.launch {
            store.awaitReady()
            // Beta.66 #60: the store has loaded now, so
            // the saved filter-list config actually takes
            // effect (the early call above read an empty
            // store on a cold start).
            applySavedFilterConfig()
            // Beta.63 #25: delete-on-exit — the previous
            // session's browsing data goes before anything
            // is composed, so it never survives a session
            // boundary even if the process was killed.
            if (store.setting("deleteOnExit", "false") == "true") {
                wipeBrowsingData()
            }
            // Beta.63 #26: blocked-count history, loaded before
            // anything is composed.
            FilterEngine.loadBlockedHistory(store.setting("blockedHistory", ""))
            Downloads.sweepStaging(this@MainActivity, store)
            // Storage housekeeping (0.0.996): font-import
            // staging files, uninstalled update downloads,
            // and half-installed voice packs never become
            // useful again; clear them at cold start.
            kotlinx.coroutines.withContext(
                kotlinx.coroutines.Dispatchers.IO
            ) {
                net.notscape.browser.data.StorageInfo
                    .tidy(this@MainActivity)
            }
            setContent {
            val rev = store.revision
            // Diagnostic trace (beta.55): one line per screen opened,
            // via the single screen state every switch routes through.
            LaunchedEffect(screen) {
                Trace.event("screen: ${screen.name}")
            }
            var crashReport by remember {
                mutableStateOf(
                    runCatching {
                        val f = java.io.File(filesDir, "last_crash.txt")
                        if (f.exists()) f.readText() else null
                    }.getOrNull()
                )
            }
            var storageErrorDismissed by remember {
                mutableStateOf(false)
            }
            NotscapeTheme(store) {
                // Immersive full screen (Settings → Appearance): hide the
                // system bars and let content run edge to edge; the app's
                // own bars pad for the camera cutout and gesture area.
                val immersive = store.setting("immersive", "false") == "true"
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
                if (store.storageError != null &&
                    !storageErrorDismissed
                ) {
                    FrostDialog(
                        onDismissRequest = {
                            storageErrorDismissed = true
                        },
                        title = { Text("Storage problem") },
                        text = {
                            Text(
                                store.storageError
                                    ?: "The library database could " +
                                        "not be opened."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                storageErrorDismissed = true
                            }) { Text("OK") }
                        }
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
                // First-run welcome (0.0.998): until the
                // "onboarded" setting is set, the welcome
                // replaces the browser. Settings > About
                // replays it by raising the same state - the
                // browser waits underneath, untouched.
                var showWelcome by remember {
                    mutableStateOf(
                        store.setting("onboarded", "false") != "true"
                    )
                }
                // Pinned-shortcut arrivals (beta.7): a
                // section switches screens, a game
                // launches straight into GameActivity.
                LaunchedEffect(pendingSection) {
                    pendingSection?.let {
                        screen = it
                        pendingSection = null
                    }
                }
                LaunchedEffect(pendingGameId) {
                    val id = pendingGameId ?: return@LaunchedEffect
                    pendingGameId = null
                    val game = GameLibrary.load(store)
                        .firstOrNull { it.id == id }
                    if (game == null) {
                        Toast.makeText(
                            this@MainActivity,
                            "That game is no longer in your library",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else if (!GameLibrary.isPlayable(game.system)) {
                        Toast.makeText(
                            this@MainActivity,
                            "That core is not in this build",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        startActivity(
                            Intent(
                                this@MainActivity,
                                GameActivity::class.java
                            )
                                .putExtra(
                                    GameActivity.EXTRA_ROM_PATH,
                                    game.romFile(this@MainActivity)
                                        .absolutePath
                                )
                                .putExtra(
                                    GameActivity.EXTRA_GAME_ID,
                                    game.id
                                )
                                .putExtra(
                                    GameActivity.EXTRA_GAME_TITLE,
                                    game.title
                                )
                                .putExtra(
                                    GameActivity.EXTRA_LOAD_SLOT,
                                    SaveStates.NO_SLOT
                                )
                        )
                    }
                }
                if (showWelcome) {
                    WelcomeScreen(
                        store = store,
                        onDone = {
                            store.putSetting("onboarded", "true")
                            showWelcome = false
                        }
                    )
                } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    BrowserScreen(
                        store = store,
                        onOpenScreen = { screen = it },
                        onReadArticle = { url, isPrivate ->
                            readerBookId = null
                            readerArticleUrl = url
                            readerPrivateOrigin = isPrivate
                            screen = Screen.Reader
                        },
                        initialUrl = initialUrl,
                        pendingUrl = pendingUrl,
                        initialAppMode = initialAppMode,
                        pendingAppMode = pendingAppMode,
                        onPendingConsumed = {
                            pendingUrl = null
                            pendingAppMode = false
                        },
                        pendingOfflinePage = pendingOfflinePage,
                        onPendingOfflineConsumed = {
                            pendingOfflinePage = null
                        },
                        onOpenBook = { id ->
                            readerBookId = id
                            readerArticleUrl = null
                            readerPrivateOrigin = false
                            screen = Screen.Reader
                        },
                        browserActive = screen == Screen.Browser,
                        renewSignal = renewSignal,
                        erasePrivateSignal = erasePrivateSignal,
                        trimMemorySignal = trimMemorySignal,
                        onErasePrivateDone = { ok ->
                            // The handshake's final wipe: covers
                            // the no-private-tabs case, and repeats
                            // the wipe closeTab already did, so a
                            // profile that survived a tab close is
                            // still wiped before the note shows.
                            PrivateProfile.wipe()
                            Downloads.wipePrivateDownloads(this@MainActivity, store)
                            erasePrivateResult = ok
                        },
                        onOpenGamesConsole = {
                            pendingGamesConsole = it
                            screen = Screen.Games
                        },
                        onOpenGame = { pendingGameId = it },
                        onOpenPdf = { path, url ->
                            pendingPdfPath = path
                            pendingPdfUrl = url
                            screen = Screen.PdfViewer
                        }
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
                    Screen.Favorites -> FavoritesScreen(
                        store = store,
                        onBack = { screen = Screen.Browser },
                        onOpenUrl = { url ->
                            pendingUrl = url
                            screen = Screen.Browser
                        }
                    )
                    Screen.ReadingList -> ReadingListScreen(
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
                        onBack = { screen = Screen.Browser },
                        onOpenUrl = { url ->
                            pendingUrl = url
                            screen = Screen.Browser
                        },
                        onOpenOfflinePage = { page ->
                            pendingOfflinePage = page
                            screen = Screen.Browser
                        }
                    )
                    Screen.PdfViewer -> PdfViewerScreen(
                        path = pendingPdfPath,
                        sourceUrl = pendingPdfUrl,
                        onBack = { screen = Screen.Browser },
                        onDownload = {
                            val url = pendingPdfUrl ?: return@PdfViewerScreen
                            val name = android.net.Uri.parse(url)
                                .lastPathSegment?.takeIf {
                                    it.endsWith(".pdf", ignoreCase = true)
                                } ?: "document.pdf"
                            net.notscape.browser.media.Downloads.start(
                                this@MainActivity, store, name, url,
                                media = false,
                                contentType = "application/pdf",
                                suggestedName = name,
                                referer = null,
                                isPrivate = false
                            )
                        }
                    )
                    Screen.Downloads -> DownloadsScreen(
                        store = store,
                        onBack = { screen = Screen.Browser },
                        onOpenGame = { pendingGameId = it },
                        onOpenGames = { screen = Screen.Games }
                    )
                    Screen.Bookshelf -> BookshelfScreen(
                        store = store,
                        onBack = { screen = Screen.Browser },
                        onOpenBook = { id ->
                            readerBookId = id
                            readerArticleUrl = null
                            readerPrivateOrigin = false
                            screen = Screen.Reader
                        }
                    )
                    Screen.Reader -> ReaderScreen(
                        store = store,
                        bookId = readerBookId,
                        articleUrl = readerArticleUrl,
                        privateOrigin = readerPrivateOrigin,
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
                    Screen.Games -> GamesScreen(
                        store = store,
                        onBack = { screen = Screen.Browser },
                        initialConsole = pendingGamesConsole,
                        onInitialConsoleConsumed = {
                            pendingGamesConsole = null
                        }
                    )
                                Screen.Settings -> SettingsScreen(
                                    store = store,
                                    onBack = { screen = Screen.Browser },
                                    onRenew = {
                                        renewSignal++
                                        screen = Screen.Browser
                                    },
                                    onErasePrivate = {
                                        erasePrivateResult = null
                                        erasePrivateSignal++
                                    },
                                    erasePrivateResult = erasePrivateResult,
                                    onErasePrivateResultConsumed = {
                                        erasePrivateResult = null
                                    },
                                    onOpenScreen = { screen = it },
                                    onOpenUrl = { url ->
                                        pendingUrl = url
                                        screen = Screen.Browser
                                    },
                                    onOpenUpdates = { screen = Screen.Updates },
                                    onOpenStartPage = { screen = Screen.StartPageSettings },
                                    onReplayWelcome = { showWelcome = true }
                                )
                                Screen.Updates -> UpdatesScreen(
                                    store = store,
                                    onBack = { screen = Screen.Browser }
                                )
                                Screen.StartPageSettings -> StartPageSettingsScreen(
                                    store = store,
                                    onBack = { screen = Screen.Settings }
                                )
                            }
                        }
                    }
                }
                }
            }
        }
        }
    }

    /**
     * Audit P-m22: Search + Share join the system
     * selection menu; Copy and the rest stay the
     * WebView's own. The wrapper delegates everything
     * else to the WebView's callback untouched.
     */
    override fun onWindowStartingActionMode(
        callback: android.view.ActionMode.Callback
    ): android.view.ActionMode? {
        val wrapped = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(
                mode: android.view.ActionMode,
                menu: android.view.Menu
            ): Boolean {
                val ok = callback.onCreateActionMode(mode, menu)
                menu.add(0, 1001, 100, "Search")
                menu.add(0, 1002, 101, "Share")
                return ok
            }

            override fun onPrepareActionMode(
                mode: android.view.ActionMode,
                menu: android.view.Menu
            ): Boolean = callback.onPrepareActionMode(mode, menu)

            override fun onActionItemClicked(
                mode: android.view.ActionMode,
                item: android.view.MenuItem
            ): Boolean {
                return when (item.itemId) {
                    1001 -> {
                        SelectionActions.searchSelected?.invoke()
                        mode.finish()
                        true
                    }
                    1002 -> {
                        SelectionActions.shareSelected?.invoke()
                        mode.finish()
                        true
                    }
                    else -> callback.onActionItemClicked(mode, item)
                }
            }

            override fun onDestroyActionMode(mode: android.view.ActionMode) {
                callback.onDestroyActionMode(mode)
            }
        }
        return super.onWindowStartingActionMode(wrapped)
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
            pendingAppMode = intent.getBooleanExtra(
                net.notscape.browser.browser.APP_MODE_EXTRA, false
            )
            screen = Screen.Browser
        }
        if (intent.action == Intent.ACTION_SEND) {
            sanitizeIncomingUrl(intent.getStringExtra(Intent.EXTRA_TEXT))?.let {
                pendingUrl = it
                screen = Screen.Browser
            }
        }
        bookUriFrom(intent)?.let { pendingBookUri = it }
        if (isOwnMainIntent(intent)) sectionFrom(intent)?.let { pendingSection = it }
        if (isOwnMainIntent(intent)) intent.getStringExtra(LauncherPins.GAME_EXTRA)?.let {
            pendingGameId = it
        }
    }

    /**
     * Audit P-m18: leaving the app while a fullscreen WebView
     * video is showing drops into 16:9 PiP. The fullscreen view
     * itself is never removed, so closing PiP shows it again
     * (device confirmation owed).
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            net.notscape.browser.browser.FullscreenState.isFullscreen
        ) {
            runCatching {
                enterPictureInPictureMode(
                    android.app.PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9))
                        .build()
                )
            }
        }
    }

    /**
     * A VIEW intent carrying a book file (the Open-with flow). Only
     * EPUB-shaped handovers count: the EPUB MIME type, or a path
     * that ends in .epub. The import itself re-parses the file, so
     * a mislabelled handover fails politely rather than shelving
     * junk.
     */
    /**
     * Audit S-m9: section/game extras are honoured only on explicit
     * intents to this activity — our own launcher shortcuts and
     * download notifications. An implicit VIEW intent from another
     * app carrying a forged extra must not steer the UI.
     */
    private fun isOwnMainIntent(intent: Intent?): Boolean =
        intent?.component?.className == MainActivity::class.java.name

    /** A launcher section pin's target screen, if
     * this intent carries one (beta.7). */
    private fun sectionFrom(intent: Intent?): Screen? {
        return when (
            intent?.getStringExtra(LauncherPins.SECTION_EXTRA)
        ) {
            "playlists" -> Screen.Playlists
            "library" -> Screen.Library
            "bookshelf" -> Screen.Bookshelf
            "games" -> Screen.Games
            else -> null
        }
    }

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

}

/**
 * True fullscreen for video surfaces (0.0.987, Robert):
 * hide or restore the system bars. A swipe from a screen
 * edge brings them back briefly either way.
 */
fun setSystemBarsHidden(activity: android.app.Activity, hidden: Boolean) {
    val controller = WindowCompat.getInsetsController(
        activity.window, activity.window.decorView
    )
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (hidden) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

/** Audit P-M3/P-M9: the one gate for URLs entering from
 * outside the browser - other apps (VIEW/SEND intents)
 * and scanned QR codes. Only plain http(s) passes;
 * custom schemes, file:, intent:, javascript:... are dropped. */
internal fun sanitizeIncomingUrl(raw: String?): String? {
    val u = raw?.trim().orEmpty()
    if (u.isEmpty()) return null
    val scheme = u.substringBefore("://", "").lowercase()
    return if (scheme == "http" || scheme == "https") u else null
}
