package com.musicarr.android.offline

import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.musicarr.android.R

/**
 * Foreground service that runs pinned-track downloads, so they survive the app
 * being backgrounded and show progress in the notification shade.
 *
 * The notification is built here rather than with media3's
 * DownloadNotificationHelper, which lives in media3-ui — a dependency this app
 * doesn't otherwise need (its UI is Compose).
 */
@UnstableApi
class MusicarrDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.offline_channel_name,
    /* channelDescriptionResourceId = */ 0,
) {

    override fun getDownloadManager(): DownloadManager = OfflineDownloads.manager(this)

    // No Scheduler: downloads run while the service is alive rather than being
    // deferred to a system-chosen window. Pinning is an explicit, immediate
    // user action ("I'm about to get on a train"), so waiting for an idle
    // window would be the wrong behaviour.
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int,
    ): Notification {
        val active = downloads.count { it.state == Download.STATE_DOWNLOADING }
        // Download.percentDownloaded is C.PERCENTAGE_UNSET (-1) until the total
        // size is known; show an indeterminate bar rather than a bogus 0%.
        val percent = downloads
            .filter { it.state == Download.STATE_DOWNLOADING && it.percentDownloaded >= 0 }
            .map { it.percentDownloaded }
            .average()
            .takeIf { !it.isNaN() }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.offline_downloading))
            .setContentText(
                resources.getQuantityString(R.plurals.offline_tracks_remaining, active, active)
            )
            .setProgress(100, percent?.toInt() ?: 0, percent == null)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 2001
        const val CHANNEL_ID = "musicarr_offline_downloads"
    }
}
