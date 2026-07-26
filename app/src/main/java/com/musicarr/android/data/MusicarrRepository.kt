package com.musicarr.android.data

/**
 * Thin repository over the API: every call comes back as a [Result] whose
 * failure already carries a human-readable message.
 */
class MusicarrRepository(private val client: ApiClient, private val session: SessionManager) {
    private val api get() = client.api

    private suspend fun <T> call(block: suspend MusicarrApi.() -> T): Result<T> =
        try { Result.success(api.block()) }
        catch (e: Exception) { Result.failure(Exception(client.errorMessage(e), e)) }

    suspend fun login(serverUrl: String, username: String, password: String): Result<Me> {
        session.setServerUrl(serverUrl)
        return call { login(LoginRequest(username, password)) }
            .onSuccess { me -> session.setSession(session.sessionCookie, me.username) }
    }

    suspend fun logout() {
        try { api.logout() } catch (_: Exception) { /* best-effort */ }
        session.clearSession()
    }

    suspend fun me() = call { me() }
    suspend fun home() = call { home() }
    suspend fun search(q: String) = call { search(q) }
    suspend fun artist(id: Long) = call { artist(id) }
    suspend fun album(id: Long) = call { album(id) }
    suspend fun library(q: String? = null) = call { library(q) }
    suspend fun libraryAlbums() = call { libraryAlbums() }
    suspend fun libraryArtists() = call { libraryArtists() }
    suspend fun playlists() = call { playlists() }
    suspend fun playlist(id: Long) = call { playlist(id) }
    suspend fun favorites() = call { favorites() }
    suspend fun setFavorite(track: Track, favorite: Boolean) = call {
        if (favorite) addFavorite(track.trackId, track) else removeFavorite(track.trackId)
    }
    suspend fun download(kind: String, deezerId: Long) = call { download(DownloadRequest(kind, deezerId)) }
    suspend fun downloads() = call { downloads() }
    suspend fun retryDownload(id: Long) = call { retryDownload(id) }
    suspend fun dismissDownload(id: Long) = call { dismissDownload(id) }
    suspend fun offlinePins() = call { offlinePins() }
    suspend fun pinOffline(track: Track) = call { pinOffline(track.trackId, track) }.map { }
    suspend fun unpinOffline(trackId: Long) = call { unpinOffline(trackId) }.map { }
    suspend fun mixes() = call { mixes() }
    suspend fun history() = call { history() }
    suspend fun recordPlay(trackId: Long) = call { recordPlay(PlayRequest(trackId)) }
}
