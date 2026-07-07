package com.musicarr.android

import android.app.Application
import com.musicarr.android.data.ApiClient
import com.musicarr.android.data.MusicarrRepository
import com.musicarr.android.data.SessionManager

/**
 * Process-wide dependencies, reachable from activities and the playback
 * service alike (no DI framework for an app this size).
 */
class MusicarrApp : Application() {
    lateinit var session: SessionManager; private set
    lateinit var apiClient: ApiClient; private set
    lateinit var repository: MusicarrRepository; private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        session = SessionManager(this)
        session.load()
        apiClient = ApiClient(session)
        repository = MusicarrRepository(apiClient, session)
    }

    companion object {
        lateinit var instance: MusicarrApp; private set
    }
}
