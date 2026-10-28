package net.notscape.browser.media

import android.app.NotificationChannel
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
 * Runs one segmented download job in the background (v1): the job
 * survives the app process dying, and WorkManager retries it with
 * backoff on transient network failures — the journal makes each
 * retry a per-chunk resume, not a restart. Mirrors HlsDownloadWorker
 * (same channel, same terminal-notice shape) with its own
 * notification ids.
 */
class SegmentedDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val recId = inputData.getLong("recId", -1)
        if (recId < 0) return Result.failure()
        val referer = inputData.getString("referer")
        val finalUrl = inputData.getString("finalUrl") ?: return Result.failure()
        val total = inputData.getLong("total", -1)
        if (total < 0) return Result.failure()
        val cookie = inputData.getString("cookie")?.takeIf { it.isNotBlank() }
        val md5 = inputData.getString("md5")?.takeIf { it.isNotBlank() }
        val store = AppStore.get(applicationContext)
        // The store loads asynchronously (0.0.988); a worker
        // can be the first thing in a fresh process, so wait
        // for the truth before judging the record gone.
        store.awaitReady()
        val rec = store.downloadsSnapshot().firstOrNull { it.id == recId }
            ?: return Result.success() // record deleted meanwhile
        // A record paused or cancelled while this worker was
        // queued must not run the engine: pause() already owns
        // the slot handoff, and cancel owns the terminal state.
        if (rec.status == "paused" || rec.status == "cancelled") return Result.failure()
        runCatching { setForeground(foregroundInfo(recId, rec.title, rec.isPrivate)) }
        val outcome = Downloads.runSegmentedJob(
            applicationContext, store, rec, referer, finalUrl, total, cookie, md5
        ) { pct -> postProgress(recId, rec.title, pct, rec.isPrivate) }
        return when (outcome) {
            Downloads.SegmentedOutcome.OK -> {
                postTerminal(store, recId, rec.title, true, rec.isPrivate)
                Downloads.promoteQueued(applicationContext, store)
                Result.success()
            }
            Downloads.SegmentedOutcome.RETRY ->
                if (runAttemptCount >= 3) {
                    // Retries exhausted: say so on the record —
                    // until 0.0.99991 the job died here silently
                    // and the row sat at downloading 0% forever.
                    Downloads.writeTerminalIfNotPaused(store, recId, "failed", progress = -1)
                    val termStatus = store.downloadsSnapshot()
                        .firstOrNull { it.id == recId }?.status
                    if (termStatus != "cancelled" && termStatus != "paused" && !Downloads.isPaused(recId)) postTerminal(store, recId, rec.title, false, rec.isPrivate)
                    Downloads.promoteQueued(applicationContext, store)
                    Result.failure()
                } else {
                    Result.retry()
                }
            Downloads.SegmentedOutcome.PAUSED -> {
                // User pause: no terminal notice, no status
                // write (the record already says "paused");
                // pause() already promoted the next queued job.
                Result.failure()
            }
            Downloads.SegmentedOutcome.DOWNGRADE -> {
                // Second 429: hand the job to DownloadManager
                // (single connection) and let the system finish
                // it. The record keeps "downloading" and refresh()
                // picks up DownloadManager progress via dmId.
                val dmId = Downloads.enqueueDownloadManager(
                    applicationContext, store, rec, rec.url, referer
                )
                if (dmId >= 0) {
                    store.updateDownload(recId, "downloading", dmId)
                    Result.success()
                } else {
                    Downloads.writeTerminalIfNotPaused(store, recId, "failed", progress = -1)
                    val termStatus = store.downloadsSnapshot()
                        .firstOrNull { it.id == recId }?.status
                    if (termStatus != "cancelled" && termStatus != "paused" && !Downloads.isPaused(recId)) postTerminal(store, recId, rec.title, false, rec.isPrivate)
                    Downloads.promoteQueued(applicationContext, store)
                    Result.failure()
                }
            }
            else -> {
                // A user-cancelled or user-paused job reports
                // FAILED from the engine; neither earns a
                // "failed" notification, and a pause keeps
                // its staging for resume.
                val termStatus = store.downloadsSnapshot()
                    .firstOrNull { it.id == recId }
                    ?.status
                if (termStatus != "cancelled" && termStatus != "paused" && !Downloads.isPaused(recId)) postTerminal(store, recId, rec.title, false, rec.isPrivate)
                Downloads.promoteQueued(applicationContext, store)
                Result.failure()
            }
        }
    }

    // The foreground notification, kept live at the
    // engine's progress steps (beta.9 pattern).
    private fun postProgress(recId: Long, title: String, pct: Int, isPrivate: Boolean) {
        runCatching {
            val mgr = applicationContext
                .getSystemService(NotificationManager::class.java)
            val notification = NotificationCompat
                .Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading")
                .setContentText(Downloads.progressNoticeText(isPrivate, title, pct))
                .setProgress(100, pct, false)
                .setOngoing(true)
                .build()
            mgr.notify(Downloads.notificationIdFor(recId, false), notification)
        }
    }

    // Terminal notice on its own id, so it survives
    // the foreground notification going away with the
    // worker. Tapping a finished download opens the
    // file with the system app picker; tapping a
    // failed one opens the Offline library, where the
    // failure reason lives.
    private fun postTerminal(store: AppStore, recId: Long, title: String, ok: Boolean, isPrivate: Boolean) {
        // F-M6: a private download announces nothing —
        // neither its success nor its failure may appear
        // in the system shade with its title.
        if (isPrivate) return
        runCatching {
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
                applicationContext, Downloads.notificationIdFor(recId, true), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )
            val mgr = applicationContext
                .getSystemService(NotificationManager::class.java)
            val notification = NotificationCompat
                .Builder(applicationContext, CHANNEL_ID)
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
            mgr.notify(Downloads.notificationIdFor(recId, true), notification)
        }
    }

    private fun foregroundInfo(recId: Long, title: String, isPrivate: Boolean): ForegroundInfo {
        HlsDownloadWorker.ensureChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(Downloads.foregroundNoticeText(isPrivate, title))
            .setProgress(100, 0, true)
            .setOngoing(true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                Downloads.notificationIdFor(recId, false),
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(Downloads.notificationIdFor(recId, false), notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
    }
}
