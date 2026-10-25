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
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import java.security.MessageDigest
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
    var expectedSha256 by mutableStateOf<String?>(null)
        private set
    var releasePageUrl by mutableStateOf<String?>(null)
        private set
    var releaseNotes by mutableStateOf<String?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    var lastCheckedAt by mutableStateOf(0L)
        private set

    /** -1 idle, 0..100 downloading percent, -2 failed,
     * -3 downloaded but failed its integrity check. */
    var downloadProgress by mutableStateOf(-1)
        private set

    private const val RELEASES_URL =
        "https://api.github.com/repos/DocBobert6/NotscapeMariner/releases?per_page=5"

    fun checkAsync(store: AppStore, force: Boolean = false) {
        val last = store.setting("lastUpdateCheck", "0").toLongOrNull() ?: 0L
        lastCheckedAt = last
        if (!force && System.currentTimeMillis() - last < 3600L * 1000) return
        if (checking) return
        checking = true
        val checkStartedAt = System.currentTimeMillis()
        Thread {
            // Stamped only when a check actually completes
            // (0.0.973): a failed attempt used to write the
            // timestamp too, suppressing retries for the whole
            // interval even though nothing was ever learned.
            var completed = false
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
                        if (tag.isBlank() || !isNewerVersion(tag, BuildConfig.VERSION_NAME)) continue
                        var apk: String? = null
                        var sha: String? = null
                        val assets = rel.optJSONArray("assets")
                        if (assets != null) {
                            for (j in 0 until assets.length()) {
                                val a = assets.getJSONObject(j)
                                if (a.optString("name").endsWith(".apk")) {
                                    apk = a.optString("browser_download_url")
                                    sha = parseAssetDigest(
                                        a.optString("digest")
                                    )
                                    break
                                }
                            }
                        }
                        availableVersion = tag
                        releaseNotes = rel.optString("body").trim()
                            .take(4000).ifBlank { null }
                        downloadUrl = apk
                        expectedSha256 = sha
                        releasePageUrl = rel.optString("html_url")
                        found = true
                        break
                    }
                    if (!found) {
                        availableVersion = null
                        releaseNotes = null
                        downloadUrl = null
                        expectedSha256 = null
                        releasePageUrl = null
                    }
                    completed = true
                }
                conn.disconnect()
            } catch (_: Exception) {
                // Offline or no releases yet — stay silent.
            } finally {
                if (completed) {
                    val now = System.currentTimeMillis()
                    store.putSetting("lastUpdateCheck", now.toString())
                    lastCheckedAt = now
                }
                // Minimum display (0.0.994, Robert): a fast
                // check used to flash its spinner for a few
                // frames and read as a glitch. Hold the
                // checking state about a second, so the
                // result's reveal feels deliberate. Purely
                // cosmetic - the result is already known.
                val elapsed = System.currentTimeMillis() - checkStartedAt
                if (elapsed in 1 until 1000) {
                    try {
                        Thread.sleep(1000 - elapsed)
                    } catch (_: InterruptedException) {
                    }
                }
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
                val md = MessageDigest.getInstance("SHA-256")
                conn.inputStream.use { input ->
                    val total = conn.contentLengthLong
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            md.update(buf, 0, n)
                            done += n
                            if (total > 0) {
                                downloadProgress = ((done * 100) / total).toInt().coerceIn(0, 100)
                            }
                        }
                    }
                }
                conn.disconnect()
                // Updater integrity (0.0.980): the file reaches
                // the installer only if it is exactly the file
                // the release published - SHA-256 against the
                // asset digest GitHub reports - AND it carries
                // the same signing certificate as the app
                // already installed. Anything else is deleted.
                val actualSha = md.digest()
                    .joinToString("") { "%02x".format(it) }
                val expected = expectedSha256
                val hashOk = expected == null ||
                    expected.equals(actualSha, ignoreCase = true)
                if (!hashOk || !signerMatches(context, out)) {
                    out.delete()
                    downloadProgress = -3
                } else {
                    downloadProgress = -1
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider", out
                    )
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(
                                uri,
                                "application/vnd.android.package-archive"
                            )
                            addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            } catch (e: Exception) {
                downloadProgress = -2
            }
        }.start()
    }

    /**
     * The releases API reports each asset's digest as
     * "sha256:<hex>"; this returns the bare lowercase hex, or
     * null when the value is absent or malformed (the signer
     * check still applies on its own then).
     */
    fun parseAssetDigest(raw: String?): String? {
        val hex = raw?.trim()?.removePrefix("sha256:")?.trim()
        return if (hex != null && hex.length == 64 && hex.all {
                it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F'
            }
        ) {
            hex.lowercase()
        } else {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun signerMatches(context: Context, apk: File): Boolean {
        val pm = context.packageManager
        return try {
            val flags = if (android.os.Build.VERSION.SDK_INT >= 28) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            val archive = pm.getPackageArchiveInfo(
                apk.absolutePath, flags
            ) ?: return false
            val installed = pm.getPackageInfo(
                context.packageName, flags
            ) ?: return false
            val mine = certBytes(installed)
            certBytes(archive).any { a ->
                mine.any { b -> a.contentEquals(b) }
            }
        } catch (e: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun certBytes(info: PackageInfo): List<ByteArray> =
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
                ?.map { it.toByteArray() } ?: emptyList()
        } else {
            info.signatures?.map { it.toByteArray() } ?: emptyList()
        }

}

/**
 * Categorized release notes (0.1.0-beta.1): the release body carries
 * "## <Category>" sections with "- " items, written into the build's
 * commit message and lifted into the release body by the workflow.
 * Lines before any section land in an untitled section, so older
 * one-line bodies and plain prose still render instead of vanishing.
 */
data class ReleaseSection(val title: String, val items: List<String>)

fun parseReleaseNotes(body: String): List<ReleaseSection> {
    val titles = mutableListOf<String>()
    val sections = mutableListOf<MutableList<String>>()
    var current: MutableList<String>? = null
    for (raw in body.lines()) {
        val line = raw.trim()
        if (line.startsWith("## ")) {
            titles.add(line.removePrefix("## ").trim())
            current = mutableListOf()
            sections.add(current)
        } else if (line.isNotEmpty()) {
            val item = if (line.startsWith("- ") || line.startsWith("* ")) {
                line.drop(2).trim()
            } else {
                line
            }
            if (item.isEmpty()) continue
            if (current == null) {
                titles.add("")
                current = mutableListOf()
                sections.add(current)
            }
            current.add(item)
        }
    }
    return titles.indices
        .map { i -> ReleaseSection(titles[i], sections[i]) }
        .filter { it.items.isNotEmpty() }
}

/**
 * Semver-aware newer check (0.1.0-beta.1): "0.1.0" beats
 * "0.1.0-beta.2" (a release outranks its own prereleases),
 * prereleases compare by their numeric suffix, and the old
 * digit-tail versions compare as plain numeric cores. Without
 * this, the RC 0.1.0 release would have compared below
 * 0.1.0-beta.N and the updater would never have offered it.
 */
fun isNewerVersion(candidate: String, current: String): Boolean {
    fun parts(v: String): Pair<List<Int>, List<String>> {
        val coreAndPre = v.split("-", limit = 2)
        val core = coreAndPre[0].split(".").map { it.toIntOrNull() ?: 0 }
        val pre = if (coreAndPre.size > 1) coreAndPre[1].split(".") else emptyList()
        return core to pre
    }
    val (aCore, aPre) = parts(candidate)
    val (bCore, bPre) = parts(current)
    for (i in 0 until maxOf(aCore.size, bCore.size)) {
        val x = aCore.getOrElse(i) { 0 }
        val y = bCore.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    // Equal cores: no prerelease beats any prerelease.
    if (aPre.isEmpty() && bPre.isNotEmpty()) return true
    if (aPre.isNotEmpty() && bPre.isEmpty()) return false
    for (i in 0 until maxOf(aPre.size, bPre.size)) {
        val x = aPre.getOrElse(i) { "" }
        val y = bPre.getOrElse(i) { "" }
        if (x == y) continue
        val xn = x.toIntOrNull()
        val yn = y.toIntOrNull()
        return if (xn != null && yn != null) xn > yn else x > y
    }
    return false
}
