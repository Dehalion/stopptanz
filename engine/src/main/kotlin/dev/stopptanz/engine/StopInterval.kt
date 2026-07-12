package dev.stopptanz.engine

data class StopInterval(val minMillis: Long, val maxMillis: Long) {
    init {
        require(minMillis >= 0) { "minMillis must be >= 0" }
        require(maxMillis >= minMillis) { "maxMillis must be >= minMillis" }
    }

    companion object {
        /** Placeholder for callers that don't yet trigger auto Stops (no Stop Interval UI/timer wired up). */
        val unused = StopInterval(0, 0)
    }
}
