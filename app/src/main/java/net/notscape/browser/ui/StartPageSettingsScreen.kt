package net.notscape.browser.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import net.notscape.browser.data.AppStore

/**
 * The start page's own settings page (0.0.972, Robert): reached
 * from Settings -> Start page, it holds everything that shapes
 * the start page - the wallpaper (which follows the theme
 * unless a custom photo is chosen), the banner toggle, the
 * sections, and the homepage - instead of crowding the main
 * Settings list.
 */
@Composable
fun StartPageSettingsScreen(store: AppStore, onBack: () -> Unit) {
    val rev = store.revision
    val context = LocalContext.current
    var homeDraft by remember { mutableStateOf(store.setting("homePage", "")) }
    // Custom start-page photo: picked here, copied into app
    // files downscaled, drawn by the start page. Nothing leaves
    // the device.
    val customWallpaperFile = remember {
        java.io.File(context.filesDir, "wallpaper/custom.jpg")
    }
    var customWallpaperExists by remember {
        mutableStateOf(customWallpaperFile.exists())
    }
    // Picking a photo opens the adjuster: the user frames the
    // photo first, and only the framed crop is saved.
    var adjustBitmap by remember {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            Thread {
                val bmp = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        android.graphics.BitmapFactory.decodeStream(input)
                    }
                }.getOrNull()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (bmp == null) {
                        Toast.makeText(
                            context, "Couldn't read that photo",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val scale = 2048f / maxOf(bmp.width, bmp.height)
                        adjustBitmap = if (scale < 1f) {
                            android.graphics.Bitmap.createScaledBitmap(
                                bmp,
                                (bmp.width * scale).toInt().coerceAtLeast(1),
                                (bmp.height * scale).toInt().coerceAtLeast(1),
                                true
                            )
                        } else {
                            bmp
                        }
                    }
                }
            }.start()
        }
    }
    adjustBitmap?.let { bmp ->
        WallpaperAdjustDialog(
            bitmap = bmp,
            onCancel = { adjustBitmap = null },
            onConfirm = { cropped ->
                adjustBitmap = null
                Thread {
                    val ok = runCatching {
                        customWallpaperFile.parentFile?.mkdirs()
                        java.io.FileOutputStream(customWallpaperFile).use { fos ->
                            cropped.compress(
                                android.graphics.Bitmap.CompressFormat.JPEG,
                                88, fos
                            )
                        }
                    }.isSuccess
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        customWallpaperExists = customWallpaperFile.exists()
                        if (ok && customWallpaperExists) {
                            store.putSetting("wallpaper", "custom")
                        }
                    }
                }.start()
            }
        )
    }

    SubScreen("Start page", onBack) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            GroupTitle("Wallpaper")
            SettingsCard {
                val wallpaper = store.setting("wallpaper", "auto")
                Text(
                    "The wallpaper follows your theme - Light, Dark " +
                        "or Material You - unless you choose a custom photo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = wallpaper != "custom",
                        onClick = { store.putSetting("wallpaper", "auto") },
                        label = { Text("Follow theme") }
                    )
                    FilterChip(
                        selected = wallpaper == "custom",
                        onClick = {
                            store.putSetting("wallpaper", "custom")
                            if (!customWallpaperExists) {
                                wallpaperPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                        },
                        label = { Text("Custom") }
                    )
                }
                if (wallpaper == "custom") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            wallpaperPicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (customWallpaperExists) {
                                "Replace photo"
                            } else {
                                "Choose photo"
                            }
                        )
                    }
                    Text(
                        "Pick a photo and frame it - it fills the start " +
                            "page, and the banner takes its colours from " +
                            "it. Nothing leaves the device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                    )
                }
            }

            GroupTitle("Sections")
            SettingsCard {
                ToggleRow(
                    "Banner on start page",
                    store.setting("showBanner", "true") == "true",
                    icon = Icons.Filled.Article
                ) { store.putSetting("showBanner", it.toString()) }
                InsetDivider()
                ToggleRow(
                    "Continue section on start page",
                    store.setting("showContinue", "true") == "true",
                    icon = Icons.Filled.PlayArrow
                ) { store.putSetting("showContinue", it.toString()) }
                InsetDivider()
                ToggleRow(
                    "Frequently visited on start page",
                    store.setting("showFrequent", "true") == "true",
                    icon = Icons.Filled.Restore
                ) { store.putSetting("showFrequent", it.toString()) }
                InsetDivider()
                ToggleRow(
                    "Favorites section on start page",
                    store.setting("showFavorites", "true") == "true",
                    icon = Icons.Filled.Star
                ) { store.putSetting("showFavorites", it.toString()) }
            }

            GroupTitle("Pinned sites")
            SettingsCard {
                Text(
                    "Pin a bookmark, a favorite, or any page to the start page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
                )
                var siteDraft by remember { mutableStateOf("") }
                var pinsTick by remember { mutableStateOf(0) }
                val pins = remember(rev, pinsTick) {
                    net.notscape.browser.emu.StartShortcuts.load(store::setting)
                }
                OutlinedTextField(
                    value = siteDraft,
                    onValueChange = { siteDraft = it },
                    label = { Text("Custom site URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = {
                        val url = siteDraft.trim()
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            net.notscape.browser.emu.StartShortcuts.toggleSite(store, url)
                            siteDraft = ""
                            pinsTick++
                        } else {
                            Toast.makeText(
                                context, "URL must start with http:// or https://",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) { Text("Pin site") }
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = {
                        store.removeSetting("frequentHidden")
                        Toast.makeText(
                            context, "Hidden frequently-visited sites restored",
                            Toast.LENGTH_SHORT
                        ).show()
                    }) { Text("Reset hidden sites") }
                }
                Spacer(Modifier.height(8.dp))
                pins.sites.forEach { url ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            url, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            net.notscape.browser.emu.StartShortcuts.toggleSite(store, url)
                            pinsTick++
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Unpin site")
                        }
                    }
                }
                if (store.bookmarks.isNotEmpty()) {
                    Text(
                        "From bookmarks",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    store.bookmarks.forEach { b ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                b.title.ifBlank { b.url }, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = {
                                net.notscape.browser.emu.StartShortcuts.toggleSite(store, b.url)
                                pinsTick++
                            }) {
                                Text(if (b.url in pins.sites) "Pinned" else "Pin")
                            }
                        }
                    }
                }
                if (store.favourites.isNotEmpty()) {
                    Text(
                        "From favorites",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                    store.favourites.forEach { f ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                f.title.ifBlank { f.url }, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = {
                                net.notscape.browser.emu.StartShortcuts.toggleSite(store, f.url)
                                pinsTick++
                            }) {
                                Text(if (f.url in pins.sites) "Pinned" else "Pin")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            GroupTitle("Homepage")
            SettingsCard {
                ToggleRow(
                    "Home button in address bar",
                    store.setting("showHomeButton", "true") == "true",
                    icon = Icons.Filled.Home
                ) { store.putSetting("showHomeButton", it.toString()) }
                InsetDivider()
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
                        Toast.makeText(
                            context, "Homepage saved", Toast.LENGTH_SHORT
                        ).show()
                    }) { Text("Save homepage") }
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = {
                        homeDraft = ""
                        store.putSetting("homePage", "")
                    }) { Text("Use start page") }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
