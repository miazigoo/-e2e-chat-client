package com.example.securechatapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.securechatapp.data.remote.websocket.RealtimeConnectionState

@Composable
fun RealtimeStatusBadge(
    connectionState: RealtimeConnectionState,
    modifier: Modifier = Modifier,
) {
    val (label, containerColor, contentColor) = statusColors(connectionState)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(contentColor),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
fun RealtimeStatusBanner(
    connectionState: RealtimeConnectionState,
    modifier: Modifier = Modifier,
) {
    if (connectionState == RealtimeConnectionState.CONNECTED) return

    val (_, containerColor, contentColor) = statusColors(connectionState)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Text(
            text = when (connectionState) {
                RealtimeConnectionState.CONNECTING -> "Подключаем realtime…"
                RealtimeConnectionState.RECONNECTING -> "Соединение потеряно. Пытаемся переподключиться…"
                RealtimeConnectionState.DISCONNECTED -> "Realtime отключён. Возможны задержки обновлений."
                RealtimeConnectionState.CONNECTED -> ""
            },
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun statusColors(
    connectionState: RealtimeConnectionState,
): Triple<String, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    return when (connectionState) {
        RealtimeConnectionState.CONNECTED -> Triple(
            "Онлайн",
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.primary,
        )

        RealtimeConnectionState.CONNECTING -> Triple(
            "Подключение",
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.secondary,
        )

        RealtimeConnectionState.RECONNECTING -> Triple(
            "Переподключение",
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.secondary,
        )

        RealtimeConnectionState.DISCONNECTED -> Triple(
            "Оффлайн",
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.error,
        )
    }
}
