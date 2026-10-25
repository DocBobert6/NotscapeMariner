package net.notscape.browser.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entities for the Phase F persistence migration. One table per
 * store collection, fields mirroring the model classes one-to-one so
 * the model <-> entity mapping (StoreMigration) stays a pure,
 * unit-testable copy with no interpretation.
 */
@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: Long, val title: String, val url: String,
    val createdAt: Long, val folder: String
)

@Entity(tableName = "bookmark_folders")
data class FolderEntity(@PrimaryKey val name: String)

@Entity(tableName = "favourites")
data class FavouriteEntity(
    @PrimaryKey val id: Long, val title: String, val url: String, val createdAt: Long
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: Long, val title: String, val url: String, val visitedAt: Long
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: Long, val name: String, val createdAt: Long
)

@Entity(tableName = "playlist_items")
data class PlaylistItemEntity(
    @PrimaryKey val id: Long, val playlistId: Long, val title: String,
    val url: String, val position: Int
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: Long, val title: String, val url: String,
    val fileName: String, val dmId: Long, val status: String,
    val addedAt: Long, val media: Boolean, val progress: Int,
    val contentUri: String
)

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: Long, val title: String, val author: String,
    val filePath: String, val format: String, val chapterIndex: Int,
    val scrollFraction: Float, val addedAt: Long, val coverPath: String
)

@Entity(tableName = "vault")
data class VaultEntity(
    @PrimaryKey val id: Long, val site: String, val username: String,
    val passwordEnc: String
)

@Entity(tableName = "site_settings")
data class SiteSettingEntity(
    @PrimaryKey val host: String, val javaScript: Boolean,
    val blockImages: Boolean, val allowPopups: Boolean,
    val disableBlocking: Boolean, val blockPopups: Boolean,
    val allowThirdPartyCookies: Boolean
)

@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String)

@Entity(tableName = "positions")
data class PositionEntity(@PrimaryKey val url: String, val positionMs: Long)

@Entity(tableName = "meta")
data class MetaEntity(@PrimaryKey val key: String, val value: String)
