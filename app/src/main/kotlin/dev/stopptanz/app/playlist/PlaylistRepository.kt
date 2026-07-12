package dev.stopptanz.app.playlist

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.stopptanz.app.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

private const val KEY_LAST_FOLDER_URI = "last_picked_folder_uri"

class PlaylistRepository(private val context: Context, private val settings: SettingsRepository) {

    fun lastFolderUriFlow(): Flow<String> = settings.stringFlow(KEY_LAST_FOLDER_URI, "")

    /** Loads the persisted folder selection on app start; re-scans it if access still holds. */
    suspend fun loadPersistedSelection(): PlaylistSelectionState {
        val saved = lastFolderUriFlow().first()
        if (saved.isBlank()) return PlaylistSelectionState.NotSelected

        val uri = Uri.parse(saved)
        if (!hasPersistedPermission(uri)) {
            return PlaylistSelectionState.PermissionUnavailable(uri.lastPathSegment)
        }
        return scanFolder(uri)
    }

    /** Takes persistable access to a freshly picked folder, persists it, and scans it. */
    suspend fun selectFolder(uri: Uri): PlaylistSelectionState {
        val granted = try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        } catch (e: SecurityException) {
            false
        }
        if (!granted) return PlaylistSelectionState.PermissionUnavailable(folderName = null)

        settings.setString(KEY_LAST_FOLDER_URI, uri.toString())
        return scanFolder(uri)
    }

    private fun hasPersistedPermission(uri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    private fun scanFolder(uri: Uri): PlaylistSelectionState {
        val root = DocumentFile.fromTreeUri(context, uri)
        if (root == null || !root.isDirectory) {
            return PlaylistSelectionState.PermissionUnavailable(root?.name ?: uri.lastPathSegment)
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
            PlaylistSelectionState.Empty(folderName)
        } else {
            PlaylistSelectionState.Selected(folderName, playlist)
        }
    }
}
