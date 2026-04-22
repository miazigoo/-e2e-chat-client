package com.example.securechatapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.securechatapp.ui.screens.auth.LoginScreen
import com.example.securechatapp.ui.screens.auth.RegisterScreen
import com.example.securechatapp.ui.screens.auth.SplashScreen
import com.example.securechatapp.ui.screens.auth.VerifyEmailCodeScreen
import com.example.securechatapp.ui.screens.chats.ChatsScreen
import com.example.securechatapp.ui.viewmodel.AuthViewModel

@Composable
fun SecureChatNavHost() {
    val navController = rememberNavController()
    val viewModel: AuthViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Routes.Splash,
    ) {
        composable(Routes.Splash) {
            SplashScreen(
                isAuthorized = state.isAuthorized,
                onNavigateNext = {
                    if (state.isAuthorized) {
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
                state = state,
                onLogin = viewModel::login,
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
                state = state,
                onRegister = viewModel::register,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "${Routes.VerifyEmailCode}/{challengeId}",
            arguments = listOf(navArgument("challengeId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val challengeId = backStackEntry.arguments?.getString("challengeId").orEmpty()
            VerifyEmailCodeScreen(
                state = state,
                challengeId = challengeId,
                onVerify = viewModel::verifyEmailCode,
                onSuccess = {
                    navController.navigate(Routes.Chats) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.Chats) {
            ChatsScreen(
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