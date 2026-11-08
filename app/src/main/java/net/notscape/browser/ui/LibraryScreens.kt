package net.notscape.browser.ui

import android.app.DownloadManager
import android.content.Intent
import android.content.Context
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Public
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.notscape.browser.R
import net.notscape.browser.data.AppStore
import net.notscape.browser.data.Bookmark
import net.notscape.browser.ui.AppHeader
import net.notscape.browser.media.Downloads
import net.notscape.browser.media.PlayerActivity
import net.notscape.browser.media.downloadNotificationsEnabled
import net.notscape.browser.media.openAppNotificationSettings
import net.notscape.browser.media.rememberAskDownloadNotifPermission
import android.net.Uri
import java.io.File
import net.notscape.browser.browser.BookmarkHtml
import net.notscape.browser.data.DownloadRecord
import net.notscape.browser.data.ReadingItem
import net.notscape.browser.data.findMediaDownload
import net.notscape.browser.data.folderChild
import net.notscape.browser.data.folderChildren
import net.notscape.browser.data.folderName
import net.notscape.browser.data.folderParent
import net.notscape.browser.data.isFolderSegment
import net.notscape.browser.emu.BoxArt
import net.notscape.browser.emu.CoreStore
import net.notscape.browser.emu.CueSheet
import net.notscape.browser.emu.GameEntry
import net.notscape.browser.emu.GameLibrary
import net.notscape.browser.emu.ZipRomImport

@Composable
fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = title, onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme
                        .surfaceContainerLow
                )
        ) { content() }
    }
}

/** The kit list row for the library screens (0.0.990):
 * a Frost card surface with a leading slot, a title and
 * supporting lines in kit type, and a trailing slot.
 * Replaces the stock ListItem rows these screens used
 * to wear. */
@Composable
fun KitRow(
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    title: @Composable () -> Unit,
    supporting: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = Frost.CardShape,
        color = Frost.cardColor(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (onClick != null) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                title()
                if (supporting != null) supporting()
            }
            if (trailing != null) trailing()
        }
    }
}

/** The kit's selectable pill (0.0.990), standing in for
 * the stock FilterChip/AssistChip: filled with the
 * primary colour when selected, a quiet container
 * colour otherwise. */
@Composable
fun KitPill(
    label: String,
    selected: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.heightIn(min = 48.dp).clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 14.dp, vertical = 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A row inside a grouped card (0.0.995): no surface
 * of its own - it sits directly on a SettingsCard, the
 * way Settings' own rows do - with the leading slot
 * centred in a fixed-width column so the text starts
 * align down the card. This is the composition half of
 * the library restyle: 0.0.990 gave these screens kit
 * rows, but they still sat as separate cards on a plain
 * background instead of grouped cards on the tinted
 * backdrop, and Robert called that out. */
@Composable
fun KitGroupedRow(
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    leadingWidth: Dp = 30.dp,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    title: @Composable () -> Unit,
    supporting: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier.width(leadingWidth),
                contentAlignment = Alignment.Center
            ) { leading() }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            title()
            if (supporting != null) supporting()
        }
        if (trailing != null) trailing()
    }
}

/** The History day-group label (0.0.995). */
internal fun dayLabel(visitedAt: Long): String {
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = visitedAt
    }
    val now = java.util.Calendar.getInstance()
    fun sameDay(
        a: java.util.Calendar, b: java.util.Calendar
    ) = a.get(java.util.Calendar.ERA) ==
        b.get(java.util.Calendar.ERA) &&
        a.get(java.util.Calendar.YEAR) ==
        b.get(java.util.Calendar.YEAR) &&
        a.get(java.util.Calendar.DAY_OF_YEAR) ==
        b.get(java.util.Calendar.DAY_OF_YEAR)
    if (sameDay(cal, now)) return "Today"
    now.add(java.util.Calendar.DAY_OF_YEAR, -1)
    if (sameDay(cal, now)) return "Yesterday"
    return java.text.SimpleDateFormat(
        "EEE, MMM d, yyyy", java.util.Locale.US
    ).format(java.util.Date(visitedAt))
}

