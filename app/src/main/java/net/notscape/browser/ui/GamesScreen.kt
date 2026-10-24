package net.notscape.browser.ui

import android.content.Intent
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.notscape.browser.data.AppStore
import net.notscape.browser.emu.CoreStore
import net.notscape.browser.emu.GameActivity

/**
 * The Games library (Phase J, J1): deliberately minimal -
 * import a ROM you own and play it. J2 grows this into
 * the full library. Cores are built in (Game Boy, Game
 * Boy Color and Game Boy Advance through mGBA); no game
 * content ships with the app, ever - every ROM here was
 * imported by the user and is copied into app storage.
 */
@Composable
fun GamesScreen(store: AppStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastRomPath by remember {
        mutableStateOf(store.setting("lastRomPath", ""))
    }
    var lastRomName by remember {
        mutableStateOf(store.setting("lastRomName", ""))
    }
    fun launch(path: String) {
        context.startActivity(
            Intent(context, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_ROM_PATH, path)
        )
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
                        .lowercase()
                    require(ext in setOf("gb", "gbc", "gba")) {
                        "That isn't a Game Boy ROM (.gb, .gbc, .gba)"
                    }
                    val safeName = displayName.replace(
                        Regex("[^A-Za-z0-9._-]"), "_"
                    )
                    val dest = File(CoreStore.romDir(context), safeName)
                    context.contentResolver.openInputStream(uri)
                        ?.use { input ->
                            dest.outputStream().use { input.copyTo(it) }
                        } ?: error("Couldn't read that file")
                    dest.absolutePath to displayName
                }
            }
            result.onSuccess { (path, name) ->
                store.putSetting("lastRomPath", path)
                store.putSetting("lastRomName", name)
                lastRomPath = path
                lastRomName = name
                launch(path)
            }.onFailure { e ->
                Toast.makeText(
                    context,
                    e.message ?: "Couldn't import that ROM",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    SubScreen(title = "Games", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                "Game Boy, Game Boy Color and Game Boy " +
                    "Advance, played on a built-in core. " +
                    "Bring your own ROMs - nothing is " +
                    "downloaded, and imported games are " +
                    "copied into the app's own storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            if (lastRomPath.isNotBlank() &&
                File(lastRomPath).exists()
            ) {
                SettingsCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                lastRomName.ifBlank { "Last game" },
                                style = MaterialTheme
                                    .typography.bodyLarge
                            )
                            Text(
                                "Played most recently",
                                style = MaterialTheme
                                    .typography.bodySmall,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        }
                        Button(onClick = { launch(lastRomPath) }) {
                            Text("Play")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Choose a ROM") }
        }
    }
}
