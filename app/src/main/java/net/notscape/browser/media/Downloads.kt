package net.notscape.browser.media

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import net.notscape.browser.data.AppStore
import net.notscape.browser.data.DownloadRecord
import java.io.File

object Downloads {

    fun start(context: Context, store: AppStore, title: String, url: String): Long {
        val fileName = guessFileName(url, title)
        val rec = DownloadRecord(
            id = System.currentTimeMillis() * 1000 + (0..999).random(),
            title = title.ifBlank { fileName },
            url = url,
            fileName = fileName,
            dmId = -1,
            status = "queued",
            addedAt = System.currentTimeMillis()
        )
        store.addDownload(rec)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val req = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(rec.title)
            setDescription(url)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, "notscape/$fileName")
            setAllowedOverMetered(true)
        }
        val dmId = runCatching { dm.enqueue(req) }.getOrElse {
            store.updateDownload(rec.id, "failed")
            return rec.id
        }
        store.updateDownload(rec.id, "downloading", dmId)
        return rec.id
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

    fun localFile(context: Context, rec: DownloadRecord): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "notscape/${rec.fileName}")

    private fun guessFileName(url: String, title: String): String {
        val fromUrl = Uri.parse(url).lastPathSegment?.takeIf { it.contains(".") }
        if (fromUrl != null) return sanitize(fromUrl)
        val ext = when {
            url.contains(".m3u8") -> "mp4"
            url.contains(".mp3") -> "mp3"
            url.contains(".webm") -> "webm"
            else -> "mp4"
        }
        val base = title.ifBlank { "media" }.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48)
        return "$base.$ext"
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
}
