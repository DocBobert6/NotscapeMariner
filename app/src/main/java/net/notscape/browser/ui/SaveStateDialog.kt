package net.notscape.browser.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.notscape.browser.emu.SaveStates

/**
 * The save-state picker (Phase J; J7 redesign of the
 * J5 dialog, which crowded three buttons into each
 * row and wrapped its text mid-word). A near-full-width
 * custom dialog: each row is two lines - thumbnail,
 * name and a short date on the first, the actions on
 * their own line underneath, so nothing wraps. All
 * five slots are in one capped scrolling list. In a
 * game, Save is offered and Load applies at once; on a
 * console page, Save is hidden and Load launches the
 * game into that slot. The battery save, when present,
 * gets its own row with a Delete.
 */
@Composable
fun SaveStateDialog(
    gameId: String,
    showSave: Boolean,
    batteryExists: Boolean,
    onDeleteBattery: (() -> Unit)?,
    onLoad: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var batteryGone by remember { mutableStateOf(false) }
    val dateFormat = remember {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    }
    Dialog(onDismissRequest = onDismiss) {
        GroupedCard(
            modifier = Modifier.fillMaxWidth(0.94f)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    "Save states",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    if (batteryExists && !batteryGone &&
                        onDeleteBattery != null
                    ) {
                        item {
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
                    items(
                        listOf(
                            SaveStates.AUTO_SLOT, 0, 1, 2, 3
                        )
                    ) { slot ->
                        // Re-read on every tick so Save
                        // and Delete refresh the rows.
                        @Suppress("UNUSED_VARIABLE")
                        val refresh = tick
                        val info = SaveStates.slotInfo(
                            context, gameId, slot
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
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
                                        if (slot ==
                                            SaveStates.AUTO_SLOT
                                        ) {
                                            "Auto-save"
                                        } else {
                                            "Slot ${slot + 1}"
                                        },
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
                            }
                            Row(
                                horizontalArrangement =
                                    Arrangement.End,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (showSave) {
                                    TextButton(onClick = {
                                        SaveStates.save(
                                            context, gameId, slot
                                        )
                                        tick++
                                    }) { Text("Save") }
                                }
                                if (info.exists) {
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
