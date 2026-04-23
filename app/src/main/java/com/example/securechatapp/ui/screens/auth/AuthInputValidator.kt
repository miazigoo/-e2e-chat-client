package com.example.securechatapp.ui.screens.auth

private val nicknameRegex = Regex("^@?[A-Za-z0-9_]{4,32}$")
private val emailRegex = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
private val codeRegex = Regex("^\\d{6}$")

object AuthInputValidator {

    fun normalizeNickname(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return trimmed
        return if (trimmed.startsWith("@")) trimmed else "@$trimmed"
    }

    fun nicknameError(value: String): String? {
        val normalized = normalizeNickname(value)
        if (normalized.isBlank()) return "Введите никнейм"
        if (!nicknameRegex.matches(normalized)) {
            return "Никнейм должен быть 4–32 символа и может содержать буквы, цифры и _"
        }
        return null
    }

    fun passwordError(value: String): String? {
        if (value.isBlank()) return "Введите пароль"
        if (value.length < 8) return "Пароль должен быть не короче 8 символов"
        return null
    }

    fun emailError(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        if (!emailRegex.matches(trimmed)) return "Введите корректный email"
        return null
    }

    fun codeError(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return "Введите код"
        if (!codeRegex.matches(trimmed)) return "Код должен содержать 6 цифр"
        return null
    }
}
