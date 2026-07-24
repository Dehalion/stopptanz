package com.stopptanz.app.session

/** Live playback position for the current Track, polled from the player. */
data class PlaybackPosition(val currentMillis: Long, val totalMillis: Long) {
    fun formatCurrent(): String = format(currentMillis)

    fun formatTotal(): String = format(totalMillis)

    private fun format(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}
