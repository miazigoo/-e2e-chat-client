package com.example.securechatapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechatapp.data.local.preferences.ThemePreferenceDataSource
import com.example.securechatapp.ui.theme.ThemePalette
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ThemeViewModel @Inject constructor(
    themePreferenceDataSource: ThemePreferenceDataSource,
) : ViewModel() {

    val darkThemeEnabled: StateFlow<Boolean> =
        themePreferenceDataSource.darkThemeEnabledFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val colorScheme: StateFlow<ThemePalette> =
        themePreferenceDataSource.colorSchemeFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemePalette.TELEGRAM,
        )
}
