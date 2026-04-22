package com.example.securechatapp.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ThemePreferenceDataSource(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val darkThemeEnabled = booleanPreferencesKey("dark_theme_enabled")
    }

    val darkThemeEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.darkThemeEnabled] ?: false
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.darkThemeEnabled] = enabled
        }
    }
}
