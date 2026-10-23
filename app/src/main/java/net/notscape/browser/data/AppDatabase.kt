package net.notscape.browser.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The Room database behind AppStore (Phase F). AppStore remains the
 * facade every screen talks to — it keeps its in-memory state and
 * lock-free snapshots exactly as before, and persists through this
 * database on its writer thread. Since 0.0.988 the startup
 * load runs on that writer too, so no query ever touches the
 * main thread and the database needs no main-thread allowance.
 */
@Database(
    entities = [
        BookmarkEntity::class, FolderEntity::class, FavouriteEntity::class,
        HistoryEntity::class, PlaylistEntity::class, PlaylistItemEntity::class,
        DownloadEntity::class, BookEntity::class, VaultEntity::class,
        SiteSettingEntity::class, SettingEntity::class, PositionEntity::class,
        MetaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "notscape.db")
                .build()
    }
}

@Dao
interface StoreDao {
    @Query("SELECT * FROM bookmarks") fun bookmarks(): List<BookmarkEntity>
    @Query("DELETE FROM bookmarks") fun clearBookmarks()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertBookmarks(items: List<BookmarkEntity>)

    @Query("SELECT * FROM bookmark_folders") fun folders(): List<FolderEntity>
    @Query("DELETE FROM bookmark_folders") fun clearFolders()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertFolders(items: List<FolderEntity>)

    @Query("SELECT * FROM favourites") fun favourites(): List<FavouriteEntity>
    @Query("DELETE FROM favourites") fun clearFavourites()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertFavourites(items: List<FavouriteEntity>)

    @Query("SELECT * FROM history") fun history(): List<HistoryEntity>
    @Query("DELETE FROM history") fun clearHistory()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertHistory(items: List<HistoryEntity>)

    @Query("SELECT * FROM playlists") fun playlists(): List<PlaylistEntity>
    @Query("DELETE FROM playlists") fun clearPlaylists()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertPlaylists(items: List<PlaylistEntity>)

    @Query("SELECT * FROM playlist_items ORDER BY position") fun playlistItems(): List<PlaylistItemEntity>
    @Query("DELETE FROM playlist_items") fun clearPlaylistItems()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertPlaylistItems(items: List<PlaylistItemEntity>)

    @Query("SELECT * FROM downloads") fun downloads(): List<DownloadEntity>
    @Query("DELETE FROM downloads") fun clearDownloads()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertDownloads(items: List<DownloadEntity>)

    @Query("SELECT * FROM books") fun books(): List<BookEntity>
    @Query("DELETE FROM books") fun clearBooks()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertBooks(items: List<BookEntity>)

    @Query("SELECT * FROM vault") fun vault(): List<VaultEntity>
    @Query("DELETE FROM vault") fun clearVault()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertVault(items: List<VaultEntity>)

    @Query("SELECT * FROM site_settings") fun siteSettings(): List<SiteSettingEntity>
    @Query("DELETE FROM site_settings") fun clearSiteSettings()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertSiteSettings(items: List<SiteSettingEntity>)

    @Query("SELECT * FROM settings") fun settings(): List<SettingEntity>
    @Query("DELETE FROM settings") fun clearSettings()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertSettings(items: List<SettingEntity>)

    @Query("SELECT * FROM positions") fun positions(): List<PositionEntity>
    @Query("DELETE FROM positions") fun clearPositions()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun insertPositions(items: List<PositionEntity>)

    @Query("SELECT value FROM meta WHERE `key` = :key") fun meta(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putMeta(item: MetaEntity)
}
