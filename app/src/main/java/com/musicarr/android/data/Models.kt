package com.musicarr.android.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The server mixes SQLite booleans (0/1) and JS booleans (true/false) on the
 * wire depending on whether a row came from the database or a Deezer proxy, so
 * boolean-ish fields decode through this tolerant serializer.
 */
object FlexBoolean : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexBoolean", PrimitiveKind.BOOLEAN)
    override fun deserialize(decoder: Decoder): Boolean {
        val p = (decoder as JsonDecoder).decodeJsonElement().jsonPrimitive
        return p.booleanOrNull ?: (p.intOrNull ?: 0) != 0
    }
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}

typealias FBool = @Serializable(with = FlexBoolean::class) Boolean

/**
 * One track. Library/database rows identify it as `deezer_id`; Deezer-proxied
 * browse results use `id`. [trackId] hides that difference.
 */
@Serializable
data class Track(
    val id: Long? = null,
    val deezer_id: Long? = null,
    val title: String = "",
    val artist: String? = null,
    val artist_id: Long? = null,
    val album: String? = null,
    val album_id: Long? = null,
    val track_position: Int? = null,
    val duration: Int? = null,
    val cover: String? = null,
    val available: FBool = false,
    val favorite: FBool = false,
    val download_status: String? = null,
) {
    val trackId: Long get() = deezer_id ?: id ?: 0L
}

@Serializable
data class ArtistRef(
    val id: Long,
    val name: String = "",
    val picture: String? = null,
    val nb_fan: Long? = null,
    val count: Int? = null,
)

@Serializable
data class AlbumRef(
    val id: Long,
    val title: String = "",
    val artist: String? = null,
    val artist_id: Long? = null,
    val cover: String? = null,
    val nb_tracks: Int? = null,
    val release_date: String? = null,
    val record_type: String? = null,
    val available: FBool = false,
    val count: Int? = null,
)

@Serializable
data class DeezerPlaylistRef(
    val id: Long,
    val title: String = "",
    val cover: String? = null,
    val nb_tracks: Int? = null,
    val by: String? = null,
)

@Serializable
data class SearchResults(
    val artists: List<ArtistRef> = emptyList(),
    val albums: List<AlbumRef> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

@Serializable
data class HomeFeed(
    val tracks: List<Track> = emptyList(),
    val albums: List<AlbumRef> = emptyList(),
    val artists: List<ArtistRef> = emptyList(),
    val playlists: List<DeezerPlaylistRef> = emptyList(),
)

@Serializable
data class ArtistDetail(
    val artist: ArtistRef,
    val following: FBool = false,
    val top: List<Track> = emptyList(),
    val albums: List<AlbumRef> = emptyList(),
    val related: List<ArtistRef> = emptyList(),
)

@Serializable
data class AlbumDetail(
    val id: Long,
    val title: String = "",
    val artist: String? = null,
    val artist_id: Long? = null,
    val cover: String? = null,
    val release_date: String? = null,
    val nb_tracks: Int? = null,
    val tracks: List<Track> = emptyList(),
)

@Serializable
data class Playlist(
    val id: Long,
    val name: String = "",
    val count: Int? = null,
    val cover: String? = null,
    val is_owner: FBool = true,
    val shared: FBool = false,
    val owner_name: String? = null,
    val can_edit: FBool = false,
    val tracks: List<Track> = emptyList(),
)

@Serializable
data class Mix(
    val key: String,
    val title: String = "",
    val subtitle: String? = null,
    val cover: String? = null,
    val tracks: List<Track> = emptyList(),
)

@Serializable
data class Mixes(
    val smart: List<Mix> = emptyList(),
    val daily: List<Mix> = emptyList(),
)

@Serializable
data class Download(
    val id: Long,
    val kind: String = "",
    val deezer_id: Long = 0,
    val label: String = "",
    val cover: String? = null,
    val status: String = "",
    val detail: String? = null,
    val progress: Double = 0.0,
    val created_at: String? = null,
    val username: String? = null,
)

@Serializable
data class Me(
    val id: Long,
    val username: String = "",
    val is_admin: FBool = false,
    val must_change_password: FBool = false,
    val avatar: String? = null,
)

@Serializable data class LoginRequest(val username: String, val password: String)
@Serializable data class DownloadRequest(val kind: String, val deezer_id: Long)
@Serializable data class DownloadQueued(val id: Long? = null, val alreadyHave: FBool = false)
@Serializable data class PlayRequest(val track_id: Long)
@Serializable data class OkResponse(val ok: FBool = false)
@Serializable data class ApiError(val error: String? = null)
