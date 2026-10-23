package net.notscape.browser.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import net.notscape.browser.BuildConfig
import net.notscape.browser.data.AppStore
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Update check against the project's GitHub releases. Releases are beta
 * (pre-release) builds, so the check reads the releases list — the
 * "/latest" endpoint skips pre-releases entirely. When a newer build is
 * found, its APK can be downloaded and handed to the system installer
 * right from the app.
 */
object UpdateChecker {

    var availableVersion by mutableStateOf<String?>(null)
        private set
    var downloadUrl by mutableStateOf<String?>(null)
        private set
    var releasePageUrl by mutableStateOf<String?>(null)
        private set
    var releaseNotes by mutableStateOf<String?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var lastCheckedAt by mutableStateOf(0L)
        private set

    /** -1 idle, 0..100 downloading percent, -2 failed. */
    var downloadProgress by mutableStateOf(-1)
        private set

    private const val RELEASES_URL =
        "https://api.github.com/repos/DocBobert6/NotscapeMariner/releases?per_page=5"

    fun checkAsync(store: AppStore, force: Boolean = false) {
        val last = store.setting("lastUpdateCheck", "0").toLongOrNull() ?: 0L
        lastCheckedAt = last
        if (!force && System.currentTimeMillis() - last < 24L * 3600 * 1000) return
        if (checking) return
        checking = true
        Thread {
            try {
                val conn = URL(RELEASES_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "NotscapeMariner")
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    val releases = org.json.JSONArray(body)
                    var found = false
                    for (i in 0 until releases.length()) {
                        val rel = releases.getJSONObject(i)
                        if (rel.optBoolean("draft")) continue
                        val tag = rel.optString("tag_name").removePrefix("v")
                        if (tag.isBlank() || !isNewer(tag, BuildConfig.VERSION_NAME)) continue
                        var apk: String? = null
                        val assets = rel.optJSONArray("assets")
                        if (assets != null) {
                            for (j in 0 until assets.length()) {
                                val a = assets.getJSONObject(j)
                                if (a.optString("name").endsWith(".apk")) {
                                    apk = a.optString("browser_download_url")
                                    break
                                }
                            }
                        }
                        availableVersion = tag
                        releaseNotes = rel.optString("body").trim()
                            .take(600).ifBlank { null }
                        downloadUrl = apk
                        releasePageUrl = rel.optString("html_url")
                        found = true
                        break
                    }
                    if (!found) {
                        availableVersion = null
                        releaseNotes = null
                        downloadUrl = null
                        releasePageUrl = null
                    }
                }
                conn.disconnect()
            } catch (_: Exception) {
                // Offline or no releases yet — stay silent.
            } finally {
                val now = System.currentTimeMillis()
                store.putSetting("lastUpdateCheck", now.toString())
                lastCheckedAt = now
                checking = false
            }
        }.start()
    }

    /** Downloads the update APK, then opens the system installer for it. */
    fun downloadAndInstall(context: Context) {
        val url = downloadUrl ?: run {
            // No APK attached to the release — fall back to the release page.
            releasePageUrl?.let {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(it))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            return
        }
        if (downloadProgress in 0..100) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(
                context,
                "Allow installs from Notscape Mariner to update in place",
                Toast.LENGTH_LONG
            ).show()
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        downloadProgress = 0
        Thread {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val out = File(dir, "notscape-update.apk")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 20_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", "NotscapeMariner")
                conn.inputStream.use { input ->
                    val total = conn.contentLengthLong
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                downloadProgress = ((done * 100) / total).toInt().coerceIn(0, 100)
                            }
                        }
                    }
                }
                conn.disconnect()
                downloadProgress = -1
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", out
                )
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (e: Exception) {
                downloadProgress = -2
            }
        }.start()
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
