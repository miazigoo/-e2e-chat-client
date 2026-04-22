package com.example.securechatapp.data.local.preferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

data class SessionState(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val deviceUuid: String? = null,
)

class SessionLocalDataSource(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val accessToken = stringPreferencesKey("access_token")
        val refreshToken = stringPreferencesKey("refresh_token")
        val deviceUuid = stringPreferencesKey("device_uuid")
    }

    val sessionFlow: Flow<SessionState> = dataStore.data.map { prefs ->
        SessionState(
            accessToken = prefs[Keys.accessToken],
            refreshToken = prefs[Keys.refreshToken],
            deviceUuid = prefs[Keys.deviceUuid],
        )
    }

    suspend fun getSessionSnapshot(): SessionState? = sessionFlow.firstOrNull()

    suspend fun getOrCreateDeviceUuid(): String {
        val current = getSessionSnapshot()?.deviceUuid
        if (!current.isNullOrBlank()) return current

        val generated = UUID.randomUUID().toString()
        dataStore.edit { prefs ->
            prefs[Keys.deviceUuid] = generated
        }
        return generated
    }

    suspend fun saveFullSession(
        accessToken: String,
        refreshToken: String,
        deviceUuid: String,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.accessToken] = accessToken
            prefs[Keys.refreshToken] = refreshToken
            prefs[Keys.deviceUuid] = deviceUuid
        }
    }

    suspend fun updateTokens(
        accessToken: String,
        refreshToken: String,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.accessToken] = accessToken
            prefs[Keys.refreshToken] = refreshToken
        }
    }

    suspend fun saveDeviceUuid(deviceUuid: String) {
        dataStore.edit { prefs ->
            prefs[Keys.deviceUuid] = deviceUuid
        }
    }

    suspend fun clearSession(keepDeviceUuid: Boolean = true) {
        val currentDeviceUuid = if (keepDeviceUuid) getSessionSnapshot()?.deviceUuid else null
        dataStore.edit { prefs ->
            prefs.remove(Keys.accessToken)
            prefs.remove(Keys.refreshToken)
            if (currentDeviceUuid == null) {
                prefs.remove(Keys.deviceUuid)
            } else {
                prefs[Keys.deviceUuid] = currentDeviceUuid
            }
        }
    }
}