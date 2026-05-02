package com.example.securechatapp.data.remote.websocket

import com.example.securechatapp.BuildConfig
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
import com.example.securechatapp.data.repository.SessionRepository
import com.example.securechatapp.domain.model.AppReleaseInfo
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

sealed interface RealtimeEvent {
    data object Connected : RealtimeEvent
    data object Disconnected : RealtimeEvent
    data class Subscribed(val conversationId: Int) : RealtimeEvent
    data class Unsubscribed(val conversationId: Int) : RealtimeEvent
    data class ConversationEvent(
        val conversationId: Int,
        val eventType: String,
        val targetMessageId: Int? = null,
    ) : RealtimeEvent
    data class DeviceApprovalRequested(
        val requestId: String,
        val deviceUuid: String? = null,
        val deviceName: String? = null,
        val platform: String? = null,
        val appVersion: String? = null,
    ) : RealtimeEvent
    data class AppUpdateAvailable(
        val release: AppReleaseInfo,
    ) : RealtimeEvent
    data class Error(
        val statusCode: Int? = null,
        val code: String? = null,
        val message: String,
    ) : RealtimeEvent
}

@Singleton
class RealtimeWebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val sessionLocalDataSource: SecureSessionLocalDataSource,
    private val sessionRepository: SessionRepository,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()
    private val recoveryMutex = Mutex()

    private var webSocket: WebSocket? = null
    private var isConnected: Boolean = false
    private var keepAliveJob: Job? = null
    private var shouldProbeHttpSession: Boolean = false

    private val subscribedConversationIds = linkedSetOf<Int>()

    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    suspend fun connectIfNeeded(): Boolean {
        stateMutex.withLock {
            if (webSocket != null) return true
        }

        val shouldRepeatProbe = consumeHttpSessionProbe()
        runCatching {
            sessionRepository.heartbeat()
        }
        if (shouldRepeatProbe) {
            runCatching {
                sessionRepository.heartbeat()
            }
        }

        val session = sessionLocalDataSource.getSessionSnapshot()
        val accessToken = session.accessToken?.takeIf { it.isNotBlank() } ?: return false
        val deviceUuid = session.deviceUuid?.takeIf { it.isNotBlank() } ?: return false

        val request = Request.Builder()
            .url(buildWsUrl())
            .header("Authorization", "Bearer $accessToken")
            .header("X-Device-UUID", deviceUuid)
            .build()

        stateMutex.withLock {
            if (webSocket != null) return true
        }

        val createdSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    scope.launch {
                        handleSocketOpened(webSocket)
                        _events.emit(RealtimeEvent.Connected)
                        resubscribeAll()
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    scope.launch {
                        handleIncomingMessage(text)
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    scope.launch {
                        handleSocketClosed(webSocket)
                        _events.emit(RealtimeEvent.Disconnected)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    scope.launch {
                        handleSocketClosed(webSocket)
                        _events.emit(RealtimeEvent.Disconnected)
                    }
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: okhttp3.Response?,
                ) {
                    scope.launch {
                        val statusCode = response?.code
                        if (statusCode == 401 || statusCode == 403) {
                            markShouldProbeHttpSession()
                        }
                        val recovered = if (statusCode == 403) {
                            attemptDeviceRecovery()
                        } else {
                            false
                        }
                        handleSocketClosed(webSocket)
                        if (recovered) {
                            _events.emit(RealtimeEvent.Disconnected)
                            connectIfNeeded()
                            return@launch
                        }
                        _events.emit(
                            RealtimeEvent.Error(
                                statusCode = statusCode,
                                message = buildFailureMessage(
                                    throwable = t,
                                    statusCode = statusCode,
                                ),
                            )
                        )
                        _events.emit(RealtimeEvent.Disconnected)
                    }
                }
            }
        )

        stateMutex.withLock {
            if (webSocket == null) {
                webSocket = createdSocket
                return true
            }
        }

        createdSocket.close(1000, "duplicate_socket")
        return true
    }

    fun disconnect() {
        scope.launch {
            val socketToClose = stateMutex.withLock {
                stopKeepAliveLocked()
                val currentSocket = webSocket
                webSocket = null
                isConnected = false
                currentSocket
            }
            socketToClose?.close(1000, "client_disconnect")
        }
    }

    suspend fun subscribeConversation(conversationId: Int) {
        stateMutex.withLock {
            subscribedConversationIds.add(conversationId)
        }
        if (!connectIfNeeded()) return

        val socketToUse = stateMutex.withLock {
            if (!isConnected) return
            webSocket
        }

        socketToUse?.send(subscribePayload(conversationId))
    }

    fun unsubscribeConversation(conversationId: Int) {
        scope.launch {
            val socketToUse = stateMutex.withLock {
                subscribedConversationIds.remove(conversationId)
                if (!isConnected) return@withLock null
                webSocket
            }

            socketToUse?.send(unsubscribePayload(conversationId))
        }
    }

    private suspend fun sendPing() {
        val socketToUse = stateMutex.withLock {
            if (!isConnected) return
            webSocket
        }
        socketToUse?.send("""{"type":"ping"}""")
    }

    private suspend fun handleSocketOpened(openedSocket: WebSocket) {
        stateMutex.withLock {
            if (webSocket !== openedSocket) return
            isConnected = true
            shouldProbeHttpSession = false
            startKeepAliveLocked()
        }
    }

    private suspend fun handleSocketClosed(closedSocket: WebSocket) {
        stateMutex.withLock {
            if (webSocket !== closedSocket) return
            stopKeepAliveLocked()
            webSocket = null
            isConnected = false
        }
    }

    private fun startKeepAliveLocked() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive) {
                delay(25_000)
                sendPing()
            }
        }
    }

    private fun stopKeepAliveLocked() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    private suspend fun resubscribeAll() {
        val (socketToUse, conversationIds) = stateMutex.withLock {
            webSocket to subscribedConversationIds.toList()
        }
        conversationIds.forEach { conversationId ->
            socketToUse?.send(subscribePayload(conversationId))
        }
    }

    private suspend fun handleIncomingMessage(raw: String) {
        val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        val type = parsed["type"]?.jsonPrimitive?.contentOrNull ?: return

        when (type) {
            "connected" -> {
                _events.emit(RealtimeEvent.Connected)
            }

            "subscribed" -> {
                val conversationId = parsed["conversation_id"]?.jsonPrimitive?.intOrNull ?: return
                _events.emit(RealtimeEvent.Subscribed(conversationId))
            }

            "unsubscribed" -> {
                val conversationId = parsed["conversation_id"]?.jsonPrimitive?.intOrNull ?: return
                _events.emit(RealtimeEvent.Unsubscribed(conversationId))
            }

            "conversation.event" -> {
                val conversationId = parsed["conversation_id"]?.jsonPrimitive?.intOrNull ?: return
                val eventPayload = parsed["event"]?.jsonObject
                val eventType = eventPayload
                    ?.get("event_type")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: "unknown"
                val targetMessageId = eventPayload
                    ?.get("target_message_id")
                    ?.jsonPrimitive
                    ?.intOrNull
                    ?: eventPayload
                        ?.get("payload")
                        ?.jsonObject
                        ?.get("message_id")
                        ?.jsonPrimitive
                        ?.intOrNull

                _events.emit(
                    RealtimeEvent.ConversationEvent(
                        conversationId = conversationId,
                        eventType = eventType,
                        targetMessageId = targetMessageId,
                    )
                )
            }

            "device_approval_requested" -> {
                val requestId = parsed["request_id"]?.jsonPrimitive?.contentOrNull ?: return
                _events.emit(
                    RealtimeEvent.DeviceApprovalRequested(
                        requestId = requestId,
                        deviceUuid = parsed["device_uuid"]?.jsonPrimitive?.contentOrNull,
                        deviceName = parsed["device_name"]?.jsonPrimitive?.contentOrNull,
                        platform = parsed["platform"]?.jsonPrimitive?.contentOrNull,
                        appVersion = parsed["app_version"]?.jsonPrimitive?.contentOrNull,
                    )
                )
            }

            "error" -> {
                val code = parsed["code"]?.jsonPrimitive?.contentOrNull
                val message = parsed["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown websocket error"
                _events.emit(
                    RealtimeEvent.Error(
                        code = code,
                        message = message,
                    )
                )
            }

            "app_update_available" -> {
                val release = parsed["release"]?.jsonObject ?: return
                val versionCode = release["version_code"]?.jsonPrimitive?.intOrNull ?: return
                val fileSize = release["file_size"]?.jsonPrimitive?.longOrNull ?: 0L
                val uploadedAt = release["uploaded_at"]?.jsonPrimitive?.contentOrNull ?: return
                val versionName = release["version_name"]?.jsonPrimitive?.contentOrNull ?: return
                val fileName = release["file_name"]?.jsonPrimitive?.contentOrNull ?: return
                val platform = release["platform"]?.jsonPrimitive?.contentOrNull ?: "android"
                val sha256 = release["sha256"]?.jsonPrimitive?.contentOrNull ?: ""
                val changelog = release["changelog"]?.jsonPrimitive?.contentOrNull
                val forceUpdate = release["force_update"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.toBooleanStrictOrNull()
                    ?: false
                val minSupportedVersionCode = release["min_supported_version_code"]
                    ?.jsonPrimitive
                    ?.intOrNull
                val contentType = release["content_type"]?.jsonPrimitive?.contentOrNull
                    ?: "application/vnd.android.package-archive"

                _events.emit(
                    RealtimeEvent.AppUpdateAvailable(
                        release = AppReleaseInfo(
                            platform = platform,
                            versionName = versionName,
                            versionCode = versionCode,
                            fileName = fileName,
                            fileSize = fileSize,
                            sha256 = sha256,
                            changelog = changelog,
                            forceUpdate = forceUpdate,
                            minSupportedVersionCode = minSupportedVersionCode,
                            contentType = contentType,
                            uploadedAt = uploadedAt,
                            downloadUrl = "",
                            downloadUrlExpiresIn = 0,
                        )
                    )
                )
            }
        }
    }

    private fun buildWsUrl(): String {
        val httpBase = BuildConfig.API_BASE_URL.trimEnd('/')
        return when {
            httpBase.startsWith("https://") -> httpBase.replaceFirst("https://", "wss://") + "/ws"
            httpBase.startsWith("http://") -> httpBase.replaceFirst("http://", "ws://") + "/ws"
            else -> error("Unsupported API_BASE_URL: $httpBase")
        }
    }

    private suspend fun consumeHttpSessionProbe(): Boolean {
        return stateMutex.withLock {
            val current = shouldProbeHttpSession
            shouldProbeHttpSession = false
            current
        }
    }

    private suspend fun markShouldProbeHttpSession() {
        stateMutex.withLock {
            shouldProbeHttpSession = true
        }
    }

    private fun buildFailureMessage(
        throwable: Throwable,
        statusCode: Int?,
    ): String {
        return when {
            statusCode == 401 ->
                "Realtime недоступен: сессия истекла, перепроверяю авторизацию."
            statusCode == 403 ->
                "Realtime недоступен: сервер отклонил websocket-сессию. Перепроверяю привязку устройства."
            throwable is SocketTimeoutException ->
                "Realtime недоступен: сервер не завершил подключение вовремя."
            throwable is IOException && throwable.message.equals("Read timed out", ignoreCase = true) ->
                "Realtime недоступен: сервер не завершил подключение вовремя."
            else -> throwable.message?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "Realtime connection failed"
        }
    }

    private suspend fun attemptDeviceRecovery(): Boolean {
        return recoveryMutex.withLock {
            runCatching {
                sessionRepository.rebootstrapCurrentDevice()
            }.getOrDefault(false)
        }
    }

    private fun subscribePayload(conversationId: Int): String =
        """{"type":"subscribe_conversation","conversation_id":$conversationId}"""

    private fun unsubscribePayload(conversationId: Int): String =
        """{"type":"unsubscribe_conversation","conversation_id":$conversationId}"""
}

private val kotlinx.serialization.json.JsonPrimitive.intOrNull: Int?
    get() = content.toIntOrNull()

private val kotlinx.serialization.json.JsonPrimitive.longOrNull: Long?
    get() = content.toLongOrNull()

private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
    get() = runCatching { content }.getOrNull()
