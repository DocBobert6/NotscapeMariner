package net.notscape.browser.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
 * Launch choice (beta.7): tapping a game with
 * auto-saves asks first - Continue (the latest
 * auto-save), any older archived auto-saves by
 * date, or Start fresh. A game with no auto-saves
 * never shows this; it plays straight in.
 */
@Composable
fun LaunchChoiceDialog(
    gameTitle: String,
    autoModifiedAt: Long,
    archives: List<SaveStates.AutoArchive>,
    onContinue: () -> Unit,
    onLoadArchive: (SaveStates.AutoArchive) -> Unit,
    onStartFresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                modifier = Modifier.fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    gameTitle,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))
                if (autoModifiedAt > 0L) {
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onContinue() }
                            .padding(vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Continue",
                                style = MaterialTheme.typography
                                    .titleSmall
                            )
                            Text(
                                "Where you left off - " +
                                    dateFormat.format(
                                        Date(autoModifiedAt)
                                    ),
                                style = MaterialTheme.typography
                                    .bodySmall,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        }
                    }
                }
                archives.forEach { archive ->
                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onLoadArchive(archive) }
                            .padding(vertical = 10.dp)
                    ) {
                        val thumbPath = archive.thumbFile.absolutePath
                        // Audit P-m5: decode off the main thread
                        // and downsampled — the old remember ran a
                        // full-size decode during composition.
                        val thumb by produceState<
                            android.graphics.Bitmap?
                        >(null, thumbPath) {
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
                                modifier = Modifier.size(48.dp, 36.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp, 36.dp)
                                    .background(
                                        MaterialTheme.colorScheme
                                            .surfaceContainerLow,
                                        RoundedCornerShape(6.dp)
                                    )
                            ) {}
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Earlier auto-save",
                                style = MaterialTheme.typography
                                    .titleSmall
                            )
                            Text(
                                dateFormat.format(
                                    Date(archive.modifiedAt)
                                ),
                                style = MaterialTheme.typography
                                    .bodySmall,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onStartFresh() }
                        .padding(vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Start fresh",
                            style = MaterialTheme.typography
                                .titleSmall
                        )
                        Text(
                            "Begin from the title screen - " +
                                "your auto-saves are kept",
                            style = MaterialTheme.typography
                                .bodySmall,
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}
