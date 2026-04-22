package com.example.securechatapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechatapp.core.result.AppResult
import com.example.securechatapp.data.local.preferences.SessionLocalDataSource
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
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val debugCode: String? = null,
    val emailMasked: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionLocalDataSource: SessionLocalDataSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionLocalDataSource.sessionFlow.collect { session ->
                _uiState.update {
                    it.copy(isAuthorized = !session.accessToken.isNullOrBlank())
                }
            }
        }
    }

    fun register(
        nickname: String,
        password: String,
        email: String?,
        email2faEnabled: Boolean,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
            )

            when (
                val result = authRepository.register(
                    nickname = nickname,
                    password = password,
                    email = email,
                    email2faEnabled = email2faEnabled,
                )
            ) {
                is AppResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        infoMessage = "Аккаунт создан. Устройство зарегистрировано. Теперь входи.",
                    )
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

    fun login(
        nickname: String,
        password: String,
        onNeedVerifyCode: (String) -> Unit,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
                debugCode = null,
                emailMasked = null,
            )

            val deviceUuid = sessionLocalDataSource.getOrCreateDeviceUuid()

            when (
                val result = authRepository.login(
                    nickname = nickname,
                    password = password,
                    deviceUuid = deviceUuid,
                )
            ) {
                is AppResult.Success -> {
                    val data = result.data

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        debugCode = data.debugCode,
                        emailMasked = data.emailMasked,
                        isAuthorized = !data.accessToken.isNullOrBlank(),
                    )

                    when {
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
            )

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