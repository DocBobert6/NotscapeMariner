package net.notscape.browser.browser

/**
 * File-scope helpers and small composables for the browser screen
 * (Phase O4). Pure code motion out of BrowserScreen.kt, which now
 * holds only the BrowserScreen composable itself; behaviour is
 * unchanged. Former `private` declarations are `internal` here
 * because BrowserScreen.kt still uses them.
 */

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.notscape.browser.media.MergePick

/** One picked element (beta.18): the selector the
 * confirm dialog offers, plus what it shows about
 * the element. Parsed from the picker's JSON. */
internal data class PickInfo(
    val selector: String,
    val tag: String,
    val id: String,
    val classes: String,
    val width: Int,
    val height: Int,
    val src: String = ""
)

/** Parse one bridge pick; null when the JSON
 * is unusable or the selector came back blank.
 * Top-level (not a composable local) so unit
 * tests can pin it down; the picker call sites
 * in this file are unchanged. */
internal fun parsePick(json: String): PickInfo? = runCatching {
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

/** True when [targetUrl] is the same page as
 * [currentUrl] up to a fragment change: a plain
 * anchor hop, which the "Open links in a new
 * tab" setting must leave alone. An exact
 * self-link with no fragment on either side is
 * NOT an anchor here — it still opens a tab.
 * Top-level so unit tests can pin it down. */
internal fun isAnchorLink(currentUrl: String, targetUrl: String): Boolean {
    if (currentUrl.isBlank() || targetUrl.isBlank()) return false
    if (currentUrl.substringBefore('#') != targetUrl.substringBefore('#')) return false
    return '#' in currentUrl || '#' in targetUrl
}

/** History is recorded for normal tabs only: a private tab
 * leaves no history entry, so nothing of it can surface in
 * suggestions, frequently visited, or the history list.
 * Top-level so unit tests can pin it down. */
internal fun shouldRecordHistory(isPrivate: Boolean): Boolean = !isPrivate

/** A detected-media "Add to playlist" tap persists the media
 * URL into a playlist that survives the session, so the button
 * exists for normal tabs only. Top-level so unit tests can
 * pin it down. */
internal fun playlistAddAllowed(isPrivate: Boolean): Boolean = !isPrivate

/** Tabs-overlay view-toggle label: a single word naming the view a
 * tap switches TO (list mode shows "Grid", grid mode shows "List").
 * One word keeps the actions row on a single line; the button's Text
 * also forces maxLines = 1 with softWrap off so it can never wrap
 * again even on a narrow screen. Top-level so unit tests can
 * pin it down. */
internal fun tabViewToggleLabel(listMode: Boolean): String =
    if (listMode) "Grid" else "List"

/** Address-bar display: the FULL URL, verbatim (scheme, host, path,
 * query). The bar used to show the host only, which also broke copy:
 * the bar has no copy button, so a long-press copy takes the displayed
 * field text, and a copied host lands on the homepage. Blank and
 * about:blank still display as "".
 * Top-level so unit tests can pin it down. */
internal fun displayFor(url: String): String =
    if (url.isBlank() || url == "about:blank") ""
    else url

/** Beta.66 #2: which tabs may be shed under memory
 *  pressure — every tab except the active one. Pure,
 *  so unit tests pin it down without a WebView. */
internal fun backgroundTabsToDiscard(
    tabIds: List<Int>, activeId: Int
): List<Int> = tabIds.filter { it != activeId }

/** Beta.66 #3: the file:// URL for a saved offline page. */
internal fun offlineFileUrl(path: String): String = "file://$path"

/** Beta.66 #3: is [path] a saved offline page inside the app's
 *  offline dir? Canonical paths, so ".." escapes and symlinks
 *  cannot smuggle another file in. Pure, so unit tests pin it
 *  down without a WebView. */
internal fun isOfflineFilePath(path: String, offlineDir: java.io.File): Boolean {
    if (!path.endsWith(".mht")) return false
    return runCatching {
        val file = java.io.File(path).canonicalFile
        val dir = offlineDir.canonicalFile
        file.path.startsWith(dir.path + java.io.File.separator)
    }.getOrDefault(false)
}

/** Tab-grid thumbnail size: fit a tab's viewport into a box no
 * wider than [maxW], keeping the aspect ratio. Pure math, so unit
 * tests pin it down without a WebView. A zero viewport gives an
 * empty size. */
internal fun thumbnailSizeFor(viewW: Int, viewH: Int, maxW: Int = 320): Pair<Int, Int> {
    if (viewW <= 0 || viewH <= 0 || maxW <= 0) return 0 to 0
    val w = minOf(viewW, maxW)
    val h = ((viewH.toLong() * w) / viewW).toInt().coerceAtLeast(1)
    return w to h
}

/** The picker's page side (beta.18). Installed on
 * entering pick mode, removed on exit: a capturing
 * click listener that outlines the tapped element
 * and reports it through the bridge, instead of
 * letting the tap navigate. Wider/narrower walk
 * the element stack; clear restores the outline
 * and drops the listener. */
internal const val PICKER_INSTALL_JS = """
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

internal const val PICKER_WIDEN_JS =
    "(function(){var p=window.__nsPicker; if(p) p.widen();})()"
internal const val PICKER_NARROW_JS =
    "(function(){var p=window.__nsPicker; if(p) p.narrow();})()"
internal const val PICKER_CLEAR_JS =
    "(function(){var p=window.__nsPicker; if(p) p.clear();})()"
internal const val PICKER_LAST_PICK_JS =
    "(function(){var p=window.__nsPicker; return p && p.lastPick ? p.lastPick : '';})()"

/** The picker's bridge (beta.18): the page calls
 * this one method with a picked element's JSON.
 * It runs off the main thread, so it posts home;
 * it can do nothing on its own — blocking always
 * waits for the user's tap in the confirm dialog. */
internal class PickerBridge(private val onPick: (String) -> Unit) {
    private val mainHandler =
        android.os.Handler(android.os.Looper.getMainLooper())

    @android.webkit.JavascriptInterface
    fun onPick(json: String) {
        mainHandler.post { onPick(json) }
    }
}

/** A download awaiting its confirmation dialog (beta.44): the
 *  dialog edits the file name and destination before start(). */
internal data class PendingDownload(
    val title: String,
    val url: String,
    val media: Boolean,
    val contentType: String?,
    val suggestedName: String?,
    val referer: String?,
    val isPrivate: Boolean,
    /** Set for merges: the dialog renames and places the
     *  merged file instead of starting it directly. */
    val videoUrl: String? = null,
    val audioUrl: String? = null
)

/** One quality choice in the download dialog (audit D-m13):
 *  an HLS variant URL, or a DASH bandwidth cap. */
internal data class DownloadVariant(
    val label: String,
    val url: String?,
    val bandwidth: Long
)

/** A pending site-permission decision (audit P-M2):
 *  the origin, the resources it asked for
 *  ("camera", "microphone", "geolocation"), and the
 *  callbacks that grant a subset or deny all. */
internal data class PermPrompt(
    val origin: String,
    val resources: List<String>,
    val onGrant: (List<String>) -> Unit,
    val onDeny: () -> Unit
)

/** Beta.62 #22: a vault fill suggestion for the login field
 *  that has focus — the tab it belongs to, the page host, and
 *  the kind ('login'; 'generate' arrives with #42). */
internal data class FieldSuggestion(
    val tabId: Int,
    val host: String,
    val kind: String
)

/** Audit P-m18: whether a WebView fullscreen video is showing.
 *  MainActivity reads this in onUserLeaveHint; BrowserScreen
 *  sets it in onShow/onHideCustomView. */
object FullscreenState {
    @Volatile var isFullscreen: Boolean = false
}

/** Startup field text: null means no incoming URL, so the
 *  address field starts empty. Kept in its own tiny method
 *  so the null check never lives inside the giant BrowserScreen
 *  method body, where the verifier mis-allocates it. */
internal fun startTextFor(url: String?): String = url ?: ""


@Composable
internal fun MenuRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Probe for a page's effective background colour (body first, then
 *  the root element), returned as a CSS rgb()/rgba() string. */
internal const val PAGE_BG_PROBE_JS =
    "(function(){try{" +
        "var b=getComputedStyle(document.body).backgroundColor;" +
        "var h=getComputedStyle(document.documentElement).backgroundColor;" +
        "function ok(c){return c&&c!=='transparent'&&c!=='rgba(0, 0, 0, 0)'}" +
        "return ok(b)?b:(ok(h)?h:'')" +
        "}catch(e){return ''}})()"

/** Parse a CSS rgb()/rgba() string (JSON-quoted by evaluateJavascript)
 *  into an opaque ARGB int; null when transparent or unparseable. */
internal fun parseCssColor(raw: String?): Int? {
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

/** One row of the merge confirm dialog: the file label
 * plus its Video / Audio role toggle, with a check on
 * the current role (the block dialog's pattern). */
@Composable
internal fun MergeRoleRow(
    label: String,
    role: MergePick.Role,
    onRole: (MergePick.Role) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { onRole(MergePick.Role.VIDEO) }) {
            Text(if (role == MergePick.Role.VIDEO) "Video ✓" else "Video")
        }
        TextButton(onClick = { onRole(MergePick.Role.AUDIO) }) {
            Text(if (role == MergePick.Role.AUDIO) "Audio ✓" else "Audio")
        }
    }
}

/**
 * F-M5: the downloads the chrome pill may show. A
 * private download is never among them — its title
 * must not appear in the chrome on any tab — so the
 * filter is a pure function the pill and its tests
 * share, rather than an inline predicate.
 */
fun activePillDownloads(
    downloads: List<net.notscape.browser.data.DownloadRecord>
): List<net.notscape.browser.data.DownloadRecord> =
    downloads.filter {
        !it.isPrivate && (
            it.status == "queued" ||
                it.status == "downloading" ||
                it.status == "paused"
        )
    }


/**
 * Audit P-m22: the bridge between MainActivity's
 * selection ActionMode wrapper and the browser's live
 * WebView. Set by BrowserScreen; MainActivity only
 * calls, never owns, the WebView.
 */
object SelectionActions {
    var searchSelected: (() -> Unit)? = null
    var shareSelected: (() -> Unit)? = null

    /** Decode the JSON-quoted string evaluateJavascript returns. */
    fun decodeJsString(raw: String?): String? {
        if (raw == null || raw.length < 2 || !raw.startsWith("\"")) return null
        val body = raw.substring(1, raw.length - 1)
        val out = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                when (body[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'r' -> out.append('\r')
                    '"' -> out.append('"')
                    '\\' -> out.append('\\')
                    else -> out.append(body[i + 1])
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString().takeIf { it.isNotBlank() }
    }
}

/** One row of the back/forward history sheet: the
 *  entry's title and URL, and how many steps from the
 *  current page it sits (negative = back, positive =
 *  forward). Pure so the unit suite can pin it. */
data class HistorySheetEntry(
    val title: String,
    val url: String,
    val steps: Int
)

/** The entries for one direction of the history sheet,
 *  nearest first: [items] are (title, url) pairs in
 *  WebBackForwardList order, [currentIndex] the page
 *  showing now, [direction] -1 for back, +1 for
 *  forward. Anything else is empty. A blank title
 *  falls back to the URL. */
internal fun historySheetEntries(
    currentIndex: Int,
    items: List<Pair<String, String>>,
    direction: Int
): List<HistorySheetEntry> {
    if (direction != -1 && direction != 1) return emptyList()
    val out = ArrayList<HistorySheetEntry>()
    var i = currentIndex + direction
    while (i in items.indices) {
        val (title, url) = items[i]
        out.add(
            HistorySheetEntry(
                title = title.ifBlank { url },
                url = url,
                steps = i - currentIndex
            )
        )
        i += direction
    }
    return out
}
