package com.example.securechatapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.securechatapp.data.local.preferences.NotificationPreferenceDataSource
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.push.NotificationIntents
import com.example.securechatapp.push.VisibleConversationTracker
import com.example.securechatapp.ui.navigation.SecureChatNavHost
import com.example.securechatapp.ui.theme.SecureChatAppTheme
import com.example.securechatapp.ui.viewmodel.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var sessionLocalDataSource: SecureSessionLocalDataSource

    @Inject
    lateinit var notificationPreferenceDataSource: NotificationPreferenceDataSource

    @Inject
    lateinit var visibleConversationTracker: VisibleConversationTracker

    private var pendingOpenConversationId by mutableStateOf<Int?>(null)
    private var pendingOpenRoute by mutableStateOf<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        lifecycleScope.launch {
            notificationPreferenceDataSource.markNotificationPermissionPromptShown()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        maybeRequestNotificationPermission()
        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val darkTheme by themeViewModel.darkThemeEnabled.collectAsStateWithLifecycle()
            val colorScheme by themeViewModel.colorScheme.collectAsStateWithLifecycle()

            SecureChatAppTheme(
                darkTheme = darkTheme,
                palette = colorScheme,
                dynamicColor = false,
            ) {
                SecureChatNavHost(
                    openConversationId = pendingOpenConversationId,
                    openRoute = pendingOpenRoute,
                    onVisibleConversationChanged = visibleConversationTracker::setConversationId,
                    onConversationHandled = { pendingOpenConversationId = null },
                    onRouteHandled = { pendingOpenRoute = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
        maybeRequestNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        visibleConversationTracker.setAppVisible(true)
    }

    override fun onStop() {
        visibleConversationTracker.setAppVisible(false)
        super.onStop()
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return

        val conversationId = intent.getIntExtra(NotificationIntents.EXTRA_OPEN_CONVERSATION_ID, -1)
        if (conversationId > 0) {
            pendingOpenConversationId = conversationId
            pendingOpenRoute = null
        }

        intent.getStringExtra(NotificationIntents.EXTRA_OPEN_ROUTE)?.let { route ->
            if (pendingOpenConversationId == null) {
                pendingOpenRoute = route
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        lifecycleScope.launch {
            val authorized = !sessionLocalDataSource.getSessionSnapshot().accessToken.isNullOrBlank()
            val pushEnabled = notificationPreferenceDataSource.isPushNotificationsEnabled()
            val promptShown = notificationPreferenceDataSource.isNotificationPermissionPromptShown()

            if (authorized && pushEnabled && !promptShown) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
