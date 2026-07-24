package com.stopptanz.engine

data class Playlist(
    val tracks: List<Track>,
    val shuffle: Boolean = false,
    val loop: Boolean = false,
)
