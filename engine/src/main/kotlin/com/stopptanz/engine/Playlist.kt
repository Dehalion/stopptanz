// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
package com.stopptanz.engine

data class Playlist(
    val tracks: List<Track>,
    val shuffle: Boolean = false,
    val loop: Boolean = false,
)
