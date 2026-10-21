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
            val fileName = resolveFileName(url, title, contentType, suggestedName)
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
            if (isHls) {
                // A playlist is not a video: download the actual stream
                // segments and join them into one playable file.
                store.updateDownload(rec.id, "downloading")
                val ok = downloadHls(appCtx, store, url, referer, destFile(appCtx, store, fileName))
                store.updateDownload(rec.id, if (ok) "complete" else "failed")
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
                for (seg in segments) {
                    openGet(seg, referer).inputStream.use { it.copyTo(out) }
                }
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
            url.contains(".m3u8") -> "mp4"
            url.contains(".mpd") -> "mp4"
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
        for (rec in store.downloads.toList()) {
            if (rec.dmId < 0) continue
            if (rec.status == "complete" || rec.status == "failed") continue
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
                    if (newStatus != rec.status) {
                        store.updateDownload(rec.id, newStatus)
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
}
