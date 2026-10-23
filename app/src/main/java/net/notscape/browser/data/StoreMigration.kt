package net.notscape.browser.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure model <-> entity mapping for the Phase F Room migration, plus
 * the tab-session's meta-table encoding. No Android or Room-runtime
 * calls live here (entities are plain data classes), so the whole
 * migration mapping is unit-testable on the JVM: fixture JSON goes in
 * through StoreJson, entities come out, and the reverse mapping is
 * what AppStore's load-from-Room path applies.
 */
object StoreMigration {

    /** Everything the main save persists, as entity lists. */
    class MainSlice(
        val bookmarks: List<BookmarkEntity>,
        val folders: List<FolderEntity>,
        val favourites: List<FavouriteEntity>,
        val playlists: List<PlaylistEntity>,
        val playlistItems: List<PlaylistItemEntity>,
        val downloads: List<DownloadEntity>,
        val books: List<BookEntity>,
        val vault: List<VaultEntity>,
        val siteSettings: List<SiteSettingEntity>,
        val settings: List<SettingEntity>,
        val sessionJson: String
    )

    fun mainSlice(
        bookmarks: List<Bookmark>,
        bookmarkFolders: List<String>,
        favourites: List<Bookmark>,
        playlists: List<Playlist>,
        downloads: List<DownloadRecord>,
        books: List<BookRecord>,
        vault: List<VaultEntry>,
        siteSettings: Map<String, SiteSetting>,
        settings: Map<String, String>,
        tabSession: List<TabSnapshot>,
        tabSessionActive: Int
    ): MainSlice {
        val playlistEntities = playlists.map { PlaylistEntity(it.id, it.name, it.createdAt) }
        val itemEntities = playlists.flatMap { p ->
            p.items.mapIndexed { idx, item ->
                PlaylistItemEntity(item.id, p.id, item.title, item.url, idx)
            }
        }
        return MainSlice(
            bookmarks = bookmarks.map {
                BookmarkEntity(it.id, it.title, it.url, it.createdAt, it.folder)
            },
            folders = bookmarkFolders.map { FolderEntity(it) },
            favourites = favourites.map {
                FavouriteEntity(it.id, it.title, it.url, it.createdAt)
            },
            playlists = playlistEntities,
            playlistItems = itemEntities,
            downloads = downloads.map {
                DownloadEntity(
                    it.id, it.title, it.url, it.fileName, it.dmId, it.status,
                    it.addedAt, it.media, it.progress, it.contentUri
                )
            },
            books = books.map {
                BookEntity(
                    it.id, it.title, it.author, it.filePath, it.format,
                    it.chapterIndex, it.scrollFraction, it.addedAt, it.coverPath
                )
            },
            vault = vault.map { VaultEntity(it.id, it.site, it.username, it.passwordEnc) },
            siteSettings = siteSettings.values.map {
                SiteSettingEntity(
                    it.host, it.javaScript, it.blockImages, it.allowPopups,
                    it.disableBlocking, it.blockPopups, it.allowThirdPartyCookies
                )
            },
            settings = settings.map { (k, v) -> SettingEntity(k, v) },
            sessionJson = sessionToJson(tabSession, tabSessionActive)
        )
    }

    fun historySlice(history: List<HistoryEntry>): List<HistoryEntity> =
        history.map { HistoryEntity(it.id, it.title, it.url, it.visitedAt) }

    fun positionsSlice(positions: Map<String, Long>): List<PositionEntity> =
        positions.map { (k, v) -> PositionEntity(k, v) }

    fun sessionToJson(tabs: List<TabSnapshot>, active: Int): String {
        val root = JSONObject()
        root.put("active", active)
        root.put("tabs", JSONArray().apply {
            tabs.forEach {
                put(
                    JSONObject().put("url", it.url)
                        .put("title", it.title)
                        .put("group", it.group)
                )
            }
        })
        return root.toString()
    }

    fun sessionFromJson(text: String?): Pair<List<TabSnapshot>, Int> {
        if (text.isNullOrBlank()) return emptyList<TabSnapshot>() to 0
        return runCatching {
            val root = JSONObject(text)
            val tabs = mutableListOf<TabSnapshot>()
            root.optJSONArray("tabs")?.let { arr ->
                for (i in 0 until arr.length()) arr.getJSONObject(i).let {
                    tabs.add(
                        TabSnapshot(
                            it.getString("url"),
                            it.optString("title"),
                            it.optString("group")
                        )
                    )
                }
            }
            tabs to root.optInt("active", 0)
        }.getOrDefault(emptyList<TabSnapshot>() to 0)
    }

    /** The store's complete in-memory state, rebuilt from entities. */
    class ModelState(
        val bookmarks: List<Bookmark>,
        val bookmarkFolders: List<String>,
        val favourites: List<Bookmark>,
        val history: List<HistoryEntry>,
        val playlists: List<Playlist>,
        val downloads: List<DownloadRecord>,
        val books: List<BookRecord>,
        val vault: List<VaultEntry>,
        val siteSettings: Map<String, SiteSetting>,
        val settings: Map<String, String>,
        val tabSession: List<TabSnapshot>,
        val tabSessionActive: Int,
        val playbackPositions: Map<String, Long>
    )

    fun toModel(
        bookmarks: List<BookmarkEntity>,
        folders: List<FolderEntity>,
        favourites: List<FavouriteEntity>,
        history: List<HistoryEntity>,
        playlists: List<PlaylistEntity>,
        playlistItems: List<PlaylistItemEntity>,
        downloads: List<DownloadEntity>,
        books: List<BookEntity>,
        vault: List<VaultEntity>,
        siteSettings: List<SiteSettingEntity>,
        settings: List<SettingEntity>,
        positions: List<PositionEntity>,
        sessionJson: String?
    ): ModelState {
        val itemsByPlaylist = playlistItems.groupBy { it.playlistId }
        val (tabs, active) = sessionFromJson(sessionJson)
        return ModelState(
            bookmarks = bookmarks.map {
                Bookmark(it.id, it.title, it.url, it.createdAt, it.folder)
            },
            bookmarkFolders = folders.map { it.name },
            favourites = favourites.map {
                Bookmark(it.id, it.title, it.url, it.createdAt)
            },
            history = history.map {
                HistoryEntry(it.id, it.title, it.url, it.visitedAt)
            },
            playlists = playlists.map { p ->
                Playlist(
                    p.id, p.name, p.createdAt,
                    (itemsByPlaylist[p.id] ?: emptyList())
                        .sortedBy { it.position }
                        .map { PlaylistItem(it.id, it.title, it.url) }
                        .toMutableList()
                )
            },
            downloads = downloads.map {
                DownloadRecord(
                    it.id, it.title, it.url, it.fileName, it.dmId, it.status,
                    it.addedAt, it.media, it.progress
                ).also { rec -> rec.contentUri = it.contentUri }
            },
            books = books.map {
                BookRecord(
                    it.id, it.title, it.author, it.filePath, it.format,
                    it.chapterIndex, it.scrollFraction, it.addedAt, it.coverPath
                )
            },
            vault = vault.map { VaultEntry(it.id, it.site, it.username, it.passwordEnc) },
            siteSettings = siteSettings.associate {
                it.host to SiteSetting(
                    it.host, it.javaScript, it.blockImages, it.allowPopups,
                    it.disableBlocking, it.blockPopups, it.allowThirdPartyCookies
                )
            },
            settings = settings.associate { it.key to it.value },
            tabSession = tabs,
            tabSessionActive = active,
            playbackPositions = positions.associate { it.url to it.positionMs }
        )
    }
}
