package net.notscape.browser.ui

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
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
fun SettingsScreen(store: AppStore, onBack: () -> Unit, onRenew: () -> Unit = {}, onOpenUpdates: () -> Unit = {}, onOpenStartPage: () -> Unit = {}) {
    val rev = store.revision
    val context = LocalContext.current
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmRenew by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<String?>(null) }
    var clearedNote by remember { mutableStateOf("") }
    var siteQuery by remember { mutableStateOf("") }
    var showSiteControls by remember { mutableStateOf(false) }
    // Custom interface font (0.0.975): picked here, validated,
    // copied into app files. Nothing leaves the device.
    var uiFontName by remember {
        mutableStateOf(store.setting("uiFontCustomName", ""))
    }
    val uiFontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            Thread {
                var loadedName: String? = null
                val ok = runCatching {
                    val dir = java.io.File(
                        context.filesDir, "fonts"
                    ).apply { mkdirs() }
                    val tmp = java.io.File(dir, "ui-font.tmp")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    } ?: error("unreadable")
                    val name = runCatching {
                        context.contentResolver.query(
                            uri,
                            arrayOf(
                                android.provider.OpenableColumns.DISPLAY_NAME
                            ),
                            null, null, null
                        )?.use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        }
                    }.getOrNull()
                    // The gate: a usable font starts with known
                    // magic (TrueType / OpenType / TrueType-flavoured)
                    // and Android's typeface loader accepts it.
                    // Collections (.ttc) and web fonts fail the
                    // magic and are refused, never half-applied.
                    val magic = ByteArray(4)
                    val read = tmp.inputStream().use { it.read(magic) }
                    val asText = if (read == 4) {
                        String(magic, Charsets.US_ASCII)
                    } else {
                        ""
                    }
                    val magicOk = read == 4 && (
                        (magic[0] == 0.toByte() && magic[1] == 1.toByte() &&
                            magic[2] == 0.toByte() &&
                            magic[3] == 0.toByte()) ||
                            asText == "OTTO" || asText == "true"
                        )
                    val faceOk = magicOk && runCatching {
                        android.graphics.Typeface
                            .createFromFile(tmp) != null
                    }.getOrDefault(false)
                    if (!faceOk) {
                        tmp.delete()
                        false
                    } else {
                        tmp.copyTo(
                            java.io.File(dir, "ui-font"), overwrite = true
                        )
                        tmp.delete()
                        loadedName = name ?: "Custom font"
                        true
                    }
                }.getOrDefault(false)
                android.os.Handler(
                    android.os.Looper.getMainLooper()
                ).post {
                    if (ok) {
                        uiFontName = loadedName ?: "Custom font"
                        store.putSetting("uiFontCustomName", uiFontName)
                        store.putSetting("uiFont", "custom")
                        Toast.makeText(
                            context, "Font loaded",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "That file couldn't be loaded as a " +
                                "font - .ttf and .otf work best",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.start()
        }
    }
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
                            RowGlyph(Icons.Filled.OpenInBrowser, null)
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
                    icon = Icons.Filled.SettingsBrightness, label = "System default", selected = themeMode == "system"
                ) { store.putSetting("themeMode", "system") }
                InsetDivider()
                ChoiceRow(
                    icon = Icons.Filled.WbSunny, label = "Light", selected = themeMode == "light"
                ) { store.putSetting("themeMode", "light") }
                InsetDivider()
                ChoiceRow(
                    icon = Icons.Filled.DarkMode, label = "Dark", selected = themeMode == "dark"
                ) { store.putSetting("themeMode", "dark") }
                InsetDivider()
                // Material You (Phase G; the stock dropdown popup
                // was retired in 0.0.962, Robert's ruling - it did
                // not match the kit): the row selects the dynamic
                // palette, and its three variants sit inline beneath
                // it as plain kit rows, like every other choice in
                // this card.
                ChoiceRow(
                    icon = Icons.Filled.Palette, label = "Material You",
                    selected = themeMode.startsWith("materialyou")
                ) { store.putSetting("themeMode", "materialyou") }
                if (themeMode.startsWith("materialyou")) {
                    listOf(
                        Triple("Follow system", "materialyou", themeMode == "materialyou"),
                        Triple("Light", "materialyou-light", themeMode == "materialyou-light"),
                        Triple("Dark", "materialyou-dark", themeMode == "materialyou-dark")
                    ).forEach { (label, value, active) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { store.putSetting("themeMode", value) }
                                .padding(vertical = 7.dp)
                        ) {
                            Spacer(Modifier.size(44.dp))
                            Text(
                                label, modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (active) {
                                Icon(
                                    Icons.Filled.Check, contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                InsetDivider()
                val barPosition = store.setting("barPosition", "bottom")
                ChoiceRow(
                    icon = Icons.Filled.VerticalAlignBottom, label = "Address bar: Bottom", selected = barPosition == "bottom"
                ) { store.putSetting("barPosition", "bottom") }
                InsetDivider()
                ChoiceRow(
                    icon = Icons.Filled.VerticalAlignTop, label = "Address bar: Top", selected = barPosition == "top"
                ) { store.putSetting("barPosition", "top") }

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
                            icon = Icons.Filled.DarkMode, label = mode, selected = webDark == mode
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
                InsetDivider()
                Text(
                    "Interface font",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
                Text(
                    "Dresses the app's own text. The wordmark and " +
                        "the reader keep theirs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                val uiFont = store.setting("uiFont", "default")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "default" to "Default",
                        "opensans" to "Open Sans",
                        "roboto" to "Roboto",
                        "inter" to "Inter",
                        "custom" to "Custom"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = uiFont == value,
                            onClick = {
                                if (value == "custom" &&
                                    !java.io.File(
                                        context.filesDir, "fonts/ui-font"
                                    ).exists()
                                ) {
                                    uiFontPicker.launch(arrayOf("*/*"))
                                } else {
                                    store.putSetting("uiFont", value)
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
                if (uiFont == "custom") {
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedButton(onClick = {
                            uiFontPicker.launch(arrayOf("*/*"))
                        }) { Text("Replace font file") }
                        Spacer(Modifier.size(8.dp))
                        OutlinedButton(onClick = {
                            java.io.File(
                                context.filesDir, "fonts/ui-font"
                            ).delete()
                            store.putSetting("uiFont", "default")
                        }) { Text("Remove") }
                    }
                    if (uiFontName.isNotBlank()) {
                        Text(
                            uiFontName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Interface text size",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                val uiFontScale = store.setting("uiFontScale", "1.0")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "0.9" to "Compact",
                        "1.0" to "Default",
                        "1.15" to "Large",
                        "1.3" to "Extra large"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = uiFontScale == value,
                            onClick = {
                                store.putSetting("uiFontScale", value)
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "The quick brown fox jumps over the lazy dog.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            GroupTitle("Start page")
            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenStartPage() }
                        .padding(vertical = 7.dp)
                ) {
                    RowGlyph(Icons.Filled.Image, null)
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Start page options", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Wallpaper, banner, sections and homepage",
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
            GroupTitle("Browsing")
            SettingsCard {
                ToggleRow(
                    "Immersive full screen",
                    store.setting("immersive", "false") == "true",
                    icon = Icons.Filled.Fullscreen
                ) { store.putSetting("immersive", it.toString()) }
                InsetDivider()
                ToggleRow(
                    "Restore tabs on launch",
                    store.setting("restoreTabs", "true") == "true",
                    icon = Icons.Filled.Restore
                ) { store.putSetting("restoreTabs", it.toString()) }
            }

            GroupTitle("Search")
            SettingsCard {
                val engine = store.setting("searchEngine", "duckduckgo")
                val engines = listOf(
                    Triple("duckduckgo", "DuckDuckGo (No AI)", "duckduckgo.com"),
                    Triple("startpage", "Startpage", "startpage.com"),
                    Triple("google", "Google", "google.com"),
                    Triple("bing", "Bing", "bing.com")
                )
                engines.forEachIndexed { i, (value, label, host) ->
                    if (i > 0) InsetDivider()
                    ChoiceRow(
                        faviconHost = host,
                        label = label, selected = engine == value
                    ) { store.putSetting("searchEngine", value) }
                }
                InsetDivider()
                ChoiceRow(
                    icon = Icons.Filled.Tune,
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

            GroupTitle("Privacy & blocking")
            SettingsCard {
                ToggleRow(
                    "Block ads (filter lists)",
                    store.setting("blockAds", "true") == "true",
                    icon = Icons.Filled.Block
                ) { store.putSetting("blockAds", it.toString()) }
                InsetDivider()
                ToggleRow(
                    "Block trackers",
                    store.setting("blockTrackers", "true") == "true",
                    icon = Icons.Filled.VisibilityOff
                ) { store.putSetting("blockTrackers", it.toString()) }
                val filterStatus = when {
                    FilterEngine.updating -> "Updating filter lists…"
                    FilterEngine.ruleCount > 0 -> {
                        val whenText = if (FilterEngine.updatedAt > 0)
                            DateFormat.getDateInstance().format(Date(FilterEngine.updatedAt)) else "—"
                        "${FilterEngine.ruleCount} rules · updated $whenText · ${FilterEngine.blockedCount} blocked this session"
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
                    icon = Icons.Filled.PictureInPictureAlt
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
                    icon = Icons.Filled.Cookie
                ) {
                    store.putSetting("cookies", it.toString())
                    CookieManager.getInstance().setAcceptCookie(it)
                }
                InsetDivider()
                ToggleRow(
                    "Block third-party cookies",
                    store.setting("blockThirdPartyCookies", "false") == "true",
                    icon = Icons.Filled.Cookie
                ) { store.putSetting("blockThirdPartyCookies", it.toString()) }
                Text(
                    "Sites that need an exception (some sign-ins do) can be " +
                        "allowed under Per-site controls.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 42.dp, top = 2.dp, bottom = 4.dp)
                )

            }

            GroupTitle("Per-site controls")
            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { showSiteControls = true }
                        .padding(vertical = 7.dp)
                ) {
                    RowGlyph(Icons.Filled.Tune, null)
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
                    letter = "D", label = "Downloads folder", selected = dlLoc == "public"
                ) { store.putSetting("downloadLocation", "public") }
                InsetDivider()
                ChoiceRow(
                    letter = "A", label = "App folder (private)", selected = dlLoc == "app"
                ) { store.putSetting("downloadLocation", "app") }
                InsetDivider()
                ToggleRow(
                    "Downloaded EPUBs go to bookshelf",
                    store.setting("epubAutoShelf", "true") == "true",
                    icon = Icons.Filled.LibraryBooks
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
                OutlinedButton(
                    onClick = { confirmRenew = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Autorenew, contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF599F5C)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Renew — clear & close")
                }
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

            GroupTitle("Updates")
            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenUpdates() }
                        .padding(vertical = 7.dp)
                ) {
                    RowGlyph(Icons.Filled.Refresh, null)
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Check for updates", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (UpdateChecker.availableVersion != null) {
                                "Version ${UpdateChecker.availableVersion} is available"
                            } else {
                                "You're on ${BuildConfig.VERSION_NAME} (beta)"
                            },
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

            GroupTitle("About")
            SettingsCard {
                Text("Notscape Mariner — version ${BuildConfig.VERSION_NAME} (beta)")
                Text("Package: net.notscape.browser")
                Text("Local-only and private: your data is stored on this device.")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmRenew) {
        FrostDialog(
            onDismissRequest = { confirmRenew = false },
            title = { Text("Renew — clear & close?") },
            text = {
                Text(
                    "Cookies, site storage, cache and history are cleared, " +
                        "and every tab is closed. Bookmarks, downloads, books " +
                        "and the vault are kept."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmRenew = false; onRenew() }) {
                    Text("Clear & close")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRenew = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmClearAll) {
        FrostDialog(
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
        FrostDialog(
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
fun GroupTitle(text: String) {
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
fun SettingsCard(content: @Composable () -> Unit) {
    // The design kit's grouped card: bright card on the tinted
    // backdrop the Settings page wears (Phase C sweep — the old
    // surface-on-background pairing was white-on-white in light mode).
    GroupedCard {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            content()
        }
    }
}

/** A bare glyph with no tile behind it (0.0.990,
 * Robert): the kit look is the icon itself, tinted to
 * follow the theme. */
@Composable
private fun RowGlyph(icon: ImageVector?, letter: String?) {
    Box(
        modifier = Modifier.size(30.dp),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                letter ?: "",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

private val engineFaviconCache =
    java.util.concurrent.ConcurrentHashMap<String, android.graphics.Bitmap>()

private fun loadEngineFavicon(
    context: android.content.Context, host: String
): android.graphics.Bitmap? {
    engineFaviconCache[host]?.let { return it }
    val dir = java.io.File(context.filesDir, "favicons")
        .apply { mkdirs() }
    val file = java.io.File(dir, "$host.png")
    if (file.exists()) {
        val cached = runCatching {
            android.graphics.BitmapFactory
                .decodeFile(file.absolutePath)
        }.getOrNull()
        if (cached != null) {
            engineFaviconCache[host] = cached
            return cached
        }
    }
    val fetched = runCatching {
        val conn = java.net.URL("https://$host/favicon.ico")
            .openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.setRequestProperty(
            "User-Agent", "Mozilla/5.0 (Linux; Android 14)"
        )
        conn.inputStream.use {
            android.graphics.BitmapFactory.decodeStream(it)
        }
    }.getOrNull()
    if (fetched != null) {
        engineFaviconCache[host] = fetched
        runCatching {
            file.outputStream().use { out ->
                fetched.compress(
                    android.graphics.Bitmap.CompressFormat.PNG,
                    100, out
                )
            }
        }
    }
    return fetched
}

/** A search engine's real favicon (0.0.990, Robert),
 * fetched once and cached; a shapeless letter stands in
 * until it arrives, and for good if it never does. */
@Composable
private fun EngineFavicon(host: String, letter: String) {
    val context = androidx.compose.ui.platform
        .LocalContext.current
    var bmp by remember(host) {
        mutableStateOf(engineFaviconCache[host])
    }
    LaunchedEffect(host) {
        if (bmp == null) {
            bmp = withContext(Dispatchers.IO) {
                loadEngineFavicon(context, host)
            }
        }
    }
    Box(
        modifier = Modifier.size(30.dp),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            androidx.compose.foundation.Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                letter,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun InsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 42.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    )
}

@Composable
private fun ChoiceRow(
    icon: ImageVector? = null,
    letter: String? = null,
    faviconHost: String? = null,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
            .padding(vertical = 7.dp)
    ) {
        if (faviconHost != null) {
            EngineFavicon(faviconHost, label.first().toString())
        } else {
            RowGlyph(icon, letter)
        }
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
fun ToggleRow(
    label: String,
    checked: Boolean,
    icon: ImageVector? = null,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            RowGlyph(icon, null)
            Spacer(Modifier.size(12.dp))
        }
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Wallpaper adjuster (0.0.965, Robert): the picked photo is
 * framed at the screen's own aspect - drag to move, pinch to
 * zoom (the photo always covers the frame). Confirming renders
 * exactly what the frame shows, so the saved wallpaper fills
 * the start page edge to edge at any size.
 */
@Composable
fun WallpaperAdjustDialog(
    bitmap: android.graphics.Bitmap,
    onCancel: () -> Unit,
    onConfirm: (android.graphics.Bitmap) -> Unit
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    fun coverScale(w: Int, h: Int): Float =
        maxOf(w.toFloat() / bitmap.width, h.toFloat() / bitmap.height)
    fun clamped(o: Offset, z: Float, w: Int, h: Int): Offset {
        val s = coverScale(w, h) * z
        val maxX = ((bitmap.width * s - w) / 2f).coerceAtLeast(0f)
        val maxY = ((bitmap.height * s - h) / 2f).coerceAtLeast(0f)
        return Offset(
            o.x.coerceIn(-maxX, maxX),
            o.y.coerceIn(-maxY, maxY)
        )
    }
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Canvas(
                modifier = Modifier.fillMaxSize()
                    .onSizeChanged { frame = it }
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            val z = (zoom * gestureZoom).coerceIn(1f, 5f)
                            zoom = z
                            offset = clamped(
                                offset + pan, z, size.width, size.height
                            )
                        }
                    }
            ) {
                val s = coverScale(
                    size.width.toInt(), size.height.toInt()
                ) * zoom
                val dw = (bitmap.width * s).toInt()
                val dh = (bitmap.height * s).toInt()
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = IntOffset(
                        ((size.width - dw) / 2f + offset.x).toInt(),
                        ((size.height - dh) / 2f + offset.y).toInt()
                    ),
                    dstSize = IntSize(dw, dh)
                )
            }
            Text(
                "Drag to move, pinch to zoom. The photo will fill the start page.",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(20.dp)
            )
            Row(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    border = BorderStroke(
                        1.dp, Color.White.copy(alpha = 0.6f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) { Text("Cancel") }
                Button(onClick = {
                    val w = frame.width
                    val h = frame.height
                    if (w > 0 && h > 0) {
                        val k = (1440f / maxOf(w, h)).coerceAtMost(1f)
                        val outW = (w * k).toInt().coerceAtLeast(1)
                        val outH = (h * k).toInt().coerceAtLeast(1)
                        val out = android.graphics.Bitmap.createBitmap(
                            outW, outH,
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        val canvas = android.graphics.Canvas(out)
                        val s = coverScale(w, h) * zoom * k
                        val m = android.graphics.Matrix()
                        m.postTranslate(
                            -bitmap.width / 2f, -bitmap.height / 2f
                        )
                        m.postScale(s, s)
                        m.postTranslate(
                            outW / 2f + offset.x * k,
                            outH / 2f + offset.y * k
                        )
                        canvas.drawBitmap(
                            bitmap, m,
                            android.graphics.Paint(
                                android.graphics.Paint.FILTER_BITMAP_FLAG
                            )
                        )
                        onConfirm(out)
                    }
                }) { Text("Use this photo") }
            }
        }
    }
}
