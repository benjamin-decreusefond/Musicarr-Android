package com.musicarr.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicarr.android.MusicarrApp
import com.musicarr.android.data.HomeFeed
import com.musicarr.android.data.Mixes
import com.musicarr.android.data.Track
import com.musicarr.android.ui.ErrorBox
import com.musicarr.android.ui.LoadState
import com.musicarr.android.ui.LoadingBox
import com.musicarr.android.ui.OfflineBanner
import com.musicarr.android.ui.LocalPlayer
import com.musicarr.android.ui.MediaCard
import com.musicarr.android.ui.SectionHeader
import com.musicarr.android.ui.TrackRow
import com.musicarr.android.ui.playOrDownload
import com.musicarr.android.ui.rememberLoad
import com.musicarr.android.ui.rememberOffline
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class HomeData(
    val feed: HomeFeed,
    val mixes: Mixes,
    val history: List<Track>,
)

@Composable
fun HomeScreen(
    snackbar: SnackbarHostState,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
) {
    val repo = MusicarrApp.instance.repository
    val player = LocalPlayer.current
    val scope = rememberCoroutineScope()

    val offlineNow = rememberOffline()
    val (state, refresh) = rememberLoad(offlineNow) {
        // The Home feed is computed server-side (mixes, trending) and has no
        // local equivalent. Offline, the honest thing to show is the music
        // that's actually on the device.
        if (offlineNow) {
            val local = MusicarrApp.instance.offline.localTracks()
            return@rememberLoad Result.success(HomeData(HomeFeed(tracks = local), Mixes(), emptyList()))
        }
        coroutineScope {
            val feed = async { repo.home() }
            val mixes = async { repo.mixes() }
            val history = async { repo.history() }
            // The feed is the page's backbone; mixes/history degrade gracefully.
            feed.await().map {
                HomeData(it, mixes.await().getOrDefault(Mixes()), history.await().getOrDefault(emptyList()))
            }
        }
    }

    if (offlineNow) OfflineBanner()
    when (state) {
        is LoadState.Loading -> LoadingBox()
        is LoadState.Failed -> ErrorBox(state.message, refresh)
        is LoadState.Ready -> {
            val data = state.data
            val mixList = data.mixes.smart + data.mixes.daily
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (mixList.isNotEmpty()) {
                    item { SectionHeader("Made for you") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(mixList, key = { it.key }) { mix ->
                                MediaCard(mix.title, mix.subtitle, mix.cover, cardWidth = 150.dp, onClick = { player.play(mix.tracks) })
                            }
                        }
                    }
                }
                if (data.history.isNotEmpty()) {
                    item { SectionHeader("Recently played") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(data.history, key = { it.trackId }) { t ->
                                MediaCard(t.title, t.artist, t.cover, onClick = {
                                    playOrDownload(data.history, t, player, repo, scope, snackbar)
                                })
                            }
                        }
                    }
                }
                if (data.feed.albums.isNotEmpty()) {
                    item { SectionHeader("Trending albums") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(data.feed.albums, key = { it.id }) { a ->
                                MediaCard(a.title, a.artist, a.cover, onClick = { onOpenAlbum(a.id) })
                            }
                        }
                    }
                }
                if (data.feed.artists.isNotEmpty()) {
                    item { SectionHeader("Trending artists") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(data.feed.artists, key = { it.id }) { a ->
                                MediaCard(a.name, null, a.picture, circle = true, onClick = { onOpenArtist(a.id) })
                            }
                        }
                    }
                }
                if (data.feed.tracks.isNotEmpty()) {
                    item { SectionHeader("Trending tracks") }
                    items(data.feed.tracks, key = { it.trackId }) { t ->
                        TrackRow(t, onClick = { playOrDownload(data.feed.tracks, t, player, repo, scope, snackbar) })
                    }
                }
            }
        }
    }
}
