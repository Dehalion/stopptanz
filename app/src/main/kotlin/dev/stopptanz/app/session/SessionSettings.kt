package dev.stopptanz.app.session

import dev.stopptanz.app.settings.SettingsRepository
import dev.stopptanz.engine.Mode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val KEY_PAUSE_DURATION_MILLIS = "pause_duration_millis"
private const val DEFAULT_PAUSE_DURATION_MILLIS = 5_000
private const val KEY_MODE = "mode"
private val DEFAULT_MODE = Mode.FREEZE_DANCE
private const val KEY_STOP_INTERVAL_MIN_MILLIS = "stop_interval_min_millis"
private const val DEFAULT_STOP_INTERVAL_MIN_MILLIS = 5_000
private const val KEY_STOP_INTERVAL_MAX_MILLIS = "stop_interval_max_millis"
private const val DEFAULT_STOP_INTERVAL_MAX_MILLIS = 15_000
private const val KEY_SHUFFLE = "shuffle"
private const val KEY_LOOP = "loop"

class SessionSettings(private val settings: SettingsRepository) {

    fun pauseDurationMillisFlow(): Flow<Int> =
        settings.intFlow(KEY_PAUSE_DURATION_MILLIS, DEFAULT_PAUSE_DURATION_MILLIS)

    suspend fun setPauseDurationMillis(value: Int) {
        settings.setInt(KEY_PAUSE_DURATION_MILLIS, value)
    }

    fun modeFlow(): Flow<Mode> =
        settings.stringFlow(KEY_MODE, DEFAULT_MODE.name).map { Mode.valueOf(it) }

    suspend fun setMode(mode: Mode) {
        settings.setString(KEY_MODE, mode.name)
    }

    fun stopIntervalMinMillisFlow(): Flow<Int> =
        settings.intFlow(KEY_STOP_INTERVAL_MIN_MILLIS, DEFAULT_STOP_INTERVAL_MIN_MILLIS)

    suspend fun setStopIntervalMinMillis(value: Int) {
        settings.setInt(KEY_STOP_INTERVAL_MIN_MILLIS, value)
    }

    fun stopIntervalMaxMillisFlow(): Flow<Int> =
        settings.intFlow(KEY_STOP_INTERVAL_MAX_MILLIS, DEFAULT_STOP_INTERVAL_MAX_MILLIS)

    suspend fun setStopIntervalMaxMillis(value: Int) {
        settings.setInt(KEY_STOP_INTERVAL_MAX_MILLIS, value)
    }

    fun shuffleFlow(): Flow<Boolean> = settings.booleanFlow(KEY_SHUFFLE, false)

    suspend fun setShuffle(value: Boolean) {
        settings.setBoolean(KEY_SHUFFLE, value)
    }

    fun loopFlow(): Flow<Boolean> = settings.booleanFlow(KEY_LOOP, false)

    suspend fun setLoop(value: Boolean) {
        settings.setBoolean(KEY_LOOP, value)
    }
}
