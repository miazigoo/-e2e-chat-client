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

    private val lock = Any()

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

    private val sessionStateFlow = MutableStateFlow(readSessionLocked())

    val sessionFlow: Flow<SessionState> = sessionStateFlow.asStateFlow()

    fun getSessionSnapshot(): SessionState = synchronized(lock) {
        readSessionLocked()
    }

    fun getOrCreateDeviceUuid(): String = synchronized(lock) {
        prefs.getString(Keys.DEVICE_UUID, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val generated = UUID.randomUUID().toString()
        val committed = prefs.edit()
            .putString(Keys.DEVICE_UUID, generated)
            .commit()

        check(committed) { "Не удалось сохранить идентификатор устройства" }
        emitLatestLocked()
        generated
    }

    fun saveFullSession(
        accessToken: String,
        refreshToken: String,
        deviceUuid: String,
    ) = synchronized(lock) {
        val committed = prefs.edit()
            .putString(Keys.ACCESS_TOKEN, accessToken)
            .putString(Keys.REFRESH_TOKEN, refreshToken)
            .putString(Keys.DEVICE_UUID, deviceUuid)
            .commit()

        check(committed) { "Не удалось сохранить сессию" }
        emitLatestLocked()
    }

    fun updateTokens(
        accessToken: String,
        refreshToken: String,
    ) = synchronized(lock) {
        val committed = prefs.edit()
            .putString(Keys.ACCESS_TOKEN, accessToken)
            .putString(Keys.REFRESH_TOKEN, refreshToken)
            .commit()

        check(committed) { "Не удалось обновить токены" }
        emitLatestLocked()
    }

    fun saveDeviceUuid(deviceUuid: String) = synchronized(lock) {
        val committed = prefs.edit()
            .putString(Keys.DEVICE_UUID, deviceUuid)
            .commit()

        check(committed) { "Не удалось сохранить идентификатор устройства" }
        emitLatestLocked()
    }

    fun clearSession(keepDeviceUuid: Boolean = true) = synchronized(lock) {
        val currentDeviceUuid = if (keepDeviceUuid) {
            prefs.getString(Keys.DEVICE_UUID, null)
        } else {
            null
        }

        val cleared = prefs.edit().clear().commit()
        check(cleared) { "Не удалось очистить сессию" }

        if (!currentDeviceUuid.isNullOrBlank()) {
            val restored = prefs.edit()
                .putString(Keys.DEVICE_UUID, currentDeviceUuid)
                .commit()
            check(restored) { "Не удалось восстановить идентификатор устройства" }
        }

        emitLatestLocked()
    }

    private fun readSessionLocked(): SessionState {
        return SessionState(
            accessToken = prefs.getString(Keys.ACCESS_TOKEN, null),
            refreshToken = prefs.getString(Keys.REFRESH_TOKEN, null),
            deviceUuid = prefs.getString(Keys.DEVICE_UUID, null),
        )
    }

    private fun emitLatestLocked() {
        sessionStateFlow.value = readSessionLocked()
    }
}
