package com.musicarr.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.musicarr.android.MusicarrApp
import com.musicarr.android.ui.CoverImage
import com.musicarr.android.ui.LocalPlayer
import com.musicarr.android.ui.SectionHeader
import com.musicarr.android.ui.TrackRow
import com.musicarr.android.ui.formatDuration
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(snackbar: SnackbarHostState) {
    val player = LocalPlayer.current
    val repo = MusicarrApp.instance.repository
    val scope = rememberCoroutineScope()
    val now by player.nowPlaying.collectAsState()
    val queue by player.queue.collectAsState()

    // Seed the heart from the queued track's known state; toggle optimistically.
    val queueTrack = queue.firstOrNull { it.trackId == now.trackId }
    var liked by remember(now.trackId) { mutableStateOf(queueTrack?.favorite ?: false) }

    if (now.trackId == 0L) {
        Text(
            "Nothing playing — pick a song.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CoverImage(now.cover, 280.dp)
                Text(
                    now.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 20.dp)
                )
                Text(now.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)

                // Seek bar
                val duration = now.durationMs.coerceAtLeast(1)
                Slider(
                    value = (now.positionMs.toFloat() / duration).coerceIn(0f, 1f),
                    onValueChange = { player.seekTo((it * duration).toLong()) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration((now.positionMs / 1000).toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDuration((now.durationMs / 1000).toInt()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Transport controls
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = player::toggleShuffle) {
                        Icon(
                            Icons.Default.Shuffle, contentDescription = "Shuffle",
                            tint = if (now.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = player::previous, enabled = true) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
                    }
                    FilledIconButton(onClick = player::togglePlayPause, modifier = Modifier.size(64.dp)) {
                        Icon(
                            if (now.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (now.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    IconButton(onClick = player::next, enabled = now.hasNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = player::cycleRepeat) {
                        Icon(
                            if (now.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (now.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        val track = queueTrack ?: return@IconButton
                        val target = !liked
                        liked = target
                        scope.launch {
                            repo.setFavorite(track, target).onFailure {
                                liked = !target
                                snackbar.showSnackbar(it.message ?: "Failed")
                            }
                        }
                    }) {
                        Icon(
                            if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (liked) "Unlike" else "Like",
                            tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        val upNext = queue.dropWhile { it.trackId != now.trackId }.drop(1)
        if (upNext.isNotEmpty()) {
            item { SectionHeader("Up next") }
            items(upNext, key = { it.trackId }) { t ->
                TrackRow(t, onClick = { player.playFromQueue(t.trackId) })
            }
        }
    }
}
