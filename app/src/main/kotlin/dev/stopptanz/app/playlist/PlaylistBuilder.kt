package dev.stopptanz.app.playlist

import dev.stopptanz.engine.Playlist

data class ScannedFile(
    val uriString: String,
    val displayName: String,
    val mimeType: String?,
    val isDirectory: Boolean,
)

object PlaylistBuilder {

    private val audioExtensions = setOf("mp3", "m4a", "flac", "wav", "ogg", "oga", "aac", "opus")

    fun isAudioFile(file: ScannedFile): Boolean {
        if (file.isDirectory) return false
        val mimeType = file.mimeType
        if (mimeType != null) return mimeType.startsWith("audio/")
        return file.displayName.substringAfterLast('.', "").lowercase() in audioExtensions
    }

    fun build(files: List<ScannedFile>): Playlist? {
        val tracks = files.filter(::isAudioFile).map { it.uriString }
        return if (tracks.isEmpty()) null else Playlist(tracks = tracks)
    }
}
