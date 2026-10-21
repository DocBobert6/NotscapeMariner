package net.notscape.browser.ui

import android.app.DownloadManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import net.notscape.browser.media.Downloads
import net.notscape.browser.media.PlayerActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad)) { content() }
    }
}

@Composable
fun BookmarksScreen(store: AppStore, onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val rev = store.revision
    SubScreen("Bookmarks", onBack) {
        if (store.bookmarks.isEmpty()) {
            Text("No bookmarks yet. Star a page to add one.", modifier = Modifier.padding(16.dp))
        }
        LazyColumn {
            items(store.bookmarks.toList(), key = { it.id }) { b ->
                ListItem(
                    headlineContent = { Text(b.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(b.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        IconButton(onClick = { store.toggleBookmark(b.title, b.url) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove")
                        }
                    },
                    modifier = Modifier.clickable { onOpenUrl(b.url) }
                )
            }
        }
    }
}

@Composable
fun HistoryScreen(store: AppStore, onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    val rev = store.revision
    SubScreen("History", onBack) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { store.clearHistory() }) { Text("Clear history") }
        }
        if (store.history.isEmpty()) {
            Text("No history yet.", modifier = Modifier.padding(16.dp))
        }
        LazyColumn {
            items(store.history.toList(), key = { it.id }) { h ->
                ListItem(
                    headlineContent = { Text(h.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(h.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.clickable { onOpenUrl(h.url) }
                )
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
            Text(
                "No playlists yet. Use “Find media on page” in the browser to add videos.",
                modifier = Modifier.padding(16.dp)
            )
        }
        LazyColumn {
            store.playlists.toList().forEach { playlist ->
                item(key = "pl-${playlist.id}") {
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { Text("${playlist.items.size} items") },
                        trailingContent = {
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
                        }
                    )
                }
                items(playlist.items.toList(), key = { "it-${it.id}" }) { item ->
                    ListItem(
                        headlineContent = {
                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
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
                        modifier = Modifier.padding(start = 24.dp)
                    )
                }
            }
        }
    }
    if (showCreate) {
        AlertDialog(
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
    LaunchedEffect(Unit) {
        Downloads.refresh(context, store)
    }
    SubScreen("Offline library", onBack) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { Downloads.refresh(context, store) }) { Text("Refresh status") }
        }
        if (store.downloads.isEmpty()) {
            Text(
                "Nothing downloaded yet. Use “Find media on page” in the browser and tap download.",
                modifier = Modifier.padding(16.dp)
            )
        }
        LazyColumn {
            items(store.downloads.toList(), key = { it.id }) { rec ->
                val file = Downloads.localFile(context, rec)
                ListItem(
                    headlineContent = { Text(rec.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text("${rec.status} • ${rec.fileName}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = {
                        Row {
                            if (rec.status == "complete" && file.exists()) {
                                IconButton(onClick = {
                                    PlayerActivity.launch(context, file.absolutePath, rec.title)
                                }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play offline")
                                }
                            }
                            IconButton(onClick = {
                                if (rec.dmId >= 0) {
                                    runCatching {
                                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                                        dm.remove(rec.dmId)
                                    }
                                }
                                runCatching { file.delete() }
                                store.removeDownload(rec.id)
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete download")
                            }
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
