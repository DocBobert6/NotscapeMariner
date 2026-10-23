package net.notscape.browser.browser

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
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
import net.notscape.browser.ui.FrostSheet
import net.notscape.browser.R
import net.notscape.browser.ui.SmallCapsFontFamily
import net.notscape.browser.ui.WordmarkFontFamily
import net.notscape.browser.ui.GroupedCard
import net.notscape.browser.ui.PanelCard
import net.notscape.browser.ui.Screen
import net.notscape.browser.ui.frostBar

/**
 * The browser start page (favourites, continue-reading, continue
 * watching), extracted from BrowserScreen in Phase F.
 */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StartPage(
    store: AppStore,
    onOpen: (String) -> Unit,
    onOpenBook: (Long) -> Unit = {},
    onOpenUpdates: () -> Unit = {},
    contentTopPad: Dp = 0.dp,
    contentBottomPad: Dp = 0.dp
) {
    val context = LocalContext.current
    val wallpaper = store.setting("wallpaper", "materialyou")
    val rev = store.revision
    // The wallpaper dresses the PAGE background only: the banner is
    // a UI-kit card and never re-skins with it. Three choices since
    // 0.0.958 (Robert's direction - the stock gradient presets and
    // the custom photo are gone): Light and Dark are the app's own
    // theme backgrounds; Material You is the phone's dynamic
    // background for the app's current light/dark mode. Any older
    // stored value falls through to Material You.
    val themeMode = store.setting("themeMode", "system")
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val appDark = when (themeMode) {
        "dark", "materialyou-dark" -> true
        "light", "materialyou-light" -> false
        else -> systemDark
    }
    val wallpaperColor = when (wallpaper) {
        "light" -> Color(0xFFFFFFFF)
        "dark" -> Color(0xFF0A0A0A)
        else -> if (android.os.Build.VERSION.SDK_INT >= 31) {
            if (appDark) {
                androidx.compose.material3.dynamicDarkColorScheme(context).background
            } else {
                androidx.compose.material3.dynamicLightColorScheme(context).background
            }
        } else {
            MaterialTheme.colorScheme.background
        }
    }
    // Custom photo (0.0.963, Robert restored the option): copied
    // into app files by Settings and drawn over the themed colour,
    // which stays as the fallback while no photo exists. Keyed on
    // the file's mtime so replacing the photo redraws in place.
    val customWallpaperFile = remember {
        java.io.File(context.filesDir, "wallpaper/custom.jpg")
    }
    val wallpaperBitmap = remember(wallpaper, customWallpaperFile.lastModified()) {
        if (wallpaper == "custom") {
            runCatching {
                android.graphics.BitmapFactory.decodeFile(
                    customWallpaperFile.absolutePath
                )
            }.getOrNull()
        } else {
            null
        }
    }
    // Banner palette (0.0.965, Robert): when a custom photo is
    // the wallpaper, the banner card wears the photo's own
    // dominant colours instead of the kit's neutral card.
    var bannerSwatch by remember {
        mutableStateOf<androidx.palette.graphics.Palette.Swatch?>(null)
    }
    LaunchedEffect(wallpaperBitmap) {
        bannerSwatch = wallpaperBitmap?.let { bmp ->
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val small = android.graphics.Bitmap.createScaledBitmap(
                        bmp, 96,
                        (96f * bmp.height / bmp.width).toInt().coerceAtLeast(1),
                        false
                    )
                    val pal = androidx.palette.graphics.Palette.from(small).generate()
                    pal.dominantSwatch ?: pal.vibrantSwatch ?: pal.mutedSwatch
                }
            }.getOrNull()
        }
    }
    // Section titles over a photo get a soft shadow so they stay
    // readable on any part of the image (0.0.965).
    val headerStyle = MaterialTheme.typography.titleMedium.copy(
        shadow = if (wallpaperBitmap != null) {
            androidx.compose.ui.graphics.Shadow(
                Color.Black.copy(alpha = 0.55f),
                androidx.compose.ui.geometry.Offset(0f, 2f), 6f
            )
        } else {
            null
        }
    )
    var favMenuFor by remember { mutableStateOf<Long?>(null) }
    var continueMenuFor by remember { mutableStateOf<String?>(null) }
    var continueBookMenuFor by remember { mutableStateOf<Long?>(null) }

    // Continue watching/listening: saved playback positions with best-known titles.
    val continueItems = remember(rev) {
        store.playbackPositions.entries.map { (url, pos) ->
            val title = store.playbackTitles[url]?.takeIf { it.isNotBlank() }
                ?: store.downloads.firstOrNull { it.url == url }?.title
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
        // TRUE frequency (0.0.953): visits per host counted from
        // history, most-visited first, ties broken by recency;
        // each host is represented by its most recent visit.
        // (This used to be distinctBy + take — the six most
        // recent hosts wearing a "frequently" label they had
        // not earned.)
        store.history.groupBy { Uri.parse(it.url).host ?: it.url }
            .values.map { visits ->
                visits.maxByOrNull { it.visitedAt }!! to visits.size
            }
            .sortedWith(
                compareByDescending<Pair<net.notscape.browser.data.HistoryEntry, Int>> { it.second }
                    .thenByDescending { it.first.visitedAt }
            )
            .take(6)
            .map { it.first }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxSize().background(wallpaperColor))
        wallpaperBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // A light veil so cards and titles stay readable over
            // any photo.
            Box(
                Modifier.fillMaxSize().background(
                    MaterialTheme.colorScheme.background.copy(alpha = 0.22f)
                )
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = 16.dp + contentTopPad, bottom = 16.dp + contentBottomPad
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column {
                // The welcome banner is a UI-kit panel card now
                // (0.0.958, Robert's audit: the bespoke gradient
                // hero did not match the kit). Same layout and
                // lockup - the app mark, the wordmark in the
                // logo's own typeface - in the kit's card material
                // and ink colours, so it belongs with every other
                // card on the page. The wallpaper dresses the page
                // behind it, never the banner.
                PanelCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = bannerSwatch?.let { Color(it.rgb) }
                        ?: Color.Unspecified
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(R.drawable.banner_mark),
                            contentDescription = null,
                            modifier = Modifier.size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                "NOTSCAPE",
                                fontFamily = WordmarkFontFamily,
                                style = MaterialTheme.typography.headlineMedium,
                                color = bannerSwatch?.let { Color(it.titleTextColor) }
                                    ?: MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 1.sp
                            )
                            Text(
                                "MARINER",
                                fontFamily = SmallCapsFontFamily,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge,
                                color = bannerSwatch?.let { Color(it.bodyTextColor) }
                                    ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 6.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Local-only and private — everything stays on your device. " +
                                    "Type in the address bar to search or visit a site.",
                                fontFamily = SmallCapsFontFamily,
                                style = MaterialTheme.typography.bodyMedium,
                                color = bannerSwatch?.let { Color(it.bodyTextColor) }
                                    ?: MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // The update note lives directly below the
                // banner's bottom-right corner (0.0.959, Robert's
                // placement) and opens the updates page.
                if (UpdateChecker.availableVersion != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            "Update available — ${UpdateChecker.availableVersion}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onOpenUpdates() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                }
            }
            if (store.setting("showContinue", "true") == "true" && continueItems.isNotEmpty()) {
                item {
                    Text("Continue", style = headerStyle)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(continueItems) { (url, title, pos) ->
                            Box {
                            PanelCard(
                                modifier = Modifier.width(220.dp).combinedClickable(
                                    onClick = { PlayerActivity.launch(context, url, title) },
                                    onLongClick = { continueMenuFor = url }
                                )
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
                            DropdownMenu(
                                expanded = continueMenuFor == url,
                                onDismissRequest = { continueMenuFor = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Remove from Continue") },
                                    onClick = {
                                        store.clearPlaybackPosition(url)
                                        continueMenuFor = null
                                    }
                                )
                            }
                            }
                        }
                    }
                }
            }
            if (continueBooks.isNotEmpty()) {
                item {
                    Text("Continue reading", style = headerStyle)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        continueBooks.take(3).forEach { book ->
                            Box {
                            PanelCard(
                                modifier = Modifier.fillMaxWidth().combinedClickable(
                                    onClick = { onOpenBook(book.id) },
                                    onLongClick = { continueBookMenuFor = book.id }
                                )
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
                            DropdownMenu(
                                expanded = continueBookMenuFor == book.id,
                                onDismissRequest = { continueBookMenuFor = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Remove from Continue") },
                                    onClick = {
                                        // Same bargain as Continue
                                        // watching: the book stays
                                        // on the shelf; its saved
                                        // place is forgotten.
                                        store.updateBookProgress(book.id, 0, 0f)
                                        continueBookMenuFor = null
                                    }
                                )
                            }
                            }
                        }
                    }
                }
            }
            // No favourites, no section (Robert, 0.0.965): the
            // header and the empty-state hint sat on the start
            // page permanently; the section exists only when
            // there is something in it.
            if (store.favourites.isNotEmpty()) {
            item {
                Text("Favorites", style = headerStyle)
                Spacer(Modifier.height(8.dp))
                run {
                    val favRows = ((store.favourites.size + 3) / 4).coerceAtLeast(1)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.height((favRows * 96).dp)
                    ) {
                        items(store.favourites.toList()) { fav ->
                            Box {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth().combinedClickable(
                                        onClick = { onOpen(fav.url) },
                                        onLongClick = { favMenuFor = fav.id }
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier.size(52.dp).clip(CircleShape)
                                            .background(
                                                MaterialTheme.colorScheme.primary
                                                    .copy(alpha = 0.16f)
                                            ),
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
                                        fav.title, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                DropdownMenu(
                                    expanded = favMenuFor == fav.id,
                                    onDismissRequest = { favMenuFor = null }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Remove from Favorites") },
                                        onClick = {
                                            favMenuFor = null
                                            store.toggleFavourite(fav.title, fav.url)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
            if (store.setting("showFrequent", "true") == "true" && frequent.isNotEmpty()) {
                item {
                    Text("Frequently visited", style = headerStyle)
                    Spacer(Modifier.height(8.dp))
                    PanelCard {
                        Column {
                            frequent.forEachIndexed { index, entry ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                                ListItem(
                                    headlineContent = {
                                        Text(entry.title.ifBlank { entry.url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    supportingContent = {
                                        Text(Uri.parse(entry.url).host ?: entry.url, maxLines = 1)
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = androidx.compose.ui.graphics.Color.Transparent
                                    ),
                                    modifier = Modifier.clickable { onOpen(entry.url) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
