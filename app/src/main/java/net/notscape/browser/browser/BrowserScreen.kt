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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import android.view.MotionEvent
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import net.notscape.browser.data.AppStore
import net.notscape.browser.data.TabSnapshot
import net.notscape.browser.data.VaultEntry
import net.notscape.browser.vault.VaultCrypto
import net.notscape.browser.vault.VaultFill
import net.notscape.browser.media.Downloads
import net.notscape.browser.media.PlayerActivity
import net.notscape.browser.ui.Frost
import net.notscape.browser.ui.FrostSheet
import net.notscape.browser.ui.FrostDialog
import net.notscape.browser.ui.GroupedCard
import net.notscape.browser.ui.PanelCard
import net.notscape.browser.ui.Screen
import net.notscape.browser.ui.frostBar


/** One picked element (beta.18): the selector the
 * confirm dialog offers, plus what it shows about
 * the element. Parsed from the picker's JSON. */
private data class PickInfo(
    val selector: String,
    val tag: String,
    val id: String,
    val classes: String,
    val width: Int,
    val height: Int,
    val src: String = ""
)

/** The picker's page side (beta.18). Installed on
 * entering pick mode, removed on exit: a capturing
 * click listener that outlines the tapped element
 * and reports it through the bridge, instead of
 * letting the tap navigate. Wider/narrower walk
 * the element stack; clear restores the outline
 * and drops the listener. */
private const val PICKER_INSTALL_JS = """
(function(){
  if (window.__nsPicker) { window.__nsPicker.clear(); }
  function uniqueSel(sel){
    try { return document.querySelectorAll(sel).length === 1; }
    catch (e) { return false; }
  }
  function buildSelector(el){
    if (el.id) {
      var idSel = '#' + el.id.replace(/[^A-Za-z0-9_-]/g, '');
      if (idSel.length > 1 && uniqueSel(idSel)) return idSel;
    }
    var tag = el.tagName.toLowerCase();
    var cls = [];
    if (el.classList) {
      for (var i = 0; i < el.classList.length; i++) {
        var c = el.classList[i];
        if (/^[A-Za-z0-9_-]+$/.test(c)) cls.push(c);
      }
    }
    if (cls.length) {
      var sel = tag + '.' + cls.join('.');
      if (uniqueSel(sel)) return sel;
    }
    var path = [];
    var cur = el;
    while (cur && cur.nodeType === 1 && cur !== document.body && cur !== document.documentElement) {
      var t = cur.tagName.toLowerCase();
      var idx = 1;
      var sib = cur.previousElementSibling;
      while (sib) { if (sib.tagName === cur.tagName) idx++; sib = sib.previousElementSibling; }
      path.unshift(t + ':nth-of-type(' + idx + ')');
      cur = cur.parentElement;
      if (path.length >= 4) break;
    }
    return path.join(' > ');
  }
  function info(el){
    if (!el || el === document.body || el === document.documentElement) return null;
    var r = el.getBoundingClientRect();
    var cn = el.className;
    if (cn && cn.baseVal !== undefined) cn = cn.baseVal;
    var src = '';
    var tn = el.tagName;
    if (tn === 'IFRAME' || tn === 'IMG' || tn === 'VIDEO' || tn === 'EMBED' || tn === 'SOURCE' || tn === 'OBJECT') {
      src = el.src || el.getAttribute('src') || '';
      if (tn === 'IMG' && el.currentSrc) src = el.currentSrc;
    }
    return {selector: buildSelector(el), tag: el.tagName.toLowerCase(), id: el.id || '', cls: cn || '', w: Math.round(r.width), h: Math.round(r.height), src: src};
  }
  function mark(el){
    var p = window.__nsPicker;
    if (p.prev) { p.prev.el.style.outline = p.prev.outline; }
    p.prev = {el: el, outline: el.style.outline};
    el.style.outline = '3px solid #ff5252';
    el.style.outlineOffset = '-3px';
  }
  function iframeAt(x, y){
    var best = null, bestArea = Infinity;
    var frs = document.querySelectorAll('iframe');
    for (var i = 0; i < frs.length; i++) {
      var r = frs[i].getBoundingClientRect();
      if (x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) {
        var area = r.width * r.height;
        if (area < bestArea) { bestArea = area; best = frs[i]; }
      }
    }
    return best;
  }
  function handler(e){
    e.preventDefault(); e.stopPropagation();
    var target = iframeAt(e.clientX, e.clientY) || e.target;
    var inf = info(target);
    if (!inf) return;
    var p = window.__nsPicker;
    p.stack = [target]; p.infos = [inf];
    p.lastPick = JSON.stringify(inf);
    mark(target);
    try { notscapePicker.onPick(p.lastPick); } catch (e) {}
  }
  window.__nsPicker = {
    stack: [], infos: [], prev: null,
    widen: function(){
      var p = window.__nsPicker;
      var cur = p.stack[p.stack.length - 1];
      if (!cur) return;
      var parent = cur.parentElement;
      if (!parent || parent === document.body || parent === document.documentElement) return;
      var inf = info(parent);
      if (!inf) return;
      p.stack.push(parent); p.infos.push(inf);
      p.lastPick = JSON.stringify(inf);
      mark(parent);
      try { notscapePicker.onPick(p.lastPick); } catch (e) {}
    },
    narrow: function(){
      var p = window.__nsPicker;
      if (p.stack.length <= 1) return;
      p.stack.pop(); p.infos.pop();
      var el = p.stack[p.stack.length - 1];
      mark(el);
      p.lastPick = JSON.stringify(p.infos[p.infos.length - 1]);
      try { notscapePicker.onPick(p.lastPick); } catch (e) {}
    },
    clear: function(){
      var p = window.__nsPicker;
      if (p.prev) { p.prev.el.style.outline = p.prev.outline; p.prev = null; }
      document.removeEventListener('click', handler, true);
      var st = document.getElementById('__nsPickerIframeStyle');
      if (st) st.remove();
    }
  };
  document.addEventListener('click', handler, true);
  var st = document.createElement('style');
  st.id = '__nsPickerIframeStyle';
  st.textContent = 'iframe{pointer-events:none !important;}';
  (document.head || document.documentElement).appendChild(st);
})();
"""

private const val PICKER_WIDEN_JS =
    "(function(){var p=window.__nsPicker; if(p) p.widen();})()"
private const val PICKER_NARROW_JS =
    "(function(){var p=window.__nsPicker; if(p) p.narrow();})()"
private const val PICKER_CLEAR_JS =
    "(function(){var p=window.__nsPicker; if(p) p.clear();})()"
private const val PICKER_LAST_PICK_JS =
    "(function(){var p=window.__nsPicker; return p && p.lastPick ? p.lastPick : '';})()"

/** The picker's bridge (beta.18): the page calls
 * this one method with a picked element's JSON.
 * It runs off the main thread, so it posts home;
 * it can do nothing on its own — blocking always
 * waits for the user's tap in the confirm dialog. */
private class PickerBridge(private val onPick: (String) -> Unit) {
    private val mainHandler =
        android.os.Handler(android.os.Looper.getMainLooper())

