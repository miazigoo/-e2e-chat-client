package com.example.securechatapp.ui.screens.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthInputValidatorTest {

    @Test
    fun `normalizeNickname trims and strips optional at sign`() {
        assertEquals("alice", AuthInputValidator.normalizeNickname("  alice  "))
        assertEquals("bob", AuthInputValidator.normalizeNickname("@bob"))
    }

    @Test
    fun `nicknameError rejects invalid nickname`() {
        assertEquals(
            "Никнейм должен быть 4–32 символа и может содержать буквы, цифры и _",
            AuthInputValidator.nicknameError("ab"),
        )
    }

    @Test
    fun `emailError allows blank email and validates malformed values`() {
        assertNull(AuthInputValidator.emailError("   "))
        assertEquals("Введите корректный email", AuthInputValidator.emailError("wrong-email"))
    }

    @Test
    fun `registrationEmailError requires email when email 2fa is enabled`() {
        assertEquals(
            "Укажите email, если включаете email 2FA",
            AuthInputValidator.registrationEmailError("   ", email2faEnabled = true),
        )
        assertNull(
            AuthInputValidator.registrationEmailError(
                "user@example.com",
                email2faEnabled = true,
            )
        )
    }

    @Test
    fun `codeError accepts six digits only`() {
        assertNull(AuthInputValidator.codeError("123456"))
        assertEquals("Код должен содержать 6 цифр", AuthInputValidator.codeError("12345"))
    }
}
