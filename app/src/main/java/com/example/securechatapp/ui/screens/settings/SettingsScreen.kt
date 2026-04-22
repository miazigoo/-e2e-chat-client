package com.example.securechatapp.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.securechatapp.BuildConfig
import com.example.securechatapp.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showRevokeConfirm by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text("← Назад")
                }

                Text(
                    text = "Настройки",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            state.error?.let {
                InfoCard(
                    title = "Ошибка",
                    value = it,
                    isError = true,
                )
            }

            state.info?.let {
                InfoCard(
                    title = "Статус",
                    value = it,
                )
            }

            SectionCard(title = "Профиль и сессия") {
                SettingRow("Никнейм", state.nickname)
                HorizontalDivider()
                SettingRow("User ID", state.userId)
                HorizontalDivider()
                SettingRow("Статус сессии", state.sessionStatus)
                HorizontalDivider()
                SettingRowWithAction(
                    label = "Device UUID",
                    value = state.deviceUuid,
                    actionText = "Скопировать",
                    onActionClick = {
                        clipboardManager.setText(AnnotatedString(state.deviceUuid))
                        Toast.makeText(context, "Device UUID скопирован", Toast.LENGTH_SHORT).show()
                    },
                    mono = true,
                )
            }

            SectionCard(title = "Тема") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Тёмная тема",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Сохраняется локально",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Switch(
                        checked = state.darkThemeEnabled,
                        onCheckedChange = viewModel::setDarkThemeEnabled,
                    )
                }
            }

            SectionCard(title = "Сеть и heartbeat") {
                SettingRow("Последний heartbeat", state.lastHeartbeatAt)

                Spacer(modifier = Modifier.padding(top = 4.dp))

                Button(
                    onClick = viewModel::sendHeartbeatNow,
                    enabled = !state.isSendingHeartbeat,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        if (state.isSendingHeartbeat) {
                            "Отправляем..."
                        } else {
                            "Отправить heartbeat"
                        }
                    )
                }
            }

            SectionCard(title = "Безопасность") {
                Button(
                    onClick = { showLogoutConfirm = true },
                    enabled = !state.isLoggingOut,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        if (state.isLoggingOut) {
                            "Выходим..."
                        } else {
                            "Выйти из сессии"
                        }
                    )
                }

                Button(
                    onClick = { showRevokeConfirm = true },
                    enabled = !state.isRevokingDevice,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        if (state.isRevokingDevice) {
                            "Отзываем устройство..."
                        } else {
                            "Отозвать текущее устройство"
                        }
                    )
                }
            }

            SectionCard(title = "О приложении") {
                SettingRow("Версия", BuildConfig.VERSION_NAME)
                HorizontalDivider()
                SettingRow("API", BuildConfig.API_BASE_URL, mono = true)
                HorizontalDivider()
                SettingRow(
                    "Среда",
                    if (BuildConfig.DEBUG) "debug" else "release",
                )
            }
        }
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Выйти из сессии?") },
            text = {
                Text("Текущая сессия будет завершена, но устройство останется зарегистрированным.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        viewModel.logoutSession(onLoggedOut)
                    }
                ) {
                    Text("Выйти")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutConfirm = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showRevokeConfirm) {
        AlertDialog(
            onDismissRequest = { showRevokeConfirm = false },
            title = { Text("Отозвать текущее устройство?") },
            text = {
                Text(
                    "Устройство станет неактивным. Для повторного использования потребуется новая авторизация и повторная регистрация устройства."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRevokeConfirm = false
                        viewModel.revokeCurrentDevice(onLoggedOut)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Отозвать")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRevokeConfirm = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    mono: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (mono) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingRowWithAction(
    label: String,
    value: String,
    actionText: String,
    onActionClick: () -> Unit,
    mono: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = value,
            style = if (mono) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        TextButton(
            onClick = onActionClick,
            modifier = Modifier.align(Alignment.Start),
        ) {
            Text(actionText)
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String,
    isError: Boolean = false,
) {
    val bg = if (isError) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    }

    val fg = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = bg,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = fg,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
            )
        }
    }
}
