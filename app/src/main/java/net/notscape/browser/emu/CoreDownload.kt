package net.notscape.browser.emu

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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

    /** The manifest's detached signature, beside it on
     * the same release. */
    const val MANIFEST_SIGNATURE_URL =
        "https://github.com/DocBobert6/NotscapeMariner" +
            "/releases/download/cores/cores.json.sig"

    /**
     * The pinned public key the manifest signature is
     * verified against (S-M1): X.509 DER, base64. The
     * matching private key lives only as the
     * CORES_SIGNING_KEY repo secret and signs every
     * cores.json publish. ECDSA P-256, not Ed25519:
     * Ed25519 Signature/KeyFactory exist in Android
     * platform providers only from API 33, while this
     * app supports API 24+; ECDSA verifies on every
     * supported level with the same fail-closed
     * property.
     */
    const val MANIFEST_PUBLIC_KEY_BASE64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEWEHv73KOSiYlToTUCnui6Ihs1JYE2T2//sroGTpxjEr8j7StfdZFHgVjMtQnTR2rXLsdfqOI4LdR35YBQnV+9A=="

    /** One core on the cores release. */
    data class Entry(
        val key: String,
        val file: String,
        val size: Long,
        val sha256: String,
        /** Informational metadata only: never used for
         * trust decisions. Trust comes from the
         * manifest signature (S-M1) plus each core's
         * size and SHA-256, never from this field. */
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
        "ps1" -> "swanstation"
        "ds" -> "melonds"
        "segacd" -> "genesis_plus_gx"
        "32x" -> "picodrive"
        else -> null
    }

    /** Expected on-disk file for a manifest core key
     * (audit S-M2): the only name a core download
     * may ever write. The loader uses the same fixed
     * names, so a manifest entry naming anything
     * else is never the core the app will load. */
    fun expectedFileFor(key: String): String? = when (key) {
        "mgba" -> "mgba_libretro_android.so"
        "fceumm" -> "fceumm_libretro_android.so"
        "snes9x" -> "snes9x_libretro_android.so"
        "genesis_plus_gx" -> "genesis_plus_gx_libretro_android.so"
        "swanstation" -> "swanstation_libretro_android.so"
        "melonds" -> "melonds_libretro_android.so"
        "picodrive" -> "picodrive_libretro_android.so"
        else -> null
    }

    /** Rejects a manifest entry whose file is not
     * exactly the expected name for its key (audit
     * S-M2): no slash, no traversal, no surprise
     * name. Returns the safe file name. */
    fun validatedFileFor(entry: Entry): String {
        val expected = expectedFileFor(entry.key)
            ?: throw IllegalStateException(
                "Unknown core key in manifest: ${entry.key}"
            )
        if (entry.file != expected) {
            throw IllegalStateException(
                "Manifest core file name does not " +
                    "match its key; refusing download"
            )
        }
        return expected
    }

    /** All seven core keys, for Download all. */
    val allKeys: List<String>
        get() = listOf(
            "mgba", "fceumm", "snes9x", "genesis_plus_gx", "swanstation",
            "melonds", "picodrive"
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

    /**
     * Minimal standard-alphabet Base64 decoder. Deliberately
     * not android.util.Base64 (an Android stub under local
     * JVM unit tests) and not java.util.Base64 (API 26+,
     * above this app's minSdk 24): this runs identically
     * on device and in tests. Whitespace and padding are
     * ignored; the leftover length decides the final
     * partial group.
     */
    internal fun base64Decode(input: String): ByteArray {
        val table =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val vals = input.filter { it in table }
        val out = ArrayList<Byte>()
        var i = 0
        while (i + 4 <= vals.length) {
            val a = table.indexOf(vals[i])
            val b = table.indexOf(vals[i + 1])
            val c = table.indexOf(vals[i + 2])
            val d = table.indexOf(vals[i + 3])
            out.add(((a shl 2) or (b shr 4)).toByte())
            out.add((((b and 15) shl 4) or (c shr 2)).toByte())
            out.add((((c and 3) shl 6) or d).toByte())
            i += 4
        }
        when (vals.length - i) {
            2 -> {
                val a = table.indexOf(vals[i])
                val b = table.indexOf(vals[i + 1])
                out.add(((a shl 2) or (b shr 4)).toByte())
            }
            3 -> {
                val a = table.indexOf(vals[i])
                val b = table.indexOf(vals[i + 1])
                val c = table.indexOf(vals[i + 2])
                out.add(((a shl 2) or (b shr 4)).toByte())
                out.add((((b and 15) shl 4) or (c shr 2)).toByte())
            }
        }
        return out.toByteArray()
    }

    /**
     * S-M1: true only when [signature] is a valid
     * ECDSA P-256 / SHA-256 signature over [manifest]
     * under the pinned public key. Any failure — bad
     * key, malformed signature, wrong bytes — is false,
     * never an exception, so callers fail closed.
     * The [publicKeyBase64] parameter exists so unit
     * tests can verify against a generated keypair
     * instead of the pinned key.
     */
    fun verifyManifestSignature(
        manifest: ByteArray,
        signature: ByteArray,
        publicKeyBase64: String = MANIFEST_PUBLIC_KEY_BASE64
    ): Boolean = runCatching {
        val keyBytes = base64Decode(publicKeyBase64)
        val keySpec = java.security.spec.X509EncodedKeySpec(keyBytes)
        val key = java.security.KeyFactory.getInstance("EC")
            .generatePublic(keySpec)
        val verifier = java.security.Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(key)
        verifier.update(manifest)
        verifier.verify(signature)
    }.getOrDefault(false)

    private fun fetchBytes(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        try {
            if (conn.responseCode != 200) {
                throw IllegalStateException(
                    "Cores release returned ${conn.responseCode} for $url"
                )
            }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Fetch the manifest from the cores release, and
     * refuse it unless its detached signature verifies
     * against the pinned public key (S-M1). A missing
     * or invalid signature fails closed: the manifest
     * is the trust root for native code, so an
     * unsigned or tampered manifest yields no cores
     * at all rather than cores from an untrusted list.
     */
    suspend fun fetchManifest(): Result<List<Entry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val manifestBytes = fetchBytes(MANIFEST_URL)
                val signatureBytes = fetchBytes(MANIFEST_SIGNATURE_URL)
                if (!verifyManifestSignature(manifestBytes, signatureBytes)) {
                    throw IllegalStateException(
                        "Cores manifest signature is missing or " +
                            "invalid; refusing to trust it"
                    )
                }
                parseManifest(manifestBytes.decodeToString())
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

    /** True when an installed core differs from the
     * manifest entry: that is the update check. A
     * missing installed hash is not an update (that
     * console needs a download, not an update), and
     * the comparison is case-insensitive because the
     * manifest and the sidecar both lowercase. */
    fun isUpdateAvailable(
        installedSha256: String?,
        manifestSha256: String
    ): Boolean =
        installedSha256 != null &&
            installedSha256.lowercase() != manifestSha256.lowercase()

    /** Core keys for [systems], deduped in first-seen
     * order; unknown systems are dropped. Genesis Plus
     * GX therefore appears once for its four consoles. */
    fun dedupeKeys(systems: List<String>): List<String> {
        val out = ArrayList<String>()
        for (system in systems) {
            val key = coreKeyFor(system) ?: continue
            if (!out.contains(key)) out.add(key)
        }
        return out
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
     * download is deleted here so the next attempt
     * re-downloads it; callers that want to report
     * it check status() first. */
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

    /**
     * The downloaded file when present, without
     * hashing it: callers that already checked
     * status() use this instead of paying for a
     * second full-file SHA-256 via downloadedPath().
     */
    fun downloadedFileIfPresent(
        context: Context,
        system: String
    ): File? {
        val fileName = CoreStore.coreFileNameFor(system) ?: return null
        val file = downloadedFile(context, fileName)
        return file.takeIf { it.exists() }
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
        val result = runCatching {
            val safeFile = validatedFileFor(entry)
            val dir = downloadedDir(context)
            val tmp = File(dir, "$safeFile.tmp")
            // Canonical-path defence in depth
            // (S-M2): the destination must stay
            // inside the cores directory.
            val dirCanon = dir.canonicalPath + File.separator
            if (!File(dir, safeFile).canonicalPath
                    .startsWith(dirCanon) ||
                !tmp.canonicalPath.startsWith(dirCanon)
            ) {
                throw IllegalStateException(
                    "Core destination escapes the " +
                        "cores directory"
                )
            }
            // Range resume (audit P-m9): a cancelled
            // attempt leaves its .tmp; a retry resumes
            // it instead of restarting. A leftover at
            // or past the expected size cannot be a
            // valid prefix, so it restarts fresh.
            val existing = if (tmp.exists()) tmp.length() else 0L
            val resumable = existing > 0L &&
                (entry.size <= 0L || existing < entry.size)
            if (tmp.exists() && !resumable) tmp.delete()
            val final = File(dir, safeFile)
            val sidecar = File(dir, "$safeFile.sha256")
            try {
                val conn =
                    URL(entry.url).openConnection()
                        as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 60000
                if (resumable) {
                    conn.setRequestProperty(
                        "Range", "bytes=$existing-"
                    )
                }
                try {
                    val code = conn.responseCode
                    val resumed = resumable && code == 206
                    if (resumed) {
                        val range =
                            conn.getHeaderField("Content-Range") ?: ""
                        if (!range.startsWith("bytes $existing-")) {
                            throw IllegalStateException(
                                "Core server refused resume; " +
                                    "retry will restart"
                            )
                        }
                    } else if (code != 200) {
                        throw IllegalStateException(
                            "Core download returned $code"
                        )
                    }
                    val remaining = conn.contentLengthLong
                    val total = if (resumed && remaining >= 0) {
                        existing + remaining
                    } else {
                        remaining
                    }
                    val digest =
                        MessageDigest.getInstance("SHA-256")
                    var read = 0L
                    var lastPct = -1
                    if (resumed) {
                        // Seed the digest with the
                        // kept prefix, so the final
                        // hash covers the whole file.
                        tmp.inputStream().use { input ->
                            val buf = ByteArray(65536)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                digest.update(buf, 0, n)
                            }
                        }
                        read = existing
                    }
                    conn.inputStream.use { input ->
                        val outStream = if (resumed) {
                            FileOutputStream(tmp, true)
                        } else {
                            tmp.outputStream()
                        }
                        outStream.use { out ->
                            val buf = ByteArray(65536)
                            while (true) {
                                ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                digest.update(buf, 0, n)
                                read += n
                                if (total > 0) {
                                    val pct =
                                        (read * 100 / total).toInt()
                                    // 5% steps (P-m9):
                                    // per-percent state
                                    // writes recomposed
                                    // the download dialog.
                                    if (pct % 5 == 0 &&
                                        pct != lastPct
                                    ) {
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
                // Cancellation keeps the .tmp for
                // Range resume (P-m9); any other
                // failure drops it, so a corrupt
                // prefix can never be resumed.
                if (e !is CancellationException) tmp.delete()
                throw e
            }
        }
        // runCatching swallows CancellationException;
        // rethrow so a cancelled download aborts the
        // job instead of reporting as a failure.
        result.exceptionOrNull()?.let {
            if (it is CancellationException) throw it
        }
        result
    }
}
