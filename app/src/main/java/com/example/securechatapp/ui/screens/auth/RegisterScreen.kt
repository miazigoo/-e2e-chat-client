package com.example.securechatapp.ui.screens.auth

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.*
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
    onRegisterSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    var nickname by remember { mutableStateOf("@alice") }
    var password by remember { mutableStateOf("supersecret123") }
    var email by remember { mutableStateOf("") }
    var email2fa by remember { mutableStateOf(false) }

    TelegramAuthScaffold(
        title = "Создать аккаунт",
        subtitle = "Новый пользователь Secure Chat",
    ) {
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("Никнейм") },
            placeholder = { Text("@username") },
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

        state.errorMessage?.let {
            TelegramStatusCard(text = it, isError = true)
        }

        state.infoMessage?.let {
            TelegramStatusCard(text = it)
        }

        Button(
            onClick = {
                onRegister(
                    nickname.trim(),
                    password,
                    email.trim().ifBlank { null },
                    email2fa,
                    onRegisterSuccess,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
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
        ) {
            Text("У меня уже есть аккаунт")
        }
    }
}
