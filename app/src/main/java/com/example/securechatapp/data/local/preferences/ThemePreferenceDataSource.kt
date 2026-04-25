package com.example.securechatapp.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import com.example.securechatapp.ui.theme.ThemePalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ThemePreferenceDataSource(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val darkThemeEnabled = booleanPreferencesKey("dark_theme_enabled")
        val colorScheme = stringPreferencesKey("color_scheme")
    }

    val darkThemeEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.darkThemeEnabled] ?: false
    }

    val colorSchemeFlow: Flow<ThemePalette> = dataStore.data.map { prefs ->
        ThemePalette.fromStorageValue(prefs[Keys.colorScheme])
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.darkThemeEnabled] = enabled
        }
    }

    suspend fun setColorScheme(palette: ThemePalette) {
        dataStore.edit { prefs ->
            prefs[Keys.colorScheme] = palette.storageValue
        }
    }
}
