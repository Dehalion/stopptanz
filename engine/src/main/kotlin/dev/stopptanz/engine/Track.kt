package dev.stopptanz.engine

/** A single audio file within a Playlist. [name] is the display name shown to the host.
 *  [missing] marks a Playlist File entry that didn't resolve to a file; excluded from playback. */
data class Track(val uri: String, val name: String, val missing: Boolean = false)

/** How many Tracks remain in the Playlist, shaped differently depending on [Playlist.loop]. */
sealed class TrackRemaining {
    data class Position(val current: Int, val total: Int) : TrackRemaining()
    data class Countdown(val remaining: Int) : TrackRemaining()
}

data class TrackStatus(val current: Track, val next: Track?, val remaining: TrackRemaining)
