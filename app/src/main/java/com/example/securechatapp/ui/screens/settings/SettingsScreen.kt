package com.example.securechatapp.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.securechatapp.BuildConfig
import com.example.securechatapp.push.NotificationSoundCatalog
import com.example.securechatapp.ui.picker.SystemDocumentPickerActivity
import com.example.securechatapp.ui.picker.SystemDocumentPickerBus
import com.example.securechatapp.ui.theme.ThemePalette
import com.example.securechatapp.ui.theme.themePaletteBundle
import com.example.securechatapp.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val activity = context as? Activity

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showLogoutAllConfirm by remember { mutableStateOf(false) }
    var showRevokeConfirm by remember { mutableStateOf(false) }
    var showSoundPicker by remember { mutableStateOf(false) }
    var google2faCode by remember { mutableStateOf("") }
    var notificationsPermissionGranted by remember {
        mutableStateOf(hasNotificationPermission(context))
    }
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val pickedUri = result.data?.let { data ->
            @Suppress("DEPRECATION")
            data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        viewModel.setCustomSystemNotificationSound(pickedUri)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsPermissionGranted = granted
        if (!granted) {
            Toast.makeText(
                context,
                "Без разрешения Android уведомления в шторке не будут показываться",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        SystemDocumentPickerBus.results.collectLatest { result ->
            if (result.requestKey != SystemDocumentPickerActivity.REQUEST_AVATAR) {
                return@collectLatest
            }

            result.uris.firstOrNull()?.let(Uri::parse)?.let(viewModel::uploadAvatar)
        }
    }

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

            SectionCard(title = "Профиль") {
                ProfileHeader(
                    avatarUrl = state.avatarUrl,
                    nickname = state.nickname,
                    fullName = state.fullName,
                    publicId = state.publicId,
                    avatarUpdatedAt = state.avatarUpdatedAt,
                    isUploading = state.isUploadingAvatar,
                    onUpload = {
                        if (activity != null) {
                            activity.startActivity(
                                SystemDocumentPickerActivity.createIntent(
                                    activity = activity,
                                    mimeTypes = arrayOf("image/*"),
                                    allowMultiple = false,
                                    requestKey = SystemDocumentPickerActivity.REQUEST_AVATAR,
                                )
                            )
                        }
                    },
                    onDelete = viewModel::deleteAvatar,
                )

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = state.nickname,
                    onValueChange = viewModel::updateNickname,
                    label = { Text("Никнейм") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                    ),
                )

                OutlinedTextField(
                    value = state.fullName,
                    onValueChange = viewModel::updateFullName,
                    label = { Text("Отображаемое имя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = state.bio,
                    onValueChange = viewModel::updateBio,
                    label = { Text("О себе") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                )

                OutlinedTextField(
                    value = state.languageCode,
                    onValueChange = viewModel::updateLanguageCode,
                    label = { Text("Язык профиля") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.None,
                    ),
                )

                Text(
                    text = "Тема профиля на сервере",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("system", "light", "dark").forEach { theme ->
                        FilterChip(
                            selected = state.profileTheme == theme,
                            onClick = { viewModel.updateProfileTheme(theme) },
                            label = { Text(theme) },
                        )
                    }
                }

                ToggleRow(
                    title = "Push-уведомления",
                    subtitle = "Показывать уведомления на устройстве и синхронизировать настройку с сервером",
                    checked = state.pushNotificationsEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.setPushNotificationsEnabled(enabled)
                        if (enabled && !notificationsPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )

                if (state.pushNotificationsEnabled && !notificationsPermissionGranted) {
                    TextButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Запросить разрешение на уведомления")
                    }
                }

                HorizontalDivider()

                SettingRowWithAction(
                    label = "Звук сообщений",
                    value = state.notificationSoundLabel,
                    actionText = "Выбрать",
                    onActionClick = { showSoundPicker = true },
                )

                TextButton(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TYPE,
                                RingtoneManager.TYPE_NOTIFICATION,
                            )
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                            )
                        }
                        ringtonePickerLauncher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Выбрать системный звук устройства")
                }

                ToggleRow(
                    title = "Лёгкая вибрация",
                    subtitle = "Короткая вибрация вместе со звуком уведомления о сообщении",
                    checked = state.notificationVibrationEnabled,
                    onCheckedChange = viewModel::setNotificationVibrationEnabled,
                )

                ToggleRow(
                    title = "Уведомления об обновлениях",
                    subtitle = "Показывать уведомления о новых APK и свежих production-релизах",
                    checked = state.apkUpdateNotificationsEnabled,
                    onCheckedChange = viewModel::setApkUpdateNotificationsEnabled,
                )

                Button(
                    onClick = viewModel::saveProfile,
                    enabled = !state.isSavingProfile && !state.isLoadingProfile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        if (state.isSavingProfile) {
                            "Сохраняем профиль..."
                        } else {
                            "Сохранить профиль"
                        }
                    )
                }
            }

            SectionCard(title = "Локальная тема") {
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
                            text = "Этот переключатель меняет текущий UI клиента локально",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Switch(
                        checked = state.darkThemeEnabled,
                        onCheckedChange = viewModel::setDarkThemeEnabled,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Цветовая схема",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Выберите визуальный стиль приложения под свой вкус",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemePalette.entries.forEach { palette ->
                        PalettePreviewCard(
                            palette = palette,
                            selected = state.colorScheme == palette,
                            onClick = { viewModel.setColorScheme(palette) },
                        )
                    }
                }
            }

            SectionCard(title = "Обновления приложения") {
                SettingRow("Текущая версия", "${state.currentVersionName} (${state.currentVersionCode})")
                HorizontalDivider()
                SettingRow("Статус", state.updateStatus)

                state.latestVersionName?.let { versionName ->
                    HorizontalDivider()
                    SettingRow(
                        "Последний релиз",
                        "$versionName (${state.latestVersionCode ?: "—"})",
                    )
                }

                state.latestVersionChangelog?.takeIf { it.isNotBlank() }?.let { changelog ->
                    HorizontalDivider()
                    SettingRow("Changelog", changelog)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = viewModel::checkForAppUpdates,
                    enabled = !state.isCheckingForUpdates,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        if (state.isCheckingForUpdates) {
                            "Проверяем..."
                        } else {
                            "Проверить обновления"
                        }
                    )
                }

                state.updateDownloadUrl?.let { url ->
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse(url)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Открыть ссылку на APK")
                    }
                }
            }

            SectionCard(title = "Профиль и сессия") {
                SettingRow("User ID", state.userId)
                HorizontalDivider()
                SettingRowWithAction(
                    label = "Public ID",
                    value = state.publicId,
                    actionText = "Скопировать",
                    onActionClick = {
                        clipboardManager.setText(AnnotatedString(state.publicId))
                        Toast.makeText(context, "Public ID скопирован", Toast.LENGTH_SHORT).show()
                    },
                    mono = true,
                )
                HorizontalDivider()
                SettingRow("Статус сессии", state.sessionStatus)
                HorizontalDivider()
                SettingRow("Access token истекает", state.accessTokenExpiresAt)
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

            SectionCard(title = "Устройства") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Активные устройства и запросы на вход",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = viewModel::refreshDevices,
                        enabled = !state.isLoadingDevices,
                    ) {
                        Text(if (state.isLoadingDevices) "Обновляем..." else "Обновить")
                    }
                }

                InfoCard(
                    title = "FCM регистрация",
                    value = buildString {
                        append(state.pushRegistrationStatus)
                        state.pushRegistrationTokenPreview?.let {
                            append("\nТокен: ")
                            append(it)
                            append("…")
                        }
                        state.pushRegistrationError?.takeIf { it.isNotBlank() }?.let {
                            append("\nОшибка: ")
                            append(it)
                        }
                    },
                    isError = state.pushRegistrationError != null,
                )

                TextButton(
                    onClick = viewModel::retryPushRegistration,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Повторить регистрацию FCM")
                }

                if (state.pendingDeviceRequests.isEmpty()) {
                    InfoCard(
                        title = "Запросы на авторизацию",
                        value = "Новых запросов на вход нет",
                    )
                } else {
                    state.pendingDeviceRequests.forEach { request ->
                        DeviceAuthorizationRequestCard(
                            request = request,
                            busy = state.isResolvingDeviceRequest,
                            onApprove = { viewModel.approveDeviceAuthorizationRequest(request.requestId) },
                            onDeny = { viewModel.denyDeviceAuthorizationRequest(request.requestId) },
                        )
                    }
                }

                if (state.devices.isEmpty() && !state.isLoadingDevices) {
                    InfoCard(
                        title = "Активные устройства",
                        value = "Список устройств пока пуст",
                    )
                } else {
                    state.devices.forEach { device ->
                        DeviceListItemCard(
                            device = device,
                            busy = state.isRevokingListedDevice,
                            onRevoke = {
                                if (!device.isCurrent) {
                                    viewModel.revokeListedDevice(device.deviceId)
                                }
                            },
                        )
                    }
                }
            }

            SectionCard(title = "Сеть и heartbeat") {
                SettingRow("Последний heartbeat", state.lastHeartbeatAt)

                Spacer(modifier = Modifier.height(4.dp))

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
                Text(
                    text = "Google 2FA",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (state.google2faEnabled) {
                        "Дополнительная TOTP-защита включена. При входе приложение запросит код из Google Authenticator."
                    } else {
                        "Опциональная защита входа через Google Authenticator. По умолчанию отключена."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingRow(
                    "Статус",
                    if (state.google2faEnabled) "Включена" else "Отключена",
                )

                state.google2faConfirmedAt?.let {
                    HorizontalDivider()
                    SettingRow("Подтверждена", it)
                }

                if (!state.google2faEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::beginGoogle2faSetup,
                        enabled = !state.isStartingGoogle2fa && !state.isConfirmingGoogle2fa,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            if (state.isStartingGoogle2fa) {
                                "Готовим секрет..."
                            } else {
                                "Включить Google 2FA"
                            }
                        )
                    }
                }

                state.pendingGoogle2faSecret?.let { secret ->
                    Spacer(modifier = Modifier.height(10.dp))
                    InfoCard(
                        title = "Секрет для Google Authenticator",
                        value = secret,
                    )
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(secret))
                            Toast.makeText(context, "Секрет скопирован", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Скопировать секрет")
                    }

                    state.pendingGoogle2faProvisioningUri?.let { provisioningUri ->
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(provisioningUri))
                                Toast.makeText(context, "URI для Google Authenticator скопирован", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Скопировать provisioning URI")
                        }
                    }

                    OutlinedTextField(
                        value = google2faCode,
                        onValueChange = { google2faCode = it.filter(Char::isDigit).take(8) },
                        label = { Text("Код из Google Authenticator") },
                        supportingText = {
                            Text("Введите текущий код, чтобы завершить настройку")
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                    )

                    Button(
                        onClick = { viewModel.confirmGoogle2fa(google2faCode) },
                        enabled = google2faCode.length >= 6 && !state.isConfirmingGoogle2fa,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            if (state.isConfirmingGoogle2fa) {
                                "Проверяем код..."
                            } else {
                                "Подтвердить Google 2FA"
                            }
                        )
                    }
                }

                if (state.google2faEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::disableGoogle2fa,
                        enabled = !state.isDisablingGoogle2fa,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            if (state.isDisablingGoogle2fa) {
                                "Отключаем..."
                            } else {
                                "Отключить Google 2FA"
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { showLogoutConfirm = true },
                    enabled = !state.isLoggingOut,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        if (state.isLoggingOut) "Выходим..." else "Выйти из текущей сессии"
                    )
                }

                Button(
                    onClick = { showLogoutAllConfirm = true },
                    enabled = !state.isLoggingOutAll,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        if (state.isLoggingOutAll) "Завершаем..." else "Выйти из всех сессий"
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
                SettingRow("Версия клиента", BuildConfig.VERSION_NAME)
                HorizontalDivider()
                SettingRow("API", BuildConfig.API_BASE_URL, mono = true)
                HorizontalDivider()
                SettingRow("Среда", if (BuildConfig.DEBUG) "debug" else "release")
            }
        }
    }

    if (showLogoutConfirm) {
        ConfirmDialog(
            title = "Выйти из текущей сессии?",
            text = "Текущая сессия будет завершена, но устройство останется зарегистрированным.",
            confirmLabel = "Выйти",
            onDismiss = { showLogoutConfirm = false },
            onConfirm = {
                showLogoutConfirm = false
                viewModel.logoutSession(onLoggedOut)
            },
        )
    }

    if (showLogoutAllConfirm) {
        ConfirmDialog(
            title = "Выйти из всех сессий?",
            text = "Все активные сессии аккаунта будут завершены. Для продолжения потребуется снова войти в приложение.",
            confirmLabel = "Выйти везде",
            onDismiss = { showLogoutAllConfirm = false },
            onConfirm = {
                showLogoutAllConfirm = false
                viewModel.logoutAllSessions(onLoggedOut)
            },
        )
    }

    if (showRevokeConfirm) {
        ConfirmDialog(
            title = "Отозвать текущее устройство?",
            text = "Устройство станет неактивным. Для повторного использования потребуется новая авторизация и повторная регистрация устройства.",
            confirmLabel = "Отозвать",
            destructive = true,
            onDismiss = { showRevokeConfirm = false },
            onConfirm = {
                showRevokeConfirm = false
                viewModel.revokeCurrentDevice(onLoggedOut)
            },
        )
    }

    if (showSoundPicker) {
        NotificationSoundPickerDialog(
            selectedKey = state.notificationSoundKey,
            onDismiss = { showSoundPicker = false },
            onSelect = { soundKey ->
                showSoundPicker = false
                when (soundKey) {
                    NotificationSoundCatalog.SYSTEM_DEFAULT_KEY -> viewModel.setDefaultNotificationSound()
                    else -> viewModel.setBundledNotificationSound(soundKey)
                }
            },
        )
    }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun DeviceAuthorizationRequestCard(
    request: com.example.securechatapp.domain.model.DeviceAuthorizationRequest,
    busy: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = request.deviceName?.ifBlank { "Неизвестное устройство" }
                    ?: "Неизвестное устройство",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = listOfNotNull(
                    request.platform?.takeIf { it.isNotBlank() },
                    request.appVersion?.takeIf { it.isNotBlank() },
                    request.ipAddress?.takeIf { it.isNotBlank() },
                ).joinToString(" • ").ifBlank { request.deviceUuid },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Запрошено: ${request.requestedAt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onApprove,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Подтвердить")
                }
                Button(
                    onClick = onDeny,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text("Отклонить")
                }
            }
        }
    }
}