@Composable
fun BookmarksScreen(store: AppStore, onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val rev = store.revision
    val context = LocalContext.current
    // Folder filter: null = All, "" = Unfiled, otherwise a folder name.
    var folderFilter by remember { mutableStateOf<String?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var showManageFolders by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<String?>(null) }
    var confirmDeleteFolder by remember { mutableStateOf<String?>(null) }
    var moveTarget by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkQuery by remember { mutableStateOf("") }
    // Audit P-m1/P-m7: edit dialog, sort order, multi-select.
    var editBookmark by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkSort by remember { mutableStateOf("newest") }
    var bookmarkSelecting by remember { mutableStateOf(false) }
    var bookmarkSelected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // Beta.64 #41: undo after delete (like tab-close
    // undo) + bulk move between folders.
    val snackHost = remember { SnackbarHostState() }
    val snackScope = rememberCoroutineScope()
    var showBulkMove by remember { mutableStateOf(false) }
    // Beta.64 #17: export bookmarks as Netscape HTML,
    // the same format the Settings import reads.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            val text = BookmarkHtml.export(
                store.bookmarks.map {
                    BookmarkHtml.ExportItem(
                        it.title, it.url, it.folder, it.createdAt
                    )
                }
            )
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray())
                } ?: error("no stream")
            }.isSuccess
            Toast.makeText(
                context,
                if (ok) "Exported ${store.bookmarks.size} bookmarks"
                else "Export failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    SubScreen("Bookmarks", onBack) {
        OutlinedTextField(
            value = bookmarkQuery,
            onValueChange = { bookmarkQuery = it },
            label = { Text("Search bookmarks") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                bookmarkSort = when (bookmarkSort) {
                    "newest" -> "oldest"
                    "oldest" -> "title"
                    else -> "newest"
                }
            }) {
                Text(
                    when (bookmarkSort) {
                        "oldest" -> "Sort: oldest"
                        "title" -> "Sort: title"
                        else -> "Sort: newest"
                    }
                )
            }
            TextButton(onClick = { exportLauncher.launch("bookmarks.html") }) {
                Text("Export")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                bookmarkSelecting = !bookmarkSelecting
                if (!bookmarkSelecting) bookmarkSelected = emptySet()
            }) { Text(if (bookmarkSelecting) "Done" else "Select") }
            if (bookmarkSelected.isNotEmpty()) {
                TextButton(onClick = { showBulkMove = true }) {
                    Text("Move (${bookmarkSelected.size})")
                }
                TextButton(onClick = {
                    // Beta.64 #41: undo after delete.
                    val deleted = store.bookmarks.filter { it.id in bookmarkSelected }
                    bookmarkSelected.forEach { store.removeBookmark(it) }
                    bookmarkSelected = emptySet()
                    bookmarkSelecting = false
                    snackScope.launch {
                        if (snackHost.showSnackbar(
                                if (deleted.size == 1) "Deleted 1 bookmark"
                                else "Deleted ${deleted.size} bookmarks",
                                actionLabel = "Undo"
                            ) == SnackbarResult.ActionPerformed
                        ) {
                            store.restoreBookmarks(deleted)
                        }
                    }
                }) { Text("Delete (${bookmarkSelected.size})") }
            }
        }
        // Beta.64 #18: the row shows a breadcrumb back
        // up the current path, then that folder's
        // children (as segments); the top level shows
        // every top-level folder, Unfiled, New folder
        // and Manage folders.
        val childFolders = folderChildren(
            store.bookmarkFolders, folderFilter ?: ""
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (folderFilter != null && folderFilter!!.isNotBlank()) {
                item {
                    KitPill(
                        label = "All",
                        selected = false,
                        onClick = { folderFilter = null }
                    )
                }
                var path = ""
                for (seg in folderFilter!!.split("/")) {
                    path = if (path.isEmpty()) seg else "$path/$seg"
                    val p = path
                    item {
                        KitPill(
                            label = seg,
                            selected = p == folderFilter,
                            onClick = { folderFilter = p }
                        )
                    }
                }
            } else {
                item {
                    KitPill(
                        label = "All",
                        selected = folderFilter == null,
                        onClick = { folderFilter = null }
                    )
                }
                item {
                    KitPill(
                        label = "Unfiled",
                        selected = folderFilter == "",
                        onClick = { folderFilter = "" }
                    )
                }
            }
            items(childFolders) { f ->
                KitPill(
                    label = folderName(f),
                    selected = folderFilter == f,
                    onClick = { folderFilter = f }
                )
            }
            item {
                KitPill(
                    label = "New folder",
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    onClick = { showNewFolder = true }
                )
            }
            if (store.bookmarkFolders.isNotEmpty()) {
                item {
                    KitPill(
                        label = "Manage folders",
                        onClick = { showManageFolders = true }
                    )
                }
            }
        }
        val shown = store.bookmarks.filter { b ->
            (folderFilter == null || b.folder == folderFilter) &&
                (bookmarkQuery.isBlank() ||
                    b.title.contains(bookmarkQuery, ignoreCase = true) ||
                    b.url.contains(bookmarkQuery, ignoreCase = true))
        }.let { list ->
            when (bookmarkSort) {
                "oldest" -> list.sortedBy { it.createdAt }
                "title" -> list.sortedBy { it.title.lowercase() }
                else -> list.sortedByDescending { it.createdAt }
            }
        }
        if (store.bookmarks.isEmpty()) {
            PanelCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("No bookmarks yet. Star a page to add one.", modifier = Modifier.padding(16.dp))
            }
        } else if (shown.isEmpty()) {
            PanelCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    if (bookmarkQuery.isNotBlank()) "No bookmarks match your search."
                    else "Nothing in this folder yet.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        if (shown.isNotEmpty()) {
            LazyColumn {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        GroupTitle(
                            when {
                                folderFilter == null -> "All"
                                folderFilter == "" -> "Unfiled"
                                else -> folderFilter!!
                            }
                        )
                        SettingsCard {
                            shown.forEachIndexed { i, b ->
                                if (i > 0) InsetDivider()
                                KitGroupedRow(
                                    onClick = {
                                        if (bookmarkSelecting) {
                                            bookmarkSelected =
                                                if (b.id in bookmarkSelected) bookmarkSelected - b.id
                                                else bookmarkSelected + b.id
                                        } else onOpenUrl(b.url)
                                    },
                                leading = {
                                    // Audit P-m6: the tab-captured
                                    // favicon for this host, with
                                    // the generic icon as fallback.
                                    val host = runCatching {
                                        android.net.Uri.parse(b.url).host
                                    }.getOrNull()
                                    val fav = host?.let {
                                        remember(it) { store.loadFavicon(it) }
                                    }
                                    if (bookmarkSelecting) {
                                        Checkbox(
                                            checked = b.id in bookmarkSelected,
                                            onCheckedChange = {
                                                bookmarkSelected =
                                                    if (b.id in bookmarkSelected) bookmarkSelected - b.id
                                                    else bookmarkSelected + b.id
                                            }
                                        )
                                    } else if (fav != null) {
                                        Image(
                                            bitmap = fav.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Public,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme
                                                .onSurfaceVariant
                                        )
                                    }
                                },
                                title = {
                        Text(
                            b.title, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                                supporting = {
                        Column {
                            Text(
                                b.url, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (folderFilter == null && b.folder.isNotBlank()) {
                                Text(
                                    b.folder, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                                trailing = {
                        Row {
                            IconButton(onClick = { editBookmark = b }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit bookmark")
                            }
                            IconButton(onClick = { moveTarget = b }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Move to folder")
                            }
                            IconButton(onClick = { store.toggleBookmark(b.title, b.url) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove")
                            }
                        }
                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // Audit P-m1: edit title + URL, re-validated to http(s).
    editBookmark?.let { b ->
        var titleDraft by remember(b.id) { mutableStateOf(b.title) }
        var urlDraft by remember(b.id) { mutableStateOf(b.url) }
        val urlOk = urlDraft.startsWith("http://") || urlDraft.startsWith("https://")
        FrostDialog(
            onDismissRequest = { editBookmark = null },
            title = { Text("Edit bookmark") },
            text = {
                Column {
                    OutlinedTextField(
                        value = titleDraft,
                        onValueChange = { titleDraft = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = urlDraft,
                        onValueChange = { urlDraft = it },
                        label = { Text("URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!urlOk) {
                        Text(
                            "URL must start with http:// or https://",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = urlOk && urlDraft.isNotBlank(),
                    onClick = {
                        store.updateBookmark(b.id, titleDraft, urlDraft.trim())
                        editBookmark = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editBookmark = null }) { Text("Cancel") }
            }
        )
    }

    if (showNewFolder) {
        var name by remember { mutableStateOf("") }
        FrostDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Folder name") }, singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Beta.64 #18: the new folder is
                        // created inside the folder being
                        // viewed (top level from All/Unfiled).
                        if (isFolderSegment(name)) {
                            val parent = folderFilter ?: ""
                            store.addBookmarkFolder(name, parent)
                            folderFilter = folderChild(parent, name)
                        }
                        showNewFolder = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolder = false }) { Text("Cancel") }
            }
        )
    }

    if (showManageFolders) {
        FrostDialog(
            onDismissRequest = { showManageFolders = false },
            title = { Text("Manage folders") },
            text = {
                Column {
                    Text("Deleting a folder keeps its bookmarks — they become Unfiled. Subfolders are deleted too.")
                    Spacer(Modifier.height(8.dp))
                    store.bookmarkFolders.toList().forEach { f ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Beta.64 #18: nested folders are
                            // indented by depth, showing only
                            // their last segment.
                            Text(
                                folderName(f),
                                modifier = Modifier.weight(1f)
                                    .padding(
                                        start = (f.count { it == '/' } * 12).dp,
                                        top = 10.dp, bottom = 10.dp
                                    )
                            )
                            TextButton(onClick = {
                                showManageFolders = false
                                renameFolder = f
                            }) { Text("Rename") }
                            TextButton(onClick = {
                                showManageFolders = false
                                confirmDeleteFolder = f
                            }) { Text("Delete") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageFolders = false }) { Text("Done") }
            }
        )
    }

    renameFolder?.let { old ->
        var name by remember(old) { mutableStateOf(folderName(old)) }
        FrostDialog(
            onDismissRequest = { renameFolder = null },
            title = { Text("Rename folder") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Folder name") }, singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.renameBookmarkFolder(old, name)
                        // The view follows the renamed
                        // folder (and any folder below it).
                        val newPath = folderChild(folderParent(old), name)
                        if (folderFilter == old) folderFilter = newPath
                        else if (folderFilter != null &&
                            folderFilter!!.startsWith("$old/")
                        ) {
                            folderFilter = newPath +
                                folderFilter!!.removePrefix(old)
                        }
                        renameFolder = null
                    },
                    enabled = name.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameFolder = null }) { Text("Cancel") }
            }
        )
    }

    confirmDeleteFolder?.let { f ->
        FrostDialog(
            onDismissRequest = { confirmDeleteFolder = null },
            title = { Text("Delete folder “$f”?") },
            text = { Text("Its bookmarks are kept and become Unfiled.") },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteBookmarkFolder(f)
                    if (folderFilter == f ||
                        (folderFilter != null &&
                            folderFilter!!.startsWith("$f/"))
                    ) folderFilter = null
                    confirmDeleteFolder = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFolder = null }) { Text("Cancel") }
            }
        )
    }

    if (showBulkMove) {
        FrostDialog(
            onDismissRequest = { showBulkMove = false },
            title = { Text("Move ${bookmarkSelected.size} bookmarks") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            bookmarkSelected.forEach { store.setBookmarkFolder(it, "") }
                            bookmarkSelected = emptySet()
                            bookmarkSelecting = false
                            showBulkMove = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Unfiled") }
                    store.bookmarkFolders.toList().forEach { f ->
                        TextButton(
                            onClick = {
                                bookmarkSelected.forEach { store.setBookmarkFolder(it, f) }
                                bookmarkSelected = emptySet()
                                bookmarkSelecting = false
                                showBulkMove = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(f) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBulkMove = false }) { Text("Cancel") }
            }
        )
    }

    moveTarget?.let { b ->
        FrostDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("Move to folder") },
            text = {
                Column {
                    Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { store.setBookmarkFolder(b.id, ""); moveTarget = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (b.folder.isEmpty()) "Unfiled ✓" else "Unfiled") }
                    store.bookmarkFolders.toList().forEach { f ->
                        TextButton(
                            onClick = { store.setBookmarkFolder(b.id, f); moveTarget = null },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (b.folder == f) "$f ✓" else f) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { moveTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Beta.64 #41: undo snackbar (like tab-close undo).
    // Zero height while idle — the host only lays out
    // while a snackbar is showing.
    Box(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        SnackbarHost(hostState = snackHost)
    }
}

@Composable
fun HistoryScreen(store: AppStore, onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val rev = store.revision
    var historyQuery by remember { mutableStateOf("") }
    // Audit P-m2/P-m7: per-row delete, time-range
    // clear, sort, multi-select.
    var historySort by remember { mutableStateOf("newest") }
    var historySelecting by remember { mutableStateOf(false) }
    var historySelected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showClearHistory by remember { mutableStateOf(false) }
    // Beta.64 #41: undo after delete (like tab-close undo).
    val historySnackHost = remember { SnackbarHostState() }
    val historySnackScope = rememberCoroutineScope()
    SubScreen("History", onBack) {
        OutlinedTextField(
            value = historyQuery,
            onValueChange = { historyQuery = it },
            label = { Text("Search history") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            TextButton(onClick = {
                historySort = when (historySort) {
                    "newest" -> "oldest"
                    "oldest" -> "title"
                    else -> "newest"
                }
            }) {
                Text(
                    when (historySort) {
                        "oldest" -> "Sort: oldest"
                        "title" -> "Sort: title"
                        else -> "Sort: newest"
                    }
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                historySelecting = !historySelecting
                if (!historySelecting) historySelected = emptySet()
            }) { Text(if (historySelecting) "Done" else "Select") }
            if (historySelected.isNotEmpty()) {
                TextButton(onClick = {
                    // Beta.64 #41: undo after delete.
                    val deleted = store.history.filter { it.id in historySelected }
                    historySelected.forEach { store.removeHistory(it) }
                    historySelected = emptySet()
                    historySelecting = false
                    historySnackScope.launch {
                        if (historySnackHost.showSnackbar(
                                if (deleted.size == 1) "Deleted 1 entry"
                                else "Deleted ${deleted.size} entries",
                                actionLabel = "Undo"
                            ) == SnackbarResult.ActionPerformed
                        ) {
                            store.restoreHistory(deleted)
                        }
                    }
                }) { Text("Delete (${historySelected.size})") }
            }
            TextButton(onClick = { showClearHistory = true }) { Text("Clear history") }
        }
        val shownHistory = store.history.filter { h ->
            historyQuery.isBlank() ||
                h.title.contains(historyQuery, ignoreCase = true) ||
                h.url.contains(historyQuery, ignoreCase = true)
        }.let { list ->
            when (historySort) {
                "oldest" -> list.sortedBy { it.visitedAt }
                "title" -> list.sortedBy { it.title.lowercase() }
                else -> list.sortedByDescending { it.visitedAt }
            }
        }
        if (store.history.isEmpty()) {
            PanelCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("No history yet.", modifier = Modifier.padding(16.dp))
            }
        } else if (shownHistory.isEmpty()) {
            PanelCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text("No history matches your search.", modifier = Modifier.padding(16.dp))
            }
        }
        if (shownHistory.isNotEmpty()) {
            LazyColumn {
                shownHistory.groupBy { dayLabel(it.visitedAt) }
                    .forEach { (day, entries) ->
                        item(key = "day-$day") {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                // Beta.64 #19: the count makes
                                // repeat visits visible.
                                GroupTitle(
                                    if (entries.size == 1) "$day (1 visit)"
                                    else "$day (${entries.size} visits)"
                                )
                                SettingsCard {
                                    entries.forEachIndexed { i, h ->
                                        if (i > 0) InsetDivider()
                                        KitGroupedRow(
                                            onClick = {
                                                if (historySelecting) {
                                                    historySelected =
                                                        if (h.id in historySelected) historySelected - h.id
                                                        else historySelected + h.id
                                                } else onOpenUrl(h.url)
                                            },
                                        leading = {
                                            val host = runCatching {
                                                android.net.Uri.parse(h.url).host
                                            }.getOrNull()
                                            val fav = host?.let {
                                                remember(it) { store.loadFavicon(it) }
                                            }
                                            if (historySelecting) {
                                                Checkbox(
                                                    checked = h.id in historySelected,
                                                    onCheckedChange = {
                                                        historySelected =
                                                            if (h.id in historySelected) historySelected - h.id
                                                            else historySelected + h.id
                                                    }
                                                )
                                            } else if (fav != null) {
                                                Image(
                                                    bitmap = fav.asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Filled.Public,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme
                                                        .onSurfaceVariant
                                                )
                                            }
                                        },
                                        trailing = {
                                            IconButton(onClick = {
                                                // Beta.64 #41: undo after delete.
                                                val deleted = listOf(h)
                                                store.removeHistory(h.id)
                                                historySnackScope.launch {
                                                    if (historySnackHost.showSnackbar(
                                                            "Deleted 1 entry",
                                                            actionLabel = "Undo"
                                                        ) == SnackbarResult.ActionPerformed
                                                    ) {
                                                        store.restoreHistory(deleted)
                                                    }
                                                }
                                            }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                                            }
                                        },
                                        title = {
                        Text(
                            h.title, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                                        supporting = {
                        Text(
                            h.url, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
            }
        }
    }

    // Audit P-m2: time-range clear.
    if (showClearHistory) {
        FrostDialog(
            onDismissRequest = { showClearHistory = false },
            title = { Text("Clear history") },
            text = { Text("Remove history from the last hour, day, week, or all of it?") },
            confirmButton = {
                Column {
                    listOf(
                        "Last hour" to 3600_000L,
                        "Last day" to 24L * 3600_000L,
                        "Last week" to 7L * 24 * 3600_000L
                    ).forEach { (label, range) ->
                        TextButton(
                            onClick = {
                                store.clearHistoryOlderThan(
                                    System.currentTimeMillis() - range
                                )
                                showClearHistory = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label) }
                    }
                    TextButton(
                        onClick = {
                            store.clearHistory()
                            showClearHistory = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("All history") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistory = false }) { Text("Cancel") }
            }
        )
    }

    // Beta.64 #41: undo snackbar (like tab-close undo).
    // Zero height while idle — the host only lays out
    // while a snackbar is showing.
    Box(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        SnackbarHost(hostState = historySnackHost)
    }
}

@Composable
fun PlaylistsScreen(store: AppStore, onBack: () -> Unit) {
    val rev = store.revision
    // Beta.64 #53: progress ticks re-render the
    // "Downloading • N%" state live.
    val progressRev = store.downloadProgressRev
    val context = LocalContext.current
    var newName by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    SubScreen("Playlists", onBack) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("New playlist")
            }
        }
        if (store.playlists.isEmpty()) {
            PanelCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "No playlists yet. Use “Media on this page” in the browser to add videos.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        // Beta.64 #53: media downloads, for the per-item
        // download / offline state below.
        val mediaDownloads = store.downloadsSnapshot().filter { it.media }
        LazyColumn {
            store.playlists.toList().forEach { playlist ->
                item(key = "pl-${playlist.id}") {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        GroupTitle(playlist.name)
                        // Beta.64 #53: how many items are
                        // downloaded / available offline.
                        val downloadedCount = playlist.items.count { item ->
                            mediaDownloads.any {
                                it.url == item.url && it.status == "complete"
                            }
                        }
                        SettingsCard {
                            KitGroupedRow(
                                    leading = {
                                        Icon(
                                            Icons.Filled.PlaylistPlay,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme
                                                .onSurfaceVariant
                                        )
                                    },
                                    title = {
                            Text(
                                playlist.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                                    supporting = {
                            Text(
                                "${playlist.items.size} items" +
                                    if (downloadedCount > 0) " • $downloadedCount downloaded"
                                    else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                                    trailing = {
                            Row {
                                IconButton(
                                    onClick = {
                                        if (playlist.items.isNotEmpty()) {
                                            PlayerActivity.launchQueue(
                                                context,
                                                playlist.items.map { it.url },
                                                playlist.items.map { it.title },
                                                0
                                            )
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.PlaylistPlay, contentDescription = "Play all")
                                }
                                IconButton(onClick = { store.deletePlaylist(playlist.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete playlist")
                                }
                            }
                        },
                            )
                            playlist.items.forEach { item ->
                                InsetDivider()
                                KitGroupedRow(
                                leading = {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme
                                            .onSurfaceVariant
                                    )
                                },
                                title = {
                            Text(
                                item.title, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                                supporting = {
                            Column {
                                Text(
                                    item.url, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // Beta.64 #53: download / offline state.
                                val dl = findMediaDownload(mediaDownloads, item.url)
                                if (dl != null) {
                                    Text(
                                        when {
                                            dl.status == "complete" ->
                                                "Downloaded • available offline"
                                            dl.status == "failed" -> "Download failed"
                                            else -> "Downloading" +
                                                if (dl.progress >= 0) " • ${dl.progress}%"
                                                else "…"
                                        },
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                                trailing = {
                            Row {
                                IconButton(onClick = {
                                    PlayerActivity.launch(context, item.url, item.title)
                                }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                                }
                                IconButton(onClick = { store.removeFromPlaylist(playlist.id, item.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove")
                                }
                            }
                        },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
    if (showCreate) {
        FrostDialog(
            onDismissRequest = { showCreate = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) store.createPlaylist(newName.trim())
                    newName = ""
                    showCreate = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun LibraryScreen(store: AppStore, onBack: () -> Unit, onOpenUrl: (String) -> Unit = {}) {
    val rev = store.revision
    // Audit P-M5: progress ticks bump this, not the global revision.
    val progressRev = store.downloadProgressRev
    val context = LocalContext.current
    // One-time download notification ask (2026-10-26); no-ops once asked or granted.
    val askDownloadNotif = rememberAskDownloadNotifPermission(store)
    var downloadQuery by remember { mutableStateOf("") }
    // Audit P-m7: multi-select delete on this screen.
    var dlSelecting by remember { mutableStateOf(false) }
    var dlSelected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val dlScreenMedia = true
    // Beta.64 #21: sort the offline library.
    var mediaSort by remember { mutableStateOf("newest") }
    var exportTarget by remember {
        mutableStateOf<net.notscape.browser.data.DownloadRecord?>(null)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val rec = exportTarget
        exportTarget = null
        if (uri != null && rec != null) {
            Thread { Downloads.copyExport(context, store, rec, uri) }.start()
        }
    }
    LaunchedEffect(Unit) {
        Downloads.refresh(context, store)
    }
    SubScreen("Offline library", onBack) {
        var refreshing by remember { mutableStateOf(false) }
        val refreshScope = rememberCoroutineScope()
        OutlinedTextField(
            value = downloadQuery,
            onValueChange = { downloadQuery = it },
            label = { Text("Search downloads") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                dlSelecting = !dlSelecting
                if (!dlSelecting) dlSelected = emptySet()
            }) { Text(if (dlSelecting) "Done" else "Select") }
            if (dlSelected.isNotEmpty()) {
                // Records only, files stay — same rule
                // as "Clear completed" (audit P-p4).
                TextButton(onClick = {
                    store.downloadsSnapshot()
                        .filter { it.id in dlSelected && it.media == dlScreenMedia }
                        .forEach { rec ->
                            Downloads.deleteEngineStaging(context, store, rec)
                            Downloads.clearJobBundle(store, rec.id)
                            store.removeDownload(rec.id)
                        }
                    dlSelected = emptySet()
                    dlSelecting = false
                }) { Text("Delete records (${dlSelected.size})") }
            }
            TextButton(onClick = {
                mediaSort = when (mediaSort) {
                    "newest" -> "oldest"
                    "oldest" -> "title"
                    else -> "newest"
                }
            }) {
                Text(
                    when (mediaSort) {
                        "oldest" -> "Sort: oldest"
                        "title" -> "Sort: title"
                        else -> "Sort: newest"
                    }
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            TextButton(
                enabled = !refreshing,
                onClick = {
                    refreshScope.launch {
                        refreshing = true
                        withContext(Dispatchers.IO) {
                            Downloads.refresh(context, store)
                        }
                        refreshing = false
                    }
                }
            ) { Text(if (refreshing) "Refreshing…" else "Refresh status") }
            // Audit P-p4: records only — files stay.
            if (store.downloadsSnapshot().any { it.media && it.status == "complete" }) {
                TextButton(onClick = { Downloads.clearCompletedRecords(store, true) }) {
                    Text("Clear completed")
                }
            }
        }
        // The offline library is for media downloads only; ordinary file
        // and image downloads live in the phone's Downloads folder
        // and on the Downloads page.
        val mediaDownloads = store.downloadsSnapshot().filter { rec ->
            rec.media && !rec.isPrivate &&
                (downloadQuery.isBlank() ||
                    rec.title.contains(downloadQuery, ignoreCase = true) ||
                    rec.fileName.contains(downloadQuery, ignoreCase = true))
        }.let { list ->
            // Beta.64 #21: sort the offline library.
            when (mediaSort) {
                "oldest" -> list.sortedBy { it.addedAt }
                "title" -> list.sortedBy { it.title.lowercase() }
                else -> list.sortedByDescending { it.addedAt }
            }
        }
        if (mediaDownloads.isEmpty()) {
            PanelCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    if (downloadQuery.isNotBlank()) "No downloads match your search." else "Nothing downloaded yet. Use “Media on this page” in the browser and tap download.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        if (mediaDownloads.isNotEmpty()) {
            LazyColumn {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        GroupTitle("Downloads")
                        SettingsCard {
                            mediaDownloads.forEachIndexed { i, rec ->
                                if (i > 0) InsetDivider()
                                val source = Downloads.localSource(context, store, rec)
                                val active = rec.status == "queued" || rec.status == "downloading" ||
                                    rec.status == "paused"
                                KitGroupedRow(
                                leading = {
                                    if (dlSelecting) {
                                        Checkbox(
                                            checked = rec.id in dlSelected,
                                            onCheckedChange = {
                                                dlSelected =
                                                    if (rec.id in dlSelected) dlSelected - rec.id
                                                    else dlSelected + rec.id
                                            }
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme
                                                .onSurfaceVariant
                                        )
                                    }
                                },
                                title = {
                        Text(
                            rec.title, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                                supporting = {
                        Column {
                            // Audit P-m13: a download still in progress
                            // after a failed attempt is a retry in
                            // flight, not a fresh download.
                            val dlErrFirst = store.setting(
                                "dlerror:${rec.id}", ""
                            )
                            val statusText = when {
                                Downloads.isWaitingForWifi(context, store) &&
                                    (rec.status == "downloading" || rec.status == "queued") ->
                                    "Waiting for Wi-Fi"
                                rec.status == "downloading" && dlErrFirst.isNotBlank() ->
                                    stringResource(R.string.download_status_retrying)
                                else -> rec.status
                            }
                            Text(
                                "${statusText}${if (active && rec.progress >= 0) " • ${rec.progress}%" else ""} • ${rec.fileName}",
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Beta.64 #30: size / speed / ETA.
                            Downloads.progressDetailLine(rec)?.let { detail ->
                                Text(
                                    detail,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val dlErr = store.setting(
                                "dlerror:${rec.id}", ""
                            )
                            if (dlErr.isNotBlank() &&
                                (rec.status == "failed" || active)
                            ) {
                                Text(
                                    dlErr,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography
                                        .bodySmall,
                                    color = MaterialTheme.colorScheme
                                        .error
                                )
                            }
                            // Audit D-p4: a downgraded job kept
                            // going under the system downloader
                            // — say so instead of implying the
                            // segmented gate still applies.
                            if (store.setting("dldowngrade:${rec.id}", "") == "1") {
                                Text(
                                    "Continued via system downloader",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography
                                        .bodySmall,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                )
                            }
                            if (active) {
                                LinearProgressIndicator(
                                    progress = { if (rec.progress >= 0) rec.progress / 100f else 0f },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }
                    },
                                trailing = {
                        Row {
                            if (rec.status == "downloading" &&
                                Downloads.isResumableEngine(Downloads.engineFor(store, rec))
                            ) {
                                IconButton(onClick = { Downloads.pause(context, store, rec) }) {
                                    Icon(Icons.Filled.Pause, contentDescription = "Pause download")
                                }
                            }
                            if (rec.status == "paused" &&
                                Downloads.isResumableEngine(Downloads.engineFor(store, rec))
                            ) {
                                IconButton(onClick = { Downloads.resume(context, store, rec) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Resume download")
                                }
                            }
                            if (active) {
                                IconButton(onClick = { Downloads.cancel(context, store, rec) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Cancel download")
                                }
                            }
                            if (rec.status == "complete" && source != null) {
                                IconButton(onClick = {
                                    PlayerActivity.launch(context, source, rec.title)
                                }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play offline")
                                }
                            }
                            if (rec.status == "complete" && source != null) {
                                IconButton(onClick = {
                                    Downloads.shareIntent(context, store, rec)
                                        ?.let { intent ->
                                            context.startActivity(
                                                Intent.createChooser(
                                                    intent, "Share download"
                                                ).addFlags(
                                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                                )
                                            )
                                        }
                                }) {
                                    Icon(
                                        Icons.Filled.Share,
                                        contentDescription = "Share download"
                                    )
                                }
                                IconButton(onClick = {
                                    exportTarget = rec
                                    exportLauncher.launch(rec.fileName)
                                }) {
                                    Icon(
                                        Icons.Filled.SaveAlt,
                                        contentDescription = "Save a copy"
                                    )
                                }
                            }
                            if (rec.status == "failed") {
                                IconButton(onClick = {
                                    // Audit D-M1: retry re-enqueues
                                    // through the saved job bundle —
                                    // merges re-run with both side
                                    // URLs, location and rename are
                                    // preserved. Only a missing
                                    // bundle falls back to
                                    // delete-and-restart.
                                    askDownloadNotif()
                                    if (Downloads.retry(context, store, rec)) {
                                        Toast.makeText(
                                            context, "Download restarted",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.download_delete_failed),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }) {
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = "Retry download"
                                    )
                                }
                            }
                            IconButton(onClick = {
                                // Audit F12: only drop the record when
                                // both removals actually worked.
                                val dmOk = if (rec.dmId >= 0) {
                                    runCatching {
                                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                        dm.remove(rec.dmId)
                                    }.isSuccess
                                } else true
                                val wiped = Downloads.deleteLocal(context, store, rec)
                                if (dmOk && wiped) {
                                    Downloads.clearJobBundle(store, rec.id)
                                    store.removeDownload(rec.id)
                                Downloads.deleteEngineStaging(context, store, rec)
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.download_delete_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete download")
                            }
                        }
                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
                item {
                    // Audit P-m24: saved pages, newest first.
                    val savedPages = store.offlinePages()
                    if (savedPages.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            GroupTitle("Saved pages")
                            SettingsCard {
                                savedPages.forEachIndexed { i, page ->
                                    if (i > 0) InsetDivider()
                                    KitGroupedRow(
                                        onClick = { onOpenUrl(page.url) },
                                        title = {
                                            Text(page.title.ifBlank { page.url })
                                        },
                                        supporting = {
                                            Text(
                                                "Saved copy on device · ${page.url}"
                                            )
                                        },
                                        trailing = {
                                            IconButton(onClick = {
                                                runCatching {
                                                    java.io.File(page.path).delete()
                                                }
                                                store.removeOfflinePage(page.id)
                                            }) {
                                                Icon(
                                                    Icons.Filled.Delete,
                                                    contentDescription = "Delete saved page"
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun DownloadsScreen(
    store: AppStore,
    onBack: () -> Unit,
    onOpenGame: (String) -> Unit,
    onOpenGames: () -> Unit
) {
    val rev = store.revision
    // Audit P-M5: progress ticks bump this, not the global revision.
    val progressRev = store.downloadProgressRev
    val context = LocalContext.current
    // One-time download notification ask (2026-10-26); no-ops once asked or granted.
    val askDownloadNotif = rememberAskDownloadNotifPermission(store)
    var downloadQuery by remember { mutableStateOf("") }
    // Beta.64 #20: the private-downloads confirm dialog.
    var showPrivateConfirm by remember { mutableStateOf(false) }
    // Audit P-m7: multi-select delete on this screen.
    var dlSelecting by remember { mutableStateOf(false) }
    var dlSelected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val dlScreenMedia = false
    // Beta.64 #21: sort file downloads (and the private
    // list below, same order).
    var fileSort by remember { mutableStateOf("newest") }
    LaunchedEffect(Unit) {
        Downloads.refresh(context, store)
    }
    SubScreen("Downloads", onBack) {
        var refreshing by remember { mutableStateOf(false) }
        val refreshScope = rememberCoroutineScope()
        // Zip import from the Downloads page
        // (beta.34): mirrors the console-page zip
        // import, but targetless — every supported
        // system matches, and shared disc extensions
        // land on PlayStation, exactly like folder
        // import. Sega CD discs go through the
        // Sega CD page. Cover art is fetched on the
        // IO thread, never on Main.
        fun openDownloadStream(rec: DownloadRecord): java.io.InputStream? {
            val src = Downloads.localSource(context, store, rec) ?: return null
            return if (src.startsWith("content://")) {
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(src))
                }.getOrNull()
            } else {
                runCatching { File(src).inputStream() }.getOrNull()
            }
        }
        fun importDownloadZip(rec: DownloadRecord) {
            refreshScope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching {
                        val imported = mutableListOf<GameEntry>()
                        val wanted = mutableMapOf<String, String>()
                        openDownloadStream(rec)?.use { input ->
                            ZipRomImport.forEachEntry(
                                input, null
                            ) { fileName, displayName, system, bytes ->
                                if (displayName.endsWith(
                                        ".cue", ignoreCase = true
                                    )
                                ) {
                                    for (ref in CueSheet.cueFiles(
                                        bytes.decodeToString()
                                    )) {
                                        wanted[ref.lowercase()] = ref
                                    }
                                } else if (displayName.endsWith(
                                        ".m3u", ignoreCase = true
                                    )
                                ) {
                                    for (ref in CueSheet.m3uFiles(
                                        bytes.decodeToString()
                                    )) {
                                        wanted[ref.lowercase()] = ref
                                    }
                                }
                                val dir = File(
                                    CoreStore.romDir(context), system
                                ).apply { mkdirs() }
                                File(dir, fileName).outputStream()
                                    .use { it.write(bytes) }
                                imported.add(
                                    GameLibrary.add(
                                        store,
                                        title = displayName
                                            .substringBeforeLast('.'),
                                        system = system,
                                        fileName = fileName
                                    )
                                )
                            }
                        } ?: error("Couldn't read that download")
                        if (wanted.isNotEmpty()) {
                            val destDir = File(
                                CoreStore.romDir(context), "ps1"
                            )
                            openDownloadStream(rec)?.use { input ->
                                ZipRomImport.streamBinSiblings(
                                    input, wanted, destDir
                                )
                            } ?: error("Couldn't read that download")
                        }
                        imported.forEach {
                            runCatching {
                                BoxArt.fetch(context, it, false)
                            }
                        }
                        imported
                    }
                }
                outcome.onSuccess { imported ->
                    if (imported.isEmpty()) {
                        Toast.makeText(
                            context,
                            "No games found in that zip",
                            Toast.LENGTH_LONG
                        ).show()
                    } else if (imported.size == 1 &&
                        GameLibrary.isPlayable(imported[0].system)
                    ) {
                        onOpenGame(imported[0].id)
                    } else {
                        Toast.makeText(
                            context,
                            "Imported ${imported.size} games",
                            Toast.LENGTH_SHORT
                        ).show()
                        onOpenGames()
                    }
                }.onFailure { e ->
                    Toast.makeText(
                        context,
                        e.message ?: "Couldn't import that zip",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        OutlinedTextField(
            value = downloadQuery,
            onValueChange = { downloadQuery = it },
            label = { Text("Search downloads") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                dlSelecting = !dlSelecting
                if (!dlSelecting) dlSelected = emptySet()
            }) { Text(if (dlSelecting) "Done" else "Select") }
            if (dlSelected.isNotEmpty()) {
                // Records only, files stay — same rule
                // as "Clear completed" (audit P-p4).
                TextButton(onClick = {
                    store.downloadsSnapshot()
                        .filter { it.id in dlSelected && it.media == dlScreenMedia }
                        .forEach { rec ->
                            Downloads.deleteEngineStaging(context, store, rec)
                            Downloads.clearJobBundle(store, rec.id)
                            store.removeDownload(rec.id)
                        }
                    dlSelected = emptySet()
                    dlSelecting = false
                }) { Text("Delete records (${dlSelected.size})") }
            }
            TextButton(onClick = {
                fileSort = when (fileSort) {
                    "newest" -> "oldest"
                    "oldest" -> "title"
                    else -> "newest"
                }
            }) {
                Text(
                    when (fileSort) {
                        "oldest" -> "Sort: oldest"
                        "title" -> "Sort: title"
                        else -> "Sort: newest"
                    }
                )
            }
            Spacer(Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            TextButton(
                enabled = !refreshing,
                onClick = {
                    refreshScope.launch {
                        refreshing = true
                        withContext(Dispatchers.IO) {
                            Downloads.refresh(context, store)
                        }
                        refreshing = false
                    }
                }
            ) { Text(if (refreshing) "Refreshing…" else "Refresh status") }
            // Audit P-p4: records only — files stay.
            if (store.downloadsSnapshot().any { !it.media && it.status == "complete" }) {
                TextButton(onClick = { Downloads.clearCompletedRecords(store, false) }) {
                    Text("Clear completed")
                }
            }
            // Audit D-m12: remove files pruned records left behind.
            if (Downloads.prunedFiles(store).isNotEmpty()) {
                TextButton(onClick = { Downloads.clearPrunedFiles(context, store) }) {
                    Text("Clear leftover files")
                }
            }
        }
        // Download notifications are off (2026-10-26): worker
        // downloads announce completion as an ordinary
        // notification, which needs the permission — the
        // row below is the recovery path for a denial.
        // notifTick re-checks when returning from Settings.
        var notifTick by remember { mutableStateOf(0) }
        val notifLifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(notifLifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    notifTick++
                }
            }
            notifLifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                notifLifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        val notifOff = remember(notifTick) {
            android.os.Build.VERSION.SDK_INT >= 33 &&
                !downloadNotificationsEnabled(context)
        }
        if (notifOff) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Download notifications are off",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { openAppNotificationSettings(context) }) {
                    Text("Enable")
                }
            }
        }
        // The Downloads page is for ordinary file and
        // image downloads only; media downloads live
        // in the Offline library.
        val fileDownloads = store.downloadsSnapshot().filter { rec ->
            !rec.media && !rec.isPrivate &&
                (downloadQuery.isBlank() ||
                    rec.title.contains(downloadQuery, ignoreCase = true) ||
                    rec.fileName.contains(downloadQuery, ignoreCase = true))
        }.let { list ->
            // Beta.64 #21: sort file downloads.
            when (fileSort) {
                "oldest" -> list.sortedBy { it.addedAt }
                "title" -> list.sortedBy { it.title.lowercase() }
                else -> list.sortedByDescending { it.addedAt }
            }
        }
        // Beta.64 #20: private file downloads, shown only
        // behind the confirm gate below.
        val privateDownloads = store.downloadsSnapshot().filter { rec ->
            !rec.media && rec.isPrivate &&
                (downloadQuery.isBlank() ||
                    rec.title.contains(downloadQuery, ignoreCase = true) ||
                    rec.fileName.contains(downloadQuery, ignoreCase = true))
        }.let { list ->
            // Beta.64 #21: same order as the file list.
            when (fileSort) {
                "oldest" -> list.sortedBy { it.addedAt }
                "title" -> list.sortedBy { it.title.lowercase() }
                else -> list.sortedByDescending { it.addedAt }
            }
        }
        if (fileDownloads.isEmpty() && privateDownloads.isEmpty()) {
            PanelCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    if (downloadQuery.isNotBlank()) "No downloads match your search." else "Nothing downloaded yet. Files and images you download from the browser will appear here.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        if (fileDownloads.isNotEmpty() || privateDownloads.isNotEmpty()) {
            LazyColumn {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        GroupTitle("Downloads")
                        SettingsCard {
                            fileDownloads.forEachIndexed { i, rec ->
                                if (i > 0) InsetDivider()
                                val source = Downloads.localSource(context, store, rec)
                                val active = rec.status == "queued" || rec.status == "downloading" ||
                                    rec.status == "paused"
                                KitGroupedRow(
                                leading = {
                                    if (dlSelecting) {
                                        Checkbox(
                                            checked = rec.id in dlSelected,
                                            onCheckedChange = {
                                                dlSelected =
                                                    if (rec.id in dlSelected) dlSelected - rec.id
                                                    else dlSelected + rec.id
                                            }
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme
                                                .onSurfaceVariant
                                        )
                                    }
                                },
                                title = {
                        Text(
                            rec.title, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                                supporting = {
                        Column {
                            // Audit P-m13: a download still in progress
                            // after a failed attempt is a retry in
                            // flight, not a fresh download.
                            val dlErrFirst = store.setting(
                                "dlerror:${rec.id}", ""
                            )
                            val statusText = when {
                                Downloads.isWaitingForWifi(context, store) &&
                                    (rec.status == "downloading" || rec.status == "queued") ->
                                    "Waiting for Wi-Fi"
                                rec.status == "downloading" && dlErrFirst.isNotBlank() ->
                                    stringResource(R.string.download_status_retrying)
                                else -> rec.status
                            }
                            Text(
                                "${statusText}${if (active && rec.progress >= 0) " • ${rec.progress}%" else ""} • ${rec.fileName}",
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Beta.64 #30: size / speed / ETA.
                            Downloads.progressDetailLine(rec)?.let { detail ->
                                Text(
                                    detail,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val dlErr = store.setting(
                                "dlerror:${rec.id}", ""
                            )
                            if (dlErr.isNotBlank() &&
                                (rec.status == "failed" || active)
                            ) {
                                Text(
                                    dlErr,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography
                                        .bodySmall,
                                    color = MaterialTheme.colorScheme
                                        .error
                                )
                            }
                            // Audit D-p4: a downgraded job kept
                            // going under the system downloader
                            // — say so instead of implying the
                            // segmented gate still applies.
                            if (store.setting("dldowngrade:${rec.id}", "") == "1") {
                                Text(
                                    "Continued via system downloader",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography
                                        .bodySmall,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                )
                            }
                            if (active) {
                                LinearProgressIndicator(
                                    progress = { if (rec.progress >= 0) rec.progress / 100f else 0f },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                )
                            }
                        }
                    },
                                trailing = {
                        Row {
                            if (rec.status == "downloading" &&
                                Downloads.isResumableEngine(Downloads.engineFor(store, rec))
                            ) {
                                IconButton(onClick = { Downloads.pause(context, store, rec) }) {
                                    Icon(Icons.Filled.Pause, contentDescription = "Pause download")
                                }
                            }
                            if (rec.status == "paused" &&
                                Downloads.isResumableEngine(Downloads.engineFor(store, rec))
                            ) {
                                IconButton(onClick = { Downloads.resume(context, store, rec) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Resume download")
                                }
                            }
                            if (active) {
                                IconButton(onClick = { Downloads.cancel(context, store, rec) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Cancel download")
                                }
                            }
                            if (rec.status == "complete" && source != null) {
                                IconButton(onClick = {
                                    val intent = Downloads.openIntent(context, store, rec)
                                    val opened = intent != null && runCatching {
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }.isSuccess
                                    if (!opened) {
                                        Toast.makeText(
                                            context, "No app can open this file",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }) {
                                    Icon(Icons.Filled.OpenInNew, contentDescription = "Open download")
                                }
                            }
                            if (rec.status == "complete" && source != null) {
                                IconButton(onClick = {
                                    Downloads.shareIntent(context, store, rec)
                                        ?.let { intent ->
                                            context.startActivity(
                                                Intent.createChooser(
                                                    intent, "Share download"
                                                ).addFlags(
                                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                                )
                                            )
                                        }
                                }) {
                                    Icon(
                                        Icons.Filled.Share,
                                        contentDescription = "Share download"
                                    )
                                }
                            }
                            if (rec.status == "complete" && source != null &&
                                rec.fileName.endsWith(".zip", ignoreCase = true)
                            ) {
                                IconButton(onClick = { importDownloadZip(rec) }) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Import to games"
                                    )
                                }
                            }
                            if (rec.status == "failed") {
                                IconButton(onClick = {
                                    // Audit D-M1: retry re-enqueues
                                    // through the saved job bundle —
                                    // merges re-run with both side
                                    // URLs, location and rename are
                                    // preserved. Only a missing
                                    // bundle falls back to
                                    // delete-and-restart.
                                    askDownloadNotif()
                                    if (Downloads.retry(context, store, rec)) {
                                        Toast.makeText(
                                            context, "Download restarted",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.download_delete_failed),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }) {
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = "Retry download"
                                    )
                                }
                            }
                            IconButton(onClick = {
                                // Audit F12: only drop the record when
                                // both removals actually worked.
                                val dmOk = if (rec.dmId >= 0) {
                                    runCatching {
                                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                        dm.remove(rec.dmId)
                                    }.isSuccess
                                } else true
                                val wiped = Downloads.deleteLocal(context, store, rec)
                                if (dmOk && wiped) {
                                    Downloads.clearJobBundle(store, rec.id)
                                    store.removeDownload(rec.id)
                                Downloads.deleteEngineStaging(context, store, rec)
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.download_delete_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete download")
                            }
                        }
                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
                if (privateDownloads.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            GroupTitle("Private downloads")
                            if (!store.privateDownloadsGate.unlocked) {
                                // Beta.64 #20: private content
                                // stays hidden until the user
                                // explicitly confirms, this
                                // session only.
                                PanelCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Private downloads are hidden",
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { showPrivateConfirm = true }) {
                                            Text("Show")
                                        }
                                    }
                                }
                            } else {
                                SettingsCard {
                                    privateDownloads.forEachIndexed { i, rec ->
                                        if (i > 0) InsetDivider()
                                        val source = Downloads.localSource(context, store, rec)
                                        val active = rec.status == "queued" || rec.status == "downloading" ||
                                            rec.status == "paused"
                                        KitGroupedRow(
                                            leading = {
                                                Icon(
                                                    Icons.Filled.Download,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme
                                                        .onSurfaceVariant
                                                )
                                            },
                                            title = {
                                                Text(
                                                    rec.title, maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            },
                                            supporting = {
                                                Column {
                                                    Text(
                                                        "${rec.status}${if (active && rec.progress >= 0) " • ${rec.progress}%" else ""} • ${rec.fileName}",
                                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    // Beta.64 #30: size / speed / ETA.
                                                    Downloads.progressDetailLine(rec)?.let { detail ->
                                                        Text(
                                                            detail,
                                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    if (active) {
                                                        LinearProgressIndicator(
                                                            progress = { if (rec.progress >= 0) rec.progress / 100f else 0f },
                                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            trailing = {
                                                Row {
                                                    if (rec.status == "complete" && source != null) {
                                                        IconButton(onClick = {
                                                            val intent = Downloads.openIntent(context, store, rec)
                                                            val opened = intent != null && runCatching {
                                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                context.startActivity(intent)
                                                            }.isSuccess
                                                            if (!opened) {
                                                                Toast.makeText(
                                                                    context, "No app can open this file",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        }) {
                                                            Icon(Icons.Filled.OpenInNew, contentDescription = "Open download")
                                                        }
                                                    }
                                                    IconButton(onClick = {
                                                        // Audit F12: only drop the record when
                                                        // both removals actually worked.
                                                        val dmOk = if (rec.dmId >= 0) {
                                                            runCatching {
                                                                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                                                dm.remove(rec.dmId)
                                                            }.isSuccess
                                                        } else true
                                                        val wiped = Downloads.deleteLocal(context, store, rec)
                                                        if (dmOk && wiped) {
                                                            Downloads.clearJobBundle(store, rec.id)
                                                            store.removeDownload(rec.id)
                                                            Downloads.deleteEngineStaging(context, store, rec)
                                                        } else {
                                                            Toast.makeText(
                                                                context,
                                                                context.getString(R.string.download_delete_failed),
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        }
                                                    }) {
                                                        Icon(Icons.Filled.Delete, contentDescription = "Delete download")
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                                TextButton(onClick = { store.privateDownloadsGate.hide() }) {
                                    Text("Hide private downloads")
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
        if (showPrivateConfirm) {
            FrostDialog(
                onDismissRequest = { showPrivateConfirm = false },
                title = { Text("Show private downloads?") },
                text = {
                    Text("Private downloads are hidden by default. Showing them lists files downloaded in private tabs, until you hide them again.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        store.privateDownloadsGate.confirm()
                        showPrivateConfirm = false
                    }) { Text("Show") }
                },
                dismissButton = {
                    TextButton(onClick = { showPrivateConfirm = false }) { Text("Cancel") }
                }
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Audit P-m3: Favorites manager — search, delete, open.
 * Favorites are the starred sites (store.favourites).
 */
@Composable
fun FavoritesScreen(store: AppStore, onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val rev = store.revision
    var query by remember { mutableStateOf("") }
    SubScreen(title = "Favorites", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search favorites") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
            val shown = store.favourites.filter { f ->
                query.isBlank() ||
                    f.title.contains(query, ignoreCase = true) ||
                    f.url.contains(query, ignoreCase = true)
            }
            if (shown.isEmpty()) {
                Text(
                    "No favorites yet. Star a page from the menu to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(shown, key = { it.id }) { f ->
                        KitGroupedRow(
                            onClick = { onOpenUrl(f.url) },
                            leading = {
                                val host = runCatching {
                                    android.net.Uri.parse(f.url).host
                                }.getOrNull()
                                val fav = host?.let {
                                    remember(it) { store.loadFavicon(it) }
                                }
                                if (fav != null) {
                                    Image(
                                        bitmap = fav.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailing = {
                                IconButton(onClick = {
                                    store.toggleFavourite(f.title, f.url)
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove favorite")
                                }
                            },
                            title = { Text(f.title.ifBlank { f.url }) },
                            supporting = { Text(f.url) }
                        )
                        InsetDivider()
                    }
                }
            }
        }
    }
}

/**
 * Audit P-m23: Reading List — unread/read states,
 * mark read/unread, delete, open.
 */
/**
 * Beta.64 #40: save an offline snapshot (an .mht archive)
 * of a reading-list item with a headless WebView: the page
 * loads off-screen, and on finish the archive is written,
 * added to the Offline library, and linked to the item.
 * User-initiated per item, like opening the page. A 30s
 * timeout fails honestly instead of hanging. Runs on the
 * UI thread (WebView requirement); [onDone] fires on
 * success or failure so the row can re-enable.
 */
private fun saveReadingItemOffline(
    context: Context, store: AppStore, item: ReadingItem,
    onDone: () -> Unit
) {
    val webView = WebView(context)
    var done = false
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    lateinit var timeout: Runnable
    fun finish(ok: Boolean, message: String) {
        if (done) return
        done = true
        handler.removeCallbacks(timeout)
        runCatching { webView.stopLoading() }
        runCatching { webView.destroy() }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onDone()
    }
    timeout = Runnable { finish(false, "Couldn't save that page") }
    handler.postDelayed(timeout, 30_000)
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String) {
            val dir = java.io.File(context.filesDir, "offline")
                .apply { mkdirs() }
            val file = java.io.File(
                dir, "${System.currentTimeMillis()}.mht"
            )
            view.saveWebArchive(file.absolutePath, false) { savedPath ->
                if (savedPath != null) {
                    store.addOfflinePage(item.title, item.url, file.absolutePath)
                    store.setReadingItemOffline(item.id, file.absolutePath)
                    finish(true, "Page saved offline")
                } else {
                    finish(false, "Couldn't save that page")
                }
            }
        }

        override fun onReceivedError(
            view: WebView, request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) {
                finish(false, "Couldn't load that page")
            }
        }
    }
    webView.loadUrl(item.url)
}

@Composable
fun ReadingListScreen(store: AppStore, onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val rev = store.revision
    val context = LocalContext.current
    var showRead by remember { mutableStateOf(false) }
    // Beta.64 #40: search, multi-select, and the
    // in-flight offline snapshots.
    var readingQuery by remember { mutableStateOf("") }
    var readingSelecting by remember { mutableStateOf(false) }
    var readingSelected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var savingOffline by remember { mutableStateOf<Set<Long>>(emptySet()) }
    SubScreen(title = "Reading list", onBack = onBack) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                TextButton(onClick = { showRead = false }) {
                    Text(if (!showRead) "Unread •" else "Unread")
                }
                TextButton(onClick = { showRead = true }) {
                    Text(if (showRead) "Read •" else "Read")
                }
            }
            OutlinedTextField(
                value = readingQuery,
                onValueChange = { readingQuery = it },
                label = { Text("Search reading list") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    readingSelecting = !readingSelecting
                    if (!readingSelecting) readingSelected = emptySet()
                }) { Text(if (readingSelecting) "Done" else "Select") }
                if (readingSelected.isNotEmpty()) {
                    TextButton(onClick = {
                        store.removeReadingItems(readingSelected)
                        readingSelected = emptySet()
                        readingSelecting = false
                    }) { Text("Delete (${readingSelected.size})") }
                    TextButton(onClick = {
                        store.setReadingItemsRead(readingSelected, !showRead)
                        readingSelected = emptySet()
                        readingSelecting = false
                    }) { Text(if (showRead) "Mark unread (${readingSelected.size})" else "Mark read (${readingSelected.size})") }
                }
                Spacer(Modifier.weight(1f))
            }
            val shown = store.readingItems().filter { it.read == showRead &&
                (readingQuery.isBlank() ||
                    it.title.contains(readingQuery, ignoreCase = true) ||
                    it.url.contains(readingQuery, ignoreCase = true))
            }
            if (shown.isEmpty()) {
                Text(
                    if (showRead) "Nothing marked read yet."
                    else "Nothing saved yet. Use \"Read later\" in the menu to save a page.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(shown, key = { it.id }) { item ->
                        KitGroupedRow(
                            onClick = {
                                if (readingSelecting) {
                                    readingSelected =
                                        if (item.id in readingSelected) readingSelected - item.id
                                        else readingSelected + item.id
                                } else onOpenUrl(item.url)
                            },
                            leading = {
                                if (readingSelecting) {
                                    Checkbox(
                                        checked = item.id in readingSelected,
                                        onCheckedChange = {
                                            readingSelected =
                                                if (item.id in readingSelected) readingSelected - item.id
                                                else readingSelected + item.id
                                        }
                                    )
                                }
                            },
                            trailing = {
                                Row {
                                    // Beta.64 #40: an offline
                                    // snapshot (saved page) per item.
                                    IconButton(
                                        enabled = item.id !in savingOffline,
                                        onClick = {
                                            savingOffline += item.id
                                            saveReadingItemOffline(
                                                context, store, item
                                            ) { savingOffline -= item.id }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.Download,
                                            contentDescription = "Save offline"
                                        )
                                    }
                                    TextButton(onClick = {
                                        store.setReadingItemRead(item.id, !item.read)
                                    }) {
                                        Text(if (item.read) "Unread" else "Read")
                                    }
                                    IconButton(onClick = {
                                        store.removeReadingItem(item.id)
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Remove")
                                    }
                                }
                            },
                            title = { Text(item.title.ifBlank { item.url }) },
                            supporting = {
                                Text(
                                    item.url +
                                        if (item.offlinePath.isNotBlank()) " • Saved offline"
                                        else ""
                                )
                            }
                        )
                        InsetDivider()
                    }
                }
            }
        }
    }
}
