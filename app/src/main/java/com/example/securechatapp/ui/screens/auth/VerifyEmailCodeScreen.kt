package com.example.securechatapp.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.securechatapp.ui.viewmodel.AuthUiState

@Composable
fun VerifyEmailCodeScreen(
    state: AuthUiState,
    challengeId: String,
    onVerify: (String, String, () -> Unit) -> Unit,
    onSuccess: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Verify Email Code", style = MaterialTheme.typography.headlineMedium)
        Text("Challenge: $challengeId")

        state.debugCode?.let {
            Text("DEBUG CODE: $it")
        }

        state.infoMessage?.let {
            Text(it)
        }

        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("6-digit code") },
            modifier = Modifier.fillMaxWidth(),
        )

        state.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = {
                onVerify(challengeId, code, onSuccess)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
        ) {
            Text(if (state.isLoading) "Verifying..." else "Verify")
        }
    }
}