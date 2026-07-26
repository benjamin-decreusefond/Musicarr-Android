package com.musicarr.android.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cache entries must be keyed by track, never by URL.
 *
 * `/api/stream/{id}` accepts `?fmt=` and `?br=` for the server's optional
 * transcoding, so one track has several valid URLs. media3's default key is the
 * URI, which would make a track downloaded as Opus a cache *miss* when playback
 * requests the plain URL — silently re-downloading over the network, i.e.
 * exactly the failure offline playback exists to prevent.
 */
class OfflineCacheKeyTest {

    @Test
    fun `the download key and the playback key agree`() {
        // This is the invariant the whole feature rests on. Downloads are
        // registered under cacheKeyFor(trackId); playback looks entries up by
        // deriving a key from the request path. If these two ever disagree,
        // every pinned track silently re-downloads on play.
        val trackId = 1234L
        assertEquals(
            OfflineCache.cacheKeyFor(trackId),
            OfflineCache.cacheKeyForPath("/api/stream/$trackId"),
        )
        assertEquals("track:1234", OfflineCache.cacheKeyFor(trackId))
    }

    @Test
    fun `the key comes from the path, so quality parameters cannot fragment it`() {
        // Uri.getPath() excludes the query, which is where fmt/br/t live — so a
        // track downloaded as Opus and played back at original quality resolves
        // to one entry.
        assertEquals("track:1234", OfflineCache.cacheKeyForPath("/api/stream/1234"))
    }

    @Test
    fun `different tracks never collide`() {
        assertEquals("track:1", OfflineCache.cacheKeyForPath("/api/stream/1"))
        assertEquals("track:12", OfflineCache.cacheKeyForPath("/api/stream/12"))
        // A prefix relationship in the id must not become a key collision.
        assert(OfflineCache.cacheKeyForPath("/api/stream/1") != OfflineCache.cacheKeyForPath("/api/stream/12"))
    }

    @Test
    fun `works when the server is hosted under a sub-path`() {
        // A reverse proxy may mount Musicarr at /music, so "stream" is located
        // by name rather than by a fixed segment index.
        assertEquals("track:99", OfflineCache.cacheKeyForPath("/music/api/stream/99"))
        assertEquals("track:99", OfflineCache.cacheKeyForPath("api/stream/99"))
    }

    @Test
    fun `non-stream URLs yield no key so they fall back to the URI`() {
        assertNull(OfflineCache.cacheKeyForPath("/api/library"))
        assertNull(OfflineCache.cacheKeyForPath("/api/stream"))          // no id
        assertNull(OfflineCache.cacheKeyForPath("/api/stream/notanumber"))
        assertNull(OfflineCache.cacheKeyForPath(""))
        assertNull(OfflineCache.cacheKeyForPath(null))
    }
}

class StreamUrlTest {

    @Test
    fun `original quality requests the file as stored`() {
        assertEquals(
            "https://music.example.com/api/stream/7",
            streamUrlFor("https://music.example.com/", 7, DownloadQuality.ORIGINAL),
        )
    }

    @Test
    fun `saver asks the server to transcode`() {
        assertEquals(
            "https://music.example.com/api/stream/7?fmt=opus&br=96",
            streamUrlFor("https://music.example.com/", 7, DownloadQuality.SAVER),
        )
    }

    @Test
    fun `a base URL without a trailing slash still builds a valid URL`() {
        assertEquals(
            "https://music.example.com/api/stream/7",
            streamUrlFor("https://music.example.com", 7, DownloadQuality.ORIGINAL),
        )
    }

    @Test
    fun `quality ids round-trip and unknown values fall back to original`() {
        assertEquals(DownloadQuality.ORIGINAL, DownloadQuality.fromId("original"))
        assertEquals(DownloadQuality.SAVER, DownloadQuality.fromId("saver"))
        // An unset preference, or one written by a future version, must not crash.
        assertEquals(DownloadQuality.ORIGINAL, DownloadQuality.fromId(null))
        assertEquals(DownloadQuality.ORIGINAL, DownloadQuality.fromId("lossless-8k"))
    }
}
