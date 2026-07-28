// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
package com.stopptanz.engine

class SessionEngine(
    val playlist: Playlist,
    val mode: Mode,
    stopInterval: StopInterval,
    pauseDurationMillis: Long,
    private val randomSource: RandomSource = DefaultRandomSource(),
) {
    var state: SessionState = SessionState.Playing
        private set

    private var stopInterval: StopInterval = stopInterval

    var pauseDurationMillis: Long = pauseDurationMillis
        private set

    /** Playlist tracks in playback order: shuffled once per Session if [Playlist.shuffle] is set. */
    val orderedTracks: List<Track> by lazy {
        if (playlist.shuffle) shuffledTracks() else playlist.tracks
    }

    var currentTrackIndex: Int = 0
        private set

    val currentTrack: Track get() = orderedTracks[currentTrackIndex]

    /** The Track that follows [currentTrack] in [orderedTracks]; `null` if [currentTrack] is last. */
    val nextTrack: Track? get() = orderedTracks.getOrNull(currentTrackIndex + 1)

    /** Position when [Playlist.loop] is on ("Track X of N"); a true countdown of Tracks left otherwise. */
    val remainingTracks: TrackRemaining
        get() = if (playlist.loop) {
            TrackRemaining.Position(currentTrackIndex + 1, orderedTracks.size)
        } else {
            TrackRemaining.Countdown(orderedTracks.size - currentTrackIndex - 1)
        }

    /** Current Track, next Track, and remaining count, bundled for the adapter/UI to observe together. */
    val trackStatus: TrackStatus
        get() = TrackStatus(currentTrack, nextTrack, remainingTracks, canSkipPrevious, canSkipNext)

    /** Whether [skipToPrevious] would move [currentTrackIndex]: false at the first Track unless Loop is on, and always false with a single Track. */
    val canSkipPrevious: Boolean
        get() = orderedTracks.size > 1 && (playlist.loop || currentTrackIndex > 0)

    /** Whether [skipToNext] would move [currentTrackIndex]: false at the last Track unless Loop is on, and always false with a single Track. */
    val canSkipNext: Boolean
        get() = orderedTracks.size > 1 && (playlist.loop || currentTrackIndex < orderedTracks.size - 1)

    /** Called by the playback adapter each time ExoPlayer moves on to the next Track. */
    fun onTrackAdvanced() {
        check(state is SessionState.Playing) { "Cannot advance Track from $state" }
        val next = currentTrackIndex + 1
        currentTrackIndex = if (playlist.loop) {
            next % orderedTracks.size
        } else {
            check(next < orderedTracks.size) { "Cannot advance Track past the end of a non-looping Playlist" }
            next
        }
    }

    /**
     * Picks the next Stop delay, or `null` if that pick would land within the last
     * [END_OF_TRACK_GUARD_MILLIS] of the current Track — in which case the caller should let the
     * Track play out to its natural end instead of scheduling a Stop.
     */
    fun nextStopDelayMillis(remainingTrackMillis: Long = Long.MAX_VALUE): Long? {
        val delay = randomSource.nextLong(stopInterval.minMillis, stopInterval.maxMillis)
        return if (remainingTrackMillis - delay < END_OF_TRACK_GUARD_MILLIS) null else delay
    }

    companion object {
        /** No Stop is ever scheduled to land within this many milliseconds of a Track's natural end. */
        const val END_OF_TRACK_GUARD_MILLIS = 10_000L
    }

    /** Applies prospectively: a countdown already in flight captured its value on return and is unaffected. */
    fun setStopInterval(stopInterval: StopInterval) {
        this.stopInterval = stopInterval
    }

    /** Applies prospectively: a countdown already in flight captured its value on return and is unaffected. */
    fun setPauseDurationMillis(pauseDurationMillis: Long) {
        this.pauseDurationMillis = pauseDurationMillis
    }

    /** Called by the playback adapter when the Playlist reaches its end. */
    fun onPlaylistEnded() {
        check(state is SessionState.Playing) { "Cannot end Playlist from $state" }
        if (!playlist.loop) {
            state = SessionState.Finished
        }
    }

    private fun shuffledTracks(): List<Track> {
        val tracks = playlist.tracks.toMutableList()
        for (i in tracks.indices.reversed()) {
            if (i == 0) break
            val j = randomSource.nextLong(0, i.toLong()).toInt()
            val tmp = tracks[i]
            tracks[i] = tracks[j]
            tracks[j] = tmp
        }
        return tracks
    }

    fun stop() {
        check(state is SessionState.Playing) { "Cannot Stop from $state" }
        state = SessionState.Stopped
    }

    fun resume() {
        check(state is SessionState.Stopped) { "Cannot Resume from $state" }
        state = SessionState.Playing
    }

    /** Host-driven jump to the previous Track; wraps when Loop is on, a no-op at the first Track otherwise. Leaves [state] unchanged. */
    fun skipToPrevious() {
        check(state is SessionState.Playing || state is SessionState.Stopped || state is SessionState.Paused) { "Cannot Skip from $state" }
        val previous = currentTrackIndex - 1
        currentTrackIndex = if (playlist.loop) {
            (previous + orderedTracks.size) % orderedTracks.size
        } else {
            previous.coerceAtLeast(0)
        }
    }

    /** Host-driven jump to the next Track; wraps when Loop is on, a no-op at the last Track otherwise. Leaves [state] unchanged. */
    fun skipToNext() {
        check(state is SessionState.Playing || state is SessionState.Stopped || state is SessionState.Paused) { "Cannot Skip from $state" }
        val next = currentTrackIndex + 1
        currentTrackIndex = if (playlist.loop) {
            next % orderedTracks.size
        } else {
            next.coerceAtMost(orderedTracks.size - 1)
        }
    }

    /** Called by the playback adapter once [pauseDurationMillis] has elapsed since a Stop. */
    fun onPauseElapsed() {
        check(state is SessionState.Stopped) { "Cannot auto-resume from $state" }
        check(mode == Mode.FREEZE_DANCE) { "Auto-resume only applies in Freeze Dance mode" }
        state = SessionState.Playing
    }

    /**
     * Suspends the Session from [SessionState.Playing] or [SessionState.Stopped]; resumable to exactly that
     * state via [resumeFromPause]. [remainingFreezeMillis] is required when pausing mid-countdown in
     * [Mode.FREEZE_DANCE] (so the countdown can't be silently lost), and must be omitted otherwise.
     */
    fun pause(remainingFreezeMillis: Long? = null) {
        val current = state
        check(current is SessionState.Playing || current is SessionState.Stopped) { "Cannot Pause from $current" }
        val pausingMidCountdown = current is SessionState.Stopped && mode == Mode.FREEZE_DANCE
        if (pausingMidCountdown) {
            checkNotNull(remainingFreezeMillis) { "remainingFreezeMillis is required when pausing mid-countdown in Freeze Dance mode" }
        } else {
            check(remainingFreezeMillis == null) { "remainingFreezeMillis only applies when pausing mid-countdown in Freeze Dance mode" }
        }
        state = SessionState.Paused(resumedState = current, remainingFreezeMillis = remainingFreezeMillis)
    }

    /** Restores whichever state was active before [pause]; returns the freeze countdown remaining at pause time, if any. */
    fun resumeFromPause(): Long? {
        val current = state
        check(current is SessionState.Paused) { "Cannot Resume from Pause from $current" }
        state = current.resumedState
        return current.remainingFreezeMillis
    }

    fun close() {
        check(
            state is SessionState.Playing ||
                state is SessionState.Stopped ||
                state is SessionState.Finished ||
                state is SessionState.Paused,
        ) {
            "Cannot Close from $state"
        }
        state = SessionState.Closed
    }
}
