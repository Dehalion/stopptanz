package dev.stopptanz.engine

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
    val trackStatus: TrackStatus get() = TrackStatus(currentTrack, nextTrack, remainingTracks)

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

    fun nextStopDelayMillis(): Long =
        randomSource.nextLong(stopInterval.minMillis, stopInterval.maxMillis)

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

    /** Called by the playback adapter once [pauseDurationMillis] has elapsed since a Stop. */
    fun onPauseElapsed() {
        check(state is SessionState.Stopped) { "Cannot auto-resume from $state" }
        check(mode == Mode.FREEZE_DANCE) { "Auto-resume only applies in Freeze Dance mode" }
        state = SessionState.Playing
    }

    fun close() {
        check(state is SessionState.Playing || state is SessionState.Stopped || state is SessionState.Finished) {
            "Cannot Close from $state"
        }
        state = SessionState.Closed
    }
}
