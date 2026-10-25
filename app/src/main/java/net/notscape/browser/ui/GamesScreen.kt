package net.notscape.browser.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.runtime.key
import androidx.documentfile.provider.DocumentFile
import net.notscape.browser.data.AppStore
import net.notscape.browser.emu.BiosFiles
import net.notscape.browser.emu.BoxArt
import net.notscape.browser.emu.CoreOptions
import net.notscape.browser.emu.CoreStore
import net.notscape.browser.emu.CoreDownload
import net.notscape.browser.emu.GameActivity
import net.notscape.browser.emu.GameEntry
import net.notscape.browser.emu.GameLayout
import net.notscape.browser.emu.GameLibrary
import net.notscape.browser.emu.GameOrder
import net.notscape.browser.emu.SaveStates
import net.notscape.browser.emu.ShaderPresets
import net.notscape.browser.emu.StartShortcuts
import net.notscape.browser.emu.ZipRomImport

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
fun GamesScreen(
    store: AppStore,
    onBack: () -> Unit,
    initialConsole: String? = null,
    onInitialConsoleConsumed: () -> Unit = {}
) {
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
    // Bumped whenever a home group order is saved,
    // so the group sections re-read settings.
    var homeOrderTick by remember { mutableStateOf(0) }
    // System back unwinds the console page first
    // (beta.4): only when no console is open does
    // back leave Games for the browser.
    BackHandler(enabled = console != null) {
        console = null
    }
    // A start-page console pin (beta.7) lands here:
    // open that console's page, then consume.
    LaunchedEffect(initialConsole) {
        if (initialConsole != null) {
            console = initialConsole
            onInitialConsoleConsumed()
        }
    }
    var showEditor by remember { mutableStateOf(false) }
    var editConsole by remember { mutableStateOf("gb") }
    var renameTarget by remember { mutableStateOf<GameEntry?>(null) }
    var removeTarget by remember { mutableStateOf<GameEntry?>(null) }
    var importConsole by remember { mutableStateOf<String?>(null) }
    var managerTarget by remember { mutableStateOf<GameEntry?>(null) }
    var launchChoiceTarget by remember { mutableStateOf<GameEntry?>(null) }
    var customCoverTarget by remember { mutableStateOf<GameEntry?>(null) }
    // Core downloads (M2): one progress dialog shared by
    // the home Download-all button, console-page
    // Download buttons and the first-launch prompt.
    var coreJobTitle by remember { mutableStateOf("") }
    var coreJobPct by remember { mutableStateOf(-1) }
    var coreJobRunning by remember { mutableStateOf(false) }
    var coreTick by remember { mutableStateOf(0) }
    var pendingLaunch by remember {
        mutableStateOf<Triple<GameEntry, Int, String?>?>(null)
    }
    var showCorePrompt by remember { mutableStateOf(false) }

    fun refresh() {
        games = GameLibrary.load(store)
    }
    fun launch(
        game: GameEntry,
        loadSlot: Int = SaveStates.NO_SLOT,
        loadPath: String? = null
    ) {
        if (!GameLibrary.isPlayable(game.system)) {
            Toast.makeText(
                context,
                "That core is not in this build",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        // First-launch prompt (M2): offer the verified
        // source-built core once per console; a
        // downloaded core or a seen prompt launches
        // straight through.
        if (!CoreDownload.isDownloaded(context, game.system) &&
            store.setting(
                "corePromptSeen:${game.system}", "false"
            ) != "true"
        ) {
            pendingLaunch = Triple(game, loadSlot, loadPath)
            showCorePrompt = true
            return
        }
        val intent = Intent(context, GameActivity::class.java)
            .putExtra(
                GameActivity.EXTRA_ROM_PATH,
                game.romFile(context).absolutePath
            )
            .putExtra(GameActivity.EXTRA_GAME_ID, game.id)
            .putExtra(GameActivity.EXTRA_GAME_TITLE, game.title)
            .putExtra(
                GameActivity.EXTRA_LOAD_SLOT, loadSlot
            )
        if (loadPath != null) {
            intent.putExtra(GameActivity.EXTRA_LOAD_PATH, loadPath)
        }
        context.startActivity(intent)
    }
    // Download cores for [systems], driving the shared
    // progress dialog. Already-downloaded cores are
    // skipped. onDone reports whether every core
    // ended up downloaded; individual failures are
    // toasted with their refusal reason.
    fun startCoreDownload(
        systems: List<String>,
        onDone: (Boolean) -> Unit = {}
    ) {
        scope.launch {
            coreJobRunning = true
            coreJobPct = -1
            coreJobTitle = "Downloading cores..."
            val manifest = CoreDownload.fetchManifest()
                .getOrNull()
            if (manifest == null) {
                coreJobRunning = false
                Toast.makeText(
                    context,
                    "Could not reach the cores release - " +
                        "check your connection and try again.",
                    Toast.LENGTH_LONG
                ).show()
                onDone(false)
                return@launch
            }
            var allOk = true
            var index = 0
            for (system in systems) {
                index++
                val entry =
                    CoreDownload.entryFor(manifest, system)
                if (entry == null) {
                    allOk = false
                    continue
                }
                if (CoreDownload.isDownloaded(context, system)) {
                    continue
                }
                coreJobTitle =
                    "Downloading ${systemLabel(system)} core " +
                        "($index of ${systems.size})"
                val res = CoreDownload.download(
                    context, entry
                ) { pct -> coreJobPct = pct }
                if (res.isFailure) {
                    allOk = false
                    Toast.makeText(
                        context,
                        res.exceptionOrNull()?.message
                            ?: "Core download failed.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            coreJobRunning = false
            coreJobPct = -1
            coreTick++
            onDone(allOk)
        }
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
    // Zip import (beta.7): one matching ROM behaves
    // like a single import (art + launch); several
    // just refresh with a count. A zip holding no
    // ROM for this page errors the same friendly
    // way a wrong-console ROM does.
    fun importZipFile(uri: android.net.Uri, target: String) {
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val imported = mutableListOf<GameEntry>()
                    var firstOther: String? = null
                    context.contentResolver.openInputStream(uri)
                        ?.use { input ->
                            val res = ZipRomImport.forEachEntry(
                                input, target
                            ) { fileName, system, bytes ->
                                val dir = File(
                                    CoreStore.romDir(context), system
                                ).apply { mkdirs() }
                                File(dir, fileName).outputStream()
                                    .use { it.write(bytes) }
                                imported.add(
                                    GameLibrary.add(
                                        store,
                                        title = fileName
                                            .substringBeforeLast('.'),
                                        system = system,
                                        fileName = fileName
                                    )
                                )
                            }
                            firstOther = res.firstOtherSystem
                        } ?: error("Couldn't read that file")
                    imported to firstOther
                }
            }
            outcome.onSuccess { (imported, firstOther) ->
                refresh()
                if (imported.isEmpty()) {
                    Toast.makeText(
                        context,
                        if (firstOther != null) {
                            "That's a " +
                                systemLabel(firstOther) +
                                " ROM - import it from the " +
                                systemLabel(firstOther) + " page."
                        } else {
                            "No " + systemLabel(target) +
                                " ROM inside that zip"
                        },
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    imported.forEach {
                        fetchArt(it, force = false, announce = false)
                    }
                    if (imported.size == 1 &&
                        GameLibrary.isPlayable(imported[0].system)
                    ) {
                        launch(imported[0])
                    } else {
                        Toast.makeText(
                            context,
                            "Imported ${imported.size} games",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
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
    // Cover-art rescan (beta.7): fresh server art
    // for every game on the console except ones
    // wearing custom covers, with a found/missing
    // count at the end.
    fun rescanArt(console: String, onDone: () -> Unit) {
        scope.launch {
            val targets = games.filter {
                it.system == console &&
                    !BoxArt.customFile(context, it).exists()
            }
            var found = 0
            var missing = 0
            targets.forEach { game ->
                val (box, title) = withContext(Dispatchers.IO) {
                    BoxArt.fetch(context, game, true)
                }
                if (box || title) found++ else missing++
            }
            artTick++
            Toast.makeText(
                context,
                "Cover art: $found found, " +
                    "$missing still missing",
                Toast.LENGTH_LONG
            ).show()
            onDone()
        }
    }
    fun removeCustomCover(game: GameEntry) {
        runCatching {
            BoxArt.customFile(context, game).delete()
        }
        artTick++
    }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = importConsole ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // A zip (beta.7) takes its own branch:
            // one matching ROM behaves like a single
            // import, several just refresh + count.
            val isZip = withContext(Dispatchers.IO) {
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
                    displayName.substringAfterLast('.', "")
                        .equals("zip", ignoreCase = true)
                }.getOrDefault(false)
            }
            if (isZip) {
                importZipFile(uri, target)
                return@launch
            }
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
    // Custom cover picker (beta.7): the picked
    // image is scaled to at most 512px on the long
    // edge and stored per game, ahead of server art.
    val artPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val game = customCoverTarget
            ?: return@rememberLauncherForActivityResult
        customCoverTarget = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver
                        .openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Couldn't read that image")
                    val bounds = BitmapFactory.Options()
                        .apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(
                        bytes, 0, bytes.size, bounds
                    )
                    if (bounds.outWidth <= 0 ||
                        bounds.outHeight <= 0
                    ) {
                        error("That image couldn't be decoded")
                    }
                    var sample = 1
                    while (bounds.outWidth / sample > 512 ||
                        bounds.outHeight / sample > 512
                    ) {
                        sample *= 2
                    }
                    val opts = BitmapFactory.Options()
                        .apply { inSampleSize = sample }
                    val bmp = BitmapFactory.decodeByteArray(
                        bytes, 0, bytes.size, opts
                    ) ?: error("That image couldn't be decoded")
                    val longEdge = maxOf(bmp.width, bmp.height)
                    val final = if (longEdge > 512) {
                        val scale = 512f / longEdge
                        Bitmap.createScaledBitmap(
                            bmp,
                            (bmp.width * scale).toInt()
                                .coerceAtLeast(1),
                            (bmp.height * scale).toInt()
                                .coerceAtLeast(1),
                            true
                        )
                    } else {
                        bmp
                    }
                    BoxArt.customFile(context, game)
                        .outputStream().use {
                            final.compress(
                                Bitmap.CompressFormat.PNG, 100, it
                            )
                        }
                }.isSuccess
            }
            artTick++
            Toast.makeText(
                context,
                if (ok) {
                    "Custom cover saved"
                } else {
                    "Could not use that image as a cover"
                },
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (imported, skipped) = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val known = GameLibrary.load(store).map {
                    "${it.system}/${it.fileName}"
                }.toMutableSet()
                var imported = 0
                var skipped = 0
                val root = DocumentFile.fromTreeUri(context, uri)
                val stack = ArrayDeque<Pair<DocumentFile, Int>>()
                if (root != null) stack.add(root to 0)
                var scanned = 0
                while (stack.isNotEmpty() && scanned < 2000) {
                    val (dir, depth) = stack.removeFirst()
                    for (child in dir.listFiles()) {
                        if (child.isDirectory) {
                            if (depth < 4) stack.add(child to depth + 1)
                            continue
                        }
                        scanned++
                        val name = child.name ?: continue
                        // A zip (beta.7) opens in place:
                        // every supported ROM inside it
                        // imports under the existing
                        // duplicate rules.
                        if (name.endsWith(
                                ".zip", ignoreCase = true
                            )
                        ) {
                            val zipImported = runCatching {
                                var count = 0
                                context.contentResolver
                                    .openInputStream(child.uri)
                                    ?.use { input ->
                                        ZipRomImport.forEachEntry(
                                            input, null
                                        ) { fileName, system, bytes ->
                                            if ("$system/$fileName"
                                                in known
                                            ) {
                                                skipped++
                                                return@forEachEntry
                                            }
                                            val destDir = File(
                                                CoreStore.romDir(context),
                                                system
                                            ).apply { mkdirs() }
                                            File(destDir, fileName)
                                                .outputStream().use {
                                                    it.write(bytes)
                                                }
                                            GameLibrary.add(
                                                store,
                                                title = fileName
                                                    .substringBeforeLast(
                                                        '.'
                                                    ),
                                                system = system,
                                                fileName = fileName
                                            )
                                            known.add(
                                                "$system/$fileName"
                                            )
                                            count++
                                        }
                                    } ?: error("Couldn't read $name")
                                count
                            }.getOrElse { skipped++; 0 }
                            imported += zipImported
                            continue
                        }
                        val system = GameLibrary.systemForExtension(
                            name.substringAfterLast('.', "")
                        )
                        if (system == null) {
                            skipped++
                            continue
                        }
                        val fileName = GameLibrary.safeFileName(name)
                        if ("$system/$fileName" in known) {
                            skipped++
                            continue
                        }
                        val ok = runCatching {
                            val destDir =
                                File(CoreStore.romDir(context), system)
                                    .apply { mkdirs() }
                            val dest = File(destDir, fileName)
                            context.contentResolver
                                .openInputStream(child.uri)
                                ?.use { input ->
                                    dest.outputStream().use {
                                        input.copyTo(it)
                                    }
                                } ?: error("Couldn't read $name")
                            GameLibrary.add(
                                store,
                                title = name.substringBeforeLast('.'),
                                system = system,
                                fileName = fileName
                            )
                        }.isSuccess
                        if (ok) {
                            known.add("$system/$fileName")
                            imported++
                        } else {
                            skipped++
                        }
                    }
                }
                imported to skipped
            }
            refresh()
            Toast.makeText(
                context,
                "Imported $imported games" +
                    if (skipped > 0) ", skipped $skipped files"
                    else "",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    if (showEditor) {
        GameLayoutEditor(
            store = store,
            console = editConsole,
            consoleLabel = systemLabel(editConsole),
            onDone = { showEditor = false },
            restoreStatusBar =
                store.setting("immersive", "false") != "true"
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
            onPlay = { game ->
                if (SaveStates.autoExists(context, game.id) ||
                    SaveStates.autoArchives(context, game.id)
                        .isNotEmpty()
                ) {
                    launchChoiceTarget = game
                } else {
                    launch(game)
                }
            },
            onRename = { renameTarget = it },
            onFetchArt = { fetchArt(it, true, true) },
            onRemove = { removeTarget = it },
            onEditLayout = {
                editConsole = openConsole
                showEditor = true
            },
            onManageSaves = { managerTarget = it },
            onSetCustomCover = { customCoverTarget = it },
            onRemoveCustomCover = { removeCustomCover(it) },
            onRescanArt = { done -> rescanArt(openConsole, done) },
            onDownloadCore = { system ->
                startCoreDownload(listOf(system))
            },
            coreTick = coreTick,
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
                item {
                    OutlinedButton(
                        onClick = { folderPicker.launch(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Import a folder of ROMs") }
                    Spacer(Modifier.height(12.dp))
                }
                item {
                    OutlinedButton(
                        onClick = {
                            startCoreDownload(
                                listOf(
                                    "gb", "nes", "snes", "genesis"
                                )
                            ) { ok ->
                                Toast.makeText(
                                    context,
                                    if (ok) "Cores ready"
                                    else "Some cores failed - " +
                                        "try again",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Download all cores") }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Source-built cores from the cores " +
                            "release, verified before loading " +
                            "(about 24 MB). The built-in cores " +
                            "keep working without them.",
                        style = MaterialTheme.typography.bodySmall,
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
                    var gridView by remember {
                        mutableStateOf(
                            store.setting("gamesView", "grid") ==
                                "grid"
                        )
                    }
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Consoles",
                            style = MaterialTheme.typography
                                .titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        for ((label, isGrid) in listOf(
                            "List" to false, "Grid" to true
                        )) {
                            FilterChip(
                                selected = gridView == isGrid,
                                onClick = {
                                    gridView = isGrid
                                    store.putSetting(
                                        "gamesView",
                                        if (isGrid) "grid" else "list"
                                    )
                                },
                                label = { Text(label) },
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Separated groups (beta.6), each in
                    // release order by default and
                    // long-press-drag reorderable
                    // within its own group.
                    val handheldDrag = remember {
                        ReorderState()
                    }
                    val homeDrag = remember {
                        ReorderState()
                    }
                    val handheldOrder = remember(homeOrderTick) {
                        GameOrder.orderedConsoles(
                            store::setting, "handheld"
                        )
                    }
                    val homeOrder = remember(homeOrderTick) {
                        GameOrder.orderedConsoles(
                            store::setting, "home"
                        )
                    }
                    ConsoleGroupSection(
                        title = "Handhelds",
                        order = handheldOrder,
                        drag = handheldDrag,
                        gridView = gridView,
                        games = games,
                        onOpen = { console = it },
                        onOrderChange = { next ->
                            GameOrder.saveConsoles(
                                store, "handheld", next
                            )
                            homeOrderTick++
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    ConsoleGroupSection(
                        title = "Home consoles",
                        order = homeOrder,
                        drag = homeDrag,
                        gridView = gridView,
                        games = games,
                        onOpen = { console = it },
                        onOrderChange = { next ->
                            GameOrder.saveConsoles(
                                store, "home", next
                            )
                            homeOrderTick++
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    launchChoiceTarget?.let { game ->
        val autoInfo = SaveStates.slotInfo(
            context, game.id, SaveStates.AUTO_SLOT
        )
        LaunchChoiceDialog(
            gameTitle = game.title,
            autoModifiedAt = if (autoInfo.exists) {
                autoInfo.modifiedAt
            } else {
                0L
            },
            archives = SaveStates.autoArchives(context, game.id),
            onContinue = {
                launchChoiceTarget = null
                launch(game, SaveStates.AUTO_SLOT)
            },
            onLoadArchive = { archive ->
                launchChoiceTarget = null
                launch(
                    game,
                    loadPath = archive.stateFile.absolutePath
                )
            },
            onStartFresh = {
                launchChoiceTarget = null
                runCatching {
                    SaveStates.archiveAuto(context, game.id)
                }
                launch(game)
            },
            onDismiss = { launchChoiceTarget = null }
        )
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
            onLoadPath = { path -> launch(game, loadPath = path) },
            onDismiss = { managerTarget = null }
        )
    }

    // Core download progress (M2).
    if (coreJobRunning) {
        FrostDialog(
            onDismissRequest = {},
            title = { Text(coreJobTitle) },
            text = {
                Text(
                    if (coreJobPct >= 0) {
                        "${coreJobPct}% downloaded"
                    } else {
                        "Contacting the cores release..."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
    // First-launch core prompt (M2).
    if (showCorePrompt) {
        pendingLaunch?.let { (game, loadSlot, loadPath) ->
            FrostDialog(
                onDismissRequest = {
                    showCorePrompt = false
                    pendingLaunch = null
                },
                title = {
                    Text(
                        "Download the " +
                            "${systemLabel(game.system)} core?"
                    )
                },
                text = {
                    Text(
                        "The source-built core from the " +
                            "cores release is verified before " +
                            "loading. You can also play on " +
                            "the built-in core.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showCorePrompt = false
                        store.putSetting(
                            "corePromptSeen:${game.system}",
                            "true"
                        )
                        startCoreDownload(
                            listOf(game.system)
                        ) { ok ->
                            if (ok) {
                                pendingLaunch = null
                                launch(game, loadSlot, loadPath)
                            } else {
                                // Refusal already toasted;
                                // re-offer the built-in core.
                                pendingLaunch =
                                    Triple(game, loadSlot, loadPath)
                                showCorePrompt = true
                            }
                        }
                    }) { Text("Download core") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        store.putSetting(
                            "corePromptSeen:${game.system}",
                            "true"
                        )
                        showCorePrompt = false
                        pendingLaunch = null
                        launch(game, loadSlot, loadPath)
                    }) { Text("Use built-in core") }
                }
            )
        }
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
private fun ConsoleGroupSection(
    title: String,
    order: List<String>,
    drag: ReorderState,
    gridView: Boolean,
    games: List<GameEntry>,
    onOpen: (String) -> Unit,
    onOrderChange: (List<String>) -> Unit
) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(Modifier.height(8.dp))
    if (gridView) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            for (row in order.chunked(2)) {
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    for (c in row) {
                        ConsoleGridCard(
                            console = c,
                            count = games.count {
                                it.system == c
                            },
                            drag = drag,
                            onOpen = onOpen,                            onDrop = { d, t, a ->
                                onOrderChange(
                                    GameOrder.moveId(
                                        order, d, t, a
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    } else {
        Column {
            for (c in order) {
                ConsoleListRow(
                    console = c,
                    count = games.count {
                        it.system == c
                    },
                    drag = drag,
                    onOpen = onOpen,
                    onDrop = { d, t, a ->
                        onOrderChange(
                            GameOrder.moveId(order, d, t, a)
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ConsoleGridCard(
    console: String,
    count: Int,
    drag: ReorderState,
    onOpen: (String) -> Unit,
    onDrop: (String, String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    GroupedCard(
        modifier = modifier
            .reorderableItem(
                drag, console,
                onDrop = onDrop
            )
            .clickable {
                if (!drag.takeTapGuard(console)) {
                    onOpen(console)
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 14.dp,
                    vertical = 8.dp
                )
        ) {
            ConsoleArt(
                console = console,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                systemLabel(console),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (count == 0) {
                    "No games yet"
                } else if (count == 1) {
                    "1 game"
                } else {
                    "$count games"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConsoleListRow(
    console: String,
    count: Int,
    drag: ReorderState,
    onOpen: (String) -> Unit,
    onDrop: (String, String, Boolean) -> Unit
) {
    // The drag modifier rides on the wrapper so the
    // whole card lifts, not just its inner row.
    Box(
        modifier = Modifier.reorderableItem(
            drag, console,
            onDrop = onDrop
        )
    ) {
        SettingsCard {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        if (!drag.takeTapGuard(console)) {
                            onOpen(console)
                        }
                    }
                    .padding(vertical = 12.dp)
            ) {
                ConsoleArt(
                    console = console,
                    modifier = Modifier
                        .width(64.dp)
                        .height(48.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    systemLabel(console),
                    style = MaterialTheme.typography.titleSmall,
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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    ">",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

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
    onManageSaves: (GameEntry) -> Unit,
    onSetCustomCover: (GameEntry) -> Unit,
    onRemoveCustomCover: (GameEntry) -> Unit,
    onRescanArt: ((() -> Unit) -> Unit),
    onDownloadCore: (String) -> Unit,
    coreTick: Int
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Favorites + search (J9): title search and a
    // favorites-only filter over this console's games.
    // `favTick` re-reads the favorites setting after a
    // star toggle (store writes don't recompose).
    var query by remember { mutableStateOf("") }
    var favOnly by remember { mutableStateOf(false) }
    var favTick by remember { mutableStateOf(0) }
    // Bumped whenever this console's game order is
    // saved, so the grid re-reads settings.
    var orderTick by remember { mutableStateOf(0) }
    // Saved order first, then alphabetical (beta.6):
    // the previous build showed library order.
    val ordered = remember(games, orderTick) {
        GameOrder.orderedGames(store::setting, console, games)
    }
    val tileDrag = remember { ReorderState() }
    // Move mode (tile menu "Move"): the id of the
    // tile being moved, or null when no tile is.
    var moveId by remember { mutableStateOf<String?>(null) }
    val shown = remember(ordered, query, favOnly, favTick) {
        ordered.filter { game ->
            (!favOnly ||
                GameLibrary.isFavorite(store, game.id)) &&
                (query.isBlank() ||
                    game.title.contains(query, ignoreCase = true))
        }
    }
    var biosTarget by remember { mutableStateOf<String?>(null) }
    var pinTick by remember { mutableStateOf(0) }
    var rescanning by remember { mutableStateOf(false) }
    val consolePinned = remember(console, pinTick) {
        StartShortcuts.load(store::setting)
            .consoles.contains(console)
    }
    var biosTick by remember { mutableStateOf(0) }
    // Bumped when this page's downloaded core is
    // removed, so the core row re-reads (M2).
    var localCoreTick by remember { mutableStateOf(0) }
    val biosPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = biosTarget
            ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.use { input ->
                            BiosFiles.file(context, target)
                                .outputStream()
                                .use { input.copyTo(it) }
                        } != null
                }.getOrDefault(false)
            }
            biosTick++
            Toast.makeText(
                context,
                if (ok) "$target installed."
                else "Could not import that file.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    // System back cancels move mode first, before
    // leaving the console page (GamesScreen's own
    // BackHandler handles the page itself).
    BackHandler(enabled = moveId != null) {
        moveId = null
        tileDrag.clearDrag()
    }
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
                    Spacer(Modifier.height(8.dp))
                    // Core download (M2): the verified
                    // source-built core, or the built-in
                    // one until it is downloaded.
                    SettingsCard {
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            val downloaded =
                                remember(coreTick, localCoreTick) {
                                    CoreDownload.isDownloaded(
                                        context, console
                                    )
                                }
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (downloaded) {
                                        "Downloaded core in use"
                                    } else {
                                        "Built-in core in use"
                                    },
                                    style = MaterialTheme
                                        .typography.bodyLarge
                                )
                                Text(
                                    if (downloaded) {
                                        "Verified against the " +
                                            "cores release."
                                    } else {
                                        "Download the source-built " +
                                            "core, verified before " +
                                            "loading."
                                    },
                                    style = MaterialTheme
                                        .typography.bodySmall,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                )
                            }
                            if (downloaded) {
                                TextButton(onClick = {
                                    CoreDownload.removeDownloaded(
                                        context, console
                                    )
                                    localCoreTick++
                                }) { Text("Remove") }
                            } else {
                                Button(onClick = {
                                    onDownloadCore(console)
                                }) { Text("Download core") }
                            }
                        }
                    }
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
                    if (games.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text("Search games") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        FilterChip(
                            selected = favOnly,
                            onClick = { favOnly = !favOnly },
                            label = { Text("Favorites") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                StartShortcuts.toggleConsole(
                                    store, console
                                )
                                pinTick++
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (consolePinned) {
                                    "Unpin from start page"
                                } else {
                                    "Pin to start page"
                                }
                            )
                        }
                        if (games.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    rescanning = true
                                    onRescanArt {
                                        rescanning = false
                                    }
                                },
                                enabled = !rescanning,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (rescanning) {
                                        "Rescanning…"
                                    } else {
                                        "Rescan cover art"
                                    }
                                )
                            }
                        }
                    }
                }
            }
            if (games.isNotEmpty() && shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No games match.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme
                            .onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            items(shown, key = { it.id }) { game ->
                GameTile(
                    game = game,
                    artTick = artTick,
                    store = store,
                    favTick = favTick,
                    reorderState = tileDrag,
                    moveActive = moveId != null,
                    isMoving = moveId == game.id,
                    onRequestMove = { moveId = game.id },
                    onMoveEnd = { moveId = null },
                    onDropTile = { d, t, a ->
                        val next = GameOrder.moveId(
                            ordered.map { it.id }, d, t, a
                        )
                        GameOrder.saveGames(
                            store, console, next
                        )
                        orderTick++
                    },
                    onPlay = { onPlay(game) },
                    onRename = { onRename(game) },
                    onFetchArt = { onFetchArt(game) },
                    onRemove = { onRemove(game) },
                    onSetCustomCover = { onSetCustomCover(game) },
                    onRemoveCustomCover = { onRemoveCustomCover(game) },
                    onToggleStartPin = {
                        StartShortcuts.toggleGame(store, game.id)
                        pinTick++
                    },
                    startPinned = remember(game.id, pinTick) {
                        StartShortcuts.load(store::setting)
                            .games.contains(game.id)
                    },
                    onPinHomeScreen = {
                        LauncherPins.requestGamePin(context, game)
                    },
                    onToggleFavorite = {
                        GameLibrary.setFavorite(
                            store,
                            game.id,
                            !GameLibrary.isFavorite(store, game.id)
                        )
                        favTick++
                    }
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
                            var level by remember(console) {
                                mutableStateOf(
                                    store.setting(
                                        "gameHaptics:$console",
                                        store.setting(
                                            "gameHaptics", "Light"
                                        )
                                    )
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
                                            "gameHaptics:$console", option
                                        )
                                    },
                                    label = { Text(option) }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        var alpha by remember(console) {
                            mutableStateOf(
                                store.setting(
                                    "gameControlsAlpha:$console",
                                    store.setting(
                                        "gameControlsAlpha", "100"
                                    )
                                ).toIntOrNull() ?: 100
                            )
                        }
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                "Control opacity",
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
                                    "gameControlsAlpha:$console",
                                    alpha.toString()
                                )
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                        var shadersOn by remember(console) {
                            mutableStateOf(
                                store.setting(
                                    "gameShaders:$console",
                                    store.setting(
                                        "gameShaders", "false"
                                    )
                                ) == "true"
                            )
                        }
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    shaderToggleLabel(console),
                                    style = MaterialTheme.typography
                                        .bodyMedium
                                )
                                Text(
                                    "Preset: " +
                                        ShaderPresets.forConsole(console)
                                            .name,
                                    style = MaterialTheme.typography
                                        .bodySmall,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = shadersOn,
                                onCheckedChange = {
                                    shadersOn = it
                                    store.putSetting(
                                        "gameShaders:$console",
                                        it.toString()
                                    )
                                }
                            )
                        }
                        var curveOn by remember(console) {
                            mutableStateOf(
                                ShaderPresets.curveOn(
                                    store::setting, console
                                )
                            )
                        }
                        var curveAmount by remember(console) {
                            mutableStateOf(
                                ShaderPresets.curveAmount(
                                    store::setting, console
                                )
                            )
                        }
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                "Display Curvature",
                                style = MaterialTheme.typography
                                    .bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = curveOn,
                                onCheckedChange = {
                                    curveOn = it
                                    store.putSetting(
                                        "gameCurvature:$console",
                                        it.toString()
                                    )
                                }
                            )
                        }
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                "Curve strength",
                                style = MaterialTheme.typography
                                    .bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "$curveAmount%",
                                style = MaterialTheme.typography
                                    .bodyMedium,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        }
                        Slider(
                            value = curveAmount.toFloat(),
                            onValueChange = {
                                curveAmount =
                                    it.toInt().coerceIn(0, 100)
                            },
                            valueRange = 0f..100f,
                            onValueChangeFinished = {
                                store.putSetting(
                                    "gameCurvatureAmount:$console",
                                    curveAmount.toString()
                                )
                            },
                            enabled = curveOn
                        )
                        var roundOn by remember(console) {
                            mutableStateOf(
                                ShaderPresets.roundOn(
                                    store::setting, console
                                )
                            )
                        }
                        var roundAmount by remember(console) {
                            mutableStateOf(
                                ShaderPresets.roundAmount(
                                    store::setting, console
                                )
                            )
                        }
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                "Rounded corners",
                                style = MaterialTheme.typography
                                    .bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = roundOn,
                                onCheckedChange = {
                                    roundOn = it
                                    store.putSetting(
                                        "gameRoundCorners:$console",
                                        it.toString()
                                    )
                                }
                            )
                        }
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                "Corner radius",
                                style = MaterialTheme.typography
                                    .bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "$roundAmount%",
                                style = MaterialTheme.typography
                                    .bodyMedium,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        }
                        Slider(
                            value = roundAmount.toFloat(),
                            onValueChange = {
                                roundAmount =
                                    it.toInt().coerceIn(0, 100)
                            },
                            valueRange = 0f..100f,
                            onValueChangeFinished = {
                                store.putSetting(
                                    "gameRoundAmount:$console",
                                    roundAmount.toString()
                                )
                            },
                            enabled = roundOn
                        )
                        Spacer(Modifier.height(10.dp))
                        var fpsOnPage by remember(console) {
                            mutableStateOf(
                                store.setting(
                                    "gameFpsCounter:$console",
                                    "false"
                                ) == "true"
                            )
                        }
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                "Show FPS",
                                style = MaterialTheme.typography
                                    .bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = fpsOnPage,
                                onCheckedChange = {
                                    fpsOnPage = it
                                    store.putSetting(
                                        "gameFpsCounter:$console",
                                        it.toString()
                                    )
                                }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        var ffOnPage by remember(console) {
                            mutableStateOf(
                                store.setting(
                                    "gameFastForward:$console",
                                    "false"
                                ) == "true"
                            )
                        }
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                "Fast-forward",
                                style = MaterialTheme.typography
                                    .bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = ffOnPage,
                                onCheckedChange = {
                                    ffOnPage = it
                                    store.putSetting(
                                        "gameFastForward:$console",
                                        it.toString()
                                    )
                                }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        var intOnPage by remember(console) {
                            mutableStateOf(
                                store.setting(
                                    "gameIntegerScale:$console",
                                    "false"
                                ) == "true"
                            )
                        }
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                "Integer scaling",
                                style = MaterialTheme.typography
                                    .bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = intOnPage,
                                onCheckedChange = {
                                    intOnPage = it
                                    store.putSetting(
                                        "gameIntegerScale:$console",
                                        it.toString()
                                    )
                                }
                            )
                        }
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
                            "BIOS",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "The app ships no BIOS files - " +
                                "supply your own. Imported files " +
                                "are copied into the app's system " +
                                "folder, where the core looks for " +
                                "them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        key(biosTick) {
                            val expected = BiosFiles.expectedFor(console)
                            if (expected.isEmpty()) {
                                Text(
                                    "This console doesn't need a " +
                                        "BIOS - it boots without one.",
                                    style = MaterialTheme.typography
                                        .bodyMedium,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                )
                            } else {
                                for (name in expected) {
                                    val present = BiosFiles.isInstalled(
                                        context, name
                                    )
                                    Row(
                                        verticalAlignment = Alignment
                                            .CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                name,
                                                style = MaterialTheme
                                                    .typography.bodyLarge
                                            )
                                            Text(
                                                if (present) {
                                                    "Installed"
                                                } else {
                                                    "Missing"
                                                },
                                                style = MaterialTheme
                                                    .typography.bodySmall,
                                                color = MaterialTheme
                                                    .colorScheme
                                                    .onSurfaceVariant
                                            )
                                        }
                                        if (present) {
                                            TextButton(onClick = {
                                                BiosFiles.file(
                                                    context, name
                                                ).delete()
                                                biosTick++
                                            }) { Text("Delete") }
                                        }
                                        TextButton(onClick = {
                                            biosTarget = name
                                            biosPicker.launch(
                                                arrayOf("*/*")
                                            )
                                        }) {
                                            Text(
                                                if (present) "Replace"
                                                else "Import"
                                            )
                                        }
                                    }
                                }
                                if (console == "genesis") {
                                    Text(
                                        "Enable System Boot ROM " +
                                            "under Core settings to " +
                                            "use the installed BIOS.",
                                        style = MaterialTheme
                                            .typography.bodySmall,
                                        color = MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            top = 6.dp
                                        )
                                    )
                                }
                            }
                        }
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
    store: AppStore,
    favTick: Int,
    reorderState: ReorderState,
    onDropTile: (String, String, Boolean) -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onFetchArt: () -> Unit,
    onRemove: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetCustomCover: () -> Unit,
    onRemoveCustomCover: () -> Unit,
    onToggleStartPin: () -> Unit,
    startPinned: Boolean,
    onPinHomeScreen: () -> Unit,
    moveActive: Boolean = false,
    isMoving: Boolean = false,
    onRequestMove: () -> Unit = {},
    onMoveEnd: () -> Unit = {}
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val fav = remember(game.id, favTick) {
        GameLibrary.isFavorite(store, game.id)
    }
    val hasCustom = remember(game.id, artTick) {
        BoxArt.customFile(context, game).exists()
    }
    val bitmap by produceState<android.graphics.Bitmap?>(
        null, game.id, artTick
    ) {
        value = withContext(Dispatchers.IO) {
            val f = BoxArt.customFile(context, game)
                .takeIf { it.exists() }
                ?: BoxArt.artFile(context, game)
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
                .then(
                    if (isMoving) {
                        Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(14.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (isMoving) {
                        Modifier.dragMoveItem(
                            reorderState, game.id,
                            onDrop = { d, t, a ->
                                onDropTile(d, t, a)
                            },
                            onEnd = onMoveEnd
                        )
                    } else {
                        Modifier.combinedClickable(
                            enabled = !moveActive,
                            onClick = { onPlay() },
                            onLongClick = { menuOpen = true }
                        )
                    }
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
            // Star (J9): taps here favorite, not play -
            // the inner button consumes the tap.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopEnd
            ) {
                IconButton(
                    onClick = onToggleFavorite,
                    enabled = !moveActive,
                    modifier = Modifier.padding(6.dp)
                        .size(32.dp)
                        .frostBar(CircleShape)
                ) {
                    Icon(
                        imageVector = if (fav) Icons.Filled.Star
                            else Icons.Outlined.StarBorder,
                        contentDescription = if (fav) "Unfavorite"
                            else "Favorite",
                        tint = if (fav)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
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
                    text = { Text("Move") },
                    onClick = {
                        menuOpen = false; onRequestMove()
                    }
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
                    text = { Text("Set custom cover") },
                    onClick = { menuOpen = false; onSetCustomCover() }
                )
                if (hasCustom) {
                    DropdownMenuItem(
                        text = { Text("Remove custom cover") },
                        onClick = {
                            menuOpen = false; onRemoveCustomCover()
                        }
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            if (startPinned) {
                                "Unpin from start page"
                            } else {
                                "Pin to start page"
                            }
                        )
                    },
                    onClick = { menuOpen = false; onToggleStartPin() }
                )
                DropdownMenuItem(
                    text = { Text("Add to home screen") },
                    onClick = { menuOpen = false; onPinHomeScreen() }
                )
                DropdownMenuItem(
                    text = { Text("Remove") },
                    onClick = { menuOpen = false; onRemove() }
                )
            }
        }
        if (isMoving) {
            TextButton(
                onClick = {
                    onMoveEnd()
                    reorderState.clearDrag()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Done moving") }
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

fun systemLabel(system: String): String = when (system) {
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

// The shader toggle's name follows the screen the
// console shipped with: handhelds are LCD, home
// consoles are CRT. The underlying path (LCD port,
// CRT chain, legacy grid) stays internal.
private fun shaderToggleLabel(console: String): String =
    if (console == "gb" || console == "gbc" ||
        console == "gba" || console == "gg"
    ) "LCD shaders" else "CRT shaders"
