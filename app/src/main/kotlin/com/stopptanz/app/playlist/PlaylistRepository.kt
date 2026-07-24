package com.stopptanz.app.playlist

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.stopptanz.app.settings.SettingsRepository
import com.stopptanz.engine.Playlist
import com.stopptanz.engine.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

// Name kept for back-compat with pre-existing persisted folder URIs; now stores the folder URI for
// PLAYLIST_FILE selections too (the Playlist File itself lives under KEY_LAST_PLAYLIST_FILE_URI).
private const val KEY_LAST_URI = "last_picked_folder_uri"
private const val KEY_LAST_KIND = "last_picked_kind"
private const val KEY_LAST_PLAYLIST_FILE_URI = "last_picked_playlist_file_uri"

class PlaylistRepository(private val context: Context, private val settings: SettingsRepository) {

    private fun lastUriFlow(): Flow<String> = settings.stringFlow(KEY_LAST_URI, "")

    /** Loads the persisted selection on app start; re-scans it if access still holds. */
    suspend fun loadPersistedSelection(): PlaylistSelectionState {
        val saved = lastUriFlow().first()
        if (saved.isBlank()) return PlaylistSelectionState.NotSelected

        val kind = SelectionKind.fromStored(settings.stringFlow(KEY_LAST_KIND, "").first())
        val uri = Uri.parse(saved)
        if (!hasPersistedPermission(uri)) {
            return PlaylistSelectionState.PermissionUnavailable(uri.lastPathSegment, kind)
        }
        return when (kind) {
            SelectionKind.FOLDER -> scanFolder(uri)
            SelectionKind.TRACK -> scanTrack(uri)
            SelectionKind.PLAYLIST_FILE -> {
                val playlistFileUri = settings.stringFlow(KEY_LAST_PLAYLIST_FILE_URI, "").first()
                scanPlaylistFile(uri, playlistFileUri)
            }
        }
    }

    /**
     * Takes persistable access to a freshly picked folder. If it contains `.m3u` files, defers to a
     * [PlaylistSelectionState.FolderChoice] so the user can pick raw scan vs. a Playlist File; otherwise
     * persists and scans it directly (today's behavior).
     */
    suspend fun selectFolder(uri: Uri): PlaylistSelectionState {
        if (!takePersistablePermission(uri)) return PlaylistSelectionState.PermissionUnavailable(null, SelectionKind.FOLDER)

        val root = DocumentFile.fromTreeUri(context, uri)
        if (root == null || !root.isDirectory) {
            return PlaylistSelectionState.PermissionUnavailable(root?.name ?: uri.lastPathSegment, SelectionKind.FOLDER)
        }
        val folderName = root.name ?: uri.lastPathSegment ?: uri.toString()
        val playlistFiles = root.listFiles().map { it.toScannedFile() }.filter(PlaylistFileParser::isPlaylistFile)
        if (playlistFiles.isNotEmpty()) {
            return PlaylistSelectionState.FolderChoice(uri.toString(), folderName, playlistFiles)
        }

        settings.setString(KEY_LAST_URI, uri.toString())
        settings.setString(KEY_LAST_KIND, SelectionKind.FOLDER.name)
        return scanFolder(uri)
    }

    /** Picks "Raw folder scan" from a [PlaylistSelectionState.FolderChoice]. */
    suspend fun chooseRawFolderScan(folderUriString: String): PlaylistSelectionState {
        settings.setString(KEY_LAST_URI, folderUriString)
        settings.setString(KEY_LAST_KIND, SelectionKind.FOLDER.name)
        return scanFolder(Uri.parse(folderUriString))
    }

    /** Picks a `.m3u` file from a [PlaylistSelectionState.FolderChoice]. */
    suspend fun choosePlaylistFile(folderUriString: String, playlistFile: ScannedFile): PlaylistSelectionState {
        settings.setString(KEY_LAST_URI, folderUriString)
        settings.setString(KEY_LAST_KIND, SelectionKind.PLAYLIST_FILE.name)
        settings.setString(KEY_LAST_PLAYLIST_FILE_URI, playlistFile.uriString)
        return scanPlaylistFile(Uri.parse(folderUriString), playlistFile.uriString)
    }

    /** Takes persistable access to a freshly picked single Track, persists it, and loads it. */
    suspend fun selectTrack(uri: Uri): PlaylistSelectionState {
        if (!takePersistablePermission(uri)) return PlaylistSelectionState.PermissionUnavailable(null, SelectionKind.TRACK)

        settings.setString(KEY_LAST_URI, uri.toString())
        settings.setString(KEY_LAST_KIND, SelectionKind.TRACK.name)
        return scanTrack(uri)
    }

    private fun takePersistablePermission(uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: SecurityException) {
        false
    }

