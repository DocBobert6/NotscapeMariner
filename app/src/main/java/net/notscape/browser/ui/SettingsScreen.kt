package net.notscape.browser.ui

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    var homeDraft by remember { mutableStateOf(store.setting("homePage", "")) }

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
                val themeMode = store.setting("themeMode", "system")
                ChoiceRow(
                    icon = Icons.Filled.SettingsBrightness, chipColor = Color(0xFF8E8E93),
                    label = "System default", selected = themeMode == "system"
                ) { store.putSetting("themeMode", "system") }
                InsetDivider()
                ChoiceRow(
                    icon = Icons.Filled.WbSunny, chipColor = Color(0xFFFF9500),
                    label = "Light", selected = themeMode == "light"
                ) { store.putSetting("themeMode", "light") }
                InsetDivider()
                ChoiceRow(
                    icon = Icons.Filled.DarkMode, chipColor = Color(0xFF5856D6),
                    label = "Dark", selected = themeMode == "dark"
                ) { store.putSetting("themeMode", "dark") }
                InsetDivider()
                val barPosition = store.setting("barPosition", "bottom")
                ChoiceRow(
                    icon = Icons.Filled.VerticalAlignBottom, chipColor = Color(0xFF0A84FF),
                    label = "Address bar: Bottom (Safari style)", selected = barPosition == "bottom"
                ) { store.putSetting("barPosition", "bottom") }
                InsetDivider()
                ChoiceRow(
                    icon = Icons.Filled.VerticalAlignTop, chipColor = Color(0xFF0A84FF),
                    label = "Address bar: Top", selected = barPosition == "top"
                ) { store.putSetting("barPosition", "top") }
                InsetDivider()
                Text(
                    "Start page wallpaper",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
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
                InsetDivider()
                ToggleRow(
                    "Home button in address bar",
                    store.setting("showHomeButton", "true") == "true",
                    icon = Icons.Filled.Home, chipColor = Color(0xFF0E7C8C)
                ) { store.putSetting("showHomeButton", it.toString()) }
                InsetDivider()
                ToggleRow(
                    "Immersive full screen",
                    store.setting("immersive", "true") == "true",
                    icon = Icons.Filled.Fullscreen, chipColor = Color(0xFF48484A)
                ) { store.putSetting("immersive", it.toString()) }
            }

            GroupTitle("Search")
            SettingsCard {
                val engine = store.setting("searchEngine", "duckduckgo")
                val engines = listOf(
                    Triple("duckduckgo", "DuckDuckGo", Color(0xFFDE5833)),
                    Triple("startpage", "Startpage", Color(0xFF657EEA)),
                    Triple("google", "Google", Color(0xFF4285F4)),
                    Triple("bing", "Bing", Color(0xFF008373))
                )
                engines.forEachIndexed { i, (value, label, color) ->
                    if (i > 0) InsetDivider()
                    ChoiceRow(
                        letter = label.first().toString(), chipColor = color,
                        label = label, selected = engine == value
                    ) { store.putSetting("searchEngine", value) }
                }
            }

            GroupTitle("Homepage")
            SettingsCard {
                Text(
                    "New tabs open this page. Leave it empty to use the start page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                )
                OutlinedTextField(
                    value = homeDraft,
                    onValueChange = { homeDraft = it },
                    label = { Text("Homepage URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = {
                        store.putSetting("homePage", homeDraft.trim())
                        Toast.makeText(context, "Homepage saved", Toast.LENGTH_SHORT).show()
                    }) { Text("Save homepage") }
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = {
                        homeDraft = ""
                        store.putSetting("homePage", "")
                    }) { Text("Use start page") }
                }
            }

            GroupTitle("Privacy & blocking")
            SettingsCard {
                ToggleRow(
                    "Block ads (filter lists)",
                    store.setting("blockAds", "true") == "true",
                    icon = Icons.Filled.Block, chipColor = Color(0xFFFF3B30)
                ) { store.putSetting("blockAds", it.toString()) }
                InsetDivider()
                ToggleRow(
                    "Block trackers",
                    store.setting("blockTrackers", "true") == "true",
                    icon = Icons.Filled.VisibilityOff, chipColor = Color(0xFFAF52DE)
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
                    modifier = Modifier.fillMaxWidth().padding(start = 42.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(filterStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { FilterEngine.refreshAsync(context) },
                        enabled = !FilterEngine.updating
                    ) { Text("Update now") }
                }
                InsetDivider()
                ToggleRow(
                    "Strict popup blocking",
                    store.setting("popupStrict", "true") == "true",
                    icon = Icons.Filled.PictureInPictureAlt, chipColor = Color(0xFFFF9500)
                ) { store.putSetting("popupStrict", it.toString()) }
                Text(
                    "Blocks every popup window, including ones a tap asks for. " +
                        "You can allow a blocked popup once, or always for a site below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 42.dp, top = 2.dp, bottom = 4.dp)
                )
                InsetDivider()
                ToggleRow(
                    "Accept cookies",
                    store.setting("cookies", "true") == "true",
                    icon = Icons.Filled.Cookie, chipColor = Color(0xFFA2845E)
                ) {
                    store.putSetting("cookies", it.toString())
                    CookieManager.getInstance().setAcceptCookie(it)
                }
                InsetDivider()
                ToggleRow(
                    "Restore tabs on launch",
                    store.setting("restoreTabs", "true") == "true",
                    icon = Icons.Filled.Restore, chipColor = Color(0xFF30B0C7)
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
                Text(
                    "Download location",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
                val dlLoc = store.setting("downloadLocation", "public")
                ChoiceRow(
                    letter = "D", chipColor = Color(0xFF30B0C7),
                    label = "Downloads folder", selected = dlLoc == "public"
                ) { store.putSetting("downloadLocation", "public") }
                InsetDivider()
                ChoiceRow(
                    letter = "A", chipColor = Color(0xFF8E8E93),
                    label = "App folder (private)", selected = dlLoc == "app"
                ) { store.putSetting("downloadLocation", "app") }
                Spacer(Modifier.height(10.dp))
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
                Text("Notscape Mariner — version ${BuildConfig.VERSION_NAME} (beta)")
                Text("Package: net.notscape.browser")
                Text("Local-only and private: your data is stored on this device.")
                Spacer(Modifier.height(10.dp))
                val avail = UpdateChecker.availableVersion
                val progress = UpdateChecker.downloadProgress
                when {
                    avail != null -> {
                        Text(
                            "Version $avail (beta) is available.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        if (progress in 0..100) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "Downloading… $progress%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Button(
                                onClick = { UpdateChecker.downloadAndInstall(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Download & install $avail") }
                            if (progress == -2) {
                                Text(
                                    "Download failed — check your connection and try again.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when {
                                    UpdateChecker.checking -> "Checking for updates…"
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
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            shape = RoundedCornerShape(28.dp),
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
            shape = RoundedCornerShape(28.dp),
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
        modifier = Modifier.padding(start = 20.dp)
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            content()
        }
    }
}

/** Small rounded-square glyph like the iOS Settings app. */
@Composable
private fun IconChip(icon: ImageVector?, letter: String?, chipColor: Color) {
    Box(
        modifier = Modifier.size(30.dp)
            .background(chipColor, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        } else {
            Text(
                letter ?: "",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun InsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 42.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    )
}

@Composable
private fun ChoiceRow(
    icon: ImageVector? = null,
    letter: String? = null,
    chipColor: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
            .padding(vertical = 7.dp)
    ) {
        IconChip(icon, letter, chipColor)
        Spacer(Modifier.size(12.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (selected) {
            Icon(
                Icons.Filled.Check, contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    icon: ImageVector? = null,
    chipColor: Color = Color(0xFF8E8E93),
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            IconChip(icon, null, chipColor)
            Spacer(Modifier.size(12.dp))
        }
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
