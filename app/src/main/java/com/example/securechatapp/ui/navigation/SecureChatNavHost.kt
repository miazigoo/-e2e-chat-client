package com.example.securechatapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.securechatapp.ui.screens.conversation.ConversationScreen
import com.example.securechatapp.ui.screens.settings.SettingsScreen
import com.example.securechatapp.ui.viewmodel.AuthViewModel
import com.example.securechatapp.ui.viewmodel.ChatsViewModel
import com.example.securechatapp.ui.viewmodel.ConversationViewModel
import com.example.securechatapp.ui.viewmodel.SettingsViewModel

@Composable
fun SecureChatNavHost() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsState()
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

    NavHost(
        navController = navController,
        startDestination = Routes.Splash,
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

        composable(Routes.Login) {
            LoginScreen(
                state = authState,
                onLogin = authViewModel::login,
                onOpenRegister = { navController.navigate(Routes.Register) },
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
                onRegisterSuccess = {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Register) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() },
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
                onLoggedOut = {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Chats) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
