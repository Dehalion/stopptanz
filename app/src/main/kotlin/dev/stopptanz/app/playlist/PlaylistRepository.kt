package dev.stopptanz.app.playlist

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.stopptanz.app.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

// Name kept for back-compat with pre-existing persisted folder URIs; now stores either kind.
private const val KEY_LAST_URI = "last_picked_folder_uri"
private const val KEY_LAST_KIND = "last_picked_kind"

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
        }
    }

    /** Takes persistable access to a freshly picked folder, persists it, and scans it. */
    suspend fun selectFolder(uri: Uri): PlaylistSelectionState {
        if (!takePersistablePermission(uri)) return PlaylistSelectionState.PermissionUnavailable(null, SelectionKind.FOLDER)

        settings.setString(KEY_LAST_URI, uri.toString())
        settings.setString(KEY_LAST_KIND, SelectionKind.FOLDER.name)
        return scanFolder(uri)
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
        val entries = root.listFiles().map {
            ScannedFile(
                uriString = it.uri.toString(),
                displayName = it.name ?: "",
                mimeType = it.type,
                isDirectory = it.isDirectory,
            )
        }
        val playlist = PlaylistBuilder.build(entries)
        return if (playlist == null) {
            PlaylistSelectionState.Empty(folderName, SelectionKind.FOLDER)
        } else {
            PlaylistSelectionState.Selected(folderName, playlist, SelectionKind.FOLDER)
        }
    }

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
