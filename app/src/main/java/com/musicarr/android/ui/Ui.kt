package com.musicarr.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musicarr.android.data.MusicarrRepository
import com.musicarr.android.data.Track
import com.musicarr.android.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val LocalPlayer = staticCompositionLocalOf<PlayerConnection> { error("PlayerConnection not provided") }

/** Green focus ring so the D-pad cursor is obvious on TV (harmless on touch). */
fun Modifier.dpadFocusable(shape: Shape = RoundedCornerShape(8.dp), onClick: (() -> Unit)? = null): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent
    val base = this.border(2.dp, ring, shape).clip(shape)
    if (onClick != null) {
        base.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    } else base
}

@Composable
fun CoverImage(url: String?, size: androidx.compose.ui.unit.Dp, shape: Shape = RoundedCornerShape(8.dp), fallback: ImageVector = Icons.Default.MusicNote) {
    Box(
        modifier = Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrEmpty()) {
            Icon(fallback, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size / 2))
        } else {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

fun formatDuration(seconds: Int?): String {
    val s = seconds ?: return ""
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Tap behaviour shared by every track list: play it if the file is on the
 * server, otherwise queue a Soulseek download and say so.
 */
fun playOrDownload(
    context: List<Track>,
    track: Track,
    player: PlayerConnection,
    repo: MusicarrRepository,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
) {
    if (track.available) {
        player.play(context, track.trackId)
    } else {
        scope.launch {
            val res = repo.download("track", track.trackId)
            snackbar.showSnackbar(
                res.fold(
                    onSuccess = { if (it.alreadyHave) "Already on the server" else "Download queued: ${track.title}" },
                    onFailure = { it.message ?: "Download failed" }
                )
            )
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dpadFocusable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CoverImage(track.cover, 48.dp)
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (track.available) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                subtitle ?: listOfNotNull(track.artist, track.album).joinToString(" — "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (!track.available) {
            val downloading = track.download_status in listOf("searching", "downloading", "importing")
            Icon(
                if (downloading) Icons.Default.Downloading else Icons.Default.Download,
                contentDescription = if (downloading) "Downloading" else "Not downloaded — tap to download",
                tint = if (downloading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(formatDuration(track.duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        trailing?.invoke()
    }
}

@Composable
fun MediaCard(title: String, subtitle: String?, imageUrl: String?, onClick: () -> Unit, circle: Boolean = false, cardWidth: androidx.compose.ui.unit.Dp = 128.dp) {
    val shape = if (circle) CircleShape else RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .width(cardWidth)
            .dpadFocusable(RoundedCornerShape(12.dp), onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = if (circle) Alignment.CenterHorizontally else Alignment.Start
    ) {
        CoverImage(imageUrl, cardWidth - 12.dp, shape, fallback = if (circle) Icons.Default.Person else Icons.Default.Album)
        Text(
            title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)
        )
        if (!subtitle.isNullOrEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text("Retry") }
    }
}
