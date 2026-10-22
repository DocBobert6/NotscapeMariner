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
fun StartPage(store: AppStore, onOpen: (String) -> Unit, onOpenBook: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val wallpaper = store.setting("wallpaper", "ocean")
    val rev = store.revision
    // The wallpaper dresses the PAGE background only (Phase G): the
    // header banner is a fixed UI card and never re-skins with it.
    // Presets are gradients; "custom" is the user's own photo, kept
    // downscaled in app storage; a theme veil over either keeps the
    // page's text readable in the normal theme colours.
    val presetColors = when (wallpaper) {
        "ocean" -> listOf(Color(0xFF0E2A38), Color(0xFF175263))
        "sunset" -> listOf(Color(0xFF3A1C4B), Color(0xFFB84A39))
        "forest" -> listOf(Color(0xFF0E2A1E), Color(0xFF2E6B4F))
        "slate" -> listOf(Color(0xFF23272E), Color(0xFF4A5568))
        "paper" -> listOf(Color(0xFFF3EAD3), Color(0xFFE2D3AC))
        else -> null
    }
    val customPhoto by produceState<Bitmap?>(null, wallpaper, rev) {
        value = if (wallpaper == "custom") {
            withContext(Dispatchers.IO) {
                runCatching {
                    android.graphics.BitmapFactory.decodeFile(
                        java.io.File(context.filesDir, "start_wallpaper.jpg").absolutePath
                    )
                }.getOrNull()
            }
        } else {
            null
        }
    }
    var favMenuFor by remember { mutableStateOf<Long?>(null) }

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
        if (customPhoto != null) {
            Image(
                bitmap = customPhoto!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.45f))
            )
        } else if (presetColors != null) {
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(presetColors))
            )
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f))
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                // The welcome banner: a designed hero surface in
                // its own right (Phase G redesign — the plain card
                // read as a stray document fragment on the page,
                // Robert's words). A deep ocean gradient with the
                // app's own icon artwork as its mark, the same in
                // both themes; the wallpaper dresses the page
                // behind it, never the banner.
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF14384C), Color(0xFF1B6E7D))
                            )
                        )
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
                                "Notscape Mariner",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Local-only and private — everything stays on your device. " +
                                    "Type in the address bar to search or visit a site.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
            if (continueItems.isNotEmpty()) {
                item {
                    Text("Continue", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(continueItems) { (url, title, pos) ->
                            PanelCard(
                                modifier = Modifier.width(220.dp).clickable {
                                    PlayerActivity.launch(context, url, title)
                                }
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
                            PanelCard(
                                modifier = Modifier.fillMaxWidth().clickable { onOpenBook(book.id) }
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
                Text("Favorites", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                if (store.favourites.isEmpty()) {
                    // The list is the user's own — nothing is seeded
                    // (Phase G). An honest empty state instead.
                    Text(
                        "No favorites yet. Open a page, tap the menu, and " +
                            "choose \"Add to favorites\" — it will live here. " +
                            "Long-press a favorite here to remove it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
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
                                    modifier = Modifier.combinedClickable(
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
                                        style = MaterialTheme.typography.labelSmall
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
