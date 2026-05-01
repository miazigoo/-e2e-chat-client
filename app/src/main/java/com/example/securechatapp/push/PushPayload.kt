package com.example.securechatapp.push

sealed interface PushPayload {
    data class NewMessage(
        val conversationId: Int,
        val messageId: Int,
    ) : PushPayload

    data class ConversationEvent(
        val conversationId: Int,
        val eventType: String,
    ) : PushPayload

    data class DeviceApprovalRequested(
        val requestId: String,
        val deviceName: String? = null,
        val platform: String? = null,
        val appVersion: String? = null,
    ) : PushPayload

    data class AppUpdateAvailable(
        val versionName: String,
        val versionCode: Int,
        val changelog: String?,
        val fileName: String,
        val fileSize: Long,
    ) : PushPayload
}

fun parsePushPayload(data: Map<String, String>): PushPayload? {
    return when (data["type"]) {
        "new_message" -> {
            val conversationId = data["conversation_id"]?.toIntOrNull()
            val messageId = data["message_id"]?.toIntOrNull()
            if (conversationId != null && messageId != null) {
                PushPayload.NewMessage(
                    conversationId = conversationId,
                    messageId = messageId,
                )
            } else {
                null
            }
        }

        "conversation_event" -> {
            val conversationId = data["conversation_id"]?.toIntOrNull()
            val eventType = data["event_type"]
            if (conversationId != null && !eventType.isNullOrBlank()) {
                PushPayload.ConversationEvent(
                    conversationId = conversationId,
                    eventType = eventType,
                )
            } else {
                null
            }
        }

        "device_approval_requested" -> {
            val requestId = data["request_id"]?.takeIf { it.isNotBlank() } ?: return null
            PushPayload.DeviceApprovalRequested(
                requestId = requestId,
                deviceName = data["device_name"]?.takeIf { it.isNotBlank() },
                platform = data["platform"]?.takeIf { it.isNotBlank() },
                appVersion = data["app_version"]?.takeIf { it.isNotBlank() },
            )
        }

        "app_update_available" -> {
            val versionName = data["version_name"]
            val versionCode = data["version_code"]?.toIntOrNull()
            val fileName = data["file_name"]
            val fileSize = data["file_size"]?.toLongOrNull()
            if (
                !versionName.isNullOrBlank() &&
                versionCode != null &&
                !fileName.isNullOrBlank() &&
                fileSize != null
            ) {
                PushPayload.AppUpdateAvailable(
                    versionName = versionName,
                    versionCode = versionCode,
                    changelog = data["changelog"]?.takeIf { it.isNotBlank() },
                    fileName = fileName,
                    fileSize = fileSize,
                )
            } else {
                null
            }
        }

        else -> null
    }
}
