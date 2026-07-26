package com.musicarr.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.musicarr.android.MusicarrApp
import com.musicarr.android.offline.DownloadQuality
import com.musicarr.android.offline.SAVER_BITRATE_KBPS
import com.musicarr.android.ui.SectionHeader
import com.musicarr.android.ui.dpadFocusable
import kotlinx.coroutines.launch

/** Human-readable byte size: 1.4 GB rather than 1503238553. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return "%.1f %s".format(value, units[unit])
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SettingsScreen(snackbar: SnackbarHostState) {
    val app = MusicarrApp.instance
    val offline = app.offline
    val scope = rememberCoroutineScope()

    val downloaded by offline.downloaded.collectAsStateWithLifecycle()
    var quality by remember { mutableStateOf(app.session.downloadQuality) }
    var bytes by remember { mutableLongStateOf(0L) }
    var confirmClear by remember { mutableStateOf(false) }

    // Recompute whenever the download set changes — that's exactly when the
    // figure on screen would otherwise go stale.
    LaunchedEffect(downloaded) { bytes = offline.bytesUsed() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        SectionHeader("Offline storage")
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatBytes(bytes), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${downloaded.size} track${if (downloaded.size == 1) "" else "s"} saved on this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { confirmClear = true },
                    enabled = downloaded.isNotEmpty(),
                    modifier = Modifier.padding(top = 8.dp).dpadFocusable(),
                ) { Text("Free up space") }
            }
        }

        SectionHeader("Download quality")
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.padding(vertical = 8.dp)) {
                QualityOption(
                    selected = quality == DownloadQuality.ORIGINAL,
                    title = "Original",
                    subtitle = "Exactly as stored on the server. Best quality, largest files.",
                ) {
                    quality = DownloadQuality.ORIGINAL
                    scope.launch { offline.setQuality(DownloadQuality.ORIGINAL) }
                }
                QualityOption(
                    selected = quality == DownloadQuality.SAVER,
                    title = "Space saver",
                    subtitle = "Opus $SAVER_BITRATE_KBPS kbps — roughly a tenth of the size. " +
                        "Needs transcoding enabled on the server; if it isn't, downloads " +
                        "fall back to the original.",
                ) {
                    quality = DownloadQuality.SAVER
                    scope.launch { offline.setQuality(DownloadQuality.SAVER) }
                }
                Text(
                    "Changing this re-downloads what's already saved, so your offline " +
                        "library doesn't end up a mix of qualities.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Free up space?") },
            text = {
                Text(
                    "Removes ${formatBytes(bytes)} of downloaded audio from this device. " +
                        "Your offline choices are kept, so these tracks download again " +
                        "next time the app syncs — unpin them if you want them gone for good."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    offline.clearDownloads()
                    scope.launch { snackbar.showSnackbar("Downloaded audio removed") }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun QualityOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            // The whole row is the target: a bare RadioButton is a tiny,
            // barely-visible focus point from three metres away on a TV.
            .dpadFocusable(onClick = onSelect)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(top = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
