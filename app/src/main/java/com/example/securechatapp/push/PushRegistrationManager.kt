package com.example.securechatapp.push

import android.content.Context
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.data.repository.SessionRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class PushRegistrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionLocalDataSource: SecureSessionLocalDataSource,
    private val sessionRepository: SessionRepository,
) {
    suspend fun syncCurrentToken() {
        if (!hasFirebaseConfiguration()) return
        if (sessionLocalDataSource.getSessionSnapshot().accessToken.isNullOrBlank()) return

        val token = runCatching {
            FirebaseMessaging.getInstance().token.await()
        }.getOrNull() ?: return

        sessionRepository.updateFcmToken(token)
    }

    suspend fun syncToken(token: String) {
        if (!hasFirebaseConfiguration()) return
        if (sessionLocalDataSource.getSessionSnapshot().accessToken.isNullOrBlank()) return
        sessionRepository.updateFcmToken(token)
    }

    suspend fun clearTokenIfUnavailable() {
        if (!hasFirebaseConfiguration()) return
        sessionRepository.updateFcmToken(null)
    }

    private fun hasFirebaseConfiguration(): Boolean = FirebaseApp.getApps(context).isNotEmpty()
}
