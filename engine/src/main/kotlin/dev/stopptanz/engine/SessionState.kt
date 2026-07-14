package dev.stopptanz.engine

sealed class SessionState {
    data object Playing : SessionState()
    data object Stopped : SessionState()
    data object Finished : SessionState()
    data object Closed : SessionState()

    /**
     * [resumedState] is the state to restore on [SessionEngine.resumeFromPause] (`Playing` or `Stopped`).
     * [remainingFreezeMillis] is the freeze auto-resume countdown remaining at the moment of pausing,
     * `null` unless [resumedState] is `Stopped`.
     */
    data class Paused(val resumedState: SessionState, val remainingFreezeMillis: Long? = null) : SessionState()
}
