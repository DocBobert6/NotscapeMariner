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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.notscape.browser.BuildConfig
import net.notscape.browser.browser.FilterEngine
import net.notscape.browser.browser.UpdateChecker
import net.notscape.browser.browser.SearchTemplate
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
    var showSiteControls by remember { mutableStateOf(false) }
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
    val settingsScope = rememberCoroutineScope()
    // Custom start-page wallpaper (Phase G): pick a photo, keep a
    // downscaled copy in the app's own storage — local-only, and no
    // permission over the user's library is held afterwards.
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            settingsScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val resolver = context.contentResolver
                        val bounds = android.graphics.BitmapFactory.Options()
                            .apply { inJustDecodeBounds = true }
                        resolver.openInputStream(uri)?.use {
                            android.graphics.BitmapFactory.decodeStream(it, null, bounds)
                        }
                        var sample = 1
                        while (bounds.outWidth / sample > 1440 ||
                            bounds.outHeight / sample > 1440
                        ) sample *= 2
                        val opts = android.graphics.BitmapFactory.Options()
                            .apply { inSampleSize = sample }
                        val bmp = resolver.openInputStream(uri)?.use {
                            android.graphics.BitmapFactory.decodeStream(it, null, opts)
                        } ?: return@runCatching false
                        val outFile = java.io.File(context.filesDir, "start_wallpaper.jpg")
                        java.io.FileOutputStream(outFile).use {
                            bmp.compress(
                                android.graphics.Bitmap.CompressFormat.JPEG, 88, it
                            )
                        }
                        true
                    }.getOrDefault(false)
                }
                if (ok) {
                    store.putSetting("wallpaper", "custom")
                } else {
                    Toast.makeText(
                        context, "Couldn't use that photo", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    if (showSiteControls) {
        SubScreen("Per-site controls", onBack = { showSiteControls = false }) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "JavaScript, images, popups, and ad blocking per site. Changes apply the next time a tab loads that site.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = siteQuery,
                    onValueChange = { siteQuery = it },
                    label = { Text("Search sites") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val hosts = (store.siteSettings.keys + store.history.mapNotNull {
                    runCatching { android.net.Uri.parse(it.url).host }.getOrNull()
                }).filter { !it.isNullOrBlank() }.map { it!! }
                    .filter { siteQuery.isBlank() || it.contains(siteQuery, ignoreCase = true) }
                    .distinct().sorted()
                if (hosts.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No sites yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                hosts.take(60).forEach { host ->
                    val s = store.siteSetting(host)
                    Spacer(Modifier.height(12.dp))
                    SettingsCard {
                        Text(
                            host,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        InsetDivider()
                        ToggleRow(
                            "JavaScript", s.javaScript,
                            icon = Icons.Filled.Code
                        ) { store.putSiteSetting(s.copy(javaScript = it)) }
                        InsetDivider()
                        ToggleRow(
                            "Images", !s.blockImages,
                            icon = Icons.Filled.Image
                        ) { store.putSiteSetting(s.copy(blockImages = !it)) }
                        InsetDivider()
                        // Popups is a three-state setting (blocked /
                        // allowed / always denied): a value row that
                        // cycles on tap, in the house row style.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    store.putSiteSetting(
                                        when {
                                            s.allowPopups -> s.copy(allowPopups = false, blockPopups = true)
                                            s.blockPopups -> s.copy(allowPopups = false, blockPopups = false)
                                            else -> s.copy(allowPopups = true, blockPopups = false)
                                        }
                                    )
                                }
                                .padding(vertical = 7.dp)
                        ) {
                            IconChip(Icons.Filled.OpenInBrowser, null, Color(0xFF8E8E93))
                            Spacer(Modifier.size(12.dp))
                            Text(
                                "Popups", modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                when {
                                    s.allowPopups -> "Allowed"
                                    s.blockPopups -> "Always denied"
                                    else -> "Blocked"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                Icons.Filled.ChevronRight, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        InsetDivider()
                        ToggleRow(
                            "Ad blocking", !s.disableBlocking,
                            icon = Icons.Filled.Block
                        ) { store.putSiteSetting(s.copy(disableBlocking = !it)) }
                        InsetDivider()
                        ToggleRow(
                            "Third-party cookies", s.allowThirdPartyCookies,
                            icon = Icons.Filled.Cookie
                        ) { store.putSiteSetting(s.copy(allowThirdPartyCookies = it)) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        return
    }

    SubScreen("Settings", onBack) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .verticalScroll(rememberScrollState())
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
                ChoiceRow(
                    icon = Icons.Filled.Palette, chipColor = Color(0xFF34C759),
                    label = "Material You", selected = themeMode == "materialyou"
                ) { store.putSetting("themeMode", "materialyou") }
                InsetDivider()
                val barPosition = store.setting("barPosition", "bottom")
                ChoiceRow(
                    icon = Icons.Filled.VerticalAlignBottom, chipColor = Color(0xFF8E8E93),
                    label = "Address bar: Bottom", selected = barPosition == "bottom"
                ) { store.putSetting("barPosition", "bottom") }
                InsetDivider()
                ChoiceRow(
                    icon = Icons.Filled.VerticalAlignTop, chipColor = Color(0xFF8E8E93),
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
                Row {
                    TextButton(onClick = {
                        wallpaperPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    }) {
                        Text(
                            "Custom photo",
                            color = if (wallpaper == "custom") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    if (wallpaper == "custom") {
                        TextButton(onClick = { store.putSetting("wallpaper", "ocean") }) {
                            Text("Remove")
                        }
                    }
                }
                InsetDivider()
                ToggleRow(
                    "Home button in address bar",
                    store.setting("showHomeButton", "true") == "true",
                    icon = Icons.Filled.Home, chipColor = Color(0xFF8E8E93)
                ) { store.putSetting("showHomeButton", it.toString()) }
                InsetDivider()
                ToggleRow(
                    "Immersive full screen",
                    store.setting("immersive", "true") == "true",
                    icon = Icons.Filled.Fullscreen, chipColor = Color(0xFF48484A)
                ) { store.putSetting("immersive", it.toString()) }
                val darkSupported = androidx.webkit.WebViewFeature.isFeatureSupported(
                    androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING
                ) || androidx.webkit.WebViewFeature.isFeatureSupported(
                    androidx.webkit.WebViewFeature.FORCE_DARK
                )
                if (darkSupported) {
                    InsetDivider()
                    val webDark = store.setting("webDarkMode", "Auto")
                    Text(
                        "Darken web pages",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    listOf("Auto", "On", "Off").forEach { mode ->
                        ChoiceRow(
                            icon = Icons.Filled.DarkMode, chipColor = Color(0xFF5856D6),
                            label = mode, selected = webDark == mode
                        ) { store.putSetting("webDarkMode", mode) }
                    }
                    Text(
                        "Applies to pages as they load; already-open pages " +
                            "pick it up on their next load.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                    )
                }
                InsetDivider()
                val textZoom = store.setting("webTextZoom", "100").toIntOrNull() ?: 100
                Text(
                    "Web text size — $textZoom%",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Slider(
                    value = textZoom.toFloat(),
                    onValueChange = { store.putSetting("webTextZoom", it.toInt().toString()) },
                    valueRange = 50f..200f,
                    steps = 14,
                    modifier = Modifier.fillMaxWidth()
                )
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
                InsetDivider()
                ChoiceRow(
                    letter = "C", chipColor = Color(0xFF5856D6),
                    label = "Custom", selected = engine == "custom"
                ) { store.putSetting("searchEngine", "custom") }
                if (engine == "custom") {
                    var customDraft by remember { mutableStateOf(store.setting("customSearchUrl", "")) }
                    var customError by remember { mutableStateOf<String?>(null) }
                    OutlinedTextField(
                        value = customDraft,
                        onValueChange = { customDraft = it; customError = null },
                        label = { Text("Search URL — %s stands for the terms") },
                        singleLine = true,
                        isError = customError != null,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    if (customError != null) {
                        Text(
                            customError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    TextButton(
                        onClick = {
                            val err = SearchTemplate.validate(customDraft)
                            if (err == null) {
                                store.putSetting("customSearchUrl", customDraft.trim())
                            } else {
                                customError = err
                            }
                        }
                    ) { Text("Save search URL") }
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
                    "Block third-party cookies",
                    store.setting("blockThirdPartyCookies", "false") == "true",
                    icon = Icons.Filled.Cookie, chipColor = Color(0xFFA2845E)
                ) { store.putSetting("blockThirdPartyCookies", it.toString()) }
                Text(
                    "Sites that need an exception (some sign-ins do) can be " +
                        "allowed under Per-site controls.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 42.dp, top = 2.dp, bottom = 4.dp)
                )
                InsetDivider()
                ToggleRow(
                    "Restore tabs on launch",
                    store.setting("restoreTabs", "true") == "true",
                    icon = Icons.Filled.Restore, chipColor = Color(0xFF30B0C7)
                ) { store.putSetting("restoreTabs", it.toString()) }
            }

            GroupTitle("Per-site controls")
            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { showSiteControls = true }
                        .padding(vertical = 7.dp)
                ) {
                    IconChip(Icons.Filled.Tune, null, Color(0xFF8E8E93))
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Per-site controls", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "JavaScript, images, popups, blocking by site",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                InsetDivider()
                ToggleRow(
                    "Downloaded EPUBs go to bookshelf",
                    store.setting("epubAutoShelf", "true") == "true",
                    icon = Icons.Filled.LibraryBooks, chipColor = Color(0xFF34C759)
                ) { store.putSetting("epubAutoShelf", it.toString()) }
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
                Text("Bookmarks, history, playlists, downloads list, vault entries, site settings, and favorites stored on this device will be deleted. Downloaded files and books stay in their folders.")
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
    // The design kit's grouped card: bright card on the tinted
    // backdrop the Settings page wears (Phase C sweep — the old
    // surface-on-background pairing was white-on-white in light mode).
    GroupedCard {
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
