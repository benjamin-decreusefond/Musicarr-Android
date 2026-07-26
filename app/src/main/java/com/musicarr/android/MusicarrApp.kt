package com.musicarr.android

import android.app.Application
import com.musicarr.android.data.ApiClient
import com.musicarr.android.data.Connectivity
import com.musicarr.android.data.MusicarrRepository
import com.musicarr.android.data.PlayReporter
import com.musicarr.android.data.SessionManager
import com.musicarr.android.data.local.OfflineDatabase
import com.musicarr.android.offline.OfflineManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide dependencies, reachable from activities and the playback
 * service alike (no DI framework for an app this size).
 */
class MusicarrApp : Application() {
    lateinit var session: SessionManager; private set
    lateinit var apiClient: ApiClient; private set
    lateinit var repository: MusicarrRepository; private set
    lateinit var offline: OfflineManager; private set
    lateinit var connectivity: Connectivity; private set
    lateinit var playReporter: PlayReporter; private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        session = SessionManager(this)
        session.load()
        apiClient = ApiClient(session)
        repository = MusicarrRepository(apiClient, session)
        val dao = OfflineDatabase.get(this).offlineDao()
        offline = OfflineManager(this, repository, session, dao)
        playReporter = PlayReporter({ repository.recordPlay(it) }, dao)

        connectivity = Connectivity(this).apply {
            // Coming back online is the moment to settle what we couldn't do
            // offline: replay queued listens, then re-reconcile the offline set
            // (a pinned playlist may have gained songs while we were away).
            onReconnect = {
                scope.launch {
                    playReporter.flush()
                    if (session.hasSession) offline.refresh()
                }
            }
            start()
        }
    }

    companion object {
        lateinit var instance: MusicarrApp; private set
    }
}
