package net.notscape.browser.media

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import net.notscape.browser.data.AppStore
import net.notscape.browser.data.DownloadRecord
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Downloads {

    /**
     * Start a download. The file name is resolved properly first (on a
     * background thread): Content-Disposition and the real content type
     * are honoured, so an image URL with no extension in it still lands
     * as e.g. `photo.jpg` — a real photo the gallery can open — instead
     * of an extension-less blob named after the raw URL.
     *
     * [media] marks downloads that belong in the offline library (videos
     * and audio saved from Find media). Ordinary file/image downloads go
     * to the phone's Downloads folder and stay out of the library.
     */
    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    /** In-flight HLS downloads the user has cancelled (checked between segments). */
    private val cancelledIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    /** Cancel a download: DownloadManager entries are removed; HLS joins stop at the next segment. */
    fun cancel(context: Context, store: AppStore, rec: DownloadRecord) {
        cancelledIds.add(rec.id)
        if (rec.dmId >= 0) {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            runCatching { dm.remove(rec.dmId) }
        }
        store.updateDownload(rec.id, "cancelled", progress = -1)
    }

    fun start(
        context: Context,
        store: AppStore,
        title: String,
        url: String,
        media: Boolean = false,
        contentType: String? = null,
        suggestedName: String? = null,
        referer: String? = null
    ): Long {
        val id = System.currentTimeMillis() * 1000 + (0..999).random()
        val appCtx = context.applicationContext
        Thread {
            val isHls = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ||
                contentType?.contains("mpegurl", ignoreCase = true) == true
            val isDash = url.substringBefore('?').endsWith(".mpd", ignoreCase = true)
            val fileName = if (isHls) {
                // The saved file is a joined MPEG-TS stream: name it .ts so
                // other players treat it as what it is.
                val base = (suggestedName ?: Uri.parse(url).lastPathSegment ?: title)
                    .substringBeforeLast('.')
                sanitize(base) + ".ts"
            } else {
                resolveFileName(url, title, contentType, suggestedName)
            }
            val rec = DownloadRecord(
                id = id,
                title = title.ifBlank { fileName },
                url = url,
                fileName = fileName,
                dmId = -1,
                status = "queued",
                addedAt = System.currentTimeMillis(),
                media = media
            )
            store.addDownload(rec)
            if (isDash) {
                // DASH segments aren't joined by the downloader; the stream
                // plays in the built-in player instead. Fail honestly
                // rather than saving the manifest as if it were a video.
                store.updateDownload(rec.id, "failed")
                return@Thread
            }
            if (isHls) {
                // A playlist is not a video: download the actual stream
                // segments and join them into one playable file.
                cancelledIds.remove(rec.id)
                store.updateDownload(rec.id, "downloading", progress = 0)
                // For the public Downloads folder on modern Android, the
                // join goes to a private staging file first: scoped
                // storage blocks raw file writes into Downloads, so the
                // finished file is published through MediaStore below.
                val useMediaStore = store.setting("downloadLocation", "public") != "app" &&
                    android.os.Build.VERSION.SDK_INT >= 29
                val joinDest = if (useMediaStore) {
                    File(
                        appCtx.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                        "notscape-staging/$fileName"
                    )
                } else {
                    destFile(appCtx, store, fileName)
                }
                val ok = downloadHls(appCtx, store, rec.id, url, referer, joinDest)
                val wasCancelled = cancelledIds.remove(rec.id)
                if (ok && !wasCancelled && useMediaStore) {
                    val uri = publishToMediaStore(appCtx, joinDest, fileName)
                    if (uri != null) {
                        store.updateDownloadUri(rec.id, uri.toString())
                        joinDest.delete()
                    } else {
                        // Publishing failed: keep the joined file in
                        // staging and remember the path, so the library
                        // can still play and delete it.
                        store.updateDownloadUri(rec.id, joinDest.absolutePath)
                    }
                }
                store.updateDownload(
                    rec.id,
                    when {
                        wasCancelled -> "cancelled"
                        ok -> "complete"
                        else -> "failed"
                    },
                    progress = if (ok && !wasCancelled) 100 else -1
                )
                return@Thread
            }
            val dm = appCtx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(rec.title)
                setDescription(url)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                // Many CDNs refuse DownloadManager's bare default request:
                // send the browser's UA, the page referer, and any session
                // cookies the browser already holds for this URL.
                addRequestHeader("User-Agent", UA)
                if (!referer.isNullOrBlank()) addRequestHeader("Referer", referer)
                runCatching {
                    android.webkit.CookieManager.getInstance().getCookie(url)
                }.getOrNull()?.let { addRequestHeader("Cookie", it) }
                if (store.setting("downloadLocation", "public") == "app") {
                    setDestinationInExternalFilesDir(
                        appCtx, Environment.DIRECTORY_MOVIES, "notscape/$fileName"
                    )
                } else {
                    // Straight into the phone's Downloads folder.
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS, fileName
                    )
                }
                setAllowedOverMetered(true)
            }
            val dmId = runCatching { dm.enqueue(req) }.getOrElse {
                store.updateDownload(rec.id, "failed")
                return@Thread
            }
            store.updateDownload(rec.id, "downloading", dmId)
        }.start()
        return id
    }

    private fun destFile(context: Context, store: AppStore, fileName: String): File =
        if (store.setting("downloadLocation", "public") == "app") {
            File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "notscape/$fileName")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        }

    private fun mimeFor(fileName: String): String =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "ts" -> "video/mp2t"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            else -> "application/octet-stream"
        }

    /**
     * Publish a finished file into the public Downloads folder through
     * MediaStore — the only way in under scoped storage. Returns the
     * content URI, or null on any failure (caller falls back gracefully).
     */
    private fun publishToMediaStore(context: Context, file: File, displayName: String): Uri? {
        if (android.os.Build.VERSION.SDK_INT < 29) return null
        val resolver = context.contentResolver
        val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeFor(displayName))
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        val copied = runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: throw java.io.IOException("MediaStore stream unavailable")
            true
        }.getOrDefault(false)
        if (!copied) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        runCatching {
            resolver.update(
                uri,
                android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                },
                null, null
            )
        }
        return uri
    }

    /** Find a file in the public Downloads folder by display name. */
    private fun findInMediaStore(context: Context, fileName: String): Uri? {
        if (android.os.Build.VERSION.SDK_INT < 29) return null
        val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        return runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(android.provider.MediaStore.MediaColumns._ID),
                "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(fileName),
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    android.content.ContentUris.withAppendedId(collection, c.getLong(0))
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun openGet(url: String, referer: String?): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", UA)
        if (!referer.isNullOrBlank()) conn.setRequestProperty("Referer", referer)
        runCatching {
            android.webkit.CookieManager.getInstance().getCookie(url)
        }.getOrNull()?.let { conn.setRequestProperty("Cookie", it) }
        return conn
    }

    /**
     * Minimal VOD HLS downloader: resolves a master playlist to its
     * highest-bandwidth variant, then concatenates the media segments
     * into [dest] (an MPEG-TS stream, playable by the built-in player).
     * Encrypted or fMP4 playlists are refused honestly (returns false)
     * rather than saving an unplayable file.
     */
    private fun downloadHls(
        context: Context,
        store: AppStore,
        recId: Long,
        playlistUrl: String,
        referer: String?,
        dest: File
    ): Boolean {
        return try {
            var url = playlistUrl
            var text: String
            var hops = 0
            while (true) {
                text = openGet(url, referer).inputStream.bufferedReader().readText()
                if (!text.contains("#EXT-X-STREAM-INF") || hops >= 2) break
                // Master playlist: pick the highest-bandwidth variant.
                var bestBandwidth = -1L
                var bestUri: String? = null
                val lines = text.lines()
                for (i in lines.indices) {
                    val line = lines[i].trim()
                    if (line.startsWith("#EXT-X-STREAM-INF")) {
                        val bw = Regex("BANDWIDTH=(\\d+)").find(line)
                            ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                        val uriLine = lines.getOrNull(i + 1)?.trim()
                        if (!uriLine.isNullOrBlank() && !uriLine.startsWith("#") && bw > bestBandwidth) {
                            bestBandwidth = bw
                            bestUri = uriLine
                        }
                    }
                }
                val next = bestUri ?: break
                url = URL(URL(url), next).toString()
                hops++
            }
            if (text.contains("#EXT-X-KEY") &&
                !text.contains("METHOD=NONE")
            ) return false
            if (text.contains("#EXT-X-MAP")) return false
            val segments = text.lines().map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { URL(URL(url), it).toString() }
            if (segments.isEmpty()) return false
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".part")
            tmp.outputStream().use { out ->
                segments.forEachIndexed { idx, seg ->
                    if (cancelledIds.contains(recId)) {
                        throw InterruptedException("cancelled")
                    }
                    openGet(seg, referer).inputStream.use { it.copyTo(out) }
                    // Throttle progress saves: a full store write per
                    // segment would be wasteful on long streams.
                    val pct = (idx + 1) * 100 / segments.size
                    if (pct % 5 == 0 || idx == segments.lastIndex) {
                        store.updateDownload(recId, "downloading", progress = pct)
                    }
                }
            }
            if (cancelledIds.contains(recId)) {
                tmp.delete()
                return false
            }
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun resolveFileName(
        url: String,
        title: String,
        contentType: String?,
        suggestedName: String?
    ): String {
        var name = suggestedName?.takeIf { it.substringAfterLast('.', "").length in 2..5 }
        var mime = contentType
        if (name == null) {
            // Ask the server what this actually is (headers only).
            runCatching {
                var conn = openHead(url)
                if (conn.responseCode in 300..399) {
                    conn.getHeaderField("Location")?.let { loc ->
                        conn.disconnect()
                        conn = openHead(URL(URL(url), loc).toString())
                    }
                }
                if (mime == null) mime = conn.contentType?.substringBefore(';')?.trim()
                val cd = conn.getHeaderField("Content-Disposition")
                if (cd != null) {
                    name = URLUtil.guessFileName(url, cd, mime)
                        ?.takeIf { it.substringAfterLast('.', "").length in 2..5 }
                }
                conn.disconnect()
            }
        }
        if (name == null) {
            val segment = Uri.parse(url).lastPathSegment
                ?.let { runCatching { Uri.decode(it) }.getOrNull() }
                ?.takeIf { it.isNotBlank() && !it.contains("://") }
            name = when {
                segment != null && segment.contains('.') -> segment
                segment != null -> {
                    val ext = extensionFor(url, mime)
                    if (ext != null) "$segment.$ext" else segment
                }
                else -> {
                    val base = title.ifBlank { "download" }
                    val ext = extensionFor(url, mime)
                    if (ext != null) "$base.$ext" else base
                }
            }
        }
        return sanitize(name!!)
    }

    private fun openHead(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.requestMethod = "HEAD"
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
        )
        conn.connect()
        return conn
    }

    private fun extensionFor(url: String, mime: String?): String? {
        if (mime != null) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { return it }
        }
        return when {
            url.contains(".m3u8") -> "ts"
            url.contains(".mp3") -> "mp3"
            else -> null
        }
    }

    private fun sanitize(name: String): String {
        val cleaned = name.trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('_', '.', ' ')
            .take(100)
        return cleaned.ifBlank { "download" }
    }

    /** Refresh statuses from DownloadManager. Returns true if anything changed. */
    fun refresh(context: Context, store: AppStore): Boolean {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var changed = false
        // Backfill MediaStore locations for finished public downloads
        // that don't have one yet (e.g. DownloadManager downloads, or
        // ones completed by an older version): the library needs a
        // content URI to reach them under scoped storage.
        if (store.setting("downloadLocation", "public") != "app") {
            for (rec in store.downloadsSnapshot()) {
                if (rec.status == "complete" && rec.contentUri.isBlank()) {
                    findInMediaStore(context, rec.fileName)?.let {
                        store.updateDownloadUri(rec.id, it.toString())
                        changed = true
                    }
                }
            }
        }
        for (rec in store.downloadsSnapshot()) {
            if (rec.dmId < 0) continue
            if (rec.status == "complete" || rec.status == "failed" || rec.status == "cancelled") continue
            val cursor = runCatching {
                dm.query(DownloadManager.Query().setFilterById(rec.dmId))
            }.getOrNull() ?: continue
            cursor.use {
                if (it.moveToFirst()) {
                    val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val newStatus = when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> "complete"
                        DownloadManager.STATUS_FAILED -> "failed"
                        DownloadManager.STATUS_PAUSED -> "paused"
                        DownloadManager.STATUS_PENDING -> "queued"
                        else -> "downloading"
                    }
                    val soFar = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val pct = when {
                        newStatus == "complete" -> 100
                        total > 0 -> (soFar * 100 / total).toInt()
                        else -> -1
                    }
                    if (newStatus != rec.status || pct != rec.progress) {
                        store.updateDownload(rec.id, newStatus, progress = pct)
                        changed = true
                    }
                }
            }
        }
        return changed
    }

    fun localFile(context: Context, store: AppStore, rec: DownloadRecord): File =
        if (store.setting("downloadLocation", "public") == "app") {
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "notscape/${rec.fileName}"
            )
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                rec.fileName
            )
        }

    /**
     * Where a finished download's bytes actually are, as a string the
     * player accepts (a content:// URI or an absolute path): the
     * recorded MediaStore location when there is one — verified to
     * still exist, since the user may have deleted the file elsewhere —
     * otherwise the legacy setting-based file, if present.
     */
    fun localSource(context: Context, store: AppStore, rec: DownloadRecord): String? {
        if (rec.contentUri.isNotBlank()) {
            return if (rec.contentUri.startsWith("content://")) {
                val exists = runCatching {
                    context.contentResolver.openInputStream(Uri.parse(rec.contentUri))
                        ?.use { true } ?: false
                }.getOrDefault(false)
                if (exists) rec.contentUri else null
            } else {
                if (File(rec.contentUri).exists()) rec.contentUri else null
            }
        }
        val f = localFile(context, store, rec)
        return if (f.exists()) f.absolutePath else null
    }

    /** Delete a finished download's bytes, wherever they live. */
    fun deleteLocal(context: Context, store: AppStore, rec: DownloadRecord) {
        when {
            rec.contentUri.startsWith("content://") ->
                runCatching {
                    context.contentResolver.delete(Uri.parse(rec.contentUri), null, null)
                }
            rec.contentUri.isNotBlank() ->
                runCatching { File(rec.contentUri).delete() }
            else -> runCatching { localFile(context, store, rec).delete() }
        }
    }
}
