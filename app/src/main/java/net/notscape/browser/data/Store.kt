package net.notscape.browser.data

import android.content.Context
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.notscape.browser.vault.VaultCollections
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
    var contentUri: String = "",
    /** True when the download started from a private tab: the
     *  record and its file are wiped with the private profile
     *  and never shown in the Downloads or Offline lists. */
    val isPrivate: Boolean = false
) {
    /** Beta.64 #30: transient size/speed detail, in the
     *  class body so it stays out of equals, copy and
     *  the persisted document. -1 means unknown. */
    var bytesDownloaded: Long = -1
    var totalBytes: Long = -1
    var speedBps: Long = -1
}
data class BookRecord(
    val id: Long, val title: String, val author: String, val filePath: String,
    val format: String, var chapterIndex: Int, var scrollFraction: Float, val addedAt: Long,
    val coverPath: String = "",
    // Beta.65 #33: chapter + character totals, so the
    // shelf can show progress without parsing. 0 means
    // unknown (older books, until opened once).
    var totalChapters: Int = 0, var totalChars: Int = 0
)
data class VaultEntry(val id: Long, val site: String, val username: String, val passwordEnc: String)
/** Beta.68 #4: one address profile — label plaintext, whole
 *  field set one encrypted blob (FormFillData.AddressData). */
data class AddressEntry(val id: Long, val label: String, val payloadEnc: String)
/** Beta.68 #4: one payment card — label + last 4 + brand
 *  plaintext, full number only inside the encrypted blob. */
data class CardEntry(val id: Long, val label: String, val last4: String, val brand: String, val payloadEnc: String)
data class SiteSetting(val host: String, val javaScript: Boolean = true, val blockImages: Boolean = false, val allowPopups: Boolean = false, val disableBlocking: Boolean = false, val blockPopups: Boolean = false, val allowThirdPartyCookies: Boolean = false)

/** Per-site HTTPS-only exceptions (beta.63 #24), stored as one
 *  comma-separated setting ("httpsExceptions") of lowercase
 *  hosts — the same pattern as the vault's never-save list.
 *  Pure helpers so the list math is unit-testable. */
const val HTTPS_EXCEPTIONS_KEY = "httpsExceptions"

fun parseHttpsExceptions(raw: String): Set<String> =
    raw.split(",").map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }.toSet()

fun withHttpsException(raw: String, host: String): String =
    (parseHttpsExceptions(raw) + host.trim().lowercase())
        .sorted().joinToString(",")

fun withoutHttpsException(raw: String, host: String): String =
    (parseHttpsExceptions(raw) - host.trim().lowercase())
        .sorted().joinToString(",")

fun isHttpsException(raw: String, host: String): Boolean =
    host.trim().lowercase() in parseHttpsExceptions(raw)

/** Beta.64 #18: bookmark folders are paths — segments
 *  joined with "/". A flat name is a top-level path,
 *  so existing folders need no migration: they already
 *  are valid paths. Pure helpers so the path math is
 *  unit-testable. */

/** Parent path of [path] ("" for a top-level folder). */
fun folderParent(path: String): String =
    path.substringBeforeLast("/", "")

/** Last segment of [path]. */
fun folderName(path: String): String =
    path.substringAfterLast("/")

/** Child path of [parent] + [name]; top level when
 *  [parent] is "". */
fun folderChild(parent: String, name: String): String =
    if (parent.isBlank()) name.trim()
    else "$parent/${name.trim()}"

/** True when [name] is a usable single folder segment:
 *  non-blank and free of the path separator. */
fun isFolderSegment(name: String): Boolean =
    name.isNotBlank() && "/" !in name

/** The folders that must exist for [path] to exist:
 *  the path itself plus every ancestor. */
fun folderAncestors(path: String): List<String> {
    val segs = path.split("/").filter { it.isNotBlank() }
    return (1..segs.size).map { segs.take(it).joinToString("/") }
}

/** Direct children of [parent], as full paths, sorted. */
fun folderChildren(folders: List<String>, parent: String): List<String> =
    folders.filter { folderParent(it) == parent }
        .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }

/** Rename folder [old] (and every folder below it) to
 *  [newPath]: descendants are re-rooted under it. */
fun renameFolderPath(
    folders: List<String>, old: String, newPath: String
): List<String> =
    folders.map {
        when {
            it == old -> newPath
            it.startsWith("$old/") -> newPath + it.removePrefix(old)
            else -> it
        }
    }

/** Re-file every bookmark under [old] (or a folder
 *  below it) under [newPath]. */
fun refileBookmarksAfterRename(
    bookmarks: List<Bookmark>, old: String, newPath: String
): List<Bookmark> =
    bookmarks.map {
        when {
            it.folder == old -> it.copy(folder = newPath)
            it.folder.startsWith("$old/") ->
                it.copy(folder = newPath + it.folder.removePrefix(old))
            else -> it
        }
    }

/** Beta.64 #19: collapse per-visit rows to one row per
 *  URL (the latest visit), newest first. */
/** Clear-data time ranges (beta.65 #13): the cutoff a
 *  range means, or 0 for "all time". */
fun clearRangeCutoff(range: String, nowMs: Long): Long = when (range) {
    "hour" -> nowMs - 3_600_000L
    "day" -> nowMs - 86_400_000L
    "week" -> nowMs - 7 * 86_400_000L
    else -> 0L
}

/** How many history entries fall inside [cutoffMs]
 *  (0 = all time). */
fun countHistoryInRange(history: List<HistoryEntry>, cutoffMs: Long): Int =
    if (cutoffMs <= 0L) history.size
    else history.count { it.visitedAt >= cutoffMs }

