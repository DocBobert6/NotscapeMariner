package net.notscape.browser.emu

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * On-demand emulator cores (Phase M2): the four
 * source-built cores live as assets on a dedicated
 * `cores` release in the Notscape repo, with a
 * cores.json manifest beside them. A download is
 * installed only after its size and SHA-256 match
 * the manifest; anything else is discarded and
 * reported, never loaded. The APK-bundled cores
 * stay as the fallback (M3 removes them).
 *
 * Downloaded cores live in filesDir/cores-downloaded,
 * apart from the APK-extracted copies in
 * filesDir/cores, so neither path can overwrite
 * the other. A sidecar <file>.sha256 records the
 * hash the file was verified against at install
 * time, so a later load can re-check without
 * fetching the manifest.
 */
object CoreDownload {
    /** The dedicated cores release's manifest. */
    const val MANIFEST_URL =
        "https://github.com/DocBobert6/NotscapeMariner" +
            "/releases/download/cores/cores.json"

    /** One core on the cores release. */
    data class Entry(
        val key: String,
        val file: String,
        val size: Long,
        val sha256: String,
        val pin: String,
        val consoles: List<String>
    ) {
        /** Public download URL for this core. */
        val url: String
            get() = "https://github.com/DocBobert6/NotscapeMariner" +
                "/releases/download/cores/$file"
    }

    /** State of a downloaded core for one console. */
    enum class Status { MISSING, DOWNLOADED, CORRUPT }

    /** Console key -> the core that plays it. */
    fun coreKeyFor(system: String): String? = when (system) {
        "gb", "gbc", "gba" -> "mgba"
        "nes" -> "fceumm"
        "snes" -> "snes9x"
        "genesis", "sms", "gg" -> "genesis_plus_gx"
        else -> null
    }

    /** All four core keys, for Download all. */
    val allKeys: List<String>
        get() = listOf(
            "mgba", "fceumm", "snes9x", "genesis_plus_gx"
        )

    fun downloadedDir(context: Context): File =
        File(context.filesDir, "cores-downloaded").apply { mkdirs() }

    private fun downloadedFile(
        context: Context,
        fileName: String
    ): File = File(downloadedDir(context), fileName)

    private fun sidecarFile(
        context: Context,
        fileName: String
    ): File = File(downloadedDir(context), "$fileName.sha256")

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(65536)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Parse the cores.json manifest (exact schema
     * the publish job writes; unknown fields ignored). */
    fun parseManifest(json: String): List<Entry> {
        val root = JSONObject(json)
        val cores = root.getJSONArray("cores")
        val out = ArrayList<Entry>()
        for (i in 0 until cores.length()) {
            val o = cores.getJSONObject(i)
            val consoles = ArrayList<String>()
            val arr = o.optJSONArray("consoles")
            if (arr != null) {
                for (j in 0 until arr.length()) {
                    consoles.add(arr.getString(j))
                }
            }
            out.add(
                Entry(
                    key = o.getString("key"),
                    file = o.getString("file"),
                    size = o.optLong("size", -1L),
                    sha256 = o.getString("sha256"),
                    pin = o.optString("pin", ""),
                    consoles = consoles
                )
            )
        }
        return out
    }

    /** Fetch the manifest from the cores release. */
    suspend fun fetchManifest(): Result<List<Entry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn =
                    URL(MANIFEST_URL).openConnection()
                        as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                try {
                    if (conn.responseCode != 200) {
                        throw IllegalStateException(
                            "Cores release returned " +
                                "${conn.responseCode}"
                        )
                    }
                    val text = conn.inputStream.bufferedReader()
                        .use { it.readText() }
                    parseManifest(text)
                } finally {
                    conn.disconnect()
                }
            }
        }

    /** The manifest entry for one console, or null. */
    fun entryFor(
        manifest: List<Entry>,
        system: String
    ): Entry? {
        val key = coreKeyFor(system) ?: return null
        return manifest.firstOrNull { it.key == key }
    }

    /** Pure state check: no network, no deletion. */
    fun status(context: Context, system: String): Status {
        val fileName = CoreStore.coreFileNameFor(system)
            ?: return Status.MISSING
        val file = downloadedFile(context, fileName)
        if (!file.exists()) return Status.MISSING
        val expected = runCatching {
            sidecarFile(context, fileName).readText().trim()
        }.getOrNull() ?: return Status.CORRUPT
        return runCatching {
            if (sha256Hex(file) == expected) {
                Status.DOWNLOADED
            } else {
                Status.CORRUPT
            }
        }.getOrDefault(Status.CORRUPT)
    }

    fun isDownloaded(context: Context, system: String): Boolean =
        status(context, system) == Status.DOWNLOADED

    /** Verified downloaded path, or null. A corrupt
     * download is deleted here so the APK fallback
     * can take over; callers that want to report it
     * check status() first. */
    fun downloadedPath(context: Context, system: String): String? {
        val fileName = CoreStore.coreFileNameFor(system)
            ?: return null
        return when (status(context, system)) {
            Status.DOWNLOADED ->
                downloadedFile(context, fileName).absolutePath
            Status.CORRUPT -> {
                downloadedFile(context, fileName).delete()
                sidecarFile(context, fileName).delete()
                null
            }
            Status.MISSING -> null
        }
    }

    fun removeDownloaded(context: Context, system: String) {
        val fileName = CoreStore.coreFileNameFor(system)
            ?: return
        downloadedFile(context, fileName).delete()
        sidecarFile(context, fileName).delete()
    }

    /** Download one core, verifying size and SHA-256
     * against the manifest before installing. The
     * failure message is user-facing: it names the
     * refusal reason plainly. */
    suspend fun download(
        context: Context,
        entry: Entry,
        onProgress: (Int) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = downloadedDir(context)
            val tmp = File(dir, entry.file + ".tmp")
            val final = File(dir, entry.file)
            val sidecar = File(dir, entry.file + ".sha256")
            try {
                val conn =
                    URL(entry.url).openConnection()
                        as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                try {
                    if (conn.responseCode != 200) {
                        throw IllegalStateException(
                            "Core download returned " +
                                "${conn.responseCode}"
                        )
                    }
                    val total = conn.contentLengthLong
                    val digest =
                        MessageDigest.getInstance("SHA-256")
                    var read = 0L
                    var lastPct = -1
                    conn.inputStream.use { input ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(65536)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                digest.update(buf, 0, n)
                                read += n
                                if (total > 0) {
                                    val pct =
                                        (read * 100 / total).toInt()
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        onProgress(pct)
                                    }
                                }
                            }
                        }
                    }
                    if (entry.size > 0 && read != entry.size) {
                        throw IllegalStateException(
                            "Downloaded core is the wrong " +
                                "size (${read} bytes, expected " +
                                "${entry.size}) and was discarded"
                        )
                    }
                    val hash = digest.digest().joinToString("") {
                        "%02x".format(it)
                    }
                    if (hash != entry.sha256.lowercase()) {
                        throw IllegalStateException(
                            "Downloaded core failed " +
                                "verification and was discarded"
                        )
                    }
                    if (final.exists()) final.delete()
                    if (!tmp.renameTo(final)) {
                        tmp.copyTo(final, overwrite = true)
                        tmp.delete()
                    }
                    sidecar.writeText(entry.sha256.lowercase())
                    onProgress(100)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                tmp.delete()
                throw e
            }
        }
    }
}
