package com.example.securechatapp.ui.viewmodel

import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechatapp.data.local.preferences.SessionLocalDataSource
import com.example.securechatapp.data.local.preferences.ThemePreferenceDataSource
import com.example.securechatapp.data.repository.BackendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class SettingsUiState(
    val nickname: String = "—",
    val userId: String = "—",
    val sessionStatus: String = "Не авторизован",
    val deviceUuid: String = "—",
    val darkThemeEnabled: Boolean = false,
    val lastHeartbeatAt: String = "ещё не отправлялся",
    val isSendingHeartbeat: Boolean = false,
    val isLoggingOut: Boolean = false,
    val isRevokingDevice: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sessionLocalDataSource: SessionLocalDataSource,
    private val themePreferenceDataSource: ThemePreferenceDataSource,
    private val repo: BackendRepository,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSession()
        observeTheme()
    }

    private fun observeSession() {
        viewModelScope.launch {
            sessionLocalDataSource.sessionFlow.collectLatest { session ->
                val payload = decodeJwtPayload(session.accessToken)
                val nickname = payload?.get("nickname")?.jsonPrimitive?.content ?: "—"
                val userId = payload?.get("sub")?.jsonPrimitive?.content ?: "—"

                _uiState.update {
                    it.copy(
                        nickname = nickname,
                        userId = userId,
                        sessionStatus = if (!session.accessToken.isNullOrBlank()) {
                            "Сессия активна"
                        } else {
                            "Не авторизован"
                        },
                        deviceUuid = session.deviceUuid ?: "—",
                    )
                }
            }
        }
    }

    private fun observeTheme() {
        viewModelScope.launch {
            themePreferenceDataSource.darkThemeEnabledFlow.collectLatest { enabled ->
                _uiState.update {
                    it.copy(darkThemeEnabled = enabled)
                }
            }
        }
    }

    fun setDarkThemeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themePreferenceDataSource.setDarkThemeEnabled(enabled)
        }
    }

    fun sendHeartbeatNow() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSendingHeartbeat = true,
                    error = null,
                    info = null,
                )
            }

            val lastSeenAt = repo.heartbeat()

            _uiState.update {
                it.copy(
                    isSendingHeartbeat = false,
                    lastHeartbeatAt = lastSeenAt?.let(::formatHeartbeatTime)
                        ?: formatHeartbeatTime(OffsetDateTime.now().toString()),
                    info = if (lastSeenAt != null) {
                        "Heartbeat отправлен"
                    } else {
                        "Heartbeat отправлен локально"
                    },
                )
            }
        }
    }

    fun logoutSession(onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoggingOut = true,
                    error = null,
                    info = null,
                )
            }

            repo.logoutSession()

            _uiState.update {
                it.copy(isLoggingOut = false)
            }

            onDone()
        }
    }

    fun revokeCurrentDevice(onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRevokingDevice = true,
                    error = null,
                    info = null,
                )
            }

            repo.revokeCurrentDevice()

            _uiState.update {
                it.copy(isRevokingDevice = false)
            }

            onDone()
        }
    }

    private fun decodeJwtPayload(token: String?) =
        runCatching {
            if (token.isNullOrBlank()) return@runCatching null
            val parts = token.split(".")
            if (parts.size < 2) return@runCatching null

            val payloadPart = parts[1]
            val padded = payloadPart + "=".repeat((4 - payloadPart.length % 4) % 4)

            val decodedBytes = Base64.decode(
                padded,
                Base64.URL_SAFE or Base64.NO_WRAP,
            )
            val decoded = String(decodedBytes, Charsets.UTF_8)
            json.parseToJsonElement(decoded).jsonObject
        }.getOrNull()

    private fun formatHeartbeatTime(raw: String): String {
        return runCatching {
            OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        }.getOrDefault(raw)
    }
}