    @android.webkit.JavascriptInterface
    fun onPick(json: String) {
        mainHandler.post { onPick(json) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class, ExperimentalFoundationApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    store: AppStore,
    onOpenScreen: (Screen) -> Unit,
    onReadArticle: (String) -> Unit,
    initialUrl: String?,
    pendingUrl: String? = null,
    initialAppMode: Boolean = false,
    pendingAppMode: Boolean = false,
    onPendingConsumed: () -> Unit = {},
    onOpenBook: (Long) -> Unit = {},
    browserActive: Boolean = true,
    renewSignal: Int = 0,
    onOpenGamesConsole: (String) -> Unit = {},
    onOpenGame: (String) -> Unit = {}
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
    val tabList = remember { TabList() }
    val tabs = remember {
        val list = tabList.tabs
        when {
            !initialUrl.isNullOrBlank() -> list.add(TabState(1, initialUrl, isAppMode = initialAppMode))
            restoreEnabled && store.tabSession.isNotEmpty() -> {
                store.tabSession.forEachIndexed { i, snap ->
                    list.add(
                        TabState(i + 1, snap.url).apply {
                            title = snap.title
                            group = snap.group
                        }
                    )
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
    /** In-session scroll memory (0.0.993): tab id ->
     *  (url, scrollY). A WebView that gets recreated (the
     *  desktop-mode toggle, process pressure) lands back
     *  where the reader left it. Session-only, never
     *  written to disk, and private tabs are included -
     *  it dies with the process either way. */
    val scrollMemory = remember {
        mutableStateMapOf<Int, Pair<String, Int>>()
    }
    var progress by remember { mutableStateOf(0) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(initialUrl ?: "")) }
    var barFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var menuOpen by remember { mutableStateOf(false) }
    var vaultFillChoices by remember { mutableStateOf<List<VaultEntry>>(emptyList()) }
    var showVaultFill by remember { mutableStateOf(false) }
    /** Two-step sign-in: the vault entry whose username was filled
     *  on a username-only page; its password is offered when the
     *  password step appears. Memory only — never persisted. */
    var pendingFillEntryId by remember { mutableStateOf<Long?>(null) }
    var showTabs by remember { mutableStateOf(false) }
    /** The tab whose group is being edited (0.0.992). */
    var groupEditTab by remember { mutableStateOf<TabState?>(null) }
    var barHidden by remember { mutableStateOf(false) }
    var gridPrivate by remember { mutableStateOf(false) }
    var tabSearch by remember { mutableStateOf("") }
    var showRecentlyClosed by remember { mutableStateOf(false) }
    var detected by remember { mutableStateOf<List<DetectedMedia>>(emptyList()) }
    var showDetected by remember { mutableStateOf(false) }
    var playlistPickFor by remember { mutableStateOf<DetectedMedia?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }
    var findText by remember { mutableStateOf("") }
    // Find-in-page state (0.0.990): the WebView reports
    // the active match and the total through its
    // FindListener; the pills below render them.
    var findOrdinal by remember { mutableStateOf(0) }
    var findCount by remember { mutableStateOf(0) }
    var showFind by remember { mutableStateOf(false) }
    var containerRef by remember { mutableStateOf<FrameLayout?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    /**
     * Captured popup targets, in arrival order. A queue, not a
     * single slot: one tap on a streaming-index button often fires
     * TWO window.open calls (an ad and the player), and collapsing
     * them into one slot meant the survivor was usually the ad —
     * the player never got through (Robert's report, 2026-10-22).
     */
    val pendingPopupUrls = remember { mutableStateListOf<String>() }
    var capturedPopupWv by remember { mutableStateOf<WebView?>(null) }
    var popupPageHost by remember { mutableStateOf("") }

    fun clearPendingPopups() {
        pendingPopupUrls.clear()
        capturedPopupWv?.let { runCatching { it.destroy() } }
        capturedPopupWv = null
    }

    var showPopupNotice by remember { mutableStateOf(false) }
    var contextTargetUrl by remember { mutableStateOf<String?>(null) }
    var contextTargetIsImage by remember { mutableStateOf(false) }

    // Element picker (beta.18): a long-pressed
    // link/image awaiting a network rule, pick mode
    // for any other element, and its latest pick.
    var blockNetTarget by remember { mutableStateOf<String?>(null) }
    var pickMode by remember { mutableStateOf(false) }
    var pickInfo by remember { mutableStateOf<PickInfo?>(null) }

    /** Push the stored picker rules into the engine
     * (called at WebView setup and after every save,
     * so a rule applies without restarting). */
    fun syncUserRules() {
        FilterEngine.userNetRules = UserBlockRules.parseNet(
            store.setting(UserBlockRules.NET_KEY, "")
        )
        FilterEngine.userCosmeticRules = UserBlockRules.parseCosmetic(
            store.setting(UserBlockRules.COSMETIC_KEY, "")
        )
    }

    /** Parse one bridge pick; null when the JSON
     * is unusable or the selector came back blank. */
    fun parsePick(json: String): PickInfo? = runCatching {
        val o = org.json.JSONObject(json)
        PickInfo(
            selector = o.optString("selector"),
            tag = o.optString("tag"),
            id = o.optString("id"),
            classes = o.optString("cls"),
            width = o.optInt("w"),
            height = o.optInt("h"),
            src = o.optString("src")
        ).takeIf { it.selector.isNotBlank() }
    }.getOrNull()

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
    var pullRefreshing by remember { mutableStateOf(false) }
    // 0.0.983: the pull is measured on the WebView itself (see
    // createWebView); pullOffsetPx drives the frost indicator.
    var pullOffsetPx by remember { mutableFloatStateOf(0f) }
    var startPullRefreshing by remember { mutableStateOf(false) }
    val pullTriggerPx = with(LocalDensity.current) { 84.dp.toPx() }
    LaunchedEffect(UpdateChecker.checking) {
        if (!UpdateChecker.checking && startPullRefreshing) {
            startPullRefreshing = false
        }
    }
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

    /** Install the picker listener on the current
     * tab's page when pick mode switches on. (It
     * must live after activeWebView: a local
     * function cannot be called before its own
     * declaration — that forward reference failed
     * the beta.18 main run.) */
    LaunchedEffect(pickMode) {
        if (!pickMode) return@LaunchedEffect
        activeWebView()?.evaluateJavascript(PICKER_INSTALL_JS, null)
        var delivered: String? = null
        while (pickMode) {
            delay(400)
            if (!pickMode) break
            activeWebView()?.evaluateJavascript(PICKER_LAST_PICK_JS) { result ->
                if (!pickMode) return@evaluateJavascript
                val json = runCatching {
                    org.json.JSONTokener(result ?: "").nextValue() as? String
                }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: return@evaluateJavascript
                if (json == delivered) return@evaluateJavascript
                parsePick(json)?.let { delivered = json; pickInfo = it }
            }
        }
    }

    // Find listener lifecycle (0.0.990): attach while the
    // find pills are up, detach (and reset the count) when
    // they close or the tab changes.
    DisposableEffect(showFind, activeTab.id) {
        val wv = if (showFind) activeWebView() else null
        if (wv != null) {
            wv.setFindListener { ordinal, count, _ ->
                findOrdinal = ordinal
                findCount = count
            }
            if (findText.isNotBlank()) {
                wv.findAllAsync(findText)
            }
        }
        onDispose {
            wv?.setFindListener(null)
            findOrdinal = 0
            findCount = 0
        }
    }

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
            "custom" -> store.setting("customSearchUrl", "")
                .takeIf { SearchTemplate.isValid(it) }
                ?: "https://noai.duckduckgo.com/?q=%s"
            else -> "https://noai.duckduckgo.com/?q=%s"
        }
        return template.format(Uri.encode(q))
    }

    /** HTTPS-first: returns the https:// version of an http:// URL, or
     *  null when no upgrade should be attempted. Local addresses
     *  (localhost, *.local, bare IPs — home-network devices are usually
     *  http-only) and hosts that already failed an upgrade in this tab
     *  are left alone. */
    fun httpsUpgradeFor(tab: TabState, raw: String): String? {
        val uri = Uri.parse(raw)
        if (uri.scheme != "http") return null
        val host = uri.host ?: return null
        if (host == "localhost" || host.endsWith(".local")) return null
        if (host in tab.httpOnlyHosts) return null
        if (host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) return null
        return uri.buildUpon().scheme("https").build().toString()
    }

    fun loadUrl(raw: String) {
        var url = normalizeInput(raw)
        if (url.isEmpty()) return
        httpsUpgradeFor(activeTab, url)?.let { upgraded ->
            activeTab.pendingHttpOriginal = url
            url = upgraded
        }
        activeTab.url = url
        if (!barFocused) fieldValue = TextFieldValue(displayFor(url))
        val wv = webViews[activeTab.id]
        if (wv != null && wv.url != url) wv.loadUrl(url)
    }

    /** Type [text] into the WebView's focused element as real key
     *  events: the page receives trusted input events, exactly as if
     *  the user typed (the JS setter path synthesises untrusted
     *  events that strict sites reject). False when the keyboard map
     *  can't produce some character — the caller falls back. */
    fun typeIntoWebView(wv: WebView, text: String, done: (Boolean) -> Unit) {
        val events = runCatching {
            android.view.KeyCharacterMap.load(android.view.KeyCharacterMap.VIRTUAL_KEYBOARD)
                .getEvents(text.toCharArray())
        }.getOrNull()
        if (events == null) { done(false); return }
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        events.forEachIndexed { i, ev ->
            handler.postDelayed({ runCatching { wv.dispatchKeyEvent(ev) } }, i * 14L)
        }
        handler.postDelayed({ done(true) }, events.size * 14L + 40)
    }

    /** Three-way check after a fill: the field's actual contents
     *  against the decrypted vault value. A mismatch on the typed
     *  path earns one setter-path attempt before reporting. */
    fun verifyFill(wv: WebView, entry: VaultEntry, plain: String, typedPath: Boolean) {
        wv.evaluateJavascript(VaultFill.readbackScript()) { raw ->
            val (ru, rp) = VaultFill.parseReadback(raw)
            val uOk = entry.username.isEmpty() || ru == entry.username
            if (uOk && rp == plain) {
                pendingFillEntryId = null
                Toast.makeText(context, "Filled from vault", Toast.LENGTH_SHORT).show()
            } else if (typedPath) {
                wv.evaluateJavascript(VaultFill.fillScript(entry.username, plain)) {
                    wv.evaluateJavascript(VaultFill.readbackScript()) { raw2 ->
                        val (ru2, rp2) = VaultFill.parseReadback(raw2)
                        if (rp2 == plain && (entry.username.isEmpty() || ru2 == entry.username)) {
                            pendingFillEntryId = null
                            Toast.makeText(context, "Filled from vault", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(
                                context,
                                "The page shows different text than the vault holds — check the entry for stray spaces",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } else {
                Toast.makeText(
                    context,
                    "The page shows different text than the vault holds — check the entry for stray spaces",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** Fill one vault entry into the active page's login form. */
    fun fillFromVault(entry: VaultEntry) {
        val wv = activeWebView() ?: return
        // Origin recheck at the moment of injection: the page may have
        // navigated while the biometric prompt or the chooser was up.
        // The committed URL must be HTTPS and the same site as the
        // entry, or nothing is decrypted and nothing is filled.
        if (!VaultFill.fillAllowed(entry, wv.url)) {
            Toast.makeText(
                context, "Page changed or isn't secure — fill cancelled",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        runCatching {
            val plain = VaultCrypto.decrypt(entry.passwordEnc)
            // Locate + focus the fields in the page, then type the
            // real characters as key events (trusted input).
            wv.evaluateJavascript(VaultFill.locateScript()) { raw ->
                val found = VaultFill.parseLocate(raw)
                when {
                    found.isEmpty() -> {
                        // Nothing the locator trusts: setter fallback.
                        wv.evaluateJavascript(VaultFill.fillScript(entry.username, plain)) { r ->
                            if (VaultFill.parseLocate(r) == "1") {
                                verifyFill(wv, entry, plain, typedPath = false)
                            } else {
                                Toast.makeText(
                                    context, "No login form found on this page",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    found == "u" -> {
                        // Username-only page (two-step sign-in): type
                        // the username now; the password is offered
                        // when the password step appears.
                        typeIntoWebView(wv, entry.username) { ok ->
                            if (!ok) {
                                wv.evaluateJavascript(VaultFill.fillScript(entry.username, plain), null)
                            }
                            pendingFillEntryId = entry.id
                            Toast.makeText(
                                context,
                                "Username filled — use Fill password again on the next step",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    else -> {
                        val typePassword = {
                            typeIntoWebView(wv, plain) { ok ->
                                if (ok) {
                                    verifyFill(wv, entry, plain, typedPath = true)
                                } else {
                                    wv.evaluateJavascript(VaultFill.fillScript(entry.username, plain)) {
                                        verifyFill(wv, entry, plain, typedPath = false)
                                    }
                                }
                            }
                        }
                        if (found.contains('u') && entry.username.isNotEmpty()) {
                            typeIntoWebView(wv, entry.username) {
                                wv.evaluateJavascript(VaultFill.focusPasswordScript()) { typePassword() }
                            }
                        } else {
                            wv.evaluateJavascript(VaultFill.focusPasswordScript()) { typePassword() }
                        }
                    }
                }
            }
        }.onFailure {
            Toast.makeText(
                context, "Couldn't fill — unlock the vault and try again",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun offerVaultFill(matches: List<VaultEntry>) {
        if (matches.size == 1) {
            fillFromVault(matches.first())
        } else {
            vaultFillChoices = matches
            showVaultFill = true
        }
    }

    /** Vault fill entry point: match the committed page's host
     *  against the vault, then require a fresh device authentication
     *  before any password is decrypted — nothing fills on a bare
     *  tap. Devices with no authentication enrolled get the vault
     *  screen's own fallback: the explicit tap is the confirmation. */
    fun startVaultFill() {
        // Match the page the WebView has actually committed — never
        // the tab's intended URL, which a redirect can falsify — and
        // only ever on HTTPS.
        val committedUri = activeWebView()?.url?.let { Uri.parse(it) }
        if (committedUri == null ||
            !committedUri.scheme.equals("https", ignoreCase = true)
        ) {
            Toast.makeText(
                context, "Passwords only fill on secure (HTTPS) pages",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val host = committedUri.host ?: return
        val matches = VaultFill.matches(store, host)
        if (matches.isEmpty()) {
            Toast.makeText(context, "No saved password for this site", Toast.LENGTH_SHORT).show()
            return
        }
        val activity = context as? FragmentActivity
        val can = if (activity != null) {
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        }
        if (activity == null || can != BiometricManager.BIOMETRIC_SUCCESS) {
            offerVaultFill(matches)
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    offerVaultFill(matches)
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fill password")
            .setSubtitle("Unlock to fill a saved password on this page")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }

    fun openNewTab(url: String, isPrivate: Boolean = false, appMode: Boolean = false) {
        val start = if (url.isBlank() && !isPrivate) homeUrl() else url
        val t = TabState(nextTabId++, start, isPrivate, isAppMode = appMode)
        tabs.add(t)
        activeTabId = t.id
        if (!barFocused) fieldValue = TextFieldValue(displayFor(url))
    }

    /** Open every captured popup in its own tab, then clear. */
    fun openPendingPopups() {
        val urls = pendingPopupUrls.toList()
        clearPendingPopups()
        urls.forEach { openNewTab(it, activeTab.isPrivate) }
    }

    fun closeTab(tab: TabState) {
        tabList.recordClosed(tab)
        webViews.remove(tab.id)?.destroy()
        MediaLog.clear(tab.id)
        tabList.remove(tab)
        // The last private tab closing wipes the whole private profile
        // (this tab's WebView is already destroyed above, and no other
        // private tab — popup windows included — remains).
        if (tab.isPrivate && tabs.none { it.isPrivate }) PrivateProfile.wipe()
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

    /** Duplicate a tab right after itself and switch to the copy. */
    fun duplicateTab(tab: TabState) {
        val t = TabState(nextTabId++, tab.url, tab.isPrivate)
        t.title = tab.title
        t.group = tab.group
        t.desktopMode = tab.desktopMode
        val idx = tabs.indexOf(tab)
        tabs.add(if (idx >= 0) idx + 1 else tabs.size, t)
        activeTabId = t.id
        if (!barFocused) fieldValue = TextFieldValue(displayFor(t.url))
        showTabs = false
    }

    /** Close every tab in [keep]'s group except [keep] itself. */
    fun closeOtherTabs(keep: TabState) {
        tabs.filter { it.isPrivate == keep.isPrivate && it.id != keep.id }
            .toList().forEach { closeTab(it) }
        activeTabId = keep.id
        if (!barFocused) fieldValue = TextFieldValue(displayFor(keep.url))
    }

    /** Close every tab in one privacy group. */
    fun closeAllTabsInGroup(isPrivate: Boolean) {
        tabs.filter { it.isPrivate == isPrivate }.toList().forEach { closeTab(it) }
    }

    LaunchedEffect(pendingUrl) {
        if (!pendingUrl.isNullOrBlank()) {
            if (pendingAppMode) {
                openNewTab(pendingUrl, activeTab.isPrivate, appMode = true)
            } else {
                loadUrl(pendingUrl)
            }
            onPendingConsumed()
        }
    }

    // Persist the session (non-private tabs only) for restore on next launch.
    LaunchedEffect(Unit) {
        snapshotFlow {
            val open = tabs.filter { !it.isPrivate && !it.isAppMode }
            open.map { TabSnapshot(it.url, it.title, it.group) } to
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
                val open = tabs.filter { !it.isPrivate && !it.isAppMode }
                if (open.isNotEmpty()) {
                    store.saveSession(
                        open.map { TabSnapshot(it.url, it.title, it.group) },
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
            clearPendingPopups()
        }
    }

    /**
     * Third-party cookie policy for one WebView (per-WebView in
     * Android, so it is re-evaluated from the tab's current host at
     * every page start): blocked when the global toggle is on, unless
     * the site carries a per-site exception — the exception is what
     * keeps SSO and embedded logins working.
     */
    fun applyCookiePolicy(tab: TabState, wv: WebView) {
        val block = store.setting("blockThirdPartyCookies", "false") == "true"
        if (!block) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
            return
        }
        val host = Uri.parse(tab.url).host ?: ""
        val excepted = host.isNotBlank() && store.siteSetting(host).allowThirdPartyCookies
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, excepted)
    }

    // The surface colour a WebView sits on before its page reports
    // its own background: the theme's background (Phase G).
    val chromeBgArgb = MaterialTheme.colorScheme.background.toArgb()

    fun createWebView(tab: TabState): WebView {
        val wv = WebView(context)
        wv.overScrollMode = View.OVER_SCROLL_NEVER
        // Pull-to-refresh (0.0.983): PullToRefreshBox never fired
        // around a WebView - it waits for nested-scroll signals
        // a WebView does not send, so every pull was swallowed
        // by the page. This listener only OBSERVES (always
        // returns false): a downward drag that starts with the
        // page at its very top moves the indicator, and past
        // the trigger it reloads. Scrolling and taps are
        // untouched.
        var pullStartX = 0f
        var pullStartY = 0f
        var pullTracking = false
        wv.setOnTouchListener { v, e ->
            val web = v as WebView
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pullStartX = e.x
                    pullStartY = e.y
                    pullTracking = web.scrollY == 0 && !pullRefreshing
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pullTracking) {
                        if (web.scrollY > 0) {
                            pullTracking = false
                            pullOffsetPx = 0f
                        } else {
                            val dy = e.y - pullStartY
                            val dx = kotlin.math.abs(e.x - pullStartX)
                            val slop = 24f *
                                v.resources.displayMetrics.density
                            pullOffsetPx =
                                if (dy > slop && dy > dx * 1.6f) {
                                    ((dy - slop) * 0.55f).coerceAtMost(
                                        pullTriggerPx * 1.5f
                                    )
                                } else {
                                    0f
                                }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (pullTracking && pullOffsetPx >= pullTriggerPx) {
                        pullRefreshing = true
                        web.reload()
                    }
                    pullTracking = false
                    pullOffsetPx = 0f
                }
            }
            false
        }
        // Paint on a matching surface from the very first frame.
        wv.setBackgroundColor(tab.pageBg ?: chromeBgArgb)
        wv.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        // Private tabs browse in their own WebView profile (separate
        // cookies/storage), deleted when the last one closes. This must
        // be set before the WebView loads anything.
        if (tab.isPrivate) PrivateProfile.applyTo(wv)
        // Document-start cosmetics (0.0.99995): the
        // generic hiding sheet is registered before the
        // page's own scripts run, so ad containers never
        // paint. Domain rules cannot be known at document
        // start and keep the commit-visible path.
        if (store.setting("blockAds", "true") == "true" &&
            androidx.webkit.WebViewFeature.isFeatureSupported(
                androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
            )
        ) {
            val dsScript = FilterEngine.documentStartScript
            if (dsScript.isNotEmpty()) {
                runCatching {
                    androidx.webkit.WebViewCompat
                        .addDocumentStartJavaScript(
                            wv, dsScript, setOf("*")
                        )
                }
            }
        }
        // User scripts (0.0.99996): each enabled script
        // is registered as its own document-start blob,
        // gated in-script by its match pattern.
        if (androidx.webkit.WebViewFeature.isFeatureSupported(
                androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT
            )
        ) {
            for (us in UserScripts.load(store)) {
                val blob = UserScripts.startScriptFor(us)
                if (blob.isNotEmpty()) {
                    runCatching {
                        androidx.webkit.WebViewCompat
                            .addDocumentStartJavaScript(
                                wv, blob, setOf("*")
                            )
                    }
                }
            }
        }
        val host = Uri.parse(tab.url).host ?: ""
        val site = store.siteSetting(host)
        wv.settings.apply {
            // Hardening baseline (0.0.997): no file/content
            // access, no geolocation, no mixed content.
            applyWebHardening(this)
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
            textZoom = store.setting("webTextZoom", "100").toIntOrNull() ?: 100
            // Feature-gated force-dark: algorithmic darkening where
            // the WebView supports it, legacy force-dark where it
            // reports that instead, nothing where neither exists.
            val darkMode = store.setting("webDarkMode", "Auto")
            val nightNow = (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val wantDark = darkMode == "On" || (darkMode == "Auto" && nightNow)
            if (androidx.webkit.WebViewFeature.isFeatureSupported(
                    androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING
                )
            ) {
                androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, wantDark)
            } else if (androidx.webkit.WebViewFeature.isFeatureSupported(
                    androidx.webkit.WebViewFeature.FORCE_DARK
                )
            ) {
                @Suppress("DEPRECATION")
                androidx.webkit.WebSettingsCompat.setForceDark(
                    this,
                    if (wantDark) {
                        androidx.webkit.WebSettingsCompat.FORCE_DARK_ON
                    } else {
                        androidx.webkit.WebSettingsCompat.FORCE_DARK_OFF
                    }
                )
            }
            if (tab.desktopMode) {
                userAgentString =
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
            }
        }
        // Element picker bridge (beta.18): the page
        // reports picks through this one method; the
        // user's stored rules load into the engine.
        wv.addJavascriptInterface(
            PickerBridge { json ->
                parsePick(json)?.let { pickInfo = it }
            },
            "notscapePicker"
        )
        syncUserRules()
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
            // Scroll memory (0.0.993): remember where this
            // tab is on this page (coarsely - 40px steps
            // are plenty) for if its view is recreated.
            if (scrollY > 0) {
                val prev = scrollMemory[tab.id]
                if (prev == null || prev.first != tab.url ||
                    kotlin.math.abs(prev.second - scrollY) > 40
                ) {
                    scrollMemory[tab.id] = tab.url to scrollY
                }
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
                // HTTPS-first: a tapped http:// link is tried over
                // https:// first; onReceivedError falls back to the
                // plain address if the secure one can't be reached.
                if (u.scheme == "http" && request.isForMainFrame) {
                    httpsUpgradeFor(tab, u.toString())?.let { upgraded ->
                        tab.pendingHttpOriginal = u.toString()
                        view.loadUrl(upgraded)
                        return true
                    }
                }
                // Popunder guard (0.0.962): a script-driven main-frame
                // hop with no user gesture behind it, aimed at a known
                // $document/$popup ad host, is the swap trick - the
                // page throws its real content into a window and
                // navigates itself to the ad. Capture it like a
                // blocked popup instead of loading it. Typed addresses
                // never pass through here (loadUrl skips this
                // callback) and tapped links carry a gesture, so only
                // the trick is caught.
                if (request.isForMainFrame && !request.hasGesture() &&
                    (u.scheme == "http" || u.scheme == "https")
                ) {
                    val targetHost = u.host ?: ""
                    val hereHost = Uri.parse(tab.url).host ?: ""
                    if (targetHost.isNotEmpty() && targetHost != hereHost &&
                        FilterEngine.isDocumentHost(targetHost)
                    ) {
                        val urlStr = u.toString()
                        if (urlStr !in pendingPopupUrls &&
                            pendingPopupUrls.size < 5
                        ) {
                            pendingPopupUrls.add(urlStr)
                        }
                        popupPageHost = hereHost
                        if (!store.siteSetting(hereHost).blockPopups) {
                            showPopupNotice = true
                        }
                        return true
                    }
                }
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
                // Blocking applies to GET/HEAD loads only (Phase G
                // repair, Robert's report): uploads, form posts and
                // sign-ins travel as POST/PUT, and answering one of
                // those with an empty response kills the action
                // silently — that is how image uploads died. A filter
                // list must never sit in the way of something the
                // user is deliberately sending.
                val method = request.method ?: "GET"
                if ((method == "GET" || method == "HEAD") && !blockingOff &&
                    FilterEngine.isBlocked(request.url, request.isForMainFrame, blockAds, blockTrackers, pageHost)
                ) {
                    FilterEngine.noteBlocked()
                    return FilterEngine.emptyResponse()
                }
                if (method == "GET" && !request.isForMainFrame) {
                    MediaLog.inspect(tab.id, request.url.toString())
                }
                return null
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                // A navigation ends picking: the page
                // the listener lived on is gone, and a
                // stale pick must not linger.
                pickMode = false
                pickInfo = null
                if (url != null) {
                    tab.url = url
                    if (favicon != null) tab.favicon = favicon
                    applyCookiePolicy(tab, view)
                    progress = 5
                    tab.errorText = null
                    tab.sslErrorText = null
                    if (!barFocused) fieldValue = TextFieldValue(displayFor(url))
                    MediaLog.clear(tab.id)
                    // Cosmetic rules go in at document start too, so ad
                    // boxes never flash before the page finishes loading.
                    injectCosmetic(view, url)
                }
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                // Earlier than onPageFinished: the DOM exists, so the
                // cosmetic sheet lands before most ad boxes paint.
                injectCosmetic(view, url)
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
                    val original = tab.pendingHttpOriginal
                    val connectivityFailure = error.errorCode == WebViewClient.ERROR_HOST_LOOKUP ||
                        error.errorCode == WebViewClient.ERROR_CONNECT ||
                        error.errorCode == WebViewClient.ERROR_TIMEOUT ||
                        error.errorCode == WebViewClient.ERROR_IO ||
                        error.errorCode == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE
                    if (original != null && connectivityFailure && tab.sslErrorText == null) {
                        // The HTTPS-first upgrade couldn't reach the
                        // site securely: fall back to the plain http://
                        // address that was actually asked for, and
                        // don't try upgrading this host again here.
                        tab.pendingHttpOriginal = null
                        Uri.parse(original).host?.let { tab.httpOnlyHosts.add(it) }
                        view.loadUrl(original)
                        return
                    }
                    tab.pendingHttpOriginal = null
                    tab.errorText = "${error.description} (error ${error.errorCode})"
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: android.webkit.SslErrorHandler,
                error: android.net.http.SslError
            ) {
                val failingUrl = error.url ?: ""
                val host = Uri.parse(failingUrl).host ?: ""
                if (host in tab.sslProceedHosts) {
                    handler.proceed()
                    return
                }
                handler.cancel()
                // The server did answer over TLS, so an HTTPS-first
                // upgrade reached it — no plain-http fallback for this.
                tab.pendingHttpOriginal = null
                tab.sslErrorUrl = failingUrl
                tab.sslErrorText = when (error.primaryError) {
                    android.net.http.SslError.SSL_EXPIRED ->
                        "The certificate for $host has expired."
                    android.net.http.SslError.SSL_NOTYETVALID ->
                        "The certificate for $host isn't valid yet."
                    android.net.http.SslError.SSL_IDMISMATCH ->
                        "The certificate doesn't match the site name $host."
                    android.net.http.SslError.SSL_UNTRUSTED ->
                        "The certificate for $host isn't trusted by this device."
                    android.net.http.SslError.SSL_DATE_INVALID ->
                        "The certificate's dates are invalid — the device clock may be wrong."
                    else -> "The certificate for $host couldn't be verified."
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress = 100
                // Scroll memory (0.0.993): a recreated view
                // returns to where this tab left this page.
                if (url != null) {
                    scrollMemory[tab.id]?.let { (savedUrl, savedY) ->
                        if (savedUrl == url && savedY > 0 &&
                            view.scrollY == 0
                        ) {
                            view.post { view.scrollTo(0, savedY) }
                        }
                    }
                }
                // An HTTPS-first upgrade that finished loading worked:
                // forget the plain-http original.
                if (url != null && url.startsWith("https://")) tab.pendingHttpOriginal = null
                canGoBack = view.canGoBack()
                canGoForward = view.canGoForward()
                val t = view.title ?: ""
                if (url != null && !url.startsWith("about:")) {
                    tab.title = t
                    if (!tab.isPrivate) store.addHistory(t, url)
                }
                view.favicon?.let { tab.favicon = it }
                // Cosmetic filtering (also injected at document start;
                // re-asserted here for pages whose DOM was rebuilt).
                injectCosmetic(view, url)
                // Learn the page's own background colour so tab
                // switches and repaints sit on it instead of flashing
                // the default white — a dark page in a light app is
                // exactly the case this exists for (Phase G).
                view.evaluateJavascript(PAGE_BG_PROBE_JS) { raw ->
                    val bg = parseCssColor(raw)
                    if (bg != null) {
                        tab.pageBg = bg
                        view.setBackgroundColor(bg)
                        (view.parent as? View)?.setBackgroundColor(bg)
                    }
                }
                // Login-form probe: enables "Fill password from vault".
                view.evaluateJavascript(VaultFill.PROBE_JS) { result ->
                    tab.hasLoginForm = result == "true"
                    // Two-step sign-in: a pending username fill from
                    // an earlier page reaches its password step here —
                    // same site only; a different site forgets it.
                    val pid = pendingFillEntryId
                    if (pid != null) {
                        val e = store.vault.firstOrNull { it.id == pid }
                        when {
                            e == null -> pendingFillEntryId = null
                            VaultFill.fillAllowed(e, view.url) -> {
                                if (tab.hasLoginForm) {
                                    Toast.makeText(
                                        context,
                                        "Password step found — tap Fill password to finish",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            VaultFill.entryHost(e) != null &&
                                VaultFill.hostOf(view.url ?: "") != null &&
                                !net.notscape.browser.browser.Hosts.sameSite(
                                    VaultFill.entryHost(e)!!,
                                    VaultFill.hostOf(view.url ?: "")!!
                                ) -> pendingFillEntryId = null
                        }
                    }
                }
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress = newProgress
                if (newProgress >= 100) pullRefreshing = false
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

            override fun onCloseWindow(window: WebView) {
                // An allowed popup asking to close itself
                // (window.close()): honour it by closing its tab, or
                // the opener hangs waiting on a dead window.
                val t = tabs.firstOrNull { webViews[it.id] === window }
                if (t != null && t.isPopupWin) closeTab(t)
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
                capturedPopupWv?.let { runCatching { it.destroy() } }
                val popupWv = WebView(view.context)
                capturedPopupWv = popupWv
                transport.webView = popupWv
                popupWv.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(pv: WebView, request: WebResourceRequest): Boolean {
                        val u = request.url.toString()
                        runCatching { popupWv.destroy() }
                        if (capturedPopupWv === popupWv) capturedPopupWv = null
                        if (u.isNotBlank() && u != "about:blank" &&
                            u !in pendingPopupUrls && pendingPopupUrls.size < 5
                        ) {
                            pendingPopupUrls.add(u)
                        }
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
                // True fullscreen for web video too (0.0.987).
                (context as? android.app.Activity)?.let {
                    net.notscape.browser.setSystemBarsHidden(it, true)
                }
            }

            override fun onHideCustomView() {
                fullscreenView?.let { containerRef?.removeView(it) }
                fullscreenView = null
                fullscreenCallback?.onCustomViewHidden()
                fullscreenCallback = null
                (context as? android.app.Activity)?.let {
                    net.notscape.browser.setSystemBarsHidden(it, false)
                }
            }
        }
        if (tab.url.isNotBlank() && tab.url != "about:blank") {
            wv.loadUrl(tab.url)
        }
        return wv
    }

    // System back (button or edge-swipe gesture): leave fullscreen first,
    // then close whatever UI is open, then walk the page history — and
    // when a tab has no history left but isn't the group's only tab,
    // back closes the tab and returns to its neighbour (Robert's
    // report, 2026-10-21: a fresh tab + back was quitting the app).
    // A lone tab on its first page goes to the start/home page first;
    // back falls through and closes the app ONLY from there (Robert's
    // refinement, 2026-10-21). Enabled state comes from snapshot state
    // (canGoBack is kept fresh by doUpdateVisitedHistory), so the
    // gesture is never swallowed by a stale check.
    val anyOverlayOpen = showTabs || menuOpen || showDetected || showFind ||
        playlistPickFor != null || contextTargetUrl != null || showPopupNotice
    val groupTabCount = tabList.groupCount(activeTab.isPrivate)
    // "Home" for the back chain's last step: the start page, or the
    // configured home page for a normal tab (a private tab's home is
    // the private start page — it never loads the normal home page).
    val homeTarget = if (activeTab.isPrivate) "" else homeUrl()
    val atHome = activeTab.isStartPage ||
        (homeTarget.isNotBlank() && activeTab.url.trimEnd('/') == homeTarget.trimEnd('/'))
    BackHandler(
        enabled = browserActive && (fullscreenView != null || anyOverlayOpen || canGoBack || groupTabCount > 1 || !atHome)
    ) {
        when {
            fullscreenView != null -> {
                fullscreenView?.let { containerRef?.removeView(it) }
                fullscreenView = null
                fullscreenCallback?.onCustomViewHidden()
                fullscreenCallback = null
            }
            showPopupNotice -> { showPopupNotice = false; clearPendingPopups() }
            contextTargetUrl != null -> contextTargetUrl = null
            playlistPickFor != null -> playlistPickFor = null
            showDetected -> showDetected = false
            showFind -> {
                showFind = false
                activeWebView()?.clearMatches()
            }
            menuOpen -> menuOpen = false
            showTabs -> showTabs = false
            canGoBack -> activeWebView()?.goBack()
            else -> {
                // No page history: close this tab and land on its
                // previous neighbour in the same group (next one if it
                // was the first), the way back-through-tabs should feel.
                if (tabList.groupCount(activeTab.isPrivate) > 1) {
                    val target = tabList.neighbourAfterClose(activeTab)
                    closeTab(activeTab)
                    if (target != null && tabs.any { it.id == target.id }) {
                        activeTabId = target.id
                        if (!barFocused) fieldValue = TextFieldValue(displayFor(target.url))
                    }
                } else if (!atHome) {
                    // Lone tab on its first page: go home in this tab
                    // instead of leaving the app. From home itself the
                    // handler is disabled, so back exits there.
                    if (homeTarget.isBlank()) {
                        activeTab.url = ""
                        activeTab.errorText = null
                        fieldValue = TextFieldValue("")
                    } else {
                        loadUrl(homeTarget)
                    }
                }
            }
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

    /**
     * Share a URL — cleaned of trackers first. Failures are never
     * silent (the old handler swallowed them and presented as a dead
     * button): if no share target exists, the link is copied and a
     * toast says so.
     */
    fun shareUrl(rawUrl: String, title: String = "") {
        val url = CleanLink.strip(rawUrl)
        if (url.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        try {
            val chooser = Intent.createChooser(send, "Share page")
            if (context !is android.app.Activity) {
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("link", url))
            Toast.makeText(
                context, "Couldn't open sharing — link copied instead",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Renew: one tap clears cookies, web
     * storage, cache and history, then closes every tab to a fresh
     * start page. The recently-closed list is emptied last — closing
     * tabs records snapshots, and Renew must not leave them behind.
     */
    fun doFire() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        android.webkit.WebStorage.getInstance().deleteAllData()
        webViews.values.forEach { runCatching { it.clearCache(true) } }
        store.clearHistory()
        tabs.toList().forEach { closeTab(it) }
        tabList.clearClosed()
        showTabs = false
        Toast.makeText(context, "Cleared — everything's closed", Toast.LENGTH_SHORT).show()
    }

    // Renew now lives in Settings → Data; it raises a signal in
    // MainActivity and the clearing itself happens here, where
    // the tabs and WebViews live.
    LaunchedEffect(renewSignal) {
        if (renewSignal > 0) doFire()
    }

    fun shareCurrentPage() {
        // Share the WebView's committed URL (the page actually on
        // screen), falling back to the tracked tab URL.
        val committed = webViews[activeTab.id]?.url?.takeIf { it.isNotBlank() }
        shareUrl(committed ?: activeTab.url, activeTab.title)
    }

    val barAtBottom = store.setting("barPosition", "bottom") != "top"
    val immersive = store.setting("immersive", "false") == "true"

    // Download pill (beta.9): while a download runs,
    // a small frost pill hugs the address bar on its
    // content side, left-aligned - above the bar when
    // it rides at the bottom, below it when it rides
    // at the top (the find pill's precedent). Tapping
    // it opens the Offline library. It rides inside
    // the bar's AnimatedVisibility, so it leaves with
    // the chrome on scroll-down and returns with it.
    // The store revision already recomposes this
    // screen on progress saves (5% steps).
    @Composable
    fun downloadPill() {
        // Every download, media or file: the pill
        // is the in-browser progress indicator.
        // Tapping it opens the manager that holds
        // that download - the Offline library for
        // media, the Downloads page for files.
        val active = store.downloadsSnapshot().filter {
            it.status == "queued" ||
                it.status == "downloading" ||
                it.status == "paused"
        }
        if (active.isEmpty()) return
        val latest = active.maxByOrNull { it.id } ?: return
        val others = active.size - 1
        // Live percentages for file downloads:
        // media engines push progress themselves,
        // but DownloadManager progress only reaches
        // the store when something syncs it, so
        // while a file download is active the pill
        // syncs it every two seconds. The effect
        // lives exactly as long as the pill does,
        // and refresh() only writes when a status
        // or percentage actually changed.
        val hasFileDownload = active.any { !it.media && it.dmId >= 0 }
        LaunchedEffect(hasFileDownload) {
            if (!hasFileDownload) return@LaunchedEffect
            while (true) {
                Downloads.refresh(context, store)
                delay(2000)
            }
        }
        // One standard pill, fixed width: the
        // percentage first, then the name scrolling
        // (marquee) when it is too long to fit.
        Row(
            modifier = Modifier
                .padding(
                    start = 12.dp,
                    top = if (barAtBottom) 0.dp else 6.dp,
                    bottom = if (barAtBottom) 6.dp else 0.dp
                )
                .width(240.dp)
                .frostBar(RoundedCornerShape(50))
                .clickable {
                    onOpenScreen(
                        if (latest.media) Screen.Library
                        else Screen.Downloads
                    )
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Filled.Download,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            if (latest.progress >= 0) {
                Text(
                    "${latest.progress}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                latest.title,
                maxLines = 1,
                modifier = Modifier.weight(1f).basicMarquee(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (others > 0) {
                Text(
                    "+$others",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    fun addressBar() {
        // Safari iOS 26 Compact language, solid-frost material: floating
        // capsule groups. On scroll the WHOLE bar leaves (see the root
        // AnimatedVisibility) and returns on scroll-up — collapse-to-pill
        // was not enough (Robert's report, 2026-10-21).
        val pillShape = RoundedCornerShape(50)
        val contentColor = MaterialTheme.colorScheme.onSurface
        // 0.0.63 solid-frost material (Haze removed entirely — Robert's
        // ruling; the recipe now lives in the Phase C kit, ui/FrostKit.kt).
        val frost = Modifier.frostBar(pillShape)
        var dragTotal by remember { mutableStateOf(0f) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (canGoBack || canGoForward) {
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
                // Loading progress fills the pill itself, in the
                // theme's ink — a neutral grey-black that adapts to
                // light/dark (the accent-blue fill read as leftover
                // teal on device; Robert's ruling, 2026-10-21).
                if (progress in 1..99) {
                    Box(modifier = Modifier.matchParentSize().clip(pillShape)) {
                        Box(
                            modifier = Modifier.fillMaxHeight()
                                .fillMaxWidth(progress / 100f)
                                .background(
                                    Brush.horizontalGradient(
                                        0f to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
                                        0.7f to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
                                        1f to MaterialTheme.colorScheme.onSurface.copy(alpha = 0f)
                                    )
                                )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!barFocused && store.setting("showHomeButton", "true") == "true") {
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
            } else {
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
                // Media on this page (0.0.961): the network log
                // already sniffs media passively, so this button
                // appears the moment the page has some and opens
                // the Find media sheet. In the bar, not overlaid
                // on the page - page overlays break on locked-down
                // sites and cross-origin frames.
                if (!activeTab.isStartPage && MediaLog.forTab(activeTab.id).isNotEmpty()) {
                    Box(
                        modifier = Modifier.size(44.dp).then(frost)
                            .clickable { runMediaScan() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayCircle,
                            contentDescription = "Media on this page",
                            tint = contentColor,
                            modifier = Modifier.size(22.dp)
                        )
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
        // 0.0.63: no blur source exists anywhere — Haze is gone. The
        // surfaces below render plainly under solid-frost chrome.
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                activeTab.isStartPage -> {
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // The start page fills the whole screen so
                        // its wallpaper draws behind the capsule
                        // bar (item 26) and under the status bar
                        // (item 19) — the wallpaper is the status
                        // bar's tint now. Only the page CONTENT
                        // keeps clear of both, via the pads.
                        // Start-page pull (0.0.983): a pull forces
                        // an update check on the spot; the sections
                        // are store-live, so they refresh with it.
                        // The page's own list speaks nested scroll,
                        // so the standard box works here.
                        PullToRefreshBox(
                            isRefreshing = startPullRefreshing,
                            onRefresh = {
                                startPullRefreshing = true
                                UpdateChecker.checkAsync(store, force = true)
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            StartPage(
                                store = store,
                                onOpen = { url -> loadUrl(url) },
                                onOpenBook = onOpenBook,
                                onOpenUpdates = { onOpenScreen(Screen.Updates) },
                                onOpenSection = onOpenScreen,
                                onOpenConsole = onOpenGamesConsole,
                                onOpenGame = onOpenGame,
                                contentTopPad = (if (barAtBottom || !immersive) {
                                    WindowInsets.statusBars.asPaddingValues()
                                        .calculateTopPadding()
                                } else {
                                    0.dp
                                }) + if (barAtBottom) 0.dp else 92.dp,
                                contentBottomPad = if (barAtBottom) 92.dp else 0.dp
                            )
                        }
                    }
                }
                activeTab.sslErrorText != null -> Box(
                    modifier = Modifier.fillMaxSize()
                        .then(
                            if (!immersive) Modifier.windowInsetsPadding(WindowInsets.systemBars)
                            else Modifier
                        )
                ) {
                    CertErrorPage(
                        url = activeTab.sslErrorUrl.ifBlank { activeTab.url },
                        message = activeTab.sslErrorText ?: "",
                        onBack = {
                            activeTab.sslErrorText = null
                            // The cancelled load never replaced the
                            // current page: dismissing this screen shows
                            // it again. Only a fresh tab (nothing loaded
                            // yet) goes to the start page instead.
                            val current = activeWebView()?.url
                            if (current.isNullOrBlank() || current == "about:blank") {
                                activeTab.url = ""
                                if (!barFocused) fieldValue = TextFieldValue("")
                            }
                        },
                        onProceed = {
                            val host = Uri.parse(activeTab.sslErrorUrl).host ?: ""
                            if (host.isNotBlank()) activeTab.sslProceedHosts.add(host)
                            val target = activeTab.sslErrorUrl.ifBlank { activeTab.url }
                            activeTab.sslErrorText = null
                            val wv = activeWebView()
                            if (wv != null && target.isNotBlank()) wv.loadUrl(target)
                        }
                    )
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
                    // Pull-to-refresh (Phase G): nested scrolling
                    // hands the pull to this box only when the
                    // WebView cannot scroll up any further — the
                    // very top of the page, nowhere else. It wraps
                    // only this branch, so the start page and the
                    // error pages never offer it. Outside immersive
                    // mode the box carries the system-bar insets so
                    // the page clears the status bar and cutout.
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .then(
                                if (!immersive) Modifier.windowInsetsPadding(WindowInsets.systemBars)
                                else Modifier
                            )
                    ) {
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
                            // Any unpainted gap during a tab switch
                            // then matches the page instead of showing
                            // through to white (Phase G paint work).
                            container.setBackgroundColor(activeTab.pageBg ?: chromeBgArgb)
                            wv.setBackgroundColor(activeTab.pageBg ?: chromeBgArgb)
                            if (wv.url == null && activeTab.url.isNotBlank()) {
                                wv.loadUrl(activeTab.url)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (pullOffsetPx > 0f || pullRefreshing) {
                        Box(
                            modifier = Modifier.align(Alignment.TopCenter)
                                .offset {
                                    IntOffset(0, (pullOffsetPx * 0.45f).toInt())
                                }
                                .padding(top = 16.dp)
                                .size(38.dp)
                                .frostBar(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (pullRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    Icons.Filled.ArrowDownward,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                        .rotate(
                                            (pullOffsetPx / pullTriggerPx)
                                                .coerceIn(0f, 1f) * 180f
                                        ),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    }
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
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                        .frostBar(RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Block, contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    when {
                                        pendingPopupUrls.size > 1 ->
                                            "${pendingPopupUrls.size} popups blocked on $popupPageHost"
                                        popupPageHost.isNotBlank() -> "Popup blocked on $popupPageHost"
                                        else -> "Popup blocked"
                                    },
                                    style = MaterialTheme.typography.titleSmall
                                )
                                pendingPopupUrls.firstOrNull()?.let { firstUrl ->
                                    Text(
                                        firstUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showPopupNotice = false; clearPendingPopups() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (pendingPopupUrls.isNotEmpty()) {
                                Button(onClick = {
                                    openPendingPopups()
                                    showPopupNotice = false
                                }) {
                                    Text(
                                        if (pendingPopupUrls.size > 1) {
                                            "Open all (${pendingPopupUrls.size})"
                                        } else {
                                            "Open once"
                                        }
                                    )
                                }
                            }
                            OutlinedButton(onClick = {
                                // Whitelist the site, then open the
                                // popup so the tap still lands where
                                // the user aimed.
                                val host = popupPageHost
                                if (host.isNotBlank()) {
                                    store.putSiteSetting(
                                        store.siteSetting(host).copy(allowPopups = true, blockPopups = false)
                                    )
                                }
                                openPendingPopups()
                                showPopupNotice = false
                            }) { Text("Always allow") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                showPopupNotice = false
                                clearPendingPopups()
                            }) { Text("Deny") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                // Blacklist the site: its popups are
                                // blocked silently from now on (undo in
                                // Settings, Per-site controls).
                                val host = popupPageHost
                                if (host.isNotBlank()) {
                                    store.putSiteSetting(
                                        store.siteSetting(host).copy(allowPopups = false, blockPopups = true)
                                    )
                                }
                                showPopupNotice = false
                                clearPendingPopups()
                            }) { Text("Always deny") }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            // The bar fully disappears on scroll down (barHidden) and
            // slides back on scroll up. It never hides while the user
            // is typing in it, on the start page (nothing to scroll),
            // and it also leaves for HTML5 fullscreen video.
            visible = fullscreenView == null && !activeTab.isAppMode &&
                (!barHidden || barFocused || activeTab.isStartPage),
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
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!barAtBottom) addressBar()
                downloadPill()
                if (barAtBottom) addressBar()
            }
        }

        // The pill on its own: it used to ride inside
        // the bar above, so scrolling the page hid the
        // bar and the download indicator with it, and
        // a download in progress looked like it had
        // no indicator at all. While the bar is hidden
        // the pill renders here instead, at the same
        // screen edge, so it stays visible until the
        // download finishes.
        if (barHidden && !barFocused && !activeTab.isStartPage &&
            fullscreenView == null && !activeTab.isAppMode) {
            Column(
                modifier = Modifier
                    .align(
                        if (barAtBottom) Alignment.BottomCenter
                        else Alignment.TopCenter
                    )
                    .then(
                        if (barAtBottom) Modifier.windowInsetsPadding(
                            WindowInsets.navigationBars
                        )
                        else if (!immersive) Modifier.windowInsetsPadding(
                            WindowInsets.statusBars
                        )
                        else Modifier
                    )
                    .padding(
                        top = if (barAtBottom) 0.dp else 10.dp,
                        bottom = if (barAtBottom) 10.dp else 0.dp
                    )
                    .fillMaxWidth()
            ) {
                downloadPill()
            }
        }

        // App mode (0.0.99997): no browser chrome at all -
        // just the page and a small way out. The button
        // closes the app-mode tab, landing back on whatever
        // was open beneath it.
        if (activeTab.isAppMode && fullscreenView == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.surface
                            .copy(alpha = 0.92f)
                    )
                    .clickable { closeTab(activeTab) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Leave app mode",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // ---- Find in page (0.0.990, Robert): no more
        // dialog. The typing pill hugs the address bar,
        // just above it (below it when the bar rides at
        // the top); the match count and the steppers live
        // in a small pill at the top. Matches highlight on
        // the page itself via findAllAsync.
        if (showFind) {
            FindBar(
                barAtBottom = barAtBottom,
                immersive = immersive,
                findText = findText,
                findCount = findCount,
                findOrdinal = findOrdinal,
                onFindTextChange = {
                    findText = it
                    activeWebView()?.findAllAsync(it)
                },
                onFindNext = { forward ->
                    activeWebView()?.findNext(forward)
                },
                onClose = {
                    activeWebView()?.clearMatches()
                    showFind = false
                }
            )
        }

        // ---- Local suggestions: matches from this device's own
        // bookmarks and history only. Nothing is sent anywhere. Shown
        // while typing in the bar (not for the just-focused full URL).
        val suggestionQuery = fieldValue.text.trim()
        val suggestions: List<Triple<String, String, Boolean>> =
            if (barFocused && suggestionQuery.isNotEmpty() &&
                suggestionQuery != activeTab.url.trim()
            ) {
                val q = suggestionQuery.lowercase()
                val fromBookmarks = store.bookmarks
                    .filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
                    .map { Triple(it.title, it.url, true) }
                val fromHistory = store.history
                    .filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
                    .map { Triple(it.title, it.url, false) }
                (fromBookmarks + fromHistory).distinctBy { it.second }.take(6)
            } else {
                emptyList()
            }
        if (suggestions.isNotEmpty()) {
            SuggestionsCard(
                suggestions = suggestions,
                barAtBottom = barAtBottom,
                immersive = immersive,
                suggestionQuery = suggestionQuery,
                onPick = { url ->
                    loadUrl(url)
                    focusManager.clearFocus()
                }
            )
        }

        // ---- Tab grid overlay ----
        if (showRecentlyClosed) {
            FrostDialog(
                onDismissRequest = { showRecentlyClosed = false },
                title = { Text("Recently closed") },
                text = {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(tabList.closedSnapshots.toList(), key = { it.url }) { snap ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        snap.title.ifBlank { "Untitled" },
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(snap.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                modifier = Modifier.clickable {
                                    tabList.removeClosed(snap)
                                    showRecentlyClosed = false
                                    showTabs = false
                                    openNewTab(snap.url)
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            tabList.clearClosed()
                            showRecentlyClosed = false
                        }
                    ) { Text("Clear list") }
                },
                dismissButton = {
                    TextButton(onClick = { showRecentlyClosed = false }) { Text("Close") }
                }
            )
        }
        if (showTabs) {
            Box(
                modifier = Modifier.fillMaxSize()
                    // Solid backdrop (0.0.63): a soft vertical gradient in
                    // theme colours instead of the blurred page behind —
                    // crisp cards, no grey fog, nothing captured.
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    )
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
                    OutlinedTextField(
                        value = tabSearch,
                        onValueChange = { tabSearch = it },
                        placeholder = { Text("Search tabs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val keep = tabs.firstOrNull {
                                    it.id == activeTabId && it.isPrivate == gridPrivate
                                } ?: tabs.firstOrNull { it.isPrivate == gridPrivate }
                                keep?.let { closeOtherTabs(it) }
                            },
                            enabled = tabs.count { it.isPrivate == gridPrivate } > 1
                        ) { Text("Close others") }
                        TextButton(
                            onClick = { closeAllTabsInGroup(gridPrivate) },
                            enabled = tabs.any { it.isPrivate == gridPrivate }
                        ) { Text("Close all") }
                        TextButton(onClick = {
                            val cur = store.setting("tabViewMode", "grid")
                            store.putSetting(
                                "tabViewMode",
                                if (cur == "list") "grid" else "list"
                            )
                        }) {
                            Text(
                                if (store.setting("tabViewMode", "grid") == "list") {
                                    "Grid view"
                                } else {
                                    "List view"
                                }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (!gridPrivate && tabList.closedSnapshots.isNotEmpty()) {
                            TextButton(onClick = { showRecentlyClosed = true }) {
                                Text("Recently closed (${tabList.closedSnapshots.size})")
                            }
                        }
                    }
                    val gridTabs = tabs.filter {
                        it.isPrivate == gridPrivate &&
                            (tabSearch.isBlank() ||
                                it.title.contains(tabSearch, ignoreCase = true) ||
                                it.url.contains(tabSearch, ignoreCase = true))
                    }
                    // Tab groups (0.0.992): sections in
                    // first-appearance order, ungrouped
                    // last; searching flattens to one list.
                    val listMode =
                        store.setting("tabViewMode", "grid") == "list"
                    val tabSections:
                        List<Pair<String, List<TabState>>> =
                        if (tabSearch.isNotBlank()) {
                            listOf("" to gridTabs)
                        } else {
                            val byGroup = gridTabs.groupBy { it.group }
                            val out =
                                mutableListOf<Pair<String, List<TabState>>>()
                            byGroup.keys.filter { it.isNotBlank() }
                                .forEach { name ->
                                    out.add(name to byGroup.getValue(name))
                                }
                            byGroup[""]?.takeIf { it.isNotEmpty() }
                                ?.let { out.add("" to it) }
                            if (out.isEmpty()) {
                                out.add("" to gridTabs)
                            }
                            out
                        }
                    val showGroupHeaders =
                        tabSections.size > 1 ||
                            tabSections.firstOrNull()?.first
                                ?.isNotBlank() == true
                    if (tabs.none { it.isPrivate == gridPrivate }) {
                        Text(
                            if (gridPrivate) "No private tabs yet — open one below." else "No tabs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    if (gridTabs.isEmpty() && tabs.any { it.isPrivate == gridPrivate }) {
                        Text(
                            "No tabs match your search.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (listMode) 1 else 2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        tabSections.forEach { (groupName, sectionTabs) ->
                            if (showGroupHeaders) {
                                item(
                                    key = "hdr-$gridPrivate-$groupName",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Text(
                                        groupName.ifBlank { "Ungrouped" },
                                        style = MaterialTheme.typography
                                            .titleSmall,
                                        color = MaterialTheme.colorScheme
                                            .primary,
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                    )
                                }
                            }
                            items(sectionTabs, key = { it.id }) { tab ->
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
                                modifier = Modifier.fillMaxWidth()
                                    .height(if (listMode) 72.dp else 96.dp)
                                    .combinedClickable(
                                        onClick = {
                                            activeTabId = tab.id
                                            if (!barFocused) {
                                                fieldValue = TextFieldValue(
                                                    displayFor(tab.url)
                                                )
                                            }
                                            showTabs = false
                                        },
                                        onLongClick = {
                                            groupEditTab = tab
                                        }
                                    ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (tab.id == activeTabId)
                                        Frost.cardColor()
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                border = BorderStroke(
                                    if (tab.id == activeTabId) 2.dp else 0.5.dp,
                                    if (tab.id == activeTabId)
                                        MaterialTheme.colorScheme.primary
                                    else Frost.hairline()
                                ),
                                shape = Frost.CardShape
                            ) {
                                Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (tab.favicon != null) {
                                            Image(
                                                bitmap = tab.favicon!!.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        } else if (tab.isPrivate) {
                                            Icon(
                                                Icons.Filled.Lock, contentDescription = "Private",
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(
                                            if (tab.isStartPage) "Start page"
                                            else tab.title.ifBlank { "New tab" },
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { duplicateTab(tab) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate tab", modifier = Modifier.size(15.dp))
                                        }
                                        IconButton(
                                            onClick = { closeTab(tab) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = "Close tab", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    if (!tab.isStartPage) {
                                        Text(
                                            Uri.parse(tab.url).host ?: tab.url,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .frostBar(RoundedCornerShape(50), 8.dp)
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
        BrowserMenuSheet(
            store = store,
            tab = activeTab,
            readabilitySrc = readabilitySrc,
            webView = { activeWebView() },
            onDismiss = { menuOpen = false },
            onNewTab = { url, priv -> openNewTab(url, isPrivate = priv) },
            onShare = { shareCurrentPage() },
            onReadArticle = onReadArticle,
            onMediaScan = { runMediaScan() },
            onFind = { showFind = true },
            onToggleDesktop = {
                activeTab.desktopMode = !activeTab.desktopMode
                webViews.remove(activeTab.id)?.destroy()
                if (activeTab.url.isNotBlank()) {
                    val wv = webViews.getOrPut(activeTab.id) {
                        createWebView(activeTab)
                    }
                    wv.loadUrl(activeTab.url)
                }
            },
            onOpenScreen = onOpenScreen,
            onVaultFill = { startVaultFill() },
            onPickElement = {
                if (pickMode) {
                    pickMode = false
                    pickInfo = null
                    activeWebView()?.evaluateJavascript(
                        PICKER_CLEAR_JS, null
                    )
                } else {
                    pickMode = true
                    Toast.makeText(
                        context,
                        "Tap the element to block",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            pickMode = pickMode
        )
    }

    // ---- Vault fill chooser (several saved logins match this site) ----
    if (showVaultFill) {
        FrostSheet(onDismiss = { showVaultFill = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                Text(
                    "Choose a login to fill",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
                GroupedCard {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        vaultFillChoices.forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        showVaultFill = false
                                        fillFromVault(entry)
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Lock, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.padding(start = 16.dp)) {
                                    Text(
                                        entry.site, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        entry.username, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- Long-press context sheet ----
    contextTargetUrl?.let { target ->
        FrostSheet(onDismiss = { contextTargetUrl = null }) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                GroupedCard {
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
                    cm.setPrimaryClip(ClipData.newPlainText("link", CleanLink.strip(target)))
                }
                MenuRow("Share", Icons.Filled.Share) {
                    contextTargetUrl = null
                    shareUrl(target)
                }
                MenuRow("Block this element", Icons.Filled.Block) {
                    blockNetTarget = target
                    contextTargetUrl = null
                }
                    }
                }
            }
        }
    }

    // Element picker: network rule for a
    // long-pressed link/image (beta.18).
    blockNetTarget?.let { target ->
        var width by remember { mutableStateOf("url") }
        FrostDialog(
            onDismissRequest = { blockNetTarget = null },
            title = { Text("Block this element") },
            text = {
                Column {
                    Text(
                        target, maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = { width = "url" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            (if (width == "url") "✓ " else "") +
                                "This address",
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    TextButton(
                        onClick = { width = "host" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            (if (width == "host") "✓ " else "") +
                                "Everything from this host",
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (width == "url") {
                            "Blocks this exact address everywhere. " +
                                "Other pages from the same host keep working."
                        } else {
                            "Blocks everything loaded from this host, " +
                                "everywhere. If a page breaks, remove it " +
                                "in Settings > Privacy & blocking > " +
                                "Blocked elements."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val uri = Uri.parse(target)
                        val host = uri.host?.lowercase() ?: ""
                        if (host.isBlank()) {
                            Toast.makeText(
                                context,
                                "That address can't be blocked",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            val rule =
                                if (width == "host") {
                                    UserBlockRules.NetRule.Host(host)
                                } else {
                                    UserBlockRules.NetRule.Url(
                                        UserBlockRules.urlKey(
                                            uri.scheme ?: "",
                                            host,
                                            uri.path ?: ""
                                        )
                                    )
                                }
                            val rules = UserBlockRules.parseNet(
                                store.setting(UserBlockRules.NET_KEY, "")
                            )
                            if (rule !in rules) {
                                store.putSetting(
                                    UserBlockRules.NET_KEY,
                                    UserBlockRules.serializeNet(rules + rule)
                                )
                                syncUserRules()
                            }
                            Toast.makeText(
                                context, "Blocked", Toast.LENGTH_SHORT
                            ).show()
                        }
                        blockNetTarget = null
                        activeWebView()?.reload()
                    }
                ) { Text("Block") }
            },
            dismissButton = {
                TextButton(onClick = { blockNetTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Element picker: cosmetic rule for a tapped
    // element (beta.18). Wider/narrower re-pick
    // through the bridge; blocking saves the rule
    // for this site only and reloads.
    pickInfo?.let { pick ->
        var blockMode by remember(pick.selector) {
            mutableStateOf("hide")
        }
        var netWidth by remember(pick.selector) {
            mutableStateOf("url")
        }
        val pickSrc = if (pick.src.isNotBlank()) {
            runCatching {
                java.net.URL(java.net.URL(activeTab.url), pick.src)
                    .toString()
            }.getOrNull()
                ?.takeIf { it.startsWith("http") }
        } else {
            null
        }
        val pickSrcHost = pickSrc?.let {
            Uri.parse(it).host?.lowercase()
        } ?: ""
        FrostDialog(
            onDismissRequest = {
                activeWebView()?.evaluateJavascript(PICKER_CLEAR_JS, null)
                pickMode = false
                pickInfo = null
            },
            title = { Text("Block this element") },
            text = {
                Column {
                    val label = buildString {
                        append(pick.tag)
                        if (pick.id.isNotBlank()) append(" #${pick.id}")
                        if (pick.classes.isNotBlank()) {
                            append(" .")
                            append(pick.classes.trim().split("\\s+".toRegex()).first())
                        }
                        append(" (${pick.width}×${pick.height})")
                    }
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        pick.selector, maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                activeWebView()?.evaluateJavascript(
                                    PICKER_NARROW_JS, null
                                )
                            }
                        ) { Text("Narrower") }
                        OutlinedButton(
                            onClick = {
                                activeWebView()?.evaluateJavascript(
                                    PICKER_WIDEN_JS, null
                                )
                            }
                        ) { Text("Wider") }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { blockMode = "hide" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            (if (blockMode == "hide") "✓ " else "") +
                                "Hide this element",
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        "Hides this element on this site only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (pickSrcHost.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Or block what it loads, everywhere:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                blockMode = "url"
                                netWidth = "url"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                (if (blockMode == "url") "✓ " else "") +
                                    "This address",
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        TextButton(
                            onClick = {
                                blockMode = "host"
                                netWidth = "host"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                (if (blockMode == "host") "✓ " else "") +
                                    "Everything from this host",
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (blockMode != "hide") {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (netWidth == "url") {
                                    "Blocks this exact address everywhere. " +
                                        "Other pages from the same host " +
                                        "keep working."
                                } else {
                                    "Blocks everything loaded from this " +
                                        "host, everywhere. If a page " +
                                        "breaks, remove it in Settings > " +
                                        "Privacy & blocking > " +
                                        "Blocked elements."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (blockMode == "hide") {
                            val host = Uri.parse(activeTab.url).host
                                ?.lowercase() ?: ""
                            if (host.isBlank() ||
                                !UserBlockRules.isSafeSelector(pick.selector)
                            ) {
                                Toast.makeText(
                                    context,
                                    "That element can't be blocked safely",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val rule = UserBlockRules.CosmeticRule(
                                    host, pick.selector
                                )
                                val rules = UserBlockRules.parseCosmetic(
                                    store.setting(UserBlockRules.COSMETIC_KEY, "")
                                )
                                if (rule !in rules) {
                                    store.putSetting(
                                        UserBlockRules.COSMETIC_KEY,
                                        UserBlockRules.serializeCosmetic(
                                            rules + rule
                                        )
                                    )
                                    syncUserRules()
                                }
                                Toast.makeText(
                                    context, "Blocked", Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            val uri = Uri.parse(pickSrc ?: "")
                            val host = uri.host?.lowercase() ?: ""
                            if (host.isBlank()) {
                                Toast.makeText(
                                    context,
                                    "That address can't be blocked",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                val rule =
                                    if (netWidth == "host") {
                                        UserBlockRules.NetRule.Host(host)
                                    } else {
                                        UserBlockRules.NetRule.Url(
                                            UserBlockRules.urlKey(
                                                uri.scheme ?: "",
                                                host,
                                                uri.path ?: ""
                                            )
                                        )
                                    }
                                val rules = UserBlockRules.parseNet(
                                    store.setting(UserBlockRules.NET_KEY, "")
                                )
                                if (rule !in rules) {
                                    store.putSetting(
                                        UserBlockRules.NET_KEY,
                                        UserBlockRules.serializeNet(
                                            rules + rule
                                        )
                                    )
                                    syncUserRules()
                                }
                                Toast.makeText(
                                    context, "Blocked", Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        activeWebView()?.evaluateJavascript(
                            PICKER_CLEAR_JS, null
                        )
                        pickMode = false
                        pickInfo = null
                        activeWebView()?.reload()
                    }
                ) { Text("Block") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        activeWebView()?.evaluateJavascript(
                            PICKER_CLEAR_JS, null
                        )
                        pickMode = false
                        pickInfo = null
                    }
                ) { Text("Cancel") }
            }
        )
    }

    // Find in page is the pill pair by the address bar
    // (0.0.990) — the old dialog is gone.

    groupEditTab?.let { tab ->
        var newGroupName by remember { mutableStateOf("") }
        FrostDialog(
            onDismissRequest = { groupEditTab = null },
            title = { Text("Move to group") },
            text = {
                Column {
                    Text(
                        tab.title.ifBlank { tab.url },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            tab.group = ""
                            groupEditTab = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (tab.group.isEmpty()) {
                                "No group ✓"
                            } else {
                                "No group"
                            }
                        )
                    }
                    tabs.filter { it.isPrivate == tab.isPrivate }
                        .map { it.group }
                        .filter { it.isNotBlank() }
                        .distinct()
                        .forEach { g ->
                            TextButton(
                                onClick = {
                                    tab.group = g
                                    groupEditTab = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (tab.group == g) "$g ✓" else g)
                            }
                        }
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("New group name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newGroupName.isNotBlank(),
                    onClick = {
                        tab.group = newGroupName.trim()
                        groupEditTab = null
                    }
                ) { Text("Create & move") }
            },
            dismissButton = {
                TextButton(onClick = { groupEditTab = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDetected) {
        FrostSheet(onDismiss = { showDetected = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
                GroupedCard {
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
                                                PlayerActivity.launch(context, media.url, media.label.ifBlank { activeTab.title }, referer = activeTab.url, privateSession = activeTab.isPrivate)
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
        FrostDialog(
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
internal fun MenuRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
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

/** Probe for a page's effective background colour (body first, then
 *  the root element), returned as a CSS rgb()/rgba() string. */
private const val PAGE_BG_PROBE_JS =
    "(function(){try{" +
        "var b=getComputedStyle(document.body).backgroundColor;" +
        "var h=getComputedStyle(document.documentElement).backgroundColor;" +
        "function ok(c){return c&&c!=='transparent'&&c!=='rgba(0, 0, 0, 0)'}" +
        "return ok(b)?b:(ok(h)?h:'')" +
        "}catch(e){return ''}})()"

/** Parse a CSS rgb()/rgba() string (JSON-quoted by evaluateJavascript)
 *  into an opaque ARGB int; null when transparent or unparseable. */
private fun parseCssColor(raw: String?): Int? {
    if (raw.isNullOrBlank()) return null
    val nums = Regex("\\d+(?:\\.\\d+)?").findAll(raw)
        .map { it.value.toFloat() }.toList()
    if (nums.size < 3) return null
    val alpha = if (nums.size >= 4) nums[3] else 1f
    if (alpha <= 0.01f) return null
    return android.graphics.Color.rgb(
        nums[0].toInt().coerceIn(0, 255),
        nums[1].toInt().coerceIn(0, 255),
        nums[2].toInt().coerceIn(0, 255)
    )
}
