package com.example.securechatapp.ui.screens.auth

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.securechatapp.ui.components.TelegramAuthScaffold
import com.example.securechatapp.ui.components.TelegramStatusCard
import com.example.securechatapp.ui.viewmodel.AuthUiState

@Composable
fun RegisterScreen(
    state: AuthUiState,
    onRegister: (String, String, String?, Boolean, () -> Unit) -> Unit,
    onRegisterSuccess: (String) -> Unit,
    onBack: () -> Unit,
) {
    var nickname by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var email2fa by rememberSaveable { mutableStateOf(false) }

    val nicknameError = remember(nickname) { AuthInputValidator.nicknameError(nickname) }
    val passwordError = remember(password) { AuthInputValidator.passwordError(password) }
    val emailError = remember(email, email2fa) {
        AuthInputValidator.registrationEmailError(email, email2fa)
    }
    val canSubmit = nicknameError == null &&
        passwordError == null &&
        emailError == null &&
        !state.isLoading

    val bottomMessages = buildList<Pair<String, Boolean>> {
        state.errorMessage?.takeIf { it.isNotBlank() }?.let { add(it to true) }
        state.infoMessage?.takeIf { it.isNotBlank() }?.let { add(it to false) }
    }

    TelegramAuthScaffold(
        title = "Создать аккаунт",
        subtitle = "Новый пользователь Secure Chat",
        bottomOverlay = bottomMessages.takeIf { it.isNotEmpty() }?.let { messages ->
            {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    messages.forEach { (text, isError) ->
                        TelegramStatusCard(
                            text = text,
                            isError = isError,
                            bottomSheetStyle = true,
                        )
                    }
                }
            }
        },
    ) {
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("Никнейм") },
            placeholder = { Text("@username") },
            supportingText = {
                Text(nicknameError ?: "Никнейм будет виден собеседникам")
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

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            supportingText = {
                Text(
                    emailError ?: if (email2fa) {
                        "Email нужен для подтверждения входа кодом"
                    } else {
                        "Необязательно. Нужен для email 2FA"
                    }
                )
            },
            isError = emailError != null,
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(
                checked = email2fa,
                onCheckedChange = { email2fa = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Text(
                text = "Включить email 2FA",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Button(
            onClick = {
                val normalizedNickname = AuthInputValidator.normalizeNickname(nickname)
                onRegister(
                    normalizedNickname,
                    password,
                    email.trim().ifBlank { null },
                    email2fa,
                    { onRegisterSuccess(normalizedNickname) },
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSubmit,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(if (state.isLoading) "Создаём..." else "Создать аккаунт")
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
        ) {
            Text("У меня уже есть аккаунт")
        }
    }
}
