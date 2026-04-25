package com.example.securechatapp.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushPayloadTest {
    @Test
    fun `parses new_message payload`() {
        val payload = parsePushPayload(
            mapOf(
                "type" to "new_message",
                "conversation_id" to "42",
                "message_id" to "99",
            ),
        )

        assertEquals(
            PushPayload.NewMessage(conversationId = 42, messageId = 99),
            payload,
        )
    }

    @Test
    fun `parses app update payload`() {
        val payload = parsePushPayload(
            mapOf(
                "type" to "app_update_available",
                "version_name" to "2.0.0",
                "version_code" to "200",
                "file_name" to "secure-chat.apk",
                "file_size" to "4096",
                "changelog" to "UI polish",
            ),
        )

        assertEquals(
            PushPayload.AppUpdateAvailable(
                versionName = "2.0.0",
                versionCode = 200,
                changelog = "UI polish",
                fileName = "secure-chat.apk",
                fileSize = 4096,
            ),
            payload,
        )
    }

    @Test
    fun `returns null for incomplete payload`() {
        val payload = parsePushPayload(
            mapOf(
                "type" to "new_message",
                "conversation_id" to "42",
            ),
        )

        assertNull(payload)
    }
}
