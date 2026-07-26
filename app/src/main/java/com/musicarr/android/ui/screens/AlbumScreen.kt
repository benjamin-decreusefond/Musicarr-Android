package com.musicarr.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
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
import com.musicarr.android.ui.rememberLoad
import kotlinx.coroutines.launch

@Composable
fun AlbumScreen(albumId: Long, snackbar: SnackbarHostState, onOpenArtist: (Long) -> Unit) {
    val repo = MusicarrApp.instance.repository
    val player = LocalPlayer.current
    val scope = rememberCoroutineScope()
    val (state, refresh) = rememberLoad(albumId) { repo.album(albumId) }
    when (state) {
        is LoadState.Loading -> LoadingBox()
        is LoadState.Failed -> ErrorBox(state.message, refresh)
        is LoadState.Ready -> {
            val album = state.data
            // Album tracks from Deezer don't carry the album art; reuse the album's.
            val tracks = album.tracks.map { it.copy(cover = it.cover ?: album.cover, album = it.album ?: album.title) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            CoverImage(album.cover, 112.dp)
                            Column {
                                Text(album.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    album.artist ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = if (album.artist_id != null)
                                        Modifier.dpadFocusable(onClick = { onOpenArtist(album.artist_id) }).padding(2.dp)
                                    else Modifier
                                )
                                val have = tracks.count { it.available }
                                Text(
                                    "${album.nb_tracks ?: tracks.size} tracks — $have on server" + (album.release_date?.let { " — $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { player.play(tracks) }, enabled = tracks.any { it.available }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Text("Play", Modifier.padding(start = 4.dp))
                            }
                            if (tracks.any { !it.available }) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        repo.download("album", album.id)
                                            .onSuccess { snackbar.showSnackbar("Album download queued") }
                                            .onFailure { snackbar.showSnackbar(it.message ?: "Download failed") }
                                    }
                                }) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Text("Download album", Modifier.padding(start = 4.dp))
                                }
                            }
                            // Only offer "keep offline" once the server has at
                            // least some of the album — pinning a release the
                            // server can't serve yet would download nothing.
                            if (tracks.any { it.available }) {
                                CollectionOfflineButton("album", album.id, snackbar)
                            }
                        }
                    }
                }
                items(tracks, key = { it.trackId }) { t ->
                    TrackRow(
                        t,
                        subtitle = t.artist ?: album.artist ?: "",
                        onClick = { playOrDownload(tracks, t, player, repo, scope, snackbar) },
                        trailing = { OfflineToggle(t, snackbar) },
                    )
                }
            }
        }
    }
}
