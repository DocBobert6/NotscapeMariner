package net.notscape.browser.media

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import net.notscape.browser.data.AppStore

/**
 * Runs one DASH download job in the background (0.0.9996),
 * the HlsDownloadWorker pattern: survives the process dying,
 * retries transient failures with backoff, and the staged
 * segment files make each retry a resume.
 */
class DashDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val recId = inputData.getLong("recId", -1)
        if (recId < 0) return Result.failure()
        val referer = inputData.getString("referer")
        val store = AppStore.get(applicationContext)
        store.awaitReady()
        val rec = store.downloadsSnapshot().firstOrNull { it.id == recId }
            ?: return Result.success() // record deleted meanwhile
        runCatching { setForeground(foregroundInfo(rec.title)) }
        return when (Downloads.runDashJob(applicationContext, store, rec, referer)) {
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
        HlsDownloadWorker.ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, "downloads")
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
        private const val NOTIFICATION_ID = 4712
    }
}
