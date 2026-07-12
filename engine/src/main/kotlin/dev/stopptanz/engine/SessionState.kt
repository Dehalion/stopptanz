package dev.stopptanz.engine

sealed class SessionState {
    data object Playing : SessionState()
    data object Stopped : SessionState()
    data object Finished : SessionState()
}
