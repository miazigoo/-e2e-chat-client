package com.example.securechatapp.ui.screens.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.securechatapp.BuildConfig
import com.example.securechatapp.ui.components.TelegramAuthScaffold
import com.example.securechatapp.ui.components.TelegramStatusCard
import com.example.securechatapp.ui.viewmodel.AuthUiState

@Composable
fun VerifyEmailCodeScreen(
    state: AuthUiState,
    challengeId: String,
    onVerify: (String, String, () -> Unit) -> Unit,
    onSuccess: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var codeTouched by rememberSaveable { mutableStateOf(false) }

    val codeError = remember(code) { AuthInputValidator.codeError(code) }
    val showCodeError = codeTouched && codeError != null
    val canSubmit = codeError == null && !state.isLoading
    val subtitle = state.emailMasked?.let { "Код отправлен на $it" } ?: "Введите код из письма"
    val bottomMessages = buildList<Pair<String, Boolean>> {
        if (BuildConfig.SHOW_DEBUG_AUTH_INFO) {
            add("Challenge: $challengeId" to false)
            state.debugCode?.let { add("DEBUG CODE: $it" to false) }
        }
        state.infoMessage?.let { add(it to false) }
        state.errorMessage?.let { add(it to true) }
    }

    TelegramAuthScaffold(
        title = "Подтверждение входа",
        subtitle = subtitle,
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
            value = code,
            onValueChange = {
                code = it.filter(Char::isDigit).take(6)
                codeTouched = true
            },
            label = { Text("Код подтверждения") },
            supportingText = {
                Text(if (showCodeError) codeError.orEmpty() else "Шестизначный код")
            },
            isError = showCodeError,
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )

        Button(
            onClick = {
                onVerify(challengeId, code.trim(), onSuccess)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSubmit,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(if (state.isLoading) "Проверяем..." else "Подтвердить")
        }
    }
}
