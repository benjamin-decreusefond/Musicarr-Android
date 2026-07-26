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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musicarr.android.MusicarrApp
import com.musicarr.android.ui.CoverImage
import com.musicarr.android.ui.ErrorBox
import com.musicarr.android.ui.LoadState
import com.musicarr.android.ui.LoadingBox
import com.musicarr.android.ui.LocalPlayer
import com.musicarr.android.ui.TrackRow
import com.musicarr.android.ui.dpadFocusable
import com.musicarr.android.ui.CollectionOfflineButton
import com.musicarr.android.ui.OfflineToggle
import com.musicarr.android.ui.playOrDownload
import com.musicarr.android.ui.rememberOffline
import com.musicarr.android.ui.rememberLoad
import kotlinx.coroutines.launch

@Composable
fun PlaylistsScreen(onOpenPlaylist: (Long) -> Unit) {
    val repo = MusicarrApp.instance.repository
    val offlineNow = rememberOffline()
    val (state, refresh) = rememberLoad(offlineNow) {
        if (offlineNow) Result.success(MusicarrApp.instance.offline.localPlaylists())
        else repo.playlists()
    }
    when (state) {
        is LoadState.Loading -> LoadingBox()
        is LoadState.Failed -> ErrorBox(state.message, refresh)
        is LoadState.Ready -> {
            if (state.data.isEmpty()) {
                Text(
                    "No playlists yet — create one from the web app.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                return
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(state.data, key = { it.id }) { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().dpadFocusable(onClick = { onOpenPlaylist(p.id) })
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CoverImage(p.cover, 56.dp, fallback = Icons.Default.QueueMusic)
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = buildString {
                                append("${p.count ?: 0} track${if ((p.count ?: 0) == 1) "" else "s"}")
                                if (p.shared && p.owner_name != null) append(" — shared by ${p.owner_name}")
                            }
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(playlistId: Long, snackbar: SnackbarHostState) {
    val repo = MusicarrApp.instance.repository
    val player = LocalPlayer.current
    val scope = rememberCoroutineScope()
    val offlineNow = rememberOffline()
    val (state, refresh) = rememberLoad(playlistId, offlineNow) {
        if (offlineNow) {
            MusicarrApp.instance.offline.localPlaylist(playlistId)
                ?.let { Result.success(it) }
                ?: Result.failure(Exception("This playlist isn't saved on this device"))
        } else {
            // Cache it as seen, so it renders offline later.
            repo.playlist(playlistId).onSuccess { MusicarrApp.instance.offline.cachePlaylist(it) }
        }
    }
    when (state) {
        is LoadState.Loading -> LoadingBox()
        is LoadState.Failed -> ErrorBox(state.message, refresh)
        is LoadState.Ready -> {
            val p = state.data
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CoverImage(p.cover, 96.dp, fallback = Icons.Default.QueueMusic)
                            Column {
                                Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                val playable = p.tracks.count { it.available }
                                Text(
                                    "${p.tracks.size} tracks — $playable playable" + (p.owner_name?.takeIf { !p.is_owner }?.let { " — by $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { player.play(p.tracks) }, enabled = p.tracks.any { it.available }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Text("Play", Modifier.padding(start = 4.dp))
                            }
                            OutlinedButton(
                                onClick = { player.play(p.tracks.shuffled()) },
                                enabled = p.tracks.any { it.available }
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null)
                                Text("Shuffle", Modifier.padding(start = 4.dp))
                            }
                            CollectionOfflineButton("playlist", p.id, snackbar)
                        }
                    }
                }
                items(p.tracks, key = { it.trackId }) { t ->
                    TrackRow(
                        t,
                        onClick = { playOrDownload(p.tracks, t, player, repo, scope, snackbar) },
                        trailing = { OfflineToggle(t, snackbar) },
                    )
                }
            }
        }
    }
}
