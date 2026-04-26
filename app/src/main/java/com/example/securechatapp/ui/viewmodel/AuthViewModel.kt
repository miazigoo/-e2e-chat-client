package com.example.securechatapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechatapp.core.result.AppResult
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isAuthorized: Boolean = false,
    val requiresTotp: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val suggestedNickname: String? = null,
    val debugCode: String? = null,
    val emailMasked: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionLocalDataSource: SecureSessionLocalDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    private var hadAuthorizedSession = false

    fun clearTransientState(keepSuggestedNickname: Boolean = true) {
        _uiState.update {
            it.copy(
                isLoading = false,
                requiresTotp = false,
                errorMessage = null,
                infoMessage = null,
                suggestedNickname = if (keepSuggestedNickname) it.suggestedNickname else null,
                debugCode = null,
                emailMasked = null,
            )
        }
    }

    init {
        viewModelScope.launch {
            sessionLocalDataSource.sessionFlow.collect { session ->
                val isAuthorized = !session.accessToken.isNullOrBlank()
                _uiState.update {
                    val sessionExpired = hadAuthorizedSession && !isAuthorized
                    it.copy(
                        isAuthorized = isAuthorized,
                        infoMessage = if (sessionExpired) {
                            "Сессия завершена на сервере. Войдите снова."
                        } else {
                            it.infoMessage
                        },
                        errorMessage = if (sessionExpired) null else it.errorMessage,
                        suggestedNickname = if (sessionExpired) null else it.suggestedNickname,
                        debugCode = if (sessionExpired) null else it.debugCode,
                        emailMasked = if (sessionExpired) null else it.emailMasked,
                        requiresTotp = if (sessionExpired) false else it.requiresTotp,
                    )
                }
                hadAuthorizedSession = hadAuthorizedSession || isAuthorized
            }
        }
    }

    fun showRegistrationCompleted(nickname: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                requiresTotp = false,
                errorMessage = null,
                infoMessage = "Аккаунт создан. Теперь войди.",
                suggestedNickname = nickname,
                debugCode = null,
                emailMasked = null,
            )
        }
    }

    fun register(
        nickname: String,
        password: String,
        email: String?,
        email2faEnabled: Boolean,
        onSuccess: () -> Unit,
    ) {
        var started = false
        _uiState.update { state ->
            if (state.isLoading) {
                state
            } else {
                started = true
                state.copy(
                    isLoading = true,
                    errorMessage = null,
                    infoMessage = null,
                )
            }
        }
        if (!started) return

        viewModelScope.launch {
            when (
                val result = authRepository.register(
                    nickname = nickname,
                    password = password,
                    email = email,
                    email2faEnabled = email2faEnabled,
                )
            ) {
                is AppResult.Success -> {
                    showRegistrationCompleted(nickname)
                    onSuccess()
                }

                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        requiresTotp = result.code == "INVALID_TOTP_CODE",
                        errorMessage = if (result.code == "INVALID_TOTP_CODE") {
                            "Код Google 2FA не совпал. Проверьте его и попробуйте снова."
                        } else {
                            result.message
                        },
                    )
                }
            }
        }
    }

    fun login(
        nickname: String,
        password: String,
        totpCode: String?,
        onNeedVerifyCode: (String) -> Unit,
        onSuccess: () -> Unit,
    ) {
        var started = false
        _uiState.update { state ->
            if (state.isLoading) {
                state
            } else {
                started = true
                state.copy(
                    isLoading = true,
                    errorMessage = null,
                    infoMessage = null,
                    debugCode = null,
                    emailMasked = null,
                    requiresTotp = false,
                    suggestedNickname = state.suggestedNickname,
                )
            }
        }
        if (!started) return

        viewModelScope.launch {
            val deviceUuid = sessionLocalDataSource.getOrCreateDeviceUuid()

            when (
                val result = authRepository.login(
                    nickname = nickname,
                    password = password,
                    deviceUuid = deviceUuid,
                    totpCode = totpCode,
                )
            ) {
                is AppResult.Success -> {
                    val data = result.data

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        debugCode = data.debugCode,
                        emailMasked = data.emailMasked,
                        isAuthorized = !data.accessToken.isNullOrBlank(),
                        requiresTotp = data.requiresTotp,
                        suggestedNickname = _uiState.value.suggestedNickname,
                        infoMessage = when {
                            data.requiresTotp -> "Введите код из Google Authenticator"
                            data.requiresEmailCode -> {
                                data.emailMasked?.let { "Код отправлен на $it" }
                                    ?: "Введите код из письма"
                            }

                            else -> null
                        },
                    )

                    when {
                        data.requiresTotp -> Unit
                        data.requiresEmailCode && !data.loginChallengeId.isNullOrBlank() -> {
                            onNeedVerifyCode(data.loginChallengeId)
                        }

                        !data.accessToken.isNullOrBlank() -> {
                            onSuccess()
                        }
                    }
                }

                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        requiresTotp = result.code == "INVALID_TOTP_CODE",
                        errorMessage = result.message,
                    )
                }
            }
        }
    }

    fun verifyEmailCode(
        challengeId: String,
        code: String,
        onSuccess: () -> Unit,
    ) {
        var started = false
        _uiState.update { state ->
            if (state.isLoading) {
                state
            } else {
                started = true
                state.copy(
                    isLoading = true,
                    errorMessage = null,
                    infoMessage = null,
                )
            }
        }
        if (!started) return

        viewModelScope.launch {
            val deviceUuid = sessionLocalDataSource.getOrCreateDeviceUuid()

            when (
                val result = authRepository.verifyEmailCode(
                    loginChallengeId = challengeId,
                    code = code,
                    deviceUuid = deviceUuid,
                )
            ) {
                is AppResult.Success -> {
                    val authorized = !result.data.accessToken.isNullOrBlank()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isAuthorized = authorized,
                        suggestedNickname = if (authorized) null else _uiState.value.suggestedNickname,
                        infoMessage = if (!authorized && result.data.requiresBootstrap) {
                            "Устройство зарегистрировано. Повтори вход."
                        } else {
                            null
                        },
                    )

                    if (authorized) {
                        onSuccess()
                    }
                }

                is AppResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = result.message,
                    )
                }
            }
        }
    }
}
