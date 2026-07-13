package dev.stopptanz.engine

class SessionEngine(
    val playlist: Playlist,
    val mode: Mode,
    private val stopInterval: StopInterval,
    val pauseDurationMillis: Long,
    private val randomSource: RandomSource = DefaultRandomSource(),
) {
    var state: SessionState = SessionState.Playing
        private set

    /** Playlist tracks in playback order: shuffled once per Session if [Playlist.shuffle] is set. */
    val orderedTracks: List<String> by lazy {
        if (playlist.shuffle) shuffledTracks() else playlist.tracks
    }

    fun nextStopDelayMillis(): Long =
        randomSource.nextLong(stopInterval.minMillis, stopInterval.maxMillis)

    /** Called by the playback adapter when the Playlist reaches its end. */
    fun onPlaylistEnded() {
        check(state is SessionState.Playing) { "Cannot end Playlist from $state" }
        if (!playlist.loop) {
            state = SessionState.Finished
        }
    }

    private fun shuffledTracks(): List<String> {
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
        check(mode == Mode.MUSICAL_CHAIRS) { "Manual Resume only applies in Musical Chairs mode" }
        state = SessionState.Playing
    }

    /** Called by the playback adapter once [pauseDurationMillis] has elapsed since a Stop. */
    fun onPauseElapsed() {
        check(state is SessionState.Stopped) { "Cannot auto-resume from $state" }
        check(mode == Mode.FREEZE_DANCE) { "Auto-resume only applies in Freeze Dance mode" }
        state = SessionState.Playing
    }
}
