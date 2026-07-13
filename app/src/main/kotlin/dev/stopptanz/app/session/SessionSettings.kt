package dev.stopptanz.app.session

import dev.stopptanz.app.settings.SettingsRepository
import dev.stopptanz.engine.Mode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val KEY_PAUSE_DURATION_MILLIS = "pause_duration_millis"
private const val DEFAULT_PAUSE_DURATION_MILLIS = 5_000
private const val KEY_MODE = "mode"
private val DEFAULT_MODE = Mode.FREEZE_DANCE

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
}
