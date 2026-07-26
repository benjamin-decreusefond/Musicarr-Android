package com.musicarr.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineDao {

    /* ------------------------------------------------------------ Tracks */

    @Query("SELECT * FROM offline_tracks ORDER BY pinnedAt DESC, title COLLATE NOCASE")
    fun observeTracks(): Flow<List<OfflineTrackEntity>>

    @Query("SELECT * FROM offline_tracks ORDER BY pinnedAt DESC, title COLLATE NOCASE")
    suspend fun tracks(): List<OfflineTrackEntity>

    @Query(
        """
        SELECT * FROM offline_tracks
        WHERE title LIKE '%' || :q || '%'
           OR artist LIKE '%' || :q || '%'
           OR album LIKE '%' || :q || '%'
        ORDER BY title COLLATE NOCASE
        """
    )
    suspend fun searchTracks(q: String): List<OfflineTrackEntity>

    @Query("SELECT * FROM offline_tracks WHERE albumId = :albumId ORDER BY trackPosition")
    suspend fun tracksInAlbum(albumId: Long): List<OfflineTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<OfflineTrackEntity>)

    @Query("DELETE FROM offline_tracks WHERE trackId NOT IN (:keep)")
    suspend fun deleteTracksNotIn(keep: List<Long>)

    @Query("DELETE FROM offline_tracks")
    suspend fun deleteAllTracks()

    /**
     * Replace the catalogue with the server's view of it, in one transaction so
     * a crash mid-sync can't leave a half-written catalogue that the UI would
     * then render as the user's offline library.
     */
    @Transaction
    suspend fun replaceTracks(tracks: List<OfflineTrackEntity>) {
        if (tracks.isEmpty()) deleteAllTracks() else deleteTracksNotIn(tracks.map { it.trackId })
        upsertTracks(tracks)
    }

    /* ------------------------------------------------------- Collections */

    @Query("SELECT * FROM offline_collections")
    fun observeCollections(): Flow<List<OfflineCollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollections(collections: List<OfflineCollectionEntity>)

    @Query("DELETE FROM offline_collections")
    suspend fun deleteAllCollections()

    @Transaction
    suspend fun replaceCollections(collections: List<OfflineCollectionEntity>) {
        deleteAllCollections()
        upsertCollections(collections)
    }

    /* -------------------------------------------- Cached playlists */

    @Query("SELECT * FROM offline_playlists ORDER BY name COLLATE NOCASE")
    suspend fun cachedPlaylists(): List<OfflinePlaylistEntity>

    @Query("SELECT * FROM offline_playlists WHERE playlistId = :id")
    suspend fun cachedPlaylist(id: Long): OfflinePlaylistEntity?

    /**
     * The tracks of a cached playlist that are actually on this device.
     *
     * Joining against offline_tracks is the point: a playlist member that was
     * never downloaded can't be played offline, so showing it would only be a
     * row that does nothing when tapped.
     */
    @Query(
        """
        SELECT t.* FROM offline_playlist_items i
        JOIN offline_tracks t ON t.trackId = i.trackId
        WHERE i.playlistId = :id ORDER BY i.position
        """
    )
    suspend fun cachedPlaylistTracks(id: Long): List<OfflineTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: OfflinePlaylistEntity)

    @Query("DELETE FROM offline_playlist_items WHERE playlistId = :id")
    suspend fun deletePlaylistItems(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItems(items: List<OfflinePlaylistItemEntity>)

    /** Cache a playlist as it was just seen online. */
    @Transaction
    suspend fun cachePlaylist(playlist: OfflinePlaylistEntity, items: List<OfflinePlaylistItemEntity>) {
        upsertPlaylist(playlist)
        deletePlaylistItems(playlist.playlistId)
        insertPlaylistItems(items)
    }

    /* ----------------------------------------------------- Pending plays */

    @Insert
    suspend fun queuePlay(play: PendingPlayEntity)

    @Query("SELECT * FROM pending_plays ORDER BY playedAtMillis")
    suspend fun pendingPlays(): List<PendingPlayEntity>

    @Query("DELETE FROM pending_plays WHERE id = :id")
    suspend fun deletePendingPlay(id: Long)

    @Query("SELECT COUNT(*) FROM pending_plays")
    suspend fun pendingPlayCount(): Int
}