fun collapseHistoryByUrl(history: List<HistoryEntry>): List<HistoryEntry> {
    val seen = HashSet<String>()
    return history.filter { seen.add(it.url) }
}

/** Beta.64 #20: the private-downloads gate. Private
 *  downloads stay hidden until the user explicitly
 *  confirms, in this session only — the gate starts
 *  locked on every launch and is never persisted, so
 *  private content is hidden by default. [unlocked]
 *  is observable state so the Downloads screen
 *  recomposes on confirm / hide. */
class PrivateDownloadsGate {
    private val unlockedState = mutableStateOf(false)
    /** True once the user confirms, this session only. */
    val unlocked: Boolean by unlockedState
    fun confirm() { unlockedState.value = true }
    fun hide() { unlockedState.value = false }
}

/** Beta.64 #53: the media download for [url], if any —
 *  the match behind each playlist row's download /
 *  offline state. Media only: a plain file download of
 *  the same URL is not an offline copy of the video. */
fun findMediaDownload(
    downloads: List<DownloadRecord>, url: String
): DownloadRecord? =
    downloads.firstOrNull { it.media && it.url == url }

data class TabSnapshot(
    val url: String,
    val title: String,
    /** Audit P-M5: restore desktop mode, scroll position, and
     *  back/forward history — a restart used to drop all
     *  three and land every tab at its landing page. */
    val desktopMode: Boolean = false,
    val scrollY: Int = 0,
    /** Base64 of a WebView.saveState() bundle, versioned
     *  below; "" means URL-only restore. Private and
     *  app-mode tabs never get one. */
    val stateB64: String = "",
    val stateVersion: Int = 0,
    /** Beta.66 #3: the saved .mht file this tab was showing
     *  ("" for ordinary tabs); carried so a restart still
     *  renders the saved copy instead of the live page. */
    val offlinePath: String = ""
)

/** One reading-list entry (audit P-m23): a page saved for
 *  later, with an unread/read state separate from
 *  bookmarks so "read later" stops polluting them. */
data class ReadingItem(
    val id: Long, val title: String, val url: String,
    val addedAt: Long, val read: Boolean = false,
    /** Beta.64 #40: the offline snapshot's .mht path
     *  ("" when the item has no snapshot yet). */
    val offlinePath: String = ""
)

/** One saved page (audit P-m24): the archive file plus
 *  the original address it was saved from. */
data class OfflinePage(
    val id: Long, val title: String, val url: String,
    val path: String, val addedAt: Long
)

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
class AppStore private constructor(context: Context) : VaultCollections {

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

    /** Bumped by progress-only download writes (audit P-M5): the
     *  downloads UI reads this instead of [revision], so a
     *  progress tick no longer recomposes every revision reader. */
    var downloadProgressRev by mutableStateOf(0)
        private set

    /** Beta.64 #20: the private-downloads gate (session
     *  only — never persisted; starts locked on every
     *  launch). */
    val privateDownloadsGate = PrivateDownloadsGate()

    val bookmarks = mutableListOf<Bookmark>()
    val bookmarkFolders = mutableListOf<String>()
    val history = mutableListOf<HistoryEntry>()
    val playlists = mutableListOf<Playlist>()
    val downloads = mutableListOf<DownloadRecord>()
    val books = mutableListOf<BookRecord>()
    override val vault = mutableListOf<VaultEntry>()
    /** Beta.68 #4: address profiles + payment cards, mirroring
     *  the vault list (same saveLocked() slice pattern). */
    override val addresses = mutableListOf<AddressEntry>()
    override val cards = mutableListOf<CardEntry>()
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

