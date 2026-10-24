package net.notscape.browser.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.notscape.browser.data.AppStore
import net.notscape.browser.emu.BoxArt
import net.notscape.browser.emu.CoreStore
import net.notscape.browser.emu.GameActivity
import net.notscape.browser.emu.SaveStates
import net.notscape.browser.emu.GameLayout
import net.notscape.browser.emu.GameEntry
import net.notscape.browser.emu.GameLibrary

/**
 * The Games library (Phase J, J2): imported games as
 * tiles with box art (the libretro thumbnail server,
 * cached on import; title-screen art and a styled
 * placeholder behind it), a Continue row for the last
 * game played, and the controls workshop - haptic
 * strength and the touch-layout editor. Bring your own
 * ROMs; nothing is downloaded but art, and removing a
 * game deletes only the app's copy, its save and its
 * art - never the user's original file.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun GamesScreen(store: AppStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var games by remember {
        mutableStateOf(
            run {
                GameLibrary.migrateLegacy(context, store)
                GameLibrary.load(store)
            }
        )
    }
    var artTick by remember { mutableStateOf(0) }
    var showEditor by remember { mutableStateOf(false) }
    var editConsole by remember { mutableStateOf("gb") }
    var resetAllConfirm by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<GameEntry?>(null) }
    var removeTarget by remember { mutableStateOf<GameEntry?>(null) }

    fun refresh() {
        games = GameLibrary.load(store)
    }
    fun launch(
        game: GameEntry,
        loadSlot: Int = SaveStates.NO_SLOT
    ) {
        if (!GameLibrary.isPlayable(game.system)) {
            Toast.makeText(
                context,
                "That core is not in this build",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        context.startActivity(
            Intent(context, GameActivity::class.java)
                .putExtra(
                    GameActivity.EXTRA_ROM_PATH,
                    game.romFile(context).absolutePath
                )
                .putExtra(GameActivity.EXTRA_GAME_ID, game.id)
                .putExtra(
                    GameActivity.EXTRA_LOAD_SLOT, loadSlot
                )
        )
    }
    fun fetchArt(game: GameEntry, force: Boolean, announce: Boolean) {
        scope.launch {
            val (box, title) = withContext(Dispatchers.IO) {
                BoxArt.fetch(context, game, force)
            }
            artTick++
            if (announce) {
                Toast.makeText(
                    context,
                    when {
                        box -> "Box art saved"
                        title -> "No box art - using the title screen"
                        else -> "No art found under that name"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    var displayName = "game"
                    context.contentResolver.query(
                        uri, null, null, null, null
                    )?.use { cursor ->
                        val idx = cursor.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME
                        )
                        if (idx >= 0 && cursor.moveToFirst()) {
                            displayName =
                                cursor.getString(idx) ?: displayName
                        }
                    }
                    val ext = displayName
                        .substringAfterLast('.', "")
                    val system = GameLibrary.systemForExtension(ext)
                        ?: error("That file type isn't a supported ROM")
                    val dir = File(CoreStore.romDir(context), system)
                        .apply { mkdirs() }
                    val dest = File(
                        dir, GameLibrary.safeFileName(displayName)
                    )
                    context.contentResolver.openInputStream(uri)
                        ?.use { input ->
                            dest.outputStream().use { input.copyTo(it) }
                        } ?: error("Couldn't read that file")
                    GameLibrary.add(
                        store,
                        title = displayName.substringBeforeLast('.'),
                        system = system,
                        fileName = dest.name
                    )
                }
            }
            result.onSuccess { entry ->
                refresh()
                fetchArt(entry, force = false, announce = false)
                if (GameLibrary.isPlayable(entry.system)) {
                    launch(entry)
                }
            }.onFailure { e ->
                Toast.makeText(
                    context,
                    e.message ?: "Couldn't import that ROM",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    if (showEditor) {
        GameLayoutEditor(
            store = store,
            console = editConsole,
            consoleLabel = systemLabel(editConsole),
            onDone = { showEditor = false }
        )
        return
    }

    SubScreen(title = "Games", onBack = onBack) {
        val last = games.filter { it.lastPlayedAt > 0 }
            .maxByOrNull { it.lastPlayedAt }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            modifier = Modifier.fillMaxSize()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Bring your own ROMs - imports are " +
                            "copied into the app's storage, " +
                            "and only box art is ever fetched.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme
                            .onSurfaceVariant
                    )
                    if (last != null) {
                        Spacer(Modifier.height(12.dp))
                        SettingsCard {
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        last.title,
                                        style = MaterialTheme
                                            .typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Continue - played " +
                                            DateFormat
                                                .getDateInstance()
                                                .format(
                                                    Date(last.lastPlayedAt)
                                                ),
                                        style = MaterialTheme
                                            .typography.bodySmall,
                                        color = MaterialTheme
                                            .colorScheme.onSurfaceVariant
                                    )
                                }
                                val hasAuto = SaveStates.autoExists(
                                    context, last.id
                                )
                                Button(onClick = {
                                    launch(
                                        last,
                                        if (hasAuto) {
                                            SaveStates.AUTO_SLOT
                                        } else {
                                            SaveStates.NO_SLOT
                                        }
                                    )
                                }) {
                                    Text(
                                        if (hasAuto) {
                                            "Continue"
                                        } else {
                                            "Play"
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Import a ROM") }
                    Spacer(Modifier.height(4.dp))
                }
            }
            items(games, key = { it.id }) { game ->
                GameTile(
                    game = game,
                    artTick = artTick,
                    onPlay = { launch(game) },
                    onRename = { renameTarget = game },
                    onFetchArt = { fetchArt(game, true, true) },
                    onRemove = { removeTarget = game }
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    SettingsCard {
                        Text(
                            "Touch controls",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Haptic strength",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            var level by remember {
                                mutableStateOf(
                                    store.setting("gameHaptics", "Light")
                                )
                            }
                            for (option in listOf(
                                "Off", "Light", "Medium", "Strong"
                            )) {
                                FilterChip(
                                    selected = level == option,
                                    onClick = {
                                        level = option
                                        store.putSetting(
                                            "gameHaptics", option
                                        )
                                    },
                                    label = { Text(option) }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Control opacity",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            var alpha by remember {
                                mutableStateOf(
                                    store.setting(
                                        "gameControlsAlpha", "100"
                                    )
                                )
                            }
                            for (option in listOf(
                                "100", "85", "70", "55"
                            )) {
                                FilterChip(
                                    selected = alpha == option,
                                    onClick = {
                                        alpha = option
                                        store.putSetting(
                                            "gameControlsAlpha",
                                            option
                                        )
                                    },
                                    label = { Text(option + "%") }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Layout for",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        FlowRow(
                            horizontalArrangement =
                                Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            for (c in GameLayout.CONSOLES) {
                                FilterChip(
                                    selected = editConsole == c,
                                    onClick = { editConsole = c },
                                    label = { Text(systemLabel(c)) }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { showEditor = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Edit touch layout") }
                        TextButton(
                            onClick = { resetAllConfirm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Reset all layouts") }
                        Text(
                            "Layouts are per console and per " +
                                "orientation, and apply from the " +
                                "next game you open.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (resetAllConfirm) {
        FrostDialog(
            onDismissRequest = { resetAllConfirm = false },
            title = { Text("Reset all layouts?") },
            text = {
                Text(
                    "This clears every saved control " +
                        "layout for every console, in " +
                        "both orientations."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    GameLayout.resetAll(store)
                    resetAllConfirm = false
                    Toast.makeText(
                        context, "Layouts reset", Toast.LENGTH_SHORT
                    ).show()
                }) { Text("Reset all") }
            },
            dismissButton = {
                TextButton(onClick = { resetAllConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    renameTarget?.let { game ->
        var text by remember(game.id) { mutableStateOf(game.title) }
        FrostDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename game") },
            text = {
                Column {
                    Text(
                        "The name is also how box art is looked up.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newTitle = text.trim()
                    if (newTitle.isNotBlank()) {
                        GameLibrary.rename(store, game.id, newTitle)
                        // The old name's art no longer applies.
                        runCatching {
                            BoxArt.artFile(context, game).delete()
                        }
                        runCatching {
                            BoxArt.titleFile(context, game).delete()
                        }
                        refresh()
                        fetchArt(
                            game.copy(title = newTitle),
                            force = false,
                            announce = true
                        )
                    }
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    removeTarget?.let { game ->
        FrostDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove ${game.title}?") },
            text = {
                Text(
                    "Deletes the app's copy, its battery save " +
                        "and its art. Your original file is " +
                        "untouched.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    GameLibrary.remove(context, store, game.id)
                    refresh()
                    removeTarget = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameTile(
    game: GameEntry,
    artTick: Int,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onFetchArt: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val bitmap by produceState<android.graphics.Bitmap?>(
        null, game.id, artTick
    ) {
        value = withContext(Dispatchers.IO) {
            val f = BoxArt.artFile(context, game)
                .takeIf { it.exists() }
                ?: BoxArt.titleFile(context, game)
                    .takeIf { it.exists() }
            f?.let {
                runCatching {
                    BitmapFactory.decodeFile(it.absolutePath)
                }.getOrNull()
            }
        }
    }
    Column {
        Box(
            modifier = Modifier.fillMaxWidth()
                .aspectRatio(4f / 5f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .combinedClickable(
                    onClick = onPlay,
                    onLongClick = { menuOpen = true }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap!!.asImageBitmap(),
                    contentDescription = game.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    game.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Play") },
                    onClick = { menuOpen = false; onPlay() }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menuOpen = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Fetch box art") },
                    onClick = { menuOpen = false; onFetchArt() }
                )
                DropdownMenuItem(
                    text = { Text("Remove") },
                    onClick = { menuOpen = false; onRemove() }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            game.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            systemLabel(game.system),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun systemLabel(system: String): String = when (system) {
    "gb" -> "Game Boy"
    "gbc" -> "Game Boy Color"
    "gba" -> "Game Boy Advance"
    "nes" -> "NES"
    "snes" -> "Super Nintendo"
    "genesis" -> "Genesis"
    "sms" -> "Master System"
    "gg" -> "Game Gear"
    else -> system.uppercase()
}
