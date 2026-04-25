package com.example.securechatapp.ui.screens.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.securechatapp.BuildConfig
import com.example.securechatapp.ui.components.TelegramAuthScaffold
import com.example.securechatapp.ui.components.TelegramStatusCard
import com.example.securechatapp.ui.viewmodel.AuthUiState

@Composable
fun LoginScreen(
    state: AuthUiState,
    onLogin: (String, String, String?, (String) -> Unit, () -> Unit) -> Unit,
    onOpenRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    onNeedVerifyCode: (String) -> Unit,
) {
    var nickname by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var totpCode by rememberSaveable { mutableStateOf("") }

    val nicknameError = remember(nickname) { AuthInputValidator.nicknameError(nickname) }
    val passwordError = remember(password) { AuthInputValidator.passwordError(password) }
    val totpError = remember(totpCode, state.requiresTotp) {
        when {
            !state.requiresTotp -> null
            totpCode.length < 6 -> "Введите 6 цифр из Google Authenticator"
            else -> null
        }
    }
    val canSubmit = nicknameError == null &&
            passwordError == null &&
            totpError == null &&
            !state.isLoading

    TelegramAuthScaffold(
        title = "Secure Chat",
        subtitle = "Войдите в защищённый чат",
    ) {
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("Никнейм") },
            placeholder = { Text("@username") },
            supportingText = {
                Text(nicknameError ?: "Используй свой никнейм аккаунта")
            },
            isError = nicknameError != null,
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            supportingText = {
                Text(passwordError ?: "Минимум 8 символов")
            },
            isError = passwordError != null,
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )

        if (state.requiresTotp) {
            OutlinedTextField(
                value = totpCode,
                onValueChange = { totpCode = it.filter(Char::isDigit).take(8) },
                label = { Text("Google 2FA код") },
                supportingText = {
                    Text(totpError ?: "Открой Google Authenticator и введи текущий код")
                },
                isError = totpError != null,
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
        }

        state.errorMessage?.let {
            TelegramStatusCard(text = it, isError = true)
        }

        state.infoMessage?.let {
            TelegramStatusCard(text = it)
        }

        state.emailMasked?.let {
            TelegramStatusCard(text = "Код отправлен на $it")
        }

        if (BuildConfig.SHOW_DEBUG_AUTH_INFO) {
            state.debugCode?.let {
                TelegramStatusCard(text = "DEBUG CODE: $it")
            }
        }

        Button(
            onClick = {
                onLogin(
                    AuthInputValidator.normalizeNickname(nickname),
                    password,
                    totpCode.takeIf { it.isNotBlank() },
                    onNeedVerifyCode,
                    onLoginSuccess,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            enabled = canSubmit,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                if (state.isLoading) {
                    "Входим..."
                } else if (state.requiresTotp) {
                    "Подтвердить 2FA"
                } else {
                    "Войти"
                }
            )
        }

        TextButton(
            onClick = onOpenRegister,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Создать аккаунт")
        }
    }
}
