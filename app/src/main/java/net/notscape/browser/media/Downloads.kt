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
    fun start(
        context: Context,
        store: AppStore,
        title: String,
        url: String,
        media: Boolean = false,
        contentType: String? = null,
        suggestedName: String? = null
    ): Long {
        val id = System.currentTimeMillis() * 1000 + (0..999).random()
        val appCtx = context.applicationContext
        Thread {
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
            val dm = appCtx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(rec.title)
                setDescription(url)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
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
