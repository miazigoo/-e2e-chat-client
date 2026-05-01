package com.example.securechatapp.push

import android.content.Context
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.data.repository.SessionRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class PushRegistrationState(
    val status: String = "Не запускалась",
    val tokenPreview: String? = null,
    val lastError: String? = null,
)

@Singleton
class PushRegistrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionLocalDataSource: SecureSessionLocalDataSource,
    private val sessionRepository: SessionRepository,
) {
    private val _state = MutableStateFlow(PushRegistrationState())
    val state: StateFlow<PushRegistrationState> = _state.asStateFlow()

    suspend fun syncCurrentToken() {
        if (!hasFirebaseConfiguration()) {
            _state.value = PushRegistrationState(status = "Firebase не настроен")
            return
        }
        if (sessionLocalDataSource.getSessionSnapshot().accessToken.isNullOrBlank()) {
            _state.value = PushRegistrationState(status = "Нет активной сессии")
            return
        }

        _state.value = PushRegistrationState(status = "Запрашиваю FCM token...")
        val token = fetchTokenWithRecovery(forceRefresh = false) ?: return

        sessionRepository.updateFcmToken(token)
        _state.value = PushRegistrationState(
            status = "FCM token синхронизирован",
            tokenPreview = token.take(16),
        )
    }

    suspend fun syncToken(token: String) {
        if (!hasFirebaseConfiguration()) {
            _state.value = PushRegistrationState(status = "Firebase не настроен")
            return
        }
        if (sessionLocalDataSource.getSessionSnapshot().accessToken.isNullOrBlank()) {
            _state.value = PushRegistrationState(status = "Нет активной сессии")
            return
        }
        sessionRepository.updateFcmToken(token)
        _state.value = PushRegistrationState(
            status = "FCM token синхронизирован",
            tokenPreview = token.take(16),
        )
    }

    suspend fun clearTokenIfUnavailable() {
        if (!hasFirebaseConfiguration()) {
            _state.value = PushRegistrationState(status = "Firebase не настроен")
            return
        }
        sessionRepository.updateFcmToken(null)
        _state.value = PushRegistrationState(status = "FCM token очищен")
    }

    suspend fun forceResyncCurrentToken() {
        if (!hasFirebaseConfiguration()) {
            _state.value = PushRegistrationState(status = "Firebase не настроен")
            return
        }
        if (sessionLocalDataSource.getSessionSnapshot().accessToken.isNullOrBlank()) {
            _state.value = PushRegistrationState(status = "Нет активной сессии")
            return
        }

        _state.value = PushRegistrationState(status = "Перерегистрирую FCM...")
        val token = fetchTokenWithRecovery(forceRefresh = true) ?: return
        sessionRepository.updateFcmToken(token)
        _state.value = PushRegistrationState(
            status = "FCM token синхронизирован",
            tokenPreview = token.take(16),
        )
    }

    private fun hasFirebaseConfiguration(): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private suspend fun fetchTokenWithRecovery(forceRefresh: Boolean): String? {
        if (forceRefresh) {
            runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
            runCatching { FirebaseInstallations.getInstance().delete().await() }
            delay(500)
        }

        val firstAttempt = runCatching { FirebaseMessaging.getInstance().token.await() }
        firstAttempt.getOrNull()?.let { return it }

        val firstError = firstAttempt.exceptionOrNull()
        if (!shouldResetInstallations(firstError)) {
            _state.value = PushRegistrationState(
                status = "Не удалось получить FCM token",
                lastError = firstError?.rootMessage(),
            )
            return null
        }

        _state.value = PushRegistrationState(
            status = "Сбрасываю Firebase Installations и повторяю...",
            lastError = firstError?.rootMessage(),
        )
        runCatching { FirebaseMessaging.getInstance().deleteToken().await() }
        runCatching { FirebaseInstallations.getInstance().delete().await() }
        delay(1_000)

        val secondAttempt = runCatching { FirebaseMessaging.getInstance().token.await() }
        secondAttempt.getOrNull()?.let { return it }

        _state.value = PushRegistrationState(
            status = "FCM недоступен на этом устройстве",
            lastError = secondAttempt.exceptionOrNull()?.rootMessage(),
        )
        return null
    }

    private fun shouldResetInstallations(error: Throwable?): Boolean {
        val message = error.rootMessage().orEmpty()
        return "FIS_AUTH_ERROR" in message ||
            "Firebase Installations Service is unavailable" in message
    }

    private fun Throwable?.rootMessage(): String? {
        var current = this
        var lastMessage: String? = current?.message
        while (current?.cause != null && current.cause !== current) {
            current = current.cause
            if (!current?.message.isNullOrBlank()) {
                lastMessage = current?.message
            }
        }
        return lastMessage
    }
}
