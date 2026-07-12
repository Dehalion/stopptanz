package dev.stopptanz.app.session

import dev.stopptanz.app.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow

private const val KEY_PAUSE_DURATION_MILLIS = "pause_duration_millis"
private const val DEFAULT_PAUSE_DURATION_MILLIS = 5_000

class SessionSettings(private val settings: SettingsRepository) {

    fun pauseDurationMillisFlow(): Flow<Int> =
        settings.intFlow(KEY_PAUSE_DURATION_MILLIS, DEFAULT_PAUSE_DURATION_MILLIS)

    suspend fun setPauseDurationMillis(value: Int) {
        settings.setInt(KEY_PAUSE_DURATION_MILLIS, value)
    }
}
