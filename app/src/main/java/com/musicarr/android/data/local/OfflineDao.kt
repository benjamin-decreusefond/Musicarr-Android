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
