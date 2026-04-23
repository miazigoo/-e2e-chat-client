package com.example.securechatapp.data.local.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionState(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val deviceUuid: String? = null,
)

@Singleton
class SecureSessionLocalDataSource @Inject constructor(
    @ApplicationContext context: Context,
) {

    private object Keys {
        const val PREFS_NAME = "secure_chat_secure_session"
        const val ACCESS_TOKEN = "access_token"
        const val REFRESH_TOKEN = "refresh_token"
        const val DEVICE_UUID = "device_uuid"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        Keys.PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val sessionStateFlow = MutableStateFlow(readSession())

    val sessionFlow: Flow<SessionState> = sessionStateFlow.asStateFlow()

    suspend fun getSessionSnapshot(): SessionState = readSession()

    suspend fun getOrCreateDeviceUuid(): String {
        val current = prefs.getString(Keys.DEVICE_UUID, null)
        if (!current.isNullOrBlank()) return current

        val generated = UUID.randomUUID().toString()
        prefs.edit()
            .putString(Keys.DEVICE_UUID, generated)
            .apply()

        emitLatest()
        return generated
    }

    suspend fun saveFullSession(
        accessToken: String,
        refreshToken: String,
        deviceUuid: String,
    ) {
        prefs.edit()
            .putString(Keys.ACCESS_TOKEN, accessToken)
            .putString(Keys.REFRESH_TOKEN, refreshToken)
            .putString(Keys.DEVICE_UUID, deviceUuid)
            .apply()

        emitLatest()
    }

    suspend fun updateTokens(
        accessToken: String,
        refreshToken: String,
    ) {
        prefs.edit()
            .putString(Keys.ACCESS_TOKEN, accessToken)
            .putString(Keys.REFRESH_TOKEN, refreshToken)
            .apply()

        emitLatest()
    }

    suspend fun saveDeviceUuid(deviceUuid: String) {
        prefs.edit()
            .putString(Keys.DEVICE_UUID, deviceUuid)
            .apply()

        emitLatest()
    }

    suspend fun clearSession(keepDeviceUuid: Boolean = true) {
        val currentDeviceUuid = if (keepDeviceUuid) {
            prefs.getString(Keys.DEVICE_UUID, null)
        } else {
            null
        }

        prefs.edit().clear().apply()

        if (!currentDeviceUuid.isNullOrBlank()) {
            prefs.edit()
                .putString(Keys.DEVICE_UUID, currentDeviceUuid)
                .apply()
        }

        emitLatest()
    }

    private fun readSession(): SessionState {
        return SessionState(
            accessToken = prefs.getString(Keys.ACCESS_TOKEN, null),
            refreshToken = prefs.getString(Keys.REFRESH_TOKEN, null),
            deviceUuid = prefs.getString(Keys.DEVICE_UUID, null),
        )
    }

    private fun emitLatest() {
        sessionStateFlow.value = readSession()
    }
}