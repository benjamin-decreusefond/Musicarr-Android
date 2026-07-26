package com.musicarr.android.offline

/**
 * Quality to fetch pinned tracks at.
 *
 * [ORIGINAL] takes the file as the server stores it — a FLAC album is easily
 * 400 MB. [SAVER] asks the server to transcode on the fly, roughly a tenth of
 * that, which is the difference between fitting your library on a phone and
 * not.
 *
 * SAVER depends on the server having transcoding enabled and ffmpeg installed;
 * `/api/stream` ignores `fmt` when it doesn't, and simply returns the original.
 * That means choosing SAVER can never break playback — the worst case is a
 * larger download than the user asked for.
 */
enum class DownloadQuality(val id: String) {
    ORIGINAL("original"),
    SAVER("saver");

    companion object {
        fun fromId(id: String?): DownloadQuality =
            entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}

/** Opus at 96 kbps: transparent enough for phone listening, ~10x smaller than FLAC. */
const val SAVER_BITRATE_KBPS = 96

/**
 * Stream URL for a track at the requested quality.
 *
 * Kept as a pure function so the query-building — which decides what actually
 * lands on the device — is unit testable without an Android context.
 */
fun streamUrlFor(baseUrl: String, trackId: Long, quality: DownloadQuality): String {
    val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    val path = "${base}api/stream/$trackId"
    return when (quality) {
        DownloadQuality.ORIGINAL -> path
        DownloadQuality.SAVER -> "$path?fmt=opus&br=$SAVER_BITRATE_KBPS"
    }
}
