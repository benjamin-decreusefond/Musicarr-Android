package com.musicarr.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local catalogue backing offline browsing.
 *
 * Everything here is a cache of server state or a replayable queue, so
 * destructive migration is the right fallback: on a schema change we'd rather
 * rebuild from the server on next sync than carry migration code for data that
 * is, by definition, reproducible. The one exception is pending_plays, which
 * would lose queued listens — an acceptable trade for a schema change that
 * only happens on app update, and far better than crashing on launch.
 */
@Database(
    entities = [OfflineTrackEntity::class, PendingPlayEntity::class, OfflineCollectionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class OfflineDatabase : RoomDatabase() {
    abstract fun offlineDao(): OfflineDao

    companion object {
        @Volatile private var instance: OfflineDatabase? = null

        fun get(context: Context): OfflineDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                OfflineDatabase::class.java,
                "musicarr-offline.db",
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
