package net.notscape.browser.browser

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import net.notscape.browser.Trace
import net.notscape.browser.data.TabSnapshot

/**
 * The tab-strip state holder (Phase D.0 extraction from
 * BrowserScreen): owns the tab list and the pure list operations on
 * it — privacy-class neighbour selection for tab closes — so
 * BrowserScreen keeps only the WebView and session side effects.
 * The Everyday Completeness tab tools (search, duplicate, close
 * others, reopen closed) build on this holder.
 */
class TabList {

    val tabs: SnapshotStateList<TabState> = mutableStateListOf()

    fun add(tab: TabState) {
        // Diagnostic trace (beta.55).
        Trace.event("tab opened: ${tab.id}")
        tabs.add(tab)
    }

    fun remove(tab: TabState) {
        tabs.remove(tab)
    }

    fun byId(id: Int): TabState? = tabs.firstOrNull { it.id == id }

    /** Tabs in the same privacy class as [tab], in strip order. */
    fun tabsInClass(tab: TabState): List<TabState> =
        tabs.filter { it.isPrivate == tab.isPrivate }

    fun classCount(isPrivate: Boolean): Int =
        tabs.count { it.isPrivate == isPrivate }

    /**
     * Recently closed tabs (Everyday Completeness): snapshots of
     * NORMAL tabs at close time, newest first, capped at 20. Private
     * tabs are never recorded — that is the point of them. In-memory
     * only; the list dies with the process.
     */
    private val _closedSnapshots = mutableStateListOf<TabSnapshot>()
    val closedSnapshots: List<TabSnapshot> get() = _closedSnapshots

    fun recordClosed(tab: TabState) {
        if (tab.isPrivate) return
        val url = tab.url.trim()
        if (url.isBlank() || url == "about:blank") return
        _closedSnapshots.removeAll { it.url == url }
        _closedSnapshots.add(0, TabSnapshot(url, tab.title))
        while (_closedSnapshots.size > 20) {
            _closedSnapshots.removeAt(_closedSnapshots.lastIndex)
        }
    }

    /** Audit P-M6: restore persisted closed snapshots
     *  (from the store) at startup. */
    fun restoreClosed(snaps: List<TabSnapshot>) {
        _closedSnapshots.clear()
        _closedSnapshots.addAll(snaps.take(20))
    }

    /** Audit P-M7: move [tab] to [toIndex] within its
     *  privacy class, keeping the class's relative
     *  order otherwise. Session save persists it. */
    fun moveWithinClass(tab: TabState, toIndex: Int) {
        val sameClass = tabsInClass(tab)
        val from = sameClass.indexOf(tab)
        if (from < 0) return
        val clamped = toIndex.coerceIn(0, sameClass.size - 1)
        if (clamped == from) return
        val reordered = sameClass.toMutableList().apply {
            removeAt(from)
            add(clamped, tab)
        }
        val merged = mutableListOf<TabState>()
        var gi = 0
        for (t in tabs) {
            merged.add(if (t.isPrivate == tab.isPrivate) reordered[gi++] else t)
        }
        tabs.clear()
        tabs.addAll(merged)
    }

    fun removeClosed(snapshot: TabSnapshot) {
        _closedSnapshots.remove(snapshot)
    }

    fun clearClosed() {
        _closedSnapshots.clear()
    }

    /**
     * The tab that should take focus when [tab] closes: its previous
     * neighbour in the same privacy class, or the next one when it was
     * the class's first tab. Null when the class has no other tab.
     */
    fun neighbourAfterClose(tab: TabState): TabState? {
        val sameClass = tabsInClass(tab)
        val idx = sameClass.indexOfFirst { it.id == tab.id }
        if (idx < 0) return null
        return sameClass.getOrNull(idx - 1) ?: sameClass.getOrNull(idx + 1)
    }
}

// Intent extra marking a launch as app mode (0.0.99997):
// a pinned shortcut reopens its page chromeless.
const val APP_MODE_EXTRA = "notscape_app_mode"

class TabState(
    val id: Int,
    initialUrl: String = "",
    val isPrivate: Boolean = false,
    /** A tab created to host a real popup window (window.open): it is
     *  driven by the opener page, so it must never render the start page
     *  while its URL is still blank. */
    val isPopupWin: Boolean = false,
    /** An app-mode tab (0.0.99997): opened from a pinned
     * home-screen shortcut, it shows no browser chrome -
     * just the page and a small exit button. Never saved
     * into the session. */
    val isAppMode: Boolean = false
) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf("")
    /** The site's own icon, captured from the WebView (0.0.955);
     *  shown on the tab card. Never persisted, never screenshotted. */
    var favicon by mutableStateOf<Bitmap?>(null)
    var desktopMode by mutableStateOf(false)
    /** Beta.66 #3: the saved .mht file this tab is showing
     *  (null for ordinary tabs). The tab's URL stays the
     *  original address, so the bar and history read
     *  normally; the WebView loads the file instead. */
    var offlinePath by mutableStateOf<String?>(null)
    var errorText by mutableStateOf<String?>(null)
    var sslErrorText by mutableStateOf<String?>(null)
    var sslErrorUrl by mutableStateOf("")
    /** Probed after each load: the page has a password field. */
    var hasLoginForm by mutableStateOf(false)
    /** The page's own background colour (ARGB), sampled after each
     *  load. The WebView and its container are painted with it so tab
     *  switches and repaints sit on a matching surface instead of
     *  flashing the WebView's default white (Phase G paint work).
     *  Null until a page reports one. Plain state: it feeds view
     *  properties at attach time, never composition. */
    var pageBg: Int? = null
    /** The original http:// URL of an in-flight HTTPS-first upgrade. */
    var pendingHttpOriginal: String? = null
    /** Hosts whose HTTPS upgrade failed in this tab: load them plain. */
    val httpOnlyHosts = mutableSetOf<String>()
    /** Exact (url, error type) pairs the user chose
     * to proceed past (audit S-m1): scoped to the
     * failing URL, never the whole host, so a
     * different page or error re-prompts. */
    val sslProceedKeys = mutableSetOf<String>()
    /** The failing URL's error type for the current
     * certificate error, so Proceed can scope its
     * key (S-m1). */
    var sslErrorPrimary: Int = -1
    /** Hosts this tab has loaded over HTTPS (audit
     * S-m2): they never fall back to plain HTTP. */
    val httpsSeenHosts = mutableSetOf<String>()
    val isStartPage: Boolean get() = !isPopupWin && (url.isBlank() || url == "about:blank")
}
