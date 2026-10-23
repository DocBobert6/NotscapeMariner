package net.notscape.browser.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Bookmark(
    val id: Long, val title: String, val url: String, val createdAt: Long,
    /** Folder name, or "" for unfiled. Folders are just names — the
     *  store keeps the list, so empty folders exist too. */
    val folder: String = ""
)
data class HistoryEntry(val id: Long, val title: String, val url: String, val visitedAt: Long)
data class PlaylistItem(val id: Long, val title: String, val url: String)
data class Playlist(val id: Long, val name: String, val createdAt: Long, val items: MutableList<PlaylistItem>)
data class DownloadRecord(
    val id: Long, val title: String, val url: String, val fileName: String,
    var dmId: Long, var status: String, val addedAt: Long,
    /** True for media downloads (the offline library); plain file/image
     *  downloads go to the Downloads folder and stay out of the library. */
    val media: Boolean = false,
    /** Download progress 0–100, or -1 when unknown. */
    var progress: Int = -1,
    /** Where a finished download actually lives: a MediaStore
     *  content:// URI for public downloads on modern Android, or an
     *  absolute path as a fallback. "" while the legacy location
     *  (setting-based file path) is the only record. */
    var contentUri: String = ""
)
data class BookRecord(
    val id: Long, val title: String, val author: String, val filePath: String,
    val format: String, var chapterIndex: Int, var scrollFraction: Float, val addedAt: Long,
    val coverPath: String = ""
)
data class VaultEntry(val id: Long, val site: String, val username: String, val passwordEnc: String)
data class SiteSetting(val host: String, val javaScript: Boolean = true, val blockImages: Boolean = false, val allowPopups: Boolean = false, val disableBlocking: Boolean = false, val blockPopups: Boolean = false, val allowThirdPartyCookies: Boolean = false)
data class TabSnapshot(val url: String, val title: String)

/**
 * Local-only persistence. Everything the app remembers lives on the
 * device: since Phase F the source of truth is a Room database.
 * The JSON documents were the store until 0.0.91, were kept as a
 * written mirror through the migration releases (0.0.92–0.0.93) for
 * one-release rollback, and are read here only as the backup-import
 * format and the pre-migration recovery source. (If the database is
 * ever lost, the migration re-runs from the last mirrored JSON.)
 * The in-memory collections + lock-free snapshots the UI reads are
 * unchanged — only the persistence engine moved.
 */
class AppStore private constructor(context: Context) {

    private val file = File(context.filesDir, "notscape_data.json")
    private val tmpFile = File(context.filesDir, "notscape_data.json.tmp")
    private val bakFile = File(context.filesDir, "notscape_data.json.bak")
    private val historyFile = File(context.filesDir, "notscape_history.json")
    private val historyTmp = File(context.filesDir, "notscape_history.json.tmp")
    private val historyBak = File(context.filesDir, "notscape_history.json.bak")
    private val runtimeFile = File(context.filesDir, "notscape_runtime.json")
    private val runtimeTmp = File(context.filesDir, "notscape_runtime.json.tmp")
    private val runtimeBak = File(context.filesDir, "notscape_runtime.json.bak")
    private val posTitlesFile = File(context.filesDir, "notscape_postitles.json")
    private val posTitlesTmp = File(context.filesDir, "notscape_postitles.json.tmp")
    private val posTitlesBak = File(context.filesDir, "notscape_postitles.json.bak")
    private val lock = Any()

