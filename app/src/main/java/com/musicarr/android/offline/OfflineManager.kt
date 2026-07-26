package com.musicarr.android.offline

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.musicarr.android.data.MusicarrRepository
import com.musicarr.android.data.SessionManager
import com.musicarr.android.data.Playlist
import com.musicarr.android.data.Track
import com.musicarr.android.data.local.OfflineCollectionEntity
import com.musicarr.android.data.local.OfflineDao
import com.musicarr.android.data.local.OfflinePlaylistEntity
import com.musicarr.android.data.local.OfflinePlaylistItemEntity
import com.musicarr.android.data.local.OfflineTrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * Ties together the three places "keep this offline" lives: the server pin
 * (which also stops auto-cleanup deleting the file), the downloaded audio, and
 * the local catalogue that lets the UI render it all with no network.
 *
 * Ordering matters when pinning. The server registers first, so the file is
 * protected before we spend the user's bandwidth on it — the reverse has a
 * window where cleanup could delete the track mid-download. Unpinning removes
 * the local copy first, because that is the part the user asked for (freeing
 * space); the server pin is bookkeeping that can be retried.
 */
// @OptIn, not @UnstableApi: this consumes the media3 opt-in rather than
// propagating it to every caller (annotating the class would force the
// requirement onto MusicarrApp and from there onto every screen).
@androidx.annotation.OptIn(UnstableApi::class)
class OfflineManager(
    private val context: Context,
    private val repository: MusicarrRepository,
    private val session: SessionManager,
    private val dao: OfflineDao,
) {
    private val _pinned = MutableStateFlow<Set<Long>>(emptySet())

    /** Track ids that should be on this device, whether or not the audio has
     *  landed yet. Includes tracks pulled in by a pinned album or playlist. */
    val pinned: StateFlow<Set<Long>> = _pinned

    private val _downloaded = MutableStateFlow<Set<Long>>(emptySet())

    /** Track ids fully on device and playable with no network. */
    val downloaded: StateFlow<Set<Long>> = _downloaded

    init {
        // Without this, `downloaded` only changed on a full refresh (app start,
        // reconnect), so a track's icon stayed on "downloading" until the next
        // launch even though the file had already landed. The DownloadManager
        // tells us the moment anything changes state.
        OfflineDownloads.manager(context).addListener(
            object : androidx.media3.exoplayer.offline.DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: androidx.media3.exoplayer.offline.DownloadManager,
                    download: androidx.media3.exoplayer.offline.Download,
                    finalException: Exception?,
                ) = refreshDownloaded()

                override fun onDownloadRemoved(
                    downloadManager: androidx.media3.exoplayer.offline.DownloadManager,
                    download: androidx.media3.exoplayer.offline.Download,
                ) = refreshDownloaded()
            }
        )
        refreshDownloaded()
    }

    /** Pinned albums/playlists, as "kind:id" keys. */
    val collections: Flow<Set<String>> =
        dao.observeCollections().map { list -> list.map { "${it.kind}:${it.collectionId}" }.toSet() }

    /** The offline catalogue, for browsing without a network. */
    val catalogue: Flow<List<Track>> =
        dao.observeTracks().map { rows -> rows.map { it.toTrack() } }

    /**
     * Reconcile with the server: ask what *should* be on this device, then make
     * it so.
     *
     * The server returns the effective set — explicit pins plus the expansion
     * of every pinned album/playlist — so this is a plain diff. That's what
     * makes a playlist gaining a song Just Work: it appears in the desired set
     * and gets downloaded, with no client-side notion of collections.
     *
     * A failure here leaves everything as it is. The device keeps playing what
     * it already has, which is the entire point.
     */
    suspend fun refresh(): Result<Unit> {
        val desired = repository.offlinePins().getOrElse { return Result.failure(it) }
        // Only tracks the server can actually serve are worth downloading; the
        // rest are pinned-but-gone and must be dropped locally.
        val (servable, gone) = desired.partition { it.available }
        val wanted = servable.map { it.trackId }.toSet()

        dao.replaceTracks(desired.map { OfflineTrackEntity.from(it) })
        repository.offlineCollections().onSuccess { list ->
            dao.replaceCollections(list.map { OfflineCollectionEntity(it.kind, it.collection_id) })
        }
        _pinned.value = wanted

        val onDevice = OfflineDownloads.completedTrackIds(context)
        // Download what's missing...
        for (id in wanted - onDevice) {
            OfflineDownloads.pin(context, id, session.downloadUrl(id))
        }
        // ...and reclaim anything no longer wanted, including tracks the server
        // has since deleted (auto-cleanup can still remove an unpinned track
        // that a stale local copy refers to).
        for (id in onDevice - wanted) OfflineDownloads.unpin(context, id)
        for (t in gone) OfflineDownloads.unpin(context, t.trackId)

        refreshDownloaded()
        return Result.success(Unit)
    }

    fun refreshDownloaded() {
        _downloaded.value = OfflineDownloads.completedTrackIds(context)
    }

    suspend fun pin(track: Track): Result<Unit> {
        val id = track.trackId
        if (id == 0L) return Result.failure(IllegalArgumentException("Unknown track"))
        return repository.pinOffline(track).map {
            _pinned.value = _pinned.value + id
            dao.upsertTracks(listOf(OfflineTrackEntity.from(track)))
            OfflineDownloads.pin(context, id, session.downloadUrl(id))
        }
    }

    suspend fun unpin(trackId: Long): Result<Unit> {
        OfflineDownloads.unpin(context, trackId)
        _downloaded.value = _downloaded.value - trackId
        return repository.unpinOffline(trackId).map {
            _pinned.value = _pinned.value - trackId
        }
    }

    /**
     * Pin a whole album or playlist. The server records the collection itself,
     * so the device follows it as it changes; [refresh] then pulls in whatever
     * tracks that currently means.
     */
    suspend fun pinCollection(kind: String, id: Long): Result<Unit> =
        repository.pinCollection(kind, id).mapCatching { refresh().getOrThrow() }

    suspend fun unpinCollection(kind: String, id: Long): Result<Unit> =
        repository.unpinCollection(kind, id).mapCatching { refresh().getOrThrow() }

    /* ------------------------------------------------- Offline reads */
    // What the screens fall back to when the server can't be reached. Each
    // returns only what can actually be played — there's no value in listing a
    // track whose audio isn't here.

    suspend fun localTracks(): List<Track> = dao.tracks().map { it.toTrack() }

    suspend fun searchLocal(query: String): List<Track> =
        dao.searchTracks(query.trim()).map { it.toTrack() }

    suspend fun localAlbum(albumId: Long): List<Track> =
        dao.tracksInAlbum(albumId).map { it.toTrack() }

    /** Playlists seen online, cached for offline browsing. */
    suspend fun localPlaylists(): List<Playlist> = dao.cachedPlaylists().map {
        Playlist(id = it.playlistId, name = it.name, cover = it.cover)
    }

    suspend fun localPlaylist(id: Long): Playlist? {
        val meta = dao.cachedPlaylist(id) ?: return null
        val tracks = dao.cachedPlaylistTracks(id).map { it.toTrack() }
        return Playlist(id = meta.playlistId, name = meta.name, cover = meta.cover, count = tracks.size, tracks = tracks)
    }

    /** Remember a playlist the user just opened online, so it renders offline. */
    suspend fun cachePlaylist(playlist: Playlist) {
        dao.cachePlaylist(
            OfflinePlaylistEntity(playlist.id, playlist.name, playlist.cover),
            playlist.tracks.mapIndexed { i, t -> OfflinePlaylistItemEntity(playlist.id, i, t.trackId) },
        )
    }

    /** Bytes the offline library currently occupies. */
    fun bytesUsed(): Long = OfflineCache.bytesUsed(context)

    /**
     * Delete every downloaded file, keeping the server-side pins.
     *
     * "Free up space" and "I no longer want these offline" are different
     * intentions: this is the former, so a later sync will re-download them.
     * Unpinning is how you express the latter.
     */
    fun clearDownloads() {
        OfflineDownloads.removeAll(context)
        _downloaded.value = emptySet()
    }

    /** Change the quality pinned tracks are fetched at, and re-fetch anything
     *  already on the device so the library ends up consistent rather than a
     *  mix of qualities from whenever each track happened to be pinned. */
    suspend fun setQuality(quality: DownloadQuality) {
        if (quality == session.downloadQuality) return
        session.setDownloadQuality(quality)
        val current = OfflineDownloads.completedTrackIds(context)
        for (id in current) {
            OfflineDownloads.unpin(context, id)
            OfflineDownloads.pin(context, id, session.downloadUrl(id))
        }
    }
}
