package com.example.securechatapp.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.securechatapp.ui.screens.auth.LoginScreen
import com.example.securechatapp.ui.screens.auth.RegisterScreen
import com.example.securechatapp.ui.screens.auth.SplashScreen
import com.example.securechatapp.ui.screens.auth.VerifyEmailCodeScreen
import com.example.securechatapp.ui.screens.chats.ChatsScreen
import com.example.securechatapp.ui.screens.conversation.ConversationMediaScreen
import com.example.securechatapp.ui.screens.conversation.ConversationScreen
import com.example.securechatapp.ui.screens.settings.SettingsScreen
import com.example.securechatapp.ui.viewmodel.AuthViewModel
import com.example.securechatapp.ui.viewmodel.ChatsViewModel
import com.example.securechatapp.ui.viewmodel.ConversationMediaViewModel
import com.example.securechatapp.ui.viewmodel.ConversationViewModel
import com.example.securechatapp.ui.viewmodel.SettingsViewModel
import com.example.securechatapp.ui.viewmodel.UpdateGateViewModel
import com.example.securechatapp.data.files.ApkUpdatePhase

private const val LOGIN_RESULT_REGISTERED_NICKNAME = "login_result_registered_nickname"

@Composable
fun SecureChatNavHost(
    openConversationId: Int? = null,
    openRoute: String? = null,
    onVisibleConversationChanged: (Int?) -> Unit = {},
    onConversationHandled: () -> Unit = {},
    onRouteHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val updateGateViewModel: UpdateGateViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsState()
    val updateGateState by updateGateViewModel.state.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    LaunchedEffect(authState.isAuthorized, currentRoute) {
        val protectedRoute = when {
            currentRoute == null -> false
            currentRoute == Routes.Chats -> true
            currentRoute == Routes.Settings -> true
            currentRoute.startsWith("conversation/") -> true
            else -> false
        }

        if (!authState.isAuthorized && protectedRoute) {
            navController.navigate(Routes.Login) {
                popUpTo(Routes.Splash) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(authState.isAuthorized, openRoute, openConversationId) {
        if (!authState.isAuthorized) return@LaunchedEffect

        when {
            openConversationId != null -> {
                navController.navigate(Routes.conversationRoute(openConversationId)) {
                    popUpTo(Routes.Chats)
                    launchSingleTop = true
                }
                onConversationHandled()
            }

            !openRoute.isNullOrBlank() -> {
                navController.navigate(openRoute) {
                    launchSingleTop = true
                }
                onRouteHandled()
            }
        }
    }

    LaunchedEffect(currentBackStackEntry) {
        val visibleConversationId = if (currentRoute == Routes.ConversationPattern) {
            currentBackStackEntry?.arguments?.getInt(Routes.ConversationArg)
        } else {
            null
        }
        onVisibleConversationChanged(visibleConversationId)
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Splash,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(320),
            ) + fadeIn(animationSpec = tween(220))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(260),
            ) + fadeOut(animationSpec = tween(180))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300),
            ) + fadeIn(animationSpec = tween(200))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(240),
            ) + fadeOut(animationSpec = tween(160))
        },
    ) {
        composable(Routes.Splash) {
            SplashScreen(
                isAuthorized = authState.isAuthorized,
                onNavigateNext = {
                    if (authState.isAuthorized) {
                        navController.navigate(Routes.Chats) {
                            popUpTo(Routes.Splash) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.Login) {
                            popUpTo(Routes.Splash) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.Login) { backStackEntry ->
            val registeredNickname by backStackEntry.savedStateHandle
                .getStateFlow<String?>(LOGIN_RESULT_REGISTERED_NICKNAME, null)
                .collectAsState()

            LaunchedEffect(registeredNickname) {
                val nickname = registeredNickname?.trim().orEmpty()
                if (nickname.isBlank()) return@LaunchedEffect

                authViewModel.showRegistrationCompleted(nickname)
                backStackEntry.savedStateHandle[LOGIN_RESULT_REGISTERED_NICKNAME] = null
            }

            LoginScreen(
                state = authState,
                onLogin = authViewModel::login,
                onOpenRegister = {
                    authViewModel.clearTransientState(keepSuggestedNickname = false)
                    navController.navigate(Routes.Register)
                },
                onLoginSuccess = {
                    navController.navigate(Routes.Chats) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                },
                onNeedVerifyCode = { challengeId ->
                    navController.navigate("${Routes.VerifyEmailCode}/$challengeId")
                }
            )
        }

        composable(Routes.Register) {
            RegisterScreen(
                state = authState,
                onRegister = authViewModel::register,
                onRegisterSuccess = { nickname ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(LOGIN_RESULT_REGISTERED_NICKNAME, nickname)
                    navController.popBackStack()
                },
                onBack = {
                    authViewModel.clearTransientState(keepSuggestedNickname = false)
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = "${Routes.VerifyEmailCode}/{challengeId}",
            arguments = listOf(navArgument("challengeId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val challengeId = backStackEntry.arguments?.getString("challengeId").orEmpty()
            VerifyEmailCodeScreen(
                state = authState,
                challengeId = challengeId,
                onVerify = authViewModel::verifyEmailCode,
                onSuccess = {
                    navController.navigate(Routes.Chats) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.Chats) {
            val chatsViewModel: ChatsViewModel = hiltViewModel()

            ChatsScreen(
                viewModel = chatsViewModel,
                onConversationClick = { conversationId ->
                    navController.navigate(Routes.conversationRoute(conversationId))
                },
                onLoggedOut = {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Chats) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenSettings = {
                    navController.navigate(Routes.Settings)
                },
            )
        }

        composable(Routes.Settings) {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Chats) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Routes.ConversationPattern,
            arguments = listOf(
                navArgument(Routes.ConversationArg) { type = NavType.IntType }
            ),
        ) {
            val conversationViewModel: ConversationViewModel = hiltViewModel()

            ConversationScreen(
                viewModel = conversationViewModel,
                onBack = { navController.popBackStack() },
                onOpenMedia = { conversationId ->
                    navController.navigate(Routes.conversationMediaRoute(conversationId))
                },
                onLoggedOut = {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Chats) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Routes.ConversationMediaPattern,
            arguments = listOf(
                navArgument(Routes.ConversationArg) { type = NavType.IntType }
            ),
        ) {
            val mediaViewModel: ConversationMediaViewModel = hiltViewModel()
            ConversationMediaScreen(
                viewModel = mediaViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }

    updateGateState.mandatoryRelease?.let { release ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Требуется обновление") },
            text = {
                Text(
                    buildString {
                        append("Эта версия SecureChat больше не поддерживается сервером.")
                        append("\n\n")
                        append("Нужно установить ${release.versionName} (${release.versionCode}).")
                        release.changelog?.takeIf { it.isNotBlank() }?.let {
                            append("\n\n")
                            append(it)
                        }
                        updateGateState.installState.message?.takeIf { it.isNotBlank() }?.let {
                            append("\n\n")
                            append(it)
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = updateGateViewModel::startUpdate,
                    enabled = updateGateState.installState.phase != ApkUpdatePhase.DOWNLOADING &&
                        updateGateState.installState.phase != ApkUpdatePhase.INSTALLING,
                ) {
                    Text(
                        when (updateGateState.installState.phase) {
                            ApkUpdatePhase.PERMISSION_REQUIRED -> "Разрешить"
                            ApkUpdatePhase.DOWNLOADING -> updateGateState.installState.progressPercent
                                ?.let { "Скачивание $it%" }
                                ?: "Скачивание"
                            ApkUpdatePhase.DOWNLOADED -> "Установить"
                            ApkUpdatePhase.INSTALLING -> "Установка"
                            else -> "Обновить"
                        }
                    )
                }
            },
            dismissButton = null,
        )
    }
}
