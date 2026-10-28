package net.notscape.browser.media

import android.app.DownloadManager
import android.content.Context
import androidx.core.content.FileProvider
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Environment
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import net.notscape.browser.data.AppStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import net.notscape.browser.data.DownloadRecord
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

object Downloads {

    /** Background work that isn't a download job itself (the
     *  download kickoff, the EPUB auto-shelf) runs here instead of
     *  on raw Threads (Phase F coroutines step). HLS downloads
     *  proper still run under WorkManager. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    /** In-flight worker downloads the user has paused (checked between
     *  segments/chunks, next to cancelledIds). Pause is deliberately
     *  separate from cancel: a paused job keeps its staging files and
     *  journal, so resume continues where it stopped instead of
     *  starting over. */
    private val pausedIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    /** Workers check this as well as the record status: pause()
     *  marks the id before it writes "paused", so a job winding
     *  down at the same moment still suppresses its terminal. */
    internal fun isPaused(id: Long): Boolean = pausedIds.contains(id)

    /** How many downloads run at once; further starts wait queued. */
    internal const val MAX_CONCURRENT_DOWNLOADS = 3

    /** True when no download slot is free. Pure, for tests. */
    internal fun shouldQueue(activeCount: Int): Boolean =
        activeCount >= MAX_CONCURRENT_DOWNLOADS

    /** Downloads currently running (not queued, paused, or finished). */
    internal fun activeDownloadCount(store: AppStore): Int =
        store.downloadsSnapshot().count { it.status == "downloading" }

    /**
     * Per-download notification ids, derived from the record id so
     * concurrent downloads of the same type no longer overwrite each
     * other's shade notifications. Progress and completion live in
     * distinct ranges, so the terminal notice survives the
     * foreground-service notification being removed. Pure, for tests.
     */
    internal fun notificationIdFor(recordId: Long, done: Boolean): Int {
        val slot = (recordId % 100000).toInt()
        return if (done) 900000 + slot else 800000 + slot
    }

    /**
     * F-M6: the text of a worker progress notice. A
     * private download never carries its title into
     * the system shade — only a generic "Downloading"
     * plus the percentage, which reveals nothing about
     * what is being fetched.
     */
    internal fun progressNoticeText(
        isPrivate: Boolean,
        title: String,
        pct: Int
    ): String =
        if (isPrivate) "Downloading • $pct%" else "$title • $pct%"

    /**
     * F-M6: the text of a worker foreground notice.
     * Same rule as progressNoticeText: private downloads
     * show only the generic word, never the title.
     */
    internal fun foregroundNoticeText(
        isPrivate: Boolean,
        title: String
    ): String = if (isPrivate) "Downloading" else title

    // ---------- per-download job bundles ----------
    //
    // A DownloadRecord carries no referer, no segmented probe data, and
    // no merge side URLs, so the queue and resume paths could not
    // re-enqueue a job from the record alone. The inputs a job needs
    // are therefore persisted alongside it in one store setting,
    // "dljob:<id>", as URL-encoded key=value pairs. The bundle is
    // written at start time, updated when routing picks an engine,
    // and cleared when the download leaves the active set (cancel,
    // delete, or terminal completion). It never holds credentials
    // beyond the referer/cookie the engine already sends.

    internal fun encodeJobBundle(fields: Map<String, String>): String =
        fields.entries.joinToString("&") { (k, v) ->
            java.net.URLEncoder.encode(k, "UTF-8") + "=" +
                java.net.URLEncoder.encode(v, "UTF-8")
        }

    internal fun decodeJobBundle(encoded: String): Map<String, String> {
        if (encoded.isBlank()) return emptyMap()
        val out = linkedMapOf<String, String>()
        for (pair in encoded.split("&")) {
            val eq = pair.indexOf('=')
            if (eq < 0) continue
            val k = java.net.URLDecoder.decode(pair.substring(0, eq), "UTF-8")
            val v = java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
            out[k] = v
        }
        return out
    }

    internal fun saveJobBundle(store: AppStore, id: Long, fields: Map<String, String>) {
        store.putSetting("dljob:$id", encodeJobBundle(fields))
    }

    internal fun readJobBundle(store: AppStore, id: Long): Map<String, String>? {
        val raw = store.setting("dljob:$id", "")
        return if (raw.isBlank()) null else decodeJobBundle(raw)
    }

    internal fun updateJobBundle(store: AppStore, id: Long, patch: Map<String, String>) {
        val merged = (readJobBundle(store, id) ?: emptyMap()).toMutableMap()
        merged.putAll(patch)
        saveJobBundle(store, id, merged)
    }

    internal fun clearJobBundle(store: AppStore, id: Long) {
        store.removeSetting("dljob:$id")
    }

    /** The engine a record is running (or ran) under, "" if never routed. */
    internal fun engineFor(store: AppStore, rec: DownloadRecord): String =
        readJobBundle(store, rec.id)?.get("engine").orEmpty()

    /** Engines whose jobs can genuinely resume from staging/journal. */
    internal fun isResumableEngine(engine: String): Boolean =
        engine == "hls" || engine == "dash" || engine == "seg"

    /** Per-download destination override, else the global setting. */
    internal fun locationFor(store: AppStore, rec: DownloadRecord): String {
        val override = readJobBundle(store, rec.id)?.get("location").orEmpty()
        return if (override.isNotBlank()) override
            else store.setting("downloadLocation", "public")
    }

    /**
     * Apply a per-download rename, keeping the engine's extension:
     * HLS saves .ts, DASH saves .mp4, plain keeps the resolved
     * extension unless the new name carries its own. Pure, for tests.
     */
    internal fun applyFileNameOverride(
        override: String?,
        resolved: String,
        isHls: Boolean,
        isDash: Boolean
    ): String {
        if (override.isNullOrBlank()) return resolved
        val trimmed = override.trim()
        return when {
            isHls -> sanitize(trimmed.substringBeforeLast('.')) + ".ts"
            isDash -> sanitize(trimmed.substringBeforeLast('.')) + ".mp4"
            else -> {
                val ext = trimmed.substringAfterLast('.', "")
                if (ext.isNotEmpty() && ext.length <= 5 && !trimmed.endsWith(".")) {
                    sanitize(trimmed)
                } else {
                    val resolvedExt = resolved.substringAfterLast('.', "")
                    val name = if (resolvedExt.isNotEmpty() && resolvedExt.length <= 5 &&
                        !trimmed.contains('.')
                    ) "$trimmed.$resolvedExt" else trimmed
                    sanitize(name)
                }
            }
        }
    }

    private fun statusOf(store: AppStore, id: Long): String? =
        store.downloadsSnapshot().firstOrNull { it.id == id }?.status

    /**
     * Terminal writes (failed/cancelled) must not clobber a pause that
     * landed while the job was winding down: the user asked to
     * resume later, not to lose the job.
     */
    internal fun writeTerminalIfNotPaused(
        store: AppStore,
        id: Long,
        status: String,
        progress: Int? = null
    ) {
        if (statusOf(store, id) == "paused" && status != "paused") return
        store.updateDownload(id, status, progress = progress)
    }

    /**
     * Fill free download slots from the queue, oldest first. Every
     * path that frees a slot (cancel, pause, worker terminal,
     * refresh) funnels through here, so the limit is enforced in
     * exactly one place. Never-started records route fresh; routed
     * records re-enqueue through their saved engine.
     */
    fun promoteQueued(context: Context, store: AppStore) {
        ioScope.launch {
            val appCtx = context.applicationContext
            synchronized(queueLock) {
                while (activeDownloadCount(store) < MAX_CONCURRENT_DOWNLOADS) {
                    val next = store.downloadsSnapshot()
                        .firstOrNull { it.status == "queued" } ?: break
                    val bundle = readJobBundle(store, next.id)
                    val engine = bundle?.get("engine").orEmpty()
                    if (engine.isBlank()) {
                        routeNewDownload(
                            appCtx, store, next, next.url,
                            bundle?.get("contentType")?.ifBlank { null },
                            bundle?.get("referer")?.ifBlank { null },
                            next.media
                        )
                    } else if (bundle != null) {
                        routeDirect(appCtx, store, next, bundle)
                    }
                }
            }
        }
    }

    private val queueLock = Any()

