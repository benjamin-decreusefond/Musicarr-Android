package com.musicarr.android.offline

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * The on-device audio store for offline playback.
 *
 * Deliberately uses [NoOpCacheEvictor]: everything in here was explicitly
 * pinned by the user, so nothing may be evicted behind their back. "Offline"
 * that quietly disappears under storage pressure is worse than no offline at
 * all — the whole point is that it's there when there's no network. Freeing
 * space is therefore always an explicit unpin, never an automatic eviction.
 */
@UnstableApi
object OfflineCache {

    /**
     * Cache entries are keyed by track id, never by URL.
     *
     * `/api/stream/{id}` accepts `?fmt=` and `?br=` for the server's optional
     * transcoding, so the same track has several valid URLs. media3's default
     * key is the URI, which would mean a track downloaded as Opus is a cache
     * miss when playback requests the plain URL — and it would silently
     * re-download over the network, i.e. exactly the failure this feature
     * exists to prevent.
     */
    val cacheKeyFactory = CacheKeyFactory { spec: DataSpec ->
        spec.key ?: cacheKeyFor(spec.uri) ?: spec.uri.toString()
    }

    /** `.../api/stream/1234?fmt=opus` -> "track:1234"; null if not a stream URL. */
    fun cacheKeyFor(uri: Uri): String? = cacheKeyForPath(uri.path)

    /** Split out from [cacheKeyFor] so it can be unit tested without android.net.Uri. */
    fun cacheKeyForPath(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        val segments = path.trim('/').split('/')
        val i = segments.indexOf("stream")
        if (i < 0 || i + 1 >= segments.size) return null
        val id = segments[i + 1].toLongOrNull() ?: return null
        return "track:$id"
    }

    fun cacheKeyFor(trackId: Long): String = "track:$trackId"

    /**
     * Where the audio lives. External app-specific storage: it holds gigabytes
     * without counting against the app's internal quota, and the OS deletes it
     * on uninstall. Note this is NOT a cache directory — `externalCacheDir`
     * would let the system reclaim it, defeating the purpose (see above).
     *
     * Because it is an app-specific *files* dir, Android's auto-backup would
     * include it by default, so the backup rules exclude it explicitly —
     * otherwise a user's whole offline library would be uploaded to their
     * cloud backup.
     */
    fun directory(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "offline-audio")

    @Volatile private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            directory(context.applicationContext),
            NoOpCacheEvictor(),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { cache = it }
    }

    /** Bytes currently held on device. */
    fun bytesUsed(context: Context): Long = get(context).cacheSpace
}
