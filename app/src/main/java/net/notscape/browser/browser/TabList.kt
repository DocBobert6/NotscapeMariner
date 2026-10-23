package net.notscape.browser.browser

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import net.notscape.browser.data.TabSnapshot

/**
 * The tab-strip state holder (Phase D.0 extraction from
 * BrowserScreen): owns the tab list and the pure list operations on
 * it — grouping by privacy, neighbour selection for tab closes — so
 * BrowserScreen keeps only the WebView and session side effects.
 * The Everyday Completeness tab tools (search, duplicate, close
 * others, reopen closed) build on this holder.
 */
class TabList {

    val tabs: SnapshotStateList<TabState> = mutableStateListOf()

    fun add(tab: TabState) {
        tabs.add(tab)
    }

    fun remove(tab: TabState) {
        tabs.remove(tab)
    }

    fun byId(id: Int): TabState? = tabs.firstOrNull { it.id == id }

    /** Tabs in the same privacy group as [tab], in strip order. */
    fun groupOf(tab: TabState): List<TabState> =
        tabs.filter { it.isPrivate == tab.isPrivate }

    fun groupCount(isPrivate: Boolean): Int =
        tabs.count { it.isPrivate == isPrivate }

    /**
     * The tab that should take focus when [tab] closes: its previous
     * neighbour in the same group, or the next one when it was the
     * group's first tab. Null when the group has no other tab.
     */
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

    fun removeClosed(snapshot: TabSnapshot) {
        _closedSnapshots.remove(snapshot)
    }

    fun clearClosed() {
        _closedSnapshots.clear()
    }

    fun neighbourAfterClose(tab: TabState): TabState? {
        val group = groupOf(tab)
        val idx = group.indexOfFirst { it.id == tab.id }
        if (idx < 0) return null
        return group.getOrNull(idx - 1) ?: group.getOrNull(idx + 1)
    }
}

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
    /** The tab's group name (0.0.992); "" = ungrouped.
     *  Groups never cross the privacy split: the switcher
     *  only ever shows one privacy class at a time. */
    var group by mutableStateOf("")
    var title by mutableStateOf("")
    /** The site's own icon, captured from the WebView (0.0.955);
     *  shown on the tab card. Never persisted, never screenshotted. */
    var favicon by mutableStateOf<Bitmap?>(null)
    var desktopMode by mutableStateOf(false)
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
    /** Hosts whose certificate errors the user chose to proceed past. */
    val sslProceedHosts = mutableSetOf<String>()
    val isStartPage: Boolean get() = !isPopupWin && (url.isBlank() || url == "about:blank")
}
