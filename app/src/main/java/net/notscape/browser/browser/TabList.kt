package net.notscape.browser.browser

import androidx.compose.runtime.mutableStateListOf
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
