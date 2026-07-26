package com.musicarr.android.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** The slice of the Musicarr server API this client consumes. */
interface MusicarrApi {
    // Auth (session cookie captured by the persistent cookie jar)
    @POST("api/auth/login") suspend fun login(@Body body: LoginRequest): Me
    @POST("api/auth/logout") suspend fun logout(): OkResponse
    @GET("api/auth/me") suspend fun me(): Me

    // Browse (Deezer proxied through the server)
    @GET("api/home") suspend fun home(): HomeFeed
    @GET("api/search") suspend fun search(@Query("q") q: String): SearchResults
    @GET("api/artist/{id}") suspend fun artist(@Path("id") id: Long): ArtistDetail
    @GET("api/album/{id}") suspend fun album(@Path("id") id: Long): AlbumDetail

    // Library (shared, on-disk)
    @GET("api/library") suspend fun library(@Query("q") q: String? = null): List<Track>
    @GET("api/library/albums") suspend fun libraryAlbums(): List<AlbumRef>
    @GET("api/library/artists") suspend fun libraryArtists(): List<ArtistRef>

    // Playlists
    @GET("api/playlists") suspend fun playlists(): List<Playlist>
    @GET("api/playlists/{id}") suspend fun playlist(@Path("id") id: Long): Playlist

    // Favorites
    @GET("api/favorites") suspend fun favorites(): List<Track>
    @PUT("api/favorites/{trackId}") suspend fun addFavorite(@Path("trackId") trackId: Long, @Body track: Track): OkResponse
    @DELETE("api/favorites/{trackId}") suspend fun removeFavorite(@Path("trackId") trackId: Long): OkResponse

    // Downloads (queue a Soulseek fetch, watch progress)
    @POST("api/download") suspend fun download(@Body body: DownloadRequest): DownloadQueued
    @GET("api/downloads") suspend fun downloads(): List<Download>
    @POST("api/downloads/{id}/retry") suspend fun retryDownload(@Path("id") id: Long): OkResponse
    @DELETE("api/downloads/{id}") suspend fun dismissDownload(@Path("id") id: Long): OkResponse

    // Offline pins: tracks this user wants kept on a device. The server also
    // treats a pin as a reason to spare the file from auto-cleanup.
    @GET("api/offline") suspend fun offlinePins(): List<Track>
    @PUT("api/offline/{trackId}") suspend fun pinOffline(@Path("trackId") trackId: Long, @Body track: Track): OkResponse
    @DELETE("api/offline/{trackId}") suspend fun unpinOffline(@Path("trackId") trackId: Long): OkResponse

    // Made for you + history
    @GET("api/mixes") suspend fun mixes(): Mixes
    @GET("api/history") suspend fun history(): List<Track>

    // Listening activity
    @POST("api/plays") suspend fun recordPlay(@Body body: PlayRequest): OkResponse
}
