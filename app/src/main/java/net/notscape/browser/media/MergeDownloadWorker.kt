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
                postTerminal(rec.title, true)
                Result.success()
            }
            Downloads.HlsOutcome.RETRY ->
                if (runAttemptCount >= 3) {
                    store.updateDownload(recId, "failed", progress = -1)
                    postTerminal(rec.title, false)
                    Result.failure()
                } else {
                    Result.retry()
                }
            else -> {
                val cancelled = store.downloadsSnapshot()
                    .firstOrNull { it.id == recId }
                    ?.status == "cancelled"
                if (!cancelled) postTerminal(rec.title, false)
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

    // Terminal notice on its own id; tapping it
    // opens the Offline library.
    private fun postTerminal(title: String, ok: Boolean) {
        runCatching {
            HlsDownloadWorker.ensureChannel(applicationContext)
            val intent = Intent(
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
