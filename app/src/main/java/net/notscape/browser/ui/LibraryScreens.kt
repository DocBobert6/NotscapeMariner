package net.notscape.browser.ui

import android.app.DownloadManager
import android.content.Intent
import android.content.Context
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.notscape.browser.data.AppStore
import net.notscape.browser.data.Bookmark
import net.notscape.browser.ui.AppHeader
import net.notscape.browser.media.Downloads
import net.notscape.browser.media.PlayerActivity

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
        modifier = Modifier.clickable { onClick() }
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
    // Folder filter: null = All, "" = Unfiled, otherwise a folder name.
    var folderFilter by remember { mutableStateOf<String?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var showManageFolders by remember { mutableStateOf(false) }
    var renameFolder by remember { mutableStateOf<String?>(null) }
    var confirmDeleteFolder by remember { mutableStateOf<String?>(null) }
    var moveTarget by remember { mutableStateOf<Bookmark?>(null) }
    var bookmarkQuery by remember { mutableStateOf("") }

    SubScreen("Bookmarks", onBack) {
        OutlinedTextField(
            value = bookmarkQuery,
            onValueChange = { bookmarkQuery = it },
            label = { Text("Search bookmarks") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                KitPill(
                    label = "All",
                    selected = folderFilter == null,
                    onClick = { folderFilter = null }
                )
            }
            items(store.bookmarkFolders.toList()) { f ->
                KitPill(
                    label = f,
                    selected = folderFilter == f,
                    onClick = { folderFilter = f }
                )
            }
            item {
                KitPill(
                    label = "Unfiled",
                    selected = folderFilter == "",
                    onClick = { folderFilter = "" }
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
                                    onClick = { onOpenUrl(b.url) },
                                leading = {
                                    Icon(
                                        Icons.Filled.Public,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme
                                            .onSurfaceVariant
                                    )
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
                        store.addBookmarkFolder(name)
                        folderFilter = name.trim()
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
                    Text("Deleting a folder keeps its bookmarks — they become Unfiled.")
                    Spacer(Modifier.height(8.dp))
                    store.bookmarkFolders.toList().forEach { f ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(f, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
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
        var name by remember(old) { mutableStateOf(old) }
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
                        if (folderFilter == old) folderFilter = name.trim()
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
                    if (folderFilter == f) folderFilter = null
                    confirmDeleteFolder = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteFolder = null }) { Text("Cancel") }
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
}

@Composable
fun HistoryScreen(store: AppStore, onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val rev = store.revision
    var historyQuery by remember { mutableStateOf("") }
    SubScreen("History", onBack) {
        OutlinedTextField(
            value = historyQuery,
            onValueChange = { historyQuery = it },
            label = { Text("Search history") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { store.clearHistory() }) { Text("Clear history") }
        }
        val shownHistory = store.history.filter { h ->
            historyQuery.isBlank() ||
                h.title.contains(historyQuery, ignoreCase = true) ||
                h.url.contains(historyQuery, ignoreCase = true)
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
                                GroupTitle(day)
                                SettingsCard {
                                    entries.forEachIndexed { i, h ->
                                        if (i > 0) InsetDivider()
                                        KitGroupedRow(
                                            onClick = { onOpenUrl(h.url) },
                                        leading = {
                                            Icon(
                                                Icons.Filled.Public,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme
                                                    .onSurfaceVariant
                                            )
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
}

@Composable
fun PlaylistsScreen(store: AppStore, onBack: () -> Unit) {
    val rev = store.revision
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
                    "No playlists yet. Use “Find media on page” in the browser to add videos.",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        LazyColumn {
            store.playlists.toList().forEach { playlist ->
                item(key = "pl-${playlist.id}") {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        GroupTitle(playlist.name)
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
                                "${playlist.items.size} items",
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
                            Text(
                                item.url, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
fun LibraryScreen(store: AppStore, onBack: () -> Unit) {
    val rev = store.revision
    val context = LocalContext.current
    var downloadQuery by remember { mutableStateOf("") }
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
        OutlinedTextField(
            value = downloadQuery,
            onValueChange = { downloadQuery = it },
            label = { Text("Search downloads") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { Downloads.refresh(context, store) }) { Text("Refresh status") }
        }
        // The offline library is for media downloads only; ordinary file
        // and image downloads live in the phone's Downloads folder.
        val mediaDownloads = store.downloadsSnapshot().filter { rec ->
            rec.media &&
                (downloadQuery.isBlank() ||
                    rec.title.contains(downloadQuery, ignoreCase = true) ||
                    rec.fileName.contains(downloadQuery, ignoreCase = true))
        }
        if (mediaDownloads.isEmpty()) {
            PanelCard(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Nothing downloaded yet. Use “Find media on page” in the browser and tap download.",
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
                            IconButton(onClick = {
                                if (rec.dmId >= 0) {
                                    runCatching {
                                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                        dm.remove(rec.dmId)
                                    }
                                }
                                Downloads.deleteLocal(context, store, rec)
                                store.removeDownload(rec.id)
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
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