    /** Phase D.0: disk writes happen OFF the store lock. saveLocked()
     *  serializes under the lock (fast, no I/O) and hands the text to
     *  this single writer thread; a generation counter coalesces
     *  bursts — only the newest pending document is written. */
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "notscape-store-writer").apply { isDaemon = true }
    }
    @Volatile private var mainGen = 0L
    @Volatile private var historyGen = 0L
    @Volatile private var runtimeGen = 0L

    /** Lock-free site-settings view (same pattern as settingsSnapshot):
     *  request interception reads per-site settings for every resource. */
    @Volatile private var siteSettingsSnapshot: Map<String, SiteSetting> = emptyMap()

    private fun publishSiteSettingsSnapshot() {
        siteSettingsSnapshot = HashMap(siteSettings)
    }

    var revision by mutableStateOf(0)
        private set

    val bookmarks = mutableListOf<Bookmark>()
    val bookmarkFolders = mutableListOf<String>()
    val history = mutableListOf<HistoryEntry>()
    val playlists = mutableListOf<Playlist>()
    val downloads = mutableListOf<DownloadRecord>()
    val books = mutableListOf<BookRecord>()
    val vault = mutableListOf<VaultEntry>()
    val siteSettings = mutableMapOf<String, SiteSetting>()
    val favourites = mutableListOf<Bookmark>()
    val settings = mutableMapOf<String, String>()
    val tabSession = mutableListOf<TabSnapshot>()
    var tabSessionActive: Int = 0
    val playbackPositions = mutableMapOf<String, Long>()
    /** The title each saved position was playing under (0.0.953),
     *  so Continue cards can name the video, not just its host. */
    val playbackTitles = mutableMapOf<String, String>()

    init {
        // All disk work leaves the calling thread (0.0.988):
        // the legacy JSON document and the Room database
        // load on the writer, in order, and `ready` flips
        // when the store holds the full truth. Screens
        // compose only after ready; background callers that
        // need data call awaitReady() first. This is what
        // lets the database drop allowMainThreadQueries.
        writer.execute {
            try {
                load()
                initRoomLocked(context)
            } finally {
                revision++
                ready = true
                readyLatch.countDown()
            }
        }
    }

    companion object {
        @Volatile private var instance: AppStore? = null
        fun get(context: Context): AppStore =
            instance ?: synchronized(this) {
                instance ?: AppStore(context.applicationContext).also { instance = it }
            }
    }

    /** Lock-free settings view for hot paths: request interception
     *  reads settings for every resource, and must never wait behind
     *  a save's disk write for the store lock. Refreshed (as an
     *  immutable copy) whenever the settings map changes. */
    @Volatile private var settingsSnapshot: Map<String, String> = emptyMap()

    private fun publishSettingsSnapshot() {
        settingsSnapshot = HashMap(settings)
    }

    fun setting(key: String, default: String): String =
        settingsSnapshot[key] ?: default

    fun putSetting(key: String, value: String) {
        synchronized(lock) {
            settings[key] = value
            publishSettingsSnapshot()
            saveLocked()
        }
    }

    // ---------- bookmarks / history / favourites ----------
    fun isBookmarked(url: String): Boolean =
        synchronized(lock) { bookmarks.any { it.url == url } }

    fun toggleBookmark(title: String, url: String) {
        synchronized(lock) {
            val existing = bookmarks.firstOrNull { it.url == url }
            if (existing != null) bookmarks.remove(existing)
            else bookmarks.add(0, Bookmark(newId(), title.ifBlank { url }, url, System.currentTimeMillis()))
            saveLocked()
        }
    }

    fun removeBookmark(id: Long) {
        synchronized(lock) { bookmarks.removeAll { it.id == id }; saveLocked() }
    }

    // ---------- bookmark folders ----------
    fun addBookmarkFolder(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        synchronized(lock) {
            if (bookmarkFolders.none { it.equals(n, ignoreCase = true) }) {
                bookmarkFolders.add(n)
                saveLocked()
            }
        }
    }

    fun renameBookmarkFolder(old: String, newName: String) {
        val n = newName.trim()
        if (n.isEmpty()) return
        synchronized(lock) {
            val idx = bookmarkFolders.indexOfFirst { it.equals(old, ignoreCase = true) }
            if (idx < 0) return@synchronized
            if (bookmarkFolders.any { it.equals(n, ignoreCase = true) && it != bookmarkFolders[idx] }) {
                return@synchronized
            }
            bookmarkFolders[idx] = n
            for (i in bookmarks.indices) {
                if (bookmarks[i].folder.equals(old, ignoreCase = true)) {
                    bookmarks[i] = bookmarks[i].copy(folder = n)
                }
            }
            saveLocked()
        }
    }

    fun deleteBookmarkFolder(name: String) {
        synchronized(lock) {
            bookmarkFolders.removeAll { it.equals(name, ignoreCase = true) }
            for (i in bookmarks.indices) {
                if (bookmarks[i].folder.equals(name, ignoreCase = true)) {
                    bookmarks[i] = bookmarks[i].copy(folder = "")
                }
            }
            saveLocked()
        }
    }

    fun setBookmarkFolder(id: Long, folder: String) {
        synchronized(lock) {
            val i = bookmarks.indexOfFirst { it.id == id }
            if (i >= 0) {
                bookmarks[i] = bookmarks[i].copy(folder = folder)
                saveLocked()
            }
        }
    }

    fun addHistory(title: String, url: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        synchronized(lock) {
            history.removeAll { it.url == url }
            history.add(0, HistoryEntry(newId(), title.ifBlank { url }, url, System.currentTimeMillis()))
            while (history.size > 500) history.removeAt(history.size - 1)
            saveHistoryLocked()
        }
    }

    fun clearHistory() {
        synchronized(lock) { history.clear(); saveHistoryLocked() }
    }

    fun toggleFavourite(title: String, url: String) {
        synchronized(lock) {
            val existing = favourites.firstOrNull { it.url == url }
            if (existing != null) favourites.remove(existing)
            else favourites.add(Bookmark(newId(), title.ifBlank { url }, url, System.currentTimeMillis()))
            saveLocked()
        }
    }

    fun isFavourite(url: String): Boolean =
        synchronized(lock) { favourites.any { it.url == url } }

    /** A point-in-time copy of the downloads list, safe to read from any thread. */
    fun downloadsSnapshot(): List<DownloadRecord> = synchronized(lock) { downloads.toList() }

    fun playbackPosition(url: String?): Long =
        synchronized(lock) { playbackPositions[url] ?: 0L }

    // ---------- playlists ----------
    fun createPlaylist(name: String): Playlist {
        val p = Playlist(newId(), name, System.currentTimeMillis(), mutableListOf())
        synchronized(lock) { playlists.add(p); saveLocked() }
        return p
    }

    fun deletePlaylist(id: Long) {
        synchronized(lock) { playlists.removeAll { it.id == id }; saveLocked() }
    }

    fun addToPlaylist(playlistId: Long, title: String, url: String) {
        synchronized(lock) {
            val p = playlists.firstOrNull { it.id == playlistId } ?: return
            if (p.items.none { it.url == url }) {
                p.items.add(PlaylistItem(newId(), title.ifBlank { url }, url))
            }
            saveLocked()
        }
    }

    fun removeFromPlaylist(playlistId: Long, itemId: Long) {
        synchronized(lock) {
            playlists.firstOrNull { it.id == playlistId }?.items?.removeAll { it.id == itemId }
            saveLocked()
        }
    }

    // ---------- downloads ----------
    fun addDownload(rec: DownloadRecord) {
        synchronized(lock) { downloads.add(0, rec); saveLocked() }
    }

    fun updateDownload(id: Long, status: String, dmId: Long? = null, progress: Int? = null) {
        synchronized(lock) {
            var structural = false
            downloads.firstOrNull { it.id == id }?.let {
                if (it.status != status) structural = true
                if (dmId != null && it.dmId != dmId) structural = true
                it.status = status
                if (dmId != null) it.dmId = dmId
                if (progress != null) it.progress = progress
            }
            // Phase D.0: progress-only updates are the highest-churn
            // writes in the app (polled during every download). They
            // mutate memory and ride along on the next structural
            // save instead of rewriting the whole store each poll.
            if (structural) saveLocked()
        }
    }

    fun removeDownload(id: Long) {
        synchronized(lock) { downloads.removeAll { it.id == id }; saveLocked() }
    }

    /** Rename a download's file (e.g. a CMAF join that turned out
     *  to be an MP4, not the .ts the record was created with). */
    fun updateDownloadFileName(id: Long, name: String) {
        synchronized(lock) {
            val i = downloads.indexOfFirst { it.id == id }
            if (i >= 0) {
                val r = downloads[i]
                downloads[i] = DownloadRecord(
                    r.id, r.title, r.url, name, r.dmId, r.status, r.addedAt,
                    r.media, r.progress, r.contentUri
                )
                saveLocked()
            }
        }
    }

    fun updateDownloadUri(id: Long, uri: String) {
        synchronized(lock) {
            downloads.firstOrNull { it.id == id }?.let { it.contentUri = uri }
            saveLocked()
        }
    }

    // ---------- books ----------
    fun addBook(book: BookRecord) {
        synchronized(lock) { books.add(0, book); saveLocked() }
    }

    fun updateBookProgress(id: Long, chapterIndex: Int, scrollFraction: Float) {
        synchronized(lock) {
            books.firstOrNull { it.id == id }?.let {
                it.chapterIndex = chapterIndex
                it.scrollFraction = scrollFraction
            }
            saveLocked()
        }
    }

    fun removeBook(id: Long) {
        synchronized(lock) {
            val b = books.firstOrNull { it.id == id }
            if (b != null) {
                runCatching { File(b.filePath).delete() }
                books.remove(b)
            }
            saveLocked()
        }
    }

    // ---------- vault ----------
    fun addVault(entry: VaultEntry) {
        synchronized(lock) { vault.add(0, entry); saveLocked() }
    }

    fun removeVault(id: Long) {
        synchronized(lock) { vault.removeAll { it.id == id }; saveLocked() }
    }

    /** Swap in re-encrypted passwords (vault key migration), one save. */
    fun replaceVaultPasswords(newEnc: Map<Long, String>) {
        synchronized(lock) {
            for (i in vault.indices) {
                val e = vault[i]
                newEnc[e.id]?.let { vault[i] = e.copy(passwordEnc = it) }
            }
            saveLocked()
        }
    }

    // ---------- site settings ----------
    fun siteSetting(host: String): SiteSetting =
        siteSettingsSnapshot[host] ?: SiteSetting(host)

    fun putSiteSetting(setting: SiteSetting) {
        synchronized(lock) {
            siteSettings[setting.host] = setting
            publishSiteSettingsSnapshot()
            saveLocked()
        }
    }

    /** Repoint a book at a new source file (the MOBI migration). */
    fun updateBookSource(id: Long, filePath: String, format: String) {
        synchronized(lock) {
            val idx = books.indexOfFirst { it.id == id }
            if (idx >= 0) {
                books[idx] = books[idx].copy(
                    filePath = filePath, format = format,
                    chapterIndex = 0, scrollFraction = 0f
                )
                saveLocked()
            }
        }
    }

    fun clearAllData() {
        synchronized(lock) {
            bookmarks.clear(); bookmarkFolders.clear(); history.clear(); playlists.clear()
            downloads.clear(); vault.clear(); siteSettings.clear(); favourites.clear()
            publishSiteSettingsSnapshot()
            tabSession.clear(); playbackPositions.clear(); playbackTitles.clear()
            saveLocked(); saveHistoryLocked(); saveRuntimeLocked()
        }
    }

    // ---------- session / playback ----------
    fun saveSession(tabs: List<TabSnapshot>, active: Int) {
        synchronized(lock) {
            tabSession.clear(); tabSession.addAll(tabs)
            tabSessionActive = active
            saveLocked()
        }
    }

    fun setPlaybackPosition(url: String, positionMs: Long, title: String = "") {
        synchronized(lock) {
            if (positionMs > 5_000) {
                playbackPositions[url] = positionMs
                // The page title the player was launched with —
                // the Continue card's best name for this stream
                // (0.0.953: cards fell back to the raw host
                // whenever the stream URL matched nothing else
                // on record).
                if (title.isNotBlank()) playbackTitles[url] = title
            } else {
                playbackPositions.remove(url)
                playbackTitles.remove(url)
            }
            saveRuntimeLocked()
        }
    }

    fun clearPlaybackPosition(url: String) {
        synchronized(lock) { playbackPositions.remove(url); playbackTitles.remove(url); saveRuntimeLocked() }
    }

    // ---------- backup ----------
    /** Full data backup as JSON. The vault is NOT included — it has its own
     *  passphrase-protected export because its keys are device-bound. */
    fun exportBackup(): String {
        synchronized(lock) {
            val root = JSONObject(buildJsonLocked())
            // History and playback positions live in sidecar files now;
            // include them so a backup stays complete.
            root.put("positions", JSONObject().apply {
                playbackPositions.forEach { (k, v) -> put(k, v) }
            })
            root.put("positionTitles", JSONObject().apply {
                playbackTitles.forEach { (k, v) -> put(k, v) }
            })
            root.remove("vault")
            return root.toString(2)
        }
    }

    /** Restores a backup produced by [exportBackup]. The on-device vault is kept. */
    fun importBackup(json: String): Boolean {
        synchronized(lock) {
            return runCatching {
                val root = JSONObject(json)
                require(root.has("bookmarks") || root.has("history") || root.has("playlists"))
                // Preserve the device vault (its encryption keys live on this device).
                root.put("vault", JSONArray().apply {
                    vault.forEach { put(JSONObject().put("id", it.id).put("site", it.site).put("user", it.username).put("enc", it.passwordEnc)) }
                })
                writeAtomic(root.toString(), file, tmpFile, bakFile)
                loadLocked()
                // Backups carry history/positions (possibly inside the
                // main document from older versions): restore them into
                // the sidecars.
                root.optJSONArray("history")?.let { arr ->
                    history.clear()
                    for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                        history.add(HistoryEntry(it.getLong("id"), it.getString("title"), it.getString("url"), it.getLong("t")))
                    }
                    saveHistoryLocked()
                }
                root.optJSONObject("positions")?.let { obj ->
                    playbackPositions.clear()
                    val keys = obj.keys()
                    while (keys.hasNext()) { val k = keys.next(); playbackPositions[k] = obj.optLong(k, 0) }
                    saveRuntimeLocked()
                }
                root.optJSONObject("positionTitles")?.let { obj ->
                    playbackTitles.clear()
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        obj.optString(k).takeIf { it.isNotBlank() }
                            ?.let { v -> playbackTitles[k] = v }
                    }
                    saveRuntimeLocked()
                }
                persistAllToRoomLocked()
                revision++
                true
            }.getOrDefault(false)
        }
    }

    // ---------- persistence ----------
    private fun newId(): Long = System.currentTimeMillis() * 1000 + (0..999).random()

    private fun load() {
        synchronized(lock) { loadLocked() }
    }

    /**
     * Crash-safe save: write the new document to a temp file, keep the
     * previous good document as .bak, then rename the temp over the real
     * file. A crash mid-write can no longer corrupt the only copy.
     */
    private fun writeAtomic(text: String, target: File, tmp: File, bak: File) {
        tmp.writeText(text)
        if (target.exists()) {
            bak.delete()
            target.renameTo(bak)
        }
        if (!tmp.renameTo(target)) {
            // Rename failed (shouldn't on the same filesystem): fall back to
            // a direct copy rather than losing the save.
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun loadLocked() {
        if (!file.exists() && !bakFile.exists()) {
            seedDefaultsLocked()
            return
        }
        // If the main file is missing or corrupt (e.g. a crash during an
        // older direct write), fall back to the .bak copy.
        val text = StoreJson.pickReadable(
            runCatching { file.readText() }.getOrNull(),
            runCatching { bakFile.readText() }.getOrNull()
        )
        val doc = text?.let { StoreJson.parseMainDocument(it) }
        if (doc == null) {
            seedDefaultsLocked()
            return
        }
        applyDocumentLocked(doc)
        publishSettingsSnapshot()
        // ---- sidecars (Phase D.0) ----
        history.clear()
        val histText = StoreJson.pickReadable(
            runCatching { historyFile.readText() }.getOrNull(),
            runCatching { historyBak.readText() }.getOrNull()
        )
        val parsedHistory = histText?.let { StoreJson.parseHistory(it) }
        if (parsedHistory != null) {
            history.addAll(parsedHistory)
        } else {
            // The copy inside older main documents is the migration source.
            doc.legacyHistory?.let { history.addAll(it) }
        }
        playbackPositions.clear()
        val runtimeText = StoreJson.pickReadable(
            runCatching { runtimeFile.readText() }.getOrNull(),
            runCatching { runtimeBak.readText() }.getOrNull()
        )
        val parsedPositions = runtimeText?.let { StoreJson.parsePositions(it) }
        if (parsedPositions != null) {
            playbackPositions.putAll(parsedPositions)
        } else {
            doc.legacyPositions?.let { playbackPositions.putAll(it) }
        }
        playbackTitles.clear()
        val titlesText = StoreJson.pickReadable(
            runCatching { posTitlesFile.readText() }.getOrNull(),
            runCatching { posTitlesBak.readText() }.getOrNull()
        )
        if (titlesText != null) {
            runCatching {
                val obj = JSONObject(titlesText)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    obj.optString(k).takeIf { it.isNotBlank() }
                        ?.let { playbackTitles[k] = it }
                }
            }
        }
        publishSiteSettingsSnapshot()
    }

    /** Swap a parsed document into the in-memory collections. */
    private fun applyDocumentLocked(doc: MainDocument) {
        bookmarks.clear(); bookmarks.addAll(doc.bookmarks)
        bookmarkFolders.clear(); bookmarkFolders.addAll(doc.bookmarkFolders)
        favourites.clear(); favourites.addAll(doc.favourites)
        playlists.clear(); playlists.addAll(doc.playlists)
        downloads.clear(); downloads.addAll(doc.downloads)
        books.clear(); books.addAll(doc.books)
        vault.clear(); vault.addAll(doc.vault)
        siteSettings.clear(); siteSettings.putAll(doc.siteSettings)
        settings.clear(); settings.putAll(doc.settings)
        tabSession.clear(); tabSession.addAll(doc.tabSession)
        tabSessionActive = doc.tabSessionActive
    }

    // ---------- Room persistence (Phase F) ----------
    private var db: AppDatabase? = null

    /** True once the Room step at construction has finished; saves
     *  before that (the seed save during load) must not write Room. */
    @Volatile private var roomReady = false

    /** True once the initial load (JSON + Room) has finished
     * and the in-memory state is the full truth (0.0.988). */
    @Volatile
    var ready: Boolean = false
        private set
    private val readyLatch = java.util.concurrent.CountDownLatch(1)

    /** Block until the initial load has finished. For
     * background threads only - the UI composes after ready
     * instead of blocking on it. */
    fun awaitReady(timeoutMs: Long = 15_000) {
        runCatching {
            readyLatch.await(
                timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS
            )
        }
    }

    /** Immutable snapshot of the main collections as entities.
     *  Callers hold the lock (or are still constructing the store). */
    private fun mainSliceLocked(): StoreMigration.MainSlice = StoreMigration.mainSlice(
        bookmarks.toList(), bookmarkFolders.toList(), favourites.toList(),
        playlists.map { Playlist(it.id, it.name, it.createdAt, it.items.toMutableList()) },
        downloads.map { it.copy() }, books.map { it.copy() }, vault.toList(),
        HashMap(siteSettings), HashMap(settings), tabSession.toList(), tabSessionActive
    )

    private fun replaceMainInDb(dao: StoreDao, slice: StoreMigration.MainSlice) {
        dao.clearBookmarks(); dao.insertBookmarks(slice.bookmarks)
        dao.clearFolders(); dao.insertFolders(slice.folders)
        dao.clearFavourites(); dao.insertFavourites(slice.favourites)
        dao.clearPlaylists(); dao.insertPlaylists(slice.playlists)
        dao.clearPlaylistItems(); dao.insertPlaylistItems(slice.playlistItems)
        dao.clearDownloads(); dao.insertDownloads(slice.downloads)
        dao.clearBooks(); dao.insertBooks(slice.books)
        dao.clearVault(); dao.insertVault(slice.vault)
        dao.clearSiteSettings(); dao.insertSiteSettings(slice.siteSettings)
        dao.clearSettings(); dao.insertSettings(slice.settings)
        dao.putMeta(MetaEntity("session", slice.sessionJson))
    }

    /**
     * The Phase F switch-over, run once at construction after the
     * JSON load has filled memory: if Room has never been populated,
     * import the in-memory (JSON) state, verify the copy by counts,
     * and mark it migrated. From then on Room is the source of truth
     * and memory is refilled from it. The import is idempotent — if
     * verification fails, the flag stays unset and the next launch
     * retries from the (untouched) JSON state.
     */
    private fun initRoomLocked(context: Context) {
        runCatching {
            val database = AppDatabase.create(context)
            db = database
            val dao = database.storeDao()
            if (dao.meta("migrated") != "1") {
                val main = mainSliceLocked()
                val hist = StoreMigration.historySlice(history.toList())
                val pos = StoreMigration.positionsSlice(HashMap(playbackPositions))
                database.runInTransaction {
                    replaceMainInDb(dao, main)
                    dao.clearHistory(); dao.insertHistory(hist)
                    dao.clearPositions(); dao.insertPositions(pos)
                }
                val verified = dao.bookmarks().size == bookmarks.size &&
                    dao.history().size == history.size &&
                    dao.books().size == books.size &&
                    dao.downloads().size == downloads.size &&
                    dao.vault().size == vault.size &&
                    dao.playlists().size == playlists.size
                if (verified) {
                    dao.putMeta(MetaEntity("migrated", "1"))
                }
            }
            if (dao.meta("migrated") == "1") {
                loadFromRoomLocked(dao)
            }
            roomReady = true
        }
    }

    /** Refill the in-memory collections from Room (post-migration truth). */
    private fun loadFromRoomLocked(dao: StoreDao) {
        val state = StoreMigration.toModel(
            dao.bookmarks(), dao.folders(), dao.favourites(), dao.history(),
            dao.playlists(), dao.playlistItems(), dao.downloads(), dao.books(),
            dao.vault(), dao.siteSettings(), dao.settings(), dao.positions(),
            dao.meta("session")
        )
        bookmarks.clear(); bookmarks.addAll(state.bookmarks)
        bookmarkFolders.clear(); bookmarkFolders.addAll(state.bookmarkFolders)
        favourites.clear(); favourites.addAll(state.favourites)
        history.clear(); history.addAll(state.history)
        playlists.clear(); playlists.addAll(state.playlists)
        downloads.clear(); downloads.addAll(state.downloads)
        books.clear(); books.addAll(state.books)
        vault.clear(); vault.addAll(state.vault)
        siteSettings.clear(); siteSettings.putAll(state.siteSettings)
        settings.clear(); settings.putAll(state.settings)
        tabSession.clear(); tabSession.addAll(state.tabSession)
        tabSessionActive = state.tabSessionActive
        playbackPositions.clear(); playbackPositions.putAll(state.playbackPositions)
        publishSettingsSnapshot()
        publishSiteSettingsSnapshot()
    }

    /** Write the complete in-memory state to Room now (backup import). */
    private fun persistAllToRoomLocked() {
        val database = db ?: return
        if (!roomReady) return
        runCatching {
            val dao = database.storeDao()
            val main = mainSliceLocked()
            val hist = StoreMigration.historySlice(history.toList())
            val pos = StoreMigration.positionsSlice(HashMap(playbackPositions))
            database.runInTransaction {
                replaceMainInDb(dao, main)
                dao.clearHistory(); dao.insertHistory(hist)
                dao.clearPositions(); dao.insertPositions(pos)
            }
        }
    }

    private fun seedDefaultsLocked() {
        // Phase G (Robert's direction): NO seeded favorites — the
        // list belongs to the user from the first launch. (This used
        // to plant Wikipedia / Gutenberg / Archive / Open Library.)
        saveLocked()
    }

    /** Serialize the main document. Caller holds the lock; no I/O here. */
    private fun buildJsonLocked(): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
            root.put("bookmarks", JSONArray().apply {
                bookmarks.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("url", it.url).put("t", it.createdAt).put("f", it.folder)) }
            })
            root.put("bookmarkFolders", JSONArray().apply {
                bookmarkFolders.forEach { put(it) }
            })
            root.put("favourites", JSONArray().apply {
                favourites.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("url", it.url).put("t", it.createdAt)) }
            })
            root.put("history", JSONArray().apply {
                history.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("url", it.url).put("t", it.visitedAt)) }
            })
            root.put("playlists", JSONArray().apply {
                playlists.forEach { p ->
                    put(JSONObject().put("id", p.id).put("name", p.name).put("t", p.createdAt)
                        .put("items", JSONArray().apply {
                            p.items.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("url", it.url)) }
                        }))
                }
            })
            root.put("downloads", JSONArray().apply {
                downloads.forEach {
                    put(JSONObject().put("id", it.id).put("title", it.title).put("url", it.url)
                        .put("fileName", it.fileName).put("dmId", it.dmId).put("status", it.status).put("t", it.addedAt)
                        .put("m", it.media).put("p", it.progress).put("uri", it.contentUri))
                }
            })
            root.put("books", JSONArray().apply {
                books.forEach {
                    put(JSONObject().put("id", it.id).put("title", it.title).put("author", it.author)
                        .put("path", it.filePath).put("format", it.format)
                        .put("chapter", it.chapterIndex).put("frac", it.scrollFraction.toDouble()).put("t", it.addedAt)
                        .put("cv", it.coverPath))
                }
            })
            root.put("vault", JSONArray().apply {
                vault.forEach { put(JSONObject().put("id", it.id).put("site", it.site).put("user", it.username).put("enc", it.passwordEnc)) }
            })
            root.put("siteSettings", JSONObject().apply {
                siteSettings.forEach { (host, s) -> put(host, JSONObject().put("js", s.javaScript).put("img", s.blockImages).put("pop", s.allowPopups).put("nb", s.disableBlocking).put("bp", s.blockPopups).put("c3", s.allowThirdPartyCookies)) }
            })
            root.put("settings", JSONObject().apply {
                settings.forEach { (k, v) -> put(k, v) }
            })
            root.put("session", JSONObject().put("active", tabSessionActive)
                .put("tabs", JSONArray().apply {
                    tabSession.forEach { put(JSONObject().put("url", it.url).put("title", it.title)) }
                }))
            return root.toString()
    }

    private fun saveLocked() {
        val gen = ++mainGen
        val slice = if (roomReady) {
            runCatching { mainSliceLocked() }.getOrNull()
        } else {
            null
        }
        val database = db
        writer.execute {
            // Coalesce bursts: a newer save supersedes this one.
            if (gen == mainGen && slice != null && database != null) {
                runCatching {
                    database.runInTransaction {
                        replaceMainInDb(database.storeDao(), slice)
                    }
                }
            }
        }
        revision++
    }

    private fun saveHistoryLocked() {
        val gen = ++historyGen
        val slice = if (roomReady) StoreMigration.historySlice(history.toList()) else null
        val database = db
        writer.execute {
            if (gen == historyGen && slice != null && database != null) {
                runCatching {
                    database.runInTransaction {
                        val dao = database.storeDao()
                        dao.clearHistory(); dao.insertHistory(slice)
                    }
                }
            }
        }
        revision++
    }

    private fun saveRuntimeLocked() {
        val gen = ++runtimeGen
        val slice = if (roomReady) {
            StoreMigration.positionsSlice(HashMap(playbackPositions))
        } else {
            null
        }
        val titleSlice = HashMap(playbackTitles)
        val database = db
        writer.execute {
            if (gen == runtimeGen) {
                // Continue-card titles ride in their own sidecar;
                // the positions themselves live in Room.
                runCatching {
                    val json = JSONObject().apply {
                        titleSlice.forEach { (k, v) -> put(k, v) }
                    }.toString()
                    writeAtomic(json, posTitlesFile, posTitlesTmp, posTitlesBak)
                }
            }
            if (gen == runtimeGen && slice != null && database != null) {
                runCatching {
                    database.runInTransaction {
                        val dao = database.storeDao()
                        dao.clearPositions(); dao.insertPositions(slice)
                    }
                }
            }
        }
        revision++
    }
}
