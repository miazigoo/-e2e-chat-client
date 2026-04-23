package com.example.securechatapp.data.remote.websocket

import com.example.securechatapp.BuildConfig
import com.example.securechatapp.data.local.preferences.SecureSessionLocalDataSource
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
    ) : RealtimeEvent
    data class Error(
        val code: String? = null,
        val message: String,
    ) : RealtimeEvent
}

@Singleton
class RealtimeWebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val sessionLocalDataSource: SecureSessionLocalDataSource,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var webSocket: WebSocket? = null
    private var isConnected: Boolean = false
    private var keepAliveJob: Job? = null

    private val subscribedConversationIds = linkedSetOf<Int>()

    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    suspend fun connectIfNeeded(): Boolean {
        if (webSocket != null) return true

        val session = sessionLocalDataSource.getSessionSnapshot() ?: return false
        val accessToken = session.accessToken?.takeIf { it.isNotBlank() } ?: return false
        val deviceUuid = session.deviceUuid?.takeIf { it.isNotBlank() } ?: return false

        val request = Request.Builder()
            .url(buildWsUrl())
            .header("Authorization", "Bearer $accessToken")
            .header("X-Device-UUID", deviceUuid)
            .build()

        webSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    isConnected = true
                    startKeepAlive()

                    scope.launch {
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
                    stopKeepAlive()
                    isConnected = false
                    this@RealtimeWebSocketManager.webSocket = null
                    scope.launch {
                        _events.emit(RealtimeEvent.Disconnected)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    stopKeepAlive()
                    isConnected = false
                    this@RealtimeWebSocketManager.webSocket = null
                    scope.launch {
                        _events.emit(RealtimeEvent.Disconnected)
                    }
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: okhttp3.Response?,
                ) {
                    stopKeepAlive()
                    isConnected = false
                    this@RealtimeWebSocketManager.webSocket = null
                    scope.launch {
                        _events.emit(
                            RealtimeEvent.Error(
                                message = t.message ?: "WebSocket failure"
                            )
                        )
                    }
                }
            }
        )

        return true
    }

    fun disconnect() {
        stopKeepAlive()
        webSocket?.close(1000, "client_disconnect")
        webSocket = null
        isConnected = false
    }

    suspend fun subscribeConversation(conversationId: Int) {
        subscribedConversationIds.add(conversationId)
        if (!connectIfNeeded()) return

        if (isConnected) {
            webSocket?.send(
                """
                {"type":"subscribe_conversation","conversation_id":$conversationId}
                """.trimIndent()
            )
        }
    }

    fun unsubscribeConversation(conversationId: Int) {
        subscribedConversationIds.remove(conversationId)

        if (isConnected) {
            webSocket?.send(
                """
                {"type":"unsubscribe_conversation","conversation_id":$conversationId}
                """.trimIndent()
            )
        }
    }

    fun sendPing() {
        if (isConnected) {
            webSocket?.send("""{"type":"ping"}""")
        }
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive && webSocket != null) {
                delay(25_000)
                sendPing()
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    private suspend fun resubscribeAll() {
        subscribedConversationIds.forEach { conversationId ->
            webSocket?.send(
                """
                {"type":"subscribe_conversation","conversation_id":$conversationId}
                """.trimIndent()
            )
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
                val eventType = parsed["event"]
                    ?.jsonObject
                    ?.get("event_type")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: "unknown"

                _events.emit(
                    RealtimeEvent.ConversationEvent(
                        conversationId = conversationId,
                        eventType = eventType,
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
}

private val kotlinx.serialization.json.JsonPrimitive.intOrNull: Int?
    get() = content.toIntOrNull()

private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
    get() = runCatching { content }.getOrNull()