    private fun hasPersistedPermission(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    private fun scanFolder(uri: Uri): PlaylistSelectionState {
        val root = DocumentFile.fromTreeUri(context, uri)
        if (root == null || !root.isDirectory) {
            return PlaylistSelectionState.PermissionUnavailable(root?.name ?: uri.lastPathSegment, SelectionKind.FOLDER)
        }

        val folderName = root.name ?: uri.lastPathSegment ?: uri.toString()
        val entries = root.listFiles().map { it.toScannedFile() }
        val playlist = PlaylistBuilder.build(entries)
        return if (playlist == null) {
            PlaylistSelectionState.Empty(folderName, SelectionKind.FOLDER)
        } else {
            PlaylistSelectionState.Selected(folderName, playlist, SelectionKind.FOLDER, folderUriString = uri.toString())
        }
    }

    /**
     * Parses the `.m3u` at [playlistFileUriString] and resolves its entries against [folderUri]'s contents.
     * If the Playlist File itself no longer resolves (deleted/moved), falls back to the folder's
     * [PlaylistSelectionState.FolderChoice] rather than a raw scan.
     */
    private suspend fun scanPlaylistFile(folderUri: Uri, playlistFileUriString: String): PlaylistSelectionState {
        val root = DocumentFile.fromTreeUri(context, folderUri)
        if (root == null || !root.isDirectory) {
            return PlaylistSelectionState.PermissionUnavailable(root?.name ?: folderUri.lastPathSegment, SelectionKind.PLAYLIST_FILE)
        }
        val folderName = root.name ?: folderUri.lastPathSegment ?: folderUri.toString()
        val folderFiles = root.listFiles().map { it.toScannedFile() }

        val playlistDoc = playlistFileUriString.takeIf { it.isNotBlank() }?.let { DocumentFile.fromSingleUri(context, Uri.parse(it)) }
        if (playlistDoc == null || !playlistDoc.isFile) {
            val playlistFiles = folderFiles.filter(PlaylistFileParser::isPlaylistFile)
            if (playlistFiles.isEmpty()) {
                settings.setString(KEY_LAST_URI, folderUri.toString())
                settings.setString(KEY_LAST_KIND, SelectionKind.FOLDER.name)
                return scanFolder(folderUri)
            }
            return PlaylistSelectionState.FolderChoice(folderUri.toString(), folderName, playlistFiles)
        }

        val playlistFileName = playlistDoc.name ?: playlistFileUriString
        val content = context.contentResolver.openInputStream(playlistDoc.uri)?.bufferedReader()?.use { it.readText() } ?: ""
        val tracks = PlaylistFileParser.resolve(PlaylistFileParser.parseFilenames(content), folderFiles)
        return if (tracks.isEmpty()) {
            PlaylistSelectionState.Empty(playlistFileName, SelectionKind.PLAYLIST_FILE)
        } else {
            PlaylistSelectionState.Selected(
                playlistFileName,
                Playlist(tracks = tracks),
                SelectionKind.PLAYLIST_FILE,
                folderUriString = folderUri.toString(),
            )
        }
    }

    /**
     * Writes [tracks] (already excluding missing rows) as a minimal `.m3u` into the folder at
     * [folderUriString], under [filename] (`.m3u` appended if absent). Does not touch persisted
     * selection state — this is a side-effect export, not a new selection (#22).
     */
    suspend fun savePlaylistFile(
        folderUriString: String,
        filename: String,
        tracks: List<Track>,
        overwrite: Boolean,
    ): PlaylistSaveResult {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(folderUriString))
        if (root == null || !root.isDirectory) return PlaylistSaveResult.Failed

        val normalizedName = PlaylistFileWriter.normalizeFilename(filename)
        val existing = root.listFiles().firstOrNull { it.name.equals(normalizedName, ignoreCase = true) }
        if (existing != null && !overwrite) return PlaylistSaveResult.AlreadyExists(normalizedName)

        return try {
            existing?.delete()
            val doc = root.createFile("application/octet-stream", normalizedName) ?: return PlaylistSaveResult.Failed
            val filenames = tracks.mapNotNull { DocumentFile.fromSingleUri(context, Uri.parse(it.uri))?.name }
            context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                out.write(PlaylistFileWriter.format(filenames).toByteArray())
            }
            PlaylistSaveResult.Saved(normalizedName)
        } catch (e: Exception) {
            PlaylistSaveResult.Failed
        }
    }

    private fun DocumentFile.toScannedFile() = ScannedFile(
        uriString = uri.toString(),
        displayName = name ?: "",
        mimeType = type,
        isDirectory = isDirectory,
    )

    private fun scanTrack(uri: Uri): PlaylistSelectionState {
        val doc = DocumentFile.fromSingleUri(context, uri)
        if (doc == null || !doc.isFile) {
            return PlaylistSelectionState.PermissionUnavailable(doc?.name ?: uri.lastPathSegment, SelectionKind.TRACK)
        }

        val trackName = doc.name ?: uri.lastPathSegment ?: uri.toString()
        val entry = ScannedFile(uriString = uri.toString(), displayName = trackName, mimeType = doc.type, isDirectory = false)
        val playlist = PlaylistBuilder.build(listOf(entry))
        return if (playlist == null) {
            PlaylistSelectionState.Empty(trackName, SelectionKind.TRACK)
        } else {
            PlaylistSelectionState.Selected(trackName, playlist, SelectionKind.TRACK)
        }
    }
}

/** Result of [PlaylistRepository.savePlaylistFile]. */
sealed interface PlaylistSaveResult {
    data class Saved(val filename: String) : PlaylistSaveResult
    data class AlreadyExists(val filename: String) : PlaylistSaveResult
    object Failed : PlaylistSaveResult
}
