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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.musicarr.android.ui.playOrDownload
import com.musicarr.android.ui.rememberLoad
import kotlinx.coroutines.launch

private val TABS = listOf("Tracks", "Albums", "Artists", "Liked")

/** The shared on-disk library: tracks, albums, artists — plus your liked songs. */
@Composable
fun LibraryScreen(
    snackbar: SnackbarHostState,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            TABS.forEachIndexed { i, name ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(name) })
            }
        }
        when (tab) {
            0 -> LibraryTracksTab(snackbar)
            1 -> LibraryAlbumsTab(onOpenAlbum)
            2 -> LibraryArtistsTab(onOpenArtist)
            3 -> LikedTab(snackbar)
        }
    }
}

@Composable
private fun LibraryTracksTab(snackbar: SnackbarHostState) {
    val repo = MusicarrApp.instance.repository
    val player = LocalPlayer.current
    val scope = rememberCoroutineScope()
    val (state, refresh) = rememberLoad { repo.library() }
    when (state) {
        is LoadState.Loading -> LoadingBox()
        is LoadState.Failed -> ErrorBox(state.message, refresh)
        is LoadState.Ready -> {
            val tracks = state.data
            if (tracks.isEmpty()) return EmptyHint("Nothing in the library yet — search and download something.")
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(tracks, key = { it.trackId }) { t ->
                    TrackRow(t, onClick = { playOrDownload(tracks, t, player, repo, scope, snackbar) })
                }
            }
        }
    }
}

@Composable
private fun LibraryAlbumsTab(onOpenAlbum: (Long) -> Unit) {
    val repo = MusicarrApp.instance.repository
    val (state, refresh) = rememberLoad { repo.libraryAlbums() }
    when (state) {
        is LoadState.Loading -> LoadingBox()
        is LoadState.Failed -> ErrorBox(state.message, refresh)
        is LoadState.Ready -> {
            if (state.data.isEmpty()) return EmptyHint("No albums on disk yet.")
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(state.data, key = { it.id }) { a ->
                    Row(
                        modifier = Modifier.fillMaxWidth().dpadFocusable(onClick = { onOpenAlbum(a.id) })
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CoverImage(a.cover, 56.dp)
                        Column(Modifier.weight(1f)) {
                            Text(a.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(a.artist, a.count?.let { "$it track${if (it == 1) "" else "s"}" }).joinToString(" — "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryArtistsTab(onOpenArtist: (Long) -> Unit) {
    val repo = MusicarrApp.instance.repository
    val (state, refresh) = rememberLoad { repo.libraryArtists() }
    when (state) {
        is LoadState.Loading -> LoadingBox()
        is LoadState.Failed -> ErrorBox(state.message, refresh)
        is LoadState.Ready -> {
            if (state.data.isEmpty()) return EmptyHint("No artists on disk yet.")
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(state.data, key = { it.id }) { a ->
                    Row(
                        modifier = Modifier.fillMaxWidth().dpadFocusable(onClick = { onOpenArtist(a.id) })
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CoverImage(a.picture, 56.dp, shape = androidx.compose.foundation.shape.CircleShape)
                        Column(Modifier.weight(1f)) {
                            Text(a.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            a.count?.let {
                                Text("$it track${if (it == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LikedTab(snackbar: SnackbarHostState) {
    val repo = MusicarrApp.instance.repository
    val player = LocalPlayer.current
    val scope = rememberCoroutineScope()
    val (state, refresh) = rememberLoad { repo.favorites() }
    when (state) {
        is LoadState.Loading -> LoadingBox()
        is LoadState.Failed -> ErrorBox(state.message, refresh)
        is LoadState.Ready -> {
            val tracks = state.data
            if (tracks.isEmpty()) return EmptyHint("No liked songs yet — tap the heart on the player.")
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(tracks, key = { it.trackId }) { t ->
                    TrackRow(
                        t,
                        onClick = { playOrDownload(tracks, t, player, repo, scope, snackbar) },
                        trailing = {
                            IconButton(onClick = {
                                scope.launch {
                                    repo.setFavorite(t, false)
                                        .onSuccess { refresh() }
                                        .onFailure { snackbar.showSnackbar(it.message ?: "Failed") }
                                }
                            }) {
                                Icon(Icons.Default.Favorite, contentDescription = "Unlike", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}
