package dev.stopptanz.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    fun stringFlow(key: String, default: String): Flow<String> =
        context.settingsDataStore.data.map { it[stringPreferencesKey(key)] ?: default }

    suspend fun setString(key: String, value: String) {
        context.settingsDataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    fun intFlow(key: String, default: Int): Flow<Int> =
        context.settingsDataStore.data.map { it[intPreferencesKey(key)] ?: default }

    suspend fun setInt(key: String, value: Int) {
        context.settingsDataStore.edit { it[intPreferencesKey(key)] = value }
    }

    fun booleanFlow(key: String, default: Boolean): Flow<Boolean> =
        context.settingsDataStore.data.map { it[booleanPreferencesKey(key)] ?: default }

    suspend fun setBoolean(key: String, value: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey(key)] = value }
    }
}
