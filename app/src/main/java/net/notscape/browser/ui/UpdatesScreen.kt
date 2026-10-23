package net.notscape.browser.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.notscape.browser.BuildConfig
import net.notscape.browser.browser.UpdateChecker
import net.notscape.browser.data.AppStore
import java.text.DateFormat
import java.util.Date

/**
 * The dedicated updates page (0.0.959): checking, what is available,
 * and the download's progress all live here instead of squeezed into
 * Settings' About card. Reached from Settings -> Updates and from
 * the start page's update note under the banner.
 */
@Composable
fun UpdatesScreen(store: AppStore, onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { UpdateChecker.checkAsync(store, force = true) }
    SubScreen("Updates", onBack) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            GroupedCard {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "Notscape Mariner ${BuildConfig.VERSION_NAME} (beta)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Updates come from the project's GitHub releases " +
                            "and install in place — same signature, your " +
                            "data untouched.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    val avail = UpdateChecker.availableVersion
                    val progress = UpdateChecker.downloadProgress
                    when {
                        progress in 0..100 -> {
                            Text(
                                "Downloading version $avail…",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "$progress%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        UpdateChecker.checking -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Checking for updates…")
                            }
                        }
                        avail != null -> {
                            Text(
                                "Version $avail (beta) is available.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            UpdateChecker.releaseNotes?.let { notes ->
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 14,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    UpdateChecker.downloadAndInstall(context)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Download & install $avail") }
                            if (progress == -2) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "The download failed — check your " +
                                        "connection and try again.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            UpdateChecker.releasePageUrl?.let { page ->
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    Uri.parse(page)
                                                ).addFlags(
                                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                                )
                                            )
                                        }
                                    }
                                ) { Text("View the release on GitHub") }
                            }
                        }
                        else -> {
                            Text(
                                "You're up to date.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (UpdateChecker.lastCheckedAt > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Last checked " +
                                        DateFormat.getDateTimeInstance()
                                            .format(
                                                Date(UpdateChecker.lastCheckedAt)
                                            ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    UpdateChecker.checkAsync(store, force = true)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Check again") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
