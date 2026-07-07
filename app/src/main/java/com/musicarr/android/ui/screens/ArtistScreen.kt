package com.musicarr.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import com.musicarr.android.ui.MediaCard
import com.musicarr.android.ui.SectionHeader
import com.musicarr.android.ui.TrackRow
import com.musicarr.android.ui.playOrDownload
import com.musicarr.android.ui.rememberLoad

@Composable
fun ArtistScreen(
    artistId: Long,
    snackbar: SnackbarHostState,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
) {
    val repo = MusicarrApp.instance.repository
    val player = LocalPlayer.current
    val scope = rememberCoroutineScope()
    val (state, refresh) = rememberLoad(artistId) { repo.artist(artistId) }
    when (state) {
        is LoadState.Loading -> LoadingBox()
        is LoadState.Failed -> ErrorBox(state.message, refresh)
        is LoadState.Ready -> {
            val d = state.data
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CoverImage(d.artist.picture, 96.dp, shape = CircleShape)
                        Column {
                            Text(d.artist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            d.artist.nb_fan?.let {
                                Text("%,d fans".format(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (d.top.isNotEmpty()) {
                    item { SectionHeader("Popular") }
                    items(d.top, key = { it.trackId }) { t ->
                        TrackRow(t, onClick = { playOrDownload(d.top, t, player, repo, scope, snackbar) })
                    }
                }
                if (d.albums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(d.albums, key = { it.id }) { a ->
                                MediaCard(a.title, a.release_date?.take(4), a.cover, onClick = { onOpenAlbum(a.id) })
                            }
                        }
                    }
                }
                if (d.related.isNotEmpty()) {
                    item { SectionHeader("Similar artists") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(d.related, key = { it.id }) { a ->
                                MediaCard(a.name, null, a.picture, circle = true, onClick = { onOpenArtist(a.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}
