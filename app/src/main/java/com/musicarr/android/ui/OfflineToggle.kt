package com.musicarr.android.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.musicarr.android.MusicarrApp
import com.musicarr.android.data.Track
import kotlinx.coroutines.launch

/**
 * "Keep on this device" control for a track, with three states: not pinned,
 * pinned but still downloading, and on the device.
 *
 * Only shown for tracks the server actually holds — pinning a track whose
 * audio the server doesn't have yet would produce a pin that can never
 * download, which reads as a broken control.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun OfflineToggle(track: Track, snackbar: SnackbarHostState, modifier: Modifier = Modifier) {
    if (!track.available) return
    val offline = MusicarrApp.instance.offline
    val scope = rememberCoroutineScope()
    val pinned by offline.pinned.collectAsStateWithLifecycle()
    val downloaded by offline.downloaded.collectAsStateWithLifecycle()

    val id = track.trackId
    val isPinned = id in pinned
    val isDownloaded = id in downloaded

    IconButton(
        onClick = {
            scope.launch {
                val result = if (isPinned) offline.unpin(id) else offline.pin(track)
                result.fold(
                    onSuccess = {
                        snackbar.showSnackbar(
                            if (isPinned) "Removed from this device" else "Keeping \"${track.title}\" on this device"
                        )
                    },
                    onFailure = { snackbar.showSnackbar(it.message ?: "Could not change offline status") },
                )
            }
        },
        modifier = modifier,
    ) {
        Icon(
            when {
                isDownloaded -> Icons.Default.DownloadDone
                isPinned -> Icons.Default.Downloading
                else -> Icons.Default.CloudDownload
            },
            contentDescription = when {
                isDownloaded -> "On this device — tap to remove"
                isPinned -> "Downloading to this device"
                else -> "Keep on this device"
            },
            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}
