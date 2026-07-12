package dev.stopptanz.engine

data class Playlist(
    val tracks: List<String>,
    val shuffle: Boolean = false,
    val loop: Boolean = false,
)
