package com.example.securechatapp.data.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.securechatapp.BuildConfig
import com.example.securechatapp.app.di.StorageHttpClient
import com.example.securechatapp.data.repository.AppUpdateRepository
import com.example.securechatapp.domain.model.AppReleaseInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class ApkUpdatePhase {
    IDLE,
    PERMISSION_REQUIRED,
    DOWNLOADING,
    DOWNLOADED,
    INSTALLING,
    FAILED,
}

data class ApkUpdateInstallState(
    val phase: ApkUpdatePhase = ApkUpdatePhase.IDLE,
    val versionName: String? = null,
    val versionCode: Int? = null,
    val progressPercent: Int? = null,
    val message: String? = null,
)

@Singleton
class ApkUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appUpdateRepository: AppUpdateRepository,
    @StorageHttpClient private val storageHttpClient: OkHttpClient,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy {
        context.getSharedPreferences("apk_update_manager", Context.MODE_PRIVATE)
    }

    private val _state = MutableStateFlow(ApkUpdateInstallState())
    val state: StateFlow<ApkUpdateInstallState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    init {
        syncState()
    }

    fun syncState() {
        val record = getRecord()
        if (record == null) {
            _state.value = ApkUpdateInstallState()
            return
        }

        if (BuildConfig.VERSION_CODE >= record.versionCode) {
            clearRecord(removeFile = true)
            _state.value = ApkUpdateInstallState()
            return
        }

        val apkFile = File(record.filePath)
        _state.value = when {
            apkFile.exists() && record.phase == ApkUpdatePhase.DOWNLOADED.name ->
                ApkUpdateInstallState(
                    phase = ApkUpdatePhase.DOWNLOADED,
                    versionName = record.versionName,
                    versionCode = record.versionCode,
                    progressPercent = 100,
                )

            apkFile.exists() && record.phase == ApkUpdatePhase.INSTALLING.name ->
                ApkUpdateInstallState(
                    phase = ApkUpdatePhase.DOWNLOADED,
                    versionName = record.versionName,
                    versionCode = record.versionCode,
                    progressPercent = 100,
                    message = "APK уже скачан. Нажмите, чтобы установить",
                )

            record.phase == ApkUpdatePhase.DOWNLOADING.name ->
                ApkUpdateInstallState(
                    phase = ApkUpdatePhase.FAILED,
                    versionName = record.versionName,
                    versionCode = record.versionCode,
                    message = "Предыдущее скачивание было прервано. Нажмите обновить ещё раз",
                )

            else ->
                ApkUpdateInstallState(
                    phase = ApkUpdatePhase.FAILED,
                    versionName = record.versionName,
                    versionCode = record.versionCode,
                    message = record.message ?: "Не удалось скачать обновление",
                )
        }
    }

    fun startOrInstall(release: AppReleaseInfo) {
        if (release.versionCode <= BuildConfig.VERSION_CODE) {
            clearRecord(removeFile = true)
            _state.value = ApkUpdateInstallState(
                phase = ApkUpdatePhase.IDLE,
                versionName = release.versionName,
                versionCode = release.versionCode,
                message = "Уже установлена актуальная версия",
            )
            return
        }

        val record = getRecord()
        if (record != null && record.versionCode == release.versionCode) {
            when (_state.value.phase) {
                ApkUpdatePhase.DOWNLOADING,
                ApkUpdatePhase.INSTALLING -> return
                ApkUpdatePhase.DOWNLOADED -> {
                    installDownloadedUpdate()
                    return
                }

                else -> Unit
            }
        }

        downloadJob?.cancel()
        downloadJob = scope.launch {
            startDownload(release)
        }
    }

    fun installDownloadedUpdate() {
        val record = getRecord() ?: run {
            _state.value = ApkUpdateInstallState(
                phase = ApkUpdatePhase.FAILED,
                message = "Скачанный APK не найден",
            )
            return
        }

        if (BuildConfig.VERSION_CODE >= record.versionCode) {
            clearRecord(removeFile = true)
            _state.value = ApkUpdateInstallState()
            return
        }

        val apkFile = File(record.filePath)
        if (!apkFile.exists()) {
            clearRecord(removeFile = false)
            _state.value = ApkUpdateInstallState(
                phase = ApkUpdatePhase.FAILED,
                versionName = record.versionName,
                versionCode = record.versionCode,
                message = "Скачанный APK не найден",
            )
            return
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )

        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = apkUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        }

        val resolved = intent.resolveActivity(context.packageManager) != null
        if (!resolved) {
            _state.value = ApkUpdateInstallState(
                phase = ApkUpdatePhase.FAILED,
                versionName = record.versionName,
                versionCode = record.versionCode,
                message = "Системный установщик APK недоступен",
            )
            return
        }

        runCatching {
            context.startActivity(intent)
        }.onSuccess {
            saveRecord(record.copy(phase = ApkUpdatePhase.INSTALLING.name, message = null))
            _state.value = ApkUpdateInstallState(
                phase = ApkUpdatePhase.INSTALLING,
                versionName = record.versionName,
                versionCode = record.versionCode,
                progressPercent = 100,
            )
        }.onFailure {
            if (!canInstallPackages()) {
                openInstallPermissionSettings()
                _state.value = ApkUpdateInstallState(
                    phase = ApkUpdatePhase.PERMISSION_REQUIRED,
                    versionName = record.versionName,
                    versionCode = record.versionCode,
                    message = "Разрешите установку APK для SecureChat и нажмите ещё раз",
                )
            } else {
                _state.value = ApkUpdateInstallState(
                    phase = ApkUpdatePhase.FAILED,
                    versionName = record.versionName,
                    versionCode = record.versionCode,
                    progressPercent = null,
                    message = "Не удалось открыть системный установщик APK",
                )
            }
        }
    }

    private suspend fun startDownload(initialRelease: AppReleaseInfo) {
        val release = resolveFreshRelease(initialRelease)
        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.cacheDir, "updates").apply { mkdirs() }
        val finalFile = File(targetDir, sanitizeFileName(release.fileName))
        val tempFile = File(finalFile.absolutePath + ".part")

        finalFile.parentFile?.mkdirs()
        if (finalFile.exists()) {
            finalFile.delete()
        }
        if (tempFile.exists()) {
            tempFile.delete()
        }

        saveRecord(
            UpdateRecord(
                versionName = release.versionName,
                versionCode = release.versionCode,
                fileName = release.fileName,
                filePath = finalFile.absolutePath,
                phase = ApkUpdatePhase.DOWNLOADING.name,
                message = null,
            ),
        )

        _state.value = ApkUpdateInstallState(
            phase = ApkUpdatePhase.DOWNLOADING,
            versionName = release.versionName,
            versionCode = release.versionCode,
            progressPercent = 0,
        )

        try {
            downloadToFile(
                release = release,
                tempFile = tempFile,
            )

            if (!tempFile.renameTo(finalFile)) {
                throw IOException("Не удалось подготовить APK к установке")
            }

            saveRecord(
                UpdateRecord(
                    versionName = release.versionName,
                    versionCode = release.versionCode,
                    fileName = release.fileName,
                    filePath = finalFile.absolutePath,
                    phase = ApkUpdatePhase.DOWNLOADED.name,
                    message = null,
                ),
            )
            _state.value = ApkUpdateInstallState(
                phase = ApkUpdatePhase.DOWNLOADED,
                versionName = release.versionName,
                versionCode = release.versionCode,
                progressPercent = 100,
            )
            withContext(Dispatchers.Main) {
                installDownloadedUpdate()
            }
        } catch (cancellation: CancellationException) {
            tempFile.delete()
            throw cancellation
        } catch (error: Exception) {
            tempFile.delete()
            saveRecord(
                UpdateRecord(
                    versionName = release.versionName,
                    versionCode = release.versionCode,
                    fileName = release.fileName,
                    filePath = finalFile.absolutePath,
                    phase = ApkUpdatePhase.FAILED.name,
                    message = userFacingErrorMessage(error),
                ),
            )
            _state.value = ApkUpdateInstallState(
                phase = ApkUpdatePhase.FAILED,
                versionName = release.versionName,
                versionCode = release.versionCode,
                message = userFacingErrorMessage(error),
            )
        }
    }

    private suspend fun resolveFreshRelease(initialRelease: AppReleaseInfo): AppReleaseInfo {
        return runCatching { appUpdateRepository.getLatestRelease() }
            .getOrNull()
            ?.takeIf { latest -> latest.versionCode >= initialRelease.versionCode }
            ?: initialRelease
    }

    private suspend fun downloadToFile(
        release: AppReleaseInfo,
        tempFile: File,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(release.downloadUrl)
            .get()
            .build()

        storageHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Сервер вернул ${response.code} при скачивании APK")
            }

            val body = response.body ?: throw IOException("Сервер не вернул APK")
            val totalBytes = body.contentLength()

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = 0L
                    var lastProgress = -1

                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break

                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val progress = if (totalBytes > 0L) {
                            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            null
                        }

                        if (progress != null && progress != lastProgress) {
                            lastProgress = progress
                            _state.value = _state.value.copy(
                                phase = ApkUpdatePhase.DOWNLOADING,
                                progressPercent = progress,
                                message = null,
                            )
                        }
                    }

                    output.flush()
                }
            }
        }
    }

    private fun canInstallPackages(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    private fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun clearRecord(removeFile: Boolean) {
        val currentRecord = getRecord()
        if (removeFile && currentRecord != null) {
            runCatching { File(currentRecord.filePath).delete() }
            runCatching { File(currentRecord.filePath + ".part").delete() }
        }

        prefs.edit()
            .remove(KEY_VERSION_NAME)
            .remove(KEY_VERSION_CODE)
            .remove(KEY_FILE_NAME)
            .remove(KEY_FILE_PATH)
            .remove(KEY_PHASE)
            .remove(KEY_MESSAGE)
            .apply()
    }

    private fun saveRecord(record: UpdateRecord) {
        prefs.edit()
            .putString(KEY_VERSION_NAME, record.versionName)
            .putInt(KEY_VERSION_CODE, record.versionCode)
            .putString(KEY_FILE_NAME, record.fileName)
            .putString(KEY_FILE_PATH, record.filePath)
            .putString(KEY_PHASE, record.phase)
            .putString(KEY_MESSAGE, record.message)
            .apply()
    }

    private fun getRecord(): UpdateRecord? {
        val versionCode = prefs.getInt(KEY_VERSION_CODE, -1)
        val versionName = prefs.getString(KEY_VERSION_NAME, null)
        val fileName = prefs.getString(KEY_FILE_NAME, null)
        val filePath = prefs.getString(KEY_FILE_PATH, null)
        val phase = prefs.getString(KEY_PHASE, null)

        if (versionCode <= 0 ||
            versionName.isNullOrBlank() ||
            fileName.isNullOrBlank() ||
            filePath.isNullOrBlank() ||
            phase.isNullOrBlank()
        ) {
            return null
        }

        return UpdateRecord(
            versionName = versionName,
            versionCode = versionCode,
            fileName = fileName,
            filePath = filePath,
            phase = phase,
            message = prefs.getString(KEY_MESSAGE, null),
        )
    }

    private fun sanitizeFileName(fileName: String): String {
        return fileName
            .trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "securechat-update.apk" }
    }

    private fun userFacingErrorMessage(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            raw.contains("403") -> "Ссылка на APK устарела. Нажмите обновить ещё раз"
            raw.contains("timeout", ignoreCase = true) -> "Сервер долго отвечает. Повторите обновление"
            else -> "Не удалось скачать обновление"
        }
    }

    private data class UpdateRecord(
        val versionName: String,
        val versionCode: Int,
        val fileName: String,
        val filePath: String,
        val phase: String,
        val message: String?,
    )

    private companion object {
        private const val KEY_VERSION_NAME = "update_version_name"
        private const val KEY_VERSION_CODE = "update_version_code"
        private const val KEY_FILE_NAME = "update_file_name"
        private const val KEY_FILE_PATH = "update_file_path"
        private const val KEY_PHASE = "update_phase"
        private const val KEY_MESSAGE = "update_message"
    }
}
