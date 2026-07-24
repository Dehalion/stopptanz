package com.stopptanz.app.playlist

import com.stopptanz.engine.Playlist
import com.stopptanz.engine.Track

data class ScannedFile(
    val uriString: String,
    val displayName: String,
    val mimeType: String?,
    val isDirectory: Boolean,
) {
    /** Track display name: [displayName] with its extension stripped, per the Track domain rule. */
    val trackName: String get() = displayName.substringBeforeLast('.', displayName)
}

object PlaylistBuilder {

    private val audioExtensions = setOf("mp3", "m4a", "flac", "wav", "ogg", "oga", "aac", "opus")

    fun isAudioFile(file: ScannedFile): Boolean {
        if (file.isDirectory) return false
        val mimeType = file.mimeType
        if (mimeType != null) return mimeType.startsWith("audio/")
        return file.displayName.substringAfterLast('.', "").lowercase() in audioExtensions
    }

    fun build(files: List<ScannedFile>): Playlist? {
        val tracks = files.filter(::isAudioFile).map { Track(uri = it.uriString, name = it.trackName) }
        return if (tracks.isEmpty()) null else Playlist(tracks = tracks)
    }
}
