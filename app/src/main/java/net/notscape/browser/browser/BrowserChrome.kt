package net.notscape.browser.browser

import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import net.notscape.browser.MainActivity
import net.notscape.browser.data.AppStore
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
            contentDescription = null,
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
                            contentDescription = null,
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
        "bookmark" to "Bookmark this page",
        "favorites" to "Add to favorites",
        "readArticle" to "Read this article",
        "media" to "Media on this page",
        "findInPage" to "Find in page",
        "blockElement" to "Block an element",
        "savePdf" to "Save as PDF",
        "screenshot" to "Screenshot",
        "addToHome" to "Add to home screen",
        "desktopSite" to "Desktop site",
        "siteBlocking" to "Site blocking",
        "globalAds" to "Ad blocking for all sites",
        "bookmarks" to "Bookmarks",
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
    /** Rows that persist something (a bookmark, a favourite, a
     * Pictures file, a launcher shortcut) and therefore never
     * render for a private tab: a private tab saves nothing. */
    val PRIVATE_HIDDEN: Set<String> =
        setOf("bookmark", "favorites", "screenshot", "addToHome")
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
        MenuRow(label, icon, onClick)
    }
}

/** The "Customize menu" editor: every menu row with a
 * show/hide switch, plus a reset to the defaults. The
 * editor row itself is never hideable, so the menu
 * always has a way back here. */
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
            GroupedCard {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    BrowserMenuRows.ALL.forEach { (id, label) ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .padding(
                                    horizontal = 20.dp, vertical = 8.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
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

/** The browser menu sheet: tabs, the page actions, the
 * library shortcuts, and Settings. Rows honour the
 * user's menu customization (see BrowserMenuRows);
 * the two blocking toggles moved to their own pages
 * and are hidden here unless pinned back. */
@Composable
internal fun BrowserMenuSheet(
    store: AppStore,
    tab: TabState,
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
    onCustomize: () -> Unit
) {
    val context = LocalContext.current
    val pageHost = Uri.parse(tab.url).host ?: ""
    val siteBlockingOff = pageHost.isNotBlank() && store.siteSetting(pageHost).disableBlocking
    val globalAdsOn = store.setting("blockAds", "true") == "true"
    val bookmarkedNow = store.isBookmarked(tab.url)
    FrostSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GroupedCard {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    MenuRowVisible(store, "newTab", "New tab", Icons.Filled.Add) {
                        onDismiss(); onNewTab("", false)
                    }
                    MenuRowVisible(store, "newPrivateTab", "New private tab", Icons.Filled.Lock) {
                        onDismiss(); onNewTab("", true)
                    }
                }
            }
            GroupedCard {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    if (tab.url.isNotBlank()) {
                        MenuRowVisible(store, "reload", "Reload", Icons.Filled.Refresh) {
                            onDismiss(); webView()?.reload()
                        }
                        MenuRowVisible(store, "sharePage", "Share page", Icons.Filled.Share) {
                            onDismiss(); onShare()
                        }
                        MenuRowVisible(store, "copyPageUrl", "Copy page URL", Icons.Filled.ContentCopy) {
                            onDismiss()
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("link", tab.url))
                            Toast.makeText(context, "Page URL copied", Toast.LENGTH_SHORT).show()
                        }
                        MenuRowVisible(store, "bookmark", 
                            if (bookmarkedNow) "Remove bookmark" else "Bookmark this page",
                            if (bookmarkedNow) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            isPrivate = tab.isPrivate
                        ) {
                            onDismiss()
                            if (tab.url.isNotBlank()) store.toggleBookmark(tab.title, tab.url)
                        }
                        if (tab.url.isNotBlank()) {
                            MenuRowVisible(store, "favorites", 
                                if (store.isFavourite(tab.url)) "Remove from favorites" else "Add to favorites",
                                if (store.isFavourite(tab.url)) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                isPrivate = tab.isPrivate
                            ) {
                                onDismiss()
                                store.toggleFavourite(tab.title, tab.url)
                            }
                        }
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
                        MenuRowVisible(store, "media", "Media on this page", Icons.Filled.PlayArrow) {
                            onDismiss(); onMediaScan()
                        }
                        MenuRowVisible(store, "findInPage", "Find in page", Icons.Filled.Search) {
                            onDismiss(); onFind()
                        }
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
                        MenuRowVisible(store, "savePdf", "Save as PDF", Icons.Filled.PictureAsPdf) {
                            onDismiss()
                            val wv = webView()
                            val act = context as? android.app.Activity
                            if (wv != null && act != null) {
                                PageExtras.saveAsPdf(act, wv, tab.title)
                            }
                        }
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
                        MenuRowVisible(store, "desktopSite", if (tab.desktopMode) "Mobile site" else "Desktop site", Icons.Filled.OpenInNew) {
                            onDismiss()
                            onToggleDesktop()
                        }
                    }
                    if (pageHost.isNotBlank()) {
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
                    }
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

                }
            }
            GroupedCard {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    MenuRowVisible(store, "bookmarks", "Bookmarks", Icons.Filled.Star) { onDismiss(); onOpenScreen(Screen.Bookmarks) }
                    MenuRowVisible(store, "history", "History", Icons.Filled.History) { onDismiss(); onOpenScreen(Screen.History) }
                    MenuRowVisible(store, "playlists", "Playlists", Icons.Filled.PlaylistAdd) { onDismiss(); onOpenScreen(Screen.Playlists) }
                    MenuRowVisible(store, "offlineLibrary", "Offline library", Icons.Filled.VideoLibrary) { onDismiss(); onOpenScreen(Screen.Library) }
                    MenuRowVisible(store, "downloads", "Downloads", Icons.Filled.Download) { onDismiss(); onOpenScreen(Screen.Downloads) }
                    MenuRowVisible(store, "bookshelf", "Bookshelf", Icons.Filled.MenuBook) { onDismiss(); onOpenScreen(Screen.Bookshelf) }
                    MenuRowVisible(store, "games", "Games", Icons.Filled.SportsEsports) { onDismiss(); onOpenScreen(Screen.Games) }
                    if (tab.hasLoginForm && store.vault.isNotEmpty()) {
                        MenuRowVisible(store, "vaultFill", "Fill password from vault", Icons.Filled.Lock) {
                            onDismiss(); onVaultFill()
                        }
                    }
                    MenuRowVisible(store, "passwordVault", "Password vault", Icons.Filled.Lock) { onDismiss(); onOpenScreen(Screen.Vault) }
                }
            }
            GroupedCard {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    MenuRowVisible(store, "settings", "Settings", Icons.Filled.Settings) { onDismiss(); onOpenScreen(Screen.Settings) }
                }
            }
            GroupedCard {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    MenuRow("Customize menu", Icons.Filled.Tune) {
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
    }
}
