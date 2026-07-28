// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
package com.stopptanz.app.playlist

import com.stopptanz.engine.Track

/** Parses `.m3u` Playlist File contents and resolves entries against a folder's contents. */
object PlaylistFileParser {

    fun isPlaylistFile(file: ScannedFile): Boolean =
        !file.isDirectory && file.displayName.substringAfterLast('.', "").lowercase() == "m3u"

    /** Extracts referenced filenames, skipping blank lines and `#`-prefixed directives (e.g. `#EXTM3U`). */
    fun parseFilenames(content: String): List<String> =
        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

    /** Resolves each filename against [folderFiles] by exact display name; unresolved entries become missing Tracks. */
    fun resolve(filenames: List<String>, folderFiles: List<ScannedFile>): List<Track> {
        val byName = folderFiles.associateBy { it.displayName }
        return filenames.map { filename ->
            val match = byName[filename]
            if (match != null) {
                Track(uri = match.uriString, name = match.trackName, missing = false)
            } else {
                Track(uri = "", name = filename.substringBeforeLast('.', filename), missing = true)
            }
        }
    }
}
