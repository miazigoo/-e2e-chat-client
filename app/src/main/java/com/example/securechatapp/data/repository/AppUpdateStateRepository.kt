package com.example.securechatapp.data.repository

import com.example.securechatapp.BuildConfig
import com.example.securechatapp.domain.model.AppReleaseInfo
import com.example.securechatapp.domain.model.AppVersionCheck
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppUpdateGateState(
    val mandatoryRelease: AppReleaseInfo? = null,
)

@Singleton
class AppUpdateStateRepository @Inject constructor() {

    private val _state = MutableStateFlow(AppUpdateGateState())
    val state: StateFlow<AppUpdateGateState> = _state.asStateFlow()

    fun publishRelease(release: AppReleaseInfo) {
        _state.value = _state.value.copy(
            mandatoryRelease = release.takeIf {
                it.requiresImmediateUpdate(BuildConfig.VERSION_CODE)
            },
        )
    }

    fun publishVersionCheck(result: AppVersionCheck) {
        _state.value = _state.value.copy(
            mandatoryRelease = result.release.takeIf { result.updateRequired },
        )
    }

    fun clearIfInstalled() {
        val current = _state.value.mandatoryRelease ?: return
        if (BuildConfig.VERSION_CODE >= current.versionCode) {
            _state.value = AppUpdateGateState()
        }
    }
}

fun AppReleaseInfo.requiresImmediateUpdate(currentVersionCode: Int): Boolean {
    return when {
        minSupportedVersionCode != null -> currentVersionCode < minSupportedVersionCode
        forceUpdate -> currentVersionCode < versionCode
        else -> false
    }
}
