package net.notscape.browser.media

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import net.notscape.browser.MainActivity
import net.notscape.browser.data.AppStore
import net.notscape.browser.ui.LauncherPins

/**
 * Runs one media-merge job in the background: download a
 * picked video file and a picked audio file, then remux
 * them into one MP4. The DashDownloadWorker pattern:
 * survives the process dying, retries transient failures
 * with backoff, and reports honestly on failure.
 */
class MergeDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val recId = inputData.getLong("recId", -1)
        if (recId < 0) return Result.failure()
        val videoUrl = inputData.getString("videoUrl") ?: return Result.failure()
        val audioUrl = inputData.getString("audioUrl") ?: return Result.failure()
        val referer = inputData.getString("referer")
        val store = AppStore.get(applicationContext)
        store.awaitReady()
        val rec = store.downloadsSnapshot().firstOrNull { it.id == recId }
            ?: return Result.success() // record deleted meanwhile
        runCatching { setForeground(foregroundInfo(rec.title)) }
        val outcome = Downloads.runMergeJob(
            applicationContext, store, rec, videoUrl, audioUrl, referer
        ) { pct -> postProgress(rec.title, pct) }
        return when (outcome) {
            Downloads.HlsOutcome.OK -> {
                postTerminal(store, recId, rec.title, true)
                Result.success()
            }
            Downloads.HlsOutcome.RETRY ->
                if (runAttemptCount >= 3) {
                    store.updateDownload(recId, "failed", progress = -1)
                    postTerminal(store, recId, rec.title, false)
                    Result.failure()
                } else {
                    Result.retry()
                }
            else -> {
                val cancelled = store.downloadsSnapshot()
                    .firstOrNull { it.id == recId }
                    ?.status == "cancelled"
                if (!cancelled) postTerminal(store, recId, rec.title, false)
                Result.failure()
            }
        }
    }

    private fun postProgress(title: String, pct: Int) {
        runCatching {
            HlsDownloadWorker.ensureChannel(applicationContext)
            val mgr = applicationContext
                .getSystemService(NotificationManager::class.java)
            val notification = NotificationCompat
                .Builder(applicationContext, "downloads")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading")
                .setContentText("$title • $pct%")
                .setProgress(100, pct, false)
                .setOngoing(true)
                .build()
            mgr.notify(NOTIFICATION_ID, notification)
        }
    }

    // Terminal notice on its own id. Tapping a
    // finished download opens the file with the
    // system app picker; tapping a failed one opens
    // the Offline library.
    private fun postTerminal(store: AppStore, recId: Long, title: String, ok: Boolean) {
        runCatching {
            HlsDownloadWorker.ensureChannel(applicationContext)
            // A finished download opens with the system app
            // picker: a plain VIEW intent for the file, so
            // Android offers Just once / Always. (A chooser
            // intent would force the picker every time and
            // drop the Always choice.) When the file cannot
            // be resolved, or the download failed, the tap
            // still opens the Offline library as before.
            // The record is re-read here: the DASH join can
            // replace it on a rename, leaving the worker's
            // copy with a stale file name.
            val fresh = store.downloadsSnapshot()
                .firstOrNull { it.id == recId }
            val view = if (ok && fresh != null) {
                Downloads.openIntent(
                    applicationContext, store, fresh
                )?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else null
            val intent = view ?: Intent(
                applicationContext, MainActivity::class.java
            ).apply {
                putExtra(LauncherPins.SECTION_EXTRA, "library")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pending = PendingIntent.getActivity(
                applicationContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )
            val mgr = applicationContext
                .getSystemService(NotificationManager::class.java)
            val notification = NotificationCompat
                .Builder(applicationContext, "downloads")
                .setSmallIcon(
                    if (ok) android.R.drawable.stat_sys_download_done
                    else android.R.drawable.stat_notify_error
                )
                .setContentTitle(
                    if (ok) "Download complete" else "Download failed"
                )
                .setContentText(title)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            mgr.notify(DONE_NOTIFICATION_ID, notification)
        }
    }

    private fun foregroundInfo(title: String): ForegroundInfo {
        HlsDownloadWorker.ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, "downloads")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(title)
            .setProgress(100, 0, true)
            .setOngoing(true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        private const val NOTIFICATION_ID = 4715
        private const val DONE_NOTIFICATION_ID = 4716
    }
}
