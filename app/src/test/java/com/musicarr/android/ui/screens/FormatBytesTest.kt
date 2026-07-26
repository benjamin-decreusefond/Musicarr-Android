package com.musicarr.android.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/** The storage figure is the one number that tells a user whether their
 *  offline library is about to fill the phone, so it has to read correctly at
 *  every magnitude and at the unit boundaries. */
class FormatBytesTest {

    @Test
    fun `bytes below a kilobyte are shown raw`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun `each unit boundary rolls over exactly`() {
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.0 MB", formatBytes(1024L * 1024))
        assertEquals("1.0 GB", formatBytes(1024L * 1024 * 1024))
        assertEquals("1.0 TB", formatBytes(1024L * 1024 * 1024 * 1024))
    }

    @Test
    fun `realistic library sizes read sensibly`() {
        // A FLAC album, and a decent offline library.
        assertEquals("400.0 MB", formatBytes(400L * 1024 * 1024))
        assertEquals("12.5 GB", formatBytes((12.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `it stops at terabytes rather than inventing a unit`() {
        assertEquals("1024.0 TB", formatBytes(1024L * 1024 * 1024 * 1024 * 1024))
    }
}
