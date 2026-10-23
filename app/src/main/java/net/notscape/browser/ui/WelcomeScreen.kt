package net.notscape.browser.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.notscape.browser.data.AppStore

/**
 * The first-run welcome (0.0.998): three short pages -
 * what this browser is, what the library does, and the two
 * choices worth making up front (theme, search engine),
 * written live to the same settings Settings uses. Shown
 * until the "onboarded" setting is set; Settings > About
 * can replay it at any time. Skipping counts as finishing:
 * the defaults are the ones the pages would have set.
 */
@Composable
fun WelcomeScreen(store: AppStore, onDone: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(40.dp))
            when (page) {
                0 -> {
                    Text(
                        "Notscape Mariner",
                        fontFamily = WordmarkFontFamily,
                        fontSize = 34.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "A reading and media-first private browser. " +
                            "Everything stays on this device - no " +
                            "account, no sync, no telemetry.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    PanelCard {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = 14.dp, vertical = 8.dp
                            )
                        ) {
                            WelcomeRow(
                                Icons.Filled.Lock,
                                "Private by design",
                                "History, books and passwords are " +
                                    "stored only on this device."
                            )
                            InsetDivider()
                            WelcomeRow(
                                Icons.Filled.Block,
                                "Ads and trackers blocked",
                                "Seven filter lists, updated " +
                                    "daily, enforced on-device."
                            )
                            InsetDivider()
                            WelcomeRow(
                                Icons.Filled.MenuBook,
                                "Built for reading",
                                "A real bookshelf and reader, " +
                                    "read-aloud, and a full media " +
                                    "player."
                            )
                        }
                    }
                }
                1 -> {
                    Text(
                        "Your library",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "The browser is half the app. The other " +
                            "half is a library that lives on the " +
                            "device with it.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    PanelCard {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = 14.dp, vertical = 8.dp
                            )
                        ) {
                            WelcomeRow(
                                Icons.Filled.MenuBook,
                                "Bookshelf & reader",
                                "EPUB books with a two-page " +
                                    "spread, ribbons, in-book " +
                                    "search and read-aloud."
                            )
                            InsetDivider()
                            WelcomeRow(
                                Icons.Filled.PlaylistPlay,
                                "Playlists & player",
                                "Collect videos into playlists " +
                                    "and play them in the built-in " +
                                    "player, with subtitle styling."
                            )
                            InsetDivider()
                            WelcomeRow(
                                Icons.Filled.Download,
                                "Offline library",
                                "Downloads are kept on the " +
                                    "device for watching and " +
                                    "listening offline."
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        "Make it yours",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Two choices, applied right away - and " +
                            "changeable any time in Settings.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val themeMode = store.setting("themeMode", "system")
                    GroupTitle("Theme")
                    SettingsCard {
                        WelcomeChoiceRow(
                            "System default", themeMode == "system"
                        ) { store.putSetting("themeMode", "system") }
                        InsetDivider()
                        WelcomeChoiceRow(
                            "Light", themeMode == "light"
                        ) { store.putSetting("themeMode", "light") }
                        InsetDivider()
                        WelcomeChoiceRow(
                            "Dark", themeMode == "dark"
                        ) { store.putSetting("themeMode", "dark") }
                    }
                    val engine = store.setting("searchEngine", "duckduckgo")
                    GroupTitle("Search")
                    SettingsCard {
                        listOf(
                            "duckduckgo" to "DuckDuckGo (No AI)",
                            "startpage" to "Startpage",
                            "google" to "Google",
                            "bing" to "Bing"
                        ).forEachIndexed { i, (value, label) ->
                            if (i > 0) InsetDivider()
                            WelcomeChoiceRow(label, engine == value) {
                                store.putSetting("searchEngine", value)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (page < 2) {
                TextButton(onClick = onDone) { Text("Skip") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { page++ }) { Text("Continue") }
            } else {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Start browsing") }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun WelcomeRow(
    icon: ImageVector, title: String, supporting: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier.width(30.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WelcomeChoiceRow(
    label: String, selected: Boolean, onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
