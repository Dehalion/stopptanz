package dev.stopptanz.app.playlist

import dev.stopptanz.engine.Playlist

sealed interface PlaylistSelectionState {
    object Loading : PlaylistSelectionState
    object NotSelected : PlaylistSelectionState
    object PickerCancelled : PlaylistSelectionState
    data class PermissionUnavailable(val displayName: String?, val kind: SelectionKind) : PlaylistSelectionState
    data class Empty(val displayName: String, val kind: SelectionKind) : PlaylistSelectionState
    data class Selected(val displayName: String, val playlist: Playlist, val kind: SelectionKind) : PlaylistSelectionState
}
