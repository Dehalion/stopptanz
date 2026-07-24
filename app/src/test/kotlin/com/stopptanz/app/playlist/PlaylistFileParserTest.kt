package com.stopptanz.app.playlist

import com.stopptanz.engine.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaylistFileParserTest {

    private fun file(name: String, uri: String = "content://$name") =
        ScannedFile(uriString = uri, displayName = name, mimeType = "audio/mpeg", isDirectory = false)

    @Test
    fun `parses filenames, skipping blank lines and directives`() {
        val content = "#EXTM3U\n#EXTINF:123,Some Title\ntrack1.mp3\n\ntrack2.mp3\n"

        assertEquals(listOf("track1.mp3", "track2.mp3"), PlaylistFileParser.parseFilenames(content))
    }

    @Test
    fun `trims surrounding whitespace from entries`() {
        assertEquals(listOf("track1.mp3"), PlaylistFileParser.parseFilenames("  track1.mp3  \n"))
    }

    @Test
    fun `empty content yields no filenames`() {
        assertEquals(emptyList(), PlaylistFileParser.parseFilenames(""))
    }

    @Test
    fun `resolves matching filenames to their folder file, stripping extension`() {
        val tracks = PlaylistFileParser.resolve(listOf("track1.mp3"), listOf(file("track1.mp3")))

        assertEquals(listOf(Track("content://track1.mp3", "track1", missing = false)), tracks)
    }

    @Test
    fun `unresolved filenames become missing tracks`() {
        val tracks = PlaylistFileParser.resolve(listOf("ghost.mp3"), listOf(file("track1.mp3")))

        assertEquals(listOf(Track("", "ghost", missing = true)), tracks)
    }

    @Test
    fun `preserves m3u order regardless of folder listing order`() {
        val tracks = PlaylistFileParser.resolve(
            listOf("b.mp3", "a.mp3"),
            listOf(file("a.mp3"), file("b.mp3")),
        )

        assertEquals(listOf("b", "a"), tracks.map { it.name })
    }

    @Test
    fun `isPlaylistFile matches m3u extension case-insensitively`() {
        assertTrue(PlaylistFileParser.isPlaylistFile(file("mix.M3U")))
        assertTrue(PlaylistFileParser.isPlaylistFile(file("mix.m3u")))
    }

    @Test
    fun `isPlaylistFile rejects other extensions and directories`() {
        assertEquals(
            false,
            PlaylistFileParser.isPlaylistFile(ScannedFile("content://d", "folder.m3u", null, isDirectory = true)),
        )
        assertEquals(false, PlaylistFileParser.isPlaylistFile(file("track1.mp3")))
    }
}
