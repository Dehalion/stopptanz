package com.stopptanz.app.playlist

import com.stopptanz.engine.Track

/** Pure in-memory reorder/remove edits for the Playlist review screen (#20) — never persisted to disk. */
object TrackReview {

    fun moveUp(tracks: List<Track>, index: Int): List<Track> {
        if (index !in 1..tracks.lastIndex) return tracks
        return tracks.toMutableList().apply {
            val tmp = this[index]
            this[index] = this[index - 1]
            this[index - 1] = tmp
        }
    }

    fun moveDown(tracks: List<Track>, index: Int): List<Track> = moveUp(tracks, index + 1)

    fun remove(tracks: List<Track>, index: Int): List<Track> {
        if (index !in tracks.indices) return tracks
        return tracks.toMutableList().apply { removeAt(index) }
    }
}
