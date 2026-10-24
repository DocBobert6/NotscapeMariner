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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.notscape.browser.emu.SaveStates

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
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
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
                            val thumb = if (info.hasThumb) {
                                BitmapFactory.decodeFile(
                                    SaveStates.thumbFile(
                                        context, gameId,
                                        SaveStates.AUTO_SLOT
                                    ).absolutePath
                                )
                            } else {
                                null
                            }
                            if (thumb != null) {
                                Image(
                                    thumb.asImageBitmap(),
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
                                        SaveStates.save(
                                            context, gameId,
                                            SaveStates.AUTO_SLOT
                                        )
                                        tick++
                                    }) { Text("Save") }
                                }
                                if (info.exists) {
                                    TextButton(onClick = {
                                        onLoad(SaveStates.AUTO_SLOT)
                                    }) { Text("Load") }
                                    TextButton(onClick = {
                                        SaveStates.delete(
                                            context, gameId,
                                            SaveStates.AUTO_SLOT
                                        )
                                        tick++
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
                                val thumb = if (info.hasThumb) {
                                    BitmapFactory.decodeFile(
                                        SaveStates.thumbFile(
                                            context, gameId, slot
                                        ).absolutePath
                                    )
                                } else {
                                    null
                                }
                                if (thumb != null) {
                                    Image(
                                        thumb.asImageBitmap(),
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
                                            SaveStates.save(
                                                context, gameId, slot
                                            )
                                            tick++
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
                                            SaveStates.delete(
                                                context, gameId, slot
                                            )
                                            tick++
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
                                val thumb =
                                    if (archive.thumbFile.exists()) {
                                        BitmapFactory.decodeFile(
                                            archive.thumbFile
                                                .absolutePath
                                        )
                                    } else {
                                        null
                                    }
                                if (thumb != null) {
                                    Image(
                                        thumb.asImageBitmap(),
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
                                        SaveStates.deleteArchive(
                                            archive
                                        )
                                        tick++
                                    }) { Text("Delete") }
                                }
                            }
                        }
                    }
                    if (batteryExists && !batteryGone &&
                        onDeleteBattery != null
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                                    "Battery save",
                                    style = MaterialTheme.typography
                                        .titleSmall
                                )
                                Text(
                                    "The game's own save file.",
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
                                    TextButton(onClick = {
                                        onDeleteBattery()
                                        batteryGone = true
                                    }) { Text("Delete") }
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
