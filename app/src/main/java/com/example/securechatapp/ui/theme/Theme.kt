package com.example.securechatapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun lightColorScheme(bundle: SecureChatPaletteBundle): ColorScheme = lightColorScheme(
    primary = bundle.primary,
    onPrimary = bundle.surface,
    primaryContainer = bundle.primaryLight,
    onPrimaryContainer = bundle.primaryDark,

    secondary = bundle.secondary,
    onSecondary = bundle.surface,

    background = bundle.background,
    onBackground = bundle.textPrimary,

    surface = bundle.surface,
    onSurface = bundle.textPrimary,
    surfaceVariant = bundle.surfaceAlt,
    onSurfaceVariant = bundle.textSecondary,

    outline = bundle.outline,
    error = bundle.error,
)

private fun darkColorScheme(bundle: SecureChatPaletteBundle): ColorScheme = darkColorScheme(
    primary = bundle.primary,
    onPrimary = bundle.surface,
    primaryContainer = bundle.darkSurfaceAlt,
    onPrimaryContainer = bundle.darkTextPrimary,

    secondary = bundle.secondary,
    onSecondary = bundle.surface,

    background = bundle.darkBackground,
    onBackground = bundle.darkTextPrimary,

    surface = bundle.darkSurface,
    onSurface = bundle.darkTextPrimary,
    surfaceVariant = bundle.darkSurfaceAlt,
    onSurfaceVariant = bundle.darkTextSecondary,

    outline = bundle.darkOutline,
    error = bundle.error,
)

@Composable
fun SecureChatAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ThemePalette = ThemePalette.TELEGRAM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val bundle = themePaletteBundle(palette)
    val colorScheme = if (darkTheme) {
        darkColorScheme(bundle)
    } else {
        lightColorScheme(bundle)
    }
    val extraColors = if (darkTheme) {
        SecureChatExtraColors(
            wallpaper = bundle.darkWallpaper,
            outgoingBubble = bundle.darkOutgoingBubble,
            incomingBubble = bundle.darkIncomingBubble,
            topBar = bundle.topBarDark,
        )
    } else {
        SecureChatExtraColors(
            wallpaper = bundle.background,
            outgoingBubble = bundle.outgoingBubble,
            incomingBubble = bundle.incomingBubble,
            topBar = bundle.topBarLight,
        )
    }
    val useDynamicColor = dynamicColor
    if (useDynamicColor) {
        // Dynamic color intentionally disabled for a consistent cross-device identity.
    }

    CompositionLocalProvider(LocalSecureChatExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

object SecureChatTheme {
    val extras: SecureChatExtraColors
        @Composable
        get() = LocalSecureChatExtraColors.current
}
