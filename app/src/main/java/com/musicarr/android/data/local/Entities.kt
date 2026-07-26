package com.musicarr.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.musicarr.android.data.Track

/**
 * A track that should be on this device, with enough metadata to render it
 * with no network.
 *
 * Downloading the audio was only half of offline playback: every screen used
 * to fetch its list from the API, so with no connectivity you had the files
 * but nothing to browse. This table is what the UI reads when the server can't
 * be reached.
 */
@Entity(tableName = "offline_tracks")
data class OfflineTrackEntity(
    @PrimaryKey val trackId: Long,
    val title: String,
    val artist: String?,
    val artistId: Long?,
    val album: String?,
    val albumId: Long?,
    val trackPosition: Int?,
    val duration: Int?,
    val cover: String?,
    /** False when the server no longer holds the audio — the signal to drop
     *  our local copy on the next sync. */
    val available: Boolean,
    /** Server-side pin timestamp, used only for display ordering. */
    val pinnedAt: String?,
) {
    fun toTrack(): Track = Track(
        deezer_id = trackId,
        title = title,
        artist = artist,
        artist_id = artistId,
        album = album,
        album_id = albumId,
        track_position = trackPosition,
        duration = duration,
        cover = cover,
        available = available,
    )

    companion object {
        fun from(track: Track, pinnedAt: String? = null) = OfflineTrackEntity(
            trackId = track.trackId,
            title = track.title,
            artist = track.artist,
            artistId = track.artist_id,
            album = track.album,
            albumId = track.album_id,
            trackPosition = track.track_position,
            duration = track.duration,
            cover = track.cover,
            available = track.available,
            pinnedAt = pinnedAt,
        )
    }
}

/**
 * A play that happened while the device was offline.
 *
 * PlaybackService reports every track change to /api/plays, which feeds
 * history, stats and the "On Repeat" mix. Offline those calls simply failed
 * and the listen was lost — precisely when people listen most (commutes,
 * flights). Queued here and flushed when connectivity returns.
 */
@Entity(tableName = "pending_plays")
data class PendingPlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    /** Wall-clock time of the listen. Kept for ordering; the server timestamps
     *  a play when it receives it, so a long offline stretch replays in the
     *  right order even though the recorded times shift. */
    val playedAtMillis: Long,
)

/** An album or playlist pinned for offline playback, mirrored locally so the
 *  pin state renders without the network. */
@Entity(tableName = "offline_collections", primaryKeys = ["kind", "collectionId"])
data class OfflineCollectionEntity(
    val kind: String,
    val collectionId: Long,
)
