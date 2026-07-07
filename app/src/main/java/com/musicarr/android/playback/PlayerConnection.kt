package com.musicarr.android.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.musicarr.android.MusicarrApp
import com.musicarr.android.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** UI-facing snapshot of what's playing. */
data class NowPlaying(
    val trackId: Long = 0,
    val title: String = "",
    val artist: String = "",
    val cover: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

/**
 * Owns the MediaController that talks to [PlaybackService] and republishes the
 * player state as flows Compose can collect. Created by the activity; shared
 * by every screen.
 */
class PlayerConnection(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null

    private val _nowPlaying = MutableStateFlow(NowPlaying())
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        scope.launch {
            val c = MediaController.Builder(context, token).buildAsync().await()
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish()
            })
            publish()
            // Position isn't event-driven; tick it while something plays.
            while (isActive) {
                if (_nowPlaying.value.isPlaying) publish()
                delay(500)
            }
        }
    }

    private fun publish() {
        val c = controller ?: return
        val meta: MediaMetadata? = c.currentMediaItem?.mediaMetadata
        _nowPlaying.value = NowPlaying(
            trackId = c.currentMediaItem?.mediaId?.toLongOrNull() ?: 0,
            title = meta?.title?.toString() ?: "",
            artist = meta?.artist?.toString() ?: "",
            cover = meta?.artworkUri?.toString(),
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.coerceAtLeast(0),
            hasPrevious = c.hasPreviousMediaItem(),
            hasNext = c.hasNextMediaItem(),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
        )
    }

    private fun Track.toMediaItem(): MediaItem {
        val url = MusicarrApp.instance.apiClient.streamUrl(trackId)
        return MediaItem.Builder()
            .setMediaId(trackId.toString())
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(cover?.let(Uri::parse))
                    .build()
            )
            .build()
    }

    /**
     * Replace the queue with the playable (on-disk) subset of [tracks] and
     * start from [startTrackId] (or the top). Returns how many were playable.
     */
    fun play(tracks: List<Track>, startTrackId: Long? = null): Int {
        val c = controller ?: return 0
        val playable = tracks.filter { it.available }
        if (playable.isEmpty()) return 0
        val startIndex = startTrackId?.let { id -> playable.indexOfFirst { it.trackId == id } }
            ?.takeIf { it >= 0 } ?: 0
        _queue.value = playable
        c.setMediaItems(playable.map { it.toMediaItem() }, startIndex, 0)
        c.prepare()
        c.play()
        return playable.size
    }

    fun playFromQueue(trackId: Long) {
        val c = controller ?: return
        val index = _queue.value.indexOfFirst { it.trackId == trackId }
        if (index >= 0) {
            c.seekTo(index, 0)
            c.play()
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNextMediaItem() ?: Unit
    fun previous() {
        val c = controller ?: return
        // Standard behaviour: restart the track unless we're right at its start.
        if (c.currentPosition > 3000 || !c.hasPreviousMediaItem()) c.seekTo(0)
        else c.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs) ?: Unit

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        publish()
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        publish()
    }

    fun release() {
        controller?.release()
        controller = null
        scope.cancel()
    }
}
