package net.notscape.browser.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Bookmark(val id: Long, val title: String, val url: String, val createdAt: Long)
data class HistoryEntry(val id: Long, val title: String, val url: String, val visitedAt: Long)
data class PlaylistItem(val id: Long, val title: String, val url: String)
data class Playlist(val id: Long, val name: String, val createdAt: Long, val items: MutableList<PlaylistItem>)
data class DownloadRecord(
    val id: Long, val title: String, val url: String, val fileName: String,
    var dmId: Long, var status: String, val addedAt: Long
)
data class BookRecord(
    val id: Long, val title: String, val author: String, val filePath: String,
    val format: String, var chapterIndex: Int, var scrollFraction: Float, val addedAt: Long
)
data class VaultEntry(val id: Long, val site: String, val username: String, val passwordEnc: String)
data class SiteSetting(val host: String, val javaScript: Boolean = true, val blockImages: Boolean = false, val allowPopups: Boolean = false, val disableBlocking: Boolean = false)
data class TabSnapshot(val url: String, val title: String)

/**
 * Local-only persistence: a single JSON document in app-private storage.
 * Everything the app remembers lives on the device.
 */
class AppStore private constructor(context: Context) {

    private val file = File(context.filesDir, "notscape_data.json")
    private val lock = Any()

    var revision by mutableStateOf(0)
        private set

    val bookmarks = mutableListOf<Bookmark>()
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

    init {
        load()
    }

    companion object {
        @Volatile private var instance: AppStore? = null
        fun get(context: Context): AppStore =
            instance ?: synchronized(this) {
                instance ?: AppStore(context.applicationContext).also { instance = it }
            }
    }

    fun setting(key: String, default: String): String = settings[key] ?: default
    fun putSetting(key: String, value: String) {
        synchronized(lock) { settings[key] = value; saveLocked() }
    }

    // ---------- bookmarks / history / favourites ----------
    fun isBookmarked(url: String): Boolean = bookmarks.any { it.url == url }

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

    fun addHistory(title: String, url: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        synchronized(lock) {
            history.removeAll { it.url == url }
            history.add(0, HistoryEntry(newId(), title.ifBlank { url }, url, System.currentTimeMillis()))
            while (history.size > 500) history.removeAt(history.size - 1)
            saveLocked()
        }
    }

    fun clearHistory() {
        synchronized(lock) { history.clear(); saveLocked() }
    }

    fun toggleFavourite(title: String, url: String) {
        synchronized(lock) {
            val existing = favourites.firstOrNull { it.url == url }
            if (existing != null) favourites.remove(existing)
            else favourites.add(Bookmark(newId(), title.ifBlank { url }, url, System.currentTimeMillis()))
            saveLocked()
        }
    }

    fun isFavourite(url: String): Boolean = favourites.any { it.url == url }

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

    fun updateDownload(id: Long, status: String, dmId: Long? = null) {
        synchronized(lock) {
            downloads.firstOrNull { it.id == id }?.let {
                it.status = status
                if (dmId != null) it.dmId = dmId
            }
            saveLocked()
        }
    }

