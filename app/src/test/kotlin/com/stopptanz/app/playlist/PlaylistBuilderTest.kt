// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
package com.stopptanz.app.playlist

import com.stopptanz.engine.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaylistBuilderTest {

    private fun file(name: String, mimeType: String?, isDirectory: Boolean = false, uri: String = "content://$name") =
        ScannedFile(uriString = uri, displayName = name, mimeType = mimeType, isDirectory = isDirectory)

    @Test
    fun `builds playlist from audio-mime-type files`() {
        val playlist = PlaylistBuilder.build(
            listOf(
                file("track1.mp3", "audio/mpeg"),
                file("track2.flac", "audio/flac"),
            ),
        )

        assertEquals(
            listOf(Track("content://track1.mp3", "track1"), Track("content://track2.flac", "track2")),
            playlist?.tracks,
        )
    }

    @Test
    fun `falls back to extension when mime type missing`() {
        val playlist = PlaylistBuilder.build(listOf(file("track1.mp3", mimeType = null)))

        assertEquals(listOf(Track("content://track1.mp3", "track1")), playlist?.tracks)
    }

    @Test
    fun `track display name strips the extension only`() {
        val playlist = PlaylistBuilder.build(listOf(file("My Song.Final.mp3", "audio/mpeg")))

        assertEquals("My Song.Final", playlist?.tracks?.single()?.name)
    }

    @Test
    fun `track display name is used as-is when there is no extension`() {
        val playlist = PlaylistBuilder.build(listOf(file("track1", "audio/mpeg")))

        assertEquals("track1", playlist?.tracks?.single()?.name)
    }

    @Test
    fun `excludes non-audio files`() {
        val playlist = PlaylistBuilder.build(
            listOf(
                file("cover.jpg", "image/jpeg"),
                file("notes.txt", "text/plain"),
            ),
        )

        assertNull(playlist)
    }

    @Test
    fun `excludes directories even with audio-like names`() {
        val playlist = PlaylistBuilder.build(listOf(file("subfolder.mp3", "audio/mpeg", isDirectory = true)))

        assertNull(playlist)
    }

    @Test
    fun `empty folder yields null playlist`() {
        assertNull(PlaylistBuilder.build(emptyList()))
    }

    @Test
    fun `default shuffle and loop are off`() {
        val playlist = PlaylistBuilder.build(listOf(file("track1.mp3", "audio/mpeg")))

        assertEquals(false, playlist?.shuffle)
        assertEquals(false, playlist?.loop)
    }
}
