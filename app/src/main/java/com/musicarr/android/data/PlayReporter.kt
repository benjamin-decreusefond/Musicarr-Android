package com.musicarr.android.data

import com.musicarr.android.data.local.OfflineDao
import com.musicarr.android.data.local.PendingPlayEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reports listens to the server, surviving a lack of connectivity.
 *
 * PlaybackService reports every track change to /api/plays, which feeds
 * history, stats and the "On Repeat" mix. Those calls used to just fail when
 * offline and the listen was lost — precisely when people listen most
 * (commutes, flights). A failed report is now queued and replayed when
 * connectivity returns.
 */
/**
 * The one call [PlayReporter] makes. Narrowing it to an interface keeps the
 * queue-and-replay logic testable without an Android Context, which
 * MusicarrRepository transitively needs.
 */
fun interface PlaySink {
    suspend fun recordPlay(trackId: Long): Result<*>
}

class PlayReporter(
    private val sink: PlaySink,
    private val dao: OfflineDao,
) {
    // One flush at a time: reconnect events and app-start can otherwise race
    // and post the same queued play twice.
    private val flushLock = Mutex()

    /** Report a play, queueing it locally if the server can't be reached. */
    suspend fun record(trackId: Long, atMillis: Long = System.currentTimeMillis()) {
        sink.recordPlay(trackId).onFailure {
            dao.queuePlay(PendingPlayEntity(trackId = trackId, playedAtMillis = atMillis))
        }
    }

    /**
     * Replay queued listens, oldest first.
     *
     * A row is deleted only once the server has accepted it; the first failure
     * stops the flush so the queue keeps its order and nothing is dropped
     * because the server happened to be down for one request.
     */
    suspend fun flush(): Int = flushLock.withLock {
        var sent = 0
        for (play in dao.pendingPlays()) {
            val result = sink.recordPlay(play.trackId)
            if (result.isFailure) break
            dao.deletePendingPlay(play.id)
            sent++
        }
        sent
    }

    suspend fun pendingCount(): Int = dao.pendingPlayCount()
}
