package com.stopptanz.app.playlist

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistFileWriterTest {

    @Test
    fun `normalizeFilename appends m3u extension when missing`() {
        assertEquals("mix.m3u", PlaylistFileWriter.normalizeFilename("mix"))
    }

    @Test
    fun `normalizeFilename leaves existing m3u extension untouched, case-insensitively`() {
        assertEquals("mix.m3u", PlaylistFileWriter.normalizeFilename("mix.m3u"))
        assertEquals("mix.M3U", PlaylistFileWriter.normalizeFilename("mix.M3U"))
    }

    @Test
    fun `normalizeFilename trims surrounding whitespace`() {
        assertEquals("mix.m3u", PlaylistFileWriter.normalizeFilename("  mix  "))
    }

    @Test
    fun `format writes EXTM3U header then one filename per line`() {
        val content = PlaylistFileWriter.format(listOf("track1.mp3", "track2.mp3"))

        assertEquals("#EXTM3U\ntrack1.mp3\ntrack2.mp3\n", content)
    }

    @Test
    fun `format with no filenames yields header only`() {
        assertEquals("#EXTM3U\n", PlaylistFileWriter.format(emptyList()))
    }
}
