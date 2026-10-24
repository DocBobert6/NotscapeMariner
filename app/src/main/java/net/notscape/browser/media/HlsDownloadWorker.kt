package net.notscape.browser.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import net.notscape.browser.data.AppStore

/**
 * Runs one HLS download job in the background (Phase D): the job
 * survives the app process dying, and WorkManager retries it with
 * backoff on transient network failures — the staged segment files
 * make each retry a resume, not a restart.
 */
class HlsDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val recId = inputData.getLong("recId", -1)
        if (recId < 0) return Result.failure()
        val referer = inputData.getString("referer")
        val store = AppStore.get(applicationContext)
        // The store loads asynchronously (0.0.988); a worker
        // can be the first thing in a fresh process, so wait
        // for the truth before judging the record gone.
        store.awaitReady()
        val rec = store.downloadsSnapshot().firstOrNull { it.id == recId }
            ?: return Result.success() // record deleted meanwhile
        runCatching { setForeground(foregroundInfo(rec.title)) }
        return when (Downloads.runHlsJob(applicationContext, store, rec, referer)) {
            Downloads.HlsOutcome.OK -> Result.success()
            Downloads.HlsOutcome.RETRY ->
                if (runAttemptCount >= 3) {
                    // Retries exhausted: say so on the record -
                    // until 0.0.99991 the job died here silently
                    // and the row sat at downloading 0% forever.
                    store.updateDownload(recId, "failed", progress = -1)
                    Result.failure()
                } else {
                    Result.retry()
                }
            else -> Result.failure()
        }
    }

    private fun foregroundInfo(title: String): ForegroundInfo {
        ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(title)
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

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 4711

        fun ensureChannel(context: Context) {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val mgr = context.getSystemService(NotificationManager::class.java)
                if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                    mgr.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID, "Downloads",
                            NotificationManager.IMPORTANCE_LOW
                        )
                    )
                }
            }
        }
    }
}
