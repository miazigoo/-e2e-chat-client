package com.example.securechatapp.ui.screens.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    var nicknameTouched by rememberSaveable { mutableStateOf(false) }
    var passwordTouched by rememberSaveable { mutableStateOf(false) }
    var totpTouched by rememberSaveable { mutableStateOf(false) }
    var lastAppliedSuggestedNickname by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.suggestedNickname) {
        val suggestedNickname = state.suggestedNickname?.trim().orEmpty()
        if (suggestedNickname.isBlank() || suggestedNickname == lastAppliedSuggestedNickname) return@LaunchedEffect

        nickname = suggestedNickname
        password = ""
        totpCode = ""
        nicknameTouched = false
        passwordTouched = false
        totpTouched = false
        lastAppliedSuggestedNickname = suggestedNickname
    }

    val nicknameError = remember(nickname) { AuthInputValidator.nicknameError(nickname) }
    val passwordError = remember(password) { AuthInputValidator.passwordError(password) }
    val totpError = remember(totpCode, state.requiresTotp) {
        when {
            !state.requiresTotp -> null
            totpCode.length < 6 -> "Введите 6 цифр из Google Authenticator"
            else -> null
        }
    }
    val showNicknameError = nicknameTouched && nicknameError != null
    val showPasswordError = passwordTouched && passwordError != null
    val showTotpError = totpTouched && totpError != null
    val canSubmit = nicknameError == null &&
            passwordError == null &&
            totpError == null &&
            !state.isLoading

    val bottomMessages = buildList<Pair<String, Boolean>> {
        state.errorMessage?.let { add(it to true) }
        state.emailMasked?.let { add("Код отправлен на $it" to false) }
        if (BuildConfig.SHOW_DEBUG_AUTH_INFO) {
            state.debugCode?.let { add("DEBUG CODE: $it" to false) }
        }
    }

    TelegramAuthScaffold(
        title = "Secure Chat",
        subtitle = "Войдите в защищённый чат",
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
        state.infoMessage?.let { infoMessage ->
            TelegramStatusCard(
                text = infoMessage,
                bottomSheetStyle = false,
            )
        }

        OutlinedTextField(
            value = nickname,
            onValueChange = {
                nickname = it
                nicknameTouched = true
            },
            label = { Text("Никнейм") },
            placeholder = { Text("@username") },
            supportingText = {
                Text(if (showNicknameError) nicknameError.orEmpty() else "Используй свой никнейм аккаунта")
            },
            isError = showNicknameError,
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
            onValueChange = {
                password = it
                passwordTouched = true
            },
            label = { Text("Пароль") },
            supportingText = {
                Text(if (showPasswordError) passwordError.orEmpty() else "Минимум 8 символов")
            },
            isError = showPasswordError,
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
                onValueChange = {
                    totpCode = it.filter(Char::isDigit).take(8)
                    totpTouched = true
                },
                label = { Text("Google 2FA код") },
                supportingText = {
                    Text(
                        if (showTotpError) {
                            totpError.orEmpty()
                        } else {
                            "Открой Google Authenticator и введи текущий код"
                        }
                    )
                },
                isError = showTotpError,
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
            enabled = !state.isLoading,
        ) {
            Text("Создать аккаунт")
        }
    }
}
