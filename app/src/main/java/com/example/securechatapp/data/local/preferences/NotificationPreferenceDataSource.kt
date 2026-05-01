package com.example.securechatapp.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.securechatapp.push.NotificationSoundCatalog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class NotificationPreferenceDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val pushNotificationsEnabled = booleanPreferencesKey("push_notifications_enabled")
        val apkUpdateNotificationsEnabled = booleanPreferencesKey("apk_update_notifications_enabled")
        val notificationPermissionPromptShown = booleanPreferencesKey("notification_permission_prompt_shown")
        val messageNotificationSoundKey = stringPreferencesKey("message_notification_sound_key")
        val messageNotificationCustomSoundUri = stringPreferencesKey("message_notification_custom_sound_uri")
        val messageNotificationVibrationEnabled = booleanPreferencesKey("message_notification_vibration_enabled")
    }

    val pushNotificationsEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.pushNotificationsEnabled] ?: true
    }

    val apkUpdateNotificationsEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.apkUpdateNotificationsEnabled] ?: true
    }

    val notificationPermissionPromptShownFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.notificationPermissionPromptShown] ?: false
    }

    val messageNotificationSoundKeyFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.messageNotificationSoundKey] ?: NotificationSoundCatalog.SYSTEM_DEFAULT_KEY
    }

    val messageNotificationCustomSoundUriFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.messageNotificationCustomSoundUri]
    }

    val messageNotificationVibrationEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.messageNotificationVibrationEnabled] ?: true
    }

    suspend fun setPushNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.pushNotificationsEnabled] = enabled
        }
    }

    suspend fun setApkUpdateNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.apkUpdateNotificationsEnabled] = enabled
        }
    }

    suspend fun markNotificationPermissionPromptShown() {
        dataStore.edit { prefs ->
            prefs[Keys.notificationPermissionPromptShown] = true
        }
    }

    suspend fun resetNotificationPermissionPrompt() {
        dataStore.edit { prefs ->
            prefs[Keys.notificationPermissionPromptShown] = false
        }
    }

    suspend fun setMessageNotificationSoundKey(soundKey: String) {
        dataStore.edit { prefs ->
            prefs[Keys.messageNotificationSoundKey] = soundKey
        }
    }

    suspend fun setMessageNotificationCustomSoundUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri.isNullOrBlank()) {
                prefs.remove(Keys.messageNotificationCustomSoundUri)
            } else {
                prefs[Keys.messageNotificationCustomSoundUri] = uri
            }
        }
    }

    suspend fun setMessageNotificationVibrationEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.messageNotificationVibrationEnabled] = enabled
        }
    }

    suspend fun isPushNotificationsEnabled(): Boolean = pushNotificationsEnabledFlow.first()

    suspend fun isApkUpdateNotificationsEnabled(): Boolean = apkUpdateNotificationsEnabledFlow.first()

    suspend fun isNotificationPermissionPromptShown(): Boolean = notificationPermissionPromptShownFlow.first()

    suspend fun getMessageNotificationSoundKey(): String = messageNotificationSoundKeyFlow.first()

    suspend fun getMessageNotificationCustomSoundUri(): String? = messageNotificationCustomSoundUriFlow.first()

    suspend fun isMessageNotificationVibrationEnabled(): Boolean = messageNotificationVibrationEnabledFlow.first()
}
