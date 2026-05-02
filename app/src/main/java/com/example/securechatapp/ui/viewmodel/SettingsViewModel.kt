package com.example.securechatapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechatapp.BuildConfig
import com.example.securechatapp.data.files.ApkUpdateInstallState
import com.example.securechatapp.data.files.ApkUpdateManager
import com.example.securechatapp.data.repository.DeviceRepository
import com.example.securechatapp.data.local.preferences.NotificationPreferenceDataSource
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.data.local.preferences.ThemePreferenceDataSource
import com.example.securechatapp.data.repository.BackendApiException
import com.example.securechatapp.data.repository.AppUpdateRepository
import com.example.securechatapp.data.repository.AppUpdateStateRepository
import com.example.securechatapp.data.repository.SessionRepository
import com.example.securechatapp.data.repository.UpdateUserProfileInput
import com.example.securechatapp.data.repository.UserProfileRepository
import com.example.securechatapp.domain.model.AuthorizedDevice
import com.example.securechatapp.domain.model.DeviceAuthorizationRequest
import com.example.securechatapp.push.NotificationSoundCatalog
import com.example.securechatapp.push.PushRegistrationManager
import com.example.securechatapp.ui.theme.ThemePalette
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

data class SettingsUiState(
    val nickname: String = "—",
    val publicId: String = "—",
    val fullName: String = "",
    val bio: String = "",
    val userId: String = "—",
    val sessionStatus: String = "Не авторизован",
    val deviceUuid: String = "—",
    val accessTokenExpiresAt: String = "—",
    val devices: List<AuthorizedDevice> = emptyList(),
    val pendingDeviceRequests: List<DeviceAuthorizationRequest> = emptyList(),
    val avatarUrl: String? = null,
    val avatarUpdatedAt: String = "—",
    val languageCode: String = "ru",
    val profileTheme: String = "dark",
    val pushNotificationsEnabled: Boolean = true,
    val apkUpdateNotificationsEnabled: Boolean = true,
    val notificationSoundKey: String = NotificationSoundCatalog.SYSTEM_DEFAULT_KEY,
    val notificationSoundLabel: String = "Системный по умолчанию",
    val notificationVibrationEnabled: Boolean = true,
    val google2faEnabled: Boolean = false,
    val pendingGoogle2faSecret: String? = null,
    val pendingGoogle2faProvisioningUri: String? = null,
    val pendingGoogle2faIssuer: String? = null,
    val pendingGoogle2faAccountName: String? = null,
    val google2faConfirmedAt: String? = null,
    val isStartingGoogle2fa: Boolean = false,
    val isConfirmingGoogle2fa: Boolean = false,
    val isDisablingGoogle2fa: Boolean = false,
    val darkThemeEnabled: Boolean = true,
    val colorScheme: ThemePalette = ThemePalette.TELEGRAM,
    val isLoadingProfile: Boolean = false,
    val isLoadingDevices: Boolean = false,
    val isResolvingDeviceRequest: Boolean = false,
    val isRevokingListedDevice: Boolean = false,
    val isSavingProfile: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val lastHeartbeatAt: String = "ещё не отправлялся",
    val isSendingHeartbeat: Boolean = false,
    val isLoggingOut: Boolean = false,
    val isLoggingOutAll: Boolean = false,
    val isRevokingDevice: Boolean = false,
    val currentVersionName: String = BuildConfig.VERSION_NAME,
    val currentVersionCode: Int = 0,
    val updateStatus: String = "Проверка не выполнялась",
    val updateDownloadUrl: String? = null,
    val latestVersionName: String? = null,
    val latestVersionCode: Int? = null,
    val latestVersionChangelog: String? = null,
    val isCheckingForUpdates: Boolean = false,
    val updateInstallState: ApkUpdateInstallState = ApkUpdateInstallState(),
    val pushRegistrationStatus: String = "Не запускалась",
    val pushRegistrationTokenPreview: String? = null,
    val pushRegistrationError: String? = null,
    val error: String? = null,
    val info: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionLocalDataSource: SecureSessionLocalDataSource,
    private val themePreferenceDataSource: ThemePreferenceDataSource,
    private val notificationPreferenceDataSource: NotificationPreferenceDataSource,
    private val sessionRepository: SessionRepository,
    private val deviceRepository: DeviceRepository,
    private val pushRegistrationManager: PushRegistrationManager,
    private val userProfileRepository: UserProfileRepository,
    private val appUpdateRepository: AppUpdateRepository,
    private val apkUpdateManager: ApkUpdateManager,
    private val appUpdateStateRepository: AppUpdateStateRepository,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSession()
        observeTheme()
        observeNotificationPreferences()
        observePushRegistrationState()
        observeUpdateInstaller()
        refreshProfile()
        refreshDevices()
        checkForAppUpdates()
    }

    private fun observeUpdateInstaller() {
        viewModelScope.launch {
            apkUpdateManager.state.collectLatest { installState ->
                _uiState.update {
                    it.copy(updateInstallState = installState)
                }
            }
        }
    }

    private fun observeSession() {
        viewModelScope.launch {
            sessionLocalDataSource.sessionFlow.collectLatest { session ->
                val payload = decodeJwtPayload(session.accessToken)
                val nickname = payload?.get("nickname")?.jsonPrimitive?.content ?: "—"
                val userId = payload?.get("sub")?.jsonPrimitive?.content ?: "—"

                val expiresAt = payload
                    ?.get("exp")
                    ?.jsonPrimitive
                    ?.content
                    ?.toLongOrNull()
                    ?.let(::formatEpochSeconds)

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
                        accessTokenExpiresAt = expiresAt ?: "—",
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
        viewModelScope.launch {
            themePreferenceDataSource.colorSchemeFlow.collectLatest { palette ->
                _uiState.update {
                    it.copy(colorScheme = palette)
                }
            }
        }
    }

    private fun observeNotificationPreferences() {
        viewModelScope.launch {
            notificationPreferenceDataSource.pushNotificationsEnabledFlow.collectLatest { enabled ->
                _uiState.update { it.copy(pushNotificationsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            notificationPreferenceDataSource.apkUpdateNotificationsEnabledFlow.collectLatest { enabled ->
                _uiState.update { it.copy(apkUpdateNotificationsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            notificationPreferenceDataSource.messageNotificationSoundKeyFlow.collectLatest { soundKey ->
                val customSoundUri = notificationPreferenceDataSource.getMessageNotificationCustomSoundUri()
                _uiState.update {
                    it.copy(
                        notificationSoundKey = soundKey,
                        notificationSoundLabel = NotificationSoundCatalog.resolveSelectedLabel(
                            context = context,
                            soundKey = soundKey,
                            customSoundUri = customSoundUri,
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            notificationPreferenceDataSource.messageNotificationCustomSoundUriFlow.collectLatest { customSoundUri ->
                val soundKey = notificationPreferenceDataSource.getMessageNotificationSoundKey()
                _uiState.update {
                    it.copy(
                        notificationSoundLabel = NotificationSoundCatalog.resolveSelectedLabel(
                            context = context,
                            soundKey = soundKey,
                            customSoundUri = customSoundUri,
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            notificationPreferenceDataSource.messageNotificationVibrationEnabledFlow.collectLatest { enabled ->
                _uiState.update { it.copy(notificationVibrationEnabled = enabled) }
            }
        }
    }

    private fun observePushRegistrationState() {
        viewModelScope.launch {
            pushRegistrationManager.state.collectLatest { state ->
                _uiState.update {
                    it.copy(
                        pushRegistrationStatus = state.status,
                        pushRegistrationTokenPreview = state.tokenPreview,
                        pushRegistrationError = state.lastError,
                    )
                }
            }
        }
    }

    fun setDarkThemeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themePreferenceDataSource.setDarkThemeEnabled(enabled)
        }
    }

    fun setColorScheme(palette: ThemePalette) {
        viewModelScope.launch {
            themePreferenceDataSource.setColorScheme(palette)
        }
    }

    fun updateNickname(value: String) {
        _uiState.update { it.copy(nickname = value) }
    }

    fun updateFullName(value: String) {
        _uiState.update { it.copy(fullName = value) }
    }

    fun updateBio(value: String) {
        _uiState.update { it.copy(bio = value) }
    }

    fun updateLanguageCode(value: String) {
        _uiState.update { it.copy(languageCode = value) }
    }

    fun updateProfileTheme(value: String) {
        _uiState.update { it.copy(profileTheme = value) }
    }

    fun setPushNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferenceDataSource.setPushNotificationsEnabled(enabled)
            if (enabled) {
                notificationPreferenceDataSource.resetNotificationPermissionPrompt()
                pushRegistrationManager.forceResyncCurrentToken()
                refreshDevices()
            }
        }
    }

    fun retryPushRegistration() {
        viewModelScope.launch {
            pushRegistrationManager.forceResyncCurrentToken()
            refreshDevices()
        }
    }

    fun setApkUpdateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferenceDataSource.setApkUpdateNotificationsEnabled(enabled)
        }
    }

    fun setBundledNotificationSound(soundKey: String) {
        viewModelScope.launch {
            notificationPreferenceDataSource.setMessageNotificationSoundKey(soundKey)
        }
    }

    fun setDefaultNotificationSound() {
        viewModelScope.launch {
            notificationPreferenceDataSource.setMessageNotificationSoundKey(
                NotificationSoundCatalog.SYSTEM_DEFAULT_KEY,
            )
        }
    }

    fun setCustomSystemNotificationSound(uri: Uri?) {
        viewModelScope.launch {
            if (uri == null) {
                notificationPreferenceDataSource.setMessageNotificationSoundKey(
                    NotificationSoundCatalog.SYSTEM_DEFAULT_KEY,
                )
                notificationPreferenceDataSource.setMessageNotificationCustomSoundUri(null)
                return@launch
            }

            notificationPreferenceDataSource.setMessageNotificationCustomSoundUri(uri.toString())
            notificationPreferenceDataSource.setMessageNotificationSoundKey(
                NotificationSoundCatalog.SYSTEM_PICKED_KEY,
            )
        }
    }

    fun setNotificationVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferenceDataSource.setMessageNotificationVibrationEnabled(enabled)
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingProfile = true,
                    error = null,
                    info = null,
                )
            }

            val result = runCatching { userProfileRepository.getMyProfile() }

            result
                .getOrNull()
                ?.let { profile ->
                    notificationPreferenceDataSource.setPushNotificationsEnabled(
                        profile.settings.pushNotificationsEnabled,
                    )
                    notificationPreferenceDataSource.setApkUpdateNotificationsEnabled(
                        profile.settings.apkUpdateNotificationsEnabled,
                    )
                    _uiState.update {
                        it.copy(
                            isLoadingProfile = false,
                            nickname = profile.nickname,
                            publicId = profile.publicId,
                            fullName = profile.fullName.orEmpty(),
                            bio = profile.bio.orEmpty(),
                            avatarUrl = profile.avatarUrl,
                            avatarUpdatedAt = profile.avatarUpdatedAt?.let(::formatHeartbeatTime) ?: "—",
                            languageCode = profile.settings.languageCode,
                            profileTheme = profile.settings.theme,
                            pushNotificationsEnabled = profile.settings.pushNotificationsEnabled,
                            apkUpdateNotificationsEnabled = profile.settings.apkUpdateNotificationsEnabled,
                            google2faEnabled = profile.settings.google2faEnabled,
                            error = null,
                        )
                    }
                }
            result.exceptionOrNull()?.let { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingProfile = false,
                            error = error.message ?: "Не удалось загрузить профиль",
                        )
                    }
                }
        }
    }

    fun saveProfile() {
        viewModelScope.launch {
            val current = _uiState.value
            _uiState.update {
                it.copy(
                    isSavingProfile = true,
                    error = null,
                    info = null,
                )
            }

            val result = runCatching {
                userProfileRepository.updateMyProfile(
                    UpdateUserProfileInput(
                        nickname = current.nickname,
                        fullName = current.fullName,
                        bio = current.bio,
                        languageCode = current.languageCode,
                        theme = current.profileTheme,
                        pushNotificationsEnabled = current.pushNotificationsEnabled,
                        apkUpdateNotificationsEnabled = current.apkUpdateNotificationsEnabled,
                    )
                )
            }

            result.getOrNull()?.let { profile ->
                notificationPreferenceDataSource.setPushNotificationsEnabled(
                    profile.settings.pushNotificationsEnabled,
                )
                notificationPreferenceDataSource.setApkUpdateNotificationsEnabled(
                    profile.settings.apkUpdateNotificationsEnabled,
                )
                _uiState.update {
                    it.copy(
                        isSavingProfile = false,
                        nickname = profile.nickname,
                        publicId = profile.publicId,
                        fullName = profile.fullName.orEmpty(),
                        bio = profile.bio.orEmpty(),
                        avatarUrl = profile.avatarUrl,
                        avatarUpdatedAt = profile.avatarUpdatedAt?.let(::formatHeartbeatTime) ?: "—",
                        languageCode = profile.settings.languageCode,
                        profileTheme = profile.settings.theme,
                        pushNotificationsEnabled = profile.settings.pushNotificationsEnabled,
                        apkUpdateNotificationsEnabled = profile.settings.apkUpdateNotificationsEnabled,
                        google2faEnabled = profile.settings.google2faEnabled,
                        info = "Профиль обновлён",
                    )
                }

                when (profile.settings.theme) {
                    "dark" -> themePreferenceDataSource.setDarkThemeEnabled(true)
                    "light" -> themePreferenceDataSource.setDarkThemeEnabled(false)
                }
            }

            result.exceptionOrNull()?.let { error ->
                _uiState.update {
                    it.copy(
                        isSavingProfile = false,
                        error = error.message ?: "Не удалось обновить профиль",
                    )
                }
            }
        }
    }

    fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploadingAvatar = true,
                    error = null,
                    info = null,
                )
            }

            runCatching { userProfileRepository.uploadMyAvatar(uri) }
                .onSuccess { profile ->
                    _uiState.update {
                        it.copy(
                            isUploadingAvatar = false,
                            avatarUrl = profile.avatarUrl,
                            avatarUpdatedAt = profile.avatarUpdatedAt?.let(::formatHeartbeatTime) ?: "—",
                            info = "Аватар обновлён",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isUploadingAvatar = false,
                            error = error.message ?: "Не удалось загрузить аватар",
                        )
                    }
                }
        }
    }

    fun deleteAvatar() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploadingAvatar = true,
                    error = null,
                    info = null,
                )
            }

            runCatching { userProfileRepository.deleteMyAvatar() }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            isUploadingAvatar = false,
                            avatarUrl = null,
                            avatarUpdatedAt = "—",
                            info = "Аватар удалён",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isUploadingAvatar = false,
                            error = error.message ?: "Не удалось удалить аватар",
                        )
                    }
                }
        }
    }

    fun checkForAppUpdates() {
        viewModelScope.launch {
            val currentVersionCode = resolveCurrentVersionCode()

            _uiState.update {
                it.copy(
                    isCheckingForUpdates = true,
                    currentVersionCode = currentVersionCode,
                    error = null,
                    info = null,
                )
            }

            runCatching { appUpdateRepository.checkForUpdate(currentVersionCode) }
                .onSuccess { result ->
                    apkUpdateManager.syncState()
                    appUpdateStateRepository.publishVersionCheck(result)
                    _uiState.update {
                        it.copy(
                            isCheckingForUpdates = false,
                            updateStatus = if (result.updateAvailable) {
                                if (result.updateRequired) {
                                    "Требуется обновление ${result.release.versionName} (${result.release.versionCode})"
                                } else {
                                    "Доступно обновление ${result.release.versionName} (${result.release.versionCode})"
                                }
                            } else {
                                "Установлена актуальная версия"
                            },
                            updateDownloadUrl = result.release.downloadUrl,
                            latestVersionName = result.release.versionName,
                            latestVersionCode = result.release.versionCode,
                            latestVersionChangelog = result.release.changelog,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isCheckingForUpdates = false,
                            updateStatus = "Не удалось проверить обновления",
                            error = error.message ?: "Не удалось проверить обновления",
                        )
                    }
                }
        }
    }

    fun startAppUpdate() {
        val latestVersionName = _uiState.value.latestVersionName
        val latestVersionCode = _uiState.value.latestVersionCode
        if (latestVersionName == null || latestVersionCode == null) {
            _uiState.update {
                it.copy(error = "Сначала проверьте обновления")
            }
            return
        }

        viewModelScope.launch {
            runCatching { appUpdateRepository.getLatestRelease() }
                .onSuccess { release ->
                    apkUpdateManager.startOrInstall(release)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            error = error.message ?: "Не удалось получить релиз для установки",
                        )
                    }
                }
        }
    }

    fun refreshDevices() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingDevices = true,
                    error = null,
                )
            }

            runCatching {
                deviceRepository.listDevices() to deviceRepository.listAuthorizationRequests()
            }.onSuccess { (devices, requests) ->
                _uiState.update {
                    it.copy(
                        isLoadingDevices = false,
                        devices = devices,
                        pendingDeviceRequests = requests,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingDevices = false,
                        error = error.message ?: "Не удалось загрузить устройства",
                    )
                }
            }
        }
    }

    fun approveDeviceAuthorizationRequest(requestId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isResolvingDeviceRequest = true,
                    error = null,
                    info = null,
                )
            }

            runCatching {
                deviceRepository.approveAuthorizationRequest(requestId)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isResolvingDeviceRequest = false,
                        info = "Новое устройство подтверждено. На нём можно повторить вход.",
                    )
                }
                refreshDevices()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isResolvingDeviceRequest = false,
                        error = error.message ?: "Не удалось подтвердить устройство",
                    )
                }
            }
        }
    }

    fun denyDeviceAuthorizationRequest(requestId: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isResolvingDeviceRequest = true,
                    error = null,
                    info = null,
                )
            }

            runCatching {
                deviceRepository.denyAuthorizationRequest(requestId)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isResolvingDeviceRequest = false,
                        info = "Запрос на новое устройство отклонён.",
                    )
                }
                refreshDevices()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isResolvingDeviceRequest = false,
                        error = error.message ?: "Не удалось отклонить устройство",
                    )
                }
            }
        }
    }

    fun revokeListedDevice(deviceId: Int) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRevokingListedDevice = true,
                    error = null,
                    info = null,
                )
            }

            runCatching {
                deviceRepository.revokeDevice(deviceId)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isRevokingListedDevice = false,
                        info = "Устройство отозвано.",
                    )
                }
                refreshDevices()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isRevokingListedDevice = false,
                        error = error.message ?: "Не удалось отозвать устройство",
                    )
                }
            }
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

            val lastSeenAt = sessionRepository.heartbeat()

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

    fun beginGoogle2faSetup() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isStartingGoogle2fa = true,
                    error = null,
                    info = null,
                )
            }

            runCatching { userProfileRepository.beginGoogle2faSetup() }
                .onSuccess { setup ->
                    _uiState.update {
                        it.copy(
                            isStartingGoogle2fa = false,
                            pendingGoogle2faSecret = setup.secret,
                            pendingGoogle2faProvisioningUri = setup.provisioningUri,
                            pendingGoogle2faIssuer = setup.issuer,
                            pendingGoogle2faAccountName = setup.accountName,
                            info = "Секрет для Google 2FA создан. Добавьте его в Google Authenticator и подтвердите кодом.",
                        )
                    }
                }
                .onFailure { error ->
                    val backendError = error as? BackendApiException
                    _uiState.update {
                        it.copy(
                            isStartingGoogle2fa = false,
                            error = if (backendError?.code == "GOOGLE_2FA_ALREADY_ENABLED") {
                                "Google 2FA уже включена для этого аккаунта."
                            } else {
                                error.message ?: "Не удалось начать настройку Google 2FA"
                            },
                        )
                    }
                }
        }
    }

    fun confirmGoogle2fa(code: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isConfirmingGoogle2fa = true,
                    error = null,
                    info = null,
                )
            }

            runCatching { userProfileRepository.confirmGoogle2faSetup(code) }
                .onSuccess { status ->
                    _uiState.update {
                        it.copy(
                            isConfirmingGoogle2fa = false,
                            google2faEnabled = status.enabled,
                            google2faConfirmedAt = status.confirmedAt,
                            pendingGoogle2faSecret = null,
                            pendingGoogle2faProvisioningUri = null,
                            pendingGoogle2faIssuer = null,
                            pendingGoogle2faAccountName = null,
                            info = "Google 2FA успешно включена.",
                        )
                    }
                }
                .onFailure { error ->
                    val backendError = error as? BackendApiException
                    _uiState.update {
                        it.copy(
                            isConfirmingGoogle2fa = false,
                            error = if (backendError?.code == "INVALID_TOTP_CODE") {
                                "Код не совпал. Попробуйте позже и введите актуальный код из приложения."
                            } else {
                                error.message ?: "Не удалось подтвердить Google 2FA"
                            },
                        )
                    }
                }
        }
    }

    fun disableGoogle2fa() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDisablingGoogle2fa = true,
                    error = null,
                    info = null,
                )
            }

            runCatching { userProfileRepository.disableGoogle2fa() }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isDisablingGoogle2fa = false,
                            google2faEnabled = false,
                            google2faConfirmedAt = null,
                            pendingGoogle2faSecret = null,
                            pendingGoogle2faProvisioningUri = null,
                            pendingGoogle2faIssuer = null,
                            pendingGoogle2faAccountName = null,
                            info = "Google 2FA отключена.",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isDisablingGoogle2fa = false,
                            error = error.message ?: "Не удалось отключить Google 2FA",
                        )
                    }
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

            sessionRepository.logoutSession()

            _uiState.update {
                it.copy(isLoggingOut = false)
            }

            onDone()
        }
    }

    fun logoutAllSessions(onDone: () -> Unit) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoggingOutAll = true,
                    error = null,
                    info = null,
                )
            }

            sessionRepository.logoutAllSessions()

            _uiState.update {
                it.copy(isLoggingOutAll = false)
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

            sessionRepository.revokeCurrentDevice()

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

    private fun resolveCurrentVersionCode(): Int {
        return runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .longVersionCode.toInt()
        }.getOrDefault(1)
    }

    private fun formatHeartbeatTime(raw: String): String {
        return runCatching {
            OffsetDateTime.parse(raw)
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        }.getOrDefault(raw)
    }

    private fun formatEpochSeconds(epochSeconds: Long): String {
        return runCatching {
            Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        }.getOrDefault(epochSeconds.toString())
    }
}
