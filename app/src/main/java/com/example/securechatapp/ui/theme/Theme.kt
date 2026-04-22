package com.example.securechatapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = TgBlue,
    onPrimary = TgSurface,
    primaryContainer = TgBlueLight,
    onPrimaryContainer = TgBlueDark,

    secondary = TgBlueDark,
    onSecondary = TgSurface,

    background = TgBackground,
    onBackground = TgTextPrimary,

    surface = TgSurface,
    onSurface = TgTextPrimary,
    surfaceVariant = TgSurfaceAlt,
    onSurfaceVariant = TgTextSecondary,

    outline = TgOutline,
    error = TgError,
)

private val DarkColorScheme = darkColorScheme(
    primary = TgBlue,
    onPrimary = TgSurface,
    primaryContainer = TgDarkSurfaceAlt,
    onPrimaryContainer = TgDarkTextPrimary,

    secondary = TgBlueDark,
    onSecondary = TgSurface,

    background = TgDarkBackground,
    onBackground = TgDarkTextPrimary,

    surface = TgDarkSurface,
    onSurface = TgDarkTextPrimary,
    surfaceVariant = TgDarkSurfaceAlt,
    onSurfaceVariant = TgDarkTextSecondary,

    outline = TgDarkOutline,
    error = TgError,
)

@Composable
fun SecureChatAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
