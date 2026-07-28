// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
package com.stopptanz.app.session

import com.stopptanz.app.settings.SettingsRepository
import com.stopptanz.engine.Mode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val KEY_PAUSE_DURATION_MILLIS = "pause_duration_millis"
private const val DEFAULT_PAUSE_DURATION_MILLIS = 4_000
private const val KEY_MODE = "mode"
private val DEFAULT_MODE = Mode.FREEZE_DANCE
// Freeze Dance keeps the pre-existing global keys so upgrading users' current values carry over
// instead of silently resetting; Musical Chairs gets its own keys with a larger default interval.
private const val KEY_STOP_INTERVAL_MIN_MILLIS_FREEZE_DANCE = "stop_interval_min_millis"
private const val DEFAULT_STOP_INTERVAL_MIN_MILLIS_FREEZE_DANCE = 10_000
private const val KEY_STOP_INTERVAL_MAX_MILLIS_FREEZE_DANCE = "stop_interval_max_millis"
private const val DEFAULT_STOP_INTERVAL_MAX_MILLIS_FREEZE_DANCE = 25_000
private const val KEY_STOP_INTERVAL_MIN_MILLIS_MUSICAL_CHAIRS = "stop_interval_min_millis_musical_chairs"
private const val DEFAULT_STOP_INTERVAL_MIN_MILLIS_MUSICAL_CHAIRS = 20_000
private const val KEY_STOP_INTERVAL_MAX_MILLIS_MUSICAL_CHAIRS = "stop_interval_max_millis_musical_chairs"
private const val DEFAULT_STOP_INTERVAL_MAX_MILLIS_MUSICAL_CHAIRS = 45_000
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

    fun stopIntervalMinMillisFlow(mode: Mode): Flow<Int> = when (mode) {
        Mode.FREEZE_DANCE -> settings.intFlow(KEY_STOP_INTERVAL_MIN_MILLIS_FREEZE_DANCE, DEFAULT_STOP_INTERVAL_MIN_MILLIS_FREEZE_DANCE)
        Mode.MUSICAL_CHAIRS -> settings.intFlow(KEY_STOP_INTERVAL_MIN_MILLIS_MUSICAL_CHAIRS, DEFAULT_STOP_INTERVAL_MIN_MILLIS_MUSICAL_CHAIRS)
    }

    suspend fun setStopIntervalMinMillis(mode: Mode, value: Int) {
        val key = when (mode) {
            Mode.FREEZE_DANCE -> KEY_STOP_INTERVAL_MIN_MILLIS_FREEZE_DANCE
            Mode.MUSICAL_CHAIRS -> KEY_STOP_INTERVAL_MIN_MILLIS_MUSICAL_CHAIRS
        }
        settings.setInt(key, value)
    }

    fun stopIntervalMaxMillisFlow(mode: Mode): Flow<Int> = when (mode) {
        Mode.FREEZE_DANCE -> settings.intFlow(KEY_STOP_INTERVAL_MAX_MILLIS_FREEZE_DANCE, DEFAULT_STOP_INTERVAL_MAX_MILLIS_FREEZE_DANCE)
        Mode.MUSICAL_CHAIRS -> settings.intFlow(KEY_STOP_INTERVAL_MAX_MILLIS_MUSICAL_CHAIRS, DEFAULT_STOP_INTERVAL_MAX_MILLIS_MUSICAL_CHAIRS)
    }

    suspend fun setStopIntervalMaxMillis(mode: Mode, value: Int) {
        val key = when (mode) {
            Mode.FREEZE_DANCE -> KEY_STOP_INTERVAL_MAX_MILLIS_FREEZE_DANCE
            Mode.MUSICAL_CHAIRS -> KEY_STOP_INTERVAL_MAX_MILLIS_MUSICAL_CHAIRS
        }
        settings.setInt(key, value)
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
