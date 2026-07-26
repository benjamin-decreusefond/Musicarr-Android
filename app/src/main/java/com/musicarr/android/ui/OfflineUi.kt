package com.musicarr.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.musicarr.android.MusicarrApp
import kotlinx.coroutines.launch

/** True while the device has no usable network. */
@Composable
fun rememberOffline(): Boolean {
    val online by MusicarrApp.instance.connectivity.online.collectAsStateWithLifecycle()
    return !online
}

/**
 * Banner shown when there's no network, so a short or empty list reads as
 * "this is your downloaded music" rather than "the app is broken".
 */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            "Offline — showing music saved on this device",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * "Keep on this device" for a whole album or playlist.
 *
 * The server stores the collection rather than a snapshot of its tracks, so
 * pinning a playlist means the device follows it — songs added later are
 * downloaded on the next sync.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun CollectionOfflineButton(
    kind: String,
    id: Long,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val offline = MusicarrApp.instance.offline
    val scope = rememberCoroutineScope()
    val collections by offline.collections.collectAsStateWithLifecycle(initialValue = emptySet())
    val isPinned = "$kind:$id" in collections
    var busy by remember { mutableStateOf(false) }

    TextButton(
        onClick = {
            scope.launch {
                busy = true
                val result = if (isPinned) offline.unpinCollection(kind, id) else offline.pinCollection(kind, id)
                busy = false
                result.fold(
                    onSuccess = {
                        snackbar.showSnackbar(
                            if (isPinned) "Removed from this device" else "Keeping this $kind on this device"
                        )
                    },
                    onFailure = { snackbar.showSnackbar(it.message ?: "Could not change offline status") },
                )
            }
        },
        enabled = !busy,
        modifier = modifier,
    ) {
        Icon(
            when {
                busy -> Icons.Default.Downloading
                isPinned -> Icons.Default.DownloadDone
                else -> Icons.Default.CloudDownload
            },
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            if (isPinned) "On this device" else "Keep offline",
            Modifier.padding(start = 6.dp),
        )
    }
}
