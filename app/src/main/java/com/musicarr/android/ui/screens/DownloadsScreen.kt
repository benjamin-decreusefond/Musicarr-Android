package com.musicarr.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicarr.android.MusicarrApp
import com.musicarr.android.data.Download
import com.musicarr.android.ui.CoverImage
import com.musicarr.android.ui.ErrorBox
import com.musicarr.android.ui.LoadingBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Live view of the server's download queue; refreshes while on screen. */
@Composable
fun DownloadsScreen(snackbar: SnackbarHostState) {
    val repo = MusicarrApp.instance.repository
    val scope = rememberCoroutineScope()
    var downloads by remember { mutableStateOf<List<Download>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            repo.downloads()
                .onSuccess { downloads = it; error = null }
                .onFailure { if (downloads == null) error = it.message }
            delay(4000)
        }
    }

    val list = downloads
    when {
        error != null -> ErrorBox(error!!) { error = null; downloads = null }
        list == null -> LoadingBox()
        list.isEmpty() -> Text(
            "No downloads yet.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(list, key = { it.id }) { d ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CoverImage(d.cover, 48.dp)
                    Column(Modifier.weight(1f)) {
                        Text(d.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(statusLabel(d.status), d.username?.let { "for $it" }, d.detail)
                                .joinToString(" — "),
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor(d.status),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (d.status == "downloading" || d.status == "importing") {
                            LinearProgressIndicator(
                                progress = { (d.progress / 100.0).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                        }
                    }
                    if (d.status == "error" || d.status == "not_found") {
                        IconButton(onClick = {
                            scope.launch {
                                repo.retryDownload(d.id)
                                    .onSuccess { snackbar.showSnackbar("Retrying ${d.label}") }
                                    .onFailure { snackbar.showSnackbar(it.message ?: "Retry failed") }
                            }
                        }) { Icon(Icons.Default.Refresh, contentDescription = "Retry") }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            repo.dismissDownload(d.id)
                                .onFailure { snackbar.showSnackbar(it.message ?: "Failed") }
                        }
                    }) { Icon(Icons.Default.Close, contentDescription = "Dismiss") }
                }
            }
        }
    }
}

private fun statusLabel(status: String) = when (status) {
    "searching" -> "Searching Soulseek…"
    "downloading" -> "Downloading"
    "importing" -> "Importing"
    "done" -> "Done"
    "not_found" -> "Not found"
    "error" -> "Failed"
    else -> status
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    "done" -> MaterialTheme.colorScheme.primary
    "error", "not_found" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
