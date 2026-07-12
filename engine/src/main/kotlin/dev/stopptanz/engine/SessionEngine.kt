package dev.stopptanz.engine

class SessionEngine(
    private val playlist: Playlist,
    private val mode: Mode,
    private val stopInterval: StopInterval,
    private val randomSource: RandomSource = DefaultRandomSource(),
) {
    var state: SessionState = SessionState.Playing
        private set

    fun nextStopDelayMillis(): Long =
        randomSource.nextLong(stopInterval.minMillis, stopInterval.maxMillis)

    fun stop() {
        check(state is SessionState.Playing) { "Cannot Stop from $state" }
        state = SessionState.Stopped
    }

    fun resume() {
        check(state is SessionState.Stopped) { "Cannot Resume from $state" }
        check(mode == Mode.MUSICAL_CHAIRS) { "Manual Resume only applies in Musical Chairs mode" }
        state = SessionState.Playing
    }
}
