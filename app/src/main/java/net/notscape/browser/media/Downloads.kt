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
        // Audit F-m10: the old id % 100000 slot collided for
        // records 100000 apart (5 vs 100005 shared a notification).
        // The id is now folded to 30 bits and bit 30 separates
        // progress from terminal notices, so every record gets a
        // distinct pair. In-flight downloads across the update may
        // leave one orphaned old-scheme notification.
        val hash = (recordId xor (recordId ushr 32)).toInt() and 0x3FFFFFFF
        return if (done) hash or 0x40000000 else hash
    }

    /** Total size from a 206 Content-Range header
     *  ("bytes 0-99/1234"), or null when the header is absent,
     *  unknown ("*"), or malformed. Audit F-m11: the segmented
     *  completion gate compares this per-chunk total against the
     *  probe total. */
    internal fun contentRangeTotal(header: String?): Long? {
        if (header == null) return null
        return header.substringAfterLast('/', "").trim().toLongOrNull()
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
    // Beta.58 (2): the bundle also carries a cookie snapshot
    // ("cookie" + "cookieHost") — the WebView cookies for the
    // media URL's host, captured when the user taps Download.
    // A session can end between the tap and the job running
    // (WorkManager may delay it); the snapshot lets HLS/DASH
    // replay the cookies that were live at tap time instead of
    // re-reading an empty jar. The referer is already stored;
    // the UA is a constant. The snapshot is host-scoped: it is
    // sent only to its own host, never to any other host.

    /** Beta.58 (2): WebView cookies for one host, captured at
     *  download-tap time. Host-scoped by construction. */
    internal data class CookieSnapshot(val host: String, val cookie: String)

    /** Beta.58 (2): which Cookie value a request to [url] gets.
     *  The snapshot wins, but ONLY for its own host; anything
     *  else falls back to the live jar (which may itself be
     *  empty). Pure, so the unit suite pins it without a
     *  WebView: CookieManager is an Android stub under tests. */
    internal fun cookieForRequest(
        url: String,
        snapshot: CookieSnapshot?,
        liveCookie: String?
    ): String? {
        if (snapshot != null && snapshot.cookie.isNotBlank()) {
            val host = runCatching { java.net.URL(url).host }.getOrNull()
            if (host != null && host == snapshot.host) return snapshot.cookie
        }
        return liveCookie?.takeIf { it.isNotBlank() }
    }

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

    /** A failure that must not be retried (bad key, bad IV):
     *  retrying it can never heal, so the job fails fast. */
    class FatalDownloadException(message: String) : java.io.IOException(message)

    /** Rename, falling back to copy-then-delete (audit D-M4):
     *  renameTo is not guaranteed across directories or
     *  filesystems, and a failed rename must never be
     *  reported as a completed download. */
    internal fun renameOrCopy(src: File, dst: File): Boolean {
        if (src.renameTo(dst)) return true
        return runCatching {
            src.inputStream().use { ins ->
                dst.outputStream().use { out -> ins.copyTo(out) }
            }
            src.delete()
            true
        }.getOrDefault(false)
    }

    /** Record ids, looped until unused (audit D-m9): the
     *  millisecond-plus-random shape is kept — only the
     *  collision hole is closed. */
    internal fun nextDownloadId(store: AppStore): Long {
        var id = System.currentTimeMillis() * 1000 + (0..999).random()
        while (store.downloadsSnapshot().any { it.id == id }) {
            id = System.currentTimeMillis() * 1000 + (0..999).random()
        }
        return id
    }

    /** Every directory engine staging can live in: the staging
     *  folder, the app folder (app-location and legacy
     *  destinations stage beside the finished file), and the
     *  public Downloads folder (legacy path). */
    internal fun stagingDirs(context: Context): List<File> = listOf(
        File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "notscape-staging"),
        File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "notscape"),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    )

    /** Delete one record's engine staging everywhere it can
     *  live (audits D-B1, D-m1, D-M9): id-keyed staging is
     *  matched by the record id whatever its file name is
     *  now (renames don't orphan it), and legacy name-keyed
     *  staging is removed only when no other record shares
     *  the file name. One helper for cancel, delete, wipe,
     *  and erase, so the patterns can never drift apart. */
    internal fun deleteEngineStaging(context: Context, store: AppStore, rec: DownloadRecord) {
        val othersShareName = store.downloadsSnapshot()
            .any { it.id != rec.id && it.fileName == rec.fileName }
        val idMark = ".${rec.id}."
        for (dir in stagingDirs(context)) {
            runCatching {
                dir.listFiles()?.forEach { f ->
                    val n = f.name
                    val idKeyed = n.contains(idMark)
                    val legacy = !othersShareName && (
                        n == rec.fileName + ".segments" ||
                            n == rec.fileName + ".dash0" || n == rec.fileName + ".dash1" ||
                            n == rec.fileName + ".track0" || n == rec.fileName + ".track1" ||
                            n == rec.fileName + ".mux" || n == rec.fileName + ".part" ||
                            n == rec.fileName + ".transV.mp4" || n == rec.fileName + ".transA.mp4" ||
                            n.startsWith(rec.fileName + ".merge")
                        )
                    if (idKeyed || legacy) {
                        if (f.isDirectory) f.deleteRecursively() else f.delete()
                    }
                }
            }
        }
    }

    /** Sleep in slices (audit D-m14): throws on cancel, returns
     *  true early on pause, so long rate-limit waits answer
     *  promptly instead of sleeping through the user. */
    internal fun sleepChecked(recId: Long, millis: Long): Boolean {
        var left = millis
        while (left > 0) {
            if (cancelledIds.contains(recId)) throw InterruptedException("cancelled")
            if (pausedIds.contains(recId)) return true
            val step = minOf(250L, left)
            Thread.sleep(step)
            left -= step
        }
        return pausedIds.contains(recId)
    }

    /** True when the active network is metered (or unknown
     *  capabilities hide it — no network means not metered,
     *  since the worker constraints hold the job anyway). */
    internal fun isNetworkMetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
            ?: return false
        return !caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Audit D-m10: a downloading/queued record under
     *  Wi-Fi-only on a metered network is waiting, not
     *  stalled — the row says so. */
    internal fun isWaitingForWifi(context: Context, store: AppStore): Boolean =
        store.setting("wifiOnlyDownloads", "false") == "true" && isNetworkMetered(context)

    /** Retry through the saved job bundle (audit D-M1): merges
     *  re-run with both side URLs, location and the current
     *  file name are preserved, and the record keeps its id
     *  so staging and journal resume instead of restarting.
     *  Only a missing bundle falls back to delete-and-restart. */
    fun retry(context: Context, store: AppStore, rec: DownloadRecord): Boolean {
        val appCtx = context.applicationContext
        val fresh = store.downloadsSnapshot().firstOrNull { it.id == rec.id } ?: return false
        if (fresh.status != "failed" && fresh.status != "cancelled") return false
        val bundle = readJobBundle(store, rec.id)
        if (bundle == null) {
            if (fresh.dmId >= 0) {
                runCatching {
                    val dm = appCtx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    dm.remove(fresh.dmId)
                }
            }
            val wiped = runCatching { deleteLocal(appCtx, store, fresh) }.getOrDefault(false)
            if (!wiped) return false
            runCatching { deleteEngineStaging(appCtx, store, fresh) }
            clearJobBundle(store, rec.id)
            store.removeDownload(rec.id)
            store.removeSetting("dlerror:${rec.id}")
            store.removeSetting("dldowngrade:${rec.id}")
            start(appCtx, store, fresh.title, fresh.url, media = fresh.media, isPrivate = fresh.isPrivate)
            return true
        }
        if (fresh.dmId >= 0) {
            runCatching {
                val dm = appCtx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.remove(fresh.dmId)
            }
        }
        cancelledIds.remove(rec.id)
        pausedIds.remove(rec.id)
        store.removeSetting("dlerror:${rec.id}")
        store.removeSetting("dldowngrade:${rec.id}")
        store.updateDownload(rec.id, "queued", progress = 0)
        promoteQueued(appCtx, store)
        return true
    }

    /** "Clear completed, keep files" (audit P-p4): records
     *  only — files on disk are never touched. */
    fun clearCompletedRecords(store: AppStore, media: Boolean) {
        for (rec in store.downloadsSnapshot()) {
            if (rec.status == "complete" && rec.media == media) {
                store.removeDownload(rec.id)
                store.removeSetting("dldowngrade:${rec.id}")
            }
        }
    }

    /** File names pruned records left behind (audit D-m12
     *  tombstones): the file stays, and its name is kept so
     *  it can still be removed deliberately. */
    fun prunedFiles(store: AppStore): List<String> =
        store.settings.keys.filter { it.startsWith("dltombstone:") }
            .mapNotNull { store.setting(it, "").ifBlank { null } }

    /** Remove tombstoned leftover files, wherever they live. */
    fun clearPrunedFiles(context: Context, store: AppStore) {
        val names = prunedFiles(store)
        if (names.isEmpty()) return
        for (dir in stagingDirs(context)) {
            dir.listFiles()?.forEach { f ->
                if (f.name in names) {
                    if (f.isDirectory) f.deleteRecursively() else f.delete()
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            runCatching {
                val resolver = context.contentResolver
                val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                for (name in names) {
                    resolver.query(
                        collection,
                        arrayOf(android.provider.MediaStore.MediaColumns._ID),
                        "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                        arrayOf(name), null
                    )?.use { c ->
                        while (c.moveToNext()) {
                            val uri = android.content.ContentUris.withAppendedId(
                                collection, c.getLong(0)
                            )
                            runCatching { resolver.delete(uri, null, null) }
                        }
                    }
                }
            }
        }
        store.settings.keys.filter { it.startsWith("dltombstone:") }
            .forEach { store.removeSetting(it) }
    }

    /** Erase-everything download half (audit D-m11): stop
     *  in-flight work, drop system entries and staging, and
     *  clear job bundles and error keys — called before the
     *  store list itself is erased. Finished files stay,
     *  as the dialog promises. */
    fun eraseAllDownloads(context: Context, store: AppStore) {
        val appCtx = context.applicationContext
        for (rec in store.downloadsSnapshot()) {
            runCatching { cancel(appCtx, store, rec) }
            runCatching { deleteEngineStaging(appCtx, store, rec) }
            clearJobBundle(store, rec.id)
            store.removeSetting("dlerror:${rec.id}")
            store.removeSetting("dldowngrade:${rec.id}")
        }
        store.settings.keys.filter {
            it.startsWith("dljob:") || it.startsWith("dlerror:") ||
                it.startsWith("dldowngrade:") || it.startsWith("dltombstone:")
        }.forEach { store.removeSetting(it) }
    }

    /** The engine a record is running (or ran) under, "" if never routed. */
    internal fun engineFor(store: AppStore, rec: DownloadRecord): String =
        readJobBundle(store, rec.id)?.get("engine").orEmpty()

    /** Engines whose jobs can genuinely resume from staging/journal. */
    internal fun isResumableEngine(engine: String): Boolean =
        engine == "hls" || engine == "dash" || engine == "seg"

    /** Per-download destination override, else the global setting. */
    internal fun locationFor(store: AppStore, rec: DownloadRecord): String {
        // Audit D-M2: a private download never leaves the app
        // folder, whatever the dialog or the global setting said.
        if (rec.isPrivate) return "app"
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
                // Audit D-p2: a name of only an extension
                // (".mp4") has no stem — fall back to the
                // resolved name instead of degrading to the
                // bare extension.
                if (trimmed.startsWith(".") && trimmed.substringBeforeLast('.').isEmpty()) {
                    return resolved
                }
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
            // Audit D-m8: the queue lock guards only the pick
            // and the slot claim — never the network probe
            // inside routeNewDownload, which used to stall
            // every other promotion behind one slow server.
            while (true) {
                val next = synchronized(queueLock) {
                    if (activeDownloadCount(store) >= MAX_CONCURRENT_DOWNLOADS) null
                    else store.downloadsSnapshot()
                        .firstOrNull { it.status == "queued" }
                        ?.also { store.updateDownload(it.id, "downloading") }
                } ?: break
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
                // A route that left the record queued (the
                // Wi-Fi-only deferral) must not spin: the
                // next promotion trigger retries it.
                val after = store.downloadsSnapshot()
                    .firstOrNull { it.id == next.id }?.status
                if (after == "queued") break
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
                            .setRequiredNetworkType(downloadNetworkType(store))
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
                            .setRequiredNetworkType(downloadNetworkType(store))
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
                            .setRequiredNetworkType(downloadNetworkType(store))
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
                            .setRequiredNetworkType(downloadNetworkType(store))
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
        // Audit D-m1: cancel drops the record's HLS/DASH/
        // merge staging too, not just the segmented kind.
        runCatching { deleteEngineStaging(context, store, rec) }
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
        // Audit D-m10: with Wi-Fi-only on and a metered
        // network active, don't even probe — leave the
        // record queued (the row shows "Waiting for
        // Wi-Fi") until a later promotion runs unmetered.
        if (store.setting("wifiOnlyDownloads", "false") == "true" &&
            isNetworkMetered(appCtx)
        ) {
            store.updateDownload(rec.id, "queued")
            return
        }
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
                        .setRequiredNetworkType(downloadNetworkType(store))
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
                        .setRequiredNetworkType(downloadNetworkType(store))
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
                            .setRequiredNetworkType(downloadNetworkType(store))
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
        fileNameOverride: String? = null,
        variantBandwidth: Long? = null
    ): Long {
        val id = nextDownloadId(store)
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
            // Beta.58 (2): snapshot the WebView cookies for the
            // media URL's host now, while the session is live —
            // the job may run later, after the session ends.
            // Host-scoped (see CookieSnapshot): the snapshot is
            // replayed only to its own host, never elsewhere.
            val snapCookie = runCatching {
                android.webkit.CookieManager.getInstance().getCookie(url)
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: ""
            val snapHost = runCatching {
                android.net.Uri.parse(url).host
            }.getOrNull() ?: ""
            saveJobBundle(store, rec.id, mapOf(
                "url" to url,
                "title" to title,
                "media" to media.toString(),
                "contentType" to (contentType ?: ""),
                "suggestedName" to (suggestedName ?: ""),
                "referer" to (referer ?: ""),
                "cookie" to snapCookie,
                "cookieHost" to snapHost,
                "isPrivate" to isPrivate.toString(),
                "location" to (if (isPrivate) "app" else (location ?: "")),
                "fileName" to fileName,
                "variantBandwidth" to (variantBandwidth?.toString() ?: "")
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
    /** Audit P-m11: Wi-Fi-only downloads, off by default.
     *  Workers ask for an unmetered network; DownloadManager
     *  is barred from metered networks. Applies to downloads
     *  started after the toggle changes - already-enqueued
     *  workers keep the constraints they were built with. */
    private fun downloadNetworkType(store: AppStore) =
        if (store.setting("wifiOnlyDownloads", "false") == "true")
            androidx.work.NetworkType.UNMETERED
        else androidx.work.NetworkType.CONNECTED

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
        // Audit F-m3: the public Downloads path is blind to a raw
        // File check under scoped storage — ask the MediaStore.
        val finalName = if (locationFor(store, rec) == "app") {
            dedupFileName({ File(destDir, it).exists() }, rec.fileName)
        } else {
            uniqueMediaStoreName(context, rec.fileName)
        }
        if (finalName != rec.fileName) {
            store.updateDownloadFileName(rec.id, finalName)
        }
        val req = DownloadManager.Request(Uri.parse(url)).apply {
            // Audit D-m17: a private download's title and URL
            // must not surface in the system Downloads UI,
            // which VISIBILITY_HIDDEN does not cover.
            if (rec.isPrivate) {
                setTitle("Download")
            } else {
                setTitle(rec.title)
                setDescription(url)
            }
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
            // Audit P-m11: opt-in Wi-Fi-only downloads.
            setAllowedOverMetered(
                store.setting("wifiOnlyDownloads", "false") != "true"
            )
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
        // Audit S-M8: same HTTPS-only rule as openGet.
        if (url.startsWith("https://")) {
            if (!referer.isNullOrBlank()) conn.setRequestProperty("Referer", referer)
            if (!cookie.isNullOrBlank()) conn.setRequestProperty("Cookie", cookie)
        }
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
        return runSegmentedJobInner(
            context, store, rec, referer, finalUrl, total, cookie, md5,
            onProgress, allowReprobe = true
        )
    }

    private fun runSegmentedJobInner(
        context: Context,
        store: AppStore,
        rec: DownloadRecord,
        referer: String?,
        finalUrl: String,
        total: Long,
        cookie: String?,
        md5: String?,
        onProgress: (Int) -> Unit = {},
        allowReprobe: Boolean
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
        // Audit P-M9: set when the chunk pool stalls — the chunk
        // loops check it between reads, like the downgrade flag.
        val watchdogFired = java.util.concurrent.atomic.AtomicBoolean(false)
        // Audit F-m11: set when any chunk's Content-Range total
        // differs from the probe total (the file changed size).
        val totalMismatch = java.util.concurrent.atomic.AtomicBoolean(false)
        val reportProgress = {
            val pct = ((written.get() * 100) / total).toInt()
            if (lastPct.getAndSet(pct) != pct) {
                store.updateDownloadProgress(rec.id, pct)
                // Beta.64 #30: size / speed / ETA.
                noteBytes(store, rec.id, written.get(), total)
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
                    if (downgraded.get() || watchdogFired.get()) return@chunkLabel
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
                                // Audit D-m14: slice the
                                // rate-limit wait so Pause and
                                // Cancel answer during it.
                                if (sleepChecked(rec.id, waitSecs * 1000)) {
                                    paused.set(true)
                                    writeSegJournal(journalFile, chunks, done)
                                    return@chunkLabel
                                }
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
                                // Audit F-m11: a chunk served from a
                                // changed file reports a different
                                // total than the probe saw.
                                contentRangeTotal(
                                    conn.getHeaderField("Content-Range")
                                )?.let { observed ->
                                    if (observed != total) totalMismatch.set(true)
                                }
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
                                        if (downgraded.get() || watchdogFired.get()) return@chunkLabel
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
        // Audit P-M9: the old unbounded await let a stalled chunk
        // hang the worker forever — WorkManager cancellation
        // cannot interrupt it. Wait in slices; if no chunk
        // progress arrives for ~10 minutes, stop the job and
        // retry it (the worker's attempt cap turns repeated
        // stalls into a terminal failure).
        var poolTerminated = false
        var lastWritten = written.get()
        var lastProgressAt = System.currentTimeMillis()
        // Audit D-M8: the rolling window for the
        // minimum-throughput watchdog below.
        var windowStart = System.currentTimeMillis()
        var windowBytes = written.get()
        var watchdogFailed = false
        try {
            while (!poolTerminated) {
                poolTerminated = pool.awaitTermination(
                    30, java.util.concurrent.TimeUnit.SECONDS
                )
                if (poolTerminated) break
                val now = written.get()
                if (now != lastWritten) {
                    lastWritten = now
                    lastProgressAt = System.currentTimeMillis()
                }
                // Audit D-M8: a trickling connection resets
                // the stall timer on every byte — add a
                // minimum-throughput watchdog alongside it:
                // under 256 KB in any rolling 10 minutes
                // aborts the chunk for a retry, and the row
                // shows "Stalled — very slow connection".
                if (System.currentTimeMillis() - windowStart >= 10L * 60 * 1000) {
                    if (written.get() - windowBytes < 256L * 1024) {
                        noteError(store, rec.id, "Stalled — very slow connection")
                        transientFailure.set(true)
                        watchdogFired.set(true)
                        pool.shutdownNow()
                        poolTerminated = pool.awaitTermination(
                            90, java.util.concurrent.TimeUnit.SECONDS
                        )
                        if (!poolTerminated) watchdogFailed = true
                        break
                    }
                    windowStart = System.currentTimeMillis()
                    windowBytes = written.get()
                }
                if (now == lastWritten && System.currentTimeMillis() - lastProgressAt >
                    10L * 60 * 1000
                ) {
                    transientFailure.set(true)
                    watchdogFired.set(true)
                    pool.shutdownNow()
                    // Give the interrupted threads a bounded
                    // chance to exit before returning, so a
                    // retry never writes the same staging
                    // file concurrently.
                    poolTerminated = pool.awaitTermination(
                        90, java.util.concurrent.TimeUnit.SECONDS
                    )
                    if (!poolTerminated) {
                        // Still stuck: fail terminally and drop
                        // staging, so a later manual retry
                        // starts clean instead of resuming
                        // into a file stuck threads still hold.
                        watchdogFailed = true
                    }
                    break
                }
            }
        } catch (e: InterruptedException) {
            // Worker cancellation: stop the pool and fall through
            // to the existing cancelled check below.
            pool.shutdownNow()
        }
        if (watchdogFailed) {
            staging.delete()
            journalFile.delete()
            noteError(store, rec.id, "download stalled")
            writeTerminalIfNotPaused(store, rec.id, "failed", progress = -1)
            return SegmentedOutcome.FAILED
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
        if (totalMismatch.get()) {
            noteError(store, rec.id, "file changed during download")
            // Plan-invalidating: the journal's per-chunk offsets
            // belong to the old size, so a resume would leave gaps.
            // Discard staging + journal and retry from a fresh probe.
            staging.delete()
            journalFile.delete()
            // Audit D-M7: retrying with the same stale probe
            // values could never succeed — re-probe once
            // here and re-run with the fresh size, updating
            // the bundle so later resumes use it too. Only
            // a fresh probe that also fails ends the job.
            if (allowReprobe) {
                val fresh = probeSegmented(appCtx, finalUrl, referer)
                if (fresh.eligible) {
                    updateJobBundle(store, rec.id, mapOf(
                        "finalUrl" to fresh.finalUrl,
                        "total" to fresh.total.toString(),
                        "cookie" to (fresh.cookie ?: ""),
                        "md5" to (fresh.md5 ?: "")
                    ))
                    return runSegmentedJobInner(
                        context, store, rec, referer, fresh.finalUrl,
                        fresh.total, fresh.cookie, fresh.md5, onProgress,
                        allowReprobe = false
                    )
                }
                writeTerminalIfNotPaused(store, rec.id, "failed", progress = -1)
                return SegmentedOutcome.FAILED
            }
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
            // Audit F-m2: never publish over an existing display
            // name — dedup against the MediaStore first.
            val publishName = uniqueMediaStoreName(appCtx, rec.fileName)
            if (publishName != rec.fileName) {
                store.updateDownloadFileName(rec.id, publishName)
            }
            val uri = publishToMediaStore(appCtx, staging, publishName)
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
        isPrivate: Boolean = false,
        location: String? = null,
        fileNameOverride: String? = null
    ): Long {
        val id = nextDownloadId(store)
        val appCtx = context.applicationContext
        // Audit D-p1: a dialog rename wins over the
        // title-derived name, keeping the .mp4 suffix; a
        // stem-less rename falls back to the title name.
        val fileName = if (!fileNameOverride.isNullOrBlank()) {
            val trimmed = fileNameOverride.trim()
            val stem = trimmed.substringBeforeLast('.')
            if (trimmed.startsWith(".") && stem.isEmpty()) {
                sanitize(title.ifBlank { "merged" }) + ".mp4"
            } else {
                sanitize(stem.ifBlank { title.ifBlank { "merged" } }) + ".mp4"
            }
        } else {
            sanitize(title.ifBlank { "merged" }) + ".mp4"
        }
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
                "isPrivate" to isPrivate.toString(),
                "location" to (if (isPrivate) "app" else (location ?: "")),
                "fileName" to fileName
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
            "vtt" -> "text/vtt"
            "ttml" -> "application/ttml+xml"
            else -> "application/octet-stream"
        }

    /**
     * Publish a finished file into the public Downloads folder through
     * MediaStore — the only way in under scoped storage. Returns the
     * content URI, or null on any failure (caller falls back gracefully).
     */
    /**
     * Beta.58 (6): place downloaded subtitle files next to
     * the finished video — published through MediaStore
     * beside a MediaStore video, renamed beside an
     * app-path video. The first track takes the video's
     * own base name, later ones .sub1/.sub2, so the
     * player's sibling discovery finds them. Best-effort:
     * a subtitle never fails the video.
     */
    private fun placeSubtitleFiles(
        context: Context,
        subs: List<File>,
        videoName: String,
        videoDir: File,
        useMediaStore: Boolean
    ) {
        if (subs.isEmpty()) return
        val base = videoName.substringBeforeLast('.', videoName)
        subs.forEachIndexed { i, sub ->
            runCatching {
                val suffix = if (i == 0) "" else ".sub$i"
                val name = base + suffix + "." + sub.extension
                if (useMediaStore) {
                    val uri = publishToMediaStore(context, sub, name)
                    if (uri != null) sub.delete()
                } else {
                    renameOrCopy(sub, File(videoDir, name))
                }
            }
        }
    }

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

    private fun openGet(
        url: String,
        referer: String?,
        cookieSnapshot: CookieSnapshot? = null
    ): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", UA)
        // Audit S-M8: credentials never ride a non-HTTPS request —
        // Cookie and Referer are attached only over HTTPS.
        if (url.startsWith("https://")) {
            if (!referer.isNullOrBlank()) conn.setRequestProperty("Referer", referer)
            // Beta.58 (2): the tap-time snapshot wins for its
            // own host; everything else re-reads the live jar.
            val live = runCatching {
                android.webkit.CookieManager.getInstance().getCookie(url)
            }.getOrNull()
            cookieForRequest(url, cookieSnapshot, live)
                ?.let { conn.setRequestProperty("Cookie", it) }
        }
        return conn
    }

    /** An HTTP error status from a download fetch
     *  (0.0.99991): carries the code so failures can be
     *  classified and, above all, REPORTED instead of
     *  swallowed by a blanket retry. */
    class HttpStatusException(
        val code: Int,
        /** Seconds the server asked us to wait (Retry-After),
         *  or null when the response carried none. */
        val retryAfterSecs: Long? = null
    ) : java.io.IOException("HTTP $code")

    private data class HlsResult(
        val outcome: HlsOutcome,
        val isMp4: Boolean = false,
        val error: String? = null,
        /** Beta.58 (6): staged subtitle files downloaded
         *  beside the video; the job places them next to
         *  the finished file. */
        val subtitleFiles: List<File> = emptyList()
    )

    private fun checkStatus(conn: HttpURLConnection) {
        val code = conn.responseCode
        if (code >= 400) {
            throw HttpStatusException(
                code,
                conn.getHeaderField("Retry-After")?.toLongOrNull()
            )
        }
    }

    private fun checkedBytes(conn: HttpURLConnection): ByteArray {
        checkStatus(conn)
        return conn.inputStream.use { it.readBytes() }
    }

    private fun errorLabel(e: Exception): String = when (e) {
        is HttpStatusException ->
            if (e.code == 429) "Rate-limited, retrying" else "HTTP ${e.code}"
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
        // Beta.58 (2): the tap-time cookie snapshot for this
        // record's media host, replayed by the engine only
        // to that host; a missing or blank snapshot means
        // the engine re-reads the live jar per request.
        val cookieSnapshot = readJobBundle(store, rec.id)?.let { b ->
            val c = b["cookie"].orEmpty()
            val h = b["cookieHost"].orEmpty()
            if (c.isNotBlank() && h.isNotBlank()) CookieSnapshot(h, c) else null
        }
        val result = downloadHls(
            appCtx, store, rec.id, rec.url, referer, joinDest, onProgress,
            cookieSnapshot
        )
        val outcome = result.outcome
        val isMp4 = result.isMp4
        val wasCancelled = cancelledIds.remove(rec.id)
        var publishName = fileName
        var publishFile = joinDest
        if (outcome == HlsOutcome.OK && !wasCancelled) {
            if (isMp4 && fileName.endsWith(".ts", ignoreCase = true)) {
                // CMAF stream: the joined file is an MP4 — name it so.
                publishName = fileName.substringBeforeLast('.') + ".mp4"
                if (File(joinDest.parentFile, publishName).exists()) {
                    publishName = dedupFileName(
                        { File(joinDest.parentFile, it).exists() }, publishName
                    )
                }
                val renamed = File(joinDest.parentFile, publishName)
                if (renameOrCopy(joinDest, renamed)) {
                    publishFile = renamed
                    store.updateDownloadFileName(rec.id, publishName)
                }
            }
            if (useMediaStore) {
                // Audit F-m2: same MediaStore dedup as the
                // segmented path (the staging-dir dedup above
                // does not see the public folder).
                val mediaName = uniqueMediaStoreName(appCtx, publishName)
                if (mediaName != publishName) {
                    store.updateDownloadFileName(rec.id, mediaName)
                    publishName = mediaName
                }
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
        // Beta.58 (6): subtitle files travel with the video.
        if (outcome == HlsOutcome.OK && !wasCancelled) {
            placeSubtitleFiles(
                appCtx, result.subtitleFiles, publishName,
                publishFile.parentFile ?: appCtx.filesDir,
                useMediaStore
            )
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

    private fun fetchText(
        url: String,
        referer: String?,
        cookieSnapshot: CookieSnapshot? = null
    ): String {
        val conn = openGet(url, referer, cookieSnapshot)
        checkStatus(conn)
        return conn.inputStream.bufferedReader().readText()
    }

    private fun openGetRange(
        url: String,
        referer: String?,
        range: HlsPlaylist.ByteRange?,
        cookieSnapshot: CookieSnapshot? = null
    ): HttpURLConnection {
        val conn = openGet(url, referer, cookieSnapshot)
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
        onProgress: (Int) -> Unit = {},
        cookieSnapshot: CookieSnapshot? = null
    ): HlsResult {
        return try {
            // Beta.58 (3): Referer chaining (approved by
            // Robert 2026-11-07; previously not authorised).
            // The playlist fetch carries the PAGE referer;
            // every fetch after it carries the playlist or
            // variant URL it came from — some CDNs gate
            // segments on the playlist referer, not the
            // page URL. Chaining applies to segment fetches
            // only; the page referer is not used anywhere
            // else. [chainReferer] always holds the URL of
            // the playlist the next fetch belongs to.
            var url = playlistUrl
            var chainReferer = referer
            var text = fetchText(url, chainReferer, cookieSnapshot)
            var hops = 0
            // Beta.58 (4): the separate-audio playlist URL for
            // the picked variant, if its audio lives in its own
            // rendition (captured per hop; the last hop wins).
            var separateAudioUrl: String? = null
            // Beta.58 (6): the subtitle rendition URLs for
            // the picked master (captured per hop, like the
            // audio URL; the last hop wins).
            var subtitleUrls: List<String> = emptyList()
            while (HlsPlaylist.isMaster(text) && hops < 2) {
                val master = HlsPlaylist.parseMaster(text, url)
                val best = master.bestVariant()
                    ?: return HlsResult(
                        HlsOutcome.FAILED,
                        error = "No playable variant in playlist"
                    )
                separateAudioUrl = master.audioUrlFor(best)
                subtitleUrls = master.subtitleUrls()
                val masterUrl = url
                url = best.url
                // The variant fetch belongs to the master.
                chainReferer = masterUrl
                text = fetchText(url, chainReferer, cookieSnapshot)
                hops++
            }
            // Segments, keys and the init segment belong to
            // the media playlist just fetched.
            chainReferer = url
            val plan = HlsPlaylist.parseMedia(text, url)
            // Audit D-M3: a live playlist has no ENDLIST —
            // refuse it instead of shipping a truncated
            // snapshot labelled "complete".
            if (plan.isLive) {
                return HlsResult(
                    HlsOutcome.UNSUPPORTED,
                    error = "Live streams can't be downloaded"
                )
            }
            if (plan.tooLarge) {
                return HlsResult(
                    HlsOutcome.UNSUPPORTED,
                    error = "Playlist too large"
                )
            }
            if (plan.multiInit) {
                return HlsResult(
                    HlsOutcome.UNSUPPORTED,
                    error = "Multiple init segments not supported"
                )
            }
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

            // Beta.58 (4): a separate audio rendition is no
            // longer refused — its media playlist is fetched
            // (referer = the video variant URL, per the
            // chaining rule) and validated like the video one.
            // A live/too-large/multi-init/unsupported-key
            // audio playlist fails honestly instead of joining
            // a silent video.
            var audioPlan: HlsPlaylist.MediaPlan? = null
            var audioPlaylistUrl: String? = null
            if (separateAudioUrl != null) {
                audioPlaylistUrl = separateAudioUrl
                val audioText = fetchText(
                    audioPlaylistUrl, chainReferer, cookieSnapshot
                )
                val ap = HlsPlaylist.parseMedia(audioText, audioPlaylistUrl)
                if (ap.isLive) {
                    return HlsResult(
                        HlsOutcome.UNSUPPORTED,
                        error = "Live streams can't be downloaded"
                    )
                }
                if (ap.tooLarge) {
                    return HlsResult(
                        HlsOutcome.UNSUPPORTED,
                        error = "Playlist too large"
                    )
                }
                if (ap.multiInit) {
                    return HlsResult(
                        HlsOutcome.UNSUPPORTED,
                        error = "Multiple init segments not supported"
                    )
                }
                if (ap.unsupportedKeyMethod != null) {
                    return HlsResult(
                        HlsOutcome.UNSUPPORTED,
                        error = "Unsupported encryption: " +
                            ap.unsupportedKeyMethod
                    )
                }
                if (ap.segments.isEmpty()) {
                    return HlsResult(
                        HlsOutcome.FAILED,
                        error = "Separate audio playlist has no segments"
                    )
                }
                audioPlan = ap
            }

            // Beta.58 (4): the segment fetch for one media
            // playlist, shared by the video and audio plans —
            // same staging, manifest validation, AES-128 and
            // truncation rules. Progress is global across
            // both plans (video first, then audio).
            val keyCache = mutableMapOf<String, ByteArray>()
            fun keyBytes(key: HlsPlaylist.Key, refererForKeys: String?): ByteArray? {
                val uri = key.uri ?: return null
                return keyCache.getOrPut(uri) {
                    checkedBytes(openGet(uri, refererForKeys, cookieSnapshot))
                }
            }
            var lastPct = -1
            var doneSegs = 0
            // Beta.64 #30: running byte count for the
            // size/speed line (total unknown for HLS).
            var hlsDoneBytes = 0L
            val totalSegs = plan.segments.size +
                (audioPlan?.segments?.size ?: 0)
            fun fetchSegments(
                mediaPlan: HlsPlaylist.MediaPlan,
                segDir: File,
                refererForPlan: String?
            ) {
                segDir.mkdirs()
                // Audit D-B1: the staged segments belong to THIS
                // playlist — a manifest of segment URLs (plus the
                // init URL) invalidates any staged file whose
                // source differs, so a later same-named download
                // can never inherit another video's segments.
                val segManifest = File(segDir, "segurls.txt")
                val manifestLines = runCatching { segManifest.readLines() }.getOrDefault(emptyList())
                val manifestInit = manifestLines.getOrNull(0)
                if (manifestInit != null && manifestInit != "init=${mediaPlan.initUrl ?: ""}") {
                    segDir.listFiles()?.filter { it.name.startsWith("seg-") }?.forEach { it.delete() }
                }
                segManifest.writeText(buildString {
                    appendLine("init=${mediaPlan.initUrl ?: ""}")
                    mediaPlan.segments.forEach { appendLine(it.url) }
                })
                mediaPlan.segments.forEachIndexed { idx, seg ->
                    if (cancelledIds.contains(recId)) throw InterruptedException("cancelled")
                    if (pausedIds.contains(recId)) throw InterruptedException("paused")
                    val segFile = File(segDir, "seg-%05d.bin".format(idx))
                    // A staged segment from a different playlist
                    // URL at this index is stale, not a resume.
                    if (segFile.exists() && manifestLines.getOrNull(idx + 1) != null &&
                        manifestLines[idx + 1] != seg.url
                    ) {
                        segFile.delete()
                    }
                    if (!segFile.exists()) {
                        // Audit P-m12: stream the segment straight
                        // to disk (decrypting on the fly when the
                        // playlist encrypts it) instead of holding
                        // the whole segment plus its decrypted copy
                        // in memory.
                        val conn = openGetRange(seg.url, refererForPlan, seg.range, cookieSnapshot)
                        val tmpSeg = File(segDir, "seg-%05d.tmp".format(idx))
                        try {
                            checkStatus(conn)
                            // Audit D-M5: a 200 answer to a ranged
                            // request would write the whole file
                            // into one segment slot — refuse it.
                            if (seg.range != null && conn.responseCode != 206) {
                                throw java.io.IOException(
                                    "server stopped honouring Range"
                                )
                            }
                            if (seg.key != null && seg.key.method == "AES-128") {
                                val kb = keyBytes(seg.key, refererForPlan)
                                    ?: throw java.io.IOException("HLS key fetch failed")
                                // Audit D-p3: a wrong-length key
                                // fails fast instead of retrying
                                // futilely into Cipher.init.
                                if (kb.size != 16) {
                                    throw FatalDownloadException(
                                        "Invalid encryption key"
                                    )
                                }
                                // Audit D-M10: an explicit IV that
                                // does not parse is an error, not a
                                // silent fall back to the sequence
                                // IV (which would corrupt the first
                                // block of every segment).
                                val iv = if (seg.key.ivHex != null) {
                                    HlsPlaylist.parseIvHex(seg.key.ivHex)
                                        ?: throw FatalDownloadException(
                                            "Invalid encryption IV in playlist"
                                        )
                                } else {
                                    HlsPlaylist.sequenceIv(seg.sequence)
                                }
                                val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
                                cipher.init(
                                    javax.crypto.Cipher.DECRYPT_MODE,
                                    javax.crypto.spec.SecretKeySpec(kb, "AES"),
                                    javax.crypto.spec.IvParameterSpec(iv)
                                )
                                javax.crypto.CipherInputStream(conn.inputStream, cipher).use { ins ->
                                    tmpSeg.outputStream().use { out -> ins.copyTo(out) }
                                }
                                // Encrypted segments skip the length
                                // check: Content-Length counts
                                // ciphertext, decryption yields
                                // plaintext minus padding.
                            } else {
                                val copied = conn.inputStream.use { ins ->
                                    tmpSeg.outputStream().use { out -> ins.copyTo(out) }
                                }
                                // Audit D-m15: a truncated segment
                                // must fail, not join short.
                                val expected = conn.contentLengthLong
                                if (expected > 0 && copied != expected) {
                                    throw java.io.IOException("segment truncated")
                                }
                            }
                            if (!renameOrCopy(tmpSeg, segFile)) {
                                throw java.io.IOException("could not stage segment")
                            }
                        } catch (e: Exception) {
                            // A partial segment must not survive:
                            // the retry re-downloads it from scratch.
                            tmpSeg.delete()
                            throw e
                        } finally {
                            conn.disconnect()
                        }
                    }
                    doneSegs++
                    // Beta.64 #30: running byte count (total
                    // unknown for segmented HLS).
                    hlsDoneBytes += segFile.length()
                    noteBytes(store, recId, hlsDoneBytes, -1)
                    val pct = doneSegs * 100 / totalSegs
                    // The same percentage is never written twice
                    // (0.0.9993): with 100+ segment streams the old
                    // check fired repeatedly per step, and a resume
                    // re-fired them all.
                    if ((pct % 5 == 0 || doneSegs == totalSegs) &&
                        pct != lastPct
                    ) {
                        lastPct = pct
                        store.updateDownloadProgress(recId, pct)
                        onProgress(pct)
                    }
                }
            }
            // One joined file per plan: the init segment, then
            // the media segments in order.
            fun joinPlan(
                mediaPlan: HlsPlaylist.MediaPlan,
                segDir: File,
                outFile: File,
                refererForPlan: String?
            ) {
                outFile.outputStream().use { out ->
                    if (mediaPlan.initUrl != null) {
                        val initConn = openGetRange(mediaPlan.initUrl, refererForPlan, mediaPlan.initRange, cookieSnapshot)
                        checkStatus(initConn)
                        if (mediaPlan.initRange != null && initConn.responseCode != 206) {
                            throw java.io.IOException(
                                "server stopped honouring Range"
                            )
                        }
                        val initCopied = initConn.inputStream.use { it.copyTo(out) }
                        val initExpected = initConn.contentLengthLong
                        if (initExpected > 0 && initCopied != initExpected) {
                            throw java.io.IOException("segment truncated")
                        }
                    }
                    mediaPlan.segments.indices.forEach { idx ->
                        File(segDir, "seg-%05d.bin".format(idx)).inputStream().use { it.copyTo(out) }
                    }
                }
            }
            val segDir = File(dest.parentFile, dest.name + "." + recId + ".segments")
            fetchSegments(plan, segDir, chainReferer)
            val audioSegDir = if (audioPlan != null) {
                File(dest.parentFile, dest.name + "." + recId + ".asegments")
            } else null
            if (audioPlan != null && audioSegDir != null) {
                fetchSegments(audioPlan, audioSegDir, audioPlaylistUrl)
            }
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + "." + recId + ".part")
            joinPlan(plan, segDir, tmp, chainReferer)
            // Beta.58 (4): with a separate audio rendition,
            // the audio plan joins to its own file and the
            // pair is muxed into one MP4 (the DASH/merge
            // shape) instead of shipping a silent video.
            var finalFile = tmp
            var finalIsMp4 = plan.isCmaf
            if (audioPlan != null && audioSegDir != null) {
                val audioJoined = File(
                    dest.parentFile, dest.name + "." + recId + ".track1"
                )
                joinPlan(audioPlan, audioSegDir, audioJoined, audioPlaylistUrl)
                val muxOut = File(
                    dest.parentFile, dest.name + "." + recId + ".mux"
                )
                val muxed = muxHlsPair(
                    context, tmp, audioJoined, muxOut, recId
                )
                if (muxed) {
                    tmp.delete()
                    audioJoined.delete()
                    finalFile = muxOut
                    finalIsMp4 = true
                } else {
                    // Honest failure, as in the merge path:
                    // a silent video is worse than no file.
                    muxOut.delete()
                    tmp.delete()
                    audioJoined.delete()
                    return HlsResult(
                        HlsOutcome.FAILED,
                        error = "Separate audio could not be combined " +
                            "into one file"
                    )
                }
            }
            if (cancelledIds.contains(recId)) {
                finalFile.delete()
                return HlsResult(HlsOutcome.FAILED, error = "Cancelled")
            }
            if (pausedIds.contains(recId)) {
                // The join is partial and discarded; the staged
                // segments stay, so resume re-joins from them.
                finalFile.delete()
                return HlsResult(HlsOutcome.FAILED, error = "Paused")
            }
            // Beta.58 (6): subtitle renditions, best-effort —
            // a caption that fails to fetch never fails the
            // video. A rendition is either a plain .vtt file
            // or a media playlist of .vtt segments, joined
            // with the WEBVTT header kept exactly once.
            val subtitleFiles = mutableListOf<File>()
            fun fetchSubtitleRendition(renditionUrl: String, index: Int) {
                runCatching {
                    val out = File(
                        dest.parentFile,
                        dest.name + "." + recId + ".sub" + index + ".vtt"
                    )
                    val subText = fetchText(
                        renditionUrl, chainReferer, cookieSnapshot
                    )
                    if (subText.trimStart().startsWith("WEBVTT")) {
                        // A plain caption file, not a playlist.
                        if (subText.length > 2_000_000) return
                        out.writeText(subText)
                    } else {
                        val subPlan = HlsPlaylist.parseMedia(
                            subText, renditionUrl
                        )
                        if (subPlan.isLive || subPlan.tooLarge ||
                            subPlan.multiInit ||
                            subPlan.unsupportedKeyMethod != null ||
                            subPlan.segments.isEmpty() ||
                            subPlan.segments.size > 500
                        ) return
                        val joined = StringBuilder()
                        subPlan.segments.forEachIndexed { si, seg ->
                            val conn = openGet(
                                seg.url, renditionUrl, cookieSnapshot
                            )
                            checkStatus(conn)
                            val bytes = conn.inputStream.use { it.readBytes() }
                            if (bytes.size > 2_000_000) return
                            var chunk = bytes.decodeToString()
                            // Keep the WEBVTT header once: later
                            // segments repeat it.
                            if (si > 0 &&
                                chunk.trimStart().startsWith("WEBVTT")
                            ) {
                                val nl = chunk.indexOf('\n')
                                chunk = if (nl < 0) "" else chunk.substring(nl + 1)
                            }
                            joined.append(chunk)
                        }
                        val text = joined.toString()
                        if (text.length > 2_000_000) return
                        if (!text.trimStart().startsWith("WEBVTT")) {
                            joined.insert(0, "WEBVTT\n\n")
                        }
                        out.writeText(joined.toString())
                    }
                    if (out.length() > 0) subtitleFiles.add(out)
                }
            }
            subtitleUrls.take(4).forEachIndexed { i, subUrl ->
                fetchSubtitleRendition(subUrl, i)
            }
            var writeDest = dest
            if (writeDest.exists() && writeDest.parentFile?.name != "notscape-staging") {
                val unique = dedupFileName({ File(writeDest.parentFile, it).exists() }, writeDest.name)
                store.updateDownloadFileName(recId, unique)
                writeDest = File(writeDest.parentFile, unique)
            }
            if (!renameOrCopy(finalFile, writeDest)) {
                return HlsResult(
                    HlsOutcome.FAILED,
                    error = "could not finish download"
                )
            }
            segDir.deleteRecursively()
            audioSegDir?.deleteRecursively()
            HlsResult(
                HlsOutcome.OK, isMp4 = finalIsMp4,
                subtitleFiles = subtitleFiles
            )
        } catch (e: InterruptedException) {
            HlsResult(HlsOutcome.FAILED, error = "Cancelled")
        } catch (e: HttpStatusException) {
            // A 4xx refusal (an expired signed link, a forbidden
            // CDN) will not heal by retrying; 408, 429 and 5xx
            // may (0.0.99991).
            val retryable = e.code == 408 || e.code == 429 ||
                e.code >= 500
            // Audit D-m19: honour Retry-After on 429 (capped)
            // before the worker's backoff retry, and surface
            // rate-limiting distinctly from other failures.
            if (e.code == 429) {
                val wait = e.retryAfterSecs?.coerceIn(1L, 300L) ?: 0L
                if (wait > 0) {
                    runCatching { sleepChecked(recId, wait * 1000) }
                }
            }
            HlsResult(
                if (retryable) HlsOutcome.RETRY
                else HlsOutcome.FAILED,
                error = errorLabel(e)
            )
        } catch (e: FatalDownloadException) {
            HlsResult(HlsOutcome.FAILED, error = e.message)
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
        // Beta.58 (2): same tap-time cookie snapshot as the
        // HLS job — replayed only to its own host.
        val cookieSnapshot = readJobBundle(store, rec.id)?.let { b ->
            val c = b["cookie"].orEmpty()
            val h = b["cookieHost"].orEmpty()
            if (c.isNotBlank() && h.isNotBlank()) CookieSnapshot(h, c) else null
        }
        val result = downloadDash(
            appCtx, store, rec.id, rec.url, referer, joinDest, onProgress,
            cookieSnapshot
        )
        val outcome = result.outcome
        val wasCancelled = cancelledIds.remove(rec.id)
        var publishName = fileName
        if (outcome == HlsOutcome.OK && !wasCancelled && useMediaStore) {
            // Audit F-m2: dedup against the MediaStore before
            // publishing, as in the segmented and HLS paths.
            publishName = uniqueMediaStoreName(appCtx, fileName)
            if (publishName != fileName) {
                store.updateDownloadFileName(rec.id, publishName)
            }
            val uri = publishToMediaStore(appCtx, joinDest, publishName)
            if (uri != null) {
                store.updateDownloadUri(rec.id, uri.toString())
                joinDest.delete()
            } else {
                store.updateDownloadUri(rec.id, joinDest.absolutePath)
            }
        }
        // Beta.58 (6): subtitle files travel with the video.
        if (outcome == HlsOutcome.OK && !wasCancelled) {
            placeSubtitleFiles(
                appCtx, result.subtitleFiles, publishName,
                joinDest.parentFile ?: appCtx.filesDir,
                useMediaStore
            )
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
                            // Beta.64 #30: size / speed / ETA.
                            noteBytes(store, recId, read, total)
                        }
                    }
                }
            }
        }
        // Audit D-m15: a short read against a declared
        // length is a truncation, not a completion.
        if (total != null && read != total) {
            throw java.io.IOException("download truncated")
        }
        if (out.exists()) out.delete()
        if (!renameOrCopy(tmp, out)) {
            throw java.io.IOException("could not finish download")
        }
        // Beta.64 #30: final byte count.
        noteBytes(store, recId, read, total ?: -1)
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
            videoFile = File(dest.parentFile, dest.name + "." + recId + ".mergeV")
            audioFile = File(dest.parentFile, dest.name + "." + recId + ".mergeA")
            downloadPlain(videoUrl, referer, videoFile!!, recId, 0, 45, store)
            downloadPlain(audioUrl, referer, audioFile!!, recId, 45, 90, store)
            if (cancelledIds.contains(recId)) {
                throw InterruptedException("cancelled")
            }
            dest.parentFile?.mkdirs()
            tmpOut = File(dest.parentFile, dest.name + "." + recId + ".mux")
            muxed = remuxPicked(videoFile!!, audioFile!!, tmpOut!!)
            if (muxed) {
                var writeDest = dest
                if (writeDest.exists() && writeDest.parentFile?.name != "notscape-staging") {
                    val unique = dedupFileName({ File(writeDest.parentFile, it).exists() }, writeDest.name)
                    store.updateDownloadFileName(recId, unique)
                    writeDest = File(writeDest.parentFile, unique)
                }
                if (!renameOrCopy(tmpOut!!, writeDest)) {
                    return HlsResult(
                        HlsOutcome.FAILED,
                        error = "could not finish download"
                    )
                }
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
                        dest.parentFile, dest.name + "." + recId + ".transV.mp4"
                    )
                    transAudio = File(
                        dest.parentFile, dest.name + "." + recId + ".transA.mp4"
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
                        if (!renameOrCopy(tmpOut!!, writeDest)) {
                    return HlsResult(
                        HlsOutcome.FAILED,
                        error = "could not finish download"
                    )
                }
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
            // Audit D-m19: honour Retry-After on 429 (capped)
            // before the retry decision.
            if (e.code == 429) {
                val wait = e.retryAfterSecs?.coerceIn(1L, 300L) ?: 0L
                if (wait > 0) {
                    runCatching { sleepChecked(recId, wait * 1000) }
                }
            }
            HlsResult(
                if (retryable) HlsOutcome.RETRY else HlsOutcome.FAILED,
                error = errorLabel(e)
            )
        } catch (e: FatalDownloadException) {
            HlsResult(HlsOutcome.FAILED, error = e.message)
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
            // Audit F-m2: dedup against the MediaStore before
            // publishing, as in the segmented and HLS paths.
            val publishName = uniqueMediaStoreName(appCtx, fileName)
            if (publishName != fileName) {
                store.updateDownloadFileName(rec.id, publishName)
            }
            val uri = publishToMediaStore(appCtx, joinDest, publishName)
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
        onProgress: (Int) -> Unit = {},
        cookieSnapshot: CookieSnapshot? = null
    ): HlsResult {
        return try {
            // Beta.58 (3): Referer chaining — the manifest
            // fetch carries the page referer; every segment
            // and init fetch carries the manifest URL.
            val text = fetchText(mpdUrl, referer, cookieSnapshot)
            val chainReferer = mpdUrl
            // Audit D-m5: DRM is refused before any segment
            // is fetched, not after a full download fails
            // at remux with a generic error.
            if (DashManifest.hasDrm(text)) {
                return HlsResult(
                    HlsOutcome.UNSUPPORTED,
                    error = "DRM-protected video can't be downloaded"
                )
            }
            val variantBw = readJobBundle(store, recId)
                ?.get("variantBandwidth")?.toLongOrNull()
            val plan = DashManifest.parseDownloadPlan(text, mpdUrl, variantBw)
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
            // Beta.64 #30: running byte count for the
            // size/speed line (total unknown for DASH).
            var dashDoneBytes = 0L
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
                    dest.parentFile, dest.name + "." + recId + ".dash$ti"
                )
                segDir.mkdirs()
                segDirs.add(segDir)
                // Audit D-B1: same per-track URL manifest as
                // HLS — staged segments are validated against
                // the manifest's URLs before reuse.
                val dashManifest = File(segDir, "segurls.txt")
                val dashLines = runCatching { dashManifest.readLines() }.getOrDefault(emptyList())
                if (dashLines.getOrNull(0) != null &&
                    dashLines[0] != "init=${track.initUrl ?: ""}"
                ) {
                    segDir.listFiles()?.filter { it.name.startsWith("seg-") }?.forEach { it.delete() }
                }
                dashManifest.writeText(buildString {
                    appendLine("init=${track.initUrl ?: ""}")
                    track.segmentUrls.forEach { appendLine(it) }
                })
                track.segmentUrls.forEachIndexed { idx, segUrl ->
                    if (cancelledIds.contains(recId)) {
                        throw InterruptedException("cancelled")
                    }
                    if (pausedIds.contains(recId)) {
                        throw InterruptedException("paused")
                    }
                    val segFile = File(segDir, "seg-%05d.bin".format(idx))
                    if (segFile.exists() && dashLines.getOrNull(idx + 1) != null &&
                        dashLines[idx + 1] != segUrl
                    ) {
                        segFile.delete()
                    }
                    if (!segFile.exists()) {
                        val tmpSeg = File(
                            segDir, "seg-%05d.tmp".format(idx)
                        )
                        val segConn = openGet(segUrl, chainReferer, cookieSnapshot)
                        checkStatus(segConn)
                        val segCopied = segConn.inputStream.use { ins ->
                            tmpSeg.outputStream().use { ins.copyTo(it) }
                        }
                        // Audit D-m15: truncated segments fail.
                        val segExpected = segConn.contentLengthLong
                        if (segExpected > 0 && segCopied != segExpected) {
                            throw java.io.IOException("segment truncated")
                        }
                        if (!renameOrCopy(tmpSeg, segFile)) {
                            throw java.io.IOException("could not stage segment")
                        }
                    }
                    noteProgress()
                    // Beta.64 #30: running byte count (total
                    // unknown for segmented DASH).
                    dashDoneBytes += segFile.length()
                    noteBytes(store, recId, dashDoneBytes, -1)
                }
                // Assemble the track: init segment, then the
                // media segments in order - a valid fragmented
                // MP4 per track.
                val trackFile = File(
                    dest.parentFile, dest.name + "." + recId + ".track$ti"
                )
                trackFile.outputStream().use { out ->
                    track.initUrl?.let { initUrl ->
                        val initConn = openGet(initUrl, chainReferer, cookieSnapshot)
                        checkStatus(initConn)
                        val initCopied = initConn.inputStream.use {
                            it.copyTo(out)
                        }
                        val initExpected = initConn.contentLengthLong
                        if (initExpected > 0 && initCopied != initExpected) {
                            throw java.io.IOException("segment truncated")
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
                if (!renameOrCopy(trackFiles[0], writeDest)) {
                    return HlsResult(
                        HlsOutcome.FAILED,
                        error = "could not finish download"
                    )
                }
                true
            } else {
                val tmpOut = File(dest.parentFile, dest.name + "." + recId + ".mux")
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
                    if (!renameOrCopy(tmpOut, writeDest)) {
                        tmpOut.delete()
                        return HlsResult(
                            HlsOutcome.FAILED,
                            error = "could not finish download"
                        )
                    }
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
            // Beta.58 (6): subtitle tracks, best-effort —
            // same rule as HLS: a caption never fails the
            // video. Only single-file .vtt/.ttml tracks.
            val subtitleFiles = mutableListOf<File>()
            DashManifest.parseSubtitles(text, mpdUrl)
                .forEachIndexed { i, sub ->
                    runCatching {
                        val ext = when {
                            sub.url.endsWith(".vtt", ignoreCase = true) -> "vtt"
                            sub.url.endsWith(".ttml", ignoreCase = true) -> "ttml"
                            else -> return@runCatching
                        }
                        val out = File(
                            dest.parentFile,
                            dest.name + "." + recId + ".sub" + i + "." + ext
                        )
                        val conn = openGet(
                            sub.url, chainReferer, cookieSnapshot
                        )
                        checkStatus(conn)
                        conn.inputStream.use { ins ->
                            out.outputStream().use { ins.copyTo(it) }
                        }
                        if (out.length() in 1..2_000_000) {
                            subtitleFiles.add(out)
                        } else {
                            out.delete()
                        }
                    }
                }
            HlsResult(HlsOutcome.OK, subtitleFiles = subtitleFiles)
        } catch (e: InterruptedException) {
            HlsResult(HlsOutcome.FAILED, error = "Cancelled")
        } catch (e: HttpStatusException) {
            val retryable = e.code == 408 || e.code == 429 ||
                e.code >= 500
            // Audit D-m19: honour Retry-After on 429 (capped)
            // before the worker's backoff retry.
            if (e.code == 429) {
                val wait = e.retryAfterSecs?.coerceIn(1L, 300L) ?: 0L
                if (wait > 0) {
                    runCatching { sleepChecked(recId, wait * 1000) }
                }
            }
            HlsResult(
                if (retryable) HlsOutcome.RETRY
                else HlsOutcome.FAILED,
                error = errorLabel(e)
            )
        } catch (e: FatalDownloadException) {
            HlsResult(HlsOutcome.FAILED, error = e.message)
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

    /** Beta.58 (4): mux a joined HLS video file + a joined
     *  HLS audio file into one MP4. Fast remux first (the
     *  DASH shape); when the pair's codecs cannot go into
     *  MP4 as-is (VP9 video, Opus audio, AC-3 audio), the
     *  incompatible sides are re-encoded to H.264/AAC
     *  with the Media3 Transformer, then remuxed again —
     *  the same fallback downloadMerge uses. False when
     *  either side lacks its track, the pair still cannot
     *  be muxed, or the transcode fails (the caller fails
     *  honestly instead of retrying a hopeless mux). */
    private fun muxHlsPair(
        context: Context,
        videoFile: File,
        audioFile: File,
        outFile: File,
        recId: Long
    ): Boolean {
        if (remuxPicked(videoFile, audioFile, outFile)) return true
        val videoMime = probeTrackMime(videoFile, "video/")
        val audioMime = probeTrackMime(audioFile, "audio/")
        if (videoMime == null || audioMime == null) return false
        if (videoMime == MimeTypes.VIDEO_H264 &&
            audioMime == MimeTypes.AUDIO_AAC
        ) return false
        return runCatching {
            val transVideo = File(
                outFile.parentFile,
                outFile.nameWithoutExtension + ".transV.mp4"
            )
            val transAudio = File(
                outFile.parentFile,
                outFile.nameWithoutExtension + ".transA.mp4"
            )
            val useVideo = if (videoMime != MimeTypes.VIDEO_H264) {
                transcodeSide(context, videoFile, transVideo, true, recId)
                transVideo
            } else videoFile
            val useAudio = if (audioMime != MimeTypes.AUDIO_AAC) {
                transcodeSide(context, audioFile, transAudio, false, recId)
                transAudio
            } else audioFile
            remuxPicked(useVideo, useAudio, outFile)
        }.getOrDefault(false)
    }

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
            val snapshot = store.downloadsSnapshot()
            val inFlight = snapshot
                .filter { it.status == "downloading" || it.status == "queued" || it.status == "paused" }
                .map { it.fileName }
                .toSet()
            val inFlightIds = snapshot
                .filter { it.status == "downloading" || it.status == "queued" || it.status == "paused" }
                .map { it.id }
                .toSet()
            val weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
            // Audit D-m2: the sweep covers the staging
            // folder, the app folder, and the public
            // Downloads folder — staging lives in the
            // destination parent on the legacy path, and
            // the old sweep never looked there.
            for (dir in stagingDirs(context)) {
                sweepOneDir(dir, inFlight, inFlightIds, weekAgo)
            }
        }
    }

    /** One directory of the sweep: id-keyed staging is
     *  guarded by record id, legacy name-keyed staging by
     *  file name. Only this app's staging footprints are
     *  ever deleted — never arbitrary user files. */
    private fun sweepOneDir(
        dir: File,
        inFlight: Set<String>,
        inFlightIds: Set<Long>,
        weekAgo: Long
    ) {
        runCatching {
            if (!dir.exists()) return
            dir.listFiles()?.forEach { f ->
                val name = f.name
                if (f.lastModified() >= weekAgo) return@forEach
                val idInName = Regex(
                    "\\.(\\d+)\\.(segments|asegments|dash[01]|track[01]|segpart|segjournal|merge[A-Z]|mux|part|trans[AV])"
                ).find(name)?.groupValues?.get(1)?.toLongOrNull()
                val inFlightHere = if (idInName != null) {
                    idInName in inFlightIds
                } else {
                    val base = name.substringBefore(".segments")
                        .substringBefore(".dash").substringBefore(".track")
                        .substringBefore(".merge").substringBefore(".mux")
                        .substringBefore(".part").substringBefore(".trans")
                    base in inFlight
                }
                if (inFlightHere) return@forEach
                when {
                    f.isFile && name.endsWith(".part") -> f.delete()
                    f.isFile && (name.endsWith(".segpart") || name.endsWith(".segjournal")) -> f.delete()
                    f.isDirectory && (name.endsWith(".segments") || name.endsWith(".asegments") || name.endsWith(".dash0") || name.endsWith(".dash1")) ->
                        f.deleteRecursively()
                    f.isFile && (name.endsWith(".track0") || name.endsWith(".track1")) -> f.delete()
                    f.isFile && name.contains(".merge") -> f.delete()
                    f.isFile && name.endsWith(".mux") -> f.delete()
                    f.isFile && (name.endsWith(".transV.mp4") || name.endsWith(".transA.mp4")) -> f.delete()
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
    /** True when a file with this display name already sits in the
     *  public Downloads collection (audit F-m2/F-m3): under scoped
     *  storage a raw File check on that folder is blind, so the
     *  MediaStore is the collision oracle. Below API 29 the file
     *  check works, so this returns false and callers use files. */
    internal fun mediaStoreNameExists(context: Context, name: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 29) return false
        return runCatching {
            context.contentResolver.query(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(android.provider.MediaStore.MediaColumns._ID),
                "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(name),
                null
            )?.use { c -> c.moveToFirst() } ?: false
        }.getOrDefault(false)
    }

    /** A display name free in the public Downloads collection,
     *  with the beta.41 "(1)" suffix rule on collision. */
    internal fun uniqueMediaStoreName(context: Context, name: String): String =
        // Audit D-m16: the MediaStore is blind to Downloads
        // files placed there outside it (USB/sideloading),
        // so a raw file check is a second oracle — the
        // MediaStore hit wins on conflict, since the union
        // treats either hit as "taken".
        dedupFileName(
            {
                mediaStoreNameExists(context, it) ||
                    File(
                        Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                        ), it
                    ).exists()
            }, name
        )

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

    /** Beta.64 #30: in-memory speed samples — (last
     *  bytes, last time ms) per download, and the last
     *  time a sample was forwarded to the store. Speeds
     *  are derived, never persisted. */
    private val speedSamples = mutableMapOf<Long, Pair<Long, Long>>()
    private val lastForwardedAt = mutableMapOf<Long, Long>()

    /** Record a byte observation for [recId]: the record's
     *  downloaded/total bytes and a speed derived from
     *  the previous sample. Called from every engine's
     *  progress path (segmented, DASH plain, HLS/DASH
     *  segments, DM refresh). Forwarded to the store at
     *  most once a second per download (always on
     *  completion), so rows stay live without a tick
     *  storm. */
    fun noteBytes(
        store: AppStore, recId: Long, downloaded: Long, total: Long
    ) {
        val now = System.currentTimeMillis()
        val prev = synchronized(speedSamples) {
            speedSamples.put(recId, downloaded to now)
        }
        var speed = -1L
        if (prev != null) {
            val dt = (now - prev.second) / 1000.0
            val db = downloaded - prev.first
            if (dt > 0 && db >= 0) speed = (db / dt).toLong()
        }
        val lastAt = synchronized(lastForwardedAt) {
            lastForwardedAt[recId] ?: 0L
        }
        val done = total > 0 && downloaded >= total
        if (!done && now - lastAt < 1000) return
        synchronized(lastForwardedAt) { lastForwardedAt[recId] = now }
        store.updateDownloadBytes(recId, downloaded, total, speed)
    }

    /** Beta.64 #30: human-readable byte counts. */
    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "—"
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 ->
                String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    /** Beta.64 #30: human-readable speed. */
    fun formatSpeed(bps: Long): String =
        if (bps < 0) "—" else formatBytes(bps) + "/s"

    /** Beta.64 #30: human-readable time remaining. */
    fun formatEta(seconds: Long): String {
        if (seconds < 0) return "—"
        return when {
            seconds < 60 -> "${seconds}s left"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
        }
    }

    /** Beta.64 #30: the size/speed/ETA line for a
     *  download row, or null when nothing is known yet
     *  ("12.4 MB of 48.0 MB • 1.2 MB/s • 30s left"). */
    fun progressDetailLine(rec: DownloadRecord): String? {
        val downloaded = rec.bytesDownloaded
        val total = rec.totalBytes
        val speed = rec.speedBps
        if (downloaded < 0 && total < 0) return null
        val sizePart = when {
            downloaded >= 0 && total > 0 ->
                "${formatBytes(downloaded)} of ${formatBytes(total)}"
            total > 0 -> "of ${formatBytes(total)}"
            downloaded >= 0 -> formatBytes(downloaded)
            else -> null
        } ?: return null
        val speedPart = if (speed > 0) formatSpeed(speed) else null
        val etaPart =
            if (speed > 0 && total > 0 && downloaded >= 0 &&
                total > downloaded
            ) formatEta((total - downloaded) / speed) else null
        return listOfNotNull(sizePart, speedPart, etaPart)
            .joinToString(" • ")
    }

    fun refresh(context: Context, store: AppStore): Boolean {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var changed = false
        // Backfill MediaStore locations for finished public downloads
        // that don't have one yet (e.g. DownloadManager downloads, or
        // ones completed by an older version): the library needs a
        // content URI to reach them under scoped storage.
        // Audit D-m18: backfill runs for URI-less
        // completed records on every refresh pass, not once
        // per process — MediaStore indexing may lag the
        // DownloadManager completion.
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
                if (!it.moveToFirst()) {
                    // Audit D-M6: a DownloadManager row that
                    // vanished (cleared in the system app, DM
                    // database reset) must fail honestly
                    // instead of spinning at a frozen
                    // percentage and holding a queue slot.
                    if (rec.status == "downloading" || rec.status == "queued" ||
                        rec.status == "paused"
                    ) {
                        noteError(store, rec.id, "Download removed by the system")
                        store.updateDownload(rec.id, "failed", progress = -1)
                        changed = true
                    }
                } else if (it.moveToFirst()) {
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
                    if (newStatus == "failed" && rec.status != "failed") {
                        // Audit D-m3: record WHY the system
                        // downloader failed, like worker
                        // failures do.
                        val reasonIdx = it.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = if (reasonIdx >= 0) it.getInt(reasonIdx) else -1
                        noteError(store, rec.id, when (reason) {
                            DownloadManager.ERROR_FILE_ERROR ->
                                "Download failed: file error"
                            DownloadManager.ERROR_HTTP_DATA_ERROR ->
                                "Download failed: server error"
                            DownloadManager.ERROR_INSUFFICIENT_SPACE ->
                                "Download failed: not enough storage space"
                            DownloadManager.ERROR_CANNOT_RESUME ->
                                "Download failed: cannot resume"
                            DownloadManager.ERROR_DEVICE_NOT_FOUND ->
                                "Download failed: storage not found"
                            DownloadManager.ERROR_TOO_MANY_REDIRECTS ->
                                "Download failed: too many redirects"
                            DownloadManager.ERROR_UNHANDLED_HTTP_CODE ->
                                "Download failed: server rejected the download"
                            else -> "Download failed"
                        })
                    }
                    // Beta.64 #30: size / speed / ETA.
                    noteBytes(store, rec.id, soFar, total)
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

    /** True when the file at [src] starts with a ZIP header
     *  ("PK"). Used by [mimeForDownload]: a ".apk" name alone
     *  never earns the installer MIME type. */
    private fun looksLikeZip(context: Context, src: String): Boolean {
        return runCatching {
            val input = if (src.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(src))
            } else {
                java.io.FileInputStream(File(src))
            } ?: return false
            input.use {
                val buf = ByteArray(2)
                it.read(buf) == 2 &&
                    buf[0] == 0x50.toByte() && buf[1] == 0x4B.toByte()
            }
        }.getOrDefault(false)
    }

    /** MIME for sharing/opening a finished download (audit S-m12):
     *  the extension maps the type, but an extension alone never
     *  upgrades a file to the package-installer type — a ".apk"
     *  name yields the installer MIME only when the file itself
     *  starts with a ZIP header. (No server content type is
     *  recorded on the download record, so the file's own magic
     *  is the content check.) */
    private fun mimeForDownload(context: Context, rec: DownloadRecord, src: String): String {
        val ext = rec.fileName.substringAfterLast('.', "").lowercase()
        val mapped = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(ext)
        if (mapped == "application/vnd.android.package-archive" &&
            !looksLikeZip(context, src)
        ) {
            return "application/octet-stream"
        }
        return mapped ?: "application/octet-stream"
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
        val type = mimeForDownload(context, rec, src)
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
        val type = mimeForDownload(context, rec, src)
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

    /**
     * Delete a finished download's bytes, wherever they live.
     * Returns false only when the file exists and could not be
     * removed (audit F12): callers must not drop the record on
     * a failed wipe, or the bytes would leak invisibly while
     * the record vanishes. An already-gone file counts as
     * success, as does a resolver delete that removes nothing.
     */
    fun deleteLocal(context: Context, store: AppStore, rec: DownloadRecord): Boolean {
        return when {
            rec.contentUri.startsWith("content://") ->
                runCatching {
                    context.contentResolver.delete(Uri.parse(rec.contentUri), null, null)
                }.isSuccess
            rec.contentUri.isNotBlank() -> {
                val f = File(rec.contentUri)
                runCatching { !f.exists() || f.delete() }.getOrDefault(false)
            }
            else -> {
                val f = localFile(context, store, rec)
                runCatching { !f.exists() || f.delete() }.getOrDefault(false)
            }
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
        privates.forEach { rec ->
            runCatching { cancel(appCtx, store, rec) }
            // Audit F12: a failed wipe must not drop the
            // record (the file would leak invisibly), so
            // the removal below is gated on this.
            val wiped = runCatching { deleteLocal(appCtx, store, rec) }
                .getOrDefault(false)
            // Audit D-M9: the shared staging helper covers
            // DASH dirs, track files, join/part files,
            // transcode temps, and app-location segmented
            // staging — everything the old wipe missed.
            runCatching { deleteEngineStaging(appCtx, store, rec) }
            if (wiped) {
                runCatching { clearJobBundle(store, rec.id) }
                store.removeDownload(rec.id)
            }
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