    fun removeDownload(id: Long) {
        synchronized(lock) { downloads.removeAll { it.id == id }; saveLocked() }
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

    // ---------- site settings ----------
    fun siteSetting(host: String): SiteSetting = siteSettings[host] ?: SiteSetting(host)

    fun putSiteSetting(setting: SiteSetting) {
        synchronized(lock) { siteSettings[setting.host] = setting; saveLocked() }
    }

    fun clearAllData() {
        synchronized(lock) {
            bookmarks.clear(); history.clear(); playlists.clear()
            downloads.clear(); vault.clear(); siteSettings.clear(); favourites.clear()
            tabSession.clear(); playbackPositions.clear()
            saveLocked()
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

    fun setPlaybackPosition(url: String, positionMs: Long) {
        synchronized(lock) {
            if (positionMs > 5_000) playbackPositions[url] = positionMs
            else playbackPositions.remove(url)
            saveLocked()
        }
    }

    fun clearPlaybackPosition(url: String) {
        synchronized(lock) { playbackPositions.remove(url); saveLocked() }
    }

    // ---------- backup ----------
    /** Full data backup as JSON. The vault is NOT included — it has its own
     *  passphrase-protected export because its keys are device-bound. */
    fun exportBackup(): String {
        synchronized(lock) {
            saveLocked()
            val root = JSONObject(file.readText())
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
                file.writeText(root.toString())
                loadLocked()
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

    private fun loadLocked() {
            if (!file.exists()) {
                seedDefaultsLocked()
                return
            }
            runCatching {
                val root = JSONObject(file.readText())
                bookmarks.clear(); root.optJSONArray("bookmarks")?.let { arr ->
                    for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                        bookmarks.add(Bookmark(it.getLong("id"), it.getString("title"), it.getString("url"), it.getLong("t")))
                    }
                }
                favourites.clear(); root.optJSONArray("favourites")?.let { arr ->
                    for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                        favourites.add(Bookmark(it.getLong("id"), it.getString("title"), it.getString("url"), it.getLong("t")))
                    }
                }
                history.clear(); root.optJSONArray("history")?.let { arr ->
                    for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                        history.add(HistoryEntry(it.getLong("id"), it.getString("title"), it.getString("url"), it.getLong("t")))
                    }
                }
                playlists.clear(); root.optJSONArray("playlists")?.let { arr ->
                    for (i in 0 until arr.length()) arr.getJSONObject(i).let { p ->
                        val items = mutableListOf<PlaylistItem>()
                        p.optJSONArray("items")?.let { ia ->
                            for (j in 0 until ia.length()) ia.getJSONObject(j).let {
                                items.add(PlaylistItem(it.getLong("id"), it.getString("title"), it.getString("url")))
                            }
                        }
                        playlists.add(Playlist(p.getLong("id"), p.getString("name"), p.getLong("t"), items))
                    }
                }
                downloads.clear(); root.optJSONArray("downloads")?.let { arr ->
                    for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                        downloads.add(
                            DownloadRecord(
                                it.getLong("id"), it.getString("title"), it.getString("url"),
                                it.getString("fileName"), it.optLong("dmId", -1),
                                it.optString("status", "queued"), it.getLong("t")
                            )
                        )
                    }
                }
                books.clear(); root.optJSONArray("books")?.let { arr ->
                    for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                        books.add(
                            BookRecord(
                                it.getLong("id"), it.getString("title"), it.optString("author"),
                                it.getString("path"), it.getString("format"),
                                it.optInt("chapter", 0), it.optDouble("frac", 0.0).toFloat(),
                                it.getLong("t")
                            )
                        )
                    }
                }
                vault.clear(); root.optJSONArray("vault")?.let { arr ->
                    for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                        vault.add(VaultEntry(it.getLong("id"), it.getString("site"), it.getString("user"), it.getString("enc")))
                    }
                }
                siteSettings.clear(); root.optJSONObject("siteSettings")?.let { obj ->
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val host = keys.next()
                        val o = obj.getJSONObject(host)
                        siteSettings[host] = SiteSetting(host, o.optBoolean("js", true), o.optBoolean("img", false), o.optBoolean("pop", false), o.optBoolean("nb", false))
                    }
                }
                settings.clear(); root.optJSONObject("settings")?.let { obj ->
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        settings[k] = obj.getString(k)
                    }
                }
                tabSession.clear(); root.optJSONObject("session")?.let { s ->
                    tabSessionActive = s.optInt("active", 0)
                    s.optJSONArray("tabs")?.let { arr ->
                        for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                            tabSession.add(TabSnapshot(it.getString("url"), it.optString("title")))
                        }
                    }
                }
                playbackPositions.clear(); root.optJSONObject("positions")?.let { obj ->
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        playbackPositions[k] = obj.optLong(k, 0)
                    }
                }
            }
    }

    private fun seedDefaultsLocked() {
        favourites.addAll(
            listOf(
                Bookmark(1, "Wikipedia", "https://www.wikipedia.org", 1),
                Bookmark(2, "Project Gutenberg", "https://www.gutenberg.org", 2),
                Bookmark(3, "Internet Archive", "https://archive.org", 3),
                Bookmark(4, "Open Library", "https://openlibrary.org", 4)
            )
        )
        saveLocked()
    }

    private fun saveLocked() {
        runCatching {
            val root = JSONObject()
            root.put("bookmarks", JSONArray().apply {
                bookmarks.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("url", it.url).put("t", it.createdAt)) }
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
                        .put("fileName", it.fileName).put("dmId", it.dmId).put("status", it.status).put("t", it.addedAt))
                }
            })
            root.put("books", JSONArray().apply {
                books.forEach {
                    put(JSONObject().put("id", it.id).put("title", it.title).put("author", it.author)
                        .put("path", it.filePath).put("format", it.format)
                        .put("chapter", it.chapterIndex).put("frac", it.scrollFraction.toDouble()).put("t", it.addedAt))
                }
            })
            root.put("vault", JSONArray().apply {
                vault.forEach { put(JSONObject().put("id", it.id).put("site", it.site).put("user", it.username).put("enc", it.passwordEnc)) }
            })
            root.put("siteSettings", JSONObject().apply {
                siteSettings.forEach { (host, s) -> put(host, JSONObject().put("js", s.javaScript).put("img", s.blockImages).put("pop", s.allowPopups).put("nb", s.disableBlocking)) }
            })
            root.put("settings", JSONObject().apply {
                settings.forEach { (k, v) -> put(k, v) }
            })
            root.put("session", JSONObject().put("active", tabSessionActive)
                .put("tabs", JSONArray().apply {
                    tabSession.forEach { put(JSONObject().put("url", it.url).put("title", it.title)) }
                }))
            root.put("positions", JSONObject().apply {
                playbackPositions.forEach { (k, v) -> put(k, v) }
            })
            file.writeText(root.toString())
        }
        revision++
    }
}
