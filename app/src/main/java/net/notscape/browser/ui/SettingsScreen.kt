package net.notscape.browser.ui

import android.content.Intent
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
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
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.History
import net.notscape.browser.data.clearRangeCutoff
import net.notscape.browser.data.countHistoryInRange
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.notscape.browser.R
import net.notscape.browser.Trace
import net.notscape.browser.BuildConfig
import net.notscape.browser.browser.FilterEngine
import net.notscape.browser.browser.filterSettingsSearch
import net.notscape.browser.browser.UserBlockRules
import net.notscape.browser.browser.UpdateChecker
import net.notscape.browser.browser.SearchTemplate
import net.notscape.browser.browser.PrivateProfile
import net.notscape.browser.data.AppStore
import net.notscape.browser.data.EncryptedBackup
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(store: AppStore, onBack: () -> Unit, onRenew: () -> Unit = {}, onOpenScreen: (Screen) -> Unit = {}, onOpenUrl: (String) -> Unit = {}, onOpenUpdates: () -> Unit = {}, onOpenStartPage: () -> Unit = {}, onReplayWelcome: () -> Unit = {}, onErasePrivate: () -> Unit = {}, erasePrivateResult: Boolean? = null, onErasePrivateResultConsumed: () -> Unit = {}) {
    val rev = store.revision
    val context = LocalContext.current
    var confirmClearAll by remember { mutableStateOf(false) }
    var confirmRenew by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<String?>(null) }
    var clearedNote by remember { mutableStateOf("") }
    // F-M7: the erase handshake reports back through
    // MainActivity — the note must reflect whether the
    // private tabs actually closed, not claim success
    // before the wipe ran.
    androidx.compose.runtime.LaunchedEffect(erasePrivateResult) {
        if (erasePrivateResult == null) return@LaunchedEffect
        clearedNote = if (erasePrivateResult == true) {
            if (!PrivateProfile.supported()) {
                "All on-device data erased, except private-tab cookies and " +
                    "storage: this device's WebView provider does not isolate " +
                    "private tabs, so that data may remain."
            } else {
                "All on-device data erased."
            }
        } else {
            "Private tabs could not be closed, so private browsing data may remain. Everything else was erased."
        }
        // Consume it so reopening Settings later does not
        // show a stale erase note as if it were fresh.
        onErasePrivateResultConsumed()
    }
    var encImportText by remember { mutableStateOf<String?>(null) }
    var decrypting by remember { mutableStateOf(false) }
    val importScope = rememberCoroutineScope()
    var pendingEncPass by remember { mutableStateOf("") }
    var showEncExport by remember { mutableStateOf(false) }
    var siteQuery by remember { mutableStateOf("") }
    var showSiteControls by remember { mutableStateOf(false) }
    // Audit P-m8: the host list paginates instead of
    // stopping at 60.
    var siteLimit by remember { mutableStateOf(60) }
    // Beta.59 (S6): which per-site host card is open;
    // null means all collapsed (a search expands matches).
    var expandedHost by remember { mutableStateOf<String?>(null) }
    var showBlockedElements by remember { mutableStateOf(false) }
    var showBlockImport by remember { mutableStateOf(false) }
    var showNotices by remember { mutableStateOf(false) }
    var blockImportText by remember { mutableStateOf("") }
    var blockImportResult by remember { mutableStateOf("") }
    // Settings hub (0.0.991, Robert): the sections live
    // on their own pages now; "hub" is the index page.
    var page by remember { mutableStateOf("hub") }
    // Beta.65 #12: hub search — filters the settings
    // rows by name; a match opens the page it lives on.
    var hubQuery by remember { mutableStateOf("") }
    val hubSearching = page == "hub" && hubQuery.isNotBlank()
    BackHandler(enabled = page != "hub" && !showSiteControls) {
        page = "hub"
    }
    // Per-site controls is a panel over the hub, not
    // a page: system back must close it, not leave
    // Settings the way the header back button does.
    BackHandler(enabled = showSiteControls) {
        showSiteControls = false
    }
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
    val exportEncLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(
                        store.exportBackupEncrypted(pendingEncPass)
                            .toByteArray()
                    )
                }
                Toast.makeText(
                    context, "Encrypted backup saved",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(
                    context, "Backup failed", Toast.LENGTH_SHORT
                ).show()
            }
            pendingEncPass = ""
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText().orEmpty()
                if (EncryptedBackup.isEncrypted(text)) {
                    encImportText = text
                } else if (text.isNotBlank()) {
                    confirmImport = text
                }
            }
        }
    }
    // Audit P-m26: Netscape-format HTML bookmark import.
    val htmlImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText().orEmpty()
                val items = net.notscape.browser.browser.BookmarkHtml.parse(text)
                val added = store.importBookmarks(items)
                clearedNote = "Imported $added bookmarks from HTML."
            }
        }
    }
    // Beta.59 (S2): Favorites and Reading list are
    // settings sub-pages now, not one-way doors to
    // standalone screens - back returns to the hub.
    if (page == "favorites") {
        FavoritesScreen(
            store = store,
            onBack = { page = "hub" },
            onOpenUrl = onOpenUrl
        )
        return
    }
    if (page == "readingList") {
        ReadingListScreen(
            store = store,
            onBack = { page = "hub" },
            onOpenUrl = onOpenUrl
        )
        return
    }
    if (page == "blocked") {
        BlockedScreen(
            onBack = { page = "hub" }
        )
        return
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
                    "JavaScript, images, popups, blocking, zoom, and permissions per site. Changes reload the open tab on that site. Tap a row to change its setting.",
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
                hosts.take(siteLimit).forEach { host ->
                    val s = store.siteSetting(host)
                    // Beta.59 (S6): host cards start collapsed.
                    val hostExpanded = siteQuery.isNotBlank() ||
                        expandedHost == host
                    Spacer(Modifier.height(12.dp))
                    SettingsCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    expandedHost =
                                        if (expandedHost == host) null else host
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                host,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (hostExpanded) Icons.Filled.KeyboardArrowUp
                                else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (hostExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (hostExpanded) {
                        InsetDivider()
                        ToggleRow(
                            "JavaScript", s.javaScript,
                            icon = Icons.Filled.Code
                        ) {
                            store.putSiteSetting(s.copy(javaScript = it))
                            store.requestSiteReload(host)
                        }
                        InsetDivider()
                        ToggleRow(
                            "Images", !s.blockImages,
                            icon = Icons.Filled.Image
                        ) {
                            store.putSiteSetting(s.copy(blockImages = !it))
                            store.requestSiteReload(host)
                        }
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
                                    store.requestSiteReload(host)
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
                                Icons.Filled.KeyboardArrowDown, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        InsetDivider()
                        ToggleRow(
                            "Ad blocking", !s.disableBlocking,
                            icon = Icons.Filled.Block
                        ) {
                            store.putSiteSetting(s.copy(disableBlocking = !it))
                            store.requestSiteReload(host)
                        }
                        InsetDivider()
                        ToggleRow(
                            "Third-party cookies", s.allowThirdPartyCookies,
                            icon = Icons.Filled.Cookie
                        ) {
                            store.putSiteSetting(s.copy(allowThirdPartyCookies = it))
                            store.requestSiteReload(host)
                        }
                        InsetDivider()
                        // Beta.63 #24: a remembered HTTPS-only
                        // exception for this site — only shown
                        // while HTTPS-only mode is on, since
                        // that is the only time it does anything.
                        if (store.setting("httpsOnly", "false") == "true") {
                            ToggleRow(
                                "Allow plain HTTP", store.isHttpsException(host),
                                icon = Icons.Filled.Lock
                            ) {
                                store.setHttpsException(host, it)
                                store.requestSiteReload(host)
                            }
                            InsetDivider()
                        }
                        // Audit P-m8/P-m29: per-site text zoom
                        // (0 = the global size).
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .padding(vertical = 7.dp)
                        ) {
                            RowGlyph(Icons.Filled.TextFields, null)
                            Spacer(Modifier.size(12.dp))
                            Text(
                                "Text zoom", modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            val zoom = store.siteZoom(host)
                            TextButton(
                                onClick = {
                                    val next = (if (zoom <= 0) 100 else zoom) - 25
                                    store.putSiteZoom(host, next.coerceAtLeast(50))
                                    store.requestSiteReload(host)
                                }
                            ) { Text("−") }
                            Text(
                                if (zoom <= 0) "Default" else "$zoom%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    val next = (if (zoom <= 0) 100 else zoom) + 25
                                    store.putSiteZoom(host, next.coerceAtMost(200))
                                    store.requestSiteReload(host)
                                }
                            ) { Text("+") }
                            if (zoom > 0) {
                                TextButton(onClick = {
                                    store.putSiteZoom(host, 0)
                                    store.requestSiteReload(host)
                                }) { Text("Reset") }
                            }
                        }
                        InsetDivider()
                        // Audit P-m8: per-site desktop mode.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    val cur = store.siteDesktop(host)
                                    store.putSiteDesktop(
                                        host,
                                        when (cur) {
                                            null -> true
                                            true -> false
                                            false -> null
                                        }
                                    )
                                    store.requestSiteReload(host)
                                }
                                .padding(vertical = 7.dp)
                        ) {
                            RowGlyph(Icons.Filled.DesktopWindows, null)
                            Spacer(Modifier.size(12.dp))
                            Text(
                                "Desktop site", modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                when (store.siteDesktop(host)) {
                                    true -> "On"
                                    false -> "Off"
                                    null -> "Default"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowDown, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        InsetDivider()
                        // Audit P-m17: per-site autoplay.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    val cur = store.siteAutoplay(host)
                                    store.putSiteAutoplay(
                                        host,
                                        when (cur) {
                                            "" -> "block"
                                            "block" -> "muted"
                                            "muted" -> "allow"
                                            else -> ""
                                        }
                                    )
                                    store.requestSiteReload(host)
                                }
                                .padding(vertical = 7.dp)
                        ) {
                            RowGlyph(Icons.Filled.PlayArrow, null)
                            Spacer(Modifier.size(12.dp))
                            Text(
                                "Autoplay", modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                when (store.siteAutoplay(host)) {
                                    "block" -> "Blocked"
                                    "muted" -> "Muted only"
                                    "allow" -> "Allowed"
                                    else -> "Default"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowDown, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        // Audit P-M2: per-site permission
                        // decisions, shown in the panel
                        // where the prompt said they'd be.
                        listOf(
                            "camera" to "Camera",
                            "microphone" to "Microphone",
                            "geolocation" to "Location"
                        ).forEach { (resource, label) ->
                            InsetDivider()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        val cur = store.sitePermission(host, resource)
                                        store.putSitePermission(
                                            host, resource,
                                            when (cur) {
                                                "ask" -> "allow"
                                                "allow" -> "deny"
                                                else -> "ask"
                                            }
                                        )
                                        store.requestSiteReload(host)
                                    }
                                    .padding(vertical = 7.dp)
                            ) {
                                RowGlyph(Icons.Filled.Lock, null)
                                Spacer(Modifier.size(12.dp))
                                Text(
                                    label, modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    when (store.sitePermission(host, resource)) {
                                        "allow" -> "Allowed"
                                        "deny" -> "Denied"
                                        else -> "Ask"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    Icons.Filled.KeyboardArrowDown, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        InsetDivider()
                        // Audit P-m8: clear this site's data.
                        TextButton(
                            onClick = {
                                val cm = android.webkit.CookieManager.getInstance()
                                val url = "https://$host"
                                cm.getCookie(url)?.split(";")?.forEach { part ->
                                    val name = part.substringBefore("=").trim()
                                    if (name.isNotEmpty()) {
                                        cm.setCookie(
                                            url,
                                            "$name=; Expires=Wed, 01 Jan 2020 00:00:00 GMT; Path=/"
                                        )
                                    }
                                }
                                cm.flush()
                                runCatching {
                                    android.webkit.WebStorage.getInstance()
                                        .deleteOrigin("https://$host")
                                }
                                Toast.makeText(
                                    context, "Data cleared for $host",
                                    Toast.LENGTH_SHORT
                                ).show()
                                store.requestSiteReload(host)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Clear data for this site") }
                        }
                    }
                }
                if (hosts.size > siteLimit) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = { siteLimit += 60 },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Show more sites") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        return
    }

    SubScreen(
        title = when (page) {
            "appearance" -> "Appearance"
            "browsing" -> "Browsing"
            "search" -> "Search"
            "privacy" -> "Privacy & blocking"
            "data" -> "Data"
            "video" -> "Video player"
            "scripts" -> "User scripts"
            "accessibility" -> "Accessibility"
            "blocked" -> "Blocked"
            else -> "Settings"
        },
        onBack = if (page == "hub") {
            onBack
        } else {
            { page = "hub" }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (page == "hub") {
                // Renew lives at the bottom of the hub
                // (0.0.994, Robert): an occasional action
                // belongs below the settings proper, not
                // pinned over them.
                OutlinedTextField(
                    value = hubQuery,
                    onValueChange = { hubQuery = it },
                    singleLine = true,
                    placeholder = { Text("Search settings") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp)
                )
                if (hubSearching) {
                    val hits = filterSettingsSearch(hubQuery)
                    SettingsCard {
                        if (hits.isEmpty()) {
                            Text(
                                "No settings match \u201c${hubQuery.trim()}\u201d.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            hits.forEachIndexed { i, hit ->
                                if (i > 0) InsetDivider()
                                HubRow(hit.title, hit.subtitle, Icons.Filled.Search) {
                                    hubQuery = ""
                                    page = hit.page
                                }
                            }
                        }
                    }
                }
                if (!hubSearching) {
                GroupTitle("Settings")
                SettingsCard {
                    HubRow(
                        "Appearance",
                        "Theme, wallpaper, interface font",
                        Icons.Filled.Palette
                    ) { page = "appearance" }
                    InsetDivider()
                    HubRow(
                        "Browsing",
                        "Full screen, tab restore",
                        Icons.Filled.Tune
                    ) { page = "browsing" }
                    InsetDivider()
                    HubRow(
                        "Search",
                        "Search engine",
                        Icons.Filled.Search
                    ) { page = "search" }
                    InsetDivider()
                    HubRow(
                        "Privacy & blocking",
                        "Ad and tracker blocking, cookies",
                        Icons.Filled.Block
                    ) { page = "privacy" }
                    InsetDivider()
                    HubRow(
                        "Video player",
                        "Skip interval, subtitles, playback",
                        Icons.Filled.PlayArrow
                    ) { page = "video" }
                    InsetDivider()
                    HubRow(
                        "Data",
                        "Backup, import, clear data",
                        Icons.Filled.Storage
                    ) { page = "data" }
                    InsetDivider()
                    HubRow(
                        "User scripts",
                        "Your code, run on pages you choose",
                        Icons.Filled.Code
                    ) { page = "scripts" }
                    InsetDivider()
                    HubRow(
                        "Favorites",
                        "Sites you starred, searchable",
                        Icons.Filled.Star
                    ) { page = "favorites" }
                    InsetDivider()
                    HubRow(
                        "Reading list",
                        "Pages saved to read later",
                        Icons.Filled.MenuBook
                    ) { page = "readingList" }
                    InsetDivider()
                    HubRow(
                        "Accessibility",
                        "Reduced motion, text size",
                        Icons.Filled.Accessibility
                    ) { page = "accessibility" }
                }
                }
            }
            if (page == "appearance") {
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
                // Beta.59 (S3): the slider used to save on
                // every drag tick (a full synchronous save
                // under lock each time). Drag only moves a
                // local draft now; the store saves once, on
                // release.
                var textZoomDraft by remember { mutableStateOf<Int?>(null) }
                val shownZoom = textZoomDraft ?: textZoom
                Text(
                    "Web text size — $shownZoom%",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Slider(
                    value = shownZoom.toFloat(),
                    onValueChange = { textZoomDraft = it.toInt() },
                    onValueChangeFinished = {
                        textZoomDraft?.let {
                            store.putSetting("webTextZoom", it.toString())
                        }
                        textZoomDraft = null
                    },
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

            }
            if (page == "hub" && !hubSearching) {
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
            }
            if (page == "browsing") {
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
                InsetDivider()
                ToggleRow(
                    "Open links in a new tab",
                    store.setting("openLinksInNewTab", "false") == "true",
                    icon = Icons.Filled.OpenInBrowser
                ) { store.putSetting("openLinksInNewTab", it.toString()) }
            }

            }
            if (page == "search") {
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

            }
            if (page == "privacy") {
            GroupTitle("Blocking")
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
                // Audit P-m14: per-list toggles, a custom
                // list URL, and a free-text rule box.
                Text(
                    "Filter lists",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 42.dp, top = 8.dp, bottom = 4.dp)
                )
                val listNames = listOf(
                    "EasyList", "EasyPrivacy", "StevenBlack hosts",
                    "AdGuard Tracking Protection", "AdGuard Mobile Ads",
                    "uBlock Origin filters", "AdGuard Base"
                )
                var offTick by remember { mutableStateOf(0) }
                val offSet = remember(offTick) {
                    store.setting("filterListsOff", "")
                        .split(",").mapNotNull { it.toIntOrNull() }.toSet()
                }
                listNames.forEachIndexed { i, name ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 42.dp, top = 2.dp, bottom = 2.dp)
                            .clickable {
                                val next = if (i in offSet) offSet - i else offSet + i
                                store.putSetting(
                                    "filterListsOff",
                                    next.sorted().joinToString(",")
                                )
                                FilterEngine.setListConfig(
                                    next,
                                    store.setting("customFilterUrl", ""),
                                    store.setting("customAbpRules", "")
                                )
                                FilterEngine.reloadLists(context)
                                offTick++
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            name, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = i !in offSet,
                            onCheckedChange = null
                        )
                    }
                }
                var customUrlDraft by remember {
                    mutableStateOf(store.setting("customFilterUrl", ""))
                }
                OutlinedTextField(
                    value = customUrlDraft,
                    onValueChange = { customUrlDraft = it },
                    label = { Text("Custom list URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 42.dp, top = 8.dp)
                )
                TextButton(onClick = {
                    val url = customUrlDraft.trim()
                    store.putSetting("customFilterUrl", url)
                    FilterEngine.setListConfig(
                        offSet, url, store.setting("customAbpRules", "")
                    )
                    FilterEngine.refreshAsync(context)
                }) { Text("Fetch custom list") }
                var abpDraft by remember {
                    mutableStateOf(store.setting("customAbpRules", ""))
                }
                var abpError by remember { mutableStateOf("") }
                OutlinedTextField(
                    value = abpDraft,
                    onValueChange = { abpDraft = it },
                    label = { Text("Custom filter rules (one per line)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 42.dp, top = 4.dp)
                )
                if (abpError.isNotBlank()) {
                    Text(
                        abpError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 42.dp)
                    )
                }
                TextButton(onClick = {
                    val bad = FilterEngine.validateAbpRules(abpDraft)
                    if (bad.isNotEmpty()) {
                        abpError = "Not valid filter rules: lines " +
                            bad.joinToString(", ") + "."
                    } else {
                        abpError = ""
                        store.putSetting("customAbpRules", abpDraft)
                        FilterEngine.setListConfig(
                            offSet,
                            store.setting("customFilterUrl", ""),
                            abpDraft
                        )
                        FilterEngine.reloadLists(context)
                    }
                }) { Text("Save rules") }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 42.dp, top = 6.dp, bottom = 6.dp)
                        .clickable {
                            // Beta.63 #26: flush today's counts
                            // before opening the detail page, so
                            // it shows the current session too.
                            FilterEngine.flushBlockedHistory()?.let { snap ->
                                store.putSetting("blockedHistory", snap)
                            }
                            page = "blocked"
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Blocked this session: ${FilterEngine.blockedCount}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Filled.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                InsetDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 42.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val netCount = UserBlockRules.parseNet(
                        store.setting(UserBlockRules.NET_KEY, "")
                    ).size
                    val cosCount = UserBlockRules.parseCosmetic(
                        store.setting(UserBlockRules.COSMETIC_KEY, "")
                    ).size
                    Text(
                        "Blocked elements",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (netCount + cosCount == 0) {
                            "None yet"
                        } else {
                            "$netCount network · $cosCount on sites"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = { showBlockedElements = true }
                    ) { Text("Manage") }
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
            }
            GroupTitle("Cookies & storage")
            SettingsCard {
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
                    store.setting("blockThirdPartyCookies", "true") == "true",
                    icon = Icons.Filled.Cookie
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
                    "Session-only cookies",
                    store.setting("sessionCookies", "false") == "true",
                    icon = Icons.Filled.Cookie
                ) { store.putSetting("sessionCookies", it.toString()) }
                Text(
                    "Cookies are cleared when the browser leaves the foreground.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 42.dp, top = 2.dp, bottom = 4.dp)
                )
                InsetDivider()
                ToggleRow(
                    "DOM storage",
                    store.setting("domStorage", "true") == "true",
                    icon = Icons.Filled.Storage
                ) { store.putSetting("domStorage", it.toString()) }
                Text(
                    "Turn off to stop sites storing data in DOM storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 42.dp, top = 2.dp, bottom = 4.dp)
                )
            }
            GroupTitle("Connection")
            SettingsCard {
                ToggleRow(
                    "HTTPS-only mode",
                    store.setting("httpsOnly", "false") == "true",
                    icon = Icons.Filled.Lock
                ) { store.putSetting("httpsOnly", it.toString()) }
                Text(
                    "Plain-http pages show a warning first; you can allow one site once.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 42.dp, top = 2.dp, bottom = 4.dp)
                )
                InsetDivider()
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 42.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Autoplay",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val ap = store.setting("autoplay", "allow")
                    TextButton(onClick = {
                        store.putSetting(
                            "autoplay",
                            when (ap) {
                                "allow" -> "block"
                                "block" -> "muted"
                                else -> "allow"
                            }
                        )
                    }) {
                        Text(
                            when (ap) {
                                "block" -> "Blocked"
                                "muted" -> "Muted only"
                                else -> "Allowed"
                            }
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                InsetDivider()
                ToggleRow(
                    "Send Global Privacy Control",
                    store.setting("gpc", "true") == "true",
                    icon = Icons.Filled.VisibilityOff
                ) { store.putSetting("gpc", it.toString()) }
                Text(
                    "Tells sites not to sell or share your data (Sec-GPC: 1).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 42.dp, top = 2.dp, bottom = 4.dp)
                )
                InsetDivider()
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 42.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Referer policy",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val policy = store.setting("refererPolicy", "")
                    TextButton(onClick = {
                        store.putSetting(
                            "refererPolicy",
                            when (policy) {
                                "" -> "origin"
                                "origin" -> "never"
                                else -> ""
                            }
                        )
                    }) {
                        Text(
                            when (policy) {
                                "origin" -> "Origin only"
                                "never" -> "Never"
                                else -> "Default"
                            }
                        )
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (!PrivateProfile.supported()) {
                    Text(
                        "Private tabs on this device are not fully isolated: " +
                            "this WebView provider does not support separate " +
                            "profiles, so private-tab cookies and storage are " +
                            "shared with normal tabs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 42.dp, top = 2.dp, bottom = 4.dp)
                    )
                }

            }

            }
            if (page == "hub" && !hubSearching) {
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

            }
            if (page == "data") {
            GroupTitle("Storage")
            SettingsCard {
                StorageSection()
            }
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
                ToggleRow(
                    "Use segmented downloads",
                    store.setting("segmentedDownloads", "true") == "true",
                    icon = Icons.Filled.VerticalAlignBottom
                ) { store.putSetting("segmentedDownloads", it.toString()) }
                ToggleRow(
                    "Ask before downloading",
                    store.setting("confirmDownloads", "true") == "true",
                    icon = Icons.Filled.Download
                ) { store.putSetting("confirmDownloads", it.toString()) }
                ToggleRow(
                    stringResource(R.string.wifi_only_downloads),
                    store.setting("wifiOnlyDownloads", "false") == "true",
                    icon = Icons.Filled.Download
                ) { store.putSetting("wifiOnlyDownloads", it.toString()) }
                Spacer(Modifier.height(10.dp))
                if (store.storageError != null) {
                    Text(
                        store.storageError ?: "",
                        color = androidx.compose.material3
                            .MaterialTheme.colorScheme.error,
                        style = androidx.compose.material3
                            .MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                }
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
                    onClick = { htmlImportLauncher.launch(arrayOf("text/html")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import bookmarks (HTML)") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showEncExport = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export encrypted backup") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onOpenScreen(Screen.Vault) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export vault…") }
                Spacer(Modifier.height(8.dp))
                // Beta.65 #13: clear browsing data with a
                // time range, per-type toggles, and a
                // counter. History respects the range;
                // cookies, cache and site data clear
                // completely, whatever the range.
                var clearRange by remember { mutableStateOf("day") }
                var clearHistoryOn by remember { mutableStateOf(true) }
                var clearCookiesOn by remember { mutableStateOf(true) }
                var clearCacheOn by remember { mutableStateOf(true) }
                var clearSiteDataOn by remember { mutableStateOf(true) }
                val clearCutoff = clearRangeCutoff(
                    clearRange, System.currentTimeMillis()
                )
                val clearHistoryCount = countHistoryInRange(
                    store.history, clearCutoff
                )
                Text(
                    "Clear browsing data",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
                listOf(
                    "hour" to "Last hour",
                    "day" to "Last 24 hours",
                    "week" to "Last 7 days",
                    "all" to "All time"
                ).forEachIndexed { i, (value, label) ->
                    if (i > 0) InsetDivider()
                    ChoiceRow(
                        label = label, selected = clearRange == value
                    ) { clearRange = value }
                }
                InsetDivider()
                ToggleRow(
                    if (clearHistoryCount > 0) "History ($clearHistoryCount)"
                    else "History",
                    clearHistoryOn,
                    icon = Icons.Filled.History
                ) { clearHistoryOn = it }
                InsetDivider()
                ToggleRow(
                    "Cookies", clearCookiesOn,
                    icon = Icons.Filled.Cookie
                ) { clearCookiesOn = it }
                InsetDivider()
                ToggleRow(
                    "Cache", clearCacheOn,
                    icon = Icons.Filled.Cached
                ) { clearCacheOn = it }
                InsetDivider()
                ToggleRow(
                    "Site data", clearSiteDataOn,
                    icon = Icons.Filled.Storage
                ) { clearSiteDataOn = it }
                Text(
                    "Cookies, cache and site data are cleared " +
                        "completely, whatever the time range.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                )
                Button(
                    onClick = {
                        val done = ArrayList<String>()
                        if (clearHistoryOn) {
                            store.clearHistoryOlderThan(clearCutoff)
                            done.add("history ($clearHistoryCount)")
                        }
                        if (clearCookiesOn) {
                            CookieManager.getInstance().removeAllCookies(null)
                            CookieManager.getInstance().flush()
                            done.add("cookies")
                        }
                        if (clearCacheOn) {
                            runCatching { WebView(context).clearCache(true) }
                            done.add("cache")
                        }
                        if (clearSiteDataOn) {
                            WebStorage.getInstance().deleteAllData()
                            done.add("site data")
                        }
                        clearedNote = "Cleared: " + done.joinToString(", ") + "."
                    },
                    enabled = clearHistoryOn || clearCookiesOn ||
                        clearCacheOn || clearSiteDataOn,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Clear browsing data") }
                Spacer(Modifier.height(8.dp))
                // Beta.63 #25: delete-on-exit, OFF by default
                // (Robert's condition) — history, cookies,
                // cache, site data, and open tabs are cleared
                // when you leave the app. Bookmarks, downloads,
                // and the vault are kept.
                ToggleRow(
                    "Delete browsing data on exit",
                    store.setting("deleteOnExit", "false") == "true",
                    icon = Icons.Filled.Delete
                ) { store.putSetting("deleteOnExit", it.toString()) }
                Text(
                    "History, cookies, cache, site data, and open tabs are cleared when you leave the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 42.dp, top = 2.dp, bottom = 4.dp)
                )
            }
            if (clearedNote.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(clearedNote)
            }
            // Beta.59 (S5): Erase lives in its own danger
            // section now, apart from backup and clearing.
            GroupTitle("Danger zone")
            SettingsCard {
                Button(
                    onClick = { confirmClearAll = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Erase everything on this device") }
            }

            // Diagnostic trace (beta.55): user-controlled, local-only.
            GroupTitle(stringResource(R.string.trace_title))
            SettingsCard {
                Text(
                    stringResource(R.string.trace_intro),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (Trace.tracing.value) {
                    Text(stringResource(R.string.trace_tracing, Trace.eventCount.value))
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { Trace.stop() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.trace_end)) }
                } else {
                    Button(
                        onClick = { Trace.start() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.trace_start)) }
                }
                if (Trace.hasContent()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { runCatching { Trace.export(context) } },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.trace_export)) }
                }
            }

            }
            if (page == "video") {
                PlayerSkipSettings(store)
                SubtitleSettings(store)
            }
            if (page == "scripts") {
                UserScriptsSection(store)
            }
            if (page == "accessibility") {
                GroupTitle("Accessibility")
                SettingsCard {
                    ToggleRow(
                        "Reduced motion",
                        store.setting("reducedMotion", "false") == "true",
                        icon = Icons.Filled.Accessibility
                    ) { store.putSetting("reducedMotion", it.toString()) }
                    Text(
                        "Pages are told you prefer reduced motion, and in-page " +
                            "animation and transitions are stilled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                    )
                    Text(
                        "Text size follows your system font size throughout the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                    )
                    InsetDivider()
                    // Beta.65 #50: force zoom — the engine
                    // already honours this setting (the
                    // viewport rewrite); the toggle is what
                    // was missing.
                    ToggleRow(
                        "Force zoom",
                        store.setting("forceZoom", "false") == "true",
                        icon = Icons.Filled.Fullscreen
                    ) { store.putSetting("forceZoom", it.toString()) }
                    Text(
                        "Pages that disable scaling stay zoomable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                    )
                    InsetDivider()
                    // Beta.65 #50: captions — the player's
                    // subtitle style follows Android's caption
                    // settings; this row opens them.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(android.provider.Settings.ACTION_CAPTIONING_SETTINGS)
                                )
                            }
                        }.padding(vertical = 7.dp)
                    ) {
                        RowGlyph(Icons.Filled.Subtitles, null)
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "System caption settings",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                "Caption style for video subtitles, set by Android.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Filled.ChevronRight, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    InsetDivider()
                    // Beta.65 #50: reader — shows or hides
                    // the "Read this article" menu row.
                    ToggleRow(
                        "Reader",
                        store.setting("showReader", "true") == "true",
                        icon = Icons.Filled.MenuBook
                    ) { store.putSetting("showReader", it.toString()) }
                    Text(
                        "Show the \u201cRead this article\u201d row in the menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                    )
                }
            }
            if (page == "hub" && !hubSearching) {
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
                InsetDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Update channel",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val channel = store.setting("updateChannel", "beta")
                    TextButton(onClick = {
                        store.putSetting(
                            "updateChannel",
                            if (channel == "stable") "beta" else "stable"
                        )
                    }) { Text(if (channel == "stable") "Stable" else "Beta") }
                }
                ToggleRow(
                    "Automatic update checks",
                    store.setting("autoUpdateCheck", "true") == "true",
                    icon = Icons.Filled.Refresh
                ) { store.putSetting("autoUpdateCheck", it.toString()) }
                Text(
                    "Manual checks on the Updates page always run.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 42.dp, top = 2.dp, bottom = 4.dp)
                )
            }

            GroupTitle("About")
            SettingsCard {
                Text("Notscape Mariner — version ${BuildConfig.VERSION_NAME} (beta)")
                Text("Package: net.notscape.browser")
                Text("Local-only and private: your data is stored on this device.")
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    runCatching {
                        val intent = if (android.os.Build.VERSION.SDK_INT >= 29) {
                            val rm = context.getSystemService(
                                android.app.role.RoleManager::class.java
                            )
                            if (rm != null &&
                                rm.isRoleAvailable(
                                    android.app.role.RoleManager.ROLE_BROWSER
                                ) &&
                                !rm.isRoleHeld(
                                    android.app.role.RoleManager.ROLE_BROWSER
                                )
                            ) {
                                rm.createRequestRoleIntent(
                                    android.app.role.RoleManager.ROLE_BROWSER
                                )
                            } else {
                                android.content.Intent(
                                    android.provider.Settings
                                        .ACTION_MANAGE_DEFAULT_APPS_SETTINGS
                                )
                            }
                        } else {
                            android.content.Intent(
                                android.provider.Settings
                                    .ACTION_MANAGE_DEFAULT_APPS_SETTINGS
                            )
                        }
                        context.startActivity(intent)
                    }
                }) { Text("Set as default browser") }
                TextButton(onClick = onReplayWelcome) {
                    Text("See the welcome guide again")
                }
                TextButton(onClick = { showNotices = true }) {
                    Text("Third-party notices")
                }
            }
            GroupTitle("Session")
            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { confirmRenew = true }
                        .padding(vertical = 7.dp)
                ) {
                    Box(
                        modifier = Modifier.size(30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Autorenew,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = Color(0xFF599F5C)
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Renew",
                            style = MaterialTheme.typography
                                .bodyLarge
                        )
                        Text(
                            "Clear cookies, storage, cache " +
                                "and history, and close " +
                                "every tab",
                            style = MaterialTheme.typography
                                .bodySmall,
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme
                            .onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            }
        }
    }

    // Element picker rules (beta.18): every rule
    // the user made, with a delete each, so nothing
    // the picker blocked is ever stuck.
    if (showBlockedElements) {
        FrostDialog(
            onDismissRequest = { showBlockedElements = false },
            title = { Text("Blocked elements") },
            text = {
                val netRules = UserBlockRules.parseNet(
                    store.setting(UserBlockRules.NET_KEY, "")
                )
                val cosRules = UserBlockRules.parseCosmetic(
                    store.setting(UserBlockRules.COSMETIC_KEY, "")
                )
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    if (netRules.isEmpty() && cosRules.isEmpty()) {
                        Text(
                            "Nothing blocked yet. Long-press a link or " +
                                "image, or use “Block an element” in the " +
                                "browser menu, to add a rule here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    netRules.forEach { rule ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when (rule) {
                                    is UserBlockRules.NetRule.Host ->
                                        "Everything from ${rule.host}"
                                    is UserBlockRules.NetRule.Url ->
                                        rule.url
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    val kept = netRules - rule
                                    store.putSetting(
                                        UserBlockRules.NET_KEY,
                                        UserBlockRules.serializeNet(kept)
                                    )
                                    FilterEngine.userNetRules = kept
                                }
                            ) { Text("Delete") }
                        }
                    }
                    cosRules.forEach { rule ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${rule.selector} on ${rule.host}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    val kept = cosRules - rule
                                    store.putSetting(
                                        UserBlockRules.COSMETIC_KEY,
                                        UserBlockRules.serializeCosmetic(kept)
                                    )
                                    FilterEngine.userCosmeticRules = kept
                                }
                            ) { Text("Delete") }
                        }
                    }
                    // Export/import live inside the rules column so they
                    // see the same netRules/cosRules the rows above do.
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = {
                            val exportText = listOf(
                                UserBlockRules.serializeNet(netRules),
                                UserBlockRules.serializeCosmetic(cosRules)
                            ).filter { it.isNotEmpty() }
                                .joinToString("\n")
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, exportText)
                            }
                            context.startActivity(
                                Intent.createChooser(send, "Export block list")
                            )
                        }) { Text("Export") }
                        TextButton(onClick = {
                            blockImportText = ""
                            blockImportResult = ""
                            showBlockImport = true
                        }) { Text("Import") }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.putSetting(UserBlockRules.NET_KEY, "")
                        store.putSetting(UserBlockRules.COSMETIC_KEY, "")
                        FilterEngine.userNetRules = emptyList()
                        FilterEngine.userCosmeticRules = emptyList()
                    }
                ) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBlockedElements = false }
                ) { Text("Done") }
            }
        )
    }

    if (showBlockImport) {
        FrostDialog(
            onDismissRequest = { showBlockImport = false },
            title = { Text("Import block list") },
            text = {
                Column {
                    Text(
                        "Paste exported rules (net / cos lines). " +
                            "Cosmetic rules are checked before import; " +
                            "unsafe ones are dropped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = blockImportText,
                        onValueChange = { blockImportText = it },
                        label = { Text("Rules") },
                        modifier = Modifier.fillMaxWidth()
                            .padding(top = 8.dp),
                        minLines = 4
                    )
                    if (blockImportResult.isNotEmpty()) {
                        Text(
                            blockImportResult,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val curNet = UserBlockRules.parseNet(
                        store.setting(UserBlockRules.NET_KEY, "")
                    )
                    val curCos = UserBlockRules.parseCosmetic(
                        store.setting(UserBlockRules.COSMETIC_KEY, "")
                    )
                    val newNet = UserBlockRules.parseNet(blockImportText)
                    val newCos = UserBlockRules.parseCosmetic(blockImportText)
                    val mergedNet = (curNet + newNet).distinct()
                    val mergedCos = (curCos + newCos).distinct()
                    store.putSetting(
                        UserBlockRules.NET_KEY,
                        UserBlockRules.serializeNet(mergedNet)
                    )
                    store.putSetting(
                        UserBlockRules.COSMETIC_KEY,
                        UserBlockRules.serializeCosmetic(mergedCos)
                    )
                    FilterEngine.userNetRules = mergedNet
                    FilterEngine.userCosmeticRules = mergedCos
                    val lines = blockImportText.lines()
                        .count { it.isNotBlank() }
                    val skipped = lines - newNet.size - newCos.size
                    blockImportResult =
                        "Imported ${newNet.size + newCos.size} rules" +
                            if (skipped > 0) ", $skipped lines skipped"
                            else ""
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showBlockImport = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (showNotices) {
        ThirdPartyNoticesDialog(onDismiss = { showNotices = false })
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
                Text("Bookmarks, history, playlists, downloads list, vault entries, site settings, and favorites stored on this device will be deleted, along with website cookies, DOM storage, and cached web data. Private tabs will be closed and private browsing data erased. Downloaded files and books stay in their folders.")
            },
            confirmButton = {
                TextButton(onClick = {
                    // Audit D-m11: stop in-flight downloads,
                    // drop their system entries and staging,
                    // and clear their job bundles before the
                    // list itself is erased.
                    net.notscape.browser.media.Downloads.eraseAllDownloads(context, store)
                    store.clearAllData()
                    WebStorage.getInstance().deleteAllData()
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    runCatching { WebView(context).clearCache(true) }
                    confirmClearAll = false
                    // The result note arrives via the
                    // erase handshake (see clearedNote's
                    // effect above) once the private tabs
                    // have closed and the profile is wiped.
                    onErasePrivate()
                }) { Text("Erase") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancel") }
            }
        )
    }

    if (showEncExport) {
        var pass1 by remember { mutableStateOf("") }
        var pass2 by remember { mutableStateOf("") }
        FrostDialog(
            onDismissRequest = { showEncExport = false },
            title = { Text("Encrypted backup") },
            text = {
                Column {
                    Text(
                        "The backup is encrypted with this passphrase " +
                            "(the same PBKDF2 + AES-256-GCM envelope as " +
                            "the vault export). There is no recovery " +
                            "if you forget it."
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pass1,
                        onValueChange = { pass1 = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text
                            .input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pass2,
                        onValueChange = { pass2 = it },
                        label = { Text("Confirm passphrase") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text
                            .input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingEncPass = pass1
                        showEncExport = false
                        exportEncLauncher.launch(
                            "notscape-backup-encrypted.json"
                        )
                    },
                    enabled = pass1.isNotBlank() && pass1 == pass2
                ) { Text("Export") }
            },
            dismissButton = {
                TextButton(onClick = { showEncExport = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    encImportText?.let { envelope ->
        var pass by remember { mutableStateOf("") }
        FrostDialog(
            onDismissRequest = { encImportText = null },
            title = { Text("Encrypted backup") },
            text = {
                Column {
                    Text(
                        "This backup is encrypted. Enter its " +
                            "passphrase to continue."
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Passphrase") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text
                            .input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !decrypting,
                    onClick = {
                        // PBKDF2 at 600k iterations is
                        // seconds of CPU: never on the
                        // main thread from this dialog.
                        decrypting = true
                        importScope.launch {
                            val plain = withContext(Dispatchers.IO) {
                                EncryptedBackup.decrypt(envelope, pass)
                            }
                            decrypting = false
                            encImportText = null
                            if (plain != null) {
                                confirmImport = plain
                            } else {
                                clearedNote = "That backup could not be " +
                                    "decrypted - wrong passphrase?"
                            }
                        }
                    }
                ) { Text(if (decrypting) "Decrypting…" else "Decrypt") }
            },
            dismissButton = {
                TextButton(onClick = { encImportText = null }) {
                    Text("Cancel")
                }
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

/** One hub link row (0.0.991): glyph, label, a short
 * description of what lives behind it, chevron. */
@Composable
private fun HubRow(
    label: String,
    sub: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 7.dp)
    ) {
        RowGlyph(icon, null)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Beta.63 #26: blocked-count detail — this session's
 *  per-category totals plus the per-day history. Counts
 *  are real FilterEngine counters only; the old app's
 *  chart was seeded with random placeholder data and
 *  is deliberately not reproduced. */
@Composable
private fun BlockedScreen(onBack: () -> Unit) {
    SubScreen("Blocked", onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Blocked this session",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                val cats = FilterEngine.blockedByCategory
                BlockedCountRow("Ads", cats.getOrElse(0) { 0 })
                InsetDivider()
                BlockedCountRow("Trackers", cats.getOrElse(1) { 0 })
                InsetDivider()
                BlockedCountRow("Other", cats.getOrElse(2) { 0 })
                InsetDivider()
                BlockedCountRow("Total", FilterEngine.blockedCount)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "History",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(8.dp))
            val history = FilterEngine.blockedHistory()
            SettingsCard {
                if (history.isEmpty()) {
                    Text(
                        "No previous days yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    history.entries.forEachIndexed { idx, (day, counts) ->
                        if (idx > 0) InsetDivider()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                day,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                listOf(
                                    "${counts.getOrElse(0) { 0 }} ads",
                                    "${counts.getOrElse(1) { 0 }} trackers",
                                    "${counts.getOrElse(2) { 0 }} other"
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BlockedCountRow(label: String, count: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            .padding(vertical = 12.dp)
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
        modifier = Modifier.fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
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

/** Storage sizes and cache clearing (0.0.996): measured
 * off the main thread when the Data page opens, and again
 * after a clear. */
@Composable
private fun StorageSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var areas by remember {
        mutableStateOf<List<net.notscape.browser.data.StorageInfo.Area>?>(null)
    }
    var total by remember { mutableStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        scope.launch {
            val measured = kotlinx.coroutines.withContext(
                kotlinx.coroutines.Dispatchers.IO
            ) {
                net.notscape.browser.data.StorageInfo
                    .measure(context)
            }
            areas = measured
            total = measured.sumOf { it.bytes }
        }
    }
    LaunchedEffect(Unit) { refresh() }
    val list = areas
    if (list == null) {
        Text(
            "Measuring\u2026",
            style = MaterialTheme.typography.bodyLarge
        )
    } else {
        list.forEachIndexed { i, area ->
            if (i > 0) InsetDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 7.dp)
            ) {
                Text(
                    area.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    net.notscape.browser.data.StorageInfo
                        .formatSize(area.bytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme
                        .onSurfaceVariant
                )
            }
        }
        InsetDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .padding(vertical = 7.dp)
        ) {
            Text(
                "Total",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                net.notscape.browser.data.StorageInfo
                    .formatSize(total),
                style = MaterialTheme.typography.titleSmall
            )
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = {
                busy = true
                scope.launch {
                    kotlinx.coroutines.withContext(
                        kotlinx.coroutines.Dispatchers.IO
                    ) {
                        net.notscape.browser.data.StorageInfo
                            .clearCache(context)
                    }
                    busy = false
                    refresh()
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Clearing\u2026" else "Clear cache") }
        Text(
            "The cache holds temporary copies of pages and " +
                "images. Clearing it is always safe - the " +
                "browser rebuilds it as you browse.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** The Video player page (0.0.991, Robert): subtitle
 * styling for the player - font, size, text colour,
 * background and edge. Written to the store; the player
 * applies it (PlayerActivity.applySubtitleStyle). */
@Composable
private fun PlayerSkipSettings(store: AppStore) {
    val skipSec = store.setting("playerSkipSec", "10")
    GroupTitle("Skip interval")
    SettingsCard {
        listOf(
            5 to "5 seconds", 10 to "10 seconds",
            15 to "15 seconds", 20 to "20 seconds",
            25 to "25 seconds", 30 to "30 seconds",
            60 to "1 minute", 90 to "90 seconds",
            120 to "2 minutes", 150 to "150 seconds",
            180 to "3 minutes", 300 to "5 minutes"
        ).forEachIndexed { i, (value, label) ->
            if (i > 0) InsetDivider()
            ChoiceRow(
                label = label,
                selected = skipSec == value.toString()
            ) { store.putSetting("playerSkipSec", value.toString()) }
        }
    }
    Spacer(Modifier.height(14.dp))
}

/**
 * User scripts (0.0.99996): the list, per-script toggle,
 * and a plain editor dialog. Scripts run at page start
 * with the page's own access - the copy says so.
 */
@Composable
private fun UserScriptsSection(store: AppStore) {
    var scripts by remember {
        mutableStateOf(
            net.notscape.browser.browser.UserScripts.load(store)
        )
    }
    var editing by remember {
        mutableStateOf<net.notscape.browser.browser.UserScript?>(null)
    }
    var editorOpen by remember { mutableStateOf(false) }
    fun persist(next: List<net.notscape.browser.browser.UserScript>) {
        scripts = next
        net.notscape.browser.browser.UserScripts.save(store, next)
    }
    GroupTitle("User scripts")
    Text(
        "Scripts you add run at page start on the sites " +
            "their pattern matches, with the page's own " +
            "access. Only add code you trust. Changes apply " +
            "to pages opened from now on.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))
    SettingsCard {
        if (scripts.isEmpty()) {
            Text(
                "No scripts yet.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        scripts.forEachIndexed { i, s ->
            if (i > 0) InsetDivider()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        s.name.ifBlank { "Untitled script" },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        s.pattern,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme
                            .onSurfaceVariant
                    )
                }
                Switch(
                    checked = s.enabled,
                    onCheckedChange = { on ->
                        persist(
                            scripts.map {
                                if (it.id == s.id) {
                                    it.copy(enabled = on)
                                } else {
                                    it
                                }
                            }
                        )
                    }
                )
                TextButton(onClick = {
                    editing = s
                    editorOpen = true
                }) { Text("Edit") }
                TextButton(onClick = {
                    persist(scripts.filter { it.id != s.id })
                }) { Text("Delete") }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = {
            editing = null
            editorOpen = true
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Add a script") }
    if (editorOpen) {
        var name by remember {
            mutableStateOf(editing?.name ?: "")
        }
        var pattern by remember {
            mutableStateOf(editing?.pattern ?: "*://*/*")
        }
        var code by remember {
            mutableStateOf(editing?.code ?: "")
        }
        FrostDialog(
            onDismissRequest = { editorOpen = false },
            title = {
                Text(
                    if (editing == null) {
                        "Add a script"
                    } else {
                        "Edit script"
                    }
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(
                        rememberScrollState()
                    )
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        label = { Text("Run on (match pattern)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = {
                            code = it.take(
                                net.notscape.browser.browser
                                    .UserScripts.MAX_CODE_CHARS
                            )
                        },
                        label = { Text("JavaScript") },
                        minLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val base = editing
                    val saved =
                        net.notscape.browser.browser.UserScript(
                            id = base?.id
                                ?: ("us" +
                                    System.currentTimeMillis()),
                            name = name.trim(),
                            pattern = pattern.trim()
                                .ifBlank { "*://*/*" },
                            code = code,
                            enabled = base?.enabled ?: true
                        )
                    persist(
                        if (base == null) {
                            scripts + saved
                        } else {
                            scripts.map {
                                if (it.id == base.id) saved else it
                            }
                        }
                    )
                    editorOpen = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editorOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SubtitleSettings(store: AppStore) {
    val subFont = store.setting("subFont", "default")
    val subSize = store.setting("subSize", "100")
    val subColor = store.setting("subColor", "white")
    val subBg = store.setting("subBg", "none")
    val subEdge = store.setting("subEdge", "outline")
    val context = androidx.compose.ui.platform
        .LocalContext.current
    val subFontName = store.setting("subFontCustomName", "")
    // Custom subtitle font (0.0.994): the same validated
    // import as the interface font - copied into
    // files/fonts/subtitle-font, magic-checked and
    // typeface-checked before it is kept, and applied by
    // the player's applySubtitleStyle.
    val subFontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            Thread {
                var loadedName: String? = null
                val ok = runCatching {
                    val dir = java.io.File(
                        context.filesDir, "fonts"
                    ).apply { mkdirs() }
                    val tmp = java.io.File(dir, "subtitle-font.tmp")
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
                            java.io.File(dir, "subtitle-font"),
                            overwrite = true
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
                        store.putSetting(
                            "subFontCustomName",
                            loadedName ?: "Custom font"
                        )
                        store.putSetting("subFont", "custom")
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
    GroupTitle("Subtitle font")
    SettingsCard {
        listOf(
            "default" to "Default",
            "opensans" to "Open Sans",
            "roboto" to "Roboto",
            "inter" to "Inter",
            "serif" to "Serif",
            "mono" to "Monospace",
            "custom" to "Custom"
        ).forEachIndexed { i, (value, label) ->
            if (i > 0) InsetDivider()
            ChoiceRow(
                letter = "A", label = label,
                selected = subFont == value
            ) { store.putSetting("subFont", value) }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        TextButton(
            onClick = { subFontPicker.launch(arrayOf("*/*")) }
        ) { Text("Import font file") }
        if (subFontName.isNotBlank()) {
            Text(
                subFontName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme
                    .onSurfaceVariant
            )
        }
    }
    GroupTitle("Subtitle size")
    SettingsCard {
        listOf("75", "100", "125", "150", "200")
            .forEachIndexed { i, value ->
                if (i > 0) InsetDivider()
                ChoiceRow(
                    label = "$value%",
                    selected = subSize == value
                ) { store.putSetting("subSize", value) }
            }
    }
    GroupTitle("Subtitle colour")
    SettingsCard {
        listOf(
            "white" to "White",
            "yellow" to "Yellow",
            "cyan" to "Cyan",
            "green" to "Green",
            "magenta" to "Magenta",
            "red" to "Red",
            "blue" to "Blue",
            "black" to "Black"
        ).forEachIndexed { i, (value, label) ->
            if (i > 0) InsetDivider()
            ChoiceRow(
                letter = "A", label = label,
                selected = subColor == value
            ) { store.putSetting("subColor", value) }
        }
    }
    GroupTitle("Subtitle background")
    SettingsCard {
        listOf(
            "none" to "None",
            "black50" to "Black, half",
            "black75" to "Black, strong",
            "black" to "Black, solid",
            "white75" to "White, strong"
        ).forEachIndexed { i, (value, label) ->
            if (i > 0) InsetDivider()
            ChoiceRow(
                label = label, selected = subBg == value
            ) { store.putSetting("subBg", value) }
        }
    }
    GroupTitle("Subtitle edge")
    SettingsCard {
        listOf(
            "outline" to "Outline",
            "shadow" to "Drop shadow",
            "none" to "None"
        ).forEachIndexed { i, (value, label) ->
            if (i > 0) InsetDivider()
            ChoiceRow(
                label = label, selected = subEdge == value
            ) { store.putSetting("subEdge", value) }
        }
    }
    Text(
        "Applied in the video player. Until you change " +
            "something here, your Android system caption " +
            "style applies, as before.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp)
    )
}
