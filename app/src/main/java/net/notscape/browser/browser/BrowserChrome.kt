package net.notscape.browser.browser

import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import net.notscape.browser.Trace
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import net.notscape.browser.MainActivity
import net.notscape.browser.data.AppStore
import net.notscape.browser.ui.SubScreen
import net.notscape.browser.ui.Frost
import net.notscape.browser.ui.FrostSheet
import net.notscape.browser.ui.GroupedCard
import net.notscape.browser.ui.Screen
import net.notscape.browser.ui.frostBar
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material.icons.filled.ContentCopy

/**
 * Browser chrome extracted from BrowserScreen (0.0.999,
 * zero behaviour change): the find-in-page pills, the
 * local suggestions card, and the browser menu sheet. The
 * state stays in BrowserScreen; these composables receive
 * values and callbacks, so what renders - and what every
 * tap does - is exactly what it was inline.
 */

/** Find in page: the typing pill hugs the address bar
 * (above it when the bar rides at the bottom, below it at
 * the top); the match count and steppers live in a small
 * pill at the top-right. */
@Composable
internal fun BoxScope.FindBar(
    barAtBottom: Boolean,
    immersive: Boolean,
    findText: String,
    findCount: Int,
    findOrdinal: Int,
    onFindTextChange: (String) -> Unit,
    onFindNext: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val findFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { findFocus.requestFocus() }
    }
    Row(
        modifier = Modifier
            .align(
                if (barAtBottom) Alignment.BottomCenter
                else Alignment.TopCenter
            )
            .then(
                if (barAtBottom) {
                    Modifier.windowInsetsPadding(
                        WindowInsets.navigationBars
                    ).imePadding()
                } else if (!immersive) {
                    Modifier.windowInsetsPadding(
                        WindowInsets.statusBars
                    )
                } else {
                    Modifier
                }
            )
            .padding(
                start = 12.dp, end = 12.dp,
                bottom = if (barAtBottom) 76.dp else 0.dp,
                top = if (barAtBottom) 0.dp else 76.dp
            )
            .fillMaxWidth()
            .widthIn(max = 430.dp)
            .frostBar(CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme
                .onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
                .size(19.dp)
        )
        BasicTextField(
            value = findText,
            onValueChange = onFindTextChange,
            singleLine = true,
            textStyle = MaterialTheme.typography
                .bodyLarge.copy(
                    color = MaterialTheme.colorScheme
                        .onSurface
                ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onFindNext(true)
                }
            ),
            modifier = Modifier.weight(1f)
                .padding(horizontal = 10.dp)
                .focusRequester(findFocus)
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close find",
                modifier = Modifier.size(19.dp)
            )
        }
    }
    Row(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .then(
                if (!immersive) {
                    Modifier.windowInsetsPadding(
                        WindowInsets.statusBars
                    )
                } else {
                    Modifier
                }
            )
            .padding(top = 10.dp, end = 12.dp)
            .frostBar(CircleShape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (findCount > 0) {
                "${findOrdinal + 1} / $findCount"
            } else {
                "0 / 0"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        IconButton(
            onClick = {
                onFindNext(false)
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "Previous match",
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(
            onClick = {
                onFindNext(true)
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Next match",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Local suggestions while typing in the address bar:
 * matches from this device's own bookmarks and history
 * only. Nothing is sent anywhere. */
@Composable
internal fun BoxScope.SuggestionsCard(
    suggestions: List<Triple<String, String, Boolean>>,
    barAtBottom: Boolean,
    immersive: Boolean,
    suggestionQuery: String,
    onPick: (String) -> Unit
) {
    key(suggestionQuery) {
        Card(
            modifier = Modifier
                .align(if (barAtBottom) Alignment.BottomCenter else Alignment.TopCenter)
                .then(
                    if (barAtBottom) Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                        .imePadding()
                    else if (!immersive) Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    else Modifier
                )
                .padding(
                    start = 12.dp, end = 12.dp,
                    bottom = if (barAtBottom) 84.dp else 0.dp,
                    top = if (barAtBottom) 0.dp else 84.dp
                )
                .fillMaxWidth(),
            shape = Frost.CardShape,
            colors = CardDefaults.cardColors(
                containerColor = Frost.cardColor()
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                suggestions.forEach { (title, url, isBookmark) ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                onPick(url)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isBookmark) Icons.Filled.Star else Icons.Filled.Refresh,
                            contentDescription = if (isBookmark) "Bookmarked" else "Reload",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                url, maxLines = 1, overflow = TextOverflow.Ellipsis,
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

/** Browser menu customization: which menu rows show.
 * The hidden set is stored as one comma-separated
 * setting. Before the user ever opens the editor, the
 * two rows that moved to their own pages are hidden
 * (per-site Site blocking lives on the Per-site
 * controls page, global Ad blocking on Settings'
 * Privacy & blocking page) and everything else shows.
 * parseHidden is pure so the unit suite can pin it. */
internal object BrowserMenuRows {
    const val HIDDEN_KEY = "menuHiddenRows"
    private const val UNSET = "DEFAULT"
    const val SITE_BLOCKING = "siteBlocking"
    const val GLOBAL_ADS = "globalAds"
    val ALL: List<Pair<String, String>> = listOf(
        "newTab" to "New tab",
        "newPrivateTab" to "New private tab",
        "reload" to "Reload",
        "sharePage" to "Share page",
        "copyPageUrl" to "Copy page URL",
        "openOtherBrowser" to "Open in other browser",
        "bookmark" to "Bookmark this page",
        "favorites" to "Add to favorites",
        "readArticle" to "Read this article",
        "media" to "Media on this page",
        "findInPage" to "Find in page",
        "blockElement" to "Block an element",
        "savePdf" to "Save as PDF",
        "saveOffline" to "Save page offline",
        "scanQr" to "Scan QR code",
        "screenshot" to "Screenshot",
        "addToHome" to "Add to home screen",
        "desktopSite" to "Desktop site",
        "siteBlocking" to "Site blocking",
        "globalAds" to "Ad blocking for all sites",
        "bookmarks" to "Bookmarks",
        "favoritesManager" to "Favorites",
        "readingList" to "Reading list",
        "readLater" to "Read later",
        "history" to "History",
        "playlists" to "Playlists",
        "offlineLibrary" to "Offline library",
        "downloads" to "Downloads",
        "bookshelf" to "Bookshelf",
        "games" to "Games",
        "vaultFill" to "Fill password from vault",
        "passwordVault" to "Password vault",
        "settings" to "Settings"
    )
    val DEFAULT_HIDDEN: Set<String> = setOf(SITE_BLOCKING, GLOBAL_ADS)
    /** The menu's sections. A row's section is fixed (see
     * sectionOf); the user's custom order moves rows around,
     * and the rendered menu groups consecutive rows of one
     * section under its label — so a moved row carries its
     * section with it. */
    enum class MenuSection(@androidx.annotation.StringRes val labelRes: Int) {
        TABS(net.notscape.browser.R.string.menu_section_tabs),
        PAGE(net.notscape.browser.R.string.menu_section_page),
        LIBRARY(net.notscape.browser.R.string.menu_section_library),
        SETTINGS(net.notscape.browser.R.string.menu_section_settings)
    }
    fun sectionOf(id: String): MenuSection = when (id) {
        "newTab", "newPrivateTab" -> MenuSection.TABS
        "bookmarks", "favoritesManager", "readingList", "history",
        "playlists", "offlineLibrary", "downloads", "bookshelf",
        "games", "vaultFill", "passwordVault" -> MenuSection.LIBRARY
        "settings" -> MenuSection.SETTINGS
        else -> MenuSection.PAGE
    }
    /** The default row order: the order the single menu
     * rendered in before customization existed (tabs, page
     * actions, library, settings). Note readLater sits with
     * the page actions here, not next to readingList as in
     * ALL — ALL is the editor's listing, this is the menu.
     * Every id in ALL appears exactly once here. */
    val DEFAULT_ORDER: List<String> = listOf(
        "newTab", "newPrivateTab",
        "reload", "sharePage", "copyPageUrl", "openOtherBrowser",
        "bookmark", "favorites", "readLater", "readArticle",
        "media", "findInPage", "blockElement", "savePdf",
        "saveOffline", "scanQr", "screenshot", "addToHome",
        "desktopSite", "siteBlocking", "globalAds",
        "bookmarks", "favoritesManager", "readingList", "history",
        "playlists", "offlineLibrary", "downloads", "bookshelf",
        "games", "vaultFill", "passwordVault",
        "settings"
    )
    /** The user's custom row order, stored as one
     * comma-separated setting. Unset means the default
     * order. Unknown ids are dropped; any row missing from
     * a stored order (new rows, old stores) appends in
     * default-order position, so an order can never lose a
     * row. Pure so the unit suite can pin it. */
    const val ORDER_KEY = "menuRowOrder"
    fun parseOrder(raw: String): List<String> {
        val known = ALL.map { it.first }.toSet()
        val base = if (raw == UNSET) DEFAULT_ORDER
            else raw.split(",").map { it.trim() }
                .filter { it.isNotEmpty() }.distinct()
        val kept = base.filter { it in known }
        return kept + DEFAULT_ORDER.filter { it !in kept.toSet() }
    }
    fun orderedIds(store: AppStore): List<String> =
        parseOrder(store.setting(ORDER_KEY, UNSET))
    fun setOrder(store: AppStore, ids: List<String>) {
        store.putSetting(ORDER_KEY, ids.joinToString(","))
    }
    /** Move one row by [delta] places in the custom order
     * (delta -1 = up, +1 = down); clamps at the ends. Pure
     * so the unit suite can pin it. */
    fun moved(order: List<String>, id: String, delta: Int): List<String> {
        val list = order.toMutableList()
        val from = list.indexOf(id)
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return list
        list.removeAt(from)
        list.add(to, id)
        return list
    }
    fun move(store: AppStore, id: String, delta: Int) {
        setOrder(store, moved(orderedIds(store), id, delta))
    }
    /** Rows that persist something (a bookmark, a favourite, a
     * Pictures file, a launcher shortcut) and therefore never
     * render for a private tab: a private tab saves nothing. */
    val PRIVATE_HIDDEN: Set<String> =
        setOf("bookmark", "favorites", "readLater", "openOtherBrowser", "saveOffline", "screenshot", "addToHome")
    /** One authority for the private-tab menu: a row shows for a
     * private tab only when it is not a persisting row. */
    fun visibleForTab(id: String, isPrivate: Boolean): Boolean =
        !isPrivate || id !in PRIVATE_HIDDEN
    fun parseHidden(raw: String): Set<String> =
        if (raw == UNSET) DEFAULT_HIDDEN
        else raw.split(",").map { it.trim() }
            .filter { it.isNotEmpty() }.toSet()
    fun hiddenIds(store: AppStore): Set<String> =
        parseHidden(store.setting(HIDDEN_KEY, UNSET))
    fun isVisible(store: AppStore, id: String): Boolean =
        id !in hiddenIds(store)
    fun setVisible(store: AppStore, id: String, visible: Boolean) {
        val hidden = hiddenIds(store).toMutableSet()
        if (visible) hidden.remove(id) else hidden.add(id)
        store.putSetting(HIDDEN_KEY, hidden.joinToString(","))
    }
    fun reset(store: AppStore) {
        store.putSetting(
            HIDDEN_KEY, DEFAULT_HIDDEN.joinToString(",")
        )
        store.putSetting(ORDER_KEY, UNSET)
    }
}

/** One browser-menu row that honours the user's menu
 * customization: a hidden row simply does not render. */
@Composable
internal fun MenuRowVisible(
    store: AppStore,
    id: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrivate: Boolean = false,
    onClick: () -> Unit
) {
    // Audit F-p1: the private-tab rule lives here, not at the call
    // sites — a persisting row (bookmark, favourite, screenshot,
    // home shortcut) never renders for a private tab.
    if (BrowserMenuRows.isVisible(store, id) &&
        BrowserMenuRows.visibleForTab(id, isPrivate)
    ) {
        // Diagnostic trace (beta.55): the shared menu-row seam —
        // every menu row logs here, so no call site needs its
        // own trace line.
        MenuRow(label, icon) {
            Trace.event("menu: $label")
            onClick()
        }
    }
}

/** The "Customize menu" editor: every menu row, in the menu's
 * current order, each with a show/hide switch and move
 * up/down buttons — add (switch a hidden row back on),
 * remove (switch it off), move (reorder). The editor itself
 * is never hideable, so the menu always has a way back here.
 * Reset restores both the default visibility and the default
 * order. */
@Composable
internal fun BrowserMenuCustomizeSheet(
    store: AppStore,
    onDismiss: () -> Unit
) {
    FrostSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Customize menu",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            val order = BrowserMenuRows.orderedIds(store)
            val labels = BrowserMenuRows.ALL.toMap()
            GroupedCard {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    order.forEachIndexed { index, id ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(
                                    start = 20.dp, end = 8.dp,
                                    top = 4.dp, bottom = 4.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                labels[id] ?: id,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    BrowserMenuRows.move(store, id, -1)
                                },
                                enabled = index > 0
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "Move up"
                                )
                            }
                            IconButton(
                                onClick = {
                                    BrowserMenuRows.move(store, id, 1)
                                },
                                enabled = index < order.lastIndex
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Move down"
                                )
                            }
                            Switch(
                                checked = BrowserMenuRows.isVisible(store, id),
                                onCheckedChange = { checked ->
                                    BrowserMenuRows.setVisible(
                                        store, id, checked
                                    )
                                }
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { BrowserMenuRows.reset(store) }) {
                    Text("Reset to defaults")
                }
            }
        }
    }
}

/** One browser-menu row as a slot: its id, whether it shows
 * for this tab and store, and the row itself. The menu
 * renders slots in the user's customized order (see
 * BrowserMenuRows.orderedIds), grouping consecutive rows of
 * one section under a label — so a moved row carries its
 * section with it instead of breaking the grouping. The
 * section comes from BrowserMenuRows.sectionOf. */
private class MenuSlot(
    val id: String,
    val shows: Boolean,
    val content: @Composable () -> Unit
)

/** The single browser menu (Option B): every menu row, on a
 * page that scrolls its full length, grouped under section
 * labels and rendered in the user's customized order. Rows
 * honour the user's menu customization (see BrowserMenuRows);
 * the two blocking toggles moved to their own pages and are
 * hidden here unless pinned back. The content is a plain
 * scrollable Column with navigation-bar inset padding — never
 * a bottom sheet — so the last row ("Customize menu") is
 * genuinely reachable. Back returns to the browser. */
@Composable
internal fun BrowserMenuRowsContent(
    store: AppStore,
    tab: TabState,
    /** Audit P-m15: this tab's blocked count for the header. */
    blockedForTab: Long = 0,
    readabilitySrc: String?,
    webView: () -> WebView?,
    onDismiss: () -> Unit,
    onNewTab: (String, Boolean) -> Unit,
    onShare: () -> Unit,
    onReadArticle: (String, Boolean) -> Unit,
    onMediaScan: () -> Unit,
    onFind: () -> Unit,
    onToggleDesktop: () -> Unit,
    onOpenScreen: (Screen) -> Unit,
    onVaultFill: () -> Unit,
    onPickElement: () -> Unit,
    pickMode: Boolean,
    onSaveOffline: () -> Unit = {},
    onScanQr: () -> Unit = {},
    onCustomize: () -> Unit
) {
    val context = LocalContext.current
    val pageHost = Uri.parse(tab.url).host ?: ""
    val siteBlockingOff = pageHost.isNotBlank() && store.siteSetting(pageHost).disableBlocking
    val globalAdsOn = store.setting("blockAds", "true") == "true"
    val bookmarkedNow = store.isBookmarked(tab.url)
    val hasPage = tab.url.isNotBlank()
    // MenuRowVisible still decides rendering (customization
    // + private-tab rule); the slot's `shows` flag mirrors
    // that decision plus the row's own condition, so the
    // grouping pass never renders an empty section card.
    fun shows(id: String, extra: Boolean = true): Boolean =
        extra && BrowserMenuRows.isVisible(store, id) &&
            BrowserMenuRows.visibleForTab(id, tab.isPrivate)
    val slots = listOf(
        MenuSlot("newTab", shows("newTab")) {
            MenuRowVisible(store, "newTab", "New tab", Icons.Filled.Add) {
                onDismiss(); onNewTab("", false)
            }
        },
        MenuSlot("newPrivateTab", shows("newPrivateTab")) {
            MenuRowVisible(store, "newPrivateTab", "New private tab", Icons.Filled.Lock) {
                onDismiss(); onNewTab("", true)
            }
        },
        MenuSlot("reload", shows("reload", hasPage)) {
            MenuRowVisible(store, "reload", "Reload", Icons.Filled.Refresh) {
                onDismiss(); webView()?.reload()
            }
        },
        MenuSlot("sharePage", shows("sharePage", hasPage)) {
            MenuRowVisible(store, "sharePage", "Share page", Icons.Filled.Share) {
                onDismiss(); onShare()
            }
        },
        MenuSlot("copyPageUrl", shows("copyPageUrl", hasPage)) {
            MenuRowVisible(store, "copyPageUrl", "Copy page URL", Icons.Filled.ContentCopy) {
                onDismiss()
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("link", tab.url))
                Toast.makeText(context, "Page URL copied", Toast.LENGTH_SHORT).show()
            }
        },
        MenuSlot("openOtherBrowser", shows("openOtherBrowser", hasPage)) {
            MenuRowVisible(store, "openOtherBrowser", "Open in other browser", Icons.Filled.OpenInBrowser, isPrivate = tab.isPrivate) {
                onDismiss()
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tab.url))
                    context.startActivity(Intent.createChooser(intent, "Open with"))
                }
            }
        },
        MenuSlot("bookmark", shows("bookmark", hasPage)) {
            MenuRowVisible(store, "bookmark",
                if (bookmarkedNow) "Remove bookmark" else "Bookmark this page",
                if (bookmarkedNow) Icons.Filled.Star else Icons.Outlined.StarBorder,
                isPrivate = tab.isPrivate
            ) {
                onDismiss()
                if (tab.url.isNotBlank()) store.toggleBookmark(tab.title, tab.url)
            }
        },
        MenuSlot("favorites", shows("favorites", hasPage)) {
            MenuRowVisible(store, "favorites",
                if (store.isFavourite(tab.url)) "Remove from favorites" else "Add to favorites",
                if (store.isFavourite(tab.url)) Icons.Filled.Star else Icons.Outlined.StarBorder,
                isPrivate = tab.isPrivate
            ) {
                onDismiss()
                store.toggleFavourite(tab.title, tab.url)
            }
        },
        MenuSlot("readLater", shows("readLater", hasPage)) {
            MenuRowVisible(store, "readLater", "Read later", Icons.Filled.MenuBook, isPrivate = tab.isPrivate) {
                onDismiss()
                if (!store.isReadingItem(tab.url)) {
                    store.addReadingItem(tab.title, tab.url)
                    Toast.makeText(context, "Saved to reading list", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Already in reading list", Toast.LENGTH_SHORT).show()
                }
            }
        },
        MenuSlot("readArticle", shows("readArticle", hasPage)) {
            MenuRowVisible(store, "readArticle", "Read this article", Icons.Filled.Public) {
                onDismiss()
                val wv = webView()
                val src = readabilitySrc
                if (wv != null && src != null && tab.url.isNotBlank()) {
                    // Extract from the LIVE page with Mozilla
                    // Readability (clone + gate), then hand the
                    // article to the reader screen via the cache.
                    val targetUrl = tab.url
                    wv.evaluateJavascript(src + ArticleExtractor.GLUE_JS) { raw ->
                        val article = ArticleExtractor.parseResult(raw, targetUrl)
                        if (article != null) {
                            ArticleCache.current = article
                            onReadArticle(targetUrl, tab.isPrivate)
                        } else {
                            Toast.makeText(
                                context,
                                "Reader isn't available for this page",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else if (tab.url.isNotBlank()) {
                    onReadArticle(tab.url, tab.isPrivate)
                }
            }
        },
        MenuSlot("media", shows("media", hasPage)) {
            MenuRowVisible(store, "media", "Media on this page", Icons.Filled.PlayArrow) {
                onDismiss(); onMediaScan()
            }
        },
        MenuSlot("findInPage", shows("findInPage", hasPage)) {
            MenuRowVisible(store, "findInPage", "Find in page", Icons.Filled.Search) {
                onDismiss(); onFind()
            }
        },
        MenuSlot("blockElement", shows("blockElement", hasPage)) {
            MenuRowVisible(store, "blockElement",
                if (pickMode) {
                    "Stop picking"
                } else {
                    "Block an element"
                },
                Icons.Filled.Block
            ) {
                onDismiss(); onPickElement()
            }
        },
        MenuSlot("savePdf", shows("savePdf", hasPage)) {
            MenuRowVisible(store, "savePdf", "Save as PDF", Icons.Filled.PictureAsPdf) {
                onDismiss()
                val wv = webView()
                val act = context as? android.app.Activity
                if (wv != null && act != null) {
                    PageExtras.saveAsPdf(act, wv, tab.title)
                }
            }
        },
        MenuSlot("saveOffline", shows("saveOffline", hasPage)) {
            MenuRowVisible(store, "saveOffline", "Save page offline", Icons.Filled.Download, isPrivate = tab.isPrivate) {
                onDismiss()
                onSaveOffline()
            }
        },
        MenuSlot("scanQr", shows("scanQr", hasPage)) {
            MenuRowVisible(store, "scanQr", "Scan QR code", Icons.Filled.QrCodeScanner, isPrivate = tab.isPrivate) {
                onDismiss()
                onScanQr()
            }
        },
        MenuSlot("screenshot", shows("screenshot", hasPage)) {
            MenuRowVisible(store, "screenshot", "Screenshot", Icons.Filled.Screenshot, isPrivate = tab.isPrivate) {
                onDismiss()
                val wv = webView()
                if (wv != null) {
                    val ok = PageExtras.saveScreenshot(context, wv)
                    Toast.makeText(
                        context,
                        if (ok) {
                            "Screenshot saved to Pictures"
                        } else {
                            "Couldn't take that screenshot"
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        },
        MenuSlot("addToHome", shows("addToHome", hasPage)) {
            MenuRowVisible(store, "addToHome", "Add to home screen", Icons.Filled.AddToHomeScreen, isPrivate = tab.isPrivate) {
                onDismiss()
                // A pinned shortcut that reopens
                // this page in app mode
                // (0.0.99997): the launcher gets
                // the page's title and favicon.
                val host = Uri.parse(tab.url).host ?: ""
                val label = tab.title
                    .ifBlank { host }.take(40)
                val intent = Intent(
                    context, MainActivity::class.java
                ).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse(tab.url)
                    putExtra(APP_MODE_EXTRA, true)
                }
                val icon = tab.favicon?.let {
                    IconCompat.createWithBitmap(it)
                } ?: IconCompat.createWithResource(
                    context,
                    net.notscape.browser.R.mipmap
                        .ic_launcher
                )
                val info = ShortcutInfoCompat
                    .Builder(
                        context,
                        "site-" + tab.url.hashCode()
                    )
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(icon)
                    .setIntent(intent)
                    .build()
                val ok = runCatching {
                    ShortcutManagerCompat
                        .requestPinShortcut(
                            context, info, null
                        )
                }.getOrDefault(false)
                Toast.makeText(
                    context,
                    if (ok) {
                        "Shortcut requested - your " +
                            "launcher may ask you " +
                            "to confirm"
                    } else {
                        "Your launcher doesn't " +
                            "support pinned shortcuts"
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
        },
        MenuSlot("desktopSite", shows("desktopSite", hasPage)) {
            MenuRowVisible(store, "desktopSite", if (tab.desktopMode) "Mobile site" else "Desktop site", Icons.Filled.OpenInNew) {
                onDismiss()
                onToggleDesktop()
            }
        },
        MenuSlot("siteBlocking", shows("siteBlocking", pageHost.isNotBlank())) {
            MenuRowVisible(store, "siteBlocking",
                if (siteBlockingOff) "Site blocking: off" else "Site blocking: on",
                Icons.Filled.Close
            ) {
                onDismiss()
                store.putSiteSetting(
                    store.siteSetting(pageHost).copy(disableBlocking = !siteBlockingOff)
                )
                Toast.makeText(
                    context,
                    if (siteBlockingOff) "Blocking on for $pageHost" else "Blocking off for $pageHost — reload the page",
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        MenuSlot("globalAds", shows("globalAds")) {
            MenuRowVisible(store, "globalAds",
                if (globalAdsOn) "Ad blocking for all sites: on" else "Ad blocking for all sites: off",
                Icons.Filled.Block
            ) {
                onDismiss()
                store.putSetting("blockAds", (!globalAdsOn).toString())
                webView()?.reload()
                Toast.makeText(
                    context,
                    if (globalAdsOn) "Ad blocking off for all sites" else "Ad blocking on for all sites",
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        MenuSlot("bookmarks", shows("bookmarks")) {
            MenuRowVisible(store, "bookmarks", "Bookmarks", Icons.Filled.Star) { onDismiss(); onOpenScreen(Screen.Bookmarks) }
        },
        MenuSlot("favoritesManager", shows("favoritesManager")) {
            MenuRowVisible(store, "favoritesManager", "Favorites", Icons.Filled.Star) { onDismiss(); onOpenScreen(Screen.Favorites) }
        },
        MenuSlot("readingList", shows("readingList")) {
            MenuRowVisible(store, "readingList", "Reading list", Icons.Filled.MenuBook) { onDismiss(); onOpenScreen(Screen.ReadingList) }
        },
        MenuSlot("history", shows("history")) {
            MenuRowVisible(store, "history", "History", Icons.Filled.History) { onDismiss(); onOpenScreen(Screen.History) }
        },
        MenuSlot("playlists", shows("playlists")) {
            MenuRowVisible(store, "playlists", "Playlists", Icons.Filled.PlaylistAdd) { onDismiss(); onOpenScreen(Screen.Playlists) }
        },
        MenuSlot("offlineLibrary", shows("offlineLibrary")) {
            MenuRowVisible(store, "offlineLibrary", "Offline library", Icons.Filled.VideoLibrary) { onDismiss(); onOpenScreen(Screen.Library) }
        },
        MenuSlot("downloads", shows("downloads")) {
            MenuRowVisible(store, "downloads", "Downloads", Icons.Filled.Download) { onDismiss(); onOpenScreen(Screen.Downloads) }
        },
        MenuSlot("bookshelf", shows("bookshelf")) {
            MenuRowVisible(store, "bookshelf", "Bookshelf", Icons.Filled.MenuBook) { onDismiss(); onOpenScreen(Screen.Bookshelf) }
        },
        MenuSlot("games", shows("games")) {
            MenuRowVisible(store, "games", "Games", Icons.Filled.SportsEsports) { onDismiss(); onOpenScreen(Screen.Games) }
        },
        MenuSlot("vaultFill", shows("vaultFill", tab.hasLoginForm && store.vault.isNotEmpty())) {
            MenuRowVisible(store, "vaultFill", "Fill password from vault", Icons.Filled.Lock) {
                onDismiss(); onVaultFill()
            }
        },
        MenuSlot("passwordVault", shows("passwordVault")) {
            MenuRowVisible(store, "passwordVault", "Password vault", Icons.Filled.Lock) { onDismiss(); onOpenScreen(Screen.Vault) }
        },
        MenuSlot("settings", shows("settings")) {
            MenuRowVisible(store, "settings", "Settings", Icons.Filled.Settings) { onDismiss(); onOpenScreen(Screen.Settings) }
        }
    )
    // Audit P-m15: the blocking engine's work on THIS page,
    // visible while browsing — only meaningful with a page
    // loaded, so the start page shows no header at all.
    if (hasPage) {
        Text(
            "Blocked on this page: $blockedForTab",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = 16.dp, end = 16.dp, bottom = 4.dp
            )
        )
    }
    // Render in the customized order, grouping consecutive
    // rows of one section into one labelled card.
    val byId = slots.associateBy { it.id }
    val inOrder = BrowserMenuRows.orderedIds(store)
        .mapNotNull { byId[it] }.filter { it.shows }
    var i = 0
    while (i < inOrder.size) {
        val section = BrowserMenuRows.sectionOf(inOrder[i].id)
        val group = inOrder.subList(i, inOrder.size)
            .takeWhile { BrowserMenuRows.sectionOf(it.id) == section }
        Text(
            stringResource(section.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
        )
        GroupedCard {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                group.forEach { it.content() }
            }
        }
        i += group.size
    }
    GroupedCard {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            MenuRow("Customize menu", Icons.Filled.Tune) {
                Trace.event("menu: Customize menu")
                onDismiss(); onCustomize()
            }
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

/** The single menu page (Option B): every menu row, on a
 * page that scrolls its full length. Back returns to the
 * browser. */
@Composable
internal fun BrowserMenuPage(
    store: AppStore,
    tab: TabState,
    /** Audit P-m15: this tab's blocked count for the header. */
    blockedForTab: Long = 0,
    readabilitySrc: String?,
    webView: () -> WebView?,
    onClose: () -> Unit,
    onNewTab: (String, Boolean) -> Unit,
    onShare: () -> Unit,
    onReadArticle: (String, Boolean) -> Unit,
    onMediaScan: () -> Unit,
    onFind: () -> Unit,
    onToggleDesktop: () -> Unit,
    onOpenScreen: (Screen) -> Unit,
    onVaultFill: () -> Unit,
    onPickElement: () -> Unit,
    pickMode: Boolean,
    onSaveOffline: () -> Unit = {},
    onScanQr: () -> Unit = {},
    onCustomize: () -> Unit
) {
    // The menu page renders inside BrowserScreen's root Box
    // over the still-composed start page: the opaque wrapper
    // keeps the header band from showing the page behind it
    // (the Menu-over-Shortcuts overlap). Other SubScreen
    // callers sit on MainActivity's opaque Surface and need
    // no wrapper.
    Box(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
    SubScreen(stringResource(net.notscape.browser.R.string.menu_page_title), onBack = onClose) {
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BrowserMenuRowsContent(
                store = store,
                tab = tab,
                blockedForTab = blockedForTab,
                readabilitySrc = readabilitySrc,
                webView = webView,
                onDismiss = onClose,
                onNewTab = onNewTab,
                onShare = onShare,
                onReadArticle = onReadArticle,
                onMediaScan = onMediaScan,
                onFind = onFind,
                onToggleDesktop = onToggleDesktop,
                onOpenScreen = onOpenScreen,
                onVaultFill = onVaultFill,
                onPickElement = onPickElement,
                pickMode = pickMode,
                onSaveOffline = onSaveOffline,
                onScanQr = onScanQr,
                onCustomize = onCustomize
            )
        }
    }
    }
}
