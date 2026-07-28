// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
package com.stopptanz.app.playlist

import com.stopptanz.engine.Playlist

sealed interface PlaylistSelectionState {
    object Loading : PlaylistSelectionState
    object NotSelected : PlaylistSelectionState
    object PickerCancelled : PlaylistSelectionState
    data class PermissionUnavailable(val displayName: String?, val kind: SelectionKind) : PlaylistSelectionState
    data class Empty(val displayName: String, val kind: SelectionKind) : PlaylistSelectionState
    /** [folderUriString] is the SAF tree URI tracks were resolved from; null for a standalone TRACK selection. */
    data class Selected(
        val displayName: String,
        val playlist: Playlist,
        val kind: SelectionKind,
        val folderUriString: String? = null,
    ) : PlaylistSelectionState

    /** A folder containing `.m3u` files was picked; user must choose raw scan or one of [playlistFiles]. */
    data class FolderChoice(val folderUriString: String, val folderName: String, val playlistFiles: List<ScannedFile>) :
        PlaylistSelectionState
}
