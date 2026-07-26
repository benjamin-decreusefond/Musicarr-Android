package com.musicarr.android.offline

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.musicarr.android.data.MusicarrRepository
import com.musicarr.android.data.SessionManager
import com.musicarr.android.data.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Ties the two halves of "keep this offline" together: the server-side pin
 * (which also stops auto-cleanup deleting the file) and the on-device
 * download.
 *
 * Order matters. Pinning registers with the server *first*, so the file is
 * protected before we spend the user's bandwidth on it — the reverse order has
 * a window where cleanup could delete the track mid-download. Unpinning
 * removes the local copy first, because that is the part the user can see and
 * is asking for (freeing space); the server pin is bookkeeping that can be
 * retried.
 */
// @OptIn, not @UnstableApi: this consumes the media3 opt-in rather than
// propagating it to every caller (annotating the class would force the
// requirement onto MusicarrApp and from there onto every screen).
@androidx.annotation.OptIn(UnstableApi::class)
class OfflineManager(
    private val context: Context,
    private val repository: MusicarrRepository,
    private val session: SessionManager,
) {
    private val _pinned = MutableStateFlow<Set<Long>>(emptySet())

    /** Track ids pinned by this user, whether or not the audio has landed yet. */
    val pinned: StateFlow<Set<Long>> = _pinned

    private val _downloaded = MutableStateFlow<Set<Long>>(emptySet())

    /** Track ids fully on device and playable with no network. */
    val downloaded: StateFlow<Set<Long>> = _downloaded

    /** Refresh both sets. The server is authoritative for pins; the download
     *  index is authoritative for what is actually on disk. */
    suspend fun refresh() {
        repository.offlinePins().onSuccess { tracks ->
            _pinned.value = tracks.map { it.trackId }.toSet()
        }
        refreshDownloaded()
    }

    fun refreshDownloaded() {
        _downloaded.value = OfflineDownloads.completedTrackIds(context)
    }

    suspend fun pin(track: Track): Result<Unit> {
        val id = track.trackId
        if (id == 0L) return Result.failure(IllegalArgumentException("Unknown track"))
        return repository.pinOffline(track).map {
            _pinned.value = _pinned.value + id
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

    /** Bytes the offline library currently occupies. */
    fun bytesUsed(): Long = OfflineCache.bytesUsed(context)
}
