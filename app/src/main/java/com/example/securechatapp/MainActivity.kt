package com.example.securechatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.securechatapp.ui.navigation.SecureChatNavHost
import com.example.securechatapp.ui.theme.SecureChatAppTheme
import com.example.securechatapp.ui.viewmodel.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val darkTheme by themeViewModel.darkThemeEnabled.collectAsStateWithLifecycle()
            val colorScheme by themeViewModel.colorScheme.collectAsStateWithLifecycle()

            SecureChatAppTheme(
                darkTheme = darkTheme,
                palette = colorScheme,
                dynamicColor = false,
            ) {
                SecureChatNavHost()
            }
        }
    }
}