@Composable
private fun DeviceListItemCard(
    device: com.example.securechatapp.domain.model.AuthorizedDevice,
    busy: Boolean,
    onRevoke: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (device.isCurrent) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = buildString {
                    append(device.deviceName.ifBlank { "Устройство" })
                    if (device.isCurrent) append(" • текущее")
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = listOf(
                    device.platform,
                    device.appVersion,
                    if (device.fcmTokenPresent) "FCM ok" else "FCM нет",
                ).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Последний seen: ${device.lastSeenAt ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = device.deviceUuid,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!device.isCurrent) {
                Button(
                    onClick = onRevoke,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text("Отозвать устройство")
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    avatarUrl: String?,
    nickname: String,
    fullName: String,
    publicId: String,
    avatarUpdatedAt: String,
    isUploading: Boolean,
    onUpload: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = nickname.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = fullName.ifBlank { nickname },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = publicId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Аватар обновлён: $avatarUpdatedAt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onUpload,
            enabled = !isUploading,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(if (isUploading) "Загружаем..." else "Загрузить аватар")
        }

        Button(
            onClick = onDelete,
            enabled = !isUploading && avatarUrl != null,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Удалить")
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun PalettePreviewCard(
    palette: ThemePalette,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bundle = themePaletteBundle(palette)
    Surface(
        onClick = onClick,
        modifier = Modifier.width(156.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = if (selected) 3.dp else 1.dp,
        shadowElevation = if (selected) 6.dp else 2.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bundle.background),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(bundle.primary),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .size(width = 56.dp, height = 18.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bundle.incomingBubble),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(width = 64.dp, height = 18.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bundle.outgoingBubble),
                )
            }

            Text(
                text = palette.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (selected) "Активна" else "Нажмите, чтобы применить",
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun NotificationSoundPickerDialog(
    selectedKey: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        title = {
            Text("Звук уведомлений")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NotificationSoundCatalog.settingsOptions.forEach { option ->
                    Surface(
                        onClick = { onSelect(option.key) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (selectedKey == option.key) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option.label,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (selectedKey == option.key) {
                                Text(
                                    text = "Выбрано",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                content()
            }
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String,
    isError: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
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
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (mono) {
                MaterialTheme.typography.bodySmall
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = MaterialTheme.colorScheme.onSurface,
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = if (mono) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = actionText,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onActionClick).padding(8.dp),
        )
    }
}
