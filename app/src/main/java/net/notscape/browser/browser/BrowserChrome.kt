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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import net.notscape.browser.data.AppStore
import net.notscape.browser.ui.Frost
import net.notscape.browser.ui.FrostSheet
import net.notscape.browser.ui.GroupedCard
import net.notscape.browser.ui.Screen
import net.notscape.browser.ui.frostBar

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

/** The browser menu sheet: tabs, the page actions, the
 * library shortcuts, and Settings. */
@Composable
internal fun BrowserMenuSheet(
    store: AppStore,
    tab: TabState,
    readabilitySrc: String?,
    webView: () -> WebView?,
    onDismiss: () -> Unit,
    onNewTab: (String, Boolean) -> Unit,
    onShare: () -> Unit,
    onReadArticle: (String) -> Unit,
    onMediaScan: () -> Unit,
    onFind: () -> Unit,
    onToggleDesktop: () -> Unit,
    onOpenScreen: (Screen) -> Unit,
    onVaultFill: () -> Unit
) {
    val context = LocalContext.current
    val pageHost = Uri.parse(tab.url).host ?: ""
    val siteBlockingOff = pageHost.isNotBlank() && store.siteSetting(pageHost).disableBlocking
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
                    MenuRow("New tab", Icons.Filled.Add) {
                        onDismiss(); onNewTab("", false)
                    }
                    MenuRow("New private tab", Icons.Filled.Lock) {
                        onDismiss(); onNewTab("", true)
                    }
                }
            }
            GroupedCard {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    if (tab.url.isNotBlank()) {
                        MenuRow("Reload", Icons.Filled.Refresh) {
                            onDismiss(); webView()?.reload()
                        }
                        MenuRow("Share page", Icons.Filled.Share) {
                            onDismiss(); onShare()
                        }
                        MenuRow(
                            if (bookmarkedNow) "Remove bookmark" else "Bookmark this page",
                            if (bookmarkedNow) Icons.Filled.Star else Icons.Outlined.StarBorder
                        ) {
                            onDismiss()
                            if (tab.url.isNotBlank()) store.toggleBookmark(tab.title, tab.url)
                        }
                        if (tab.url.isNotBlank()) {
                            MenuRow(
                                if (store.isFavourite(tab.url)) "Remove from favorites" else "Add to favorites",
                                Icons.Filled.Star
                            ) {
                                onDismiss()
                                store.toggleFavourite(tab.title, tab.url)
                            }
                        }
                        MenuRow("Read this article", Icons.Filled.Public) {
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
                                        onReadArticle(targetUrl)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Reader isn't available for this page",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else if (tab.url.isNotBlank()) {
                                onReadArticle(tab.url)
                            }
                        }
                        MenuRow("Find media on page", Icons.Filled.PlayArrow) {
                            onDismiss(); onMediaScan()
                        }
                        MenuRow("Find in page", Icons.Filled.Search) {
                            onDismiss(); onFind()
                        }
                        MenuRow("Save as PDF", Icons.Filled.PictureAsPdf) {
                            onDismiss()
                            val wv = webView()
                            val act = context as? android.app.Activity
                            if (wv != null && act != null) {
                                PageExtras.saveAsPdf(act, wv, tab.title)
                            }
                        }
                        MenuRow("Screenshot", Icons.Filled.Screenshot) {
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
                        MenuRow(if (tab.desktopMode) "Mobile site" else "Desktop site", Icons.Filled.OpenInNew) {
                            onDismiss()
                            onToggleDesktop()
                        }
                    }
                    if (pageHost.isNotBlank()) {
                        MenuRow(
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
                }
            }
            GroupedCard {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    MenuRow("Bookmarks", Icons.Filled.Star) { onDismiss(); onOpenScreen(Screen.Bookmarks) }
                    MenuRow("History", Icons.Filled.History) { onDismiss(); onOpenScreen(Screen.History) }
                    MenuRow("Playlists", Icons.Filled.PlaylistAdd) { onDismiss(); onOpenScreen(Screen.Playlists) }
                    MenuRow("Offline library", Icons.Filled.Download) { onDismiss(); onOpenScreen(Screen.Library) }
                    MenuRow("Bookshelf", Icons.Filled.MenuBook) { onDismiss(); onOpenScreen(Screen.Bookshelf) }
                    if (tab.hasLoginForm && store.vault.isNotEmpty()) {
                        MenuRow("Fill password from vault", Icons.Filled.Lock) {
                            onDismiss(); onVaultFill()
                        }
                    }
                    MenuRow("Password vault", Icons.Filled.Lock) { onDismiss(); onOpenScreen(Screen.Vault) }
                }
            }
            GroupedCard {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    MenuRow("Settings", Icons.Filled.Settings) { onDismiss(); onOpenScreen(Screen.Settings) }
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
