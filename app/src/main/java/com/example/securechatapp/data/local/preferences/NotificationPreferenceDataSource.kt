package com.example.securechatapp.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    suspend fun isPushNotificationsEnabled(): Boolean = pushNotificationsEnabledFlow.first()

    suspend fun isApkUpdateNotificationsEnabled(): Boolean = apkUpdateNotificationsEnabledFlow.first()

    suspend fun isNotificationPermissionPromptShown(): Boolean = notificationPermissionPromptShownFlow.first()
}
