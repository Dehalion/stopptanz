package dev.stopptanz.app.playlist

import dev.stopptanz.engine.Playlist

sealed interface PlaylistSelectionState {
    object Loading : PlaylistSelectionState
    object NotSelected : PlaylistSelectionState
    object PickerCancelled : PlaylistSelectionState
    data class PermissionUnavailable(val folderName: String?) : PlaylistSelectionState
    data class Empty(val folderName: String) : PlaylistSelectionState
    data class Selected(val folderName: String, val playlist: Playlist) : PlaylistSelectionState
}
