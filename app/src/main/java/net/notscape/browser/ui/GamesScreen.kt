package net.notscape.browser.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Slider
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
import net.notscape.browser.emu.CoreOptions
import net.notscape.browser.emu.CoreStore
import net.notscape.browser.emu.GameActivity
import net.notscape.browser.emu.GameEntry
import net.notscape.browser.emu.GameLayout
import net.notscape.browser.emu.GameLibrary
import net.notscape.browser.emu.SaveStates

/**
 * The Games library (Phase J; J7 form): the home screen
 * is one button per console, and each console's page
 * holds its games, its Import, its touch controls
 * (editor, the shared opacity slider, shared haptics,
 * per-console reset), its core settings, and its save
 * manager. Bring your own ROMs; nothing is downloaded
 * but art, and removing a game deletes only the app's
 * copy, its save and its art - never the user's
 * original file.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    var console by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editConsole by remember { mutableStateOf("gb") }
    var renameTarget by remember { mutableStateOf<GameEntry?>(null) }
    var removeTarget by remember { mutableStateOf<GameEntry?>(null) }
    var importConsole by remember { mutableStateOf<String?>(null) }
    var managerTarget by remember { mutableStateOf<GameEntry?>(null) }

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
        val target = importConsole ?: return@rememberLauncherForActivityResult
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
                    if (system != target) {
                        error(
                            "That's a ${systemLabel(system)} ROM - " +
                                "import it from the " +
                                "${systemLabel(system)} page."
                        )
                    }
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

    val openConsole = console
    if (openConsole != null) {
        ConsolePage(
            store = store,
            console = openConsole,
            artTick = artTick,
            games = games.filter { it.system == openConsole },
            onBack = { console = null },
            onImport = {
                importConsole = openConsole
                picker.launch(arrayOf("*/*"))
            },
            onPlay = { launch(it) },
            onRename = { renameTarget = it },
            onFetchArt = { fetchArt(it, true, true) },
            onRemove = { removeTarget = it },
            onEditLayout = {
                editConsole = openConsole
                showEditor = true
            },
            onManageSaves = { managerTarget = it }
        )
    } else {
        SubScreen(title = "Games", onBack = onBack) {
            val last = games.filter { it.lastPlayedAt > 0 }
                .maxByOrNull { it.lastPlayedAt }
            LazyColumn(
                modifier = Modifier.fillMaxSize()
                    .padding(horizontal = 14.dp)
            ) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Bring your own ROMs - imports are " +
                            "copied into the app's storage, " +
                            "and only box art is ever fetched.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme
                            .onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (last != null) {
                    item {
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
                        Spacer(Modifier.height(12.dp))
                    }
                }
                item {
                    SettingsCard {
                        Text(
                            "Consoles",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        for (c in GameLayout.CONSOLES) {
                            val count = games.count {
                                it.system == c
                            }
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { console = c }
                                    .padding(vertical = 10.dp)
                            ) {
                                ConsoleGlyph(
                                    c,
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    systemLabel(c),
                                    style = MaterialTheme.typography
                                        .bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    if (count == 0) {
                                        "No games yet"
                                    } else if (count == 1) {
                                        "1 game"
                                    } else {
                                        "$count games"
                                    },
                                    style = MaterialTheme.typography
                                        .bodyMedium,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    ">",
                                    style = MaterialTheme.typography
                                        .bodyLarge,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    managerTarget?.let { game ->
        SaveStateDialog(
            gameId = game.id,
            showSave = false,
            batteryExists = File(
                CoreStore.saveDir(context),
                game.romFile(context).nameWithoutExtension + ".srm"
            ).exists(),
            onDeleteBattery = {
                runCatching {
                    File(
                        CoreStore.saveDir(context),
                        game.romFile(context).nameWithoutExtension +
                            ".srm"
                    ).delete()
                }
            },
            onLoad = { slot -> launch(game, slot) },
            onDismiss = { managerTarget = null }
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

/**
 * One console's page (J7): its games, its Import, its
 * touch controls (the shared opacity slider and shared
 * haptics live here, surfaced on every page), its core
 * settings, and its save manager. Layouts stay per
 * console and per orientation; core settings apply
 * from the next game opened.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConsolePage(
    store: AppStore,
    console: String,
    artTick: Int,
    games: List<GameEntry>,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onPlay: (GameEntry) -> Unit,
    onRename: (GameEntry) -> Unit,
    onFetchArt: (GameEntry) -> Unit,
    onRemove: (GameEntry) -> Unit,
    onEditLayout: () -> Unit,
    onManageSaves: (GameEntry) -> Unit
) {
    val context = LocalContext.current
    SubScreen(title = systemLabel(console), onBack = onBack) {
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
                    Button(
                        onClick = onImport,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Import a ${systemLabel(console)} ROM") }
                    if (games.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No games here yet - import one " +
                                "above. Imports are copied into " +
                                "the app's storage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            items(games, key = { it.id }) { game ->
                GameTile(
                    game = game,
                    artTick = artTick,
                    onPlay = { onPlay(game) },
                    onRename = { onRename(game) },
                    onFetchArt = { onFetchArt(game) },
                    onRemove = { onRemove(game) }
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
                            "Haptic strength (shared)",
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
                        var alpha by remember {
                            mutableStateOf(
                                store.setting(
                                    "gameControlsAlpha", "100"
                                ).toIntOrNull() ?: 100
                            )
                        }
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                "Control opacity (shared)",
                                style = MaterialTheme.typography
                                    .bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "$alpha%",
                                style = MaterialTheme.typography
                                    .bodyMedium,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        }
                        Slider(
                            value = alpha.toFloat(),
                            onValueChange = {
                                alpha = it.toInt().coerceIn(0, 100)
                            },
                            valueRange = 0f..100f,
                            onValueChangeFinished = {
                                store.putSetting(
                                    "gameControlsAlpha",
                                    alpha.toString()
                                )
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = onEditLayout,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Edit touch layout") }
                        TextButton(
                            onClick = {
                                GameLayout.reset(store, console, true)
                                GameLayout.reset(store, console, false)
                                Toast.makeText(
                                    context,
                                    "${systemLabel(console)} layout reset",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Reset this console's layout") }
                        Text(
                            "Layouts are per console and per " +
                                "orientation, and apply from the " +
                                "next game you open. At 0% the " +
                                "buttons are invisible but still " +
                                "touchable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    SettingsCard {
                        Text(
                            "Core settings",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        val options = CoreOptions.forConsole(console)
                        if (options.isEmpty()) {
                            Text(
                                "No core settings for this console.",
                                style = MaterialTheme.typography
                                    .bodyMedium,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        } else {
                            for (option in options) {
                                CoreOptionRow(
                                    store = store,
                                    console = console,
                                    option = option
                                )
                            }
                        }
                        Text(
                            "Core settings apply from the next " +
                                "game you open.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    SettingsCard {
                        Text(
                            "Save manager",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        if (games.isEmpty()) {
                            Text(
                                "No games here yet, so nothing " +
                                    "to manage.",
                                style = MaterialTheme.typography
                                    .bodyMedium,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        } else {
                            run {
                                for (game in games) {
                                    val battery = File(
                                        CoreStore.saveDir(context),
                                        game.romFile(context)
                                            .nameWithoutExtension + ".srm"
                                    ).exists()
                                    val states = listOf(
                                        SaveStates.AUTO_SLOT, 0, 1, 2, 3
                                    ).count {
                                        SaveStates.slotInfo(
                                            context, game.id, it
                                        ).exists
                                    }
                                    Row(
                                        verticalAlignment = Alignment
                                            .CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                game.title,
                                                style = MaterialTheme
                                                    .typography.bodyLarge,
                                                maxLines = 1,
                                                overflow = TextOverflow
                                                    .Ellipsis
                                            )
                                            Text(
                                                (if (battery) {
                                                    "Battery save - "
                                                } else {
                                                    "No battery save - "
                                                }) + "$states of 5 states",
                                                style = MaterialTheme
                                                    .typography.bodySmall,
                                                color = MaterialTheme
                                                    .colorScheme
                                                    .onSurfaceVariant
                                            )
                                        }
                                        TextButton(onClick = {
                                            onManageSaves(game)
                                        }) { Text("Manage") }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

/** One core option as a label plus a value dropdown. */
@Composable
private fun CoreOptionRow(
    store: AppStore,
    console: String,
    option: net.notscape.browser.emu.CoreOption
) {
    var value by remember(console, option.key) {
        mutableStateOf(CoreOptions.storedValue(store, console, option))
    }
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            option.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Box {
            TextButton(onClick = { menuOpen = true }) {
                Text(option.display(value))
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                for (candidate in option.values) {
                    DropdownMenuItem(
                        text = { Text(option.display(candidate)) },
                        onClick = {
                            value = candidate
                            CoreOptions.setValue(
                                store, console, option.key,
                                candidate
                            )
                            menuOpen = false
                        }
                    )
                }
            }
        }
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
