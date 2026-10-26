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

    /** Cancel a download: DownloadManager entries are removed; HLS joins stop at the next segment. */
    fun cancel(context: Context, store: AppStore, rec: DownloadRecord) {
        cancelledIds.add(rec.id)
        runCatching {
            androidx.work.WorkManager.getInstance(context.applicationContext)
                .cancelAllWorkByTag("hls-${rec.id}")
        }
        runCatching {
            androidx.work.WorkManager.getInstance(context.applicationContext)
                .cancelAllWorkByTag("merge-${rec.id}")
        }
        if (rec.dmId >= 0) {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            runCatching { dm.remove(rec.dmId) }
        }
        store.updateDownload(rec.id, "cancelled", progress = -1)
    }

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
        ioScope.launch {
            val isHls = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ||
                contentType?.contains("mpegurl", ignoreCase = true) == true
            val isDash = url.substringBefore('?').endsWith(".mpd", ignoreCase = true)
            val fileName = if (isHls) {
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
            if (isDash) {
                // DASH downloads (0.0.9996): segments are fetched,
                // assembled per track and remuxed into one MP4 by a
                // WorkManager job, the HLS pattern. Manifests outside
                // the supported shapes fail honestly in the job
                // rather than saving a manifest as if it were a video.
                cancelledIds.remove(rec.id)
                store.updateDownload(rec.id, "downloading", progress = 0)
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
                return@launch
            }
            if (isHls) {
                // A playlist is not a video: download the actual stream
                // segments and join them into one playable file. The
                // join runs as a WorkManager job (Phase D): it survives
                // the process dying, resumes from the segment manifest,
                // and retries transient network failures with backoff.
                cancelledIds.remove(rec.id)
                store.updateDownload(rec.id, "downloading", progress = 0)
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
                return@launch
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
                return@launch
            }
            store.updateDownload(rec.id, "downloading", dmId)
        }
        return id
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
        referer: String?
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
                media = true
            )
            store.addDownload(rec)
            cancelledIds.remove(rec.id)
            store.updateDownload(rec.id, "downloading", progress = 0)
            val data = androidx.work.Data.Builder()
                .putLong("recId", rec.id)
                .putString("videoUrl", videoUrl)
                .putString("audioUrl", audioUrl)
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
        return id
    }

    private fun destFile(context: Context, store: AppStore, fileName: String): File =
        if (store.setting("downloadLocation", "public") == "app") {
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
        val useMediaStore = store.setting("downloadLocation", "public") != "app" &&
            android.os.Build.VERSION.SDK_INT >= 29
        val fileName = rec.fileName
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
            destFile(appCtx, store, fileName)
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
                store.updateDownload(rec.id, "cancelled", progress = -1)
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
                noteError(store, rec.id, result.error)
                store.updateDownload(rec.id, "failed", progress = -1)
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
                    store.updateDownload(recId, "downloading", progress = pct)
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
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
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
        val useMediaStore = store.setting("downloadLocation", "public") != "app" &&
            android.os.Build.VERSION.SDK_INT >= 29
        val fileName = rec.fileName
        val joinDest = if (useMediaStore) {
            File(
                appCtx.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "notscape-staging/$fileName"
            )
        } else {
            destFile(appCtx, store, fileName)
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
                store.updateDownload(rec.id, "cancelled", progress = -1)
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
                noteError(store, rec.id, result.error)
                store.updateDownload(rec.id, "failed", progress = -1)
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
                            store.updateDownload(
                                recId, "downloading", progress = pct
                            )
                        }
                    }
                }
            }
        }
        if (out.exists()) out.delete()
        tmp.renameTo(out)
        store.updateDownload(recId, "downloading", progress = toPct)
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
                if (dest.exists()) dest.delete()
                tmpOut!!.renameTo(dest)
                store.updateDownload(recId, "downloading", progress = 100)
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
                        if (dest.exists()) dest.delete()
                        tmpOut!!.renameTo(dest)
                        store.updateDownload(
                            recId, "downloading", progress = 100
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
        val useMediaStore = store.setting("downloadLocation", "public") != "app" &&
            android.os.Build.VERSION.SDK_INT >= 29
        val fileName = rec.fileName
        val joinDest = if (useMediaStore) {
            File(
                appCtx.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                "notscape-staging/$fileName"
            )
        } else {
            destFile(appCtx, store, fileName)
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
                store.updateDownload(rec.id, "cancelled", progress = -1)
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
                noteError(store, rec.id, result.error)
                store.updateDownload(rec.id, "failed", progress = -1)
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
                    store.updateDownload(
                        recId, "downloading", progress = pct
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
            dest.parentFile?.mkdirs()
            val ok = if (plan.audio == null) {
                if (dest.exists()) dest.delete()
                trackFiles[0].renameTo(dest)
            } else {
                val tmpOut = File(dest.parentFile, dest.name + ".mux")
                val muxed = remuxMp4(
                    trackFiles[0], trackFiles[1], tmpOut
                )
                trackFiles.forEach { it.delete() }
                if (muxed) {
                    if (dest.exists()) dest.delete()
                    tmpOut.renameTo(dest)
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
}
