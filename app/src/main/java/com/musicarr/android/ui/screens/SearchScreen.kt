package com.musicarr.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.musicarr.android.MusicarrApp
import com.musicarr.android.data.SearchResults
import com.musicarr.android.ui.LocalPlayer
import com.musicarr.android.ui.MediaCard
import com.musicarr.android.ui.SectionHeader
import com.musicarr.android.ui.TrackRow
import com.musicarr.android.ui.playOrDownload
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    snackbar: SnackbarHostState,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
) {
    val repo = MusicarrApp.instance.repository
    val player = LocalPlayer.current
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }

    // Debounced live search against /api/search.
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { results = null; error = null; return@LaunchedEffect }
        searching = true
        delay(400)
        repo.search(q)
            .onSuccess { results = it; error = null }
            .onFailure { error = it.message }
        searching = false
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Artists, albums, songs…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        val r = results
        when {
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            r == null -> Text(
                if (searching) "Searching…" else "Search the Deezer catalog through your server.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (r.artists.isNotEmpty()) {
                    item { SectionHeader("Artists") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(r.artists, key = { it.id }) { a ->
                                MediaCard(a.name, null, a.picture, circle = true, onClick = { onOpenArtist(a.id) })
                            }
                        }
                    }
                }
                if (r.albums.isNotEmpty()) {
                    item { SectionHeader("Albums") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(r.albums, key = { it.id }) { a ->
                                MediaCard(a.title, a.artist, a.cover, onClick = { onOpenAlbum(a.id) })
                            }
                        }
                    }
                }
                if (r.tracks.isNotEmpty()) {
                    item { SectionHeader("Tracks") }
                    items(r.tracks, key = { it.trackId }) { t ->
                        TrackRow(t, onClick = { playOrDownload(r.tracks, t, player, repo, scope, snackbar) })
                    }
                }
            }
        }
    }
}
