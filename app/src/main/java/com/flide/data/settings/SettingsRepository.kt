package com.flide.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "flide_settings")

data class IdeSettings(
    val autoSave: Boolean = true,
    val fontSizeSp: Float = 14f,
    val darkTheme: Boolean = true,
    val flutterChannel: String = "stable",
    val environmentConfigured: Boolean = false,
    val wakeLockEnabled: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val AUTO_SAVE = booleanPreferencesKey("auto_save")
        val FONT_SIZE_SP = floatPreferencesKey("font_size_sp")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val FLUTTER_CHANNEL = stringPreferencesKey("flutter_channel")
        val ENVIRONMENT_CONFIGURED = booleanPreferencesKey("environment_configured")
        val WAKE_LOCK_ENABLED = booleanPreferencesKey("wake_lock_enabled")
    }

    val settingsFlow: Flow<IdeSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            IdeSettings(
                autoSave = preferences[PreferencesKeys.AUTO_SAVE] ?: true,
                fontSizeSp = preferences[PreferencesKeys.FONT_SIZE_SP] ?: 14f,
                darkTheme = preferences[PreferencesKeys.DARK_THEME] ?: true,
                flutterChannel = preferences[PreferencesKeys.FLUTTER_CHANNEL] ?: "stable",
                environmentConfigured = preferences[PreferencesKeys.ENVIRONMENT_CONFIGURED] ?: false,
                wakeLockEnabled = preferences[PreferencesKeys.WAKE_LOCK_ENABLED] ?: true
            )
        }

    suspend fun updateAutoSave(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_SAVE] = enabled
        }
    }

    suspend fun updateFontSize(sizeSp: Float) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FONT_SIZE_SP] = sizeSp
        }
    }

    suspend fun updateDarkTheme(dark: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_THEME] = dark
        }
    }

    suspend fun updateFlutterChannel(channel: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FLUTTER_CHANNEL] = channel
        }
    }

    suspend fun setEnvironmentConfigured(configured: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ENVIRONMENT_CONFIGURED] = configured
        }
    }

    suspend fun updateWakeLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WAKE_LOCK_ENABLED] = enabled
        }
    }
}
