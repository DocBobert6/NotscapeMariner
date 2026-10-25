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
import androidx.compose.material3.Button
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
