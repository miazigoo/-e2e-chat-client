package com.example.securechatapp.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.securechatapp.ui.viewmodel.AuthUiState

@Composable
fun LoginScreen(
    state: AuthUiState,
    onLogin: (String, String, (String) -> Unit, () -> Unit) -> Unit,
    onOpenRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    onNeedVerifyCode: (String) -> Unit,
) {
    var nickname by remember { mutableStateOf("@alice") }
    var password by remember { mutableStateOf("supersecret123") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Login", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("Nickname") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        state.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        state.infoMessage?.let {
            Text(text = it)
        }

        state.emailMasked?.let {
            Text(text = "Код отправлен на: $it")
        }

        state.debugCode?.let {
            Text(text = "DEBUG CODE: $it")
        }

        Button(
            onClick = {
                onLogin(
                    nickname,
                    password,
                    onNeedVerifyCode,
                    onLoginSuccess,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
            contentPadding = PaddingValues(14.dp),
        ) {
            Text(if (state.isLoading) "Loading..." else "Login")
        }

        Button(
            onClick = onOpenRegister,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open register")
        }
    }
}