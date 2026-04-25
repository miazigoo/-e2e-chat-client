package com.example.securechatapp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemePalette(
    val storageValue: String,
    val displayName: String,
) {
    TELEGRAM("telegram", "Telegram"),
    SUNSET("sunset", "Sunset"),
    FOREST("forest", "Forest"),
    ROSE("rose", "Rose");

    companion object {
        fun fromStorageValue(value: String?): ThemePalette {
            return entries.firstOrNull { it.storageValue == value } ?: TELEGRAM
        }
    }
}

@Immutable
data class SecureChatExtraColors(
    val wallpaper: Color,
    val outgoingBubble: Color,
    val incomingBubble: Color,
    val topBar: Color,
)

@Immutable
data class SecureChatPaletteBundle(
    val primary: Color,
    val primaryDark: Color,
    val primaryLight: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val outline: Color,
    val error: Color,
    val darkBackground: Color,
    val darkSurface: Color,
    val darkSurfaceAlt: Color,
    val darkTextPrimary: Color,
    val darkTextSecondary: Color,
    val darkOutline: Color,
    val darkWallpaper: Color,
    val outgoingBubble: Color,
    val incomingBubble: Color,
    val darkOutgoingBubble: Color,
    val darkIncomingBubble: Color,
    val topBarLight: Color,
    val topBarDark: Color,
)

val LocalSecureChatExtraColors = staticCompositionLocalOf {
    SecureChatExtraColors(
        wallpaper = Color(0xFFEFECE5),
        outgoingBubble = Color(0xFFE7F7CB),
        incomingBubble = Color(0xFFFFFFFF),
        topBar = Color(0xFFFFFFFF),
    )
}

internal fun themePaletteBundle(palette: ThemePalette): SecureChatPaletteBundle {
    return when (palette) {
        ThemePalette.TELEGRAM -> SecureChatPaletteBundle(
            primary = Color(0xFF229ED9),
            primaryDark = Color(0xFF1D8EC6),
            primaryLight = Color(0xFFE9F5FC),
            secondary = Color(0xFF1D8EC6),
            background = Color(0xFFF4F6F8),
            surface = Color(0xFFFFFFFF),
            surfaceAlt = Color(0xFFF0F3F5),
            textPrimary = Color(0xFF18222D),
            textSecondary = Color(0xFF708499),
            outline = Color(0xFFD8E1E8),
            error = Color(0xFFD9534F),
            darkBackground = Color(0xFF17212B),
            darkSurface = Color(0xFF1F2C36),
            darkSurfaceAlt = Color(0xFF243947),
            darkTextPrimary = Color(0xFFF5F5F5),
            darkTextSecondary = Color(0xFF9DB2C3),
            darkOutline = Color(0xFF31424F),
            darkWallpaper = Color(0xFF0F1923),
            outgoingBubble = Color(0xFFE7F7CB),
            incomingBubble = Color(0xFFFFFFFF),
            darkOutgoingBubble = Color(0xFF2B5278),
            darkIncomingBubble = Color(0xFF182533),
            topBarLight = Color(0xFFFFFFFF),
            topBarDark = Color(0xFF1F2C36),
        )

        ThemePalette.SUNSET -> SecureChatPaletteBundle(
            primary = Color(0xFFDA6B4D),
            primaryDark = Color(0xFFB95339),
            primaryLight = Color(0xFFFFE7DF),
            secondary = Color(0xFF8C4A3B),
            background = Color(0xFFFFF7F2),
            surface = Color(0xFFFFFCFA),
            surfaceAlt = Color(0xFFF9EBE4),
            textPrimary = Color(0xFF2F211E),
            textSecondary = Color(0xFF8D6B63),
            outline = Color(0xFFE4C8BC),
            error = Color(0xFFBF3F3F),
            darkBackground = Color(0xFF211716),
            darkSurface = Color(0xFF2C201E),
            darkSurfaceAlt = Color(0xFF3A2A28),
            darkTextPrimary = Color(0xFFFFF1EB),
            darkTextSecondary = Color(0xFFD8B7AC),
            darkOutline = Color(0xFF5B4541),
            darkWallpaper = Color(0xFF1F1514),
            outgoingBubble = Color(0xFFFFD9C8),
            incomingBubble = Color(0xFFFFFFFF),
            darkOutgoingBubble = Color(0xFF8F4A37),
            darkIncomingBubble = Color(0xFF322523),
            topBarLight = Color(0xFFFFF6F1),
            topBarDark = Color(0xFF2C201E),
        )

        ThemePalette.FOREST -> SecureChatPaletteBundle(
            primary = Color(0xFF2E8B57),
            primaryDark = Color(0xFF236A42),
            primaryLight = Color(0xFFE1F3E9),
            secondary = Color(0xFF476D57),
            background = Color(0xFFF4FAF6),
            surface = Color(0xFFFFFFFF),
            surfaceAlt = Color(0xFFEAF3ED),
            textPrimary = Color(0xFF19251D),
            textSecondary = Color(0xFF63786A),
            outline = Color(0xFFC9DDD0),
            error = Color(0xFFC14949),
            darkBackground = Color(0xFF142019),
            darkSurface = Color(0xFF1B2A21),
            darkSurfaceAlt = Color(0xFF24372C),
            darkTextPrimary = Color(0xFFEAF7EF),
            darkTextSecondary = Color(0xFFA8C0B0),
            darkOutline = Color(0xFF385142),
            darkWallpaper = Color(0xFF101A14),
            outgoingBubble = Color(0xFFD9F2D8),
            incomingBubble = Color(0xFFFFFFFF),
            darkOutgoingBubble = Color(0xFF285E46),
            darkIncomingBubble = Color(0xFF1E2D24),
            topBarLight = Color(0xFFF7FCF8),
            topBarDark = Color(0xFF1B2A21),
        )

        ThemePalette.ROSE -> SecureChatPaletteBundle(
            primary = Color(0xFFB44E7A),
            primaryDark = Color(0xFF923B62),
            primaryLight = Color(0xFFFFE4EE),
            secondary = Color(0xFF7E4A62),
            background = Color(0xFFFFF7FA),
            surface = Color(0xFFFFFCFD),
            surfaceAlt = Color(0xFFF8EAF0),
            textPrimary = Color(0xFF2E1D25),
            textSecondary = Color(0xFF8C6A79),
            outline = Color(0xFFE3C7D3),
            error = Color(0xFFC23F5A),
            darkBackground = Color(0xFF1E1519),
            darkSurface = Color(0xFF291D22),
            darkSurfaceAlt = Color(0xFF34252D),
            darkTextPrimary = Color(0xFFFFEEF4),
            darkTextSecondary = Color(0xFFD8B4C2),
            darkOutline = Color(0xFF56404A),
            darkWallpaper = Color(0xFF171114),
            outgoingBubble = Color(0xFFFFD8E7),
            incomingBubble = Color(0xFFFFFFFF),
            darkOutgoingBubble = Color(0xFF7C3B58),
            darkIncomingBubble = Color(0xFF312228),
            topBarLight = Color(0xFFFFF8FB),
            topBarDark = Color(0xFF291D22),
        )
    }
}