    fun removeSetting(key: String) {
        synchronized(lock) {
            if (settings.remove(key) != null) {
                publishSettingsSnapshot()
                saveLocked()
            }
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

    /** Beta.64 #41: restore bookmarks deleted by the user
     *  (undo) — the exact objects, ids and order kept, so
     *  an undo is a true undo. */
    fun restoreBookmarks(items: List<Bookmark>) {
        synchronized(lock) {
            for (b in items) {
                if (bookmarks.none { it.id == b.id }) bookmarks.add(b)
            }
            bookmarks.sortByDescending { it.createdAt }
            saveLocked()
        }
    }

    /** Audit P-m1: edit a bookmark's title and URL in
     *  place. The caller re-validates the URL (http(s)
     *  only, like the custom-search template); a blank
     *  title falls back to the URL, as on add. */
    fun updateBookmark(id: Long, title: String, url: String) {
        val u = url.trim()
        if (u.isBlank()) return
        synchronized(lock) {
            val i = bookmarks.indexOfFirst { it.id == id }
            if (i >= 0) {
                bookmarks[i] = bookmarks[i].copy(
                    title = title.ifBlank { u }, url = u
                )
                saveLocked()
            }
        }
    }

    /** Audit P-m26: import parsed HTML bookmarks, deduped
     *  by URL against everything already shelved, creating
     *  folders as needed. Returns how many were added. */
    fun importBookmarks(items: List<Triple<String, String, String>>): Int {
        var added = 0
        synchronized(lock) {
            val known = bookmarks.map { it.url }.toSet()
            for ((title, url, folder) in items) {
                if (url.isBlank() || url in known) continue
                if (folder.isNotBlank()) {
                    for (ancestor in folderAncestors(folder)) {
                        if (bookmarkFolders.none {
                                it.equals(ancestor, ignoreCase = true)
                            }
                        ) bookmarkFolders.add(ancestor)
                    }
                }
                bookmarks.add(
                    Bookmark(
                        newId(), title.ifBlank { url }, url,
                        System.currentTimeMillis(), folder
                    )
                )
                added++
            }
            if (added > 0) saveLocked()
        }
        return added
    }

    // ---------- bookmark folders ----------
    /** Beta.64 #18: create [name] under [parent] ("" for
     *  the top level). [name] is one segment — a "/" in
     *  it is rejected, paths are built, not typed. */
    fun addBookmarkFolder(name: String, parent: String = "") {
        if (!isFolderSegment(name)) return
        val path = folderChild(parent, name)
        synchronized(lock) {
            if (bookmarkFolders.none { it.equals(path, ignoreCase = true) }) {
                bookmarkFolders.add(path)
                saveLocked()
            }
        }
    }

    /** Beta.64 #18: rename the last segment of [old]
     *  to [newSegment]. The folder, every folder below
     *  it, and their bookmarks are re-rooted under the
     *  new path; a clash with an existing folder is a
     *  no-op, like before. */
    fun renameBookmarkFolder(old: String, newSegment: String) {
        if (!isFolderSegment(newSegment)) return
        val newPath = folderChild(folderParent(old), newSegment)
        synchronized(lock) {
            val idx = bookmarkFolders.indexOfFirst { it.equals(old, ignoreCase = true) }
            if (idx < 0) return@synchronized
            if (bookmarkFolders.any {
                    it.equals(newPath, ignoreCase = true) &&
                        !it.equals(old, ignoreCase = true)
                }
            ) {
                return@synchronized
            }
            val updated = renameFolderPath(bookmarkFolders, old, newPath)
            bookmarkFolders.clear()
            bookmarkFolders.addAll(updated)
            val refiled = refileBookmarksAfterRename(bookmarks, old, newPath)
            for (i in bookmarks.indices) bookmarks[i] = refiled[i]
            saveLocked()
        }
    }

    fun deleteBookmarkFolder(name: String) {
        synchronized(lock) {
            // Beta.64 #18: the folder and every folder
            // below it go; their bookmarks become Unfiled.
            bookmarkFolders.removeAll {
                it.equals(name, ignoreCase = true) ||
                    it.startsWith("$name/", ignoreCase = true)
            }
            for (i in bookmarks.indices) {
                if (bookmarks[i].folder.equals(name, ignoreCase = true) ||
                    bookmarks[i].folder.startsWith("$name/", ignoreCase = true)
                ) {
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

    /** Beta.64 #19: every visit is its own row — no
     *  dedupe by URL, no 500-entry cap. Existing rows
     *  (one per URL, latest visit) are the seed; new
     *  visits simply append. No schema change is needed:
     *  HistoryEntity has no unique index on url, and
     *  parseHistory / toModel pass rows through
     *  unchanged, so nothing is lost. */
    fun addHistory(title: String, url: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        synchronized(lock) {
            history.add(0, HistoryEntry(newId(), title.ifBlank { url }, url, System.currentTimeMillis()))
            saveHistoryLocked()
        }
    }

    fun clearHistory() {
        synchronized(lock) { history.clear(); saveHistoryLocked() }
    }

    /** Audit P-m2: delete one history entry. */
    fun removeHistory(id: Long) {
        synchronized(lock) {
            if (history.removeAll { it.id == id }) saveHistoryLocked()
        }
    }

    /** Beta.64 #41: restore history entries deleted by
     *  the user (undo) — the exact objects, ids and order
     *  kept, so an undo is a true undo. */
    fun restoreHistory(items: List<HistoryEntry>) {
        synchronized(lock) {
            for (h in items) {
                if (history.none { it.id == h.id }) history.add(h)
            }
            history.sortByDescending { it.visitedAt }
            saveHistoryLocked()
        }
    }

    /** Audit P-m2: clear entries older than [cutoffMs]
     *  (0 clears everything, like clearHistory). */
    fun clearHistoryOlderThan(cutoffMs: Long) {
        synchronized(lock) {
            if (cutoffMs <= 0L) history.clear()
            else history.removeAll { it.visitedAt < cutoffMs }
            saveHistoryLocked()
        }
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

    // ---------- reading list (audit P-m23) ----------
    // Stored as one JSON array under the "readingList"
    // setting — no schema change for a small list.
    fun readingItems(): List<ReadingItem> = synchronized(lock) {
        runCatching {
            val arr = JSONArray(settings["readingList"] ?: "")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ReadingItem(
                    o.optLong("id"), o.optString("title"),
                    o.optString("url"), o.optLong("addedAt"),
                    o.optBoolean("read"), o.optString("offlinePath")
                )
            }.filter { it.url.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun saveReadingItemsLocked(items: List<ReadingItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("title", it.title)
                    .put("url", it.url).put("addedAt", it.addedAt)
                    .put("read", it.read)
                    .put("offlinePath", it.offlinePath)
            )
        }
        settings["readingList"] = arr.toString()
        saveLocked()
    }

    fun addReadingItem(title: String, url: String) {
        synchronized(lock) {
            val items = readingItems().toMutableList()
            if (items.any { it.url == url }) return@synchronized
            items.add(
                0, ReadingItem(
                    newId(), title.ifBlank { url }, url,
                    System.currentTimeMillis(), false
                )
            )
            saveReadingItemsLocked(items)
        }
    }

    fun removeReadingItem(id: Long) {
        synchronized(lock) {
            val items = readingItems().toMutableList()
            if (items.removeAll { it.id == id }) saveReadingItemsLocked(items)
        }
    }

    fun setReadingItemRead(id: Long, read: Boolean) {
        synchronized(lock) {
            val items = readingItems().toMutableList()
            val i = items.indexOfFirst { it.id == id }
            if (i >= 0) {
                items[i] = items[i].copy(read = read)
                saveReadingItemsLocked(items)
            }
        }
    }

    /** Beta.64 #40: link an offline snapshot to an item. */
    fun setReadingItemOffline(id: Long, path: String) {
        synchronized(lock) {
            val items = readingItems().toMutableList()
            val i = items.indexOfFirst { it.id == id }
            if (i >= 0) {
                items[i] = items[i].copy(offlinePath = path)
                saveReadingItemsLocked(items)
            }
        }
    }

    /** Beta.64 #40: bulk delete for multi-select. */
    fun removeReadingItems(ids: Set<Long>) {
        synchronized(lock) {
            val items = readingItems().toMutableList()
            if (items.removeAll { it.id in ids }) saveReadingItemsLocked(items)
        }
    }

    /** Beta.64 #40: bulk mark read/unread for multi-select. */
    fun setReadingItemsRead(ids: Set<Long>, read: Boolean) {
        synchronized(lock) {
            val items = readingItems().toMutableList()
            var changed = false
            for (i in items.indices) {
                if (items[i].id in ids && items[i].read != read) {
                    items[i] = items[i].copy(read = read)
                    changed = true
                }
            }
            if (changed) saveReadingItemsLocked(items)
        }
    }

    fun isReadingItem(url: String): Boolean =
        synchronized(lock) { readingItems().any { it.url == url } }

    // ---------- saved pages (audit P-m24) ----------
    // One JSON array under the "offlinePages" setting,
    // mirroring the reading list — no schema change.
    fun offlinePages(): List<OfflinePage> = synchronized(lock) {
        runCatching {
            val arr = JSONArray(settings["offlinePages"] ?: "")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                OfflinePage(
                    o.optLong("id"), o.optString("title"),
                    o.optString("url"), o.optString("path"),
                    o.optLong("addedAt")
                )
            }.filter { it.url.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun addOfflinePage(title: String, url: String, path: String) {
        synchronized(lock) {
            val items = offlinePages().toMutableList()
            items.removeAll { it.url == url }
            items.add(
                0, OfflinePage(
                    newId(), title.ifBlank { url }, url, path,
                    System.currentTimeMillis()
                )
            )
            val arr = JSONArray()
            items.forEach {
                arr.put(
                    JSONObject().put("id", it.id).put("title", it.title)
                        .put("url", it.url).put("path", it.path)
                        .put("addedAt", it.addedAt)
                )
            }
            settings["offlinePages"] = arr.toString()
            saveLocked()
        }
    }

    fun removeOfflinePage(id: Long) {
        synchronized(lock) {
            val items = offlinePages().filter { it.id != id }
            val arr = JSONArray()
            items.forEach {
                arr.put(
                    JSONObject().put("id", it.id).put("title", it.title)
                        .put("url", it.url).put("path", it.path)
                        .put("addedAt", it.addedAt)
                )
            }
            settings["offlinePages"] = arr.toString()
            saveLocked()
        }
    }

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
        synchronized(lock) {
            downloads.add(0, rec)
            // Audit P-m7: cap retained downloads. Terminal records
            // older than 90 days go, and the list never keeps more
            // than 500 entries; active downloads are never pruned
            // and files on disk are never touched - only records.
            val cutoff = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
            val terminal = setOf("complete", "failed", "cancelled")
            val pruned = downloads.filter {
                it.status in terminal && it.addedAt < cutoff
            }
            if (pruned.isNotEmpty()) {
                downloads.removeAll(pruned.toSet())
                pruned.forEach {
                    settings.remove("dlerror:${it.id}")
                    settings.remove("dljob:${it.id}")
                    // Audit D-m12: the file stays — only a
                    // tombstone is kept, so it can still be
                    // removed deliberately instead of being
                    // orphaned invisibly.
                    settings["dltombstone:${it.id}"] = it.fileName
                }
                publishSettingsSnapshot()
            }
            if (downloads.size > 500) {
                val excess = downloads.filter { it.status in terminal }
                    .takeLast(downloads.size - 500)
                if (excess.isNotEmpty()) {
                    downloads.removeAll(excess.toSet())
                    excess.forEach {
                        settings.remove("dlerror:${it.id}")
                        settings.remove("dljob:${it.id}")
                        settings["dltombstone:${it.id}"] = it.fileName
                    }
                    publishSettingsSnapshot()
                }
            }
            saveLocked()
        }
    }

    fun updateDownload(id: Long, status: String, dmId: Long? = null, progress: Int? = null) {
        synchronized(lock) {
            var structural = false
            var progressChanged = false
            var newProgress = -1
            downloads.firstOrNull { it.id == id }?.let {
                if (it.status != status) structural = true
                if (dmId != null && it.dmId != dmId) structural = true
                it.status = status
                if (dmId != null) it.dmId = dmId
                if (progress != null && it.progress != progress) {
                    it.progress = progress
                    progressChanged = true
                    newProgress = progress
                }
            }
            // Audit P-M5: a structural change saves the whole diff
            // and bumps the global revision, as before. A
            // progress-only change writes just that row and bumps
            // only the download progress revision, so screens that
            // don't show downloads stop recomposing on every tick.
            if (structural) saveLocked()
            else if (progressChanged) saveDownloadProgressLocked(id, newProgress)
        }
    }

    /**
     * Progress-only download write (audit P-M5): a single-row
     * progress update on the writer, without copying the whole
     * slice and without bumping the global revision. The next
     * structural save's diff still rewrites the table from
     * current memory, so [lastWrittenMain] is deliberately NOT
     * updated here. A row deleted since the tick simply
     * matches no row — the update is a no-op, never a
     * resurrection.
     */
    private fun saveDownloadProgressLocked(id: Long, progress: Int) {
        val database = if (roomReady) db else null
        writer.execute {
            runCatching {
                database?.storeDao()?.updateDownloadProgress(id, progress)
            }
        }
        downloadProgressRev++
    }

    /**
     * Progress-only update (F-M1): a progress tick must
     * never resurrect a paused, cancelled, or finished
     * record — a pause landing between an engine's
     * progress write and the user's Resume would
     * otherwise hide Resume and freeze the bar. The
     * status read and the write happen inside one lock
     * hold, so no pause can slip between them
     * (synchronized is re-entrant, so updateDownload's
     * own lock hold below is the same hold).
     */
    /** Beta.64 #30: transient byte/progress detail for a
     *  download — never persisted. Bumps only the
     *  progress revision, and only when values move. */
    fun updateDownloadBytes(
        id: Long, downloaded: Long, total: Long, speedBps: Long
    ) {
        synchronized(lock) {
            val rec = downloads.firstOrNull { it.id == id } ?: return
            if (rec.bytesDownloaded == downloaded &&
                rec.totalBytes == total &&
                rec.speedBps == speedBps
            ) return
            rec.bytesDownloaded = downloaded
            rec.totalBytes = total
            rec.speedBps = speedBps
            downloadProgressRev++
        }
    }

    fun updateDownloadProgress(id: Long, progress: Int) {
        // Audit P-M5: engines tick per percent; only 5% steps
        // reach the store (0 and 100 are multiples of 5, so the
        // start and the finish still land).
        if (progress % 5 != 0) return
        synchronized(lock) {
            val current = downloads.firstOrNull { it.id == id }?.status
            updateDownload(id, progressTargetStatus(current), progress = progress)
        }
    }

    fun removeDownload(id: Long) {
        synchronized(lock) {
            downloads.removeAll { it.id == id }
            var touched = false
            if (settings.remove("dlerror:$id") != null) touched = true
            if (settings.remove("dldowngrade:$id") != null) touched = true
            if (settings.remove("dltombstone:$id") != null) touched = true
            if (touched) publishSettingsSnapshot()
            saveLocked()
        }
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
                    r.media, r.progress, r.contentUri, r.isPrivate
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

    /** Beta.65 #33: store a book's totals (chapter count
     *  + character count) so the shelf can show progress
     *  without parsing. Writes only when they change. */
    fun updateBookTotals(id: Long, totalChapters: Int, totalChars: Int) {
        synchronized(lock) {
            books.firstOrNull { it.id == id }?.let {
                if (it.totalChapters != totalChapters ||
                    it.totalChars != totalChars
                ) {
                    it.totalChapters = totalChapters
                    it.totalChars = totalChars
                    saveLocked()
                }
            }
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
    override fun addVault(entry: VaultEntry) {
        synchronized(lock) { vault.add(0, entry); saveLocked() }
    }

    fun removeVault(id: Long) {
        synchronized(lock) { vault.removeAll { it.id == id }; saveLocked() }
    }

    /** Edit-in-place (credentials round): swap one entry, same id,
     *  one save. Callers re-encrypt first when the site or password
     *  changed, since the ciphertext is bound to site + id as AAD. */
    fun updateVault(entry: VaultEntry) {
        synchronized(lock) {
            val i = vault.indexOfFirst { it.id == entry.id }
            if (i >= 0) vault[i] = entry
            saveLocked()
        }
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

    // ---------- form fill (beta.68 #4) ----------
    override fun addAddress(entry: AddressEntry) {
        synchronized(lock) { addresses.add(0, entry); saveLocked() }
    }

    fun removeAddress(id: Long) {
        synchronized(lock) {
            addresses.removeAll { it.id == id }
            // A card linked to a deleted address loses the link,
            // never the card itself. Best-effort: when the vault
            // is locked the decrypt fails and the link is left
            // for the next unlocked delete or fill (the fill
            // treats a dangling link as unlinked). The vault
            // screen deletes from an unlocked context, so in
            // practice the link clears here.
            for (i in cards.indices) {
                val c = cards[i]
                runCatching {
                    val data = net.notscape.browser.vault.CardData.fromJsonString(
                        net.notscape.browser.vault.VaultCrypto.decryptForKind(
                            c.payloadEnc, "card", c.id
                        )
                    )
                    if (data.billingAddressId == id) {
                        cards[i] = c.copy(
                            payloadEnc = net.notscape.browser.vault.VaultCrypto.encryptForKind(
                                data.copy(billingAddressId = null).toJsonString(), "card", c.id
                            )
                        )
                    }
                }
            }
            saveLocked()
        }
    }

    /** Edit-in-place: swap one profile, same id, one save.
     *  Callers re-encrypt first when the payload changed,
     *  since the ciphertext is bound to kind + id as AAD. */
    fun updateAddress(entry: AddressEntry) {
        synchronized(lock) {
            val i = addresses.indexOfFirst { it.id == entry.id }
            if (i >= 0) addresses[i] = entry
            saveLocked()
        }
    }

    override fun addCard(entry: CardEntry) {
        synchronized(lock) { cards.add(0, entry); saveLocked() }
    }

    /** Most-recently-used first (beta.68 #4, D9): a successful
     *  fill moves the entry to the front, so the chooser lists
     *  the address / card the user actually uses first. No
     *  schema change — the list order IS the recency order. */
    fun touchAddress(id: Long) {
        synchronized(lock) {
            val i = addresses.indexOfFirst { it.id == id }
            if (i > 0) {
                val e = addresses.removeAt(i)
                addresses.add(0, e)
                saveLocked()
            }
        }
    }

    fun touchCard(id: Long) {
        synchronized(lock) {
            val i = cards.indexOfFirst { it.id == id }
            if (i > 0) {
                val e = cards.removeAt(i)
                cards.add(0, e)
                saveLocked()
            }
        }
    }

    fun removeCard(id: Long) {
        synchronized(lock) { cards.removeAll { it.id == id }; saveLocked() }
    }

    /** Edit-in-place: swap one card, same id, one save.
     *  Callers re-encrypt first when the payload changed,
     *  and update last4/brand alongside it. */
    fun updateCard(entry: CardEntry) {
        synchronized(lock) {
            val i = cards.indexOfFirst { it.id == entry.id }
            if (i >= 0) cards[i] = entry
            saveLocked()
        }
    }

    // ---------- site settings ----------
    // Audit P-m8/P-m17: the new per-site fields (zoom,
    // desktop mode, permission decisions, autoplay)
    // live in the settings map, not the Room
    // SiteSetting row — no schema migration for
    // fields that are just per-host overrides.
    /** Per-site text zoom percent (P-m8/P-m29); 0 = unset, use global. */
    fun siteZoom(host: String): Int =
        setting("sitezoom:$host", "0").toIntOrNull() ?: 0

    fun putSiteZoom(host: String, zoom: Int) {
        if (zoom <= 0) removeSetting("sitezoom:$host")
        else putSetting("sitezoom:$host", zoom.toString())
    }

    /** Per-site desktop mode (P-m8); null = unset, use tab default. */
    fun siteDesktop(host: String): Boolean? = when (setting("sitedesktop:$host", "")) {
        "true" -> true
        "false" -> false
        else -> null
    }

    fun putSiteDesktop(host: String, desktop: Boolean?) {
        if (desktop == null) removeSetting("sitedesktop:$host")
        else putSetting("sitedesktop:$host", desktop.toString())
    }

    /** Per-site permission decision (P-M2): "allow",
     *  "deny", or "ask" (unset). Resource is
     *  "camera", "microphone", or "geolocation". */
    fun sitePermission(host: String, resource: String): String =
        setting("siteperm:$host:$resource", "ask")

    fun putSitePermission(host: String, resource: String, decision: String) {
        putSetting("siteperm:$host:$resource", decision)
    }

    /** Per-site autoplay override (P-m17): "allow",
     *  "block", "muted", or "" (unset, use global). */
    fun siteAutoplay(host: String): String = setting("siteautoplay:$host", "")

    fun putSiteAutoplay(host: String, mode: String) {
        if (mode.isBlank()) removeSetting("siteautoplay:$host")
        else putSetting("siteautoplay:$host", mode)
    }

    /** Audit P-m8: mark a host's open tabs for reload —
     *  BrowserScreen watches this key and reloads the
     *  tab instead of waiting for its next load. */
    fun requestSiteReload(host: String) {
        putSetting("sitereload:$host", System.currentTimeMillis().toString())
    }

    /** Audit P-m8: hosts flagged for reload since the
     *  last call; flags are consumed here so a reload
     *  never repeats. */
    fun consumeSiteReloads(): List<String> = synchronized(lock) {
        val hosts = settings.keys.filter { it.startsWith("sitereload:") }
            .map { it.removePrefix("sitereload:") }
        if (hosts.isNotEmpty()) {
            hosts.forEach { settings.remove("sitereload:$it") }
            saveLocked()
        }
        hosts
    }
    fun siteSetting(host: String): SiteSetting =
        siteSettingsSnapshot[host] ?: SiteSetting(host)

    fun putSiteSetting(setting: SiteSetting) {
        synchronized(lock) {
            siteSettings[setting.host] = setting
            publishSiteSettingsSnapshot()
            saveLocked()
        }
    }

    /** Beta.63 #24: is [host] excepted from HTTPS-only mode? */
    fun isHttpsException(host: String): Boolean =
        isHttpsException(settingsSnapshot[HTTPS_EXCEPTIONS_KEY] ?: "", host)

    /** Beta.63 #24: grant or revoke a per-site HTTPS-only exception. */
    fun setHttpsException(host: String, on: Boolean) {
        synchronized(lock) {
            settings[HTTPS_EXCEPTIONS_KEY] = if (on) {
                withHttpsException(settings[HTTPS_EXCEPTIONS_KEY] ?: "", host)
            } else {
                withoutHttpsException(settings[HTTPS_EXCEPTIONS_KEY] ?: "", host)
            }
            publishSettingsSnapshot()
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
            downloads.clear(); vault.clear(); addresses.clear(); cards.clear(); siteSettings.clear(); favourites.clear()
            // Audit D-m11: job bundles and error keys belong
            // to the downloads list — they must not survive
            // an erase that deletes the list itself.
            settings.keys.filter {
                it.startsWith("dljob:") || it.startsWith("dlerror:") ||
                    it.startsWith("dldowngrade:") || it.startsWith("dltombstone:")
            }.forEach { settings.remove(it) }
            publishSettingsSnapshot()
            publishSiteSettingsSnapshot()
            tabSession.clear(); playbackPositions.clear(); playbackTitles.clear()
            saveLocked(); saveHistoryLocked(); saveRuntimeLocked()
        }
    }

    /** Beta.63 #25: delete-on-exit wipes browsing traces only —
     *  history, the open-tab session, recently-closed tabs, and
     *  playback resume positions. Bookmarks, downloads, vault,
     *  settings, favourites and site settings are untouched. */
    fun clearBrowsingData() {
        synchronized(lock) {
            history.clear()
            tabSession.clear(); tabSessionActive = 0
            settings.remove("closedTabs")
            playbackPositions.clear(); playbackTitles.clear()
            publishSettingsSnapshot()
            saveLocked(); saveHistoryLocked(); saveRuntimeLocked()
        }
    }

    // ---------- session / playback ----------
    /** Audit P-M6: persist recently-closed tabs
     *  (non-private only) so a crash no longer erases
     *  the one way back. Entries older than 30 days
     *  drop on load; explicit clear wipes the key. */
    fun saveClosedTabs(tabs: List<TabSnapshot>) {
        synchronized(lock) {
            val arr = JSONArray()
            tabs.take(20).forEach {
                arr.put(
                    JSONObject().put("url", it.url)
                        .put("title", it.title)
                        .put("at", System.currentTimeMillis())
                )
            }
            settings["closedTabs"] = arr.toString()
            saveLocked()
        }
    }

    fun loadClosedTabs(): List<TabSnapshot> = synchronized(lock) {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        runCatching {
            val arr = JSONArray(settings["closedTabs"] ?: "")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                // Entries saved before timestamps existed
                // carry no "at" — keep them; only dated
                // entries past 30 days expire.
                val at = o.optLong("at", 0L)
                if (at != 0L && at < cutoff) return@mapNotNull null
                val url = o.optString("url")
                if (url.isBlank()) return@mapNotNull null
                TabSnapshot(url, o.optString("title"))
            }
        }.getOrDefault(emptyList())
    }

    fun clearClosedTabs() {
        synchronized(lock) {
            settings.remove("closedTabs")
            saveLocked()
        }
    }

    // ---------- site favicons (audit P-m6) ----------
    // Tab-captured favicons, persisted per host as small
    // PNG files — manager rows read them without
    // bloating the settings document.
    private fun faviconDir(): java.io.File =
        java.io.File(file.parentFile, "favicons").apply { mkdirs() }

    fun saveFavicon(host: String, bitmap: Bitmap) {
        if (host.isBlank()) return
        runCatching {
            val scaled = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            java.io.File(faviconDir(), host.hashCode().toString() + ".png")
                .writeBytes(out.toByteArray())
            if (scaled != bitmap) scaled.recycle()
        }
    }

    fun loadFavicon(host: String): Bitmap? {
        if (host.isBlank()) return null
        return runCatching {
            android.graphics.BitmapFactory.decodeFile(
                java.io.File(faviconDir(), host.hashCode().toString() + ".png").absolutePath
            )
        }.getOrNull()
    }

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
            root.remove("addresses")
            root.remove("cards")
            return root.toString(2)
        }
    }

    /** [exportBackup], sealed with a passphrase (0.0.9993) for
     *  transport through a shared folder: the document travels
     *  as ciphertext. The vault still exports separately. */
    fun exportBackupEncrypted(passphrase: String): String =
        EncryptedBackup.encrypt(exportBackup(), passphrase)

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
                root.put("addresses", JSONArray().apply {
                    addresses.forEach { put(JSONObject().put("id", it.id).put("label", it.label).put("enc", it.payloadEnc)) }
                })
                root.put("cards", JSONArray().apply {
                    cards.forEach { put(JSONObject().put("id", it.id).put("label", it.label).put("last4", it.last4).put("brand", it.brand).put("enc", it.payloadEnc)) }
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
        addresses.clear(); addresses.addAll(doc.addresses)
        cards.clear(); cards.addAll(doc.cards)
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

    /** Why the library database is unusable, when it is
     *  (0.0.9993): set when Room fails to open or migrate at
     *  startup. Until this existed the failure was silent -
     *  saves went nowhere and the UI never said a word. */
    @Volatile
    var storageError: String? = null
        private set

    /** 0.0.9994: the main slice as last written to Room - the diff
     * baseline. Updated only AFTER a write completes, so a stale
     * baseline can cause an extra table rewrite, never a missed one. */
    @Volatile
    private var lastWrittenMain: StoreMigration.MainSlice? = null

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
     *  Callers hold the lock, or the writer takes it (beta.66
     *  #14 moved the saveLocked() copy onto the writer). */
    private fun mainSliceLocked(): StoreMigration.MainSlice = StoreMigration.mainSlice(
        bookmarks.toList(), bookmarkFolders.toList(), favourites.toList(),
        playlists.map { Playlist(it.id, it.name, it.createdAt, it.items.toMutableList()) },
        downloads.map { it.copy() }, books.map { it.copy() }, vault.toList(),
        addresses.toList(), cards.toList(),
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
        dao.clearAddresses(); dao.insertAddresses(slice.addresses)
        dao.clearCards(); dao.insertCards(slice.cards)
        dao.clearSiteSettings(); dao.insertSiteSettings(slice.siteSettings)
        dao.clearSettings(); dao.insertSettings(slice.settings)
        dao.putMeta(MetaEntity("session", slice.sessionJson))
    }

    /** 0.0.9994: rewrite only the tables whose slice changed since
     * the last successful write (StoreMigration.mainSliceChanges).
     * A progress tick in downloads rewrites the downloads table
     * alone, not all eleven parts. */
    private fun writeMainDiffInDb(dao: StoreDao, slice: StoreMigration.MainSlice) {
        val changed = StoreMigration.mainSliceChanges(lastWrittenMain, slice)
        if ("bookmarks" in changed) {
            dao.clearBookmarks(); dao.insertBookmarks(slice.bookmarks)
        }
        if ("folders" in changed) {
            dao.clearFolders(); dao.insertFolders(slice.folders)
        }
        if ("favourites" in changed) {
            dao.clearFavourites(); dao.insertFavourites(slice.favourites)
        }
        if ("playlists" in changed) {
            dao.clearPlaylists(); dao.insertPlaylists(slice.playlists)
        }
        if ("playlistItems" in changed) {
            dao.clearPlaylistItems(); dao.insertPlaylistItems(slice.playlistItems)
        }
        if ("downloads" in changed) {
            dao.clearDownloads(); dao.insertDownloads(slice.downloads)
        }
        if ("books" in changed) {
            dao.clearBooks(); dao.insertBooks(slice.books)
        }
        if ("vault" in changed) {
            dao.clearVault(); dao.insertVault(slice.vault)
        }
        if ("addresses" in changed) {
            dao.clearAddresses(); dao.insertAddresses(slice.addresses)
        }
        if ("cards" in changed) {
            dao.clearCards(); dao.insertCards(slice.cards)
        }
        if ("siteSettings" in changed) {
            dao.clearSiteSettings(); dao.insertSiteSettings(slice.siteSettings)
        }
        if ("settings" in changed) {
            dao.clearSettings(); dao.insertSettings(slice.settings)
        }
        if ("session" in changed) {
            dao.putMeta(MetaEntity("session", slice.sessionJson))
        }
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
                    dao.addresses().size == addresses.size &&
                    dao.cards().size == cards.size &&
                    dao.playlists().size == playlists.size
                if (verified) {
                    dao.putMeta(MetaEntity("migrated", "1"))
                }
            }
            if (dao.meta("migrated") == "1") {
                loadFromRoomLocked(dao)
            }
            roomReady = true
            lastWrittenMain = runCatching { mainSliceLocked() }.getOrNull()
        }.onFailure { e ->
            storageError = "The library database could not be " +
                "opened (${e.message ?: "unknown error"}). " +
                "Changes made this session will not be saved."
        }
    }

    /** Refill the in-memory collections from Room (post-migration truth). */
    private fun loadFromRoomLocked(dao: StoreDao) {
        val state = StoreMigration.toModel(
            dao.bookmarks(), dao.folders(), dao.favourites(), dao.history(),
            dao.playlists(), dao.playlistItems(), dao.downloads(), dao.books(),
            dao.vault(), dao.addresses(), dao.cards(),
            dao.siteSettings(), dao.settings(), dao.positions(),
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
        addresses.clear(); addresses.addAll(state.addresses)
        cards.clear(); cards.addAll(state.cards)
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
            lastWrittenMain = main
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
                        .put("m", it.media).put("p", it.progress).put("uri", it.contentUri)
                        .put("priv", it.isPrivate))
                }
            })
            root.put("books", JSONArray().apply {
                books.forEach {
                    put(JSONObject().put("id", it.id).put("title", it.title).put("author", it.author)
                        .put("path", it.filePath).put("format", it.format)
                        .put("chapter", it.chapterIndex).put("frac", it.scrollFraction.toDouble()).put("t", it.addedAt)
                        .put("cv", it.coverPath).put("tc", it.totalChapters).put("tch", it.totalChars))
                }
            })
            root.put("vault", JSONArray().apply {
                vault.forEach { put(JSONObject().put("id", it.id).put("site", it.site).put("user", it.username).put("enc", it.passwordEnc)) }
            })
            root.put("addresses", JSONArray().apply {
                addresses.forEach { put(JSONObject().put("id", it.id).put("label", it.label).put("enc", it.payloadEnc)) }
            })
            root.put("cards", JSONArray().apply {
                cards.forEach { put(JSONObject().put("id", it.id).put("label", it.label).put("last4", it.last4).put("brand", it.brand).put("enc", it.payloadEnc)) }
            })
            root.put("siteSettings", JSONObject().apply {
                siteSettings.forEach { (host, s) -> put(host, JSONObject().put("js", s.javaScript).put("img", s.blockImages).put("pop", s.allowPopups).put("nb", s.disableBlocking).put("bp", s.blockPopups).put("c3", s.allowThirdPartyCookies)) }
            })
            root.put("settings", JSONObject().apply {
                settings.forEach { (k, v) -> put(k, v) }
            })
            root.put("session", JSONObject().put("active", tabSessionActive)
                .put("tabs", JSONArray().apply {
                    tabSession.forEach {
                        put(
                            JSONObject().put("url", it.url).put("title", it.title)
                                .put("desktop", it.desktopMode)
                                .put("scrollY", it.scrollY)
                        )
                    }
                }))
            return root.toString()
    }

    /**
     * Block until the writer thread has drained every
     * save submitted before this call (a no-op task
     * queued behind them). A coalesced-away save is
     * still covered: the newer save that superseded
     * it serializes the same in-memory state. Used
     * by the vault migration so the v1 key is only
     * retired once the re-encrypted records are
     * durably in Room. False on timeout.
     */
    fun awaitPendingWrites(timeoutMs: Long = 10_000): Boolean =
        runCatching {
            writer.submit(java.lang.Runnable {}).get(
                timeoutMs,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
        }.isSuccess

    /** Serialize the main document. Caller holds the lock;
     *  the slice itself is copied on the writer thread
     *  (beta.66 #14): the eleven-collection copy used to
     *  run on the caller thread under the lock, so a big
     *  library stalled the UI on every save. The
     *  generation still stamps the call order, so a newer
     *  save supersedes this one before any copy is made.
     *  Same data saved, same ordering — only the thread
     *  doing the copy moved. */
    private fun saveLocked() {
        val gen = ++mainGen
        val roomWasReady = roomReady
        val database = db
        writer.execute {
            // Coalesce bursts: a newer save supersedes this one.
            if (gen != mainGen) return@execute
            val slice = if (roomWasReady) {
                runCatching {
                    synchronized(lock) { mainSliceLocked() }
                }.getOrNull()
            } else {
                null
            }
            if (slice != null && database != null) {
                runCatching {
                    database.runInTransaction {
                        writeMainDiffInDb(database.storeDao(), slice)
                    }
                    lastWrittenMain = slice
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

/**
 * F-M1: the status a progress tick may move a record
 * to. Paused, cancelled, and terminal records keep
 * their status — a tick must never resurrect them.
 * Anything else (queued, downloading, or a record
 * that vanished mid-write) becomes "downloading",
 * matching what progress writes always wrote before.
 */
fun progressTargetStatus(current: String?): String =
    when (current) {
        "paused", "cancelled", "complete", "failed" -> current
        else -> "downloading"
    }
