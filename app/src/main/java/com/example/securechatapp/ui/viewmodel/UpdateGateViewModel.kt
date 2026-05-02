package com.example.securechatapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechatapp.BuildConfig
import com.example.securechatapp.data.files.ApkUpdateInstallState
import com.example.securechatapp.data.files.ApkUpdateManager
import com.example.securechatapp.data.repository.AppUpdateRepository
import com.example.securechatapp.data.repository.AppUpdateStateRepository
import com.example.securechatapp.domain.model.AppReleaseInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UpdateGateUiState(
    val mandatoryRelease: AppReleaseInfo? = null,
    val installState: ApkUpdateInstallState = ApkUpdateInstallState(),
)

@HiltViewModel
class UpdateGateViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
    private val appUpdateStateRepository: AppUpdateStateRepository,
    private val apkUpdateManager: ApkUpdateManager,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateGateUiState())
    val state: StateFlow<UpdateGateUiState> = _state.asStateFlow()

    init {
        observeGateState()
        observeInstallerState()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                appUpdateRepository.checkForUpdate(BuildConfig.VERSION_CODE)
            }.onSuccess { result ->
                appUpdateStateRepository.publishVersionCheck(result)
            }
        }
    }

    fun startUpdate() {
        val release = _state.value.mandatoryRelease ?: return
        apkUpdateManager.startOrInstall(release)
    }

    private fun observeGateState() {
        viewModelScope.launch {
            appUpdateStateRepository.state.collect { gateState ->
                _state.value = _state.value.copy(
                    mandatoryRelease = gateState.mandatoryRelease,
                )
            }
        }
    }

    private fun observeInstallerState() {
        viewModelScope.launch {
            apkUpdateManager.state.collect { installState ->
                appUpdateStateRepository.clearIfInstalled()
                _state.value = _state.value.copy(
                    installState = installState,
                )
            }
        }
    }
}
