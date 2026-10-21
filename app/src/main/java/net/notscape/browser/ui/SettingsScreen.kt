package net.notscape.browser.ui

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import net.notscape.browser.BuildConfig
import net.notscape.browser.browser.FilterEngine
import net.notscape.browser.browser.UpdateChecker
import net.notscape.browser.data.AppStore
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(store: AppStore, onBack: () -> Unit) {
    val rev = store.revision
    val context = LocalContext.current
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<String?>(null) }
    var clearedNote by remember { mutableStateOf("") }
    var siteQuery by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(store.exportBackup().toByteArray())
                }
                Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Backup failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText().orEmpty()
                if (text.isNotBlank()) confirmImport = text
            }
        }
    }

    SubScreen("Settings", onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            GroupTitle("Appearance")
            SettingsCard {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                val themeMode = store.setting("themeMode", "system")
                listOf("system" to "System default", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { store.putSetting("themeMode", value) }
                    ) {
                        RadioButton(selected = themeMode == value, onClick = { store.putSetting("themeMode", value) })
                        Text(label)
                    }
                }
                Text("Address bar position", style = MaterialTheme.typography.titleSmall)
                val barPosition = store.setting("barPosition", "bottom")
                listOf("bottom" to "Bottom (Safari style)", "top" to "Top").forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { store.putSetting("barPosition", value) }
                    ) {
                        RadioButton(selected = barPosition == value, onClick = { store.putSetting("barPosition", value) })
                        Text(label)
                    }
                }
                Text("Start page wallpaper", style = MaterialTheme.typography.titleSmall)
                val wallpaper = store.setting("wallpaper", "ocean")
                Row {
                    listOf("ocean", "sunset", "forest", "slate", "paper").forEach { w ->
                        TextButton(onClick = { store.putSetting("wallpaper", w) }) {
                            Text(
                                w.replaceFirstChar { it.uppercase() },
                                color = if (wallpaper == w) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            GroupTitle("Search")
            SettingsCard {
                val engine = store.setting("searchEngine", "duckduckgo")
                listOf(
                    "duckduckgo" to "DuckDuckGo",
                    "startpage" to "Startpage",
                    "google" to "Google",
                    "bing" to "Bing"
                ).forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { store.putSetting("searchEngine", value) }
                    ) {
                        RadioButton(selected = engine == value, onClick = { store.putSetting("searchEngine", value) })
                        Text(label)
                    }
                }
            }

            GroupTitle("Privacy & blocking")
            SettingsCard {
                ToggleRow(
                    "Block ads (filter lists)",
                    store.setting("blockAds", "true") == "true"
                ) { store.putSetting("blockAds", it.toString()) }
                ToggleRow(
                    "Block trackers",
                    store.setting("blockTrackers", "true") == "true"
                ) { store.putSetting("blockTrackers", it.toString()) }
                val filterStatus = when {
                    FilterEngine.updating -> "Updating filter lists…"
                    FilterEngine.ruleCount > 0 -> {
                        val whenText = if (FilterEngine.updatedAt > 0)
                            DateFormat.getDateInstance().format(Date(FilterEngine.updatedAt)) else "—"
                        "${FilterEngine.ruleCount} rules · updated $whenText"
                    }
                    else -> "Lists not downloaded yet (built-in rules active)"
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(filterStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { FilterEngine.refreshAsync(context) },
                        enabled = !FilterEngine.updating
                    ) { Text("Update now") }
                }
                ToggleRow(
                    "Strict popup blocking",
                    store.setting("popupStrict", "true") == "true"
                ) { store.putSetting("popupStrict", it.toString()) }
                Text(
                    "Blocks every popup window, including ones a tap asks for. " +
                        "You can allow a blocked popup once, or always for a site below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ToggleRow(
                    "Accept cookies",
                    store.setting("cookies", "true") == "true"
                ) {
                    store.putSetting("cookies", it.toString())
                    CookieManager.getInstance().setAcceptCookie(it)
                }
                ToggleRow(
                    "Restore tabs on launch",
                    store.setting("restoreTabs", "true") == "true"
                ) { store.putSetting("restoreTabs", it.toString()) }
            }

            GroupTitle("Per-site controls")
            SettingsCard {
                Text(
                    "JavaScript, images, popups, and ad blocking per site. Changes apply the next time a tab loads that site.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = siteQuery,
                    onValueChange = { siteQuery = it },
                    label = { Text("Search sites") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                val hosts = (store.siteSettings.keys + store.history.mapNotNull {
                    runCatching { android.net.Uri.parse(it.url).host }.getOrNull()
                }).filter { !it.isNullOrBlank() }.map { it!! }
                    .filter { siteQuery.isBlank() || it.contains(siteQuery, ignoreCase = true) }
                    .distinct().sorted()
                if (hosts.isEmpty()) {
                    Text("No sites yet.", style = MaterialTheme.typography.bodySmall)
                }
                hosts.take(60).forEach { host ->
                    val s = store.siteSetting(host)
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(host, style = MaterialTheme.typography.titleSmall)
                        Row {
                            TextButton(onClick = {
                                store.putSiteSetting(s.copy(javaScript = !s.javaScript))
                            }) {
                                Text("JavaScript: ${if (s.javaScript) "on" else "off"}")
                            }
                            TextButton(onClick = {
                                store.putSiteSetting(s.copy(blockImages = !s.blockImages))
                            }) {
                                Text("Images: ${if (s.blockImages) "blocked" else "shown"}")
                            }
                            TextButton(onClick = {
                                store.putSiteSetting(s.copy(allowPopups = !s.allowPopups))
                            }) {
                                Text("Popups: ${if (s.allowPopups) "allowed" else "blocked"}")
                            }
                            TextButton(onClick = {
                                store.putSiteSetting(s.copy(disableBlocking = !s.disableBlocking))
                            }) {
                                Text("Blocking: ${if (s.disableBlocking) "off" else "on"}")
                            }
                        }
                    }
                }
            }

            GroupTitle("Data")
            SettingsCard {
                OutlinedButton(
                    onClick = { exportLauncher.launch("notscape-backup.json") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export backup (no vault)") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import backup") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        store.clearHistory()
                        clearedNote = "History cleared."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Clear history") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        WebStorage.getInstance().deleteAllData()
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                        runCatching { WebView(context).clearCache(true) }
                        clearedNote = "Web storage, cookies, and cache cleared."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Clear web data") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { confirmClearAll = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Erase everything on this device") }
                if (clearedNote.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(clearedNote)
                }
            }

            GroupTitle("About")
            SettingsCard {
                Text("Notscape Mariner — version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                Text("Package: net.notscape.browser")
                Text("Local-only and private: your data is stored on this device.")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            UpdateChecker.checking -> "Checking for updates…"
                            UpdateChecker.availableVersion != null ->
                                "Version ${UpdateChecker.availableVersion} is available on GitHub."
                            UpdateChecker.lastCheckedAt > 0 -> "You're up to date."
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { UpdateChecker.checkAsync(store, force = true) },
                        enabled = !UpdateChecker.checking
                    ) { Text("Check for updates") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Erase everything?") },
            text = {
                Text("Bookmarks, history, playlists, downloads list, vault entries, site settings, and favourites stored on this device will be deleted. Downloaded files and books stay in their folders.")
            },
            confirmButton = {
                TextButton(onClick = {
                    store.clearAllData()
                    confirmClearAll = false
                    clearedNote = "All on-device data erased."
                }) { Text("Erase") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
            }
        )
    }

    confirmImport?.let { json ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Import backup?") },
            text = {
                Text("This replaces your current bookmarks, history, playlists, and settings with the backup's. Your password vault on this device is kept.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = store.importBackup(json)
                    confirmImport = null
                    clearedNote = if (ok) "Backup imported." else "That file is not a Notscape backup."
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun GroupTitle(text: String) {
    Spacer(Modifier.height(22.dp))
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
