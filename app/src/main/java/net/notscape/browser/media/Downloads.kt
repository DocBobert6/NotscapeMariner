package net.notscape.browser.media

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import android.os.Environment
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import net.notscape.browser.data.AppStore
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
                // DASH segments aren't joined by the downloader; the stream
                // plays in the built-in player instead. Fail honestly
                // rather than saving the manifest as if it were a video.
                store.updateDownload(rec.id, "failed")
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

    enum class HlsOutcome { OK, FAILED, RETRY, UNSUPPORTED }

    /**
     * Execute one HLS download job (the WorkManager worker's unit of
     * work; also its retry unit). Owns the record's status
     * transitions except RETRY, which leaves it "downloading" for
     * the next attempt.
     */
    fun runHlsJob(context: Context, store: AppStore, rec: DownloadRecord, referer: String?): HlsOutcome {
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
        val (outcome, isMp4) = downloadHls(appCtx, store, rec.id, rec.url, referer, joinDest)
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
                store.updateDownload(rec.id, "complete", progress = 100)
                HlsOutcome.OK
            }
            outcome == HlsOutcome.RETRY -> HlsOutcome.RETRY
            else -> {
                store.updateDownload(rec.id, "failed", progress = -1)
                outcome
            }
        }
    }

    private fun fetchText(url: String, referer: String?): String =
        openGet(url, referer).inputStream.bufferedReader().readText()

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
        dest: File
    ): Pair<HlsOutcome, Boolean> {
        return try {
            var url = playlistUrl
            var text = fetchText(url, referer)
            var hops = 0
            var separateAudio = false
            while (HlsPlaylist.isMaster(text) && hops < 2) {
                val master = HlsPlaylist.parseMaster(text, url)
                val best = master.bestVariant() ?: return HlsOutcome.FAILED to false
                if (master.variantHasSeparateAudio(best)) separateAudio = true
                url = best.url
                text = fetchText(url, referer)
                hops++
            }
            if (separateAudio) return HlsOutcome.UNSUPPORTED to false
            val plan = HlsPlaylist.parseMedia(text, url)
            if (plan.unsupportedKeyMethod != null) return HlsOutcome.UNSUPPORTED to false
            if (plan.segments.isEmpty()) return HlsOutcome.FAILED to false

            val segDir = File(dest.parentFile, dest.name + ".segments")
            segDir.mkdirs()
            val keyCache = mutableMapOf<String, ByteArray>()
            fun keyBytes(key: HlsPlaylist.Key): ByteArray? {
                val uri = key.uri ?: return null
                return keyCache.getOrPut(uri) {
                    openGet(uri, referer).inputStream.use { it.readBytes() }
                }
            }
            plan.segments.forEachIndexed { idx, seg ->
                if (cancelledIds.contains(recId)) throw InterruptedException("cancelled")
                val segFile = File(segDir, "seg-%05d.bin".format(idx))
                if (!segFile.exists()) {
                    val raw = openGetRange(seg.url, referer, seg.range).inputStream.use { it.readBytes() }
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
                if (pct % 5 == 0 || idx == plan.segments.lastIndex) {
                    store.updateDownload(recId, "downloading", progress = pct)
                }
            }
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, dest.name + ".part")
            tmp.outputStream().use { out ->
                if (plan.initUrl != null) {
                    openGetRange(plan.initUrl, referer, plan.initRange).inputStream.use { it.copyTo(out) }
                }
                plan.segments.indices.forEach { idx ->
                    File(segDir, "seg-%05d.bin".format(idx)).inputStream().use { it.copyTo(out) }
                }
            }
            if (cancelledIds.contains(recId)) {
                tmp.delete()
                return HlsOutcome.FAILED to false
            }
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
            segDir.deleteRecursively()
            HlsOutcome.OK to plan.isCmaf
        } catch (e: InterruptedException) {
            HlsOutcome.FAILED to false
        } catch (e: Exception) {
            // Network-class failure: the worker retries with backoff,
            // and the staged segments make the retry a resume.
            HlsOutcome.RETRY to false
        }
    }

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
        if (!rec.fileName.lowercase().endsWith(".epub")) return
        val destName = "autoshelf-${rec.id}.epub"
        if (store.books.any { it.filePath.endsWith(destName) }) return
        ioScope.launch {
            val main = android.os.Handler(android.os.Looper.getMainLooper())
            runCatching {
                val fresh = store.downloadsSnapshot().firstOrNull { it.id == rec.id } ?: rec
                val src = localSource(context, store, fresh)
                    ?: error("download not readable")
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
