package com.musicarr.android.offline

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.musicarr.android.MusicarrApp
import java.util.concurrent.Executors

/**
 * Owns the media3 [DownloadManager] that pulls pinned tracks onto the device,
 * and the [DataSource.Factory] playback reads through.
 */
@UnstableApi
object OfflineDownloads {

    @Volatile private var manager: DownloadManager? = null

    fun manager(context: Context): DownloadManager = manager ?: synchronized(this) {
        manager ?: run {
            val app = context.applicationContext
            val http = OkHttpFactory.create(app)
            DownloadManager(
                app,
                androidx.media3.database.StandaloneDatabaseProvider(app),
                OfflineCache.get(app),
                http,
                // Several small audio files at once; the bottleneck is the
                // server, not the device.
                Executors.newFixedThreadPool(3),
            ).apply {
                maxParallelDownloads = 3
            }.also { manager = it }
        }
    }

    /**
     * Factory for playback. Reads from the offline cache when the track is
     * there and falls back to the network otherwise, so a pinned track plays
     * with no connectivity while an unpinned one still streams normally.
     *
     * FLAG_IGNORE_CACHE_ON_ERROR keeps a corrupt or partial cache entry from
     * breaking playback outright — it falls through to the network instead.
     */
    fun playbackDataSourceFactory(context: Context): DataSource.Factory {
        val app = context.applicationContext
        return CacheDataSource.Factory()
            .setCache(OfflineCache.get(app))
            .setUpstreamDataSourceFactory(OkHttpFactory.create(app))
            .setCacheKeyFactory(OfflineCache.cacheKeyFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Queue a track for download. [url] already carries the chosen quality. */
    fun pin(context: Context, trackId: Long, url: String) {
        val request = DownloadRequest.Builder(OfflineCache.cacheKeyFor(trackId), android.net.Uri.parse(url))
            // Ties the download to the track, not the URL it was fetched with,
            // so a later playback request at a different quality still hits it.
            .setCustomCacheKey(OfflineCache.cacheKeyFor(trackId))
            .build()
        DownloadService.sendAddDownload(
            context, MusicarrDownloadService::class.java, request, /* foreground = */ false
        )
    }

    /** Drop a track's audio from the device. */
    fun unpin(context: Context, trackId: Long) {
        DownloadService.sendRemoveDownload(
            context, MusicarrDownloadService::class.java,
            OfflineCache.cacheKeyFor(trackId), /* foreground = */ false
        )
    }

    /** Ids of tracks fully downloaded and playable offline right now. */
    fun completedTrackIds(context: Context): Set<Long> {
        val out = mutableSetOf<Long>()
        manager(context).downloadIndex.getDownloads(Download.STATE_COMPLETED).use { cursor ->
            while (cursor.moveToNext()) {
                cursor.download.request.id.removePrefix("track:").toLongOrNull()?.let(out::add)
            }
        }
        return out
    }

    /** The app's shared OkHttp client, so downloads carry the session cookie
     *  and go through the same cleartext policy as every other request. */
    private object OkHttpFactory {
        fun create(context: Context): DataSource.Factory =
            androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(
                MusicarrApp.instance.apiClient.okHttp
            )
    }
}
