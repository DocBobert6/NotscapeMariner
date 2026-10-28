package net.notscape.browser.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.notscape.browser.R
import net.notscape.browser.emu.SaveStates

/** Bounds-pass sampled decode (audit P-m5/P-m6): never hold a
 *  full-resolution image for a thumbnail-sized view. */
private fun decodeSampledFile(path: String, maxSide: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return runCatching { BitmapFactory.decodeFile(path, opts) }.getOrNull()
}

/**
 * The save-state picker (Phase J; beta.7 layout at
 * Robert's word: the auto-save sits above all in a
 * full-width line card, the four numbered slots in
 * a 2x2 grid below it, older archived auto-saves
 * as dated line rows under the grid). In a game,
 * Save is offered and Load applies at once; on a
 * console page, Save is hidden and Load launches
 * the game into that state. The battery save, when
 * present, gets its own row with a Delete.
 */
@Composable
fun SaveStateDialog(
    gameId: String,
    showSave: Boolean,
    batteryExists: Boolean,
    onDeleteBattery: (() -> Unit)?,
    onLoad: (Int) -> Unit,
    onLoadPath: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
    /** PS1 (beta.17): the battery file is the
     * memory card, so the row gains import/export
     * and shows even when no card exists yet. */
    isPs1: Boolean = false,
    onImportCard: (() -> Unit)? = null,
    onExportCard: (() -> Unit)? = null,
    /** In-game (C10): the row is status-only.
     * Import/export would race the running core's
     * exit autosave, so they live on the console
     * page instead. */
    cardStatusOnly: Boolean = false
) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var batteryGone by remember { mutableStateOf(false) }
    val dateFormat = remember {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    }
    // Platform-default dialog width (~300dp) starves
    // the auto-save row's weighted text column in
    // landscape: the buttons keep full width and the
    // text wraps one letter per line. Full width
    // (the same fix SettingsScreen already uses).
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        // Height-capped to the window (Robert's
        // 2026-10-24 screenshot: on a short window
        // the old 420dp list cap overflowed and cut
        // Slot 3/4 off). The grid now takes the
        // remaining card height and scrolls inside.
        // Surface: the kit dialog material (same
        // verdict - the plain white card looked basic
        // and old): the sheets' 28dp shape + sheet
        // colour, as every stock dialog wears.
        Card(
            modifier = Modifier.fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = Frost.DialogShape,
            colors = CardDefaults.cardColors(
                containerColor = Frost.sheetColor()
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 0.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
                    .fillMaxHeight()
            ) {
                Text(
                    "Save states",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // The auto-save: above all, in a
                    // full-width line card.
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        @Suppress("UNUSED_VARIABLE")
                        val refresh = tick
                        val info = SaveStates.slotInfo(
                            context, gameId, SaveStates.AUTO_SLOT
                        )
                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            val thumbPath = SaveStates.thumbFile(
                                context, gameId,
                                SaveStates.AUTO_SLOT
                            ).absolutePath
                            // Audit P-m5: decode off the main thread
                            // and downsampled — the old remember ran
                            // a full-size decode during composition.
                            val thumb by produceState<
                                android.graphics.Bitmap?
                            >(null, thumbPath, tick) {
                                value = withContext(Dispatchers.IO) {
                                    if (info.hasThumb) {
                                        decodeSampledFile(thumbPath, 256)
                                    } else {
                                        null
                                    }
                                }
                            }
                            val thumbNow = thumb
                            if (thumbNow != null) {
                                Image(
                                    thumbNow.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(64.dp, 48.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp, 48.dp)
                                        .background(
                                            MaterialTheme.colorScheme
                                                .surfaceContainerLow,
                                            RoundedCornerShape(6.dp)
                                        )
                                ) {}
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "Auto-save",
                                    style = MaterialTheme.typography
                                        .titleSmall
                                )
                                Text(
                                    if (info.exists) {
                                        dateFormat.format(
                                            Date(info.modifiedAt)
                                        )
                                    } else {
                                        "Empty"
                                    },
                                    style = MaterialTheme.typography
                                        .bodySmall,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                )
                            }
                            Row(
                                horizontalArrangement =
                                    Arrangement.End
                            ) {
                                if (showSave) {
                                    TextButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                SaveStates.save(
                                                    context, gameId,
                                                    SaveStates.AUTO_SLOT
                                                )
                                            }
                                            tick++
                                        }
                                    }) { Text("Save") }
                                }
                                if (info.exists) {
                                    TextButton(onClick = {
                                        onLoad(SaveStates.AUTO_SLOT)
                                    }) { Text("Load") }
                                    TextButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                SaveStates.delete(
                                                    context, gameId,
                                                    SaveStates.AUTO_SLOT
                                                )
                                            }
                                            tick++
                                        }
                                    }) { Text("Delete") }
                                }
                            }
                        }
                    }
                    // The four numbered slots, 2x2.
                    items(listOf(0, 1, 2, 3)) { slot ->
                        // Re-read on every tick so Save
                        // and Delete refresh the cards.
                        @Suppress("UNUSED_VARIABLE")
                        val refresh = tick
                        val info = SaveStates.slotInfo(
                            context, gameId, slot
                        )
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Frost.cardColor()
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 0.dp
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp)
                            ) {
                                val thumbPath = SaveStates.thumbFile(
                                    context, gameId, slot
                                ).absolutePath
                                // Audit P-m5: as above — IO +
                                // downsampled, not composition-time.
                                val thumb by produceState<
                                    android.graphics.Bitmap?
                                >(null, thumbPath, tick) {
                                    value = withContext(Dispatchers.IO) {
                                        if (info.hasThumb) {
                                            decodeSampledFile(thumbPath, 256)
                                        } else {
                                            null
                                        }
                                    }
                                }
                                val thumbNow = thumb
                                if (thumbNow != null) {
                                    Image(
                                        thumbNow.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(4f / 3f)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(4f / 3f)
                                            .background(
                                                MaterialTheme.colorScheme
                                                    .surfaceContainerLow,
                                                RoundedCornerShape(6.dp)
                                            )
                                    ) {}
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Slot ${slot + 1}",
                                    style = MaterialTheme.typography
                                        .titleSmall
                                )
                                Text(
                                    if (info.exists) {
                                        dateFormat.format(
                                            Date(info.modifiedAt)
                                        )
                                    } else {
                                        "Empty"
                                    },
                                    style = MaterialTheme.typography
                                        .bodySmall,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                )
                                if (showSave) {
                                    Row(
                                        horizontalArrangement =
                                            Arrangement.End,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        TextButton(onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    SaveStates.save(
                                                        context, gameId, slot
                                                    )
                                                }
                                                tick++
                                            }
                                        }) { Text("Save") }
                                    }
                                }
                                if (info.exists) {
                                    Row(
                                        horizontalArrangement =
                                            Arrangement.End,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        TextButton(onClick = {
                                            onLoad(slot)
                                        }) { Text("Load") }
                                        TextButton(onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    SaveStates.delete(
                                                        context, gameId, slot
                                                    )
                                                }
                                                tick++
                                            }
                                        }) { Text("Delete") }
                                    }
                                }
                            }
                        }
                    }
                    // Older archived auto-saves
                    // (beta.7): dated line rows under
                    // the grid. Load or Delete only -
                    // saving always goes to the live
                    // auto-save above.
                    val archives = SaveStates.autoArchives(
                        context, gameId
                    )
                    if (archives.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "Older auto-saves",
                                style = MaterialTheme.typography
                                    .titleSmall,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                            )
                            Text(
                                stringResource(
                                    R.string.auto_archives_kept,
                                    SaveStates.MAX_AUTO_ARCHIVES
                                ),
                                style = MaterialTheme.typography
                                    .bodySmall,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        }
                        items(
                            archives,
                            key = { it.stateFile.absolutePath },
                            span = { GridItemSpan(maxLineSpan) }
                        ) { archive ->
                            @Suppress("UNUSED_VARIABLE")
                            val refresh = tick
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                val thumbPath =
                                    archive.thumbFile.absolutePath
                                // Audit P-m5: as above — IO +
                                // downsampled, not composition-time.
                                val thumb by produceState<
                                    android.graphics.Bitmap?
                                >(null, thumbPath, tick) {
                                    value = withContext(Dispatchers.IO) {
                                        if (archive.thumbFile.exists()) {
                                            decodeSampledFile(thumbPath, 256)
                                        } else {
                                            null
                                        }
                                    }
                                }
                                val thumbNow = thumb
                                if (thumbNow != null) {
                                    Image(
                                        thumbNow.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(64.dp, 48.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp, 48.dp)
                                            .background(
                                                MaterialTheme.colorScheme
                                                    .surfaceContainerLow,
                                                RoundedCornerShape(6.dp)
                                            )
                                    ) {}
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        "Auto-save",
                                        style = MaterialTheme
                                            .typography.titleSmall
                                    )
                                    Text(
                                        dateFormat.format(
                                            Date(archive.modifiedAt)
                                        ),
                                        style = MaterialTheme
                                            .typography.bodySmall,
                                        color = MaterialTheme
                                            .colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    horizontalArrangement =
                                        Arrangement.End
                                ) {
                                    TextButton(onClick = {
                                        onLoadPath?.invoke(
                                            archive.stateFile
                                                .absolutePath
                                        )
                                    }) { Text("Load") }
                                    TextButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                SaveStates.deleteArchive(
                                                    archive
                                                )
                                            }
                                            tick++
                                        }
                                    }) { Text("Delete") }
                                }
                            }
                        }
                    }
                    val showBatteryRow =
                        (batteryExists && !batteryGone &&
                            onDeleteBattery != null) ||
                            (isPs1 && (onImportCard != null ||
                                cardStatusOnly))
                    if (showBatteryRow) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                                    if (isPs1) {
                                        "Memory card"
                                    } else {
                                        "Battery save"
                                    },
                                    style = MaterialTheme.typography
                                        .titleSmall
                                )
                                Text(
                                    if (isPs1) {
                                        if (cardStatusOnly) {
                                            if (batteryExists &&
                                                !batteryGone
                                            ) {
                                                "The game's PlayStation " +
                                                    "memory card (128KB). " +
                                                    "Import and export are " +
                                                    "on the console page."
                                            } else {
                                                "No memory card yet - " +
                                                    "save in a game to " +
                                                    "create one, or import " +
                                                    "one from the console page."
                                            }
                                        } else if (batteryExists &&
                                            !batteryGone
                                        ) {
                                            "The game's PlayStation " +
                                                "memory card (128KB)."
                                        } else {
                                            "No memory card yet - " +
                                                "import one, or save " +
                                                "in a game to create one."
                                        }
                                    } else {
                                        "The game's own save file."
                                    },
                                    style = MaterialTheme.typography
                                        .bodySmall,
                                    color = MaterialTheme.colorScheme
                                        .onSurfaceVariant
                                )
                                Row(
                                    horizontalArrangement =
                                        Arrangement.End,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isPs1 &&
                                        onImportCard != null
                                    ) {
                                        TextButton(
                                            onClick = onImportCard
                                        ) { Text("Import") }
                                    }
                                    if (isPs1 && batteryExists &&
                                        !batteryGone &&
                                        onExportCard != null
                                    ) {
                                        TextButton(
                                            onClick = onExportCard
                                        ) { Text("Export") }
                                    }
                                    if (batteryExists &&
                                        !batteryGone &&
                                        onDeleteBattery != null
                                    ) {
                                        TextButton(onClick = {
                                            onDeleteBattery()
                                            batteryGone = true
                                        }) { Text("Delete") }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}