    /**
     * Re-enqueue a previously routed download through its saved
     * engine. Used by queue promotion and by resume; the record's
     * file name and progress are kept, and the engines pick up
     * their staging files/journal from disk.
     */
    private fun routeDirect(
        appCtx: Context,
        store: AppStore,
        rec: DownloadRecord,
        bundle: Map<String, String>
    ) {
        val referer = bundle["referer"]?.ifBlank { null }
        cancelledIds.remove(rec.id)
        pausedIds.remove(rec.id)
        store.updateDownload(rec.id, "downloading")
        when (bundle["engine"]) {
            "hls" -> {
                val data = androidx.work.Data.Builder()
                    .putLong("recId", rec.id)
                    .putString("referer", referer)
                    .build()
                val request = androidx.work.OneTimeWorkRequestBuilder<HlsDownloadWorker>()
                    .setInputData(data)
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build()
                    )
                    .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        30, java.util.concurrent.TimeUnit.SECONDS
                    )
                    .addTag("hls-${rec.id}")
                    .build()
                runCatching {
                    androidx.work.WorkManager.getInstance(appCtx).enqueue(request)
                }.onFailure { store.updateDownload(rec.id, "failed") }
            }
            "dash" -> {
                val data = androidx.work.Data.Builder()
                    .putLong("recId", rec.id)
                    .putString("referer", referer)
                    .build()
                val request = androidx.work.OneTimeWorkRequestBuilder<DashDownloadWorker>()
                    .setInputData(data)
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build()
                    )
                    .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        30, java.util.concurrent.TimeUnit.SECONDS
                    )
                    .addTag("dash-${rec.id}")
                    .build()
                runCatching {
                    androidx.work.WorkManager.getInstance(appCtx).enqueue(request)
                }.onFailure { store.updateDownload(rec.id, "failed") }
            }
            "seg" -> {
                val data = androidx.work.Data.Builder()
                    .putLong("recId", rec.id)
                    .putString("referer", referer)
                    .putString("finalUrl", bundle["finalUrl"].orEmpty())
                    .putLong("total", bundle["total"]?.toLongOrNull() ?: 0L)
                    .putString("cookie", bundle["cookie"].orEmpty())
                    .putString("md5", bundle["md5"].orEmpty())
                    .build()
                val request = androidx.work.OneTimeWorkRequestBuilder<SegmentedDownloadWorker>()
                    .setInputData(data)
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build()
                    )
                    .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        30, java.util.concurrent.TimeUnit.SECONDS
                    )
                    .addTag("seg-${rec.id}")
                    .build()
                runCatching {
                    androidx.work.WorkManager.getInstance(appCtx).enqueue(request)
                }.onFailure { store.updateDownload(rec.id, "failed") }
            }
            "merge" -> {
                val data = androidx.work.Data.Builder()
                    .putLong("recId", rec.id)
                    .putString("videoUrl", bundle["videoUrl"].orEmpty())
                    .putString("audioUrl", bundle["audioUrl"].orEmpty())
                    .putString("referer", referer)
                    .build()
                val request = androidx.work.OneTimeWorkRequestBuilder<MergeDownloadWorker>()
                    .setInputData(data)
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build()
                    )
                    .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        30, java.util.concurrent.TimeUnit.SECONDS
                    )
                    .addTag("merge-${rec.id}")
                    .build()
                runCatching {
                    androidx.work.WorkManager.getInstance(appCtx).enqueue(request)
                }.onFailure { store.updateDownload(rec.id, "failed") }
            }
            "dm" -> {
                val dmId = enqueueDownloadManager(appCtx, store, rec, rec.url, referer)
                if (dmId < 0) {
                    store.updateDownload(rec.id, "failed")
                } else {
                    store.updateDownload(rec.id, "downloading", dmId)
                }
            }
            else -> routeNewDownload(
                appCtx, store, rec, rec.url, null, referer, rec.media
            )
        }
    }

    /**
     * Pause a running worker download. The job is told to stop (its
     * staging files and journal stay on disk), the record keeps its
     * progress, and the freed slot promotes the next queued download.
     * Returns false when there is nothing pausable: a queued, finished,
     * or plain-DownloadManager download, or an engine that cannot
     * resume. Plain DownloadManager downloads are not user-pausable
     * (the system API offers no pause); a system-paused one still
     * shows as "paused" via refresh(), but that is not this button.
     */
    fun pause(context: Context, store: AppStore, rec: DownloadRecord): Boolean {
        val fresh = store.downloadsSnapshot().firstOrNull { it.id == rec.id }
            ?: return false
        if (fresh.status != "downloading") return false
        if (!isResumableEngine(engineFor(store, fresh))) return false
        pausedIds.add(rec.id)
        runCatching {
            androidx.work.WorkManager.getInstance(context.applicationContext)
                .cancelAllWorkByTag("hls-${rec.id}")
        }
        runCatching {
            androidx.work.WorkManager.getInstance(context.applicationContext)
                .cancelAllWorkByTag("dash-${rec.id}")
        }
        runCatching {
            androidx.work.WorkManager.getInstance(context.applicationContext)
                .cancelAllWorkByTag("seg-${rec.id}")
        }
        runCatching {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            mgr.cancel(notificationIdFor(rec.id, false))
        }
        store.updateDownload(rec.id, "paused")
        promoteQueued(context, store)
        return true
    }

    /**
     * Resume a paused download: it rejoins the queue (oldest first),
     * so a resume never pushes the running count over the limit.
     * Returns false for anything that is not a paused, resumable
     * download.
     */
    fun resume(context: Context, store: AppStore, rec: DownloadRecord): Boolean {
        val fresh = store.downloadsSnapshot().firstOrNull { it.id == rec.id }
            ?: return false
        if (fresh.status != "paused") return false
        if (!isResumableEngine(engineFor(store, fresh))) return false
        pausedIds.remove(rec.id)
        store.updateDownload(rec.id, "queued")
        promoteQueued(context, store)
        return true
    }

    /** Cancel a download: DownloadManager entries are removed; worker jobs stop at the next segment/buffer. The freed slot promotes the next queued download. */
    fun cancel(context: Context, store: AppStore, rec: DownloadRecord) {
        cancelledIds.add(rec.id)
        pausedIds.remove(rec.id)
        runCatching {
            androidx.work.WorkManager.getInstance(context.applicationContext)
                .cancelAllWorkByTag("hls-${rec.id}")
        }
        runCatching {
            androidx.work.WorkManager.getInstance(context.applicationContext)
                .cancelAllWorkByTag("dash-${rec.id}")
        }
        runCatching {
            androidx.work.WorkManager.getInstance(context.applicationContext)
                .cancelAllWorkByTag("seg-${rec.id}")
        }
        runCatching {
            androidx.work.WorkManager.getInstance(context.applicationContext)
                .cancelAllWorkByTag("merge-${rec.id}")
        }
        if (rec.dmId >= 0) {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            runCatching { dm.remove(rec.dmId) }
        }
        clearJobBundle(store, rec.id)
        runCatching {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            mgr.cancel(notificationIdFor(rec.id, false))
            mgr.cancel(notificationIdFor(rec.id, true))
        }
        store.updateDownload(rec.id, "cancelled", progress = -1)
        promoteQueued(context, store)
    }

    /**
     * Route a never-started download to its engine: DASH, HLS,
     * segmented, or plain DownloadManager, exactly as start()
     * routed before the queue existed. Queue promotion calls this
     * for records whose bundle has no engine yet; the engine is
     * recorded in the bundle as soon as it is picked.
     */
    private fun routeNewDownload(
        appCtx: Context,
        store: AppStore,
        rec: DownloadRecord,
        url: String,
        contentType: String?,
        referer: String?,
        media: Boolean
    ) {
        // The branch conditions start() used to compute before
        // routing: they live here now that routing is shared
        // with queue promotion.
        val isHls = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ||
            contentType?.contains("mpegurl", ignoreCase = true) == true
        val isDash = url.substringBefore('?').endsWith(".mpd", ignoreCase = true)
        if (isDash) {
            // DASH downloads (0.0.9996): segments are fetched,
            // assembled per track and remuxed into one MP4 by a
            // WorkManager job, the HLS pattern. Manifests outside
            // the supported shapes fail honestly in the job
            // rather than saving a manifest as if it were a video.
            cancelledIds.remove(rec.id)
            pausedIds.remove(rec.id)
            store.updateDownloadProgress(rec.id, 0)
            updateJobBundle(store, rec.id, mapOf("engine" to "dash"))
            val data = androidx.work.Data.Builder()
                .putLong("recId", rec.id)
                .putString("referer", referer)
                .build()
            val request = androidx.work.OneTimeWorkRequestBuilder<DashDownloadWorker>()
                .setInputData(data)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    30, java.util.concurrent.TimeUnit.SECONDS
                )
                .addTag("dash-${rec.id}")
                .build()
            runCatching {
                androidx.work.WorkManager.getInstance(appCtx).enqueue(request)
            }.onFailure { store.updateDownload(rec.id, "failed") }
            return
        }
        if (isHls) {
            // A playlist is not a video: download the actual stream
            // segments and join them into one playable file. The
            // join runs as a WorkManager job (Phase D): it survives
            // the process dying, resumes from the segment manifest,
            // and retries transient network failures with backoff.
            cancelledIds.remove(rec.id)
            pausedIds.remove(rec.id)
            store.updateDownloadProgress(rec.id, 0)
            updateJobBundle(store, rec.id, mapOf("engine" to "hls"))
            val data = androidx.work.Data.Builder()
                .putLong("recId", rec.id)
                .putString("referer", referer)
                .build()
            val request = androidx.work.OneTimeWorkRequestBuilder<HlsDownloadWorker>()
                .setInputData(data)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    30, java.util.concurrent.TimeUnit.SECONDS
                )
                .addTag("hls-${rec.id}")
                .build()
            runCatching {
                androidx.work.WorkManager.getInstance(appCtx).enqueue(request)
            }.onFailure { store.updateDownload(rec.id, "failed") }
            return
        }
        // Segmented downloads (v1): eligible plain downloads go
        // to the segmented worker; anything ineligible falls
        // through to the DownloadManager path below, unchanged.
        if (store.setting("segmentedDownloads", "true") == "true") {
            val probe = probeSegmented(appCtx, url, referer)
            if (probe.eligible) {
                cancelledIds.remove(rec.id)
            pausedIds.remove(rec.id)
                store.updateDownloadProgress(rec.id, 0)
            updateJobBundle(store, rec.id, mapOf(
                "engine" to "seg",
                "finalUrl" to probe.finalUrl,
                "total" to probe.total.toString(),
                "cookie" to (probe.cookie ?: ""),
                "md5" to (probe.md5 ?: "")
            ))
                val data = androidx.work.Data.Builder()
                    .putLong("recId", rec.id)
                    .putString("referer", referer)
                    .putString("finalUrl", probe.finalUrl)
                    .putLong("total", probe.total)
                    .putString("cookie", probe.cookie ?: "")
                    .putString("md5", probe.md5 ?: "")
                    .build()
                val request = androidx.work.OneTimeWorkRequestBuilder<SegmentedDownloadWorker>()
                    .setInputData(data)
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build()
                    )
                    .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        30, java.util.concurrent.TimeUnit.SECONDS
                    )
                    .addTag("seg-${rec.id}")
                    .build()
                runCatching {
                    androidx.work.WorkManager.getInstance(appCtx).enqueue(request)
                }.onFailure { store.updateDownload(rec.id, "failed") }
                return
            }
        }
        val dmId = enqueueDownloadManager(appCtx, store, rec, url, referer)
        if (dmId < 0) {
            store.updateDownload(rec.id, "failed")
            return
        }
        updateJobBundle(store, rec.id, mapOf("engine" to "dm"))
        store.updateDownload(rec.id, "downloading", dmId)
    }

    fun start(
        context: Context,
        store: AppStore,
        title: String,
        url: String,
        media: Boolean = false,
        contentType: String? = null,
        suggestedName: String? = null,
        referer: String? = null,
        isPrivate: Boolean = false,
        location: String? = null,
        fileNameOverride: String? = null
    ): Long {
        val id = System.currentTimeMillis() * 1000 + (0..999).random()
        val appCtx = context.applicationContext
        ioScope.launch {
            val isHls = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ||
                contentType?.contains("mpegurl", ignoreCase = true) == true
            val isDash = url.substringBefore('?').endsWith(".mpd", ignoreCase = true)
            val resolved = if (isHls) {
                // The saved file is a joined MPEG-TS stream: name it .ts so
                // other players treat it as what it is.
                val base = (suggestedName ?: Uri.parse(url).lastPathSegment ?: title)
                    .substringBeforeLast('.')
                sanitize(base) + ".ts"
            } else if (isDash) {
                // Assembled and remuxed to a real MP4 (0.0.9996).
                val base = (suggestedName ?: Uri.parse(url).lastPathSegment ?: title)
                    .substringBeforeLast('.')
                sanitize(base) + ".mp4"
            } else {
                resolveFileName(url, title, contentType, suggestedName)
            }
            // A per-download rename (from the confirmation dialog) wins
            // over the resolved name, keeping the engine's extension.
            val fileName = applyFileNameOverride(fileNameOverride, resolved, isHls, isDash)
            val rec = DownloadRecord(
                id = id,
                title = title.ifBlank { fileName },
                url = url,
                fileName = fileName,
                dmId = -1,
                status = "queued",
                addedAt = System.currentTimeMillis(),
                media = media,
                isPrivate = isPrivate
            )
            store.addDownload(rec)
            saveJobBundle(store, rec.id, mapOf(
                "url" to url,
                "title" to title,
                "media" to media.toString(),
                "contentType" to (contentType ?: ""),
                "suggestedName" to (suggestedName ?: ""),
                "referer" to (referer ?: ""),
                "isPrivate" to isPrivate.toString(),
                "location" to (location ?: ""),
                "fileName" to fileName
            ))
            // The queue gate lives in promoteQueued alone: the
            // record starts at once when a slot is free and waits
            // (oldest first) when the limit is reached.
            promoteQueued(appCtx, store)
        }
        return id
    }

    /**
     * Enqueue a plain download with Android's DownloadManager
     * (single connection, system-managed). Shared by start()'s
     * plain path and by the segmented worker's 429 downgrade, so
     * both routes send identical headers and destinations.
     * Returns the DownloadManager id, or -1 on failure.
     */
    fun enqueueDownloadManager(
        context: Context,
        store: AppStore,
        rec: DownloadRecord,
        url: String,
        referer: String?
    ): Long {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        // Never silently overwrite an earlier download: a
        // colliding name gets a " (1)" suffix instead, and
        // the record is renamed to match the file on disk.
        val destDir = if (locationFor(store, rec) == "app") {
            File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "notscape")
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }
        val finalName = dedupFileName({ File(destDir, it).exists() }, rec.fileName)
        if (finalName != rec.fileName) {
            store.updateDownloadFileName(rec.id, finalName)
        }
        val req = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(rec.title)
            setDescription(url)
            setNotificationVisibility(
                // F-M6: a private download must not announce
                // itself in the system shade at all — hidden
                // visibility shows nothing, not even a title.
                if (rec.isPrivate) DownloadManager.Request.VISIBILITY_HIDDEN
                else DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            // Many CDNs refuse DownloadManager's bare default request:
            // send the browser's UA, the page referer, and any session
            // cookies the browser already holds for this URL.
            addRequestHeader("User-Agent", UA)
            if (!referer.isNullOrBlank()) addRequestHeader("Referer", referer)
            runCatching {
                android.webkit.CookieManager.getInstance().getCookie(url)
            }.getOrNull()?.let { addRequestHeader("Cookie", it) }
            if (locationFor(store, rec) == "app") {
                setDestinationInExternalFilesDir(
                    context, Environment.DIRECTORY_MOVIES, "notscape/$finalName"
                )
            } else {
                // Straight into the phone's Downloads folder.
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, finalName
                )
            }
            setAllowedOverMetered(true)
        }
        return runCatching { dm.enqueue(req) }.getOrDefault(-1)
    }

    private data class SegProbe(
        val eligible: Boolean,
        val finalUrl: String,
        val total: Long,
        val cookie: String?,
        val md5: String?
    )

    /**
     * Eligibility probe (spec §4), run on start()'s IO scope before
     * routing. Never throws: any probe failure means "fall back to
     * DownloadManager", never "fail the download". The cookie is
     * read here, once, and handed to the worker (spec §6) so all
     * chunks share one session.
     */
    private fun probeSegmented(
        context: Context,
        url: String,
        referer: String?
    ): SegProbe {
        val fallback = SegProbe(false, url, 0L, null, null)
        return runCatching {
            val rangeConn = openGetRange(url, referer, HlsPlaylist.ByteRange(1, 0))
            val code = rangeConn.responseCode
            val finalUrl = rangeConn.url.toString()
            val rangeTotal = if (code == 206) {
                SegmentedPlan.parseContentRange(rangeConn.getHeaderField("Content-Range"))
            } else {
                null
            }
            rangeConn.disconnect()
            var headLength: Long? = null
            var headAccept: String? = null
            var headMd5: String? = null
            if (code != 206 || rangeTotal == null) {
                // HEAD fallback (spec §4 step 2). A HEAD that fails
                // just leaves the fallback values null.
                runCatching {
                    val head = openGet(finalUrl, referer)
                    head.requestMethod = "HEAD"
                    if (head.responseCode < 400) {
                        headLength = head.contentLengthLong.takeIf { it > 0 }
                        headAccept = head.getHeaderField("Accept-Ranges")
                        // Content-MD5 on a HEAD covers the whole
                        // resource, so it is usable for the §7 gate.
                        // (A 206 response's Content-MD5 would cover
                        // only the 1-byte range, so it is ignored.)
                        headMd5 = head.getHeaderField("Content-MD5")
                    }
                    head.disconnect()
                }
            }
            when (val decision = SegmentedPlan.decideProbe(
                code, rangeTotal, headLength, headAccept
            )) {
                is SegmentedPlan.ProbeDecision.Eligible -> {
                    val cookie = runCatching {
                        android.webkit.CookieManager.getInstance().getCookie(finalUrl)
                    }.getOrNull()
                    SegProbe(true, finalUrl, decision.total, cookie, headMd5)
                }
                else -> fallback
            }
        }.getOrDefault(fallback)
    }

    /**
     * Like openGet, but with a caller-supplied cookie (read once at
     * job start, spec §6) instead of re-reading CookieManager per
     * chunk, so all chunks share one session.
     */
    private fun openSegmentedGet(
        url: String,
        referer: String?,
        cookie: String?
    ): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", UA)
        if (!referer.isNullOrBlank()) conn.setRequestProperty("Referer", referer)
        if (!cookie.isNullOrBlank()) conn.setRequestProperty("Cookie", cookie)
        return conn
    }

    enum class SegmentedOutcome { OK, FAILED, RETRY, DOWNGRADE, PAUSED }

    /**
     * Execute one segmented download job (the SegmentedDownloadWorker's
     * unit of work; also its retry unit). Owns the record's status
     * transitions except RETRY, which leaves it "downloading" for the
     * next attempt, and DOWNGRADE, which the worker turns into a
     * DownloadManager handoff. Staging and journal files carry the
     * record id in their names so two downloads sharing a display
     * name can never share a partial file.
     */
    fun runSegmentedJob(
        context: Context,
        store: AppStore,
        rec: DownloadRecord,
        referer: String?,
        finalUrl: String,
        total: Long,
        cookie: String?,
        md5: String?,
        onProgress: (Int) -> Unit = {}
    ): SegmentedOutcome {
        val appCtx = context.applicationContext
        val useMediaStore = locationFor(store, rec) != "app" &&
            android.os.Build.VERSION.SDK_INT >= 29
        val stagingDir = if (useMediaStore) {
            File(appCtx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "notscape-staging")
        } else {
            destFile(appCtx, store, rec.fileName, locationFor(store, rec)).parentFile!!
        }.apply { mkdirs() }
        val staging = File(stagingDir, "${rec.fileName}.${rec.id}.segpart")
        val journalFile = File(stagingDir, "${rec.fileName}.${rec.id}.segjournal")
        val chunks = SegmentedPlan.plan(total)
        if (chunks.isEmpty()) {
            // Should not happen (start() gated on eligibility), but
            // never spin a worker on an unplannable job.
            noteError(store, rec.id, "file too small to segment")
            writeTerminalIfNotPaused(store, rec.id, "failed", progress = -1)
            return SegmentedOutcome.FAILED
        }
        val done = LongArray(chunks.size)
        val resumed = staging.exists() && journalFile.exists()
        if (resumed) {
            val saved = runCatching {
                SegmentedPlan.parseJournal(journalFile.readText())
            }.getOrDefault(emptyMap())
            for (c in chunks) {
                done[c.index] = (saved[c.index] ?: 0L).coerceIn(0L, c.length)
            }
        }
        try {
            java.io.RandomAccessFile(staging, "rw").use { it.setLength(total) }
        } catch (e: Exception) {
            noteError(store, rec.id, errorLabel(e))
            writeTerminalIfNotPaused(store, rec.id, "failed", progress = -1)
            return SegmentedOutcome.FAILED
        }
        if (!resumed) writeSegJournal(journalFile, chunks, done)
        val written = java.util.concurrent.atomic.AtomicLong(done.sum())
        val lastPct = java.util.concurrent.atomic.AtomicInteger(-1)
        val rateLimited = java.util.concurrent.atomic.AtomicInteger(0)
        val downgraded = java.util.concurrent.atomic.AtomicBoolean(false)
        val paused = java.util.concurrent.atomic.AtomicBoolean(false)
        val hardFailure = java.util.concurrent.atomic.AtomicReference<Exception?>(null)
        val transientFailure = java.util.concurrent.atomic.AtomicBoolean(false)
        val reportProgress = {
            val pct = ((written.get() * 100) / total).toInt()
            if (lastPct.getAndSet(pct) != pct) {
                store.updateDownloadProgress(rec.id, pct)
                onProgress(pct)
            }
        }
        val downloadChunk = chunkLabel@{ chunk: SegmentedPlan.ChunkRange ->
            val raf = java.io.RandomAccessFile(staging, "rw")
            try {
                var chunkDone = done[chunk.index]
                var lastJournaled = chunkDone
                var attempt = 0
                while (chunkDone < chunk.length) {
                    if (cancelledIds.contains(rec.id)) throw InterruptedException("cancelled")
                    if (pausedIds.contains(rec.id)) {
                        // Pause keeps staging + journal: the chunk
                        // threads all stop here and resume picks
                        // up from the journal, not from zero.
                        paused.set(true)
                        writeSegJournal(journalFile, chunks, done)
                        return@chunkLabel
                    }
                    if (downgraded.get()) return@chunkLabel
                    attempt++
                    try {
                        val conn = openSegmentedGet(finalUrl, referer, cookie)
                        val from = chunk.start + chunkDone
                        conn.setRequestProperty("Range", "bytes=$from-${chunk.end}")
                        val code = conn.responseCode
                        when {
                            code == 429 -> {
                                val waitSecs = conn.getHeaderField("Retry-After")
                                    ?.toLongOrNull()?.coerceIn(1L, 60L) ?: 5L
                                conn.disconnect()
                                if (rateLimited.incrementAndGet() >= 2) {
                                    // Second 429 anywhere: stop
                                    // looking like a hammering client
                                    // and let the worker hand the job
                                    // to DownloadManager instead.
                                    downgraded.set(true)
                                    return@chunkLabel
                                }
                                Thread.sleep(waitSecs * 1000)
                                if (attempt >= 3) {
                                    transientFailure.set(true)
                                    return@chunkLabel
                                }
                                continue
                            }
                            code >= 400 -> {
                                conn.disconnect()
                                hardFailure.compareAndSet(null, HttpStatusException(code))
                                return@chunkLabel
                            }
                            code != 206 -> {
                                // A 200 here would mean the server
                                // stopped honouring Range mid-download:
                                // writing it at an offset would
                                // corrupt the file, so fail instead.
                                conn.disconnect()
                                hardFailure.compareAndSet(
                                    null,
                                    java.io.IOException("unexpected HTTP $code for range")
                                )
                                return@chunkLabel
                            }
                            else -> {
                                raf.seek(from)
                                conn.inputStream.use { ins ->
                                    val buf = ByteArray(65536)
                                    while (true) {
                                        if (cancelledIds.contains(rec.id)) {
                                            throw InterruptedException("cancelled")
                                        }
                                        if (pausedIds.contains(rec.id)) {
                                            paused.set(true)
                                            writeSegJournal(journalFile, chunks, done)
                                            runCatching { conn.disconnect() }
                                            return@chunkLabel
                                        }
                                        if (downgraded.get()) return@chunkLabel
                                        val n = ins.read(buf)
                                        if (n < 0) break
                                        // Never write past this
                                        // chunk's end, even if a
                                        // misbehaving server sends
                                        // more than the range asked.
                                        val remaining =
                                            (chunk.length - chunkDone).coerceAtMost(n.toLong())
                                        if (remaining <= 0) break
                                        raf.write(buf, 0, remaining.toInt())
                                        chunkDone += remaining
                                        done[chunk.index] = chunkDone
                                        written.addAndGet(remaining)
                                        reportProgress()
                                        if (chunkDone - lastJournaled >= 1024 * 1024) {
                                            lastJournaled = chunkDone
                                            writeSegJournal(journalFile, chunks, done)
                                        }
                                        if (chunkDone >= chunk.length) break
                                    }
                                }
                                conn.disconnect()
                                if (chunkDone < chunk.length) {
                                    // Short stream: retry from
                                    // chunkDone, not from zero.
                                    if (attempt >= 3) {
                                        transientFailure.set(true)
                                        return@chunkLabel
                                    }
                                    Thread.sleep(
                                        (1000L shl (attempt - 1)).coerceAtMost(4000L)
                                    )
                                    continue
                                }
                            }
                        }
                    } catch (e: InterruptedException) {
                        throw e
                    } catch (e: Exception) {
                        if (attempt >= 3) {
                            transientFailure.set(true)
                            return@chunkLabel
                        }
                        Thread.sleep((1000L shl (attempt - 1)).coerceAtMost(4000L))
                    }
                }
                writeSegJournal(journalFile, chunks, done)
            } finally {
                runCatching { raf.close() }
            }
        }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(chunks.size)
        for (chunk in chunks) {
            pool.submit { downloadChunk(chunk) }
        }
        pool.shutdown()
        runCatching {
            pool.awaitTermination(Long.MAX_VALUE, java.util.concurrent.TimeUnit.DAYS)
        }
        val wasCancelled = cancelledIds.remove(rec.id)
        if (wasCancelled) {
            staging.delete()
            journalFile.delete()
            writeTerminalIfNotPaused(store, rec.id, "cancelled", progress = -1)
            return SegmentedOutcome.FAILED
        }
        if (paused.get()) {
            // Pause, not failure: staging + journal stay, the
            // record already says "paused", and no terminal
            // write here may overwrite it.
            return SegmentedOutcome.PAUSED
        }
        if (downgraded.get()) {
            staging.delete()
            journalFile.delete()
            updateJobBundle(store, rec.id, mapOf("engine" to "dm"))
            return SegmentedOutcome.DOWNGRADE
        }
        hardFailure.get()?.let { e ->
            noteError(store, rec.id, errorLabel(e))
            writeTerminalIfNotPaused(store, rec.id, "failed", progress = -1)
            staging.delete()
            journalFile.delete()
            return SegmentedOutcome.FAILED
        }
        if (transientFailure.get()) {
            noteError(store, rec.id, "download interrupted")
            // Keep staging + journal: the next attempt resumes
            // per chunk instead of restarting the file.
            return SegmentedOutcome.RETRY
        }
        // Completion gate (spec §7): size is the only integrity
        // signal for plain downloads, and it is enforced. The
        // staging file is preallocated, so its length alone proves
        // nothing — the counted bytes are the check.
        val sizeOk = written.get() == total
        val md5Ok = if (md5.isNullOrBlank()) {
            true
        } else {
            runCatching {
                val digest = java.security.MessageDigest.getInstance("MD5")
                staging.inputStream().use { ins ->
                    val buf = ByteArray(65536)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        digest.update(buf, 0, n)
                    }
                }
                android.util.Base64.encodeToString(
                    digest.digest(), android.util.Base64.NO_WRAP
                ).let { it == md5.trim() }
            }.getOrDefault(false)
        }
        if (!sizeOk || !md5Ok) {
            noteError(store, rec.id, "download incomplete")
            writeTerminalIfNotPaused(store, rec.id, "failed", progress = -1)
            staging.delete()
            journalFile.delete()
            return SegmentedOutcome.FAILED
        }
        if (useMediaStore) {
            val uri = publishToMediaStore(appCtx, staging, rec.fileName)
            if (uri != null) {
                store.updateDownloadUri(rec.id, uri.toString())
                staging.delete()
            } else {
                // Publishing failed: keep the staged file and
                // remember the path, so the library can still play
                // and delete it (same fallback as runHlsJob).
                store.updateDownloadUri(rec.id, staging.absolutePath)
            }
        } else {
            var dest = destFile(appCtx, store, rec.fileName, locationFor(store, rec))
            if (dest.exists()) {
                val unique = dedupFileName(
                    { destFile(appCtx, store, it, locationFor(store, rec)).exists() }, rec.fileName
                )
                store.updateDownloadFileName(rec.id, unique)
                dest = destFile(appCtx, store, unique, locationFor(store, rec))
            }
            if (!staging.renameTo(dest)) {
                noteError(store, rec.id, "could not finish download")
                writeTerminalIfNotPaused(store, rec.id, "failed", progress = -1)
                staging.delete()
                journalFile.delete()
                return SegmentedOutcome.FAILED
            }
        }
        journalFile.delete()
        noteError(store, rec.id, null)
        store.updateDownload(rec.id, "complete", progress = 100)
        // EPUBs downloaded the plain way used to reach the bookshelf
        // via refresh() on DownloadManager completion; segmented
        // records bypass refresh() (dmId -1), so shelf them here.
        maybeAutoShelf(appCtx, store, rec)
        return SegmentedOutcome.OK
    }

    /** Journal writes come from several chunk threads at once. */
    private val segJournalLock = Any()

    private fun writeSegJournal(
        journalFile: File,
        chunks: List<SegmentedPlan.ChunkRange>,
        done: LongArray
    ) {
        synchronized(segJournalLock) {
            runCatching {
                journalFile.writeText(SegmentedPlan.journalText(chunks, done))
            }
        }
    }

    /**
     * Start a media merge: download a picked video file
     * and a picked audio file, then remux them into one
     * MP4 (MergeDownloadWorker, the DASH pattern).
     */
    fun startMerge(
        context: Context,
        store: AppStore,
        title: String,
        videoUrl: String,
        audioUrl: String,
        referer: String?,
        isPrivate: Boolean = false
    ): Long {
        val id = System.currentTimeMillis() * 1000 + (0..999).random()
        val appCtx = context.applicationContext
        val fileName = sanitize(title.ifBlank { "merged" }) + ".mp4"
        ioScope.launch {
            val rec = DownloadRecord(
                id = id,
                title = title.ifBlank { fileName },
                url = videoUrl,
                fileName = fileName,
                dmId = -1,
                status = "queued",
                addedAt = System.currentTimeMillis(),
                media = true,
                isPrivate = isPrivate
            )
            store.addDownload(rec)
            saveJobBundle(store, rec.id, mapOf(
                "engine" to "merge",
                "videoUrl" to videoUrl,
                "audioUrl" to audioUrl,
                "referer" to (referer ?: ""),
                "isPrivate" to isPrivate.toString()
            ))
            // Merge joins the queue like every other download; its
            // worker reads the side URLs from the bundle on promotion.
            promoteQueued(appCtx, store)
        }
        return id
    }

    private fun destFile(context: Context, store: AppStore, fileName: String): File =
        if (store.setting("downloadLocation", "public") == "app") {
            File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "notscape/$fileName")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        }

    /** Per-download variant: an override from the confirmation
     *  dialog wins over the global downloadLocation setting. */
    private fun destFile(context: Context, store: AppStore, fileName: String, location: String): File =
        if (location == "app") {
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

    /**
     * Find a finished download in the public Downloads folder.
     * Name-only matching can bind the WRONG file (two downloads can
     * share a display name), so a candidate must also have landed
     * within the record's time window, and if more than one
     * candidate qualifies the match is refused rather than guessed.
     */
    private fun findInMediaStore(context: Context, rec: DownloadRecord): Uri? {
        if (android.os.Build.VERSION.SDK_INT < 29) return null
        val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
        return runCatching {
            context.contentResolver.query(
                collection,
                arrayOf(
                    android.provider.MediaStore.MediaColumns._ID,
                    android.provider.MediaStore.MediaColumns.DATE_ADDED
                ),
                "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(rec.fileName),
                null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
                val dateCol = c.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATE_ADDED)
                var found: Uri? = null
                var ambiguous = false
                while (c.moveToNext()) {
                    val addedMs = c.getLong(dateCol) * 1000
                    // DATE_ADDED is when MediaStore first saw the
                    // file — for our downloads, close to when the
                    // record was created (2-day tolerance).
                    if (kotlin.math.abs(addedMs - rec.addedAt) <= 2L * 24 * 3600 * 1000) {
                        if (found != null) { ambiguous = true; break }
                        found = android.content.ContentUris.withAppendedId(collection, c.getLong(idCol))
                    }
                }
                if (ambiguous) null else found
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

    /** An HTTP error status from a download fetch
     *  (0.0.99991): carries the code so failures can be
     *  classified and, above all, REPORTED instead of
     *  swallowed by a blanket retry. */
    class HttpStatusException(val code: Int) :
        java.io.IOException("HTTP $code")

    private data class HlsResult(
        val outcome: HlsOutcome,
        val isMp4: Boolean = false,
        val error: String? = null
    )

    private fun checkStatus(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code >= 400) throw HttpStatusException(code)
    }

    private fun checkedBytes(conn: HttpURLConnection): ByteArray {
        checkStatus(conn)
        return conn.inputStream.use { it.readBytes() }
    }

    private fun errorLabel(e: Exception): String = when (e) {
        is HttpStatusException -> "HTTP ${e.code}"
        is java.net.SocketTimeoutException -> "Connection timed out"
        is java.net.UnknownHostException -> "Host not found"
        else -> (e.message ?: e.javaClass.simpleName).take(80)
    }

    /** The last failure reason for a download, kept as a
     *  setting so the library row can show WHY (0.0.99991). */
    private fun noteError(
        store: AppStore, recId: Long, error: String?
    ) {
        store.putSetting("dlerror:$recId", error ?: "")
    }

    enum class HlsOutcome { OK, FAILED, RETRY, UNSUPPORTED }

    /**
     * Execute one HLS download job (the WorkManager worker's unit of
     * work; also its retry unit). Owns the record's status
     * transitions except RETRY, which leaves it "downloading" for
     * the next attempt.
     */
    fun runHlsJob(context: Context, store: AppStore, rec: DownloadRecord, referer: String?, onProgress: (Int) -> Unit = {}): HlsOutcome {
        val appCtx = context.applicationContext
        val useMediaStore = locationFor(store, rec) != "app" &&
            android.os.Build.VERSION.SDK_INT >= 29
        var fileName = rec.fileName
        if (!useMediaStore) {
            // Legacy path writes straight to the final file:
            // never overwrite an earlier download.
            val unique = dedupFileName(
                { destFile(appCtx, store, it, locationFor(store, rec)).exists() }, fileName
            )
            if (unique != fileName) {
                store.updateDownloadFileName(rec.id, unique)
                fileName = unique
            }
        }
        // For the public Downloads folder on modern Android, the join
        // goes to a private staging file first: scoped storage blocks
        // raw file writes into Downloads, so the finished file is
        // published through MediaStore below.
        val joinDest = if (useMediaStore) {
            File(
                appCtx.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "notscape-staging/$fileName"
            )
        } else {
            destFile(appCtx, store, fileName, locationFor(store, rec))
        }
        val result = downloadHls(appCtx, store, rec.id, rec.url, referer, joinDest, onProgress)
        val outcome = result.outcome
        val isMp4 = result.isMp4
        val wasCancelled = cancelledIds.remove(rec.id)
        if (outcome == HlsOutcome.OK && !wasCancelled) {
            var publishName = fileName
            var publishFile = joinDest
            if (isMp4 && fileName.endsWith(".ts", ignoreCase = true)) {
                // CMAF stream: the joined file is an MP4 — name it so.
                publishName = fileName.substringBeforeLast('.') + ".mp4"
                if (File(joinDest.parentFile, publishName).exists()) {
                    publishName = dedupFileName(
                        { File(joinDest.parentFile, it).exists() }, publishName
                    )
                }
                val renamed = File(joinDest.parentFile, publishName)
                if (joinDest.renameTo(renamed)) {
                    publishFile = renamed
                    store.updateDownloadFileName(rec.id, publishName)
                }
            }
            if (useMediaStore) {
                val uri = publishToMediaStore(appCtx, publishFile, publishName)
                if (uri != null) {
                    store.updateDownloadUri(rec.id, uri.toString())
                    publishFile.delete()
                } else {
                    // Publishing failed: keep the joined file in
                    // staging and remember the path, so the library
                    // can still play and delete it.
                    store.updateDownloadUri(rec.id, publishFile.absolutePath)
                }
            }
        }
        return when {
            wasCancelled -> {
                writeTerminalIfNotPaused(store, rec.id, "cancelled", progress = -1)
                HlsOutcome.FAILED
            }
            outcome == HlsOutcome.OK -> {
                noteError(store, rec.id, null)
                store.updateDownload(rec.id, "complete", progress = 100)
                HlsOutcome.OK
            }
            outcome == HlsOutcome.RETRY -> {
                noteError(store, rec.id, result.error)
                HlsOutcome.RETRY
            }
            else -> {
                if (statusOf(store, rec.id) != "paused" && result.error != "Paused") noteError(store, rec.id, result.error)
                writeTerminalIfNotPaused(store, rec.id, "failed", progress = -1)
                outcome
            }
        }
    }

    private fun fetchText(url: String, referer: String?): String {
        val conn = openGet(url, referer)
        checkStatus(conn)
        return conn.inputStream.bufferedReader().readText()
    }

    private fun openGetRange(url: String, referer: String?, range: HlsPlaylist.ByteRange?): HttpURLConnection {
        val conn = openGet(url, referer)
        if (range != null) {
            val from = range.offset ?: 0L
            conn.setRequestProperty("Range", "bytes=$from-${from + range.length - 1}")
        }
        return conn
    }

    /**
     * HLS downloader (Phase D): resolves a master playlist to its
     * highest-bandwidth variant via [HlsPlaylist], then downloads
     * the media segments — staged as individual files so an
     * interrupted job resumes from what is already on disk — and
     * joins them. Supports AES-128 (explicit or sequence-derived
     * IV) and fMP4/CMAF (init segment + fragments concatenate into
     * a playable MP4, no remux). A variant whose audio lives in a
     * separate rendition is refused (UNSUPPORTED) rather than
     * silently joined without sound, as are non-AES-128 keys.
     * Returns the outcome and whether the joined file is an MP4.
     */
    private fun downloadHls(
        context: Context,
        store: AppStore,
        recId: Long,
        playlistUrl: String,
        referer: String?,
        dest: File,
        onProgress: (Int) -> Unit = {}
    ): HlsResult {
        return try {
            var url = playlistUrl
            var text = fetchText(url, referer)
            var hops = 0
            var separateAudio = false
            while (HlsPlaylist.isMaster(text) && hops < 2) {
                val master = HlsPlaylist.parseMaster(text, url)
                val best = master.bestVariant()
                    ?: return HlsResult(
                        HlsOutcome.FAILED,
                        error = "No playable variant in playlist"
                    )
                if (master.variantHasSeparateAudio(best)) separateAudio = true
                url = best.url
                text = fetchText(url, referer)
                hops++
            }
            if (separateAudio) {
                return HlsResult(
                    HlsOutcome.UNSUPPORTED,
                    error = "Separate audio rendition not supported"
                )
            }
            val plan = HlsPlaylist.parseMedia(text, url)
            if (plan.unsupportedKeyMethod != null) {
                return HlsResult(
                    HlsOutcome.UNSUPPORTED,
                    error = "Unsupported encryption: " +
                        plan.unsupportedKeyMethod
                )
            }
            if (plan.segments.isEmpty()) {
                return HlsResult(
                    HlsOutcome.FAILED,
                    error = "Playlist has no segments"
                )
            }

            val segDir = File(dest.parentFile, dest.name + ".segments")
            segDir.mkdirs()
            val keyCache = mutableMapOf<String, ByteArray>()
            fun keyBytes(key: HlsPlaylist.Key): ByteArray? {
                val uri = key.uri ?: return null
                return keyCache.getOrPut(uri) {
                    checkedBytes(openGet(uri, referer))
                }
            }
            var lastPct = -1
            plan.segments.forEachIndexed { idx, seg ->
                if (cancelledIds.contains(recId)) throw InterruptedException("cancelled")
                if (pausedIds.contains(recId)) throw InterruptedException("paused")
                val segFile = File(segDir, "seg-%05d.bin".format(idx))
                if (!segFile.exists()) {
                    val raw = checkedBytes(openGetRange(seg.url, referer, seg.range))
                    val bytes = if (seg.key != null && seg.key.method == "AES-128") {
                        val kb = keyBytes(seg.key)
                            ?: throw java.io.IOException("HLS key fetch failed")
                        val iv = seg.key.ivHex?.let { HlsPlaylist.parseIvHex(it) }
                            ?: HlsPlaylist.sequenceIv(seg.sequence)
                        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
                        cipher.init(
                            javax.crypto.Cipher.DECRYPT_MODE,
                            javax.crypto.spec.SecretKeySpec(kb, "AES"),
                            javax.crypto.spec.IvParameterSpec(iv)
                        )
                        cipher.doFinal(raw)
                    } else {
                        raw
                    }
                    val tmpSeg = File(segDir, "seg-%05d.tmp".format(idx))
                    tmpSeg.writeBytes(bytes)
                    tmpSeg.renameTo(segFile)
                }
                val pct = (idx + 1) * 100 / plan.segments.size
                // The same percentage is never written twice
                // (0.0.9993): with 100+ segment streams the old
                // check fired repeatedly per step, and a resume
                // re-fired them all.
                if ((pct % 5 == 0 || idx == plan.segments.lastIndex) &&
                    pct != lastPct
                ) {
                    lastPct = pct
                    store.updateDownloadProgress(recId, pct)
                    onProgress(pct)
                }
            }
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".part")
            tmp.outputStream().use { out ->
                if (plan.initUrl != null) {
                    val initConn = openGetRange(plan.initUrl, referer, plan.initRange)
                    checkStatus(initConn)
                    initConn.inputStream.use { it.copyTo(out) }
                }
                plan.segments.indices.forEach { idx ->
                    File(segDir, "seg-%05d.bin".format(idx)).inputStream().use { it.copyTo(out) }
                }
            }
            if (cancelledIds.contains(recId)) {
                tmp.delete()
                return HlsResult(HlsOutcome.FAILED, error = "Cancelled")
            }
            if (pausedIds.contains(recId)) {
                // The join is partial and discarded; the staged
                // segments stay, so resume re-joins from them.
                tmp.delete()
                return HlsResult(HlsOutcome.FAILED, error = "Paused")
            }
            var writeDest = dest
            if (writeDest.exists() && writeDest.parentFile?.name != "notscape-staging") {
                val unique = dedupFileName({ File(writeDest.parentFile, it).exists() }, writeDest.name)
                store.updateDownloadFileName(recId, unique)
                writeDest = File(writeDest.parentFile, unique)
            }
            tmp.renameTo(writeDest)
            segDir.deleteRecursively()
            HlsResult(HlsOutcome.OK, isMp4 = plan.isCmaf)
        } catch (e: InterruptedException) {
            HlsResult(HlsOutcome.FAILED, error = "Cancelled")
        } catch (e: HttpStatusException) {
            // A 4xx refusal (an expired signed link, a forbidden
            // CDN) will not heal by retrying; 408, 429 and 5xx
            // may (0.0.99991).
            val retryable = e.code == 408 || e.code == 429 ||
                e.code >= 500
            HlsResult(
                if (retryable) HlsOutcome.RETRY
                else HlsOutcome.FAILED,
                error = "HTTP ${e.code}"
            )
        } catch (e: Exception) {
            // Network-class failure: the worker retries with backoff,
            // and the staged segments make the retry a resume.
            HlsResult(HlsOutcome.RETRY, error = errorLabel(e))
        }
    }

/**
     * Execute one DASH download job (0.0.9996), the HLS job's
     * sibling: same staging, publishing and status ownership.
     */
    fun runDashJob(
        context: Context,
        store: AppStore,
        rec: DownloadRecord,
        referer: String?,
        onProgress: (Int) -> Unit = {}
    ): HlsOutcome {
        val appCtx = context.applicationContext
        val useMediaStore = locationFor(store, rec) != "app" &&
            android.os.Build.VERSION.SDK_INT >= 29
        var fileName = rec.fileName
        if (!useMediaStore) {
            // Legacy path writes straight to the final file:
            // never overwrite an earlier download.
            val unique = dedupFileName(
                { destFile(appCtx, store, it, locationFor(store, rec)).exists() }, fileName
            )
            if (unique != fileName) {
                store.updateDownloadFileName(rec.id, unique)
                fileName = unique
            }
        }
        val joinDest = if (useMediaStore) {
            File(
                appCtx.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "notscape-staging/$fileName"
            )
        } else {
            destFile(appCtx, store, fileName, locationFor(store, rec))
        }
        val result = downloadDash(appCtx, store, rec.id, rec.url, referer, joinDest, onProgress)
        val outcome = result.outcome
        val wasCancelled = cancelledIds.remove(rec.id)
        if (outcome == HlsOutcome.OK && !wasCancelled && useMediaStore) {
            val uri = publishToMediaStore(appCtx, joinDest, fileName)
            if (uri != null) {
                store.updateDownloadUri(rec.id, uri.toString())
                joinDest.delete()
            } else {
                store.updateDownloadUri(rec.id, joinDest.absolutePath)
            }
        }
        return when {
            wasCancelled -> {
                writeTerminalIfNotPaused(store, rec.id, "cancelled", progress = -1)
                HlsOutcome.FAILED
            }
            outcome == HlsOutcome.OK -> {
                noteError(store, rec.id, null)
                store.updateDownload(rec.id, "complete", progress = 100)
                HlsOutcome.OK
            }
            outcome == HlsOutcome.RETRY -> {
                noteError(store, rec.id, result.error)
                HlsOutcome.RETRY
            }
            else -> {
                if (statusOf(store, rec.id) != "paused" && result.error != "Paused") noteError(store, rec.id, result.error)
                writeTerminalIfNotPaused(store, rec.id, "failed", progress = -1)
                outcome
            }
        }
    }

    /** Download one plain file with byte progress mapped
     * into a phase range; tmp/rename so a partial file
     * is never mistaken for the finished one. */
    private fun downloadPlain(
        url: String,
        referer: String?,
        out: File,
        recId: Long,
        fromPct: Int,
        toPct: Int,
        store: AppStore
    ) {
        if (cancelledIds.contains(recId)) throw InterruptedException("cancelled")
        val conn = openGet(url, referer)
        checkStatus(conn)
        val total = conn.contentLengthLong.takeIf { it > 0 }
        val tmp = File(out.parentFile, out.name + ".tmp")
        var read = 0L
        var lastPct = -1
        conn.inputStream.use { ins ->
            tmp.outputStream().use { outs ->
                val buf = ByteArray(65536)
                while (true) {
                    if (cancelledIds.contains(recId)) {
                        throw InterruptedException("cancelled")
                    }
                    val n = ins.read(buf)
                    if (n < 0) break
                    outs.write(buf, 0, n)
                    read += n
                    if (total != null) {
                        val pct = fromPct +
                            ((read * (toPct - fromPct)) / total).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            store.updateDownloadProgress(
                                recId, pct
                            )
                        }
                    }
                }
            }
        }
        if (out.exists()) out.delete()
        tmp.renameTo(out)
        store.updateDownloadProgress(recId, toPct)
    }

    /** Media merge: download the picked video file and
     * audio file, then remux them into one MP4. No
     * re-encode, so pairs whose codecs the MP4 muxer
     * cannot carry fail here honestly instead of
     * shipping a broken file. */
    private fun downloadMerge(
        context: Context,
        store: AppStore,
        recId: Long,
        videoUrl: String,
        audioUrl: String,
        referer: String?,
        dest: File,
        onProgress: (Int) -> Unit = {}
    ): HlsResult {
        var videoFile: File? = null
        var audioFile: File? = null
        var tmpOut: File? = null
        var transVideo: File? = null
        var transAudio: File? = null
        var muxed = false
        return try {
            videoFile = File(dest.parentFile, dest.name + ".mergeV")
            audioFile = File(dest.parentFile, dest.name + ".mergeA")
            downloadPlain(videoUrl, referer, videoFile!!, recId, 0, 45, store)
            downloadPlain(audioUrl, referer, audioFile!!, recId, 45, 90, store)
            if (cancelledIds.contains(recId)) {
                throw InterruptedException("cancelled")
            }
            dest.parentFile?.mkdirs()
            tmpOut = File(dest.parentFile, dest.name + ".mux")
            muxed = remuxPicked(videoFile!!, audioFile!!, tmpOut!!)
            if (muxed) {
                var writeDest = dest
                if (writeDest.exists() && writeDest.parentFile?.name != "notscape-staging") {
                    val unique = dedupFileName({ File(writeDest.parentFile, it).exists() }, writeDest.name)
                    store.updateDownloadFileName(recId, unique)
                    writeDest = File(writeDest.parentFile, unique)
                }
                tmpOut!!.renameTo(writeDest)
                store.updateDownloadProgress(recId, 100)
                onProgress(100)
                HlsResult(HlsOutcome.OK)
            } else {
                // Honest, specific failure: a missing track
                // and an incompatible codec are different
                // problems, and the user can only act on
                // a message that says which one it is.
                val err = when {
                    !hasTrack(videoFile!!, "video/") ->
                        "Merge failed: the video file has no video track"
                    !hasTrack(audioFile!!, "audio/") ->
                        "Merge failed: the audio file has no audio track"
                    else ->
                        "Merge failed: these files use codecs " +
                            "that cannot go into an MP4 file as-is " +
                            "(for example VP9 video or Opus audio)"
                }
                // Transcode fallback (Phase 3): when both tracks
                // exist but their codecs cannot go into MP4 as-is
                // (VP9 video, Opus audio), re-encode just the
                // incompatible sides to H.264/AAC with the in-app
                // Media3 Transformer, then remux again. Sides
                // already at the target codec are carried over
                // untouched — a fully compatible pair never
                // reaches here, because the fast remux above
                // succeeds for it.
                val videoMime = probeTrackMime(videoFile!!, "video/")
                val audioMime = probeTrackMime(audioFile!!, "audio/")
                val needsTranscode = videoMime != null &&
                    audioMime != null &&
                    (videoMime != MimeTypes.VIDEO_H264 ||
                        audioMime != MimeTypes.AUDIO_AAC)
                if (!needsTranscode) {
                    HlsResult(HlsOutcome.FAILED, error = err)
                } else {
                    onProgress(92)
                    transVideo = File(
                        dest.parentFile, dest.name + ".transV.mp4"
                    )
                    transAudio = File(
                        dest.parentFile, dest.name + ".transA.mp4"
                    )
                    val useVideo = if (videoMime != MimeTypes.VIDEO_H264) {
                        transcodeSide(
                            context, videoFile!!, transVideo!!,
                            true, recId
                        )
                        transVideo!!
                    } else videoFile!!
                    val useAudio = if (audioMime != MimeTypes.AUDIO_AAC) {
                        transcodeSide(
                            context, audioFile!!, transAudio!!,
                            false, recId
                        )
                        transAudio!!
                    } else audioFile!!
                    muxed = remuxPicked(useVideo, useAudio, tmpOut!!)
                    if (muxed) {
                        var writeDest = dest
                        if (writeDest.exists() && writeDest.parentFile?.name != "notscape-staging") {
                            val unique = dedupFileName({ File(writeDest.parentFile, it).exists() }, writeDest.name)
                            store.updateDownloadFileName(recId, unique)
                            writeDest = File(writeDest.parentFile, unique)
                        }
                        tmpOut!!.renameTo(writeDest)
                        store.updateDownloadProgress(
                            recId, 100
                        )
                        onProgress(100)
                        HlsResult(HlsOutcome.OK)
                    } else {
                        HlsResult(HlsOutcome.FAILED, error = err)
                    }
                }
            }
        } catch (e: InterruptedException) {
            HlsResult(HlsOutcome.FAILED, error = "Cancelled")
        } catch (e: HttpStatusException) {
            val retryable = e.code == 408 || e.code == 429 ||
                e.code >= 500
            HlsResult(
                if (retryable) HlsOutcome.RETRY else HlsOutcome.FAILED,
                error = "HTTP ${e.code}"
            )
        } catch (e: Exception) {
            // No retry here: a merge retry re-downloads
            // both sources from scratch (downloadPlain
            // has no Range resume), so only retryable
            // HTTP codes retry.
            HlsResult(HlsOutcome.FAILED, error = errorLabel(e))
        } finally {
            // Staging never survives a merge attempt,
            // success or not. On success tmpOut was
            // renamed to dest, so its delete is a
            // no-op - except when the rename itself
            // failed, in which case the muxed output
            // is kept for the staging sweep instead
            // of being thrown away.
            videoFile?.delete()
            audioFile?.delete()
            transVideo?.delete()
            transAudio?.delete()
            if (!muxed || dest.exists()) tmpOut?.delete()
        }
    }

    /** The MIME type of the first track starting with [prefix], or null. */
    private fun probeTrackMime(file: File, prefix: String): String? =
        runCatching {
            val ex = android.media.MediaExtractor()
            try {
                ex.setDataSource(file.absolutePath)
                (0 until ex.trackCount).asSequence().mapNotNull { i ->
                    ex.getTrackFormat(i)
                        .getString(android.media.MediaFormat.KEY_MIME)
                }.firstOrNull { it.startsWith(prefix) }
            } finally {
                ex.release()
            }
        }.getOrNull()

    /**
     * Re-encode one side to an MP4-compatible codec with the in-app
     * Media3 Transformer: video to H.264, audio to AAC. The other
     * side of the input is dropped (the remux takes each side from
     * its own file). Transformer must run on the main thread, so
     * the build + start are posted there and this worker thread
     * awaits the completion latch, polling for cancellation every
     * 500ms; a cancel posts transformer.cancel() and surfaces as
     * InterruptedException (the merge's "Cancelled" path).
     */
    private fun transcodeSide(
        context: Context,
        input: File,
        output: File,
        isVideo: Boolean,
        recId: Long
    ) {
        val latch = java.util.concurrent.CountDownLatch(1)
        val error =
            java.util.concurrent.atomic.AtomicReference<Exception?>(null)
        val transformerRef =
            java.util.concurrent.atomic.AtomicReference<Transformer?>(null)
        val mainHandler =
            android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            runCatching {
                val mediaItem = MediaItem.fromUri(Uri.fromFile(input))
                val edited = EditedMediaItem.Builder(mediaItem).apply {
                    if (isVideo) setRemoveAudio(true)
                    else setRemoveVideo(true)
                }.build()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            exportResult: ExportResult
                        ) {
                            latch.countDown()
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            error.set(exportException)
                            latch.countDown()
                        }
                    })
                    .build()
                transformerRef.set(transformer)
                if (output.exists()) output.delete()
                transformer.start(edited, output.absolutePath)
            }.onFailure {
                error.set(it as? Exception ?: Exception(it.message))
                latch.countDown()
            }
        }
        while (!latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            if (cancelledIds.contains(recId)) {
                mainHandler.post { transformerRef.get()?.cancel() }
                throw InterruptedException("cancelled")
            }
        }
        error.get()?.let { throw it }
    }

    /**
     * Execute one media-merge job (the WorkManager
     * worker's unit of work; also its retry unit).
     * Owns the record's status transitions except
     * RETRY, which leaves it "downloading" for the
     * next attempt. Mirrors runDashJob.
     */
    fun runMergeJob(
        context: Context,
        store: AppStore,
        rec: DownloadRecord,
        videoUrl: String,
        audioUrl: String,
        referer: String?,
        onProgress: (Int) -> Unit = {}
    ): HlsOutcome {
        val appCtx = context.applicationContext
        val useMediaStore = locationFor(store, rec) != "app" &&
            android.os.Build.VERSION.SDK_INT >= 29
        var fileName = rec.fileName
        if (!useMediaStore) {
            // Legacy path writes straight to the final file:
            // never overwrite an earlier download.
            val unique = dedupFileName(
                { destFile(appCtx, store, it, locationFor(store, rec)).exists() }, fileName
            )
            if (unique != fileName) {
                store.updateDownloadFileName(rec.id, unique)
                fileName = unique
            }
        }
        val joinDest = if (useMediaStore) {
            File(
                appCtx.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "notscape-staging/$fileName"
            )
        } else {
            destFile(appCtx, store, fileName, locationFor(store, rec))
        }
        val result = downloadMerge(
            appCtx, store, rec.id, videoUrl, audioUrl, referer,
            joinDest, onProgress
        )
        val outcome = result.outcome
        val wasCancelled = cancelledIds.remove(rec.id)
        if (outcome == HlsOutcome.OK && !wasCancelled && useMediaStore) {
            val uri = publishToMediaStore(appCtx, joinDest, fileName)
            if (uri != null) {
                store.updateDownloadUri(rec.id, uri.toString())
                joinDest.delete()
            } else {
                store.updateDownloadUri(rec.id, joinDest.absolutePath)
            }
        }
        return when {
            wasCancelled -> {
                writeTerminalIfNotPaused(store, rec.id, "cancelled", progress = -1)
                HlsOutcome.FAILED
            }
            outcome == HlsOutcome.OK -> {
                noteError(store, rec.id, null)
                store.updateDownload(rec.id, "complete", progress = 100)
                HlsOutcome.OK
            }
            outcome == HlsOutcome.RETRY -> {
                noteError(store, rec.id, result.error)
                HlsOutcome.RETRY
            }
            else -> {
                if (statusOf(store, rec.id) != "paused" && result.error != "Paused") noteError(store, rec.id, result.error)
                writeTerminalIfNotPaused(store, rec.id, "failed", progress = -1)
                outcome
            }
        }
    }

    private fun downloadDash(
        context: Context,
        store: AppStore,
        recId: Long,
        mpdUrl: String,
        referer: String?,
        dest: File,
        onProgress: (Int) -> Unit = {}
    ): HlsResult {
        return try {
            val text = fetchText(mpdUrl, referer)
            val plan = DashManifest.parseDownloadPlan(text, mpdUrl)
                ?: return HlsResult(
                    HlsOutcome.UNSUPPORTED,
                    error = "Unsupported DASH manifest"
                )
            val tracks = listOfNotNull(plan.video, plan.audio)
            val totalSegs = tracks.sumOf { it.segmentUrls.size }
            if (totalSegs == 0) {
                return HlsResult(
                    HlsOutcome.FAILED,
                    error = "Manifest has no segments"
                )
            }
            var doneSegs = 0
            var lastPct = -1
            fun noteProgress() {
                doneSegs++
                val pct = doneSegs * 100 / totalSegs
                if ((pct % 5 == 0 || doneSegs == totalSegs) &&
                    pct != lastPct
                ) {
                    lastPct = pct
                    store.updateDownloadProgress(
                        recId, pct
                    )
                    onProgress(pct)
                }
            }
            val trackFiles = mutableListOf<File>()
            val segDirs = mutableListOf<File>()
            tracks.forEachIndexed { ti, track ->
                val segDir = File(
                    dest.parentFile, dest.name + ".dash$ti"
                )
                segDir.mkdirs()
                segDirs.add(segDir)
                track.segmentUrls.forEachIndexed { idx, segUrl ->
                    if (cancelledIds.contains(recId)) {
                        throw InterruptedException("cancelled")
                    }
                    if (pausedIds.contains(recId)) {
                        throw InterruptedException("paused")
                    }
                    val segFile = File(segDir, "seg-%05d.bin".format(idx))
                    if (!segFile.exists()) {
                        val tmpSeg = File(
                            segDir, "seg-%05d.tmp".format(idx)
                        )
                        val segConn = openGet(segUrl, referer)
                        checkStatus(segConn)
                        segConn.inputStream.use { ins ->
                            tmpSeg.outputStream().use { ins.copyTo(it) }
                        }
                        tmpSeg.renameTo(segFile)
                    }
                    noteProgress()
                }
                // Assemble the track: init segment, then the
                // media segments in order - a valid fragmented
                // MP4 per track.
                val trackFile = File(
                    dest.parentFile, dest.name + ".track$ti"
                )
                trackFile.outputStream().use { out ->
                    track.initUrl?.let { initUrl ->
                        val initConn = openGet(initUrl, referer)
                        checkStatus(initConn)
                        initConn.inputStream.use {
                            it.copyTo(out)
                        }
                    }
                    track.segmentUrls.indices.forEach { idx ->
                        File(segDir, "seg-%05d.bin".format(idx))
                            .inputStream().use { it.copyTo(out) }
                    }
                }
                trackFiles.add(trackFile)
            }
            if (cancelledIds.contains(recId)) {
                trackFiles.forEach { it.delete() }
                return HlsResult(HlsOutcome.FAILED, error = "Cancelled")
            }
            if (pausedIds.contains(recId)) {
                // Assembled tracks are partial and discarded; the
                // staged segments stay, so resume re-assembles.
                trackFiles.forEach { it.delete() }
                return HlsResult(HlsOutcome.FAILED, error = "Paused")
            }
            dest.parentFile?.mkdirs()
            val ok = if (plan.audio == null) {
                var writeDest = dest
                if (writeDest.exists() && writeDest.parentFile?.name != "notscape-staging") {
                    val unique = dedupFileName({ File(writeDest.parentFile, it).exists() }, writeDest.name)
                    store.updateDownloadFileName(recId, unique)
                    writeDest = File(writeDest.parentFile, unique)
                }
                trackFiles[0].renameTo(writeDest)
            } else {
                val tmpOut = File(dest.parentFile, dest.name + ".mux")
                val muxed = remuxMp4(
                    trackFiles[0], trackFiles[1], tmpOut
                )
                trackFiles.forEach { it.delete() }
                if (muxed) {
                    var writeDest = dest
                    if (writeDest.exists() && writeDest.parentFile?.name != "notscape-staging") {
                        val unique = dedupFileName({ File(writeDest.parentFile, it).exists() }, writeDest.name)
                        store.updateDownloadFileName(recId, unique)
                        writeDest = File(writeDest.parentFile, unique)
                    }
                    tmpOut.renameTo(writeDest)
                    true
                } else {
                    tmpOut.delete()
                    false
                }
            }
            if (!ok) {
                return HlsResult(
                    HlsOutcome.FAILED, error = "Remux failed"
                )
            }
            segDirs.forEach { it.deleteRecursively() }
            HlsResult(HlsOutcome.OK)
        } catch (e: InterruptedException) {
            HlsResult(HlsOutcome.FAILED, error = "Cancelled")
        } catch (e: HttpStatusException) {
            val retryable = e.code == 408 || e.code == 429 ||
                e.code >= 500
            HlsResult(
                if (retryable) HlsOutcome.RETRY
                else HlsOutcome.FAILED,
                error = "HTTP ${e.code}"
            )
        } catch (e: Exception) {
            HlsResult(HlsOutcome.RETRY, error = errorLabel(e))
        }
    }

    /** Remux a video fMP4 and an audio fMP4 into one standard
     * MP4 with the platform muxer. False on any trouble - the
     * download then fails honestly rather than shipping a file
     * that only pretends to be complete. */
    private fun remuxMp4(
        videoFile: File,
        audioFile: File,
        outFile: File,
        // Which tracks to take from each input. The
        // default takes every video/audio track (the
        // DASH shape: one track per file). The merge
        // passes a selective pick instead.
        pick: (inputIndex: Int, mime: String) -> Boolean =
            { _, _ -> true },
        // When true, fail unless at least one video
        // track and one audio track were picked.
        requireBoth: Boolean = false
    ): Boolean = runCatching {
        val muxer = android.media.MediaMuxer(
            outFile.absolutePath,
            android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
        val extractors = mutableListOf<android.media.MediaExtractor>()
        try {
            val trackMap = mutableMapOf<Pair<Int, Int>, Int>()
            var sawVideo = false
            var sawAudio = false
            listOf(videoFile, audioFile).forEachIndexed { ei, f ->
                val ex = android.media.MediaExtractor()
                ex.setDataSource(f.absolutePath)
                extractors.add(ex)
                for (ti in 0 until ex.trackCount) {
                    val fmt = ex.getTrackFormat(ti)
                    val mime = fmt.getString(
                        android.media.MediaFormat.KEY_MIME
                    ) ?: ""
                    if (pick(ei, mime)) {
                        trackMap[ei to ti] = muxer.addTrack(fmt)
                        ex.selectTrack(ti)
                        if (mime.startsWith("video/")) sawVideo = true
                        if (mime.startsWith("audio/")) sawAudio = true
                    }
                }
            }
            // A selective merge with a missing side is
            // a failed merge, not a silent half-file:
            // the caller deletes the output on false.
            if (requireBoth && (!sawVideo || !sawAudio)) return false
            muxer.start()
            val info = android.media.MediaCodec.BufferInfo()
            val buffer = java.nio.ByteBuffer.allocate(2_097_152)
            extractors.forEachIndexed { ei, ex ->
                while (true) {
                    val size = ex.readSampleData(buffer, 0)
                    if (size < 0) break
                    val outTrack = trackMap[ei to ex.sampleTrackIndex]
                    if (outTrack != null) {
                        info.offset = 0
                        info.size = size
                        info.presentationTimeUs = ex.sampleTime
                        info.flags =
                            if ((ex.sampleFlags and
                                    android.media.MediaExtractor
                                        .SAMPLE_FLAG_SYNC) != 0
                            ) {
                                android.media.MediaCodec
                                    .BUFFER_FLAG_KEY_FRAME
                            } else {
                                0
                            }
                        muxer.writeSampleData(outTrack, buffer, info)
                    }
                    ex.advance()
                }
            }
            muxer.stop()
        } finally {
            extractors.forEach { runCatching { it.release() } }
            runCatching { muxer.release() }
        }
        outFile.length() > 0
    }.getOrDefault(false)

    /** Remux a user-picked video file + audio file into
     * one standard MP4 (media merge): the video track
     * comes ONLY from the video pick and the audio track
     * ONLY from the audio pick, so a "black screen with
     * sound" file picked as audio cannot contribute its
     * black video track. False when either side lacks
     * its track. */
    /** True when [file] carries a track whose MIME type starts with [prefix]. */
    private fun hasTrack(file: File, prefix: String): Boolean =
        runCatching {
            val ex = android.media.MediaExtractor()
            try {
                ex.setDataSource(file.absolutePath)
                (0 until ex.trackCount).any { i ->
                    ex.getTrackFormat(i)
                        .getString(android.media.MediaFormat.KEY_MIME)
                        ?.startsWith(prefix) == true
                }
            } finally {
                ex.release()
            }
        }.getOrDefault(false)

    private fun remuxPicked(
        videoFile: File,
        audioFile: File,
        outFile: File
    ): Boolean = remuxMp4(
        videoFile, audioFile, outFile,
        pick = { ei, mime ->
            (ei == 0 && mime.startsWith("video/")) ||
                (ei == 1 && mime.startsWith("audio/"))
        },
        requireBoth = true
    )

    /**
     * Startup sweep of the HLS staging area: partial joins older
     * than a week go, and segment folders go when their download
     * is no longer in flight and they are a week old. Anything
     * younger may still be a resume waiting to happen.
     */
    fun sweepStaging(context: Context, store: AppStore) {
        runCatching {
            val staging = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "notscape-staging"
            )
            if (!staging.exists()) return
            val inFlight = store.downloadsSnapshot()
                .filter { it.status == "downloading" || it.status == "queued" || it.status == "paused" }
                .map { it.fileName }
                .toSet()
            val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
            staging.listFiles()?.forEach { f ->
                when {
                    f.isFile && f.name.endsWith(".part") && f.lastModified() < weekAgo ->
                        f.delete()
                    f.isDirectory && f.name.endsWith(".segments") -> {
                        val base = f.name.removeSuffix(".segments")
                        if (base !in inFlight && f.lastModified() < weekAgo) {
                            f.deleteRecursively()
                        }
                    }
                    // Merge staging from failed or
                    // cancelled merges: same week-old
                    // rule, same in-flight guard.
                    f.isFile && f.name.contains(".merge") &&
                        f.lastModified() < weekAgo &&
                        f.name.substringBefore(".merge") !in inFlight ->
                        f.delete()
                    f.isFile && f.name.endsWith(".mux") &&
                        f.lastModified() < weekAgo &&
                        f.name.removeSuffix(".mux") !in inFlight ->
                        f.delete()
                }
            }
        }
    }

    /**
     * Collision-proof file name: when [name] already exists
     * (checked via [exists]), "name (1).ext", "name (2).ext",
     * and so on, keeping the extension. Pure, so the unit
     * suite pins it without touching the filesystem.
     */
    internal fun dedupFileName(exists: (String) -> Boolean, name: String): String {
        if (!exists(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!exists(candidate)) return candidate
            i++
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
    /**
     * EPUB auto-shelf (Phase E): when a download finishes and the
     * file is an EPUB, copy it into the books folder and add it to
     * the bookshelf (Settings toggle, default on). The download
     * itself is never touched; a book that can't be parsed is
     * reported and left as a plain download. Runs on a worker
     * thread — parsing a book is not main-thread work.
     */
    fun maybeAutoShelf(context: Context, store: AppStore, rec: DownloadRecord) {
        // A shelved book would survive the private-profile wipe,
        // so private downloads are never auto-shelved.
        if (rec.isPrivate) return
        if (store.setting("epubAutoShelf", "true") != "true") return
        val byName = rec.fileName.lowercase().endsWith(".epub") ||
            rec.url.lowercase().substringBefore('?').substringBefore('#')
                .endsWith(".epub")
        val destName = "autoshelf-${rec.id}.epub"
        if (store.books.any { it.filePath.endsWith(destName) }) return
        ioScope.launch {
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            runCatching {
                val fresh = store.downloadsSnapshot().firstOrNull { it.id == rec.id } ?: rec
                val src = localSource(context, store, fresh)
                    ?: error("download not readable")
                if (!byName && !sniffsAsEpub(context, src)) return@launch
                val dir = File(context.filesDir, "books").apply { mkdirs() }
                val dest = File(dir, destName)
                if (src.startsWith("content://")) {
                    context.contentResolver.openInputStream(Uri.parse(src))?.use { input ->
                        dest.outputStream().use { out -> input.copyTo(out) }
                    } ?: error("download not readable")
                } else {
                    File(src).copyTo(dest, overwrite = true)
                }
                val parsed = net.notscape.browser.reader.BookParser.parse(dest)
                val bookId = System.currentTimeMillis() * 1000 + (0..999).random()
                val coverPath = parsed.cover?.let { bytes ->
                    runCatching {
                        val cf = File(dir, "$bookId-cover.img")
                        cf.writeBytes(bytes)
                        cf.absolutePath
                    }.getOrNull()
                } ?: ""
                store.addBook(
                    net.notscape.browser.data.BookRecord(
                        id = bookId,
                        title = parsed.title.ifBlank { rec.title },
                        author = parsed.author,
                        filePath = dest.absolutePath,
                        format = "EPUB",
                        chapterIndex = 0,
                        scrollFraction = 0f,
                        addedAt = System.currentTimeMillis(),
                        coverPath = coverPath
                    )
                )
                main.post {
                    Toast.makeText(
                        context, "Added \"${parsed.title}\" to your bookshelf",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }.onFailure {
                main.post {
                    Toast.makeText(
                        context,
                        "That EPUB couldn't be added to the bookshelf — the download is untouched",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * A real EPUB is a zip carrying META-INF/container.xml (and
     * usually a "mimetype" entry). Used when a finished download's
     * name and URL give nothing away — some sites serve books
     * under opaque names, and the shelf should still get them.
     */
    private fun sniffsAsEpub(context: Context, src: String): Boolean = runCatching {
        val stream = if (src.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(src))
        } else {
            File(src).inputStream()
        }
        stream?.use { ins ->
            java.util.zip.ZipInputStream(ins).use { z ->
                var e = z.nextEntry
                var found = false
                while (e != null && !found) {
                    if (e.name == "META-INF/container.xml" || e.name == "mimetype") {
                        found = true
                    }
                    e = z.nextEntry
                }
                found
            }
        } ?: false
    }.getOrDefault(false)

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
                    findInMediaStore(context, rec)?.let {
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
                    if (newStatus == "complete" && rec.status != "complete") {
                        maybeAutoShelf(context, store, rec)
                    }
                    if (newStatus == "complete" && rec.contentUri.isBlank()) {
                        // The authoritative location: what
                        // DownloadManager itself recorded for THIS
                        // download id — no name matching involved.
                        val localIdx = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        val localUri = if (localIdx >= 0) it.getString(localIdx) else null
                        if (!localUri.isNullOrBlank() && localUri.startsWith("content://")) {
                            store.updateDownloadUri(rec.id, localUri)
                            changed = true
                        }
                    }
                }
            }
        }
        promoteQueued(context, store)
        return changed
    }

    fun localFile(context: Context, store: AppStore, rec: DownloadRecord): File =
        if (locationFor(store, rec) == "app") {
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

    /** A share intent for a completed download (0.0.9999):
     *  the system sheet takes it from there. */
    fun shareIntent(
        context: Context, store: AppStore, rec: DownloadRecord
    ): Intent? {
        val src = localSource(context, store, rec) ?: return null
        val uri = if (src.startsWith("content://")) {
            Uri.parse(src)
        } else {
            runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(src)
                )
            }.getOrNull() ?: return null
        }
        val type = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(
                rec.fileName.substringAfterLast('.', "").lowercase()
            ) ?: "application/octet-stream"
        return Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, rec.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Open a finished download with another app (the
     *  Downloads page's Open action): the same URI and MIME
     *  resolution as shareIntent, but ACTION_VIEW. */
    fun openIntent(
        context: Context, store: AppStore, rec: DownloadRecord
    ): Intent? {
        val src = localSource(context, store, rec) ?: return null
        val uri = if (src.startsWith("content://")) {
            Uri.parse(src)
        } else {
            runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(src)
                )
            }.getOrNull() ?: return null
        }
        val type = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(
                rec.fileName.substringAfterLast('.', "").lowercase()
            ) ?: "application/octet-stream"
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Copy a completed download to a destination the user
     *  picked (0.0.9999). Call off the main thread. */
    fun copyExport(
        context: Context, store: AppStore,
        rec: DownloadRecord, dest: Uri
    ): Boolean {
        val src = localSource(context, store, rec) ?: return false
        return runCatching {
            val input = if (src.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(src))
            } else {
                java.io.FileInputStream(File(src))
            } ?: return false
            input.use { ins ->
                context.contentResolver.openOutputStream(dest)
                    ?.use { out -> ins.copyTo(out) } ?: return false
            }
            true
        }.getOrDefault(false)
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

    /**
     * Wipe every private download: cancel in-flight work, delete
     * the finished file wherever it lives, drop rec-id-keyed
     * staging files, and remove the record — so nothing from a
     * private session survives in the Downloads or Offline
     * lists or on disk. Name-keyed leftovers (HLS segment
     * dirs, merge temps) are removed only when no surviving
     * record shares the file name, so another download's
     * in-flight files are never touched.
     */
    fun wipePrivateDownloads(context: Context, store: AppStore) {
        val appCtx = context.applicationContext
        val snapshot = store.downloadsSnapshot()
        val privates = snapshot.filter { it.isPrivate }
        if (privates.isEmpty()) return
        val survivingNames = snapshot.filter { !it.isPrivate }.map { it.fileName }.toSet()
        val stagingDir = File(
            appCtx.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "notscape-staging"
        )
        privates.forEach { rec ->
            val legacyDir = if (locationFor(store, rec) == "app") {
                File(appCtx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "notscape")
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
            runCatching { cancel(appCtx, store, rec) }
            runCatching { deleteLocal(appCtx, store, rec) }
            runCatching {
                stagingDir.listFiles()?.forEach { f ->
                    if (f.name.contains(".${rec.id}.")) f.delete()
                }
            }
            if (rec.fileName !in survivingNames) {
                runCatching {
                    File(stagingDir, rec.fileName + ".segments").deleteRecursively()
                    File(legacyDir, rec.fileName + ".segments").deleteRecursively()
                    listOf(".mergeV", ".mergeA", ".mux").forEach { suffix ->
                        File(stagingDir, rec.fileName + suffix).delete()
                        File(legacyDir, rec.fileName + suffix).delete()
                    }
                }
            }
            runCatching { clearJobBundle(store, rec.id) }
            store.removeDownload(rec.id)
        }
    }

    /** Startup path: wait for the store to load, then wipe. */
    fun wipePrivateDownloadsAfterLoad(context: Context, store: AppStore) {
        val appCtx = context.applicationContext
        ioScope.launch {
            store.awaitReady()
            wipePrivateDownloads(appCtx, store)
        }
    }
}
