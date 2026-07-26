package com.musicarr.android.data

import com.musicarr.android.data.local.OfflineCollectionEntity
import com.musicarr.android.data.local.OfflineDao
import com.musicarr.android.data.local.OfflineTrackEntity
import com.musicarr.android.data.local.PendingPlayEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Listens must survive a lack of connectivity. /api/plays feeds history, stats
 * and the "On Repeat" mix; those calls used to fail silently offline, losing
 * the listen exactly when offline playback means people are listening most.
 */
class PlayReporterTest {

    /** In-memory stand-in for the Room DAO. */
    private class FakeDao : OfflineDao {
        val queued = mutableListOf<PendingPlayEntity>()
        private var nextId = 1L

        override suspend fun queuePlay(play: PendingPlayEntity) {
            queued += play.copy(id = nextId++)
        }
        override suspend fun pendingPlays(): List<PendingPlayEntity> = queued.sortedBy { it.playedAtMillis }
        override suspend fun deletePendingPlay(id: Long) { queued.removeAll { it.id == id } }
        override suspend fun pendingPlayCount(): Int = queued.size

        // Unused by these tests.
        override fun observeTracks(): Flow<List<OfflineTrackEntity>> = flowOf(emptyList())
        override suspend fun tracks(): List<OfflineTrackEntity> = emptyList()
        override suspend fun searchTracks(q: String): List<OfflineTrackEntity> = emptyList()
        override suspend fun tracksInAlbum(albumId: Long): List<OfflineTrackEntity> = emptyList()
        override suspend fun upsertTracks(tracks: List<OfflineTrackEntity>) {}
        override suspend fun deleteTracksNotIn(keep: List<Long>) {}
        override suspend fun deleteAllTracks() {}
        override fun observeCollections(): Flow<List<OfflineCollectionEntity>> = flowOf(emptyList())
        override suspend fun upsertCollections(collections: List<OfflineCollectionEntity>) {}
        override suspend fun deleteAllCollections() {}
    }

    /** Sink whose outcome the test drives. */
    private open class FakeSink(var online: Boolean) : PlaySink {
        val accepted = mutableListOf<Long>()
        override suspend fun recordPlay(trackId: Long): Result<*> {
            if (!online) return Result.failure<Unit>(Exception("offline"))
            accepted += trackId
            return Result.success(Unit)
        }
    }

    @Test
    fun `a play reported while offline is queued, not lost`() = runTest {
        val dao = FakeDao()
        val repo = FakeSink(online = false)
        val reporter = PlayReporter(repo, dao)

        reporter.record(1, atMillis = 100)
        reporter.record(2, atMillis = 200)

        assertTrue(repo.accepted.isEmpty())
        assertEquals(2, reporter.pendingCount())
    }

    @Test
    fun `flushing replays queued plays oldest first and clears them`() = runTest {
        val dao = FakeDao()
        val repo = FakeSink(online = false)
        val reporter = PlayReporter(repo, dao)

        reporter.record(1, atMillis = 300)
        reporter.record(2, atMillis = 100)   // listened to earlier
        reporter.record(3, atMillis = 200)

        repo.online = true
        assertEquals(3, reporter.flush())
        // Order is by when the track was actually played, not when it was queued.
        assertEquals(listOf(2L, 3L, 1L), repo.accepted)
        assertEquals(0, reporter.pendingCount())
    }

    @Test
    fun `a play reported while online is never queued`() = runTest {
        val dao = FakeDao()
        val repo = FakeSink(online = true)
        val reporter = PlayReporter(repo, dao)

        reporter.record(7)
        assertEquals(listOf(7L), repo.accepted)
        assertEquals(0, reporter.pendingCount())
    }

    @Test
    fun `a flush that fails part-way keeps the rest of the queue`() = runTest {
        val dao = FakeDao()
        // Accept the first replay, then go offline again mid-flush.
        val repo = object : FakeSink(online = true) {
            var allowed = 1
            override suspend fun recordPlay(trackId: Long): Result<*> {
                if (allowed-- <= 0) return Result.failure<Unit>(Exception("dropped out"))
                return super.recordPlay(trackId)
            }
        }
        val reporter = PlayReporter(repo, dao)
        repo.allowed = 0            // queue three while "offline"
        reporter.record(1, atMillis = 100)
        reporter.record(2, atMillis = 200)
        reporter.record(3, atMillis = 300)
        assertEquals(3, reporter.pendingCount())

        repo.allowed = 1            // only the first replay gets through
        assertEquals(1, reporter.flush())
        // Nothing is dropped because the server hiccuped, and order is kept.
        assertEquals(2, reporter.pendingCount())
        assertEquals(listOf(2L, 3L), dao.pendingPlays().map { it.trackId })
    }
}
