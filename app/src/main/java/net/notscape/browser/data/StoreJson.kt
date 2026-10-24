package net.notscape.browser.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure (Android-free) JSON helpers for the store's sidecar files and
 * its crash-recovery choice. Kept separate from AppStore so the
 * recovery and round-trip logic is unit-testable on the JVM.
 */
object StoreJson {

    /** First parseable JSON document of [main], then [bak]; null when
     *  neither parses. This is the store's corruption-recovery rule:
     *  a damaged main document never shadows a good backup. */
    fun pickReadable(main: String?, bak: String?): String? {
        if (main != null && runCatching { JSONObject(main) }.isSuccess) return main
        if (bak != null && runCatching { JSONObject(bak) }.isSuccess) return bak
        return null
    }

    fun historyToJson(entries: List<HistoryEntry>): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("history", JSONArray().apply {
            entries.forEach {
                put(JSONObject().put("id", it.id).put("title", it.title)
                    .put("url", it.url).put("t", it.visitedAt))
            }
        })
        return root.toString()
    }

    /** Null when [text] is not a readable history document. */
    fun parseHistory(text: String): List<HistoryEntry>? = runCatching {
        val arr = JSONObject(text).getJSONArray("history")
        val out = ArrayList<HistoryEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(HistoryEntry(o.getLong("id"), o.getString("title"), o.getString("url"), o.getLong("t")))
        }
        out
    }.getOrNull()

    fun positionsToJson(map: Map<String, Long>): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("positions", JSONObject().apply {
            map.forEach { (k, v) -> put(k, v) }
        })
        return root.toString()
    }

    /** Null when [text] is not a readable positions document. */
    fun parsePositions(text: String): Map<String, Long>? = runCatching {
        val obj = JSONObject(text).getJSONObject("positions")
        val out = LinkedHashMap<String, Long>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = obj.optLong(k, 0)
        }
        out
    }.getOrNull()

    /**
     * Parse a main store document into a [MainDocument], or null when
     * the text is not a readable store document. Mirrors the field
     * defaults AppStore's loader has always used (same keys, same
     * fallbacks), including restoring bookmark folders that are
     * referenced by bookmarks but missing from the folder list.
     */
    fun parseMainDocument(text: String): MainDocument? = runCatching {
        val root = JSONObject(text)
        val doc = MainDocument()
        root.optJSONArray("bookmarks")?.let { arr ->
            for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                doc.bookmarks.add(
                    Bookmark(
                        it.getLong("id"), it.getString("title"), it.getString("url"),
                        it.getLong("t"), it.optString("f", "")
                    )
                )
            }
        }
        root.optJSONArray("bookmarkFolders")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let { doc.bookmarkFolders.add(it) }
            }
        }
        // Folders referenced by bookmarks but missing from the list
        // (older saves) are restored as folders too.
        doc.bookmarks.map { it.folder }.filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .forEach { f ->
                if (doc.bookmarkFolders.none { it.equals(f, ignoreCase = true) }) {
                    doc.bookmarkFolders.add(f)
                }
            }
        root.optJSONArray("favourites")?.let { arr ->
            for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                doc.favourites.add(Bookmark(it.getLong("id"), it.getString("title"), it.getString("url"), it.getLong("t")))
            }
        }
        doc.legacyHistory = root.optJSONArray("history")?.let { arr ->
            val out = mutableListOf<HistoryEntry>()
            for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                out.add(HistoryEntry(it.getLong("id"), it.getString("title"), it.getString("url"), it.getLong("t")))
            }
            out
        }
        root.optJSONArray("playlists")?.let { arr ->
            for (i in 0 until arr.length()) arr.getJSONObject(i).let { pl ->
                val items = mutableListOf<PlaylistItem>()
                pl.optJSONArray("items")?.let { ia ->
                    for (j in 0 until ia.length()) ia.getJSONObject(j).let {
                        items.add(PlaylistItem(it.getLong("id"), it.getString("title"), it.getString("url")))
                    }
                }
                doc.playlists.add(Playlist(pl.getLong("id"), pl.getString("name"), pl.getLong("t"), items))
            }
        }
        root.optJSONArray("downloads")?.let { arr ->
            for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                doc.downloads.add(
                    DownloadRecord(
                        it.getLong("id"), it.getString("title"), it.getString("url"),
                        it.getString("fileName"), it.optLong("dmId", -1),
                        it.optString("status", "queued"), it.getLong("t"),
                        it.optBoolean("m", false), it.optInt("p", -1)
                    ).also { rec -> rec.contentUri = it.optString("uri", "") }
                )
            }
        }
        root.optJSONArray("books")?.let { arr ->
            for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                doc.books.add(
                    BookRecord(
                        it.getLong("id"), it.getString("title"), it.optString("author"),
                        it.getString("path"), it.getString("format"),
                        it.optInt("chapter", 0), it.optDouble("frac", 0.0).toFloat(),
                        it.getLong("t"), it.optString("cv", "")
                    )
                )
            }
        }
        root.optJSONArray("vault")?.let { arr ->
            for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                doc.vault.add(VaultEntry(it.getLong("id"), it.getString("site"), it.getString("user"), it.getString("enc")))
            }
        }
        root.optJSONObject("siteSettings")?.let { obj ->
            val keys = obj.keys()
            while (keys.hasNext()) {
                val host = keys.next()
                val o = obj.getJSONObject(host)
                doc.siteSettings[host] = SiteSetting(
                    host, o.optBoolean("js", true), o.optBoolean("img", false),
                    o.optBoolean("pop", false), o.optBoolean("nb", false),
                    o.optBoolean("bp", false), o.optBoolean("c3", false)
                )
            }
        }
        root.optJSONObject("settings")?.let { obj ->
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                doc.settings[k] = obj.getString(k)
            }
        }
        root.optJSONObject("session")?.let { sess ->
            doc.tabSessionActive = sess.optInt("active", 0)
            sess.optJSONArray("tabs")?.let { arr ->
                for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                    doc.tabSession.add(
                        TabSnapshot(
                            it.getString("url"),
                            it.optString("title"),
                            it.optString("group")
                        )
                    )
                }
            }
        }
        doc.legacyPositions = root.optJSONObject("positions")?.let { obj ->
            val out = LinkedHashMap<String, Long>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = obj.optLong(k, 0)
            }
            out
        }
        doc
    }.getOrNull()
}

/**
 * A fully-parsed main store document (the model collections exactly
 * as AppStore keeps them in memory). Produced by [parseMainDocument];
 * shared by AppStore's JSON load and the Phase F Room importer, so
 * both paths read a document with identical semantics.
 */
class MainDocument {
    val bookmarks = mutableListOf<Bookmark>()
    val bookmarkFolders = mutableListOf<String>()
    val favourites = mutableListOf<Bookmark>()
    val playlists = mutableListOf<Playlist>()
    val downloads = mutableListOf<DownloadRecord>()
    val books = mutableListOf<BookRecord>()
    val vault = mutableListOf<VaultEntry>()
    val siteSettings = mutableMapOf<String, SiteSetting>()
    val settings = mutableMapOf<String, String>()
    val tabSession = mutableListOf<TabSnapshot>()
    var tabSessionActive: Int = 0
    /** History as embedded in pre-D.0 main documents (migration source). */
    var legacyHistory: List<HistoryEntry>? = null
    /** Playback positions as embedded in pre-D.0 main documents. */
    var legacyPositions: Map<String, Long>? = null
}
