package net.notscape.browser.browser

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.debounce
import net.notscape.browser.data.AppStore
import net.notscape.browser.data.TabSnapshot
import net.notscape.browser.media.Downloads
import net.notscape.browser.media.PlayerActivity
import net.notscape.browser.ui.Screen

class TabState(
    val id: Int,
    initialUrl: String = "",
    val isPrivate: Boolean = false,
    /** A tab created to host a real popup window (window.open): it is
     *  driven by the opener page, so it must never render the start page
     *  while its URL is still blank. */
    val isPopupWin: Boolean = false
) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf("")
    var desktopMode by mutableStateOf(false)
    var errorText by mutableStateOf<String?>(null)
    val isStartPage: Boolean get() = !isPopupWin && (url.isBlank() || url == "about:blank")
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    store: AppStore,
    onOpenScreen: (Screen) -> Unit,
    onReadArticle: (String) -> Unit,
    initialUrl: String?,
    pendingUrl: String? = null,
    onPendingConsumed: () -> Unit = {},
    onOpenBook: (Long) -> Unit = {},
    browserActive: Boolean = true
) {
    val context = LocalContext.current

    /** The user's custom homepage, or "" for the built-in start page. */
    fun homeUrl(): String {
        val raw = store.setting("homePage", "").trim()
        if (raw.isBlank()) return ""
        return if (raw.startsWith("http://") || raw.startsWith("https://") ||
            raw.startsWith("about:")
        ) raw else "https://$raw"
    }

    val restoreEnabled = store.setting("restoreTabs", "true") == "true"
    val tabs = remember {
        val list = mutableStateListOf<TabState>()
        when {
            !initialUrl.isNullOrBlank() -> list.add(TabState(1, initialUrl))
            restoreEnabled && store.tabSession.isNotEmpty() -> {
                store.tabSession.forEachIndexed { i, snap ->
                    list.add(TabState(i + 1, snap.url).apply { title = snap.title })
                }
            }
            else -> list.add(TabState(1, homeUrl()))
        }
        list
    }
    var nextTabId by remember { mutableStateOf(tabs.size + 1) }
    var activeTabId by remember {
        mutableStateOf(
            if (initialUrl.isNullOrBlank() && restoreEnabled && store.tabSession.isNotEmpty()) {
                tabs[store.tabSessionActive.coerceIn(0, tabs.size - 1)].id
            } else tabs.first().id
        )
    }
    val webViews = remember { mutableStateMapOf<Int, WebView>() }
    var progress by remember { mutableStateOf(0) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(initialUrl ?: "")) }
    var barFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var menuOpen by remember { mutableStateOf(false) }
    var showTabs by remember { mutableStateOf(false) }
    var barHidden by remember { mutableStateOf(false) }
    var gridPrivate by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf<List<DetectedMedia>>(emptyList()) }
    var showDetected by remember { mutableStateOf(false) }
    var playlistPickFor by remember { mutableStateOf<DetectedMedia?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }
    var findText by remember { mutableStateOf("") }
    var showFind by remember { mutableStateOf(false) }
    var containerRef by remember { mutableStateOf<FrameLayout?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var pendingPopupUrl by remember { mutableStateOf<String?>(null) }
    var popupPageHost by remember { mutableStateOf("") }
    var showPopupNotice by remember { mutableStateOf(false) }
    var contextTargetUrl by remember { mutableStateOf<String?>(null) }
    var contextTargetIsImage by remember { mutableStateOf(false) }
    val hazeState = dev.chrisbanes.haze.rememberHazeState()
    var readabilitySrc by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        readabilitySrc = withContext(Dispatchers.IO) {
            runCatching { ArticleExtractor.loadLibrary(context) }.getOrNull()
        }
    }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    // File uploads (<input type="file">): the WebChromeClient hands the
    // page's callback here and the activity-result launcher picks the file.
    var fileChooserCallback by remember { mutableStateOf<android.webkit.ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = fileChooserCallback
        fileChooserCallback = null
        cb?.onReceiveValue(
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            } else null
        )
    }

    val activeTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
    val rev = store.revision // observe store changes
    val mediaRev = MediaLog.revision

    fun activeWebView(): WebView? = webViews[activeTab.id]

    fun displayFor(url: String): String =
        if (url.isBlank() || url == "about:blank") ""
        else (Uri.parse(url).host?.removePrefix("www.") ?: url)

    fun showFieldDisplay() {
        if (!barFocused) fieldValue = TextFieldValue(displayFor(activeTab.url))
    }

    fun normalizeInput(raw: String): String {
        val q = raw.trim()
        if (q.isEmpty()) return q
        if (q.startsWith("http://") || q.startsWith("https://") || q.startsWith("about:")) return q
        val looksLikeHost = !q.contains(" ") && (q.contains(".") || q == "localhost")
        if (looksLikeHost) return "https://$q"
        val engine = store.setting("searchEngine", "duckduckgo")
        val template = when (engine) {
            "google" -> "https://www.google.com/search?q=%s"
            "bing" -> "https://www.bing.com/search?q=%s"
            "startpage" -> "https://www.startpage.com/sp/search?query=%s"
            else -> "https://duckduckgo.com/?q=%s"
        }
        return template.format(Uri.encode(q))
    }

    fun loadUrl(raw: String) {
        val url = normalizeInput(raw)
        if (url.isEmpty()) return
        activeTab.url = url
        if (!barFocused) fieldValue = TextFieldValue(displayFor(url))
        val wv = webViews[activeTab.id]
        if (wv != null && wv.url != url) wv.loadUrl(url)
    }

    fun openNewTab(url: String, isPrivate: Boolean = false) {
        val start = if (url.isBlank() && !isPrivate) homeUrl() else url
        val t = TabState(nextTabId++, start, isPrivate)
        tabs.add(t)
        activeTabId = t.id
        if (!barFocused) fieldValue = TextFieldValue(displayFor(url))
    }

    fun closeTab(tab: TabState) {
        webViews.remove(tab.id)?.destroy()
        MediaLog.clear(tab.id)
        tabs.remove(tab)
        if (tabs.isEmpty()) {
            val t = TabState(nextTabId++, homeUrl())
            tabs.add(t)
            // That was the last tab: leave the tab grid and land on the
            // homepage (or the start page) with the fresh tab.
            showTabs = false
            activeTabId = t.id
            if (!barFocused) fieldValue = TextFieldValue(displayFor(t.url))
        }
        if (activeTabId == tab.id) {
            activeTabId = tabs.first().id
            if (!barFocused) fieldValue = TextFieldValue(displayFor(tabs.first().url))
        }
    }

    LaunchedEffect(pendingUrl) {
        if (!pendingUrl.isNullOrBlank()) {
            loadUrl(pendingUrl)
            onPendingConsumed()
        }
    }

    // Persist the session (non-private tabs only) for restore on next launch.
    LaunchedEffect(Unit) {
        snapshotFlow {
            val open = tabs.filter { !it.isPrivate }
            open.map { TabSnapshot(it.url, it.title) } to
                open.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)
        }.debounce(700).collect { (snaps, idx) ->
            if (snaps.isNotEmpty()) store.saveSession(snaps, idx)
        }
    }

    // The debounced saver alone can lose the last change if Android kills
    // the process soon after the app is backgrounded — save again, right
    // now, the moment the app pauses.
    @Suppress("DEPRECATION")
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                val open = tabs.filter { !it.isPrivate }
                if (open.isNotEmpty()) {
                    store.saveSession(
                        open.map { TabSnapshot(it.url, it.title) },
                        open.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0)
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // On a tab switch, sync the nav buttons with the newly shown tab and
    // bring the address bar back.
    LaunchedEffect(activeTabId) {
        barHidden = false
        val wv = webViews[activeTabId]
        canGoBack = wv?.canGoBack() ?: false
        canGoForward = wv?.canGoForward() ?: false
    }

    // Select-all when the bar gains focus. The tap that focuses the field
    // also places the cursor, wiping the selection set at focus time — so
    // re-assert it a beat later.
    LaunchedEffect(barFocused) {
        if (barFocused) {
            kotlinx.coroutines.delay(60)
            val text = fieldValue.text
            if (barFocused && text.isNotEmpty()) {
                fieldValue = TextFieldValue(text, TextRange(0, text.length))
            }
        }
    }

    // Apply the cookies setting globally.
    LaunchedEffect(store.setting("cookies", "true")) {
        CookieManager.getInstance().setAcceptCookie(store.setting("cookies", "true") == "true")
    }

    // Popup notice auto-dismiss.
    LaunchedEffect(showPopupNotice) {
        if (showPopupNotice) {
            kotlinx.coroutines.delay(12_000)
            showPopupNotice = false
        }
    }

    fun createWebView(tab: TabState): WebView {
        val wv = WebView(context)
        wv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        val host = Uri.parse(tab.url).host ?: ""
        val site = store.siteSetting(host)
        wv.settings.apply {
            javaScriptEnabled = site.javaScript
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // Required for onCreateWindow to fire at all: without multiple-
            // window support, popup links (window.open / target=_blank)
            // navigate the current tab instead of being intercepted.
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            blockNetworkImage = site.blockImages
            if (tab.desktopMode) {
                userAgentString =
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
            }
        }
        wv.setOnLongClickListener {
            val hr = wv.hitTestResult
            val extra = hr.extra
            val isLinkOrImage = hr.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                hr.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ||
                hr.type == WebView.HitTestResult.IMAGE_TYPE
            if (extra != null && isLinkOrImage) {
                contextTargetUrl = extra
                contextTargetIsImage = hr.type != WebView.HitTestResult.SRC_ANCHOR_TYPE
                true
            } else false
        }
        // Safari-style: the address bar slides away while scrolling down
        // a page, and comes back on the first scroll up.
        wv.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            val isMedia = mimeType?.startsWith("video/") == true ||
                mimeType?.startsWith("audio/") == true
            Downloads.start(
                context, store, tab.title, url,
                media = isMedia,
                contentType = mimeType,
                suggestedName = URLUtil.guessFileName(url, contentDisposition, mimeType),
                referer = tab.url
            )
            Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
        }
        wv.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            when {
                scrollY > oldScrollY + 6 && scrollY > 80 -> barHidden = true
                scrollY < oldScrollY - 6 -> barHidden = false
            }
        }
        // Cosmetic filtering: hide the ad containers the lists know about
        // on this host (network blocking alone leaves the empty boxes).
        fun injectCosmetic(view: WebView, url: String?) {
            if (url == null || store.setting("blockAds", "true") != "true") return
            val host = Uri.parse(url).host ?: ""
            if (host.isEmpty() || store.siteSetting(host).disableBlocking) return
            val css = FilterEngine.cosmeticCss(host)
            if (css.isEmpty()) return
            val js = "(function(){try{" +
                "var s=document.getElementById('__ns_cos');" +
                "if(!s){s=document.createElement('style');s.id='__ns_cos';" +
                "(document.head||document.documentElement).appendChild(s);}" +
                "s.textContent=" + org.json.JSONObject.quote(css) + ";" +
                "}catch(e){}})()"
            view.evaluateJavascript(js, null)
        }
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                return when (u.scheme) {
                    "http", "https", "about", "data" -> false
                    else -> {
                        // External schemes (tel:, mailto:, app deep links…)
                        // open only from a real user tap on the main frame —
                        // never from redirects, scripts or subframes, so a
                        // page can't launch other apps unprompted.
                        if (request.isForMainFrame && request.hasGesture()) {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                        true
                    }
                }
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val blockAds = store.setting("blockAds", "true") == "true"
                val blockTrackers = store.setting("blockTrackers", "true") == "true"
                // NOTE: this callback runs on a background thread — never
                // call methods on `view` here (release builds enforce the
                // WebView thread check and crash). Use our tracked tab URL.
                val pageHost = Uri.parse(tab.url).host ?: ""
                val blockingOff = store.siteSetting(pageHost).disableBlocking
                if (!blockingOff && FilterEngine.isBlocked(request.url, request.isForMainFrame, blockAds, blockTrackers, pageHost)) {
                    return FilterEngine.emptyResponse()
                }
                if (!request.isForMainFrame) {
                    MediaLog.inspect(tab.id, request.url.toString())
                }
                return null
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url != null) {
                    tab.url = url
                    progress = 5
                    tab.errorText = null
                    if (!barFocused) fieldValue = TextFieldValue(displayFor(url))
                    MediaLog.clear(tab.id)
                    // Cosmetic rules go in at document start too, so ad
                    // boxes never flash before the page finishes loading.
                    injectCosmetic(view, url)
                }
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                // Keeps back/forward state fresh for SPA navigations that
                // never fire onPageFinished (history.pushState).
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                if (request.isForMainFrame) {
                    tab.errorText = "${error.description} (error ${error.errorCode})"
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress = 100
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
                val t = view.title ?: ""
                if (url != null && !url.startsWith("about:")) {
                    tab.title = t
                    if (!tab.isPrivate) store.addHistory(t, url)
                }
                // Cosmetic filtering (also injected at document start;
                // re-asserted here for pages whose DOM was rebuilt).
                injectCosmetic(view, url)
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress = newProgress
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: android.webkit.ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                // Cancel any chooser still pending from an earlier tap.
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback
                val intent = runCatching { fileChooserParams.createIntent() }.getOrNull()
                if (intent == null) {
                    fileChooserCallback = null
                    filePathCallback.onReceiveValue(null)
                    return false
                }
                runCatching { fileChooserLauncher.launch(intent) }
                    .onFailure {
                        fileChooserCallback = null
                        filePathCallback.onReceiveValue(null)
                    }
                return true
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                if (title != null) tab.title = title
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val strict = store.setting("popupStrict", "true") == "true"
                val pageHost = Uri.parse(tab.url).host ?: ""
                val allowed = !strict || store.siteSetting(pageHost).allowPopups
                if (allowed) {
                    // Allowed popups get a REAL window: a new tab whose
                    // WebView is the popup, driven by the opener page.
                    // The old capture-and-reopen approach left pages that
                    // depend on the window (logins, players) hanging on a
                    // dead "not responding" screen.
                    val t = TabState(nextTabId++, "", tab.isPrivate, isPopupWin = true)
                    tabs.add(t)
                    activeTabId = t.id
                    val pwv = webViews.getOrPut(t.id) { createWebView(t) }
                    transport.webView = pwv
                    resultMsg.sendToTarget()
                    return true
                }
                // Blocked: capture the target if the popup tries to
                // navigate somewhere, and show the notice either way —
                // JS-driven popups never navigate, and the user still
                // deserves to know (and to whitelist the site). Sites the
                // user has blacklisted ("Always deny") are silenced
                // completely: no notice, ever.
                val alwaysDenied = store.siteSetting(pageHost).blockPopups
                popupPageHost = pageHost
                pendingPopupUrl = null
                val popupWv = WebView(view.context)
                transport.webView = popupWv
                popupWv.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(pv: WebView, request: WebResourceRequest): Boolean {
                        val u = request.url.toString()
                        runCatching { popupWv.destroy() }
                        if (u.isNotBlank() && u != "about:blank") pendingPopupUrl = u
                        if (!alwaysDenied) showPopupNotice = true
                        return true
                    }
                }
                if (!alwaysDenied) showPopupNotice = true
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreenView = view
                fullscreenCallback = callback
                containerRef?.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }

            override fun onHideCustomView() {
                fullscreenView?.let { containerRef?.removeView(it) }
                fullscreenView = null
                fullscreenCallback?.onCustomViewHidden()
                fullscreenCallback = null
            }
        }
        if (tab.url.isNotBlank() && tab.url != "about:blank") {
            wv.loadUrl(tab.url)
        }
        return wv
    }

    // System back (button or edge-swipe gesture): leave fullscreen first,
    // then close whatever UI is open, then walk the page history. Enabled
    // state comes from snapshot state (canGoBack is kept fresh by
    // doUpdateVisitedHistory), so the gesture is never swallowed by a
    // stale check — and when nothing is open and there's no history, back
    // falls through and closes the app as usual.
    val anyOverlayOpen = showTabs || menuOpen || showDetected || showFind ||
        playlistPickFor != null || contextTargetUrl != null || showPopupNotice
    BackHandler(
        enabled = browserActive && (fullscreenView != null || anyOverlayOpen || canGoBack)
    ) {
        when {
            fullscreenView != null -> {
                fullscreenView?.let { containerRef?.removeView(it) }
                fullscreenView = null
                fullscreenCallback?.onCustomViewHidden()
                fullscreenCallback = null
            }
            showPopupNotice -> showPopupNotice = false
            contextTargetUrl != null -> contextTargetUrl = null
            playlistPickFor != null -> playlistPickFor = null
            showDetected -> showDetected = false
            showFind -> {
                showFind = false
                activeWebView()?.clearMatches()
            }
            menuOpen -> menuOpen = false
            showTabs -> showTabs = false
            else -> activeWebView()?.goBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViews.values.forEach { it.destroy() }
            webViews.clear()
        }
    }

    fun runMediaScan() {
        activeWebView()?.evaluateJavascript(MediaDetect.JS) { raw ->
            val dom = parseDetectedMedia(raw)
            val net = MediaLog.forTab(activeTab.id)
            detected = (dom + net).distinctBy { it.url }
            showDetected = true
        }
    }

    fun shareCurrentPage() {
        if (activeTab.url.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, activeTab.title)
            putExtra(Intent.EXTRA_TEXT, activeTab.url)
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, "Share page").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    val barAtBottom = store.setting("barPosition", "bottom") != "top"
    val immersive = store.setting("immersive", "true") == "true"

    @Composable
    fun addressBar() {
        // Safari iOS 26 Compact language, Material-blur material: floating
        // capsule groups over a frosted backdrop. On scroll the bar
        // collapses to the URL pill alone; tapping it re-expands.
        val collapsed = barHidden && !barFocused && !activeTab.isStartPage
        val pillShape = RoundedCornerShape(50)
        val contentColor = MaterialTheme.colorScheme.onSurface
        val surfaceTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f)
        val frost = Modifier
            .shadow(6.dp, pillShape)
            .clip(pillShape)
            .hazeEffect(
                state = hazeState,
                style = dev.chrisbanes.haze.HazeStyle(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    tints = listOf(dev.chrisbanes.haze.HazeTint(surfaceTint)),
                    blurRadius = 24.dp,
                    noiseFactor = 0f,
                    fallbackTint = dev.chrisbanes.haze.HazeTint(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    )
                )
            )
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), pillShape)
        var dragTotal by remember { mutableStateOf(0f) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!collapsed && (canGoBack || canGoForward)) {
                Row(modifier = frost) {
                    if (canGoBack) {
                        IconButton(onClick = { activeWebView()?.goBack() }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = contentColor, modifier = Modifier.size(24.dp))
                        }
                    }
                    if (canGoForward) {
                        IconButton(onClick = { activeWebView()?.goForward() }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Forward", tint = contentColor, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
            Box(
                modifier = Modifier.weight(1f).then(frost)
                    .pointerInput(tabs.size, activeTabId) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragTotal = 0f },
                            onHorizontalDrag = { _, amount -> dragTotal += amount },
                            onDragEnd = {
                                // Swiping the pill moves within the current
                                // tab class only (normal ↔ normal,
                                // private ↔ private).
                                val siblings = tabs.filter { it.isPrivate == activeTab.isPrivate }
                                val idx = siblings.indexOfFirst { it.id == activeTabId }
                                val target = when {
                                    dragTotal < -90f && idx in 0 until siblings.size - 1 -> siblings[idx + 1]
                                    dragTotal > 90f && idx > 0 -> siblings[idx - 1]
                                    else -> null
                                }
                                if (target != null) {
                                    activeTabId = target.id
                                    if (!barFocused) fieldValue = TextFieldValue(displayFor(target.url))
                                }
                                dragTotal = 0f
                            },
                            onDragCancel = { dragTotal = 0f }
                        )
                    }
            ) {
                // Loading progress fills the pill itself, in the accent.
                if (progress in 1..99) {
                    Box(modifier = Modifier.matchParentSize().clip(pillShape)) {
                        Box(
                            modifier = Modifier.fillMaxHeight()
                                .fillMaxWidth(progress / 100f)
                                .background(
                                    Brush.horizontalGradient(
                                        0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                        0.7f to MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                        1f to MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                                    )
                                )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!collapsed && !barFocused && store.setting("showHomeButton", "true") == "true") {
                        IconButton(
                            onClick = {
                                val home = homeUrl()
                                if (home.isBlank()) {
                                    activeTab.url = ""
                                    activeTab.errorText = null
                                    fieldValue = TextFieldValue("")
                                } else {
                                    loadUrl(home)
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Filled.Home, contentDescription = "Home", tint = contentColor, modifier = Modifier.size(19.dp))
                        }
                    }
                    if (activeTab.isPrivate) {
                        Icon(
                            Icons.Filled.Lock, contentDescription = "Private tab",
                            tint = contentColor, modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { fieldValue = it },
                        modifier = Modifier.weight(1f).onFocusChanged { st ->
                            if (st.isFocused && !barFocused) {
                                barFocused = true
                                fieldValue = TextFieldValue(
                                    activeTab.url,
                                    TextRange(0, activeTab.url.length)
                                )
                            } else if (!st.isFocused && barFocused) {
                                barFocused = false
                                fieldValue = TextFieldValue(displayFor(activeTab.url))
                            }
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = contentColor,
                            fontSize = 16.sp,
                            textAlign = if (barFocused) TextAlign.Start else TextAlign.Center
                        ),
                        cursorBrush = SolidColor(contentColor),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Go,
                            keyboardType = KeyboardType.Uri,
                            autoCorrectEnabled = false,
                            capitalization = KeyboardCapitalization.None
                        ),
                        keyboardActions = KeyboardActions(onGo = {
                            loadUrl(fieldValue.text)
                            focusManager.clearFocus()
                        }),
                        decorationBox = { inner ->
                            if (fieldValue.text.isEmpty()) {
                                Text(
                                    "Search or enter address",
                                    color = contentColor.copy(alpha = 0.55f),
                                    fontSize = 16.sp, maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            inner()
                        }
                    )
                    if (barFocused && fieldValue.text.isNotEmpty()) {
                        IconButton(
                            onClick = { fieldValue = TextFieldValue("") },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = contentColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            if (barFocused) {
                TextButton(onClick = { focusManager.clearFocus() }) { Text("Cancel") }
            } else if (!collapsed) {
                Box(
                    modifier = Modifier.size(44.dp).then(frost)
                        .clickable {
                            gridPrivate = activeTab.isPrivate
                            showTabs = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = contentColor.copy(alpha = 0.14f),
                        modifier = Modifier.size(25.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${tabs.count { it.isPrivate == activeTab.isPrivate }}",
                                style = MaterialTheme.typography.titleSmall,
                                color = contentColor
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier.size(44.dp).then(frost)
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = "Menu", tint = contentColor, modifier = Modifier.size(22.dp))
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
            when {
                activeTab.isStartPage -> {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .then(
                                if (barAtBottom || !immersive) Modifier.windowInsetsPadding(WindowInsets.statusBars)
                                else Modifier
                            )
                            .padding(top = if (barAtBottom) 0.dp else 92.dp, bottom = if (barAtBottom) 92.dp else 0.dp)
                    ) {
                        StartPage(
                            store = store,
                            onOpen = { url -> loadUrl(url) },
                            onOpenBook = onOpenBook
                        )
                    }
                }
                activeTab.errorText != null -> Box(
                    modifier = Modifier.fillMaxSize()
                        .then(
                            if (!immersive) Modifier.windowInsetsPadding(WindowInsets.systemBars)
                            else Modifier
                        )
                ) {
                    ErrorPage(
                        url = activeTab.url,
                        message = activeTab.errorText ?: "",
                        onRetry = {
                            val wv = activeWebView()
                            activeTab.errorText = null
                            if (wv != null) wv.reload() else loadUrl(activeTab.url)
                        },
                        onHome = {
                            activeTab.errorText = null
                            activeTab.url = ""
                            if (!barFocused) fieldValue = TextFieldValue("")
                        }
                    )
                }
                else -> {
                    AndroidView(
                        factory = { ctx ->
                            FrameLayout(ctx).also { containerRef = it }
                        },
                        update = { container ->
                            containerRef = container
                            val wv = webViews.getOrPut(activeTab.id) { createWebView(activeTab) }
                            if (wv.parent !== container) {
                                (wv.parent as? ViewGroup)?.removeView(wv)
                                // Detach the previously shown tab's WebView —
                                // without this it stays stacked on top and
                                // the switch never becomes visible. (The
                                // fullscreen video view is not a WebView
                                // and is left alone.)
                                for (i in container.childCount - 1 downTo 0) {
                                    val child = container.getChildAt(i)
                                    if (child is WebView && child !== wv) {
                                        container.removeView(child)
                                    }
                                }
                                container.addView(wv, 0)
                                // A WebView created in the background can
                                // attach showing a blank white page until
                                // it is resumed; nudge it on activation.
                                runCatching { wv.onResume() }
                            }
                            if (wv.url == null && activeTab.url.isNotBlank()) {
                                wv.loadUrl(activeTab.url)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                            .then(
                                // Outside immersive mode the page must
                                // clear the status bar, camera cutout and
                                // navigation bar — not just the home page.
                                if (!immersive) Modifier.windowInsetsPadding(WindowInsets.systemBars)
                                else Modifier
                            )
                    )
                }
            }
        }

        if (showPopupNotice) {
            Box(
                modifier = Modifier
                    .align(if (barAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                    .padding(top = if (barAtBottom) 0.dp else 92.dp, bottom = if (barAtBottom) 92.dp else 0.dp)
                    .fillMaxWidth()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (popupPageHost.isNotBlank()) "Popup blocked on $popupPageHost" else "Popup blocked",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showPopupNotice = false }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                            }
                        }
                        Row {
                            if (pendingPopupUrl != null) {
                                TextButton(onClick = {
                                    pendingPopupUrl?.let { openNewTab(it, activeTab.isPrivate) }
                                    showPopupNotice = false
                                    pendingPopupUrl = null
                                }) { Text("Open once") }
                            }
                            TextButton(onClick = {
                                // Whitelist the site, then open the popup
                                // (if we know its URL) so the tap the user
                                // just made still lands where they aimed.
                                val host = popupPageHost
                                if (host.isNotBlank()) {
                                    store.putSiteSetting(
                                        store.siteSetting(host).copy(allowPopups = true, blockPopups = false)
                                    )
                                }
                                pendingPopupUrl?.let { openNewTab(it, activeTab.isPrivate) }
                                showPopupNotice = false
                                pendingPopupUrl = null
                            }) { Text("Always allow") }
                        }
                        Row {
                            TextButton(onClick = {
                                showPopupNotice = false
                                pendingPopupUrl = null
                            }) { Text("Deny") }
                            TextButton(onClick = {
                                // Blacklist the site: its popups are
                                // blocked silently from now on (undo in
                                // Settings → Per-site controls).
                                val host = popupPageHost
                                if (host.isNotBlank()) {
                                    store.putSiteSetting(
                                        store.siteSetting(host).copy(allowPopups = false, blockPopups = true)
                                    )
                                }
                                showPopupNotice = false
                                pendingPopupUrl = null
                            }) { Text("Always deny") }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            // The bar now stays present and collapses internally to the
            // URL pill on scroll (iOS 26 behavior); it only leaves for
            // HTML5 fullscreen video.
            visible = fullscreenView == null,
            modifier = Modifier
                .align(if (barAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                .then(
                    if (barAtBottom) Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        .imePadding()
                    else if (!immersive) Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    else Modifier
                )
                .padding(top = if (barAtBottom) 0.dp else 10.dp, bottom = if (barAtBottom) 10.dp else 0.dp),
            enter = slideInVertically { if (barAtBottom) it else -it } + fadeIn(),
            exit = slideOutVertically { if (barAtBottom) it else -it } + fadeOut()
        ) {
            addressBar()
        }

        // ---- Tab grid overlay ----
        if (showTabs) {
            Box(
                modifier = Modifier.fillMaxSize()
                                        .hazeEffect(
                        state = hazeState,
                        style = dev.chrisbanes.haze.HazeStyle(
                            backgroundColor = MaterialTheme.colorScheme.background,
                            tints = listOf(
                                dev.chrisbanes.haze.HazeTint(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.30f)
                                )
                            ),
                            blurRadius = 28.dp,
                            noiseFactor = 0f,
                            fallbackTint = dev.chrisbanes.haze.HazeTint(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                            )
                        )
                    )
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .padding(16.dp)
                ) {
                    if (UpdateChecker.availableVersion != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    "Update ${UpdateChecker.availableVersion} available",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (tabs.none { it.isPrivate == gridPrivate }) {
                        Text(
                            if (gridPrivate) "No private tabs yet — open one below." else "No tabs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(tabs.filter { it.isPrivate == gridPrivate }, key = { it.id }) { tab ->
                            // Swipe a card sideways to close the tab (the
                            // ✕ button still works too).
                            val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart ||
                                        value == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
                                    ) {
                                        closeTab(tab)
                                        true
                                    } else false
                                }
                            )
                            androidx.compose.material3.SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier.fillMaxSize()
                                            .background(
                                                MaterialTheme.colorScheme.errorContainer,
                                                RoundedCornerShape(20.dp)
                                            )
                                            .padding(horizontal = 16.dp),
                                        contentAlignment = if (dismissState.dismissDirection ==
                                            androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
                                        ) Alignment.CenterStart else Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            Icons.Filled.Close, contentDescription = "Close tab",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            ) {
                            Card(
                                modifier = Modifier.fillMaxWidth().height(132.dp)
                                    .clickable {
                                        activeTabId = tab.id
                                        if (!barFocused) fieldValue = TextFieldValue(displayFor(tab.url))
                                        showTabs = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (tab.id == activeTabId)
                                        MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                border = BorderStroke(
                                    if (tab.id == activeTabId) 2.dp else 0.5.dp,
                                    if (tab.id == activeTabId)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                                ),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (tab.isPrivate) {
                                            Icon(
                                                Icons.Filled.Lock, contentDescription = "Private",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(
                                            tab.title.ifBlank { "New tab" },
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { closeTab(tab) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = "Close tab", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text(
                                        if (tab.isStartPage) "Start page"
                                        else (Uri.parse(tab.url).host ?: tab.url),
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(50))
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                            .border(
                                0.5.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                RoundedCornerShape(50)
                            )
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                openNewTab("", isPrivate = gridPrivate)
                                showTabs = false
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = if (gridPrivate) "New private tab" else "New tab",
                                modifier = Modifier.size(25.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box(
                            modifier = Modifier.weight(1f)
                                .clip(RoundedCornerShape(50))
                                .clickable { gridPrivate = !gridPrivate }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (gridPrivate) {
                                    Icon(
                                        Icons.Filled.Lock,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    if (gridPrivate) "Private" else "${tabs.count { !it.isPrivate }} Tabs",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                        }
                        IconButton(
                            onClick = { showTabs = false },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Done",
                                modifier = Modifier.size(25.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- Main menu sheet ----
    if (menuOpen) {
        val pageHost = Uri.parse(activeTab.url).host ?: ""
        val siteBlockingOff = pageHost.isNotBlank() && store.siteSetting(pageHost).disableBlocking
        val bookmarkedNow = store.isBookmarked(activeTab.url)
        ModalBottomSheet(
            onDismissRequest = { menuOpen = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        MenuRow("New tab", Icons.Filled.Add) {
                            menuOpen = false; openNewTab("")
                        }
                        MenuRow("New private tab", Icons.Filled.Lock) {
                            menuOpen = false; openNewTab("", isPrivate = true)
                        }
                        MenuRow("Reload", Icons.Filled.Refresh) {
                            menuOpen = false; activeWebView()?.reload()
                        }
                        MenuRow("Share page", Icons.Filled.Share) {
                            menuOpen = false; shareCurrentPage()
                        }
                        MenuRow(
                            if (bookmarkedNow) "Remove bookmark" else "Bookmark this page",
                            if (bookmarkedNow) Icons.Filled.Star else Icons.Outlined.StarBorder
                        ) {
                            menuOpen = false
                            if (activeTab.url.isNotBlank()) store.toggleBookmark(activeTab.title, activeTab.url)
                        }
                        if (activeTab.url.isNotBlank()) {
                            MenuRow(
                                if (store.isFavourite(activeTab.url)) "Remove from favourites" else "Add to favourites",
                                Icons.Filled.Star
                            ) {
                                menuOpen = false
                                store.toggleFavourite(activeTab.title, activeTab.url)
                            }
                        }
                        MenuRow("Find media on page", Icons.Filled.PlayArrow) {
                            menuOpen = false; runMediaScan()
                        }
                        MenuRow("Read this article", Icons.Filled.Public) {
                            menuOpen = false
                            val wv = activeWebView()
                            val src = readabilitySrc
                            if (wv != null && src != null && activeTab.url.isNotBlank()) {
                                // Extract from the LIVE page with Mozilla
                                // Readability (clone + gate), then hand the
                                // article to the reader screen via the cache.
                                val targetUrl = activeTab.url
                                wv.evaluateJavascript(src + ArticleExtractor.GLUE_JS) { raw ->
                                    val article = ArticleExtractor.parseResult(raw, targetUrl)
                                    if (article != null) {
                                        ArticleCache.current = article
                                        onReadArticle(targetUrl)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Reader isn't available for this page",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else if (activeTab.url.isNotBlank()) {
                                onReadArticle(activeTab.url)
                            }
                        }
                        MenuRow("Find in page", Icons.Filled.Search) {
                            menuOpen = false; showFind = true
                        }
                        MenuRow(if (activeTab.desktopMode) "Mobile site" else "Desktop site", Icons.Filled.OpenInNew) {
                            menuOpen = false
                            activeTab.desktopMode = !activeTab.desktopMode
                            webViews.remove(activeTab.id)?.destroy()
                            if (activeTab.url.isNotBlank()) {
                                val wv = webViews.getOrPut(activeTab.id) { createWebView(activeTab) }
                                wv.loadUrl(activeTab.url)
                            }
                        }
                        if (pageHost.isNotBlank()) {
                            MenuRow(
                                if (siteBlockingOff) "Site blocking: off" else "Site blocking: on",
                                Icons.Filled.Close
                            ) {
                                menuOpen = false
                                store.putSiteSetting(
                                    store.siteSetting(pageHost).copy(disableBlocking = !siteBlockingOff)
                                )
                                Toast.makeText(
                                    context,
                                    if (siteBlockingOff) "Blocking on for $pageHost" else "Blocking off for $pageHost — reload the page",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        MenuRow("Bookmarks", Icons.Filled.Star) { menuOpen = false; onOpenScreen(Screen.Bookmarks) }
                        MenuRow("History", Icons.Filled.Refresh) { menuOpen = false; onOpenScreen(Screen.History) }
                        MenuRow("Playlists", Icons.Filled.PlaylistAdd) { menuOpen = false; onOpenScreen(Screen.Playlists) }
                        MenuRow("Offline library", Icons.Filled.Download) { menuOpen = false; onOpenScreen(Screen.Library) }
                        MenuRow("Bookshelf", Icons.Filled.Public) { menuOpen = false; onOpenScreen(Screen.Bookshelf) }
                        MenuRow("Password vault", Icons.Filled.Lock) { menuOpen = false; onOpenScreen(Screen.Vault) }
                        MenuRow("Settings", Icons.Filled.MoreVert) { menuOpen = false; onOpenScreen(Screen.Settings) }
                    }
                }
                if (UpdateChecker.availableVersion != null) {
                    Text(
                        "Version ${UpdateChecker.availableVersion} is available on GitHub.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }

    // ---- Long-press context sheet ----
    contextTargetUrl?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { contextTargetUrl = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    target, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                if (!contextTargetIsImage) {
                    MenuRow("Open in new tab", Icons.Filled.OpenInNew) {
                        contextTargetUrl = null
                        openNewTab(target, activeTab.isPrivate)
                    }
                }
                if (contextTargetIsImage) {
                    MenuRow("View image", Icons.Filled.OpenInNew) {
                        contextTargetUrl = null
                        openNewTab(target, activeTab.isPrivate)
                    }
                }
                MenuRow("Download", Icons.Filled.Download) {
                    contextTargetUrl = null
                    Downloads.start(context, store, activeTab.title, target, referer = activeTab.url)
                    Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                }
                MenuRow("Copy link", Icons.Filled.ContentCopy) {
                    contextTargetUrl = null
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("link", target))
                }
                MenuRow("Share", Icons.Filled.Share) {
                    contextTargetUrl = null
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, target)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                    }
                }
            }
        }
    }

    if (showFind) {
        AlertDialog(
            shape = RoundedCornerShape(28.dp),
            onDismissRequest = { showFind = false },
            title = { Text("Find in page") },
            text = {
                OutlinedTextField(
                    value = findText,
                    onValueChange = {
                        findText = it
                        activeWebView()?.findAllAsync(it)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { activeWebView()?.findNext(true) }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = {
                    activeWebView()?.clearMatches()
                    showFind = false
                }) { Text("Done") }
            }
        )
    }

    if (showDetected) {
        ModalBottomSheet(
            onDismissRequest = { showDetected = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright)
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Text(
                    "Media on this page (${detected.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                if (detected.isEmpty()) {
                    Text(
                        "No playable media was found on this page. If a video is playing, " +
                            "give it a few seconds and try again — streams are spotted as the page loads them.",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                        items(detected) { media ->
                            ListItem(
                                colors = androidx.compose.material3.ListItemDefaults.colors(
                                    containerColor = Color.Transparent
                                ),
                                headlineContent = {
                                    Text(
                                        media.label.ifBlank { media.url.substringAfterLast('/') },
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text("${media.kind} • ${media.url}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                trailingContent = {
                                    if (media.kind == "page") {
                                        TextButton(onClick = {
                                            showDetected = false
                                            loadUrl(media.url)
                                        }) { Text("Open") }
                                    } else {
                                        Row {
                                            IconButton(onClick = {
                                                showDetected = false
                                                PlayerActivity.launch(context, media.url, media.label.ifBlank { activeTab.title }, referer = activeTab.url)
                                            }) {
                                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                                            }
                                            IconButton(onClick = {
                                                Downloads.start(context, store, media.label.ifBlank { activeTab.title }, media.url, media = true, referer = activeTab.url)
                                                showDetected = false
                                            }) {
                                                Icon(Icons.Filled.Download, contentDescription = "Download")
                                            }
                                            IconButton(onClick = { playlistPickFor = media }) {
                                                Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to playlist")
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                    }
                }
                }
            }
        }
    }

    playlistPickFor?.let { media ->
        AlertDialog(
            shape = RoundedCornerShape(28.dp),
            onDismissRequest = { playlistPickFor = null },
            title = { Text("Add to playlist") },
            text = {
                Column {
                    if (store.playlists.isEmpty()) {
                        Text("No playlists yet — create one below.")
                    }
                    store.playlists.forEach { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text("${p.items.size} items") },
                            modifier = Modifier.clickable {
                                store.addToPlaylist(p.id, media.label.ifBlank { activeTab.title }, media.url)
                                playlistPickFor = null
                                showDetected = false
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            label = { Text("New playlist") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                val p = store.createPlaylist(newPlaylistName.trim())
                                store.addToPlaylist(p.id, media.label.ifBlank { activeTab.title }, media.url)
                                newPlaylistName = ""
                                playlistPickFor = null
                                showDetected = false
                            }
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = "Create and add")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { playlistPickFor = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MenuRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorPage(url: String, message: String, onRetry: () -> Unit, onHome: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Public, contentDescription = null,
            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("Can't open this page", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            url, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onHome) { Text("Start page") }
            androidx.compose.material3.Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
fun StartPage(store: AppStore, onOpen: (String) -> Unit, onOpenBook: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val wallpaper = store.setting("wallpaper", "ocean")
    val colors = when (wallpaper) {
        "sunset" -> listOf(Color(0xFF3A1C4B), Color(0xFFB84A39))
        "forest" -> listOf(Color(0xFF0E2A1E), Color(0xFF2E6B4F))
        "slate" -> listOf(Color(0xFF23272E), Color(0xFF4A5568))
        "paper" -> listOf(Color(0xFFF3EAD3), Color(0xFFE2D3AC))
        else -> listOf(Color(0xFF0B2536), Color(0xFF1B6A8A))
    }
    val headerText = if (wallpaper == "paper") Color(0xFF3A3226) else Color.White
    val rev = store.revision

    // Continue watching/listening: saved playback positions with best-known titles.
    val continueItems = remember(rev) {
        store.playbackPositions.entries.map { (url, pos) ->
            val title = store.downloads.firstOrNull { it.url == url }?.title
                ?: store.playlists.flatMap { it.items }.firstOrNull { it.url == url }?.title
                ?: store.history.firstOrNull { it.url == url }?.title
                ?: (Uri.parse(url).host ?: url)
            Triple(url, title, pos)
        }
    }
    val continueBooks = remember(rev) {
        store.books.filter { it.chapterIndex > 0 || it.scrollFraction > 0f }
    }
    val frequent = remember(rev) {
        store.history.distinctBy { Uri.parse(it.url).host ?: it.url }.take(6)
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(Brush.verticalGradient(colors)).padding(20.dp)
                ) {
                    Column {
                        Text(
                            "Notscape Mariner",
                            style = MaterialTheme.typography.headlineMedium,
                            color = headerText
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Local-only and private — everything stays on your device. " +
                                "Type in the address bar to search or visit a site.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = headerText.copy(alpha = 0.85f)
                        )
                    }
                }
            }
            if (continueItems.isNotEmpty()) {
                item {
                    Text("Continue", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(continueItems) { (url, title, pos) ->
                            Card(
                                modifier = Modifier.width(220.dp).clickable {
                                    PlayerActivity.launch(context, url, title)
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(6.dp))
                                    Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Resume at ${net.notscape.browser.media.formatTime(pos)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (continueBooks.isNotEmpty()) {
                item {
                    Text("Continue reading", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        continueBooks.take(3).forEach { book ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onOpenBook(book.id) },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "Chapter ${book.chapterIndex + 1}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text("Favourites", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val favRows = ((store.favourites.size + 3) / 4).coerceAtLeast(1)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height((favRows * 96).dp)
                ) {
                    items(store.favourites.toList()) { fav ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onOpen(fav.url) }
                        ) {
                            Box(
                                modifier = Modifier.size(52.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    fav.title.trim().firstOrNull()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                fav.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
            if (frequent.isNotEmpty()) {
                item {
                    Text("Frequently visited", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Column {
                        frequent.forEach { entry ->
                            ListItem(
                                headlineContent = {
                                    Text(entry.title.ifBlank { entry.url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(Uri.parse(entry.url).host ?: entry.url, maxLines = 1)
                                },
                                modifier = Modifier.clickable { onOpen(entry.url) }
                            )
                        }
                    }
                }
            }
        }
    }
}
